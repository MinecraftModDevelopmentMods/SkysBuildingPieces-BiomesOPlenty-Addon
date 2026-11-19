package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.*;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.event.TickEvent;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketData;

/** Exercise the real player-save loader only in explicitly selected disposable Sylvester tests. */
public final class BasketPlayerUpgrade {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger();

    private static boolean ran;

    public static void tick(TickEvent.ServerTickEvent.Pre event) {
        try {
            audit();
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot audit the disposable player saves", failure);
        }
    }

    private static void audit() throws Exception {
        if (ran || !System.getProperty("skysbuildingpieces.integrationPhase", "").startsWith("sylvester")) {
            return;
        }

        ran = true;

        ServerLevel world = ServerLifecycleHooks.getCurrentServer()
                .getLevel(net.minecraft.world.level.Level.OVERWORLD);
        var server = world.getServer();
        File legacyPlayers = server.getWorldPath(LevelResource.ROOT).resolve("playerdata").toFile();
        File currentPlayers = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile();
        // Read a profile once when Minecraft has already copied it to the new directory.
        var profiles = new java.util.TreeMap<String, File>();
        for (File directory : new File[] {legacyPlayers, currentPlayers}) {
            File[] files = directory.listFiles((parent, name) -> name.endsWith(".dat"));
            if (files != null) {
                for (File file : files) {
                    profiles.put(file.getName(), file);
                }
            }
        }
        int loaded = 0;
        for (File file : profiles.values()) {
            CompoundTag raw;
            try (InputStream input = new FileInputStream(file)) {
                raw = NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            }
            if (!hasBasket(raw)) {
                continue;
            }
            UUID uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
            ServerPlayer player = BopProbeItems.player(world, new GameProfile(uuid, "MigrationTest"));
            BopProbeItems.loadPlayer(player);
            savePlayer(player);
            loaded++;
        }

        LOGGER.info("BOP_BASKET_PLAYER_LOADER_PASS profiles=" + loaded);
    }

    private static void savePlayer(ServerPlayer player) throws Exception {
        java.lang.reflect.Method save = null;
        for (java.lang.reflect.Method method : net.minecraft.server.players.PlayerList.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isProtected(method.getModifiers()) && method.getReturnType() == void.class
                    && java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[] { ServerPlayer.class })) {
                if (save != null) {
                    throw new IllegalStateException("The native player-save method is ambiguous");
                }
                save = method;
            }
        }
        if (save == null) {
            throw new IllegalStateException("The native player-save method is missing");
        }
        save.setAccessible(true);
        save.invoke(player.level().getServer().getPlayerList(), player);
    }

    private static boolean hasBasket(Tag value) {
        if (value instanceof CompoundTag) {
            CompoundTag tag = (CompoundTag) value;

            if (BasketData.isBasket(tag.getStringOr("id", ""))) {
                return true;
            }
            for (String key : tag.keySet()) {
                if (hasBasket(tag.get(key))) {
                    return true;
                }
            }
        } else if (value instanceof ListTag) {
            for (Tag child : (ListTag) value) {
                if (hasBasket(child)) {
                    return true;
                }
            }
        }

        return false;
    }
}
