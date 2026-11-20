package dev.greatsea.mixin;

import dev.greatsea.FarlandsData;
import dev.greatsea.FarlandsWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.inventory.StackReference;
import net.minecraft.inventory.StackWithSlot;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla EnderItems remains untouched; both inventories are serialized on every save. */
@Mixin(PlayerEntity.class)
public abstract class FarlandsPlayerDataMixin implements FarlandsData {
    @Unique private final EnderChestInventory greatsea$farlandsChest=new EnderChestInventory();
    @Unique private static final String greatsea$CHEST_KEY="unstableterrain.FarlandsEnderItems";

    @Override public EnderChestInventory greatsea$farlandsEnderChest() { return greatsea$farlandsChest; }
    @Override public void greatsea$copyFarlandsData(FarlandsData source) {
        var inventory=source.greatsea$farlandsEnderChest();
        for (int slot=0;slot<inventory.size();slot++) greatsea$farlandsChest.setStack(slot,inventory.getStack(slot).copy());
    }
    @Inject(method="getEnderChestInventory",at=@At("HEAD"),cancellable=true)
    private void greatsea$activeEnderChest(CallbackInfoReturnable<EnderChestInventory> cir) {
        if (FarlandsWorld.activePlayer((PlayerEntity)(Object)this)) cir.setReturnValue(greatsea$farlandsChest);
    }
    @Inject(method="getStackReference",at=@At("HEAD"),cancellable=true)
    private void greatsea$activeEnderSlot(int mappedIndex,CallbackInfoReturnable<StackReference> cir) {
        int slot=mappedIndex-PlayerEntity.ENDER_SLOT_OFFSET;
        if (slot>=0 && slot<greatsea$farlandsChest.size() &&
                FarlandsWorld.activePlayer((PlayerEntity)(Object)this))
            cir.setReturnValue(greatsea$farlandsChest.getStackReference(slot));
    }
    @Inject(method="readCustomData",at=@At("RETURN"))
    private void greatsea$loadFarlandsData(ReadView view,CallbackInfo ci) {
        greatsea$farlandsChest.readData(view.getTypedListView(greatsea$CHEST_KEY,StackWithSlot.CODEC));
    }
    @Inject(method="writeCustomData",at=@At("RETURN"))
    private void greatsea$saveFarlandsData(WriteView view,CallbackInfo ci) {
        greatsea$farlandsChest.writeData(view.getListAppender(greatsea$CHEST_KEY,StackWithSlot.CODEC));
    }
}
