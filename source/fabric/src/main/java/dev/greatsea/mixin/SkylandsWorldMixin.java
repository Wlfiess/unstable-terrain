package dev.greatsea.mixin;
import dev.greatsea.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public abstract class SkylandsWorldMixin {
    @Inject(method="<init>",at=@At("TAIL"))
    private void unstableterrain$skylands(CallbackInfo ci){
        var world=(ServerWorld)(Object)this;
        if(!FarlandsWorld.supported(world))return;
        var registries=world.getRegistryManager();
        var settings=registries.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS).getOrThrow(ChunkGeneratorSettings.FLOATING_ISLANDS);
        VanillaSkylands.register(world.getSeed(),NoiseConfig.create(settings.value(),registries.getOrThrow(RegistryKeys.NOISE_PARAMETERS),world.getSeed()));
    }
}
