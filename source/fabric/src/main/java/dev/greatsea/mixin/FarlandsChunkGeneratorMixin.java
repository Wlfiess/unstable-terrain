package dev.greatsea.mixin;

import dev.greatsea.*;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.world.World;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class FarlandsChunkGeneratorMixin {
    @Inject(method="generateFeatures",at=@At("HEAD"),cancellable=true)
    private void unstableterrain$features(StructureWorldAccess world,Chunk chunk,StructureAccessor structures,CallbackInfo ci) {
        if(FarlandsWorld.supported(world.toServerWorld()) && FarlandsGeneration.intersects(chunk)) {
            FarlandsStructures.generate((ChunkGenerator)(Object)this,world,chunk,structures);
            FarlandsDecoration.decorate(chunk,world);ci.cancel();
        }
    }
    @Inject(method="setStructureStarts",at=@At("HEAD"),cancellable=true)
    private void unstableterrain$structure(DynamicRegistryManager registries,StructurePlacementCalculator placement,StructureAccessor structures,
            Chunk chunk,StructureTemplateManager templates,RegistryKey<World> dimension,CallbackInfo ci) {
        if(dimension.equals(World.OVERWORLD) && FarlandsWorld.supported((ChunkGenerator)(Object)this) && FarlandsGeneration.intersects(chunk) && !FarlandsStructures.enabled(chunk.getPos().getCenterX(),chunk.getPos().getCenterZ()))ci.cancel();
    }
    @Inject(method="setStructureStarts",at=@At("RETURN"))
    private void unstableterrain$buriedTrials(DynamicRegistryManager registries,StructurePlacementCalculator placement,StructureAccessor structures,Chunk chunk,StructureTemplateManager templates,RegistryKey<World> dimension,CallbackInfo ci){
        if(dimension.equals(World.OVERWORLD)&&FarlandsWorld.supported((ChunkGenerator)(Object)this))FarlandsStructures.prepareTrials(chunk,placement.getStructureSeed(),registries.getOrThrow(net.minecraft.registry.RegistryKeys.STRUCTURE));
    }
    @Inject(method="trySetStructureStart",at=@At("HEAD"),cancellable=true)
    private void unstableterrain$allowed(net.minecraft.structure.StructureSet.WeightedEntry entry,StructureAccessor structures,
            DynamicRegistryManager registries,net.minecraft.world.gen.noise.NoiseConfig noise,StructureTemplateManager templates,long seed,
            Chunk chunk,net.minecraft.util.math.ChunkPos pos,net.minecraft.util.math.ChunkSectionPos section,RegistryKey<World> dimension,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir){
        if(dimension.equals(World.OVERWORLD)&&FarlandsWorld.supported((ChunkGenerator)(Object)this)&&SpokelandsTerrain.contains(pos.getCenterX(),pos.getCenterZ())){
            if(entry.structure().getKey().map(key->FarlandsStructures.accepts(key,pos.getCenterX(),pos.getCenterZ())&&key.getValue().getPath().startsWith("mineshaft")).orElse(false)){
                cir.setReturnValue(FarlandsStructures.createSpokeMines(entry,structures,registries,noise,templates,seed,chunk,pos,section,dimension,(ChunkGenerator)(Object)this));return;
            }
        }
        if(dimension.equals(World.OVERWORLD)&&FarlandsWorld.supported((ChunkGenerator)(Object)this)&&entry.structure().getKey().map(key->key.getValue().getPath().equals("trial_chambers")).orElse(false)&&!FarlandsStructures.trialAllowed(pos.getCenterX(),pos.getCenterZ())){cir.setReturnValue(false);return;}
        if(dimension.equals(World.OVERWORLD)&&FarlandsWorld.supported((ChunkGenerator)(Object)this)&&FarlandsGeneration.intersects(chunk))
            if(entry.structure().getKey().map(key->!FarlandsStructures.accepts(key,pos.getCenterX(),pos.getCenterZ())).orElse(false))cir.setReturnValue(false);
    }
}
