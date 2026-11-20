package dev.greatsea;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Opt-in checks of the real mainland surface, including a hill above the old Y=70 cut. */
final class MainlandJoinVerification {
    private MainlandJoinVerification() {}
    private static void check(boolean condition,String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static boolean ground(BlockState state) {
        return state.isSolid() && !state.isIn(BlockTags.LOGS) && !state.isIn(BlockTags.LEAVES);
    }
    private static int ground(ServerWorld world,int x,int z) {
        BlockPos.Mutable position=new BlockPos.Mutable();
        for (int y=world.getTopYInclusive();y>=world.getBottomY();y--)
            if (ground(world.getBlockState(position.set(x,y,z)))) return y;
        return world.getBottomY();
    }
    private static boolean soil(BlockState state) {
        return state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.DIRT) || state.isOf(Blocks.COARSE_DIRT) ||
                state.isOf(Blocks.PODZOL) || state.isOf(Blocks.MYCELIUM) || state.isOf(Blocks.ROOTED_DIRT);
    }
    private static int nativeTop(ServerWorld world,int x,int z) {
        var generator=world.getChunkManager().getChunkGenerator();
        var noise=world.getChunkManager().getNoiseConfig();
        return SeaGeneration.nativeQuery(()->generator.getHeight(x,z,Heightmap.Type.OCEAN_FLOOR_WG,world,noise))-1;
    }
    static void verify(ServerWorld world,List<String> report) throws Exception {
        SeaConfig config=GreatSea.CONFIG;long seed=world.getSeed();
        var generator=world.getChunkManager().getChunkGenerator();
        var noise=world.getChunkManager().getNoiseConfig();
        int hillZ=-4608;
        if (nativeTop(world,config.innerBoundary+16,hillZ)<=config.mainlandPreserveTopY+8) {
            boolean found=false;
            for (int z=-8192;z<=8192;z+=64)
                if (nativeTop(world,config.innerBoundary+16,z)>config.mainlandPreserveTopY+8) {
                    hillZ=z;found=true;break;
                }
            check(found,"No high native hill found to exercise the mainland clipping taper");
        }
        List<String> csv=new ArrayList<>();
        csv.add("distance,x,z,native_y,profile_y,ceiling_y,ground_y,preserved,surface");
        int retained=0,cleared=0,clipped=0,soilCaps=0,checked=0;
        int firstInside=-1,lastOutside=-1,firstNative=-1,lastNative=-1;
        BlockPos.Mutable position=new BlockPos.Mutable();
        for (int distance=-32;distance<=320;distance++) {
            int x=config.innerBoundary+distance,z=hillZ;
            var chunk=world.getChunk(x>>4,z>>4);
            int nativeY=nativeTop(world,x,z);
            double profile=SeaTerrain.height(config,seed,x,z,nativeY,SeaTerrain.floorBaseline(config));
            int profileY=(int)Math.round(profile);
            int ceiling=(int)Math.round(MainlandJoin.ceiling(config,nativeY,profile));
            int actual=ground(world,x,z);
            boolean preserved=SeaGeneration.preservesMainland(chunk,x,z);
            var surface=world.getBlockState(position.set(x,actual,z));
            csv.add(distance+","+x+","+z+","+nativeY+","+profileY+","+ceiling+","+actual+","+
                    preserved+","+Registries.BLOCK.getId(surface.getBlock()));
            if (distance==-1) { lastOutside=actual;lastNative=nativeY; }
            if (distance==0) { firstInside=actual;firstNative=nativeY; }
            if (!chunk.getStructureReferences().isEmpty()) continue;
            int queried=generator.getHeight(x,z,Heightmap.Type.OCEAN_FLOOR_WG,world,noise)-1;
            check(Math.abs(actual-queried)<=2,"Mainland actual/query mismatch at distance "+distance+": "+actual+" vs "+queried);
            if (distance<0) {
                check(Math.abs(actual-nativeY)<=2,"Native mainland before beach changed at distance "+distance);
                continue;
            }
            var original=SeaGeneration.nativeQuery(()->generator.getColumnSample(x,z,world,noise));
            // Allow the native surface-rule offset and its four relocated topsoil layers.
            // The column below the cap must still be native solid ground over buried sand.
            for (int y=profileY+1;y<=ceiling-2;y++) if (original.getState(y).isSolid()) {
                check(ground(world.getBlockState(position.set(x,y,z))),
                        "Retained mainland lost a solid block at "+x+","+y+","+z);
                retained++;
            }
            for (int y=Math.max(profileY,ceiling)+3;y<=nativeY+2;y++) if (original.getState(y).isSolid()) {
                check(!ground(world.getBlockState(position.set(x,y,z))),
                        "Original hill remains over tapered mainland ceiling at "+x+","+y+","+z);
                cleared++;
            }
            if (profileY<ceiling-2 && nativeY>ceiling+2) {
                clipped++;
                check(Math.abs(actual-ceiling)<=2,"Tapered hill did not reach its clipping ceiling at distance "+distance);
                // The supplied hill is a forest fixture. Copying the original surface
                // should leave a natural soil cap, rather than an exposed stone plateau.
                var cap=surface;
                if (cap.isOf(Blocks.SNOW_BLOCK)) cap=world.getBlockState(position.set(x,actual-1,z));
                if (soil(cap)) soilCaps++;
                check(!cap.isOf(Blocks.STONE) && !cap.isOf(Blocks.DEEPSLATE),
                        "Clipped forest mainland has exposed density stone at distance "+distance);
            }
            checked++;
        }
        Files.write(Path.of("greatsea-mainland.csv"),csv);
        check(firstInside>=0 && lastOutside>=0,"Missing mainland boundary samples");
        check(Math.abs(firstInside-firstNative)<=2,"First beach column still cuts the native hill to Y=70");
        check(Math.abs(firstInside-lastOutside)<=Math.abs(firstNative-lastNative)+3,
                "Artificial cliff at the first mainland/beach column");
        check(checked>250 && retained>50 && cleared>20 && clipped>=4 && soilCaps>=4,
                "Mainland fixture did not exercise retained terrain, smooth hill clipping and native soil caps: checked="+checked+
                        ", retained="+retained+", cleared="+cleared+", clipped="+clipped+", soilCaps="+soilCaps);
        report.add("Smooth mainland hill join at Z="+hillZ+": boundary native Y="+firstNative+", actual Y="+firstInside+
                "; "+checked+" inland profile columns; retained solid blocks="+retained+", removed hill blocks="+cleared+
                ", tapered clipped columns="+clipped+", native soil caps="+soilCaps+"; no boundary cliff or exposed stone plateau");
        int shoreQueries=0;
        for (int side=0;side<5;side++) for (int distance:new int[]{-1,0,1,16,64,128,256}) {
            int coordinate=side==4 ? config.outerBoundary-distance : config.innerBoundary+distance;
            int sign=side==1 || side==3 ? -1 : 1;
            int x=side<2 || side==4 ? sign*coordinate : 37;
            int z=side>=2 && side<4 ? sign*coordinate : 37;
            var chunk=world.getChunk(x>>4,z>>4);
            if (!chunk.getStructureReferences().isEmpty()) continue;
            int actual=ground(world,x,z);
            int expected=generator.getHeight(x,z,Heightmap.Type.OCEAN_FLOOR_WG,world,noise)-1;
            check(Math.abs(actual-expected)<=2,"Mainland height query mismatch on shore "+side+" distance "+distance+
                    ": "+actual+" vs "+expected);
            var column=generator.getColumnSample(x,z,world,noise);
            int columnTop=world.getBottomY();
            for (int y=world.getTopYInclusive();y>=world.getBottomY();y--)
                if (ground(column.getState(y))) { columnTop=y;break; }
            check(Math.abs(columnTop-expected)<=1,"Column sample disagrees with mainland height query on shore "+side+
                    " distance "+distance+": "+columnTop+" vs "+expected);
            shoreQueries++;
        }
        check(shoreQueries>=25,"Too few mainland/cardinal/outer shore queries verified");
        report.add("Mainland ground, height and column queries agree on "+shoreQueries+" samples across four inner shores and the outer shore");
    }
}
