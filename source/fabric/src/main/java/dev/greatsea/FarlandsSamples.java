package dev.greatsea;

import net.minecraft.block.BlockState;
import java.util.ArrayList;

/** Reference volumes sampled without distorting tree trunks or small repeated tiles. */
final class FarlandsSamples {
    static final FarlandsChunkTemplate CLASSIC=FarlandsChunkTemplate.load("classic-volume");
    // Visiting one region should not decode every other region's reference volume.
    private static final class Repeating {static final FarlandsChunkTemplate SAMPLE=FarlandsChunkTemplate.load("repeat-oxums");}
    private static final class Floating {static final FarlandsChunkTemplate SAMPLE=FarlandsChunkTemplate.load("floating-volume");}
    private static final class Cherry {static final FarlandsChunkTemplate SAMPLE=FarlandsChunkTemplate.load("cherry-volume");}
    private static final class Mosaic {static final FarlandsChunkTemplate[][] GROUPS=mosaics();}
    private static FarlandsChunkTemplate[][] mosaics() {
        var groups=new ArrayList<ArrayList<FarlandsChunkTemplate>>();
        for(int i=0;i<8;i++)groups.add(new ArrayList<>());
        for(int i=0;i<64;i++){var tile=FarlandsChunkTemplate.load("mosaic-"+i);groups.get(tile.biome).add(tile);}
        var result=new FarlandsChunkTemplate[8][];
        for(int i=0;i<8;i++) {
            if(groups.get(i).isEmpty())groups.get(i).add(FarlandsChunkTemplate.load("chunk-"+i));
            result[i]=groups.get(i).toArray(FarlandsChunkTemplate[]::new);
        }
        return result;
    }
    private static int mirror(int n,int size) {int p=Math.floorMod(n,2*size);return p<size?p:2*size-1-p;}
    static int repeatingTangent(int tangent){return mirror(tangent+Repeating.SAMPLE.depth/2,Repeating.SAMPLE.depth);}
    static int repeatingDepth(){return Repeating.SAMPLE.depth;}
    private static final class Column {
        long seed=Long.MIN_VALUE;int x=Integer.MIN_VALUE,z,a,b,target,sourceGround;FarlandsChunkTemplate template;FarlandsTerrain.Stage stage;
        int y(int y) {
            if(stage!=FarlandsTerrain.Stage.CHUNKS && stage!=FarlandsTerrain.Stage.DECAY)return stage==FarlandsTerrain.Stage.CHERRY?y+18-target:y;
            int surface=sourceGround+target;
            return y>surface-5 ? y-target:(int)Math.round(-64+(y+59)*(sourceGround+59.0)/(surface+54.0));
        }
    }
    private static final ThreadLocal<Column> CACHE=ThreadLocal.withInitial(Column::new);
    private static Column column(long seed,int x,int z) {
        Column c=CACHE.get();if(c.seed==seed && c.x==x && c.z==z)return c;
        c.seed=seed;c.x=x;c.z=z;
        var point=FarlandsWorld.project(x,z);c.stage=FarlandsTerrain.stage(x,z);
        int depth=point.depth(),tangent=point.tangent();
        c.target=c.stage==FarlandsTerrain.Stage.CHERRY?cherryLift(seed,depth,tangent):0;
        c.template=switch(c.stage) {
            case PLAINS,CORRUPTED,SAND_FARLANDS -> CLASSIC;
            case REPEATING -> Repeating.SAMPLE;
            case FLOATING -> Floating.SAMPLE;
            case CHERRY -> Cherry.SAMPLE;
            case DECAY -> {
                long h=SeaTerrain.hash(seed+860,Math.floorDiv(tangent,16),0);
                var group=Mosaic.GROUPS[(int)(h&7)];var tile=group[(int)Math.floorMod(h>>>8,group.length)];
                c.sourceGround=tile.medianGround;
                int target=SeaTerrain.unit(SeaTerrain.hash(h,8,1))<.38?60+(int)(SeaTerrain.unit(h)*35):235+(int)(SeaTerrain.unit(h)*57);
                c.target=target-c.sourceGround;
                yield tile;
            }
            case CHUNKS -> {
                var group=Mosaic.GROUPS[FarlandsTerrain.chunkBiome(seed,x,z)&7];
                long h=SeaTerrain.hash(seed+824,Math.floorDiv(x,16),Math.floorDiv(z,16));
                var tile=group[(int)Math.floorMod(h,group.length)];
                c.sourceGround=tile.medianGround;
                c.target=40+(int)(SeaTerrain.unit(SeaTerrain.hash(h,17,9))*205)-c.sourceGround;
                yield tile;
            }
            default -> throw new IllegalArgumentException("Stage has no reference volume");
        };
        if(c.stage==FarlandsTerrain.Stage.CHUNKS){c.a=x;c.b=z;}
        else if(c.stage==FarlandsTerrain.Stage.REPEATING || c.stage==FarlandsTerrain.Stage.DECAY) {
            // Oxums repeats every 16 travel blocks. Continue only its verified
            // tall interior across the tangent; downloaded crop edges are unknown.
            c.a=Math.floorMod(depth,16);c.b=c.stage==FarlandsTerrain.Stage.REPEATING ? repeatingTangent(tangent):Math.floorMod(tangent,16);
        } else {
            c.a=mirror(depth+(int)(SeaTerrain.hash(seed,9,3)&127),c.template.width);
            c.b=mirror(tangent+(int)(SeaTerrain.hash(seed,3,9)&127),c.template.depth);
        }
        return c;
    }
    static int cherrySourceGround(long seed,int depth,int tangent){
        var template=Cherry.SAMPLE;
        int a=mirror(depth+(int)(SeaTerrain.hash(seed,9,3)&127),template.width);
        int b=mirror(tangent+(int)(SeaTerrain.hash(seed,3,9)&127),template.depth);
        return template.ground(a,b);
    }
    static int cherryLift(long seed,int depth,int tangent){
        // Raise entire existing terraces together; never slope their grassy interiors.
        return Math.max(0,Math.min(34,(int)Math.round((cherrySourceGround(seed,depth,tangent)-50)*.8)));
    }
    static int material(long seed,int x,int y,int z) {if(FarlandsTerrain.stage(x,z)==FarlandsTerrain.Stage.CHERRY){var p=FarlandsWorld.project(x,z);return TerrainSamples138.cherryMaterial(seed,p.depth(),y,p.tangent());}var c=column(seed,x,z);int m=c.template.material(c.a,c.y(y),c.b);
        if((c.stage==FarlandsTerrain.Stage.CHERRY || c.stage==FarlandsTerrain.Stage.DECAY) && (m==14 || m==15))return 0;
        // Petals belong to newly generated groves, not the mirrored reference tile.
        if(c.stage==FarlandsTerrain.Stage.CHERRY && m==27)return 0;
        return c.stage==FarlandsTerrain.Stage.DECAY && y<c.sourceGround+c.target-5 && y>-60 && m==0?1:m;}

