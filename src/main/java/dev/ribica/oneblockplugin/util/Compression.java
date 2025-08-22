package dev.ribica.oneblockplugin.util;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Compression {
    public static byte[] gzipCompress(byte[] in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(out)) {
            gos.write(in);
        }
        return out.toByteArray();
    }

    public static byte[] gzipDecompress(byte[] in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(in))) {
            copy(gis, out);
        }
        return out.toByteArray();
    }


    public static byte[] zstdCompress(byte[] in, int level) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZstdOutputStream zos = new ZstdOutputStream(out, level)) {
            zos.write(in);
        }
        return out.toByteArray();
    }

    public static byte[] zstdDecompress2(byte[] in, int origSize) {
        return Zstd.decompress(in, origSize);
    }

    public static byte[] zstdCompressAndVerify2(byte[] in, int level) throws IOException {
        int origSize = in.length;
        byte[] compressed = Zstd.compress(in, level);
        byte[] restored = Zstd.decompress(compressed, origSize);

        if (!Arrays.equals(restored, in)) {
            String timestamp =  LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss"));
            Files.write(Paths.get("C:\\FloxyOneBlock\\logs\\BUGGED_AGAIN_in_" + timestamp), in);
            Files.write(Paths.get("C:\\FloxyOneBlock\\logs\\BUGGED_AGAIN_compressed_" + timestamp), compressed);
            Files.write(Paths.get("C:\\FloxyOneBlock\\logs\\BUGGED_AGAIN_restored_" + timestamp), restored);
            throw new RuntimeException("Verifying compression failed (data mismatch)! Is this a zstd bug?");
        }
        return compressed;
    }

    public static byte[] zstdDecompress(byte[] in) throws IOException {
        // Zstd.decompress(zstdData, (int) Zstd.getFrameContentSize(zstdData));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZstdInputStream zis = new ZstdInputStream(new ByteArrayInputStream(in))) {
            copy(zis, out);
        }
        return out.toByteArray();
    }

    public static byte[] zstdCompressAndVerify(byte[] in, int level) throws IOException, RuntimeException {
        byte[] compressed = zstdCompress(in, level);
        byte[] restored = zstdDecompress(compressed);

        if (!Arrays.equals(restored, in)) {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");
            String timestamp = now.format(formatter);

            Files.write(Paths.get("C:\\FloxyOneBlock\\logs\\bugged_in_" + timestamp), in);
            Files.write(Paths.get("C:\\FloxyOneBlock\\logs\\bugged_compressed_" + timestamp), compressed);
            Files.write(Paths.get("C:\\FloxyOneBlock\\logs\\bugged_restored_" + timestamp), restored);
            throw new RuntimeException("Verifying compression failed (data mismatch)! Is this a zstd bug?");
        }
        return compressed;
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
    }

    public static void main(String[] args) throws IOException {
        String filePath = "C:\\FloxyOneBlock\\logs\\BUGGED_ISLAND.txt";
        /*
        try {
            String base64Content = new String(Files.readAllBytes(Paths.get(filePath)));

            byte[] decodedBytes = Base64.getDecoder().decode(base64Content);
            byte[] gzipDecompressed = gzipDecompress(decodedBytes);

            System.out.println("Original length: " + gzipDecompressed.length);
            System.out.println("Gzip compressed length: " + decodedBytes.length);
            byte[] zstd1 = zstdCompressAndVerify(gzipDecompressed, 3);
            System.out.println("Zstd compressed length: " + zstd1.length);
        } catch (IOException e) {
            System.err.println("Error reading the file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.err.println("Error decoding Base64: " + e.getMessage());
        }

         */

        byte[] someData = "gwripgrpwgp4wg4wrjghopšqwrgkqwškg4qekg9'0q4uth94812zt12380rz/)7878gh3580g249rtiweq0ti90'j90HG982Z294T8Z24GH2490G284G2IH235ZUH523UZ2905Z25H25'UČ35Š6ČZ35ĐZĆ23ĐTŽĆ24HG3HIJ350HIO2".getBytes();

        byte[] compressed = zstdCompressAndVerify(someData, 14);
        Files.write(Paths.get("here.txt"), compressed);

    }
}
