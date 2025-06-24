package dev.ribica.oneblockplugin.util;

public class TimeUtils {
    public static long ms() {
        return System.nanoTime() / 1_000_000;
    }

    public static long msSince(long startTime) {
        return ms() - startTime;
    }
}
