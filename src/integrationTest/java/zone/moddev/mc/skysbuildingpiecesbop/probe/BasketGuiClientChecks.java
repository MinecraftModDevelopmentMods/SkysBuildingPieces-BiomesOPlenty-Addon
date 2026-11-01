package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;

import java.io.*;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.event.TickEvent;

import zone.moddev.mc.skysbuildingpieces.probe.ClientStatus;
import zone.moddev.mc.skysbuildingpiecesbop.basket.*;
import zone.moddev.mc.skysbuildingpiecesbop.client.BasketScreen;

/** Normal GUI packets between a packaged client and its integrated server. */
public final class BasketGuiClientChecks {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger();

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

        minecraft.options.pauseOnLostFocus = false;

        if (stage != lastStage) {
            LOGGER.info("BASKET_GUI_STAGE " + stage);
            lastStage = stage;
        }
        if (!ready || minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            return;
        }
        if (stage == 3 && ticks % 40 == 0) {
            LOGGER.info("BASKET_GUI_CLIENT_STATE container=" + minecraft.player.containerMenu.getClass().getName()
                            + " first=" + minecraft.player.containerMenu.getSlot(0).getItem() + " can_interact="
                            + minecraft.player.containerMenu.stillValid(minecraft.player));
            minecraft.getSingleplayerServer().execute(() -> {
                ServerPlayer player = minecraft.getSingleplayerServer().getPlayerList()
                        .getPlayer(minecraft.player.getUUID());

                LOGGER.info("BASKET_GUI_SERVER_STATE first=" + player.containerMenu.getSlot(0).getItem()
                        + " input=" + player.getInventory().getItem(9) + " can_interact="
                        + player.containerMenu.stillValid(player));
            });
        }
        if (stage == 0) {
            stage = 1;
            minecraft.getSingleplayerServer().execute(() -> {
                ServerPlayer player = minecraft.getSingleplayerServer().getPlayerList()
                        .getPlayer(minecraft.player.getUUID());

                player.getInventory().clearContent();
                player.getInventory().selected = 0;

                ItemStack basket = new ItemStack(Baskets.ITEM);
                CompoundTag tag = new CompoundTag();

                tag.putString("TestBasket", TEST_BASKET);
                basket.setTag(tag);
                player.getInventory().setItem(0, basket);
                player.getInventory().setItem(9, new ItemStack(net.minecraft.world.level.block.Blocks.OAK_SAPLING, 5));
                player.inventoryMenu.broadcastChanges();
            });
        } else if (stage == 1 && minecraft.player.getMainHandItem().getItem() == Baskets.ITEM) {
            stage = 2;
            minecraft.gameMode.useItem(minecraft.player, minecraft.level, InteractionHand.MAIN_HAND);
        } else if (stage == 2 && minecraft.screen instanceof BasketScreen) {
            stage = 3;
            minecraft.gameMode.handleInventoryMouseClick(minecraft.player.containerMenu.containerId, 18, 0, ClickType.QUICK_MOVE,
                    minecraft.player);
        } else if (stage == 3 && minecraft.player.containerMenu instanceof BasketContainer
                && ((BasketContainer) minecraft.player.containerMenu).basket.getItem(0).getCount() == 5) {
            net.minecraft.client.Screenshot.grab(minecraft.gameDirectory, "flower-basket-gui.png",
                    minecraft.getMainRenderTarget(), message -> {
                    });
            minecraft.gameMode.handleInventoryMouseClick(minecraft.player.containerMenu.containerId, 45, 1, ClickType.THROW,
                    minecraft.player);
            stage = 4;
            minecraft.getSingleplayerServer().execute(() -> {
                ServerPlayer player = minecraft.getSingleplayerServer().getPlayerList()
                        .getPlayer(minecraft.player.getUUID());

                if (player.getInventory().getItem(0).getItem() != Baskets.ITEM
                        || new BasketInventory(player.getInventory().getItem(0)).getItem(0).getCount() != 5) {
                    throw new AssertionError("GUI throw lost the owner or its contents");
                }

                serverChecked = true;
            });
        } else if (stage == 4 && serverChecked) {
            serverChecked = false;
            minecraft.player.closeContainer();
            stage = 5;
        } else if (stage == 5) {
            if (!offhandMoved && ticks % 5 == 0) {
                minecraft.getSingleplayerServer().execute(() -> {
                    ServerPlayer player = minecraft.getSingleplayerServer().getPlayerList()
                            .getPlayer(minecraft.player.getUUID());
                    if (offhandMoved || player.containerMenu != player.inventoryMenu) {
                        return;
                    }
                    // Wait for the actual close packet. Moving the owner earlier
                    // correctly makes the basket rescue itself into its locked slot.
                    ItemStack basket = player.getInventory().getItem(0);
                    if (basket.getItem() != Baskets.ITEM || !basket.hasTag()
                            || !basket.getTag().getString("TestBasket").equals(TEST_BASKET)
                            || new BasketInventory(basket).getItem(0).getCount() != 5) {
                        throw new AssertionError("The closed basket keeps its owner and five saplings");
                    }
                    player.getInventory().setItem(0, ItemStack.EMPTY);
                    player.setItemInHand(InteractionHand.OFF_HAND, basket);
                    player.inventoryMenu.broadcastChanges();
                    offhandMoved = true;
                });
            }
            if (offhandMoved && minecraft.player.getOffhandItem().getItem() == Baskets.ITEM) {
                stage = 6;
                minecraft.gameMode.useItem(minecraft.player, minecraft.level, InteractionHand.OFF_HAND);
            }
        } else if (stage == 6 && minecraft.screen instanceof BasketScreen) {
            stage = 7;
            minecraft.gameMode.handleInventoryMouseClick(minecraft.player.containerMenu.containerId, 0, 0, ClickType.QUICK_MOVE,
                    minecraft.player);
        } else if (stage == 7 && minecraft.player.containerMenu instanceof BasketContainer
                && ((BasketContainer) minecraft.player.containerMenu).basket.isEmpty()) {
            stage = 8;
            minecraft.getSingleplayerServer().execute(() -> {
                ServerPlayer player = minecraft.getSingleplayerServer().getPlayerList()
                        .getPlayer(minecraft.player.getUUID());
                int plants = 0;

                for (ItemStack stack : player.getInventory().items) {
                    if (stack.getItem() == net.minecraft.world.item.Item
                            .byBlock(net.minecraft.world.level.block.Blocks.OAK_SAPLING)) {
                        plants += stack.getCount();
                    }
                }
                if (plants != 5 || player.getOffhandItem().getItem() != Baskets.ITEM) {
                    throw new AssertionError("Offhand withdrawal lost or duplicated items");
                }

                ((BasketContainer) player.containerMenu).basket
                        .insert(new ItemStack(net.minecraft.world.level.block.Blocks.OAK_SAPLING, 3));
                player.getAbilities().instabuild = false;
                player.setHealth(0);
                player.die(net.minecraft.world.damagesource.DamageSource.OUT_OF_WORLD);
            });
            deathCheckAt = ticks + 20;
        } else if (stage == 8 && ticks >= deathCheckAt && !serverChecked) {
            deathCheckAt = Integer.MAX_VALUE;
            minecraft.getSingleplayerServer().execute(() -> {
                ServerPlayer player = minecraft.getSingleplayerServer().getPlayerList()
                        .getPlayer(minecraft.player.getUUID());
                int dropped = 0;

                for (net.minecraft.world.entity.Entity entity : ((net.minecraft.server.level.ServerLevel) player.level)
                        .getEntities(net.minecraft.world.entity.EntityType.ITEM, candidate -> true)) {
                    if (entity instanceof net.minecraft.world.entity.item.ItemEntity) {
                        ItemStack stack = ((net.minecraft.world.entity.item.ItemEntity) entity).getItem();

                        if (stack.getItem() == Baskets.ITEM && stack.hasTag()
                                && stack.getTag().getString("TestBasket").equals(TEST_BASKET)) {
                            dropped++;

                            if (new BasketInventory(stack).getItem(0).getCount() != 3) {
                                throw new AssertionError("Death lost basket contents");
                            }
                        }
                    }
                }
                if (dropped != 1 || player.containerMenu instanceof BasketContainer) {
                    throw new AssertionError("Death did not close and drop exactly one basket: drops=" + dropped
                            + " container=" + player.containerMenu.getClass().getName());
                }

                serverChecked = true;
            });
        } else if (stage == 8 && serverChecked) {
            // Finish the death check with a normal respawn so reload starts
            // with a live player who can open containers again.
            serverChecked = false;
            stage = 9;
            minecraft.player.respawn();
            minecraft.setScreen(null);
        } else if (stage == 9 && minecraft.player.isAlive() && !respawnChecked) {
            respawnChecked = true;
            minecraft.getSingleplayerServer().execute(() -> {
                ServerPlayer player = minecraft.getSingleplayerServer().getPlayerList()
                        .getPlayer(minecraft.player.getUUID());

                if (player == null || !player.isAlive() || player.getHealth() <= 0) {
                    throw new AssertionError("Basket death check did not respawn the player");
                }
                serverChecked = true;
            });
        } else if (stage == 9 && serverChecked) {
            try (PrintWriter writer = new PrintWriter(new File(minecraft.gameDirectory, "basket-client-network-pass.txt"),
                    "UTF-8")) {
                writer.println(
                        "main_hand=true offhand=true shift_click=true owner_throw=blocked death_contents=preserved respawn=true");
            } catch (IOException failure) {
                throw new RuntimeException(failure);
            }

            LOGGER.info(
                    "BOP_BASKET_GUI_NETWORK_PASS hands=2 shift_click=true owner_throw=blocked death_contents=3 dropped_baskets=1 respawn=true");
            stage = 10;
            ClientStatus.complete = true;
        }
    }
}
