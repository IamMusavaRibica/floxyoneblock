package dev.ribica.oneblockplugin.items.impl;

import dev.ribica.oneblockplugin.items.RawItem;
import lombok.Getter;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class FancyGold extends RawItem {
    private final @Getter int maxStackSize = 7;


    public FancyGold() {
        super("fancy_gold", Key.key("minecraft:gold_nugget"));
    }

    @Override
    public void onInteract(PlayerInteractEvent event, Player player, ItemStack itemStack) {
        // Only handle right-click actions
        var action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        var location = player.getLocation();

        if (player.isSneaking()) {
            // Spawn flame particles in a spiral pattern when shift-right-clicking
            for (double y = 0; y < 2; y += 0.1) {
                double radius = y / 2;
                double x = Math.cos(y * Math.PI * 2) * radius;
                double z = Math.sin(y * Math.PI * 2) * radius;
                location.getWorld().spawnParticle(
                        Particle.FLAME,
                        location.clone().add(x, y, z),
                        1, 0, 0, 0, 0
                );
            }
        } else {
            // Spawn sparkle particles in a circle when right-clicking
            for (int i = 0; i < 36; i++) {
                double angle = i * 10 * Math.PI / 180;
                double x = Math.cos(angle) * 1.5;
                double z = Math.sin(angle) * 1.5;
                location.getWorld().spawnParticle(
                        Particle.ELECTRIC_SPARK,
                        location.clone().add(x, 1, z),
                        5, 0.1, 0.1, 0.1, 0
                );
            }
        }
    }
}
