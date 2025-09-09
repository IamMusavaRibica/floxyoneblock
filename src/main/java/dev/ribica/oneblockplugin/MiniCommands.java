package dev.ribica.oneblockplugin;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import com.fathzer.soft.javaluator.DoubleEvaluator;
import com.fathzer.soft.javaluator.StaticVariableSet;
import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.google.common.collect.HashMultimap;
import com.sk89q.minecraft.util.commands.Command;
import dev.ribica.oneblockplugin.items.RawItem;
import dev.ribica.oneblockplugin.playerdata.User;
import dev.ribica.oneblockplugin.skills.Skill;
import dev.ribica.oneblockplugin.util.McTextUtils;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.PotionContents;
import io.papermc.paper.datacomponent.item.Tool;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.data.DataCommands;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.bukkit.*;
import org.bukkit.block.BlockType;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.time.Duration.ofSeconds;
import static net.kyori.adventure.text.Component.text;

@RequiredArgsConstructor
//@CommandAlias("test")
@SuppressWarnings("unused")
public class MiniCommands extends BaseCommand {
    private final OneBlockPlugin plugin;


    @CommandAlias("getlocale")
    public void tgrg(Player player) {
        player.sendMessage("Your locale is: " + player.locale());
    }


    @CommandAlias("newbeam")
    public void newBeam(Player player) {
        int id = plugin.getFakeBeaconRenderer().newBeam(player.getLocation());
        player.sendMessage("New beam created with id: " + id);
    }

    @CommandAlias("newbeacon")
    public void newBeacon(Player player) {
        int id = plugin.getKlyBeaconRenderer().newBeacon(player.getLocation());
        player.sendMessage("New beacon created with id: " + id);
    }

    @CommandAlias("removebeam")
    public void removeBeam(Player player, int id) {
        try {
            plugin.getFakeBeaconRenderer().removeBeam(id);
            player.sendMessage("Beam with id " + id + " removed.");
        } catch (IllegalArgumentException e) {
            player.sendMessage("No beam with id " + id + " found.");
        }
    }

    @CommandAlias("maketile")
    public void rgwughwr(Player player) {
        Location loc = player.getLocation();
        var spawnLoc = new Location(
                loc.getWorld(), loc.getX(), loc.getY(), loc.getZ()
        );


//        TextDisplay td = player.getWorld().spawn(spawnLoc, TextDisplay.class);
//        td.setText(" ");
//        td.setBackgroundColor(Color.fromARGB(166, 0, 255, 122));
//        td.setTransformationMatrix(new Matrix4f()/*.scale(.5f, .5f, .5f)*/.translate(.4f, 0, 0).scale(8, 4, 1));
//        td.addScoreboardTag("testtile");
//        td.setTeleportDuration(1);
//        td.setInterpolationDuration(1);
        BlockDisplay bd = player.getWorld().spawn(spawnLoc, BlockDisplay.class);
        bd.setBlock(Material.DIAMOND.createBlockData());
        bd.setTransformationMatrix(
                new Matrix4f()
                        .scale(.5f, .5f, .5f)
                        .translate(-.5f, 0, -.5f)
        );
//        new Matrix4f().mulLocal
    }

    @CommandAlias("opencustomnamegui")
    public void ocng(Player player, String base64, int rows) {
        String actualName = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        ChestGui chestGui = new ChestGui(rows,
                ComponentHolder.of(Component.text(actualName, TextColor.color(0xffffff)))
        );
        chestGui.show(player);
    }
    @CommandAlias("opencustomnamegui2")
    public void ocng1(Player player, String base64) {
//        String actualName = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
//        player.getWorld().save();
        ChestGui chestGui = new ChestGui(6, ComponentHolder.of(
                text()
                        .append(Component.text("A"))
                        .append(Component.translatable("space.-1"))
                        .append(Component.text("B"))
                        .append(Component.translatable("space.-1"))
                        .append(Component.text("C"))
                        .append(Component.translatable("space.-1"))
                        .append(Component.text("D"))
                        .append(Component.translatable("space.-1"))
                        .append(Component.text("E"))
                        .build()

        ));
        chestGui.show(player);
//        Bukkit.getWorld()
    }

    @CommandAlias("giveskillxp")
    public void gskxp(Player player, String skillName, long xp) {
        var user = plugin.getUser(player);
        var skill = Skill.valueOf(skillName.toUpperCase());
        user.skills.addRawXp(skill, xp);
    }

