package dev.ribica.oneblockplugin;

import com.destroystokyo.paper.event.brigadier.CommandRegisteredEvent;
import com.mojang.brigadier.tree.CommandNode;
import io.papermc.paper.command.MSPTCommand;
import io.papermc.paper.command.PaperCommand;
import lombok.RequiredArgsConstructor;
import net.minecraft.server.MinecraftServer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

import java.util.List;

@RequiredArgsConstructor
public class Fix_weird_commands implements Listener {
    private final OneBlockPlugin plugin;

    @EventHandler
    public void SL(ServerLoadEvent event) {
        // CommandDispatcher<CommandSourceStack>, RootCommandNode<CommandSourceStack>
        var dispatcher = MinecraftServer.getServer().getCommands().getDispatcher();
        var root = dispatcher.getRoot();

        // Commands to remove
        for (String cmd : List.of("minecraft:me", "me", "minecraft:help", "help", "bukkit:help",
                "stop", "restart", "minecraft:stop", "minecraft:restart", "bukkit:?", /*"paper:callback",*/ "?",
                "minecraft:reload", "reload", "bukkit:reload", "spigot:restart")) {
            root.removeCommand(cmd);
        }

        // Commands to hide
        // TODO: rather use bukkit instead of vanilla permission system?
        for (String cmdToHide : List.of( "about", "bukkit:about", "bukkit:plugins",
                "bukkit:ver", "bukkit:version", "pl", "plugins", "ver", "version", "icanhasbukkit",  // icanhasbukkit is an alias

                "msg", "teammsg", "tell", "tm", "trigger", "w",
                "minecraft:msg", "minecraft:teammsg", "minecraft:tell", "minecraft:tm", "minecraft:trigger", "minecraft:w",

                "fastasyncworldedit:fastasyncworldedit",
                "fastasyncworldedit:fawe", "fastasyncworldedit:we", "fastasyncworldedit:worldedit",

                "worldedit", "we", "fawe", "fastasyncworldedit",

                "god", "heal", "region", "regions", "rg", "slay", "ungod",
                "worldguard:god", "worldguard:heal", "worldguard:region", "worldguard:regions",
                "worldguard:rg", "worldguard:slay", "worldguard:ungod")) {

            var cmdNode = root.getChild(cmdToHide);
            if (cmdNode == null) {
                plugin.getLogger().warning("Command " + cmdToHide + " not found in command tree, skipping hiding.");
                continue;
            }

            // Thanks Paper for making this field public!
            cmdNode.requirement = s -> s.hasPermission(3, "test");
        }

    }
}
