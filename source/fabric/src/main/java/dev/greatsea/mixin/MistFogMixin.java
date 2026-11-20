package dev.greatsea.mixin;
import dev.greatsea.*;
import net.minecraft.client.render.fog.*;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.sugar.Local;
@Mixin(FogRenderer.class)
public abstract class MistFogMixin {
 // Change the actual FogData before both vanilla GPU upload and Sodium's local capture.
 @Inject(method="applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderSystem;getDevice()Lcom/mojang/blaze3d/systems/GpuDevice;"))
 private void mist$fog(net.minecraft.client.render.Camera camera,int distance,net.minecraft.client.render.RenderTickCounter ticks,float sky,net.minecraft.client.world.ClientWorld world,CallbackInfoReturnable<Vector4f> cir,@Local FogData data,@Local Vector4f color){
  float s=MistClient.fog();if(s<=0)return;
  var client=net.minecraft.client.MinecraftClient.getInstance();
  if(camera.getSubmersionType()!=net.minecraft.block.enums.CameraSubmersionType.NONE)return;
  if(client.player!=null&&(client.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS)||client.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.DARKNESS)))return;
  data.environmentalStart=MistFogProfile.start(data.environmentalStart,s);
  data.environmentalEnd=MistFogProfile.end(data.environmentalEnd,s);
  data.renderDistanceStart=MistFogProfile.start(data.renderDistanceStart,s);
  data.renderDistanceEnd=MistFogProfile.end(data.renderDistanceEnd,s);
  data.skyEnd=Math.min(data.skyEnd,data.environmentalEnd);data.cloudEnd=Math.min(data.cloudEnd,data.environmentalEnd);
  float light=Math.min(1,.12f+.6f*(color.x+color.y+color.z));if(MistClient.cave())light*=.4f;
  color.set(color.x+(.64f*light-color.x)*s,color.y+(.70f*light-color.y)*s,color.z+(.78f*light-color.z)*s,color.w);
 }
}
