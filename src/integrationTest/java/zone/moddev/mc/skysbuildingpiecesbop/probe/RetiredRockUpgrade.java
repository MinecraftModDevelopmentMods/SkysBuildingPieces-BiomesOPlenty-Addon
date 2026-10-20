package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.io.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.*;

/** Checks a world saved by the exact accepted 1.10 production jars. */
public final class RetiredRockUpgrade {
    public static void run(WorldServer world, String phase) throws Exception {
        if (phase.startsWith("retired-rocks-items-")) {
            RetiredItemUpgrade.run(world, phase);
            return;
        }
        if (phase.startsWith("retired-rocks-baskets-")) {
            BasketUpgrade.run(world, phase);
            return;
        }

        String[] materials = { "limestone", "siltstone", "shale", "limestone_polished", "siltstone_polished",
                "shale_polished", "crag_rock" };

        world.getChunk(31, 31);

        TileEntityChest chest = (TileEntityChest) world.getTileEntity(new BlockPos(508, 100, 508));

        if (chest == null) {
            throw new AssertionError("Source chest lost");
        }
        for (int entryIndex = 0; entryIndex < materials.length; entryIndex++) {
            IBlockState expected = Pieces
                    .nativeState(zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue.INSTANCE.materials
                            .get("biomesoplenty:" + materials[entryIndex]), "full");

            if (!world.getBlockState(new BlockPos(500 + entryIndex, 100, 500)).equals(expected)) {
                throw new AssertionError("Retired full block identity " + entryIndex);
            }

            ItemStack actual = chest.getStackInSlot(entryIndex);
            ItemStack target = Pieces.stack(expected, 17 + entryIndex);

            if (actual.isEmpty() || actual.getItem() != target.getItem() || actual.getMetadata() != target.getMetadata()
                    || actual.getCount() != 17 + entryIndex) {
                throw new AssertionError("Retired chest identity/count " + entryIndex);
            }

            NBTTagCompound tag = actual.getTagCompound();

            if (tag == null || !tag.getString("Fixture").equals("retired-rock-" + entryIndex)
                    || !tag.getCompoundTag("display").getString("Name").equals("Preserved rock " + entryIndex)
                    || tag.getCompoundTag("Unrelated").getInteger("Number") != 700 + entryIndex) {
                throw new AssertionError("Retired custom item NBT " + entryIndex);
            }
        }

        int entities = 0;
        int units = 0;

        for (net.minecraft.entity.Entity entity : world.loadedEntityList) {
            if (entity instanceof EntityItem) {
                ItemStack stack = ((EntityItem) entity).getItem();

                if (stack.getTagCompound() != null
                        && stack.getTagCompound().getString("Fixture").startsWith("retired-rock-")) {
                    int entryIndex = Integer.parseInt(stack.getTagCompound().getString("Fixture").substring(13));
                    IBlockState expected = Pieces
                            .nativeState(zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue.INSTANCE.materials
                                    .get("biomesoplenty:" + materials[entryIndex]), "full");
                    ItemStack target = Pieces.stack(expected, 17 + entryIndex);

                    if (stack.getItem() != target.getItem() || stack.getMetadata() != target.getMetadata()
                            || stack.getCount() != 17 + entryIndex) {
                        throw new AssertionError("Retired entity stack " + entryIndex);
                    }

                    entities++;
                    units += stack.getCount();
                }
            }
        }
        if (entities != 7 || units != 140) {
            throw new AssertionError("Retired entities: " + entities + " / " + units);
        }

        LegacyBridge.report(world);

        PiecesWorldState state = PiecesWorldState.get(world);
        PiecesWorldState.Totals retired = state.modules.get(HistoricalFullBlocks.MODULE);

        if (retired == null || retired.blocks != 7 || retired.items != 280) {
            throw new AssertionError("Retired fixture counters: "
                    + (retired == null ? "missing" : retired.blocks + " / " + retired.items));
        }

        File marker = new File(world.getSaveHandler().getWorldDirectory(), "retired-rock-upgrade.properties");
        Properties saved = new Properties();

        if (marker.isFile()) {
            try (InputStream input = new FileInputStream(marker)) {
                saved.load(input);
            }
        }

        saved.setProperty(phase, "passed");

        try (OutputStream output = new FileOutputStream(marker)) {
            saved.store(output, "Disposable retired-rock qualification");
        }

        System.out.println(
                "RETIRED_ROCK_UPGRADE_PASS " + phase + " variants=7 chest_units=140 entity_units=140 counters=7/280");
    }
}
