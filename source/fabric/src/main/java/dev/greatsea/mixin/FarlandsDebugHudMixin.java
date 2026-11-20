package dev.greatsea.mixin;

import dev.greatsea.FarlandsCoordinates;
import dev.greatsea.UnderworldClientCoordinates;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.util.ArrayList;
import java.util.List;

@Mixin(DebugHud.class)
public abstract class FarlandsDebugHudMixin {
    @ModifyVariable(method="drawText",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private List<String> greatsea$relativeCoordinates(List<String> original) {
        var player=MinecraftClient.getInstance().player;
        if(player==null)return original;
        boolean underworld=UnderworldClientCoordinates.active(player);
        if(!underworld && !FarlandsCoordinates.clientActive(player))return original;
        List<String> lines=new ArrayList<>(original.size());
        for(String line:original)lines.add(underworld?UnderworldClientCoordinates.line(line):FarlandsCoordinates.relativeClientLine(line));
        return lines;
    }
}
