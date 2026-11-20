package dev.greatsea;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** A portable, north-facing redstone launcher pattern. */
final class PearlLauncher {
    static final BlockPos BUTTON=new BlockPos(5,1,3),
            BARREL=new BlockPos(1,1,8), REPEATER=new BlockPos(5,1,5),
            LAMP=new BlockPos(5,1,6), DISPENSER=new BlockPos(5,1,8),
            PAD=new BlockPos(5,1,2);

    private PearlLauncher() {}

    static boolean matches(ServerWorld world,BlockPos origin) {
        return world.getBlockState(origin.add(BUTTON)).isOf(Blocks.POLISHED_BLACKSTONE_BUTTON)
                && world.getBlockState(origin.add(BARREL)).isOf(Blocks.BARREL)
                && world.getBlockState(origin.add(REPEATER)).isOf(Blocks.REPEATER)
                && world.getBlockState(origin.add(LAMP)).isOf(Blocks.REDSTONE_LAMP)
                && world.getBlockState(origin.add(DISPENSER)).isOf(Blocks.DISPENSER)
                && world.getBlockState(origin.add(PAD).down()).isOf(Blocks.GOLD_BLOCK);
    }
}
