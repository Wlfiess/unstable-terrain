package dev.greatsea.mixin;

import dev.greatsea.GeneratedIcePolicy;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents native surface/weather freezing after ice-free chunks have finished generating. */
@Mixin(Biome.class)
public abstract class BiomeIceGenerationMixin {
    @Inject(method="canSetIce(Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;Z)Z",at=@At("HEAD"),cancellable=true)
    private void unstableterrain$noBiomeIce(WorldView world,BlockPos pos,boolean adjacentWater,CallbackInfoReturnable<Boolean> cir) {
        if(dev.greatsea.MistTerrain.preservesIce(world,pos))return;
        if(GeneratedIcePolicy.supported(world))cir.setReturnValue(false);
    }
}
