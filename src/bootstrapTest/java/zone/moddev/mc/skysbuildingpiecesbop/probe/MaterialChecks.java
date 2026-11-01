package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Geometry;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpiecesbop.SkysBuildingPiecesBop;

/** Exercise the complete resolved catalogue with the real native block classes. */
final class MaterialChecks {
    private MaterialChecks() {
    }

    static void run(ServerWorld world) {
        BlockPos position = new BlockPos(-700, 90, -700);
        BlockState previous = world.getBlockState(position);
        int checked = 0;

        try {
            for (Catalogue.MaterialDef material : Catalogue.INSTANCE.modules.get("biomesoplenty").materials.values()) {
                BlockState full = Pieces.nativeState(material, "full");

                for (Shape shape : new Shape[] { Shape.SLAB, Shape.VERTICAL_SLAB, Shape.HORIZONTAL_STEP,
                        Shape.VERTICAL_STEP, Shape.CORNER, Shape.STAIRS, Shape.WALL }) {
                    for (int orientation = 0; orientation < shape.states; orientation++) {
                        BlockState state = Pieces.state(material.id, shape, orientation);

                        if (state == null) {
                            continue;
                        }

                        world.setBlockState(position, state, 2);

                        ItemStack item = Pieces.stack(state, 3);

                        require(item != null && !item.isEmpty() && item.getCount() == 3, "Piece item " + material.id);
                        require(world.getTileEntity(position) == null,
                                "Material pieces must not create block entities");
                        require(state.getMaterial() == full.getMaterial(), "Native block material " + material.id);
                        require(state.getBlock().getBlockHardness(state, world, position) == full.getBlock()
                                .getBlockHardness(full, world, position), "Native hardness " + material.id);
                        Catalogue.MaterialDef placedMaterial = state.getBlock() instanceof PieceBlock
                                ? ((PieceBlock) state.getBlock()).definition(state) : null;
                        // Retired rocks keep their declared resistance even when Mineralogy supplies the full block.
                        // Reused native slabs and stairs retain their own native properties.
                        float resistance = placedMaterial != null && placedMaterial.explosionResistance() >= 0
                                ? placedMaterial.explosionResistance()
                                : full.getBlock().getExplosionResistance(full, world, position, null, null);

                        require(state.getBlock().getExplosionResistance(state, world, position, null, null) == resistance,
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
                            List<AxisAlignedBB> boxes = state.getCollisionShape(world, position).toBoundingBoxList();
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

                        if (state.has(net.minecraft.block.SlabBlock.WATERLOGGED)) {
                            BlockState wet = state.with(net.minecraft.block.SlabBlock.WATERLOGGED, true);

                            world.setBlockState(position, wet, 2);
                            require(world.getFluidState(position).getFluid() == net.minecraft.fluid.Fluids.WATER,
                                    "Waterlogged material lost its fluid: " + material.id + " / " + shape);
                            require(net.minecraft.nbt.NBTUtil.readBlockState(
                                    net.minecraft.nbt.NBTUtil.writeBlockState(wet)).equals(wet),
                                    "Waterlogged orientation did not survive serialization: " + material.id);
                            world.setBlockState(position, state, 2);
                        }

                        checked++;
                    }
                }
            }

            long registered = ForgeRegistries.BLOCKS.getValues().stream()
                    .filter(block -> block.getRegistryName().getNamespace().equals(SkysBuildingPiecesBop.ID)).count();

            require(registered == 74 && registered <= 80, "Complete add-on block budget: " + registered);
            System.out.println("BOP_NATIVE_MATERIALS_PASS states=" + checked + " blocks=" + registered);
        } finally {
            world.setBlockState(position, previous, 2);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
