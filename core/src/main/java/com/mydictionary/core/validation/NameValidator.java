package com.mydictionary.core.validation;

import java.util.regex.Pattern;

/**
 * ブックタイトル・タグ名・シェルフ名などの文字種チェック。
 * 許可文字: 半角/全角英数字、平仮名、カタカナ、漢字、記号(-.+/*_!?()[])、全角記号(・「」『』〜など)、
 * 丸数字/丸囲み文字(①〜⑳、Ⓐ〜Ⓩなど、2026-09追加)、半角スペース
 */
public final class NameValidator {

    private static final Pattern ALLOWED = Pattern.compile(
        "^[0-9a-zA-Zぁ-ん\\u30A0-\\u30FF\\u4E00-\\u9FFF\\u3001-\\u303F"
            + "\\uFF10-\\uFF19\\uFF21-\\uFF3A\\uFF41-\\uFF5A\\u2460-\\u24FF\\-.+/*_!?()\\[\\] ]+$"
    );

    private NameValidator() {
    }

    public static boolean isValid(String name) {
        return name != null && !name.isEmpty() && ALLOWED.matcher(name).matches();
    }
}
