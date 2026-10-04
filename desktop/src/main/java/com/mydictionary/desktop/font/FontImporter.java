package com.mydictionary.desktop.font;

import com.mydictionary.core.font.CustomFonts;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ユーザーが選んだフォントファイル(.ttc/.woff/.woff2/.ttf/.otf)を、共通の単体.ttf形式へ正規化して
 * フォントフォルダに保存する（ファイル名は{@link CustomFonts#fileName}の規則）。
 * 形式は拡張子ではなくファイル先頭のバイト列で判定する（拡張子と実体が食い違うファイルでも壊れないように）。
 */
public final class FontImporter {

    /** 取り込んだフォント1つ分の結果。 */
    public record Imported(String family, int weight, boolean italic, String fileName) {
    }

    private static final long MAX_FILE_BYTES = 200L * 1024 * 1024;

    private FontImporter() {
    }

    public static List<Imported> importFile(Path source, Path fontsDir) throws IOException {
        if (Files.size(source) > MAX_FILE_BYTES) {
            throw new IOException("フォントファイルが大きすぎます（200MBまで）");
        }
        byte[] data = Files.readAllBytes(source);
        List<byte[]> sfnts = normalize(data);

        Files.createDirectories(fontsDir);
        // ttc内の複数フォントが同じ名前・太さになる場合は、最初の1つだけ取り込む。
        Map<String, byte[]> byFileName = new LinkedHashMap<>();
        Map<String, Imported> results = new LinkedHashMap<>();
        for (byte[] sfnt : sfnts) {
            Map<String, byte[]> tables = Sfnt.readTables(sfnt, 0);
            if (!tables.containsKey("cmap") || !(tables.containsKey("glyf") || tables.containsKey("CFF "))) {
                throw new IOException("文字のデータを持たないフォントです");
            }
            Sfnt.Info info = Sfnt.readInfo(tables);
            String fileName = CustomFonts.fileName(info.family(), info.weight(), info.italic());
            if (byFileName.putIfAbsent(fileName, sfnt) == null) {
                CustomFonts.Face face = CustomFonts.parse(fileName).orElseThrow();
                results.put(fileName, new Imported(face.family(), face.weight(), face.italic(), fileName));
            }
        }
        for (Map.Entry<String, byte[]> e : byFileName.entrySet()) {
            Path target = fontsDir.resolve(e.getKey());
            Path temp = fontsDir.resolve(e.getKey() + ".tmp");
            Files.write(temp, e.getValue());
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new ArrayList<>(results.values());
    }

    /** 形式を判定して、単体sfnt(.ttf/.otf)のバイト列の一覧にそろえる。 */
    static List<byte[]> normalize(byte[] data) throws IOException {
        if (Woff2Decoder.matches(data)) {
            return List.of(Woff2Decoder.decode(data));
        }
        if (Woff1Decoder.matches(data)) {
            return List.of(Woff1Decoder.decode(data));
        }
        if (TtcSplitter.matches(data)) {
            return TtcSplitter.split(data);
        }
        if (data.length >= 12 && Sfnt.isSfntVersion(ByteBuffer.wrap(data).getInt(0))) {
            return List.of(data);
        }
        throw new IOException("対応していない形式です（.ttc/.woff/.woff2/.ttf/.otfのフォントファイルを選んでください）");
    }
}
