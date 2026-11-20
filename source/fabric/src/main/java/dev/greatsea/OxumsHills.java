package dev.greatsea;

/**
 * Continuous seeded hills calibrated to the grassy interior of the Oxums save.
 * Its downloaded edge and surrounding y=-23 superflat are not terrain features.
 * No heightmap is tiled: independently seeded, rotated gradient fields produce
 * the measured joined crests, broad saddles, and steep rounded troughs.
 */
final class OxumsHills {
    private OxumsHills() {}

    static int height(long seed, int x, int z) {
        // Broad, gentle coordinate bending varies crest direction without folds.
        double wx=x+8*noise(seed+8591,x,z,170,.955336489125606,.295520206661340);
        double wz=z+8*noise(seed+8592,x,z,170,.453596121425577,.891207360061435);
        // The 54-block principal scale reproduces the sample's ~32-block
        // correlation falloff. Shorter scales retain irregular shoulders/crests.
        double relief=.76*noise(seed+8593,wx,wz,54,.819648017845479,.572867460100481)
                +.23*noise(seed+8594,wx,wz,27.54,.371659872260533,.928368967249167)
                +.075*noise(seed+8595,wx,wz,13.77,.935896823677935,-.352274233275090);
        double detail=2.2*noise(seed+8596,wx,wz,8,.573519986072457,.819191568300998)
                +6*noise(seed+8597,x,z,210,.955336489125606,-.295520206661340);
        // Rounded extrema and steep middle slopes: source p10/p50/p90 =
        // 78/172/269; axis-step p50/p90/p99 = 3/8/12 blocks.
        int top=(int)Math.round(173+137*Math.tanh(2.6*relief)+detail);
        return Math.max(30,Math.min(311,top));
    }

    private static double noise(long seed,double x,double z,double scale,double cosine,double sine) {
        double a=(cosine*x+sine*z)/scale,b=(cosine*z-sine*x)/scale;
        long ix=(long)Math.floor(a),iz=(long)Math.floor(b);
        a-=ix;b-=iz;
        double u=smooth(a),v=smooth(b);
        double north=lerp(gradient(seed,ix,iz,a,b),gradient(seed,ix+1,iz,a-1,b),u);
        double south=lerp(gradient(seed,ix,iz+1,a,b-1),gradient(seed,ix+1,iz+1,a-1,b-1),u);
        return 1.5*lerp(north,south,v);
    }
    private static double smooth(double t) {return t*t*t*(t*(t*6-15)+10);}
    private static double lerp(double a,double b,double t) {return a+(b-a)*t;}
    private static double gradient(long seed,long x,long z,double a,double b) {
        int direction=(int)(hash(seed,x,z)&7);
        return switch(direction) {
            case 0 -> a;
            case 1 -> -a;
            case 2 -> b;
            case 3 -> -b;
            case 4 -> (a+b)*.7071067811865476;
            case 5 -> (a-b)*.7071067811865476;
            case 6 -> (-a+b)*.7071067811865476;
            default -> (-a-b)*.7071067811865476;
        };
    }
    private static long hash(long seed,long x,long z) {
        long h=seed^x*0x9E3779B97F4A7C15L^z*0xC2B2AE3D27D4EB4FL;
        h=(h^(h>>>30))*0xBF58476D1CE4E5B9L;
        h=(h^(h>>>27))*0x94D049BB133111EBL;
        return h^(h>>>31);
    }
}
