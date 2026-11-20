package dev.greatsea;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

/** Separate negative-face route observed in Spoke's two Farlands films.
 * Shapes are continuous seeded fields; an edited film does not specify exact widths. */
public final class SpokelandsTerrain {
    public enum Region { CLASSIC,FLOWER_HILLS,RED_PLAINS,WINDING,DARK_FOREST,BRIDGES,RAINBOW_FOREST,SAND_LANDS,SCULK_FOREST }
    private static final Region[] REGIONS=Region.values();
    private static final int WINDING_APPROACH_LENGTH=500;
    private static final net.minecraft.block.enums.Thickness[] BRIDGE_THICKNESS={net.minecraft.block.enums.Thickness.TIP,net.minecraft.block.enums.Thickness.TIP_MERGE,net.minecraft.block.enums.Thickness.FRUSTUM,net.minecraft.block.enums.Thickness.MIDDLE,net.minecraft.block.enums.Thickness.BASE};
    public static final RegistryKey<Biome> RED_BIOME=RegistryKey.of(net.minecraft.registry.RegistryKeys.BIOME,net.minecraft.util.Identifier.of("greatsea","spoke_red_plains"));
    public static final RegistryKey<Biome> DARK_BIOME=RegistryKey.of(net.minecraft.registry.RegistryKeys.BIOME,net.minecraft.util.Identifier.of("greatsea","spoke_dark_forest"));
    // IDs above the old terrain palette are local to this route and its state mapper.
    public static final int CRIMSON=42,NETHERRACK=43,ANDESITE=44,RED_SAND=45,RED_SANDSTONE=46,
            SCULK=47,WARPED=48,DRIPSTONE=49,GRANITE=50,CLAY=51,PLANKS=52,FENCE=53,CHAIN=54,SANDSTONE=55,SAND=56,BARK=57,TERRACOTTA=58,ORANGE_CLAY=59,MOSS=60,BRIDGE_STAIRS=61,MUD=62,FROGLIGHT=63,MOSSY_ROCK=64,SMOOTH_ROCK=65,BRIDGE_TOP_SLAB=66,BRIDGE_BOTTOM_SLAB=67,JUNGLE_PLANKS=68,JUNGLE_WOOD_Y=69,JUNGLE_WOOD_X=70,JUNGLE_WOOD_Z=71,BRIDGE_DRIPSTONE_LAST=81,BRIDGE_SPRUCE=82,BRIDGE_VILLAGE=83;
    private static final class Column {
        long seed;int x,z,boundary,widths,ground;boolean valid,hasWaves;final byte[] bridgeDirections=new byte[384];
        final byte[] blocks=new byte[384],sandSource=new byte[384];
        
    }
    private static final class Blend {
        final Column left=new Column(),right=new Column();final double[] ld=new double[384],rd=new double[384];
    }
    private static final ThreadLocal<Blend> BLEND=ThreadLocal.withInitial(Blend::new);
    private static final ThreadLocal<Column> COLUMN=ThreadLocal.withInitial(Column::new);
    private SpokelandsTerrain(){}
    public static boolean contains(int x,int z){
        return FarlandsWorld.contains(x,z) && negativeFace(x,z);
    }
    public static boolean negativeFace(int x,int z){
        return Math.abs((long)x)>=Math.abs((long)z)?x<0:z<0;
    }
    private static int width(Region region){
        int configured=GreatSea.FARLANDS.spokelandsWidths[region.ordinal()];
        return region==Region.WINDING?Math.max(configured,WINDING_APPROACH_LENGTH+256):configured;
    }
    public static int start(Region region){int n=0;for(int i=0;i<region.ordinal();i++)n+=width(REGIONS[i]);return n;}
    public static int end(Region region){return start(region)+width(region);}
    static int windingStart(){return end(Region.RED_PLAINS)+WINDING_APPROACH_LENGTH;}
    public static Region region(int x,int z){return atDepth((int)FarlandsWorld.depth(x,z));}
    private static Region atDepth(int depth){int n=0;for(var r:REGIONS){n+=width(r);if(depth<n)return r;}return Region.SCULK_FOREST;}
    public static RegistryKey<Biome> biome(long seed,int x,int z){
        return switch(region(x,z)){
            case CLASSIC -> BiomeKeys.PLAINS;
            case FLOWER_HILLS,RAINBOW_FOREST -> BiomeKeys.FLOWER_FOREST;
            case RED_PLAINS -> RED_BIOME;
            case WINDING -> BiomeKeys.PLAINS;
            case DARK_FOREST -> DARK_BIOME;
            case BRIDGES -> BiomeKeys.DRIPSTONE_CAVES;
            case SAND_LANDS -> BiomeKeys.BADLANDS;
            // The footage keeps the blue Overworld sky above the teal woodland.
            // Its warped ground, fungi and luminous trees are placed explicitly.
            case SCULK_FOREST -> BiomeKeys.FOREST;
        };
    }
    private static Column column(long seed,int x,int z){
        var c=COLUMN.get();int stamp=java.util.Arrays.hashCode(GreatSea.FARLANDS.spokelandsWidths);
        if(!c.valid||c.seed!=seed||c.x!=x||c.z!=z||c.boundary!=GreatSea.FARLANDS.entryBoundary||c.widths!=stamp){
            c.valid=true;c.seed=seed;c.x=x;c.z=z;c.boundary=GreatSea.FARLANDS.entryBoundary;c.widths=stamp;build(c,seed,x,z);
        }
        return c;
    }
    public static int material(long seed,int x,int y,int z){if(!contains(x,z)||y< -64||y>575)return 0;
        var c=column(seed,x,z);
        if(c.hasWaves&&y>=160){
            var p=FarlandsWorld.project(x,z);
            int q=y-160;
            if(!windingWave(seed,p.depth(),q,p.tangent()))return 0;
            return windingWave(seed,p.depth(),q+1,p.tangent())?3:4;
        }
        return y>319?0:c.blocks[y+64]&255;}
    // Keep all approved lower waves. The last complete crest is lifted nine
    // blocks into the space occupied by the formerly truncated extra layer.
    // Source phases 8..10 are empty, so the crest ends naturally at Y526.
    static boolean windingWave(long seed,int depth,int q,int tangent){
        if(q<0||q>366)return false;
        if(q>322){if(q<335)return false;q-=9;}
        return TerrainSamples138.windingSolid(seed,depth,q,tangent);
    }
    static int riverBed(long seed,double a,double b){
        double patch=.72*FarlandsNatural.gradient2(seed+18196,a/15.0,b/15.0)
                +.28*FarlandsNatural.gradient2(seed+18197,a/5.0,b/5.0);
        return patch>-.10?MUD:SCULK;
    }
    /** The walking ground, rather than the highest overhead winding ribbon. */
    public static int ground(long seed,int x,int z){return contains(x,z)?column(seed,x,z).ground:-65;}
    public static BlockState state(long seed,int x,int y,int z){
        int sampled=material(seed,x,y,z);
        if(sampled==BRIDGE_VILLAGE)return BridgeVillages.state(x,y,z);
        if(sampled>=72&&sampled<=81)return Blocks.POINTED_DRIPSTONE.getDefaultState().with(net.minecraft.state.property.Properties.VERTICAL_DIRECTION,sampled>=77?net.minecraft.util.math.Direction.DOWN:net.minecraft.util.math.Direction.UP).with(net.minecraft.state.property.Properties.THICKNESS,BRIDGE_THICKNESS[Math.floorMod(sampled-72,5)]);
        return switch(sampled){
            case 0 -> Blocks.AIR.getDefaultState();case 2 -> Blocks.DEEPSLATE.getDefaultState();
            case 3 -> Blocks.DIRT.getDefaultState();case 4 -> Blocks.GRASS_BLOCK.getDefaultState();
            case 8 -> Blocks.WATER.getDefaultState();case 9 -> Blocks.GRAVEL.getDefaultState();case 10 -> Blocks.BEDROCK.getDefaultState();
            case CRIMSON -> Blocks.CRIMSON_NYLIUM.getDefaultState();case NETHERRACK -> Blocks.NETHERRACK.getDefaultState();
            case ANDESITE -> Blocks.ANDESITE.getDefaultState();case RED_SAND -> Blocks.RED_SAND.getDefaultState();
            case RED_SANDSTONE -> Blocks.RED_SANDSTONE.getDefaultState();case SCULK -> Blocks.SCULK.getDefaultState();
            case WARPED -> Blocks.WARPED_NYLIUM.getDefaultState();case DRIPSTONE -> Blocks.DRIPSTONE_BLOCK.getDefaultState();
            case GRANITE -> Blocks.GRANITE.getDefaultState();case CLAY -> Blocks.CLAY.getDefaultState();
            case PLANKS -> Blocks.OAK_PLANKS.getDefaultState();case FENCE -> fence(seed,x,y,z);
            case SANDSTONE -> Blocks.SANDSTONE.getDefaultState();case SAND -> Blocks.SAND.getDefaultState();
            case BARK -> Blocks.STRIPPED_OAK_WOOD.getDefaultState();
            case BRIDGE_TOP_SLAB -> Blocks.OAK_SLAB.getDefaultState().with(net.minecraft.state.property.Properties.SLAB_TYPE,net.minecraft.block.enums.SlabType.TOP);
            case BRIDGE_BOTTOM_SLAB -> Blocks.OAK_SLAB.getDefaultState();
            case BRIDGE_STAIRS -> Blocks.OAK_STAIRS.getDefaultState().with(net.minecraft.state.property.Properties.HORIZONTAL_FACING,net.minecraft.util.math.Direction.values()[column(seed,x,z).bridgeDirections[y+64]]);
            case TERRACOTTA -> Blocks.TERRACOTTA.getDefaultState();case ORANGE_CLAY -> Blocks.ORANGE_TERRACOTTA.getDefaultState();case MOSS -> Blocks.MOSS_BLOCK.getDefaultState();
            case MOSSY_ROCK -> Blocks.MOSSY_COBBLESTONE.getDefaultState();case SMOOTH_ROCK -> Blocks.SMOOTH_STONE.getDefaultState();
            case MUD -> Blocks.MUD.getDefaultState();case FROGLIGHT -> Blocks.PEARLESCENT_FROGLIGHT.getDefaultState();
            case BRIDGE_SPRUCE -> Blocks.SPRUCE_PLANKS.getDefaultState();
            case JUNGLE_PLANKS -> Blocks.JUNGLE_PLANKS.getDefaultState();
            case JUNGLE_WOOD_Y -> Blocks.STRIPPED_JUNGLE_WOOD.getDefaultState();
            case JUNGLE_WOOD_X -> Blocks.STRIPPED_JUNGLE_WOOD.getDefaultState().with(net.minecraft.state.property.Properties.AXIS,net.minecraft.util.math.Direction.Axis.X);
            case JUNGLE_WOOD_Z -> Blocks.STRIPPED_JUNGLE_WOOD.getDefaultState().with(net.minecraft.state.property.Properties.AXIS,net.minecraft.util.math.Direction.Axis.Z);
            case CHAIN -> Blocks.IRON_CHAIN.getDefaultState();default -> Blocks.STONE.getDefaultState();
        };
    }
    private static BlockState fence(long seed,int x,int y,int z){
        var state=Blocks.SPRUCE_FENCE.getDefaultState();
        // Generated rail states need their connections immediately; worldgen does
        // not promise the neighbour updates used by a player-placed fence.
        state=state.with(net.minecraft.state.property.Properties.NORTH,railConnects(material(seed,x,y,z-1)));
        state=state.with(net.minecraft.state.property.Properties.SOUTH,railConnects(material(seed,x,y,z+1)));
        state=state.with(net.minecraft.state.property.Properties.WEST,railConnects(material(seed,x-1,y,z)));
        return state.with(net.minecraft.state.property.Properties.EAST,railConnects(material(seed,x+1,y,z)));
    }
    private static boolean railConnects(int material){return material!=0&&material!=8;}
    private static double n(long seed,int salt,double a,double b,double scale){return SeaTerrain.noise(seed+salt,a,b,scale);}
    private static int floor(long seed,Region r,double a,double b){
        return switch(r){
            case FLOWER_HILLS -> flowerHeight(seed,a,b);
            case RED_PLAINS -> (int)Math.floor(156+10*n(seed,18111,a,b,430)+4*n(seed,18112,a,b,72)+1.5*n(seed,18113,a,b,19));
            case WINDING -> (int)Math.floor(144+8*n(seed,18121,a,b,180)+3*n(seed,18122,a,b,37));
            case DARK_FOREST -> (int)Math.floor(145+8*n(seed,18131,a,b,290)+2.5*n(seed,18132,a,b,46));
            case BRIDGES -> (int)Math.floor(36+18*n(seed,18141,a,b,180)+5*n(seed,18142,a,b,27));
            case RAINBOW_FOREST -> (int)Math.floor(146+14*n(seed,18151,a,b,270)+5*n(seed,18152,a,b,46));
            case SAND_LANDS -> (int)Math.floor(143+5*n(seed,18161,a,b,310)+1.8*n(seed,18162,a,b,65));
            case SCULK_FOREST -> (int)Math.floor(64+2*n(seed,18171,a,b,260)+n(seed,18172,a,b,47));
            default -> 255;
        };
    }
    /** Low continuous hill-and-dune relief; never isolated mountain knobs. */
    private static int flowerHeight(long seed,double a,double b){return (int)Math.floor(flowerElevation(seed,a,b));}
    private static double flowerElevation(long seed,double a,double b){
        double wa=a+22*n(seed,18107,a,b,155),wb=b+22*n(seed,18108,a,b,155);
        double rolling=n(seed,18101,wa,wb,112);
        double dunes=1-Math.abs(2*n(seed,18102,wa,wb,57));
        double detail=n(seed,18103,wa,wb,24);
        double relief=20+25*rolling+12*(dunes-.65)+2*detail;
        return 139+Math.max(0,Math.min(40,relief));
    }
    static double riverCenter(long seed,double a){
        // Nested bends change direction at several scales rather than tracing
        // one almost-straight stripe down tangent zero.
        double q=a-start(Region.SCULK_FOREST);
        return 168*Math.sin(q/179.0+.8)+83*Math.sin(q/397.0-1.1)
                +42*n(seed,18173,q,0,310);
    }
    static double riverWidth(long seed,double a){return 22+5*n(seed,18174,a,0,170);}
    /** Closest point on the curved centreline avoids stretched banks at tight bends. */
    static double riverDistance(long seed,double a,double b){
        double spring=start(Region.SCULK_FOREST)+192.0;
        double q=Math.max(a,spring);
        for(int i=0;i<5;i++){
            double c=riverCenter(seed,q),slope=(riverCenter(seed,q+1)-riverCenter(seed,q-1))*.5;
            double step=((q-a)+(c-b)*slope)/(1+slope*slope);
            q=Math.max(spring,Math.max(a-72,Math.min(a+72,q-Math.max(-18,Math.min(18,step)))));
        }
        return Math.hypot(q-a,riverCenter(seed,q)-b);
    }
    private static void riverRocks(Column c,long seed,double a,double b){
        int cell=(int)Math.floor(a/96);
        for(int ca=cell-1;ca<=cell+1;ca++)for(int side:new int[]{-1,1}){
            long hash=SeaTerrain.hash(seed+14107,ca,side);if(SeaTerrain.unit(hash)>.24)continue;
            int ra=ca*96+20+(int)(56*SeaTerrain.unit(SeaTerrain.hash(hash,1,0)));
            int rb=(int)Math.round(riverCenter(seed,ra)+side*(riverWidth(seed,ra)+4));
            if(Math.abs(a-ra)>11||Math.abs(b-rb)>11)continue;
            int turn=(int)(hash&3);
            for(var v:TerrainSamples138.riverRock(hash)){
                int dx=turn==0?v[0]:turn==1?-v[2]:turn==2?-v[0]:v[2],dz=turn==0?v[2]:turn==1?v[0]:turn==2?-v[2]:-v[0];
                if((int)a!=ra+dx||(int)b!=rb+dz)continue;
                int y=62+v[1];if(y>=c.ground&&y<80)c.blocks[y+64]=(byte)(v[1]>2?1:ANDESITE);
            }
        }
    }
    /** Conservative complete-crown footprint, including bends upstream/downstream.
     * Testing just the trunk or the river at the trunk's depth misses overhangs. */
    static boolean treeClearOfRiver(long seed,int x,int z,int extent){
        var p=FarlandsWorld.project(x,z);
        for(int offset=-extent;offset<=extent;offset++){
            double a=p.depth()+offset,center=riverCenter(seed,a);
            double slope=(riverCenter(seed,a+1)-riverCenter(seed,a-1))*.5;
            double bank=(38+7*n(seed,18174,a,0,170))*Math.sqrt(1+slope*slope);
            if(Math.abs(p.tangent()-center)<=extent+bank+2)return false;
        }
        return true;
    }
    private static void build(Column c,long seed,int x,int z){

        var p=FarlandsWorld.project(x,z);var region=atDepth(p.depth());
        c.hasWaves=region==Region.WINDING&&p.depth()>=windingStart();

        int distance=Integer.MAX_VALUE;
        for(int edge:new int[]{start(Region.RED_PLAINS),start(Region.SAND_LANDS)})
            distance=Math.min(distance,Math.abs(p.depth()-edge));
        if(distance>=128){buildRaw(c,seed,x,z,region);return;}
        // Finish this biome's own features before its straight palette boundary.

        // No neighbouring terrain generator or neighbouring palette is sampled.

        var work=BLEND.get();buildRaw(work.left,seed,x,z,region);

        int level=FarlandsMargins.floor(seed,p.depth(),p.tangent());

        int cap=region==Region.RED_PLAINS?CRIMSON:region==Region.FLOWER_HILLS?MOSS:region==Region.SCULK_FOREST?WARPED:region==Region.SAND_LANDS?RED_SAND:4;

        int soil=region==Region.RED_PLAINS||region==Region.SCULK_FOREST?NETHERRACK:region==Region.SAND_LANDS?RED_SANDSTONE:3;

        for(int i=0;i<384;i++)work.right.blocks[i]=(byte)(i-64>level?0:i-64==level?cap:i-64>level-3?soil:i<5?8:i==5?10:i<64?2:1);

        double t=1-SeaTerrain.smooth(Math.max(0,Math.min(1,distance/128.0)));

        TerrainMorph.blend(work.left.blocks,work.right.blocks,work.ld,work.rd,t,false,c.blocks);

        System.arraycopy(work.left.bridgeDirections,0,c.bridgeDirections,0,384);

        for(int i=0;i<384;i++)if((c.blocks[i]&255)==BRIDGE_STAIRS&&(c.bridgeDirections[i]<2||c.bridgeDirections[i]>5))c.bridgeDirections[i]=2;

        for(int i=383;i>=0;i--)if(TerrainMorph.solid(c.blocks[i]&255)&&(i==383||c.blocks[i+1]==0)){

            int old=c.blocks[i]&255;if(old==PLANKS||old==FENCE||old==BRIDGE_STAIRS||old==CHAIN)continue;

            c.blocks[i]=(byte)cap;

            for(int k=1;k<=2&&i-k>=0&&TerrainMorph.solid(c.blocks[i-k]&255);k++)c.blocks[i-k]=(byte)soil;

        }

        int expected=(int)Math.round(SeaTerrain.lerp(work.left.ground,level,t))+64,best=-1;

        for(int d=0;d<384&&best<0;d++)for(int sign:new int[]{-1,1}){

            int i=expected+sign*d;

            if(i>=0&&i<383&&TerrainMorph.solid(c.blocks[i]&255)&&!TerrainMorph.solid(c.blocks[i+1]&255)){best=i;break;}

        }

        c.ground=best<0?-65:best-64;

    }

