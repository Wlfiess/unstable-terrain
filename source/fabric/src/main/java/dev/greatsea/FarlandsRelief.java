package dev.greatsea;

/** Source-calibrated hills and dunes, with separately spaced stone spires. */
final class FarlandsRelief {
    private static final class CachedHeight {long seed=Long.MIN_VALUE;int x=Integer.MIN_VALUE,z,kind,height;}
    private static final ThreadLocal<CachedHeight> CACHE=ThreadLocal.withInitial(CachedHeight::new);
    private static int cached(long seed,int x,int z,int kind) {
        var c=CACHE.get();if(c.seed!=seed || c.x!=x || c.z!=z || c.kind!=kind) {
            c.seed=seed;c.x=x;c.z=z;c.kind=kind;
            c.height=kind==2?computeSpire(seed,x,z):computeRidge(seed,x,z,kind==1);
        }
        return c.height;
    }
    static int ridge(long seed,int x,int z,boolean sand){return cached(seed,x,z,sand?1:0);}
    static int spire(long seed,int x,int z){return cached(seed,x,z,2);}
    static int spikeFloor(long seed,int x,int z) {
        return Math.max(-62,Math.min(-57,-60+(int)Math.round(
                2.8*SeaTerrain.noise(seed+8341,x,z,64)+1.2*SeaTerrain.noise(seed+8342,x,z,24))));
    }
    private static int computeRidge(long seed,int x,int z,boolean sand) {
        if(sand)return OxumsDunes.height(seed,x,z);
        int offset=x-FarlandsTerrain.start(FarlandsTerrain.Stage.PLAINS_HILLS);
        int natural=OxumsHills.height(seed,x,z);
        if(offset<0||offset>=112)return natural;
        int cherryX=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.PLAINS_HILLS)-1;
        int cherryTop=-64;
        for(int y=319;y>=-64;y--)if(TerrainMorph.solid(FarlandsTerrain.rawMaterial(seed,cherryX,y,z))){cherryTop=y;break;}
        int landing=cherryTop-20;
        // A quiet landing keeps hill crests out of the straight twenty-block step.
        double weight=SeaTerrain.smooth(Math.max(0,Math.min(1,(offset-16)/96.0)));
        return (int)Math.round(SeaTerrain.lerp(landing,natural,weight));
    }
    private static int computeSpire(long seed,int x,int z) {
        return ProceduralRelief.spire(seed,x,z);
    }
}
