package dev.greatsea;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.world.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Standalone Paper entry point, with no additional plugin or client dependency. */
public final class PaperPlugin extends JavaPlugin implements Listener {
    static PaperPlugin instance;
    private final PaperUnderworldCoordinates underworldCoordinates=new PaperUnderworldCoordinates();
    private final Set<UUID> managed=new HashSet<>(),automaticModeChanges=new HashSet<>();
    private NamespacedKey autoAdventure,normalChest,farlandsChest,chestActive;
    private org.spigotmc.SpigotWorldConfig orbitalTntConfig;
    private int originalOrbitalTntLimit;
    @Override public void onLoad(){
        instance=this;getDataFolder().mkdirs();
        GreatSea.CONFIG=SeaConfig.load();GreatSea.FARLANDS=FarlandsConfig.load();
        GameplaySettings.initialize(getDataFolder().toPath());
    }
    @Override public void onEnable(){
        RocketHooks.initialize();
        PaperMist.initialize();
        autoAdventure=new NamespacedKey(this,"underworld_adventure");normalChest=new NamespacedKey(this,"normal_ender_items");farlandsChest=new NamespacedKey(this,"farlands_ender_items");chestActive=new NamespacedKey(this,"farlands_ender_active");
        Bukkit.getPluginManager().registerEvents(this,this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,event->PaperCommands.register(event.registrar()));
        for(var world:Bukkit.getWorlds())install(world);
        for(var world:Bukkit.getWorlds())for(var entity:world.getEntities())removeLoadedMob(entity);
        var server=((org.bukkit.craftbukkit.CraftServer)getServer()).getServer();
        Bukkit.getScheduler().runTaskTimer(this,()->{
            restoreOrbitalTntLimit();
            if(!Bukkit.getWorlds().isEmpty()){
                OrbitalRuntime.tick(server);
                var world=OrbitalRuntime.flyingNukeWorld(server);
                if(world!=null&&world.spigotConfig.maxTntTicksPerTick>0){
                    orbitalTntConfig=world.spigotConfig;
                    originalOrbitalTntLimit=orbitalTntConfig.maxTntTicksPerTick;
                    orbitalTntConfig.maxTntTicksPerTick=(int)Math.min(Integer.MAX_VALUE,(long)originalOrbitalTntLimit+661);
                }
            }
            for(var player:Bukkit.getOnlinePlayers())updatePlayer(player);
        },1,1);
        getLogger().info("Unstable Terrain Paper "+getPluginMeta().getVersion());
        if(Boolean.getBoolean("unstableterrain.paperVerify"))Bukkit.getScheduler().runTask(this,()->PaperVerifier.run(this));
        if(Boolean.getBoolean("unstableterrain.paperRevision127Verify"))Bukkit.getScheduler().runTask(this,()->PaperRevision127Verifier.run(this));
        if(Boolean.getBoolean("unstableterrain.paperSpokelandsVerify"))Bukkit.getScheduler().runTask(this,()->PaperSpokelandsVerifier.run(this));
        if(Boolean.getBoolean("unstableterrain.paperRelief1390Verify"))Bukkit.getScheduler().runTask(this,()->PaperRelief1390Verifier.run(this));
        if(Boolean.getBoolean("unstableterrain.paperTerrain126Verify"))Bukkit.getScheduler().runTask(this,()->PaperTerrain126Verifier.run(this));
        if(Boolean.getBoolean("unstableterrain.paperEmpireVerify"))Bukkit.getScheduler().runTask(this,()->PaperEmpireVerifier.run(this));
    }
    @Override public void onDisable(){restoreOrbitalTntLimit();OrbitalRuntime.stopped(((org.bukkit.craftbukkit.CraftServer)getServer()).getServer());underworldCoordinates.clear();for(var player:Bukkit.getOnlinePlayers())PaperMist.clear(player);for(var player:Bukkit.getOnlinePlayers())saveActiveChest(player);}
    private void restoreOrbitalTntLimit(){
        if(orbitalTntConfig!=null){
            orbitalTntConfig.maxTntTicksPerTick=originalOrbitalTntLimit;
            orbitalTntConfig=null;
        }
    }
    @EventHandler public void orbitalTickEnd(com.destroystokyo.paper.event.server.ServerTickEndEvent event){restoreOrbitalTntLimit();}
    private static net.minecraft.server.level.ServerPlayer nativePlayer(Player player){return ((org.bukkit.craftbukkit.entity.CraftPlayer)player).getHandle();}
    private static net.minecraft.world.InteractionHand nativeHand(org.bukkit.inventory.EquipmentSlot hand){
        return hand==org.bukkit.inventory.EquipmentSlot.HAND?net.minecraft.world.InteractionHand.MAIN_HAND:net.minecraft.world.InteractionHand.OFF_HAND;
    }
    @EventHandler(priority=EventPriority.LOWEST) public void initialize(WorldInitEvent event){install(event.getWorld());}
    private void install(World world){
        managed.add(world.getUID());
        if(world.getEnvironment()==World.Environment.THE_END)return;
        var level=((CraftWorld)world).getHandle();var map=level.getChunkSource().chunkMap;
        if(map.worldGenContext.generator() instanceof PaperGenerator)return;
        if(!(map.worldGenContext.generator() instanceof NoiseBasedChunkGenerator vanilla))throw new IllegalStateException("Another custom generator already owns "+world.getName());
        boolean nether=world.getEnvironment()==World.Environment.NETHER;
        if(nether && level.getMinY()!=UnstableNether.MIN_Y)throw new IllegalStateException("The embedded extended Nether did not load for "+world.getName()+". Create a fresh world with this plugin installed before first startup.");
        try{
            var old=map.worldGenContext;
            var generator=new PaperGenerator(level,vanilla,nether);
            var replacement=new WorldGenContext(old.level(),generator,old.structureManager(),old.lightEngine(),old.mainThreadExecutor(),old.unsavedListener());
            Field field=map.getClass().getDeclaredField("worldGenContext");field.setAccessible(true);field.set(map,replacement);
            getLogger().info("Installed matching generator in "+world.getName()+" (Y="+level.getMinY()+".."+level.getMaxY()+")");
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Paper 1.21.11 generation adapter is incompatible",error);}
    }
    boolean managed(World world){return managed.contains(world.getUID());}
    boolean farlands(Player player){return managed(player.getWorld()) && player.getWorld().getEnvironment()==World.Environment.NORMAL && FarlandsWorld.contains(player.getLocation().getX(),player.getLocation().getZ());}
    boolean underworld(Player player){return managed(player.getWorld()) && player.getWorld().getEnvironment()==World.Environment.NETHER && player.getLocation().getY()<UnstableNether.UNDERWORLD_TOP;}
    boolean protectedPlayer(Player player){return GameplaySettings.Rule.UNDERWORLD_ADVENTURE.enabled() && underworld(player) && (player.getGameMode()==GameMode.SURVIVAL || player.getGameMode()==GameMode.ADVENTURE);}
    boolean disabledBoost(Player player){return !GameplaySettings.Rule.FARLANDS_FIREWORKS.enabled() && farlands(player) && FarlandsWorld.depth(player.getLocation().getX(),player.getLocation().getZ())>=GreatSea.FARLANDS.fireworkDisableDepth;}
    void updatePlayer(Player player){
        underworldCoordinates.update(player,underworld(player));
        SpokelandsDarkness.update(((org.bukkit.craftbukkit.entity.CraftPlayer)player).getHandle());
        PaperMist.update(player);
        var data=player.getPersistentDataContainer();boolean owned=data.has(autoAdventure,PersistentDataType.BYTE);
        boolean adventureRequired=GameplaySettings.Rule.UNDERWORLD_ADVENTURE.enabled() && underworld(player);
        if(adventureRequired && player.getGameMode()==GameMode.SURVIVAL){
            automaticModeChanges.add(player.getUniqueId());
            try{player.setGameMode(GameMode.ADVENTURE);if(player.getGameMode()==GameMode.ADVENTURE)data.set(autoAdventure,PersistentDataType.BYTE,(byte)1);}
            finally{automaticModeChanges.remove(player.getUniqueId());}
        }else if(owned && player.getGameMode()==GameMode.ADVENTURE && !adventureRequired){
            automaticModeChanges.add(player.getUniqueId());
            try{player.setGameMode(GameMode.SURVIVAL);if(player.getGameMode()==GameMode.SURVIVAL)data.remove(autoAdventure);}
            finally{automaticModeChanges.remove(player.getUniqueId());}
        }else if(owned && player.getGameMode()!=GameMode.ADVENTURE)data.remove(autoAdventure);
        boolean active=data.has(chestActive,PersistentDataType.BYTE),target=farlands(player);
        if(active!=target){
            player.closeInventory();saveActiveChest(player);
            byte[] saved=data.get(target?farlandsChest:normalChest,PersistentDataType.BYTE_ARRAY);
            player.getEnderChest().setStorageContents(saved==null?new ItemStack[27]:ItemStack.deserializeItemsFromBytes(saved));
            if(target)data.set(chestActive,PersistentDataType.BYTE,(byte)1);else data.remove(chestActive);
        }
    }
    private void saveActiveChest(Player player){
        var data=player.getPersistentDataContainer();boolean active=data.has(chestActive,PersistentDataType.BYTE);
        data.set(active?farlandsChest:normalChest,PersistentDataType.BYTE_ARRAY,ItemStack.serializeItemsAsBytes(player.getEnderChest().getStorageContents()));
    }
    @EventHandler public void join(PlayerJoinEvent event){updatePlayer(event.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent event){PaperMist.clear(event.getPlayer());underworldCoordinates.forget(event.getPlayer().getUniqueId());saveActiveChest(event.getPlayer());}
    @EventHandler public void changedWorld(PlayerChangedWorldEvent event){updatePlayer(event.getPlayer());}
    @EventHandler public void respawn(PlayerRespawnEvent event){Bukkit.getScheduler().runTask(this,()->updatePlayer(event.getPlayer()));}
    @EventHandler(priority=EventPriority.LOWEST) public void openInventory(InventoryOpenEvent event){if(event.getPlayer() instanceof Player player)updatePlayer(player);}
    @EventHandler public void closeInventory(InventoryCloseEvent event){if(event.getPlayer() instanceof Player player)saveActiveChest(player);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void changedMode(PlayerGameModeChangeEvent event){if(!automaticModeChanges.contains(event.getPlayer().getUniqueId()))event.getPlayer().getPersistentDataContainer().remove(autoAdventure);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void spawn(CreatureSpawnEvent event){
        if(!GameplaySettings.Rule.MOB_SPAWNING.enabled() && managed(event.getLocation().getWorld())
                && !OrbitalRuntime.controlMob(event.getEntity().getScoreboardTags())
                && ((org.bukkit.craftbukkit.entity.CraftEntity)event.getEntity()).getHandle() instanceof net.minecraft.world.entity.Mob)event.setCancelled(true);
    }
    private void removeLoadedMob(org.bukkit.entity.Entity entity){
        if(GameplaySettings.Rule.MOB_SPAWNING.enabled() || !managed(entity.getWorld()) || OrbitalRuntime.controlMob(entity.getScoreboardTags())
                || !(((org.bukkit.craftbukkit.entity.CraftEntity)entity).getHandle() instanceof net.minecraft.world.entity.Mob))return;
        // Paper forbids removal while its entity sections are changing tracking status.
        Bukkit.getScheduler().runTask(this,()->{if(!GameplaySettings.Rule.MOB_SPAWNING.enabled())entity.remove();});
    }
    @EventHandler public void loadedEntities(EntitiesLoadEvent event){for(var entity:event.getEntities())removeLoadedMob(entity);}
    @EventHandler public void addedEntity(com.destroystokyo.paper.event.entity.EntityAddToWorldEvent event){removeLoadedMob(event.getEntity());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breakBlock(BlockBreakEvent event){if(protectedPlayer(event.getPlayer()))event.setCancelled(true);}
    private static void orbitalBroken(Block block){
        OrbitalRuntime.blockBroken(((CraftWorld)block.getWorld()).getHandle(),new BlockPos(block.getX(),block.getY(),block.getZ()));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void orbitalBreak(BlockBreakEvent event){orbitalBroken(event.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void orbitalBurn(BlockBurnEvent event){orbitalBroken(event.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void orbitalDestroy(com.destroystokyo.paper.event.block.BlockDestroyEvent event){orbitalBroken(event.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void orbitalBlockBreak(io.papermc.paper.event.block.BlockBreakBlockEvent event){orbitalBroken(event.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void orbitalBlockBlast(BlockExplodeEvent event){event.blockList().forEach(PaperPlugin::orbitalBroken);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void orbitalEntityBlast(EntityExplodeEvent event){event.blockList().forEach(PaperPlugin::orbitalBroken);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void orbitalEntityBreak(EntityChangeBlockEvent event){if(event.getTo().isAir())orbitalBroken(event.getBlock());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void placeBlock(BlockPlaceEvent event){
        if(protectedPlayer(event.getPlayer()))event.setCancelled(true);
        if(managed(event.getBlock().getWorld()))removeVault(event.getBlock());
        if(!event.isCancelled()){var b=event.getBlock();MistShelterData.placed(((CraftWorld)b.getWorld()).getHandle(),new BlockPos(b.getX(),b.getY(),b.getZ()));}
    }
    private static boolean modifies(ItemStack stack){
        if(stack==null)return false;var item=CraftItemStack.asNMSCopy(stack).getItem();
        return item instanceof net.minecraft.world.item.BlockItem || item instanceof net.minecraft.world.item.BucketItem || item instanceof net.minecraft.world.item.BoneMealItem || item instanceof net.minecraft.world.item.FlintAndSteelItem || item instanceof net.minecraft.world.item.FireChargeItem || item instanceof net.minecraft.world.item.AxeItem || item instanceof net.minecraft.world.item.HoeItem || item instanceof net.minecraft.world.item.ShovelItem || item instanceof net.minecraft.world.item.HoneycombItem || item instanceof net.minecraft.world.item.ShearsItem || item instanceof net.minecraft.world.item.BrushItem;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interact(PlayerInteractEvent event){
        if(event.getAction()==Action.RIGHT_CLICK_BLOCK&&event.getClickedBlock()!=null&&event.getHand()!=null){
            var block=event.getClickedBlock();
            OrbitalRuntime.useBlock(nativePlayer(event.getPlayer()),nativeHand(event.getHand()),new BlockPos(block.getX(),block.getY(),block.getZ()));
        }
        if(protectedPlayer(event.getPlayer())){
            boolean changes=modifies(event.getItem());
            if(changes)event.setUseItemInHand(Event.Result.DENY);
            if(event.getClickedBlock()!=null && event.getItem()!=null){
                var block=((org.bukkit.craftbukkit.block.CraftBlock)event.getClickedBlock()).getNMS().getBlock();
                boolean activation=block instanceof net.minecraft.world.level.block.AbstractCauldronBlock || changes &&
                    (block instanceof net.minecraft.world.level.block.PumpkinBlock || block instanceof net.minecraft.world.level.block.AbstractCandleBlock || block instanceof net.minecraft.world.level.block.CakeBlock || block instanceof net.minecraft.world.level.block.BeehiveBlock || block instanceof net.minecraft.world.level.block.FlowerPotBlock || block instanceof net.minecraft.world.level.block.RespawnAnchorBlock || block instanceof net.minecraft.world.level.block.ComposterBlock);
                if(activation)event.setUseInteractedBlock(Event.Result.DENY);
            }
        }
        if(disabledBoost(event.getPlayer()) && event.getPlayer().isGliding() && event.getItem()!=null && event.getItem().getType()==Material.FIREWORK_ROCKET)event.setUseItemInHand(Event.Result.DENY);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void boost(com.destroystokyo.paper.event.player.PlayerElytraBoostEvent event){
        if(!managed(event.getPlayer().getWorld()))return;
        event.setCancelled(true);
        if(disabledBoost(event.getPlayer()))return;
        var original=((org.bukkit.craftbukkit.entity.CraftFirework)event.getFirework()).getHandle();
        var rocket=RocketHooks.copy(original);
        var world=((CraftWorld)event.getPlayer().getWorld()).getHandle();
        if(world.addFreshEntity(rocket) && event.shouldConsume() && event.getPlayer().getGameMode()!=GameMode.CREATIVE){
            ItemStack item=event.getItemStack().clone();item.subtract(1);event.getPlayer().getInventory().setItem(event.getHand(),item);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void emptyBucket(PlayerBucketEmptyEvent event){if(protectedPlayer(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fillBucket(PlayerBucketFillEvent event){if(protectedPlayer(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void portal(PlayerPortalEvent event){
        if(event.getCause()!=PlayerTeleportEvent.TeleportCause.NETHER_PORTAL)return;
        if(blockedPortal(event.getFrom())||blockedPortal(event.getTo()))event.setCancelled(true);
    }
    private static boolean blockedPortal(Location location){
        return location!=null&&location.getWorld() instanceof CraftWorld world&&OrbitalRuntime.blocksPortal(world.getHandle(),location.getBlockX(),location.getBlockZ());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void orbitalEntityPortal(EntityPortalEvent event){
        var to=event.getTo();
        if((event.getFrom().getWorld().getEnvironment()==World.Environment.NETHER||to!=null&&to.getWorld().getEnvironment()==World.Environment.NETHER)
                &&(blockedPortal(event.getFrom())||blockedPortal(to)))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void orbitalPearl(PlayerTeleportEvent event){
        if(event.getCause()==PlayerTeleportEvent.TeleportCause.ENDER_PEARL)OrbitalRuntime.beforePull(nativePlayer(event.getPlayer()));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void orbitalCast(PlayerFishEvent event){
        if(event.getState()==PlayerFishEvent.State.FISHING&&event.getHand()!=null
                &&OrbitalRuntime.rod(nativePlayer(event.getPlayer()),nativeHand(event.getHand()),true))event.setCancelled(true);
        switch(event.getState()){
            case REEL_IN, IN_GROUND, CAUGHT_ENTITY, CAUGHT_FISH -> OrbitalRuntime.cancelCast(nativePlayer(event.getPlayer()));
            default -> { }
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void ignite(PortalCreateEvent event){
        if(event.getBlocks().stream().anyMatch(block->OrbitalRuntime.blocksPortal(((CraftWorld)event.getWorld()).getHandle(),block.getX(),block.getZ())))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fall(EntityDamageEvent event){
        if(event.getEntity() instanceof Player player && underworld(player) && event.getCause()==EntityDamageEvent.DamageCause.FALL && UnderworldEntry.protectsLanding(player.getY(),player.getFallDistance())){event.setCancelled(true);player.setFallDistance(0);}
    }
    @EventHandler public void loadChunk(ChunkLoadEvent event){
        if(!managed(event.getWorld()))return;
        var chunk=((CraftWorld)event.getWorld()).getHandle().getChunk(event.getChunk().getX(),event.getChunk().getZ());
        if(event.isNewChunk())PaperGenerator.clean(chunk,event.getWorld().getEnvironment()==World.Environment.NORMAL);
        for(var tile:event.getChunk().getTileEntities())removeVault(tile.getBlock());
    }
    private boolean stableRainbow(Block block){
        if(!managed(block.getWorld())||block.getWorld().getEnvironment()!=World.Environment.NORMAL)return false;
        var world=((CraftWorld)block.getWorld()).getHandle();
        var pos=new net.minecraft.core.BlockPos(block.getX(),block.getY(),block.getZ());
        return SpokelandsDecoration.stableRainbow(world.getSeed(),pos,world.getBlockState(pos));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void physics(BlockPhysicsEvent event){if(stableRainbow(event.getBlock()))event.setCancelled(true);else if(managed(event.getBlock().getWorld()))removeVault(event.getBlock());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void rainbowDecay(org.bukkit.event.block.LeavesDecayEvent event){if(stableRainbow(event.getBlock()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void rainbowFall(org.bukkit.event.entity.EntityChangeBlockEvent event){if(event.getEntity() instanceof org.bukkit.entity.FallingBlock&&stableRainbow(event.getBlock()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void rainbowGrow(org.bukkit.event.block.BlockGrowEvent event){if(stableRainbow(event.getBlock()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void freezing(BlockFormEvent event){
        if(event instanceof EntityBlockFormEvent)return;
        if(event.getBlock().getWorld().getEnvironment()==World.Environment.NORMAL&&MistRegion.contains(event.getBlock().getX(),event.getBlock().getZ()))return;
        var type=event.getNewState().getType();
        if(managed(event.getBlock().getWorld())&&(type==Material.ICE||type==Material.PACKED_ICE||type==Material.BLUE_ICE||type==Material.FROSTED_ICE))event.setCancelled(true);
    }
    private static void removeVault(Block block){if(block.getBlockData() instanceof org.bukkit.block.data.type.Vault vault && vault.isOminous() && !(block.getWorld().getEnvironment()==World.Environment.NORMAL && ForbiddenWorldBlocks.allowsOminous(block.getX(),block.getZ())))block.setType(Material.AIR,false);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void mistMelt(BlockFadeEvent event){
        var b=event.getBlock();if(managed(b.getWorld())&&b.getType()==Material.ICE&&b.getWorld().getEnvironment()==World.Environment.NORMAL&&MistRegion.frost(b.getWorld().getSeed(),b.getX(),b.getZ()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void mistWater(PlayerBucketEmptyEvent event){
        var b=event.getBlockClicked();if(!managed(b.getWorld())||b.getWorld().getEnvironment()!=World.Environment.NORMAL||!MistRegion.contains(b.getX(),b.getZ()))return;
        Bukkit.getScheduler().runTask(this,()->{var level=((CraftWorld)b.getWorld()).getHandle();for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){
            var pos=new BlockPos(b.getX()+dx,b.getY()+dy,b.getZ()+dz);if(!MistRegion.frost(b.getWorld().getSeed(),pos.getX(),pos.getZ()))continue;
            var old=level.getBlockState(pos);var state=MistTerrain.frozen(old);if(state!=old)level.setBlock(pos,state,3);
        }});
    }

}



