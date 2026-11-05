package zone.moddev.mc.skysbuildingpieces.probe;

import java.lang.reflect.Method;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import zone.moddev.mc.skysbuildingpiecesbop.probe.BopProbeItems;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.minecraft.core.registries.BuiltInRegistries;
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
                BuiltInRegistries.ITEM.get(ResourceLocation.parse("buildingbricks:rock_step")), count);

        BopProbeItems.setCustom(stack, NativeLegacyChecks.stack(material, count).getCompound("tag").copy());
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
                        .get(world).save(new CompoundTag(), world.registryAccess());
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
                                    .save(new CompoundTag(), world.registryAccess())),
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
        NativeLegacyChecks.require(BopProbeItems.hasCustom(stack) && BopProbeItems.custom(stack).equals(BopProbeItems.custom(legacy(count))),
                "Complete unrelated custom data is retained");
    }

    private static void liveInventories(ServerLevel world, boolean convert) throws Exception {
        ServerPlayer player = BopProbeItems.player(world);

        player.getInventory().setItem(0, legacy(13));
        player.getEnderChestInventory().setItem(0, legacy(11));
        NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(player, world));
        identity(player.getInventory().getItem(0), 13, convert);
        identity(player.getEnderChestInventory().getItem(0), 11, convert);
        player.getInventory().setItem(1, legacy(9));
        NeoForge.EVENT_BUS.post(new PlayerEvent.LoadFromFile(player, world.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
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

        BlockPos handlerPosition = position.west();
        world.setBlock(handlerPosition, handlerBlock.defaultBlockState(), 2);
        HandlerTile tile = (HandlerTile) world.getBlockEntity(handlerPosition);
        NativeLegacyChecks.require(tile != null, "BOP policy fixture item handler is installed");
        tile.handler.setStackInSlot(0, legacy(17));

        Method scan = MigrationEvents.class.getDeclaredMethod("containers", BlockEntity.class);

        scan.setAccessible(true);
        scan.invoke(null, tile);
        identity(tile.handler.getStackInSlot(0), 17, convert);

        CompoundTag once = tile.handler.serializeNBT(world.registryAccess());

        scan.invoke(null, tile);
        NativeLegacyChecks.require(once.equals(tile.handler.serializeNBT(world.registryAccess())), "Shared handler faces convert only once");

        world.removeBlock(handlerPosition, false);

        ItemStack crate = new ItemStack(Blocks.CHEST);
        CompoundTag tag = new CompoundTag();

        tag.putBoolean("Sealed", true);

        ListTag contents = new ListTag();

        contents.add(BopProbeItems.save(legacy(7)));
        tag.put("Items", contents);
        BopProbeItems.setCustom(crate, tag);

        ItemStack migrated = MigrationEvents.convert(crate, world);
        CompoundTag recovered = BopProbeItems.custom(migrated).getList("Items", 10).getCompound(0);

        identity(BopProbeItems.read(recovered), 7, convert);
        NativeLegacyChecks.require(migrated.getItem() == crate.getItem() && BopProbeItems.custom(migrated).getBoolean("Sealed"),
                "Sealed container identity and custom data");
        NativeLegacyChecks.require(MigrationEvents.convert(migrated, world) == migrated,
                "Live nested inventory is idempotent");

        CraftingContainer grid = new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
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

        LegacyPieceRecipe recipe = new LegacyPieceRecipe(net.minecraft.world.item.crafting.CraftingBookCategory.BUILDING);

        NativeLegacyChecks.require(recipe.matches(grid.asCraftInput(), world),
                "Explicit bridge recipe works independently of replacement");
        identity(recipe.assemble(grid.asCraftInput(), world.registryAccess()), 1, true);
        drop.discard();
        frame.discard();
        world.removeBlock(position.north(), false);
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();
    }

    private static void newlyPlaced(ServerLevel world, boolean convert) {
        BlockPos position = new BlockPos(934, 70, 932);
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("buildingbricks:rock_step"));
        BlockState old = block.defaultBlockState().setValue((IntegerProperty) block.getStateDefinition().getProperty("meta"),
                11);

        world.setBlock(position, old, 2);

        BlockEntity tile = world.getBlockEntity(position);
        CompoundTag tag = tile.saveWithFullMetadata(world.registryAccess());

        tag.putString("material", material);
        tag.putString("custom", "Keep the old tile in coverage");
        tile.loadWithComponents(tag, world.registryAccess());
        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(world.dimension(), world, position),
                Blocks.STONE.defaultBlockState(), BopProbeItems.player(world)));
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

        CompoundTag unknownData = BopProbeItems.custom(unknown);
        unknownData.putString("material", "missing:unknown");
        BopProbeItems.setCustom(unknown, unknownData);

        CompoundTag before = BopProbeItems.save(unknown);
        ItemStack result = MigrationEvents.convert(unknown, world);

        NativeLegacyChecks.require(BopProbeItems.save(result).equals(before),
                "Installed legacy mod retains unsupported materials");

        // Horizontal soil recovery belongs to Grass Slabs and must stay outside this bridge.
        CompoundTag soil = NativeLegacyChecks.stack("minecraft:dirt", 7);

        soil.putString("id", "buildingbricks:dirt_slab");
        before = soil.copy();
        LegacyBridge.prepareStack(soil);
        NativeLegacyChecks.require(soil.equals(before), "Core must not take ownership of horizontal soil slabs");
    }

    private static Block handlerBlock;
    private static BlockEntityType<HandlerTile> handlerType;

    public static void registerHandler(IEventBus modBus) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("buildingpiecesbopchecks", "item_handler_fixture");
        modBus.addListener((RegisterEvent event) -> {
            event.register(Registries.BLOCK, id, () -> handlerBlock = new HandlerBlock());
            event.register(Registries.BLOCK_ENTITY_TYPE, id,
                    () -> handlerType = BlockEntityType.Builder.of(HandlerTile::new, handlerBlock).build(null));
        });
        modBus.addListener((RegisterCapabilitiesEvent event) ->
                event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, handlerType, (tile, side) -> tile.handler));
    }

    private static final class HandlerBlock extends Block implements EntityBlock {
        private HandlerBlock() {
            super(Block.Properties.of());
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
            return new HandlerTile(position, state);
        }
    }

    public static final class HandlerTile extends BlockEntity {
        final ItemStackHandler handler = new ItemStackHandler(2);

        HandlerTile(BlockPos position, BlockState state) {
            super(handlerType, position, state);
        }
    }
}
