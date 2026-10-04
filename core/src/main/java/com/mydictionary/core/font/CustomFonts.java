package com.mydictionary.core.font;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * ユーザーがデスクトップ版で追加するフォント（ノート本文用）の、ファイル名規則とCSS生成。
 * 追加されたフォントはデータ保存先の{@code fonts}フォルダに、必ず共通のTrueType形式(.ttf)へ
 * 正規化して置く（ttc/woff/woff2はデスクトップ版が取り込み時に変換する）。ファイル名に
 * 「ファミリー名・太さ・斜体か」を持たせるので、Android版はフォントファイルを解析せず、
 * ファイル名を読むだけで一覧とCSSを作れる。ファイル名は {@code <ファミリー名>.<太さ><n|i>.ttf}
 * （例: {@code BIZ UDPGothic.700n.ttf}）。
 */
public final class CustomFonts {

    /** フォントフォルダの名前（デスクトップ・Androidのデータ保存先、Driveの共有フォルダ内で共通）。 */
    public static final String FOLDER_NAME = "fonts";

    public static final String EXTENSION = ".ttf";

    private static final int MAX_FAMILY_LENGTH = 80;

    /** 追加済みフォント1ファイル分（同じファミリーの標準・太字・斜体は別ファイル）。 */
    public record Face(String family, int weight, boolean italic, String fileName) {
    }

    private CustomFonts() {
    }

    /**
     * ファミリー名をファイル名・CSS・HTMLのstyle属性に安全に埋め込める形にする。
     * Windowsのファイル名に使えない文字と、CSS/HTMLの区切りになる文字（引用符・セミコロン・括弧・
     * 山括弧など）を「_」に置き換える。表示名とCSSのfont-familyは同じ値を使う。
     */
    public static String sanitizeFamily(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.trim().toCharArray()) {
            boolean bad = c < 0x20 || "\\/:*?\"'<>|;{}()&%#".indexOf(c) >= 0;
            sb.append(bad ? '_' : c);
        }
        String result = sb.toString().replaceAll("\\s+", " ").trim();
        if (result.length() > MAX_FAMILY_LENGTH) {
            result = result.substring(0, MAX_FAMILY_LENGTH).trim();
        }
        // 末尾のドットはWindowsでファイル名として扱えない。
        while (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result.isEmpty() ? "Font" : result;
    }

    public static String fileName(String family, int weight, boolean italic) {
        return sanitizeFamily(family) + "." + clampWeight(weight) + (italic ? "i" : "n") + EXTENSION;
    }

    /** ファイル名から{@link Face}を復元する。規則に合わないファイル名（他のファイル等）は空。 */
    public static Optional<Face> parse(String fileName) {
        if (fileName == null || !fileName.toLowerCase().endsWith(EXTENSION)) {
            return Optional.empty();
        }
        String stem = fileName.substring(0, fileName.length() - EXTENSION.length());
        int dot = stem.lastIndexOf('.');
        if (dot <= 0 || stem.length() - dot < 3) {
            return Optional.empty();
        }
        String style = stem.substring(dot + 1);
        char last = style.charAt(style.length() - 1);
        if (last != 'n' && last != 'i') {
            return Optional.empty();
        }
        int weight;
        try {
            weight = Integer.parseInt(style.substring(0, style.length() - 1));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        String family = stem.substring(0, dot);
        if (weight < 1 || weight > 1000 || family.isBlank() || !family.equals(sanitizeFamily(family))) {
            return Optional.empty();
        }
        return Optional.of(new Face(family, weight, last == 'i', fileName));
    }

    /** ファイル名の一覧から、フォントとして有効なものだけを、ファミリー名→太さ→斜体の順で返す。 */
    public static List<Face> parseAll(Collection<String> fileNames) {
        List<Face> faces = new ArrayList<>();
        for (String fileName : fileNames) {
            parse(fileName).ifPresent(faces::add);
        }
        faces.sort(Comparator.comparing(Face::family, String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(Face::weight).thenComparing(Face::italic));
        return faces;
    }

    /** 重複を除いたファミリー名の一覧（faces内の並び順のまま）。 */
    public static List<String> families(List<Face> faces) {
        Set<String> families = new LinkedHashSet<>();
        for (Face face : faces) {
            families.add(face.family());
        }
        return new ArrayList<>(families);
    }

    /**
     * 全ての追加フォントを使えるようにする{@code @font-face}規則。標準・太字・斜体を同じ
     * ファミリーに太さ/スタイル付きで登録するので、ブラウザは太字(&lt;b&gt;)に本物の太字ファイルを選べる。
     * urlOfはファイル名から、その環境でWebViewが読めるURLを返す。
     */
    public static String fontFaceCss(List<Face> faces, Function<String, String> urlOf) {
        StringBuilder css = new StringBuilder();
        for (Face face : faces) {
            String url = urlOf.apply(face.fileName()).replace("'", "%27");
            css.append("@font-face{font-family:'").append(face.family()).append("';font-weight:")
                .append(face.weight()).append(";font-style:").append(face.italic() ? "italic" : "normal")
                .append(";src:url('").append(url).append("') format('truetype');}");
        }
        return css.toString();
    }

    private static int clampWeight(int weight) {
        return Math.max(1, Math.min(1000, weight));
    }
}
