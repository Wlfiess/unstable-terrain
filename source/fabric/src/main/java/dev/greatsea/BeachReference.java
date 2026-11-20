package dev.greatsea;

import java.io.DataInputStream;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/** Filled and filtered surface measurements; no source caves, fluids or seabed. */
final class BeachReference {
    private static final int WIDTH=101,DEPTH=256;
    private static final float[] HEIGHTS=load();
    private static float[] load() {
        try(var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(BeachReference.class.getResourceAsStream("/assets/greatsea/beach-reference-height.bin.gz"))))) {
            if(in.readInt()!=0x55424832 || in.readInt()!=WIDTH || in.readInt()!=DEPTH || in.readInt()!=4)throw new IllegalStateException("Invalid beach height field");
            float[] h=new float[WIDTH*DEPTH];for(int i=0;i<h.length;i++)h[i]=in.readShort()/100f;return h;
        }catch(java.io.IOException e){throw new ExceptionInInitializerError(e);}
    }
    private static int reflected(int i,int size) {int p=Math.floorMod(i,2*(size-1));return p<size?p:2*(size-1)-p;}
    private static double sample(int x,int z){return HEIGHTS[reflected(z,DEPTH)*WIDTH+Math.max(0,Math.min(WIDTH-1,x))];}
    private static double cubic(double a,double b,double c,double d,double t){return b+.5*t*(c-a+t*(2*a-5*b+4*c-d+t*(3*(b-c)+d-a)));}
    private static double row(int x,int z,double t){return cubic(sample(x-1,z),sample(x,z),sample(x+1,z),sample(x+2,z),t);}
    static double measured(double t,double along) {
        double x=Math.max(0,Math.min(1,t))*100,z=along/4;int ix=(int)Math.floor(x),iz=(int)Math.floor(z);
        return cubic(row(ix,iz-1,x-ix),row(ix,iz,x-ix),row(ix,iz+1,x-ix),row(ix,iz+2,x-ix),z-iz);
    }
    static double rise(double t,double along) {
        double start=measured(0,along),end=measured(1,along);
        double shape=Math.max(0,Math.min(1,(measured(t,along)-start)/(end-start)));
        // Keep tangent joins at the mainland and crest while retaining the measured contour.
        double fade=SeaTerrain.smooth(t/.10)*SeaTerrain.smooth((1-t)/.10);
        return SeaTerrain.lerp(SeaTerrain.ramp(t),shape,fade);
    }
}
