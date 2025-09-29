package dev.ribica.oneblockplugin.stats.calculation;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface ItemStatProvider {
    void contribute(Player player, ItemStack itemStack, InventorySlot slot, StatAccumulator accumulator);
}
