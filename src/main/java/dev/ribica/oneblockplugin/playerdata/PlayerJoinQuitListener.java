package dev.ribica.oneblockplugin.playerdata;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.islands.Island;
import dev.ribica.oneblockplugin.util.UUIDNamePair;
import io.papermc.paper.math.Rotation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Calendar;

public class PlayerJoinQuitListener implements Listener {
    private final OneBlockPlugin plugin;
    private final Logger logger;

    public PlayerJoinQuitListener(OneBlockPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    private void delayJoin() {
        // zadrzi igrace koji ulaze do sljedece pune tridesete sekunde
        Calendar now = Calendar.getInstance();
        Calendar nextTarget = Calendar.getInstance();

        int currentSecond = nextTarget.get(Calendar.SECOND);
        if (currentSecond < 30) {
            nextTarget.set(Calendar.SECOND, 30);
        } else {
            nextTarget.set(Calendar.SECOND, 0);
            nextTarget.add(Calendar.MINUTE, 1);
        }
        nextTarget.set(Calendar.MILLISECOND, 0);
        long sleepTime = nextTarget.getTimeInMillis() - now.getTimeInMillis();
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException ignored) {

        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preLogin(AsyncPlayerPreLoginEvent event) {
        if (plugin.isStopping()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_FULL,
                    Component.text("Server se restarta, probaj ponovno za minutu!"));
            return;
        }
        UUID uuid = event.getUniqueId();
        try {
            // delayJoin();

            // Load the user data from storage
            User user = plugin.getStorageProvider().loadUser(uuid);

            // The player field will be null here because the Player object doesn't exist yet during
            // AsyncPlayerPreLoginEvent. It will be set in the onJoin method
            assert user.getPlayer() == null;

            // Verify that the user was added to the UserManager
            plugin.getUserManager().addUser(user);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load player data for uuid=" + uuid, e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text(
                    "There was an error loading your data!", TextColor.color(0xff0000)
            ));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getUserManager().hasUser(player.getUniqueId())) {
            logger.log(Level.SEVERE, "Player " + player.getName() + " does not have a User object");
            player.kick(Component.text("An unexpected error prevented loading your data.", TextColor.color(0xdd0000)));
            return;
        }
        UUIDNamePair.of(player.getUniqueId()).getName();  // trigger a name update

        User user = plugin.getUser(player);
        user.setPlayer(player);
        user.updateStats();
        user.resumeQuests();
        user.updateQuests();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
//        logger.info("PLAYER QUIT EVENT: " + player.getName());
        // IMPORTANT: player.isOnline() STILL RETURNS TRUE HERE, BUT player.isConnected() RETURNS FALSE
        if (!plugin.getUserManager().hasUser(player.getUniqueId())) {
            logger.warning("Player " + player.getName() + " has quit but wasn't registered in UserManager");
            return;
        }
        User user = plugin.getUser(player);
        user.clearTemporaryEffects();
        player.setGameMode(GameMode.SURVIVAL);          // TODO!

        plugin.runTaskAsync(() -> {
            try {
                plugin.getStorageProvider().saveUser(user);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error saving user data for " + user.getUuid(), e);
            }
            user.getActiveIsland().unloadIfSafe();
            plugin.getUserManager().removeUser(player.getUniqueId());
        });

        // tick the island allocator to unload empty worlds
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getIslandAllocator2().checkAndUnloadEmptyWorlds();
        }, 20L);
    }

    @EventHandler
    public void onSpawn(PlayerSpawnLocationEvent event) {
        User user = plugin.getUser(event.getPlayer());
        Location target = user.getActiveIsland().getOrigin().add(0.5, 1.00123, 0.5);
        target.setRotation(Rotation.rotation(17.1f, 68.9f));
        // logger.info("Spawning " + event.getPlayer().getName() + " at " + target);
        event.setSpawnLocation(target);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        User user = plugin.getUser(event.getPlayer());
        Location target = user.getActiveIsland().getOrigin().add(0.5, 1.00123, 0.5);
        target.setRotation(Rotation.rotation(17.1f, 68.9f));
        event.setRespawnLocation(target);
    }
}


