package dev.greatsea;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Standalone-server cosmetic display; vanilla clients retain their native F3 coordinates. */
final class PaperUnderworldCoordinates {
    private final Map<UUID,Long> showing=new HashMap<>();
    void update(Player player,boolean underworld){
        if(!underworld){if(showing.remove(player.getUniqueId())!=null)player.sendActionBar(Component.empty());return;}
        // Ten updates per second; no metadata/teleport packets or physical-coordinate changes.
        long frame=UnderworldCoordinateText.animationFrame(System.nanoTime()),slot=Math.floorDiv(frame,2);
        var previous=showing.put(player.getUniqueId(),slot);if(previous!=null && previous==slot)return;
        player.sendActionBar(Component.text(UnderworldCoordinateText.actionBar(frame),NamedTextColor.LIGHT_PURPLE));
    }
    void forget(UUID player){showing.remove(player);}
    void clear(){showing.clear();}
}
