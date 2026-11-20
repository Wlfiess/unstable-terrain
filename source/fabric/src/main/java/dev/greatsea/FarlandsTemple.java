package dev.greatsea;
import net.minecraft.block.BlockState;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.world.chunk.Chunk;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
/** Exact block-state import of the user-supplied temple, one on the east Farlands route. */
final class FarlandsTemple {
    static final FarlandsChunkTemplate SAMPLE=FarlandsChunkTemplate.load("lakerek-temple");
    static final int BASE_Y=169;
    static int center(){return FarlandsTerrain.end(FarlandsTerrain.Stage.SPLIT)-512;}
    static final int OFFSET_X=114;
    static int a(int x,int z){return FarlandsWorld.project(x,z).depth()-center()+OFFSET_X;}
    static int b(int x,int z){return FarlandsWorld.project(x,z).tangent()+SAMPLE.depth/2;}
    static boolean footprint(int x,int z){int a=a(x,z),b=b(x,z);return StructurePolicy.enabled() && FarlandsOrbitals.face(x,z)==0 && FarlandsWorld.contains(x,z) && FarlandsTerrain.stage(x,z)==FarlandsTerrain.Stage.SPLIT && a>=0 && a<SAMPLE.width && b>=0 && b<SAMPLE.depth;}
    static boolean contains(int x,int y,int z){return footprint(x,z) && y>=BASE_Y && y<BASE_Y+SAMPLE.height && (SAMPLE.material(a(x,z),y-BASE_Y,b(x,z))!=0 || Math.hypot(a(x,z)-114,b(x,z)-79)<=79);}
    static int material(int x,int y,int z){return SAMPLE.material(a(x,z),y-BASE_Y,b(x,z))==0?0:41;}
    static BlockState state(int x,int y,int z){
        var s=SAMPLE.state(a(x,z),y-BASE_Y,b(x,z));
        if(Math.abs((long)x)>=Math.abs((long)z))return x<0?s.mirror(BlockMirror.FRONT_BACK):s;
        // Swap outward and transverse axes, including stairs, doors and directional blocks.
        s=s.mirror(BlockMirror.LEFT_RIGHT).rotate(BlockRotation.CLOCKWISE_90);
        return z<0?s.mirror(BlockMirror.LEFT_RIGHT):s;
    }
    private static final class Data {
        static final List<NbtCompound> TILES=loadTiles();
        static final Map<Integer,List<NbtCompound>> BY_COLUMN=new HashMap<>();
        static {for(var n:TILES)BY_COLUMN.computeIfAbsent(n.getInt("x",0)+SAMPLE.width*n.getInt("z",0),k->new ArrayList<>()).add(n);}
        private static List<NbtCompound> loadTiles(){
            try(var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(Objects.requireNonNull(
                    FarlandsTemple.class.getResourceAsStream("/assets/unstableterrain/farlands/lakerek-temple-tiles.snbt.gz"))),StandardCharsets.UTF_8))){
                var result=new ArrayList<NbtCompound>();
                for(String line;(line=reader.readLine())!=null;)result.add(StringNbtReader.readCompound(line));
                return List.copyOf(result);
            }catch(Exception e){throw new IllegalStateException("Cannot load temple block entities",e);}
        }
    }
    static List<NbtCompound> tiles(){return Data.TILES;}
    static void populate(Chunk chunk){
        if(!StructurePolicy.enabled())return;
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();
        int startA=a(sx,sz),endA=a(sx+15,sz+15),startB=b(sx,sz),endB=b(sx+15,sz+15);
        if(Math.max(startA,endA)<0 || Math.min(startA,endA)>=SAMPLE.width || Math.max(startB,endB)<0 || Math.min(startB,endB)>=SAMPLE.depth)return;
        for(int z=sz;z<sz+16;z++)for(int x=sx;x<sx+16;x++){
            if(!footprint(x,z))continue;
            for(var source:Data.BY_COLUMN.getOrDefault(a(x,z)+SAMPLE.width*b(x,z),List.of())){
                var n=source.copy();n.putInt("x",x);n.putInt("y",BASE_Y+source.getInt("y",0));n.putInt("z",z);
                chunk.addPendingBlockEntityNbt(n);
            }
        }
    }
}
