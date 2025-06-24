package dev.ribica.oneblockplugin.util;

import lombok.NonNull;

import java.util.UUID;

public class StringUtils {
    public static String UUIDtoString(@NonNull UUID uuid) {
        return uuid.toString().replace("-", "");
    }
}
