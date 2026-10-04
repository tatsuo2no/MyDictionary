package com.mydictionary.core.markdown;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ブラウザ定義済みの色名（CSS3拡張色名からgrey綴りの別名7件を除いた140色）。
 * 文字色・文字背景色ピッカー（7行×20列）で、ノート本文の
 * {@code <span style="color:red">}のように色名のまま指定できるものを色相順に並べて提供する。
 */
public final class CssNamedColors {

    public record NamedColor(String name, String hex) {
        public int red() {
            return Integer.parseInt(hex.substring(1, 3), 16);
        }

        public int green() {
            return Integer.parseInt(hex.substring(3, 5), 16);
        }

        public int blue() {
            return Integer.parseInt(hex.substring(5, 7), 16);
        }
    }

    public static final int ROWS = 7;
    public static final int COLUMNS = 20;

    /** 彩度がこの値未満の色は「無彩色」として色相順の最後に明るさ順でまとめる。 */
    private static final double ACHROMATIC_SATURATION = 0.10;

    private static final double HUE_BAND_DEGREES = 15.0;

    private static final String[][] RAW = {
        {"aliceblue", "#F0F8FF"}, {"antiquewhite", "#FAEBD7"}, {"aqua", "#00FFFF"},
        {"aquamarine", "#7FFFD4"}, {"azure", "#F0FFFF"}, {"beige", "#F5F5DC"},
        {"bisque", "#FFE4C4"}, {"black", "#000000"}, {"blanchedalmond", "#FFEBCD"},
        {"blue", "#0000FF"}, {"blueviolet", "#8A2BE2"}, {"brown", "#A52A2A"},
        {"burlywood", "#DEB887"}, {"cadetblue", "#5F9EA0"}, {"chartreuse", "#7FFF00"},
        {"chocolate", "#D2691E"}, {"coral", "#FF7F50"}, {"cornflowerblue", "#6495ED"},
        {"cornsilk", "#FFF8DC"}, {"crimson", "#DC143C"}, {"cyan", "#00FFFF"},
        {"darkblue", "#00008B"}, {"darkcyan", "#008B8B"}, {"darkgoldenrod", "#B8860B"},
        {"darkgray", "#A9A9A9"}, {"darkgreen", "#006400"}, {"darkkhaki", "#BDB76B"},
        {"darkmagenta", "#8B008B"}, {"darkolivegreen", "#556B2F"}, {"darkorange", "#FF8C00"},
        {"darkorchid", "#9932CC"}, {"darkred", "#8B0000"}, {"darksalmon", "#E9967A"},
        {"darkseagreen", "#8FBC8F"}, {"darkslateblue", "#483D8B"}, {"darkslategray", "#2F4F4F"},
        {"darkturquoise", "#00CED1"}, {"darkviolet", "#9400D3"}, {"deeppink", "#FF1493"},
        {"deepskyblue", "#00BFFF"}, {"dimgray", "#696969"}, {"dodgerblue", "#1E90FF"},
        {"firebrick", "#B22222"}, {"floralwhite", "#FFFAF0"}, {"forestgreen", "#228B22"},
        {"fuchsia", "#FF00FF"}, {"gainsboro", "#DCDCDC"}, {"ghostwhite", "#F8F8FF"},
        {"gold", "#FFD700"}, {"goldenrod", "#DAA520"}, {"gray", "#808080"},
        {"green", "#008000"}, {"greenyellow", "#ADFF2F"}, {"honeydew", "#F0FFF0"},
        {"hotpink", "#FF69B4"}, {"indianred", "#CD5C5C"}, {"indigo", "#4B0082"},
        {"ivory", "#FFFFF0"}, {"khaki", "#F0E68C"}, {"lavender", "#E6E6FA"},
        {"lavenderblush", "#FFF0F5"}, {"lawngreen", "#7CFC00"}, {"lemonchiffon", "#FFFACD"},
        {"lightblue", "#ADD8E6"}, {"lightcoral", "#F08080"}, {"lightcyan", "#E0FFFF"},
        {"lightgoldenrodyellow", "#FAFAD2"}, {"lightgray", "#D3D3D3"}, {"lightgreen", "#90EE90"},
        {"lightpink", "#FFB6C1"}, {"lightsalmon", "#FFA07A"}, {"lightseagreen", "#20B2AA"},
        {"lightskyblue", "#87CEFA"}, {"lightslategray", "#778899"}, {"lightsteelblue", "#B0C4DE"},
        {"lightyellow", "#FFFFE0"}, {"lime", "#00FF00"}, {"limegreen", "#32CD32"},
        {"linen", "#FAF0E6"}, {"magenta", "#FF00FF"}, {"maroon", "#800000"},
        {"mediumaquamarine", "#66CDAA"}, {"mediumblue", "#0000CD"}, {"mediumorchid", "#BA55D3"},
        {"mediumpurple", "#9370DB"}, {"mediumseagreen", "#3CB371"}, {"mediumslateblue", "#7B68EE"},
        {"mediumspringgreen", "#00FA9A"}, {"mediumturquoise", "#48D1CC"}, {"mediumvioletred", "#C71585"},
        {"midnightblue", "#191970"}, {"mintcream", "#F5FFFA"}, {"mistyrose", "#FFE4E1"},
        {"moccasin", "#FFE4B5"}, {"navajowhite", "#FFDEAD"}, {"navy", "#000080"},
        {"oldlace", "#FDF5E6"}, {"olive", "#808000"}, {"olivedrab", "#6B8E23"},
        {"orange", "#FFA500"}, {"orangered", "#FF4500"}, {"orchid", "#DA70D6"},
        {"palegoldenrod", "#EEE8AA"}, {"palegreen", "#98FB98"}, {"paleturquoise", "#AFEEEE"},
        {"palevioletred", "#DB7093"}, {"papayawhip", "#FFEFD5"}, {"peachpuff", "#FFDAB9"},
        {"peru", "#CD853F"}, {"pink", "#FFC0CB"}, {"plum", "#DDA0DD"},
        {"powderblue", "#B0E0E6"}, {"purple", "#800080"}, {"red", "#FF0000"},
        {"rosybrown", "#BC8F8F"}, {"royalblue", "#4169E1"}, {"saddlebrown", "#8B4513"},
        {"salmon", "#FA8072"}, {"sandybrown", "#F4A460"}, {"seagreen", "#2E8B57"},
        {"seashell", "#FFF5EE"}, {"sienna", "#A0522D"}, {"silver", "#C0C0C0"},
        {"skyblue", "#87CEEB"}, {"slateblue", "#6A5ACD"}, {"slategray", "#708090"},
        {"snow", "#FFFAFA"}, {"springgreen", "#00FF7F"}, {"steelblue", "#4682B4"},
        {"tan", "#D2B48C"}, {"teal", "#008080"}, {"thistle", "#D8BFD8"},
        {"tomato", "#FF6347"}, {"turquoise", "#40E0D0"}, {"violet", "#EE82EE"},
        {"wheat", "#F5DEB3"}, {"white", "#FFFFFF"}, {"whitesmoke", "#F5F5F5"},
        {"yellow", "#FFFF00"}, {"yellowgreen", "#9ACD32"},
    };

