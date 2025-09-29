package dev.ribica.oneblockplugin;

import lombok.RequiredArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;


@RequiredArgsConstructor
public class FakeBeaconRenderer {
    private final JavaPlugin plugin;

    private final Map<Integer, Beam> locations = new HashMap<>();
    private long currentTick = 0;
    private int counter = 0;

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void tick() {
        currentTick++;
        locations.values().forEach(Beam::update);
    }

    public int newBeam(Location loc) {
        if (loc.getWorld() == null) {
            throw new IllegalArgumentException("Location world cannot be null");
        }
        Beam beam = new Beam(counter++, loc);
        locations.put(beam.id, beam);
        return beam.id;
    }

    public void removeBeam(int id) {
        if (!locations.containsKey(id)) {
            throw new IllegalArgumentException("No beam with id " + id);
        }
        locations.remove(id).remove();
    }

    private class Beam {
        private final Location location;
        private final int id;

        private final TextDisplay[] sides = new TextDisplay[4];
        private final Matrix4f[] matrices = new Matrix4f[4];  // initial transformation matrices
        private final float BEAM_LENGTH = 1000f;
        private final float BEAM_WIDTH = 0.4f;
        private final float PI = (float) Math.PI;

        public Beam(int id, Location location0) {
            this.id = id;
            location = location0.clone();
            location.setYaw(0);    // yaw 0 = u pozitivnom smjeru Z osi (prema jugu)
            location.setPitch(0);

            for (int i = 0; i < 4; i++) {
                sides[i] = makeEntity(i);  // 0 = south, 1 = east, 2 = north, 3 = west
            }
        }

        public void update() {
            float rotationAmount = 2*PI/100 * currentTick;
            for (int i = 0; i < 4; i++) {
                sides[i].setTransformationMatrix(
                        new Matrix4f().rotateY(rotationAmount).mul(matrices[i])
                );
                sides[i].setInterpolationDelay(0);
            }
        }

        public void remove() {
            for (TextDisplay td : sides) {
                if (td != null && td.isValid()) {
                    td.remove();
                }
            }
        }
        private TextDisplay makeEntity(int sideNum) {
            // transformacije citamo s desna na lijevo (ovdje u kodu odozdo prema gore)
            matrices[sideNum] = new Matrix4f()
                    .rotateY(PI / 2 * sideNum)  // rotiramo oko Y osi
                    .translate(0, 0, BEAM_WIDTH / 2)     // odmaknemo od pivota prema naprijed
                    .scale(BEAM_WIDTH, BEAM_LENGTH, 0)   // length ide prema gore,
                                                            // debljina u z smjeru je nebitna jer je ovo 2D
                    .translate(-.5f, 0, 0)         // tako da pivot (ishodište) bude u sredini kvadrata

                    // standardna transformacija na veličinu 1x1 strane jednog bloka:
                    .translate(.4f, 0, 0)  // donji lijevi kut je na poziciji entitya,
                    .scale(8, 4, 1)        // a kvadrat se prostire u pozitivnom smjeru X i Y osi
            ;

            TextDisplay td = location.getWorld().spawn(location.clone(), TextDisplay.class);
            td.setText(" ");
            td.setBackgroundColor(Color.fromARGB(166, 0, 255, 122));
            td.setTransformationMatrix(matrices[sideNum]);
            td.addScoreboardTag("side_" + sideNum + "_" + id);
            td.setInterpolationDuration(2);

//            td.setGlowing(true);
//            td.setGlowColorOverride(Color.fromARGB(166, 0, 255, 122));
            return td;
        }
    }
}
