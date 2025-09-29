package dev.ribica.oneblockplugin.items.eco;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;


public class CoinConversionListener implements Listener {
    private final OneBlockPlugin plugin;

    public CoinConversionListener(OneBlockPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        plugin.runTaskLater(() -> this.convertCoins(player), 1L);
    }

    @EventHandler
    public void onHotbar(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        plugin.runTaskLater(() -> this.convertCoins(player), 1L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        plugin.runTaskLater(() -> this.convertCoins(player), 1L);
    }

    private Optional<Coin> asCoin(ItemStack itemStack) {
        var raw = plugin.getItemRegistry().fromItemStack(itemStack);
        if (raw.isPresent() && raw.get() instanceof Coin coin) {
            return Optional.of(coin);
        }
        return Optional.empty();

    }

    private void convertCoins(Player player) {
        long start = System.currentTimeMillis();

        Map<CoinType, List<CoinSlot>> coinSlotsByType = new EnumMap<>(CoinType.class);
        for (var type : CoinType.values()) {
            coinSlotsByType.put(type, new ArrayList<>());
        }

        // scan inventory to find all coin slots
        var inventory = player.getInventory();
        for (int s = 0; s < inventory.getSize(); s++) {
            int slot = s;
            var itemStack = inventory.getItem(s);
            asCoin(itemStack).ifPresent(coin -> {
                CoinType type = coin.getCoinType();
                //noinspection DataFlowIssue
                coinSlotsByType.get(type).add(new CoinSlot(slot, itemStack.getAmount()));
            });
        }

        // process conversions starting from the lowest tier
        // UNTESTED CODE!
        boolean madeConversion;
        do {
            madeConversion = false;

            for (CoinType type : CoinType.values()) {
                if (type.getHigherTier() == null) continue; // Skip highest tier

                CoinType higherType = type.getHigherTier();
                List<CoinSlot> slots = coinSlotsByType.get(type);

                // Count total coins of this type
                int totalCoins = slots.stream().mapToInt(CoinSlot::getAmount).sum();

                // Check if conversion is needed
                if (totalCoins >= 100) {
                    int conversions = totalCoins / 100;
                    int remainder = totalCoins % 100;

                    // Clear all slots of this type
                    for (CoinSlot slot : slots) {
                        inventory.setItem(slot.getSlot(), null);
                    }

                    // Add back the remainder coins to the first available slot
                    if (remainder > 0 && !slots.isEmpty()) {
                        Coin coin = (Coin) plugin.getItemRegistry().byId(type.getId());
                        ItemStack stack = coin.newItemStack();
                        stack.setAmount(remainder);
                        inventory.setItem(slots.getFirst().getSlot(), stack);

                        // Update the slot information
                        slots.getFirst().setAmount(remainder);
                        // Remove all other slots
                        if (slots.size() > 1) {
                            slots.subList(1, slots.size()).clear();
                        }
                    } else {
                        // No remainder, clear all slots
                        slots.clear();
                    }

                    // Handle the higher tier coins
                    List<CoinSlot> higherSlots = coinSlotsByType.get(higherType);
                    Coin higherCoin = (Coin) plugin.getItemRegistry().byId(higherType.getId());
                    int maxStackSize = higherCoin.getMaxStackSize();

                    // Try to add to existing higher tier slots first
                    for (CoinSlot higherSlot : new ArrayList<>(higherSlots)) {
                        int currentAmount = higherSlot.getAmount();
                        int spaceAvailable = maxStackSize - currentAmount;

                        if (spaceAvailable > 0) {
                            int toAdd = Math.min(conversions, spaceAvailable);
                            ItemStack stack = inventory.getItem(higherSlot.getSlot());
                            //noinspection DataFlowIssue
                            stack.setAmount(currentAmount + toAdd);
                            higherSlot.setAmount(currentAmount + toAdd);
                            conversions -= toAdd;

                            if (conversions == 0) break;
                        }
                    }

                    // If we still have conversions to add, create new stacks
                    while (conversions > 0) {
                        int amount = Math.min(conversions, maxStackSize);
                        ItemStack stack = higherCoin.newItemStack();
                        stack.setAmount(amount);

                        // Find first empty slot
                        int emptySlot = inventory.firstEmpty();
                        if (emptySlot != -1) {
                            inventory.setItem(emptySlot, stack);
                            higherSlots.add(new CoinSlot(emptySlot, amount));
                            conversions -= amount;
                        } else {
                            // Inventory is full, drop the item
                            player.getWorld().dropItem(player.getLocation(), stack);
                            conversions -= amount;
                        }
                    }

                    madeConversion = true;
                    player.sendMessage("§6Converted coins to " + higherType.getId().replace("_coin", "").substring(0, 1).toUpperCase() +
                            higherType.getId().replace("_coin", "").substring(1) + " Coins!");
                }
            }
        } while (madeConversion);

        long end = System.currentTimeMillis();
//        plugin.getLogger().info("Processed coins for player " + player.getName() + " in " + (end - start) + " ms");
    }

    @Getter @Setter
    @AllArgsConstructor
    private static class CoinSlot {
        private final int slot;
        private int amount;
    }
}
