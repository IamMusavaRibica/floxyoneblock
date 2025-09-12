package dev.ribica.oneblockplugin.items.eco;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;


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

        Map<CoinType, Integer> coinCounts = new EnumMap<>(CoinType.class);
        for (var type : CoinType.values()) {
            coinCounts.put(type, 0);
        }

        // count all coins in inventory and remove them
        var inventory = player.getInventory();
        for (int s = 0; s < inventory.getSize(); s++) {
            int slot = s;
            var itemStack = inventory.getItem(slot);
            this.asCoin(itemStack).ifPresent(item -> {
                assert itemStack != null;  // ensured by ItemRegistry#fromItemStack
                var coinType = item.getCoinType();
                coinCounts.put(coinType, coinCounts.get(coinType) + itemStack.getAmount());
                inventory.setItem(slot, null);
            });
        }

        // convert coins to higher denominations
        for (var type : CoinType.values()) {
            int count = coinCounts.get(type);
            var higher = type.getHigherTier();
            int rate = 100;   // we could add type.getConversionRate()
            if (count >= 100 && higher != null) {
                int conversions = count / 100;
                int remainder = count % 100;

                coinCounts.put(type, remainder);
                coinCounts.put(higher, coinCounts.get(higher) + conversions);
            }
        }

        // add back to inventory
        for (var type : CoinType.values()) {
            int count = coinCounts.get(type);
            assert count >= 0;

            var coinItem = (Coin) plugin.getItemRegistry().byId(type.getId());
            int MAX_STACK_SIZE = coinItem.getMaxStackSize();

            while (count > MAX_STACK_SIZE) {
                ItemStack stack = coinItem.newItemStack();
                stack.setAmount(MAX_STACK_SIZE);
                inventory.addItem(stack);
                count -= MAX_STACK_SIZE;
            }

            if (count > 0) {
                ItemStack stack = coinItem.newItemStack();
                stack.setAmount(count);
                inventory.addItem(stack);
            }
        }

        long end = System.currentTimeMillis();
        plugin.getLogger().info("Converted coins for player " + player.getName() + " in " + (end - start) + " ms");
    }
}
