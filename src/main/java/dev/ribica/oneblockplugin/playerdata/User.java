package dev.ribica.oneblockplugin.playerdata;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.islands.Island;
import dev.ribica.oneblockplugin.quests.UserQuestsHolder;
import dev.ribica.oneblockplugin.skills.UserSkillsHolder;
import dev.ribica.oneblockplugin.util.StringUtils;
import lombok.*;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bson.Document;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class User {
    private @Getter final OneBlockPlugin plugin;  // TODO: expose plugin through User?
    private final @Getter UUID uuid;
    private @Getter Player player;

    private @Getter @Setter(AccessLevel.PROTECTED) UUID ownedIslandUuid;
    private @Getter @Setter(AccessLevel.PROTECTED) Island activeIsland;
    private @Getter @Setter(AccessLevel.PROTECTED) Date joinedAt;
    private @Getter @Setter(AccessLevel.PROTECTED) boolean wantsToSeeChallengeBar = true;

    // Cached list of island UUIDs this user has access to (retrieved from island_members collection)
    private @Getter List<UUID> profiles = new ArrayList<>();

    // Track mined blocks from the one block across all islands
    private final @Getter Map<Material, Integer> minedBlocks = new HashMap<>();
    private @Getter BossBar challengeBar = BossBar.bossBar(Component.empty(), 1.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);

    private @Getter int level = 1;
    public UserQuestsHolder quests = null;
    public UserSkillsHolder skills = null;





    private final Set<PotionEffect> temporaryEffects = new HashSet<>();

    public void addTemporaryEffect(@NonNull PotionEffect effect) {
        if (player == null) {
            plugin.getLogger().warning("Cannot add temporary effect " + effect.getType() + " to user " + uuid + " because player is null");
            return;
        }
        temporaryEffects.add(effect);
        player.addPotionEffect(effect);
    }

    public void removeTemporaryEffect(@NonNull PotionEffectType effectType) {
        if (player == null) {
            plugin.getLogger().warning("Cannot remove temporary effect " + effectType + " from user " + uuid + " because player is null");
            return;
        }
        temporaryEffects.removeIf(effect -> effect.getType().equals(effectType));
        player.removePotionEffect(effectType);
    }

    public void clearTemporaryEffects() {
        if (player != null)
            temporaryEffects.forEach(effect -> player.removePotionEffect(effect.getType()));
    }



    protected void loadSkills(Document skillsDoc) {
        if (skillsDoc == null) {
            skillsDoc = new Document();
        }
        this.skills = UserSkillsHolder.deserialize(skillsDoc, this);
    }



    protected void loadQuests(Document questsDoc) {
        if (questsDoc == null) {
            questsDoc = new Document();
        }
        // TODO: this can be better, 'quests' should be final
        plugin.getLogger().info("User#loadQuests: quests doc: " + questsDoc.toJson());
        this.quests = UserQuestsHolder.deserialize(questsDoc, this);
    }

    public void resumeQuests() {
        plugin.getLogger().warning("Active quests: " + this.quests.getActiveQuests());

        this.quests.getActiveQuests().values().forEach(q -> {
            if (q == null) {
                plugin.getLogger().warning("Quest is null for user " + player.getName());
                return;
            } else {
                plugin.getLogger().info("Resuing quest " + q.getMetadata().id() + " for user " + player.getName());
                q.resume();
            }
        });
    }

    public void updateQuests() {
        List<Integer> startable = this.quests.getStartableQuests();
        for (int questId : startable) {
            plugin.getLogger().info("Starting quest " + questId + " for user " + player.getName());
            this.quests.beginQuest(questId);
        }
    }

    public void showInfoMessage(String message) {

    }

    public void showMessage(String message) {
        List<String> parts = StringUtils.splitWorldsIntoRows(message, 40);
//        for (String part : parts) {
//            player.sendMessage(TextCenter.chat(part));
//        }
//        player.sendMessage(TextCenter.chat(message));

        Component c = Component.text("abcd");


        player.sendMessage(Component.text(message));
        player.sendMessage(Component.text("[OPCIJA 1]").clickEvent(
                ClickEvent.callback(
                        aud -> {
                            aud.sendMessage((Component.text("You clicked option 1!")));
                        },
                        ClickCallback.Options.builder().lifetime(Duration.ofSeconds(5)).build()
                )
        ).hoverEvent(HoverEvent.showItem(HoverEvent.ShowItem.showItem(Key.key("diamond_sword"), 3)))
                .appendSpace().append(Component.text("[OPCIJA 2]").clickEvent(
                ClickEvent.callback(
                        aud -> {
                            aud.sendMessage((Component.text("You clicked option 2!")));
                        },
                        ClickCallback.Options.builder().lifetime(Duration.ofSeconds(5)).build()
                )
        ).hoverEvent(HoverEvent.showEntity(HoverEvent.ShowEntity.showEntity(Key.key("zombie"), UUID.randomUUID())))));
    }

    // Public method to track a mined block for this user
    public void trackBlockMined(Material material) {
        minedBlocks.put(material, minedBlocks.getOrDefault(material, 0) + 1);
//        material.asBlockType()
    }

    protected void setPlayer(@NonNull Player player) {
        if (this.player != null)
            throw new IllegalStateException("Player already set");
        if (player.getUniqueId() != uuid)
            throw new IllegalArgumentException("Player UUID does not match User UUID");
        this.player = player;
    }

    protected void ensureLoaded() {
        String invalid = "";
//        if (player == null)         invalid += "player, ";
        if (activeIsland == null)   invalid += "activeIsland, ";
        if (joinedAt == null)       invalid += "joinedAt, ";
        if (ownedIslandUuid == null) invalid += "ownedIslandUuid, ";
        if (minedBlocks == null)    invalid += "minedBlocks, ";  // TODO this is never null
        if (quests == null)         invalid += "quests, ";

        if (!invalid.isEmpty()) {
            throw new IllegalStateException("User missing properties: " + invalid.substring(0, invalid.length() - 2));
        }
    }

    public void updateStats() {
        if (player == null) {
            plugin.getLogger().severe("Cannot update stats for user " + uuid + " because player is null");
            return;
        }
        plugin.getLogger().info("TODO: Updating stats for " + player.getName());
    }

    public boolean isOnOwnedIsland() {
        // do not use == to compare UUIDs
        return activeIsland.getOwner().getId().getUuid().equals(uuid) && activeIsland == getCurrentIsland();
    }

    public CompletableFuture<Island> switchActiveIsland(@NonNull UUID islandUuid) {
        assert player != null;
        CompletableFuture<Island> future = new CompletableFuture<>();



        plugin.runTaskAsync(() -> {
            try {
                Island nextIsland = Islands.loadIsland(islandUuid).get();

                if (!nextIsland.isCurrentMemberOrOwner(uuid)) {
                    throw new RuntimeException("Player is not a member of that island!");
                }

                Island prevIsland = this.activeIsland;
                this.activeIsland = nextIsland;
                plugin.runTaskAsync(prevIsland::unloadIfSafe);
                plugin.runTask(() -> player.teleport(nextIsland.getSpawnLocation()));
                future.complete(nextIsland);
            } catch (Exception ex) {
                future.completeExceptionally(ex);
            }
        });
        return future;
    }

    /**
     * Gets the island the player is physically located on
     * This may be different from the player's active island if they are visiting another player's island
     * @return The island the player is currently on, or null if they are not on any island
     */
    public @Nullable Island getCurrentIsland() {
        if (player == null || !player.isOnline()) {
            return null;
        }
        // musava_ribica: optimize this method
        return plugin.getIslandAllocator2().getIslandForWorld(player.getWorld());
        /*
        // Check all allocated islands to see if player is within bounds of any of them
        Location playerLoc = player.getLocation();
        for (Island island : plugin.getIslandAllocator2().getForwardMapping().values()) {
            Location origin = island.getOrigin();
            if (origin == null || !playerLoc.getWorld().equals(origin.getWorld())) {
                continue;
            }
            var bounds = island.getBoundary();
            // expand by one block in each direction
            bounds.expand(BlockVector3.at(1, 1, 1), BlockVector3.at(-1, -1, -1));

            if (bounds.contains(BukkitAdapter.adapt(playerLoc).toBlockPoint())) {
                return island;
            }
        }
        return null; // Player is not on any island
        */
    }
}
