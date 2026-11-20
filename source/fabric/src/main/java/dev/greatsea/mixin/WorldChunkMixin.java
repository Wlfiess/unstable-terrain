package dev.greatsea.mixin;

import dev.greatsea.SeaGeneration;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin {
    @Inject(method="<init>(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/ProtoChunk;Lnet/minecraft/world/chunk/WorldChunk$EntityLoader;)V",at=@At("TAIL"))
    private void greatsea$preservation(ServerWorld world,ProtoChunk proto,WorldChunk.EntityLoader loader,CallbackInfo ci) {
        SeaGeneration.copyPreserved(proto,(WorldChunk)(Object)this);
        dev.greatsea.ForbiddenWorldBlocks.removeGenerated(world,(WorldChunk)(Object)this);
    }
}