    static BlockState state(long seed,int x,int y,int z) {if(FarlandsTerrain.stage(x,z)==FarlandsTerrain.Stage.CHERRY){int m=material(seed,x,y,z);return switch(m){case 1 -> net.minecraft.block.Blocks.STONE.getDefaultState();case 2 -> net.minecraft.block.Blocks.DEEPSLATE.getDefaultState();case 3 -> net.minecraft.block.Blocks.DIRT.getDefaultState();case 4 -> net.minecraft.block.Blocks.GRASS_BLOCK.getDefaultState();default -> net.minecraft.block.Blocks.AIR.getDefaultState();};}var c=column(seed,x,z);var state=c.template.state(c.a,c.y(y),c.b);
        int material=c.template.material(c.a,c.y(y),c.b);
        if((c.stage==FarlandsTerrain.Stage.CHERRY || c.stage==FarlandsTerrain.Stage.DECAY) && (material==14 || material==15))return net.minecraft.block.Blocks.AIR.getDefaultState();
        if(c.stage==FarlandsTerrain.Stage.CHERRY && material==27)return net.minecraft.block.Blocks.AIR.getDefaultState();
        return c.stage==FarlandsTerrain.Stage.DECAY && y<c.sourceGround+c.target-5 && y>-60 && state.isAir()?net.minecraft.block.Blocks.STONE.getDefaultState():state;}
    static int backdropMaterial(int x,int y,int z){var p=FarlandsWorld.project(x,z);return Repeating.SAMPLE.material(Math.floorMod(p.tangent()-FarlandsEmpire.NEAREST_TANGENT-1,16),y,repeatingTangent(p.depth()-FarlandsEmpire.origin()+FarlandsEmpire.tangentOrigin()));}
    static BlockState backdropState(int x,int y,int z){var p=FarlandsWorld.project(x,z);return Repeating.SAMPLE.state(Math.floorMod(p.tangent()-FarlandsEmpire.NEAREST_TANGENT-1,16),y,repeatingTangent(p.depth()-FarlandsEmpire.origin()+FarlandsEmpire.tangentOrigin()));}
    static int classic(long seed,int x,int y,int z) {return material(seed,x,y,z);}
    static boolean basalWater(FarlandsTerrain.Stage stage) {return stage==FarlandsTerrain.Stage.CHUNKS || stage==FarlandsTerrain.Stage.DECAY;}
}
