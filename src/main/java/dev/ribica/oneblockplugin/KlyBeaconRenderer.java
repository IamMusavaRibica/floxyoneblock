package dev.ribica.oneblockplugin;

import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;


@RequiredArgsConstructor
public class KlyBeaconRenderer {
    private final JavaPlugin plugin;

    private final Map<Integer, BeaconBlockDisplay> locations = new HashMap<>();
    private long currentTick = 0;
    private int counter = 0;

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void tick() {
        currentTick++;
        locations.values().forEach(BeaconBlockDisplay::update);
    }

    public int newBeacon(Location loc) {
        if (loc.getWorld() == null) {
            throw new IllegalArgumentException("Location world cannot be null");
        }
        BeaconBlockDisplay bde = new BeaconBlockDisplay(counter++, loc);
        locations.put(bde.id, bde);
        return bde.id;
    }

    public void removeBeacon(int id) {
        if (!locations.containsKey(id)) {
            throw new IllegalArgumentException("No beam with id " + id);
        }
        locations.remove(id).remove();
    }

    private class BeaconBlockDisplay {
        private final int id;

        private final BlockDisplay bd;
        private final Matrix4f initial = new Matrix4f()
                .scale(.5f, .5f, .5f)
                .translate(-0.5f, 0, -0.5f);
        private final float PI = (float) Math.PI;

        public BeaconBlockDisplay(int id, Location location0) {
            this.id = id;
            Location location = location0.clone();
            location.setYaw(0);    // yaw 0 = u pozitivnom smjeru Z osi (prema jugu)
            location.setPitch(0);

            bd = location.getWorld().spawn(location.clone(), BlockDisplay.class);
            bd.setBlock(Material.BEACON.createBlockData());
            bd.setTransformationMatrix(initial);
            bd.addScoreboardTag("beacon_" + id);
            bd.setPersistent(false);
            bd.setInterpolationDuration(2);
        }

        public void update() {
            float rotationAmount = 2*PI/100 * currentTick;
            float yOffset = 0.1f * (float)Math.sin(0.05f * currentTick);
            bd.setInterpolationDelay(0);
            bd.setTransformationMatrix(
                    new Matrix4f()
                            .translate(0, yOffset, 0)
                            .rotateY(rotationAmount)
                            .mul(initial)
            );
        }

        public void remove() {
            if (bd != null && bd.isValid()) {
                bd.remove();
            }
        }
    }
}
