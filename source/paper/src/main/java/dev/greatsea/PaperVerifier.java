package dev.greatsea;
final class PaperVerifier {
    static void run(PaperPlugin plugin){
        var report=new StringBuilder();
        try{
            var world=((org.bukkit.craftbukkit.CraftWorld)org.bukkit.Bukkit.getWorlds().stream().filter(w->w.getEnvironment()==org.bukkit.World.Environment.NORMAL).findFirst().orElseThrow()).getHandle();
            var nether=((org.bukkit.craftbukkit.CraftWorld)org.bukkit.Bukkit.getWorlds().stream().filter(w->w.getEnvironment()==org.bukkit.World.Environment.NETHER).findFirst().orElseThrow()).getHandle();
            if(nether.getMinY()!=-320 || nether.getHeight()!=576)throw new AssertionError("Nether bounds");
            report.append("PASS standalone startup and extended Nether\n");
            PaperCommandVerifier.verify(world,report);
            long checked=0;
            for(var stage:FarlandsTerrain.Stage.values()){
                int x=12_550_821+FarlandsTerrain.start(stage)+GreatSea.FARLANDS.stageWidths[stage.ordinal()]/2;
                var chunk=world.getChunk(x>>4,0);
                var structureBoxes=new java.util.ArrayList<net.minecraft.world.level.levelgen.structure.BoundingBox>();
                for(var start:chunk.getAllStarts().values())if(start.isValid())structureBoxes.add(start.getBoundingBox());
                for(var entry:chunk.getAllReferences().entrySet())for(long reference:entry.getValue()){
                    var origin=new net.minecraft.world.level.ChunkPos(reference);var start=world.getChunk(origin.x,origin.z).getStartForStructure(entry.getKey());
                    if(start!=null&&start.isValid())structureBoxes.add(start.getBoundingBox());
                }
                for(int dx=0;dx<16;dx+=3)for(int z=0;z<16;z+=3)for(int y=-64;y<300;y+=3){
                    var p=new net.minecraft.core.BlockPos((x&~15)+dx,y,z);
                    var expected=FarlandsGeneration.state(world.getSeed(),p.getX(),y,z);
                    var actual=chunk.getBlockState(p);
                    if(actual!=expected){
                        boolean structure=false;
                        for(var box:structureBoxes)if(box.isInside(p)){structure=true;break;}
                        if(structure)continue;
                        boolean decorated=actual.is(net.minecraft.tags.BlockTags.LEAVES)||actual.is(net.minecraft.tags.BlockTags.LOGS)||actual.is(net.minecraft.tags.BlockTags.FLOWERS)||actual.is(net.minecraft.world.level.block.Blocks.SHORT_GRASS)||actual.is(net.minecraft.world.level.block.Blocks.TALL_GRASS)||actual.is(net.minecraft.world.level.block.Blocks.SNOW);
                        decorated |= actual.is(net.minecraft.world.level.block.Blocks.FERN)||actual.is(net.minecraft.world.level.block.Blocks.LARGE_FERN)||actual.is(net.minecraft.world.level.block.Blocks.DEAD_BUSH)||actual.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)||actual.is(net.minecraft.world.level.block.Blocks.CACTUS)||actual.is(net.minecraft.world.level.block.Blocks.BAMBOO)||actual.is(net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM)||actual.is(net.minecraft.world.level.block.Blocks.RED_MUSHROOM)||actual.is(net.minecraft.world.level.block.Blocks.VINE);
                        if(!decorated)throw new AssertionError(stage+" "+p+" expected "+expected+" got "+actual);
                    }
                    checked++;
                }
                report.append("PASS generated ").append(stage).append('\n');
                plugin.getLogger().info("Verified generated "+stage);
            }
            report.append("Checked ").append(checked).append(" generated block states\n");
            var rocket=new net.minecraft.world.entity.projectile.FireworkRocketEntity(net.minecraft.world.entity.EntityType.FIREWORK_ROCKET,world);
            var controlled=RocketHooks.copy(rocket);controlled.lifetime=80;controlled.tick();
            if(controlled.life!=1)throw new AssertionError("Native rocket tick");
            report.append("PASS native rocket copy and tick\n");
            for(String name:new String[]{"amplified","beach","ocean","outer_beach","underworld"}){
                var destination=RegionCommands.destination(name.equals("underworld")?nether:world,name);
                report.append("PASS safe destination ").append(name).append(' ').append(destination).append('\n');
            }
            var under=nether.getChunk(0,0);
            for(int x=0;x<16;x+=3)for(int z=0;z<16;z+=3)for(int y=-320;y<-128;y+=3){
                var p=new net.minecraft.core.BlockPos(x,y,z);
                if(under.getBlockState(p)!=UnderworldTerrain.state(nether.getSeed(),x,y,z))throw new AssertionError("Underworld base "+p);
            }
            report.append("PASS generated Underworld shelf\n");
            playerChecks(plugin,world,nether,report);
            int templeTiles=0;
            for(var tile:FarlandsTemple.tiles()){
                int x=12_550_821+FarlandsTemple.center()-FarlandsTemple.OFFSET_X+tile.getInt("x").orElseThrow();
                int y=FarlandsTemple.BASE_Y+tile.getInt("y").orElseThrow(),z=tile.getInt("z").orElseThrow()-FarlandsTemple.SAMPLE.depth/2;
                var pos=new net.minecraft.core.BlockPos(x,y,z);world.getChunk(x>>4,z>>4);
                if(world.getBlockState(pos)!=FarlandsTemple.state(x,y,z)||world.getBlockEntity(pos)==null)throw new AssertionError("Temple block entity "+pos);
                templeTiles++;
            }
            report.append("PASS temple ").append(templeTiles).append(" block entities in generated terrain\n");
            int boundary=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.CORRUPTED);
            var trial=world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getOrThrow(net.minecraft.world.level.levelgen.structure.BuiltinStructures.TRIAL_CHAMBERS);
            boolean chamber=false;
            for(var placement:world.getChunkSource().getGeneratorState().getPlacementsForStructure(trial)){
                if(!(placement instanceof net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement spread))continue;
                for(int i=0;i<12&&!chamber;i++){
                    var pos=spread.getPotentialStructureChunk(world.getSeed(),(boundary+2048+i*512)>>4,0);
                    var start=world.getChunk(pos.x,pos.z).getStartForStructure(trial.value());
                    if(start==null||!start.isValid())continue;
                    int spawners=0,ominous=0,gapSpawners=0;var box=start.getBoundingBox();
                    for(int cx=box.minX()>>4;cx<=box.maxX()>>4;cx++)for(int cz=box.minZ()>>4;cz<=box.maxZ()>>4;cz++)
                        for(var tile:world.getChunk(cx,cz).getBlockEntities().values()){
                            if(tile.getBlockState().is(net.minecraft.world.level.block.Blocks.TRIAL_SPAWNER)){spawners++;if(FarlandsTerrain.missingChunk(world.getSeed(),tile.getBlockPos().getX(),tile.getBlockPos().getZ()))gapSpawners++;}
                            if(PaperBridge.ominous(tile.getBlockState()))ominous++;
                        }
                    if(spawners==0||ominous==0)throw new AssertionError("Corrupted trial blocks "+spawners+","+ominous);
                    if(gapSpawners==0)throw new AssertionError("Trial spawners clipped from missing chunks");
                    report.append("PASS native Corrupted trial chamber ").append(pos).append(" spawners=").append(spawners).append(" ominousVaults=").append(ominous).append(" spawnersInGaps=").append(gapSpawners).append('\n');chamber=true;
                }
            }
            if(!chamber)throw new AssertionError("No native Corrupted trial chamber");
            int repeating=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.REPEATING)+64;boolean loot=false;
            for(int x=repeating;x<repeating+16&&!loot;x++)for(int z=-FarlandsSamples.repeatingDepth()/2;z<FarlandsSamples.repeatingDepth()/2&&!loot;z++)for(int y=0;y<16&&!loot;y++){
                if(FarlandsRepeatingContents.dungeonMaterial(world.getSeed(),x,y,z)!=FarlandsRepeatingContents.CHEST)continue;
                var first=new net.minecraft.core.BlockPos(x,y,z);var second=first.offset(16,16,0);world.getChunk(x>>4,z>>4);world.getChunk(second.getX()>>4,second.getZ()>>4);
                if(!(world.getBlockEntity(first) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity a)||!(world.getBlockEntity(second) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity b))throw new AssertionError("Repeated chest block entities");
                int occupied=0;for(int slot=0;slot<27;slot++){
                    if(!net.minecraft.world.item.ItemStack.matches(a.getItem(slot),b.getItem(slot)))throw new AssertionError("Repeated loot "+slot);
                    if(!a.getItem(slot).isEmpty())occupied++;
                }
                if(occupied==0)throw new AssertionError("Repeated chests empty");loot=true;
            }
            if(!loot)throw new AssertionError("No repeated loot chest");report.append("PASS repeated chest inventories have identical nonempty loot\n");
            var mainlandReport=new java.util.ArrayList<String>();MainlandJoinVerification.verify(world,mainlandReport);
            for(String line:mainlandReport)report.append(line).append('\n');
            iceChecks(plugin,world,report);
        }catch(Throwable e){report.append("FAIL ").append(e).append('\n');plugin.getLogger().log(java.util.logging.Level.SEVERE,"Paper verification failed",e);}
        // Let queued removal run after Paper finishes its entity-section transitions.
        org.bukkit.Bukkit.getScheduler().runTask(plugin,()->{
            for(var level:org.bukkit.Bukkit.getWorlds())if(plugin.managed(level))for(var entity:level.getEntities())
                if(((org.bukkit.craftbukkit.entity.CraftEntity)entity).getHandle() instanceof net.minecraft.world.entity.Mob)
                    report.append("FAIL loaded mob survived cleanup: ").append(entity.getType()).append('\n');
            if(report.indexOf("FAIL")<0)report.append("PASS loaded and generated mobs removed after chunk activation\n");
            finish(plugin,report);
        });
    }
    private static void finish(PaperPlugin plugin,StringBuilder report){
        try{java.nio.file.Files.writeString(java.nio.file.Path.of("paper-verification.txt"),report);}catch(Exception e){throw new RuntimeException(e);}
        plugin.getLogger().info(report.toString());org.bukkit.Bukkit.shutdown();
    }
    private static void iceChecks(PaperPlugin plugin,net.minecraft.server.level.ServerLevel world,StringBuilder report){
        var generator=world.getChunkSource().getGenerator();var sampler=world.getChunkSource().randomState().sampler();
        net.minecraft.core.BlockPos frozen=null;
        for(int x=290_000;x<900_000&&frozen==null;x+=10031)for(int z=-180_000;z<180_000&&frozen==null;z+=21013){
            var biome=generator.getBiomeSource().getNoiseBiome(x>>2,45,z>>2,sampler);
            if(biome.is(net.minecraft.world.level.biome.Biomes.FROZEN_OCEAN)&&OceanFloorMaterials.gravel(GreatSea.CONFIG,world.getSeed(),x,z))
                frozen=new net.minecraft.core.BlockPos(x,GreatSea.CONFIG.seaLevel-1,z);
        }
        if(frozen==null)throw new AssertionError("No frozen ocean fixture with gravel");
        var chunk=world.getChunk(frozen.getX()>>4,frozen.getZ()>>4);int gravel=0;
        for(var section:chunk.getSections())if(section.maybeHas(GeneratedIcePolicy::isIce))throw new AssertionError("Generated ocean ice");
        for(int y=chunk.getMinY();y<GreatSea.CONFIG.seaLevel;y++)if(chunk.getBlockState(frozen.atY(y)).is(net.minecraft.world.level.block.Blocks.GRAVEL))gravel++;
        if(gravel==0)throw new AssertionError("Cold ocean lost its gravel floor");
        var block=world.getWorld().getBlockAt(frozen.getX(),frozen.getY(),frozen.getZ());var state=block.getState();state.setType(org.bukkit.Material.ICE);
        var freezing=new org.bukkit.event.block.BlockFormEvent(block,state);org.bukkit.Bukkit.getPluginManager().callEvent(freezing);
        if(!freezing.isCancelled())throw new AssertionError("Natural ocean freezing allowed");
        var placed=new net.minecraft.core.BlockPos(16,100,16);world.setBlock(placed,net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState(),3);
        if(!world.getBlockState(placed).is(net.minecraft.world.level.block.Blocks.PACKED_ICE))throw new AssertionError("Player-placed ice blocked");
        report.append("PASS frozen-ocean generation has no ice, cold gravel retained, natural freezing blocked and placed ice preserved\n");
    }
    private static void playerChecks(PaperPlugin plugin,net.minecraft.server.level.ServerLevel world,net.minecraft.server.level.ServerLevel nether,StringBuilder report)throws Exception{
        var actor=new net.minecraft.server.level.ServerPlayer(world.getServer(),world,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"ParityCheck"),net.minecraft.server.level.ClientInformation.createDefault());
        var real=actor.getBukkitEntity();var mode=new org.bukkit.GameMode[]{org.bukkit.GameMode.SURVIVAL};
        var player=(org.bukkit.entity.Player)java.lang.reflect.Proxy.newProxyInstance(PaperVerifier.class.getClassLoader(),new Class[]{org.bukkit.entity.Player.class},(proxy,method,args)->{
            if(method.getName().equals("getGameMode"))return mode[0];
            if(method.getName().equals("setGameMode")){mode[0]=(org.bukkit.GameMode)args[0];return null;}
            if(method.getName().equals("closeInventory"))return null;
            try{return method.invoke(real,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
        });
        actor.setPos(0,100,0);player.getEnderChest().setItem(0,new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND,7));
        actor.setPos(12_550_900,100,0);plugin.updatePlayer(player);
        if(player.getEnderChest().getItem(0)!=null)throw new AssertionError("Farlands chest initial bank");
        player.getEnderChest().setItem(0,new org.bukkit.inventory.ItemStack(org.bukkit.Material.EMERALD,11));
        actor.setPos(0,100,0);plugin.updatePlayer(player);
        if(player.getEnderChest().getItem(0).getAmount()!=7)throw new AssertionError("normal chest bank");
        actor.setPos(12_550_900,100,0);plugin.updatePlayer(player);
        if(player.getEnderChest().getItem(0).getAmount()!=11)throw new AssertionError("Farlands chest bank");
        actor.setServerLevel(nether);actor.setPos(0,-205,0);plugin.updatePlayer(player);
        if(mode[0]!=org.bukkit.GameMode.ADVENTURE)throw new AssertionError("Underworld adventure entry");
        actor.setPos(0,30,0);plugin.updatePlayer(player);
        if(mode[0]!=org.bukkit.GameMode.SURVIVAL)throw new AssertionError("Underworld survival restore");
        mode[0]=org.bukkit.GameMode.ADVENTURE;actor.setPos(0,-205,0);plugin.updatePlayer(player);actor.setPos(0,30,0);plugin.updatePlayer(player);
        if(mode[0]!=org.bukkit.GameMode.ADVENTURE)throw new AssertionError("Existing adventure preserved");
        report.append("PASS isolated player Underworld mode and two Ender-chest banks\n");
        actor.setServerLevel(world);actor.setPos(12_570_900,100,0);actor.startFallFlying();
        var source=new net.minecraft.world.entity.projectile.FireworkRocketEntity(net.minecraft.world.entity.EntityType.FIREWORK_ROCKET,world);
        source.attachedToEntity=actor;source.getEntityData().set(net.minecraft.world.entity.projectile.FireworkRocketEntity.DATA_ATTACHED_TO_TARGET,java.util.OptionalInt.of(actor.getId()));
        var controlled=RocketHooks.copy(source);controlled.lifetime=80;
        var velocity=new net.minecraft.world.phys.Vec3(.12,-.08,.05);actor.setDeltaMovement(velocity);controlled.tick();
        if(!actor.getDeltaMovement().equals(velocity)||controlled.life!=1)throw new AssertionError("Active rocket blocked velocity/lifetime");
        actor.setPos(0,100,0);controlled.tick();if(actor.getDeltaMovement().equals(velocity))throw new AssertionError("Ordinary rocket boost");
        report.append("PASS active rocket velocity blocked in Farlands and restored outside\n");
        var chunk=world.getChunk(0,0);var debris=new net.minecraft.core.BlockPos(0,-50,0);var vault=new net.minecraft.core.BlockPos(1,-50,0);var regular=new net.minecraft.core.BlockPos(2,-50,0);
        chunk.setBlockState(debris,net.minecraft.world.level.block.Blocks.ANCIENT_DEBRIS.defaultBlockState(),3);
        chunk.setBlockState(vault,net.minecraft.world.level.block.Blocks.VAULT.defaultBlockState().setValue(net.minecraft.world.level.block.VaultBlock.OMINOUS,true),3);
        chunk.setBlockState(regular,net.minecraft.world.level.block.Blocks.VAULT.defaultBlockState(),3);PaperGenerator.clean(chunk);
        if(!chunk.getBlockState(debris).isAir()||!chunk.getBlockState(vault).isAir()||!chunk.getBlockState(regular).is(net.minecraft.world.level.block.Blocks.VAULT))throw new AssertionError("Debris/vault cleanup");
        report.append("PASS ancient debris and ominous vault become air; regular vault retained\n");
        actor.setPos(12_570_900,100,0);
        var portal=new org.bukkit.event.player.PlayerPortalEvent(real,real.getLocation(),new org.bukkit.Location(nether.getWorld(),0,60,0),org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);plugin.portal(portal);
        if(!portal.isCancelled())throw new AssertionError("Lit Farlands portal entry");
        var testMob=new net.minecraft.world.entity.animal.cow.Cow(net.minecraft.world.entity.EntityType.COW,world);testMob.setPos(12_570_900,100,0);
        var egg=new org.bukkit.event.entity.CreatureSpawnEvent((org.bukkit.entity.LivingEntity)testMob.getBukkitEntity(),org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER_EGG);plugin.spawn(egg);
        if(!egg.isCancelled())throw new AssertionError("Spawn eggs must be blocked");
        int accepted=0;for(int i=0;i<100000;i++){
            var natural=new org.bukkit.event.entity.CreatureSpawnEvent((org.bukkit.entity.LivingEntity)testMob.getBukkitEntity(),org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL);plugin.spawn(natural);if(!natural.isCancelled())accepted++;
        }
        if(accepted!=0)throw new AssertionError("Natural spawn rate "+accepted);
        for(var reason:org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.values()){
            var creature=new org.bukkit.event.entity.CreatureSpawnEvent((org.bukkit.entity.LivingEntity)testMob.getBukkitEntity(),reason);plugin.spawn(creature);if(!creature.isCancelled())throw new AssertionError("Mob source "+reason);
        }
        report.append("PASS lit Farlands portal blocked; all mob reasons including eggs blocked; natural filter accepted ").append(accepted).append(" / 100000\n");
    }
}



