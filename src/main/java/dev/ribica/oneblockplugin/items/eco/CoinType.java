package dev.ribica.oneblockplugin.items.eco;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.key.Key;

@RequiredArgsConstructor
@Getter
public enum CoinType {
    BRONZE("bronze_coin", Key.key("minecraft:copper_ingot"), null),
    SILVER("silver_coin", Key.key("minecraft:iron_ingot"), BRONZE),
    GOLD("gold_coin", Key.key("minecraft:gold_ingot"), SILVER),
    PLATINUM("platinum_coin", Key.key("minecraft:netherite_ingot"), GOLD);

    private final String id;
    private final Key itemModel;
    private final CoinType lowerTier;

    public CoinType getHigherTier() {
        for (CoinType type : values()) {
            if (type.getLowerTier() == this) {
                return type;
            }
        }
        return null;
    }
}
