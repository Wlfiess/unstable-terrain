package dev.greatsea;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Opt-in development checks of fresh FULL chunks, including their real decoration.
 * The caller owns report persistence, preview rendering and server shutdown. */
public final class SpokelandsVerifier {
    private SpokelandsVerifier() {}
    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
    private static final class Counts {
        final Map<Block,Long> blocks=new HashMap<>();
        final Set<Long> inspectedChunks=new HashSet<>();
        long columns,solidBaseChecks,flowers,logs,leaves,rootColumns,overheadGrass,aboveGroundGray;
        int lowest=Integer.MAX_VALUE,highest=Integer.MIN_VALUE;
        long count(Block block){return blocks.getOrDefault(block,0L);}
        void block(BlockState state){blocks.merge(state.getBlock(),1L,Long::sum);}
    }

    public static void verify(ServerWorld world,List<String> report) {
        check(world.getRegistryKey().equals(World.OVERWORLD)&&FarlandsWorld.enabledOverworld(world),
                "Spokelands verifier requires an enabled Unstable Overworld");
        long began=System.nanoTime(),seed=world.getSeed();int routes=0,columnChecks=0;
        int boundary=GreatSea.FARLANDS.entryBoundary;
        var generator=world.getChunkManager().getChunkGenerator();
        var noise=world.getChunkManager().getNoiseConfig();
        var position=new BlockPos.Mutable();
        // Exercise both independent negative faces, at each entrance and in its interior.
        for(var region:SpokelandsTerrain.Region.values())for(boolean west:new boolean[]{false,true}) {
            int width=GreatSea.FARLANDS.spokelandsWidths[region.ordinal()];
            int middle=boundary+SpokelandsTerrain.start(region)+width/2;
            var counts=new Counts();
            int chunks=switch(region){
                case RED_PLAINS,DARK_FOREST,RAINBOW_FOREST,FLOWER_HILLS,WINDING -> 4;
                case BRIDGES,SCULK_FOREST -> 6;
                default -> 2;
            };
            scan(world,region,west,middle,0,chunks,counts);
            // Rare giant-tree proposals and bridges must be observed, not assumed.
            if(!complete(region,counts))scan(world,region,west,middle,96,chunks,counts);
            if(!complete(region,counts))scan(world,region,west,middle,-96,chunks,counts);
            assertPalette(region,counts);
            int entrance=boundary+SpokelandsTerrain.start(region)+Math.min(64,width/4);
            for(int outward:new int[]{entrance,middle}) {
                int x=west?-outward:16,z=west?16:-outward;
                check(SpokelandsTerrain.contains(x,z)&&SpokelandsTerrain.region(x,z)==region,
                        "Spokelands region/face routing differs at "+x+","+z);
                check(!SpokelandsTerrain.contains(west?outward:16,west?16:outward),
                        "Spokelands replaced a positive face");
                var chunk=world.getChunk(x>>4,z>>4);
                var queried=generator.getColumnSample(x,z,world,noise);
                for(int y=world.getBottomY();y<=world.getTopYInclusive();y++) {
                    var expected=SpokelandsTerrain.state(seed,x,y,z);
                    check(queried.getState(y).equals(expected),"Spokelands column query mismatch "+position.set(x,y,z));
                    check(FarlandsGeneration.state(seed,x,y,z).equals(expected),
                            "Old landmark/profile overrode Spokelands "+position);
                    columnChecks++;
                }
                int qx=x>>2,qz=z>>2;
                var biome=SpokelandsTerrain.biome(seed,qx*4+2,qz*4+2);
                check(chunk.getBiomeForNoiseGen(qx,20,qz).matchesKey(biome),
                        "Stored Spokelands biome mismatch "+region+" "+x+","+z);
                check(generator.getBiomeSource().getBiome(qx,20,qz,noise.getMultiNoiseSampler()).matchesKey(biome),
                        "Queried Spokelands biome mismatch "+region+" "+x+","+z);
            }
            report.add("PASS Spokelands "+region+" "+(west?"west":"north")+": "+counts.inspectedChunks.size()+
                    " FULL chunks, "+counts.columns+" columns, ground="+counts.lowest+".."+counts.highest+
                    ", flowers="+counts.flowers+", logs="+counts.logs+", leaves="+counts.leaves+
                    ", roots="+counts.rootColumns+", crimson roots="+counts.count(Blocks.CRIMSON_ROOTS)+
                    ", warped roots="+counts.count(Blocks.WARPED_ROOTS)+", lanterns="+counts.count(Blocks.SEA_LANTERN)+
                    ", glass="+(counts.count(Blocks.GLASS)+counts.count(Blocks.LIGHT_BLUE_STAINED_GLASS)+counts.count(Blocks.CYAN_STAINED_GLASS))+", bridge planks="+counts.count(Blocks.OAK_PLANKS)+
                    ", conserved base voxels="+counts.solidBaseChecks);
            GreatSea.LOGGER.info("SPOKELANDS_NATIVE_REGION_PASS {} {}",region,west?"west":"north");routes++;
        }
        int riverColumns=0;
        int riverDepth=SpokelandsTerrain.start(SpokelandsTerrain.Region.SCULK_FOREST)+GreatSea.FARLANDS.spokelandsWidths[8]/2;
        int riverTangent=(int)Math.round(SpokelandsTerrain.riverCenter(seed,riverDepth));
        for(boolean west:new boolean[]{false,true}){
            int centerX=west?-boundary-riverDepth:riverTangent,centerZ=west?riverTangent:-boundary-riverDepth;
            for(int cz=(centerZ>>4)-2;cz<=(centerZ>>4)+2;cz++)for(int cx=(centerX>>4)-2;cx<=(centerX>>4)+2;cx++){
                var chunk=world.getChunk(cx,cz);
                for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++){
                    int x=cx*16+dx,z=cz*16+dz;
                    if(SpokelandsTerrain.material(seed,x,141,z)!=8)continue;
                    riverColumns++;
                    for(int y=142;y<320;y++)check(chunk.getBlockState(position.set(x,y,z)).isAir(),"Tree overhang above river at "+position);
                }
            }
        }
        check(riverColumns>1000,"River-clearance verification did not inspect real water");
        report.add("PASS no tree blocks above "+riverColumns+" native river columns on both negative faces");
        // Sample where the previous negative-face landmarks would have occupied the route.
        // The actual chunk must contain neither their block entities nor their terrain override.
        int landmarkColumns=0;
        for(int depth:new int[]{FarlandsEmpire.origin()+FarlandsEmpire.SAMPLE.width/2,
                FarlandsTemple.center(),FarlandsTerrain.start(FarlandsTerrain.Stage.ORBITALS)+1024}) {
            for(boolean west:new boolean[]{false,true})for(int tangent:new int[]{-640,-512,0}) {
                int outward=boundary+depth,x=west?-outward:tangent,z=west?tangent:-outward;
                var region=SpokelandsTerrain.region(x,z);var chunk=world.getChunk(x>>4,z>>4);
                assertStructures(world,chunk,region);
                for(int y=-64;y<320;y++)check(FarlandsGeneration.state(seed,x,y,z).equals(SpokelandsTerrain.state(seed,x,y,z)),
                        "Old negative landmark still alters raw terrain at "+position.set(x,y,z));
                landmarkColumns++;
            }
        }
        report.add("PASS Spokelands: "+routes+" native region/face volumes, "+columnChecks+
                " column-query states, "+landmarkColumns+" previous landmark positions; no corrupted chunk masks or forbidden structures; "+
                "elapsed="+(System.nanoTime()-began)/1e9+" seconds");
    }

    private static void scan(ServerWorld world,SpokelandsTerrain.Region region,boolean west,int outward,int tangent,
                             int size,Counts counts) {
        long seed=world.getSeed();int centerX=west?-outward:tangent,centerZ=west?tangent:-outward;
        int firstX=(centerX>>4)-size/2,firstZ=(centerZ>>4)-size/2;
        var position=new BlockPos.Mutable();
        for(int dz=0;dz<size;dz++)for(int dx=0;dx<size;dx++) {
            int cx=firstX+dx,cz=firstZ+dz;long key=((long)cx<<32)^(cz&0xffffffffL);
            if(!counts.inspectedChunks.add(key))continue;
            var chunk=world.getChunk(cx,cz);assertStructures(world,chunk,region);
            long chunkSolids=0;
            for(int z=cz*16;z<cz*16+16;z++)for(int x=cx*16;x<cx*16+16;x++) {
                check(SpokelandsTerrain.contains(x,z)&&SpokelandsTerrain.region(x,z)==region,"Native volume crossed a Spokelands boundary");
                check(!FarlandsTerrain.missingChunk(seed,x,z),"Corrupted missing-chunk mask leaked into Spokelands");
                int ground=SpokelandsTerrain.ground(seed,x,z);counts.lowest=Math.min(counts.lowest,ground);
                counts.highest=Math.max(counts.highest,ground);counts.columns++;
                boolean rooted=false;
                for(int y=world.getBottomY();y<=world.getTopYInclusive();y++) {
                    var actual=chunk.getBlockState(position.set(x,y,z));counts.block(actual);
                    check(!actual.isOf(Blocks.ANCIENT_DEBRIS)&&!ForbiddenWorldBlocks.ominousVault(actual),"Forbidden generated block in Spokelands at "+position);
                    check(!actual.isOf(Blocks.TRIAL_SPAWNER),"Trial chamber leaked into Spokelands at "+position);
                    if(actual.isSolid())chunkSolids++;
                    if(actual.isIn(BlockTags.FLOWERS))counts.flowers++;
                    if(actual.isIn(BlockTags.LOGS))counts.logs++;
                    if(actual.isIn(BlockTags.LEAVES))counts.leaves++;
                    if(y==ground+1&&(actual.isIn(BlockTags.LOGS)||actual.isOf(Blocks.WARPED_STEM)||actual.isOf(Blocks.SMOOTH_STONE)||actual.isOf(Blocks.ANDESITE)))rooted=true;
                    if(region==SpokelandsTerrain.Region.SCULK_FOREST&&y==ground+1&&(actual.isOf(Blocks.WARPED_PLANKS)||actual.isOf(Blocks.MOSSY_COBBLESTONE)||actual.isOf(Blocks.GREEN_CONCRETE)||actual.isOf(Blocks.BROWN_TERRACOTTA)))rooted=true;
                    if(y>ground+12&&actual.isOf(Blocks.GRASS_BLOCK))counts.overheadGrass++;
                    if(y>ground&&(actual.isOf(Blocks.SMOOTH_STONE)||actual.isOf(Blocks.ANDESITE)))counts.aboveGroundGray++;
                    // Custom decoration may fill air and native trees can convert their
                    // supporting grass to dirt. Deeper terrain must remain the same volume.
                    if(y<ground-5) {
                        var expected=SpokelandsTerrain.state(seed,x,y,z);
                        if(!expected.isAir()) {
                            boolean treeSoil=expected.isOf(Blocks.GRASS_BLOCK)&&actual.isOf(Blocks.DIRT);
                            check(actual.equals(expected)||treeSoil,"Spokelands base changed at "+position+": "+actual+" / "+expected);
                            counts.solidBaseChecks++;
                        }
                    }
                }
                if(rooted)counts.rootColumns++;
            }
            // The source-derived Classic wall can have genuine full-height passages.
            // Other surface regions must never become an entirely empty download tile.
            if(region!=SpokelandsTerrain.Region.CLASSIC)
                check(chunkSolids>0,"Entire missing/cut-off chunk in Spokelands at "+chunk.getPos());
        }
    }

    private static void assertStructures(ServerWorld world,WorldChunk chunk,SpokelandsTerrain.Region region) {
        var registry=world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
        for(var entry:chunk.getStructureStarts().entrySet()) {
            if(!entry.getValue().hasChildren())continue;
            String key=registry.getKey(entry.getKey()).orElseThrow().getValue().getPath();
            check(region==SpokelandsTerrain.Region.RED_PLAINS&&(key.equals("mineshaft")||key.equals("mineshaft_mesa")),
                    "Unrelated native structure "+key+" in Spokelands "+region);
        }
        if(region!=SpokelandsTerrain.Region.RED_PLAINS)check(chunk.getBlockEntities().isEmpty(),
                "Old landmark block entities in Spokelands "+region+" at "+chunk.getPos());
    }
    private static boolean complete(SpokelandsTerrain.Region region,Counts counts) {
        return switch(region) {
            case CLASSIC -> counts.count(Blocks.GRASS_BLOCK)>0;
            case FLOWER_HILLS -> counts.flowers>100&&counts.highest>counts.lowest;
            case RED_PLAINS -> counts.count(Blocks.CRIMSON_NYLIUM)>100&&counts.count(Blocks.CRIMSON_ROOTS)>100&&counts.count(Blocks.PALE_OAK_LEAVES)>100&&counts.count(Blocks.STRIPPED_PALE_OAK_WOOD)>10&&counts.count(Blocks.PALE_OAK_STAIRS)>10&&counts.count(Blocks.DEAD_HORN_CORAL_FAN)>10;
            case WINDING -> counts.overheadGrass>10;
            case DARK_FOREST -> counts.count(Blocks.STRIPPED_JUNGLE_WOOD)+counts.count(Blocks.OAK_WOOD)+counts.count(Blocks.BAMBOO_BLOCK)>100&&counts.leaves>100&&counts.count(Blocks.VINE)>0&&counts.count(Blocks.JUNGLE_STAIRS)+counts.count(Blocks.SPRUCE_STAIRS)>0;
            case BRIDGES -> counts.count(Blocks.DRIPSTONE_BLOCK)>1000&&counts.count(Blocks.OAK_PLANKS)+counts.count(Blocks.OAK_STAIRS)+counts.count(Blocks.OAK_SLAB)>10&&counts.count(Blocks.MOSS_BLOCK)>20;
            case RAINBOW_FOREST -> counts.flowers>100&&counts.count(Blocks.CHERRY_LEAVES)>30&&counts.count(Blocks.BIRCH_LEAVES)>30&&counts.leaves>500&&counts.logs>100;
            case SAND_LANDS -> counts.count(Blocks.RED_SAND)>100&&counts.count(Blocks.TERRACOTTA)+counts.count(Blocks.ORANGE_TERRACOTTA)>1000&&counts.highest-counts.lowest>4;
            case SCULK_FOREST -> counts.count(Blocks.WARPED_NYLIUM)>100&&counts.count(Blocks.MUD)>20&&counts.count(Blocks.LIGHT_BLUE_STAINED_GLASS)>20&&counts.count(Blocks.WARPED_WART_BLOCK)>100&&counts.count(Blocks.WARPED_ROOTS)>10;
        };
    }
    private static void assertPalette(SpokelandsTerrain.Region region,Counts counts) {
        check(complete(region,counts),"Missing native Spokelands palette/geometry for "+region+": "+counts.blocks);
        if(region==SpokelandsTerrain.Region.SAND_LANDS)check(counts.count(Blocks.CACTUS)==0,
                "Cacti appeared in the reference's bare red-sand biome");
        if(region==SpokelandsTerrain.Region.RAINBOW_FOREST||region==SpokelandsTerrain.Region.DARK_FOREST||region==SpokelandsTerrain.Region.SCULK_FOREST)
            check(counts.rootColumns>0,"Custom trees lack supported roots in "+region);
    }
}

