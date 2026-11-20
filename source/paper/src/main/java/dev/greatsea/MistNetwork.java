package dev.greatsea;
import net.minecraft.server.level.ServerPlayer;
import java.nio.ByteBuffer;
public final class MistNetwork {
 public static final String CHANNEL="unstableterrain:mist";
 private MistNetwork(){}
 public static void send(ServerPlayer p,MistEffects.State s){
  var player=p.getBukkitEntity();if(!player.getListeningPluginChannels().contains(CHANNEL))return;
  byte[] data=ByteBuffer.allocate(10).putFloat(s.snow()).putFloat(s.fog()).put((byte)(s.sheltered()?1:0)).put((byte)(s.cave()?1:0)).array();
  player.sendPluginMessage(PaperPlugin.instance,CHANNEL,data);
 }
}
