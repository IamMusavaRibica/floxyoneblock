package dev.ribica.oneblockplugin.islands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.playerdata.User;
import dev.ribica.oneblockplugin.util.UUIDNamePair;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.util.*;


@SuppressWarnings("unused")
@CommandAlias("is")
@RequiredArgsConstructor
public class IslandCommands extends BaseCommand {
    private final OneBlockPlugin plugin;

    /*
    User who ran the command and his active island are provided for convenience.
    (the following assertion is true:  user.getActiveIsland() == island)
     */
    @Default
    @Description("Sve o ostrvima!")
    public void is(User user, Island island) {
        user.getPlayer().sendMessage("Welcome /is " + island.getUuid());
    }

//    @CommandAlias("progresschallenge")
//    public void testProgressChallenge(User user, Island island, String challengeId) {
//        IslandMember userMember = island.getMember(user.getUuid());
//        if (userMember == null && island.getOwner().getId().getUuid().equals(user.getUuid())) {
//            userMember = island.getOwner();
//        }
//        if (userMember != null) {
//            userMember.incrementChallengeProgress(challengeId);
//            user.getPlayer().sendMessage("Sada imaš +1 za " + challengeId);
//        }
//    }

    @CommandAlias("coopadd")
    @CommandCompletion("@players")
    public void coopadd(User user, Island island, OnlinePlayer other) {
        Player sender = user.getPlayer();
        Player target = other.getPlayer();
        plugin.getLogger().info("Player " + sender.getName() + " and target " + target.getName());
        User targetUser = plugin.getUser(target);

        if (!user.isOnOwnedIsland()) {
            sender.sendMessage(Component.text("Moraš biti na svojem ostrvu.", NamedTextColor.RED));
        } else if (user == targetUser) {
            sender.sendMessage(Component.text("To si ti!", NamedTextColor.RED));
        } else if (island.members.hasMember(targetUser.getUuid(), true)) {
            sender.sendMessage(Component.text("Igrač je već član tvojeg ostrva.", NamedTextColor.RED));
        } else {
            island.members.addMember(UUIDNamePair.of(targetUser.getUuid()), 1, Date.from(Instant.now()));
            // Update the cached profiles list
            targetUser.getProfiles().add(island.getUuid());

            plugin.getIslandRegionManager().updateMemberPermissions(island);
            sender.sendMessage("Gotovo.");
            target.sendMessage(Component.text(sender.getName() + " te je dodao na svoje ostrvo ")/*.append(island.getName())*/);
        }
    }

    @CommandAlias("coopkick")
    @CommandCompletion("@coopmembers")
    public void coopkick(User user, Island island, IslandMember target) {
        Player sender = user.getPlayer();

        if (!user.isOnOwnedIsland()) {
            sender.sendMessage(Component.text("Moraš biti na svojem ostrvu.", NamedTextColor.RED));
            return;
        }

        UUID memberUuid = target.getId().getUuid();
        String memberName = target.getId().getName();
        island.members.removeMember(memberUuid);
        sender.sendMessage(Component.text(memberName + ", doviđenja", NamedTextColor.GREEN));

        plugin.getIslandRegionManager().updateMemberPermissions(island);

        // this method handles both cases if the target is online or offline

        // Try to notify kicked member if they're online
        Player kickedPlayer = plugin.getServer().getPlayer(memberUuid);
        if (kickedPlayer != null && kickedPlayer.isOnline()) {
            User targetUser = plugin.getUser(kickedPlayer);
            // Update the cached profiles list
            targetUser.getProfiles().remove(island.getUuid());

            // if they're currently playing on this island, ...
            if (targetUser.getActiveIsland() == island) {
                targetUser.switchActiveIsland(targetUser.getOwnedIslandUuid());
            }
            kickedPlayer.sendMessage(Component.text(sender.getName() + " vas je izbacio sa svojeg ostrva ", NamedTextColor.RED)/*.append(island.getName())*/);
        }

    }

    @Subcommand("management")
    public void is_management(User user, Island island) {
        Player player = user.getPlayer();

        // check if the user's active island is his island and he is currently on it
        if (!user.isOnOwnedIsland()) {
            player.sendMessage(Component.text("Moraš biti na svom ostrvu da upravljaš njime.", NamedTextColor.RED));
            return;
        }

        player.sendMessage("još nije implementirano");
    }

    @Subcommand("switch")
    public void is_switch(User user, Island currentIsland) {
        Player player = user.getPlayer();
        player.sendMessage("Loading islands...");

        plugin.runTaskAsync(() -> {
            // TODO: in case of error, ignore the bad island and continue with the rest
            // Use cached profiles from User object instead of querying the database
            List<Island> islands = user.getProfiles().parallelStream()
                    .map(iuuid -> plugin.getStorageProvider().loadIslandFromDatabase(iuuid))
                    .filter(Objects::nonNull) // Filter out null results
                    .filter(i -> i.isCurrentMemberOrOwner(user.getUuid()))
                    .toList();

            plugin.runTask(() -> {
                ChestGui gui = new ChestGui(4, "Switch Island");
                gui.setOnGlobalClick(event -> event.setCancelled(true)); // Prevent taking items out or putting items in

                OutlinePane pane = new OutlinePane(0, 0, 9, 4);
                for (Island island : islands) {
                    boolean selected = island.getUuid().equals(currentIsland.getUuid());
                    ItemStack icon = new ItemStack(Material.GRASS_BLOCK);
                    icon.editMeta((ItemMeta meta) -> {
                        meta.displayName(island.getName().decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
                        meta.lore(List.of(
                                Component.empty(),
                                Component.text(island.getUuid().toString()).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE),
                                selected ?
                                    Component.text("Currently selected", NamedTextColor.GREEN) :
                                    Component.text("Click to switch to this island", NamedTextColor.YELLOW)
                        ));
                    });

                    pane.addItem(new GuiItem(icon, event -> {
                        event.setCancelled(true);
                        if (selected) {
                            player.sendMessage(Component.text("You are already on this island.", NamedTextColor.RED));
                        } else {
                            player.sendMessage(Component.text("Switching to island ", NamedTextColor.GREEN)
                                    .append(island.getName())
                                    .append(Component.text("...", NamedTextColor.GREEN)));

                            user.switchActiveIsland(island.getUuid()).whenComplete((newIsland, error) -> {
//                                plugin.getLogger().info("Switching islands complete for " + player.getName());
                                if (error != null) {
                                    player.sendMessage(Component.text("There was an error while switching islands! ", TextColor.color(0xcc0000))
                                            .appendNewline()
                                            .append(Component.text("Error: " + error.getMessage(), NamedTextColor.RED)));
                                } else {
                                    player.sendMessage(Component.text("Welcome!", NamedTextColor.GREEN));
                                }
                            });
                        }
                    }));
                }
                gui.addPane(pane);
                gui.show(player);
            });


        });

    }

