package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.datafix.DefaultTypeReferences;
import net.minecraft.world.server.ServerWorld;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyMapping;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyTestScope;

/** All BOP materials share the old generic step ID; only their saved material selects the target. */
public final class BopMigrationChecks {
    private BopMigrationChecks() {
    }

    public static void run(ServerWorld world) throws Exception {
        boolean forced = SkysBuildingPieces.forceReplace;
        int checked = 0;

        try (LegacyTestScope source = new LegacyTestScope(world)) {
            SkysBuildingPieces.forceReplace = true;

            for (Catalogue.MaterialDef material : Catalogue.INSTANCE.modules.get("biomesoplenty").materials.values()) {
                for (int code = 0; code < 16; code++) {
                    CompoundNBT raw = NativeLegacyChecks.chunk(LegacyTestScope.STEP_ID, code, material.id);

                    raw.putInt("DataVersion", 512);

                    CompoundNBT coverage = new CompoundNBT();

                    coverage.putInt("vanilla", 1);
                    coverage.putInt("future_addon", 9);
                    raw.getCompound("Level").put("skysbuildingpieces_coverage", coverage);
                    LegacyBridge.prepareLegacyChunk(raw);

                    Shape shape = LegacyMapping.shape("buildingbricks:rock_step", code);
                    BlockState expected = Pieces.state(material.id, shape, LegacyMapping.orientation(shape, code));
                    BlockState staged = LegacyTestScope
                            .staged(raw.getCompound("Level").getList("Sections", 10).getCompound(0), 0);

                    require(expected.equals(staged), "Wrong legacy material or shape: " + material.id + "/" + code);
                    require(coverage.getInt("vanilla") == 1 && coverage.getInt("biomesoplenty") == 1
                            && coverage.getInt("future_addon") == 9, "Independent module coverage changed");

                    CompoundNBT fixed = NBTUtil.update(world.getServer().getFixerUpper(), DefaultTypeReferences.CHUNK,
                            raw.copy(), 512, 1976);

                    require(contains(fixed, expected), "Native flattening lost " + material.id + "/" + code);
                    require(fixed.getCompound("Level").getList("TileEntities", 10).isEmpty(),
                            "Native fixing invented a block entity for " + material.id + "/" + code);
                    checked++;
                }

                CompoundNBT remaining = NativeLegacyChecks.chunk(LegacyTestScope.STEP_ID, 0, material.id);
                CompoundNBT coverage = new CompoundNBT();

                coverage.putInt("vanilla", 1);
                coverage.putInt("biomesoplenty", 1);
                coverage.putInt("dirt_vertical_slabs", 1);
                remaining.getCompound("Level").put("skysbuildingpieces_coverage", coverage);
                LegacyBridge.prepareLegacyChunk(remaining);
                require(LegacyTestScope.staged(remaining.getCompound("Level").getList("Sections", 10).getCompound(0),
                        0) != null, "Remaining covered BOP content was skipped: " + material.id);
                require(remaining.getCompound("Level").getCompound("skysbuildingpieces_additional_module_delta")
                        .getLong("biomesoplenty") == 1, "Covered recovery must add only the actual conversion");

                zone.moddev.mc.skysbuildingpieces.legacy.PiecesWorldState totals = zone.moddev.mc.skysbuildingpieces.legacy.PiecesWorldState
                        .get(world);

                totals.addBlocks("biomesoplenty", 0);

                long blocks = totals.modules.get("biomesoplenty").blocks;
                long chunks = totals.modules.get("biomesoplenty").chunks;

                LegacyBridge.finalizeLegacyChunk(remaining);
                new zone.moddev.mc.skysbuildingpieces.legacy.MigrationEvents()
                        .load(new net.minecraftforge.event.world.ChunkDataEvent.Load(world.getChunk(0, 0),
                                remaining.getCompound("Level"), net.minecraft.world.chunk.ChunkStatus.Type.LEVELCHUNK));
                require(totals.modules.get("biomesoplenty").blocks == blocks + 1
                        && totals.modules.get("biomesoplenty").chunks == chunks,
                        "Covered recovery must not count the chunk twice");
            }
        } finally {
            SkysBuildingPieces.forceReplace = forced;
        }

        System.out.println("BOP_NATIVE_LEGACY_PASS states=" + checked + " material_nbt=true covered_chunks=true");
    }

    private static boolean contains(CompoundNBT root, BlockState expected) {
        CompoundNBT encoded = NBTUtil.writeBlockState(expected);
        ListNBT sections = root.getCompound("Level").getList("Sections", 10);

        for (int index = 0; index < sections.size(); index++) {
            ListNBT palette = sections.getCompound(index).getList("Palette", 10);

            for (int entry = 0; entry < palette.size(); entry++) {
                if (encoded.equals(palette.getCompound(entry))) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
