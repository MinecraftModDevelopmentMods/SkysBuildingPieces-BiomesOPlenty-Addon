package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;
import zone.moddev.mc.skysbuildingpieces.content.*;
import zone.moddev.mc.skysbuildingpieces.legacy.*;

/** Real BOP physics, early raw migration and independent coverage; build-only. */
public final class BopChecks {
    public static void run(WorldServer world) {
        require(Catalogue.INSTANCE.modules.get("biomesoplenty").palettes.size()==66,"BOP ID budget");
        int corePalettes=SkysBuildingPieces.VERSION.equals("0.3.0.110021")?235:234;
        require(Catalogue.INSTANCE.modules.get("vanilla").palettes.size()==corePalettes,"core palette budget");
        int ids=0,highest=0;for(Block b:Block.REGISTRY){ids++;highest=Math.max(highest,Block.getIdFromBlock(b));}
        require(highest<4096,"legacy registry ceiling");System.out.println("BOP_REGISTRY_BUDGET registered="+ids+" highest="+highest+" free="+(4096-ids));
        BlockPos pos=new BlockPos(-700,90,-700);int checked=0;
        for(Catalogue.MaterialDef d:Catalogue.INSTANCE.modules.get("biomesoplenty").materials.values())for(Shape shape:new Shape[]{Shape.SLAB,Shape.VERTICAL_SLAB,Shape.HORIZONTAL_STEP,Shape.VERTICAL_STEP,Shape.CORNER,Shape.STAIRS,Shape.WALL})for(int o=0;o<shape.states;o++) {
            IBlockState target=Pieces.state(d.id,shape,o);if(target==null)continue;
            require(Pieces.stack(target,1)!=null,"registered item");
            if(target.getBlock() instanceof PieceBlock) {
                PieceBlock b=(PieceBlock)target.getBlock();world.setBlockState(pos,target,2);IBlockState full=Pieces.nativeState(d,"full");
                float hard=b.getBlockHardness(target,world,pos);float blast=b.getExplosionResistance(world,pos,null,null);
                int fire=b.getFlammability(world,pos,EnumFacing.UP),spread=b.getFireSpreadSpeed(world,pos,EnumFacing.UP);
                world.setBlockState(pos,full,2);
                require(hard==full.getBlock().getBlockHardness(full,world,pos),"native hardness "+d.id);
                require(blast==full.getBlock().getExplosionResistance(world,pos,null,null),"native blast resistance "+d.id);
                require(fire==full.getBlock().getFlammability(world,pos,EnumFacing.UP),"native flammability "+d.id);
                require(spread==full.getBlock().getFireSpreadSpeed(world,pos,EnumFacing.UP),"native spread "+d.id);
                require(target.getMaterial()==full.getMaterial(),"native block material "+d.id);
            }
            checked++;
        }
        world.setBlockToAir(pos);
        Block old=Block.getBlockFromName("buildingbricks:rock_step");require(old!=null,"legacy registry probe");
        boolean installed=net.minecraftforge.fml.common.Loader.isModLoaded("buildingbricks"),forced=SkysBuildingPieces.forceReplace;
        try {
            if(installed) {
                SkysBuildingPieces.forceReplace=false;NBTTagCompound off=chunk(old,0,"biomesoplenty:limestone"),copy=off.copy();
                LegacyBridge.prepareChunk(world,off);require(copy.equals(off),"replacement off does not mark or migrate");
                inventories(world,false);
                SkysBuildingPieces.forceReplace=true;
                inventories(world,true);
            }
            for(int meta=0;meta<16;meta++) {
                NBTTagCompound raw=chunk(old,meta,"biomesoplenty:limestone"),level=raw.getCompoundTag("Level"),coverage=new NBTTagCompound();
                coverage.setInteger("vanilla",1);coverage.setInteger("later_addon",7);level.setTag("skysbuildingpieces_coverage",coverage);
                LegacyBridge.prepareChunk(world,raw);
                NBTTagCompound section=level.getTagList("Sections",10).getCompoundTagAt(0);
                int id=(section.getByteArray("Blocks")[0]&255)|(LegacyBridge.nibble(section.getByteArray("Add"),0)<<8);
                IBlockState actual=Block.getBlockById(id).getStateFromMeta(LegacyBridge.nibble(section.getByteArray("Data"),0));
                Shape shape=LegacyMapping.shape("buildingbricks:rock_step",meta);
                require(actual.equals(Pieces.state("biomesoplenty:limestone",shape,LegacyMapping.orientation(shape,meta))),"BOP legacy geometry "+meta);
                require(coverage.getInteger("vanilla")==1&&coverage.getInteger("biomesoplenty")==1&&coverage.getInteger("later_addon")==7,"independent markers");
                require(!raw.hasKey("skysbuildingpieces_delta")&&raw.getCompoundTag("skysbuildingpieces_module_delta").getLong("biomesoplenty")==1,"BOP only delta");
                require(level.getTagList("TileEntities",10).tagCount()==0&&coverage.getTagList("retained_tiles",10).tagCount()==1,"successful tile removal and retention");
                NBTTagCompound before=raw.copy();LegacyBridge.prepareChunk(world,raw);require(before.equals(raw),"chunk recovery idempotent");
            }
            NBTTagCompound raw=new NBTTagCompound(),tag=new NBTTagCompound();raw.setString("id","buildingbricks:rock_step");raw.setByte("Count",(byte)17);raw.setShort("Damage",(short)30000);
            tag.setString("material","biomesoplenty:limestone");tag.setString("custom","unchanged");raw.setTag("tag",tag);
            LegacyBridge.prepareStack(raw);ItemStack stack=ItemStack.loadItemStackFromNBT(raw);
            require(stack!=null&&stack.stackSize==17&&stack.getTagCompound().equals(tag),"item count and unrelated NBT");
            require(stack.getItem().getRegistryName().getResourceDomain().equals("skysbuildingpiecesbop"),"item owner");
            NBTTagCompound before=raw.copy();LegacyBridge.prepareStack(raw);require(before.equals(raw),"item recovery idempotent");
            PiecesWorldState state=new PiecesWorldState();NBTTagCompound saved=new NBTTagCompound();saved.setInteger("schema_version",1);saved.setLong("vanilla_blocks",6062);saved.setLong("vanilla_items",1170);saved.setLong("vanilla_chunks",193);saved.setString("future_field","preserved");
            state.readFromNBT(saved);state.addChunk("biomesoplenty",601);state.addItems("biomesoplenty",52);NBTTagCompound result=state.writeToNBT(new NBTTagCompound());
            require(result.getLong("vanilla_blocks")==6062&&result.getLong("vanilla_items")==1170&&result.getString("future_field").equals("preserved"),"historical vanilla state unchanged");
            PiecesWorldState reloaded=new PiecesWorldState();reloaded.readFromNBT(result);require(reloaded.modules.get("biomesoplenty").blocks==601&&reloaded.modules.get("biomesoplenty").items==52,"module state continuity");
        }finally{SkysBuildingPieces.forceReplace=forced;}
        System.out.println("BOP_RUNTIME_PASS states="+checked+" legacy_step_codes=16");
    }
    private static ItemStack legacy(int count) {
        ItemStack stack=new ItemStack(net.minecraft.item.Item.getByNameOrId("buildingbricks:rock_step"),count,30000);
        NBTTagCompound tag=new NBTTagCompound();tag.setString("material","biomesoplenty:limestone");tag.setString("custom_label","unchanged");
        NBTTagList enchantments=new NBTTagList();NBTTagCompound enchantment=new NBTTagCompound();enchantment.setShort("id",(short)33);enchantment.setShort("lvl",(short)2);enchantments.appendTag(enchantment);tag.setTag("ench",enchantments);stack.setTagCompound(tag);return stack;
    }
    private static void identity(ItemStack stack,int count,boolean convert) {
        require(stack!=null&&stack.stackSize==count,"BOP inventory count");
        require(stack.getItem().getRegistryName().getResourceDomain().equals(convert?"skysbuildingpiecesbop":"buildingbricks"),"BOP inventory owner");
        require(stack.getTagCompound().equals(legacy(1).getTagCompound()),"BOP complete inventory NBT");
    }
    private static void inventories(WorldServer world,boolean convert) {
        try {
            net.minecraft.entity.player.EntityPlayerMP player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world);
            player.inventory.setInventorySlotContents(0,legacy(13));player.getInventoryEnderChest().setInventorySlotContents(0,legacy(11));
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.EntityJoinWorldEvent(player,world));
            identity(player.inventory.getStackInSlot(0),13,convert);identity(player.getInventoryEnderChest().getStackInSlot(0),11,convert);
            player.inventory.setInventorySlotContents(1,legacy(9));
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerEvent.LoadFromFile(player,world.getSaveHandler().getWorldDirectory(),player.getUniqueID().toString()));
            identity(player.inventory.getStackInSlot(1),9,convert);
            BlockPos pos=new BlockPos(934,70,936);world.getChunkFromBlockCoords(pos);
            net.minecraft.entity.item.EntityItem drop=new net.minecraft.entity.item.EntityItem(world,934,70,936,legacy(5));require(world.spawnEntity(drop),"BOP dropped entity join");identity(drop.getEntityItem(),5,convert);
            world.setBlockState(pos.north(),net.minecraft.init.Blocks.STONE.getDefaultState(),2);
            net.minecraft.entity.item.EntityItemFrame frame=new net.minecraft.entity.item.EntityItemFrame(world,pos,EnumFacing.SOUTH);frame.setDisplayedItem(legacy(1));world.spawnEntity(frame);identity(frame.getDisplayedItem(),1,convert);
            zone.moddev.mc.skysbuildingpieces.probe.CompatibilityChecks.HandlerTile handler=new zone.moddev.mc.skysbuildingpieces.probe.CompatibilityChecks.HandlerTile();handler.setWorld(world);
            net.minecraftforge.items.IItemHandlerModifiable items=(net.minecraftforge.items.IItemHandlerModifiable)handler.getCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,null);items.setStackInSlot(0,legacy(17));
            java.lang.reflect.Method scan=MigrationEvents.class.getDeclaredMethod("containers",net.minecraft.tileentity.TileEntity.class);scan.setAccessible(true);scan.invoke(null,handler);identity(items.getStackInSlot(0),17,convert);
            ItemStack first=items.getStackInSlot(0);scan.invoke(null,handler);require(items.getStackInSlot(0)==first,"BOP handler duplicate-face idempotence");
            world.setBlockState(pos,net.minecraft.init.Blocks.CHEST.getDefaultState(),2);net.minecraft.tileentity.TileEntityChest chest=(net.minecraft.tileentity.TileEntityChest)world.getTileEntity(pos);chest.setInventorySlotContents(0,legacy(7));scan.invoke(null,chest);identity(chest.getStackInSlot(0),7,convert);
            IBlockState old=Block.getBlockFromName("buildingbricks:rock_step").getStateFromMeta(11);world.setBlockState(pos,old,2);
            net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);NBTTagCompound raw=tile.writeToNBT(new NBTTagCompound());raw.setString("material","biomesoplenty:limestone");tile.readFromNBT(raw);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.BlockEvent.PlaceEvent(net.minecraftforge.common.util.BlockSnapshot.getBlockSnapshot(world,pos),net.minecraft.init.Blocks.STONE.getDefaultState(),player,net.minecraft.util.EnumHand.MAIN_HAND));
            require(world.getBlockState(pos).getBlock().getRegistryName().getResourceDomain().equals(convert?"skysbuildingpiecesbop":"buildingbricks"),"BOP placed replacement gating");
            require((world.getTileEntity(pos)==null)==convert,"BOP material tile removed only after conversion");
            drop.setDead();frame.setDead();world.setBlockToAir(pos);world.setBlockToAir(pos.north());player.inventory.clear();player.getInventoryEnderChest().clear();
            System.out.println("BOP_INVENTORY_PASS force="+convert);
        }catch(Exception failure){throw new AssertionError("BOP live inventory checks",failure);}
    }
    private static NBTTagCompound chunk(Block old,int meta,String material) {
        NBTTagCompound root=new NBTTagCompound(),level=new NBTTagCompound(),section=new NBTTagCompound();root.setTag("Level",level);
        byte[] low=new byte[4096],high=new byte[2048],data=new byte[2048];int id=Block.getIdFromBlock(old);low[0]=(byte)id;LegacyBridge.setNibble(high,0,id>>8);LegacyBridge.setNibble(data,0,meta);
        section.setByte("Y",(byte)4);section.setByteArray("Blocks",low);section.setByteArray("Add",high);section.setByteArray("Data",data);NBTTagList sections=new NBTTagList();sections.appendTag(section);level.setTag("Sections",sections);
        NBTTagCompound tile=new NBTTagCompound();tile.setString("id","buildingbricks:material");tile.setString("material",material);tile.setInteger("x",0);tile.setInteger("y",64);tile.setInteger("z",0);tile.setString("unrelated","retained");
        NBTTagList tiles=new NBTTagList();tiles.appendTag(tile);level.setTag("TileEntities",tiles);return root;
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
