package dev.greatsea;

import java.io.DataInputStream;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/** 1,024 complete 16-square height profiles measured in the supplied chunk mosaic. */
final class RandomChunkReference {
    private static final short[] HEIGHTS=load();
    private static short[] load() {
        try(var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(RandomChunkReference.class.getResourceAsStream("/assets/greatsea/random-chunk-heights.bin.gz"))))) {
            if(in.readInt()!=0x55524331 || in.readInt()!=1024)throw new IllegalStateException("Invalid random chunk heights");
            short[] h=new short[1024*256];for(int i=0;i<h.length;i++)h[i]=in.readShort();return h;
        }catch(java.io.IOException e){throw new ExceptionInInitializerError(e);}
    }
    static int height(long seed,int x,int z) {
        long h=SeaTerrain.hash(seed+856,Math.floorDiv(x,16),Math.floorDiv(z,16));
        int profile=(int)(h&1023),lx=Math.floorMod(x,16),lz=Math.floorMod(z,16);
        if((h&1024)!=0)lx=15-lx;
        if((h&2048)!=0){int swap=lx;lx=lz;lz=swap;}
        return HEIGHTS[profile*256+lz*16+lx];
    }
}
