package dev.ribica.oneblockplugin.islands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.oneblock.Stage;
import dev.ribica.oneblockplugin.playerdata.User;
import dev.ribica.oneblockplugin.util.UUIDNamePair;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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

    @CommandAlias("progresschallenge")
    public void testProgressChallenge(User user, Island island, String challengeId) {
        IslandMember userMember = island.getMember(user.getUuid());
        if (userMember == null && island.getOwner().getId().getUuid().equals(user.getUuid())) {
            userMember = island.getOwner();
        }
        if (userMember != null) {
            userMember.incrementChallengeProgress(challengeId);
            user.getPlayer().sendMessage("Sada imaš +1 za " + challengeId);
        }
    }

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
            return;
        } else if (island.getMember(targetUser.getUuid()) != null) {
            sender.sendMessage(Component.text("Igrač je već član tvojeg ostrva.", NamedTextColor.RED));
        } else {
            island.addMember(UUIDNamePair.of(targetUser.getUuid()), 1, Date.from(Instant.now()));
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
        island.removeMember(memberUuid);
        sender.sendMessage(Component.text(memberName + ", doviđenja", NamedTextColor.GREEN));

        plugin.getIslandRegionManager().updateMemberPermissions(island);

        // this method handles both cases if the target is online or offline

        // Try to notify kicked member if they're online
        Player kickedPlayer = plugin.getServer().getPlayer(memberUuid);
        if (kickedPlayer != null && kickedPlayer.isOnline()) {
            User targetUser = plugin.getUser(kickedPlayer);
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
            List<Island> islands = user.getProfiles().parallelStream()
                    .map(iuuid -> plugin.getStorageProvider().loadIslandMetadata(iuuid, true).getLeft())
                    .filter(Objects::nonNull) // Filter out null results
                    .toList();

            plugin.runTask(() -> {
                ChestGui gui = new ChestGui(4, "Switch Island (test)");
                OutlinePane pane = new OutlinePane(0, 0, 9, 4);

                gui.setOnGlobalClick(event -> event.setCancelled(true)); // Prevent taking items out or putting items in

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
                                    player.sendMessage(Component.text("There was an error while switching islands!", TextColor.color(0xcc0000)));
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
        island.getMembers().forEach(member -> {
            player.sendMessage("- " + member.getId().getName() + " (added at " + member.getAddedAt() + ")");
        });
    }

    @Subcommand("challenges")
    public void is_challenges(User user, Island island) {
        Player player = user.getPlayer();
        plugin.getLogger().info("/is challenges opened by " + player.getName());
        // Recalculate the total challenge progress in case it changed
        island.recalculateChallengeProgress();
        Map<String, Integer> totalProgress = island.getChallengeProgress();
        plugin.getLogger().info("Total progress map: " + totalProgress);
        List<IslandMember> allMembers = new ArrayList<>();
        allMembers.add(island.getOwner());
        allMembers.addAll(island.getAllMembers());
        plugin.getLogger().info("All members count (including owner): " + allMembers.size());
        for (IslandMember m : allMembers) {
            plugin.getLogger().info("Member: " + m.getId().getName() + ", progress: " + m.getChallengeProgress());
        }

        // Collect all challenge IDs present in any member
        Set<String> allChallengeIds = new HashSet<>();
        for (IslandMember member : allMembers) {
            allChallengeIds.addAll(member.getChallengeProgress().keySet());
        }
        plugin.getLogger().info("All challenge IDs: " + allChallengeIds);
        List<String> sortedChallengeIds = new ArrayList<>(allChallengeIds);
        Collections.sort(sortedChallengeIds);
        plugin.getLogger().info("Sorted challenge IDs: " + sortedChallengeIds);

        int memberRows = allMembers.size();
        int totalRows = 1;
        int headerRows = 1;
        int neededRows = headerRows + memberRows + totalRows;
        int guiRows = Math.min(6, Math.max(3, neededRows));
        plugin.getLogger().info("GUI rows: " + guiRows + ", memberRows: " + memberRows + ", neededRows: " + neededRows);
        ChestGui gui = new ChestGui(guiRows, "Island Challenges");
        gui.setOnGlobalClick(event -> event.setCancelled(true)); // Prevent taking items out

        // Use StaticPane for proper grid placement
        com.github.stefvanschie.inventoryframework.pane.StaticPane pane = new com.github.stefvanschie.inventoryframework.pane.StaticPane(0, 0, 9, guiRows);

        // Place header (challenge names) in row 0
        for (int col = 0; col < sortedChallengeIds.size() && col < 9; col++) {
            String challengeId = sortedChallengeIds.get(col);
            plugin.getLogger().info("Header col " + col + ": " + challengeId);
            ItemStack icon = new ItemStack(Material.BOOK);
            icon.editMeta(meta -> meta.displayName(Component.text(challengeId, NamedTextColor.AQUA)));
            pane.addItem(new GuiItem(icon), col, 0);
        }

        // For each member, show their progress for each challenge (row per member)
        for (int row = 0; row < allMembers.size() && row + 1 < guiRows - 1; row++) {
            IslandMember member = allMembers.get(row);
            String memberName = member.getId().getName();
            for (int col = 0; col < sortedChallengeIds.size() && col < 9; col++) {
                String challengeId = sortedChallengeIds.get(col);
                int progress = member.getChallengeProgress().getOrDefault(challengeId, 0);
                plugin.getLogger().info("Member row " + row + ", col " + col + ": " + memberName + " - " + challengeId + " = " + progress);
                ItemStack icon = new ItemStack(Material.PAPER);
                icon.editMeta(meta -> meta.displayName(Component.text(memberName + ": " + progress, NamedTextColor.YELLOW)));
                pane.addItem(new GuiItem(icon), col, row + 1);
            }
        }

        // Add a row for total progress (last row)
        int totalRow = guiRows - 1;
        for (int col = 0; col < sortedChallengeIds.size() && col < 9; col++) {
            String challengeId = sortedChallengeIds.get(col);
            int total = totalProgress.getOrDefault(challengeId, 0);
            plugin.getLogger().info("Total row col " + col + ": " + challengeId + " = " + total);
            ItemStack icon = new ItemStack(Material.EMERALD);
            icon.editMeta(meta -> meta.displayName(Component.text("Total: " + total, NamedTextColor.GREEN)));
            pane.addItem(new GuiItem(icon), col, totalRow);
        }

        gui.addPane(pane);
        gui.show(player);
    }

    @Subcommand("stages")
    public void is_stages(User user, Island island) {
        Player player = user.getPlayer();
        int progress = island.getChallengeProgress().getOrDefault("stages", 0);
        int currentStageLevel = island.getStage().getLevel();
        int maxStage = 8;
        ChestGui gui = new ChestGui(3, "Island Stages");
        OutlinePane pane = new OutlinePane(0, 0, 9, 3);
        for (int i = 1; i <= maxStage; i++) {
            ItemStack icon;
            String name;
            if (i < currentStageLevel) {
                // Past stage
                icon = new ItemStack(Stage.getByLevel(i).getIcon());
                name = "Stage " + i;
            } else if (i == currentStageLevel) {
                // Current stage
                icon = new ItemStack(Stage.getByLevel(i).getIcon());
                name = "Current: Stage " + i;
            } else {
                // Future stage
                icon = new ItemStack(Material.BEDROCK);
                name = "§c?";
            }
            icon.editMeta(meta -> meta.displayName(Component.text(name)));
            pane.addItem(new GuiItem(icon));
        }
        gui.addPane(pane);
        gui.setOnGlobalClick(event -> event.setCancelled(true));
        gui.show(player);
    }
}
