package com.mydictionary.core.tagimport;

import com.mydictionary.core.validation.NameValidator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Markdown（インデント付き箇条書き）またはJSON（入れ子配列）で書かれたタグ構造を解析する。
 * タグ画面からのタグ一括インポート機能（デスクトップ版のみ）で使用する。
 *
 * Markdown形式の例（インデント幅は不問。半角スペース/タブいずれも可）:
 * <pre>
 * - 動物
 *   - 哺乳類
 *     - 犬
 *     - 猫
 *   - 鳥類
 * - 植物
 * </pre>
 *
 * JSON形式の例:
 * <pre>
 * [
 *   { "name": "動物", "children": [
 *     { "name": "哺乳類", "children": [ { "name": "犬" }, { "name": "猫" } ] },
 *     { "name": "鳥類" }
 *   ] },
 *   { "name": "植物" }
 * ]
 * </pre>
 */
public final class TagImportParser {

    /** 解析結果の1タグ（DB未登録の中間表現）。子タグを再帰的に保持する。 */
    public static final class ImportedTag {
        private final String name;
        private final List<ImportedTag> children = new ArrayList<>();

        public ImportedTag(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public List<ImportedTag> getChildren() {
            return children;
        }
    }

    /** ファイルの解析に失敗した際にユーザーへそのまま提示できるメッセージを持つ例外。 */
    public static final class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }

    private TagImportParser() {
    }

    // ==================== Markdown ====================

    public static List<ImportedTag> parseMarkdown(String content) throws ParseException {
        List<ImportedTag> roots = new ArrayList<>();
        List<Integer> indents = new ArrayList<>();
        List<ImportedTag> stack = new ArrayList<>();

        String[] lines = content.split("\r\n|\r|\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            int indentWidth = countIndentWidth(line);
            String name = stripBulletMarker(line.strip(), i + 1);
            if (name.isEmpty()) {
                throw new ParseException((i + 1) + "行目: タグ名が空です");
            }
            if (!NameValidator.isValid(name)) {
                throw new ParseException((i + 1) + "行目: 使用できない文字が含まれています: " + name);
            }

            while (!indents.isEmpty() && indentWidth <= indents.get(indents.size() - 1)) {
                indents.remove(indents.size() - 1);
                stack.remove(stack.size() - 1);
            }

            ImportedTag tag = new ImportedTag(name);
            if (stack.isEmpty()) {
                roots.add(tag);
            } else {
                stack.get(stack.size() - 1).getChildren().add(tag);
            }
            indents.add(indentWidth);
            stack.add(tag);
        }

        if (roots.isEmpty()) {
            throw new ParseException("タグが1件も見つかりませんでした");
        }
        return roots;
    }

