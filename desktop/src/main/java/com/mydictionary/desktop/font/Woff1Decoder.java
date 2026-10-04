package com.mydictionary.desktop.font;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** WOFF(1.0)を単体のsfnt(.ttf)へ変換する。各テーブルをzlibで展開して組み直すだけ。 */
final class Woff1Decoder {
    private static final int SIGNATURE = 0x774F4646; // 'wOFF'

    private Woff1Decoder() {
    }

    static boolean matches(byte[] data) {
        return data.length >= 4 && ByteBuffer.wrap(data).getInt(0) == SIGNATURE;
    }

    static byte[] decode(byte[] data) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(data);
        if (!matches(data) || data.length < 44) {
            throw new IOException("WOFFファイルとして認識できません");
        }
        int flavor = b.getInt(4);
        int numTables = b.getShort(12) & 0xFFFF;
        Map<String, byte[]> tables = new TreeMap<>();
        for (int i = 0; i < numTables; i++) {
            int rec = 44 + i * 20;
            if (rec + 20 > data.length) {
                throw new IOException("WOFFのテーブル一覧が壊れています");
            }
            String tag = Sfnt.tagString(b.getInt(rec));
            long offset = b.getInt(rec + 4) & 0xFFFFFFFFL;
            long compLength = b.getInt(rec + 8) & 0xFFFFFFFFL;
            long origLength = b.getInt(rec + 12) & 0xFFFFFFFFL;
            if (offset + compLength > data.length || origLength > Integer.MAX_VALUE) {
                throw new IOException("WOFFのテーブル「" + tag + "」が範囲外です");
            }
            byte[] table = new byte[(int) origLength];
            if (compLength == origLength) {
                System.arraycopy(data, (int) offset, table, 0, (int) origLength);
            } else {
                Inflater inflater = new Inflater();
                inflater.setInput(data, (int) offset, (int) compLength);
                try {
                    int done = 0;
                    while (done < table.length && !inflater.finished()) {
                        int n = inflater.inflate(table, done, table.length - done);
                        if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                            break;
                        }
                        done += n;
                    }
                    if (done != table.length) {
                        throw new IOException("WOFFのテーブル「" + tag + "」の展開サイズが一致しません");
                    }
                } catch (DataFormatException e) {
                    throw new IOException("WOFFのテーブル「" + tag + "」を展開できません", e);
                } finally {
                    inflater.end();
                }
            }
            tables.put(tag, table);
        }
        return Sfnt.build(flavor, tables);
    }
}
