package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpokelandsSandReferenceTest {
    private static final long SEED=4200439302357479658L;
    private int base(int a,int b){return (int)Math.floor(143+5*SeaTerrain.noise(SEED+18161,a,b,310)+1.8*SeaTerrain.noise(SEED+18162,a,b,65));}
    @Test void referenceAreaRetainsBroadBasinsHeavyRoofsAndRoundedHighCrowns() {
        byte[] column=new byte[384];int basin=0,roof=0,highest=-64;var crowns=new java.util.HashSet<Integer>();
        for(int a=148750;a<149262;a+=8)for(int b=180;b<692;b+=8) {
            int floor=base(a,b),foot=SpokelandsSand.fill(column,SEED,a,b,floor),top=-65;
            for(int y=-64;y<320;y++)if(column[y+64]!=0&&column[y+64]!=8)top=y;
            if(foot-floor<=4)basin++;
            if(top-foot>65)roof++;
            highest=Math.max(highest,top);if(top>=296)crowns.add(top);
        }
        assertTrue(basin>1500,"Broad walking basins must remain between supports");
        assertTrue(roof>1000,"The reference has connected heavy overhead terrain");
        assertTrue(highest>=300&&highest<317,"Tall crowns should round below a flat clipping plane");
        assertTrue(crowns.size()>=10,"High crowns must retain stepped curvature");
    }
    @Test void lowerFeetStayContinuousAndSurfaceSandIsSupported() {
        byte[] column=new byte[384];int[] previous=new int[128];
        for(int a=148900;a<149028;a++)for(int b=300;b<428;b++) {
            int foot=SpokelandsSand.fill(column,SEED,a,b,base(a,b));
            assertTrue(foot>=130&&foot<210);
            if(a>148900)assertTrue(Math.abs(previous[b-300]-foot)<=4,"A neighboring walking column was cut off");
            previous[b-300]=foot;
            for(int y=-59;y<=foot;y++)assertNotEquals(0,column[y+64],"The basin floor must be solid");
            for(int y=-59;y<320;y++)if((column[y+64]&255)==SpokelandsTerrain.RED_SAND)
                assertTrue(column[y+63]!=0&&column[y+63]!=8,"A sand veneer needs a supporting block");
        }
    }
}
