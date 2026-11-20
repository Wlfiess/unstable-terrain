package dev.greatsea.mixin;
import dev.greatsea.MistShelterData;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(BlockItem.class)
public abstract class MistPlacementMixin {
 @Inject(method="place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",at=@At("RETURN"))
 private void mist$remember(ItemPlacementContext context,CallbackInfoReturnable<ActionResult> cir){
  if(cir.getReturnValue().isAccepted()&&context.getWorld() instanceof ServerWorld world)MistShelterData.placed(world,context.getBlockPos());
 }
}
