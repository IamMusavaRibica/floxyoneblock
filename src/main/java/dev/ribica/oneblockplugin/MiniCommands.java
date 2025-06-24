package dev.ribica.oneblockplugin;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import com.google.common.collect.HashMultimap;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

import static java.time.Duration.ofSeconds;

@RequiredArgsConstructor
//@CommandAlias("test")
@SuppressWarnings("unused")
public class MiniCommands extends BaseCommand {
    private final OneBlockPlugin plugin;

    @CommandAlias("halt")
    @CommandPermission("op")
    public void halt(CommandSender sender) {
        plugin.setStopping(true);
        Audience server = plugin.getServer();
        server.sendMessage(Component.text("Server restart uskoro!", TextColor.color(0xffff00))
                .appendNewline()
                .append(Component.text("Molimo vas da napustite server unutar sljedećih 30 sekundi!", TextColor.color(0x77ff77), TextDecoration.BOLD)));
        server.showTitle(Title.title(Component.text("test title"), Component.text("test subtitle"),
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
                target.kick(Component.text("Server se restarta!"));
            }
        }, 5*20L, 40L);
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
