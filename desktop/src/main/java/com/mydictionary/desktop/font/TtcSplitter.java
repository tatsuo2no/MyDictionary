package com.mydictionary.desktop.font;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * TrueTypeコレクション(.ttc)を、フォントごとの単体sfnt(.ttf)へ分割する。
 * ttcはAndroid(Chromium)のWebフォントが読めないため、取り込み時に分割しておく。
 */
final class TtcSplitter {
    private static final int TTCF = 0x74746366;

    private TtcSplitter() {
    }

    static boolean matches(byte[] data) {
        return data.length >= 4 && ByteBuffer.wrap(data).getInt(0) == TTCF;
    }

    static List<byte[]> split(byte[] data) throws IOException {
        if (!matches(data) || data.length < 12) {
            throw new IOException("TTCファイルとして認識できません");
        }
        ByteBuffer b = ByteBuffer.wrap(data);
        int numFonts = b.getInt(8);
        if (numFonts < 1 || numFonts > 64 || 12 + numFonts * 4 > data.length) {
            throw new IOException("TTCのフォント数が不正です");
        }
        List<byte[]> fonts = new ArrayList<>();
        for (int i = 0; i < numFonts; i++) {
            int offset = b.getInt(12 + i * 4);
            if (offset < 0 || offset + 12 > data.length) {
                throw new IOException("TTC内のフォントの位置が不正です");
            }
            fonts.add(Sfnt.build(b.getInt(offset), Sfnt.readTables(data, offset)));
        }
        return fonts;
    }
}
