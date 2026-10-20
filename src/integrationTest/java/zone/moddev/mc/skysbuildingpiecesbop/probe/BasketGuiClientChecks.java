package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.io.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import zone.moddev.mc.skysbuildingpieces.probe.ClientStatus;
import zone.moddev.mc.skysbuildingpiecesbop.basket.*;
import zone.moddev.mc.skysbuildingpiecesbop.client.BasketScreen;

/** Normal GUI packets between a packaged client and its integrated server. */
public final class BasketGuiClientChecks {
    private static int stage, ticks, lastStage = -1, deathCheckAt;
    private static boolean ready, controlled;
    private static volatile boolean serverChecked;

    @SubscribeEvent(
            priority = EventPriority.HIGHEST)
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !System.getProperty("skysbuildingpieces.integrationPhase", "").equals("client-basket")) {
            return;
        }

        ready |= ClientStatus.complete;
        ClientStatus.complete = false;

        if (++ticks > 6000) {
            throw new AssertionError("Basket GUI network test timed out at stage " + stage);
        }

        Minecraft minecraft = Minecraft.getMinecraft();

        minecraft.gameSettings.pauseOnLostFocus = false;

        if (stage != lastStage) {
            System.out.println("BASKET_GUI_STAGE " + stage);
            lastStage = stage;
        }
        if (!controlled && minecraft.getIntegratedServer() != null) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS
                    .unregister(zone.moddev.mc.skysbuildingpieces.probe.RuntimeProbe.proxy);
            controlled = true;
        }
        if (!ready || minecraft.player == null || minecraft.getIntegratedServer() == null) {
            return;
        }
        if (stage == 3 && ticks % 40 == 0) {
            System.out
                    .println("BASKET_GUI_CLIENT_STATE container=" + minecraft.player.openContainer.getClass().getName()
                            + " first=" + minecraft.player.openContainer.getSlot(0).getStack() + " can_interact="
                            + minecraft.player.openContainer.canInteractWith(minecraft.player));
            minecraft.getIntegratedServer().addScheduledTask(() -> {
                EntityPlayerMP player = minecraft.getIntegratedServer().getPlayerList().getPlayerByUUID(minecraft.player.getUniqueID());

                System.out.println("BASKET_GUI_SERVER_STATE first=" + player.openContainer.getSlot(0).getStack() + " input="
                        + player.inventory.getStackInSlot(9) + " can_interact=" + player.openContainer.canInteractWith(player));
            });
        }
        if (stage == 0) {
            stage = 1;
            minecraft.getIntegratedServer().addScheduledTask(() -> {
                EntityPlayerMP player = minecraft.getIntegratedServer().getPlayerList().getPlayerByUUID(minecraft.player.getUniqueID());

                player.inventory.clear();
                player.inventory.currentItem = 0;

                ItemStack basket = new ItemStack(Baskets.ITEM);
                NBTTagCompound tag = new NBTTagCompound();

                tag.setString("TestBasket", "network");
                basket.setTagCompound(tag);
                player.inventory.setInventorySlotContents(0, basket);
                player.inventory.setInventorySlotContents(9, new ItemStack(net.minecraft.init.Blocks.SAPLING, 5));
                player.inventoryContainer.detectAndSendChanges();
            });
        } else if (stage == 1 && minecraft.player.getHeldItemMainhand().getItem() == Baskets.ITEM) {
            stage = 2;
            minecraft.playerController.processRightClick(minecraft.player, minecraft.world, EnumHand.MAIN_HAND);
        } else if (stage == 2 && minecraft.currentScreen instanceof BasketScreen) {
            stage = 3;
            minecraft.playerController.windowClick(minecraft.player.openContainer.windowId, 18, 0, ClickType.QUICK_MOVE,
                    minecraft.player);
        } else if (stage == 3 && minecraft.player.openContainer instanceof BasketContainer
                && ((BasketContainer) minecraft.player.openContainer).basket.getStackInSlot(0).getCount() == 5) {
            net.minecraft.util.ScreenShotHelper.saveScreenshot(minecraft.gameDir, "flower-basket-gui.png",
                    minecraft.displayWidth, minecraft.displayHeight, minecraft.getFramebuffer());
            minecraft.playerController.windowClick(minecraft.player.openContainer.windowId, 45, 1, ClickType.THROW,
                    minecraft.player);
            stage = 4;
            minecraft.getIntegratedServer().addScheduledTask(() -> {
                EntityPlayerMP player = minecraft.getIntegratedServer().getPlayerList().getPlayerByUUID(minecraft.player.getUniqueID());

                if (player.inventory.getStackInSlot(0).getItem() != Baskets.ITEM
                        || new BasketInventory(player.inventory.getStackInSlot(0)).getStackInSlot(0).getCount() != 5) {
                    throw new AssertionError("GUI throw lost the owner or its contents");
                }

                serverChecked = true;
            });
        } else if (stage == 4 && serverChecked) {
            serverChecked = false;
            minecraft.player.closeScreen();
            stage = 5;
            minecraft.getIntegratedServer().addScheduledTask(() -> {
                EntityPlayerMP player = minecraft.getIntegratedServer().getPlayerList().getPlayerByUUID(minecraft.player.getUniqueID());
                ItemStack basket = player.inventory.getStackInSlot(0);

                player.inventory.setInventorySlotContents(0, ItemStack.EMPTY);
                player.setHeldItem(EnumHand.OFF_HAND, basket);
                player.inventoryContainer.detectAndSendChanges();
            });
        } else if (stage == 5 && minecraft.player.getHeldItemOffhand().getItem() == Baskets.ITEM) {
            stage = 6;
            minecraft.playerController.processRightClick(minecraft.player, minecraft.world, EnumHand.OFF_HAND);
        } else if (stage == 6 && minecraft.currentScreen instanceof BasketScreen) {
            stage = 7;
            minecraft.playerController.windowClick(minecraft.player.openContainer.windowId, 0, 0, ClickType.QUICK_MOVE,
                    minecraft.player);
        } else if (stage == 7 && minecraft.player.openContainer instanceof BasketContainer
                && ((BasketContainer) minecraft.player.openContainer).basket.isEmpty()) {
            stage = 8;
            minecraft.getIntegratedServer().addScheduledTask(() -> {
                EntityPlayerMP player = minecraft.getIntegratedServer().getPlayerList().getPlayerByUUID(minecraft.player.getUniqueID());
                int plants = 0;

                for (ItemStack stack : player.inventory.mainInventory) {
                    if (stack.getItem() == net.minecraft.item.Item
                            .getItemFromBlock(net.minecraft.init.Blocks.SAPLING)) {
                        plants += stack.getCount();
                    }
                }
                if (plants != 5 || player.getHeldItemOffhand().getItem() != Baskets.ITEM) {
                    throw new AssertionError("Offhand withdrawal lost or duplicated items");
                }

                ((BasketContainer) player.openContainer).basket.insert(new ItemStack(net.minecraft.init.Blocks.SAPLING, 3));
                player.capabilities.isCreativeMode = false;
                player.setHealth(0);
                player.onDeath(net.minecraft.util.DamageSource.OUT_OF_WORLD);
            });
            deathCheckAt = ticks + 20;
        } else if (stage == 8 && ticks >= deathCheckAt && !serverChecked) {
            deathCheckAt = Integer.MAX_VALUE;
            minecraft.getIntegratedServer().addScheduledTask(() -> {
                EntityPlayerMP player = minecraft.getIntegratedServer().getPlayerList().getPlayerByUUID(minecraft.player.getUniqueID());
                int dropped = 0;

                for (net.minecraft.entity.Entity entity : player.world.loadedEntityList) {
                    if (entity instanceof net.minecraft.entity.item.EntityItem) {
                        ItemStack stack = ((net.minecraft.entity.item.EntityItem) entity).getItem();

                        if (stack.getItem() == Baskets.ITEM && stack.hasTagCompound()
                                && stack.getTagCompound().getString("TestBasket").equals("network")) {
                            dropped++;

                            if (new BasketInventory(stack).getStackInSlot(0).getCount() != 3) {
                                throw new AssertionError("Death lost basket contents");
                            }
                        }
                    }
                }
                if (dropped != 1 || player.openContainer instanceof BasketContainer) {
                    throw new AssertionError("Death did not close and drop exactly one basket: drops=" + dropped
                            + " container=" + player.openContainer.getClass().getName());
                }

                serverChecked = true;
            });
        } else if (stage == 8 && serverChecked) {
            try (PrintWriter writer = new PrintWriter(new File(minecraft.gameDir, "basket-client-network-pass.txt"),
                    "UTF-8")) {
                writer.println(
                        "main_hand=true offhand=true shift_click=true owner_throw=blocked death_contents=preserved");
            } catch (IOException failure) {
                throw new RuntimeException(failure);
            }

            System.out.println(
                    "BOP_BASKET_GUI_NETWORK_PASS hands=2 shift_click=true owner_throw=blocked death_contents=3 dropped_baskets=1");
            stage = 9;
            minecraft.shutdown();
        }
    }
}
