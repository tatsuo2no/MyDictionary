package com.mydictionary.desktop.font;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * TrueType/OpenType(sfnt)フォントのバイト列を組み立て・読み取りする最小限の道具。
 * woff/woff2/ttcを共通の単体.ttfへ変換する各変換器が使う。
 */
final class Sfnt {

    static final int TTF_VERSION = 0x00010000;
    static final int OTTO = 0x4F54544F;
    static final int TRUE = 0x74727565;

    private Sfnt() {
    }

    /** 4文字のタグを数値にする。 */
    static int tagValue(String tag) {
        byte[] b = tag.getBytes(StandardCharsets.ISO_8859_1);
        return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
    }

    static String tagString(int tag) {
        return new String(new byte[]{(byte) (tag >>> 24), (byte) (tag >>> 16), (byte) (tag >>> 8), (byte) tag},
            StandardCharsets.ISO_8859_1);
    }

    static boolean isSfntVersion(int version) {
        return version == TTF_VERSION || version == OTTO || version == TRUE;
    }

    /** テーブル名→内容 からsfntを組み立てる。ディレクトリはタグ順、各テーブルは4バイト境界に揃える。 */
    static byte[] build(int flavor, Map<String, byte[]> tables) {
        TreeMap<String, byte[]> sorted = new TreeMap<>(tables);
        int numTables = sorted.size();
        int entrySelector = 0;
        while ((1 << (entrySelector + 1)) <= numTables) {
            entrySelector++;
        }
        int searchRange = (1 << entrySelector) * 16;
        int rangeShift = numTables * 16 - searchRange;

        int offset = 12 + 16 * numTables;
        int total = offset;
        for (byte[] t : sorted.values()) {
            total += (t.length + 3) & ~3;
        }
        ByteBuffer out = ByteBuffer.allocate(total);
        out.putInt(flavor).putShort((short) numTables).putShort((short) searchRange)
            .putShort((short) entrySelector).putShort((short) rangeShift);

        int headDataOffset = -1;
        for (Map.Entry<String, byte[]> e : sorted.entrySet()) {
            byte[] data = e.getValue();
            out.put(e.getKey().getBytes(StandardCharsets.ISO_8859_1));
            out.putInt(checksum(data, "head".equals(e.getKey())));
            out.putInt(offset).putInt(data.length);
            if ("head".equals(e.getKey())) {
                headDataOffset = offset;
            }
            offset += (data.length + 3) & ~3;
        }
        for (byte[] data : sorted.values()) {
            out.put(data);
            out.put(new byte[((data.length + 3) & ~3) - data.length]);
        }
        byte[] result = out.array();

        if (headDataOffset >= 0 && sorted.get("head").length >= 12) {
            // head.checkSumAdjustment = 0xB1B0AFBA - (フォント全体のチェックサム)。
            ByteBuffer.wrap(result).putInt(headDataOffset + 8, 0);
            int whole = checksum(result, false);
            ByteBuffer.wrap(result).putInt(headDataOffset + 8, 0xB1B0AFBA - whole);
        }
        return result;
    }

    /** 4バイトずつ足し合わせるチェックサム。headはcheckSumAdjustment欄(8〜11バイト目)を0として数える。 */
    static int checksum(byte[] data, boolean isHead) {
        long sum = 0;
        int padded = (data.length + 3) & ~3;
        for (int i = 0; i < padded; i += 4) {
            int word = 0;
            for (int j = 0; j < 4; j++) {
                int idx = i + j;
                int b = idx < data.length ? data[idx] & 0xFF : 0;
                if (isHead && idx >= 8 && idx < 12) {
                    b = 0;
                }
                word = (word << 8) | b;
            }
            sum += word & 0xFFFFFFFFL;
        }
        return (int) sum;
    }

