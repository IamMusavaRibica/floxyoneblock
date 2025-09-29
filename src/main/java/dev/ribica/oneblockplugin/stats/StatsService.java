package dev.ribica.oneblockplugin.stats;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.items.ItemRegistry;
import dev.ribica.oneblockplugin.stats.calculation.InventorySlot;
import dev.ribica.oneblockplugin.stats.calculation.ItemStatProvider;
import dev.ribica.oneblockplugin.stats.calculation.StatAccumulator;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Map;

@RequiredArgsConstructor
public class StatsService {
    private final OneBlockPlugin plugin;
    private final ItemRegistry itemRegistry;
//    private final List<StatProvider> providers = new ArrayList<>();


    public Map<Stat, Double> getTotals(Player player) {
        // TODO: if this ends up being a performance issue, we could use nms directly instead of going through bukkit wrappers
        long __start = System.nanoTime();

        var acc = new StatAccumulator();

        // item stat providers
        var inventory = player.getInventory();

        includeItem(player, inventory.getItemInMainHand(), InventorySlot.MAIN_HAND, acc);
        includeItem(player, inventory.getItemInOffHand(), InventorySlot.OFF_HAND, acc);
        includeItem(player, inventory.getHelmet(), InventorySlot.HEAD, acc);
        includeItem(player, inventory.getChestplate(), InventorySlot.CHEST, acc);
        includeItem(player, inventory.getLeggings(), InventorySlot.LEGS, acc);
        includeItem(player, inventory.getBoots(), InventorySlot.FEET, acc);

        int heldItemSlot = inventory.getHeldItemSlot();
        var contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack is = contents[i];
            if (is != null && i != heldItemSlot && !is.getType().isAir()) {
                includeItem(player, is, InventorySlot.INVENTORY, acc);
            }
        }

        // TODO: add global stat providers here


        long __elapsed = System.nanoTime() - __start;
        plugin.getLogger().info("Stats computed in " + (__elapsed / 1_000_000.0) + " ms");

        return acc.computeAll();
    }

    private void includeItem(Player p, ItemStack is, InventorySlot slot, StatAccumulator acc) {
        itemRegistry.fromItemStack(is).ifPresent(item -> {
            if (item instanceof ItemStatProvider isp)
                isp.contribute(p, is, slot, acc);
        });
    }
}
