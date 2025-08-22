package dev.ribica.oneblockplugin.challenges;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.challenges.types.CraftSingleItemsChallenge;
import dev.ribica.oneblockplugin.islands.Island;
import dev.ribica.oneblockplugin.playerdata.User;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;

@RequiredArgsConstructor
public class ChallengesEventListener implements Listener {
    private final OneBlockPlugin plugin;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        User user = plugin.getUser(player);
        Island island = user.getActiveIsland();
//        island.getActiveChallenges().forEach(chall -> {
//            if (chall instanceof CraftSingleItemsChallenge craftChall) {
//                craftChall.progress(user, event);
//            }
//        });
        plugin.getLogger().info("Crafted: " + event.getCurrentItem() + " by " + event.getWhoClicked().getName());
    }
}
