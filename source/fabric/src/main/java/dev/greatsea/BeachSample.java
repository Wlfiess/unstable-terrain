package dev.greatsea;
import java.io.*;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
/** Measured block heights from the supplied Great Sea Beach Sample, without extra noise. */
final class BeachSample {
    static final int WIDTH=294,LENGTH=1025,WATER=139;
    private static final short[] HEIGHTS=load();
    private static short[] load() {
        try(var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(BeachSample.class.getResourceAsStream("/assets/greatsea/beach-sample.bin.gz"))))) {
            if(in.readInt()!=0x55424831 || in.readInt()!=WIDTH || in.readInt()!=LENGTH)throw new IOException("Invalid beach sample");
            short[] h=new short[WIDTH*LENGTH];for(int i=0;i<h.length;i++)h[i]=in.readShort();return h;
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    private static int acrossIndex(int x){int p=Math.floorMod(x,2*WIDTH);return p<WIDTH?p:2*WIDTH-1-p;}
    static int measured(int across,int along){return HEIGHTS[Math.max(0,Math.min(LENGTH-1,along))*WIDTH+acrossIndex(across)];}
    static double height(double across,double along,int seaLevel) {
        int a=(int)Math.floor(across),b=(int)Math.floor(along);double u=across-a,v=along-b;
        double h=SeaTerrain.lerp(SeaTerrain.lerp(measured(a,b),measured(a+1,b),u),SeaTerrain.lerp(measured(a,b+1),measured(a+1,b+1),u),v);
        return h+seaLevel-WATER;
    }
}
