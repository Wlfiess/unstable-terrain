package dev.greatsea;

import com.mojang.authlib.GameProfile;
import net.minecraft.command.permission.PermissionPredicate;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.nio.file.*;
import java.util.*;

/** Opt-in live command checks against fully generated negative-region landings. */
public final class SpokelandsCommandVerifier {
    private SpokelandsCommandVerifier(){}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static final class OfflinePlayer extends ServerPlayerEntity {
        ServerWorld destination;Vec3d landed;Set<PositionFlag> flags;
        OfflinePlayer(MinecraftServer server,ServerWorld world){super(server,world,new GameProfile(UUID.randomUUID(),"SpokelandsTest"),SyncedClientOptions.createDefault());}
        @Override public void closeHandledScreen(){currentScreenHandler=playerScreenHandler;}
        @Override public void sendAbilitiesUpdate(){}
        @Override public boolean teleport(ServerWorld world,double x,double y,double z,Set<PositionFlag> flags,float yaw,float pitch,boolean resetCamera){
            destination=world;landed=new Vec3d(x,y,z);this.flags=Set.copyOf(flags);
            setServerWorld(world);setPosition(x,y,z);setYaw(yaw);setPitch(pitch);return true;
        }
    }
    public static void verify(ServerWorld world,List<String> report)throws Exception{
        check(FarlandsWorld.enabledOverworld(world),"Verification requires an enabled Unstable Overworld");
        var server=world.getServer();var dispatcher=server.getCommandManager().getDispatcher();var player=new OfflinePlayer(server,world);
        var source=server.getCommandSource().withEntity(player).withPermissions(PermissionPredicate.ALL).withSilent();
        var denied=source.withPermissions(PermissionPredicate.NONE);var root=dispatcher.getRoot().getChild("spokelands");
        check(root!=null&&root.canUse(source)&&!root.canUse(denied),"Spokelands registration or operator permission failed");
        check(dispatcher.execute("spokelands list",source)==1,"Spokelands list command failed");
        for(String invalid:new String[]{"spokelands 1","spokelands plains south","spokelands plains east"}){
            var parsed=dispatcher.parse(invalid,source);check(parsed.getReader().canRead()||!parsed.getExceptions().isEmpty()||parsed.getContext().getCommand()==null,"Invalid command accepted: "+invalid);
        }
        var suggestions=dispatcher.getCompletionSuggestions(dispatcher.parse("spokelands ",source)).get(5,java.util.concurrent.TimeUnit.SECONDS);
        var names=new HashSet<String>();for(var item:suggestions.getList())names.add(item.getText());
        int routes=0;
        for(var region:SpokelandsTerrain.Region.values()){
            check(names.contains(SpokelandsCommand.name(region)),"Missing region completion: "+region);
            for(boolean west:new boolean[]{false,true}){
                String command="spokelands "+SpokelandsCommand.name(region)+" "+(west?"west":"north");
                var expected=SpokelandsCommand.destination(world,region,west);player.landed=null;
                check(dispatcher.execute(command,source)==1&&player.destination==world&&player.landed!=null&&player.flags.isEmpty(),"Absolute same-Overworld teleport failed: "+command);
                var feet=BlockPos.ofFloored(player.landed);check(expected.equals(feet),"Different actual command landing: "+command);
                check(SpokelandsTerrain.contains(feet.getX(),feet.getZ())&&SpokelandsTerrain.region(feet.getX(),feet.getZ())==region,"Landing left requested negative region: "+command);
                check(west?feet.getX()<0&&Math.abs(feet.getZ())<=128:feet.getZ()<0&&Math.abs(feet.getX())<=128,"Landing selected wrong face: "+command);
                int depth=(int)FarlandsWorld.depth(feet.getX(),feet.getZ());check(depth>=SpokelandsTerrain.start(region)&&depth<=SpokelandsTerrain.start(region)+132,"Landing is not near region entrance: "+command);
                check(world.getWorldBorder().contains(feet)&&world.getBlockState(feet).isAir()&&world.getBlockState(feet.up()).isAir()&&world.getBlockState(feet.down()).isSideSolidFullSquare(world,feet.down(),Direction.UP),"Unsafe or obstructed real footing: "+command);
                report.add("PASS /"+command+" generated entrance landing "+feet.toShortString());routes++;
            }
            player.landed=null;check(dispatcher.execute("spokelands "+SpokelandsCommand.name(region),source)==1&&player.landed.z<0&&Math.abs(player.landed.x)<129,"Default face is not north: "+region);
        }
        player.landed=null;check(dispatcher.execute("spokelands",source)==1&&SpokelandsTerrain.region((int)Math.floor(player.landed.x),(int)Math.floor(player.landed.z))==SpokelandsTerrain.Region.CLASSIC,"Default entrance command failed");
        report.add("PASS all "+routes+" named north/west Spokelands routes, north defaults, numeric rejection, Tab completion and operator permissions");
    }
    public static void run(MinecraftServer server){
        var report=new ArrayList<String>();long began=System.nanoTime();
        try{verify(server.getOverworld(),report);report.add("PASS Spokelands commands in "+(System.nanoTime()-began)/1e9+" seconds");GreatSea.LOGGER.info("SPOKELANDS_COMMAND_VERIFICATION_PASS {}",report);}
        catch(Throwable error){report.add("FAIL "+error);GreatSea.LOGGER.error("SPOKELANDS_COMMAND_VERIFICATION_FAIL",error);}
        finally{try{Files.write(Path.of("spokelands-command-verification.txt"),report);}catch(Exception error){GreatSea.LOGGER.error("Cannot write Spokelands command verification",error);}server.stop(false);}
    }
}
