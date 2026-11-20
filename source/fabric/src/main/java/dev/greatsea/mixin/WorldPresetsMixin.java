package dev.greatsea.mixin;

import dev.greatsea.UnstableWorldGen;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.gen.WorldPreset;
import net.minecraft.world.gen.WorldPresets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

/** Keep the native Re-Create screen's World Type label when opening a saved Unstable world. */
@Mixin(WorldPresets.class)
public abstract class WorldPresetsMixin {
    @Inject(method="getWorldPreset",at=@At("HEAD"),cancellable=true)
    private static void greatsea$recognizeSavedWorld(DimensionOptionsRegistryHolder dimensions,
            CallbackInfoReturnable<Optional<RegistryKey<WorldPreset>>> cir) {
        if (UnstableWorldGen.isUnstable(dimensions.getChunkGenerator())) cir.setReturnValue(Optional.of(UnstableWorldGen.PRESET));
    }
}
