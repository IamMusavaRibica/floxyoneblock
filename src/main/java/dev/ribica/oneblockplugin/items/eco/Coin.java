package dev.ribica.oneblockplugin.items.eco;

import dev.ribica.oneblockplugin.items.RawItem;
import lombok.Getter;

@Getter
public class Coin extends RawItem {
    private final CoinType coinType;
    private final int maxStackSize = 99;

    public Coin(CoinType coinType) {
        super(coinType.getId(), coinType.getItemModel());
        this.coinType = coinType;
    }
}