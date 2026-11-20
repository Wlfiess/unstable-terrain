package dev.greatsea;

import com.google.gson.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Unscaled original trees exclusively from the uploaded Horace tree pack. */
final class SpokelandsRainbowTreeAssets {
    record Voxel(int x,int y,int z,BlockState state){}
    interface Sink {void put(int x,int y,int z,BlockState state);}
    private static final List<List<Voxel>[]> TREES=load();
    private static final Set<Block> BLOCKS=blocks();
    private static Set<Block> blocks(){var result=new HashSet<Block>();for(var rotations:TREES)for(var voxel:rotations[0])result.add(voxel.state().getBlock());return Set.copyOf(result);}
    static boolean uses(BlockState state){return BLOCKS.contains(state.getBlock());}
    static int count(){return TREES.size();}
    static int index(long hash){return (int)Long.remainderUnsigned(SeaTerrain.hash(hash,3401,71),TREES.size());}
    static void place(long hash,int x,int y,int z,Sink sink){
        int rotation=(int)(SeaTerrain.hash(hash,3402,71)&3);
        for(var v:TREES.get(index(hash))[rotation])sink.put(x+v.x,y+v.y,z+v.z,v.state);
    }
    @SuppressWarnings("unchecked") private static List<List<Voxel>[]> load(){
        try(var input=new InputStreamReader(new GZIPInputStream(Objects.requireNonNull(SpokelandsRainbowTreeAssets.class.getResourceAsStream("/assets/unstableterrain/spokelands/rainbow-trees.json.gz"))),StandardCharsets.UTF_8)){
            var root=JsonParser.parseReader(input).getAsJsonObject();var palette=new ArrayList<BlockState>();
            for(var entry:root.getAsJsonArray("palette")){
                var definition=entry.getAsJsonObject();var state=Registries.BLOCK.get(Identifier.of(definition.get("Name").getAsString())).getDefaultState();
                if(definition.has("Properties"))for(var property:definition.getAsJsonObject("Properties").entrySet())state=property(state,property.getKey(),property.getValue().getAsString());
                // Decorative source leaves must survive independently of vanilla log-distance rules.
                if(state.contains(net.minecraft.state.property.Properties.PERSISTENT))state=state.with(net.minecraft.state.property.Properties.PERSISTENT,true);
                palette.add(state);
            }
            var trees=new ArrayList<List<Voxel>[]>();
            for(var element:root.getAsJsonArray("trees")){
                List<Voxel>[] rotations=new List[4];for(int i=0;i<4;i++)rotations[i]=new ArrayList<>();
                for(var entry:element.getAsJsonObject().getAsJsonArray("blocks")){
                    var a=entry.getAsJsonArray();int x=a.get(0).getAsInt(),y=a.get(1).getAsInt(),z=a.get(2).getAsInt();var state=palette.get(a.get(3).getAsInt());
                    for(int i=0;i<4;i++){
                        int rx=i==0?x:i==1?-z:i==2?-x:z,rz=i==0?z:i==1?x:i==2?-z:-x;
                        rotations[i].add(new Voxel(rx,y,rz,state.rotate(BlockRotation.values()[i])));
                    }
                }
                for(int i=0;i<4;i++)rotations[i]=List.copyOf(rotations[i]);trees.add(rotations);
            }
            if(trees.size()!=20)throw new IllegalArgumentException("Expected twenty original Horace trees");
            return List.copyOf(trees);
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    private static <T extends Comparable<T>> BlockState property(BlockState state,String name,String value){
        @SuppressWarnings("unchecked") var property=(Property<T>)state.getBlock().getStateManager().getProperty(name);
        if(property==null)throw new IllegalArgumentException("Unknown source property "+name);
        return state.with(property,property.parse(value).orElseThrow());
    }
}
