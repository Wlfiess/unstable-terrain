package dev.greatsea;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.List;

/** Development-only checks against completed, decorated chunks. */
public final class CherryGroveVerifier {
    private CherryGroveVerifier() {}
    public static void verify(ServerWorld world,List<String> report) {
        long seed=world.getSeed();
        int sx=(GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.CHERRY)+GreatSea.FARLANDS.stageWidths[FarlandsTerrain.Stage.CHERRY.ordinal()]/2)/16*16;
        int petals=0,logs=0,leaves=0,grass=0,roots=0,columns=0;
        BlockPos.Mutable pos=new BlockPos.Mutable();
        for(int cz=-4;cz<4;cz++)for(int cx=0;cx<8;cx++) {
            var chunk=world.getChunk((sx>>4)+cx,cz);
            for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++) {
                int x=chunk.getPos().getStartX()+dx,z=chunk.getPos().getStartZ()+dz;
                var ground=FarlandsTerrain.column(seed,x,z);
                if(ground.voidColumn() || (ground.material()!=1 && ground.material()!=3 && ground.material()!=4))throw new AssertionError("Cherry vegetation became ground at "+x+","+z);
                columns++;
                for(int y=-64;y<120;y++) {
                    var state=chunk.getBlockState(pos.set(x,y,z));
                    if(state.isOf(Blocks.PINK_PETALS)) {
                        petals++;
                        if(!state.canPlaceAt(world,pos))throw new AssertionError("Unsupported petal at "+pos);
                    }
                    if(state.isOf(Blocks.CHERRY_LOG)) {
                        logs++;
                        var below=world.getBlockState(pos.down());
                        if(below.isOf(Blocks.GRASS_BLOCK)||below.isOf(Blocks.DIRT))roots++;
                    }
                    if(state.isOf(Blocks.CHERRY_LEAVES))leaves++;
                    if(state.isOf(Blocks.GRASS_BLOCK))grass++;
                }
            }
        }
        if(petals<100 || logs<100 || leaves<1000 || roots<10 || grass<1000)throw new AssertionError("Cherry ecosystem missing: petals="+petals+",logs="+logs+",leaves="+leaves+",roots="+roots+",grass="+grass);
        if(roots>110)throw new AssertionError("Cherry canopy exceeds the measured source density: "+roots+" roots in 64 chunks; source equivalent 76.25");
        report.add("PASS Cherry: 64 FULL chunks, "+columns+" natural ground columns; petals="+petals+",logs="+logs+",leaves="+leaves+",roots="+roots+",grass="+grass+"; all petals supported; no source-tiled vegetation");
    }
}