    private static int countIndentWidth(String line) {
        int width = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ') {
                width++;
            } else if (c == '\t') {
                width += 4;
            } else {
                break;
            }
        }
        return width;
    }

    private static String stripBulletMarker(String trimmedLine, int lineNumber) throws ParseException {
        if (trimmedLine.startsWith("- ")) {
            return trimmedLine.substring(2).strip();
        }
        if (trimmedLine.startsWith("* ")) {
            return trimmedLine.substring(2).strip();
        }
        if (trimmedLine.startsWith("・")) {
            return trimmedLine.substring(1).strip();
        }
        throw new ParseException(lineNumber + "行目: 箇条書き（\"- \"など）で始まっていません: " + trimmedLine);
    }

    // ==================== JSON ====================

    public static List<ImportedTag> parseJson(String content) throws ParseException {
        JsonParser parser = new JsonParser(content);
        Object root = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw new ParseException("JSONの末尾に余分な文字があります");
        }
        if (!(root instanceof List<?> list)) {
            throw new ParseException("JSONのトップレベルは配列である必要があります");
        }

        List<ImportedTag> roots = new ArrayList<>();
        for (Object element : list) {
            roots.add(toImportedTag(element));
        }
        if (roots.isEmpty()) {
            throw new ParseException("タグが1件も見つかりませんでした");
        }
        return roots;
    }

    private static ImportedTag toImportedTag(Object element) throws ParseException {
        if (!(element instanceof Map<?, ?> map)) {
            throw new ParseException("タグは{\"name\": ...}形式のオブジェクトである必要があります");
        }
        Object nameValue = map.get("name");
        if (!(nameValue instanceof String) || ((String) nameValue).isBlank()) {
            throw new ParseException("\"name\"が指定されていないタグがあります");
        }
        String name = ((String) nameValue).strip();
        if (!NameValidator.isValid(name)) {
            throw new ParseException("使用できない文字が含まれています: " + name);
        }

        ImportedTag tag = new ImportedTag(name);
        Object childrenValue = map.get("children");
        if (childrenValue != null) {
            if (!(childrenValue instanceof List<?> children)) {
                throw new ParseException("\"" + name + "\"の\"children\"は配列である必要があります");
            }
            for (Object child : children) {
                tag.getChildren().add(toImportedTag(child));
            }
        }
        return tag;
    }

    /**
     * このクラス専用の最小限のJSONパーサー（外部ライブラリを追加しないための自前実装）。
     * オブジェクト・配列・文字列・数値・真偽値・nullの一般的なJSON文法をひととおりサポートする。
     */
    private static final class JsonParser {
        private final String text;
        private int pos;

        JsonParser(String text) {
            this.text = text;
            this.pos = 0;
        }

        boolean isAtEnd() {
            return pos >= text.length();
        }

        void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        Object parseValue() throws ParseException {
            skipWhitespace();
            if (isAtEnd()) {
                throw new ParseException("JSONが不完全です");
            }
            char c = text.charAt(pos);
            switch (c) {
                case '{':
                    return parseObject();
                case '[':
                    return parseArray();
                case '"':
                    return parseString();
                case 't':
                case 'f':
                    return parseBoolean();
                case 'n':
                    return parseNull();
                default:
                    return parseNumber();
            }
        }

        private Map<String, Object> parseObject() throws ParseException {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char next = next();
                if (next == '}') {
                    break;
                }
                if (next != ',') {
                    throw new ParseException("JSONオブジェクトの形式が不正です（','または'}'が必要, 位置: " + (pos - 1) + "）");
                }
            }
            return map;
        }

        private List<Object> parseArray() throws ParseException {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                char next = next();
                if (next == ']') {
                    break;
                }
                if (next != ',') {
                    throw new ParseException("JSON配列の形式が不正です（','または']'が必要, 位置: " + (pos - 1) + "）");
                }
            }
            return list;
        }

        private String parseString() throws ParseException {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (isAtEnd()) {
                    throw new ParseException("文字列が閉じられていません");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    if (isAtEnd()) {
                        throw new ParseException("文字列のエスケープが不完全です");
                    }
                    char esc = text.charAt(pos++);
                    switch (esc) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'u':
                            if (pos + 4 > text.length()) {
                                throw new ParseException("\\uエスケープが不完全です");
                            }
                            sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                            pos += 4;
                            break;
                        default:
                            throw new ParseException("不正なエスケープ文字です: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Boolean parseBoolean() throws ParseException {
            if (text.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new ParseException("不正な値です（位置: " + pos + "）");
        }

        private Object parseNull() throws ParseException {
            if (text.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new ParseException("不正な値です（位置: " + pos + "）");
        }

        private Double parseNumber() throws ParseException {
            int start = pos;
            while (pos < text.length() && "-+.0123456789eE".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
            if (pos == start) {
                throw new ParseException("不正な値です（位置: " + pos + "）");
            }
            try {
                return Double.parseDouble(text.substring(start, pos));
            } catch (NumberFormatException e) {
                throw new ParseException("不正な数値です（位置: " + start + "）");
            }
        }

        private char peek() throws ParseException {
            if (isAtEnd()) {
                throw new ParseException("JSONが不完全です");
            }
            return text.charAt(pos);
        }

        private char next() throws ParseException {
            if (isAtEnd()) {
                throw new ParseException("JSONが不完全です");
            }
            return text.charAt(pos++);
        }

        private void expect(char expected) throws ParseException {
            skipWhitespace();
            char actual = next();
            if (actual != expected) {
                throw new ParseException("'" + expected + "'が必要です（位置: " + (pos - 1) + "）");
            }
        }
    }
}
