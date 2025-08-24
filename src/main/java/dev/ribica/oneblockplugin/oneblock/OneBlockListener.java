package dev.ribica.oneblockplugin.oneblock;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.islands.Island;
import dev.ribica.oneblockplugin.playerdata.User;
import dev.ribica.oneblockplugin.quests.types.QuestPartMineSourceBlock0;
import dev.ribica.oneblockplugin.util.WorldUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Random;

/**
 * Handles the OneBlock mine and regeneration mechanics
 */
public class OneBlockListener implements Listener {
    private final OneBlockPlugin plugin;

    public OneBlockListener(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    // ignoreCancelled because worldguard already cancelled some of them
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        User user = plugin.getUser(player);
        Block block = event.getBlock();

        @NotNull Island island = user.getActiveIsland();
        @Nullable Island current = user.getCurrentIsland();

        if (island != current) {
            if (current != null && current.members.hasMember(user.getUuid(), true)) {
                // player mined on island which is not his currently active island
                plugin.getLogger().warning(player.getName() + " broke a block on island " + island.getUuid() +
                        " but their current island is " + current.getUuid() + "!");
                player.sendMessage(Component.text("Prebaci se na ovo ostrvo da kopaš tu! OVO NE SME NORMALNO DA SE DOGODI", TextColor.color(0xff7f00)));
            } else {
                plugin.getLogger().warning(player.getName() + " pokusa da unistava blokove na necijem ostrvu, ali nije clan tog ostrva");
            }
            event.setCancelled(true);
            return;
        }

        if (WorldUtils.compareBlockLocation(block.getLocation(), island.getSourceBlockLocation())) {
            island.sourceBlockMined(user, block, block.getWorld(), event);
        }
    }
}
