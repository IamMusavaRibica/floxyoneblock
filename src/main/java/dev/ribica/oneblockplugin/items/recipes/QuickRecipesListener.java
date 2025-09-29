package dev.ribica.oneblockplugin.items.recipes;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.items.ItemRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;

public final class QuickRecipesListener implements Listener {
    private final OneBlockPlugin plugin;
    private final QuickRecipeBook book;
    private final ItemRegistry reg;

    public QuickRecipesListener(OneBlockPlugin plugin, QuickRecipeBook book, ItemRegistry itemRegistry) {
        this.plugin = plugin;
        this.book = book;
        this.reg = itemRegistry;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() instanceof CraftingInventory inv) {
            int slot = event.getSlot();
            int rawSlot = event.getRawSlot();
            var slotType = event.getSlotType();
            plugin.getLogger().info("InventoryClickEvent slot=" + slot + " rawSlot=" + rawSlot + " type=" + slotType);
//            plugin.getLogger().info("Inventory: " + inv);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepare(PrepareItemCraftEvent e) {
        var inv = e.getInventory();
        StringBuilder s = new StringBuilder();
        for (var is : inv.getMatrix()) {
            s.append(is).append(", ");
        }
        plugin.getLogger().info("PrepareItemCraftEvent " + s);

        book.firstMatch(inv, reg, true).ifPresentOrElse(
                r -> inv.setResult(r.createResult()),
                () -> {}   // () -> inv.setResult(null)
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        var inv = e.getInventory();

        plugin.getLogger().info("CraftItemEvent");
        book.firstMatch(inv, reg, true).ifPresent(r -> {
            // if (e.isShiftClick())
//            r.chargeExtra(inv);
            var cur = e.getCurrentItem();
            if (cur != null) {
                r.callOnCraft(p, cur);
            } else {
                plugin.getLogger().warning("current item is null");
            }
        });
     }
}
