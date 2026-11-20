package dev.greatsea;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

/** Client-side detection also works on remote extended-Nether worlds. */
public final class UnderworldClientCoordinates {
    private UnderworldClientCoordinates() {}
    public static boolean active(PlayerEntity player){
        return player!=null && UnderworldCoordinateText.hidden(
                player.getEntityWorld().getRegistryKey().equals(World.NETHER),
                player.getEntityWorld().getBottomY(),player.getY());
    }
    public static String line(String original){return UnderworldCoordinateText.maskLine(original,UnderworldCoordinateText.animationFrame(System.nanoTime()));}
}
