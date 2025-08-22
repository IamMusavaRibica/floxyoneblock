package dev.ribica.oneblockplugin.util;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static net.kyori.adventure.text.Component.text;

public class McTextUtils {
    private static final Logger log = Logger.getLogger(McTextUtils.class.getName());
    private static final byte W6 = 6, W5 = 5, W4 = 4, W2 = 2, W7 = 7, W3 = 3, W9 = 9;
    public static final Map<Character, Byte> WIDTHS;
    static {
        Map<Character, Byte> m = new HashMap<>();
        put(m, W2, 'i', '!', ',', '.', ';', ':');
        put(m, W3, 'l', '\'', '`');
        put(m, W4, 'I', 't', ' ', '[', ']');
        put(m, W5, 'f', 'k', '"', '(', ')', '*');
        put(m, W6, 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'g', 'h', 'j', 'm', 'n', 'o', 'p', 'q', 'r', 's', 'u', 'v', 'w', 'x', 'y', 'z', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '+', '#', '$', '%', '/', '=', '?', '-', '_', '^', '◆');
        put(m, W7, '~', '«', '»', '≡', '⏪', '⏩');
        // any remaining singles:
//        m.put('◆', W6);
        m.put('\u3000', W9);
        WIDTHS = Collections.unmodifiableMap(m);
    }

    private static void put(Map<Character, Byte> m, byte w, char... cs) {
        for (char c : cs) m.put(c, w);
    }

    public static int getWidth(@NonNull String text, boolean bold) {
        int width = 0;
        for (char c : text.toCharArray()) {
            Byte w = WIDTHS.get(c);
            if (w != null) {
                width += w;
            } else {
                log.warning("Don't know the width for: " + c);
                width += W6; // default width for unknown characters
            }
        }
        if (bold)
            width += text.length(); // each bold character adds 1 extra width
        return width;
    }

    public static Component center(String text, int width, boolean bold) {
        int textWidth = getWidth(text, bold);
        if (textWidth >= width) {
            return text(text).decoration(TextDecoration.BOLD, bold);
        }

        // 4m + 5n + textWidth + 4m + 5n = width
        // solve equation 4m + 5n = c
        int c = (width - textWidth) / 2;
        int m = 0, n = c/5;
        switch (c) {
            // these are all 'c' where no positive solution exists
            case 0, 1, 2, 3 -> {m = 0; n = 0;}
            case 6 -> {m = 0; n = 1;}
            case 7 -> {m = 2; n = 0;}
            case 11 -> {m = 0; n = 2;}
            default -> {
                while (n >= 0) {
                    int rem = c - 5 * n;
                    if (rem % 4 == 0) {
                        m = rem / 4;
                        break;
                    }
                    n--;
                }
                if (n < 0) {
                    log.severe("No solution found for c=" + c + ", using m=0, n=0");
                    m = 0; n = 0; // fallback
                }
            }
        }


        log.info("text length: " + text.length() + ", textWidth: " + textWidth + ", width: " + width +
                ", c: " + c + ", m: " + m + ", n: " + n);

        return text()
                .append(Component.text(" ".repeat(m)))
                .append(Component.text(" ".repeat(n), Style.style(TextDecoration.BOLD)))
                .append(Component.text(text).decoration(TextDecoration.BOLD, bold))
                .build();
    }
    // chat 320
    public static Component chat(String text, boolean bold) {
        return text()
                .append(Component.text(" "))
                .append(center(text, 316, bold))
                .build();
    }

    public static List<String> splitToRows(String string, int maxWidth) {
        String[] words = string.split(" ");
        StringBuilder currentRow = new StringBuilder();
        int currentRowWidth = 0;
        List<String> rows = new java.util.ArrayList<>();
        for (String word : words) {
            int wordWidth = getWidth(word, false);
            if (currentRowWidth + wordWidth >= maxWidth) {
                rows.add(currentRow.toString().trim());
                currentRow.setLength(0);
                currentRowWidth = 0;
            }
            currentRow.append(word).append(" ");
            currentRowWidth += wordWidth + 5;   // account for space
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow.toString().trim());
        }
        return rows;
    }
}