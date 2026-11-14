package zone.moddev.mc.skysbuildingpiecesbop.basket;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.server.MinecraftServer;
import zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery;

/** Validate saved inventories before the normal loader can discard an invalid basket. */
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
        File folder = server.isDedicatedServer() ? server.getFile(server.getFolderName())
                : new File(server.getDataDirectory(), "saves/" + server.getFolderName());
        Path world = folder.toPath();
        LegacyItemRecovery validator = new LegacyItemRecovery();
        int chunks = scan(world, validator::fixTagCompound);
        System.out.println("BOP item safety check passed for " + chunks + " saved chunks.");
    }

    static int scan(Path world, Consumer<NBTTagCompound> validator) throws IOException {
        Path root = world.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return 0;
        }

        int chunks = 0;
        try (Stream<Path> paths = Files.walk(root)) {
            Iterator<Path> files = paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).iterator();
            while (files.hasNext()) {
                Path path = files.next();
                Path relative = root.relativize(path);
                if (isRegion(relative)) {
                    chunks += readRegion(path, validator);
                } else if (isInventoryFile(relative)) {
                    try {
                        validator.accept(readInventoryFile(path));
                    } catch (IOException failure) {
                        throw unreadable(path, "", failure);
                    } catch (RuntimeException failure) {
                        throw invalidContents(path, "", failure);
                    }
                }
            }
        }
        return chunks;
    }

    private static boolean isInventoryFile(Path relative) {
        // Inventories live in these native files, not arbitrary mod-owned .dat files.
        if (relative.getNameCount() == 1) {
            return relative.toString().equals("level.dat");
        }
        return relative.getNameCount() == 2 && relative.getName(0).toString().equals("playerdata")
                && relative.getFileName().toString().matches(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.dat");
    }

    private static boolean isRegion(Path relative) {
        Path directory = relative.getParent();
        if (directory == null || !relative.getFileName().toString().matches("r\\.-?\\d+\\.-?\\d+\\.mca")) {
            return false;
        }
        String name = directory.getFileName().toString();
        if (!name.equals("region")) {
            return false;
        }

        Path dimension = directory.getParent();
        if (dimension == null) {
            return true;
        }
        if (dimension.getNameCount() == 1) {
            return dimension.toString().matches("DIM-?\\d+");
        }
        return false;
    }

    private static NBTTagCompound readInventoryFile(Path path) throws IOException {
        try (PushbackInputStream input = new PushbackInputStream(Files.newInputStream(path), 2)) {
            int first = input.read();
            int second = input.read();
            if (second >= 0) {
                input.unread(second);
            }
            if (first >= 0) {
                input.unread(first);
            }
            NBTTagCompound root = first == 31 && second == 139
                    ? CompressedStreamTools.readCompressed(input)
                    : CompressedStreamTools.read(new DataInputStream(input));
            return requireCompound(root);
        }
    }

    private static int readRegion(Path path, Consumer<NBTTagCompound> validator) throws IOException {
        int slot = -1;
        try (RandomAccessFile region = new RandomAccessFile(path.toFile(), "r")) {
            if (!hasRegionHeader(region.length(), path.toString())) {
                return 0;
            }
            int[] locations = new int[1024];
            for (int entry = 0; entry < locations.length; entry++) {
                locations[entry] = region.readInt();
            }

            int chunks = 0;
            for (slot = 0; slot < locations.length; slot++) {
                int location = locations[slot];
                if (location == 0) {
                    continue;
                }
                long offset = (location >>> 8) * 4096L;
                if (offset < 8192 || offset + 5 > region.length()) {
                    throw new IOException("Invalid chunk offset");
                }
                region.seek(offset);
                int length = region.readInt();
                int compression = region.readUnsignedByte();
                if (length < 1 || length > (location & 255) * 4096 - 4
                        || offset + 4 + length > region.length()) {
                    throw new IOException("Invalid chunk extent");
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
                    validator.accept(requireCompound(CompressedStreamTools.read(data)));
                }
                chunks++;
            }
            return chunks;
        } catch (IOException failure) {
            throw unreadable(path, slot < 0 ? "" : " / chunk slot " + slot, failure);
        } catch (RuntimeException failure) {
            throw invalidContents(path, slot < 0 ? "" : " / chunk slot " + slot, failure);
        }
    }

    private static NBTTagCompound requireCompound(NBTTagCompound root) throws IOException {
        if (root == null) {
            throw new IOException("Expected a saved NBT compound");
        }
        return root;
    }

    private static IOException unreadable(Path path, String position, IOException failure) {
        String detail = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return new IOException("Cannot read saved item data in " + path + position + ": " + detail
                + ". Check this file or restore it from a backup before loading the world.", failure);
    }

    private static IllegalStateException invalidContents(Path path, String position, RuntimeException failure) {
        return new IllegalStateException("Cannot validate saved item data in " + path + position + ": "
                + failure.getMessage(), failure);
    }
}
