package dev.greatsea.mixin;

import dev.greatsea.*;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.OceanMonumentStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Inject(method="generateFeatures",at=@At("HEAD"),cancellable=true)
    private void greatsea$features(StructureWorldAccess world,Chunk chunk,StructureAccessor structures,CallbackInfo ci) {
        if (SeaGeneration.supported((ChunkGenerator)(Object)this) && SeaGeneration.intersects(chunk)) {
            OceanStructures.generate((ChunkGenerator)(Object)this,world,chunk,structures);
            // Retained mainland keeps its ordinary trees and other biome features. Writes
            // onto the sandy embankment are filtered by ChunkRegionMixin.
            if (OceanStructures.needsNativeFeatures(chunk)) return;
            SeaDecoration.decorate(chunk,world);
            ci.cancel();
        }
    }
    @Inject(method="generateFeatures",at=@At("RETURN"))
    private void greatsea$afterNativeFeatures(StructureWorldAccess world,Chunk chunk,StructureAccessor structures,CallbackInfo ci) {
        if (SeaGeneration.supported((ChunkGenerator)(Object)this) && SeaGeneration.intersects(chunk))
            SeaDecoration.decorate(chunk,world);
    }
    @Inject(method="setStructureStarts",at=@At("HEAD"),cancellable=true)
    private void greatsea$shoreStructures(DynamicRegistryManager registries,StructurePlacementCalculator placement,
            StructureAccessor structures,Chunk chunk,StructureTemplateManager templates,RegistryKey<World> dimension,CallbackInfo ci) {
        if (dimension.equals(World.OVERWORLD) && SeaGeneration.supported((ChunkGenerator)(Object)this) &&
                SeaGeneration.intersects(chunk) && !OceanStructures.offshore(placement.getStructureSeed(),
                chunk.getPos().getCenterX(),chunk.getPos().getCenterZ())) ci.cancel();
    }
    @Inject(method="trySetStructureStart",at=@At("HEAD"),cancellable=true)
    private void greatsea$oceanStarts(StructureSet.WeightedEntry entry,StructureAccessor structures,
            DynamicRegistryManager registries,NoiseConfig noise,StructureTemplateManager templates,long seed,
            Chunk chunk,ChunkPos pos,ChunkSectionPos section,RegistryKey<World> dimension,CallbackInfoReturnable<Boolean> cir) {
        if (!dimension.equals(World.OVERWORLD) || !SeaGeneration.supported((ChunkGenerator)(Object)this) ||
                !SeaGeneration.intersects(chunk)) return;
        if (!OceanStructures.offshore(seed,pos.getCenterX(),pos.getCenterZ()) || !OceanStructures.oceanType(entry.structure().value()) ||
                !entry.structure().getKey().map(key->OceanStructures.acceptsStart(seed,key,pos)).orElse(true)) {
            cir.setReturnValue(false);
        } else if (entry.structure().value() instanceof OceanMonumentStructure) {
            cir.setReturnValue(OceanStructures.createMonumentStart(entry,structures,registries,noise,templates,seed,
                    chunk,pos,section,dimension,(ChunkGenerator)(Object)this));
        }
    }
}
