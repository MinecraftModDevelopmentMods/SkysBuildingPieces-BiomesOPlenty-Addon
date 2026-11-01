package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.io.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.event.TickEvent;
import zone.moddev.mc.skysbuildingpieces.probe.ClientStatus;
import zone.moddev.mc.skysbuildingpiecesbop.basket.*;
import zone.moddev.mc.skysbuildingpiecesbop.client.BasketScreen;

/** Disconnect with the window still open, then inspect the normal player save on reconnect. */
public final class BasketDisconnectClientChecks {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger();

    private static int stage, ticks;
    private static boolean ready;
    private static volatile boolean checked;
    private static Thread closingServerThread;
    private static File savedPlayer;

    @SubscribeEvent(
            priority = EventPriority.HIGHEST)
    public static void tick(TickEvent.ClientTickEvent event) {
        String phase = System.getProperty("skysbuildingpieces.integrationPhase", "");

        if (event.phase != TickEvent.Phase.END || !phase.startsWith("client-basket-disconnect")) {
            return;
        }

        ready |= ClientStatus.complete;
        ClientStatus.complete = false;

        Minecraft minecraft = Minecraft.getInstance();

        minecraft.options.pauseOnLostFocus = false;

        if (++ticks > 4000) {
            throw new AssertionError("Open-basket disconnect check timed out at stage " + stage);
        }
        if (stage == 3) {
            if (!closingServerThread.isAlive()) {
                checkSavedBasket();
                finish(minecraft, "BOP_BASKET_DISCONNECT_PASS window=open units=11 server_stopped=true saved_baskets=1");
            }
            return;
        }
        if (!ready || minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            return;
        }
        if (stage == 0) {
            stage = phase.endsWith("reload") ? 10 : 1;
            minecraft.getSingleplayerServer().execute(() -> {
                ServerPlayer player = minecraft.getSingleplayerServer().getPlayerList()
                        .getPlayer(minecraft.player.getUUID());

                if (phase.endsWith("reload")) {
                    int baskets = 0;

                    for (ItemStack stack : player.getInventory().items) {
                        if (stack.getItem() == Baskets.ITEM) {
                            baskets++;

                            if (!stack.getHoverName().getString().equals("Disconnect plants")
                                    || new BasketInventory(stack).getItem(7).getCount() != 11) {
                                throw new AssertionError("Disconnect changed the basket name, slot or contents");
                            }
                        }
                    }
                    if (baskets != 1) {
                        throw new AssertionError("Reconnect did not retain exactly one basket: " + baskets);
                    }

                    checked = true;
                } else {
                    player.getInventory().clearContent();
                    player.getInventory().selected = 0;

                    ItemStack stack = new ItemStack(Baskets.ITEM);

                    stack.setHoverName(new net.minecraft.network.chat.TextComponent("Disconnect plants"));
                    new BasketInventory(stack).setItem(7,
                            new ItemStack(net.minecraft.world.level.block.Blocks.BIRCH_SAPLING, 11));
                    player.getInventory().setItem(0, stack);
                    player.inventoryMenu.broadcastChanges();
                }
            });
        } else if (stage == 10 && checked) {
            finish(minecraft, "BOP_BASKET_DISCONNECT_RELOAD_PASS baskets=1 units=11 slot=7 custom_name=preserved");
        } else if (stage == 1 && minecraft.player.getMainHandItem().getItem() == Baskets.ITEM) {
            stage = 2;
            minecraft.gameMode.useItem(minecraft.player, minecraft.level, InteractionHand.MAIN_HAND);
        } else if (stage == 2 && minecraft.screen instanceof BasketScreen) {
            stage = 3;
            closingServerThread = minecraft.getSingleplayerServer().getRunningThread();
            savedPlayer = new File(minecraft.getSingleplayerServer()
                    .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
                    "playerdata/" + minecraft.player.getUUID() + ".dat");
            minecraft.level.disconnect();
            minecraft.clearLevel();
            minecraft.setScreen(new TitleScreen());
            // Keep the client alive until the normal server thread has finished
            // saving. Exiting the JVM here can interrupt the player-file rename.
        }
    }

    private static void checkSavedBasket() {
        try (InputStream input = new FileInputStream(savedPlayer)) {
            net.minecraft.nbt.ListTag inventory = net.minecraft.nbt.NbtIo.readCompressed(input)
                    .getList("Inventory", 10);
            int baskets = 0;
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack stack = ItemStack.of(inventory.getCompound(slot));
                if (stack.getItem() == Baskets.ITEM) {
                    baskets++;
                    if (!stack.getHoverName().getString().equals("Disconnect plants")
                            || new BasketInventory(stack).getItem(7).getCount() != 11) {
                        throw new AssertionError("The stopped server did not save the complete basket");
                    }
                }
            }
            if (baskets != 1) {
                throw new AssertionError("The stopped server must save exactly one basket: " + baskets);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot inspect the normal saved player file", failure);
        }
    }

    private static void finish(Minecraft minecraft, String marker) {
        try (PrintWriter writer = new PrintWriter(new File(minecraft.gameDirectory,
                stage == 3 ? "basket-disconnect-pass.txt" : "basket-disconnect-reload-pass.txt"), "UTF-8")) {
            writer.println(marker);
        } catch (IOException failure) {
            throw new RuntimeException(failure);
        }

        LOGGER.info(marker);
        stage = 4;
        minecraft.stop();
    }
}
