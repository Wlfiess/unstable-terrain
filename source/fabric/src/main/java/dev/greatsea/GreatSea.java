package dev.greatsea;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GreatSea implements ModInitializer {
    public static final Logger LOGGER=LoggerFactory.getLogger("unstableterrain");
    public static SeaConfig CONFIG=new SeaConfig();
    public static FarlandsConfig FARLANDS=new FarlandsConfig();
    @Override public void onInitialize() {
        UnstableNether.initialize();
        CONFIG=SeaConfig.load();
        FARLANDS=FarlandsConfig.load();
        GameplaySettings.initialize(FabricLoader.getInstance().getConfigDir());
        ServerLifecycleEvents.SERVER_STARTING.register(server->{var registry=server.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.BIOME);FarlandsBiomes.registerBiomes(registry);MistBiomes.registerBiomes(registry);});
        FarlandsCoordinates.initialize();
        MistNetwork.initialize();
        UnderworldGameMode.initialize();
        ServerLifecycleEvents.SERVER_STOPPED.register(OrbitalRuntime::stopped);
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)
                OrbitalRuntime.useBlock(serverPlayer,hand,hit.getBlockPos());
            return net.minecraft.util.ActionResult.PASS;
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{
            OrbitalRuntime.tick(server);
            for(var player:server.getPlayerManager().getPlayerList()){SpokelandsDarkness.update(player);MistEffects.update(player);}
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->FarlandsCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->SpokelandsCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->dispatcher.register(
                CommandManager.literal("unstableterrain")
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK)).executes(context->{
                    var source=context.getSource();int x=(int)source.getPosition().x,z=(int)source.getPosition().z;
                    boolean unstable=FarlandsWorld.supported(source.getWorld());
                    source.sendFeedback(()->Text.literal(!unstable ? "Unstable Terrain is inactive here; select Unstable in World Type." :
                            FarlandsWorld.contains(x,z) ? (SpokelandsTerrain.contains(x,z)?"Spokelands: "+SpokelandsTerrain.region(x,z):"Farlands: "+FarlandsTerrain.stage(x,z))+"; depth="+(int)FarlandsWorld.depth(x,z)+
                                    "; "+(GameplaySettings.Rule.FARLANDS_FIREWORKS.enabled()?"firework boosts are allowed":"firework boost stops at depth "+FARLANDS.fireworkDisableDepth)+". Physical map coordinates remain unchanged." :
                                    "Unstable Terrain: Mist, Great Sea, Underworld and Farlands. Farlands begins at |X| or |Z| "+FARLANDS.entryBoundary+"."),false);
                    return 1;
                }).then(CommandManager.literal("orbitalpull")
                        .executes(c->OrbitalRuntime.simulatePull(c.getSource().getPlayerOrThrow())))
                  .then(GameplayCommands.settings(GameplaySettings.current(),
                        (source,message)->source.sendFeedback(()->Text.literal(message),false)))));
        LOGGER.info("Great Sea: Unstable world type only; rounded square ring {}..{}, water Y={}, native ocean floor centered near Y={}",CONFIG.innerBoundary,CONFIG.outerBoundary,CONFIG.seaLevel,(CONFIG.minFloorY+CONFIG.maxFloorY)/2.0);
        CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->RegionCommands.register(dispatcher));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register(ForbiddenWorldBlocks::removeSavedVaults);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
            if(!UnstableMobSpawns.allow(world,entity))entity.discard();
        });
        if (Boolean.getBoolean("greatsea.verify")) ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> SeaVerifier.run(server)));
        if (Boolean.getBoolean("greatsea.verifyVanilla")) ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> SeaVerifier.runVanilla(server)));
        if (Boolean.getBoolean("greatsea.verifyCorners")) ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> SeaCornerVerifier.run(server)));
        if (Boolean.getBoolean("greatsea.verifyUnderworld")) ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> UnderworldVerifier.run(server)));
        if (Boolean.getBoolean("unstableterrain.verifyOrbitals")) ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> OrbitalsVerifier.run(server)));
        if (Boolean.getBoolean("unstableterrain.verifyFarlands")) ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> FarlandsVerifier.run(server)));
        if (Boolean.getBoolean("unstableterrain.verifyFarlandsCommand")) ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> FarlandsCommandVerifier.run(server)));
        if (Boolean.getBoolean("unstableterrain.verifySpokelandsCommands")) ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> SpokelandsCommandVerifier.run(server)));
        if (Boolean.getBoolean("unstableterrain.verifySpokelands")) ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> SpokelandsVerification.run(server)));
        if (Boolean.getBoolean("unstableterrain.verifyRevision")) ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> RevisionVerifier.run(server)));
        if (Boolean.getBoolean("unstableterrain.verifyEmpire")) ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> FabricEmpireVerifier.run(server)));
        if (Boolean.getBoolean("unstableterrain.verifyRevision127")) ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> Revision127Verifier.run(server)));
        if (Boolean.getBoolean("unstableterrain.verify1381")) ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> Revision1381Verifier.run(server)));
        if (Boolean.getBoolean("unstableterrain.verifyTerrain126")) ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> Terrain126Verifier.run(server)));
    }
}
