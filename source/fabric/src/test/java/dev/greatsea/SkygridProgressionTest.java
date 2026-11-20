package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkygridProgressionTest {
    private static final long SEED=4200439302357479658L;
    @Test void entranceHasExactlyFiveEmptyBlocksOnEveryAxisAndBothSkygridFaces() {
        int start=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.SKYGRID);
        for(int face=0;face<2;face++)for(int outward=0;outward<=18;outward++)for(int y=-12;y<=12;y++)for(int t=-12;t<=12;t++) {
            int distance=start+outward,x=face==0?distance:t,z=face==0?t:distance;
            assertEquals(outward%6==0&&y%6==0&&t%6==0?4:0,FarlandsTerrain.material(SEED,x,y,z));
        }
    }
    @Test void outwardPlanesSeparateGraduallyAndNeverFallBelowSixBlocks() {
        int start=FarlandsTerrain.start(FarlandsTerrain.Stage.SKYGRID),width=GreatSea.FARLANDS.stageWidths[FarlandsTerrain.Stage.SKYGRID.ordinal()];
        int previous=-1,spacing=0,planes=0;
        for(int offset=0;offset<width;offset++) {
            int next=FarlandsTerrain.skygridSpacing(start+offset);if(next==0)continue;
            assertTrue(next>=6&&next<=128);assertTrue(next>=spacing);
            if(previous>=0){assertEquals(spacing,offset-previous);assertTrue(next-spacing<=1,"Spacing should increase a block at a time at the default width");}
            previous=offset;spacing=next;planes++;
        }
        assertTrue(planes>1000);assertTrue(spacing>=125);
    }
    @Test void candidatePopulationDecreasesInAdditionToTheWideningLattice() {
        int start=FarlandsTerrain.start(FarlandsTerrain.Stage.SKYGRID),width=GreatSea.FARLANDS.stageWidths[FarlandsTerrain.Stage.SKYGRID.ordinal()],previous=1025;
        for(double progress:new double[]{0,.30,.60,.83}) {
            int depth=start+(int)(width*progress);while(FarlandsTerrain.skygridSpacing(depth)==0)depth++;
            int spacing=FarlandsTerrain.skygridSpacing(depth),x=GreatSea.FARLANDS.entryBoundary+depth,count=0;
            for(int t=0;t<1024;t++)if(FarlandsTerrain.material(SEED,x,0,t*spacing)!=0)count++;
            assertTrue(count<previous,"Farther candidate planes should retain fewer blocks");
            if(progress==0)assertEquals(1024,count);
            if(progress>.8)assertTrue(count<50);
            previous=count;
        }
    }
}
