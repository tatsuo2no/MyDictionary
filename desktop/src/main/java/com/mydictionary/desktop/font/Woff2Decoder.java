package com.mydictionary.desktop.font;

import org.brotli.dec.BrotliInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * WOFF2を単体のsfnt(.ttf)へ変換する（W3C WOFF2仕様の復号）。デスクトップ版のWebViewはWOFF2を
 * 表示できないため、取り込み時に変換する。Brotliで全テーブルを展開した後、WOFF2独自の変換
 * （glyf/locaの再エンコード、hmtxの側方ベアリング省略）を元のTrueType形式へ戻す。
 */
final class Woff2Decoder {
    private static final int SIGNATURE = 0x774F4632; // 'wOF2'
    private static final int TTCF = 0x74746366;
    private static final int HEADER_SIZE = 48;
    private static final int KNOWN_TAG_ARBITRARY = 63;

    private static final String[] KNOWN_TAGS = {
        "cmap", "head", "hhea", "hmtx", "maxp", "name", "OS/2", "post", "cvt ", "fpgm", "glyf", "loca", "prep",
        "CFF ", "VORG", "EBDT", "EBLC", "gasp", "hdmx", "kern", "LTSH", "PCLT", "VDMX", "vhea", "vmtx", "BASE",
        "GDEF", "GPOS", "GSUB", "EBSC", "JSTF", "MATH", "CBDT", "CBLC", "COLR", "CPAL", "SVG ", "sbix", "acnt",
        "avar", "bdat", "bloc", "bsln", "cvar", "fdsc", "feat", "fmtx", "fvar", "gvar", "hsty", "just", "lcar",
        "mort", "morx", "opbd", "prop", "trak", "Zapf", "Silf", "Glat", "Gloc", "Feat", "Sill"};

    /** 展開後のサイズの上限（壊れた/悪意あるファイルでメモリを使い切らないための保険）。 */
    private static final long MAX_UNCOMPRESSED = 256L * 1024 * 1024;

    private Woff2Decoder() {
    }

    static boolean matches(byte[] data) {
        return data.length >= 4 && ByteBuffer.wrap(data).getInt(0) == SIGNATURE;
    }

    private record Entry(String tag, boolean transformed, long origLength, long transformLength) {
    }

    static byte[] decode(byte[] data) throws IOException {
        if (!matches(data) || data.length < HEADER_SIZE) {
            throw new IOException("WOFF2ファイルとして認識できません");
        }
        ByteBuffer header = ByteBuffer.wrap(data);
        int flavor = header.getInt(4);
        if (flavor == TTCF) {
            throw new IOException("フォントコレクション形式のWOFF2には対応していません");
        }
        int numTables = header.getShort(12) & 0xFFFF;
        long compressedSize = header.getInt(20) & 0xFFFFFFFFL;

        Cursor dir = new Cursor(data, HEADER_SIZE, data.length);
        List<Entry> entries = new ArrayList<>();
        long totalLength = 0;
        for (int i = 0; i < numTables; i++) {
            int flags = dir.u8();
            int tagIndex = flags & 0x3F;
            int version = (flags >> 6) & 3;
            String tag = tagIndex == KNOWN_TAG_ARBITRARY ? Sfnt.tagString(dir.i32()) : KNOWN_TAGS[tagIndex];
            long origLength = dir.base128();
            boolean isGlyfOrLoca = "glyf".equals(tag) || "loca".equals(tag);
            boolean transformed = isGlyfOrLoca ? version != 3 : version != 0;
            long transformLength = transformed ? dir.base128() : origLength;
            totalLength += transformLength;
            entries.add(new Entry(tag, transformed, origLength, transformLength));
        }
        if (totalLength > MAX_UNCOMPRESSED) {
            throw new IOException("WOFF2の展開後サイズが大きすぎます");
        }
        int compressedStart = dir.pos;
        if (compressedStart + compressedSize > data.length) {
            throw new IOException("WOFF2の圧縮データが範囲外です");
        }

        byte[] raw = brotli(data, compressedStart, (int) compressedSize, (int) totalLength);

        Map<String, byte[]> tables = new TreeMap<>();
        Map<String, byte[]> transformedTables = new TreeMap<>();
        int pos = 0;
        for (Entry e : entries) {
            byte[] table = new byte[(int) e.transformLength()];
            System.arraycopy(raw, pos, table, 0, table.length);
            pos += table.length;
            if (e.transformed()) {
                transformedTables.put(e.tag(), table);
            } else {
                tables.put(e.tag(), table);
            }
        }

        int[] xMin = null;
        if (transformedTables.containsKey("glyf")) {
            GlyfResult glyf = reconstructGlyf(transformedTables.get("glyf"));
            tables.put("glyf", glyf.glyf);
            tables.put("loca", glyf.loca);
            xMin = glyf.xMin;
        } else if (transformedTables.containsKey("loca")) {
            throw new IOException("glyfなしでlocaだけが変換されています");
        }
        if (transformedTables.containsKey("hmtx")) {
            if (xMin == null || !tables.containsKey("hhea") || !tables.containsKey("maxp")) {
                throw new IOException("hmtxの復元に必要なテーブルがありません");
            }
            int numHMetrics = ByteBuffer.wrap(tables.get("hhea")).getShort(34) & 0xFFFF;
            int numGlyphs = ByteBuffer.wrap(tables.get("maxp")).getShort(4) & 0xFFFF;
            tables.put("hmtx", reconstructHmtx(transformedTables.get("hmtx"), numGlyphs, numHMetrics, xMin));
        }
        return Sfnt.build(flavor, tables);
    }

