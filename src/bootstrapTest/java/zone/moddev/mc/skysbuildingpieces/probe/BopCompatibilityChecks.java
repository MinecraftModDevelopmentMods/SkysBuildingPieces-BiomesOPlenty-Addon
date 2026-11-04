package zone.moddev.mc.skysbuildingpieces.probe;

import java.lang.reflect.Method;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.content.LegacyPieceRecipe;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;
import zone.moddev.mc.skysbuildingpieces.probe.legacy.LegacyTestScope;
import zone.moddev.mc.skysbuildingpieces.legacy.MigrationEvents;

/** Checks each BOP material through real events with the build-only legacy fixture. */
public final class BopCompatibilityChecks {
    private static String material;
    private BopCompatibilityChecks() {
    }

    public static ItemStack legacy(int count) {
        ItemStack stack = new ItemStack(
                ForgeRegistries.ITEMS.getValue(new ResourceLocation("buildingbricks:rock_step")), count);

        stack.setTag(NativeLegacyChecks.stack(material, count).getCompound("tag").copy());
        return stack;
    }

    public static void run(ServerLevel world) throws Exception {
        if (!ModList.get().isLoaded("buildingbricks")) {
            throw new AssertionError("Installed-mod policy checks require their build-only fixture");
        }

        boolean original = SkysBuildingPieces.forceReplace;

        try (LegacyTestScope source = new LegacyTestScope(world)) {
            for (boolean convert : new boolean[] { false, true }) {
                SkysBuildingPieces.forceReplace = convert;
                CompoundTag before = zone.moddev.mc.skysbuildingpieces.legacy.PiecesWorldState
                        .get(world).save(new CompoundTag());
                for (zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue.MaterialDef definition
                        : zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue.INSTANCE.modules
                                .get("biomesoplenty").materials.values()) {
                    material = definition.id;
                    liveInventories(world, convert);
                    newlyPlaced(world, convert);
                    unknownMaterial(world);
                }
                if (!convert) {
                    NativeLegacyChecks.require(before.equals(
                            zone.moddev.mc.skysbuildingpieces.legacy.PiecesWorldState.get(world)
                                    .save(new CompoundTag())),
                            "Disabled replacement must retain every migration counter and report");
                }
            }
        } finally {
            SkysBuildingPieces.forceReplace = original;
        }

        org.apache.logging.log4j.LogManager.getLogger().info(
                "BOP_INSTALLED_POLICY_PASS materials=33 installed_fixture=true replacement_off=true replacement_on=true");
    }

    private static void identity(ItemStack stack, int count, boolean converted) {
        NativeLegacyChecks.require(
                !stack.isEmpty() && stack.getCount() == count,
                "Live stack count changed for " + material);
        if (converted) {
            zone.moddev.mc.skysbuildingpieces.catalogue.Shape shape =
                    zone.moddev.mc.skysbuildingpieces.legacy.LegacyMapping.shape("buildingbricks:rock_step", 0);
            ItemStack expected = zone.moddev.mc.skysbuildingpieces.content.Pieces.stack(
                    zone.moddev.mc.skysbuildingpieces.content.Pieces.state(material, shape, 0), count);
            NativeLegacyChecks.require(expected != null && expected.getItem() == stack.getItem(),
                    "Stored BOP piece retains its exact material and shape: " + material);
        } else {
            NativeLegacyChecks.require(stack.getItem() == legacy(count).getItem(),
                    "Disabled replacement keeps the original item for " + material);
        }
        NativeLegacyChecks.require(stack.hasTag() && stack.getTag().equals(legacy(count).getTag()),
                "Complete unrelated custom data is retained");
    }

    private static void liveInventories(ServerLevel world, boolean convert) throws Exception {
        ServerPlayer player = FakePlayerFactory.getMinecraft(world);

        player.getInventory().setItem(0, legacy(13));
        player.getEnderChestInventory().setItem(0, legacy(11));
        MinecraftForge.EVENT_BUS.post(new EntityJoinLevelEvent(player, world));
        identity(player.getInventory().getItem(0), 13, convert);
        identity(player.getEnderChestInventory().getItem(0), 11, convert);
        player.getInventory().setItem(1, legacy(9));
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.LoadFromFile(player, world.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
                player.getUUID().toString()));
        identity(player.getInventory().getItem(1), 9, convert);
        world.getChunk(new BlockPos(930, 70, 930));

        ItemEntity drop = new ItemEntity(world, 930, 70, 930, legacy(5));

        NativeLegacyChecks.require(world.addFreshEntity(drop), "Dropped item joins the actual native world");
        identity(drop.getItem(), 5, convert);

        BlockPos position = new BlockPos(930, 70, 932);

        world.setBlock(position.north(), Blocks.STONE.defaultBlockState(), 2);

        ItemFrame frame = new ItemFrame(world, position, Direction.SOUTH);

        frame.setItem(legacy(1));
        world.addFreshEntity(frame);
        identity(frame.getItem(), 1, convert);

        HandlerTile tile = new HandlerTile(position);

        tile.setLevel(world);
        tile.handler.setStackInSlot(0, legacy(17));

