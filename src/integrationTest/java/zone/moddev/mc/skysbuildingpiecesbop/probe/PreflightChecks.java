package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.CompressedStreamTools;
import zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketData;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketPreflight;

/** Exercises the file reader with the real, registered item recovery code. */
public final class PreflightChecks {
    private PreflightChecks() {
    }

    public static void run() throws IOException {
        String phase = System.getProperty("skysbuildingpieces.integrationPhase", "native");
        Path fixtures = Paths.get("preflight-fixtures").toAbsolutePath();
        Files.createDirectories(fixtures);
        Path world = Files.createTempDirectory(fixtures, phase + "-");
        Path level = world.resolve("level.dat");
        Path player = world.resolve("playerdata/903b39a1-c052-4ac8-8264-177b9abf8e6a.dat");
        Path region = world.resolve("region/r.0.0.mca");
        Files.createDirectories(player.getParent());
        Files.createDirectories(region.getParent());

        NBTTagCompound saved = inventory(false);
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
            NBTTagCompound basket = tag.getTagList("Inventory", 10).getCompoundTagAt(0);
            require(basket.getString("id").equals(BasketData.ID), "historical basket identity");
            NBTTagCompound contents = basket.getCompoundTag("tag");
            require(contents.getString("note").equals("keep this"), "unrelated custom data");
            require((contents.getTagList("Items", 10).getCompoundTagAt(0).getByte("Count") & 255) == 7,
                    "seven stored saplings survive");
            converted.incrementAndGet();
        });
        require(chunks == 1 && converted.get() == 3, "level, player and legacy region were validated");
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
        System.out.println("BOP_BASKET_PREFLIGHT_PASS unrelated=ignored native=checked legacy_regions=checked"
                + " malformed=blocked source_bytes=unchanged");
    }

    // Keep the filesystem seam private in production and avoid split packages in the probe module.
    private static int scan(Path world, java.util.function.Consumer<NBTTagCompound> validator) throws IOException {
        try {
            java.lang.reflect.Method method = BasketPreflight.class.getDeclaredMethod("scan", Path.class, java.util.function.Consumer.class);
            method.setAccessible(true);
            return (int) method.invoke(null, world, validator);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new AssertionError("Cannot run the preflight check", cause);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("The preflight test seam changed", failure);
        }
    }

    private static NBTTagCompound inventory(boolean duplicate) {
        NBTTagCompound sapling = new NBTTagCompound();
        sapling.setByte("Slot", (byte) 0);
        sapling.setString("id", "minecraft:sapling");
        sapling.setByte("Count", (byte) 7);
        sapling.setShort("Damage", (short) 0);
        NBTTagList items = new NBTTagList();
        items.appendTag(sapling);
        if (duplicate) {
            items.appendTag(sapling.copy());
        }
        NBTTagCompound contents = new NBTTagCompound();
        contents.setTag("Items", items);
        contents.setString("note", "keep this");
        NBTTagCompound basket = new NBTTagCompound();
        basket.setString("id", BasketData.OLD);
        basket.setByte("Count", (byte) 1);
        basket.setTag("tag", contents);
        NBTTagList inventory = new NBTTagList();
        inventory.appendTag(basket);
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("Inventory", inventory);
        return root;
    }

    private static byte[] nbt(NBTTagCompound root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            CompressedStreamTools.write(root, output);
        }
        return bytes.toByteArray();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
