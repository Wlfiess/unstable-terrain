package dev.greatsea;
import net.minecraft.block.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureKeys;

public final class MistShelter {
 private MistShelter(){}
 public record Evidence(boolean trial,boolean overhead,int builtRoof,int placedRoof,int walls,boolean flatRoof,boolean flatFloor,int rectangularWalls){}
 public static boolean sheltered(Evidence e){
  if(e.trial())return true;if(!e.overhead())return false;
  if(e.builtRoof()>=5||e.placedRoof()>=3)return true;
  if((e.builtRoof()>=2||e.placedRoof()>0)&&e.walls()>=2)return true;
  return e.flatRoof()&&e.flatFloor()&&e.rectangularWalls()==4;
 }
 public static boolean built(String id){
  return id.endsWith("_planks")||id.endsWith("_stairs")||id.endsWith("_slab")||id.endsWith("_bricks")||id.endsWith("_tiles")||id.endsWith("_glass")||id.endsWith("_concrete")||id.startsWith("polished_")||id.startsWith("smooth_")||id.contains("copper")||(id.contains("prismarine")||id.startsWith("purpur")||id.equals("glass"))||id.equals("bricks")||id.equals("cobblestone")||id.equals("mossy_cobblestone");
 }
 public static boolean trial(ServerWorld world,BlockPos pos){
  return world.getStructureAccessor().getStructureContaining(pos,e->e.matchesKey(StructureKeys.TRIAL_CHAMBERS)).hasChildren();
 }
 private static boolean solid(ServerWorld world,BlockPos p){
  if(!world.isChunkLoaded(p.getX()>>4,p.getZ()>>4))return false;
  var s=world.getBlockState(p);return !s.isIn(BlockTags.LEAVES)&&!s.isOf(Blocks.SNOW)&&!s.isAir()&&!s.getCollisionShape(world,p).isEmpty();
 }
 private static int wall(ServerWorld world,BlockPos p,int dx,int dz,int offset){
  var q=new BlockPos.Mutable();
  for(int d=1;d<=32;d++){
   q.set(p.getX()+dx*d+(dz!=0?offset:0),p.getY()+1,p.getZ()+dz*d+(dx!=0?offset:0));
   if(solid(world,q)&&solid(world,q.down()))return d;
  }return -1;
 }
 public static Evidence evidence(ServerWorld world,BlockPos pos){
  if(trial(world,pos))return new Evidence(true,true,0,0,0,false,false,0);
  
  if(world.getStructureAccessor().getStructureContaining(pos,e->e.matchesKey(StructureKeys.IGLOO)).hasChildren())return new Evidence(true,true,0,0,0,false,false,0);
  int roofs=0,built=0,placed=0,roofY=Integer.MIN_VALUE;boolean flatRoof=true;var q=new BlockPos.Mutable();var marked=MistShelterData.get(world);
  for(int dz=-2;dz<=2;dz+=2)for(int dx=-2;dx<=2;dx+=2){
   int found=Integer.MIN_VALUE;
   for(int dy=1;dy<=128&&pos.getY()+dy<=world.getTopYInclusive();dy++){
    q.set(pos.getX()+dx,pos.getY()+dy,pos.getZ()+dz);if(!solid(world,q))continue;
    found=q.getY();roofs++;if(built(Registries.BLOCK.getId(world.getBlockState(q).getBlock()).getPath()))built++;
    if(marked.contains(q))placed++;break;
   }
   if(roofY==Integer.MIN_VALUE)roofY=found;else if(roofY!=found)flatRoof=false;
  }
  flatRoof&=roofs==9;
  int walls=0,rectangular=0;
  for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
   int a=wall(world,pos,d[0],d[1],0);if(a>0){walls++;if(a<=16&&wall(world,pos,d[0],d[1],-1)==a&&wall(world,pos,d[0],d[1],1)==a)rectangular++;}
  }
  boolean flatFloor=true;int floor=Integer.MIN_VALUE;
  for(int[] d:new int[][]{{0,0},{1,0},{-1,0},{0,1},{0,-1}}){
   int found=Integer.MIN_VALUE;for(int dy=0;dy<=3;dy++){q.set(pos.getX()+d[0],pos.getY()-dy,pos.getZ()+d[1]);if(solid(world,q)){found=q.getY();break;}}
   if(found==Integer.MIN_VALUE)flatFloor=false;if(floor==Integer.MIN_VALUE)floor=found;else if(floor!=found)flatFloor=false;
  }
  return new Evidence(false,roofs>=5,built,placed,walls,flatRoof,flatFloor,rectangular);
 }
 public static boolean inside(ServerWorld world,BlockPos pos){return sheltered(evidence(world,pos));}
}
