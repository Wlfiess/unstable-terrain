package dev.greatsea;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.Blocks;
import dev.greatsea.mixin.UnderworldInteractionManagerAccessor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.PalettesFactory;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.dimension.PortalForcer;
import net.minecraft.world.gen.HeightContext;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ErrorReporter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.EnumSet;

/** Short opt-in checks of generated Underworld chunks; never runs during ordinary play. */
final class UnderworldVerifier {
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
    private static boolean crystal(net.minecraft.block.BlockState block) {
        return block.isOf(Blocks.GREEN_STAINED_GLASS) || block.isOf(Blocks.LIME_STAINED_GLASS) || block.isOf(Blocks.VERDANT_FROGLIGHT);
    }
    static void run(MinecraftServer server) {
        var report=new ArrayList<String>();long started=System.nanoTime();
        try {
            var world=server.getWorld(World.NETHER);
            check(world!=null && UnstableNether.supported(world),"Requires a fresh Unstable world with the Underworld Nether settings");
            check(world.getBottomY()==-320 && world.getTopYInclusive()==255,"Wrong Nether build limits");
            WorldPresetVerification.verify(server,report);
            var generator=world.getChunkManager().getChunkGenerator();
            var noise=world.getChunkManager().getNoiseConfig();long seed=world.getSeed();
            var original=new NoiseChunkGenerator(((UnderworldBiomeSource)generator.getBiomeSource()).netherSource(),
                    server.getRegistryManager().getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS).getOrThrow(ChunkGeneratorSettings.NETHER));
            BlockPos.Mutable pos=new BlockPos.Mutable();
            int noiseStates=0,bedrock=0,bedrockPattern=0;
            for (int[] point:new int[][]{{0,0},{37,53},{-37,-53},{1024,-2048},{100000,51},{-100000,51},{51,100000},{51,-100000}}) {
                int x=point[0],z=point[1];var chunk=world.getChunk(x>>4,z>>4);
                var column=UnderworldTerrain.column(seed,x,z);int ground=column.top();
                check(world.getBlockState(pos.set(x,ground,z)).isSolid(),"Missing Underworld surface at "+x+","+z);
                for (int y=column.bottom();y<=ground-5;y++)
                    check(world.getBlockState(pos.set(x,y,z)).equals(UnderworldTerrain.state(seed,x,y,z)),"Shelf material differs at "+pos);
                check(world.getBlockState(pos.set(x,column.bottom()-1,z)).isAir(),"Underworld underside is not open void at "+pos);
                for (int y=-127;y<0;y++) check(world.getBlockState(pos.set(x,y,z)).isAir(),"Nether-to-Underworld gap is obstructed at "+pos);
                var queried=generator.getColumnSample(x,z,world,noise);var nativeColumn=original.getColumnSample(x,z,world,noise);
                check(queried.getState(ground-5).equals(UnderworldTerrain.state(seed,x,ground-5,z)),"Underworld column query differs");
                for (int y=0;y<128;y++) {
                    check(queried.getState(y).equals(nativeColumn.getState(y)),"Native Nether noise changed at "+x+","+y+","+z);noiseStates++;
                }
                for (int y:new int[]{0,127}) {
                    check(world.getBlockState(pos.set(x,y,z)).isOf(Blocks.BEDROCK),"Original Nether bedrock boundary moved at "+pos);bedrock++;
                }
                var control=new ProtoChunk(chunk.getPos(),UpgradeData.NO_UPGRADE_DATA,HeightLimitView.create(0,256),
                        PalettesFactory.fromRegistryManager(server.getRegistryManager()),null);
                control.populateBiomes(original.getBiomeSource(),noise.getMultiNoiseSampler());
                var nativeNoise=original.populateNoise(Blender.getNoBlending(),noise,world.getStructureAccessor(),control);
                long deadline=System.nanoTime()+15_000_000_000L;
                // The worker asks the main server thread for structure data. Pump those
                // tasks while awaiting this standalone control, instead of blocking it.
                server.runTasks(()->nativeNoise.isDone() || System.nanoTime()>deadline);
                check(nativeNoise.isDone(),"Timed out generating standalone native bedrock control");
                nativeNoise.join();
                Heightmap.populateHeightmaps(control,EnumSet.of(Heightmap.Type.OCEAN_FLOOR_WG,Heightmap.Type.WORLD_SURFACE_WG));
                original.buildSurface(control,new HeightContext(original,control),noise,world.getStructureAccessor(),
                        world.getBiomeAccess(),server.getRegistryManager().getOrThrow(RegistryKeys.BIOME),Blender.getNoBlending());
                for (int dz=0;dz<16;dz++) for (int dx=0;dx<16;dx++) for (int y:new int[]{0,1,2,3,4,123,124,125,126,127}) {
                    pos.set(chunk.getPos().getStartX()+dx,y,chunk.getPos().getStartZ()+dz);
                    check(chunk.getBlockState(pos).isOf(Blocks.BEDROCK)==control.getBlockState(pos).isOf(Blocks.BEDROCK),
                            "Native bedrock pattern changed at "+pos);bedrockPattern++;
                }
                int bx=x>>2,bz=z>>2;
                check(chunk.getBiomeForNoiseGen(bx,-51,bz).getKey().orElseThrow().getValue().equals(Identifier.of("greatsea","underworld")),
                        "Stored Underworld biome missing at "+x+","+z);
                check(generator.getBiomeSource().getBiome(bx,-51,bz,noise.getMultiNoiseSampler()).getKey().orElseThrow().getValue().equals(Identifier.of("greatsea","underworld")),
                        "Queried Underworld biome missing at "+x+","+z);
                check(generator.getBiomeSource().getBiome(bx,16,bz,noise.getMultiNoiseSampler()).equals(
                        original.getBiomeSource().getBiome(bx,16,bz,noise.getMultiNoiseSampler())),"Native Nether biome changed above bedrock");
                report.add("Generated column "+x+","+z+": floor="+ground+", underside="+column.bottom()+", empty gap, original bedrock Y=0/127");
            }
            report.add("Native Nether controls: "+noiseStates+" queried block states Y=0..127 unchanged; "+bedrock+" actual boundary blocks intact; "+
                    bedrockPattern+" bottom/roof band positions match separately generated vanilla bedrock patterns; stored and queried lower biomes agree.");
            GreatSea.LOGGER.info("UNDERWORLD_VERIFY_COLUMNS_PASS");
            var models=JsonParser.parseReader(new InputStreamReader(UnderworldVerifier.class.getResourceAsStream(
                    "/assets/greatsea/underworld/crystals.json"))).getAsJsonArray();
            var seen=new HashSet<Integer>();UnderworldTerrain.CrystalCandidate preview=null;int voxels=0;
            for (int gz=-8;gz<=8 && seen.size()<models.size();gz++) for (int gx=-8;gx<=8 && seen.size()<models.size();gx++) {
                var candidate=UnderworldTerrain.crystalCandidate(seed,gx,gz);
                if (SeaTerrain.unit(candidate.hash())>UnderworldTerrain.CRYSTAL_CHANCE || !seen.add(candidate.variant())) continue;
                if (preview==null) preview=candidate;
                var model=models.get(candidate.variant()).getAsJsonObject();var palette=model.getAsJsonArray("palette");
                int base=UnderworldTerrain.column(seed,candidate.x(),candidate.z()).top()-2;
                for (var element:model.getAsJsonArray("blocks")) {
                    var block=element.getAsJsonArray();int dx=block.get(0).getAsInt(),dz=block.get(2).getAsInt();
                    for (int r=0;r<candidate.rotation();r++) { int old=dx;dx=-dz;dz=old; }
                    int x=candidate.x()+dx,y=base+block.get(1).getAsInt(),z=candidate.z()+dz;
                    if (y>=UnderworldTerrain.TOP_Y) continue;
                    world.getChunk(x>>4,z>>4);
                    String id=palette.get(block.get(3).getAsInt()).getAsJsonObject().get("Name").getAsString();
                    check(Registries.BLOCK.getId(world.getBlockState(pos.set(x,y,z)).getBlock()).toString().equals(id),
                            "Crystal "+candidate.variant()+" differs across chunk boundary at "+pos+"; wanted "+id);voxels++;
                }
                report.add("Crystal variant "+candidate.variant()+": exact model verified at "+candidate.x()+","+base+","+candidate.z());
            }
            check(seen.size()==models.size(),"Not all six crystal variants generated");
            report.add("Natural crystal controls: all "+seen.size()+" extracted variants and "+voxels+" actual blocks match, including chunk boundaries.");
            GreatSea.LOGGER.info("UNDERWORLD_VERIFY_CRYSTALS_PASS");
            check(preview!=null,"No preview formation found");
            writeMap(world,preview.x(),preview.z(),report);
            GreatSea.LOGGER.info("UNDERWORLD_VERIFY_MAP_PASS");
            var player=new ServerPlayerEntity(server,world,new GameProfile(UUID.fromString("53a3b9fe-4804-47dc-a2b0-60c80d802343"),"UnderworldTest"),SyncedClientOptions.createDefault());
            player.setPosition(0,-205,0);player.setHealth(20);player.fallDistance=206;
            boolean damaged=player.handleFallDamage(206,1,world.getDamageSources().fall());
            check(!damaged && player.getHealth()==20 && player.fallDistance==0,"Nether-floor entry drop was not protected by the actual player mixin");
            player.fallDistance=10;
            player.handleFallDamage(1,1,world.getDamageSources().fall());
            check(player.fallDistance==10,"Ordinary Underworld fall was incorrectly waived");
            report.add("Player entry: actual server-player fall handler protects a 206-block Nether-floor drop; ordinary Underworld fall leaves normal handling active.");
            verifyBuilding(world,player,report);
            verifyAdventure(server,world,report);
            var portal=new PortalForcer(world).createPortal(new BlockPos(48,80,48),Direction.Axis.X);
            check(portal.isPresent() && portal.orElseThrow().lowerLeft.getY()>=0 && portal.orElseThrow().lowerLeft.getY()+portal.orElseThrow().height<=128,
                    "Native Nether portal creation moved into the Underworld");
            report.add("Native portal creation: portal created at "+portal.orElseThrow().lowerLeft+", within original Nether Y=0..127.");
            report.add("PASS: Underworld generation, six crystal models, native Nether controls, world codec, entry and portal checks; seconds="+
                    String.format(java.util.Locale.ROOT,"%.2f",(System.nanoTime()-started)/1e9));
            GreatSea.LOGGER.info("UNDERWORLD_VERIFICATION_PASS {}",report);
        } catch (Throwable error) {
            report.add("FAIL: "+error);GreatSea.LOGGER.error("UNDERWORLD_VERIFICATION_FAIL",error);
        } finally {
            try { Files.write(Path.of("underworld-verification.txt"),report); }
            catch (Exception error) { GreatSea.LOGGER.error("Cannot save Underworld verification",error); }
            server.stop(false);
        }
    }
    private static void verifyBuilding(ServerWorld world,ServerPlayerEntity player,ArrayList<String> report) {
        var manager=(UnderworldInteractionManagerAccessor)player.interactionManager;
        var previousMode=player.getGameMode();
        var target=new BlockPos(4,-205,4);
        var above=target.up();
        var previousTarget=world.getBlockState(target);
        var previousAbove=world.getBlockState(above);
        try {
            manager.greatsea$setVerificationGameMode(GameMode.SURVIVAL);
            player.setPosition(4.5,-204,4.5);
            world.setBlockState(target,Blocks.STONE.getDefaultState());
            world.setBlockState(above,Blocks.AIR.getDefaultState());
            var hit=new BlockHitResult(Vec3d.ofCenter(target),Direction.UP,target,false);
            var stone=new ItemStack(Items.STONE);
            player.setStackInHand(Hand.MAIN_HAND,stone);
            var placement=new ItemPlacementContext(player,Hand.MAIN_HAND,stone,hit);
            check(!player.canModifyBlocks() && !player.canPlaceOn(target,Direction.UP,stone),"Survival building abilities were not restricted in the Underworld");
            check(!player.interactionManager.tryBreakBlock(target) && world.getBlockState(target).isOf(Blocks.STONE),"Survival player broke an Underworld block");
            check(((BlockItem)Items.STONE).place(placement)==ActionResult.FAIL && world.getBlockState(above).isAir(),"Survival player placed an Underworld block");
            check(stone.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit))==ActionResult.FAIL,"Survival item-placement branch was not rejected");
            for (var item:new net.minecraft.item.Item[]{Items.BONE_MEAL,Items.FLINT_AND_STEEL,Items.DIAMOND_SHOVEL,Items.HONEYCOMB}) {
                var stack=new ItemStack(item);player.setStackInHand(Hand.MAIN_HAND,stack);
                check(stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit))==ActionResult.FAIL,"Underworld block-changing item was allowed: "+item);
            }
            var bucket=new ItemStack(Items.WATER_BUCKET);player.setStackInHand(Hand.MAIN_HAND,bucket);
            check(bucket.use(world,player,Hand.MAIN_HAND)==ActionResult.FAIL,"Underworld bucket use was allowed");
            var shears=new ItemStack(Items.SHEARS);
            check(Blocks.PUMPKIN.getDefaultState().onUseWithItem(shears,world,player,Hand.MAIN_HAND,hit)==ActionResult.FAIL,"Native pumpkin-carving activation bypassed Underworld protection");
            check(!UnderworldProtection.changesBlockActivation(stone,Blocks.CHEST) && !UnderworldProtection.changesBlockActivation(stone,Blocks.STONE_BUTTON),"Holding a block incorrectly restricted container/button activation");
            check(!UnderworldProtection.changesBlocks(Items.APPLE) && !UnderworldProtection.changesBlocks(Items.DIAMOND_SWORD),"Food/combat items were classified as building tools");
            player.setPosition(4.5,64,4.5);
            check(!UnderworldProtection.protectedPlayer(player) && player.canModifyBlocks() && player.canPlaceOn(target,Direction.UP,stone),"Survival building remained restricted after leaving the Underworld");
            player.setPosition(4.5,-204,4.5);
            manager.greatsea$setVerificationGameMode(GameMode.CREATIVE);
            check(!UnderworldProtection.protectedPlayer(player) && player.canModifyBlocks(),"Creative player was restricted in the Underworld");
            manager.greatsea$setVerificationGameMode(GameMode.SPECTATOR);
            check(!UnderworldProtection.protectedPlayer(player),"Spectator player was restricted in the Underworld");
            report.add("Building protection: actual block breaking, direct placement, item placement, bucket and modifying-item calls rejected; food/combat allowed; leaving, Creative and Spectator controls pass.");
        } finally {
            manager.greatsea$setVerificationGameMode(previousMode);
            world.setBlockState(target,previousTarget);world.setBlockState(above,previousAbove);
            player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
        }
    }
    private static class OfflineModePlayer extends ServerPlayerEntity {
        OfflineModePlayer(MinecraftServer server,ServerWorld world){
            super(server,world,new GameProfile(UUID.randomUUID(),"UnderworldModeTest"),SyncedClientOptions.createDefault());
        }
        @Override public void sendAbilitiesUpdate(){}
        @Override public boolean changeGameMode(GameMode mode){
            if(getGameMode()==mode)return false;
            ((UnderworldInteractionManagerAccessor)interactionManager).greatsea$setVerificationGameMode(mode);
            mode.setAbilities(getAbilities());return true;
        }
    }
    private static void verifyAdventure(MinecraftServer server,ServerWorld world,ArrayList<String> report){
        var player=new OfflineModePlayer(server,world);
        player.changeGameMode(GameMode.SURVIVAL);
        player.setPosition(0,-128,0);UnderworldGameMode.update(player);
        check(player.getGameMode()==GameMode.SURVIVAL,"Adventure switch occurred above the Underworld");
        player.setPosition(0,-129,0);UnderworldGameMode.update(player);
        check(player.getGameMode()==GameMode.ADVENTURE && ((UnderworldGameModeData)player).greatsea$underworldAdventure(),"Survival did not switch to owned Adventure below Y=-128");
        check(UnderworldProtection.protectedPlayer(player) && !player.canModifyBlocks(),"Adventure building restriction missing");
        var save=NbtWriteView.create(ErrorReporter.EMPTY,server.getRegistryManager());player.writeData(save);
        var loaded=new OfflineModePlayer(server,world);
        loaded.readData(NbtReadView.create(ErrorReporter.EMPTY,server.getRegistryManager(),save.getNbt()));
        check(((UnderworldGameModeData)loaded).greatsea$underworldAdventure(),"Automatic Adventure ownership was lost after player NBT reload");
        loaded.changeGameMode(GameMode.ADVENTURE);loaded.setPosition(0,-129,0);UnderworldGameMode.update(loaded);
        check(loaded.getGameMode()==GameMode.ADVENTURE,"Reloaded player lost Adventure inside the Underworld");
        var respawn=new OfflineModePlayer(server,world);respawn.copyFrom(loaded,false);
        check(((UnderworldGameModeData)respawn).greatsea$underworldAdventure(),"Automatic mode ownership was lost during death copy");
        respawn.changeGameMode(GameMode.ADVENTURE);respawn.setPosition(0,64,0);UnderworldGameMode.update(respawn);
        check(respawn.getGameMode()==GameMode.SURVIVAL && !((UnderworldGameModeData)respawn).greatsea$underworldAdventure(),"Survival was not restored after respawn outside the Underworld");
        loaded.setPosition(0,-128,0);UnderworldGameMode.update(loaded);
        check(loaded.getGameMode()==GameMode.SURVIVAL,"Survival was not restored at the Underworld boundary");
        for(var mode:new GameMode[]{GameMode.ADVENTURE,GameMode.CREATIVE,GameMode.SPECTATOR}){
            var control=new OfflineModePlayer(server,world);control.changeGameMode(mode);
            control.setPosition(0,-205,0);UnderworldGameMode.update(control);
            control.setPosition(0,64,0);UnderworldGameMode.update(control);
            check(control.getGameMode()==mode && !((UnderworldGameModeData)control).greatsea$underworldAdventure(),"Preexisting game mode changed: "+mode);
        }
        player.changeGameMode(GameMode.CREATIVE);UnderworldGameMode.update(player);
        player.setPosition(0,64,0);UnderworldGameMode.update(player);
        check(player.getGameMode()==GameMode.CREATIVE && !((UnderworldGameModeData)player).greatsea$underworldAdventure(),"Explicit Creative change was overwritten on exit");
        var normal=new OfflineModePlayer(server,server.getOverworld());normal.changeGameMode(GameMode.SURVIVAL);
        normal.setPosition(0,-205,0);UnderworldGameMode.update(normal);
        check(normal.getGameMode()==GameMode.SURVIVAL,"Non-Nether control switched mode");
        report.add("Underworld Adventure: Survival switches below Y=-128 and returns on exit; automatic ownership survives actual player NBT reload and death copy; preexisting Adventure, Creative, Spectator and other-world controls pass. Mode packet transport is bypassed for these offline test players.");
    }
    private static void writeMap(ServerWorld world,int centerX,int centerZ,ArrayList<String> report) throws Exception {
        var csv=new ArrayList<String>();csv.add("x,z,ground_y,material,top_y,top_block");
        BlockPos.Mutable pos=new BlockPos.Mutable();int min=0,max=-320,crystals=0;
        for (int z=centerZ-64;z<centerZ+64;z+=8) for (int x=centerX-64;x<centerX+64;x+=8) {
            world.getChunk(x>>4,z>>4);var column=UnderworldTerrain.column(world.getSeed(),x,z);
            int top=column.top();
            for (int y=-129;y>top;y--) if (!world.getBlockState(pos.set(x,y,z)).isAir()) { top=y;break; }
            var state=world.getBlockState(pos.set(x,top,z));if (crystal(state)) crystals++;
            min=Math.min(min,column.top());max=Math.max(max,column.top());
            csv.add(x+","+z+","+column.top()+","+Registries.BLOCK.getId(world.getBlockState(new BlockPos(x,column.top()-5,z)).getBlock())+
                    ","+top+","+Registries.BLOCK.getId(state.getBlock()));
        }
        Files.write(Path.of("underworld-map.csv"),csv);
        report.add("Actual 128x128-block map preview: "+(csv.size()-1)+" eight-block samples; floor="+min+".."+max+"; crystal-top samples="+crystals+"; center="+centerX+","+centerZ+".");
    }
}
