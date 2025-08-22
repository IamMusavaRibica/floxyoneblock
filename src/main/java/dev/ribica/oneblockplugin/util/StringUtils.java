package dev.ribica.oneblockplugin.util;

import lombok.NonNull;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

public class StringUtils {
    public static String UUIDtoString(@NonNull UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    public static List<String> splitWorldsIntoRows(String string, int maxWidth) {
        String[] words = string.split(" ");
        StringBuilder currentRow = new StringBuilder();
        List<String> rows = new java.util.ArrayList<>();
        for (String word : words) {
            if (currentRow.length() + word.length() + 1 > maxWidth) {
                rows.add(currentRow.toString().trim());
                currentRow.setLength(0);
            }
            currentRow.append(word).append(" ");
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow.toString().trim());
        }
        return rows;
    }
}
