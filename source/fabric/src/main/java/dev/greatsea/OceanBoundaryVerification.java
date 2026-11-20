package dev.greatsea;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.BiomeKeys;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Opt-in check of real generated columns across a warm/cold material transition. */
final class OceanBoundaryVerification {
    private OceanBoundaryVerification() {}
    private static void check(boolean condition,String message) {
        if (!condition) throw new AssertionError(message);
    }
    static void verify(ServerWorld world,List<String> report,int coldX,int coldZ) throws Exception {
        SeaConfig config=GreatSea.CONFIG;long seed=world.getSeed();
        int inner=coldX,outer=coldX;
        while (outer<coldX+24_000 && SeaBiomeContext.cold(config,seed,outer,coldZ)) {
            inner=outer;outer+=64;
        }
        check(outer<coldX+24_000,"Cannot find bounded cold patch edge");
        while (outer-inner>4) {
            int middle=(inner+outer)/2;
            if (SeaBiomeContext.cold(config,seed,middle,coldZ)) inner=middle;
            else outer=middle;
        }
        int edge=(outer+inner)/2;
        List<String> csv=new ArrayList<>();csv.add("x,z,floor_y,cold_blend,cold,gravel");
        int columns=0,mixed=0,warmGravel=0,coldSand=0,kelp=0,grass=0;
        BlockPos.Mutable position=new BlockPos.Mutable();
        for (int z=coldZ-96;z<=coldZ+96;z+=2) for (int x=edge-128;x<=edge+128;x+=2) {
            var chunk=world.getChunk(x>>4,z>>4);
            // Real structures have their own blocks/archaeology above the original bed.
            if (!chunk.getStructureReferences().isEmpty()) continue;
            int floor=(int)Math.round(VanillaOceanTerrain.floorHeight(config,seed,x,z));
            double blend=SeaBiomeContext.coldBlend(config,seed,x,z);
            boolean cold=SeaBiomeContext.cold(config,seed,x,z);
            boolean gravel=OceanFloorMaterials.gravel(config,seed,x,z);
            var material=gravel ? Blocks.GRAVEL : Blocks.SAND;
            for (int y=floor;y>floor-5;y--)
                check(chunk.getBlockState(position.set(x,y,z)).isOf(material),"Gap or wrong material in mixed floor skin at "+x+","+y+","+z);
            var foundation=chunk.getBlockState(position.set(x,floor-5,z));
            check(foundation.isSolid() && !foundation.isOf(Blocks.SAND) && !foundation.isOf(Blocks.GRAVEL),
                    "Unsupported mixed ocean floor at "+x+","+z);
            check(chunk.getBiomeForNoiseGen(x>>2,0,z>>2).matchesKey(cold ? BiomeKeys.COLD_OCEAN : BiomeKeys.WARM_OCEAN),
                    "Mixed floor edge differs from the stored climate at "+x+","+z);
            var above=chunk.getBlockState(position.set(x,floor+1,z));
            check(above.isOf(Blocks.WATER) || above.isOf(Blocks.KELP) || above.isOf(Blocks.KELP_PLANT) || above.isOf(Blocks.SEAGRASS),
                    "Climate transition changed the native floor height at "+x+","+z);
            for (int y=floor+1;y<=floor+config.coldKelpMaxHeight;y++) {
                var state=chunk.getBlockState(position.set(x,y,z));
                if (state.isOf(Blocks.SEAGRASS)) {
                    grass++;check(y==floor+1,"Floating seagrass at climate join");
                }
                if (state.isOf(Blocks.KELP)) {
                    kelp++;
                    long hash=SeaTerrain.hash(seed+93,x,z);
                    check(y-floor==SeaDecoration.kelpLength(config,cold,hash),"Climate-join kelp height differs from its bed-relative distribution");
                    for (int root=floor+1;root<y;root++)
                        check(chunk.getBlockState(position.set(x,root,z)).isOf(Blocks.KELP_PLANT),"Disconnected kelp across mixed materials");
                }
            }
            if (blend>0 && blend<1) {
                mixed++;
                if (!cold && gravel) warmGravel++;
                if (cold && !gravel) coldSand++;
            } else check(gravel==cold,"Warm/cold interior floor lost its material");
            columns++;
            csv.add(x+","+z+","+floor+","+blend+","+cold+","+gravel);
        }
        check(columns>2000 && mixed>100 && warmGravel>0 && coldSand>0,"Ocean join did not mix materials across its irregular edge");
        check(kelp>0 && grass>0,"Mixed ocean floor lacks rooted vegetation");
        Files.write(Path.of("greatsea-boundary.csv"),csv);
        report.add("Irregular warm/cold material join at "+edge+","+coldZ+": "+columns+" native floor columns; mixed="+mixed+
                ", warm gravel="+warmGravel+", cold sand="+coldSand+"; five-block supported skin; rooted kelp="+kelp+", seagrass="+grass);
    }
}
