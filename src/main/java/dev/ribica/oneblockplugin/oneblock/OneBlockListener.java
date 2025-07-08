package dev.ribica.oneblockplugin.oneblock;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.islands.Island;
import dev.ribica.oneblockplugin.playerdata.User;
import dev.ribica.oneblockplugin.util.WorldUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.server.commands.StopCommand;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * Handles the OneBlock mine and regeneration mechanics
 */
public class OneBlockListener implements Listener {
    private final OneBlockPlugin plugin;
    private final Random random = new Random();

    public OneBlockListener(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    // ignoreCancelled because worldguard already cancelled some of them
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        User user = plugin.getUser(player);
        Block block = event.getBlock();
        Location blockLocation = block.getLocation();  // block location has whole numbers, not ##.5 decimals

        // Get the player's current island
        Island island = plugin.getUser(player).getActiveIsland();
        Island current = plugin.getUser(player).getCurrentIsland();
        if (island != current) {
            plugin.getLogger().warning(player.getName() + " broke a block on island " + island.getUuid() +
                    " but their current island is " + (current != null ? current.getUuid() : null) + "!");
            player.sendMessage(Component.text("Prebaci se na ovo ostrvo da kopaš tu! OVO NE SME NORMALNO DA SE DOGODI", TextColor.color(0xff7f00)));
            event.setCancelled(true);
            return;
        }

        Location originLocation = island.getOrigin();
        if (WorldUtils.compareBlockLocation(blockLocation, originLocation)) {
            event.setCancelled(true); // Cancel normal drops

            World world = block.getWorld();
            // TODO: ovaj kod mora biti u Island, ne ovdje

            // Get the drops from the broken block
            Material brokenType = block.getType();
            if (brokenType != Material.BEDROCK) {
                // Drop the items in the world above the block with randomized positions
                for (ItemStack drop : block.getDrops(player.getInventory().getItemInMainHand())) {
                    // x = x0 + rand(0.3, 0.7),  y = y0 + 1.56, z = z0 + rand(0.3, 0.7)
                    double randX = blockLocation.getX() + 0.3 + (random.nextDouble() * 0.4);
                    double randZ = blockLocation.getZ() + 0.3 + (random.nextDouble() * 0.4);
                    Location dropLocation = new Location(world, randX, blockLocation.getY() + 1.56, randZ);
                    world.dropItemNaturally(dropLocation, drop);
                }
            }

            // Record the mining statistics both for user and island
            user.trackBlockMined(brokenType);
            island.trackBlockMined(player.getUniqueId(), player.getName(), brokenType);

            Stage currentStage = island.getStage();
            Material newMaterial = BlockWeights.getRandomBlock(currentStage);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Set the new block
                block.setType(newMaterial);

                // Spawn cloud particles at the center of the block
                Location particleLocation = block.getLocation().add(0.5, 0.5, 0.5);
                world.spawnParticle(Particle.CLOUD, particleLocation, 5, 0.01, 0.01, 0.01, 0.1);
            });
        }
    }
}

