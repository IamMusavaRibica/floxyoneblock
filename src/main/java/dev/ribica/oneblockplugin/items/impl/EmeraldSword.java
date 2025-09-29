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

public class EmeraldSword extends RawItem implements ItemStatProvider {
    public EmeraldSword() {
        super("emerald_sword", Key.key("minecraft", "diamond_sword"),
                Component.text("Emerald Sword", NamedTextColor.GREEN));
    }


    @Override
    public void contribute(Player player, ItemStack itemStack, InventorySlot slot, StatAccumulator accumulator) {
        if (slot == InventorySlot.MAIN_HAND) {
            accumulator.add(Stat.STRENGTH, 50);
        }
    }
}
