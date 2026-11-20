package dev.greatsea.mixin;

import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Used only by the opt-in offline verifier, which has no connection for game-mode packets. */
@Mixin(ServerPlayerInteractionManager.class)
public interface UnderworldInteractionManagerAccessor {
    @Accessor("gameMode")
    void greatsea$setVerificationGameMode(GameMode gameMode);
}
