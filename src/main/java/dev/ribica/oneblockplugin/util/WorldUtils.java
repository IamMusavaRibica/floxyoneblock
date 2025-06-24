package dev.ribica.oneblockplugin.util;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.mask.ExistingBlockMask;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import dev.ribica.oneblockplugin.OneBlockPlugin;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.io.*;

public class WorldUtils {
    public static void fillBlockData(@NonNull org.bukkit.World world, @NonNull Region region, @NonNull BlockData blockData) {
        World we_world = BukkitAdapter.adapt(world);
        try (EditSession s = WorldEdit.getInstance().newEditSession(we_world)) {
            s.replaceBlocks(region, new ExistingBlockMask(we_world), BukkitAdapter.adapt(blockData));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void fillAir(@NonNull org.bukkit.World world, @NonNull Region region) {
        fillBlockData(world, region, Material.AIR.createBlockData());
    }

    public static void setBlockData(@NonNull Location location, @NonNull BlockData blockData) {
        World we_world = BukkitAdapter.adapt(location.getWorld());
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        OneBlockPlugin.getInstance().getLogger().info("Setting block at " + x + ", " + y + ", " + z + " to " + blockData);
        try (EditSession s = WorldEdit.getInstance().newEditSession(we_world)) {
            s.setBlock(x, y, z, BukkitAdapter.adapt(blockData));
        }
    }

    public static void pasteSchematic(byte @NonNull [] schematicData, @NonNull Location loc) throws IOException {
        byte[] gzipped = Compression.gzipCompress(schematicData);
        Clipboard clipboard;
        try (ClipboardReader reader = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getReader(new ByteArrayInputStream(gzipped))) {
            clipboard = reader.read();
        }
        World we_World = BukkitAdapter.adapt(loc.getWorld());
        try (EditSession session = WorldEdit.getInstance().newEditSession(we_World)) {
            Operations.complete(new ClipboardHolder(clipboard)
                    .createPaste(session)
                    .to(BukkitAdapter.asBlockVector(loc))
                    .ignoreAirBlocks(true)
                    .copyEntities(true)
                    .copyBiomes(true)
                    .build());
        }
    }

    public static byte[] getRawSchematic(@NonNull Region region, @NonNull Location origin) throws IOException {
        if (region.getWorld() == null)
            throw new IllegalArgumentException("Region must have a world!");

//        OneBlockPlugin.getInstance().getLogger().info("calling getRawSchematic for region: " + region.getMinimumPoint() + " to " + region.getMaximumPoint() + " in world: " + region.getWorld().getName());

        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        try (EditSession session = WorldEdit.getInstance().newEditSession(region.getWorld())) {
            clipboard.setOrigin(BukkitAdapter.asBlockVector(origin));
            ForwardExtentCopy copy = new ForwardExtentCopy(session, region, clipboard, region.getMinimumPoint());
            copy.setCopyingBiomes(true);
            copy.setCopyingEntities(true);
            Operations.complete(copy);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(baos)) {
            writer.write(clipboard);
        } finally {
            clipboard.close();
        }

        return baos.toByteArray();
    }

    public static boolean compareBlockLocation(@NonNull Location loc1, @NonNull Location loc2) {
        return loc1.getBlockX() == loc2.getBlockX() &&
               loc1.getBlockY() == loc2.getBlockY() &&
               loc1.getBlockZ() == loc2.getBlockZ() &&
               loc1.getWorld().equals(loc2.getWorld());
    }
}