    private static void buildRaw(Column c,long seed,int x,int z,Region r){
        java.util.Arrays.fill(c.blocks,(byte)0);var p=FarlandsWorld.project(x,z);
        double a=p.depth(),b=p.tangent();int ground=floor(seed,r,a,b);c.ground=ground;
        if(r==Region.CLASSIC){
            for(int y=-64;y<=255;y++)c.blocks[y+64]=(byte)PlainsFarlandsProfile.material(seed,a,y,b);
            skin(c,4,3);c.ground=highest(c);return;
        }
        if(r==Region.RED_PLAINS){
            double remaining=end(Region.RED_PLAINS)-a;
            boolean grass=remaining<32+26*n(seed,18115,a,b,28);
            fillRedPlainsSlab(c,seed,a,b,ground,grass);
            return;
        }
        if(r==Region.WINDING&&a<windingStart()){
            fillRedPlainsSlab(c,seed,a,b,floor(seed,Region.RED_PLAINS,a,b),true);
            return;
        }
        if(r==Region.BRIDGES){c.ground=SpokelandsBridges.fill(c.blocks,c.bridgeDirections,seed,a,b,Math.abs((long)x)>=Math.abs((long)z));return;}
        if(r==Region.SAND_LANDS){
            c.ground=SpokelandsSand.fill(c.blocks,seed,a,b,ground);finishSandCoast(c,seed,a,b,ground);return;
        }
        int base=ground;
        if(r==Region.SCULK_FOREST){
            double center=riverCenter(seed,a);
            double distance=riverDistance(seed,a,b),width=riverWidth(seed,a);
            double bank=Math.max(0,Math.min(1,(distance-width)/6.0));
            double relief=TerrainSamples138.sculkRelief(seed,(int)a,(int)b)*SeaTerrain.smooth(Math.max(0,Math.min(1,(a-start(Region.SCULK_FOREST))/256.0)));
            ground=(int)Math.round(SeaTerrain.lerp(63,Math.max(63,ground+relief),SeaTerrain.smooth(bank)));
            boolean wet=distance<width;
            if(wet)ground=60;
            long spot=SeaTerrain.hash(seed+18194,(int)Math.floor(a/8),(int)Math.floor(b/8));
            boolean light=SeaTerrain.unit(SeaTerrain.hash(spot,0,0))<.4
                    &&Math.floorMod((int)Math.floor(a),8)==1+(int)(6*SeaTerrain.unit(SeaTerrain.hash(spot,1,0)))
                    &&Math.floorMod((int)Math.floor(b),8)==1+(int)(6*SeaTerrain.unit(SeaTerrain.hash(spot,0,1)));
            int bed=light?FROGLIGHT:riverBed(seed,a,b);
            for(int y=-64;y<=ground;y++){
                // The luminous roots descend into broad natural caverns beneath the
                // forest. The riverbed and a substantial roof remain supported.
                double cavity=y>28&&y<ground-18 ? FarlandsNatural.gradient2(seed+18177,a/71,b/71)
                        +.48*Math.cos((y-75)/29.0)+.28*FarlandsNatural.gradient2(seed+18178,(a+y*.4)/32,b/33):-10;
                if(cavity>.34)continue;
                c.blocks[y+64]=(byte)(y<=-60?8:y==-59?10:y==ground?(wet?bed:(TerrainSamples138.warpedMud(seed,(int)a,(int)b)&&FarlandsNatural.gradient2(seed+14401,a/13,b/13)>.12?MUD:WARPED)):y>ground-3?NETHERRACK:y<0?2:((n(seed,18179,a+y*.2,b,19)>.12)?ANDESITE:1));
            }
            if(wet)for(int y=ground+1;y<=62;y++)c.blocks[y+64]=8;
            c.ground=ground;riverRocks(c,seed,a,b);return;
        }
        double slope=r==Region.FLOWER_HILLS?Math.max(Math.abs(flowerElevation(seed,a+1,b)-flowerElevation(seed,a-1,b)),Math.abs(flowerElevation(seed,a,b+1)-flowerElevation(seed,a,b-1)))*.5:0;
        double cliffPatch=r==Region.FLOWER_HILLS?n(seed,18109,a,b,29):0;
        boolean cliff=r==Region.FLOWER_HILLS&&slope>.32&&cliffPatch>-.05;
        int soilCeiling=ground;
        if(r==Region.FLOWER_HILLS)soilCeiling=Math.min(Math.min(flowerHeight(seed,a-1,b),flowerHeight(seed,a+1,b)),Math.min(flowerHeight(seed,a,b-1),flowerHeight(seed,a,b+1)));
        for(int y=-64;y<=ground;y++){
            double mineral=n(seed,18110,a+y*.23,b,11),moss=n(seed,18108,a+y*.4,b,17);
            int rock=r==Region.FLOWER_HILLS?(moss>.12?MOSSY_ROCK:mineral>.23?ANDESITE:mineral<-.14?SMOOTH_ROCK:1):1;
            c.blocks[y+64]=(byte)(y<=-60?8:y==-59?10:y==ground?(r==Region.FLOWER_HILLS?MOSS:4):r==Region.FLOWER_HILLS&&y>soilCeiling?(cliff?rock:MOSS):y>ground-3?3:y<0?2:rock);
        }

    }

