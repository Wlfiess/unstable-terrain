package dev.greatsea;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.VaultBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

/** Generation/placement sanitization and removal of saved ominous vaults on load. */
public final class ForbiddenWorldBlocks {
    public static final java.util.concurrent.atomic.AtomicLong ancientReplacements=new java.util.concurrent.atomic.AtomicLong();
    private ForbiddenWorldBlocks() {}
    public static boolean ominousVault(BlockState state){return state.isOf(Blocks.VAULT) && state.get(VaultBlock.OMINOUS);}
    public static boolean allowsOminous(ServerWorld world,int x,int z){
        return world.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD) && allowsOminous(x,z);
    }
    public static boolean allowsOminous(int x,int z){
        return FarlandsWorld.contains(x,z) && !SpokelandsTerrain.contains(x,z) && FarlandsWorld.depth(x,z)>=FarlandsTerrain.start(FarlandsTerrain.Stage.CORRUPTED);
    }
    public static BlockState generated(ServerWorld world,BlockPos pos,BlockState state){
        if(MistRegion.active(world)&&MistRegion.frost(world.getSeed(),pos.getX(),pos.getZ()))state=MistTerrain.frozen(state);
        if(GeneratedIcePolicy.isIce(state)&&!(MistRegion.active(world)&&MistRegion.contains(pos.getX(),pos.getZ())))return GeneratedIcePolicy.generated(state,false,pos.getY());
        if(state.isOf(Blocks.ANCIENT_DEBRIS)){ancientReplacements.incrementAndGet();return Blocks.AIR.getDefaultState();}
        return ominousVault(state) && !allowsOminous(world,pos.getX(),pos.getZ())?Blocks.AIR.getDefaultState():state;
    }
    public static void removeSavedVaults(ServerWorld world,WorldChunk chunk) {
        clean(world,chunk,false);
    }
    public static void removeGenerated(ServerWorld world,WorldChunk chunk) {
        clean(world,chunk,true);
        GeneratedIcePolicy.cleanGenerated(world,chunk);
    }
    private static void clean(ServerWorld world,WorldChunk chunk,boolean generated) {
        if(!UnstableMobSpawns.supported(world))return;
        var sections=chunk.getSectionArray();var pos=new BlockPos.Mutable();
        for(int i=0;i<sections.length;i++) {
            var section=sections[i];if(!section.hasAny(s->ominousVault(s) || generated && s.isOf(Blocks.ANCIENT_DEBRIS)))continue;
            int bottom=chunk.getBottomY()+i*16;
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)if(ominousVault(section.getBlockState(x,y,z)) && !allowsOminous(world,chunk.getPos().getStartX()+x,chunk.getPos().getStartZ()+z) || generated && section.getBlockState(x,y,z).isOf(Blocks.ANCIENT_DEBRIS)) {
                pos.set(chunk.getPos().getStartX()+x,bottom+y,chunk.getPos().getStartZ()+z);
                // Generation conversion precedes registration in the world's chunk
                // manager, so update this chunk directly instead of requesting it.
                if(generated)chunk.setBlockState(pos,Blocks.AIR.getDefaultState(),3);
                else world.setBlockState(pos,Blocks.AIR.getDefaultState(),3);
            }
        }
    }
}
