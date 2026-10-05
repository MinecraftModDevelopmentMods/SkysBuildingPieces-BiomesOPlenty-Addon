package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.*;

/** Genuine mixed-material save, with no reliance on dynamic item damage. */
public final class BopLegacyFixture {
    private static final String[] IDS={"wood_vertical_slab","wood_corner","rock_wall","metal_stairs","wood_slab","rock_slab"};
    private static final String[] MATERIALS={"biomesoplenty:planks_cherry","biomesoplenty:planks_hellbark","biomesoplenty:limestone","biomesoplenty:amethyst_block","biomesoplenty:planks_ebony","biomesoplenty:crag_rock"};
    private static BlockPos pos(int i){return new BlockPos(800+(i&15),70,800+(i>>4));}
    private static String id(int i){return i<32?"rock_step":IDS[i-32];}
    private static String material(int i){return i<16?"minecraft:stone":i<32?"biomesoplenty:limestone":MATERIALS[i-32];}
    private static int meta(int i){return i<32?i&15:i==32?3:i==33?7:i==35?6:i==36?1:0;}
    public static void run(WorldServer world,String phase) throws Exception {
        boolean source=phase.equals("bop-legacy-source")||phase.equals("bop-legacy-unknown-source");
        boolean converted=LegacyBridge.active();
        if(source){
            require(!converted,"source replacement must be disabled");
            for(int i=0;i<38;i++){
                Block old=Block.getBlockFromName("buildingbricks:"+id(i));require(old!=null,"real legacy block "+id(i));
                world.setBlockState(pos(i),old.getStateFromMeta(meta(i)),2);
                TileEntity tile=world.getTileEntity(pos(i));require(tile!=null,"real legacy material tile");
                NBTTagCompound raw=tile.writeToNBT(new NBTTagCompound());raw.setString("material",material(i));raw.setString("fixture_note","preserve unrelated tile data");tile.readFromNBT(raw);tile.markDirty();
            }
            world.setBlockState(pos(40),Blocks.CHEST.getDefaultState(),2);TileEntityChest chest=(TileEntityChest)world.getTileEntity(pos(40));
            chest.setInventorySlotContents(0,stack("minecraft:stone",7));chest.setInventorySlotContents(1,stack("biomesoplenty:limestone",47));
            world.spawnEntity(new EntityItem(world,804,72,804,stack("biomesoplenty:limestone",5)));
            if(phase.equals("bop-legacy-unknown-source")){
                TileEntity tile=world.getTileEntity(pos(0));NBTTagCompound raw=tile.writeToNBT(new NBTTagCompound());raw.setString("material","missing:unknown");tile.readFromNBT(raw);tile.markDirty();
            }
        }else{
            for(int i=0;i<38;i++){
                IBlockState actual=world.getBlockState(pos(i));
                if(converted){
                    ShapePair pair=new ShapePair(id(i),meta(i));IBlockState expected=Pieces.state(material(i),pair.shape,pair.orientation);
                    require(actual.equals(expected),"identity and orientation "+i+" "+actual+" != "+expected);
                    require(world.getTileEntity(pos(i))==null,"old material tile removed only after conversion");
                }else{
                    require(actual.getBlock().getRegistryName().toString().equals("buildingbricks:"+id(i)),"replacement off identity");
                    require(world.getTileEntity(pos(i)).writeToNBT(new NBTTagCompound()).getString("material").equals(material(i)),"retained material");
                }
            }
            TileEntityChest chest=(TileEntityChest)world.getTileEntity(pos(40));
            for(int slot=0;slot<2;slot++){
                ItemStack actual=chest.getStackInSlot(slot);require(actual!=null&&actual.stackSize==(slot==0?7:47),"container count");
                require(actual.getTagCompound().equals(tag(slot==0?"minecraft:stone":"biomesoplenty:limestone")),"complete item NBT");
                require(actual.getItem().getRegistryName().getResourceDomain().equals(converted?(slot==0?"skysbuildingpieces":"skysbuildingpiecesbop"):"buildingbricks"),"module item identity");
            }
            if(converted){LegacyBridge.report(world);PiecesWorldState state=PiecesWorldState.get(world);
                require(state.blocks==16&&state.items==7,"vanilla accounting "+state.blocks+" / "+state.items);
                PiecesWorldState.Totals bop=state.modules.get("biomesoplenty");require(bop!=null&&bop.blocks==22&&bop.items==52,"BOP accounting");
            }else require(!new java.io.File(world.getSaveHandler().getWorldDirectory(),"data/skysbuildingpieces_world_state.dat").exists(),"replacement off no state");
        }
        System.out.println("BUILDING_PIECES_LEGACY_PASS "+phase+" vanilla=16 BOP=22 items=7/52");
    }
    private static ItemStack stack(String material,int count){
        NBTTagCompound raw=new NBTTagCompound();raw.setString("id","buildingbricks:rock_step");raw.setByte("Count",(byte)count);raw.setShort("Damage",(short)32767);
        raw.setTag("tag",tag(material));return ItemStack.loadItemStackFromNBT(raw);
    }
    private static NBTTagCompound tag(String material){NBTTagCompound tag=new NBTTagCompound();tag.setString("material",material);tag.setString("custom_label","complete NBT survives");tag.setInteger("RepairCost",9);return tag;}
    private static final class ShapePair {
        final zone.moddev.mc.skysbuildingpieces.catalogue.Shape shape;final int orientation;
        ShapePair(String id,int meta){shape=LegacyMapping.shape("buildingbricks:"+id,meta);orientation=LegacyMapping.orientation(shape,meta);}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
