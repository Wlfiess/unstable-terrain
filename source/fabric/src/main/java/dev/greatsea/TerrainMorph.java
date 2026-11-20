package dev.greatsea;

/** Vertical signed distances join volumes, including suspended slabs and galleries.
 * Interpolating material IDs or picking one complete column creates sliced cliffs. */
final class TerrainMorph {
    private TerrainMorph(){}
    static void distances(byte[] blocks,double[] out){
        int last=-10000;
        for(int i=0;i<384;i++){
            boolean solid=solid(blocks[i]&255);
            if(i>0&&solid!=solid(blocks[i-1]&255))last=i;
            out[i]=i-last+.5;
        }
        last=10000;
        for(int i=383;i>=0;i--){
            boolean solid=solid(blocks[i]&255);
            if(i<383&&solid!=solid(blocks[i+1]&255))last=i;
            double distance=Math.min(out[i],last-i+.5);
            out[i]=(solid?1:-1)*Math.min(384,distance);
        }
    }
    static boolean solid(int m){return m!=0&&m!=8&&m!=27&&m!=28&&m!=30&&m!=31;}
    static int nearestSolid(byte[] blocks,int at){
        for(int d=0;d<384;d++){
            if(at-d>=0&&solid(blocks[at-d]&255))return at-d;
            if(at+d<384&&solid(blocks[at+d]&255))return at+d;
        }
        return -1;
    }
    static double weight(int depth,int boundary,int width){
        double t=Math.max(0,Math.min(1,(depth-boundary+width)/(2.0*width)));
        return t*t*t*(t*(t*6-15)+10);
    }
    static void blend(byte[] left,byte[] right,double[] ld,double[] rd,double t,boolean rightPalette,byte[] result){
        distances(left,ld);distances(right,rd);
        for(int i=0;i<384;i++){
            if(SeaTerrain.lerp(ld[i],rd[i],t)<=0){result[i]=(byte)((left[i]==8&&right[i]==8)?8:0);continue;}
            byte[] preferred=rightPalette?right:left,other=rightPalette?left:right;
            // Closing a cavity inserts geological fill. Copying the nearest
            // surface would extrude grass, stairs or ore through the filled gap.
            result[i]=solid(preferred[i]&255)?preferred[i]:(byte)(i<64?2:1);
            if(!solid(result[i]&255)&&solid(other[i]&255))result[i]=other[i];
        }
    }
}
