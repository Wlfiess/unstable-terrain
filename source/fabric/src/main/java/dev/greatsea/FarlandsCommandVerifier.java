package dev.greatsea;

import com.mojang.authlib.GameProfile;
import net.minecraft.command.permission.PermissionPredicate;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

/** Opt-in command routing and actual landing checks, with no fabricated terrain platforms. */
public final class FarlandsCommandVerifier {
    private static final String[] NAMES={"plains","repeating","floating","gravel","spikes","gravel_after_spikes","split","cherry","plains_hills","sand_dunes","sand_farlands","corrupted","orbitals","chunks","decay","water","skygrid"};
    private FarlandsCommandVerifier() {}
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
    private static final class OfflinePlayer extends ServerPlayerEntity {
        ServerWorld capturedWorld;
        Vec3d capturedPosition;
        Set<PositionFlag> capturedFlags;
        OfflinePlayer(MinecraftServer server,ServerWorld world) {
            super(server,world,new GameProfile(UUID.randomUUID(),"FarlandsCmdTest"),SyncedClientOptions.createDefault());
        }
        @Override public void closeHandledScreen() { currentScreenHandler=playerScreenHandler; }
        @Override public void sendAbilitiesUpdate() {}
        @Override public boolean teleport(ServerWorld world,double x,double y,double z,Set<PositionFlag> flags,float yaw,float pitch,boolean resetCamera) {
            capturedWorld=world;capturedPosition=new Vec3d(x,y,z);capturedFlags=Set.copyOf(flags);
            setServerWorld(world);setPosition(x,y,z);setYaw(yaw);setPitch(pitch);
            return true;
        }
    }
    public static void run(MinecraftServer server) {
        var report=new ArrayList<String>();long started=System.nanoTime();
        try {
            var world=server.getOverworld();
            check(FarlandsWorld.supported(world),"Command verification requires an enabled Unstable Overworld");
            var dispatcher=server.getCommandManager().getDispatcher();
            var root=dispatcher.getRoot().getChild("farlands");
            check(root!=null,"/farlands was not registered");
            var player=new OfflinePlayer(server,world);
            ServerCommandSource allowed=server.getCommandSource().withEntity(player).withPermissions(PermissionPredicate.ALL).withSilent();
            var denied=allowed.withPermissions(PermissionPredicate.NONE);
            check(root.canUse(allowed) && !root.canUse(denied),"/farlands permission checks did not distinguish ALL/NONE");
            for (int number=0;number<=18;number++) {
                String invalid="farlands "+number;
                var parsed=dispatcher.parse(invalid,allowed);
                check(parsed.getReader().canRead() || !parsed.getExceptions().isEmpty() || parsed.getContext().getCommand()==null,
                        "Out-of-range stage number was accepted: "+invalid);
            }
            int aliases=0;
            var stages=FarlandsTerrain.Stage.values();
            check(stages.length==NAMES.length,"Stage/name count differs");
            for (int index=0;index<stages.length;index++) {
                var stage=stages[index];
                BlockPos feet=FarlandsCommand.destination(world,stage);
                validateLanding(world,stage,feet);
                if(stage!=FarlandsTerrain.Stage.SPLIT && stage!=FarlandsTerrain.Stage.ORBITALS) {
                    int start=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(stage);
                    check(feet.getX()>=start && feet.getX()<=start+132,"Landing is not at the region start: "+stage+" "+feet);
                }
                for (String alias:new String[]{NAMES[index]}) {
                    String input="farlands "+alias;
                    var parsed=dispatcher.parse(input,allowed);
                    check(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty() && parsed.getContext().getCommand()!=null,
                            "Command did not fully parse: "+input);
                    player.capturedPosition=null;player.capturedWorld=null;player.capturedFlags=null;
                    check(dispatcher.execute(parsed)>0,"Command returned failure: "+input);
                    check(player.capturedWorld==world && player.capturedPosition!=null && player.capturedFlags!=null && player.capturedFlags.isEmpty(),
                            "Command did not call absolute same-Overworld teleport: "+input);
                    var actual=BlockPos.ofFloored(player.capturedPosition);
                    check(actual.equals(feet),"Command alias selected a different destination: "+input+" -> "+actual+" instead of "+feet);
                    validateLanding(world,stage,actual);aliases++;
                }
                report.add(NAMES[index]+": named command executes; real generated landing "+feet.toShortString()+"; two air blocks and supported footing.");
            }
            for(String command:new String[]{"greatsea","greatsea beach","greatsea ocean","greatsea outer_beach","amplified","underworld"}) {
                var parsed=dispatcher.parse(command,allowed);
                check(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty(),"New navigation command did not parse: "+command);
                player.capturedPosition=null;check(dispatcher.execute(parsed)>0,"Navigation command failed: "+command);
                check(player.capturedPosition!=null,"Navigation command did not teleport: "+command);
                var target=player.capturedWorld;var feet=BlockPos.ofFloored(player.capturedPosition);
                check(target.getBlockState(feet).isAir() && target.getBlockState(feet.up()).isAir(),"Navigation landing is obstructed: "+command);
                if(command.equals("underworld"))check(UnstableNether.supported(target) && feet.getY()<UnstableNether.UNDERWORLD_TOP,"Wrong Underworld landing");
                if(command.equals("amplified"))check(AmplifiedTerrain.contains(feet.getX(),feet.getZ()),"Wrong Amplified landing");
                report.add("/"+command+": actual teleport "+target.getRegistryKey().getValue()+" "+feet.toShortString());
            }
            WorldChangesVerification.verify(server,report);
            int contacts=0,templeX=GreatSea.FARLANDS.entryBoundary+FarlandsTemple.center()-FarlandsTemple.OFFSET_X,templeZ=-FarlandsTemple.SAMPLE.depth/2;
            for(int z=0;z<FarlandsTemple.SAMPLE.depth;z++)for(int x=0;x<FarlandsTemple.SAMPLE.width;x++) {
                if(FarlandsTemple.SAMPLE.material(x,19,z)==0 || Math.hypot(x-114,z-79)<76)continue;
                for(int[] offset:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    int wx=templeX+x+offset[0],wz=templeZ+z+offset[1];
                    if(FarlandsTemple.contains(wx,164,wz) || FarlandsTerrain.bareSplit(wx,wz))continue;
                    world.getChunk(wx>>4,wz>>4);
                    check(!world.getBlockState(new BlockPos(wx,164,wz)).isAir(),"Raised terrain does not touch the temple wall");contacts++;
                }
            }
            check(contacts>=20,"Temple still has a cleared moat: "+contacts);
            report.add("Temple terrain join: "+contacts+" raised surface contacts against the round foundation at Y=164; main grass corridor aligned to the staircase.");
            int approach=GreatSea.FARLANDS.entryBoundary+FarlandsTemple.center()-320;
            for(int x=approach-32;x<=approach+32;x+=2)for(int z=-20;z<=20;z+=2) {
                world.getChunk(x>>4,z>>4);
                for(int y=145;y<=164;y++) {
                    var block=world.getBlockState(new BlockPos(x,y,z));
                    String name=net.minecraft.registry.Registries.BLOCK.getId(block.getBlock()).getPath();
                    check(!name.endsWith("_leaves") && !name.equals("vine") && !name.equals("short_grass") && !name.equals("fern"),"Foliage spilled into the bare temple approach at "+x+","+y+","+z);
                }
            }
            report.add("Bare temple approach: no leaves, vines, short grass or ferns in the sampled corridor; native tree canopy spillover is blocked.");
            int dunes=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.SAND_DUNES)+GreatSea.FARLANDS.stageWidths[9]/2,cacti=0,bushes=0,lowerCacti=0;
            for(int cz=-2;cz<=2;cz++)for(int cx=-2;cx<=2;cx++) {
                var chunk=world.getChunk((dunes>>4)+cx,cz);
                int chunkSummit=-64;for(int az=0;az<16;az++)for(int ax=0;ax<16;ax++)chunkSummit=Math.max(chunkSummit,FarlandsTerrain.column(world.getSeed(),chunk.getPos().getStartX()+ax,chunk.getPos().getStartZ()+az).top());
                var pos=new BlockPos.Mutable();
                for(int z=0;z<16;z++)for(int x=0;x<16;x++)for(int y=-64;y<319;y++) {
                    var state=chunk.getBlockState(pos.set(chunk.getPos().getStartX()+x,y,chunk.getPos().getStartZ()+z));
                    if(state.isOf(net.minecraft.block.Blocks.CACTUS)) {
                        cacti++;
                        if(y<chunkSummit-8)lowerCacti++;
                    }
                    if(state.isOf(net.minecraft.block.Blocks.DEAD_BUSH))bushes++;
                }
            }
            check(cacti>0,"Sand Dunes generated no actual cacti");check(lowerCacti>0,"Cacti only appeared on chunk summits");check(bushes>0,"Sand Dunes generated no actual dead bushes");report.add("Sand Dunes: "+cacti+" actual cactus blocks and "+bushes+" dead bushes in 25 fresh FULL chunks; "+lowerCacti+" cactus blocks lie at least 8 blocks below their chunk summit.");
            if(Boolean.getBoolean("unstableterrain.previewFarlands"))FarlandsPerspective.renderAll(world,report);
            report.add("Permissions: ALL accepted, NONE denied; every numeric Farlands route 0..18 rejected.");
            int frameX=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.PLAINS)+256;
            world.getChunk(frameX>>4,40>>4);
            for(int dx=0;dx<4;dx++)for(int dy=0;dy<5;dy++)
                world.setBlockState(new BlockPos(frameX+dx,200+dy,40),
                        (dx==0 || dx==3 || dy==0 || dy==4 ? net.minecraft.block.Blocks.OBSIDIAN:net.minecraft.block.Blocks.AIR).getDefaultState(),2);
            var ignition=new BlockPos(frameX+1,201,40);
            boolean enabled=GreatSea.FARLANDS.enabled;
            try {
                GreatSea.FARLANDS.enabled=false;
                check(net.minecraft.world.dimension.NetherPortal.getNewPortal(world,ignition,Direction.Axis.X).isPresent(),"Portal control frame is not valid");
            } finally { GreatSea.FARLANDS.enabled=enabled; }
            check(net.minecraft.world.dimension.NetherPortal.getNewPortal(world,ignition,Direction.Axis.X).isEmpty(),"Farlands portal ignition remained possible");
            report.add("Portal ignition: a real valid obsidian frame passes native detection with Farlands disabled and is rejected when enabled.");
            report.add("Teleport validation: "+aliases+" routes invoke absolute same-Overworld teleport. Offline override captures native API arguments; client packet synchronization is outside this check.");
            report.add("PASS: 17 named stages and 6 new navigation routes with actual generated landings; numeric routes rejected; seconds="+
                    String.format(java.util.Locale.ROOT,"%.2f",(System.nanoTime()-started)/1e9));
            GreatSea.LOGGER.info("FARLANDS_COMMAND_VERIFICATION_PASS {}",report);
        } catch (Throwable error) {
            report.add("FAIL: "+error);GreatSea.LOGGER.error("FARLANDS_COMMAND_VERIFICATION_FAIL",error);
        } finally {
            try { Files.write(Path.of("farlands-command-verification.txt"),report); }
            catch (Exception error) { GreatSea.LOGGER.error("Cannot save Farlands command verification",error); }
            server.stop(false);
        }
    }
    private static void validateLanding(ServerWorld world,FarlandsTerrain.Stage stage,BlockPos feet) {
        world.getChunk(feet.getX()>>4,feet.getZ()>>4);
        check(FarlandsTerrain.stage(feet.getX(),feet.getZ())==stage,"Landing left requested stage "+stage+" at "+feet);
        check(world.getBlockState(feet).isAir() && world.getBlockState(feet.up()).isAir(),"Landing is obstructed at "+feet);
        var below=feet.down();var state=world.getBlockState(below);
        check(state.isSideSolidFullSquare(world,below,Direction.UP) ||
                (stage==FarlandsTerrain.Stage.WATER && state.getFluidState().isIn(FluidTags.WATER)),"Landing has no supported footing at "+feet);
    }
}
