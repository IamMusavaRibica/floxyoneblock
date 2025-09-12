package dev.ribica.oneblockplugin.items;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

public class ItemsEventListener implements Listener {
    private final OneBlockPlugin plugin;

    public ItemsEventListener(OneBlockPlugin plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        var itemStack = event.getItem();
        if (itemStack == null)  // null is when player steps on a pressure plate
            return;

        plugin.getItemRegistry().fromItemStack(itemStack).ifPresent(item -> {
            item.onInteract(event, event.getPlayer(), itemStack);
        });
    }
}
