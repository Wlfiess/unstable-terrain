package dev.greatsea;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
/** Full-height captured block cross-sections, excluding unknown chunks. */
final class PlainsFarlandsProfile {
    private static final int WIDTH,HEIGHT;
    private static final float[] FIELD;
    private static final class Horizontal {long seed;double depth,tangent,value,lift,stretch,shear;boolean valid;}
    private static final ThreadLocal<Horizontal> HORIZONTAL=ThreadLocal.withInitial(Horizontal::new);
    static {
        try(var in=new DataInputStream(new GZIPInputStream(PlainsFarlandsProfile.class.getResourceAsStream("/assets/unstableterrain/farlands/plains-voxel-profile.bin.gz")))) {
            if(in.readInt()!=0x50565831)throw new IOException("Invalid voxel plains profile");
            WIDTH=in.readInt();HEIGHT=in.readInt();if(WIDTH<1000||WIDTH>8192||HEIGHT!=320)throw new IOException("Invalid long plains dimensions");FIELD=new float[WIDTH*HEIGHT*2];
            for(int i=0;i<FIELD.length;i++)FIELD[i]=in.readFloat();
        }catch(IOException | NullPointerException e){throw new ExceptionInInitializerError(e);}
    }
    private static double along(long seed,double depth,double tangent) {
        var cached=HORIZONTAL.get();
        if(cached.valid && cached.seed==seed && cached.depth==depth && cached.tangent==tangent)return cached.value;
        double x=tangent+(SeaTerrain.hash(seed,915,0)&65535)+7*SeaTerrain.noise(seed+9151,1024,tangent,1700)+4*SeaTerrain.noise(seed+9152,0,tangent,650);
        x+=.20*SeaTerrain.noise(seed+9161,1024*.005,tangent,230)+.10*SeaTerrain.noise(seed+9162,1024*.004,tangent,73);
        cached.lift=0;cached.stretch=0;cached.shear=0;
        double period=2*(WIDTH-1);x-=Math.floor(x/period)*period;
        cached.seed=seed;cached.depth=depth;cached.tangent=tangent;cached.value=x<WIDTH-1?x:period-x;cached.valid=true;
        return cached.value;
    }
    static double density(long seed,double depth,double y,double tangent,double fallback) {return value(seed,depth,y,tangent,0);}
    static int material(long seed,double depth,double y,double tangent) {
        return value(seed,depth,y,tangent,0)>0 ? value(seed,depth,y,tangent,1)>.5?3:1:0;
    }
    static int orbitalMaterial(long seed,double depth,double y,double tangent){
        return material(seed,depth,y,tangent);
    }
    private static double value(long seed,double depth,double y,double tangent,int channel) {
        if(y< -64 || y>255)return channel==0?-1:0;
        double x=along(seed,depth,tangent);var horizontal=HORIZONTAL.get();
        double fade=Math.max(0,Math.min(1,Math.min((y+64)/24.0,(255-y)/24.0)));
        double v=Math.max(0,Math.min(HEIGHT-1,y+64+fade*(horizontal.lift+(y-96)*horizontal.stretch)));
        x+=horizontal.shear*Math.sin((y+35)/87.0);
        double period=2*(WIDTH-1);x-=Math.floor(x/period)*period;x=x<WIDTH-1?x:period-x;
        return sampled(x,v,channel);
    }
    private static double sampled(double x,double v,int channel){
        return sampled(FIELD,WIDTH,x,v,channel);
    }
    private static double sampled(float[] field,int width,double x,double v,int channel){
        int ix=(int)x,iy=(int)v,x1=Math.min(width-1,ix+1),y1=Math.min(HEIGHT-1,iy+1);
        return SeaTerrain.lerp(SeaTerrain.lerp(field[(iy*width+ix)*2+channel],field[(iy*width+x1)*2+channel],x-ix),SeaTerrain.lerp(field[(y1*width+ix)*2+channel],field[(y1*width+x1)*2+channel],x-ix),v-iy);
    }
}