        Method scan = MigrationEvents.class.getDeclaredMethod("containers", BlockEntity.class);

        scan.setAccessible(true);
        scan.invoke(null, tile);
        identity(tile.handler.getStackInSlot(0), 17, convert);

        CompoundTag once = tile.handler.serializeNBT();

        scan.invoke(null, tile);
        NativeLegacyChecks.require(once.equals(tile.handler.serializeNBT()), "Shared handler faces convert only once");

        ItemStack crate = new ItemStack(Blocks.CHEST);
        CompoundTag tag = new CompoundTag();

        tag.putBoolean("Sealed", true);

        ListTag contents = new ListTag();

        contents.add(legacy(7).save(new CompoundTag()));
        tag.put("Items", contents);
        crate.setTag(tag);

        ItemStack migrated = MigrationEvents.convert(crate, world);
        CompoundTag recovered = migrated.getTag().getList("Items", 10).getCompound(0);

        identity(ItemStack.of(recovered), 7, convert);
        NativeLegacyChecks.require(migrated.getItem() == crate.getItem() && migrated.getTag().getBoolean("Sealed"),
                "Sealed container identity and custom data");
        NativeLegacyChecks.require(MigrationEvents.convert(migrated, world) == migrated,
                "Live nested inventory is idempotent");

        CraftingContainer grid = new CraftingContainer(new AbstractContainerMenu(null, 0) {
                        @Override
                        public ItemStack quickMoveStack(Player player, int slot) {
                            return ItemStack.EMPTY;
                        }

            @Override
            public boolean stillValid(Player ignored) {
                return true;
            }
        }, 3, 3);

        grid.setItem(0, legacy(1));

        LegacyPieceRecipe recipe = new LegacyPieceRecipe(new ResourceLocation("skysbuildingpieces:bridge_test"), net.minecraft.world.item.crafting.CraftingBookCategory.BUILDING);

        NativeLegacyChecks.require(recipe.matches(grid, world),
                "Explicit bridge recipe works independently of replacement");
        identity(recipe.assemble(grid, world.registryAccess()), 1, true);
        drop.discard();
        frame.discard();
        world.removeBlock(position.north(), false);
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();
    }

    private static void newlyPlaced(ServerLevel world, boolean convert) {
        BlockPos position = new BlockPos(934, 70, 932);
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("buildingbricks:rock_step"));
        BlockState old = block.defaultBlockState().setValue((IntegerProperty) block.getStateDefinition().getProperty("meta"),
                11);

        world.setBlock(position, old, 2);

        BlockEntity tile = world.getBlockEntity(position);
        CompoundTag tag = tile.saveWithFullMetadata();

        tag.putString("material", material);
        tag.putString("custom", "Keep the old tile in coverage");
        tile.load(tag);
        MinecraftForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(world.dimension(), world, position),
                Blocks.STONE.defaultBlockState(), FakePlayerFactory.getMinecraft(world)));
        if (convert) {
            zone.moddev.mc.skysbuildingpieces.catalogue.Shape shape =
                    zone.moddev.mc.skysbuildingpieces.legacy.LegacyMapping.shape("buildingbricks:rock_step", 11);
            NativeLegacyChecks.require(world.getBlockState(position).equals(
                    zone.moddev.mc.skysbuildingpieces.content.Pieces.state(material, shape,
                            zone.moddev.mc.skysbuildingpieces.legacy.LegacyMapping.orientation(shape, 11))),
                    "Newly placed BOP piece retains its exact material and orientation");
        } else {
            NativeLegacyChecks.require(world.getBlockState(position).equals(old),
                    "Disabled replacement keeps the original placed state");
        }
        NativeLegacyChecks.require((world.getBlockEntity(position) == null) == convert,
                "Remove the material tile only after successful conversion");
        world.removeBlock(position, false);
    }

    private static void unknownMaterial(ServerLevel world) {
        ItemStack unknown = legacy(3);

        unknown.getTag().putString("material", "missing:unknown");

        CompoundTag before = unknown.save(new CompoundTag());
        ItemStack result = MigrationEvents.convert(unknown, world);

        NativeLegacyChecks.require(result.save(new CompoundTag()).equals(before),
                "Installed legacy mod retains unsupported materials");

        // Horizontal soil recovery belongs to Grass Slabs and must stay outside this bridge.
        CompoundTag soil = NativeLegacyChecks.stack("minecraft:dirt", 7);

        soil.putString("id", "buildingbricks:dirt_slab");
        before = soil.copy();
        LegacyBridge.prepareStack(soil);
        NativeLegacyChecks.require(soil.equals(before), "Core must not take ownership of horizontal soil slabs");
    }

    public static final class HandlerTile extends BlockEntity {
        final ItemStackHandler handler = new ItemStackHandler(2);
        private final LazyOptional<ItemStackHandler> optional = LazyOptional.of(() -> handler);

        HandlerTile(BlockPos position) {
            super(BlockEntityType.CHEST, position, Blocks.CHEST.defaultBlockState());
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
            return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? optional.cast()
                    : super.getCapability(capability, side);
        }
    }
}
