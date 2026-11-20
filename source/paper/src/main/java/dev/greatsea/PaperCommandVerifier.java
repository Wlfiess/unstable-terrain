package dev.greatsea;

/** Exercises the live Paper dispatcher and Bukkit command map after lifecycle registration. */
final class PaperCommandVerifier {
    private PaperCommandVerifier() {}
    static void verify(net.minecraft.server.level.ServerLevel world,StringBuilder report)throws Exception{
        var server=world.getServer();var dispatcher=server.getCommands().getDispatcher();var source=server.createCommandSourceStack();
        String[] roots={"farlands","spokelands","greatsea","amplified","underworld","mist","unstableterrain"};
        for(String name:roots){
            var node=dispatcher.getRoot().getChild(name);
            if(node==null||!node.canUse(source))throw new AssertionError("Live Paper command unavailable: "+name);
            if(org.bukkit.Bukkit.getCommandMap().getCommand(name)==null || org.bukkit.Bukkit.getCommandMap().getCommand("unstableterrain:"+name)==null)
                throw new AssertionError("Bukkit command-map registration missing: "+name);
        }
        for(String command:new String[]{"farlands list","spokelands list","greatsea list","unstableterrain"}){
            if(dispatcher.execute(command,source)!=1 || !org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(),command))
                throw new AssertionError("Live Paper command dispatch failed: "+command);
        }
        for(String name:PaperCommands.FARLANDS_NAMES){
            var parsed=dispatcher.parse("farlands "+name,source);
            if(parsed.getReader().canRead()||parsed.getContext().getCommand()==null)throw new AssertionError("Named Farlands command missing: "+name);
            rejectsConsoleTeleport(dispatcher,source,"farlands "+name);
        }
        rejectsConsoleTeleport(dispatcher,source,"spokelands");
        for(var region:SpokelandsTerrain.Region.values())for(String suffix:new String[]{""," north"," west"}){
            String command="spokelands "+SpokelandsCommand.name(region)+suffix;
            var parsed=dispatcher.parse(command,source);
            if(parsed.getReader().canRead()||parsed.getContext().getCommand()==null)throw new AssertionError("Named Spokelands command missing: "+command);
            rejectsConsoleTeleport(dispatcher,source,command);
        }
        for(String command:new String[]{"greatsea","greatsea beach","greatsea ocean","greatsea outer_beach","amplified","underworld"})
            rejectsConsoleTeleport(dispatcher,source,command);
        var numeric=dispatcher.parse("farlands 1",source);
        if(!numeric.getReader().canRead() && numeric.getContext().getCommand()!=null)throw new AssertionError("Numeric Farlands command unexpectedly accepted");
        var spokeNumeric=dispatcher.parse("spokelands 1",source);
        if(!spokeNumeric.getReader().canRead()&&spokeNumeric.getContext().getCommand()!=null)throw new AssertionError("Numeric Spokelands command unexpectedly accepted");
        var suggestions=dispatcher.getCompletionSuggestions(dispatcher.parse("farlands ",source)).get(5,java.util.concurrent.TimeUnit.SECONDS);
        var names=new java.util.HashSet<String>();for(var entry:suggestions.getList())names.add(entry.getText());
        for(String name:PaperCommands.FARLANDS_NAMES)if(!names.contains(name))throw new AssertionError("Farlands Tab completion missing: "+name);
        var spokeSuggestions=dispatcher.getCompletionSuggestions(dispatcher.parse("spokelands ",source)).get(5,java.util.concurrent.TimeUnit.SECONDS);
        var spokeNames=new java.util.HashSet<String>();for(var entry:spokeSuggestions.getList())spokeNames.add(entry.getText());
        for(var region:SpokelandsTerrain.Region.values())if(!spokeNames.contains(SpokelandsCommand.name(region)))throw new AssertionError("Spokelands Tab completion missing: "+region);
        var actor=new net.minecraft.server.level.ServerPlayer(server,world,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"CommandCheck"),net.minecraft.server.level.ClientInformation.createDefault());
        var ordinary=actor.createCommandSourceStack();
        for(String name:roots){
            if(dispatcher.getRoot().getChild(name).canUse(ordinary)||dispatcher.getRoot().getChild("unstableterrain:"+name).canUse(ordinary))
                throw new AssertionError("Non-operator can use command: "+name);
        }
        // An isolated NPC and an in-memory packet sink exercise the real teleport
        // callbacks without logging in a client or sending packets on a network.
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,io.netty.channel.ChannelFutureListener listener){}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,io.netty.channel.ChannelFutureListener listener,boolean flush){}
        };
        connection.channel=new io.netty.channel.embedded.EmbeddedChannel();connection.address=new java.net.InetSocketAddress("127.0.0.1",0);
        actor.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,connection,actor,
            net.minecraft.server.network.CommonListenerCookie.createInitial(actor.getGameProfile(),false));
        world.getChunk(0,0);actor.setPos(8,100,8);world.addNewPlayer(actor);
        var player=actor.getBukkitEntity();var permissions=player.addAttachment(PaperPlugin.instance);
        for(String name:roots)permissions.setPermission("unstableterrain.command."+name,true);
        permissions.setPermission("unstableterrain.command.settings",true);
        try{
            for(String name:roots)if(dispatcher.getRoot().getChild(name).canUse(actor.createCommandSourceStack()))
                throw new AssertionError("Legacy permission grants access to non-operator: "+name);
            report.append("PASS live Paper and Bukkit command dispatch, Tab completion, and operator-only commands and aliases\n");
            player.setOp(true);
            var commands=new java.util.ArrayList<String>(java.util.List.of("greatsea","amplified","underworld","farlands plains","farlands split","farlands orbitals","spokelands"));
            for(var region:SpokelandsTerrain.Region.values())for(String face:new String[]{"north","west"})commands.add("spokelands "+SpokelandsCommand.name(region)+" "+face);
            for(String command:commands){
                if(dispatcher.execute(command,actor.createCommandSourceStack())!=1)throw new AssertionError("Player command failed: "+command);
                var location=player.getLocation();
                if(command.equals("greatsea")&&!SeaTerrain.contains(GreatSea.CONFIG,location.getBlockX(),location.getBlockZ()))throw new AssertionError("Great Sea teleport missed");
                if(command.equals("amplified")&&!AmplifiedTerrain.contains(location.getBlockX(),location.getBlockZ()))throw new AssertionError("Amplified teleport missed");
                if(command.equals("underworld")&&(player.getWorld().getEnvironment()!=org.bukkit.World.Environment.NETHER||location.getY()>=UnstableNether.UNDERWORLD_TOP))throw new AssertionError("Underworld teleport missed");
                if(command.startsWith("farlands ")&&!FarlandsWorld.contains(location.getBlockX(),location.getBlockZ()))throw new AssertionError("Farlands teleport missed");
                if(command.startsWith("spokelands")){
                    int x=location.getBlockX(),z=location.getBlockZ();
                    if(!SpokelandsTerrain.contains(x,z))throw new AssertionError("Spokelands teleport missed negative terrain: "+command);
                    var fields=command.split(" ");var expected=SpokelandsTerrain.Region.CLASSIC;
                    if(fields.length>1)expected=java.util.Arrays.stream(SpokelandsTerrain.Region.values()).filter(region->SpokelandsCommand.name(region).equals(fields[1])).findFirst().orElseThrow();
                    if(SpokelandsTerrain.region(x,z)!=expected)throw new AssertionError("Spokelands teleport chose wrong region: "+command);
                    boolean west=command.endsWith(" west");
                    if(west?x>=0||Math.abs(z)>128:z>=0||Math.abs(x)>128)throw new AssertionError("Spokelands teleport chose wrong face: "+command);
                    int depth=(int)FarlandsWorld.depth(x,z);
                    if(depth<SpokelandsTerrain.start(expected)||depth>SpokelandsTerrain.start(expected)+132)throw new AssertionError("Spokelands teleport is not at region entrance: "+command);
                    var feet=net.minecraft.core.BlockPos.containing(location.getX(),location.getY(),location.getZ());
                    if(!world.getWorldBorder().isWithinBounds(feet)||!world.getBlockState(feet).isAir()||!world.getBlockState(feet.above()).isAir()||!world.getBlockState(feet.below()).isFaceSturdy(world,feet.below(),net.minecraft.core.Direction.UP))throw new AssertionError("Spokelands landing is unsafe: "+command);
                    report.append("PASS live /"+command+" generated entrance landing "+feet.toShortString()+"\n");
                }
            }
            report.append("PASS live player /greatsea /amplified /underworld /farlands plains, split and orbitals and all north/west /spokelands teleports\n");
        }finally{player.setOp(false);player.removeAttachment(permissions);actor.discard();connection.channel.close();}
    }
    private static void rejectsConsoleTeleport(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,net.minecraft.commands.CommandSourceStack source,String command)throws Exception{
        try{dispatcher.execute(command,source);throw new AssertionError("Console teleport accepted: "+command);}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){
            if(!expected.getRawMessage().getString().equals("Only a player can use this teleport command."))
                throw new AssertionError("Teleport callback was not reached: "+command+" -> "+expected.getMessage());
        }
    }
}
