package zone.moddev.mc.skysbuildingpiecesbop.basket;

import java.io.*;
import java.nio.file.*;
import java.util.Iterator;
import java.util.zip.*;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;

/** Validate saved baskets before a chunk loader can catch an error and generate a replacement chunk. */
public final class BasketPreflight {
    private BasketPreflight() {
    }

    static boolean hasRegionHeader(long size, String name) throws IOException {
        // Native RegionFile may create an empty file before saving its first chunk.
        if (size == 0) {
            return false;
        }
        if (size < 8192) {
            throw new IOException("Invalid region header: " + name);
        }
        return true;
    }

    public static void check(MinecraftServer server) throws IOException {
        File world = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.LEVEL_DATA_FILE).toFile().getParentFile();

        if (!world.isDirectory()) {
            return;
        }

        zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery validator = new zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery();
        int chunks = 0;

        try (java.util.stream.Stream<Path> paths = Files.walk(world.toPath())) {
            Iterator<Path> files = paths.filter(Files::isRegularFile).iterator();

            while (files.hasNext()) {
                Path path = files.next();
                String name = path.getFileName().toString();

                if (name.matches("r\\.-?\\d+\\.-?\\d+\\.mca")
                        && path.getParent().getFileName().toString().equals("region")) {
                    try (RandomAccessFile region = new RandomAccessFile(path.toFile(), "r")) {
                        if (!hasRegionHeader(region.length(), name)) {
                            continue;
                        }

                        int[] locations = new int[1024];

                        for (int chunkEntryIndex = 0; chunkEntryIndex < 1024; chunkEntryIndex++) {
                            locations[chunkEntryIndex] = region.readInt();
                        }
                        for (int slot = 0; slot < 1024; slot++) {
                            if (locations[slot] != 0) {
                                int location = locations[slot];
                                long offset = (location >>> 8) * 4096L;

                                region.seek(offset);

                                int length = region.readInt();
                                int compression = region.readUnsignedByte();

                                if (length < 1 || length > (location & 255) * 4096 - 4
                                        || offset + 4 + length > region.length()) {
                                    throw new IOException("Invalid chunk extent: " + name + " / " + slot);
                                }

                                byte[] payload = new byte[length - 1];

                                region.readFully(payload);

                                InputStream input = new ByteArrayInputStream(payload);

                                if (compression == 1) {
                                    input = new GZIPInputStream(input);
                                } else if (compression == 2) {
                                    input = new InflaterInputStream(input);
                                } else if (compression != 3) {
                                    throw new IOException("Unsupported chunk compression: " + compression);
                                }
                                try (DataInputStream data = new DataInputStream(input)) {
                                    validator.fixTagCompound(NbtIo.read(data));
                                } catch (IllegalStateException failure) {
                                    throw new IllegalStateException(
                                            name + " / chunk slot " + slot + ": " + failure.getMessage(), failure);
                                }

                                chunks++;
                            }
                        }
                    }
                } else if (name.endsWith(".dat")) {
                    try (PushbackInputStream input = new PushbackInputStream(new FileInputStream(path.toFile()), 2)) {
                        int first = input.read();
                        int second = input.read();

                        if (second >= 0) {
                            input.unread(second);
                        }
                        if (first >= 0) {
                            input.unread(first);
                        }
                        if (first == 31 && second == 139) {
                            validator.fixTagCompound(NbtIo.readCompressed(input));
                        } else {
                            validator.fixTagCompound(NbtIo.read(new DataInputStream(input)));
                        }
                    } catch (IllegalStateException failure) {
                        throw new IllegalStateException(name + ": " + failure.getMessage(), failure);
                    }
                }
            }
        }

        System.out.println("BOP item safety check passed for " + chunks + " saved chunks.");
    }
}
