package dev.greatsea.mixin;

import dev.greatsea.*;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Allows real ocean pieces and retained mainland features while protecting sandy slopes. */
@Mixin(ChunkRegion.class)
public abstract class ChunkRegionMixin {
    @Inject(method="setBlockState",at=@At("HEAD"),cancellable=true)
    private void greatsea$protect(BlockPos pos,BlockState state,int flags,int maxUpdateDepth,CallbackInfoReturnable<Boolean> cir) {
        if (!SeaTerrain.contains(GreatSea.CONFIG,pos.getX(),pos.getZ())) return;
        var world=((ChunkRegion)(Object)this).toServerWorld();
        if (!world.getRegistryKey().equals(World.OVERWORLD) || !SeaGeneration.supported(world.getChunkManager().getChunkGenerator())) return;
        if (OceanStructures.inStructurePass() && SeaTerrain.deepFloor(GreatSea.CONFIG,world.getSeed(),pos.getX(),pos.getZ())) return;
        if (SeaDecoration.inOceanVegetationPass()) {
            if (!SeaDecoration.permitsOceanVegetation((ChunkRegion)(Object)this,pos,state)) cir.setReturnValue(false);
            return;
        }
        ChunkRegion region=(ChunkRegion)(Object)this;
        if (!region.isValidForSetBlock(pos)) { cir.setReturnValue(false); return; }
        if (SeaGeneration.preservesMainland(region.getChunk(pos.getX()>>4,pos.getZ()>>4),pos.getX(),pos.getZ())) {
            int profile=SeaGeneration.height(world.getSeed(),pos.getX(),pos.getZ());
            if (pos.getY()>Math.max(SeaGeneration.retainedCeiling(region.getChunk(pos.getX()>>4,pos.getZ()>>4),pos.getX(),pos.getZ()),profile) && state.isSolid() &&
                    !state.isIn(net.minecraft.registry.tag.BlockTags.LOGS) && !state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) {
                cir.setReturnValue(false);return;
            }
            return;
        }
        cir.setReturnValue(false);
    }
    @Inject(method="getSeaLevel",at=@At("RETURN"),cancellable=true)
    private void greatsea$structureWaterLevel(CallbackInfoReturnable<Integer> cir) {
        if (OceanStructures.inStructurePass() || SeaDecoration.inOceanVegetationPass()) cir.setReturnValue(GreatSea.CONFIG.seaLevel);
    }
}
