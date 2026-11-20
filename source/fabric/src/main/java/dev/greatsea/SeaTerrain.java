package dev.greatsea;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;

/** A single, continuous height field covers both shores and the sea floor. */
public final class SeaTerrain {
    public static final int FLOOR_TRANSITION_WIDTH = 128;
    private static final int TILE_SIZE = 128;
    private static final float[] DUNES = loadDunes();
    private SeaTerrain() {}
    public static long radius(int x, int z) { return Math.max(Math.abs((long)x), Math.abs((long)z)); }
    /** Only a sixteen-block tangent blend softens the otherwise square right-angle turn. */
    public static double cornerRadius(SeaConfig c) { return 16; }
    private static double boundaryDistance(SeaConfig c,double boundary,double x,double z) {
        double ax=Math.abs(x),az=Math.abs(z),rounding=cornerRadius(c);
        double corner=Math.max(0,rounding-Math.abs(ax-az));
        // A smooth maximum keeps the same small turn radius on every coast contour.
        // Euclidean offsets made a 550-block beach round out into a huge quarter-circle.
        return Math.max(ax,az)+corner*corner/(4*rounding)-boundary;
    }
    public static boolean contains(SeaConfig c, int x, int z) {
        return c.enabled && shoreDistance(c,x,z)>=0;
    }
    public static boolean intersects(SeaConfig c, int startX, int startZ) {
        long lowX = startX <= 0 && startX + 15 >= 0 ? 0 : Math.min(Math.abs((long)startX), Math.abs((long)startX + 15));
        long lowZ = startZ <= 0 && startZ + 15 >= 0 ? 0 : Math.min(Math.abs((long)startZ), Math.abs((long)startZ + 15));
        long highX=Math.max(Math.abs((long)startX),Math.abs((long)startX+15));
        long highZ=Math.max(Math.abs((long)startZ),Math.abs((long)startZ+15));
        return c.enabled && boundaryDistance(c,c.innerBoundary,highX,highZ)>=0 &&
                boundaryDistance(c,c.outerBoundary,lowX,lowZ)<=0;
    }
    public static double shoreDistance(SeaConfig c, int x, int z) {
        return Math.min(boundaryDistance(c,c.innerBoundary,x,z),
                -boundaryDistance(c,c.outerBoundary,x,z));
    }
    /** Keep the mainland boundary fixed, but let the ridge wander gently along it. */
    public static double localBeachWidth(SeaConfig c,long seed,int x,int z) {
        var p=boundaryProjection(c,x,z);
        double bend=34*noise(seed+301,p.x,p.z,240)+14*noise(seed+302,p.x,p.z,100);
        return c.beachWidth+bend*c.coastWaviness/48.0;
    }
    private record BoundaryPoint(double x,double z) {}
    private static BoundaryPoint boundaryProjection(SeaConfig c,int x,int z) {
        double inner=boundaryDistance(c,c.innerBoundary,x,z),outer=-boundaryDistance(c,c.outerBoundary,x,z);
        double boundary=inner<outer ? c.innerBoundary : c.outerBoundary;
        double ax=Math.abs((double)x),az=Math.abs((double)z);
        double corner=Math.max(0,cornerRadius(c)-Math.abs(ax-az));
        double square=Math.max(ax,az)+corner*corner/(4*cornerRadius(c));
        if (square==0) return new BoundaryPoint(boundary,0);
        // Project a continuous ray onto the same smoothed square. Averaging the two
        // nearest face points made their coastal noise jump hundreds of blocks at
        // the outer diagonal, even though the geometric corner itself was smooth.
        double scale=boundary/square;
        double blend=smooth((2048-Math.abs(ax-az))/1024);
        double faceX=Math.copySign(ax>=az ? boundary : Math.min(ax,boundary),x);
        double faceZ=Math.copySign(az>=ax ? boundary : Math.min(az,boundary),z);
        return new BoundaryPoint(lerp(faceX,x*scale,blend),lerp(faceZ,z*scale,blend));
    }
    /** Profile distance: a wavy 550-ish block rise followed by exactly 500 blocks of slope. */
    public static double shoreDistance(SeaConfig c,long seed,int x,int z) {
        double d=shoreDistance(c,x,z),width=localBeachWidth(c,seed,x,z);
        return d<width ? d*c.beachWidth/width : c.beachWidth+d-width;
    }
    public static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t*t*t*(t*(t*6-15)+10);
    }
    public static double lerp(double a, double b, double t) { return a + (b-a)*t; }
    public static long hash(long seed, long x, long z) {
        long v = seed ^ x*0x9E3779B97F4A7C15L ^ z*0xC2B2AE3D27D4EB4FL;
        v = (v ^ (v >>> 30)) * 0xBF58476D1CE4E5B9L;
        v = (v ^ (v >>> 27)) * 0x94D049BB133111EBL;
        return v ^ (v >>> 31);
    }
    public static double unit(long hash) { return (hash >>> 11) * 0x1.0p-53; }
    public static double noise(long seed, double x, double z, double scale) {
        x /= scale; z /= scale;
        long ix = (long)Math.floor(x), iz = (long)Math.floor(z);
        double tx = smooth(x-ix), tz = smooth(z-iz);
        double a = lerp(unit(hash(seed,ix,iz)), unit(hash(seed,ix+1,iz)), tx);
        double b = lerp(unit(hash(seed,ix,iz+1)), unit(hash(seed,ix+1,iz+1)), tx);
        return lerp(a,b,tz)*2-1;
    }
    private static double gradient(long seed,long ix,long iz,double x,double z) {
        int direction=(int)(hash(seed,ix,iz)&7);
        return switch(direction) {
            case 0 -> x; case 1 -> -x; case 2 -> z; case 3 -> -z;
            case 4 -> (x+z)*.7071067811865476;
            case 5 -> (x-z)*.7071067811865476;
            case 6 -> (-x+z)*.7071067811865476;
            default -> (-x-z)*.7071067811865476;
        };
    }
    /** Rotated gradient noise avoids the rectangular flat patches of value noise. */
    private static double rollingNoise(long seed,double x,double z,double scale) {
        double a=(x*.8+z*.6)/scale,b=(z*.8-x*.6)/scale;
        long ix=(long)Math.floor(a),iz=(long)Math.floor(b);
        a-=ix;b-=iz;
        double u=smooth(a),v=smooth(b);
        return 1.5*lerp(lerp(gradient(seed,ix,iz,a,b),gradient(seed,ix+1,iz,a-1,b),u),
                lerp(gradient(seed,ix,iz+1,a,b-1),gradient(seed,ix+1,iz+1,a-1,b-1),u),v);
    }
    public static double floorHeight(SeaConfig c, long seed, int x, int z) {
        double n = .62*noise(seed+13,x,z,192) + .27*noise(seed+14,x,z,72) + .11*noise(seed+15,x,z,24);
        return lerp(c.minFloorY,c.maxFloorY,(n+1)*.5);
    }
    public static double height(SeaConfig c, long seed, int x, int z, double inlandHeight) {
        return height(c,seed,x,z,inlandHeight,floorHeight(c,seed,x,z));
    }
    public static double ridgeHeight(SeaConfig c,long seed,int x,int z) {
        var p=boundaryProjection(c,x,z);
        return Math.max(c.seaLevel+7,c.beachPeakY+9+BeachReference.measured(1,beachAlong(seed,p))-219);
    }
    private static double beachAlong(long seed,BoundaryPoint p) {
        return p.x+p.z+(hash(seed,41,42)&65535)+32*noise(seed+304,p.x,p.z,800);
    }
    /** Width of the cap within half a block of its peak, rather than a broad flat shelf. */
    public static double crestWidth(SeaConfig c,long seed,int x,int z) {
        var p=boundaryProjection(c,x,z);
        return 8+5*(noise(seed+310,p.x,p.z,160)+1)*.5;
    }
    private static double crestDrop(double distance,double width) {
        double t=Math.abs(distance)/(width*.5);
        return t<=1 ? .5*t*t : t-.5;
    }
    private static double softMinimum(double a,double b,double blend) {
        if (blend<=0) return Math.min(a,b);
        double overlap=Math.max(0,blend-Math.abs(a-b));
        return Math.min(a,b)-overlap*overlap/(4*blend);
    }
    public static double floorBaseline(SeaConfig c) { return (c.minFloorY+c.maxFloorY)/2.0; }
    /** Buried beach profile; a separate taper lowers high mainland into its rising sand. */
    public static double height(SeaConfig c, long seed, int x, int z, double inlandHeight,double floor) {
        double raw=shoreDistance(c,x,z);
        if (raw<=0) return c.mainlandJoinY;
        double foot=c.beachWidth+c.underwaterSlopeWidth;
        // localBeachWidth differs by at most coastWaviness. Past this conservative
        // bound the transition is exactly complete for every seed and shore orientation.
        if(raw>=foot+c.coastWaviness+FLOOR_TRANSITION_WIDTH)
            return lerp(floorBaseline(c),floor,1);
        double d = shoreDistance(c,seed,x,z);
        if (d >= foot) {
            // Native ridges/channels begin on the floor, not on the descending sand ramp.
            // Ease their influence in after the foot so both height and tangent join smoothly.
            return lerp(floorBaseline(c),floor,smooth((d-foot)/FLOOR_TRANSITION_WIDTH));
        }
        double crest=ridgeHeight(c,seed,x,z);
        if (d >= c.beachWidth) {
            return descendingHeight(c,crest,crestWidth(c,seed,x,z),d-c.beachWidth);
        }
        double t = Math.max(0,d/c.beachWidth);
        var coast=boundaryProjection(c,x,z);
        double rise=BeachReference.rise(t,beachAlong(seed,coast));
        double base=lerp(c.mainlandJoinY,crest,rise);
        double cap=crest-crestDrop(d-c.beachWidth,crestWidth(c,seed,x,z));
        double height=softMinimum(base,cap,8*smooth((c.beachWidth-d)/32));
        // Small broad undulations break the excessively perfect contour sheets. They
        // taper away at both joins and never import the sample's holes or cave mouths.
        double bumps=1.35*rollingNoise(seed+311,x,z,68)+.55*rollingNoise(seed+312,x,z,45);
        return height+bumps*smooth(d/48)*smooth((c.beachWidth-d)/48);
    }
    private static double descendingHeight(SeaConfig c,double crest,double capWidth,double offset){
        double base=lerp(crest,floorBaseline(c),ramp(offset/c.underwaterSlopeWidth));
        double cap=crest-crestDrop(offset,capWidth);
        return softMinimum(base,cap,8*smooth(offset/32));
    }
    /** Zero is the first wet sand column: rounded ground falls below seaLevel-1. */
    static double wetShoreOffset(SeaConfig c,long seed,int x,int z){
        double crest=ridgeHeight(c,seed,x,z),capWidth=crestWidth(c,seed,x,z),low=0,high=c.underwaterSlopeWidth;
        for(int i=0;i<20;i++){double middle=(low+high)*.5;if(descendingHeight(c,crest,capWidth,middle)>=c.seaLevel-1.5)low=middle;else high=middle;}
        return shoreDistance(c,seed,x,z)-c.beachWidth-(low+high)*.5;
    }
    /** Mostly straight ascent/descent, with a short tangent blend at each end. */
    public static double ramp(double t) {
        t=Math.max(0,Math.min(1,t));
        double e=.16;
        if (t<e) { double u=t/e; return e*(u*u*u-.5*u*u*u*u)/(1-e); }
        if (t>1-e) return 1-ramp(1-t);
        return (t-e*.5)/(1-e);
    }
    public static boolean deepFloor(SeaConfig c,long seed,int x,int z) {
        double raw=shoreDistance(c,x,z);
        if(!c.enabled||raw<0)return false;
        double foot=c.beachWidth+c.underwaterSlopeWidth;
        return raw>=foot+c.coastWaviness || shoreDistance(c,seed,x,z)>=foot;
    }
    public static boolean coldEligible(SeaConfig c,int x,int z) {
        return contains(c,x,z) && shoreDistance(c,x,z)>=c.coldOceanInset;
    }
    private static double referenceDunes(long seed, double x, double z) {
        double a = x + 160*noise(seed+41,x,z,768) + (hash(seed,3,4)&65535);
        double b = z + 160*noise(seed+42,x,z,768) + (hash(seed,5,6)&65535);
        double u = mirror(a/6), v = mirror(b/6);
        int ix=(int)u, iz=(int)v;
        return cubic(row(ix,iz-1,u-ix),row(ix,iz,u-ix),row(ix,iz+1,u-ix),row(ix,iz+2,u-ix),v-iz);
    }
    public static double vegetationDensity(int groundY) { return smooth((-14-groundY)/8.0); }
    private static double cubic(double a,double b,double c,double d,double t) {
        return b+.5*t*(c-a+t*(2*a-5*b+4*c-d+t*(3*(b-c)+d-a)));
    }
    private static double row(int x,int z,double t) {
        return cubic(sample(x-1,z),sample(x,z),sample(x+1,z),sample(x+2,z),t);
    }
    private static double sample(int x,int z) {
        x=Math.abs(x);z=Math.abs(z);
        if (x>=TILE_SIZE) x=2*(TILE_SIZE-1)-x;
        if (z>=TILE_SIZE) z=2*(TILE_SIZE-1)-z;
        return DUNES[z*TILE_SIZE+x];
    }
    private static double mirror(double x) {
        double period=2*(TILE_SIZE-1);
        x=x-Math.floor(x/period)*period;
        return x<TILE_SIZE-1 ? x : period-x;
    }
    private static float[] loadDunes() {
        float[] result=new float[TILE_SIZE*TILE_SIZE];
        try (DataInputStream in = new DataInputStream(Objects.requireNonNull(
                SeaTerrain.class.getResourceAsStream("/assets/greatsea/dunes.bin")))) {
            for (int i=0;i<result.length;i++) result[i]=in.readShort()/100f;
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
        return result;
    }
}
