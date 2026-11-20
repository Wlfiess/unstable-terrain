package dev.greatsea.mixin;

import dev.greatsea.GreatSea;
import dev.greatsea.OceanStructures;
import dev.greatsea.SeaTerrain;
import net.minecraft.structure.StructurePiecesList;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.OceanMonumentStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla regenerates monument room layouts when loading the start; retain the saved Y. */
@Mixin(OceanMonumentStructure.class)
public abstract class OceanMonumentStructureMixin {
    @Inject(method="modifyPiecesOnRead",at=@At("RETURN"))
    private static void greatsea$restoreFloorHeight(ChunkPos pos,long seed,StructurePiecesList saved,
            CallbackInfoReturnable<StructurePiecesList> cir) {
        if (!SeaTerrain.contains(GreatSea.CONFIG,pos.getCenterX(),pos.getCenterZ()) || saved.isEmpty()) return;
        StructurePiecesList rebuilt=cir.getReturnValue();
        if (rebuilt.isEmpty()) return;
        int y=saved.pieces().getFirst().getBoundingBox().getMinY();
        for (var piece:rebuilt.pieces()) OceanStructures.moveMonument(piece,y-piece.getBoundingBox().getMinY());
    }
}
