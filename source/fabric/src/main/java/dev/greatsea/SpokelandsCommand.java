package dev.greatsea;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import java.util.Set;

/** Named navigation to the separate negative-coordinate terrain sequence. */
public final class SpokelandsCommand {
    private static final String[] NAMES={"plains","flower_hills","red_plains","winding","dark_forest","bridges","rainbow_forest","sand_lands","sculk_forest"};
    private static final String[] LABELS={"Spokelands Plains","Flower Hills","Red Plains","Winding Lands","Dark Forest","Bridges","Rainbow Forest","Sand Lands","Sculk Forest"};
    private SpokelandsCommand() {}
    public static String name(SpokelandsTerrain.Region region){return NAMES[region.ordinal()];}
    public static String label(SpokelandsTerrain.Region region){return LABELS[region.ordinal()];}
    private static CommandSyntaxException error(String text){return new SimpleCommandExceptionType(Text.literal(text)).create();}
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher){
        var root=CommandManager.literal("spokelands")
            .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
            .executes(c->teleport(c.getSource(),SpokelandsTerrain.Region.CLASSIC,false));
        root.then(CommandManager.literal("list").executes(c->list(c.getSource())));
        for(var region:SpokelandsTerrain.Region.values()){
            var named=CommandManager.literal(name(region)).executes(c->teleport(c.getSource(),region,false));
            named.then(CommandManager.literal("north").executes(c->teleport(c.getSource(),region,false)));
            named.then(CommandManager.literal("west").executes(c->teleport(c.getSource(),region,true)));
            root.then(named);
        }
        dispatcher.register(root);
    }
    private static int list(ServerCommandSource source){
        var text=new StringBuilder("/spokelands <name> [north|west] — north is the default:");
        for(var region:SpokelandsTerrain.Region.values())text.append('\n').append(name(region));
        source.sendFeedback(()->Text.literal(text.toString()),false);return 1;
    }
    private static int teleport(ServerCommandSource source,SpokelandsTerrain.Region region,boolean west)throws CommandSyntaxException{
        var player=source.getPlayerOrThrow();var world=source.getServer().getOverworld();
        if(!FarlandsWorld.enabledOverworld(world))throw error("Spokelands teleport requires an Unstable world with Farlands enabled.");
        var feet=destination(world,region,west);
        if(!player.teleport(world,feet.getX()+.5,feet.getY(),feet.getZ()+.5,Set.of(),player.getYaw(),player.getPitch(),true))
            throw error("Could not teleport to "+label(region)+".");
        player.setVelocity(Vec3d.ZERO);player.fallDistance=0;player.stopGliding();player.closeHandledScreen();
        source.sendFeedback(()->Text.literal("Teleported to "+label(region)+" on the "+(west?"west":"north")+" face."),false);return 1;
    }
    /** Finds real safe footing at the region entrance; creates no teleport platforms. */
    public static BlockPos destination(ServerWorld world,SpokelandsTerrain.Region region,boolean west)throws CommandSyntaxException{
        long entrance=(long)GreatSea.FARLANDS.entryBoundary+SpokelandsTerrain.start(region);
        if(entrance>29_999_872L)throw error("This Spokelands region is beyond Minecraft's coordinate limit.");
        int outward=((int)entrance+3)&~3;
        for(int radius=0;radius<=128;radius+=4){
            for(int tangent=-radius;tangent<=radius;tangent+=4)for(int advance=-radius;advance<=radius;advance+=4){
                if(radius>0 && Math.abs(tangent)!=radius && Math.abs(advance)!=radius)continue;
                int x=west?-(outward+advance):tangent,z=west?tangent:-(outward+advance);
                if(!SpokelandsTerrain.contains(x,z)||SpokelandsTerrain.region(x,z)!=region||!world.getWorldBorder().contains(new BlockPos(x,0,z)))continue;
                world.getChunk(x>>4,z>>4);
                int y=world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z);
                if(y<=world.getBottomY()||y>=world.getTopYInclusive())continue;
                var feet=new BlockPos(x,y,z);
                if(!world.getBlockState(feet).isAir()||!world.getBlockState(feet.up()).isAir())continue;
                var below=feet.down();var floor=world.getBlockState(below);
                if(floor.isSideSolidFullSquare(world,below,Direction.UP)&&!floor.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK))return feet;
            }
        }
        throw error("No clear landing found at the start of "+label(region)+"; teleport cancelled.");
    }
}
