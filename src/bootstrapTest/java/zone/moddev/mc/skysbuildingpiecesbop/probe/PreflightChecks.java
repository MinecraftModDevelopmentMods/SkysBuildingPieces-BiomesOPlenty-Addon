package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketData;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketPreflight;

/** Exercises the file reader with the real, registered item recovery code. */
public final class PreflightChecks {
    private PreflightChecks() {
    }

    public static void run() throws IOException {
        String phase = System.getProperty("skysbuildingpieces.integrationPhase");
        Path world = Path.of("preflight-fixtures", phase).toAbsolutePath();
        Files.createDirectories(world.getParent());
        Files.createDirectory(world);
        Path level = world.resolve("level.dat");
        Path player = world.resolve("playerdata/903b39a1-c052-4ac8-8264-177b9abf8e6a.dat");
        Path region = world.resolve("entities/r.0.0.mca");
        Files.createDirectories(player.getParent());
        Files.createDirectories(region.getParent());

        CompoundTag saved = inventory(false);
        byte[] raw = nbt(saved);
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(level))) {
            output.write(raw);
        }
        Files.write(player, raw);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream output = new DeflaterOutputStream(compressed)) {
            output.write(raw);
        }
        ByteBuffer bytes = ByteBuffer.allocate(12288);
        bytes.putInt(0, 2 << 8 | 1);
        bytes.position(8192);
        bytes.putInt(compressed.size() + 1);
        bytes.put((byte) 2);
        bytes.put(compressed.toByteArray());
        Files.write(region, bytes.array());
        Path unrelated = world.resolve("data/another-mod/custom.dat");
        Files.createDirectories(unrelated.getParent());
        Files.write(unrelated, new byte[] {10, 0});

        byte[] levelBefore = Files.readAllBytes(level);
        byte[] regionBefore = Files.readAllBytes(region);
        LegacyItemRecovery recovery = new LegacyItemRecovery();
        AtomicInteger converted = new AtomicInteger();
        int chunks = scan(world, tag -> {
            recovery.fixTagCompound(tag);
            CompoundTag basket = tag.getList("Inventory", 10).getCompound(0);
            require(basket.getString("id").equals(BasketData.ID), "historical basket identity");
            CompoundTag contents = BasketData.savedCustomData(basket);
            require(contents.getString("note").equals("keep this"), "unrelated custom data");
            require(BasketData.count(contents.getList("Items", 10).getCompound(0)) == 7,
                    "seven stored saplings survive");
            converted.incrementAndGet();
        });
        require(chunks == 1 && converted.get() == 3, "level, player and entity region were validated");
        require(Arrays.equals(levelBefore, Files.readAllBytes(level))
                && Arrays.equals(raw, Files.readAllBytes(player))
                && Arrays.equals(regionBefore, Files.readAllBytes(region)), "preflight leaves saved bytes alone");
        require(Arrays.equals(new byte[] {10, 0}, Files.readAllBytes(unrelated)), "unrelated file is untouched");

        Files.write(player, nbt(inventory(true)));
        try {
            scan(world, recovery::fixTagCompound);
            throw new AssertionError("Duplicate basket slots were accepted");
        } catch (IllegalStateException expected) {
            String message = expected.getMessage();
            require(message.contains(player.toString())
                    && (message.contains("duplicate slot") || message.contains("duplicate inventory slot")),
                    "invalid basket names its player file: " + message);
        }
        Files.write(player, new byte[] {10, 0});
        try {
            scan(world, recovery::fixTagCompound);
            throw new AssertionError("Truncated native inventory was accepted");
        } catch (IOException expected) {
            require(expected.getMessage().contains(player.toString()), "unreadable inventory names its file");
        }
        Files.write(player, raw);
        scan(world, recovery::fixTagCompound);
        System.out.println("BOP_BASKET_PREFLIGHT_PASS unrelated=ignored native=checked entities=checked"
                + " malformed=blocked source_bytes=unchanged");
    }

    // Keep the filesystem seam private in production and avoid split packages in the probe module.
    private static int scan(Path world, java.util.function.Consumer<CompoundTag> validator) throws IOException {
        try {
            var method = BasketPreflight.class.getDeclaredMethod("scan", Path.class, java.util.function.Consumer.class);
            method.setAccessible(true);
            return (int) method.invoke(null, world, validator);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new AssertionError("Cannot run the preflight check", cause);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("The preflight test seam changed", failure);
        }
    }

    private static CompoundTag inventory(boolean duplicate) {
        CompoundTag sapling = new CompoundTag();
        sapling.putByte("Slot", (byte) 0);
        sapling.putString("id", "minecraft:oak_sapling");
        sapling.putByte("Count", (byte) 7);
        sapling.putShort("Damage", (short) 0);
        ListTag items = new ListTag();
        items.add(sapling);
        if (duplicate) {
            items.add(sapling.copy());
        }
        CompoundTag contents = new CompoundTag();
        contents.put("Items", items);
        contents.putString("note", "keep this");
        CompoundTag basket = new CompoundTag();
        basket.putString("id", BasketData.OLD);
        basket.putByte("Count", (byte) 1);
        basket.put("tag", contents);
        ListTag inventory = new ListTag();
        inventory.add(basket);
        CompoundTag root = new CompoundTag();
        root.put("Inventory", inventory);
        return root;
    }

    private static byte[] nbt(CompoundTag root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.write(root, output);
        }
        return bytes.toByteArray();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
