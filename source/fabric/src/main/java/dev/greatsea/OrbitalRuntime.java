package dev.greatsea;

import net.minecraft.block.*;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.mob.BreezeEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.projectile.BreezeWindChargeEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Unit;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.BlockView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import net.minecraft.world.rule.GameRules;

import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;

/** Server-side controls for the orbital silos and the pearl launcher's redstone pulse. */
public final class OrbitalRuntime {
    private static final int LOAD_TICKS=600, STAB_DELAY=6, ORBITAL_TIME_SCALE=64;
    private static final int BREEZE_SHOT_TICKS=15+4+10;
    private static final long LOADING_WINDOW_MILLIS=600_000;
    private static final BigInteger ALL_STOCKED=BigInteger.ONE.shiftLeft(FarlandsOrbitals.SILOS).subtract(BigInteger.ONE);
    private static final int[] NUKE_RING_COUNTS={22,40,55,66,79,89,94,103,112};
    private static final int NUKE_FUSE=78, NUKE_SPREAD_DELAY=6;
    private static final ChunkTicketType NUKE_TICKET=new ChunkTicketType(400,
            ChunkTicketType.FOR_LOADING|ChunkTicketType.FOR_SIMULATION|ChunkTicketType.RESETS_IDLE_TIMEOUT);
    private static final ChunkTicketType STAB_TICKET=new ChunkTicketType(40,ChunkTicketType.FOR_LOADING);
    private static final String ROD_CHARGE="unstableterrain.orbital_charge";
    public static final String BREEZE_TAG="unstableterrain.orbital_breeze";
    public static final String GOLEM_TAG="unstableterrain.orbital_golem";
    private static final Map<MinecraftServer,OrbitalRuntime> INSTANCES=new WeakHashMap<>();

