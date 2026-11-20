package dev.greatsea;

import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Small opt-in check of actual corner chunks; never runs in ordinary play. */
final class SeaCornerVerifier {
    static void run(MinecraftServer server) {
        var report=new ArrayList<String>();
        try {
            var world=server.getOverworld();var generator=world.getChunkManager().getChunkGenerator();
            var noise=world.getChunkManager().getNoiseConfig();
            SeaConfig c=GreatSea.CONFIG;long seed=world.getSeed();
            if (!UnstableWorldGen.isUnstable(generator)) throw new AssertionError("Requires Unstable world preset");
            double rounding=SeaTerrain.cornerRadius(c);
            for (int boundary:new int[]{c.innerBoundary,c.outerBoundary}) for (int sign:new int[]{-1,1})
            for (int offset:new int[]{-40,250}) {
                int direction=boundary==c.innerBoundary ? 1 : -1;
                int shore=boundary-(int)Math.round(rounding/4);
                double width=SeaTerrain.localBeachWidth(c,seed,sign*shore,sign*shore);
                int coordinate=sign*(int)Math.round(shore+direction*(width+offset));
                int x=coordinate,z=coordinate;
                if (!SeaTerrain.contains(c,x,z)) throw new AssertionError("Unmanaged corner point");
                var chunk=world.getChunk(x>>4,z>>4);
                int profile=(int)Math.round(SeaTerrain.height(c,seed,x,z,64,-20));
                if (!world.getBlockState(new BlockPos(x,profile,z)).isOf(Blocks.SAND))
                    throw new AssertionError("Corner ground differs from profile at "+x+","+z+" Y="+profile);
                int query=generator.getHeight(x,z,Heightmap.Type.OCEAN_FLOOR_WG,world,noise)-1;
                if (query!=profile) throw new AssertionError("Corner height query mismatch: "+query+" vs "+profile);
                var column=generator.getColumnSample(x,z,world,noise);
                if (!column.getState(profile).isOf(Blocks.SAND)) throw new AssertionError("Corner column query mismatch");
                if (offset>0) {
                    if (!world.getBlockState(new BlockPos(x,c.seaLevel-1,z)).isOf(Blocks.WATER) ||
                            !world.getBlockState(new BlockPos(x,c.seaLevel,z)).isAir())
                        throw new AssertionError("Corner water level differs from Y="+c.seaLevel);
                }
                report.add("Corner "+x+","+z+": ground Y="+profile+", native chunk generation and height/column queries agree");
            }
            report.add("PASS: eight fresh inner/outer square corners with a sixteen-block tangent blend, matching ground queries and sea at Y=200.");
            GreatSea.LOGGER.info("GREATSEA_CORNER_VERIFICATION_PASS {}",report);
        } catch (Throwable e) {
            report.add("FAIL: "+e);GreatSea.LOGGER.error("GREATSEA_CORNER_VERIFICATION_FAIL",e);
        } finally {
            try { Files.write(Path.of("greatsea-corner-verification.txt"),report); }
            catch (Exception e) { GreatSea.LOGGER.error("Cannot save corner check",e); }
            server.stop(false);
        }
    }
}
