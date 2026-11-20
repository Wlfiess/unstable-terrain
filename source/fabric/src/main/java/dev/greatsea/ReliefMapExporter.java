package dev.greatsea;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
/** Development-only wide height maps used to inspect variation beyond a small preview. */
final class ReliefMapExporter {
    static void export(long seed) throws Exception {
        Files.createDirectories(Path.of("relief-maps"));
        for(var stage:new FarlandsTerrain.Stage[]{FarlandsTerrain.Stage.SPIKES,FarlandsTerrain.Stage.PLAINS_HILLS,FarlandsTerrain.Stage.SAND_DUNES}) {
            int center=FarlandsTerrain.start(stage)+GreatSea.FARLANDS.stageWidths[stage.ordinal()]/2;
            try(var out=new DataOutputStream(Files.newOutputStream(Path.of("relief-maps",stage.name().toLowerCase()+".bin")))) {
                out.writeInt(512);out.writeInt(2);
                for(int z=0;z<512;z++)for(int x=0;x<512;x++)out.writeShort(stage==FarlandsTerrain.Stage.SPIKES?FarlandsRelief.spire(seed,center-512+x*2,-512+z*2):FarlandsRelief.ridge(seed,center-512+x*2,-512+z*2,stage==FarlandsTerrain.Stage.SAND_DUNES));
            }
        }
    }
}
