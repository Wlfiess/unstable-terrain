package dev.greatsea;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;

/** Region-local building rules, including the automatic Adventure mode. */
public final class UnderworldProtection {
    private static final RegistryKey<DimensionType> DIMENSION=RegistryKey.of(RegistryKeys.DIMENSION_TYPE,UnstableNether.ID);
    private UnderworldProtection() {}
    public static boolean restricted(boolean unstableNether,boolean survival,double y) {
        return unstableNether && survival && y<UnstableNether.UNDERWORLD_TOP;
    }
    public static boolean protectedPlayer(PlayerEntity player) {
        if (player==null || !GameplaySettings.Rule.UNDERWORLD_ADVENTURE.enabled()) return false;
        World world=player.getEntityWorld();
        return restricted(world instanceof ServerWorld && world.getRegistryKey().equals(World.NETHER) && world.getDimensionEntry().matchesKey(DIMENSION),
                player.getGameMode()==GameMode.SURVIVAL || player.getGameMode()==GameMode.ADVENTURE,player.getY());
    }
    public static boolean changesBlocks(Item item) {
        return item instanceof BlockItem || item instanceof BucketItem || item instanceof BoneMealItem ||
                item instanceof FlintAndSteelItem || item instanceof FireChargeItem || item instanceof AxeItem ||
                item instanceof HoeItem || item instanceof ShovelItem || item instanceof HoneycombItem ||
                item instanceof ShearsItem || item instanceof BrushItem;
    }
    public static boolean changesBlockActivation(ItemStack stack,Block block) {
        // These blocks consume placement/mutation items before ItemStack.useOnBlock.
        // Chests, buttons and other ordinary activations keep their vanilla path.
        return (!stack.isEmpty() && block instanceof AbstractCauldronBlock) ||
                (changesBlocks(stack.getItem()) && (block instanceof PumpkinBlock ||
                        block instanceof AbstractCandleBlock || block instanceof CakeBlock ||
                        block instanceof BeehiveBlock || block instanceof FlowerPotBlock ||
                        block instanceof RespawnAnchorBlock || block instanceof ComposterBlock));
    }
}
