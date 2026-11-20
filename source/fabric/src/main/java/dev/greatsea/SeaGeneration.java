package dev.greatsea;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WrapperProtoChunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import java.util.EnumSet;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public final class SeaGeneration {
    private static final Map<Chunk,boolean[]> PRESERVED=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Chunk,int[]> RETAINED_CEILINGS=Collections.synchronizedMap(new WeakHashMap<>());
    private record ShoreSurface(int[] originalTop,int[] sandTop) {}
    private static final Map<Chunk,ShoreSurface> SHORE_SURFACES=Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> NATIVE_QUERY=ThreadLocal.withInitial(()->false);
    private static final java.util.concurrent.ConcurrentHashMap<Long,net.minecraft.util.math.random.RandomSplitter> DEEPSLATE=new java.util.concurrent.ConcurrentHashMap<>();
    public static void registerSurface(long seed,net.minecraft.world.gen.noise.NoiseConfig noise){
        DEEPSLATE.putIfAbsent(seed,noise.getOrCreateRandomDeriver(net.minecraft.util.Identifier.ofVanilla("deepslate")));
    }
    private SeaGeneration() {}
    public static boolean supported(ChunkGenerator generator) {
        return !NATIVE_QUERY.get() && GreatSea.CONFIG.enabled && UnstableWorldGen.isUnstable(generator);
    }
    public static boolean intersects(Chunk chunk) {
        return SeaTerrain.intersects(GreatSea.CONFIG, chunk.getPos().getStartX(),chunk.getPos().getStartZ());
    }
    public static boolean needsVanilla(Chunk chunk) {
        int x=chunk.getPos().getStartX(),z=chunk.getPos().getStartZ();
        for (int dx=0;dx<16;dx++) for (int dz=0;dz<16;dz++)
            if (!SeaTerrain.contains(GreatSea.CONFIG,x+dx,z+dz) ||
                    SeaTerrain.shoreDistance(GreatSea.CONFIG,x+dx,z+dz)<GreatSea.CONFIG.beachWidth+GreatSea.CONFIG.coastWaviness) return true;
        return false;
    }
    /** Vanilla terrain can survive only below the inland preservation ceiling. */
    public static boolean needsVanilla(Chunk chunk,long seed){
        if(!needsVanilla(chunk))return false;
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();var c=GreatSea.CONFIG;
        for(int z=sz;z<sz+16;z++)for(int x=sx;x<sx+16;x++){
            if(!SeaTerrain.contains(c,x,z))return true;
            double d=SeaTerrain.shoreDistance(c,seed,x,z);
            if(d<c.beachWidth&&SeaTerrain.height(c,seed,x,z,64)<c.mainlandPreserveTopY)return true;
        }
        return false;
    }
    public static int height(long seed,int x,int z) {
        return (int)Math.round(SeaTerrain.height(GreatSea.CONFIG,seed,x,z,64));
    }
    public static <T> T nativeQuery(Supplier<T> query) {
        boolean previous=NATIVE_QUERY.get();NATIVE_QUERY.set(true);
        try { return query.get(); } finally { NATIVE_QUERY.set(previous); }
    }
    /** Native grass/soil/rock painting uses native climate instead of the beach stored in the chunk. */
    public static net.minecraft.world.biome.source.BiomeAccess nativeSurfaceBiomes(
            net.minecraft.world.biome.source.BiomeAccess access,
            net.minecraft.world.biome.source.BiomeSource source,
            net.minecraft.world.biome.source.util.MultiNoiseUtil.MultiNoiseSampler sampler) {
        return access.withSource((x,y,z)->SeaBiomeContext.nativeBiomeQuery(()->source.getBiome(x,y,z,sampler)));
    }
    public static boolean preservesMainland(Chunk chunk,int x,int z) {
        if (chunk instanceof WrapperProtoChunk wrapper) chunk=wrapper.getWrappedChunk();
        boolean[] columns=PRESERVED.get(chunk);
        if (columns!=null) return columns[(z&15)*16+(x&15)];
        // Saved chunks have no transient generation metadata. Reconstruct the same decision
        // from their retained surface, excluding tree canopies, when new neighbours generate.
        if (chunk instanceof WorldChunk live && live.getWorld() instanceof ServerWorld world) {
            long seed=world.getSeed();
            if (!SeaTerrain.contains(GreatSea.CONFIG,x,z) || SeaTerrain.shoreDistance(GreatSea.CONFIG,seed,x,z)>=GreatSea.CONFIG.beachWidth) return false;
            int sandTop=(int)Math.round(SeaTerrain.height(GreatSea.CONFIG,seed,x,z,64));
            int y=live.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR,x&15,z&15);
            BlockPos.Mutable p=new BlockPos.Mutable();
            for (;y>sandTop;y--) {
                BlockState state=live.getBlockState(p.set(x,y,z));
                if (state.isSolid() && !state.isIn(BlockTags.LOGS) && !state.isIn(BlockTags.LEAVES)) return true;
            }
        }
        return false;
    }
    public static void copyPreserved(Chunk source,Chunk destination) {
        boolean[] columns=PRESERVED.get(source);
        if (columns!=null) PRESERVED.put(destination,columns);
        int[] ceilings=RETAINED_CEILINGS.get(source);
        if (ceilings!=null) RETAINED_CEILINGS.put(destination,ceilings);
    }
    public static int retainedCeiling(Chunk chunk,int x,int z) {
        if (chunk instanceof WrapperProtoChunk wrapper) chunk=wrapper.getWrappedChunk();
        int[] ceilings=RETAINED_CEILINGS.get(chunk);
        if (ceilings!=null) return ceilings[(z&15)*16+(x&15)];
        // Reloaded chunks already contain their tapered surface. Exclude tree canopies
        // when limiting native feature writes onto newly generated neighbours.
        BlockPos.Mutable pos=new BlockPos.Mutable();
        for (int y=chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR,x&15,z&15);y>GreatSea.CONFIG.mainlandPreserveTopY;y--) {
            BlockState state=chunk.getBlockState(pos.set(x,y,z));
            if (state.isSolid() && !state.isIn(BlockTags.LOGS) && !state.isIn(BlockTags.LEAVES)) return y;
        }
        return GreatSea.CONFIG.mainlandPreserveTopY;
    }
    public static boolean fullyManaged(Chunk chunk) {
        int x=chunk.getPos().getStartX(),z=chunk.getPos().getStartZ();
        for (int dx:new int[]{0,15}) for (int dz:new int[]{0,15})
            if (!SeaTerrain.contains(GreatSea.CONFIG,x+dx,z+dz)) return false;
        return true;
    }
    public static BlockState state(long seed,int x,int y,int z,int top,int bottom) {
        SeaConfig c=GreatSea.CONFIG;
        if (y<=bottom+(int)(SeaTerrain.unit(SeaTerrain.hash(seed,x,z))*4)) return Blocks.BEDROCK.getDefaultState();
        if (y>top) return y<c.seaLevel ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
        if (y>top-8) return Blocks.SAND.getDefaultState();
        if (y>top-12) return Blocks.SANDSTONE.getDefaultState();
        return (y<0 ? Blocks.DEEPSLATE : Blocks.STONE).getDefaultState();
    }
    public static Chunk fill(Chunk chunk,long seed,boolean blendVanilla) {
        SeaConfig c=GreatSea.CONFIG;
        BlockPos.Mutable pos=new BlockPos.Mutable();
        int startX=chunk.getPos().getStartX(), startZ=chunk.getPos().getStartZ();
        int bottom=chunk.getBottomY(), end=bottom+chunk.getHeight();
        // Only fresh, wholly managed chunks can bypass per-block heightmap updates.
        // Section setters still maintain palette, block and fluid counts normally.
        boolean direct=!blendVanilla&&fullyManaged(chunk);
        if(direct)for(var section:chunk.getSectionArray())if(!section.isEmpty()){direct=false;break;}
        // Existing heightmaps are ready on vanilla futures; fresh chunks need initial maps.
        if(!direct)Heightmap.populateHeightmaps(chunk,EnumSet.of(Heightmap.Type.OCEAN_FLOOR_WG,Heightmap.Type.WORLD_SURFACE_WG));
        // Newly bypassed vanilla chunks still need its exact Y0..8 stone transition.
        var deepslate=direct&&needsVanilla(chunk)?DEEPSLATE.get(seed):null;
        boolean[] preserved=new boolean[256];
        int[] originalTop=new int[256],sandTop=new int[256],ceilings=new int[256];
        for (int dz=0;dz<16;dz++) for (int dx=0;dx<16;dx++) {
            int x=startX+dx,z=startZ+dz;
            if (!SeaTerrain.contains(c,x,z)) continue;
            int old=blendVanilla ? chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR_WG,dx,dz) : 64;
            double distance=SeaTerrain.shoreDistance(c,seed,x,z);
            double floor=distance<c.beachWidth+c.underwaterSlopeWidth ? SeaTerrain.floorBaseline(c) : VanillaOceanTerrain.floorHeight(c,seed,x,z);
            double h=SeaTerrain.height(c,seed,x,z,old,floor);
            int top=(int)Math.round(h);
            boolean beach=distance<c.beachWidth;
            boolean ocean=distance>=c.beachWidth+c.underwaterSlopeWidth;
            boolean gravel=ocean && OceanFloorMaterials.gravel(c,seed,x,z);
            int index=dz*16+dx;
            originalTop[index]=beach ? old : Integer.MIN_VALUE;sandTop[index]=top;
            ceilings[index]=beach && blendVanilla ? (int)Math.round(MainlandJoin.ceiling(c,old,h)) : top;
            int limit=direct?Math.min(end,Math.max(c.seaLevel,top+1)):end;
            for (int y=bottom;y<limit;y++) {
                // Let native surface rules dress the original hill before cutting it.
                // Final clipping and copying its topsoil happen after buildSurface.
                if (beach && blendVanilla && y>top) {
                    if (y<=ceilings[index] && chunk.getBlockState(pos.set(x,y,z)).isSolid()) preserved[index]=true;
                    continue;
                }
                BlockState block=beach && y>top ? Blocks.AIR.getDefaultState() :
                        ocean ? VanillaOceanTerrain.state(c,seed,x,y,z,top,bottom,gravel) : state(seed,x,y,z,top,bottom);
                if(deepslate!=null&&y>=0&&y<8&&block.isOf(Blocks.STONE)&&
                        (y==0||deepslate.split(x,y,z).nextFloat()<1-y/8.0))block=Blocks.DEEPSLATE.getDefaultState();
                if(direct){
                    if(!block.isAir())chunk.getSection(chunk.getSectionIndex(y)).setBlockState(dx,y&15,dz,block);
                }else if (!chunk.getBlockState(pos.set(x,y,z)).equals(block)) chunk.setBlockState(pos,block,0);
            }
        }
        if(direct){
            Heightmap.populateHeightmaps(chunk,EnumSet.of(Heightmap.Type.OCEAN_FLOOR_WG,Heightmap.Type.WORLD_SURFACE_WG));
            chunk.markNeedsSaving();
        }
        if (blendVanilla) {
            PRESERVED.put(chunk,preserved);
            RETAINED_CEILINGS.put(chunk,ceilings);
            SHORE_SURFACES.put(chunk,new ShoreSurface(originalTop,sandTop));
        }
        return chunk;
    }
    public static void finishShoreSurface(Chunk chunk) {
        SeaConfig c=GreatSea.CONFIG;
        ShoreSurface shore=SHORE_SURFACES.remove(chunk);
        BlockPos.Mutable p=new BlockPos.Mutable();
        for (int dz=0;dz<16;dz++) for (int dx=0;dx<16;dx++) {
            int x=chunk.getPos().getStartX()+dx,z=chunk.getPos().getStartZ()+dz;
            if (!SeaTerrain.contains(c,x,z)) continue;
            int index=dz*16+dx;
            // This finishing pass is for dry sand and retained hills only. Edge chunks
            // also include submerged columns; never repaint their ocean-floor materials.
            if (shore==null || shore.originalTop()[index]==Integer.MIN_VALUE) continue;
            boolean retained=preservesMainland(chunk,x,z);
            if (shore!=null && shore.originalTop()[index]!=Integer.MIN_VALUE) {
                int profile=shore.sandTop()[index],original=shore.originalTop()[index];
                int ceiling=retainedCeiling(chunk,x,z);
                // The native grass/soil/rock skin was generated at the original height.
                // Carry it onto a lowered hill so a cut does not expose bare subsoil.
                BlockState[] skin=new BlockState[4];
                for (int i=0;i<skin.length;i++) skin[i]=chunk.getBlockState(p.set(x,original-i,z));
                int exposed=Math.min(ceiling,original);
                while (exposed>profile && !chunk.getBlockState(p.set(x,exposed,z)).isSolid()) exposed--;
                for (int y=Math.max(profile,ceiling)+1;y<chunk.getBottomY()+chunk.getHeight();y++)
                    if (!chunk.getBlockState(p.set(x,y,z)).isAir()) chunk.setBlockState(p,Blocks.AIR.getDefaultState(),0);
                if (retained && exposed>profile && exposed<original) {
                    for (int i=0;i<skin.length && exposed-i>profile;i++) {
                        if (skin[i].isSolid()) chunk.setBlockState(p.set(x,exposed-i,z),skin[i],0);
                    }
                }
            }
            if (retained) continue;
            int top=chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR_WG,dx,dz);
            for (int i=0;i<12;i++) chunk.setBlockState(p.set(x,top-i,z),
                    (i<8 ? Blocks.SAND : Blocks.SANDSTONE).getDefaultState(),0);
        }
    }
}
