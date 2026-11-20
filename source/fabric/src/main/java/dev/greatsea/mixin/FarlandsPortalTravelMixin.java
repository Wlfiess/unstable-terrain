package dev.greatsea.mixin;

import dev.greatsea.OrbitalRuntime;
import net.minecraft.block.BlockState;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NetherPortalBlock.class)
public abstract class FarlandsPortalTravelMixin {
    private static boolean blocked(World world,Entity entity,BlockPos pos) {
        return world instanceof ServerWorld server && (OrbitalRuntime.blocksPortal(server,pos.getX(),pos.getZ())
                ||OrbitalRuntime.blocksPortal(server,entity.getBlockX(),entity.getBlockZ()));
    }
    @Inject(method="onEntityCollision",at=@At("HEAD"),cancellable=true)
    private void unstableterrain$denyEntry(BlockState state,World world,BlockPos pos,Entity entity,EntityCollisionHandler handler,boolean moved,CallbackInfo ci) {
        if(blocked(world,entity,pos))ci.cancel();
    }
    @Inject(method="createTeleportTarget",at=@At("HEAD"),cancellable=true)
    private void unstableterrain$denyTravel(ServerWorld world,Entity entity,BlockPos pos,CallbackInfoReturnable<TeleportTarget> cir) {
        if(blocked(world,entity,pos))cir.setReturnValue(null);
    }
    @Inject(method="createTeleportTarget",at=@At("RETURN"),cancellable=true)
    private void unstableterrain$denyArrival(ServerWorld world,Entity entity,BlockPos pos,CallbackInfoReturnable<TeleportTarget> cir){
        var target=cir.getReturnValue();
        if(target==null)return;
        var destination=BlockPos.ofFloored(target.position());
        if(OrbitalRuntime.blocksPortal(target.world(),destination.getX(),destination.getZ()))cir.setReturnValue(null);
    }
}
