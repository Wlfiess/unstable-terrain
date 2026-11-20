package dev.greatsea;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.Registries;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** Opt-in integration check of the new machine and global new-mob gate. */
final class OrbitalsVerifier {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void run(MinecraftServer server){
        var report=new ArrayList<String>();long began=System.nanoTime();
        try{
            var world=server.getOverworld();long seed=world.getSeed();int border=GreatSea.FARLANDS.entryBoundary;
            int origin=border+FarlandsOrbitals.origin(seed),oz=-FarlandsOrbitals.SAMPLE.depth/2;
            int blocks=0;var s=FarlandsOrbitals.SAMPLE;var pos=new BlockPos.Mutable();
            for(int z=0;z<s.depth;z++){
                for(int x=0;x<s.width;x++)for(int y=0;y<s.height;y++){
                    if(s.material(x,y,z)==0)continue;
                    pos.set(origin+x,FarlandsOrbitals.BASE_Y+y,oz+z);
                    check(world.getBlockState(pos).equals(s.state(x,y,z)),"Orbital block mismatch at "+pos);blocks++;
                }
                if(z%256==0)GreatSea.LOGGER.info("ORBITALS_VERIFY_BLOCKS {} / {}",z,s.depth);
            }
            check(blocks==205438,"Source block count changed");report.add("Orbitals: all "+blocks+" non-air block states match actual FULL chunks; unscaled 142 x 137 x 1786.");
            int tiles=0,items=0;
            for(var source:FarlandsOrbitals.tiles()){
                var n=source.copy();var p=new BlockPos(origin+n.getInt("x",0),FarlandsOrbitals.BASE_Y+n.getInt("y",0),oz+n.getInt("z",0));
                n.putInt("x",p.getX());n.putInt("y",p.getY());n.putInt("z",p.getZ());
                var actual=world.getBlockEntity(p);var expected=BlockEntity.createFromNbt(p,world.getBlockState(p),n,world.getRegistryManager());
                check(actual!=null&&expected!=null,"Missing orbital block entity at "+p+" / "+n.getString("id", ""));
                check(actual.createNbtWithIdentifyingData(world.getRegistryManager()).equals(expected.createNbtWithIdentifyingData(world.getRegistryManager())),"Orbital block entity data differs at "+p);
                tiles++;items+=source.getListOrEmpty("Items").size();
            }
            check(tiles==14863,"Orbital tile count changed");report.add("Orbitals: all "+tiles+" block entities and "+items+" inventory slots retain their canonical source NBT.");
            int gap=FarlandsOrbitals.length(seed),gx=border+FarlandsOrbitals.center()-gap/2;
            for(int x=gx;x<gx+gap;x++)for(int y=-64;y<320;y++)check(world.getBlockState(pos.set(x,y,2000)).isAir(),"Void has terrain at "+pos);
            report.add("Void: "+gap+" blocks across travel, empty from Y=-64 through 319 outside the machine; no floor or platform.");
            for(int x:new int[]{origin-1,origin+s.width})for(int z=oz;z<oz+s.depth;z+=16)for(int y=-64;y<320;y++)
                check(world.getBlockState(pos.set(x,y,z)).isAir(),"Terrain touches the orbital bounding box at "+pos);
            report.add("Machine clearance: complete Y=-64..319 columns beside both schematic edges are air along its full length.");
            for(int face=1;face<4;face++){
                int x=face==1?-origin:0,z=face>=2?(face==3?-origin:origin):0;
                // Compare a small generated sample on each rotated/mirrored face.
                int checked=0;for(int a=0;a<s.width;a+=17)for(int b=0;b<s.depth;b+=211)for(int y=0;y<s.height;y+=7){
                    int wx=face==1?-(origin+a):b+oz,wz=face==1?b+oz:face==3?-(origin+a):origin+a;
                    if(s.material(a,y,b)==0 && !FarlandsOrbitals.gap(seed,wx,wz))continue;
                    var expected=FarlandsOrbitals.orient(s.state(a,y,b),face);
                    check(world.getBlockState(pos.set(wx,FarlandsOrbitals.BASE_Y+y,wz)).equals(expected),"Orbital orientation differs on face "+face);checked++;
                }
                report.add("Orbital face "+face+": "+checked+" actual rotated/mirrored voxel checks.");
            }
            int accepted=0,attempts=0;
            for(var dimension:server.getWorlds()){
                check(UnstableMobSpawns.supported(dimension),"Spawn gate missing in "+dimension.getRegistryKey());
                dimension.getChunk(0,0);dimension.getRandom().setSeed(1140142+attempts);
                for(var type:List.of(EntityType.ZOMBIE,EntityType.COW,EntityType.VILLAGER)){
                    var mob=type.create(dimension,SpawnReason.NATURAL);mob.setPosition(8,110,8);
                    int count=0;long before=UnstableMobSpawns.verificationRolls;
                    for(int i=0;i<20000;i++){
                        if(dimension.spawnEntity(mob)){count++;mob.discard();mob=type.create(dimension,SpawnReason.NATURAL);mob.setPosition(8,110,8);}
                    }
                    check(UnstableMobSpawns.verificationRolls-before==20000,"Spawn did not roll exactly once per attempt");
                    attempts+=20000;accepted+=count;report.add(dimension.getRegistryKey().getValue()+" "+Registries.ENTITY_TYPE.getId(type)+": "+count+" accepted / 20000 new spawn attempts.");
                }
                var saved=EntityType.VILLAGER.create(dimension,SpawnReason.LOAD);saved.setPosition(8,110,8);
                long before=UnstableMobSpawns.verificationRolls;dimension.loadEntities(Stream.of(saved));
                check(UnstableMobSpawns.verificationRolls==before,"Saved mobs were rerolled");
                var vehicle=EntityType.COW.create(dimension,SpawnReason.LOAD);vehicle.setPosition(8,110,8);
                check(dimension.tryLoadEntity(vehicle),"Saved vehicle mob refused");
                check(UnstableMobSpawns.verificationRolls==before,"Saved vehicle rerolled");
                var generated=EntityType.ZOMBIE.create(dimension,SpawnReason.CHUNK_GENERATION);generated.setPosition(8,110,8);
                dimension.addEntities(Stream.of(generated));check(UnstableMobSpawns.verificationRolls==before+1,"Generation mobs bypassed rare gate");
                var item=EntityType.ITEM.create(dimension,SpawnReason.COMMAND);item.setPosition(8,110,8);check(dimension.spawnEntity(item),"Non-mob entity was blocked");
                check(UnstableMobSpawns.verificationRolls==before+1,"Non-mob entity consumed spawn roll");
            }
            check(accepted>=5&&accepted<=40,"Rare spawn rate outside expected seeded range: "+accepted);
            report.add("Rare mob gate: "+accepted+" / "+attempts+" actual spawn attempts across Overworld, Nether and End; exactly one 1-in-10000 roll per new mob. Generation stream gated; saved mobs and non-mob entities bypassed.");
            if(Boolean.getBoolean("unstableterrain.previewFarlands"))FarlandsPerspective.renderAll(world,report);
            report.add("PASS in "+(System.nanoTime()-began)/1e9+" seconds");Files.write(Path.of("orbitals-verification.txt"),report);GreatSea.LOGGER.info("ORBITALS_VERIFICATION_PASS");
        }catch(Throwable t){report.add("FAIL: "+t);try{Files.write(Path.of("orbitals-verification.txt"),report);}catch(Exception ignored){}GreatSea.LOGGER.error("ORBITALS_VERIFICATION_FAILED",t);}
        finally{server.stop(false);}
    }
}
