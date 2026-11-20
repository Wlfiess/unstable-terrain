package dev.greatsea;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.Direction;
import net.minecraft.state.property.Properties;
import net.minecraft.world.chunk.Chunk;

/** Ores, dungeon blocks and loot share the measured sixteen-block repeat phase. */
public final class FarlandsRepeatingContents {
    static final int COBBLE=43,MOSSY=44,SPAWNER=45,CHEST=46;
    private FarlandsRepeatingContents(){}
    private static final class Local {
        long seed=Long.MIN_VALUE,hash,oreHash;
        int x=Integer.MIN_VALUE,z,a,b,dx,dz,floor,oreX,oreY,oreZ,oreMaterial;
        double oreRadius;
        boolean dungeon,ore;
    }
    private static final ThreadLocal<Local> CACHE=ThreadLocal.withInitial(Local::new);
    private static Local local(long seed,int x,int z) {
        var c=CACHE.get();if(c.seed==seed && c.x==x && c.z==z)return c;
        c.seed=seed;c.x=x;c.z=z;
        var point=FarlandsWorld.project(x,z);
        c.a=Math.floorMod(point.depth(),16);c.b=FarlandsSamples.repeatingTangent(point.tangent());
        int band=Math.floorDiv(c.b,32),bands=FarlandsSamples.repeatingDepth()/32;
        c.hash=SeaTerrain.hash(seed+9167,band,0);
        int centerA=6+(int)(SeaTerrain.unit(SeaTerrain.hash(c.hash,1,2))*4);
        int centerB=band*32+10+(int)(SeaTerrain.unit(SeaTerrain.hash(c.hash,2,1))*12);
        c.floor=2+(int)(SeaTerrain.unit(SeaTerrain.hash(c.hash,3,1))*3);
        c.dungeon=band==Math.floorMod(SeaTerrain.hash(seed+9168,0,0),bands) || SeaTerrain.unit(c.hash)<.07;
        c.dx=c.a-centerA;c.dz=c.b-centerB;
        int oreBand=Math.floorDiv(c.b,16);
        c.oreHash=SeaTerrain.hash(seed+9157,oreBand,0);c.ore=SeaTerrain.unit(c.oreHash)<=.65;
        c.oreX=3+(int)(SeaTerrain.unit(SeaTerrain.hash(c.oreHash,1,0))*10);
        c.oreY=3+(int)(SeaTerrain.unit(SeaTerrain.hash(c.oreHash,0,1))*10);
        c.oreZ=oreBand*16+3+(int)(SeaTerrain.unit(SeaTerrain.hash(c.oreHash,1,1))*10);
        c.oreRadius=1.65+1.1*SeaTerrain.unit(SeaTerrain.hash(c.oreHash,2,2));
        c.oreMaterial=17+(int)Math.floorMod(SeaTerrain.hash(c.oreHash,4,5),7);
        return c;
    }
    /** -1 means that the source terrain remains unchanged; zero explicitly carves air. */
    static int dungeonMaterial(long seed,int x,int y,int z) {
        if(y< -48 || y>223)return -1;
        var c=local(seed,x,z);
        int dy=Math.floorMod(y+64,16)-c.floor;
        if(!c.dungeon || Math.abs(c.dx)>3 || Math.abs(c.dz)>3 || dy<0 || dy>5)return -1;
        if(dy==0)return SeaTerrain.unit(SeaTerrain.hash(c.hash,c.dx,c.dz))<.55?MOSSY:COBBLE;
        if(dy==5 || Math.abs(c.dx)==3 || Math.abs(c.dz)==3) {
            if(Math.abs(c.dx)==3 && c.dz==0 && dy<=2)return 0;
            return COBBLE;
        }
        if(dy==1 && c.dx==0 && c.dz==0)return SPAWNER;
        if(dy==1 && c.dx==2 && c.dz==2)return CHEST;
        return 0;
    }
    static int material(long seed,int x,int y,int z,int original) {
        int dungeon=dungeonMaterial(seed,x,y,z);
        if(dungeon>=0)return dungeon;
        if(original!=1 || y< -48 || y>223)return original;
        var c=local(seed,x,z);if(!c.ore)return original;
        double dx=c.a-c.oreX,dy=Math.floorMod(y+64,16)-c.oreY,dz=c.b-c.oreZ;
        return dx*dx+dy*dy+dz*dz<=c.oreRadius*c.oreRadius?c.oreMaterial:original;
    }
    static boolean changed(int material){return material>=17 && material<=23 || material>=COBBLE && material<=CHEST;}
    static BlockState state(int material,int x,int z) {
        return switch(material) {
            case 17 -> Blocks.IRON_ORE.getDefaultState();case 18 -> Blocks.COAL_ORE.getDefaultState();
            case 19 -> Blocks.COPPER_ORE.getDefaultState();case 20 -> Blocks.GOLD_ORE.getDefaultState();
            case 21 -> Blocks.DIAMOND_ORE.getDefaultState();case 22 -> Blocks.REDSTONE_ORE.getDefaultState();
            case 23 -> Blocks.LAPIS_ORE.getDefaultState();case COBBLE -> Blocks.COBBLESTONE.getDefaultState();
            case MOSSY -> Blocks.MOSSY_COBBLESTONE.getDefaultState();case SPAWNER -> Blocks.SPAWNER.getDefaultState();
            case CHEST -> Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING,
                    Math.abs((long)x)>=Math.abs((long)z)?Direction.NORTH:Direction.WEST);
            default -> throw new IllegalArgumentException("Not repeating content: "+material);
        };
    }
    static long lootSeed(long seed,int x,int y,int z) {
        var c=local(seed,x,z);
        return SeaTerrain.hash(c.hash,9169,1);
    }
    static NbtCompound blockEntity(long seed,int x,int y,int z,int material) {
        var n=new NbtCompound();n.putInt("x",x);n.putInt("y",y);n.putInt("z",z);
        if(material==CHEST) {
            n.putString("id","minecraft:chest");
            n.putString("LootTable","minecraft:chests/simple_dungeon");
            n.putLong("LootTableSeed",lootSeed(seed,x,y,z));
        }else if(material==SPAWNER) {
            var c=local(seed,x,z);
            String entityId=switch((int)Math.floorMod(c.hash,3)) {
                case 0 -> "minecraft:zombie";case 1 -> "minecraft:skeleton";default -> "minecraft:spider";
            };
            n.putString("id","minecraft:mob_spawner");n.putShort("Delay",(short)20);
            n.putShort("MinSpawnDelay",(short)200);n.putShort("MaxSpawnDelay",(short)800);
            n.putShort("SpawnCount",(short)4);n.putShort("MaxNearbyEntities",(short)6);
            n.putShort("RequiredPlayerRange",(short)16);n.putShort("SpawnRange",(short)4);
            var entity=new NbtCompound();entity.putString("id",entityId);
            var data=new NbtCompound();data.put("entity",entity);n.put("SpawnData",data);
            var potential=new NbtCompound();potential.putInt("weight",1);potential.put("data",data.copy());
            var potentials=new NbtList();potentials.add(potential);n.put("SpawnPotentials",potentials);
        }else throw new IllegalArgumentException("No block entity for "+material);
        return n;
    }
    public static void populate(Chunk chunk,long seed) {
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();
        for(int z=sz;z<sz+16;z++)for(int x=sx;x<sx+16;x++) {
            if(SpokelandsTerrain.contains(x,z) || !FarlandsWorld.contains(x,z) || FarlandsTerrain.stage(x,z)!=FarlandsTerrain.Stage.REPEATING)continue;
            for(int y=Math.max(-48,chunk.getBottomY());y<=Math.min(223,chunk.getTopYInclusive());y++) {
                if(FarlandsEmpire.contains(x,y,z))continue;
                int material=dungeonMaterial(seed,x,y,z);
                if(material==SPAWNER || material==CHEST)chunk.addPendingBlockEntityNbt(blockEntity(seed,x,y,z,material));
            }
        }
    }
}
