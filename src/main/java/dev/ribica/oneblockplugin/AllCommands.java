package dev.ribica.oneblockplugin;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public class AllCommands extends BaseCommand {
    private final OneBlockPlugin plugin;

    @CommandAlias("hub")
    public void hub(Player player) {
        player.teleport(plugin.getHubWorld().getSpawnLocation());
    }
}
