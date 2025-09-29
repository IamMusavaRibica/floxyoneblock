package dev.ribica.oneblockplugin.items.impl;

import dev.ribica.oneblockplugin.items.RawItem;
import dev.ribica.oneblockplugin.stats.Stat;
import dev.ribica.oneblockplugin.stats.calculation.InventorySlot;
import dev.ribica.oneblockplugin.stats.calculation.ItemStatProvider;
import dev.ribica.oneblockplugin.stats.calculation.StatAccumulator;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class NethemeraldSword extends RawItem implements ItemStatProvider {
    public NethemeraldSword() {
        super("nethemerald_sword", Key.key("minecraft", "netherite_sword"),
                Component.text("Nethemerald Sword", NamedTextColor.DARK_PURPLE));
    }

    @Override
    public void contribute(Player player, ItemStack itemStack, InventorySlot slot, StatAccumulator accumulator) {
        if (slot == InventorySlot.MAIN_HAND) {
            accumulator.add(Stat.STRENGTH, 125);
        }
    }
}