package dev.greatsea.mixin;
import dev.greatsea.*;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.*;
import net.minecraft.world.tick.ScheduledTickView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class RainbowTreeStabilityMixin {
    private boolean stable(ServerWorld world,BlockPos pos){return world.getRegistryKey()==World.OVERWORLD&&UnstableMobSpawns.supported(world)&&SpokelandsDecoration.stableRainbow(world.getSeed(),pos,(BlockState)(Object)this);}
    @Inject(method="getStateForNeighborUpdate",at=@At("HEAD"),cancellable=true)
    private void keep(WorldView world,ScheduledTickView ticks,BlockPos pos,Direction direction,BlockPos neighbor,BlockState neighborState,Random random,CallbackInfoReturnable<BlockState> ci){
        if(world instanceof ServerWorld server&&stable(server,pos))ci.setReturnValue((BlockState)(Object)this);
    }
    @Inject(method={"scheduledTick","randomTick"},at=@At("HEAD"),cancellable=true)
    private void tick(ServerWorld world,BlockPos pos,Random random,CallbackInfo ci){if(stable(world,pos))ci.cancel();}
}
