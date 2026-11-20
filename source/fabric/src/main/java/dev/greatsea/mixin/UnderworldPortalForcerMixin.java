package dev.greatsea.mixin;

import dev.greatsea.UnstableNether;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.dimension.PortalForcer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Extending the dimension must not move newly created Nether portals beneath bedrock. */
@Mixin(PortalForcer.class)
public abstract class UnderworldPortalForcerMixin {
    @Redirect(method="createPortal",at=@At(value="INVOKE",target="Lnet/minecraft/server/world/ServerWorld;getBottomY()I"))
    private int greatsea$nativePortalBottom(ServerWorld world) { return UnstableNether.supported(world) ? 0 : world.getBottomY(); }
    @Redirect(method="createPortal",at=@At(value="INVOKE",target="Lnet/minecraft/server/world/ServerWorld;getLogicalHeight()I"))
    private int greatsea$nativePortalHeight(ServerWorld world) { return UnstableNether.supported(world) ? 128 : world.getLogicalHeight(); }
}
