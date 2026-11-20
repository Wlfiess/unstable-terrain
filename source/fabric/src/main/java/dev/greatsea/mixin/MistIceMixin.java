package dev.greatsea.mixin;
import dev.greatsea.*;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(IceBlock.class)
public abstract class MistIceMixin {
 @Inject(method="randomTick",at=@At("HEAD"),cancellable=true)
 private void mist$keepIce(BlockState state,ServerWorld world,BlockPos pos,Random random,CallbackInfo ci){
  if(MistRegion.active(world)&&MistRegion.frost(world.getSeed(),pos.getX(),pos.getZ()))ci.cancel();
 }
}
