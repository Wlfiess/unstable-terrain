package dev.greatsea;
import net.minecraft.block.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;

/** Freeze terrain water at every elevation, including cave aquifers. */
public final class MistTerrain {
 private MistTerrain(){}
 public static boolean preservesIce(net.minecraft.world.WorldView view,BlockPos p){
  var world=view instanceof net.minecraft.server.world.ServerWorld w?w:view instanceof net.minecraft.world.ChunkRegion r?r.toServerWorld():null;
  return world!=null&&MistRegion.active(world)&&MistRegion.contains(p.getX(),p.getZ());
 }
 public static BlockState[] column(net.minecraft.world.gen.chunk.VerticalBlockSample original,int bottom,int height){
  BlockState[] states=new BlockState[height];for(int i=0;i<height;i++)states[i]=frozen(original.getState(bottom+i));return states;
 }
 public static BlockState frozen(BlockState state){
  if(state.isOf(Blocks.WATER)||state.isOf(Blocks.KELP)||state.isOf(Blocks.KELP_PLANT)||state.isOf(Blocks.SEAGRASS)||state.isOf(Blocks.TALL_SEAGRASS))return Blocks.ICE.getDefaultState();
  if(state.contains(Properties.WATERLOGGED)&&state.get(Properties.WATERLOGGED))return state.with(Properties.WATERLOGGED,false);
  return state;
 }
 public static Chunk freeze(Chunk chunk,long seed){
  int x0=chunk.getPos().getStartX(),z0=chunk.getPos().getStartZ();
  if(!MistRegion.intersects(x0,z0))return chunk;
  var pos=new BlockPos.Mutable();var sections=chunk.getSectionArray();
  for(int s=0;s<sections.length;s++){
   var section=sections[s];if(!section.hasAny(state->frozen(state)!=state))continue;
   for(int z=0;z<16;z++)for(int x=0;x<16;x++){
    int bx=x0+x,bz=z0+z;if(!MistRegion.frost(seed,bx,bz))continue;
    for(int y=0;y<16;y++){
     var old=section.getBlockState(x,y,z);var state=frozen(old);
     if(state!=old)chunk.setBlockState(pos.set(bx,chunk.getBottomY()+s*16+y,bz),state,0);
    }
   }
  }return chunk;
 }
 public static void finish(Chunk chunk,StructureWorldAccess world){
  if(!MistRegion.intersects(chunk.getPos().getStartX(),chunk.getPos().getStartZ()))return;
  freeze(chunk,world.getSeed());int x0=chunk.getPos().getStartX(),z0=chunk.getPos().getStartZ();
  var pos=new BlockPos.Mutable();
  for(int z=0;z<16;z++)for(int x=0;x<16;x++){
   int bx=x0+x,bz=z0+z;if(!MistRegion.frost(world.getSeed(),bx,bz))continue;
   int y=chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG,x,z);if(y<=chunk.getBottomY()||y>chunk.getTopYInclusive())continue;
   pos.set(bx,y,bz);var below=chunk.getBlockState(pos.down());
   if(below.isOf(Blocks.SNOW)||below.isOf(Blocks.SNOW_BLOCK))continue;
   var snow=Blocks.SNOW.getDefaultState();if(chunk.getBlockState(pos).isAir()&&snow.canPlaceAt(world,pos))chunk.setBlockState(pos,snow,0);
  }
 }
}
