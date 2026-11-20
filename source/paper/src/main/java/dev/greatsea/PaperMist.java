package dev.greatsea;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import java.util.*;
/** Vanilla clients use normal 64-block distance fog; modded clients receive the precise Mist renderer. */
final class PaperMist {
 private record Owned(int distance,WeatherType weather){}
 private static final Map<UUID,Owned> OWNED=new HashMap<>();
 private PaperMist(){}
 static void initialize(){PaperPlugin.instance.getServer().getMessenger().registerOutgoingPluginChannel(PaperPlugin.instance,MistNetwork.CHANNEL);}
 static void update(Player player){
  var handle=((CraftPlayer)player).getHandle();MistEffects.update(handle);var s=MistEffects.state(handle);var old=OWNED.get(player.getUniqueId());
  if(s.snow()<=0){if(old!=null)clear(player);return;}
  if(old==null){old=new Owned(player.getViewDistance(),player.getPlayerWeather());OWNED.put(player.getUniqueId(),old);}
  if(player.getPlayerWeather()!=WeatherType.DOWNFALL)player.setPlayerWeather(WeatherType.DOWNFALL);
  boolean precise=player.getListeningPluginChannels().contains(MistNetwork.CHANNEL);
  int distance=!precise&&s.fog()>.01?4:old.distance();
  if(player.getViewDistance()!=distance)player.setViewDistance(distance);
 }
 static void clear(Player p){
  var old=OWNED.remove(p.getUniqueId());if(old==null)return;
  if(p.getViewDistance()==4)p.setViewDistance(old.distance());
  if(old.weather()==null)p.resetPlayerWeather();else p.setPlayerWeather(old.weather());
 }
}
