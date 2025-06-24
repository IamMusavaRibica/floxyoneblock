package dev.ribica.oneblockplugin.util;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;

import java.io.*;
import java.util.Arrays;
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
}
