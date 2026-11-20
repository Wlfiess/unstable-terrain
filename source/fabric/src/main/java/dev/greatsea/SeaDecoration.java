package dev.greatsea;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.BiomeKeys;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SeaDecoration {
    private record Voxel(int x,int y,int z,BlockState state) {}
    private record Palm(List<Voxel> blocks,List<Voxel> roots) {}
    private static final class PalmTemplates { private static final List<Palm> PALMS=loadPalms(); }
    /** Compatibility hooks for the former coral pass; the restored plant pass writes its own chunk. */
    public static boolean inOceanVegetationPass() { return false; }
    public static boolean permitsOceanVegetation(StructureWorldAccess world,BlockPos pos,BlockState state) { return false; }
    private SeaDecoration() {}
    public static void decorate(Chunk chunk,StructureWorldAccess world) {
        long seed=world.getSeed();
        SeaConfig c=GreatSea.CONFIG;
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();
        BlockPos.Mutable pos=new BlockPos.Mutable();
        for (int dz=0;dz<16;dz++) for (int dx=0;dx<16;dx++) {
            int x=sx+dx,z=sz+dz;
            if (!SeaTerrain.contains(c,x,z)) continue;
            int top=chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR_WG,dx,dz);
            long h=SeaTerrain.hash(seed+93,x,z);
            BlockState bed=chunk.getBlockState(pos.set(x,top,z));
            double density=vegetationDensity(c,seed,x,z,top);
            if (density>0 && (bed.isOf(Blocks.SAND) || bed.isOf(Blocks.GRAVEL))) {
                var biome=chunk.getBiomeForNoiseGen(x>>2,0,z>>2);
                boolean cold=biome.matchesKey(BiomeKeys.COLD_OCEAN) || biome.matchesKey(BiomeKeys.DEEP_COLD_OCEAN) ||
                        biome.matchesKey(BiomeKeys.FROZEN_OCEAN) || biome.matchesKey(BiomeKeys.DEEP_FROZEN_OCEAN);
                // Irregular stands with moderately taller stalks and a few taller plants,
                // all measured from the local bed rather than an absolute Y level.
                double patch=.55+.45*(SeaTerrain.noise(seed+94,x,z,32)+1)*.5;
                double kelpChance=(cold ? .045 : .026)*density*patch;
                double grassChance=(cold ? .19 : .23)*density*patch;
                if (SeaTerrain.unit(h)<kelpChance) {
                    int length=kelpLength(c,cold,h);
                    length=Math.min(length,c.seaLevel-top-3);
                    // Structures are placed first. Kelp may occupy only source water, so it
                    // cannot replace a chest, ruin block, ship hull or monument room.
                    int free=0;
                    while (free<length && sourceWater(chunk.getBlockState(pos.set(x,top+free+1,z)))) free++;
                    length=free;
                    for (int dy=1;dy<=length;dy++) chunk.setBlockState(pos.set(x,top+dy,z),
                            dy==length ? Blocks.KELP.getDefaultState().with(net.minecraft.block.KelpBlock.AGE,25) : Blocks.KELP_PLANT.getDefaultState(),0);
                } else if (SeaTerrain.unit(h)<kelpChance+grassChance && sourceWater(chunk.getBlockState(pos.set(x,top+1,z))))
                    chunk.setBlockState(pos,Blocks.SEAGRASS.getDefaultState(),0);
            }
        }
        // Every intersecting chunk reconstructs the same random spatial candidates.
        // Each chunk writes only its own voxels, keeping trees complete across chunk borders.
        grid(chunk,seed+201,c.deadBushSpacing,0,(x,z,h) -> {
            if (x<sx || x>=sx+16 || z<sz || z>=sz+16) return;
            int top=landHeight(chunk,seed,x,z);
            if (top==Integer.MIN_VALUE || SeaTerrain.unit(h)>c.deadBushChance) return;
            if (x>=sx && x<sx+16 && z>=sz && z<sz+16 && chunk.getBlockState(pos.set(x,top,z)).isOf(Blocks.SAND) &&
                    chunk.getBlockState(pos.set(x,top+1,z)).isAir())
                chunk.setBlockState(pos.set(x,top+1,z),Blocks.DEAD_BUSH.getDefaultState(),0);
        });
        for (var candidate:PalmLayout.candidates(seed,sx-12,sz-12,sx+27,sz+27,c.palmSpacing)) {
            int x=candidate.x(),z=candidate.z();long h=candidate.hash();
            double distance=SeaTerrain.shoreDistance(c,seed,x,z);
            if (!SeaTerrain.contains(c,x,z) || distance<48 || distance>c.beachWidth-24 || SeaTerrain.unit(h)>c.palmChance) continue;
            Palm palm=PalmTemplates.PALMS.get(palmVariant(h,PalmTemplates.PALMS.size()));
            int rotation=(int)((h>>>5)&3);
            boolean intersects=false;
            for (Voxel v:palm.blocks) {
                int[] offset=rotate(v.x,v.z,rotation);
                int bx=x+offset[0],bz=z+offset[1];
                if (bx>=sx && bx<sx+16 && bz>=sz && bz<sz+16) { intersects=true;break; }
            }
            if (!intersects) continue;
            int base=Integer.MIN_VALUE;
            boolean invalidRoot=false;
            List<int[]> roots=new ArrayList<>();
            for (Voxel root:palm.roots) {
                int[] offset=rotate(root.x,root.z,rotation);
                int bx=x+offset[0],bz=z+offset[1];
                Chunk rootChunk=world.getChunk(bx>>4,bz>>4);
                int ground=SeaGeneration.preservesMainland(rootChunk,bx,bz) ? Integer.MIN_VALUE : sandHeight(rootChunk,bx,bz);
                if (ground==Integer.MIN_VALUE) { invalidRoot=true;break; }
                BlockState above=rootChunk.getBlockState(new BlockPos(bx,ground+1,bz));
                // Original rivers and shallow water above the buried embankment are retained.
                // Reject a wet/obstructed root before any chunk places part of this tree.
                if (!above.isAir() && !above.isOf(Blocks.DEAD_BUSH) && !above.isOf(Blocks.JUNGLE_WOOD)) {
                    invalidRoot=true;break;
                }
                roots.add(new int[]{bx,bz,ground}); base=Math.max(base,ground+1);
            }
            if (invalidRoot || base==Integer.MIN_VALUE || base>=chunk.getBottomY()+chunk.getHeight()-26) continue;
            // Every lowest trunk voxel gets a support down to its own actual sand height.
            for (int i=0;i<roots.size();i++) {
                int[] root=roots.get(i);
                if (root[0]<sx || root[0]>=sx+16 || root[1]<sz || root[1]>=sz+16) continue;
                BlockState rootState=palm.roots.get(i).state;
                for (int y=root[2]+1;y<base;y++) chunk.setBlockState(pos.set(root[0],y,root[1]),rootState,0);
            }
            for (Voxel v:palm.blocks) {
                int[] offset=rotate(v.x,v.z,rotation);
                int vx=offset[0],vz=offset[1];
                int bx=x+vx,bz=z+vz,by=base+v.y;
                if (bx<sx || bx>=sx+16 || bz<sz || bz>=sz+16 || by>=chunk.getBottomY()+chunk.getHeight() || !SeaTerrain.contains(c,bx,bz)) continue;
                BlockState state=v.state;
                if ((rotation&1)!=0 && state.contains(net.minecraft.state.property.Properties.AXIS)) {
                    Direction.Axis axis=state.get(net.minecraft.state.property.Properties.AXIS);
                    if (axis!=Direction.Axis.Y) state=state.with(net.minecraft.state.property.Properties.AXIS,axis==Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
                }
                BlockState existing=chunk.getBlockState(pos.set(bx,by,bz));
                if (existing.isAir() || existing.isOf(Blocks.DEAD_BUSH)) chunk.setBlockState(pos,state,0);
            }
        }
    }
    /** The underwater edge has a depth gate; the floor interior supports plants at any bed height. */
    public static double vegetationDensity(SeaConfig config,long seed,int x,int z,int groundY) {
        if (!SeaTerrain.contains(config,x,z)) return 0;
        double distance=SeaTerrain.shoreDistance(config,seed,x,z);
        if (distance<config.beachWidth) return 0;
        double depth=SeaTerrain.vegetationDensity(groundY);
        double floorDistance=distance-config.beachWidth-config.underwaterSlopeWidth;
        return floorDistance<=0 ? depth : Math.max(depth,SeaTerrain.smooth(floorDistance/64.0));
    }
    private static boolean sourceWater(BlockState state) {
        return state.isOf(Blocks.WATER) && state.get(net.minecraft.block.FluidBlock.LEVEL)==0;
    }
    static int kelpLength(SeaConfig config,boolean cold,long hash) {
        int max=cold ? config.coldKelpMaxHeight : config.kelpMaxHeight;
        double height=.2+.8*SeaTerrain.unit(SeaTerrain.hash(hash,7,9))*SeaTerrain.unit(SeaTerrain.hash(hash,13,5));
        if (!cold) return 2+(int)Math.round(height*(max-2));
        int previousMax=Math.max(2,(int)Math.round(max/1.2));
        int previousLength=2+(int)Math.round(height*(previousMax-2));
        return Math.min(max,(int)Math.round(previousLength*1.2));
    }
    static int palmVariant(long hash,int variantCount) {
        return (int)((hash>>>3)%variantCount);
    }
    private static int[] rotate(int x,int z,int rotation) {
        for (int r=0;r<rotation;r++) { int old=x;x=-z;z=old; }
        return new int[]{x,z};
    }
    private static int landHeight(Chunk chunk,long seed,int x,int z) {
        SeaConfig c=GreatSea.CONFIG;
        if (!SeaTerrain.contains(c,x,z)) return Integer.MIN_VALUE;
        if (SeaGeneration.preservesMainland(chunk,x,z)) return Integer.MIN_VALUE;
        double distance=SeaTerrain.shoreDistance(c,seed,x,z);
        if (distance<32 || distance>c.beachWidth-24) return Integer.MIN_VALUE;
        return sandHeight(chunk,x,z);
    }
    private static int sandHeight(Chunk chunk,int x,int z) {
        // Read the immutable sand surface, independent of decorations and chunk status.
        int start=chunk.getBottomY()+chunk.getHeight()-1;
        BlockPos.Mutable p=new BlockPos.Mutable();
        for (int y=start;y>=chunk.getBottomY();y--)
            if (chunk.getBlockState(p.set(x,y,z)).isOf(Blocks.SAND)) return y;
        return Integer.MIN_VALUE;
    }
    private interface Candidate { void place(int x,int z,long h); }
    private static void grid(Chunk chunk,long seed,int spacing,int margin,Candidate callback) {
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();
        for (int gz=Math.floorDiv(sz-margin,spacing);gz<=Math.floorDiv(sz+15+margin,spacing);gz++)
            for (int gx=Math.floorDiv(sx-margin,spacing);gx<=Math.floorDiv(sx+15+margin,spacing);gx++) {
                long h=SeaTerrain.hash(seed,gx,gz);
                int x=gx*spacing+(int)(SeaTerrain.unit(SeaTerrain.hash(h,1,0))*spacing);
                int z=gz*spacing+(int)(SeaTerrain.unit(SeaTerrain.hash(h,0,1))*spacing);
                callback.place(x,z,h);
            }
    }
    private static List<Palm> loadPalms() {
        List<Palm> trees=new ArrayList<>();
        try (var reader=new InputStreamReader(Objects.requireNonNull(SeaDecoration.class.getResourceAsStream("/assets/greatsea/palms.json")),StandardCharsets.UTF_8)) {
            for (var element:JsonParser.parseReader(reader).getAsJsonArray()) {
                JsonObject tree=element.getAsJsonObject();
                List<BlockState> palette=new ArrayList<>();
                for (var entry:tree.getAsJsonArray("palette")) {
                    JsonObject definition=entry.getAsJsonObject();
                    Block block=Registries.BLOCK.get(Identifier.of(definition.get("Name").getAsString()));
                    BlockState state=block.getDefaultState();
                    if (definition.has("Properties")) for (var property:definition.getAsJsonObject("Properties").entrySet())
                        state=applyProperty(state,property.getKey(),property.getValue().getAsString());
                    palette.add(state);
                }
                List<Voxel> voxels=new ArrayList<>();
                for (var entry:tree.getAsJsonArray("blocks")) {
                    JsonArray a=entry.getAsJsonArray();
                    voxels.add(new Voxel(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt(),palette.get(a.get(3).getAsInt())));
                }
                List<Voxel> roots=voxels.stream().filter(v -> v.y==0 && v.state.isOf(Blocks.JUNGLE_WOOD)).toList();
                if (roots.isEmpty()) throw new IllegalArgumentException("Palm has no ground roots");
                trees.add(new Palm(List.copyOf(voxels),roots));
            }
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
        return List.copyOf(trees);
    }
    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state,String name,String value) {
        @SuppressWarnings("unchecked") Property<T> property=(Property<T>)state.getBlock().getStateManager().getProperty(name);
        if (property==null) throw new IllegalArgumentException("Unknown palm block property "+name);
        T parsed=property.parse(value).orElseThrow();
        return state.with(property,parsed);
    }
}
