package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.io.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.event.TickEvent;

import zone.moddev.mc.skysbuildingpieces.probe.ClientStatus;
import zone.moddev.mc.skysbuildingpiecesbop.basket.*;
import zone.moddev.mc.skysbuildingpiecesbop.client.BasketScreen;

/** Normal GUI packets between a packaged client and its integrated server. */
public final class BasketGuiClientChecks {
    private static final String TEST_BASKET = "network-" + java.util.UUID.randomUUID();
    private static int stage, ticks, lastStage = -1, deathCheckAt;
    private static boolean ready, controlled, respawnChecked;
    private static volatile boolean serverChecked;
    private static volatile boolean offhandMoved;

    @SubscribeEvent(
            priority = EventPriority.HIGHEST)
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !System.getProperty("skysbuildingpieces.integrationPhase", "").equals("client-basket")) {
            return;
        }
        if (stage == 10) {
            return;
        }

        ready |= ClientStatus.complete;
        ClientStatus.complete = false;

        if (++ticks > 6000) {
            throw new AssertionError("Basket GUI network test timed out at stage " + stage);
        }

        Minecraft minecraft = Minecraft.getInstance();

        minecraft.gameSettings.pauseOnLostFocus = false;

        if (stage != lastStage) {
            System.out.println("BASKET_GUI_STAGE " + stage);
            lastStage = stage;
        }
        if (!ready || minecraft.player == null || minecraft.getIntegratedServer() == null) {
            return;
        }
        if (stage == 3 && ticks % 40 == 0) {
            System.out
                    .println("BASKET_GUI_CLIENT_STATE container=" + minecraft.player.openContainer.getClass().getName()
                            + " first=" + minecraft.player.openContainer.getSlot(0).getStack() + " can_interact="
                            + minecraft.player.openContainer.canInteractWith(minecraft.player));
            minecraft.getIntegratedServer().execute(() -> {
                ServerPlayerEntity player = minecraft.getIntegratedServer().getPlayerList()
                        .getPlayerByUUID(minecraft.player.getUniqueID());

                System.out.println("BASKET_GUI_SERVER_STATE first=" + player.openContainer.getSlot(0).getStack()
                        + " input=" + player.inventory.getStackInSlot(9) + " can_interact="
                        + player.openContainer.canInteractWith(player));
            });
        }
        if (stage == 0) {
            stage = 1;
            minecraft.getIntegratedServer().execute(() -> {
                ServerPlayerEntity player = minecraft.getIntegratedServer().getPlayerList()
                        .getPlayerByUUID(minecraft.player.getUniqueID());

                player.inventory.clear();
                player.inventory.currentItem = 0;

                ItemStack basket = new ItemStack(Baskets.ITEM);
                CompoundNBT tag = new CompoundNBT();

                tag.putString("TestBasket", TEST_BASKET);
                basket.setTag(tag);
                player.inventory.setInventorySlotContents(0, basket);
                player.inventory.setInventorySlotContents(9, new ItemStack(net.minecraft.block.Blocks.OAK_SAPLING, 5));
                player.container.detectAndSendChanges();
            });
        } else if (stage == 1 && minecraft.player.getHeldItemMainhand().getItem() == Baskets.ITEM) {
            stage = 2;
            minecraft.playerController.processRightClick(minecraft.player, minecraft.world, Hand.MAIN_HAND);
        } else if (stage == 2 && minecraft.currentScreen instanceof BasketScreen) {
            stage = 3;
            minecraft.playerController.windowClick(minecraft.player.openContainer.windowId, 18, 0, ClickType.QUICK_MOVE,
                    minecraft.player);
        } else if (stage == 3 && minecraft.player.openContainer instanceof BasketContainer
                && ((BasketContainer) minecraft.player.openContainer).basket.getStackInSlot(0).getCount() == 5) {
            net.minecraft.util.ScreenShotHelper.saveScreenshot(minecraft.gameDir, "flower-basket-gui.png",
                    minecraft.getMainWindow().getFramebufferWidth(), minecraft.getMainWindow().getFramebufferHeight(),
                    minecraft.getFramebuffer(), message -> {
                    });
            minecraft.playerController.windowClick(minecraft.player.openContainer.windowId, 45, 1, ClickType.THROW,
                    minecraft.player);
            stage = 4;
            minecraft.getIntegratedServer().execute(() -> {
                ServerPlayerEntity player = minecraft.getIntegratedServer().getPlayerList()
                        .getPlayerByUUID(minecraft.player.getUniqueID());

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
        } else if (stage == 5) {
            if (!offhandMoved && ticks % 5 == 0) {
                minecraft.getIntegratedServer().execute(() -> {
                    ServerPlayerEntity player = minecraft.getIntegratedServer().getPlayerList()
                            .getPlayerByUUID(minecraft.player.getUniqueID());
                    if (offhandMoved || player.openContainer != player.container) {
                        return;
                    }
                    // Wait for the actual close packet. Moving the owner earlier
                    // correctly makes the basket rescue itself into its locked slot.
                    ItemStack basket = player.inventory.getStackInSlot(0);
                    if (basket.getItem() != Baskets.ITEM || !basket.hasTag()
                            || !basket.getTag().getString("TestBasket").equals(TEST_BASKET)
                            || new BasketInventory(basket).getStackInSlot(0).getCount() != 5) {
                        throw new AssertionError("The closed basket keeps its owner and five saplings");
                    }
                    player.inventory.setInventorySlotContents(0, ItemStack.EMPTY);
                    player.setHeldItem(Hand.OFF_HAND, basket);
                    player.container.detectAndSendChanges();
                    offhandMoved = true;
                });
            }
            if (offhandMoved && minecraft.player.getHeldItemOffhand().getItem() == Baskets.ITEM) {
                stage = 6;
                minecraft.playerController.processRightClick(minecraft.player, minecraft.world, Hand.OFF_HAND);
            }
        } else if (stage == 6 && minecraft.currentScreen instanceof BasketScreen) {
            stage = 7;
            minecraft.playerController.windowClick(minecraft.player.openContainer.windowId, 0, 0, ClickType.QUICK_MOVE,
                    minecraft.player);
        } else if (stage == 7 && minecraft.player.openContainer instanceof BasketContainer
                && ((BasketContainer) minecraft.player.openContainer).basket.isEmpty()) {
            stage = 8;
            minecraft.getIntegratedServer().execute(() -> {
                ServerPlayerEntity player = minecraft.getIntegratedServer().getPlayerList()
                        .getPlayerByUUID(minecraft.player.getUniqueID());
                int plants = 0;

                for (ItemStack stack : player.inventory.mainInventory) {
                    if (stack.getItem() == net.minecraft.item.Item
                            .getItemFromBlock(net.minecraft.block.Blocks.OAK_SAPLING)) {
                        plants += stack.getCount();
                    }
                }
                if (plants != 5 || player.getHeldItemOffhand().getItem() != Baskets.ITEM) {
                    throw new AssertionError("Offhand withdrawal lost or duplicated items");
                }

                ((BasketContainer) player.openContainer).basket
                        .insert(new ItemStack(net.minecraft.block.Blocks.OAK_SAPLING, 3));
                player.abilities.isCreativeMode = false;
                player.setHealth(0);
                player.onDeath(net.minecraft.util.DamageSource.OUT_OF_WORLD);
            });
            deathCheckAt = ticks + 20;
        } else if (stage == 8 && ticks >= deathCheckAt && !serverChecked) {
            deathCheckAt = Integer.MAX_VALUE;
            minecraft.getIntegratedServer().execute(() -> {
                ServerPlayerEntity player = minecraft.getIntegratedServer().getPlayerList()
                        .getPlayerByUUID(minecraft.player.getUniqueID());
                int dropped = 0;

                for (net.minecraft.entity.Entity entity : ((net.minecraft.world.server.ServerWorld) player.world)
                        .getEntities(null, candidate -> true)) {
                    if (entity instanceof net.minecraft.entity.item.ItemEntity) {
                        ItemStack stack = ((net.minecraft.entity.item.ItemEntity) entity).getItem();

                        if (stack.getItem() == Baskets.ITEM && stack.hasTag()
                                && stack.getTag().getString("TestBasket").equals(TEST_BASKET)) {
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
            // Finish the death check with a normal respawn so reload starts
            // with a live player who can open containers again.
            serverChecked = false;
            stage = 9;
            minecraft.player.respawnPlayer();
            minecraft.displayGuiScreen(null);
        } else if (stage == 9 && minecraft.player.isAlive() && !respawnChecked) {
            respawnChecked = true;
            minecraft.getIntegratedServer().execute(() -> {
                ServerPlayerEntity player = minecraft.getIntegratedServer().getPlayerList()
                        .getPlayerByUUID(minecraft.player.getUniqueID());

                if (player == null || !player.isAlive() || player.getHealth() <= 0) {
                    throw new AssertionError("Basket death check did not respawn the player");
                }
                serverChecked = true;
            });
        } else if (stage == 9 && serverChecked) {
            try (PrintWriter writer = new PrintWriter(new File(minecraft.gameDir, "basket-client-network-pass.txt"),
                    "UTF-8")) {
                writer.println(
                        "main_hand=true offhand=true shift_click=true owner_throw=blocked death_contents=preserved respawn=true");
            } catch (IOException failure) {
                throw new RuntimeException(failure);
            }

            System.out.println(
                    "BOP_BASKET_GUI_NETWORK_PASS hands=2 shift_click=true owner_throw=blocked death_contents=3 dropped_baskets=1 respawn=true");
            stage = 10;
            ClientStatus.complete = true;
        }
    }
}
