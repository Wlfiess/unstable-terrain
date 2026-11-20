package dev.greatsea;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
/** Native bridge terrain sampled from the supplied 2026-11-17 save, at original Y levels. */
final class SpokelandsBridges {
 private SpokelandsBridges(){}
 static final int SOURCE_X=-256,SOURCE_Z=12580480;
 private static final class Reference {
  final int width,depth,bottom,height;final int[] offsets;final byte[] runs;
  Reference(){try(var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(SpokelandsBridges.class.getResourceAsStream("/assets/unstableterrain/samples/bridges.volume.gz"))))){
   if(in.readInt()!=0x55425231)throw new IOException("Bad bridge reference");width=in.readInt();depth=in.readInt();bottom=in.readInt();height=in.readInt();
   if(width!=1024||depth!=1024||bottom!=-64||height!=192)throw new IOException("Unexpected bridge sample dimensions");
   offsets=new int[width*depth+1];var bytes=new ByteArrayOutputStream();
   for(int i=0;i<width*depth;i++){offsets[i]=bytes.size();int n=in.readUnsignedByte(),last=0;for(int j=0;j<n;j++){int end=in.readUnsignedByte(),material=in.readUnsignedByte();if(end<=last||end>height||!(material==0||material==1||material==60||material>=68&&material<=82))throw new IOException("Bad bridge run");bytes.write(end);bytes.write(material);last=end;}if(last!=height)throw new IOException("Incomplete bridge column");}
   offsets[width*depth]=bytes.size();runs=bytes.toByteArray();
  }catch(IOException e){throw new ExceptionInInitializerError(e);}}
 }
 private static final class Holder{static final Reference DATA=new Reference();}
 /** Reflect complete edge columns so a sampled crossing continues across each tile join. */
 static int fold(int value,int size){int q=Math.floorMod(value,2*size);return q<size?q:2*size-1-q;}
 static int sourceX(int tangent){return SOURCE_X+fold(tangent-SOURCE_X,1024);}
 static int sourceZ(int depth){return SOURCE_Z+fold(depth-SpokelandsTerrain.start(SpokelandsTerrain.Region.BRIDGES),1024);}
 static int fill(byte[] blocks,byte[] directions,long seed,double a,double b,boolean west){
  int top=fillTerrain(blocks,directions,seed,a,b,west);return Math.max(top,BridgeVillages.fill(blocks,(int)Math.floor(a),(int)Math.floor(b)));
 }
 static int fillTerrain(byte[] blocks,byte[] directions,long seed,double a,double b,boolean west){
  Arrays.fill(blocks,(byte)0);Arrays.fill(directions,(byte)0);var ref=Holder.DATA;
  int x=fold((int)Math.floor(b)-SOURCE_X,ref.width),z=fold((int)Math.floor(a)-SpokelandsTerrain.start(SpokelandsTerrain.Region.BRIDGES),ref.depth),column=z*ref.width+x,previous=0,top=-65;
  for(int i=ref.offsets[column];i<ref.offsets[column+1];i+=2){int end=ref.runs[i]&255;byte material=ref.runs[i+1];Arrays.fill(blocks,previous,end,material);if(material!=0)top=ref.bottom+end-1;previous=end;}
  return top;
 }
}
