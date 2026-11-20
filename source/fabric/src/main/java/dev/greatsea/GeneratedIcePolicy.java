package dev.greatsea;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.WorldChunk;

/** Removes generated ice without changing snow, placed blocks, or ordinary world presets. */
public final class GeneratedIcePolicy {
    public enum Replacement { KEEP, AIR, WATER, STONE, DEEPSLATE }
    private GeneratedIcePolicy() {}
    public static boolean isIce(BlockState state) {
        return state.isOf(Blocks.ICE) || state.isOf(Blocks.PACKED_ICE) ||
                state.isOf(Blocks.BLUE_ICE) || state.isOf(Blocks.FROSTED_ICE);
    }
    public static boolean isIce(String id) {
        return switch(id) {
            case "minecraft:ice","minecraft:packed_ice","minecraft:blue_ice","minecraft:frosted_ice" -> true;
            default -> false;
        };
    }
    /** Pure policy boundary used to verify generation scope and ocean/land replacement semantics. */
    public static Replacement replacement(String id,boolean unstable,boolean generation,boolean ocean,boolean water,int y,int seaLevel) {
        if (!unstable || !generation || !isIce(id)) return Replacement.KEEP;
        if (ocean) return y<seaLevel ? Replacement.WATER : Replacement.AIR;
        if (water) return Replacement.WATER;
        return y<0 ? Replacement.DEEPSLATE : Replacement.STONE;
    }
    public static boolean ocean(RegistryEntry<Biome> biome) {
        return biome.matchesKey(BiomeKeys.WARM_OCEAN) || biome.matchesKey(BiomeKeys.LUKEWARM_OCEAN) ||
                biome.matchesKey(BiomeKeys.DEEP_LUKEWARM_OCEAN) || biome.matchesKey(BiomeKeys.OCEAN) ||
                biome.matchesKey(BiomeKeys.DEEP_OCEAN) || biome.matchesKey(BiomeKeys.COLD_OCEAN) ||
                biome.matchesKey(BiomeKeys.DEEP_COLD_OCEAN) || biome.matchesKey(BiomeKeys.FROZEN_OCEAN) ||
                biome.matchesKey(BiomeKeys.DEEP_FROZEN_OCEAN);
    }
    public static boolean supported(WorldView view) {
        ServerWorld world=view instanceof ServerWorld server ? server : view instanceof ChunkRegion region ? region.toServerWorld() : null;
        return world!=null && UnstableMobSpawns.supported(world);
    }
    /** Convenience for custom land samples: ice keeps its terrain volume as ordinary rock. */
    public static BlockState generated(BlockState state,boolean underwater,int y) {
        if (!isIce(state)) return state;
        return underwater ? Blocks.WATER.getDefaultState() : (y<0 ? Blocks.DEEPSLATE : Blocks.STONE).getDefaultState();
    }
    public static BlockState generated(BlockState state,boolean ocean,boolean water,int y,int seaLevel) {
        if (!isIce(state)) return state;
        if (ocean) return (y<seaLevel ? Blocks.WATER : Blocks.AIR).getDefaultState();
        return generated(state,water,y);
    }
    /** Feature writes are evaluated against their region, never a not-yet-registered server chunk. */
    public static BlockState generated(ChunkRegion region,BlockPos pos,BlockState state) {
        if(MistRegion.active(region.toServerWorld())&&MistRegion.frost(region.getSeed(),pos.getX(),pos.getZ()))return MistTerrain.frozen(state);
        if (!isIce(state) || !supported(region) || !region.isValidForSetBlock(pos)) return state;
        ServerWorld world=region.toServerWorld();
        var chunk=region.getChunk(pos.getX()>>4,pos.getZ()>>4);
        boolean ocean=ocean(chunk.getBiomeForNoiseGen(pos.getX()>>2,pos.getY()>>2,pos.getZ()>>2));
        int seaLevel=world.getRegistryKey().equals(World.OVERWORLD) && SeaTerrain.contains(GreatSea.CONFIG,pos.getX(),pos.getZ()) ? GreatSea.CONFIG.seaLevel : region.getSeaLevel();
        return generated(state,ocean,region.getFluidState(pos).isIn(FluidTags.WATER),pos.getY(),seaLevel);
    }
    /** Fresh chunk conversion also catches native density surfaces and imported sample voxels. */
    public static void cleanGenerated(ServerWorld world,WorldChunk chunk) {
        if (!UnstableMobSpawns.supported(world)) return;
        var sections=chunk.getSectionArray();var pos=new BlockPos.Mutable();
        for (int index=0;index<sections.length;index++) {
            var section=sections[index];if (!section.hasAny(GeneratedIcePolicy::isIce)) continue;
            int bottom=chunk.getBottomY()+index*16;
            for (int y=0;y<16;y++) for (int z=0;z<16;z++) for (int x=0;x<16;x++) {
                var state=section.getBlockState(x,y,z);if (!isIce(state)) continue;
                int bx=chunk.getPos().getStartX()+x,bz=chunk.getPos().getStartZ()+z,by=bottom+y;
                pos.set(bx,by,bz);
                if(MistRegion.active(world)&&MistRegion.contains(bx,bz))continue;
                boolean ocean=ocean(chunk.getBiomeForNoiseGen(bx>>2,by>>2,bz>>2));
                boolean water=by>chunk.getBottomY() && chunk.getFluidState(pos.down()).isIn(FluidTags.WATER);
                int seaLevel=world.getRegistryKey().equals(World.OVERWORLD) && SeaTerrain.contains(GreatSea.CONFIG,bx,bz) ? GreatSea.CONFIG.seaLevel : world.getSeaLevel();
                chunk.setBlockState(pos,generated(state,ocean,water,by,seaLevel),0);
            }
        }
    }
}
