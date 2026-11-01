package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.io.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.event.TickEvent;
import zone.moddev.mc.skysbuildingpieces.probe.ClientStatus;
import zone.moddev.mc.skysbuildingpiecesbop.basket.*;
import zone.moddev.mc.skysbuildingpiecesbop.client.BasketScreen;

/** Disconnect with the window still open, then inspect the normal player save on reconnect. */
public final class BasketDisconnectClientChecks {
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

        minecraft.gameSettings.pauseOnLostFocus = false;

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
        if (!ready || minecraft.player == null || minecraft.getIntegratedServer() == null) {
            return;
        }
        if (stage == 0) {
            stage = phase.endsWith("reload") ? 10 : 1;
            minecraft.getIntegratedServer().execute(() -> {
                ServerPlayerEntity player = minecraft.getIntegratedServer().getPlayerList()
                        .getPlayerByUUID(minecraft.player.getUniqueID());

                if (phase.endsWith("reload")) {
                    int baskets = 0;

                    for (ItemStack stack : player.inventory.mainInventory) {
                        if (stack.getItem() == Baskets.ITEM) {
                            baskets++;

                            if (!stack.getDisplayName().getString().equals("Disconnect plants")
                                    || new BasketInventory(stack).getStackInSlot(7).getCount() != 11) {
                                throw new AssertionError("Disconnect changed the basket name, slot or contents");
                            }
                        }
                    }
                    if (baskets != 1) {
                        throw new AssertionError("Reconnect did not retain exactly one basket: " + baskets);
                    }

                    checked = true;
                } else {
                    player.inventory.clear();
                    player.inventory.currentItem = 0;

                    ItemStack stack = new ItemStack(Baskets.ITEM);

                    stack.setDisplayName(new net.minecraft.util.text.StringTextComponent("Disconnect plants"));
                    new BasketInventory(stack).setInventorySlotContents(7,
                            new ItemStack(net.minecraft.block.Blocks.BIRCH_SAPLING, 11));
                    player.inventory.setInventorySlotContents(0, stack);
                    player.container.detectAndSendChanges();
                }
            });
        } else if (stage == 10 && checked) {
            finish(minecraft, "BOP_BASKET_DISCONNECT_RELOAD_PASS baskets=1 units=11 slot=7 custom_name=preserved");
        } else if (stage == 1 && minecraft.player.getHeldItemMainhand().getItem() == Baskets.ITEM) {
            stage = 2;
            minecraft.playerController.processRightClick(minecraft.player, minecraft.world, Hand.MAIN_HAND);
        } else if (stage == 2 && minecraft.currentScreen instanceof BasketScreen) {
            stage = 3;
            closingServerThread = minecraft.getIntegratedServer().getExecutionThread();
            savedPlayer = new File(minecraft.getIntegratedServer()
                    .getWorld(net.minecraft.world.dimension.DimensionType.OVERWORLD).getSaveHandler().getWorldDirectory(),
                    "playerdata/" + minecraft.player.getUniqueID() + ".dat");
            minecraft.world.sendQuittingDisconnectingPacket();
            minecraft.loadWorld(null);
            minecraft.displayGuiScreen(new MainMenuScreen());
            // Keep the client alive until the normal server thread has finished
            // saving. Exiting the JVM here can interrupt the player-file rename.
        }
    }

    private static void checkSavedBasket() {
        try (InputStream input = new FileInputStream(savedPlayer)) {
            net.minecraft.nbt.ListNBT inventory = net.minecraft.nbt.CompressedStreamTools.readCompressed(input)
                    .getList("Inventory", 10);
            int baskets = 0;
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack stack = ItemStack.read(inventory.getCompound(slot));
                if (stack.getItem() == Baskets.ITEM) {
                    baskets++;
                    if (!stack.getDisplayName().getString().equals("Disconnect plants")
                            || new BasketInventory(stack).getStackInSlot(7).getCount() != 11) {
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
        try (PrintWriter writer = new PrintWriter(new File(minecraft.gameDir,
                stage == 3 ? "basket-disconnect-pass.txt" : "basket-disconnect-reload-pass.txt"), "UTF-8")) {
            writer.println(marker);
        } catch (IOException failure) {
            throw new RuntimeException(failure);
        }

        System.out.println(marker);
        stage = 4;
        minecraft.shutdown();
    }
}
