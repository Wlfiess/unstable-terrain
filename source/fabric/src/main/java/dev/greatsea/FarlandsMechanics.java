package dev.greatsea;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.screen.GenericContainerScreenHandler;

/** Farlands mechanics are coordinate-scoped within the existing Overworld. */
public final class FarlandsMechanics {
    private FarlandsMechanics() {}
    public static boolean fireworksDisabled(PlayerEntity player) {
        if (!FarlandsWorld.activePlayer(player)) return false;
        return player.getEntityWorld() instanceof ServerWorld ?
                !GameplaySettings.Rule.FARLANDS_FIREWORKS.enabled() &&
                FarlandsWorld.depth(player.getX(),player.getZ())>=GreatSea.FARLANDS.fireworkDisableDepth :
                FarlandsCoordinates.clientDepth(player)>=FarlandsCoordinates.clientFireworkDisableDepth();
    }
    public static boolean closeRegionMenu(ServerPlayerEntity player,boolean previousFarlands,boolean currentFarlands) {
        if (previousFarlands==currentFarlands || player.currentScreenHandler==player.playerScreenHandler) return false;
        player.closeHandledScreen();
        return true;
    }
    public static boolean closeStaleEnderMenu(ServerPlayerEntity player) {
        if (!(player.currentScreenHandler instanceof GenericContainerScreenHandler menu) ||
                !(menu.getInventory() instanceof EnderChestInventory inventory) || inventory==player.getEnderChestInventory()) return false;
        player.closeHandledScreen();
        return true;
    }
}
