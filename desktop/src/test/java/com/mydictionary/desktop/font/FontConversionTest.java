package com.mydictionary.desktop.font;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mydictionary.core.font.CustomFonts;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * woff2/woff/ttcの変換が正しいことを、同じフォント(KaTeX Main Regular)のttf・woff・woff2が揃った
 * 「正解データ」と比べて検証する。glyfは変換の過程でバイト列が変わりうるため、グリフの中身
 * （輪郭の点・bbox・命令）を解釈して比較する。
 */
class FontConversionTest {

    private static byte[] resource(String path) throws IOException {
        try (InputStream in = FontConversionTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("テスト用リソースがありません: " + path);
            }
            return in.readAllBytes();
        }
    }

    private static byte[] oracleTtf() throws IOException {
        return resource("/fonts/KaTeX_Main-Regular.ttf");
    }

    private static byte[] woff2() throws IOException {
        return resource("/com/mydictionary/desktop/katex/fonts/KaTeX_Main-Regular.woff2");
    }

    // ---------------------------------------------------------------- グリフ解釈（検証用）

    private record Glyph(int nContours, int xMin, int yMin, int xMax, int yMax, int[] endPts, byte[] instr,
                         int[] x, int[] y, boolean[] on, byte[] composite) {
    }

    private static List<Glyph> glyphs(Map<String, byte[]> tables) {
        byte[] glyf = tables.get("glyf");
        ByteBuffer loca = ByteBuffer.wrap(tables.get("loca"));
        boolean shortFormat = ByteBuffer.wrap(tables.get("head")).getShort(50) == 0;
        int numGlyphs = ByteBuffer.wrap(tables.get("maxp")).getShort(4) & 0xFFFF;
        List<Glyph> result = new ArrayList<>();
        for (int g = 0; g < numGlyphs; g++) {
            int start = shortFormat ? (loca.getShort(g * 2) & 0xFFFF) * 2 : loca.getInt(g * 4);
            int end = shortFormat ? (loca.getShort(g * 2 + 2) & 0xFFFF) * 2 : loca.getInt(g * 4 + 4);
            result.add(end == start ? null : parseGlyph(glyf, start));
        }
        return result;
    }

    private static Glyph parseGlyph(byte[] glyf, int off) {
        ByteBuffer b = ByteBuffer.wrap(glyf);
        int n = b.getShort(off);
        int xMin = b.getShort(off + 2);
        int yMin = b.getShort(off + 4);
        int xMax = b.getShort(off + 6);
        int yMax = b.getShort(off + 8);
        int p = off + 10;
        if (n < 0) {
            int start = p;
            int flags;
            boolean instr = false;
            do {
                flags = b.getShort(p) & 0xFFFF;
                p += 4 + ((flags & 1) != 0 ? 4 : 2);
                if ((flags & 0x0008) != 0) {
                    p += 2;
                } else if ((flags & 0x0040) != 0) {
                    p += 4;
                } else if ((flags & 0x0080) != 0) {
                    p += 8;
                }
                instr |= (flags & 0x0100) != 0;
            } while ((flags & 0x20) != 0);
            if (instr) {
                p += 2 + (b.getShort(p) & 0xFFFF);
            }
            return new Glyph(n, xMin, yMin, xMax, yMax, null, null, null, null, null,
                Arrays.copyOfRange(glyf, start, p));
        }
        int[] endPts = new int[n];
        for (int i = 0; i < n; i++) {
            endPts[i] = b.getShort(p) & 0xFFFF;
            p += 2;
        }
        int instrLen = b.getShort(p) & 0xFFFF;
        p += 2;
        byte[] instr = Arrays.copyOfRange(glyf, p, p + instrLen);
        p += instrLen;
        int total = n == 0 ? 0 : endPts[n - 1] + 1;
        int[] flags = new int[total];
        for (int i = 0; i < total; i++) {
            int f = glyf[p++] & 0xFF;
            flags[i] = f;
            if ((f & 0x08) != 0) {
                int repeat = glyf[p++] & 0xFF;
                while (repeat-- > 0) {
                    flags[++i] = f;
                }
            }
        }
        int[] xs = new int[total];
        int[] ys = new int[total];
        boolean[] on = new boolean[total];
        int v = 0;
        for (int i = 0; i < total; i++) {
            int f = flags[i];
            on[i] = (f & 1) != 0;
            if ((f & 0x02) != 0) {
                int d = glyf[p++] & 0xFF;
                v += (f & 0x10) != 0 ? d : -d;
            } else if ((f & 0x10) == 0) {
                v += b.getShort(p);
                p += 2;
            }
            xs[i] = v;
        }
        v = 0;
        for (int i = 0; i < total; i++) {
            int f = flags[i];
            if ((f & 0x04) != 0) {
                int d = glyf[p++] & 0xFF;
                v += (f & 0x20) != 0 ? d : -d;
            } else if ((f & 0x20) == 0) {
                v += b.getShort(p);
                p += 2;
            }
            ys[i] = v;
        }
        return new Glyph(n, xMin, yMin, xMax, yMax, endPts, instr, xs, ys, on, null);
    }

    private static void assertSameGlyphs(byte[] expectedTtf, byte[] actualTtf) throws IOException {
        List<Glyph> expected = glyphs(Sfnt.readTables(expectedTtf, 0));
        List<Glyph> actual = glyphs(Sfnt.readTables(actualTtf, 0));
        assertEquals(expected.size(), actual.size());
        int compared = 0;
        for (int i = 0; i < expected.size(); i++) {
            Glyph e = expected.get(i);
            Glyph a = actual.get(i);
            if (e == null || e.nContours() == 0) {
                assertTrue(a == null || a.nContours() == 0, "グリフ" + i + "は空のはず");
                continue;
            }
            assertEquals(e.nContours(), a.nContours(), "輪郭数 グリフ" + i);
            assertEquals(e.xMin(), a.xMin(), "xMin グリフ" + i);
            assertEquals(e.yMin(), a.yMin(), "yMin グリフ" + i);
            assertEquals(e.xMax(), a.xMax(), "xMax グリフ" + i);
            assertEquals(e.yMax(), a.yMax(), "yMax グリフ" + i);
            if (e.nContours() > 0) {
                assertArrayEquals(e.endPts(), a.endPts(), "終点 グリフ" + i);
                assertArrayEquals(e.instr(), a.instr(), "命令 グリフ" + i);
                assertArrayEquals(e.x(), a.x(), "x座標 グリフ" + i);
                assertArrayEquals(e.y(), a.y(), "y座標 グリフ" + i);
                assertTrue(Arrays.equals(e.on(), a.on()), "オンカーブ グリフ" + i);
            } else {
                assertArrayEquals(e.composite(), a.composite(), "複合グリフ" + i);
            }
            compared++;
        }
        assertTrue(compared > 100, "比較したグリフ数: " + compared);
    }

    // ---------------------------------------------------------------- テスト

    @Test
    void woff2DecodesToTheSameGlyphsAsTheOriginalTtf() throws IOException {
        byte[] decoded = Woff2Decoder.decode(woff2());
        assertSameGlyphs(oracleTtf(), decoded);

        Map<String, byte[]> expected = Sfnt.readTables(oracleTtf(), 0);
        Map<String, byte[]> actual = Sfnt.readTables(decoded, 0);
        assertEquals(expected.keySet(), actual.keySet());
        for (String tag : List.of("cmap", "hhea", "hmtx", "maxp", "name", "OS/2", "post")) {
            if (expected.containsKey(tag)) {
                assertArrayEquals(expected.get(tag), actual.get(tag), "テーブル " + tag);
            }
        }
    }

    @Test
    void decodedWoff2IsAcceptedAndRenderedByTheJavaFontEngine() throws Exception {
        byte[] decoded = Woff2Decoder.decode(woff2());
        Font font = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(decoded)).deriveFont(1000f);
        assertTrue(font.canDisplay('A'));
        Rectangle2D bounds = font.createGlyphVector(new FontRenderContext(new AffineTransform(), true, true), "o")
            .getVisualBounds();
        assertTrue(bounds.getWidth() > 200 && bounds.getHeight() > 200, "oの大きさ: " + bounds);
    }

    @Test
    void woffDecodesToTheOriginalTables() throws IOException {
        byte[] decoded = Woff1Decoder.decode(resource("/fonts/KaTeX_Main-Regular.woff"));
        Map<String, byte[]> expected = Sfnt.readTables(oracleTtf(), 0);
        Map<String, byte[]> actual = Sfnt.readTables(decoded, 0);
        assertEquals(expected.keySet(), actual.keySet());
        for (String tag : expected.keySet()) {
            if (!"head".equals(tag)) {
                assertArrayEquals(expected.get(tag), actual.get(tag), "テーブル " + tag);
            }
        }
    }

    @Test
    void ttcIsSplitIntoOneTtfPerFont() throws IOException {
        byte[] ttf = oracleTtf();
        byte[] ttc = buildTtc(ttf, ttf);

        List<byte[]> fonts = TtcSplitter.split(ttc);

        assertEquals(2, fonts.size());
        for (byte[] font : fonts) {
            assertSameGlyphs(ttf, font);
        }
    }

    @Test
    void sfntInfoReadsFamilyWeightAndStyle() throws IOException {
        Sfnt.Info info = Sfnt.readInfo(Sfnt.readTables(oracleTtf(), 0));
        assertEquals("KaTeX_Main", info.family());
        assertEquals(400, info.weight());
        assertFalse(info.italic());
    }

    @Test
    void importerNormalizesEveryFormatToTheSameNamedTtf(@TempDir Path tempDir) throws IOException {
        Path ttc = tempDir.resolve("collection.bin");
        Files.write(ttc, buildTtc(oracleTtf(), oracleTtf()));
        Path woff2 = tempDir.resolve("misnamed.ttf");
        Files.write(woff2, woff2());
        Path woff = tempDir.resolve("x.woff");
        Files.write(woff, resource("/fonts/KaTeX_Main-Regular.woff"));
        Path ttf = tempDir.resolve("plain.otf");
        Files.write(ttf, oracleTtf());

        for (Path source : List.of(ttc, woff2, woff, ttf)) {
            Path fonts = tempDir.resolve("fonts-" + source.getFileName());
            List<FontImporter.Imported> imported = FontImporter.importFile(source, fonts);
            assertEquals(1, imported.size(), source.toString());
            assertEquals("KaTeX_Main.400n.ttf", imported.get(0).fileName());
            assertTrue(CustomFonts.parse(imported.get(0).fileName()).isPresent());
            assertAcceptedByJava(Files.readAllBytes(fonts.resolve("KaTeX_Main.400n.ttf")));
        }
    }

    @Test
    void importerRejectsFilesThatAreNotFonts(@TempDir Path tempDir) throws IOException {
        Path notFont = tempDir.resolve("fake.woff2");
        Files.writeString(notFont, "これはフォントではありません");
        assertThrows(IOException.class, () -> FontImporter.importFile(notFont, tempDir.resolve("fonts")));
        assertFalse(Files.exists(tempDir.resolve("fonts")) && Files.list(tempDir.resolve("fonts")).findAny().isPresent());
    }

    @Test
    void truncatedWoff2IsRejectedInsteadOfCrashing() throws IOException {
        byte[] data = woff2();
        byte[] truncated = Arrays.copyOf(data, data.length / 2);
        assertThrows(IOException.class, () -> Woff2Decoder.decode(truncated));
    }

    private static void assertAcceptedByJava(byte[] ttf) throws IOException {
        try {
            assertTrue(Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(ttf)).canDisplay('A'));
        } catch (java.awt.FontFormatException e) {
            throw new IOException(e);
        }
    }

    /** 同じフォントを2つ並べたTTC（テーブルは共有せず、それぞれに持たせる単純な構成）を作る。 */
    private static byte[] buildTtc(byte[]... fonts) throws IOException {
        List<Map<String, byte[]>> tableSets = new ArrayList<>();
        for (byte[] font : fonts) {
            tableSets.add(new TreeMap<>(Sfnt.readTables(font, 0)));
        }
        int headerSize = 12 + 4 * fonts.length;
        int[] dirOffsets = new int[fonts.length];
        int pos = headerSize;
        for (int i = 0; i < fonts.length; i++) {
            dirOffsets[i] = pos;
            pos += 12 + 16 * tableSets.get(i).size();
        }
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        ByteBuffer dirs = ByteBuffer.allocate(pos - headerSize);
        int dataStart = pos;
        int offset = dataStart;
        for (Map<String, byte[]> tables : tableSets) {
            dirs.putInt(0x00010000).putShort((short) tables.size()).putShort((short) 0).putShort((short) 0)
                .putShort((short) 0);
            for (Map.Entry<String, byte[]> e : tables.entrySet()) {
                dirs.put(e.getKey().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
                dirs.putInt(0).putInt(offset).putInt(e.getValue().length);
                data.write(e.getValue());
                int pad = (4 - e.getValue().length % 4) % 4;
                data.write(new byte[pad]);
                offset += e.getValue().length + pad;
            }
        }
        ByteBuffer header = ByteBuffer.allocate(headerSize);
        header.putInt(0x74746366).putInt(0x00010000).putInt(fonts.length);
        for (int dirOffset : dirOffsets) {
            header.putInt(dirOffset);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header.array());
        out.write(dirs.array());
        out.write(data.toByteArray());
        return out.toByteArray();
    }
}
