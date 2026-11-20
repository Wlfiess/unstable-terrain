package dev.greatsea;

import com.google.gson.JsonParser;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/** A complete natural reference chunk, with builds and block entities excluded. */
final class FarlandsChunkTemplate {
    final int width,depth;
    final int bottom,height;
    private final byte[] categories;
    private final String[] descriptions;
    private final char[] cells;
    private volatile BlockState[] states;
    final int medianGround,biome;
    private final int[] terrainGround;
    private FarlandsChunkTemplate(int width,int depth,int bottom,int height,byte[] categories,String[] descriptions,char[] cells) {
        this.width=width;this.depth=depth;this.bottom=bottom;this.height=height;this.categories=categories;this.descriptions=descriptions;this.cells=cells;
        int[] ground=new int[width*depth],counts=new int[categories.length];
        for(char cell:cells)counts[cell]++;
        for(int z=0;z<depth;z++)for(int x=0;x<width;x++) {
            ground[z*width+x]=bottom;
            for(int y=bottom+height-1;y>=bottom;y--) {
                int m=material(x,y,z);
                if(m!=0 && m!=8 && m!=29 && m!=14 && m!=15 && m!=27 && m!=28){ground[z*width+x]=y;break;}
            }
        }
        terrainGround=ground.clone();
        java.util.Arrays.sort(ground);medianGround=ground[ground.length/2];
        int score=0,chosen=0;
        String[] markers={"grass_block","oak_log","birch_log","jungle_log","snow","spruce_log","dark_oak_log","poppy"};
        for(int i=1;i<markers.length;i++) {
            int n=0;for(int j=0;j<descriptions.length;j++)if(descriptions[j].contains("\"minecraft:"+markers[i]+"\""))n+=counts[j];
            if(i==4)n/=8;if(i==7)n*=4;
            if(n>score){score=n;chosen=i;}
        }
        biome=chosen;
    }
    private int index(int x,int y,int z) {
        return y<bottom || y>=bottom+height ? 0:cells[((y-bottom)*depth+Math.floorMod(z,depth))*width+Math.floorMod(x,width)];
    }
    int ground(int x,int z){return terrainGround[Math.floorMod(z,depth)*width+Math.floorMod(x,width)];}
    int material(int x,int y,int z) {return categories[index(x,y,z)]&255;}
    BlockState state(int x,int y,int z) {return states()[index(x,y,z)];}
    // Delay Minecraft registry access so pure terrain tests need no game bootstrap.
    private BlockState[] states() {
        if(states!=null)return states;
        synchronized(this) {
            if(states!=null)return states;
            BlockState[] result=new BlockState[descriptions.length];
            for(int i=0;i<result.length;i++) {
                var json=JsonParser.parseString(descriptions[i]).getAsJsonObject();
                NbtCompound nbt=new NbtCompound();nbt.putString("Name",json.get("Name").getAsString());
                if(json.has("Properties")) {
                    NbtCompound props=new NbtCompound();
                    for(var entry:json.getAsJsonObject("Properties").entrySet())props.putString(entry.getKey(),entry.getValue().getAsString());
                    nbt.put("Properties",props);
                }
                result[i]=NbtHelper.toBlockState(Registries.BLOCK,nbt);
            }
            states=result;return result;
        }
    }
    static FarlandsChunkTemplate load(String name) {
        try(DataInputStream in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(
                FarlandsChunkTemplate.class.getResourceAsStream("/assets/unstableterrain/farlands/"+name+".bin.gz"))))) {
            if(in.readInt()!=0x55465232)throw new IOException("Bad native Farlands volume");
            int width=in.readInt(),depth=in.readInt();
            int bottom=in.readInt(),height=in.readInt(),count=in.readInt();
            if(height<1 || height>384 || width<1 || depth<1 || (long)width*depth*height>64_000_000 || count<1 || count>65535)throw new IOException("Invalid native volume palette");
            byte[] categories=new byte[count];String[] descriptions=new String[count];
            for(int i=0;i<count;i++) {
                categories[i]=in.readByte();int length=in.readInt();
                if(length<1 || length>8192)throw new IOException("Invalid block description");
                descriptions[i]=new String(in.readNBytes(length),StandardCharsets.UTF_8);
            }
            char[] cells=new char[height*width*depth];
            for(int i=0;i<cells.length;i++) {cells[i]=in.readChar();if(cells[i]>=count)throw new IOException("Bad palette index");}
            return new FarlandsChunkTemplate(width,depth,bottom,height,categories,descriptions,cells);
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
}
