package com.mydictionary.core.markdown;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 書式設定ツールバー（太字・文字色・配置・リスト等）が、ノート本文のMarkdown風ソースを書き換えるための
 * 純粋な文字列変換ロジック。UI（デスクトップのTextArea・AndroidのEditText）には依存せず、
 * 「現在の本文と選択範囲」を受け取って「新しい本文と選択範囲」を返すだけなので、両OSで共通利用でき、
 * 単体テストもできる。
 */
public final class MarkdownEditing {

    /** 変換後の本文と、その上での選択範囲（start==endならカーソル位置）。 */
    public record Result(String text, int selectionStart, int selectionEnd) {
    }

    private record Edit(int start, int end, String replacement) {
        int delta() {
            return replacement.length() - (end - start);
        }
    }

    private static final Pattern SPAN_WHOLE =
        Pattern.compile("^<span\\s+style=\"([^\"<>]*)\">([\\s\\S]*)</span>$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPAN_OPEN_AT_END =
        Pattern.compile("<span\\s+style=\"([^\"<>]*)\">$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RUBY_WHOLE =
        Pattern.compile("^<ruby>([\\s\\S]*?)<rt>([\\s\\S]*?)</rt></ruby>$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALIGN_DIRECTIVE =
        Pattern.compile("^\\{:\\s*align=\"(left|center|right)\"\\s*\\}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADING_LINE = Pattern.compile("^#{1,6}\\s+.+$");
    private static final Pattern ORDERED_PREFIX = Pattern.compile("^\\d+\\.\\s+");
    private static final Pattern HEADING_PREFIX = Pattern.compile("^(#{1,6})\\s+");
    private static final String SPAN_CLOSE = "</span>";

    private MarkdownEditing() {
    }

    // ---------------------------------------------------------------- インライン書式

    /**
     * 選択範囲の各行を、markerで囲む（既に囲まれていれば外す）。太字("**")・斜体("*")・下線("__")・
     * 打ち消し線("~~")・インラインコード("`")に使う。選択が無いときはmarkerを2つ挿入しカーソルを間に置く。
     * Markdownのインライン記法は行をまたげず、前後の空白があると効かないため、行ごと・空白を除いた範囲に適用する。
     */
    public static Result toggleWrap(String text, int start, int end, String marker) {
        int s = clamp(Math.min(start, end), text);
        int e = clamp(Math.max(start, end), text);
        if (s == e) {
            String inserted = marker + marker;
            return new Result(text.substring(0, s) + inserted + text.substring(s),
                s + marker.length(), s + marker.length());
        }
        int m = marker.length();
        List<Edit> edits = new ArrayList<>();
        for (int[] seg : lineSegments(text, s, e)) {
            int a = seg[0];
            int b = seg[1];
            String inner = text.substring(a, b);
            if (isWrappedInside(inner, marker)) {
                edits.add(new Edit(a, b, inner.substring(m, inner.length() - m)));
            } else if (isWrappedOutside(text, a, b, marker)) {
                edits.add(new Edit(a - m, b + m, inner));
            } else {
                edits.add(new Edit(a, b, marker + inner + marker));
            }
        }
        return applyEdits(text, edits, s, e);
    }

    private static boolean isWrappedInside(String inner, String marker) {
        int m = marker.length();
        if (inner.length() <= 2 * m || !inner.startsWith(marker) || !inner.endsWith(marker)) {
            return false;
        }
        if (marker.charAt(0) != '*') {
            // 「__a__ と __b__」のように、先頭と末尾が別々の囲みの端なだけの場合は「囲まれている」と見なさない。
            return !inner.substring(m, inner.length() - m).contains(marker);
        }
        return starRunMatches(marker, leadingRun(inner, '*'), trailingRun(inner, '*'));
    }

    private static boolean isWrappedOutside(String text, int a, int b, String marker) {
        int m = marker.length();
        if (a < m || b + m > text.length()
            || !text.startsWith(marker, a - m) || !text.startsWith(marker, b)) {
            return false;
        }
        if (marker.charAt(0) != '*') {
            return true;
        }
        int before = 0;
        for (int i = a - 1; i >= 0 && text.charAt(i) == '*'; i--) {
            before++;
        }
        int after = 0;
        for (int i = b; i < text.length() && text.charAt(i) == '*'; i++) {
            after++;
        }
        return starRunMatches(marker, before, after);
    }

    /** '*'の連続数から、斜体(*)・太字(**)のどちらの囲みかを区別する（***は両方を含む）。 */
    private static boolean starRunMatches(String marker, int leading, int trailing) {
        if (marker.length() == 1) {
            return (leading == 1 || leading == 3) && (trailing == 1 || trailing == 3);
        }
        return leading >= 2 && trailing >= 2;
    }

    private static int leadingRun(String s, char c) {
        int n = 0;
        while (n < s.length() && s.charAt(n) == c) {
            n++;
        }
        return n;
    }

    private static int trailingRun(String s, char c) {
        int n = 0;
        while (n < s.length() && s.charAt(s.length() - 1 - n) == c) {
            n++;
        }
        return n;
    }

    /**
     * 選択範囲の各行を{@code <span style="property:value">}で囲む。既に同じ範囲がspanで囲まれていれば
     * そのstyleのpropertyだけを書き換え、valueがnullならそのpropertyを外す（styleが空になればspanごと外す）。
     * 文字色(color)・文字背景色(background-color)・フォント(font-family)・文字サイズ(font-size)に使う。
     */
    public static Result setSpanStyle(String text, int start, int end, String property, String value) {
        int s = clamp(Math.min(start, end), text);
        int e = clamp(Math.max(start, end), text);
        if (s == e) {
            if (value == null) {
                return new Result(text, s, e);
            }
            String open = "<span style=\"" + property + ":" + value + "\">";
            String inserted = open + SPAN_CLOSE;
            return new Result(text.substring(0, s) + inserted + text.substring(s),
                s + open.length(), s + open.length());
        }
        List<Edit> edits = new ArrayList<>();
        for (int[] seg : lineSegments(text, s, e)) {
            int a = seg[0];
            int b = seg[1];
            String inner = text.substring(a, b);

            Matcher whole = SPAN_WHOLE.matcher(inner);
            if (whole.matches() && spanClosesAtEnd(inner)) {
                edits.add(new Edit(a, b, rebuildSpan(whole.group(1), property, value, whole.group(2))));
                continue;
            }
            Matcher openBefore = SPAN_OPEN_AT_END.matcher(text.substring(0, a));
            if (openBefore.find() && text.startsWith(SPAN_CLOSE, b)) {
                int openStart = a - (openBefore.end() - openBefore.start());
                edits.add(new Edit(openStart, b + SPAN_CLOSE.length(),
                    rebuildSpan(openBefore.group(1), property, value, inner)));
                continue;
            }
            if (value != null) {
                edits.add(new Edit(a, b,
                    "<span style=\"" + property + ":" + value + "\">" + inner + SPAN_CLOSE));
            }
        }
        if (edits.isEmpty()) {
            return new Result(text, s, e);
        }
        return applyEdits(text, edits, s, e);
    }

    /** 先頭の&lt;span&gt;が、文字列末尾の&lt;/span&gt;で閉じられる（＝途中で閉じて別のspanが続くのではない）か。 */
    private static boolean spanClosesAtEnd(String s) {
        Matcher tags = Pattern.compile("(?i)<span\\s+style=\"[^\"<>]*\">|</span>").matcher(s);
        int depth = 0;
        while (tags.find()) {
            depth += tags.group().startsWith("</") ? -1 : 1;
            if (depth == 0) {
                return tags.end() == s.length();
            }
        }
        return false;
    }

    private static String rebuildSpan(String oldStyle, String property, String value, String content) {
        List<String> declarations = new ArrayList<>();
        for (String decl : oldStyle.split(";")) {
            String trimmed = decl.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            String name = colon < 0 ? trimmed : trimmed.substring(0, colon).trim();
            if (!name.equalsIgnoreCase(property)) {
                declarations.add(trimmed);
            }
        }
        if (value != null) {
            declarations.add(property + ":" + value);
        }
        if (declarations.isEmpty()) {
            return content;
        }
        return "<span style=\"" + String.join(";", declarations) + "\">" + content + SPAN_CLOSE;
    }

    /**
     * 選択した文字列にルビ（ふりがな）を付ける。既にルビが付いた範囲なら読みを差し替え、
     * readingが空ならルビを外す。選択が無いときは何もしない。
     */
    public static Result applyRuby(String text, int start, int end, String reading) {
        int s = clamp(Math.min(start, end), text);
        int e = clamp(Math.max(start, end), text);
        int[] range = trimRange(text, s, e);
        if (range == null) {
            return new Result(text, s, e);
        }
        String inner = text.substring(range[0], range[1]);
        String cleanReading = reading == null ? "" : reading.replace('\n', ' ').trim();

        String replacement;
        Matcher existing = RUBY_WHOLE.matcher(inner);
        if (existing.matches()) {
            replacement = cleanReading.isEmpty() ? existing.group(1)
                : "<ruby>" + existing.group(1) + "<rt>" + cleanReading + "</rt></ruby>";
        } else if (cleanReading.isEmpty()) {
            return new Result(text, s, e);
        } else {
            replacement = "<ruby>" + inner + "<rt>" + cleanReading + "</rt></ruby>";
        }
        return applyEdits(text, List.of(new Edit(range[0], range[1], replacement)), s, e);
    }

    // ---------------------------------------------------------------- ブロック書式

    private enum Kind { BLANK, CODE, FENCE, DIRECTIVE, TABLE, HR, HEADING, QUOTE, LIST, PLAIN }

    /**
     * 選択範囲（無ければカーソル位置）のブロックの配置を変える。レンダラーの
     * {@code {: align="center"}}形式の指定行をブロックの直前に挿入/差し替えし、"left"のときは指定行を削除する。
     * 通常の段落は複数行でひとまとまり、テーブルも全行でひとまとまりとして、その直前に1回だけ指定する。
     */
    public static Result setAlignment(String text, int start, int end, String align) {
        int s = clamp(Math.min(start, end), text);
        int e = clamp(Math.max(start, end), text);
        List<int[]> lines = lineRanges(text);
        Kind[] kinds = classify(text, lines);

        int first = lineIndexOf(lines, s);
        int last = lineIndexOf(lines, e > s ? e - 1 : e);
        while (first > 0 && isBlockContinuation(kinds[first], kinds[first - 1])) {
            first--;
        }
        while (last < lines.size() - 1 && isBlockContinuation(kinds[last + 1], kinds[last])) {
            last++;
        }

        String directive = "{: align=\"" + align + "\"}";
        boolean remove = "left".equals(align);
        List<Edit> edits = new ArrayList<>();
        for (int i = first; i <= last; i++) {
            Kind kind = kinds[i];
            if (kind != Kind.PLAIN && kind != Kind.TABLE && kind != Kind.HEADING
                && kind != Kind.QUOTE && kind != Kind.LIST) {
                continue;
            }
            if (i > first && isBlockContinuation(kind, kinds[i - 1])) {
                continue;
            }
            int lineStart = lines.get(i)[0];
            if (i > 0 && kinds[i - 1] == Kind.DIRECTIVE) {
                int dirStart = lines.get(i - 1)[0];
                if (remove) {
                    edits.add(new Edit(dirStart, lineStart, ""));
                } else {
                    edits.add(new Edit(dirStart, lines.get(i - 1)[1], directive));
                }
            } else if (!remove) {
                edits.add(new Edit(lineStart, lineStart, directive + "\n"));
            }
        }
        if (edits.isEmpty()) {
            return new Result(text, s, e);
        }
        return applyEdits(text, edits, s, e);
    }

    /** 同じ段落（通常行どうし）・同じテーブル（テーブル行どうし）の続きの行か。 */
    private static boolean isBlockContinuation(Kind current, Kind previous) {
        return (current == Kind.PLAIN && previous == Kind.PLAIN)
            || (current == Kind.TABLE && previous == Kind.TABLE);
    }

    /** 選択範囲（無ければカーソル行）の各行頭に「- 」を付ける（全行が既に付いていれば外す）。 */
    public static Result toggleBulletList(String text, int start, int end) {
        return toggleLinePrefix(text, start, end, false);
    }

    /** 選択範囲（無ければカーソル行）の各行頭に「1. 」「2. 」…を付ける（全行が既に付いていれば外す）。 */
    public static Result toggleNumberedList(String text, int start, int end) {
        return toggleLinePrefix(text, start, end, true);
    }

    private static Result toggleLinePrefix(String text, int start, int end, boolean ordered) {
        int s = clamp(Math.min(start, end), text);
        int e = clamp(Math.max(start, end), text);
        List<int[]> lines = lineRanges(text);
        Kind[] kinds = classify(text, lines);
        int first = lineIndexOf(lines, s);
        int last = lineIndexOf(lines, e > s ? e - 1 : e);

        List<Integer> targets = new ArrayList<>();
        for (int i = first; i <= last; i++) {
            if (kinds[i] != Kind.BLANK && kinds[i] != Kind.DIRECTIVE && kinds[i] != Kind.FENCE
                && kinds[i] != Kind.CODE) {
                targets.add(i);
            }
        }
        if (targets.isEmpty()) {
            return new Result(text, s, e);
        }

        boolean allHave = true;
        for (int i : targets) {
            String content = lineContent(text, lines.get(i));
            String body = content.stripLeading();
            boolean has = ordered ? ORDERED_PREFIX.matcher(body).find()
                : (body.startsWith("- ") || body.startsWith("* ")) && !isCheckbox(body);
            if (!has) {
                allHave = false;
                break;
            }
        }

        List<Edit> edits = new ArrayList<>();
        int number = 1;
        for (int i : targets) {
            int lineStart = lines.get(i)[0];
            String content = lineContent(text, lines.get(i));
            int indent = content.length() - content.stripLeading().length();
            String body = content.substring(indent);
            int oldPrefix = existingListPrefixLength(body);
            String newPrefix = allHave ? "" : (ordered ? (number++) + ". " : "- ");
            edits.add(new Edit(lineStart + indent, lineStart + indent + oldPrefix, newPrefix));
        }
        return applyEdits(text, edits, s, e);
    }

    private static boolean isCheckbox(String body) {
        return body.startsWith("- [ ] ") || body.startsWith("- [x] ");
    }

    private static int existingListPrefixLength(String body) {
        if (body.startsWith("- [ ] ") || body.startsWith("- [x] ")) {
            return 6;
        }
        if (body.startsWith("- ") || body.startsWith("* ")) {
            return 2;
        }
        Matcher ordered = ORDERED_PREFIX.matcher(body);
        return ordered.find() ? ordered.end() : 0;
    }

    /**
     * 選択範囲（無ければカーソル行）の各行を、見出し(level=1〜6、行頭の「#」の数)にする。
     * 全ての行が既に同じレベルの見出しなら通常の行へ戻す（トグル）。level=0なら見出しを解除する。
     * 見出しは行単位の記法なので、行頭の箇条書き・引用・別レベルの見出しの印は置き換える。
     */
    public static Result setHeading(String text, int start, int end, int level) {
        int s = clamp(Math.min(start, end), text);
        int e = clamp(Math.max(start, end), text);
        List<int[]> lines = lineRanges(text);
        Kind[] kinds = classify(text, lines);
        int first = lineIndexOf(lines, s);
        int last = lineIndexOf(lines, e > s ? e - 1 : e);

        List<Integer> targets = new ArrayList<>();
        boolean allAlreadyThisLevel = level > 0;
        for (int i = first; i <= last; i++) {
            Kind kind = kinds[i];
            if (kind == Kind.BLANK || kind == Kind.DIRECTIVE || kind == Kind.FENCE || kind == Kind.CODE
                || kind == Kind.TABLE || kind == Kind.HR) {
                continue;
            }
            targets.add(i);
            String body = lineContent(text, lines.get(i)).stripLeading();
            if (headingLevel(body) != level) {
                allAlreadyThisLevel = false;
            }
        }
        if (targets.isEmpty()) {
            return new Result(text, s, e);
        }

        String newPrefix = (level <= 0 || allAlreadyThisLevel) ? "" : "#".repeat(level) + " ";
        List<Edit> edits = new ArrayList<>();
        for (int i : targets) {
            int lineStart = lines.get(i)[0];
            String content = lineContent(text, lines.get(i));
            int indent = content.length() - content.stripLeading().length();
            String body = content.substring(indent);
            edits.add(new Edit(lineStart + indent, lineStart + indent + blockPrefixLength(body), newPrefix));
        }
        return applyEdits(text, edits, s, e);
    }

    /** 行頭の「#」の数（見出しレベル）。見出しでなければ0。 */
    private static int headingLevel(String body) {
        Matcher m = HEADING_PREFIX.matcher(body);
        return m.find() ? m.group(1).length() : 0;
    }

    /** 見出し・引用・箇条書きなど、行頭にあるブロックの印の長さ。 */
    private static int blockPrefixLength(String body) {
        Matcher heading = HEADING_PREFIX.matcher(body);
        if (heading.find()) {
            return heading.end();
        }
        if (body.startsWith("> ")) {
            return 2;
        }
        return existingListPrefixLength(body);
    }

    /** 選択範囲（無ければカーソル行）の各行頭に「&gt; 」を付ける（全行が既に付いていれば外す）。 */
    public static Result toggleQuote(String text, int start, int end) {
        int s = clamp(Math.min(start, end), text);
        int e = clamp(Math.max(start, end), text);
        List<int[]> lines = lineRanges(text);
        Kind[] kinds = classify(text, lines);
        int first = lineIndexOf(lines, s);
        int last = lineIndexOf(lines, e > s ? e - 1 : e);

        List<Integer> targets = new ArrayList<>();
        boolean allHave = true;
        for (int i = first; i <= last; i++) {
            if (kinds[i] == Kind.BLANK || kinds[i] == Kind.DIRECTIVE || kinds[i] == Kind.FENCE
                || kinds[i] == Kind.CODE) {
                continue;
            }
            targets.add(i);
            if (!lineContent(text, lines.get(i)).stripLeading().startsWith("> ")) {
                allHave = false;
            }
        }
        if (targets.isEmpty()) {
            return new Result(text, s, e);
        }
        List<Edit> edits = new ArrayList<>();
        for (int i : targets) {
            int lineStart = lines.get(i)[0];
            String content = lineContent(text, lines.get(i));
            int indent = content.length() - content.stripLeading().length();
            if (allHave) {
                edits.add(new Edit(lineStart + indent, lineStart + indent + 2, ""));
            } else {
                edits.add(new Edit(lineStart + indent, lineStart + indent, "> "));
            }
        }
        return applyEdits(text, edits, s, e);
    }

    // ---------------------------------------------------------------- 挿入

    /** カーソル（選択があればその末尾）の位置に水平線「---」を単独行で挿入する。 */
    public static Result insertHorizontalRule(String text, int start, int end) {
        int pos = clamp(Math.max(start, end), text);
        String block = "---";
        String insertion = blockInsertion(text, pos, block);
        int caret = pos + insertion.length();
        return new Result(text.substring(0, pos) + insertion + text.substring(pos), caret, caret);
    }

    /**
     * 選択範囲をコードブロック(```)で囲む。選択が無ければ空のコードブロックを挿入し、カーソルを中に置く。
     */
    public static Result wrapCodeBlock(String text, int start, int end) {
        int s = clamp(Math.min(start, end), text);
        int e = clamp(Math.max(start, end), text);
        if (s != e && text.charAt(e - 1) == '\n') {
            e--;
        }
        String selected = text.substring(s, e);
        String prefix = (s == 0 || text.charAt(s - 1) == '\n') ? "" : "\n";
        String suffix = (e >= text.length() || text.charAt(e) == '\n') ? "" : "\n";
        String replacement = prefix + "```\n" + selected + "\n```" + suffix;
        int innerStart = s + prefix.length() + 4;
        return new Result(text.substring(0, s) + replacement + text.substring(e),
            innerStart, innerStart + selected.length());
    }

    /**
     * カーソル位置にMarkdownのテーブルを挿入する。rowsは見出し行を含む総行数、colsは列数。
     * カーソルは最初のセルに置く。
     */
    public static Result insertTable(String text, int start, int end, int rows, int cols) {
        int r = Math.max(1, rows);
        int c = Math.max(1, cols);
        int pos = clamp(Math.max(start, end), text);

        String cell = "   ";
        String rowText = "|" + (cell + "|").repeat(c);
        String separator = "|" + "---|".repeat(c);
        StringBuilder block = new StringBuilder(rowText).append('\n').append(separator);
        for (int i = 1; i < r; i++) {
            block.append('\n').append(rowText);
        }
        String insertion = blockInsertion(text, pos, block.toString());
        int prefixLength = insertion.indexOf('|');
        int caret = pos + prefixLength + 2;
        return new Result(text.substring(0, pos) + insertion + text.substring(pos), caret, caret);
    }

    /** ブロック（水平線・テーブル）を、前後の行と混ざらないよう必要なら改行を足して単独行に挿入する文字列。 */
    private static String blockInsertion(String text, int pos, String block) {
        String prefix = (pos == 0 || text.charAt(pos - 1) == '\n') ? "" : "\n";
        String suffix = (pos < text.length() && text.charAt(pos) != '\n') ? "\n" : "";
        return prefix + block + "\n" + suffix;
    }

    // ---------------------------------------------------------------- 共通の下請け

    private static int clamp(int pos, String text) {
        return Math.max(0, Math.min(pos, text.length()));
    }

    private static boolean isBlank(char c) {
        return c == ' ' || c == '\t' || c == '　' || c == '\r';
    }

    /** 範囲の前後の空白を除いた範囲。空白だけならnull。 */
    private static int[] trimRange(String text, int s, int e) {
        int a = s;
        int b = e;
        while (a < b && (isBlank(text.charAt(a)) || text.charAt(a) == '\n')) {
            a++;
        }
        while (b > a && (isBlank(text.charAt(b - 1)) || text.charAt(b - 1) == '\n')) {
            b--;
        }
        return a >= b ? null : new int[]{a, b};
    }

    /** 選択範囲を行ごとに分け、各行の前後の空白を除いた範囲 [start, end) の一覧を返す（空行は除く）。 */
    private static List<int[]> lineSegments(String text, int s, int e) {
        List<int[]> segments = new ArrayList<>();
        int pos = s;
        while (pos <= e) {
            int newline = text.indexOf('\n', pos);
            int lineEnd = (newline < 0 || newline > e) ? e : newline;
            int[] trimmed = trimRange(text, pos, lineEnd);
            if (trimmed != null) {
                segments.add(trimmed);
            }
            if (newline < 0 || newline >= e) {
                break;
            }
            pos = newline + 1;
        }
        return segments;
    }

    /** 各行の [開始オフセット, 改行を含まない終了オフセット] 。最終行が改行で終わる場合は末尾に空行を持つ。 */
    private static List<int[]> lineRanges(String text) {
        List<int[]> lines = new ArrayList<>();
        int pos = 0;
        while (true) {
            int newline = text.indexOf('\n', pos);
            if (newline < 0) {
                lines.add(new int[]{pos, text.length()});
                break;
            }
            lines.add(new int[]{pos, newline});
            pos = newline + 1;
        }
        return lines;
    }

    private static int lineIndexOf(List<int[]> lines, int offset) {
        for (int i = 0; i < lines.size(); i++) {
            if (offset <= lines.get(i)[1]) {
                return i;
            }
        }
        return lines.size() - 1;
    }

    private static String lineContent(String text, int[] range) {
        return text.substring(range[0], range[1]);
    }

    private static Kind[] classify(String text, List<int[]> lines) {
        Kind[] kinds = new Kind[lines.size()];
        boolean inFence = false;
        for (int i = 0; i < lines.size(); i++) {
            String t = lineContent(text, lines.get(i)).trim();
            if (t.startsWith("```")) {
                kinds[i] = Kind.FENCE;
                inFence = !inFence;
            } else if (inFence) {
                kinds[i] = Kind.CODE;
            } else if (t.isEmpty()) {
                kinds[i] = Kind.BLANK;
            } else if (ALIGN_DIRECTIVE.matcher(t).matches()) {
                kinds[i] = Kind.DIRECTIVE;
            } else if (t.startsWith("|")) {
                kinds[i] = Kind.TABLE;
            } else if (t.equals("---") || t.equals("***")) {
                kinds[i] = Kind.HR;
            } else if (HEADING_LINE.matcher(t).matches()) {
                kinds[i] = Kind.HEADING;
            } else if (t.startsWith("> ")) {
                kinds[i] = Kind.QUOTE;
            } else if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith(": ")
                || ORDERED_PREFIX.matcher(t).find()) {
                kinds[i] = Kind.LIST;
            } else {
                kinds[i] = Kind.PLAIN;
            }
        }
        return kinds;
    }

    /** 昇順・重複なしの置換を末尾から順に適用し、元の選択範囲を新しい本文上の位置へ写して返す。 */
    private static Result applyEdits(String text, List<Edit> edits, int selStart, int selEnd) {
        StringBuilder sb = new StringBuilder(text);
        for (int i = edits.size() - 1; i >= 0; i--) {
            Edit edit = edits.get(i);
            sb.replace(edit.start(), edit.end(), edit.replacement());
        }
        return new Result(sb.toString(), mapOffset(selStart, edits, false), mapOffset(selEnd, edits, true));
    }

    /**
     * 変換前のオフセットを変換後の位置へ写す。置換された範囲の内側にある場合は、選択の開始位置なら置換後文字列の
     * 先頭へ、終了位置なら末尾へ寄せる（選択が置換後の文字列全体を覆うようにするため）。
     */
    private static int mapOffset(int offset, List<Edit> edits, boolean isSelectionEnd) {
        int shift = 0;
        for (Edit edit : edits) {
            if (edit.end() <= offset) {
                shift += edit.delta();
            } else if (edit.start() < offset) {
                return edit.start() + shift + (isSelectionEnd ? edit.replacement().length() : 0);
            }
        }
        return offset + shift;
    }
}
