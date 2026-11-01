package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.io.*;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.*;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.event.TickEvent;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketData;

/** Exercise the real player-save loader only in explicitly selected disposable Sylvester tests. */
public final class BasketPlayerUpgrade {
    private static boolean ran;

    @SubscribeEvent(
            priority = EventPriority.HIGHEST)
    public static void tick(TickEvent.ServerTickEvent event) throws Exception {
        if (ran || event.phase != TickEvent.Phase.START
                || !System.getProperty("skysbuildingpieces.integrationPhase", "").startsWith("sylvester")) {
            return;
        }

        ran = true;

        ServerWorld world = ServerLifecycleHooks.getCurrentServer()
                .getLevel(net.minecraft.world.World.OVERWORLD);
        File players = new File(world.getServer().getWorldPath(net.minecraft.world.storage.FolderName.ROOT).toFile(), "playerdata");
        int loaded = 0;
        File[] files = players.listFiles((directory, name) -> name.endsWith(".dat"));

        if (files != null) {
            for (File file : files) {
                CompoundNBT raw;

                try (InputStream input = new FileInputStream(file)) {
                    raw = CompressedStreamTools.readCompressed(input);
                }
                if (!hasBasket(raw)) {
                    continue;
                }

                UUID uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
                ServerPlayerEntity player = FakePlayerFactory.get(world, new GameProfile(uuid, "MigrationTest"));

                world.getServer().getPlayerList().load(player);
                net.minecraftforge.fml.common.ObfuscationReflectionHelper.findMethod(
                        net.minecraft.server.management.PlayerList.class, "func_72391_b",
                        ServerPlayerEntity.class).invoke(world.getServer().getPlayerList(), player);
                loaded++;
            }
        }

        System.out.println("BOP_BASKET_PLAYER_LOADER_PASS profiles=" + loaded);
    }

    private static boolean hasBasket(INBT value) {
        if (value instanceof CompoundNBT) {
            CompoundNBT tag = (CompoundNBT) value;

            if (BasketData.isBasket(tag.getString("id"))) {
                return true;
            }
            for (String key : tag.getAllKeys()) {
                if (hasBasket(tag.get(key))) {
                    return true;
                }
            }
        } else if (value instanceof ListNBT) {
            for (INBT child : (ListNBT) value) {
                if (hasBasket(child)) {
                    return true;
                }
            }
        }

        return false;
    }
}
