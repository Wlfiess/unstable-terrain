package dev.greatsea;

import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Explicit development harness; never runs during normal gameplay. */
final class FarlandsVerifier {
    private static void check(boolean condition,String text) {if(!condition)throw new AssertionError(text);}
    static void run(MinecraftServer server) {
        var report=new ArrayList<String>();long started=System.nanoTime();
        try {
            var world=server.getOverworld();
            check(FarlandsWorld.supported(world),"Requires an Unstable Overworld");
            WorldPresetVerification.verify(server,report);
            var generator=world.getChunkManager().getChunkGenerator();var noise=world.getChunkManager().getNoiseConfig();long seed=world.getSeed();
            var original=new NoiseChunkGenerator(generator.getBiomeSource(),server.getRegistryManager().getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS).getOrThrow(ChunkGeneratorSettings.OVERWORLD));
            int border=GreatSea.FARLANDS.entryBoundary,checked=0;BlockPos.Mutable pos=new BlockPos.Mutable();
            for(var stage:FarlandsTerrain.Stage.values()) {
                int x=border+FarlandsTerrain.start(stage)+GreatSea.FARLANDS.stageWidths[stage.ordinal()]/2;
                int z=16;var chunk=world.getChunk(x>>4,z>>4);
                var column=generator.getColumnSample(x,z,world,noise);
                var terrainColumn=FarlandsTerrain.column(seed,x,z);
                for(int y=-64;y<=319;y++) {
                    var expected=FarlandsGeneration.state(seed,x,y,z);
                    var actual=world.getBlockState(pos.set(x,y,z));
                    // Decoration affects blocks above surfaces and native plants; the base
                    // volume below the highest solid remains exact terrain.
                    if((y<terrainColumn.top()-5 && !expected.isAir()) || (FarlandsTerrain.nativeStage(stage) && stage!=FarlandsTerrain.Stage.CHERRY && stage!=FarlandsTerrain.Stage.DECAY) || stage==FarlandsTerrain.Stage.WATER || stage==FarlandsTerrain.Stage.SKYGRID)
                        check(actual.equals(expected),"Actual "+stage+" base differs at "+pos+": "+actual+" / "+expected);
                    check(column.getState(y).equals(expected),"Column query differs in "+stage+" at "+pos);checked++;
                }
                var expectedBiome=FarlandsBiomes.key(seed,(x>>2)*4+2,(z>>2)*4+2);
                check(chunk.getBiomeForNoiseGen(x>>2,20,z>>2).matchesKey(expectedBiome),"Stored stage biome differs in "+stage);
                check(generator.getBiomeSource().getBiome(x>>2,20,z>>2,noise.getMultiNoiseSampler()).matchesKey(expectedBiome),"Queried stage biome differs in "+stage);
                report.add(stage+": generated actual FULL chunk "+chunk.getPos()+", top="+FarlandsTerrain.column(seed,x,z).top()+", stored/query biome="+expectedBiome.getValue());
                GreatSea.LOGGER.info("FARLANDS_VERIFY_STAGE_PASS {}",stage);
            }
            for(int[] p:new int[][]{{border+320,0},{-border-320,0},{0,border+320},{0,-border-320},{border+320,border+320}}) {
                world.getChunk(p[0]>>4,p[1]>>4);
                check(FarlandsTerrain.stage(p[0],p[1])==FarlandsTerrain.Stage.PLAINS,"First stage wrong on square face/corner");
                check(world.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD),"Farlands left the Overworld");
            }
            int bx=border-16,bz=0;
            var current=generator.getColumnSample(bx,bz,world,noise);var control=original.getColumnSample(bx,bz,world,noise);
            for(int y=-64;y<=319;y++)check(current.getState(y).equals(control.getState(y)),"Mainland before Farlands changed");
            check(!FarlandsWorld.supported(original),"Normal generator opted into Farlands");
            var vanillaFar=original.getColumnSample(border+1300,0,world,noise);
            check(!vanillaFar.getState(-64).isAir(),"Normal world's native base disappeared");
            report.add("Same Overworld: four faces and corner; mainland columns and native Normal generator remain intact. "+checked+" custom column states checked.");
            // Full equality includes the measured bare stone/dirt/grass shelves.
            int repeat=(border+FarlandsTerrain.start(FarlandsTerrain.Stage.REPEATING)+1024)/16*16;
            world.getChunk(repeat>>4,0);world.getChunk((repeat+16)>>4,0);
            int plants=0;
            for(int y=-64;y<320;y++)for(int z=0;z<16;z++)for(int dx=0;dx<16;dx++) {
                var a=world.getBlockState(pos.set(repeat+dx,y,z));
                var b=world.getBlockState(pos.set(repeat+16+dx,y,z));
                check(a.equals(b),"Repeating chunk block states differ at local "+dx+","+y+","+z);
                int m=FarlandsTerrain.material(seed,repeat+dx,y,z);if(m==14 || m==15 || m==27)plants++;
            }
            check(plants==0,"Unrelated native foliage entered the bare Repeating sample");
            report.add("Repeating: 98,304 full block states match the adjacent 16-block tile; the bare source adds no plant/tree blocks.");
            int decayEnd=border+FarlandsTerrain.end(FarlandsTerrain.Stage.DECAY)-1;
            for(int dx=0;dx<16;dx++)world.getChunk((decayEnd-dx)>>4,0);
            for(int dx=0;dx<16;dx++)for(int z=0;z<16;z++)for(int y=-64;y<320;y++) {
                var state=world.getBlockState(pos.set(decayEnd-dx,y,z));
                check(y<=-60 ? state.isOf(Blocks.WATER):state.isAir(),"Decay-to-water join has residual blocks");
            }
            report.add("Decay ends with five basal water blocks at Y=-64..-60 and open void above; no whole-chunk decay masks.");
            int beyond=border+FarlandsTerrain.end(FarlandsTerrain.Stage.SKYGRID)+64;
            world.getChunk(beyond>>4,0);
            for(int y=-64;y<=319;y++)check(world.getBlockState(pos.set(beyond,y,0)).isAir(),"Final SkyGrid did not decay to void");
            int gridStart=border+FarlandsTerrain.start(FarlandsTerrain.Stage.SKYGRID),gridWidth=GreatSea.FARLANDS.stageWidths[FarlandsTerrain.Stage.SKYGRID.ordinal()],gridChecks=0;
            for(int level=0;level<5;level++){
                int depth=FarlandsTerrain.start(FarlandsTerrain.Stage.SKYGRID)+(int)(gridWidth*(.01+.18*level));
                while(FarlandsTerrain.skygridSpacing(depth)==0)depth++;
                int outward=border+depth;
                for(int face=0;face<2;face++)for(int tangent=0;tangent<128;tangent+=4)for(int y=0;y<128;y+=4){
                    int x=face==0?outward:tangent,z=face==0?tangent:outward;
                    var actual=world.getBlockState(pos.set(x,y,z));
                    boolean solid=FarlandsTerrain.material(seed,x,y,z)!=0;
                    check(solid?actual.isOf(Blocks.GRASS_BLOCK):actual.isAir(),"SkyGrid spacing mismatch at "+pos);gridChecks++;
                }
            }
            int earlyVoid=gridStart+(int)(gridWidth*.91);
            for(int face=0;face<2;face++)for(int y=-64;y<320;y++){
                int x=face==0?earlyVoid:0,z=face==0?0:earlyVoid;
                check(world.getBlockState(pos.set(x,y,z)).isAir(),"SkyGrid did not reach early void at "+pos);gridChecks++;
            }
            report.add("SkyGrid: "+gridChecks+" actual block checks on both positive faces verify a five-empty-block entrance, gradually widening spacing, population thinning and open void from 90% of the region.");
            WorldOverviewExporter.export(seed);
            int beachChecked=0;
            for(int d:new int[]{96,176,304,416,512,608,720,864,944})for(int across:new int[]{32,96,192,256}) {
                int x=GreatSea.CONFIG.innerBoundary+d,z=across;
                var chunk=world.getChunk(x>>4,z>>4);
                int expected=(int)Math.round(SeaTerrain.height(GreatSea.CONFIG,world.getSeed(),x,z,64));
                int actual=-65;
                for(int y=319;y>=-64;y--)if(world.getBlockState(pos.set(x,y,z)).isOf(Blocks.SAND)){actual=y;break;}
                check(actual==expected,"Restored beach differs at "+x+","+z+": "+actual+" / "+expected);beachChecked++;
            }
            report.add("Reference Great Sea beach: "+beachChecked+" actual FULL-chunk sand columns match the filled, filtered source height profile.");
            int filled=0;
            for(int d:new int[]{192,288,400,496})for(int z:new int[]{32,96,192,256}) {
                int x=GreatSea.CONFIG.innerBoundary+d,top=(int)Math.round(SeaTerrain.height(GreatSea.CONFIG,seed,x,z,64));
                for(int y=-55;y<=top;y++) {check(world.getBlockState(pos.set(x,y,z)).isSolid(),"Cave cutout in filled beach at "+pos);filled++;}
            }
            report.add("Beach cave exclusion: "+filled+" actual below-surface blocks are solid.");
            int templeBlocks=0,templeX=border+FarlandsTemple.center()-FarlandsTemple.OFFSET_X,templeZ=-FarlandsTemple.SAMPLE.depth/2;
            for(int z=0;z<FarlandsTemple.SAMPLE.depth;z++)for(int x=0;x<FarlandsTemple.SAMPLE.width;x++) {
                if(x%16==0 && z%16==0)world.getChunk((templeX+x)>>4,(templeZ+z)>>4);
                for(int y=0;y<FarlandsTemple.SAMPLE.height;y++) {
                    if(FarlandsTemple.SAMPLE.material(x,y,z)==0)continue;
                    check(!FarlandsTemple.SAMPLE.state(x,y,z).isAir(),"Unknown temple block state in source at "+x+","+y+","+z);
                    check(world.getBlockState(pos.set(templeX+x,145+y,templeZ+z)).equals(FarlandsTemple.SAMPLE.state(x,y,z)),"Temple schematic block mismatch at "+pos);templeBlocks++;
                }
            }
            check(templeBlocks==125356,"Temple block count differs from the supplied schematic");
            report.add("Lakerek temple: all "+templeBlocks+" non-air schematic block states match actual FULL chunks, centered where eight bare corridors converge.");
            FarlandsTempleVerifier.verify(world,report);
            int corrupted=border+FarlandsTerrain.start(FarlandsTerrain.Stage.CORRUPTED)+1024;
            for(int cx=-1;cx<=1;cx++)for(int cz=-1;cz<=1;cz++) {
                var chunk=world.getChunk((corrupted>>4)+cx,cz);
                for(int z=0;z<16;z++)for(int x=0;x<16;x++)for(int y=-64;y<320;y++) {
                    var state=chunk.getBlockState(pos.set(chunk.getPos().getStartX()+x,y,chunk.getPos().getStartZ()+z));
                    check(!state.isIn(net.minecraft.registry.tag.BlockTags.FLOWERS) && !state.isOf(Blocks.SHORT_GRASS) && !state.isOf(Blocks.TALL_GRASS) && !state.isOf(Blocks.FERN) && !state.isOf(Blocks.LARGE_FERN),"Plant in Corrupted Farlands at "+pos);
                }
            }
            report.add("Corrupted Farlands: nine fresh FULL chunks contain no flowers, grass plants or ferns.");
            FarlandsMechanicsVerifier.verify(server,report);
            if(Boolean.getBoolean("unstableterrain.previewFarlands")){FarlandsPerspective.renderAll(world,report);ReliefMapExporter.export(seed);}
            // Deterministic terrain data renders, derived from the same state function
            // verified above against FULL Minecraft chunks.
            var csv=new StringBuilder("stage,x,y,z,material\n");
            for(var stage:FarlandsTerrain.Stage.values()) {
                int center=(border+FarlandsTerrain.start(stage)+GreatSea.FARLANDS.stageWidths[stage.ordinal()]/2)/4*4;
                for(int z=-96;z<96;z+=4)for(int x=center-96;x<center+96;x+=4)for(int y=-64;y<320;y++) {
                    int m=FarlandsTerrain.material(seed,x,y,z);
                    if(m!=0)csv.append(stage).append(',').append(x-center).append(',').append(y).append(',').append(z).append(',').append(m).append('\n');
                }
            }
            Files.writeString(Path.of("farlands-preview.csv"),csv);
            report.add("PASS in "+(System.nanoTime()-started)/1e9+" seconds");
            Files.write(Path.of("farlands-verification.txt"),report);
            GreatSea.LOGGER.info("FARLANDS_VERIFICATION_PASS");
        }catch(Throwable t) {
            report.add("FAIL: "+t);try{Files.write(Path.of("farlands-verification.txt"),report);}catch(Exception ignored){}
            GreatSea.LOGGER.error("FARLANDS_VERIFICATION_FAILED",t);
        }finally{server.stop(false);}
    }
}

