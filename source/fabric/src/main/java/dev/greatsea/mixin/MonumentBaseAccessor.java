package dev.greatsea.mixin;

import net.minecraft.structure.OceanMonumentGenerator;
import net.minecraft.structure.StructurePiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

@Mixin(OceanMonumentGenerator.Base.class)
public interface MonumentBaseAccessor {
    @Accessor("children")
    List<StructurePiece> greatsea$getChildren();
}
