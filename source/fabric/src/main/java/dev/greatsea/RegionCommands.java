package dev.greatsea;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.block.Blocks;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import java.util.Set;

/** Named navigation to real generated terrain; never creates landing platforms. */
public final class RegionCommands {
    private RegionCommands() {}
    private static CommandSyntaxException error(String text){return new SimpleCommandExceptionType(Text.literal(text)).create();}
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var sea=CommandManager.literal("greatsea").requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .executes(c->teleport(c.getSource(),"beach"));
        for(String name:new String[]{"beach","ocean","outer_beach"})sea.then(CommandManager.literal(name).executes(c->teleport(c.getSource(),name)));
        sea.then(CommandManager.literal("list").executes(c->{c.getSource().sendFeedback(()->Text.literal("/greatsea beach | ocean | outer_beach"),false);return 1;}));
        dispatcher.register(sea);
        for(String name:new String[]{"amplified","underworld","mist"})dispatcher.register(CommandManager.literal(name)
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK)).executes(c->teleport(c.getSource(),name)));
    }
    public static ServerWorld world(ServerCommandSource source,String name)throws CommandSyntaxException {
        var world=name.equals("underworld")?source.getServer().getWorld(net.minecraft.world.World.NETHER):source.getServer().getOverworld();
        if(world==null || (name.equals("underworld")?!UnstableNether.supported(world):!UnstableWorldGen.isUnstable(world.getChunkManager().getChunkGenerator())))
            throw error("This destination requires an Unstable world.");
        if(!name.equals("amplified") && !name.equals("underworld") && !name.equals("mist") && !GreatSea.CONFIG.enabled)throw error("Great Sea is disabled.");
        return world;
    }
    private static int teleport(ServerCommandSource source,String name)throws CommandSyntaxException {
        var player=source.getPlayerOrThrow();var world=world(source,name);var feet=destination(world,name);
        if(!player.teleport(world,feet.getX()+.5,feet.getY(),feet.getZ()+.5,Set.of(),player.getYaw(),player.getPitch(),true))throw error("Could not teleport to "+name+".");
        player.setVelocity(Vec3d.ZERO);player.fallDistance=0;player.stopGliding();player.closeHandledScreen();
        source.sendFeedback(()->Text.literal("Teleported to "+name+"."),false);return 1;
    }
    public static BlockPos destination(ServerWorld world,String name)throws CommandSyntaxException {
        int center=switch(name){
            case "amplified"->AmplifiedTerrain.START;
            case "mist"->MistRegion.CENTER_X;
            case "underworld"->0;
            case "beach"->GreatSea.CONFIG.innerBoundary+GreatSea.CONFIG.beachWidth/2;
            case "ocean"->GreatSea.CONFIG.innerBoundary+GreatSea.CONFIG.beachWidth+GreatSea.CONFIG.underwaterSlopeWidth+1024;
            case "outer_beach"->GreatSea.CONFIG.outerBoundary-GreatSea.CONFIG.beachWidth/2;
            default->throw error("Unknown destination: "+name);
        };
        boolean under=name.equals("underworld");
        for(int radius=0;radius<=128;radius+=4)for(int z=-radius;z<=radius;z+=4)for(int dx=-radius;dx<=radius;dx+=4) {
            if(radius>0 && Math.abs(z)!=radius && Math.abs(dx)!=radius)continue;
            int x=center+dx,targetZ=name.equals("mist")?MistRegion.CENTER_Z+z:z;
            if(name.equals("amplified") && !AmplifiedTerrain.contains(x,targetZ))continue;
            if(!world.getWorldBorder().contains(new BlockPos(x,0,targetZ)))continue;
            world.getChunk(x>>4,targetZ>>4);
            int top=under?UnstableNether.UNDERWORLD_TOP-1:world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,targetZ);
            int bottom=under?world.getBottomY():top;
            for(int y=top;y>=bottom;y--) {
                var feet=new BlockPos(x,y,targetZ);
                if(!world.getBlockState(feet).isAir() || !world.getBlockState(feet.up()).isAir())continue;
                var floor=world.getBlockState(feet.down());
                if(floor.isSideSolidFullSquare(world,feet.down(),Direction.UP) || name.equals("ocean") && floor.isOf(Blocks.WATER))return feet;
            }
        }
        throw error("No safe landing found in "+name+".");
    }
}
