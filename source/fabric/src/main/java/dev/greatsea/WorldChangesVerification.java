package dev.greatsea;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.VaultBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.PalettesFactory;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Actual Minecraft pipeline, egg spawning, ore attempts and loaded-vault checks. */
public final class WorldChangesVerification {
    private WorldChangesVerification() {}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void await(MinecraftServer server,CompletableFuture<?> future) {
        long deadline=System.nanoTime()+30_000_000_000L;
        server.runTasks(()->future.isDone() || System.nanoTime()>deadline);
        check(future.isDone(),"Timed out generating Amplified comparison");future.join();
    }
    public static void verify(MinecraftServer server,List<String> report) {
        var world=server.getOverworld();var original=(NoiseChunkGenerator)world.getChunkManager().getChunkGenerator();
        var noise=world.getChunkManager().getNoiseConfig();var registries=server.getRegistryManager();
        check(AmplifiedTerrain.delegate(original,noise)!=null,"Amplified noise context was not registered");
        var amplified=new NoiseChunkGenerator(original.getBiomeSource(),registries.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS).getOrThrow(ChunkGeneratorSettings.AMPLIFIED));
        var amplifiedNoise=NoiseConfig.create(registries,ChunkGeneratorSettings.AMPLIFIED,world.getSeed());
        var normal=new NoiseChunkGenerator(original.getBiomeSource(),registries.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS).getOrThrow(ChunkGeneratorSettings.OVERWORLD));
        var normalNoise=NoiseConfig.create(registries,ChunkGeneratorSettings.OVERWORLD,world.getSeed());
        int compared=0,different=0;
        for(int[] coordinate:new int[][]{{129_552,32},{-129_552,32},{32,129_552},{32,-129_552},{160_000,160_000}}) {
            var pos=new ChunkPos(coordinate[0]>>4,coordinate[1]>>4);
            var actual=new ProtoChunk(pos,UpgradeData.NO_UPGRADE_DATA,world,PalettesFactory.fromRegistryManager(registries),null);
            var expected=new ProtoChunk(pos,UpgradeData.NO_UPGRADE_DATA,world,PalettesFactory.fromRegistryManager(registries),null);
            await(server,original.populateBiomes(noise,Blender.getNoBlending(),world.getStructureAccessor(),actual)
                    .thenCompose(c->original.populateNoise(Blender.getNoBlending(),noise,world.getStructureAccessor(),c)));
            await(server,amplified.populateBiomes(amplifiedNoise,Blender.getNoBlending(),world.getStructureAccessor(),expected)
                    .thenCompose(c->amplified.populateNoise(Blender.getNoBlending(),amplifiedNoise,world.getStructureAccessor(),c)));
            var p=new BlockPos.Mutable();
            for(int z=0;z<16;z++)for(int x=0;x<16;x++)for(int y=-64;y<=319;y++) {
                p.set(pos.getStartX()+x,y,pos.getStartZ()+z);
                check(actual.getBlockState(p).equals(expected.getBlockState(p)),"Amplified native density differs at "+p);compared++;
            }
            int x=coordinate[0],z=coordinate[1];
            for(int dx=0;dx<256;dx+=16) {
                int px=x<0?x-dx:x+dx;
                int a=original.getHeight(px,z,Heightmap.Type.WORLD_SURFACE_WG,world,noise);
                check(a==amplified.getHeight(px,z,Heightmap.Type.WORLD_SURFACE_WG,world,amplifiedNoise),"Amplified height query differs");
                if(a!=normal.getHeight(px,z,Heightmap.Type.WORLD_SURFACE_WG,world,normalNoise))different++;
            }
        }
        check(different>0,"Amplified terrain remained Normal");
        for(int x:new int[]{127_984,240_000,249_984})check(original.getHeight(x,0,Heightmap.Type.WORLD_SURFACE_WG,world,noise)==normal.getHeight(x,0,Heightmap.Type.WORLD_SURFACE_WG,world,normalNoise),"Amplified leaked beyond its boundary");
        for(int[] p:new int[][]{{252_000,0},{-252_000,0},{0,252_000},{0,-252_000}}) {
            check(!AmplifiedTerrain.contains(p[0],p[1]),"Amplified overlaps Great Sea");
            check(world.getBlockState(new BlockPos(p[0],199,p[1])).isOf(Blocks.WATER),"Great Sea surface changed");
        }
        report.add("Amplified: "+compared+" generated noise states beyond the entrance blend match independent vanilla Amplified controls on four faces and a corner; height queries differ from Normal; 128,000/240,000 bounds and four Great Sea faces pass.");

        int eggs=0,natural=0;
        for(var dimension:server.getWorlds()) {
            dimension.getChunk(0,0);dimension.getRandom().setSeed(193004+eggs);
            for(int i=0;i<20;i++) {
                var mob=EntityType.COW.spawnFromItemStack(dimension,new ItemStack(Items.COW_SPAWN_EGG),null,new BlockPos(8,110,8),SpawnReason.SPAWN_ITEM_USE,true,false);
                check(mob!=null && !mob.isRemoved(),"Spawn egg was blocked in "+dimension.getRegistryKey());mob.discard();eggs++;
            }
            var mob=EntityType.COW.create(dimension,SpawnReason.NATURAL);mob.setPosition(8,110,8);
            for(int i=0;i<30_000;i++)if(dimension.spawnEntity(mob)){natural++;mob.discard();mob=EntityType.COW.create(dimension,SpawnReason.NATURAL);mob.setPosition(8,110,8);}
            var chunk=dimension.getChunk(0,0);var pos=new BlockPos(9,100,9);
            var vault=Blocks.VAULT.getDefaultState();dimension.setBlockState(pos,vault,3);
            check(dimension.getBlockState(pos).isOf(Blocks.VAULT),"Regular vault was removed");
            dimension.setBlockState(pos,vault.with(VaultBlock.OMINOUS,true),3);
            check(dimension.getBlockState(pos).isAir(),"New ominous vault survived");
            // Emulate a saved chunk containing an ominous vault, then invoke its
            // real load callback. Raw palette insertion bypasses placement filtering.
            dimension.setBlockState(pos,vault,3);
            chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(9,pos.getY()&15,9,vault.with(VaultBlock.OMINOUS,true));
            ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(dimension,chunk);
            check(dimension.getBlockState(pos).isAir(),"Saved ominous vault survived chunk loading");
        }
        check(natural>0 && natural<35,"Natural mob rate is incorrect: "+natural+" / 90000");
        report.add("Mobs: "+eggs+" actual cow spawn-egg spawns accepted; "+natural+" / 90,000 natural spawn attempts accepted across all three dimensions.");
        report.add("Vaults: ordinary vaults retained; new ominous vault states and saved ominous vaults removed through the chunk-load callback in all three dimensions.");
        var nether=server.getWorld(World.NETHER);int chunks=0;var p=new BlockPos.Mutable();
        for(int z=0;z<3;z++)for(int x=0;x<3;x++) {
            var chunk=nether.getChunk(x,z);chunks++;
            for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++)for(int y=0;y<128;y++)
                check(!chunk.getBlockState(p.set(x*16+dx,y,z*16+dz)).isOf(Blocks.ANCIENT_DEBRIS),"Ancient debris generated at "+p);
        }
        check(ForbiddenWorldBlocks.ancientReplacements.get()>0,"No real ancient-debris feature attempts were observed");
        report.add("Ancient debris: "+ForbiddenWorldBlocks.ancientReplacements.get()+" real generation placements changed to air; "+chunks+" full Nether chunks contain none.");
    }
}
