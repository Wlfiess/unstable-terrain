package dev.greatsea;


/** Seeded three-dimensional terrain. Templates contain terrain only, never reference builds. */
public final class FarlandsTerrain {
    public enum Stage { PLAINS,REPEATING,FLOATING,GRAVEL,SPIKES,GRAVEL_AFTER_SPIKES,SPLIT,CHERRY,PLAINS_HILLS,SAND_DUNES,SAND_FARLANDS,CORRUPTED,ORBITALS,CHUNKS,DECAY,WATER,SKYGRID }
    private static final Stage[] STAGES=Stage.values();
    public record Column(int top,int bottom,boolean voidColumn,int material) {}
    private FarlandsTerrain() {}
    public static int start(Stage stage) { int n=0;for(int i=0;i<stage.ordinal();i++)n+=GreatSea.FARLANDS.stageWidths[i];return n; }
    public static int end(Stage stage) { return start(stage)+GreatSea.FARLANDS.stageWidths[stage.ordinal()]; }
    private static Stage localStage(int x) {
        int distance=Math.max(0,x),end=0;
        for(Stage stage:STAGES) {end+=GreatSea.FARLANDS.stageWidths[stage.ordinal()];if(distance<end)return stage;}
        return Stage.SKYGRID;
    }
    public static double progress(int x) { Stage s=localStage(x);return Math.max(0,Math.min(1,(x-start(s))/(double)GreatSea.FARLANDS.stageWidths[s.ordinal()])); }
    public static Stage stage(int x,int z) { return localStage(FarlandsWorld.project(x,z).depth()); }
    public static boolean missingChunk(long seed,int x,int z) {
        if(SpokelandsTerrain.contains(x,z))return false;
        int cx=Math.floorDiv(x,16),cz=Math.floorDiv(z,16);
        // Keep this chunk-center query from evicting the active block column's
        // projection on every height sample.
        int depth=(int)FarlandsWorld.depth(cx*16+8,cz*16+8);
        Stage s=localStage(depth);
        if(s!=Stage.CORRUPTED)return false;
        double chance=.16+.30*progress(depth);
        return SeaTerrain.unit(SeaTerrain.hash(seed+810,cx,cz))<chance;
    }
    /** Neighboring cells use disjoint palette groups so the chunk mosaic really changes. */
    public static int chunkBiome(long seed,int x,int z) {
        int cx=Math.floorDiv(x,16),cz=Math.floorDiv(z,16);
        return ((cx&1)+2*(cz&1))*4+(int)(SeaTerrain.hash(seed+811,cx,cz)&3);
    }
    public static int material(long seed,int x,int y,int z) {
        if(FarlandsMargins.active(x,z))return FarlandsMargins.material(seed,x,y,z);
        return rawMaterial(seed,x,y,z);
    }
    static int rawMaterial(long seed,int x,int y,int z) {
        if(SpokelandsTerrain.contains(x,z))return SpokelandsTerrain.material(seed,x,y,z);
        if (!FarlandsWorld.contains(x,z) || missingChunk(seed,x,z)) return 0;
        FarlandsWorld.Point p=FarlandsWorld.project(x,z);
        Stage s=localStage(p.depth());
        // Landmarks only exist in their own region. Avoid testing their geometry for
        // every block of unrelated chunks while retaining landmark precedence.
        if(s==Stage.REPEATING) {
            if(FarlandsEmpire.backdrop(x,z))return FarlandsSamples.backdropMaterial(x,y,z);
            if(FarlandsEmpire.contains(x,y,z))return FarlandsEmpire.material(x,y,z);
        }
        if(s==Stage.ORBITALS && (FarlandsOrbitals.gap(seed,x,z) || FarlandsOrbitals.hasBlock(seed,x,y,z)))return FarlandsOrbitals.material(seed,x,y,z);
        if(s==Stage.SPLIT && FarlandsTemple.contains(x,y,z))return FarlandsTemple.material(x,y,z);
        if(s==Stage.SKYGRID)return gridMaterial(seed,p.depth(),x,y,z);
        if(nativeStage(s))return nativeMaterial(seed,x,y,z,p.depth());
        if(s==Stage.PLAINS || s==Stage.ORBITALS || s==Stage.CORRUPTED || s==Stage.SAND_FARLANDS || s==Stage.FLOATING || s==Stage.CHUNKS)return FarlandsNatural.material(seed,x,y,z);
        return localMaterial(seed,p.depth(),y,p.tangent(),chunkBiome(seed,x,z));
    }
    static boolean nativeStage(Stage s) {
        return s==Stage.REPEATING || s==Stage.CHERRY || s==Stage.DECAY;
    }
    private static int nativeMaterial(long seed,int x,int y,int z,int depth) {
        if(y< -64 || y>319)return 0;
        Stage s=localStage(depth);
        // Five blocks of water beneath the bedrock; the void begins below the map.
        if(FarlandsSamples.basalWater(s) && y<=-60)return 8;
        int m=FarlandsSamples.material(seed,x,y,z);
        if(s==Stage.REPEATING)m=FarlandsRepeatingContents.material(seed,x,y,z,m);
        if(!FarlandsSamples.basalWater(s) && m==10)return 0;
        if(s!=Stage.DECAY || m==0)return m;
        double p=progress(depth),u=Math.max(0,(p-.12)/.88);
        double removed=m==10 ? Math.max(0,(p-.85)/.15):Math.pow(u,3.5);
        if(depth>=end(Stage.DECAY)-16)return 0;
        // The same block rank repeats in each tile, so erosion advances monotonically.
        double rank=SeaTerrain.unit(SeaTerrain.hash(seed+815,Math.floorMod(x,16)+31L*(y+64),Math.floorMod(FarlandsWorld.project(x,z).tangent(),128)));
        return rank<removed ? 0:m;
    }
    private static int localMaterial(long seed,int x,int y,int z,int biomeIndex) {
        return localMaterial(seed,x,y,z,biomeIndex,localStage(x));
    }
    static int transitionMaterial(long seed,int x,int y,int z,Stage stage){
        if(stage==Stage.SAND_FARLANDS)return FarlandsNatural.material(seed,x,y,z,stage);
        var p=FarlandsWorld.project(x,z);
        return localMaterial(seed,p.depth(),y,p.tangent(),chunkBiome(seed,x,z),stage);
    }
    private static int localMaterial(long seed,int x,int y,int z,int biomeIndex,Stage s) {
        if(y< -64 || y>319)return 0;
        if(s==Stage.SKYGRID) {
            return gridMaterial(seed,x,x,y,z);
        }
        if(s==Stage.WATER)return y>=-64 && y<=-60 ? 8:0;
        if(s==Stage.SPLIT)return splitReferenceMaterial(x,y,z);
        if(s==Stage.GRAVEL || s==Stage.GRAVEL_AFTER_SPIKES) {
            int lane=Math.floorMod(z,32),top=lane<14?173:172;
            if(y>top)return 0;
            if(y==top)return lane<14?4:9;
            if(y>top-4)return lane<14?3:9;
            return y<0?2:1;
        }
        if(s==Stage.SPIKES) {
            int top=FarlandsRelief.spire(seed,x,z);
            if(y>top)return 0;
            if(y==top)return 7;
            // Small irregular snow shelves, not diagonal stripes across every pillar.
            long patch=SeaTerrain.hash(seed+835,Math.floorDiv(x,2)+47L*Math.floorDiv(y,3),Math.floorDiv(z,2));
            return y>-60 && SeaTerrain.unit(patch)<.03?7:1;
        }
        if(s==Stage.SAND_DUNES || s==Stage.PLAINS_HILLS) {
            int top=FarlandsRelief.ridge(seed,x,z,s==Stage.SAND_DUNES);
            if(y>top) {
                if(s==Stage.PLAINS_HILLS && y==top+1) {
                    double plant=SeaTerrain.unit(SeaTerrain.hash(seed+837,x,z));
                    if(plant<.035)return 30;if(plant<.075)return 31;
                }
                return 0;
            }
            if(s==Stage.SAND_DUNES) {
                if(y<=top-3)return 6;
                double cap=SeaTerrain.unit(SeaTerrain.hash(seed+8381,x,z));
                int thickness=cap<.205?1:cap<.85?2:3;
                return y>top-thickness?5:6;
            }
            if(y==top)return 4;
            if(y>=top-2) {
                double cap=SeaTerrain.unit(SeaTerrain.hash(seed+8382,x,z));
                int soil=cap<.038?0:cap<.8235?1:2;
                if(y>=top-soil)return 3;
            }
            return 1;
        }
        return 0;
    }
    private record SkygridSchedule(int width,int[] planes,int[] spacings) {}
    private static volatile SkygridSchedule SKYGRID_SCHEDULE;
    private static SkygridSchedule skygridSchedule() {
        int width=GreatSea.FARLANDS.stageWidths[Stage.SKYGRID.ordinal()];
        var cached=SKYGRID_SCHEDULE;if(cached!=null&&cached.width()==width)return cached;
        var planes=new java.util.ArrayList<Integer>();var spacings=new java.util.ArrayList<Integer>();
        for(int offset=0;offset<Math.ceil(width*.90);) {
            // Six blocks center-to-center means exactly five empty blocks at the entrance.
            double u=Math.max(0,(offset/(double)width-.04)/.86);
            int spacing=6+(int)Math.round(122*Math.pow(u,1.6));
            planes.add(offset);spacings.add(spacing);offset+=spacing;
        }
        cached=new SkygridSchedule(width,planes.stream().mapToInt(Integer::intValue).toArray(),spacings.stream().mapToInt(Integer::intValue).toArray());
        SKYGRID_SCHEDULE=cached;return cached;
    }
    static int skygridSpacing(int depth) {
        var grid=skygridSchedule();int offset=depth-start(Stage.SKYGRID);
        if(offset<0||offset>=Math.ceil(grid.width()*.90))return 0;
        int plane=java.util.Arrays.binarySearch(grid.planes(),offset);
        return plane<0?0:grid.spacings()[plane];
    }
    static double skygridRetention(int depth) {
        double p=(depth-start(Stage.SKYGRID))/(double)GreatSea.FARLANDS.stageWidths[Stage.SKYGRID.ordinal()];
        return 1-SeaTerrain.smooth(Math.max(0,Math.min(1,(p-.04)/.86)));
    }
    private static int gridMaterial(long seed,int depth,int x,int y,int z) {
        if(y< -64||y>319)return 0;
        int spacing=skygridSpacing(depth);if(spacing==0||Math.floorMod(y,spacing)!=0)return 0;
        int tangent=FarlandsWorld.project(x,z).tangent();if(Math.floorMod(tangent,spacing)!=0)return 0;
        // Candidate blocks remain on isolated lattice points; deterministic omissions
        // gradually thin the population in addition to the widening spaces.
        long rank=SeaTerrain.hash(seed+816,depth+104729L*Math.floorDiv(y,spacing),Math.floorDiv(tangent,spacing));
        return SeaTerrain.unit(rank)<skygridRetention(depth)?4:0;
    }

