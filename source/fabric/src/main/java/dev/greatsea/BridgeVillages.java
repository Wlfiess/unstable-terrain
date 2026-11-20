package dev.greatsea;
import com.google.gson.JsonParser;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
/** Original bridge-village blocks, kept separate from the landscape for no-structures editions. */
final class BridgeVillages {
 private BridgeVillages(){}
 private static final class Reference {
  final int[] offsets=new int[1024*1024+1];final byte[] runs;final String[] descriptions;final boolean[] beds;
  volatile BlockState[][] transformed;
  Reference(){try(var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(BridgeVillages.class.getResourceAsStream("/assets/unstableterrain/samples/bridge-villages.volume.gz"))))){
   if(in.readInt()!=0x55564c31||in.readInt()!=1024||in.readInt()!=1024||in.readInt()!=-64||in.readInt()!=64)throw new IOException("Bad bridge village dimensions");
   int count=in.readInt();if(count<1||count>65535)throw new IOException("Bad village palette");descriptions=new String[count];beds=new boolean[count];
   for(int i=0;i<count;i++){int n=in.readInt();if(n<1||n>8192)throw new IOException("Bad village state");descriptions[i]=new String(in.readNBytes(n),StandardCharsets.UTF_8);beds[i]=JsonParser.parseString(descriptions[i]).getAsJsonObject().get("Name").getAsString().endsWith("_bed");}
   var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
   for(int c=0;c<1024*1024;c++){offsets[c]=bytes.size();int n=in.readUnsignedByte(),previous=0;for(int j=0;j<n;j++){int end=in.readUnsignedShort(),id=in.readUnsignedShort();if(end<=previous||end>64||id>=count)throw new IOException("Bad village run");out.writeShort(end);out.writeShort(id);previous=end;}if(previous!=64)throw new IOException("Incomplete village column");}
   offsets[1024*1024]=bytes.size();runs=bytes.toByteArray();
  }catch(IOException e){throw new ExceptionInInitializerError(e);}}
  BlockState[][] states(){if(transformed!=null)return transformed;synchronized(this){if(transformed!=null)return transformed;
   BlockState[][] result=new BlockState[8][descriptions.length];
   for(int i=0;i<descriptions.length;i++){var json=JsonParser.parseString(descriptions[i]).getAsJsonObject();var nbt=new NbtCompound();nbt.putString("Name",json.get("Name").getAsString());if(json.has("Properties")){var props=new NbtCompound();for(var e:json.getAsJsonObject("Properties").entrySet())props.putString(e.getKey(),e.getValue().getAsString());nbt.put("Properties",props);}var raw=NbtHelper.toBlockState(Registries.BLOCK,nbt);
    for(int v=0;v<8;v++){var s=raw;if((v&1)!=0)s=s.mirror(BlockMirror.FRONT_BACK);if((v&2)!=0)s=s.mirror(BlockMirror.LEFT_RIGHT);result[v][i]=(v&4)!=0?s.rotate(BlockRotation.CLOCKWISE_90):s.mirror(BlockMirror.LEFT_RIGHT);}
   }transformed=result;return result;
  }}
  int u16(int at){return (runs[at]&255)*256+(runs[at+1]&255);}
 }
 private static final class Holder{static final Reference DATA=new Reference();}
 static int fill(byte[] blocks,int depth,int tangent){var ref=Holder.DATA;int column=column(depth,tangent),previous=0,top=-65;
  for(int i=ref.offsets[column];i<ref.offsets[column+1];i+=4){int end=ref.u16(i),id=ref.u16(i+2);if(id!=0){Arrays.fill(blocks,previous,end,(byte)SpokelandsTerrain.BRIDGE_VILLAGE);top=end-65;}previous=end;}return top;
 }
 private static int column(int depth,int tangent){return (SpokelandsBridges.sourceZ(depth)-SpokelandsBridges.SOURCE_Z)*1024+SpokelandsBridges.sourceX(tangent)-SpokelandsBridges.SOURCE_X;}
 static String description(int depth,int tangent,int y){return Holder.DATA.descriptions[index(depth,tangent,y)];}
 private static int index(int depth,int tangent,int y){if(y< -64||y>=0)return 0;var ref=Holder.DATA;int column=column(depth,tangent),q=y+64;for(int i=ref.offsets[column];i<ref.offsets[column+1];i+=4)if(q<ref.u16(i))return ref.u16(i+2);return 0;}
 static BlockState state(int x,int y,int z){var p=FarlandsWorld.project(x,z);int v=0;if(Math.floorMod(p.tangent()-SpokelandsBridges.SOURCE_X,2048)>=1024)v|=1;if(Math.floorMod(p.depth()-SpokelandsTerrain.start(SpokelandsTerrain.Region.BRIDGES),2048)>=1024)v|=2;if(Math.abs((long)x)>=Math.abs((long)z))v|=4;return Holder.DATA.states()[v][index(p.depth(),p.tangent(),y)];}
 /** Bed renderers require their block entities in the generated chunk packet. */
 static void populate(net.minecraft.world.chunk.Chunk chunk){
  int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();
  if(!SpokelandsTerrain.contains(sx+8,sz+8)||SpokelandsTerrain.region(sx+8,sz+8)!=SpokelandsTerrain.Region.BRIDGES)return;
  var ref=Holder.DATA;
  for(int z=sz;z<sz+16;z++)for(int x=sx;x<sx+16;x++){
   if(!SpokelandsTerrain.contains(x,z)||SpokelandsTerrain.region(x,z)!=SpokelandsTerrain.Region.BRIDGES)continue;
   var p=FarlandsWorld.project(x,z);int column=column(p.depth(),p.tangent()),previous=0;
   for(int i=ref.offsets[column];i<ref.offsets[column+1];i+=4){int end=ref.u16(i),id=ref.u16(i+2);if(ref.beds[id])for(int y=previous-64;y<end-64;y++){var n=new NbtCompound();n.putString("id","minecraft:bed");n.putInt("x",x);n.putInt("y",y);n.putInt("z",z);chunk.addPendingBlockEntityNbt(n);}previous=end;}
  }
 }
}
