package dev.ribica.oneblockplugin.util;

// Taken from https://github.com/python/cpython/blob/main/Lib/bisect.py
public class Bisect {
    public static int bisectRight(long[] arr, long x) {
        return bisectRight(arr, x, 0, arr.length);
    }

    public static int bisectRight(long[] arr, long x, int lo, int hi) {
        if (lo < 0)
            throw new IllegalArgumentException("lo must be non-negative");
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (x < arr[mid]) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }
}
