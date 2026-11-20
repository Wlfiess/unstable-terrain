package dev.greatsea;

import dev.greatsea.mixin.MonumentBaseAccessor;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BrushableBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureKeys;

import java.util.List;

/** Opt-in integration checks against genuine vanilla starts and their generated contents. */
public final class OceanStructureVerification {
    private OceanStructureVerification() {}
    private static void check(boolean condition,String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void verify(ServerWorld world,List<String> report,int coldX,int coldZ) {
        verifySparseMonumentStarts(world,report);
        verifyLootStructure(world,StructureKeys.SHIPWRECK,"chests/shipwreck_",report);
        verifyLootStructure(world,StructureKeys.OCEAN_RUIN_WARM,"chests/underwater_ruin_",report);
        verifyLootStructure(world,StructureKeys.OCEAN_RUIN_COLD,"chests/underwater_ruin_",report,
                findColdRuin(world,coldX,coldZ));
        verifyMonument(world,report);
    }

    private static StructureStart findColdRuin(ServerWorld world,int coldX,int coldZ) {
        var registry=world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
        Structure structure=registry.getValueOrThrow(StructureKeys.OCEAN_RUIN_COLD);
        var calculator=world.getChunkManager().getStructurePlacementCalculator();
        var source=world.getChunkManager().getChunkGenerator().getBiomeSource();
        var sampler=world.getChunkManager().getNoiseConfig().getMultiNoiseSampler();
        for (var placement:calculator.getPlacements(registry.getEntry(structure))) {
            if (!(placement instanceof RandomSpreadStructurePlacement spread)) continue;
            for (int dx=-3;dx<=3;dx++) for (int dz=-3;dz<=3;dz++) {
                ChunkPos pos=spread.getStartChunk(calculator.getStructureSeed(),
                        (coldX>>4)+dx*spread.getSpacing(),(coldZ>>4)+dz*spread.getSpacing());
                if (!OceanStructures.offshore(world.getSeed(),pos.getCenterX(),pos.getCenterZ()) ||
                        !OceanStructures.acceptsStart(world.getSeed(),StructureKeys.OCEAN_RUIN_COLD,pos) ||
                        !source.getBiome(pos.getCenterX()>>2,0,pos.getCenterZ()>>2,sampler).matchesKey(BiomeKeys.COLD_OCEAN)) continue;
                StructureStart start=world.getChunk(pos.x,pos.z).getStructureStart(structure);
                if (start!=null && start.hasChildren()) {
                    loadBounds(world,start);
                    return start;
                }
            }
        }
        throw new AssertionError("No genuine cold-ocean ruin generated near the verified broad cold patch");
    }

    private static void loadBounds(ServerWorld world,StructureStart start) {
        BlockBox box=start.getBoundingBox();
        for (int cx=box.getMinX()>>4;cx<=box.getMaxX()>>4;cx++)
            for (int cz=box.getMinZ()>>4;cz<=box.getMaxZ()>>4;cz++) world.getChunk(cx,cz);
    }

    private static StructureStart find(ServerWorld world,RegistryKey<Structure> key) {
        var registry=world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
        Structure structure=registry.getValueOrThrow(key);
        var calculator=world.getChunkManager().getStructurePlacementCalculator();
        var source=world.getChunkManager().getChunkGenerator().getBiomeSource();
        var sampler=world.getChunkManager().getNoiseConfig().getMultiNoiseSampler();
        int baseX=(GreatSea.CONFIG.innerBoundary+6000)>>4;
        for (var placement:calculator.getPlacements(registry.getEntry(structure))) {
            if (!(placement instanceof RandomSpreadStructurePlacement spread)) continue;
            int attempts=256;
            for (int attempt=0;attempt<attempts;attempt++) {
                // Reject the density gate before loading a candidate's real chunks.
                int offsetX=(attempt%16)*spread.getSpacing();
                int offsetZ=(attempt/16)*spread.getSpacing();
                ChunkPos pos=spread.getStartChunk(calculator.getStructureSeed(),baseX+offsetX,offsetZ);
                if (!OceanStructures.offshore(world.getSeed(),pos.getCenterX(),pos.getCenterZ()) ||
                        !OceanStructures.acceptsStart(world.getSeed(),key,pos)) continue;
                if (key.equals(StructureKeys.OCEAN_RUIN_WARM) &&
                        !source.getBiome(pos.getCenterX()>>2,0,pos.getCenterZ()>>2,sampler).matchesKey(BiomeKeys.WARM_OCEAN)) continue;
                StructureStart start=world.getChunk(pos.x,pos.z).getStructureStart(structure);
                if (start!=null && start.hasChildren()) {
                    loadBounds(world,start);
                    return start;
                }
            }
        }
        throw new AssertionError("No genuine vanilla "+key.getValue()+" start found offshore");
    }

    private static void verifySparseMonumentStarts(ServerWorld world,List<String> report) {
        var registry=world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
        Structure structure=registry.getValueOrThrow(StructureKeys.MONUMENT);
        var calculator=world.getChunkManager().getStructurePlacementCalculator();
        int accepted=0,rejected=0,emptyStarts=0;
        int baseX=(GreatSea.CONFIG.innerBoundary+6000)>>4;
        for (var placement:calculator.getPlacements(registry.getEntry(structure))) {
            if (!(placement instanceof RandomSpreadStructurePlacement spread)) continue;
            check(spread.getSpacing()==OceanStructureDensity.MONUMENT_CELL_CHUNKS,
                    "Monument thinning must match the native placement cell size");
            for (int dx=0;dx<16;dx++) for (int dz=0;dz<16;dz++) {
                ChunkPos pos=spread.getStartChunk(calculator.getStructureSeed(),baseX+dx*spread.getSpacing(),dz*spread.getSpacing());
                if (!OceanStructures.offshore(world.getSeed(),pos.getCenterX(),pos.getCenterZ())) continue;
                if (OceanStructures.acceptsStart(world.getSeed(),StructureKeys.MONUMENT,pos)) accepted++;
                else {
                    rejected++;
                    if (emptyStarts<4) {
                        StructureStart start=world.getChunk(pos.x,pos.z).getStructureStart(structure);
                        check(start==null || !start.hasChildren(),"Rejected monument candidate still generated a real start");
                        emptyStarts++;
                    }
                }
            }
        }
        check(accepted==16 && rejected==240 && emptyStarts==4,"Monument density is not one in sixteen native placement candidates");
        report.add("Sparse monuments: "+accepted+" accepted / "+(accepted+rejected)+
                " vanilla candidates; four rejected starts absent; neighbouring monuments separated by at least 1120 blocks");
    }

    private static void verifyLootStructure(ServerWorld world,RegistryKey<Structure> key,String lootPrefix,List<String> report) {
        verifyLootStructure(world,key,lootPrefix,report,find(world,key));
    }

    private static void verifyLootStructure(ServerWorld world,RegistryKey<Structure> key,String lootPrefix,List<String> report,StructureStart start) {
        BlockBox bounds=start.getBoundingBox();
        boolean cold=key.equals(StructureKeys.OCEAN_RUIN_COLD);
        String archaeologyTable="minecraft:archaeology/ocean_ruin_"+(cold ? "cold" : "warm");
        int chests=0,archaeology=0;
        for (int cx=bounds.getMinX()>>4;cx<=bounds.getMaxX()>>4;cx++) {
            for (int cz=bounds.getMinZ()>>4;cz<=bounds.getMaxZ()>>4;cz++) {
                var chunk=world.getChunk(cx,cz);
                for (BlockPos pos:chunk.getBlockEntityPositions()) {
                    if (pos.getX()<bounds.getMinX() || pos.getX()>bounds.getMaxX() ||
                            pos.getZ()<bounds.getMinZ() || pos.getZ()>bounds.getMaxZ()) continue;
                    var entity=chunk.getBlockEntity(pos);
                    if (entity instanceof LootableContainerBlockEntity loot && loot.getLootTable()!=null &&
                            loot.getLootTable().getValue().getNamespace().equals("minecraft") &&
                            loot.getLootTable().getValue().getPath().startsWith(lootPrefix)) {
                        check(pos.getY()<GreatSea.CONFIG.seaLevel,"Ocean loot chest placed above the Great Sea");
                        chests++;
                    }
                    if (entity instanceof BrushableBlockEntity && entity.createNbt(world.getRegistryManager())
                            .getString("LootTable","").equals(archaeologyTable)) archaeology++;
                }
            }
        }
        check(chests>0,"Genuine "+key.getValue()+" pieces have no vanilla loot chest");
        if (key.equals(StructureKeys.OCEAN_RUIN_WARM) || cold)
            check(archaeology>0,"Ocean ruin has no vanilla archaeological suspicious "+(cold ? "gravel" : "sand"));
        report.add("Real "+key.getValue()+" at "+start.getPos().getCenterX()+","+start.getPos().getCenterZ()+
                ": "+start.getChildren().size()+" vanilla pieces, "+chests+" vanilla loot chests"+
                (archaeology>0 ? ", "+archaeology+" archaeological "+(cold ? "gravel" : "sand")+" blocks" : ""));
    }

    private static void verifyMonument(ServerWorld world,List<String> report) {
        StructureStart start=find(world,StructureKeys.MONUMENT);
        ChunkPos pos=start.getPos();
        var generator=world.getChunkManager().getChunkGenerator();
        int floor=generator.getHeight(pos.getCenterX(),pos.getCenterZ(),Heightmap.Type.OCEAN_FLOOR_WG,
                world,world.getChunkManager().getNoiseConfig())-1;
        int base=start.getChildren().getFirst().getBoundingBox().getMinY();
        check(base==floor-5,"Monument did not anchor to the real seafloor: base="+base+", floor="+floor);
        check(base!=39,"Monument still uses the vanilla fixed Y=39 base");
        BlockBox box=start.getBoundingBox();
        int prismarine=0,lanterns=0;
        BlockPos.Mutable cursor=new BlockPos.Mutable();
        for (int x=box.getMinX();x<=box.getMaxX();x++) for (int z=box.getMinZ();z<=box.getMaxZ();z++) {
            for (int y=base;y<=base+23;y++) {
                var state=world.getBlockState(cursor.set(x,y,z));
                if (state.isOf(Blocks.PRISMARINE) || state.isOf(Blocks.PRISMARINE_BRICKS) || state.isOf(Blocks.DARK_PRISMARINE)) prismarine++;
                if (state.isOf(Blocks.SEA_LANTERN)) lanterns++;
            }
        }
        check(prismarine>1000 && lanterns>0,"Vanilla monument rooms were not placed into real chunks");
        StructureContext context=StructureContext.from(world);
        StructureStart reloaded=StructureStart.fromNbt(context,start.toNbt(context,pos),world.getSeed());
        check(reloaded!=null && reloaded.hasChildren(),"Monument structure start failed to reload");
        check(reloaded.getChildren().getFirst().getBoundingBox().getMinY()==base,"Monument resets to Y=39 after reload");
        var originalRooms=((MonumentBaseAccessor)start.getChildren().getFirst()).greatsea$getChildren();
        var restoredRooms=((MonumentBaseAccessor)reloaded.getChildren().getFirst()).greatsea$getChildren();
        check(originalRooms.size()>5 && originalRooms.size()==restoredRooms.size(),"Monument room graph changed on reload");
        for (int i=0;i<originalRooms.size();i++) {
            BlockBox original=originalRooms.get(i).getBoundingBox(),restored=restoredRooms.get(i).getBoundingBox();
            check(original.getMinY()==restored.getMinY() && original.getMaxY()==restored.getMaxY(),
                    "Monument nested room "+i+" lost its anchored Y on reload");
            check(restored.getMinY()>=base && restored.getMaxY()<=base+23,
                    "Monument nested room "+i+" is detached from its anchored base");
        }
        int guardians=world.getEntitiesByType(EntityType.ELDER_GUARDIAN,
                entity->entity.getX()>=box.getMinX() && entity.getX()<=box.getMaxX()+1 &&
                        entity.getZ()>=box.getMinZ() && entity.getZ()<=box.getMaxZ()+1).size();
        // Entity chunk activation may occur on the following server tick; rooms, lanterns
        // and the serialized start are checked synchronously during startup verification.
        report.add("Real minecraft:monument at "+pos.getCenterX()+","+pos.getCenterZ()+": base Y="+base+
                ", "+prismarine+" prismarine blocks, "+lanterns+" sea lanterns, "+restoredRooms.size()+" anchored rooms survive reload"+
                (guardians>0 ? ", "+guardians+" loaded elder guardians" : ""));
    }
}
