package dev.greatsea;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;

/** Server-authoritative Adventure mode while a Survival player is in the Underworld. */
public final class UnderworldGameMode {
    private UnderworldGameMode(){}
    public record Transition(GameMode target,boolean automatic){}
    static Transition transition(boolean inside,GameMode current,boolean automatic){
        if(inside && current==GameMode.SURVIVAL)return new Transition(GameMode.ADVENTURE,true);
        if(automatic && current==GameMode.ADVENTURE){
            return inside?new Transition(null,true):new Transition(GameMode.SURVIVAL,false);
        }
        return new Transition(null,false);
    }
    public static void initialize(){
        ServerTickEvents.END_SERVER_TICK.register(server->{
            for(var player:server.getPlayerManager().getPlayerList())update(player);
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->update(handler.player));
    }
    public static void update(ServerPlayerEntity player){
        var data=(UnderworldGameModeData)player;
        boolean inside=GameplaySettings.Rule.UNDERWORLD_ADVENTURE.enabled()
                && UnstableNether.supported(player.getEntityWorld()) && player.getY()<UnstableNether.UNDERWORLD_TOP;
        var transition=transition(inside,player.getGameMode(),data.greatsea$underworldAdventure());
        if(transition.target()!=null && !player.changeGameMode(transition.target()))return;
        data.greatsea$underworldAdventure(transition.automatic());
    }
}
