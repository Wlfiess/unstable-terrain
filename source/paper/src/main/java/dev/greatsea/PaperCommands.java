package dev.greatsea;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.command.VanillaCommandWrapper;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

/** Typed Paper registration: plugin permissions and command-map entries survive dispatcher rebuilds. */
final class PaperCommands {
    static final String[] FARLANDS_NAMES={"plains","repeating","floating","gravel","spikes","gravel_after_spikes","split","cherry","plains_hills",
        "sand_dunes","sand_farlands","corrupted","orbitals","chunks","decay","water","skygrid"};
    private static final String[] LABELS={"Farlands Plains","Repeating Lands","Floating Islands","Gravel Lands","Spike Lands","Gravel Lands After Spikes","Split Lands",
        "Cherry Grove","Plains Hills","Sand Dunes","Sand Farlands","Corrupted Farlands","Classic Farlands / Orbitals","Chunks","Corrupting Repeating Chunks","Water","SkyGrid"};
    private PaperCommands() {}

    private static boolean operator(CommandSourceStack source){return source.getSender().isOp();}

    static void register(Commands registrar){
        var farlands=Commands.literal("farlands").requires(PaperCommands::operator)
            .executes(c->listFarlands(c.getSource()));
        farlands.then(Commands.literal("list").executes(c->listFarlands(c.getSource())));
        for(var stage:FarlandsTerrain.Stage.values())farlands.then(Commands.literal(FARLANDS_NAMES[stage.ordinal()])
            .executes(c->farlands(c.getSource(),stage)));
        registrar.register(farlands.build(),"Navigate to a named Farlands region");

        var spokelands=Commands.literal("spokelands").requires(PaperCommands::operator)
            .executes(c->spokelands(c.getSource(),SpokelandsTerrain.Region.CLASSIC,false));
        spokelands.then(Commands.literal("list").executes(c->listSpokelands(c.getSource())));
        for(var region:SpokelandsTerrain.Region.values()){
            var named=Commands.literal(SpokelandsCommand.name(region)).executes(c->spokelands(c.getSource(),region,false));
            named.then(Commands.literal("north").executes(c->spokelands(c.getSource(),region,false)));
            named.then(Commands.literal("west").executes(c->spokelands(c.getSource(),region,true)));
            spokelands.then(named);
        }
        registrar.register(spokelands.build(),"Navigate to a named negative-coordinate Spokelands region");

        var sea=Commands.literal("greatsea").requires(PaperCommands::operator)
            .executes(c->region(c.getSource(),"beach"));
        for(String name:new String[]{"beach","ocean","outer_beach"})sea.then(Commands.literal(name).executes(c->region(c.getSource(),name)));
        sea.then(Commands.literal("list").executes(c->{c.getSource().getSender().sendMessage("/greatsea beach | ocean | outer_beach");return 1;}));
        registrar.register(sea.build(),"Navigate to the Great Sea");
        for(String name:new String[]{"amplified","underworld","mist"})registrar.register(Commands.literal(name)
            .requires(PaperCommands::operator).executes(c->region(c.getSource(),name)).build(),"Navigate to "+name);
        registrar.register(Commands.literal("unstableterrain").requires(PaperCommands::operator).executes(c->status(c.getSource()))
                .then(Commands.literal("orbitalpull")
                        .executes(c->OrbitalRuntime.simulatePull(((org.bukkit.craftbukkit.entity.CraftPlayer)player(c.getSource())).getHandle())))
                .then(GameplayCommands.settings(GameplaySettings.current(),
                        (source,message)->source.getSender().sendMessage(message)))
                .build(),"Show the current terrain region and configure gameplay");
    }

    private static CommandSyntaxException error(String text){return new SimpleCommandExceptionType(new LiteralMessage(text)).create();}
    private static Player player(CommandSourceStack source)throws CommandSyntaxException{
        if(source.getExecutor() instanceof Player player)return player;
        throw error("Only a player can use this teleport command.");
    }
    private static int listFarlands(CommandSourceStack source){
        source.getSender().sendMessage("/farlands <name> - use Tab to choose a region:\n"+String.join("\n",FARLANDS_NAMES));return 1;
    }
    private static int farlands(CommandSourceStack source,FarlandsTerrain.Stage stage)throws CommandSyntaxException{
        var player=player(source);
        var server=((CraftWorld)player.getWorld()).getHandle().getServer();
        var world=server.overworld();
        if(!FarlandsWorld.enabledOverworld(world))throw error("Farlands teleport requires an Unstable world with Farlands enabled.");
        teleport(player,world,FarlandsCommand.destination(world,stage));
        source.getSender().sendMessage("Teleported to "+LABELS[stage.ordinal()]+" ("+(stage.ordinal()+1)+"/17).");return 1;
    }
    private static int listSpokelands(CommandSourceStack source){
        var text=new StringBuilder("/spokelands <name> [north|west] - north is the default:");
        for(var region:SpokelandsTerrain.Region.values())text.append('\n').append(SpokelandsCommand.name(region));
        source.getSender().sendMessage(text.toString());return 1;
    }
    private static int spokelands(CommandSourceStack source,SpokelandsTerrain.Region region,boolean west)throws CommandSyntaxException{
        var player=player(source);var world=((CraftWorld)player.getWorld()).getHandle().getServer().overworld();
        if(!FarlandsWorld.enabledOverworld(world))throw error("Spokelands teleport requires an Unstable world with Farlands enabled.");
        teleport(player,world,SpokelandsCommand.destination(world,region,west));
        source.getSender().sendMessage("Teleported to "+SpokelandsCommand.label(region)+" on the "+(west?"west":"north")+" face.");return 1;
    }
    private static int region(CommandSourceStack source,String name)throws CommandSyntaxException{
        var player=player(source);
        var world=RegionCommands.world(VanillaCommandWrapper.getListener(source.getSender()),name);
        teleport(player,world,RegionCommands.destination(world,name));
        source.getSender().sendMessage("Teleported to "+name+".");return 1;
    }
    private static void teleport(Player player,net.minecraft.server.level.ServerLevel world,net.minecraft.core.BlockPos feet)throws CommandSyntaxException{
        var rotation=player.getLocation();
        if(!player.teleport(new Location(world.getWorld(),feet.getX()+.5,feet.getY(),feet.getZ()+.5,rotation.getYaw(),rotation.getPitch()),PlayerTeleportEvent.TeleportCause.COMMAND))
            throw error("Could not teleport to the destination.");
        player.setVelocity(new Vector());player.setFallDistance(0);player.setGliding(false);player.closeInventory();
    }
    private static int status(CommandSourceStack source){
        var location=source.getLocation();var level=((CraftWorld)location.getWorld()).getHandle();
        int x=location.getBlockX(),z=location.getBlockZ();
        String message=!FarlandsWorld.supported(level)?"Unstable Terrain is inactive here.":
            FarlandsWorld.contains(x,z)?(SpokelandsTerrain.contains(x,z)?"Spokelands: "+SpokelandsTerrain.region(x,z):"Farlands: "+FarlandsTerrain.stage(x,z))+"; depth="+(int)FarlandsWorld.depth(x,z)+
                "; "+(GameplaySettings.Rule.FARLANDS_FIREWORKS.enabled()?"firework boosts are allowed":"firework boost stops at depth "+GreatSea.FARLANDS.fireworkDisableDepth)+". Physical map coordinates remain unchanged.":
                "Unstable Terrain: Great Sea, Underworld and Farlands. Farlands begins at |X| or |Z| "+GreatSea.FARLANDS.entryBoundary+".";
        source.getSender().sendMessage(message);return 1;
    }
}