    @Subcommand("members")
    public void is_members(User user, Island island) {
        Player player = user.getPlayer();
        player.sendMessage("Members of island " + island.getName() + ":");
        island.members.getCurrentMembers().forEach(member -> {
            player.sendMessage("- " + member.getId().getName() + " (added at " + member.getAddedAt() + ")");
        });
    }

    @Subcommand("advancestage")
    @Description("Advance to the next stage (requires blocks mined)")
    public void is_advancestage(User user, Island island) {
        Player player = user.getPlayer();

        // Check if the user is on their own island
        if (!user.isOnOwnedIsland()) {
            player.sendMessage(Component.text("Moraš biti na svom ostrvu da napredujеš kroz nivoe.", NamedTextColor.RED));
            return;
        }

        // Check current stage and blocks mined
        int currentStage = island.getCurrentStageId();
        int blocksMinedSinceLastStage = island.getBlocksMinedSinceLastStage();
        int blocksNeeded = island.getBlocksNeededForNextStage();

        // Get stage information
        var stageManager = plugin.getStageManager();
        var currentStageInfo = stageManager.getStage(currentStage);
        String currentStageName = currentStageInfo != null ? currentStageInfo.name() : "Nepoznat";

        if (blocksNeeded > 0) {
            player.sendMessage(Component.text("Potrebno je da iskopaš još " + blocksNeeded + " blokova da napredujеš na sledeći nivo.", NamedTextColor.RED));
            player.sendMessage(Component.text("Trenutni nivo: " + currentStageName + " (ID: " + currentStage + ")", NamedTextColor.GRAY));
            player.sendMessage(Component.text("Iskopao si: " + blocksMinedSinceLastStage + " blokova", NamedTextColor.GRAY));
            return;
        }

        // Advance to next stage
        boolean success = island.advanceToNextStage();
        if (success) {
            int newStage = island.getCurrentStageId();
            var newStageInfo = stageManager.getStage(newStage);
            String newStageName = newStageInfo != null ? newStageInfo.name() : "Nepoznat";

            player.sendMessage(Component.text("Čestitamo! Napredovao si na nivo: " + newStageName + " (ID: " + newStage + ")", NamedTextColor.GREEN));

            // Special message if cycled back to stage 1
            if (newStage == 1 && currentStage > 1) {
                player.sendMessage(Component.text("Završio si sve nivoe i vratio si se na početak ciklusa!", NamedTextColor.GOLD));
            }
        } else {
            player.sendMessage(Component.text("Došlo je do greške pri napredovanju nivoa.", NamedTextColor.RED));
        }
    }

    @SuppressWarnings({"UnstableApiUsage"})
    @Subcommand("stages")
    public void is_stages(User user, Island island) {
        Player player = user.getPlayer();
        int currentStageId = island.getCurrentStageId();
        var allStages = plugin.getStageManager().getAllStages();
        int maxStage = allStages.size();

        int[][] snake = new int[][] {{0, 0}, {1, 0}, {1, 1}, {1, 2}, {1, 3}, {2, 3},
                {3, 3}, {3, 2}, {3, 1}, {3, 0}, {4, 0}, {5, 0},
                {5, 1}, {5, 2}, {5, 3}, {6, 3}, {7, 3}, {7, 2},
                {7, 1}, {7, 0}, {8, 0}};

        ChestGui gui = new ChestGui(6, ComponentHolder.of(
                Component.text("\uDAFF\uDFF8䵖", NamedTextColor.WHITE)
        ));
        StaticPane pane0 = new StaticPane(0, 2, 9,  6);
        for (int i = 0; i < maxStage && i < 21; i++) {
            var stage = plugin.getStageManager().getStage(i);
            if (stage == null)
                continue;

            int x = snake[i][0];
            int y = snake[i][1];
            String cmd =
                    i == currentStageId ? "cyan" :
                    i < currentStageId ? "white" : "gray";

            ItemStack icon = new ItemStack(Material.ICE);
            icon.editMeta(meta -> meta.displayName(plugin.deserializeMiniMessage(stage.name(), true)));
            icon.setData(DataComponentTypes.ITEM_MODEL, Key.key("floxy", "outlined_number"));
            icon.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(cmd).addFloat(i*1f));
            pane0.addItem(new GuiItem(icon), x, y);
        }

        gui.addPane(pane0);
        gui.setOnGlobalClick(event -> event.setCancelled(true));
        gui.show(player);
    }
}
