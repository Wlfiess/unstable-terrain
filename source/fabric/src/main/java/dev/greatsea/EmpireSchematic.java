package dev.greatsea;

import com.google.gson.JsonParser;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Exact bounded schematic, streamed in horizontal chunks rather than one huge array. */
final class EmpireSchematic {
    private static final String ROOT="/assets/unstableterrain/farlands/empire/";
    final int width,height,depth;
    final long nonAirBlocks;
    final int blockEntities;
    private final String[] descriptions;
    private volatile BlockState[] states;
    private final Map<Long,char[]> chunks=new LinkedHashMap<>(64,.75f,true);
    private final ThreadLocal<Local> last=ThreadLocal.withInitial(Local::new);
    private static final class Local {int x=-1,z=-1;char[] cells;}
    private EmpireSchematic(int width,int height,int depth,long blocks,int tiles,String[] descriptions){
        this.width=width;this.height=height;this.depth=depth;this.nonAirBlocks=blocks;this.blockEntities=tiles;this.descriptions=descriptions;
    }
    static EmpireSchematic load(){
        try(var reader=new InputStreamReader(Objects.requireNonNull(EmpireSchematic.class.getResourceAsStream(ROOT+"manifest.json")),StandardCharsets.UTF_8)){
            var json=JsonParser.parseReader(reader).getAsJsonObject();var palette=json.getAsJsonArray("palette");
            var descriptions=new String[palette.size()];for(int i=0;i<descriptions.length;i++)descriptions[i]=palette.get(i).toString();
            int width=json.get("width").getAsInt(),height=json.get("height").getAsInt(),depth=json.get("depth").getAsInt();
            if(width<1||depth<1||height<1||height>384||descriptions.length<1||descriptions.length>65535)throw new IOException("Invalid empire bounds/palette");
            return new EmpireSchematic(width,height,depth,json.get("nonAirBlocks").getAsLong(),json.get("blockEntities").getAsInt(),descriptions);
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    boolean contains(int x,int y,int z){return x>=0&&x<width&&y>=0&&y<height&&z>=0&&z<depth;}
    int index(int x,int y,int z){
        if(!contains(x,y,z))return 0;
        int cx=x>>4,cz=z>>4;var local=last.get();
        if(local.x!=cx||local.z!=cz||local.cells==null){local.cells=chunk(cx,cz);local.x=cx;local.z=cz;}
        return local.cells[(y*16+(z&15))*16+(x&15)];
    }
    private synchronized char[] chunk(int x,int z){
        long key=((long)x<<32)|(z&0xffffffffL);var cells=chunks.get(key);if(cells!=null)return cells;
        try(var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(EmpireSchematic.class.getResourceAsStream(ROOT+"blocks-"+x+"-"+z+".bin.gz"))))){
            if(in.readInt()!=0x55454331||in.readInt()!=height)throw new IOException("Invalid empire chunk");
            cells=new char[height*256];
            for(int i=0;i<cells.length;i++){cells[i]=in.readChar();if(cells[i]>=descriptions.length)throw new IOException("Invalid empire state index");}
            chunks.put(key,cells);if(chunks.size()>64)chunks.remove(chunks.keySet().iterator().next());return cells;
        }catch(IOException e){throw new IllegalStateException("Cannot load empire chunk "+x+","+z,e);}
    }
    int material(int x,int y,int z){return index(x,y,z)==0?0:47;}
    long countBlocks(){
        long count=0;
        for(int cz=0;cz<(depth+15)/16;cz++)for(int cx=0;cx<(width+15)/16;cx++){
            char[] cells=chunk(cx,cz);int w=Math.min(16,width-cx*16),d=Math.min(16,depth-cz*16);
            for(int y=0;y<height;y++)for(int z=0;z<d;z++)for(int x=0;x<w;x++)if(cells[(y*16+z)*16+x]!=0)count++;
        }
        return count;
    }
    BlockState state(int x,int y,int z){return states()[index(x,y,z)];}
    private BlockState[] states(){
        if(states!=null)return states;
        synchronized(this){
            if(states!=null)return states;var result=new BlockState[descriptions.length];
            for(int i=0;i<result.length;i++){
                var json=JsonParser.parseString(descriptions[i]).getAsJsonObject();var nbt=new NbtCompound();nbt.putString("Name",json.get("Name").getAsString());
                if(json.has("Properties")){var props=new NbtCompound();for(var property:json.getAsJsonObject("Properties").entrySet())props.putString(property.getKey(),property.getValue().getAsString());nbt.put("Properties",props);}
                result[i]=NbtHelper.toBlockState(Registries.BLOCK,nbt);
                String name=json.get("Name").getAsString();
                if(result[i].isAir()&&!name.equals("minecraft:air")&&!name.equals("minecraft:cave_air")&&!name.equals("minecraft:void_air"))throw new IllegalStateException("Unknown empire block: "+descriptions[i]);
            }
            states=result;return result;
        }
    }
    List<NbtCompound> tiles(int x,int z){
        var input=EmpireSchematic.class.getResourceAsStream(ROOT+"tiles-"+x+"-"+z+".snbt.gz");if(input==null)return List.of();
        try(var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(input),StandardCharsets.UTF_8))){
            var result=new ArrayList<NbtCompound>();for(String line;(line=reader.readLine())!=null;)result.add(StringNbtReader.readCompound(line));return result;
        }catch(Exception e){throw new IllegalStateException("Cannot read empire block entities",e);}
    }
}
