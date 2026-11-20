package dev.greatsea.mixin;
import dev.greatsea.ForbiddenWorldBlocks;
import dev.greatsea.UnstableMobSpawns;
import dev.greatsea.GeneratedIcePolicy;
import net.minecraft.block.BlockState;
import net.minecraft.world.ChunkRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChunkRegion.class)
public abstract class ForbiddenChunkRegionBlocksMixin {
    @ModifyVariable(method="setBlockState",at=@At("HEAD"),argsOnly=true)
    private BlockState unstableterrain$filter(BlockState state,net.minecraft.util.math.BlockPos pos,BlockState original,int flags,int maxUpdateDepth){
        ChunkRegion region=(ChunkRegion)(Object)this;var world=region.toServerWorld();
        if(!UnstableMobSpawns.supported(world))return state;
        return ForbiddenWorldBlocks.generated(world,pos,GeneratedIcePolicy.generated(region,pos,state));
    }
}
