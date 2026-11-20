package dev.greatsea;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

public final class MistClient {
 private static volatile MistNetwork.Payload state=new MistNetwork.Payload(0,0,false,false);
 private MistClient(){}
 public static void initialize(){
  if(Boolean.getBoolean("unstableterrain.vanillaMistClient"))return;
  ClientPlayNetworking.registerGlobalReceiver(MistNetwork.Payload.ID,(p,c)->c.client().execute(()->state=p));
  ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->state=new MistNetwork.Payload(0,0,false,false));
  ClientTickEvents.END_CLIENT_TICK.register(c->{if(active(c)&&state.snow()>0){c.world.setRainGradient(Math.min(state.snow(),(float)MistRegion.strength(c.player.getX(),c.player.getZ())));c.world.setThunderGradient(0);}});
 }
 private static boolean active(MinecraftClient c){return c.world!=null&&c.player!=null&&c.world.getRegistryKey().equals(World.OVERWORLD);}
 public static float fog(){var c=MinecraftClient.getInstance();return active(c)&&!state.sheltered()?Math.min(state.fog(),(float)MistRegion.strength(c.player.getX(),c.player.getZ())):0;}
 public static boolean cave(){return state.cave();}
}
