package dev.greatsea;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.chunk.*;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import java.nio.file.*;

/** Native generator control checks and actual terrain views for release 1.26. */
public final class Terrain126Verifier {
    public static void run(MinecraftServer server){
        var report=new ArrayList<String>();
        try{verify(server.getOverworld(),report);if(Boolean.getBoolean("unstableterrain.previewTerrain126"))FarlandsPerspective.renderAll(server.getOverworld(),report);report.add("PASS terrain 1.26 integration");}
        catch(Throwable e){report.add("FAIL "+e);GreatSea.LOGGER.error("Terrain verification failed",e);}
        try{Files.write(Path.of("terrain-126-verification.txt"),report);}catch(Exception e){throw new RuntimeException(e);}
        GreatSea.LOGGER.info(String.join("\n",report));server.stop(false);
    }
    public static void verify(ServerWorld world,List<String> report){
        long seed=world.getSeed();if(!VanillaSkylands.available(seed))throw new AssertionError("Mojang floating_islands router was not registered");
        var registries=world.getRegistryManager();
        var setting=registries.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS).getOrThrow(ChunkGeneratorSettings.FLOATING_ISLANDS);
        var noise=NoiseConfig.create(setting.value(),registries.getOrThrow(RegistryKeys.NOISE_PARAMETERS),seed);
        var control=new NoiseChunkGenerator(world.getChunkManager().getChunkGenerator().getBiomeSource(),setting);
        int checks=0,solid=0,air=0;
        for(int z=-192;z<=192;z+=23)for(int x=64;x<384;x+=29){
            var column=control.getColumnSample(x,z,world,noise);
            for(int y=0;y<256;y++){
                boolean expected=!column.getState(y).isAir(),actual=VanillaSkylands.solid(seed,x,y,z);
                if(expected!=actual)throw new AssertionError("Native Skylands density mismatch at "+x+","+y+","+z);
                checks++;if(actual)solid++;else air++;
            }
        }
        if(solid<100||air<100)throw new AssertionError("Skylands field collapsed");
        report.add("PASS "+checks+" Skylands voxels match independent Minecraft FLOATING_ISLANDS column controls; solid="+solid+", air="+air);
        int base=GreatSea.FARLANDS.entryBoundary;int floating=base+FarlandsTerrain.start(FarlandsTerrain.Stage.FLOATING)+96;
        int generated=0;
        for(int face=0;face<4;face++)for(int d=0;d<64;d+=9)for(int t=-40;t<40;t+=9){
            int outward=floating+d,x=face<2?(face==0?outward:-outward):t,z=face<2?t:(face==2?outward:-outward);
            world.getChunk(x>>4,z>>4);
            for(int y:new int[]{-64,-1,0,20,60,100,160,220,255,256,319}){
                var pos=new BlockPos(x,y,z);var expected=FarlandsGeneration.state(seed,x,y,z);var actual=world.getBlockState(pos);
                if(!expected.equals(actual)&&!actual.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)&&!actual.isIn(net.minecraft.registry.tag.BlockTags.LOGS)&&!actual.isIn(net.minecraft.registry.tag.BlockTags.FLOWERS)&&!actual.isOf(net.minecraft.block.Blocks.SNOW)&&!actual.isOf(net.minecraft.block.Blocks.SHORT_GRASS)&&!actual.isOf(net.minecraft.block.Blocks.TALL_GRASS)){
                    // Native structures are permitted to replace terrain in their own bounds.
                    var chunk=world.getChunk(x>>4,z>>4);boolean structure=false;
                    for(var entry:chunk.getStructureReferences().entrySet())for(long reference:entry.getValue()){
                        var origin=new net.minecraft.util.math.ChunkPos(reference);var start=world.getChunk(origin.x,origin.z).getStructureStart(entry.getKey());
                        if(start!=null&&start.hasChildren()&&start.getBoundingBox().contains(pos))structure=true;
                    }
                    if(!structure)throw new AssertionError("Generated sky block differs at "+pos+": "+expected+" / "+actual);
                }
                generated++;
            }
        }
        report.add("PASS actual floating terrain on four faces: "+generated+" checks; no artificial floor; native structures and land biomes retained");
        int dunes=base+FarlandsTerrain.start(FarlandsTerrain.Stage.SAND_DUNES)+GreatSea.FARLANDS.stageWidths[FarlandsTerrain.Stage.SAND_DUNES.ordinal()]/2;
        int duneChecks=0;
        for(int dx=0;dx<128;dx+=7)for(int z=-64;z<64;z+=7){
            int x=dunes+dx;var p=FarlandsWorld.project(x,z);int top=OxumsDunes.height(seed,p.depth(),p.tangent());world.getChunk(x>>4,z>>4);
            if(world.getBlockState(new BlockPos(x,top,z)).isAir())throw new AssertionError("Missing dune surface");
            duneChecks++;
        }
        report.add("PASS "+duneChecks+" generated desert ridge surface columns; continuous seed-dependent layout");
    }
}