    private static byte[] brotli(byte[] data, int start, int length, int expected) throws IOException {
        try (BrotliInputStream in = new BrotliInputStream(new ByteArrayInputStream(data, start, length))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(expected);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > expected) {
                    break;
                }
            }
            if (out.size() != expected) {
                throw new IOException("WOFF2の展開サイズが一致しません");
            }
            return out.toByteArray();
        }
    }

    // ---------------------------------------------------------------- glyf / loca

    private record GlyfResult(byte[] glyf, byte[] loca, int[] xMin) {
    }

    private static GlyfResult reconstructGlyf(byte[] t) throws IOException {
        Cursor head = new Cursor(t, 0, t.length);
        head.u16(); // reserved
        int optionFlags = head.u16();
        int numGlyphs = head.u16();
        int indexFormat = head.u16();
        long nContourSize = head.u32();
        long nPointsSize = head.u32();
        long flagSize = head.u32();
        long glyphSize = head.u32();
        long compositeSize = head.u32();
        long bboxSize = head.u32();
        long instructionSize = head.u32();

        int p = head.pos;
        Cursor nContourStream = sub(t, p, nContourSize);
        p += nContourSize;
        Cursor nPointsStream = sub(t, p, nPointsSize);
        p += nPointsSize;
        Cursor flagStream = sub(t, p, flagSize);
        p += flagSize;
        Cursor glyphStream = sub(t, p, glyphSize);
        p += glyphSize;
        Cursor compositeStream = sub(t, p, compositeSize);
        p += compositeSize;
        Cursor bboxStream = sub(t, p, bboxSize);
        p += bboxSize;
        Cursor instructionStream = sub(t, p, instructionSize);
        p += instructionSize;
        byte[] overlapBitmap = null;
        if ((optionFlags & 1) != 0) {
            int len = (numGlyphs + 7) / 8;
            overlapBitmap = new byte[len];
            if (p + len > t.length) {
                throw new IOException("glyf変換データが短すぎます");
            }
            System.arraycopy(t, p, overlapBitmap, 0, len);
        }

        int bitmapBytes = 4 * ((numGlyphs + 31) / 32);
        byte[] bboxBitmap = bboxStream.bytes(bitmapBytes);

        ByteArrayOutputStream glyf = new ByteArrayOutputStream();
        int[] offsets = new int[numGlyphs + 1];
        int[] xMinPerGlyph = new int[numGlyphs];

        for (int g = 0; g < numGlyphs; g++) {
            offsets[g] = glyf.size();
            int nContours = nContourStream.s16();
            boolean hasBbox = (bboxBitmap[g >> 3] & (0x80 >> (g & 7))) != 0;
            ByteBuffer out;
            if (nContours == 0) {
                if (hasBbox) {
                    throw new IOException("空のグリフにbboxが指定されています");
                }
                continue;
            } else if (nContours > 0) {
                out = simpleGlyph(nContours, hasBbox, nPointsStream, flagStream, glyphStream, bboxStream,
                    instructionStream, overlapBitmap != null && (overlapBitmap[g >> 3] & (0x80 >> (g & 7))) != 0,
                    xMinPerGlyph, g);
            } else {
                if (!hasBbox) {
                    throw new IOException("複合グリフにbboxがありません");
                }
                out = compositeGlyph(compositeStream, glyphStream, bboxStream, instructionStream, xMinPerGlyph, g);
            }
            byte[] bytes = new byte[out.position()];
            System.arraycopy(out.array(), 0, bytes, 0, bytes.length);
            glyf.write(bytes);
            int pad = (4 - bytes.length % 4) % 4;
            glyf.write(new byte[pad]);
        }
        offsets[numGlyphs] = glyf.size();

        ByteBuffer loca = ByteBuffer.allocate((numGlyphs + 1) * (indexFormat == 0 ? 2 : 4));
        for (int offset : offsets) {
            if (indexFormat == 0) {
                loca.putShort((short) (offset / 2));
            } else {
                loca.putInt(offset);
            }
        }
        return new GlyfResult(glyf.toByteArray(), loca.array(), xMinPerGlyph);
    }

    private static Cursor sub(byte[] t, int start, long length) throws IOException {
        if (length < 0 || start + length > t.length) {
            throw new IOException("glyf変換データのストリームが範囲外です");
        }
        return new Cursor(t, start, (int) (start + length));
    }

    private static ByteBuffer simpleGlyph(int nContours, boolean hasBbox, Cursor nPointsStream, Cursor flagStream,
                                          Cursor glyphStream, Cursor bboxStream, Cursor instructionStream,
                                          boolean overlap, int[] xMinPerGlyph, int glyphIndex) throws IOException {
        int[] endPts = new int[nContours];
        int total = 0;
        for (int c = 0; c < nContours; c++) {
            total += nPointsStream.read255UInt16();
            endPts[c] = total - 1;
        }
        int[] xs = new int[total];
        int[] ys = new int[total];
        boolean[] onCurve = new boolean[total];
        int x = 0;
        int y = 0;
        for (int i = 0; i < total; i++) {
            int flag = flagStream.u8();
            onCurve[i] = (flag & 0x80) == 0;
            flag &= 0x7F;
            int dx;
            int dy;
            if (flag < 10) {
                dx = 0;
                dy = withSign(flag, ((flag & 14) << 7) + glyphStream.u8());
            } else if (flag < 20) {
                dx = withSign(flag, (((flag - 10) & 14) << 7) + glyphStream.u8());
                dy = 0;
            } else if (flag < 84) {
                int b0 = flag - 20;
                int b1 = glyphStream.u8();
                dx = withSign(flag, 1 + (b0 & 0x30) + (b1 >> 4));
                dy = withSign(flag >> 1, 1 + ((b0 & 0x0C) << 2) + (b1 & 0x0F));
            } else if (flag < 120) {
                int b0 = flag - 84;
                int d0 = glyphStream.u8();
                int d1 = glyphStream.u8();
                dx = withSign(flag, 1 + ((b0 / 12) << 8) + d0);
                dy = withSign(flag >> 1, 1 + (((b0 % 12) >> 2) << 8) + d1);
            } else if (flag < 124) {
                int d0 = glyphStream.u8();
                int d1 = glyphStream.u8();
                int d2 = glyphStream.u8();
                dx = withSign(flag, (d0 << 4) + (d1 >> 4));
                dy = withSign(flag >> 1, ((d1 & 0x0F) << 8) + d2);
            } else {
                int d0 = glyphStream.u8();
                int d1 = glyphStream.u8();
                int d2 = glyphStream.u8();
                int d3 = glyphStream.u8();
                dx = withSign(flag, (d0 << 8) + d1);
                dy = withSign(flag >> 1, (d2 << 8) + d3);
            }
            x += dx;
            y += dy;
            xs[i] = x;
            ys[i] = y;
        }
        int instructionLength = glyphStream.read255UInt16();
        byte[] instructions = instructionStream.bytes(instructionLength);

        int xMin;
        int yMin;
        int xMax;
        int yMax;
        if (hasBbox) {
            xMin = bboxStream.s16();
            yMin = bboxStream.s16();
            xMax = bboxStream.s16();
            yMax = bboxStream.s16();
        } else {
            xMin = Integer.MAX_VALUE;
            yMin = Integer.MAX_VALUE;
            xMax = Integer.MIN_VALUE;
            yMax = Integer.MIN_VALUE;
            for (int i = 0; i < total; i++) {
                xMin = Math.min(xMin, xs[i]);
                xMax = Math.max(xMax, xs[i]);
                yMin = Math.min(yMin, ys[i]);
                yMax = Math.max(yMax, ys[i]);
            }
            if (total == 0) {
                xMin = yMin = xMax = yMax = 0;
            }
        }
        xMinPerGlyph[glyphIndex] = xMin;

        ByteBuffer out = ByteBuffer.allocate(10 + nContours * 2 + 2 + instructions.length + total * 5 + 16);
        out.putShort((short) nContours).putShort((short) xMin).putShort((short) yMin).putShort((short) xMax)
            .putShort((short) yMax);
        for (int endPt : endPts) {
            out.putShort((short) endPt);
        }
        out.putShort((short) instructionLength);
        out.put(instructions);

        // 座標は差分で書く。1バイトに収まる値は短縮形(X/Y_SHORT)にする。flagsの繰り返し圧縮はしない。
        byte[] flags = new byte[total];
        ByteBuffer xData = ByteBuffer.allocate(total * 2);
        ByteBuffer yData = ByteBuffer.allocate(total * 2);
        int px = 0;
        int py = 0;
        for (int i = 0; i < total; i++) {
            int f = onCurve[i] ? 0x01 : 0;
            if (i == 0 && overlap) {
                f |= 0x40;
            }
            int dx = xs[i] - px;
            int dy = ys[i] - py;
            px = xs[i];
            py = ys[i];
            if (dx == 0) {
                f |= 0x10;
            } else if (dx > -256 && dx < 256) {
                f |= 0x02 | (dx > 0 ? 0x10 : 0);
                xData.put((byte) Math.abs(dx));
            } else {
                xData.putShort((short) dx);
            }
            if (dy == 0) {
                f |= 0x20;
            } else if (dy > -256 && dy < 256) {
                f |= 0x04 | (dy > 0 ? 0x20 : 0);
                yData.put((byte) Math.abs(dy));
            } else {
                yData.putShort((short) dy);
            }
            flags[i] = (byte) f;
        }
        out.put(flags);
        out.put(xData.array(), 0, xData.position());
        out.put(yData.array(), 0, yData.position());
        return out;
    }

    private static int withSign(int flag, int baseValue) {
        return (flag & 1) != 0 ? baseValue : -baseValue;
    }

    private static ByteBuffer compositeGlyph(Cursor compositeStream, Cursor glyphStream, Cursor bboxStream,
                                              Cursor instructionStream, int[] xMinPerGlyph, int glyphIndex)
        throws IOException {
        ByteArrayOutputStream components = new ByteArrayOutputStream();
        boolean haveInstructions = false;
        int flags;
        do {
            flags = compositeStream.u16();
            int glyph = compositeStream.u16();
            components.write(flags >> 8);
            components.write(flags);
            components.write(glyph >> 8);
            components.write(glyph);
            int argBytes = (flags & 0x0001) != 0 ? 4 : 2;
            int scaleBytes = 0;
            if ((flags & 0x0008) != 0) {
                scaleBytes = 2;
            } else if ((flags & 0x0040) != 0) {
                scaleBytes = 4;
            } else if ((flags & 0x0080) != 0) {
                scaleBytes = 8;
            }
            components.write(compositeStream.bytes(argBytes + scaleBytes));
            if ((flags & 0x0100) != 0) {
                haveInstructions = true;
            }
        } while ((flags & 0x0020) != 0);

        int xMin = bboxStream.s16();
        int yMin = bboxStream.s16();
        int xMax = bboxStream.s16();
        int yMax = bboxStream.s16();
        xMinPerGlyph[glyphIndex] = xMin;

        byte[] instructions = new byte[0];
        int instructionLength = 0;
        if (haveInstructions) {
            instructionLength = glyphStream.read255UInt16();
            instructions = instructionStream.bytes(instructionLength);
        }
        byte[] comp = components.toByteArray();
        ByteBuffer out = ByteBuffer.allocate(10 + comp.length + (haveInstructions ? 2 + instructions.length : 0));
        out.putShort((short) -1).putShort((short) xMin).putShort((short) yMin).putShort((short) xMax)
            .putShort((short) yMax);
        out.put(comp);
        if (haveInstructions) {
            out.putShort((short) instructionLength);
            out.put(instructions);
        }
        return out;
    }

    // ---------------------------------------------------------------- hmtx

    private static byte[] reconstructHmtx(byte[] t, int numGlyphs, int numHMetrics, int[] xMin) throws IOException {
        Cursor in = new Cursor(t, 0, t.length);
        int flags = in.u8();
        boolean lsbOmitted = (flags & 0x01) != 0;
        boolean leftSideBearingOmitted = (flags & 0x02) != 0;
        if (numHMetrics > numGlyphs || numHMetrics < 1) {
            throw new IOException("hmtxのメトリクス数が不正です");
        }
        int[] advances = new int[numHMetrics];
        for (int i = 0; i < numHMetrics; i++) {
            advances[i] = in.u16();
        }
        int[] lsb = new int[numGlyphs];
        for (int i = 0; i < numHMetrics; i++) {
            lsb[i] = lsbOmitted ? xMin[i] : in.s16();
        }
        for (int i = numHMetrics; i < numGlyphs; i++) {
            lsb[i] = leftSideBearingOmitted ? xMin[i] : in.s16();
        }
        ByteBuffer out = ByteBuffer.allocate(numHMetrics * 4 + (numGlyphs - numHMetrics) * 2);
        for (int i = 0; i < numHMetrics; i++) {
            out.putShort((short) advances[i]).putShort((short) lsb[i]);
        }
        for (int i = numHMetrics; i < numGlyphs; i++) {
            out.putShort((short) lsb[i]);
        }
        return out.array();
    }

    // ---------------------------------------------------------------- 読み取り用カーソル

    /** 範囲を超えて読むとIOExceptionを投げる、ビッグエンディアンの読み取り位置付きバッファ。 */
    private static final class Cursor {
        final byte[] d;
        int pos;
        final int end;

        Cursor(byte[] d, int pos, int end) {
            this.d = d;
            this.pos = pos;
            this.end = end;
        }

        private void need(int n) throws IOException {
            if (n < 0 || pos + n > end) {
                throw new IOException("フォントデータが途中で終わっています");
            }
        }

        int u8() throws IOException {
            need(1);
            return d[pos++] & 0xFF;
        }

        int u16() throws IOException {
            need(2);
            int v = ((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF);
            pos += 2;
            return v;
        }

        int s16() throws IOException {
            return (short) u16();
        }

        int i32() throws IOException {
            need(4);
            int v = ((d[pos] & 0xFF) << 24) | ((d[pos + 1] & 0xFF) << 16) | ((d[pos + 2] & 0xFF) << 8)
                | (d[pos + 3] & 0xFF);
            pos += 4;
            return v;
        }

        long u32() throws IOException {
            return i32() & 0xFFFFFFFFL;
        }

        byte[] bytes(int n) throws IOException {
            need(n);
            byte[] b = new byte[n];
            System.arraycopy(d, pos, b, 0, n);
            pos += n;
            return b;
        }

        /** UIntBase128: 1〜5バイトの可変長整数（上位ビットが続きを示す）。 */
        long base128() throws IOException {
            long result = 0;
            for (int i = 0; i < 5; i++) {
                int b = u8();
                if (i == 0 && b == 0x80) {
                    throw new IOException("UIntBase128の先頭が不正です");
                }
                if ((result & 0xFE00000000L) != 0) {
                    throw new IOException("UIntBase128があふれました");
                }
                result = (result << 7) | (b & 0x7F);
                if ((b & 0x80) == 0) {
                    return result;
                }
            }
            throw new IOException("UIntBase128が長すぎます");
        }

        /** 255UInt16: 0〜252はそのまま、253で続く2バイト、254で続く1バイト+506、255で続く1バイト+253。 */
        int read255UInt16() throws IOException {
            int code = u8();
            if (code == 253) {
                return u16();
            }
            if (code == 255) {
                return u8() + 253;
            }
            if (code == 254) {
                return u8() + 506;
            }
            return code;
        }
    }
}
