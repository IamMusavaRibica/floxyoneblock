package dev.ribica.oneblockplugin.islands;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class HubProtector implements Listener {
    private final OneBlockPlugin plugin;
    private final World hubWorld;

    public HubProtector(OneBlockPlugin plugin, World hubWorld) {
        this.plugin = plugin;
        this.hubWorld = hubWorld;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onSignChange(SignChangeEvent event) {
        var world = event.getBlock().getWorld();
        if (world.equals(hubWorld)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onInteract(PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        var world = block != null ? block.getWorld() : null;
        if (world != null && world.equals(hubWorld)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onBlockPlace(BlockPlaceEvent event) {
        var world = event.getBlock().getWorld();
        if (world.equals(hubWorld)) {
            event.setCancelled(true);
        }
    }
}
