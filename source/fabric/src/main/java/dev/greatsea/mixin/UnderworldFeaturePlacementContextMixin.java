package dev.greatsea.mixin;

import dev.greatsea.UnstableNether;
import net.minecraft.world.gen.feature.FeaturePlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Multilayer Nether features keep their original Y=0 lower scan boundary. */
@Mixin(FeaturePlacementContext.class)
public abstract class UnderworldFeaturePlacementContextMixin {
    @Inject(method="getBottomY",at=@At("HEAD"),cancellable=true)
    private void greatsea$nativeFeatureBottom(CallbackInfoReturnable<Integer> cir) {
        if (UnstableNether.supported(((FeaturePlacementContext)(Object)this).getChunkGenerator())) cir.setReturnValue(0);
    }
}
