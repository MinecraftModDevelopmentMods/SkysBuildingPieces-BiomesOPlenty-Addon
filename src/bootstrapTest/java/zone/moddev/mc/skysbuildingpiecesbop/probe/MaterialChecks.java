package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Geometry;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpiecesbop.SkysBuildingPiecesBop;

/** Exercise the complete resolved catalogue with the real native block classes. */
final class MaterialChecks {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger();

    private MaterialChecks() {
    }

    static void run(ServerLevel world) {
        BlockPos position = new BlockPos(-700, 90, -700);
        BlockState previous = world.getBlockState(position);
        int checked = 0;

        try {
            retiredMining(world, position);
            for (Catalogue.MaterialDef material : Catalogue.INSTANCE.modules.get("biomesoplenty").materials.values()) {
                BlockState full = Pieces.nativeState(material, "full");

                for (Shape shape : new Shape[] { Shape.SLAB, Shape.VERTICAL_SLAB, Shape.HORIZONTAL_STEP,
                        Shape.VERTICAL_STEP, Shape.CORNER, Shape.STAIRS, Shape.WALL }) {
                    for (int orientation = 0; orientation < shape.states; orientation++) {
                        BlockState state = Pieces.state(material.id, shape, orientation);

                        if (state == null) {
                            continue;
                        }

                        world.setBlock(position, state, 2);

                        ItemStack item = Pieces.stack(state, 3);

                        require(item != null && !item.isEmpty() && item.getCount() == 3, "Piece item " + material.id);
                        require(world.getBlockEntity(position) == null,
                                "Material pieces must not create block entities");
                        require(state.getMaterial() == full.getMaterial(), "Native block material " + material.id);
                        require(state.getDestroySpeed(world, position) == full.getDestroySpeed(world, position), "Native hardness " + material.id);
                        Catalogue.MaterialDef placedMaterial = state.getBlock() instanceof PieceBlock
                                ? ((PieceBlock) state.getBlock()).definition(state) : null;
                        // Retired rocks keep their declared resistance even when Mineralogy supplies the full block.
                        // Reused native slabs and stairs retain their own native properties.
                        float resistance = placedMaterial != null && placedMaterial.explosionResistance() >= 0
                                ? placedMaterial.explosionResistance()
                                : full.getBlock().getExplosionResistance(full, world, position, null);

                        require(state.getBlock().getExplosionResistance(state, world, position, null) == resistance,
                                "Material resistance " + material.id);

                        for (Direction face : Direction.values()) {
                            int flammability = placedMaterial != null && placedMaterial.flammability() >= 0
                                    ? placedMaterial.flammability()
                                    : full.getBlock().getFlammability(full, world, position, face);
                            int spread = placedMaterial != null && placedMaterial.fireSpread() >= 0
                                    ? placedMaterial.fireSpread()
                                    : full.getBlock().getFireSpreadSpeed(full, world, position, face);

                            require(state.getBlock().getFlammability(state, world, position, face) == flammability,
                                    "Material flammability " + material.id);
                            require(state.getBlock().getFireSpreadSpeed(state, world, position, face) == spread,
                                    "Material fire spread " + material.id);
                        }
                        if (shape != Shape.WALL) {
                            List<AABB> boxes = state.getCollisionShape(world, position).toAabbs();
                            int occupied = 0;

                            for (int cellY = 0; cellY < 2; cellY++) {
                                for (int cellZ = 0; cellZ < 2; cellZ++) {
                                    for (int cellX = 0; cellX < 2; cellX++) {
                                        double sampleX = .25 + .5 * cellX;
                                        double sampleY = .25 + .5 * cellY;
                                        double sampleZ = .25 + .5 * cellZ;

                                        if (boxes.stream().anyMatch(box -> box.contains(sampleX, sampleY, sampleZ))) {
                                            occupied |= 1 << (cellY * 4 + cellZ * 2 + cellX);
                                        }
                                    }
                                }
                            }

                            require(occupied == Geometry.mask(shape, orientation), "Collision geometry " + material.id
                                    + " / " + shape + " / " + orientation + ": " + occupied);
                        }

                        if (state.hasProperty(net.minecraft.world.level.block.SlabBlock.WATERLOGGED)) {
                            BlockState wet = state.setValue(net.minecraft.world.level.block.SlabBlock.WATERLOGGED, true);

                            world.setBlock(position, wet, 2);
                            require(world.getFluidState(position).getType() == net.minecraft.world.level.material.Fluids.WATER,
                                    "Waterlogged material lost its fluid: " + material.id + " / " + shape);
                            require(net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),
                                    net.minecraft.nbt.NbtUtils.writeBlockState(wet)).equals(wet),
                                    "Waterlogged orientation did not survive serialization: " + material.id);
                            world.setBlock(position, state, 2);
                        }

                        checked++;
                    }
                }
            }

            long registered = ForgeRegistries.BLOCKS.getValues().stream()
                    .filter(block -> net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).getNamespace().equals(SkysBuildingPiecesBop.ID)).count();

            require(registered == 74 && registered <= 80, "Complete add-on block budget: " + registered);
            LOGGER.info("BOP_NATIVE_MATERIALS_PASS states=" + checked + " blocks=" + registered);
        } finally {
            world.setBlock(position, previous, 2);
        }
    }

    private static void retiredMining(ServerLevel world, BlockPos position) {
        net.minecraftforge.common.util.FakePlayer player = net.minecraftforge.common.util.FakePlayerFactory.get(world,
                new com.mojang.authlib.GameProfile(java.util.UUID.fromString("f8ca0294-a7f9-453f-9533-66fc7c45d698"),
                        "RetiredRockProbe"));
        net.minecraft.world.item.Item[] tools = {
                net.minecraft.world.item.Items.WOODEN_PICKAXE, net.minecraft.world.item.Items.STONE_PICKAXE,
                net.minecraft.world.item.Items.GOLDEN_PICKAXE, net.minecraft.world.item.Items.IRON_PICKAXE,
                net.minecraft.world.item.Items.DIAMOND_PICKAXE, net.minecraft.world.item.Items.NETHERITE_PICKAXE
        };
        float[] hardness = { 5, 1.5f, 1.5f, 1.5f, 1.5f, 1, 1 };
        int checked = 0;
        for (int variant = 0; variant < hardness.length; variant++) {
            BlockState state = zone.moddev.mc.skysbuildingpiecesbop.RetiredRocks.BLOCK.getStateFromMeta(variant);
            world.setBlock(position, state, 2);
            require(state.getDestroySpeed(world, position) == hardness[variant], "Retired-rock hardness " + variant);
            for (int tool = -1; tool < tools.length; tool++) {
                ItemStack held = tool < 0 ? ItemStack.EMPTY : new ItemStack(tools[tool]);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
                boolean expected = tool >= 0 && (variant != 0 || tool >= 3);
                require(state.canHarvestBlock(world, position, player) == expected,
                        "Retired-rock tool tier " + variant + " / " + tool);
                BlockState reference = (variant == 0 ? net.minecraft.world.level.block.Blocks.DIAMOND_ORE
                        : net.minecraft.world.level.block.Blocks.STONE).defaultBlockState();
                float progress = player.getDigSpeed(reference, position) / hardness[variant] / (expected ? 30 : 100);
                require(Math.abs(state.getDestroyProgress(player, world, position) - progress) < .000001f,
                        "Retired-rock mining speed " + variant + " / " + tool);
                checked++;
            }
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        LOGGER.info("BOP_RETIRED_MINING_PASS variants=7 tools=6 empty_hand=true checks=" + checked);
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
