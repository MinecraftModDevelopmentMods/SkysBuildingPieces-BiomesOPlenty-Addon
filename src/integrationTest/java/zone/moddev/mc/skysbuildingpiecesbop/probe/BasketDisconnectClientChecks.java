package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.io.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.skysbuildingpieces.probe.ClientStatus;
import zone.moddev.mc.skysbuildingpiecesbop.basket.*;
import zone.moddev.mc.skysbuildingpiecesbop.client.BasketScreen;

/** Disconnect with the window still open, then inspect the normal player save on reconnect. */
public final class BasketDisconnectClientChecks {
    private static int stage, ticks;
    private static boolean controlled;
    private static volatile boolean checked;

    @SubscribeEvent(
            priority = EventPriority.HIGHEST)
    public static void tick(TickEvent.ClientTickEvent event) {
        String phase = System.getProperty("skysbuildingpieces.integrationPhase", "");

        if (event.phase != TickEvent.Phase.END || !phase.startsWith("client-basket-disconnect")) {
            return;
        }

        ClientStatus.complete = false;

        Minecraft minecraft = Minecraft.getMinecraft();

        minecraft.gameSettings.pauseOnLostFocus = false;

        if (++ticks > 4000) {
            throw new AssertionError("Open-basket disconnect check timed out at stage " + stage);
        }
        if (!controlled && minecraft.getIntegratedServer() != null) {
            MinecraftForge.EVENT_BUS.unregister(zone.moddev.mc.skysbuildingpieces.probe.RuntimeProbe.proxy);
            controlled = true;
        }
        if (minecraft.player == null || minecraft.getIntegratedServer() == null) {
            return;
        }
        if (stage == 0) {
            stage = phase.endsWith("reload") ? 10 : 1;
            minecraft.getIntegratedServer().addScheduledTask(() -> {
                EntityPlayerMP player = minecraft.getIntegratedServer().getPlayerList()
                        .getPlayerByUUID(minecraft.player.getUniqueID());

                if (phase.endsWith("reload")) {
                    int baskets = 0;

                    for (ItemStack stack : player.inventory.mainInventory) {
                        if (stack.getItem() == Baskets.ITEM) {
                            baskets++;

                            if (!stack.getDisplayName().equals("Disconnect plants")
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

                    stack.setStackDisplayName("Disconnect plants");
                    new BasketInventory(stack).setInventorySlotContents(7,
                            new ItemStack(net.minecraft.init.Blocks.SAPLING, 11, 2));
                    player.inventory.setInventorySlotContents(0, stack);
                    player.inventoryContainer.detectAndSendChanges();
                }
            });
        } else if (stage == 10 && checked) {
            finish(minecraft, "BOP_BASKET_DISCONNECT_RELOAD_PASS baskets=1 units=11 slot=7 custom_name=preserved");
        } else if (stage == 1 && minecraft.player.getHeldItemMainhand().getItem() == Baskets.ITEM) {
            stage = 2;
            minecraft.playerController.processRightClick(minecraft.player, minecraft.world, EnumHand.MAIN_HAND);
        } else if (stage == 2 && minecraft.currentScreen instanceof BasketScreen) {
            stage = 3;
            minecraft.world.sendQuittingDisconnectingPacket();
            minecraft.loadWorld(null);
            minecraft.displayGuiScreen(new GuiMainMenu());
            finish(minecraft, "BOP_BASKET_DISCONNECT_PASS window=open units=11");
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
