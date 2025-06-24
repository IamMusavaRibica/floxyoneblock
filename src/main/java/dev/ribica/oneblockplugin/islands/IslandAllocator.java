package dev.ribica.oneblockplugin.islands;

import com.sk89q.worldedit.regions.Region;
import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.util.TimeUtils;
import dev.ribica.oneblockplugin.util.WorldUtils;
import lombok.Getter;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IslandAllocator {
    private final OneBlockPlugin plugin;
    private final @Getter World world;  // Added @Getter annotation to expose the world

    public final int ORDER = 274,  // around 75000 available slots
            target = ORDER * (ORDER + 1),
            DELTA = 500,
            FIRST_SLOT = 25,
            Y_COORDINATE = 64;
    public final int[][] f = new int[target][2];

    private final @Getter Map<Integer, Island> forwardMapping = new HashMap<>();
    private final Map<Island, Integer> reverseMapping = new HashMap<>();

    public IslandAllocator(OneBlockPlugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;

        // generate grid coordinates in a spiral pattern
        f[0] = new int[]{0, 0};
        int[][] dirs = {{ 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 }};
        int x = 0, z = 0, step = 1, total = 1, dir = 0;
        while (total < target) {
            for (int i = 0; i < step && total < target; i++) {
                x += dirs[dir][0];
                z += dirs[dir][1];
                f[total][0] = x * DELTA;
                f[total++][1] = z * DELTA;
            }
            dir++;
            dir &= 3;
            if (dir == 0 || dir == 2)
                step++;
        }
    }

    public int getSlot(@NonNull Island island) {
        Integer slot = reverseMapping.get(island);
        return slot != null ? slot : -1;
    }

    public synchronized Location allocate(@NonNull Island island) {
        long start = System.nanoTime();
//        plugin.getComponentLogger().info(Component.text("IslandAllocator.allocate started   " + island.getUuid(), NamedTextColor.DARK_GRAY));

        if (reverseMapping.containsKey(island)) {
            plugin.getLogger().severe("called allocate() on an island that is already allocated: " + island.getUuid());
            return null;
        }

        // find the first available slot
        int slot = FIRST_SLOT;
        while (forwardMapping.containsKey(slot))
            slot++;

        if (slot >= f.length) {
            plugin.getLogger().severe("No more available slots for allocating islands!");
            return null;
        }

        plugin.getComponentLogger().info(Component.text("Allocated island " + island.getUuid() +
                " at slot " + slot + " (" + f[slot][0] + ", " + f[slot][1] + ")", TextColor.color(0x77ff77)));

        forwardMapping.put(slot, island);
        reverseMapping.put(island, slot);

//        plugin.getComponentLogger().info(Component.text(
//                "IslandAllocator.allocate finished   " + island.getUuid()  +
//                " after " + (System.nanoTime() - start) / 1_000_000 + " ms", NamedTextColor.DARK_GRAY));
        return new Location(world, f[slot][0], Y_COORDINATE, f[slot][1]);
    }

    public synchronized void free(Island island) {
        // TODO: add guards against concurrent calls with the same island? Islands.unloadIsland should be safe enough
        Integer slot = reverseMapping.get(island);
        if (slot == null) {
            plugin.getLogger().severe("called free() on an island that is not allocated: " + island.getUuid());
            return;
        }
//        plugin.getLogger().info("Started freeing island " + island.getUuid() + " at slot " + slot);

        long start = TimeUtils.ms();
        Region region = island.getBoundary();
        plugin.getComponentLogger().info(Component.text(
                "after-free filling air at " + world.getName() + " slot: " + slot + " region: " + region.getMinimumPoint() + " to " + region.getMaximumPoint(),
                NamedTextColor.LIGHT_PURPLE
        ));
        WorldUtils.fillAir(world, region);
        forwardMapping.remove(slot);
        reverseMapping.remove(island);
//        plugin.getLogger().info("Freed island " + island.getUuid() + " at slot " + slot + " in " + TimeUtils.msSince(start) + " ms");

    }

    protected Location getIslandOrigin(Island island) {
        Integer slot = reverseMapping.get(island);
        if (slot == null) {
            plugin.getLogger().severe("called getIslandOrigin() on an island that is not allocated: " + island.getUuid());
            return null;
        }
        return new Location(world, f[slot][0], Y_COORDINATE, f[slot][1]);
    }
}
