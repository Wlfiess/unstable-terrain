package dev.greatsea.mixin;
import dev.greatsea.AmplifiedTerrain;
import dev.greatsea.SeedAccess;
import dev.greatsea.UnstableWorldGen;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Register before any biome/noise generation, including chunks loaded at startup. */
@Mixin(ServerChunkManager.class)
public abstract class AmplifiedServerChunkManagerMixin {
    @Shadow @Final private ServerWorld world;
    @Inject(method="<init>",at=@At("TAIL"))
    private void unstableterrain$registerAmplified(CallbackInfo ci) {
        var manager=(ServerChunkManager)(Object)this;
        if(UnstableWorldGen.isUnstable(manager.getChunkGenerator())) {
            var noise=manager.getNoiseConfig();
            AmplifiedTerrain.register(noise,world.getRegistryManager(),((SeedAccess)(Object)noise).greatsea$seed());
        }
    }
}
