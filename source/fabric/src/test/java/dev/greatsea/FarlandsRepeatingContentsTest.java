package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarlandsRepeatingContentsTest {
    private static final long SEED=4200439302357479658L;
    private static final int X=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.REPEATING)+1024;
    @Test void oresAndDungeonContentsRepeatWithTheMeasuredTravelAndVerticalPeriod() {
        int ores=0,spawners=0,chests=0;var kinds=new java.util.HashSet<Integer>();
        for(int z=-208;z<208;z++)for(int dx=0;dx<16;dx++)for(int y=-32;y<96;y++) {
            int material=FarlandsTerrain.material(SEED,X+dx,y,z);
            assertEquals(material,FarlandsTerrain.material(SEED,X+dx+16,y,z),"Repeat travel phase");
            assertEquals(material,FarlandsTerrain.material(SEED,X+dx,y+16,z),"Repeat vertical phase");
            if(material>=17 && material<=23){ores++;kinds.add(material);}
            if(material==FarlandsRepeatingContents.SPAWNER)spawners++;
            if(material==FarlandsRepeatingContents.CHEST)chests++;
        }
        assertTrue(ores>1000);assertTrue(kinds.size()>=5,"Retain several real native ore types");
        assertTrue(spawners>=8 && chests>=8,"Actual spawners and chests must recur through the repeating terrain: "+spawners+" / "+chests);
    }
    @Test void repeatedLootSeedsAndSpawnerDataIgnoreTravelTileAndVerticalTile() {
        boolean found=false;
        for(int z=-208;z<208;z++)for(int dx=0;dx<16;dx++)for(int y=0;y<16;y++) {
            int material=FarlandsRepeatingContents.dungeonMaterial(SEED,X+dx,y,z);
            if(material!=FarlandsRepeatingContents.CHEST && material!=FarlandsRepeatingContents.SPAWNER)continue;
            found=true;
            var a=FarlandsRepeatingContents.blockEntity(SEED,X+dx,y,z,material);
            var b=FarlandsRepeatingContents.blockEntity(SEED,X+dx+160,y+64,z,material);
            assertEquals(a.getString("id",""),b.getString("id",""));
            if(material==FarlandsRepeatingContents.CHEST) {
                assertEquals("minecraft:chests/simple_dungeon",a.getString("LootTable",""));
                assertEquals(a.getLong("LootTableSeed",0),b.getLong("LootTableSeed",1));
            }else {
                assertEquals(a.getCompoundOrEmpty("SpawnData"),b.getCompoundOrEmpty("SpawnData"));
                assertEquals(a.getListOrEmpty("SpawnPotentials"),b.getListOrEmpty("SpawnPotentials"));
                assertFalse(a.getCompoundOrEmpty("SpawnData").getCompoundOrEmpty("entity").getString("id","").isBlank());
            }
        }
        assertTrue(found);
    }
    @Test void oreAndRoomChangesStayInRepeatingInteriorsAndRetainSourceShells() {
        for(int y:new int[]{-64,-49,224,255,319})for(int z=-208;z<208;z++)for(int dx=0;dx<16;dx++) {
            int original=FarlandsSamples.material(SEED,X+dx,y,z);
            assertEquals(original,FarlandsTerrain.material(SEED,X+dx,y,z));
        }
        for(int y=-32;y<96;y++)for(int z=-208;z<208;z+=3)for(int dx=0;dx<16;dx++) {
            int source=FarlandsSamples.material(SEED,X+dx,y,z);
            int material=FarlandsTerrain.material(SEED,X+dx,y,z);
            if(material>=17 && material<=23)assertEquals(1,source,"Ores can replace rock only");
            if(FarlandsRepeatingContents.dungeonMaterial(SEED,X+dx,y,z)<0 && source!=1)
                assertEquals(source,material,"Do not cover exposed grass or air with ores");
        }
    }
}
