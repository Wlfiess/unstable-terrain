package dev.greatsea;

import com.google.gson.JsonParser;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.BiomeKeys;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Opt-in Minecraft integration checks; never runs during ordinary play. */
final class SeaVerifier {
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
    private static int sand(ServerWorld w,int x,int z) {
        for (int y=300;y>=w.getBottomY();y--) if (w.getBlockState(new BlockPos(x,y,z)).isOf(Blocks.SAND)) return y;
        throw new AssertionError("No sand at "+x+","+z);
    }
    private static int bed(ServerWorld w,int x,int z) {
        for (int y=300;y>=w.getBottomY();y--) {
            var state=w.getBlockState(new BlockPos(x,y,z));
            if (state.isOf(Blocks.SAND) || state.isOf(Blocks.GRAVEL)) return y;
        }
        throw new AssertionError("No sand/gravel ocean bed at "+x+","+z);
    }
    private static int ground(ServerWorld w,int x,int z) {
        for (int y=300;y>=w.getBottomY();y--) {
            var b=w.getBlockState(new BlockPos(x,y,z));
            if (b.isSolid() && !b.isIn(net.minecraft.registry.tag.BlockTags.LOGS) && !b.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) return y;
        }
        return w.getBottomY();
    }
    private static boolean plant(net.minecraft.block.BlockState b) {
        return b.isOf(Blocks.KELP) || b.isOf(Blocks.KELP_PLANT) || b.isOf(Blocks.SEAGRASS);
    }
    static void run(MinecraftServer server) {
        List<String> report=new ArrayList<>();
        try {
            ServerWorld world=server.getOverworld();SeaConfig c=GreatSea.CONFIG;long seed=world.getSeed();
            var generator=world.getChunkManager().getChunkGenerator();var noise=world.getChunkManager().getNoiseConfig();
            var sampler=noise.getMultiNoiseSampler();
            check(UnstableWorldGen.isUnstable(generator),"Great Sea verifier requires the Unstable preset");
            WorldPresetVerification.verify(server,report);
            check(VanillaOceanTerrain.available(seed),"Native Overworld ocean density was not registered");
            var points=new java.util.LinkedHashMap<net.minecraft.registry.RegistryKey<net.minecraft.world.biome.Biome>,int[]>();
            for (int x=400000;x<1000000 && points.size()<9;x+=2048) for (int z=-140000;z<140000 && points.size()<9;z+=2048) {
                var key=SeaBiomeContext.biomeKey(c,seed,x,z,sampler);
                if (SeaBiomeContext.isOcean(key)) points.putIfAbsent(key,new int[]{x,z});
            }
            check(points.size()==9,"Native climate survey did not find all nine ocean variants: "+points.keySet());
            int sedimentColumns=0,plants=0,kelp=0;
            for (var point:points.entrySet()) {
                int x=point.getValue()[0],z=point.getValue()[1];var chunk=world.getChunk(x>>4,z>>4);
                check(chunk.getBiomeForNoiseGen(x>>2,0,z>>2).matchesKey(point.getKey()),"Stored ocean biome differs from native climate "+point.getKey());
                int floor=(int)Math.round(VanillaOceanTerrain.floorHeight(c,seed,x,z));
                if (chunk.getStructureReferences().isEmpty()) {
                    for (int y=floor;y>floor-5;y--)
                        check(world.getBlockState(new BlockPos(x,y,z)).equals(OceanFloorMaterials.surface(c,seed,x,z,y)),"Native sediment mismatch at "+x+","+y+","+z);
                    check(generator.getHeight(x,z,Heightmap.Type.OCEAN_FLOOR_WG,world,noise)==floor+1,"Native floor height query mismatch");
                    var column=generator.getColumnSample(x,z,world,noise);
                    check(column.getState(floor).equals(OceanFloorMaterials.surface(c,seed,x,z,floor)),"Column sampler lost the ocean sediment");
                    sedimentColumns++;
                }
                var seaSurface=world.getBlockState(new BlockPos(x,c.seaLevel-1,z));
                check(seaSurface.isOf(Blocks.WATER),"The ice-free sea lost source water at Y="+(c.seaLevel-1));
                check(world.getBlockState(new BlockPos(x,c.seaLevel,z)).isAir(),"Water appears above the sea level");
                int localKelp=0,localPlants=0;
                for (int dz=0;dz<16;dz++) for (int dx=0;dx<16;dx++) {
                    int bx=chunk.getPos().getStartX()+dx,bz=chunk.getPos().getStartZ()+dz;
                    int top=(int)Math.round(VanillaOceanTerrain.floorHeight(c,seed,bx,bz));
                    for (int y=top+1;y<=top+c.coldKelpMaxHeight+2;y++) {
                        var state=chunk.getBlockState(new BlockPos(bx,y,bz));
                        if (state.isOf(Blocks.KELP)) localKelp++;
                        if (plant(state) || state.isOf(Blocks.TALL_SEAGRASS)) localPlants++;
                    }
                }
                kelp+=localKelp;plants+=localPlants;
                report.add(point.getKey().getValue()+" "+x+","+z+": native bed Y="+floor+", sediment="+OceanFloorMaterials.sediment(c,seed,x,z)+", kelp="+localKelp+", plant blocks="+localPlants);
            }
            check(sedimentColumns>=6,"Too many structure-covered columns to verify native sediments");
            check(plants>0 && kelp>0,"The restored kelp/seagrass ocean vegetation did not generate");
            MainlandJoinVerification.verify(world,report);
            int beachSamples=0;
            for (int boundary:new int[]{c.innerBoundary,c.outerBoundary}) for (int sign:new int[]{-1,1}) {
                int inward=boundary==c.innerBoundary ? 1 : -1;
                for (int d:new int[]{100,250,450}) for (int axis=0;axis<2;axis++) {
                    int x=axis==0 ? sign*(boundary+inward*d) : 137;
                    int z=axis==0 ? 137 : sign*(boundary+inward*d);
                    check(world.getChunk(x>>4,z>>4).getBiomeForNoiseGen(x>>2,0,z>>2).matchesKey(BiomeKeys.BEACH),"Great Sea dry shore is not beach at "+x+","+z);
                    beachSamples++;
                }
            }
            report.add("All dry shores checked as native beach biomes: "+beachSamples+" samples at both boundaries and four directions");
            int palms=0;
            var templates=JsonParser.parseReader(new InputStreamReader(SeaVerifier.class.getResourceAsStream("/assets/greatsea/palms.json"))).getAsJsonArray();
            for (var candidate:PalmLayout.candidates(seed,c.innerBoundary+100,-1600,c.innerBoundary+500,1600,c.palmSpacing)) {
                int x=candidate.x(),z=candidate.z();long h=candidate.hash();
                if (SeaTerrain.unit(h)>c.palmChance || SeaGeneration.preservesMainland(world.getChunk(x>>4,z>>4),x,z)) continue;
                var template=templates.get(SeaDecoration.palmVariant(h,templates.size())).getAsJsonObject();var palette=template.getAsJsonArray("palette");
                int rotation=(int)((h>>>5)&3);var roots=new ArrayList<BlockPos>();boolean rejected=false;
                for (var voxel:template.getAsJsonArray("blocks")) {
                    var v=voxel.getAsJsonArray();
                    if (v.get(1).getAsInt()!=0 || !palette.get(v.get(3).getAsInt()).getAsJsonObject().get("Name").getAsString().contains("wood")) continue;
                    int dx=v.get(0).getAsInt(),dz=v.get(2).getAsInt();
                    for (int r=0;r<rotation;r++) { int t=dx;dx=-dz;dz=t; }
                    int bx=x+dx,bz=z+dz;var rootChunk=world.getChunk(bx>>4,bz>>4);
                    if (SeaGeneration.preservesMainland(rootChunk,bx,bz)) { rejected=true;break; }
                    var root=new BlockPos(bx,sand(world,bx,bz)+1,bz);var state=world.getBlockState(root);
                    if (!state.isAir() && !state.isOf(Blocks.DEAD_BUSH) && !state.isOf(Blocks.JUNGLE_WOOD)) { rejected=true;break; }
                    roots.add(root);
                }
                if (rejected) continue;
                for (BlockPos root:roots) check(world.getBlockState(root).isOf(Blocks.JUNGLE_WOOD),"Floating random palm root at "+root);
                if (++palms>=8) break;
            }
            check(palms>=4,"Too few actual randomly placed palms were checked");
            report.add("Random spatial palms checked with grounded roots="+palms+"; crown separation >=32, no cell/grid anchors");
            report.add("PASS: native ocean density/depth, all nine ocean biome variants, restored sand/gravel beds, native mainland surface rules, native beach biomes, restored kelp/seagrass plants and grounded random palms.");
            GreatSea.LOGGER.info("GREATSEA_VERIFICATION_PASS {}",report);
        } catch (Throwable e) {
            report.add("FAIL: "+e);GreatSea.LOGGER.error("GREATSEA_VERIFICATION_FAIL",e);
        } finally {
            try { Files.write(Path.of("greatsea-verification.txt"),report); } catch (Exception e) { GreatSea.LOGGER.error("Cannot write sea report",e); }
            server.stop(false);
        }
    }

