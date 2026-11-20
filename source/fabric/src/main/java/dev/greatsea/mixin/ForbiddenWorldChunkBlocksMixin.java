package dev.greatsea.mixin;
import dev.greatsea.ForbiddenWorldBlocks;
import dev.greatsea.OrbitalRuntime;
import dev.greatsea.UnstableMobSpawns;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunk.class)
public abstract class ForbiddenWorldChunkBlocksMixin {
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void unstableterrain$orbitalDamage(BlockPos pos,BlockState state,int flags,CallbackInfoReturnable<BlockState> result){
        if(state.isAir()&&(flags&Block.MOVED)==0&&result.getReturnValue()!=null&&!result.getReturnValue().isAir()
                &&((WorldChunk)(Object)this).getWorld() instanceof ServerWorld world)OrbitalRuntime.blockBroken(world,pos);
    }
    @ModifyVariable(method="setBlockState",at=@At("HEAD"),argsOnly=true)
    private BlockState unstableterrain$filter(BlockState state,net.minecraft.util.math.BlockPos pos,BlockState original,int flags){
        var world=((WorldChunk)(Object)this).getWorld();
        if(world instanceof ServerWorld server && dev.greatsea.MistRegion.active(server)&&dev.greatsea.MistRegion.frost(server.getSeed(),pos.getX(),pos.getZ()))state=dev.greatsea.MistTerrain.frozen(state);
        return world instanceof ServerWorld server && UnstableMobSpawns.supported(server) && ForbiddenWorldBlocks.ominousVault(state) && !ForbiddenWorldBlocks.allowsOminous(server,pos.getX(),pos.getZ())?net.minecraft.block.Blocks.AIR.getDefaultState():state;
    }
}
