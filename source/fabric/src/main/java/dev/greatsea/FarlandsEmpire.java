package dev.greatsea;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

/** One exact empire landmark on the east Repeating route, away from the zero-axis route. */
public final class FarlandsEmpire {
    static final EmpireSchematic SAMPLE=EmpireSchematic.load();
    static final int BASE_Y=-132;
    static final int SOURCE_BOTTOM=68;
    static final int NEAREST_TANGENT=-500;
    static int origin(){return FarlandsTerrain.start(FarlandsTerrain.Stage.REPEATING)+Math.max(0,(GreatSea.FARLANDS.stageWidths[FarlandsTerrain.Stage.REPEATING.ordinal()]-SAMPLE.width)/2);}
    static int tangentOrigin(){return NEAREST_TANGENT-SAMPLE.depth+1;}
    static int a(int x,int z){return FarlandsWorld.project(x,z).depth()-origin();}
    static int b(int x,int z){return FarlandsWorld.project(x,z).tangent()-tangentOrigin();}
    public static boolean footprint(int x,int z){
        if(!StructurePolicy.enabled())return false;
        if(FarlandsOrbitals.face(x,z)!=0||!FarlandsWorld.contains(x,z)||FarlandsTerrain.stage(x,z)!=FarlandsTerrain.Stage.REPEATING)return false;
        var point=FarlandsWorld.project(x,z);
        return point.depth()>=origin()&&point.depth()<origin()+SAMPLE.width&&point.tangent()>=tangentOrigin()&&point.tangent()<=NEAREST_TANGENT;
    }
    public static boolean contains(int x,int y,int z){return y>=-64&&y<320&&footprint(x,z);}
    public static boolean backdrop(int x,int z){
        if(!StructurePolicy.enabled())return false;
        if(FarlandsOrbitals.face(x,z)!=0||!FarlandsWorld.contains(x,z)||FarlandsTerrain.stage(x,z)!=FarlandsTerrain.Stage.REPEATING)return false;
        var p=FarlandsWorld.project(x,z);return p.depth()>=origin()&&p.depth()<origin()+SAMPLE.width&&p.tangent()>NEAREST_TANGENT&&p.tangent()<=NEAREST_TANGENT+32;
    }
    static int material(int x,int y,int z){return SAMPLE.material(a(x,z),y-BASE_Y,b(x,z));}
    static BlockState state(int x,int y,int z){
        var state=FarlandsOrbitals.orient(SAMPLE.state(a(x,z),y-BASE_Y,b(x,z)),FarlandsOrbitals.face(x,z));
        if(state.isOf(Blocks.ANCIENT_DEBRIS)||ForbiddenWorldBlocks.ominousVault(state))return Blocks.AIR.getDefaultState();
        return state;
    }
    static BlockPos position(int face,int a,int y,int b){
        int outward=GreatSea.FARLANDS.entryBoundary+origin()+a,tangent=tangentOrigin()+b;
        return switch(face){case 1->new BlockPos(-outward,y,tangent);case 2->new BlockPos(tangent,y,outward);case 3->new BlockPos(tangent,y,-outward);default->new BlockPos(outward,y,tangent);};
    }
    static void populate(Chunk chunk){
        if(!StructurePolicy.enabled())return;
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();
        int firstA=Math.min(a(sx,sz),a(sx+15,sz+15)),lastA=Math.max(a(sx,sz),a(sx+15,sz+15));
        int firstB=Math.min(b(sx,sz),b(sx+15,sz+15)),lastB=Math.max(b(sx,sz),b(sx+15,sz+15));
        if(lastA<0||firstA>=SAMPLE.width||lastB<0||firstB>=SAMPLE.depth)return;
        int face=FarlandsOrbitals.face(sx,sz);
        for(int cz=Math.max(0,firstB)>>4;cz<=Math.min(SAMPLE.depth-1,lastB)>>4;cz++)
            for(int cx=Math.max(0,firstA)>>4;cx<=Math.min(SAMPLE.width-1,lastA)>>4;cx++)
                for(NbtCompound source:SAMPLE.tiles(cx,cz)){
                    var pos=position(face,source.getInt("x",0),BASE_Y+source.getInt("y",0),source.getInt("z",0));
                    if(pos.getX()<sx||pos.getX()>=sx+16||pos.getZ()<sz||pos.getZ()>=sz+16||!contains(pos.getX(),pos.getY(),pos.getZ()))continue;
                    var state=state(pos.getX(),pos.getY(),pos.getZ());if(state.isAir())continue;
                    var nbt=source.copy();nbt.putInt("x",pos.getX());nbt.putInt("y",pos.getY());nbt.putInt("z",pos.getZ());
                    chunk.addPendingBlockEntityNbt(nbt);
                }
    }
}
