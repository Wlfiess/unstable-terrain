package dev.greatsea;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureKeys;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import java.util.*;
import java.nio.file.*;

/** Opt-in checks against fresh, actual Minecraft worlds for release 1.24.0. */
public final class RevisionVerifier {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void run(MinecraftServer server){
        var report=new ArrayList<String>();
        try{
            var world=server.getOverworld();long seed=world.getSeed();
            for(var dimension:server.getWorlds()){
                dimension.getChunk(0,0);
                for(var reason:SpawnReason.values()){
                    var mob=EntityType.COW.create(dimension,reason);mob.setPosition(8,110,8);
                    check(!dimension.spawnEntity(mob),"Mob admitted: "+reason+" in "+dimension.getRegistryKey());
                }
                EntityType.COW.spawnFromItemStack(dimension,new ItemStack(Items.COW_SPAWN_EGG),null,new BlockPos(8,110,8),SpawnReason.SPAWN_ITEM_USE,true,false);
                check(dimension.getEntitiesByType(EntityType.COW,e->true).isEmpty(),"Egg spawned a cow");
            }
            report.add("PASS every spawn reason and actual spawn-egg attempts blocked in all three dimensions");
            int boundary=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.CORRUPTED);
            for(int x:new int[]{0,boundary-32,boundary+64,boundary+20000}){
                var pos=new BlockPos(x,100,0);world.getChunk(x>>4,0);
                world.setBlockState(pos,Blocks.VAULT.getDefaultState().with(VaultBlock.OMINOUS,true),3);
                check(world.getBlockState(pos).isOf(Blocks.VAULT)==ForbiddenWorldBlocks.allowsOminous(x,0),"Vault boundary "+x);
            }
            report.add("PASS actual ominous-vault placement outside, at, and beyond Corrupted Lands");
            var nether=server.getWorld(World.NETHER);var chunk=nether.getChunk(0,0);
            for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=chunk.getBottomY();y<0;y++)
                check(!chunk.getBlockState(new BlockPos(x,y,z)).isOf(Blocks.COARSE_DIRT),"Coarse dirt in Underworld");
            report.add("PASS generated Underworld has no coarse dirt; surface base="+UnderworldTerrain.SURFACE_BASE);
            var generator=world.getChunkManager().getChunkGenerator();var sampler=world.getChunkManager().getNoiseConfig().getMultiNoiseSampler();
            for(int sign:new int[]{-1,1})for(int boundaryValue:new int[]{250_032,2_999_968})for(boolean axis:new boolean[]{false,true}){
                int x=axis?0:sign*boundaryValue,z=axis?sign*boundaryValue:0;
                check(generator.getBiomeSource().getBiome(x>>2,50,z>>2,sampler).matchesKey(net.minecraft.world.biome.BiomeKeys.BEACH),"Sea shore biome "+x+","+z);
            }
            var oceanBiomes=new HashSet<String>();
            BlockPos frozenSample=null;
            for(int x=290_000;x<900_000;x+=10031)for(int z=-180_000;z<180_000;z+=21013)
            {
                var biome=generator.getBiomeSource().getBiome(x>>2,45,z>>2,sampler);
                oceanBiomes.add(biome.getKey().orElseThrow().getValue().getPath());
                if(frozenSample==null&&biome.matchesKey(net.minecraft.world.biome.BiomeKeys.FROZEN_OCEAN))frozenSample=new BlockPos(x,GreatSea.CONFIG.seaLevel-1,z);
            }
            check(oceanBiomes.size()>=5,"Ocean climate collapsed: "+oceanBiomes);
            report.add("PASS all four inner/outer beach faces use beach biome; ocean families="+oceanBiomes);
            check(frozenSample!=null,"No frozen ocean fixture");
            var iceChunk=world.getChunk(frozenSample.getX()>>4,frozenSample.getZ()>>4);
            for(var section:iceChunk.getSectionArray())check(!section.hasAny(GeneratedIcePolicy::isIce),"Ice generated in frozen ocean");
            var frozenBiome=world.getRegistryManager().getOrThrow(RegistryKeys.BIOME).getOrThrow(net.minecraft.world.biome.BiomeKeys.FROZEN_OCEAN).value();
            check(!frozenBiome.canSetIce(world,frozenSample,false),"Natural surface freezing still allowed");
            var placed=new BlockPos(16,100,16);world.setBlockState(placed,Blocks.PACKED_ICE.getDefaultState(),3);
            check(world.getBlockState(placed).isOf(Blocks.PACKED_ICE),"Player-placed ice was blocked");
            report.add("PASS fresh frozen-ocean chunk contains no ice; natural freezing blocked; placed ice preserved");
            int repeating=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.REPEATING)+64;
            boolean repeatedChest=false;
            for(int x=repeating;x<repeating+16&&!repeatedChest;x++)for(int z=-FarlandsSamples.repeatingDepth()/2;z<FarlandsSamples.repeatingDepth()/2&&!repeatedChest;z++)for(int y=0;y<16&&!repeatedChest;y++){
                if(FarlandsRepeatingContents.dungeonMaterial(seed,x,y,z)!=FarlandsRepeatingContents.CHEST)continue;
                var first=new BlockPos(x,y,z);var second=first.add(16,16,0);
                world.getChunk(x>>4,z>>4);world.getChunk(second.getX()>>4,second.getZ()>>4);
                check(world.getBlockEntity(first) instanceof net.minecraft.block.entity.ChestBlockEntity,"Repeated chest data missing");
                check(world.getBlockEntity(second) instanceof net.minecraft.block.entity.ChestBlockEntity,"Repeated counterpart data missing");
                var a=(net.minecraft.block.entity.ChestBlockEntity)world.getBlockEntity(first);var b=(net.minecraft.block.entity.ChestBlockEntity)world.getBlockEntity(second);
                int occupied=0;for(int slot=0;slot<27;slot++){check(ItemStack.areEqual(a.getStack(slot),b.getStack(slot)),"Repeated loot differs in slot "+slot);if(!a.getStack(slot).isEmpty())occupied++;}
                check(occupied>0,"Repeated chests have no actual loot");
                repeatedChest=true;
            }
            check(repeatedChest,"No repeating dungeon chest found");
            report.add("PASS actual repeated chest inventories contain identical loot at period (16,16,0)");
            var registry=world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
            var trial=registry.getValueOrThrow(StructureKeys.TRIAL_CHAMBERS);
            var calculator=world.getChunkManager().getStructurePlacementCalculator();
            boolean found=false;
            for(var placement:calculator.getPlacements(registry.getEntry(trial))){
                if(!(placement instanceof RandomSpreadStructurePlacement spread))continue;
                for(int i=0;i<12&&!found;i++){
                    var pos=spread.getStartChunk(calculator.getStructureSeed(),(boundary+2048+i*512)>>4,0);
                    var start=world.getChunk(pos.x,pos.z).getStructureStart(trial);
                    if(start!=null&&start.hasChildren()){
                        var box=start.getBoundingBox();int spawners=0,ominous=0,gapSpawners=0;
                        for(int cx=box.getMinX()>>4;cx<=box.getMaxX()>>4;cx++)for(int cz=box.getMinZ()>>4;cz<=box.getMaxZ()>>4;cz++){
                            var generated=world.getChunk(cx,cz);
                            for(var blockEntity:generated.getBlockEntities().values()){
                                var state=blockEntity.getCachedState();
                                if(state.isOf(Blocks.TRIAL_SPAWNER)){spawners++;if(FarlandsTerrain.missingChunk(seed,blockEntity.getPos().getX(),blockEntity.getPos().getZ()))gapSpawners++;}
                                if(ForbiddenWorldBlocks.ominousVault(state))ominous++;
                            }
                        }
                        check(spawners>0,"Trial start has no placed trial spawners");check(ominous>0,"Corrupted trial lost every ominous vault");
                        check(gapSpawners>0,"Trial spawners were still clipped out of missing chunks");
                        found=true;report.add("PASS native trial chamber placed across Corrupted gaps: "+pos+" trialSpawners="+spawners+" ominousVaults="+ominous+" spawnersInGaps="+gapSpawners);
                    }
                }
            }
            check(found,"No native Corrupted trial chamber start generated");
            MainlandJoinVerification.verify(world,report);
            report.add("PASS revision integration");
        }catch(Throwable error){report.add("FAIL "+error);GreatSea.LOGGER.error("Revision verification failed",error);}
        try{Files.write(Path.of("revision-verification.txt"),report);}catch(Exception e){throw new RuntimeException(e);}
        GreatSea.LOGGER.info(String.join("\n",report));server.stop(false);
    }
}

