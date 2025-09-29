package dev.ribica.oneblockplugin.stats;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Stat {
    STRENGTH(5, 10000),
    DEFENSE(5, 10000),
    SPEED(100, 400),   // vanilla attribute: default 0.1, range 0-1024
    MINING_FORTUNE(0, 10000),
    FARMING_FORTUNE(0, 10000),
    ;

    private final double base;
    private final double cap;

    public double clamp(double v) {
        return v < 0 ? 0 : Math.min(v, cap);
    }
}