    enum Shot {
        STAB("Stab Shot"), NUKE("Nuke Shot");
        final String title;
        Shot(String title){this.title=title;}
        static Shot at(int silo){return (silo&1)==0?STAB:NUKE;}
    }
    private record Magazine(boolean leverOn,long expiresAt,BigInteger stocked,BigInteger damaged,Map<UUID,Shot> charges) {
        boolean ready(){return leverOn&&expiresAt>System.currentTimeMillis();}
        boolean available(int silo){return ready()&&stocked.testBit(silo)&&!damaged.testBit(silo);}
    }
    private static final class Cast {
        final ItemStack rod;
        final ServerWorld source;
        FishingBobberEntity bobber;
        int silo=-1;
        Pull pull;
        boolean departed;
        Cast(ItemStack rod,ServerWorld source){this.rod=rod;this.source=source;}
    }
    private record Pull(Vec3d before,int expires) {}
    private record Launch(ServerWorld world,BlockPos origin,int expires) {}
    private record Nuke(ServerWorld world,BlockPos center,ServerPlayerEntity owner,ItemStack rod,UUID token,int expires) {}
    private record NukeBurst(Nuke shot,TntEntity core,int spreadAt) {}
    private static final class Stab extends ExplosionBehavior {
        private static final int WIDTH=15, DEPTH=14, LAYERS=123, MOUTH=4;
        // Connected air slices from Stab Shot Hole.litematic, packed bottom to top.
        private static final short[][] PROFILE=readProfile();
        final ServerWorld world;
        final BlockPos target;
        final ServerPlayerEntity owner;
        final int startsAt;
        final int surface, rotation;
        int y;
        Stab(ServerWorld world,BlockPos target,ServerPlayerEntity owner,int startsAt){
            this.world=world;this.target=target;this.owner=owner;this.startsAt=startsAt;
            surface=world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,target.getX(),target.getZ())-1;
            rotation=world.getRandom().nextInt(4);
            y=world.getTopYInclusive();
        }
        private static short[][] readProfile(){
            try(var in=new DataInputStream(Objects.requireNonNull(OrbitalRuntime.class.getResourceAsStream(
                    "/assets/unstableterrain/farlands/stab-shot-profile.bin")))){
                var layers=new short[LAYERS][DEPTH];
                for(short[] layer:layers)for(int z=0;z<layer.length;z++)layer[z]=in.readShort();
                return layers;
            }catch(IOException e){throw new ExceptionInInitializerError(e);}
        }
        private short[] layer(int atY){
            int depth=Math.max(0,surface-atY);
            if(depth<LAYERS)return PROFILE[LAYERS-1-depth];
            int end=LAYERS-MOUTH-1;
            int folded=(depth-LAYERS+1)%(2*end);
            return PROFILE[Math.min(folded,2*end-folded)];
        }
        @Override public boolean canDestroyBlock(Explosion explosion,BlockView view,BlockPos pos,BlockState state,float power){
            int x=pos.getX()-target.getX(),z=pos.getZ()-target.getZ();
            int profileX=7+switch(rotation){case 1->z;case 2->-x;case 3->-z;default->x;};
            int profileZ=7+switch(rotation){case 1->-x;case 2->-z;case 3->x;default->z;};
            return profileX>=0&&profileX<WIDTH&&profileZ>=0&&profileZ<DEPTH
                    &&(layer(pos.getY())[profileZ]&(1<<profileX))!=0&&world.getWorldBorder().contains(pos);
        }
        private boolean hasBlocks(int y,BlockPos.Mutable pos){
            short[] layer=layer(y);
            for(int z=0;z<DEPTH;z++){
                int row=layer[z];
                while(row!=0){
                    int dx=Integer.numberOfTrailingZeros(row)-7,dz=z-7;
                    int x=switch(rotation){case 1->-dz;case 2->-dx;case 3->dz;default->dx;};
                    int zz=switch(rotation){case 1->dx;case 2->-dz;case 3->-dx;default->dz;};
                    if(!world.getBlockState(pos.set(target.getX()+x,y,target.getZ()+zz)).isAir())return true;
                    row&=row-1;
                }
            }
            return false;
        }
        boolean tick(int now){
            if(now<startsAt)return false;
            int floor=world.getBottomY();
            var pos=new BlockPos.Mutable();
            for(int batch=0;batch<12&&y>=floor;y--){
                if(!hasBlocks(y,pos))continue;
                double span=surface-y<MOUTH?1.85:1.35;
                int quadrant=(y+rotation)&3;
                double x=target.getX()+.5+((quadrant&2)==0?-span:span)+(world.getRandom().nextDouble()-.5)*.5;
                double z=target.getZ()+.5+((quadrant&1)==0?-span:span)+(world.getRandom().nextDouble()-.5)*.5;
                // Overlapping charges open solid rock before the outer rays reach the shaft walls.
                for(int charge=0;charge<3&&hasBlocks(y,pos);charge++)
                    world.createExplosion(owner,Explosion.createDamageSource(world,owner),this,x,y+.55,z,4.0f,false,World.ExplosionSourceType.TNT);
                batch++;
            }
            return y<floor;
        }
    }

    private final Path file;
    private final Map<ServerPlayerEntity,Cast> casts=new WeakHashMap<>();
    private final Map<ServerPlayerEntity,Launch> launchers=new WeakHashMap<>();
    private final List<Stab> stabs=new ArrayList<>();
    private Magazine magazine;
    private int loadTicks,clock,breezeTicks;
    private boolean fastSky;
    private Nuke pendingNuke;
    private NukeBurst nukeBurst;
    private int nukeCooldown;
    private ServerWorld flyingNukeWorld;

    private OrbitalRuntime(MinecraftServer server){
        file=server.getSavePath(WorldSavePath.ROOT).resolve("unstableterrain-orbitals.properties");
        magazine=read();
    }

    private static OrbitalRuntime runtime(ServerWorld world){
        return INSTANCES.computeIfAbsent(world.getServer(),OrbitalRuntime::new);
    }
    private static boolean active(ServerWorld world){
        return StructurePolicy.enabled()&&FarlandsWorld.supported(world.getServer().getOverworld());
    }
    private static boolean loaded(ServerWorld world,BlockPos pos){
        return world.isChunkLoaded(ChunkPos.toLong(pos.getX()>>4,pos.getZ()>>4));
    }
    private static void feedback(ServerPlayerEntity player,String text){player.sendMessage(Text.literal(text),true);}

    private Magazine read(){
        if(!Files.exists(file))return new Magazine(false,0,ALL_STOCKED,BigInteger.ZERO,Map.of());
        var properties=new java.util.Properties();
        try(var in=Files.newBufferedReader(file)){
            properties.load(in);
            var charges=new HashMap<UUID,Shot>();
            for(String key:properties.stringPropertyNames())if(key.startsWith("rod.")){
                charges.put(UUID.fromString(key.substring(4)),Shot.valueOf(properties.getProperty(key)));
            }
            boolean leverOn=Boolean.parseBoolean(properties.getProperty("leverOn","false"));
            long expiresAt=leverOn?Long.parseLong(properties.getProperty("expiresAt","0")):0;
            var stocked=new BigInteger(properties.getProperty("stocked","0")).and(ALL_STOCKED);
            var damaged=new BigInteger(properties.getProperty("damaged","0")).and(ALL_STOCKED);
            return new Magazine(leverOn,expiresAt,stocked,damaged,Map.copyOf(charges));
        }catch(IOException|IllegalArgumentException error){
            GreatSea.LOGGER.error("Could not read Orbital magazine {}",file,error);
            return new Magazine(false,0,BigInteger.ZERO,BigInteger.ZERO,Map.of());
        }
    }
    private boolean save(Magazine next){
        var properties=new java.util.Properties();
        properties.setProperty("ready",Boolean.toString(next.ready()));
        properties.setProperty("leverOn",Boolean.toString(next.leverOn));
        properties.setProperty("expiresAt",Long.toString(next.expiresAt));
        properties.setProperty("stocked",next.stocked.toString());
        properties.setProperty("damaged",next.damaged.toString());
        next.charges.forEach((token,shot)->properties.setProperty("rod."+token,shot.name()));
        try{
            Path temporary=Files.createTempFile(file.getParent(),"unstableterrain-orbitals-",".tmp");
            try{
                try(var output=Files.newBufferedWriter(temporary)){properties.store(output,"Unstable Terrain Orbital magazine");}
                try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(AtomicMoveNotSupportedException error){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
                magazine=next;
                return true;
            }finally{Files.deleteIfExists(temporary);}
        }catch(IOException error){GreatSea.LOGGER.error("Could not save Orbital magazine {}",file,error);return false;}
    }

    public static void stopped(MinecraftServer server){INSTANCES.remove(server);}
    public static ServerWorld flyingNukeWorld(MinecraftServer server){
        var runtime=INSTANCES.get(server);
        return runtime!=null&&runtime.clock<runtime.nukeCooldown?runtime.flyingNukeWorld:null;
    }
    public static boolean controlMob(Set<String> tags){return tags.contains(BREEZE_TAG)||tags.contains(GOLEM_TAG);}
    public static void blockBroken(ServerWorld world,BlockPos pos){
        if(pos.getX()<GreatSea.FARLANDS.entryBoundary||world.getRegistryKey()!=World.OVERWORLD||!active(world))return;
        int silo=FarlandsOrbitals.section(FarlandsOrbitals.b(pos.getX(),pos.getZ()));
        if(silo<0||silo>=FarlandsOrbitals.SILOS||!FarlandsOrbitals.hasBlock(world.getSeed(),pos.getX(),pos.getY(),pos.getZ()))return;
        var runtime=runtime(world);
        var magazine=runtime.magazine;
        if(magazine.damaged.testBit(silo))return;
        runtime.save(new Magazine(magazine.leverOn,magazine.expiresAt,magazine.stocked,magazine.damaged.setBit(silo),magazine.charges));
    }
    public static void tick(MinecraftServer server){
        var world=server.getOverworld();
        if(!active(world))return;
        var system=runtime(world);
        system.clock++;
        system.control(world);
        for(var player:server.getPlayerManager().getPlayerList()){
            system.renameLegacyRod(player.getMainHandStack());
            system.renameLegacyRod(player.getOffHandStack());
            system.updateCast(player);
            system.updateLauncher(player);
        }
        system.stabs.removeIf(stab->stab.tick(system.clock));
        system.updateNuke();
    }

    private void control(ServerWorld world){
        BlockPos lever=FarlandsOrbitals.controlLever(world.getSeed());
        if(!loaded(world,lever)){restoreClock(world);return;}
        BlockState state=world.getBlockState(lever);
        // The signed switch is powered in the original schematic with its lamp off.
        boolean on=state.isOf(Blocks.LEVER)&&!state.get(Properties.POWERED);
        if(!on){
            if(magazine.leverOn){
                var disabled=new Magazine(false,0,magazine.stocked,magazine.damaged,magazine.charges);
                if(!save(disabled))magazine=disabled;
            }
            loadTicks=0;
            breezeTicks=0;
            restoreClock(world);
            return;
        }
        if(!world.shouldTickEntityAt(lever)){restoreClock(world);return;}
        if(!magazine.leverOn){
            if(!save(new Magazine(true,0,magazine.stocked,magazine.damaged,magazine.charges)))return;
            notifyPlatform(world,lever,"Charging Orbitals. Leave the lever on for 30 seconds.");
        }
        boolean vanillaCycle=world.getGameRules().getValue(GameRules.ADVANCE_TIME);
        world.setTimeOfDay(world.getTimeOfDay()+ORBITAL_TIME_SCALE-(vanillaCycle?1:0));
        syncClock(world,false);
        fastSky=true;
        speedUpBreeze(world,new Box(lever).expand(18));
        if(magazine.expiresAt==0&&Math.min(++loadTicks,LOAD_TICKS)==LOAD_TICKS){
            loadTicks=LOAD_TICKS;
            if(save(new Magazine(true,System.currentTimeMillis()+LOADING_WINDOW_MILLIS,ALL_STOCKED,magazine.damaged,magazine.charges)))
                notifyPlatform(world,lever,"Orbitals armed. Keep the lever on and load a rod within ten minutes.");
        }
    }
    private void restoreClock(ServerWorld world){
        if(!fastSky)return;
        fastSky=false;
        syncClock(world,world.getGameRules().getValue(GameRules.ADVANCE_TIME));
    }
    private void speedUpBreeze(ServerWorld world,Box platform){
        var breezes=world.getEntitiesByClass(BreezeEntity.class,platform,b->b.isAlive()&&b.getCommandTags().contains(BREEZE_TAG));
        if(breezes.isEmpty())return;
        var golems=world.getEntitiesByClass(IronGolemEntity.class,platform,g->g.isAlive()&&g.getCommandTags().contains(GOLEM_TAG));
        if(golems.isEmpty())return;
        var golem=golems.getFirst();
        breezeTicks+=ORBITAL_TIME_SCALE;
        int shots=breezeTicks/BREEZE_SHOT_TICKS;
        breezeTicks%=BREEZE_SHOT_TICKS;
        for(var breeze:breezes){
            breeze.getBrain().remember(MemoryModuleType.BREEZE_SHOOT_CHARGING,Unit.INSTANCE,2);
            for(int shot=0;shot<shots;shot++)
                ProjectileEntity.spawnWithVelocity(new BreezeWindChargeEntity(breeze,world),world,ItemStack.EMPTY,
                        golem.getX()-breeze.getX(),golem.getBodyY(.3)-breeze.getChargeY(),golem.getZ()-breeze.getZ(),.7f,
                        5-world.getDifficulty().getId()*4);
        }
    }
    private static void syncClock(ServerWorld world,boolean vanillaCycle){
        var packet=new WorldTimeUpdateS2CPacket(world.getTime(),world.getTimeOfDay(),vanillaCycle);
        for(var player:world.getPlayers())player.networkHandler.sendPacket(packet);
    }
    private static void notifyPlatform(ServerWorld world,BlockPos lever,String text){
        for(var player:world.getPlayers())if(player.getEntityPos().squaredDistanceTo(Vec3d.ofCenter(lever))<32*32)feedback(player,text);
    }

    /** Returns true when a charged rod use was handled instead of normal fishing. */
    public static boolean rod(ServerPlayerEntity player,Hand hand,boolean freshCast){
        var world=player.getEntityWorld();
        if(!active(world))return false;
        var system=runtime(world);
        var stack=player.getStackInHand(hand);
        UUID token=charge(stack);
        if(token!=null){
            system.fire(player,stack,token);
            return true;
        }
        if(freshCast&&world.getRegistryKey()==World.OVERWORLD)system.casts.put(player,new Cast(stack,world));
        else if(!freshCast)cancelCast(player);
        return false;
    }
    public static void cancelCast(ServerPlayerEntity player){
        var system=INSTANCES.get(player.getEntityWorld().getServer());
        if(system!=null)system.casts.remove(player);
    }
    private static UUID charge(ItemStack stack){
        String tag=stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt().getString(ROD_CHARGE,"");
        if(tag.isEmpty())return null;
        try{return UUID.fromString(tag);}catch(IllegalArgumentException error){return null;}
    }
    private void renameLegacyRod(ItemStack stack){
        var name=stack.get(DataComponentTypes.CUSTOM_NAME);
        if(!stack.isOf(Items.FISHING_ROD)||name==null||!name.getString().endsWith(" (single use)"))return;
        Shot shot=magazine.charges.get(charge(stack));
        if(shot!=null)stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal(shot.title));
    }
    private static boolean carries(ServerPlayerEntity player,ItemStack stack){
        if(stack.isEmpty())return false;
        for(int i=0;i<player.getInventory().size();i++)if(player.getInventory().getStack(i)==stack)return true;
        return player.getOffHandStack()==stack;
    }
    private void updateCast(ServerPlayerEntity player){
        Cast cast=casts.get(player);
        if(cast==null)return;
        if(!carries(player,cast.rod)){casts.remove(player);return;}
        if(cast.pull!=null){
            if(!magazine.available(cast.silo)){
                explainUnavailable(player,cast.silo);
                casts.remove(player);
                return;
            }
            Pull pull=cast.pull;
            var world=player.getEntityWorld();
            var plate=FarlandsOrbitals.plate(cast.source.getSeed(),cast.silo);
            boolean travelled=world==cast.source&&player.getEntityPos().squaredDistanceTo(pull.before)>=2500
                    &&player.getEntityPos().squaredDistanceTo(Vec3d.ofCenter(plate))>=2500;
            if(!cast.departed&&travelled){
                cast.departed=true;
                cast.pull=new Pull(pull.before,clock+600);
                if(cast.source.shouldTickEntityAt(plate))feedback(player,"Waiting for the silo to unload.");
            }else if(clock>=pull.expires){
                if(cast.departed){feedback(player,"The silo stayed loaded. Relink the rod to try again.");casts.remove(player);}
                else cast.pull=null;
                return;
            }
            if(cast.departed&&world==cast.source&&!cast.source.shouldTickEntityAt(plate)){
                issue(player,cast);
                casts.remove(player);
            }
            return;
        }
        var world=player.getEntityWorld();
        var hook=player.fishHook;
        if(world!=cast.source){casts.remove(player);return;}
        if(hook==null){
            // The linked bobber can disappear as its chunk leaves simulation.
            if(cast.silo<0)casts.remove(player);
            return;
        }
        cast.bobber=hook;
        var pos=hook.getBlockPos();
        if(Math.abs(pos.getX()-FarlandsOrbitals.plate(world.getSeed(),0).getX())>2){cast.silo=-1;return;}
        int silo=FarlandsOrbitals.nearestSilo(pos);
        var plate=FarlandsOrbitals.plate(world.getSeed(),silo);
        if(hook.getEntityPos().squaredDistanceTo(Vec3d.ofBottomCenter(plate))>1.1*1.1
                ||!loaded(world,plate)||!world.getBlockState(plate).isOf(Blocks.OAK_PRESSURE_PLATE)){
            cast.silo=-1;
        }else if(cast.silo!=silo){
            cast.silo=silo;
            if(magazine.available(silo))feedback(player,Shot.at(silo).title+" linked. Have a partner pull your pearl stasis at least 50 blocks away.");
            else explainUnavailable(player,silo);
        }
    }
    /** Records the player position immediately before an ender-pearl pull. */
    public static void beforePull(ServerPlayerEntity player){
        Cast cast=validPull(player);
        if(cast!=null)runtime(cast.source).beginPull(player,cast);
    }
    private void beginPull(ServerPlayerEntity player,Cast cast){
        cast.departed=false;
        cast.pull=new Pull(player.getEntityPos(),clock+10);
    }
    public static boolean blocksPortal(ServerWorld world,int x,int z){
        return FarlandsWorld.supported(world)&&FarlandsWorld.contains(x,z)
                &&(!GameplaySettings.Rule.FARLANDS_PORTALS.enabled()
                ||!SpokelandsTerrain.contains(x,z)&&FarlandsTerrain.stage(x,z)==FarlandsTerrain.Stage.ORBITALS);
    }
    public static int simulatePull(ServerPlayerEntity player)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        Cast cast=validPull(player);
        if(cast==null)throw pullError("Link a fishing rod to an armed Orbital station first.");
        var world=cast.source;
        var plate=FarlandsOrbitals.plate(world.getSeed(),cast.silo);
        // Clear the entity-ticking radius even when a solo player has increased simulation distance.
        int distance=Math.max(56,(world.getServer().getPlayerManager().getSimulationDistance()+2)*16);
        int x=Math.max(player.getBlockX(),plate.getX())+distance;
        int originZ=player.getBlockZ();
        var system=runtime(world);
        for(int offset=0;offset<=16;offset+=4){
            int z=originZ+offset;
            if(!world.getWorldBorder().contains(new BlockPos(x,player.getBlockY(),z)))continue;
            int y=world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z);
            var feet=new BlockPos(x,y,z);
            if(y<=world.getBottomY()||y>=world.getTopYInclusive()||!world.getBlockState(feet.down()).isSolidBlock(world,feet.down())
                    ||!world.getBlockState(feet).isAir()||!world.getBlockState(feet.up()).isAir())continue;
            system.beginPull(player,cast);
            if(player.teleport(world,x+.5,y,z+.5,Set.of(),player.getYaw(),player.getPitch(),true))return 1;
            cast.pull=null;
            break;
        }
        throw pullError("Could not find a safe landing for the test pull.");
    }
    private static com.mojang.brigadier.exceptions.CommandSyntaxException pullError(String message){
        return new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Text.literal(message)).create();
    }
    private static Cast validPull(ServerPlayerEntity player){
        var world=player.getEntityWorld();
        if(world.getRegistryKey()!=World.OVERWORLD||!active(world))return null;
        var system=runtime(world);
        Cast cast=system.casts.get(player);
        if(cast==null||cast.source!=world||cast.departed||cast.silo<0||!carries(player,cast.rod))return null;
        var plate=FarlandsOrbitals.plate(world.getSeed(),cast.silo);
        if(world.shouldTickEntityAt(plate)&&(cast.bobber==null||cast.bobber!=player.fishHook
                ||cast.bobber.getEntityPos().squaredDistanceTo(Vec3d.ofBottomCenter(plate))>1.1*1.1
                ||!world.getBlockState(plate).isOf(Blocks.OAK_PRESSURE_PLATE)))return null;
        return system.magazine.available(cast.silo)?cast:null;
    }
    private void issue(ServerPlayerEntity player,Cast cast){
        if(!magazine.available(cast.silo)){explainUnavailable(player,cast.silo);return;}
        var token=UUID.randomUUID();
        Shot shot=Shot.at(cast.silo);
        var charges=new HashMap<>(magazine.charges);
        charges.put(token,shot);
        if(!save(new Magazine(magazine.leverOn,magazine.expiresAt,magazine.stocked.clearBit(cast.silo),magazine.damaged,Map.copyOf(charges))))return;
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA,cast.rod,data->data.putString(ROD_CHARGE,token.toString()));
        cast.rod.set(DataComponentTypes.CUSTOM_NAME,Text.literal(shot.title));
        feedback(player,shot.title+" loaded. Look at a target block and cast to fire.");
    }
    private void explainUnavailable(ServerPlayerEntity player,int silo){
        feedback(player,magazine.damaged.testBit(silo)?"This Orbital station is damaged.":"Turn on the lever for 30 seconds and keep it on to load this station.");
    }

    private void fire(ServerPlayerEntity player,ItemStack rod,UUID token){
        var world=player.getEntityWorld();
        Shot shot=magazine.charges.get(token);
        if(shot==null){feedback(player,"This Orbital rod has already been spent.");return;}
        var from=player.getEyePos();
        HitResult hit=world.raycast(new RaycastContext(from,from.add(player.getRotationVec(1).multiply(128)),
                RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player));
        boolean hitBlock=hit.getType()==HitResult.Type.BLOCK;
        BlockPos block=hitBlock?((BlockHitResult)hit).getBlockPos():BlockPos.ofFloored(hit.getPos());
        if((shot==Shot.NUKE&&!hitBlock)||!world.getWorldBorder().contains(block)||!loaded(world,block)){
            feedback(player,"Aim at a loaded block within 128 blocks.");return;
        }
        if(shot==Shot.STAB&&stabs.size()>=4){feedback(player,"Wait for the current Stab Shots to finish.");return;}
        if(shot==Shot.NUKE){
            if(pendingNuke!=null||clock<nukeCooldown){feedback(player,"Wait for the current Nuke Shot to finish.");return;}
            if(!world.getWorldBorder().contains(block.add(52,0,52))||!world.getWorldBorder().contains(block.add(-52,0,-52))){
                feedback(player,"Choose a target farther from the world border.");return;
            }
            world.getChunkManager().addTicket(NUKE_TICKET,new ChunkPos(block),6);
            pendingNuke=new Nuke(world,block,player,rod,token,clock+200);
            return;
        }
        if(!spend(player,rod,token))return;
        world.getChunkManager().addTicket(STAB_TICKET,new ChunkPos(block),1);
        stabs.add(new Stab(world,block,player,clock+STAB_DELAY));
    }

    private boolean spend(ServerPlayerEntity player,ItemStack rod,UUID token){
        if(!magazine.charges.containsKey(token))return false;
        var charges=new HashMap<>(magazine.charges);
        charges.remove(token);
        if(!save(new Magazine(magazine.leverOn,magazine.expiresAt,magazine.stocked,magazine.damaged,Map.copyOf(charges))))return false;
        rod.decrement(1);
        player.getEntityWorld().playSound(null,player.getBlockPos(),SoundEvents.ENTITY_ITEM_BREAK.value(),SoundCategory.PLAYERS,.8f,1f);
        return true;
    }

    private void updateNuke(){
        if(nukeBurst!=null&&clock>=nukeBurst.spreadAt){
            spreadNuke(nukeBurst);
            nukeBurst=null;
        }
        Nuke nuke=pendingNuke;
        if(nuke==null)return;
        if(!carries(nuke.owner,nuke.rod)||nuke.owner.getEntityWorld()!=nuke.world||clock>=nuke.expires){
            pendingNuke=null;
            feedback(nuke.owner,"Nuke canceled before launch.");
            return;
        }
        BlockPos center=nuke.center;
        for(int x=(center.getX()-52)>>4;x<=(center.getX()+52)>>4;x++)
            for(int z=(center.getZ()-52)>>4;z<=(center.getZ()+52)>>4;z++)
                if(!nuke.world.shouldTickEntityAt(new BlockPos(x*16,center.getY(),z*16)))return;
        pendingNuke=null;
        if(!spend(nuke.owner,nuke.rod,nuke.token))return;
        nukeCooldown=clock+125;
        flyingNukeWorld=nuke.world;
        double startY=Math.min(nuke.world.getTopYInclusive()-1,center.getY()+72);
        var core=tnt(nuke.world,center.getX()+.5,startY,center.getZ()+.5,nuke.owner,NUKE_FUSE,Vec3d.ZERO);
        nukeBurst=new NukeBurst(nuke,core,clock+NUKE_SPREAD_DELAY);
    }

    private static void spreadNuke(NukeBurst burst){
        TntEntity core=burst.core;
        if(core.isRemoved()||core.getFuse()<=0)return;
        int fuse=core.getFuse();
        double distancePerUnitVelocity=(1-Math.pow(.98,fuse))/.02;
        var random=burst.shot.world.getRandom();
        for(int ring=0;ring<NUKE_RING_COUNTS.length;ring++){
            double radius=ring==0?6.0:7.0+ring*5.5;
            double spread=ring/(double)(NUKE_RING_COUNTS.length-1);
            int cells=NUKE_RING_COUNTS[ring];
            var gaps=new double[cells];
            double perimeter=0;
            for(int cell=0;cell<cells;cell++){
                double gap=1+(random.nextDouble()-.5)*(.36+.8*spread);
                if(random.nextDouble()<.05+.16*spread)gap+=.35+random.nextDouble()*(.45+1.4*spread);
                gaps[cell]=gap;
                perimeter+=gap;
            }
            double angle=random.nextDouble()*Math.TAU;
            double phase=random.nextDouble()*Math.TAU;
            for(int cell=0;cell<cells;cell++){
                double radialJitter=(random.nextDouble()-.5)*(.22+.62*spread);
                double radialDrift=Math.sin(angle*3+phase)*(.12+.16*spread);
                double speed=(radius+radialJitter+radialDrift)/distancePerUnitVelocity;
                var velocity=new Vec3d(Math.cos(angle)*speed,core.getVelocity().y,Math.sin(angle)*speed);
                tnt(burst.shot.world,core.getX(),core.getY(),core.getZ(),burst.shot.owner,fuse,velocity);
                angle+=Math.TAU*gaps[cell]/perimeter;
            }
        }
    }
    private static TntEntity tnt(ServerWorld world,double x,double y,double z,ServerPlayerEntity owner,int fuse,Vec3d velocity){
        var tnt=new TntEntity(world,x,y,z,owner);
        tnt.setVelocity(velocity);tnt.setFuse(fuse);
        world.spawnEntity(tnt);
        return tnt;
    }

    /** Observe the portable launcher button without replacing its vanilla interaction. */
    public static void useBlock(ServerPlayerEntity player,Hand hand,BlockPos pos){
        var world=player.getEntityWorld();
        if(world.getRegistryKey()!=World.OVERWORLD||!active(world))return;
        if(hand==Hand.MAIN_HAND&&world.getBlockState(pos).isOf(Blocks.POLISHED_BLACKSTONE_BUTTON)){
            var system=runtime(world);
            var origin=pos.subtract(PearlLauncher.BUTTON);
            if(PearlLauncher.matches(world,origin))system.launchers.put(player,new Launch(world,origin,system.clock+24));
        }
    }
    private void updateLauncher(ServerPlayerEntity player){
        Launch launch=launchers.get(player);
        if(launch==null)return;
        if(clock>=launch.expires||player.getEntityWorld()!=launch.world){launchers.remove(player);return;}
        var world=launch.world;
        var pulse=world.getBlockState(launch.origin.add(PearlLauncher.REPEATER));
        if(!pulse.isOf(Blocks.REPEATER)||!pulse.get(Properties.POWERED))return;
        launchers.remove(player);
        var pad=Vec3d.ofBottomCenter(launch.origin.add(PearlLauncher.PAD));
        if(player.getEntityPos().squaredDistanceTo(pad)>2.5*2.5){feedback(player,"Stand on the gold launch pad.");return;}
        if(!(world.getBlockEntity(launch.origin.add(PearlLauncher.BARREL)) instanceof BarrelBlockEntity barrel))return;
        int slot=0;
        while(slot<barrel.size()&&!barrel.getStack(slot).isOf(Items.ENDER_PEARL))slot++;
        if(slot==barrel.size()){feedback(player,"Load an ender pearl into the launcher's barrel.");return;}
        try{
            var feet=FarlandsCommand.destination(world,FarlandsTerrain.Stage.PLAINS);
            world.spawnParticles(ParticleTypes.PORTAL,pad.x,pad.y+.5,pad.z,56,.7,.6,.7,.3);
            if(player.teleport(world,feet.getX()+.5,feet.getY(),feet.getZ()+.5,Set.of(),player.getYaw(),player.getPitch(),true)){
                barrel.removeStack(slot,1);
                player.setVelocity(Vec3d.ZERO);player.fallDistance=0;player.stopGliding();
                feedback(player,"Pearl launch complete. Farlands entrance.");
            }
        }catch(com.mojang.brigadier.exceptions.CommandSyntaxException error){feedback(player,error.getMessage());}
    }
}