    @CommandAlias("evaluate")
    public void evaluate(Player player, String expression) {
        var variables = new StaticVariableSet<Double>();
        variables.set("a", 10d);
        variables.set("t", 0.3d);
        double result = new DoubleEvaluator().evaluate(expression, variables);
        player.sendMessage(text("Result of expression '" + expression + "' is: " + result, TextColor.color(0x7777ff)));
    }

    @CommandAlias("customitems")
    @CommandPermission("oneblock.admin.customitems")
    public void customItems(Player player) {
        // Get all registered items
        Collection<RawItem> items = plugin.getItemRegistry().getAllItems();

        if (items.isEmpty()) {
            player.sendMessage(Component.text("No custom items are registered.", TextColor.color(0xFF5555)));
            return;
        }

        // Create a GUI with enough rows to hold all items (1 row = 9 slots)
        int rows = Math.min(6, (int) Math.ceil(items.size() / 9.0));
        ChestGui gui = new ChestGui(rows, ComponentHolder.of(
                Component.text("Custom Items", TextColor.color(0x55FFFF))
        ));

        // Prevent players from taking items out of the GUI
        gui.setOnGlobalClick(event -> event.setCancelled(true));

        // Create a pane to hold the items
        OutlinePane pane = new OutlinePane(0, 0, 9, rows);

        // Add each item to the pane
        for (RawItem item : items) {
            // Create an ItemStack for the GUI display
            ItemStack displayItem = item.newItemStack();
//            displayItem.setAmount(item.getMaxStackSize());
            // Edit the item's metadata to show its ID
            displayItem.editMeta(meta -> {
                // Keep the original display name if it exists
                Component displayName = meta.displayName();
                if (displayName == null) {
                    displayName = Component.text(item.getId(), TextColor.color(0xFFFF55))
                            .decoration(TextDecoration.ITALIC, false);
                }

                // Set the display name
                meta.displayName(displayName);

                // Add lore with item ID and instructions
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(Component.text("ID: ", TextColor.color(0xAAAAAA))
                        .append(Component.text(item.getId(), TextColor.color(0xFFFFFF)))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
                lore.add(Component.text("Click to get this item", TextColor.color(0x55FF55))
                        .decoration(TextDecoration.ITALIC, false));

                meta.lore(lore);
            });

            // Create a GuiItem with a click handler
            pane.addItem(new GuiItem(displayItem, event -> {
                // Cancel the event to prevent taking the item
                event.setCancelled(true);

                // Give the player the item
                ItemStack newItem = item.newItemStack();
                player.getInventory().addItem(newItem);

                // Send a confirmation message
                player.sendMessage(Component.text("You received ", TextColor.color(0x55FF55))
                        .append(Component.text(item.getId(), TextColor.color(0xFFFF55)))
                        .append(Component.text("!", TextColor.color(0x55FF55))));

                // Close the GUI
                player.closeInventory();
            }));
        }

        // Add the pane to the GUI
        gui.addPane(pane);

        // Show the GUI to the player
        gui.show(player);
    }

    @CommandAlias("getcomponents")
    @SuppressWarnings("UnstableApiUsage")
    public void g(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        var jukebox = item.getData(DataComponentTypes.JUKEBOX_PLAYABLE);
        player.sendMessage("Jukebox: " + jukebox);
        if (jukebox != null) {
            player.sendMessage("Song: " + jukebox.jukeboxSong());
        }
//        new ItemStack()
        net.minecraft.world.item.ItemStack nmsItem = ((CraftItemStack) item).handle;
        DataComponentMap components = nmsItem.getComponents();

//        Registry.ITEM
//        io.papermc.paper.datacomponent.DataComponentType
//        player.getBoundingBox().getHeight()
        PotionContents pot = item.getData(DataComponentTypes.POTION_CONTENTS);

        Tool toolData = item.getData(DataComponentTypes.TOOL);   // Paper api
        net.minecraft.world.item.component.Tool nmsToolData = nmsItem.get(DataComponents.TOOL);  // Vanilla api

        CustomData nmsCustomData = nmsItem.get(DataComponents.CUSTOM_DATA);
        if (nmsCustomData == null) {
            nmsCustomData = CustomData.of(new CompoundTag());
        }
        CustomData newData = nmsCustomData.update(tag -> {
            tag.putInt("something", tag.getIntOr("something", 0) + 1);
        });

//        DataCommands
//        Level
        nmsItem.set(DataComponents.CUSTOM_DATA, newData);
//        nmsToolData.rules().forEach(rule -> {
//            rule.blocks();
//        });
        if (toolData != null) {
            for (var rule : toolData.rules()) {
                rule.blocks();
            }
        }
        // nmsToolData.

        TextComponent.Builder c = text();

        components.forEach(entry -> {
            DataComponentType<?> type = entry.type();

            var value = entry.value();
            c.appendNewline();
            c.append(Component.text(type.toString(), TextColor.color(0x00ffff))).appendSpace();
            c.append(Component.text("(" + type.getClass().getName().replaceFirst("net\\.minecraft\\.core\\.component", "nmcc") + ")", TextColor.color(0x00ff00)));
            c.append(Component.text(": ", TextColor.color(0x777777)));
//            if (value instanceof Collection) {
//                c.append(Component.text("Collection of size " + ((Collection<?>) value).size(), TextColor.color(0x00ff00)));
//                ((Collection<?>) value).forEach(v -> c.append(Component.text("\n- " + v.toString(), TextColor.color(0x777777))));
//            } else {
//                c.append(Component.text(value.toString(), TextColor.color(0x777777)));
//            }
            c.append(Component.text(value.toString(), TextColor.color(0xdddddd))).appendSpace();
            c.append(Component.text("(" + value.getClass().getName() + ")", TextColor.color(0xdddddd)));
        });

        Component component = c.build();
        player.sendMessage(component);

//        item.editPersistentDataContainer(cons -> {
//           cons.set(NamespacedKey.fromString("abcd", plugin), PersistentDataType.LONG, 42598274529L);
//        });
        plugin.getComponentLogger().info(component);
    }

    @CommandAlias("redthedev_blockdisplaytest")
    public void uahdhufgrqwgouqwrg(Player player) {
        World world = player.getWorld();
        Location loc = player.getLocation();
        Location target = player.getLocation().add(3.5, 3.5, 3.5);
        BlockDisplay rbd = world.spawn(loc, BlockDisplay.class, e -> {
            e.setBlock(BlockType.DIAMOND_BLOCK.createBlockData());
            e.setTeleportDuration(59);
        });
        Bukkit.getScheduler().runTaskLater(plugin, tp -> {
            rbd.teleport(target);
        }, 2);
    }

    @CommandAlias("showmessage")
    public void x(Player player, String string) {
        final Component FIGURE_SPACE = text(" ");
        final Component SPACE = text(" ");
        final Component I = text("i");

//        ((org.bukkit.craftbukkit.inventory.CraftInventoryCustom.MinecraftInventory) ((CraftInventory) Bukkit.createInventory(null, null)).getInventory()).getTitle();

        User user = plugin.getUser(player);
//        user.showMessage(string);
        for (int i = 1; i < 155; i+=5) {
            player.sendMessage(McTextUtils.chat("i".repeat(i), false));
        }
        for (int i = 155; i > 1; i-=5) {
            player.sendMessage(McTextUtils.chat("i".repeat(i), true));
        }

        player.sendMessage(
                text("This is ender pearl: ")
                        .append(Component.translatable(Material.ENDER_PEARL))
                        .append(text(" and this is our custom translation1: "))
                        .appendNewline()
                        .append(Component.translatable("myplugin.test_translations.some-key"))
                        .appendNewline()
                        .append(text(" and this is our custom translation2: "))
                        .appendNewline()
                        .append(Component.translatable("test_translations.some-key"))
                        .appendNewline()
                        .append(text(" and this is our custom translation3: "))
                        .appendNewline()
                        .append(Component.translatable("myplugin.some-key"))
                        .appendNewline()
                        .append(text(" and this is our custom translation4: "))
                        .appendNewline()
                        .append(Component.translatable("myplugin:translations.test_translations.some-key"))
                        .appendNewline()
                        .append(text(" and this is our custom translation5: "))
                        .appendNewline()
                        .append(Component.translatable("translations.test_translations.some-key"))
                        .appendNewline()
                        .append(text(" and this is our custom translation6: "))
                        .appendNewline()
                        .append(Component.translatable("some-key"))
                        .appendNewline()
        );
    }


    @CommandAlias("testopendialog")
    @SuppressWarnings("UnstableApiUsage")
    public void t(Player player) {
        player.showDialog(Dialog.create(d -> {
            d.empty()
                    .type(DialogType.notice(
                            ActionButton.builder(text("ActionButtonLabel"))
                                    .tooltip(text("ActionButtonTooltip"))
                                    .width(33)
                                    .action(DialogAction.staticAction(
                                            ClickEvent.callback(aud -> plugin.getLogger().info("Audience:" + aud))
                                    ))
//                                    .action(DialogAction.staticAction(
//                                            ClickEvent.runCommand("tell test")     OVO NE VALJA
//                                    ))
                                    .build()
                    ))
                    .base(
                            DialogBase.builder(text("Example Title", TextColor.color(0x84a1b8)))
                                    .canCloseWithEscape(false)
                                    .pause(false)
                                    .body(List.of(
                                        DialogBody.plainMessage(text("Plain Message")),
                                        DialogBody.item(new ItemStack(Material.DIAMOND, 33)).build(),
                                        DialogBody.item(new ItemStack(Material.EMERALD, 11), /*new PlainMessageBodyImpl(
                                                Component.text("rwgwrogwrhg"), 111
                                        )*/DialogBody.plainMessage(text("iiiiiii")), false, true, 129, 36)
                                    )).inputs(
                                        List.of(
                                                DialogInput.text("abc", 144, text("label"),
                                                        true, "initial", 15, TextDialogInput.MultilineOptions.create(4, 57)),
                                                DialogInput.numberRange("keykeykey", text("labellll"), 14, 55)
                                                        .step(3.1f)
                                                        .width(277)
                                                        .labelFormat("%s a %s ")
                                                        .build(),
                                                DialogInput.singleOption("singleopt", 333, List.of(
                                                        SingleOptionDialogInput.OptionEntry.create("id1", text("Option 1"), false),
                                                        SingleOptionDialogInput.OptionEntry.create("id2", text("Option 2"), true),
                                                        SingleOptionDialogInput.OptionEntry.create("id3", text("Option 3"), false),
                                                        SingleOptionDialogInput.OptionEntry.create("id4", text("Option 4"), false),
                                                        SingleOptionDialogInput.OptionEntry.create("id5", text("Option 5"), false)
                                                ), text("Singleoptionlabel"), true)
                                        )
                                    ).build()
                    );
        }));
    }

    @CommandAlias("halt")
    @CommandPermission("op")
    public void halt(CommandSender sender) {
        plugin.setStopping(true);
        Audience server = plugin.getServer();
        server.sendMessage(text("Server restart uskoro!", TextColor.color(0xffff00))
                .appendNewline()
                .append(text("Molimo vas da napustite server unutar sljedećih 30 sekundi!", TextColor.color(0x77ff77), TextDecoration.BOLD)));
        server.showTitle(Title.title(text("test title"), text("test subtitle"),
                Title.Times.times(ofSeconds(3), ofSeconds(2), ofSeconds(1))
        ));



        final boolean[] done = {false};
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            var pcoll = plugin.getServer().getOnlinePlayers();
            if (pcoll.isEmpty() && !done[0]) {
                done[0] = true;
                plugin.getServer().getScheduler().runTaskLater(plugin, plugin.getServer()::shutdown, 40L);
            } else if (!pcoll.isEmpty()) {
                Player target = new ArrayList<>(pcoll).getFirst();
                plugin.getLogger().info("kicking " + target.getName());
                target.kick(text("Server se restarta!"));
            }
        }, 1*20L, 10L);  // 5*20L, 40L
    }

    @CommandAlias("optools")
    public void optools(Player player) {
        ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
        ItemStack pick = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemStack shovel = new ItemStack(Material.NETHERITE_SHOVEL);

        axe.editMeta(meta -> {
            meta.addEnchant(Enchantment.EFFICIENCY, 10, true);
            meta.addEnchant(Enchantment.UNBREAKING, 10, true);
            meta.setHideTooltip(true);
            meta.setAttributeModifiers(HashMultimap.create());
        });
        pick.editMeta(meta -> {
            meta.addEnchant(Enchantment.EFFICIENCY, 10, true);
            meta.addEnchant(Enchantment.UNBREAKING, 10, true);
            meta.setHideTooltip(true);
            meta.setAttributeModifiers(HashMultimap.create());
        });
        shovel.editMeta(meta -> {
            meta.addEnchant(Enchantment.EFFICIENCY, 10, true);
            meta.addEnchant(Enchantment.UNBREAKING, 10, true);
            meta.setHideTooltip(true);
            meta.setAttributeModifiers(HashMultimap.create());
        });
        player.getInventory().addItem(axe, pick, shovel);
    }

}