    private static void fillRedPlainsSlab(Column c,long seed,double a,double b,int ground,boolean grass){
        int bottom=ground-22-(int)(9*(1+n(seed,18114,a,b,160)));
        for(int y=bottom;y<=ground;y++){
            c.blocks[y+64]=(byte)(grass?(y==ground?4:y>ground-3?3:1):y==ground?CRIMSON:NETHERRACK);
        }
        c.ground=ground;
    }
    /** Finish sand relief before the straight palette boundary, at the forest's existing elevation. */
    private static void finishSandCoast(Column c,long seed,double a,double b,int originalFloor){
        double distance=end(Region.SAND_LANDS)-a;
        if(distance>=1024)return;
        double strength=SeaTerrain.smooth(Math.max(0,distance/1024.0));
        double edge=end(Region.SAND_LANDS);
        int dry=Math.max(63,floor(seed,Region.SCULK_FOREST,edge,b));
        double riverGap=riverDistance(seed,edge,b)-riverWidth(seed,edge);
        double bank=Math.max(0,Math.min(1,riverGap/6.0));
        int seam=(int)Math.round(SeaTerrain.lerp(60,dry,SeaTerrain.smooth(bank)));
        int base=(int)Math.round(SeaTerrain.lerp(seam,originalFloor,strength));
        int walk=(int)Math.round(SeaTerrain.lerp(seam,c.ground,strength));
        System.arraycopy(c.blocks,0,c.sandSource,0,384);
        for(int y=-64;y<320;y++){
            int value=0;
            if(y<=walk)value=y<=-60?8:y==-59?10:y==walk?RED_SAND:RED_SANDSTONE;
            else if(strength>.0001){
                int source=originalFloor+(int)Math.round((y-base)/strength);
                if(source>=-64&&source<320)value=c.sandSource[source+64]&255;
            }
            // The shallow river enters through sand rather than ending at a dry cliff.
            // The river begins wholly within the forest; sand contains no river water.
            c.blocks[y+64]=(byte)value;
        }
        for(int i=383;i>0;i--)if(TerrainMorph.solid(c.blocks[i]&255)&&(i==383||!TerrainMorph.solid(c.blocks[i+1]&255)))c.blocks[i]=(byte)(TerrainMorph.solid(c.blocks[i-1]&255)?RED_SAND:RED_SANDSTONE);
        c.ground=walk;
    }
    private static int highest(Column c){for(int y=319;y>=-64;y--)if(c.blocks[y+64]!=0&&c.blocks[y+64]!=8)return y;return -65;}
    private static void skin(Column c,int surface,int soil){
        for(int y=319;y>=-64;y--)if(c.blocks[y+64]!=0){
            if(y==319||c.blocks[y+65]==0){c.blocks[y+64]=(byte)surface;for(int k=1;k<=3&&y-k>=-64&&c.blocks[y-k+64]!=0;k++)c.blocks[y-k+64]=(byte)soil;}
        }
    }
}
