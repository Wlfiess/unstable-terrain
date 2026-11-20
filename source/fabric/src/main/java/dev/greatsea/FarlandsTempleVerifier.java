package dev.greatsea;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.List;

/** Development check of actual temple block entities and terrain contact on every face. */
final class FarlandsTempleVerifier {
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static BlockPos position(int face,int localX,int y,int localZ){
        int outward=GreatSea.FARLANDS.entryBoundary+FarlandsTemple.center()-FarlandsTemple.OFFSET_X+localX;
        int tangent=localZ-FarlandsTemple.SAMPLE.depth/2;
        return switch(face){
            case 1->new BlockPos(-outward,y,tangent);
            case 2->new BlockPos(tangent,y,outward);
            case 3->new BlockPos(tangent,y,-outward);
            default->new BlockPos(outward,y,tangent);
        };
    }
    static void verify(ServerWorld world,List<String> report){
        check(FarlandsTemple.tiles().size()==403,"Temple source block entity count differs");
        int tiles=0,contacts=0,upperContacts=0;
        for(int face=0;face<4;face++){
            for(var source:FarlandsTemple.tiles()){
                var p=position(face,source.getInt("x",0),FarlandsTemple.BASE_Y+source.getInt("y",0),source.getInt("z",0));
                world.getChunk(p.getX()>>4,p.getZ()>>4);
                var n=source.copy();n.putInt("x",p.getX());n.putInt("y",p.getY());n.putInt("z",p.getZ());
                var actual=world.getBlockEntity(p);
                var expected=BlockEntity.createFromNbt(p,world.getBlockState(p),n,world.getRegistryManager());
                check(actual!=null && expected!=null,"Missing imported temple block entity at "+p);
                check(actual.createNbtWithIdentifyingData(world.getRegistryManager()).equals(expected.createNbtWithIdentifyingData(world.getRegistryManager())),"Temple block entity content differs at "+p);
                tiles++;
            }
            int faceContacts=0;
            for(int z=0;z<FarlandsTemple.SAMPLE.depth;z++)for(int x=0;x<FarlandsTemple.SAMPLE.width;x++){
                if(FarlandsTemple.SAMPLE.material(x,19,z)==0 || Math.hypot(x-114,z-79)<76)continue;
                var wall=position(face,x,164,z);
                check(!world.getBlockState(wall).isAir(),"Missing round foundation block at "+wall);
                for(int[] offset:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
                    var p=position(face,x+offset[0],164,z+offset[1]);
                    if(FarlandsTemple.contains(p.getX(),164,p.getZ()) || FarlandsTerrain.bareSplit(p.getX(),p.getZ()))continue;
                    world.getChunk(p.getX()>>4,p.getZ()>>4);
                    check(!world.getBlockState(p).isAir(),"Gap between terrain and foundation at "+p);
                    faceContacts++;
                }
            }
            check(faceContacts>=20,"Cleared moat around temple face "+face+": "+faceContacts);
            int faceUpper=0;
            for(int z=0;z<FarlandsTemple.SAMPLE.depth;z++)for(int x=0;x<FarlandsTemple.SAMPLE.width;x++) {
                if(FarlandsTemple.SAMPLE.material(x,32,z)==0 || Math.hypot(x-114,z-79)<76)continue;
                for(int[] offset:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    var p=position(face,x+offset[0],177,z+offset[1]);
                    if(FarlandsTemple.contains(p.getX(),177,p.getZ()) || FarlandsTerrain.bareSplit(p.getX(),p.getZ()))continue;
                    if(FarlandsTerrain.column(world.getSeed(),p.getX(),p.getZ()).top()!=177)continue;
                    world.getChunk(p.getX()>>4,p.getZ()>>4);
                    check(world.getBlockState(p).isOf(net.minecraft.block.Blocks.GRASS_BLOCK),"Upper terrace misses the temple rim at "+p);
                    faceUpper++;
                }
            }
            check(faceUpper>=10,"Upper terrace fails to touch temple on face "+face);
            upperContacts+=faceUpper;
            contacts+=faceContacts;
            var approach=position(face,-1,144,79);
            world.getChunk(approach.getX()>>4,approach.getZ()>>4);
            check(world.getBlockState(approach).isOf(net.minecraft.block.Blocks.GRASS_BLOCK),"Staircase approach height differs at "+approach);
            check(world.getBlockState(approach.up()).isAir() && world.getBlockState(approach.up(2)).isAir(),"Terrain obstructs staircase approach at "+approach);
        }
        report.add("Updated Lakerek temple: "+tiles+" block entities match saved source contents across all four faces (403 each); "+contacts+" middle-layer foundation contacts; "+upperContacts+" upper grass terrace contacts at Y=177; staircase approaches remain flush at Y=144.");
    }
}