    /** sfntのテーブル一覧（名前→内容）を読み出す。offsetはsfntヘッダの位置（ttcの場合は各フォントの位置）。 */
    static Map<String, byte[]> readTables(byte[] data, int sfntOffset) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(data);
        if (sfntOffset + 12 > data.length || !isSfntVersion(b.getInt(sfntOffset))) {
            throw new IOException("フォントファイルとして認識できません");
        }
        int numTables = b.getShort(sfntOffset + 4) & 0xFFFF;
        Map<String, byte[]> tables = new TreeMap<>();
        for (int i = 0; i < numTables; i++) {
            int rec = sfntOffset + 12 + i * 16;
            if (rec + 16 > data.length) {
                throw new IOException("フォントのテーブル一覧が壊れています");
            }
            String tag = tagString(b.getInt(rec));
            long offset = b.getInt(rec + 8) & 0xFFFFFFFFL;
            long length = b.getInt(rec + 12) & 0xFFFFFFFFL;
            if (offset + length > data.length) {
                throw new IOException("フォントのテーブル「" + tag + "」が範囲外です");
            }
            byte[] table = new byte[(int) length];
            System.arraycopy(data, (int) offset, table, 0, (int) length);
            tables.put(tag, table);
        }
        return tables;
    }

    /** フォントの名前・太さ・斜体。 */
    record Info(String family, int weight, boolean italic) {
    }

    /** nameテーブルとOS/2・headから、ファミリー名・太さ・斜体かを調べる。 */
    static Info readInfo(Map<String, byte[]> tables) {
        String family = null;
        byte[] name = tables.get("name");
        if (name != null) {
            family = findName(name, 16);
            if (family == null) {
                family = findName(name, 1);
            }
        }
        if (family == null || family.isBlank()) {
            family = "Font";
        }

        int weight = 400;
        boolean italic = false;
        byte[] os2 = tables.get("OS/2");
        byte[] head = tables.get("head");
        if (os2 != null && os2.length >= 64) {
            ByteBuffer o = ByteBuffer.wrap(os2);
            int w = o.getShort(4) & 0xFFFF;
            if (w >= 1 && w <= 1000) {
                weight = w;
            }
            italic = (o.getShort(62) & 0x01) != 0;
        } else if (head != null && head.length >= 46) {
            int macStyle = ByteBuffer.wrap(head).getShort(44) & 0xFFFF;
            weight = (macStyle & 0x01) != 0 ? 700 : 400;
            italic = (macStyle & 0x02) != 0;
        }
        return new Info(family, weight, italic);
    }

    /** 英語(Windows 0x0409)を優先して、nameIDの文字列を取り出す。無ければ他のWindows/Unicode/Mac。 */
    private static String findName(byte[] name, int nameId) {
        ByteBuffer b = ByteBuffer.wrap(name);
        if (name.length < 6) {
            return null;
        }
        int count = b.getShort(2) & 0xFFFF;
        int storage = b.getShort(4) & 0xFFFF;
        List<String[]> candidates = new ArrayList<>();
        String best = null;
        int bestRank = Integer.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            int rec = 6 + i * 12;
            if (rec + 12 > name.length) {
                break;
            }
            int platform = b.getShort(rec) & 0xFFFF;
            int encoding = b.getShort(rec + 2) & 0xFFFF;
            int language = b.getShort(rec + 4) & 0xFFFF;
            int id = b.getShort(rec + 6) & 0xFFFF;
            int length = b.getShort(rec + 8) & 0xFFFF;
            int offset = b.getShort(rec + 10) & 0xFFFF;
            if (id != nameId || storage + offset + length > name.length) {
                continue;
            }
            int rank;
            if (platform == 3 && language == 0x0409) {
                rank = 0;
            } else if (platform == 3) {
                rank = 2;
            } else if (platform == 0) {
                rank = 3;
            } else if (platform == 1 && language == 0) {
                rank = 1;
            } else {
                rank = 4;
            }
            if (rank >= bestRank) {
                continue;
            }
            String text = (platform == 1)
                ? new String(name, storage + offset, length, StandardCharsets.ISO_8859_1)
                : new String(name, storage + offset, length, StandardCharsets.UTF_16BE);
            if (!text.isBlank()) {
                best = text.trim();
                bestRank = rank;
            }
        }
        return best;
    }
}
