package dev.ribica.oneblockplugin.skills;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;


public class SkillsBossBarManager implements Listener {
    private final OneBlockPlugin plugin;
    private final Logger logger = Logger.getLogger(SkillsBossBarManager.class.getName());

    private final Map<UUID, Map<Skill, BossBar>> bossBars = new HashMap<>();

    /* Monotonic integer that is incremented every time the boss bar is updated.
       When scheduling a removal of the boss bar, we check if the integer is the same,
       if it is not, it means the boss bar was updated again and there's a newer hide
       scheduled, so we do not remove it at that point.  */
    private final Map<UUID, Map<Skill, Integer>> actions = new HashMap<>();


    public SkillsBossBarManager(OneBlockPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void sendUpdate(Player player, Skill skill, int level, long xpFrom, long xpTo, long levelXp) {
        // the animation goes from xpFrom to xpTo
        var uuid = player.getUniqueId();

        // increment action
        final int currentAction = actions.computeIfAbsent(uuid, k -> new HashMap<>())
                .compute(skill, (k, v) -> v == null ? 0 : v + 1);

        var bossBar = bossBars.computeIfAbsent(uuid, k -> new HashMap<>()).get(skill);
        String text = "skill " + skill.getName() + " level " + level + " xp: " + xpTo + "/" + levelXp;
        Component title = Component.text(text);

        float progressOld = Math.clamp((float) xpFrom / levelXp, 0f, 1f);
        float progressNew =  Math.clamp((float) xpTo / levelXp, 0f, 1f);

        if (bossBar == null) {
            logger.info("Creating new boss bar for " + player.getName() + " skill " + skill.getName());
            final var newBar = BossBar.bossBar(title, progressOld, skill.getBarColor(), BossBar.Overlay.PROGRESS);
            player.showBossBar(newBar);
            plugin.runTaskLater(() -> newBar.progress(progressNew), 2);
            bossBars.get(uuid).put(skill, newBar);
            scheduleHide(player, skill, newBar, currentAction);
        } else {
            logger.info("Reusing existing boss bar for " + player.getName() + " skill " + skill.getName());
            bossBar.name(title);
            bossBar.progress(progressOld);
            plugin.runTaskLater(() -> bossBar.progress(progressNew), 2);
            player.showBossBar(bossBar);
            scheduleHide(player, skill, bossBar, currentAction);
        }
    }

    private void scheduleHide(Player player, Skill skill, BossBar bar, int currentAction) {
        plugin.runTaskLater(() -> {
            int newAction = actions.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).get(skill);
            if (newAction == currentAction) {
                player.hideBossBar(bar);
            }
        }, 3 * 20);
    }

    private void updateBossBar(@Nullable BossBar bar) {

    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        var uuid = player.getUniqueId();
        var r = bossBars.remove(uuid);
        if (r != null) {
            r.values().forEach(player::hideBossBar);
        }
    }
}