    static double splitHalfWidth(int depth){return Math.max(0,(end(Stage.SPLIT)-depth)*.125);}
    private static final class SplitReference{static final FarlandsChunkTemplate DATA=FarlandsChunkTemplate.load("split-reference");}
    private static final class SplitCoordinates{int depth,tangent,center;boolean valid;final int[] xy=new int[2];}
    private static final ThreadLocal<SplitCoordinates> SPLIT_COORDINATES=ThreadLocal.withInitial(SplitCoordinates::new);
    private static int[] splitCoordinates(int depth,int tangent){
        var cached=SPLIT_COORDINATES.get();int center=FarlandsTemple.center();if(cached.valid&&cached.depth==depth&&cached.tangent==tangent&&cached.center==center)return cached.xy;
        cached.valid=true;cached.depth=depth;cached.tangent=tangent;cached.center=center;
        double a=depth-FarlandsTemple.center(),b=tangent,r=Math.hypot(a,b);
        if(r<82){double angle=Math.atan2(b,a);a=82*Math.cos(angle);b=82*Math.sin(angle);}
        else if(r>230){a*=230/r;b*=230/r;}
        cached.xy[0]=256+(int)Math.round(a);cached.xy[1]=256+(int)Math.round(b);return cached.xy;
    }
    private static int splitStackRaw(int depth,int y,int tangent){
        if(y< -64||y>204)return 0;
        var p=splitCoordinates(depth,tangent);int first=Math.max(0,(int)Math.ceil((145-y)/51.0));
        for(int k=first;y+k*51<=204;k++){int material=SplitReference.DATA.material(p[0],y+k*51,p[1]);if(material!=0)return material;}
        return 0;
    }
    static int splitReferenceMaterial(int depth,int y,int tangent){
        int material=splitStackRaw(depth,y,tangent);if(material==0)return 0;
        if(y==168&&Math.hypot(depth-FarlandsTemple.center(),tangent)<=79&&StructurePolicy.enabled())return 4;
        if(splitStackRaw(depth,y+1,tangent)==0)return 4;
        return material==4?3:material;
    }
    static int splitTop(int depth,int tangent){
        if(Math.hypot(depth-FarlandsTemple.center(),tangent)<=79)return 168;
        var p=splitCoordinates(depth,tangent);return SplitReference.DATA.ground(p[0],p[1]);
    }
    private static final class SplitEdge {int depth,tangent,center;double edge;boolean valid;}
    private static final ThreadLocal<SplitEdge> SPLIT_EDGE=ThreadLocal.withInitial(SplitEdge::new);
    static double splitEdge(int depth,int tangent){
        var cached=SPLIT_EDGE.get();int center=FarlandsTemple.center();
        if(cached.valid && cached.depth==depth && cached.tangent==tangent && cached.center==center)return cached.edge;
        cached.valid=true;cached.depth=depth;cached.tangent=tangent;cached.center=center;
        double x=depth-center,z=tangent,r=Math.hypot(x,z);
        // Terrain reaches the round foundation itself; only the interior is protected.
        if(r<=79){cached.edge=-1;return -1;}
        double nearest=Double.MAX_VALUE;
        for(int ray=0;ray<8;ray++) {
            double angle=ray*Math.PI/4,along=x*Math.cos(angle)+z*Math.sin(angle);
            if(along<0)continue;
            nearest=Math.min(nearest,Math.abs(-x*Math.sin(angle)+z*Math.cos(angle))-Math.min(1000,16+along*.11));
        }
        cached.edge=nearest;return nearest;
    }
    static boolean splitBare(int depth,int tangent){return splitTop(depth,tangent)<=173;}
    public static boolean bareSplit(int x,int z){var p=FarlandsWorld.project(x,z);return !SpokelandsTerrain.contains(x,z) && stage(x,z)==Stage.SPLIT && splitBare(p.depth(),p.tangent());}
    public static int spikeHeight(long seed,int x,int z,boolean sand) {
        return sand?FarlandsRelief.ridge(seed,x,z,true):FarlandsRelief.spire(seed,x,z);
    }
    static int hillHeight(long seed,int x,int z) {return FarlandsRelief.ridge(seed,x,z,false);}
    public static Column column(long seed,int x,int z) {
        int top=-65,bottom=320,m=0;
        if(!FarlandsWorld.contains(x,z) || missingChunk(seed,x,z))return new Column(top,bottom,true,m);
        FarlandsWorld.Point p=FarlandsWorld.project(x,z);
        for(int y=319;y>=-64;y--) {
            int value=material(seed,x,y,z);
            if(value!=0 && value!=8 && value!=29 && value!=14 && value!=15 && value!=27 && value!=28 && value!=30 && value!=31) {if(top==-65){top=y;m=value;}bottom=y;}
        }
        return new Column(top,bottom,top==-65,m);
    }
}

