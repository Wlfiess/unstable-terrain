package dev.greatsea;

import dev.greatsea.mixin.MonumentBaseAccessor;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.OceanMonumentGenerator;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.OceanMonumentStructure;
import net.minecraft.world.gen.structure.OceanRuinStructure;
import net.minecraft.world.gen.structure.ShipwreckStructure;
import net.minecraft.world.gen.structure.Structure;

import java.util.List;

/** Uses vanilla structure starts, references, templates, processors and loot tables. */
public final class OceanStructures {
    private static final ThreadLocal<Boolean> STRUCTURE_PASS=ThreadLocal.withInitial(()->false);
    private OceanStructures() {}

    public static boolean inStructurePass() { return STRUCTURE_PASS.get(); }

    public static boolean oceanType(Structure structure) {
        return structure instanceof ShipwreckStructure || structure instanceof OceanRuinStructure ||
                structure instanceof OceanMonumentStructure;
    }

    /** Shared by actual start creation and read-only candidate searches. Existing starts
     * still place their pieces normally; generation never removes completed structures. */
    public static boolean acceptsStart(long seed,RegistryKey<Structure> key,ChunkPos pos) {
        return !key.getValue().getNamespace().equals("minecraft") ||
                OceanStructureDensity.accepts(seed,key.getValue().getPath(),pos.x,pos.z);
    }

    /** Whole monuments and ruin clusters fit beyond the foot of the underwater slope. */
    public static boolean offshore(long seed,int x,int z) {
        SeaConfig c=GreatSea.CONFIG;
        return SeaTerrain.contains(c,x,z) &&
                SeaTerrain.shoreDistance(c,seed,x,z)>=c.beachWidth+c.underwaterSlopeWidth+128;
    }

    public static boolean needsNativeFeatures(Chunk chunk) {
        int x=chunk.getPos().getStartX(),z=chunk.getPos().getStartZ();
        for (int dx=0;dx<16;dx++) for (int dz=0;dz<16;dz++)
            if (!SeaTerrain.contains(GreatSea.CONFIG,x+dx,z+dz) ||
                    SeaGeneration.preservesMainland(chunk,x+dx,z+dz)) return true;
        return false;
    }

    /** Vanilla monuments normally require deep-ocean biomes; this sea is physically deep
     * even where its deliberately uniform water colour uses the warm-ocean biome. */
    public static boolean createMonumentStart(StructureSet.WeightedEntry entry,StructureAccessor structures,
            DynamicRegistryManager registries,NoiseConfig noise,StructureTemplateManager templates,long seed,
            Chunk chunk,ChunkPos pos,ChunkSectionPos section,RegistryKey<World> dimension,ChunkGenerator generator) {
        Structure structure=entry.structure().value();
        StructureStart previous=structures.getStructureStart(section,structure,chunk);
        int references=previous==null ? 0 : previous.getReferences();
        StructureStart start=structure.createStructureStart(entry.structure(),dimension,registries,generator,
                generator.getBiomeSource(),noise,templates,seed,pos,references,chunk,
                biome->biome.matchesKey(BiomeKeys.WARM_OCEAN) || biome.matchesKey(BiomeKeys.LUKEWARM_OCEAN) ||
                    biome.matchesKey(BiomeKeys.OCEAN) || biome.matchesKey(BiomeKeys.COLD_OCEAN) || biome.matchesKey(BiomeKeys.FROZEN_OCEAN) ||
                    biome.matchesKey(BiomeKeys.DEEP_LUKEWARM_OCEAN) || biome.matchesKey(BiomeKeys.DEEP_OCEAN) ||
                    biome.matchesKey(BiomeKeys.DEEP_COLD_OCEAN) || biome.matchesKey(BiomeKeys.DEEP_FROZEN_OCEAN));
        if (!start.hasChildren()) return false;
        // Vanilla monuments have a fixed base Y=39. Move their real rooms and elder guardian
        // positions together, keeping the usual shallow burial into the seafloor.
        int floor=generator.getHeight(pos.getCenterX(),pos.getCenterZ(),Heightmap.Type.OCEAN_FLOOR_WG,chunk,noise)-1;
        for (StructurePiece piece:start.getChildren()) moveMonument(piece,floor-5-piece.getBoundingBox().getMinY());
        structures.setStructureStart(section,structure,start,chunk);
        return true;
    }

    public static void moveMonument(StructurePiece piece,int dy) {
        if (dy==0) return;
        piece.translate(0,dy,0);
        if (piece instanceof OceanMonumentGenerator.Base)
            for (StructurePiece room:((MonumentBaseAccessor)piece).greatsea$getChildren()) room.translate(0,dy,0);
    }

    /** The structure portion of vanilla generateFeatures, with the same decorator seeds
     * and chunk clipping. Ordinary placed features are handled separately on retained land. */
    public static void generate(ChunkGenerator generator,StructureWorldAccess world,Chunk chunk,StructureAccessor structures) {
        if (!structures.shouldGenerateStructures()) return;
        ChunkPos pos=chunk.getPos();
        ChunkSectionPos section=ChunkSectionPos.from(pos,world.getBottomSectionCoord());
        var registry=world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
        List<Structure> all=registry.stream().toList();
        ChunkRandom random=new ChunkRandom(new Xoroshiro128PlusPlusRandom(0));
        long populationSeed=random.setPopulationSeed(world.getSeed(),pos.getStartX(),pos.getStartZ());
        BlockBox box=new BlockBox(pos.getStartX(),chunk.getBottomY()+1,pos.getStartZ(),
                pos.getStartX()+15,chunk.getTopYInclusive(),pos.getStartZ()+15);
        boolean previous=STRUCTURE_PASS.get();
        STRUCTURE_PASS.set(true);
        try {
            for (GenerationStep.Feature step:GenerationStep.Feature.values()) {
                int index=0;
                for (Structure structure:all) {
                    if (structure.getFeatureGenerationStep()!=step) continue;
                    int decoratorIndex=index++;
                    if (!oceanType(structure)) continue;
                    random.setDecoratorSeed(populationSeed,decoratorIndex,step.ordinal());
                    world.setCurrentlyGeneratingStructureName(()->String.valueOf(registry.getId(structure)));
                    for (StructureStart start:structures.getStructureStarts(section,structure)) {
                        ChunkPos startPos=start.getPos();
                        if (offshore(world.getSeed(),startPos.getCenterX(),startPos.getCenterZ()))
                            start.place(world,structures,generator,random,box,pos);
                    }
                }
            }
        } finally {
            world.setCurrentlyGeneratingStructureName(null);
            STRUCTURE_PASS.set(previous);
        }
    }
}
