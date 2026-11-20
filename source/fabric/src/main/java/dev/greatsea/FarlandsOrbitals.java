package dev.greatsea;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ProtoChunk;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Full-size orbital machine suspended in a seed-selected transverse void corridor. */
final class FarlandsOrbitals {
    static final FarlandsChunkTemplate SAMPLE=FarlandsChunkTemplate.load("orbitals");
    static final int BASE_Y=118;
    static final int TANGENT=-5000;
    static final int SILOS=100;
    static final int SPACING=100;
    private static final int FIRST_SECTION=80, FIRST_PLATE=131;
    private static final int LAST_SECTION=FIRST_SECTION+(SILOS-1)*SPACING;
    static final int DEPTH=LAST_SECTION+57;
    static int center(){return FarlandsTerrain.start(FarlandsTerrain.Stage.ORBITALS)+GreatSea.FARLANDS.stageWidths[FarlandsTerrain.Stage.ORBITALS.ordinal()]/2;}
    static int length(long seed){return 160;}
    static int origin(long seed){return center()-SAMPLE.width/2;}
    static boolean gap(long seed,int x,int z){
        if(SpokelandsTerrain.contains(x,z)||!FarlandsWorld.contains(x,z)||FarlandsTerrain.stage(x,z)!=FarlandsTerrain.Stage.ORBITALS)return false;
        int a=FarlandsWorld.project(x,z).depth();return a>=center()-length(seed)/2 && a<center()-length(seed)/2+length(seed);
    }
    static int a(long seed,int x,int z){return FarlandsWorld.project(x,z).depth()-origin(seed);}
    static int tangentOrigin(){return TANGENT-SAMPLE.depth/2;}
    static int b(int x,int z){return FarlandsWorld.project(x,z).tangent()-tangentOrigin();}
    static BlockPos position(long seed,int a,int y,int b){
        return new BlockPos(GreatSea.FARLANDS.entryBoundary+origin(seed)+a,y,tangentOrigin()+b);
    }
    static BlockPos plate(long seed,int silo){
        return position(seed,106,BASE_Y+81,FIRST_PLATE+silo*SPACING);
    }
    static int sourceColumn(int b){
        if(b<970)return b;
        if(b>=LAST_SECTION)return 1690+b-LAST_SECTION;
        return FIRST_SECTION+SPACING+Math.floorMod(b-FIRST_SECTION,SPACING);
    }
    static int section(int b){return Math.floorDiv(b-FIRST_SECTION,SPACING);}
    static int nearestSilo(BlockPos pos){
        return Math.max(0,Math.min(SILOS-1,Math.floorDiv(pos.getZ()-tangentOrigin()-FIRST_PLATE+SPACING/2,SPACING)));
    }
    static BlockPos controlLever(long seed){return position(seed,16,BASE_Y+104,19);}
    static BlockPos controlLanding(long seed){return position(seed,15,BASE_Y+104,19);}
    static boolean contains(long seed,int x,int y,int z){int a=a(seed,x,z),b=b(x,z);return StructurePolicy.enabled()&&face(x,z)==0&&FarlandsWorld.contains(x,z)&&FarlandsTerrain.stage(x,z)==FarlandsTerrain.Stage.ORBITALS&&a>=0&&a<SAMPLE.width&&b>=0&&b<DEPTH&&y>=BASE_Y&&y<BASE_Y+SAMPLE.height;}
    static boolean hasBlock(long seed,int x,int y,int z){return contains(seed,x,y,z)&&SAMPLE.material(a(seed,x,z),y-BASE_Y,sourceColumn(b(x,z)))!=0;}
    static int material(long seed,int x,int y,int z){
        return hasBlock(seed,x,y,z)?42:0;
    }
    static BlockState orient(BlockState s,int face){
        if(face==0)return s;if(face==1)return s.mirror(BlockMirror.FRONT_BACK);
        s=s.mirror(BlockMirror.LEFT_RIGHT).rotate(BlockRotation.CLOCKWISE_90);
        return face==3?s.mirror(BlockMirror.LEFT_RIGHT):s;
    }
    static int face(int x,int z){return Math.abs((long)x)>=Math.abs((long)z)?(x<0?1:0):(z<0?3:2);}
    static BlockState state(long seed,int x,int y,int z){
        return contains(seed,x,y,z)?SAMPLE.state(a(seed,x,z),y-BASE_Y,sourceColumn(b(x,z))):Blocks.AIR.getDefaultState();
    }
    private static List<NbtCompound> load(String name){
        try(var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(Objects.requireNonNull(FarlandsOrbitals.class.getResourceAsStream("/assets/unstableterrain/farlands/orbitals-"+name+".snbt.gz"))),StandardCharsets.UTF_8))){
            var result=new ArrayList<NbtCompound>();for(String line;(line=reader.readLine())!=null;)result.add(StringNbtReader.readCompound(line));return List.copyOf(result);
        }catch(Exception e){throw new IllegalStateException("Cannot load orbital "+name,e);}
    }
    private static final class Data {
        static final List<NbtCompound> TILES=load("tiles"),ENTITIES=load("entities");
        static final Map<Integer,List<NbtCompound>> BY_COLUMN=new HashMap<>();
        static {for(var n:TILES)BY_COLUMN.computeIfAbsent(n.getInt("x",0)+SAMPLE.width*n.getInt("z",0),k->new ArrayList<>()).add(n);}
    }
    static List<NbtCompound> tiles(){return Data.TILES;}
    static List<NbtCompound> entities(){return Data.ENTITIES;}
    static void populate(Chunk chunk,long seed){
        if(!StructurePolicy.enabled())return;
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();
        if(face(sx+8,sz+8)!=0)return;
        int startA=a(seed,sx,sz),endA=a(seed,sx+15,sz+15),startB=b(sx,sz),endB=b(sx+15,sz+15);
        if(Math.max(startA,endA)<0 || Math.min(startA,endA)>=SAMPLE.width || Math.max(startB,endB)<0 || Math.min(startB,endB)>=DEPTH)return;
        for(int z=sz;z<sz+16;z++)for(int x=sx;x<sx+16;x++) {
            int a=a(seed,x,z),b=b(x,z);if(a<0||a>=SAMPLE.width||b<0||b>=DEPTH)continue;
            for(var source:Data.BY_COLUMN.getOrDefault(a+SAMPLE.width*sourceColumn(b),List.of())) {
                var n=source.copy();n.putInt("x",x);n.putInt("y",BASE_Y+source.getInt("y",0));n.putInt("z",z);chunk.addPendingBlockEntityNbt(n);
            }
        }
        if(!(chunk instanceof ProtoChunk proto))return;
        int face=face(sx,sz),base=GreatSea.FARLANDS.entryBoundary+origin(seed);
        for(var source:Data.ENTITIES) {
            var p=source.getListOrEmpty("Pos");if(p.size()!=3)continue;
            double a=p.getDouble(0,0),b=p.getDouble(2,0)+tangentOrigin();
            double outward=face==1||face==3?1-base-a:base+a;
            double x=face<2?outward:b,z=face<2?b:outward;
            if(Math.floor(x)<sx||Math.floor(x)>=sx+16||Math.floor(z)<sz||Math.floor(z)>=sz+16)continue;
            double y=BASE_Y+p.getDouble(1,0);
            var n=source.copy();n.remove("UUID");n.put("Pos",entityPosition(x,y,z));
            if(n.getString("id","").equals("minecraft:iron_golem"))markControlMob(n,OrbitalRuntime.GOLEM_TAG);
            for(var passenger:n.getListOrEmpty("Passengers"))
                if(passenger instanceof NbtCompound mob&&mob.getString("id","").equals("minecraft:breeze")){
                    markControlMob(mob,OrbitalRuntime.BREEZE_TAG);
                    mob.put("Pos",entityPosition(x,y+.1875,z));
                }
            var motion=source.getListOrEmpty("Motion");if(motion.size()==3){double mx=motion.getDouble(0,0),mz=motion.getDouble(2,0);if(face==1||face==3)mx=-mx;var m=new NbtList();m.add(NbtDouble.of(face<2?mx:mz));m.add(NbtDouble.of(motion.getDouble(1,0)));m.add(NbtDouble.of(face<2?mz:mx));n.put("Motion",m);}
            var rotation=source.getListOrEmpty("Rotation");if(rotation.size()==2){float yaw=rotation.getFloat(0,0);yaw=switch(face){case 1->-yaw;case 2->-90-yaw;case 3->90+yaw;default->yaw;};var r=new NbtList();r.add(NbtFloat.of(yaw));r.add(NbtFloat.of(rotation.getFloat(1,0)));n.put("Rotation",r);}
            proto.addEntity(n);
        }
    }
    private static NbtList entityPosition(double x,double y,double z){
        var position=new NbtList();
        position.add(NbtDouble.of(x));
        position.add(NbtDouble.of(y));
        position.add(NbtDouble.of(z));
        return position;
    }
    private static void markControlMob(NbtCompound mob,String tag){
        var tags=mob.getListOrEmpty("Tags");
        tags.add(NbtString.of(tag));
        mob.put("Tags",tags);
        mob.putBoolean("PersistenceRequired",true);
    }
}
