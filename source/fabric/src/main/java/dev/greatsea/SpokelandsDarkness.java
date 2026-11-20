package dev.greatsea;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.Map;
import java.util.WeakHashMap;

/** Dense blindness fog belongs only to the negative-face Dark Forest. */
public final class SpokelandsDarkness {
    private static final Map<ServerPlayerEntity,StatusEffectInstance> OWNED=new WeakHashMap<>();
    private SpokelandsDarkness(){}
    public static boolean inside(boolean unstableOverworld,int x,int z){
        return unstableOverworld&&SpokelandsTerrain.contains(x,z)&&SpokelandsTerrain.region(x,z)==SpokelandsTerrain.Region.DARK_FOREST;
    }
    private static boolean ourEffect(StatusEffectInstance effect){
        return effect!=null&&effect.getAmplifier()==0&&effect.isAmbient()&&!effect.shouldShowParticles()&&!effect.shouldShowIcon()&&effect.getDuration()<=80;
    }
    public static void update(ServerPlayerEntity player){
        boolean inside=inside(FarlandsWorld.enabledOverworld(player.getEntityWorld()),player.getBlockX(),player.getBlockZ());
        var current=player.getStatusEffect(StatusEffects.BLINDNESS);
        var owned=OWNED.get(player);
        if(!inside){
            if(owned!=null&&current==owned&&ourEffect(current))player.removeStatusEffect(StatusEffects.BLINDNESS);
            OWNED.remove(player);return;
        }
        // Do not replace or clear a player's independently acquired potion effect.
        if(current!=null&&current!=owned){OWNED.remove(player);return;}
        if(current==null||ourEffect(current)&&current.getDuration()<40){
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS,80,0,true,false,false));
            OWNED.put(player,player.getStatusEffect(StatusEffects.BLINDNESS));
        }
    }
}
