package dev.ribica.oneblockplugin.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Zip {
    public static void unzip(Path zipFile, Path destinationDir) throws IOException {
        Path destRoot = destinationDir.toAbsolutePath().normalize();
        Files.createDirectories(destRoot);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    Path dir = safeResolve(destRoot, entry.getName());
                    Files.createDirectories(dir);
                } else {
                    Path file = safeResolve(destRoot, entry.getName());
                    Files.createDirectories(file.getParent());
                    // Copy the current entry's bytes to disk (blocking/sync)
                    Files.copy(zis, file, StandardCopyOption.REPLACE_EXISTING);

                    // Best-effort: preserve last-modified time if present
                    try {
                        FileTime lm = entry.getLastModifiedTime();
                        if (lm != null) Files.setLastModifiedTime(file, lm);
                    } catch (UnsupportedOperationException | NullPointerException ignored) {
                        // Some zips/JDKs won't have this metadata — ignore quietly.
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static Path safeResolve(Path destRoot, String entryName) throws IOException {
        Path target = destRoot.resolve(entryName).normalize();
        if (!target.startsWith(destRoot)) {
            throw new IOException("Blocked unsafe ZIP entry: " + entryName);
        }
        return target;
    }
}
