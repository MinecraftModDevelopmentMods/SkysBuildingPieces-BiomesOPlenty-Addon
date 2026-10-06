package zone.moddev.mc.skysbuildingpiecesbop;

import com.google.gson.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;
import static org.junit.jupiter.api.Assertions.*;

class BopCatalogueTest {
    private static final Path ROOT=Paths.get("src/main/resources/assets/skysbuildingpiecesbop");
    private Catalogue.Module module() {
        return new Catalogue().registerModule("biomesoplenty","skysbuildingpiecesbop",
            Catalogue.read(getClass(),"/assets/skysbuildingpiecesbop/catalogue/materials.json"),
            Catalogue.read(getClass(),"/assets/skysbuildingpiecesbop/catalogue/palettes.json"),80);
    }
    @Test void fixedCatalogueBudgetAndAllMetadata() throws Exception {
        Catalogue.Module m=module();assertEquals(33,m.materials.size());assertEquals(66,m.palettes.size());
        Catalogue core=new Catalogue();int expectedCore=zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces.VERSION.equals("0.3.0.110021")?235:234;
        assertEquals(expectedCore,core.palettes.size());assertEquals(expectedCore+67,core.palettes.size()+1+m.palettes.size());
        if(expectedCore==235){Catalogue.Palette dirt=core.palettes.get(234);assertEquals("dirt_vertical_slab_00",dirt.id);assertEquals(Collections.singletonList("minecraft:dirt"),dirt.materials);}
        assertTrue(m.materials.containsKey("biomesoplenty:planks_ebony"));assertTrue(m.materials.containsKey("biomesoplenty:planks_eucalyptus"));
        Set<String> identities=new HashSet<String>();
        for(Catalogue.Palette p:m.palettes) {
            assertTrue(identities.add(p.registryId()));assertEquals("skysbuildingpiecesbop",p.namespace);
            for(String material:p.materials)for(int o=0;o<p.shape.states;o++){int meta=p.meta(material,o);assertTrue(meta<16);assertEquals(material,p.material(meta));assertEquals(o,meta%p.shape.states);}
            assertFalse(p.group.equals("wood")&&p.shape==Shape.WALL);
            for(String material:p.materials)assertNull(m.materials.get(material).block(p.shape.name().toLowerCase(Locale.ROOT)),"native equivalent must be reused");
        }
        Properties locked=new Properties();try(java.io.InputStream in=Files.newInputStream(Paths.get("gradle/catalogue-checksums.properties"))){locked.load(in);}
        for(String name:new String[]{"materials","palettes"}) {
            StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ROOT.resolve("catalogue/"+name+".json"))))hash.append(String.format("%02X",b));
            assertEquals(locked.getProperty(name),hash.toString());
        }
    }
    @Test void hellbarkAndAllNativeMaterialsHaveExplicitFireRules() {
        Catalogue.Module m=module();
        for(Catalogue.MaterialDef d:m.materials.values()){assertTrue(d.nativeProperties());assertTrue(d.flammability()>=0);assertTrue(d.fireSpread()>=0);}
        assertEquals(0,m.materials.get("biomesoplenty:planks_hellbark").flammability());
        assertEquals(0,m.materials.get("biomesoplenty:planks_hellbark").fireSpread());
        assertEquals(0,m.materials.get("biomesoplenty:bamboo_thatching").flammability());
        assertEquals(20,m.materials.get("biomesoplenty:planks_redwood").flammability());
        assertEquals(5,m.materials.get("biomesoplenty:limestone").explosionResistance());
        assertEquals(7,m.materials.get("biomesoplenty:limestone_polished").explosionResistance());
    }
    @Test void modelsAndTexturesAreCompleteAndInternalFacesUnculled() throws Exception {
        for(Catalogue.Palette p:module().palettes) {
            JsonObject state=json(ROOT.resolve("blockstates/"+p.id+".json"));
            JsonObject variants=state.getAsJsonObject("variants");
            int perMeta=p.shape==Shape.WALL?16:p.shape==Shape.STAIRS?5:1;
            assertEquals(16*perMeta,variants.entrySet().size(),p.id);
            for(int meta=0;meta<16;meta++) {
                int covered=0;for(Map.Entry<String,JsonElement> variant:variants.entrySet())for(String part:variant.getKey().split(","))if(part.equals("meta="+meta))covered++;
                assertEquals(perMeta,covered,p.id+" / "+meta);
            }
            for(Map.Entry<String,JsonElement> e:state.getAsJsonObject("variants").entrySet()) {
                String model=e.getValue().getAsJsonObject().get("model").getAsString().split(":")[1];
                JsonObject data=json(ROOT.resolve("models/block/"+model+".json"));assertTrue(data.has("textures"));
                for(JsonElement element:data.getAsJsonArray("elements"))for(Map.Entry<String,JsonElement> face:element.getAsJsonObject().getAsJsonObject("faces").entrySet())assertFalse(face.getValue().getAsJsonObject().has("cullface"));
            }
            for(String material:p.materials)assertTrue(Files.isRegularFile(ROOT.resolve("models/item/"+material.split(":")[1]+"_"+p.shape.name().toLowerCase(Locale.ROOT)+".json")));
        }
        assertFalse(Files.exists(Paths.get("src/main/resources/assets/biomesoplenty")));
    }
    @Test void allEighteenUtf8LocalesMatch() throws Exception {
        Set<String> expected=new TreeSet<String>(Arrays.asList("de_AT","de_AU","de_DE","en_CA","en_EN","en_GB","en_PT","en_US","es_ES","es_MX","fr_CA","fr_FR","ja_JP","ko_KR","pt_BR","pt_PT","ru_RU","zh_CN"));
        Set<String> actual=new TreeSet<String>();List<String> baseline=null;
        try(java.util.stream.Stream<Path> files=Files.list(ROOT.resolve("lang"))) {
            for(Path file:(Iterable<Path>)files::iterator) {
                actual.add(file.getFileName().toString().replace(".lang",""));
                String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(Files.readAllBytes(file))).toString();
                assertFalse(text.startsWith("\uFEFF"));assertFalse(text.contains("\r"));assertFalse(text.contains("\uFFFD"));assertTrue(text.endsWith("\n"));
                List<String> keys=new ArrayList<String>();for(String line:text.split("\n")){assertEquals(line.trim(),line);int split=line.indexOf('=');assertTrue(split>0&&split<line.length()-1);String key=line.substring(0,split);assertFalse(keys.contains(key));keys.add(key);}
                assertEquals(9,keys.size());if(baseline==null)baseline=keys;else assertEquals(baseline,keys);
            }
        }
        assertEquals(expected,actual);
    }
    private JsonObject json(Path file)throws Exception {return new JsonParser().parse(new String(Files.readAllBytes(file),StandardCharsets.UTF_8)).getAsJsonObject();}
}
