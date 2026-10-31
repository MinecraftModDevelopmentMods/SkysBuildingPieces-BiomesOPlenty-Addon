package zone.moddev.mc.skysbuildingpiecesbop;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.state.IProperty;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.HistoricalFullBlocks;

/** Recover the old material palettes before vanilla tries to flatten their numeric states. */
public final class BopFlattening {
    public static final String MODULE = "bop_flattening";

    private BopFlattening() {
    }

    public static void register() {
        for (String holder : new String[] { "stone", "crag_rock", "stone_formations", "jelled_poison", "tree_moss" }) {
            HistoricalFullBlocks.registerHolderCoverage(SkysBuildingPiecesBop.ID + ":legacy_bop_" + holder, MODULE);
            HistoricalFullBlocks.registerFlatteningCoverage("biomesoplenty:" + holder, MODULE);
        }

        HistoricalFullBlocks.registerStates(SkysBuildingPiecesBop.ID + ":stone_formations",
                SkysBuildingPiecesBop.ID + ":stone_formations", MODULE,
                metadata -> metadata == 0 ? RetiredDecorations.STONE_FORMATIONS.getDefaultState() : null);
        HistoricalFullBlocks.registerStates(SkysBuildingPiecesBop.ID + ":jelled_poison",
                SkysBuildingPiecesBop.ID + ":jelled_poison", MODULE,
                metadata -> metadata == 0 ? RetiredDecorations.JELLED_POISON.getDefaultState() : null);

        String path = "/assets/skysbuildingpiecesbop/catalogue/legacy_states.json";
        Map<String, JsonObject[]> palettes = new LinkedHashMap<>();

        try (InputStreamReader reader = new InputStreamReader(BopFlattening.class.getResourceAsStream(path),
                StandardCharsets.UTF_8)) {
            JsonArray rows = new Gson().fromJson(reader, JsonArray.class);

            for (JsonElement element : rows) {
                JsonObject row = element.getAsJsonObject();
                String source = row.get("id").getAsString();
                int metadata = row.get("meta").getAsInt();
                JsonObject[] states = palettes.computeIfAbsent(source, key -> new JsonObject[16]);

                if (metadata < 0 || metadata >= states.length || states[metadata] != null) {
                    throw new IllegalStateException("Duplicate or invalid BOP legacy state: " + row);
                }

                states[metadata] = row;
            }
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read the BOP legacy state table", failure);
        }
        for (JsonElement element : BopWorldMappings.rows()) {
            JsonObject row = element.getAsJsonObject();
            String source = row.get("id").getAsString();
            int metadata = row.get("meta").getAsInt();
            JsonObject[] states = palettes.computeIfAbsent(source, key -> new JsonObject[16]);

            if (metadata < 0 || metadata >= states.length || states[metadata] != null) {
                throw new IllegalStateException("Duplicate or invalid BOP world state: " + row);
            }

            states[metadata] = row;
        }
        for (Map.Entry<String, JsonObject[]> palette : palettes.entrySet()) {
            HistoricalFullBlocks.registerStates(palette.getKey(), palette.getKey(), MODULE,
                    metadata -> resolve(palette.getValue()[metadata]));
        }

        HistoricalFullBlocks.registerStates(SkysBuildingPiecesBop.ID + ":retired_rocks",
                SkysBuildingPiecesBop.ID + ":retired_rocks", MODULE, metadata -> {
                    if (metadata < 0 || metadata >= RetiredRocks.NAMES.length) {
                        return null;
                    }

                    String[] materials = { "crag_rock", "limestone", "limestone_polished", "shale", "shale_polished",
                            "siltstone", "siltstone_polished" };

                    return Pieces.nativeState(Catalogue.INSTANCE.materials.get("biomesoplenty:" + materials[metadata]),
                            "full");
                });
    }

    static BlockState resolve(JsonObject row) {
        if (row == null) {
            return null;
        }
        if (row.has("target")) {
            return BopWorldMappings.state(row.getAsJsonObject("target"));
        }

        String material = row.get("material").getAsString();
        String role = row.get("role").getAsString();
        BlockState result;

        if (role.equals("slab")) {
            result = Pieces.state(material, Shape.SLAB,
                    row.getAsJsonObject("properties").get("half").getAsString().equals("top") ? 0 : 1);
        } else if (role.equals("stairs")) {
            result = Pieces.state(material, Shape.STAIRS, row.get("meta").getAsInt());
        } else {
            result = Pieces.nativeState(Catalogue.INSTANCE.materials.get(material), role);
        }
        if (result == null) {
            throw new IllegalStateException("No equivalent BOP geometry: " + row);
        }
        for (Map.Entry<String, JsonElement> property : row.getAsJsonObject("properties").entrySet()) {
            // Variant selects the material; the remaining native properties retain orientation and gate state.
            if (property.getKey().equals("variant") || role.equals("slab") && property.getKey().equals("half")) {
                continue;
            }

            IProperty<?> target = result.getBlock().getStateContainer().getProperty(property.getKey());

            if (target != null) {
                result = withProperty(result, target, property.getValue().getAsString());
            }
        }

        return result;
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, IProperty<T> property,
            String value) {
        return state.with(property, property.parseValue(value).orElseThrow(
                () -> new IllegalStateException("Invalid recovered property " + property.getName() + "=" + value)));
    }
}