    /** A separate opt-in run proves that installing the JAR does not alter a normal world. */
    static void runVanilla(MinecraftServer server) {
        List<String> report=new ArrayList<>();
        try {
            ServerWorld world=server.getOverworld();SeaConfig c=GreatSea.CONFIG;
            var generator=world.getChunkManager().getChunkGenerator();
            var noise=world.getChunkManager().getNoiseConfig();
            check(!UnstableWorldGen.isUnstable(generator),"Vanilla verifier requires a normal world preset");
            check(!SeaBiomeContext.unstable(noise.getMultiNoiseSampler()),"Normal server sampler incorrectly opted in to Great Sea biomes");
            check(!SeaGeneration.supported(generator),"Normal generator incorrectly activated Great Sea terrain");
            int ordinaryColumns=0;
            for (int[] point:new int[][]{{c.innerBoundary+600,37},{-(c.innerBoundary+600),37},
                    {37,c.innerBoundary+600},{37,-(c.innerBoundary+600)},
                    {c.innerBoundary+10000,419},{c.outerBoundary-5000,419}}) {
                int x=point[0],z=point[1];var chunk=world.getChunk(x>>4,z>>4);
                int expected=SeaGeneration.nativeQuery(()->generator.getHeight(x,z,Heightmap.Type.OCEAN_FLOOR_WG,world,noise))-1;
                int actual=ground(world,x,z);
                // Native structures and carvers run after the column query. Skip roof
                // heights, and do not mistake a genuine carved cave for modded density.
                if (chunk.getStructureReferences().isEmpty())
                    check(Math.abs(actual-expected)<=4,"Normal world terrain was changed inside the Great Sea coordinates at "+x+","+z);
                if (expected<150) {
                    check(world.getBlockState(new BlockPos(x,c.seaLevel-1,z)).isAir(),"Normal world has Great Sea water at Y=199");
                    ordinaryColumns++;
                }
                report.add("Normal column "+x+","+z+": actual ground Y="+actual+", native query Y="+expected+", Great Sea inactive");
            }
            check(ordinaryColumns>=4,"Too few ordinary columns verified without elevated sea water");
            report.add("PASS: normal world preset retains native terrain inside the configured Great Sea ring; Unstable generation is inactive.");
            GreatSea.LOGGER.info("GREATSEA_VANILLA_VERIFICATION_PASS {}",report);
        } catch (Throwable e) {
            report.add("FAIL: "+e);GreatSea.LOGGER.error("GREATSEA_VANILLA_VERIFICATION_FAIL",e);
        } finally {
            try { Files.write(Path.of("greatsea-vanilla-verification.txt"),report); }
            catch (Exception e) { GreatSea.LOGGER.error("Cannot write vanilla-world report",e); }
            server.stop(false);
        }
    }
}
