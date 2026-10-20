package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.io.*;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
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

        WorldServer world = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
        File players = new File(world.getSaveHandler().getWorldDirectory(), "playerdata");
        int loaded = 0;
        File[] files = players.listFiles((directory, name) -> name.endsWith(".dat"));

        if (files != null) {
            for (File file : files) {
                NBTTagCompound raw;

                try (InputStream input = new FileInputStream(file)) {
                    raw = CompressedStreamTools.readCompressed(input);
                }
                if (!hasBasket(raw)) {
                    continue;
                }

                UUID uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
                EntityPlayerMP player = FakePlayerFactory.get(world, new GameProfile(uuid, "MigrationTest"));

                world.getSaveHandler().getPlayerNBTManager().readPlayerData(player);
                world.getSaveHandler().getPlayerNBTManager().writePlayerData(player);
                loaded++;
            }
        }

        System.out.println("BOP_BASKET_PLAYER_LOADER_PASS profiles=" + loaded);
    }

    private static boolean hasBasket(NBTBase value) {
        if (value instanceof NBTTagCompound) {
            NBTTagCompound tag = (NBTTagCompound) value;

            if (BasketData.isBasket(tag.getString("id"))) {
                return true;
            }
            for (String key : tag.getKeySet()) {
                if (hasBasket(tag.getTag(key))) {
                    return true;
                }
            }
        } else if (value instanceof NBTTagList) {
            for (NBTBase child : (NBTTagList) value) {
                if (hasBasket(child)) {
                    return true;
                }
            }
        }

        return false;
    }
}