    private static final List<NamedColor> ORDERED = buildOrdered();

    private CssNamedColors() {
    }

    /** 色相順（赤→橙→黄→緑→青→紫→桃）に並べた140色。無彩色（白・灰・黒など）は最後に明るい順で並ぶ。 */
    public static List<NamedColor> all() {
        return ORDERED;
    }

    private static List<NamedColor> buildOrdered() {
        List<NamedColor> chromatic = new ArrayList<>();
        List<NamedColor> achromatic = new ArrayList<>();
        for (String[] entry : RAW) {
            NamedColor color = new NamedColor(entry[0], entry[1]);
            if (saturation(color) < ACHROMATIC_SATURATION) {
                achromatic.add(color);
            } else {
                chromatic.add(color);
            }
        }
        // 色相を15度ごとの帯にまとめ、帯の中では暗い→明るいの順に並べる。厳密な色相順だけだと、
        // 隣り合う色の明暗が行ごとにバラついて見づらいため。
        chromatic.sort(Comparator.comparingInt((NamedColor c) -> (int) (hue(c) / HUE_BAND_DEGREES))
            .thenComparingDouble(CssNamedColors::lightness).thenComparing(NamedColor::name));
        achromatic.sort(Comparator.comparingDouble(CssNamedColors::lightness).reversed()
            .thenComparing(NamedColor::name));
        List<NamedColor> result = new ArrayList<>(chromatic);
        result.addAll(achromatic);
        return List.copyOf(result);
    }

    private static double lightness(NamedColor c) {
        double max = Math.max(c.red(), Math.max(c.green(), c.blue())) / 255.0;
        double min = Math.min(c.red(), Math.min(c.green(), c.blue())) / 255.0;
        return (max + min) / 2.0;
    }

    /** HSVの彩度。HSLの彩度だと白に近い淡い色（雪色など）が高彩度扱いになり無彩色グループに入らないため。 */
    private static double saturation(NamedColor c) {
        double max = Math.max(c.red(), Math.max(c.green(), c.blue())) / 255.0;
        double min = Math.min(c.red(), Math.min(c.green(), c.blue())) / 255.0;
        return max == 0 ? 0 : (max - min) / max;
    }

    /** 0〜360度の色相。赤(0度)を起点に昇順に並べるため、桃色側が最後に来る。 */
    private static double hue(NamedColor c) {
        double r = c.red() / 255.0;
        double g = c.green() / 255.0;
        double b = c.blue() / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double delta = max - min;
        if (delta == 0) {
            return 0;
        }
        double h;
        if (max == r) {
            h = ((g - b) / delta) % 6;
        } else if (max == g) {
            h = (b - r) / delta + 2;
        } else {
            h = (r - g) / delta + 4;
        }
        h *= 60;
        return h < 0 ? h + 360 : h;
    }
}
