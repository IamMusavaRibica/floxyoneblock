package dev.ribica.oneblockplugin.items;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import net.minecraft.nbt.StringTag;
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
        var item = event.getItem();
        if (item == null) return;

        @SuppressWarnings("deprecation")
        var tag = Helpers.getNmsCustomDataComponent(item).getUnsafe().get("id");
        if (!(tag instanceof StringTag(String value)))
            return;

        // Get the corresponding RawItem from the registry
        var rawItem = plugin.getItemRegistry().byId(value);
        if (rawItem == null) {
            // TODO: add custom behavior for items with the "id" tag but not registered??
            // This can be used for commodity items which don't have any special
            // functionality besides just existing
            return;
        }

        rawItem.onInteract(event, event.getPlayer(), item);
    }
}
