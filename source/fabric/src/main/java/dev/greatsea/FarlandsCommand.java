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

/** Operator navigation within the existing Overworld; generation is unchanged. */
public final class FarlandsCommand {
    private static final String[] NAMES={"plains","repeating","floating","gravel","spikes","gravel_after_spikes","split","cherry","plains_hills",
            "sand_dunes","sand_farlands","corrupted","orbitals","chunks","decay","water","skygrid"};
    private static final String[] LABELS={"Farlands Plains","Repeating Lands","Floating Islands","Gravel Lands","Spike Lands","Gravel Lands After Spikes","Split Lands",
            "Cherry Grove","Plains Hills","Sand Dunes","Sand Farlands","Corrupted Farlands","Classic Farlands / Orbitals","Chunks","Corrupting Repeating Chunks","Water","SkyGrid"};
    private FarlandsCommand() {}
    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Text.literal(message)).create();
    }
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root=CommandManager.literal("farlands")
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .executes(context->list(context.getSource()));
        root.then(CommandManager.literal("list").executes(context->list(context.getSource())));
        for(var stage:FarlandsTerrain.Stage.values()) {
            root.then(CommandManager.literal(NAMES[stage.ordinal()]).executes(context->teleport(context.getSource(),stage)));
        }
        dispatcher.register(root);
    }
    private static int list(ServerCommandSource source) {
        var message=new StringBuilder("/farlands <name> — use Tab to choose a region:");
        for(var stage:FarlandsTerrain.Stage.values())message.append('\n').append(NAMES[stage.ordinal()]);
        source.sendFeedback(()->Text.literal(message.toString()),false);
        return 1;
    }
    private static int teleport(ServerCommandSource source,FarlandsTerrain.Stage stage) throws CommandSyntaxException {
        var player=source.getPlayerOrThrow();
        var world=source.getServer().getOverworld();
        if(!FarlandsWorld.supported(world))throw error("Farlands teleport requires an Unstable world with Farlands enabled.");
        var feet=destination(world,stage);
        if(!player.teleport(world,feet.getX()+.5,feet.getY(),feet.getZ()+.5,Set.of(),player.getYaw(),player.getPitch(),true))
            throw error("Could not teleport to "+LABELS[stage.ordinal()]+".");
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance=0;
        player.stopGliding();
        player.closeHandledScreen();
        source.sendFeedback(()->Text.literal("Teleported to "+LABELS[stage.ordinal()]+" ("+(stage.ordinal()+1)+"/17)."),false);
        return 1;
    }
    /** Checks actual generated blocks, including saved edits, before selecting a landing. */
    public static BlockPos destination(ServerWorld world,FarlandsTerrain.Stage stage) throws CommandSyntaxException {
        long center=(long)GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(stage);
        if(stage==FarlandsTerrain.Stage.ORBITALS){
            if(StructurePolicy.enabled()){
                var platform=FarlandsOrbitals.controlLanding(world.getSeed());
                if(world.getWorldBorder().contains(platform)&&world.getBlockState(platform).isAir()
                        &&world.getBlockState(platform.up()).isAir()&&world.getBlockState(platform.down()).isOf(Blocks.GLASS))
                    return platform;
            }
            center=(long)GreatSea.FARLANDS.entryBoundary+FarlandsOrbitals.center()-FarlandsOrbitals.length(world.getSeed())/2-24;
        }
        if(stage==FarlandsTerrain.Stage.SPLIT)center=(long)GreatSea.FARLANDS.entryBoundary+FarlandsTemple.center()-180;
        if(center>29_999_872L)throw error("This region is beyond Minecraft's coordinate limit; adjust the Farlands distances.");
        int step=stage==FarlandsTerrain.Stage.SKYGRID?1:4;
        int x=step==1?(int)center:((int)center+3)&~3;
        // Skygrid starts on its own six-block lattice, independent of chunk alignment.
        // Search actual columns so saved edits and irregular grid intervals are respected.
        for(int radius=0;radius<=128;radius+=step) {
            for(int dz=-radius;dz<=radius;dz+=step)for(int dx=-radius;dx<=radius;dx+=step) {
                if(radius>0 && Math.abs(dx)!=radius && Math.abs(dz)!=radius)continue;
                int px=x+dx,pz=stage==FarlandsTerrain.Stage.ORBITALS&&StructurePolicy.enabled()?FarlandsOrbitals.tangentOrigin()+dz:dz;
                if(FarlandsTerrain.stage(px,pz)!=stage || !world.getWorldBorder().contains(new BlockPos(px,0,pz)))continue;
                world.getChunk(px>>4,pz>>4);
                int y=world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,px,pz);
                if(y<=world.getBottomY() || y>=world.getTopYInclusive())continue;
                var feet=new BlockPos(px,y,pz);
                if(!world.getBlockState(feet).isAir() || !world.getBlockState(feet.up()).isAir())continue;
                var floor=world.getBlockState(feet.down());
                boolean water=stage==FarlandsTerrain.Stage.WATER && floor.isOf(Blocks.WATER);
                if(water || floor.isSideSolidFullSquare(world,feet.down(),Direction.UP))return feet;
            }
        }
        throw error("No clear landing found in "+LABELS[stage.ordinal()]+"; teleport cancelled.");
    }
}

