package dev.greatsea;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.TreeConfiguredFeatures;
import net.minecraft.world.gen.feature.VegetationConfiguredFeatures;

/** Native trees and plant patches placed on the completed Farlands terrain. */
public final class FarlandsDecoration {
    public record TreeCandidate(int x,int z,long hash) {}
    private static final ThreadLocal<Integer> GENERATION_DEPTH=ThreadLocal.withInitial(()->0);
    private FarlandsDecoration() {}
    public static boolean isGenerating() { return GENERATION_DEPTH.get()>0; }

    static int treeSpacing(FarlandsTerrain.Stage stage) {
        return switch(stage) {
            case CHERRY -> 7;
            case SPLIT,CHUNKS,DECAY -> 8;
            case GRAVEL,GRAVEL_AFTER_SPIKES,SPIKES -> 16;
            default -> 10;
        };
    }
    static double treeChance(FarlandsTerrain.Stage stage) {
        return switch(stage) {
            case CHERRY -> .30;
            case SPLIT -> .85;
            case REPEATING -> 0;
            case DECAY -> .4;
            case CHUNKS -> .65;
            case FLOATING -> .45;
            case CORRUPTED -> 0;
            case GRAVEL,GRAVEL_AFTER_SPIKES -> .20;
            case PLAINS,ORBITALS,PLAINS_HILLS -> 0;
            case SPIKES -> 0;
            case SAND_DUNES,SAND_FARLANDS,WATER,SKYGRID -> 0;
        };
    }
    /** Retained for density probes. Runtime uses freely scattered chunk proposals below. */
    static TreeCandidate treeCandidate(long seed,int gx,int gz,int spacing) {
        long hash=SeaTerrain.hash(seed+9101,gx,gz);
        int x=gx*spacing+(int)(SeaTerrain.unit(SeaTerrain.hash(hash,1,0))*spacing);
        int z=gz*spacing+(int)(SeaTerrain.unit(SeaTerrain.hash(hash,0,1))*spacing);
        return new TreeCandidate(x,z,hash);
    }
    static TreeCandidate randomCandidate(long seed,int chunkX,int chunkZ,int index) {
        long hash=SeaTerrain.hash(seed+9107+index*0x632BE59BD9B4E019L,chunkX,chunkZ);
        return new TreeCandidate(chunkX*16+(int)(SeaTerrain.unit(SeaTerrain.hash(hash,3,0))*16),
                chunkZ*16+(int)(SeaTerrain.unit(SeaTerrain.hash(hash,0,3))*16),hash);
    }
    private static boolean selected(TreeCandidate c) {
        if(!FarlandsWorld.contains(c.x(),c.z()))return false;
        var stage=FarlandsTerrain.stage(c.x(),c.z());
        // Preserve the newer crown clearance only for the retained hill/dune joins.
        if(stage==FarlandsTerrain.Stage.PLAINS_HILLS||stage==FarlandsTerrain.Stage.SAND_DUNES)
            for(int dx:new int[]{-12,12})for(int dz:new int[]{-12,12})
                if(!FarlandsWorld.contains(c.x()+dx,c.z()+dz)||SpokelandsTerrain.contains(c.x()+dx,c.z()+dz)||FarlandsTerrain.stage(c.x()+dx,c.z()+dz)!=stage)return false;
        int spacing=treeSpacing(stage);
        return SeaTerrain.unit(c.hash())<treeChance(stage)*256/(spacing*spacing*16.0);
    }
    static boolean separatedCandidate(long seed,TreeCandidate candidate) {
        int cx=Math.floorDiv(candidate.x(),16),cz=Math.floorDiv(candidate.z(),16);
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)for(int i=0;i<16;i++) {
            var other=randomCandidate(seed,cx+dx,cz+dz,i);
            int distanceX=other.x()-candidate.x(),distanceZ=other.z()-candidate.z();
            if(distanceX*distanceX+distanceZ*distanceZ>=9 || other.equals(candidate) || !selected(other))continue;
            if(Long.compareUnsigned(other.hash(),candidate.hash())<0)return false;
        }
        return true;
    }
    /** Positive-route masks never suppress the independently decorated negative route. */
    public static boolean blocksFeatureWrite(long seed,int x,int z,boolean decorating,boolean placingStructure){
        if(!FarlandsWorld.contains(x,z)||placingStructure)return false;
        if(!decorating)return true;
        if(SpokelandsTerrain.contains(x,z))return false;
        var stage=FarlandsTerrain.stage(x,z);
        return FarlandsTerrain.missingChunk(seed,x,z)||stage==FarlandsTerrain.Stage.ORBITALS||stage==FarlandsTerrain.Stage.CORRUPTED||FarlandsTerrain.bareSplit(x,z);
    }
    public static void decorate(Chunk chunk,StructureWorldAccess world) {
        int previous=GENERATION_DEPTH.get();GENERATION_DEPTH.set(previous+1);
        try { decorateTerrain(chunk,world); }
        finally {
            if (previous==0) GENERATION_DEPTH.remove();else GENERATION_DEPTH.set(previous);
        }
    }
    private static void decorateTerrain(Chunk chunk,StructureWorldAccess world) {
        long seed=world.getSeed();
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();
        if(SpokelandsTerrain.contains(sx+8,sz+8)){SpokelandsDecoration.decorate(chunk,world);return;}
        if(FarlandsTerrain.stage(sx+8,sz+8)==FarlandsTerrain.Stage.FLOATING)floatingSprings(chunk,world);
        // Independent positions and random proposal counts avoid rows and cell centers.
        // A deterministic neighbour priority keeps nearby roots separated across chunks.
        for(int i=0;i<16;i++) {
                var candidate=randomCandidate(seed,chunk.getPos().x,chunk.getPos().z,i);
                int x=candidate.x(),z=candidate.z();
                var stage=FarlandsTerrain.stage(x,z);
                if (FarlandsTemple.footprint(x,z) || FarlandsTerrain.bareSplit(x,z) || !selected(candidate) || !separatedCandidate(seed,candidate)) continue;
                var column=FarlandsTerrain.column(seed,x,z);
                if (FarlandsTemple.footprint(x,z) || column.voidColumn() || column.top()+24>=world.getTopYInclusive()) continue;
                int ground=stage==FarlandsTerrain.Stage.FLOATING?floatingSurface(world,x,z,candidate.hash(),24):column.top();
                if(ground<world.getBottomY())continue;
                BlockPos root=new BlockPos(x,ground+1,z);
                if (!stableTreeRoot(world,seed,root)) continue;
                RegistryEntry<Biome> biome=world.getBiome(root);
                var feature=treeFeature(stage,biome,candidate.hash());
                if (feature!=null) generate(world,feature,candidate.hash(),root);
        }
        // Patch roots stay in the current chunk. The native feature validates every plant
        // against the actual block below it, so void gaps and water are never planted.
        for (int i=0;i<12;i++) {
            long hash=SeaTerrain.hash(seed+9131,sx+i*31L,sz-i*17L);
            int x=sx+(int)(SeaTerrain.unit(SeaTerrain.hash(hash,3,1))*16);
            int z=sz+(int)(SeaTerrain.unit(SeaTerrain.hash(hash,1,3))*16);
            var stage=FarlandsTerrain.stage(x,z);
            if (FarlandsTerrain.bareSplit(x,z) || stage==FarlandsTerrain.Stage.CORRUPTED || stage==FarlandsTerrain.Stage.PLAINS || stage==FarlandsTerrain.Stage.ORBITALS || stage==FarlandsTerrain.Stage.GRAVEL || stage==FarlandsTerrain.Stage.GRAVEL_AFTER_SPIKES || stage==null || (FarlandsTerrain.nativeStage(stage) && stage!=FarlandsTerrain.Stage.CHERRY) || stage==FarlandsTerrain.Stage.WATER || stage==FarlandsTerrain.Stage.SKYGRID || stage==FarlandsTerrain.Stage.SAND_FARLANDS) continue;
            if(stage==FarlandsTerrain.Stage.CHERRY && i>=2)continue;
            // Dunes use bounded individual placements below. Native patch
            // features scatter dozens of attempts outside this proposal.
            if(stage==FarlandsTerrain.Stage.SAND_DUNES)continue;
            var column=FarlandsTerrain.column(seed,x,z);
            if (FarlandsTemple.footprint(x,z) || column.voidColumn() || column.top()+4>=world.getTopYInclusive()) continue;
            int ground=stage==FarlandsTerrain.Stage.FLOATING?floatingSurface(world,x,z,hash,4):column.top();
            if(ground<world.getBottomY())continue;
            BlockPos root=new BlockPos(x,ground+1,z);
            BlockState soil=world.getBlockState(root.down());
            if (!world.getBlockState(root).isAir()) continue;
            if (soil.isOf(Blocks.SAND)) {
                if (stage==FarlandsTerrain.Stage.SAND_DUNES || stage==FarlandsTerrain.Stage.SAND_FARLANDS || stage==FarlandsTerrain.Stage.FLOATING || stage==FarlandsTerrain.Stage.CHUNKS || stage==FarlandsTerrain.Stage.SPLIT)
                    { if(stage!=FarlandsTerrain.Stage.SAND_DUNES || i%4!=0) generate(world,i%4==0 ? VegetationConfiguredFeatures.PATCH_CACTUS : VegetationConfiguredFeatures.PATCH_DEAD_BUSH,hash,root); }
            } else if (treeSoil(soil) && !FarlandsTerrain.bareSplit(x,z)) {
                var biome=world.getBiome(root);
                RegistryKey<ConfiguredFeature<?,?>> feature;
                if (stage==FarlandsTerrain.Stage.CHERRY || biome.matchesKey(BiomeKeys.CHERRY_GROVE))
                    feature=VegetationConfiguredFeatures.FLOWER_CHERRY;
                else if (taiga(biome))
                    feature=i%4==0 ? VegetationConfiguredFeatures.PATCH_BERRY_BUSH : VegetationConfiguredFeatures.PATCH_TAIGA_GRASS;
                else if(biome.matchesKey(BiomeKeys.JUNGLE) || biome.matchesKey(BiomeKeys.BAMBOO_JUNGLE) || biome.matchesKey(BiomeKeys.SPARSE_JUNGLE))
                    feature=i%5==0?VegetationConfiguredFeatures.PATCH_MELON:i%4==0?VegetationConfiguredFeatures.BAMBOO_NO_PODZOL:VegetationConfiguredFeatures.PATCH_GRASS_JUNGLE;
                else if(biome.matchesKey(BiomeKeys.FLOWER_FOREST))
                    feature=i%3==0?VegetationConfiguredFeatures.FLOWER_FLOWER_FOREST:VegetationConfiguredFeatures.PATCH_GRASS;
                else if(biome.matchesKey(BiomeKeys.MEADOW))
                    feature=i%3==0?VegetationConfiguredFeatures.FLOWER_MEADOW:VegetationConfiguredFeatures.PATCH_GRASS_MEADOW;
                else if(biome.matchesKey(BiomeKeys.MUSHROOM_FIELDS))
                    feature=i%2==0?VegetationConfiguredFeatures.PATCH_BROWN_MUSHROOM:VegetationConfiguredFeatures.PATCH_RED_MUSHROOM;
                else feature=i%4==0 ? VegetationConfiguredFeatures.FLOWER_PLAIN : VegetationConfiguredFeatures.PATCH_GRASS;
                generate(world,feature,hash,root);
            }
        }
        decorateDunes(world,chunk.getPos().x,chunk.getPos().z);
        // Snow-bearing biome skins keep real soil beneath a native snow layer.
        // Place snow last so configured spruce trees can replace the layer normally.
        for (int dz=0;dz<16;dz++) for (int dx=0;dx<16;dx++) {
            int x=sx+dx,z=sz+dz;
            var stage=FarlandsTerrain.stage(x,z);
            if (FarlandsTemple.footprint(x,z) || FarlandsTerrain.bareSplit(x,z) || stage==null || FarlandsTerrain.nativeStage(stage) || stage==FarlandsTerrain.Stage.SPIKES ) continue;
            if(stage==FarlandsTerrain.Stage.FLOATING && FarlandsBiomes.skin(seed,x,z)==7) {
                for(int y=world.getBottomY();y<Math.min(220,world.getTopYInclusive());y++) {
                    BlockPos pos=new BlockPos(x,y+1,z);BlockState snow=Blocks.SNOW.getDefaultState();
                    if(treeSoil(world.getBlockState(pos.down())) && world.getBlockState(pos).isAir() && snow.canPlaceAt(world,pos))world.setBlockState(pos,snow,2);
                }
                continue;
            }
            var column=FarlandsTerrain.column(seed,x,z);
            if (column.voidColumn() || column.material()!=7 || column.top()+1>=world.getTopYInclusive()) continue;
            BlockPos pos=new BlockPos(x,column.top()+1,z);
            BlockState snow=Blocks.SNOW.getDefaultState();
            if (world.getBlockState(pos).isAir() && snow.canPlaceAt(world,pos)) world.setBlockState(pos,snow,2);
        }
    }
    /** At most one cactus and two bushes per chunk, with independent omissions. */
    static boolean dunePlantSelected(long seed,int chunkX,int chunkZ,int slot){
        long hash=SeaTerrain.hash(seed+9141+slot*0x632BE59BD9B4E019L,chunkX,chunkZ);
        return SeaTerrain.unit(hash)<(slot==0?.25:slot==1?.65:.25);
    }
    record DunePlantCandidate(int x,int z,long hash){}
    static DunePlantCandidate dunePlantCandidate(long seed,int chunkX,int chunkZ,int slot,int attempt){
        long hash=SeaTerrain.hash(seed+9143+slot*37,chunkX,chunkZ);
        // 53 is coprime to 14*14. This visits every inset column once, in a
        // different order per chunk, including the rare naturally clear crests.
        int cell=(int)Math.floorMod((hash&0x7fffffffL)+attempt*53L,196);
        return new DunePlantCandidate(chunkX*16+1+cell%14,chunkZ*16+1+cell/14,SeaTerrain.hash(hash,attempt,17));
    }
    private static void decorateDunes(StructureWorldAccess world,int chunkX,int chunkZ){
        long seed=world.getSeed();
        for(int slot=0;slot<3;slot++){
            if(!dunePlantSelected(seed,chunkX,chunkZ,slot))continue;
            var state=(slot==0?Blocks.CACTUS:Blocks.DEAD_BUSH).getDefaultState();
            for(int attempt=0;attempt<(slot==0?196:12);attempt++){
                var candidate=dunePlantCandidate(seed,chunkX,chunkZ,slot,attempt);
                long hash=candidate.hash();int x=candidate.x(),z=candidate.z();
                if(FarlandsTerrain.stage(x,z)!=FarlandsTerrain.Stage.SAND_DUNES)continue;
                int top=FarlandsTerrain.column(seed,x,z).top();var root=new BlockPos(x,top+1,z);
                if(root.getY()>world.getTopYInclusive()||!world.getBlockState(root.down()).isOf(Blocks.SAND)||!world.getBlockState(root).isAir()||!state.canPlaceAt(world,root))continue;
                world.setBlockState(root,state,2);
                if(slot==0){
                    int height=1+(int)(SeaTerrain.unit(SeaTerrain.hash(hash,3,4))*3);
                    for(int dy=1;dy<height&&top+1+dy<=world.getTopYInclusive();dy++){
                        var at=root.up(dy);if(!world.getBlockState(at).isAir()||!state.canPlaceAt(world,at))break;
                        world.setBlockState(at,state,2);
                    }
                }
                break;
            }
        }
    }
    private static boolean treeSoil(BlockState state) {
        return state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.DIRT) || state.isOf(Blocks.COARSE_DIRT) || state.isOf(Blocks.PODZOL) || state.isOf(Blocks.MYCELIUM);
    }
    private static boolean stableTreeRoot(StructureWorldAccess world,long seed,BlockPos root) {
        if (!treeSoil(world.getBlockState(root.down()))) return false;
        BlockState above=world.getBlockState(root);
        if (!above.isAir() && !above.isOf(Blocks.SNOW) && !above.isOf(Blocks.SHORT_GRASS) && !above.isOf(Blocks.FERN) && !above.isOf(Blocks.PINK_PETALS)) return false;
        int ground=root.getY()-1;
        for (int dz=-1;dz<=1;dz++) for (int dx=-1;dx<=1;dx++) {
            if(FarlandsTerrain.stage(root.getX(),root.getZ())==FarlandsTerrain.Stage.FLOATING) {
                boolean support=false;
                for(int dy=-3;dy<=3;dy++) {
                    var pos=new BlockPos(root.getX()+dx,ground+dy,root.getZ()+dz);
                    if(treeSoil(world.getBlockState(pos)) && world.getBlockState(pos.up()).isAir()){support=true;break;}
                }
                if(!support)return false;
            }else {
                var neighbour=FarlandsTerrain.column(seed,root.getX()+dx,root.getZ()+dz);
                if (neighbour.voidColumn() || Math.abs(neighbour.top()-ground)>3) return false;
            }
        }
        return true;
    }
    /** Reservoir selection plants the lower islands too, rather than only the tallest roof. */
    private static int floatingSurface(StructureWorldAccess world,int x,int z,long hash,int headroom) {
        int count=0,chosen=world.getBottomY()-1;
        for(int y=world.getBottomY();y<Math.min(220,world.getTopYInclusive()-headroom);y++) {
            var soil=new BlockPos(x,y,z);
            var above=world.getBlockState(soil.up());
            if(!treeSoil(world.getBlockState(soil)) || (!above.isAir() && !above.isOf(Blocks.SNOW) && !above.isOf(Blocks.SHORT_GRASS)))continue;
            if(SeaTerrain.unit(SeaTerrain.hash(hash,y,9))*++count<1)chosen=y;
        }
        return chosen;
    }
    private static void floatingSprings(Chunk chunk,StructureWorldAccess world){
        long hash=SeaTerrain.hash(world.getSeed()+9157,chunk.getPos().x,chunk.getPos().z);
        double chance=SeaTerrain.unit(hash);if(chance>=.15)return;
        boolean lava=chance<.03;
        var registry=world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
        var trial=registry.getValueOrThrow(net.minecraft.world.gen.structure.StructureKeys.TRIAL_CHAMBERS);
        var section=net.minecraft.util.math.ChunkSectionPos.from(chunk.getPos(),world.getBottomSectionCoord());
        var chambers=new java.util.ArrayList<net.minecraft.structure.StructureStart>();
        var own=chunk.getStructureStart(trial);if(own!=null&&own.hasChildren())chambers.add(own);
        var references=chunk.getStructureReferences().get(trial);if(references!=null)for(long reference:references){var origin=new net.minecraft.util.math.ChunkPos(reference);var start=world.getChunk(origin.x,origin.z).getStructureStart(trial);if(start!=null&&start.hasChildren())chambers.add(start);}
        for(int i=0;i<8;i++){
            long h=SeaTerrain.hash(hash,i,17);var root=new BlockPos(chunk.getPos().getStartX()+1+(int)(SeaTerrain.unit(SeaTerrain.hash(h,1,2))*14),12+(int)(SeaTerrain.unit(SeaTerrain.hash(h,3,4))*224),chunk.getPos().getStartZ()+1+(int)(SeaTerrain.unit(SeaTerrain.hash(h,5,6))*14));
            if(!world.getBlockState(root).isOf(Blocks.STONE))continue;
            boolean protectedChamber=false;for(var chamber:chambers)if(chamber.getBoundingBox().expand(8).contains(root)){protectedChamber=true;break;}
            if(protectedChamber)continue;
            if(generate(world,lava?net.minecraft.world.gen.feature.MiscConfiguredFeatures.SPRING_LAVA_OVERWORLD:net.minecraft.world.gen.feature.MiscConfiguredFeatures.SPRING_WATER,h,root))break;
        }
    }
    private static boolean taiga(RegistryEntry<Biome> biome) {
        return biome.matchesKey(BiomeKeys.TAIGA) || biome.matchesKey(BiomeKeys.SNOWY_TAIGA) ||
                biome.matchesKey(BiomeKeys.OLD_GROWTH_PINE_TAIGA) || biome.matchesKey(BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA);
    }
    private static RegistryKey<ConfiguredFeature<?,?>> treeFeature(FarlandsTerrain.Stage stage,RegistryEntry<Biome> biome,long hash) {
        if (stage==FarlandsTerrain.Stage.CHERRY || biome.matchesKey(BiomeKeys.CHERRY_GROVE)) return TreeConfiguredFeatures.CHERRY;
        if(biome.matchesKey(BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA))return VegetationConfiguredFeatures.TREES_OLD_GROWTH_SPRUCE_TAIGA;
        if(biome.matchesKey(BiomeKeys.OLD_GROWTH_PINE_TAIGA))return VegetationConfiguredFeatures.TREES_OLD_GROWTH_PINE_TAIGA;
        if (taiga(biome) || biome.matchesKey(BiomeKeys.SNOWY_PLAINS)) return TreeConfiguredFeatures.SPRUCE;
        if (biome.matchesKey(BiomeKeys.DESERT) || biome.matchesKey(BiomeKeys.BADLANDS) ||
                biome.matchesKey(BiomeKeys.ERODED_BADLANDS) || biome.matchesKey(BiomeKeys.ICE_SPIKES)) return null;
        if (biome.matchesKey(BiomeKeys.BIRCH_FOREST) || biome.matchesKey(BiomeKeys.OLD_GROWTH_BIRCH_FOREST)) return TreeConfiguredFeatures.BIRCH;
        if(biome.matchesKey(BiomeKeys.FLOWER_FOREST))return VegetationConfiguredFeatures.TREES_FLOWER_FOREST;
        if(biome.matchesKey(BiomeKeys.MEADOW))return VegetationConfiguredFeatures.MEADOW_TREES;
        if(biome.matchesKey(BiomeKeys.SAVANNA) || biome.matchesKey(BiomeKeys.SAVANNA_PLATEAU) || biome.matchesKey(BiomeKeys.WINDSWEPT_SAVANNA))return TreeConfiguredFeatures.ACACIA;
        if(biome.matchesKey(BiomeKeys.JUNGLE) || biome.matchesKey(BiomeKeys.BAMBOO_JUNGLE))return VegetationConfiguredFeatures.TREES_JUNGLE;
        if(biome.matchesKey(BiomeKeys.SPARSE_JUNGLE))return VegetationConfiguredFeatures.TREES_SPARSE_JUNGLE;
        if(biome.matchesKey(BiomeKeys.DARK_FOREST))return TreeConfiguredFeatures.DARK_OAK;
        if(biome.matchesKey(BiomeKeys.MUSHROOM_FIELDS))return (hash&1)==0?TreeConfiguredFeatures.HUGE_RED_MUSHROOM:TreeConfiguredFeatures.HUGE_BROWN_MUSHROOM;
        return biome.matchesKey(BiomeKeys.FOREST)?VegetationConfiguredFeatures.TREES_BIRCH_AND_OAK:TreeConfiguredFeatures.OAK;
    }
    /** Replay successful native feature writes, rather than generating a new random tree below. */
    static void repeatSplitDecoration(StructureWorldAccess world,java.util.Map<BlockPos,BlockState> writes){
        for(var entry:writes.entrySet()){
            var source=entry.getKey();var state=entry.getValue();
            if(source.getY()<145||SpokelandsTerrain.contains(source.getX(),source.getZ())||FarlandsTerrain.stage(source.getX(),source.getZ())!=FarlandsTerrain.Stage.SPLIT)continue;
            boolean tree=state.isIn(net.minecraft.registry.tag.BlockTags.LOGS)||state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES);
            for(int y=source.getY()-51;y>=world.getBottomY();y-=51){
                var at=new BlockPos(source.getX(),y,source.getZ());
                if(FarlandsTemple.contains(at.getX(),y,at.getZ()))continue;
                // Make room immediately around a copied crown/trunk under an overhang,
                // keeping soil and the walking surfaces above intact.
                if(tree)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                    if(dx==0&&dz==0)continue;var air=at.add(dx,0,dz);var old=world.getBlockState(air);
                    if(old.isOf(Blocks.STONE)||old.isOf(Blocks.DEEPSLATE))world.setBlockState(air,Blocks.AIR.getDefaultState(),2);
                }
                world.setBlockState(at,state,2);
            }
        }
    }
    private static boolean generate(StructureWorldAccess world,RegistryKey<ConfiguredFeature<?,?>> key,long hash,BlockPos root) {
        var feature=world.getRegistryManager().getOrThrow(RegistryKeys.CONFIGURED_FEATURE).getOrThrow(key).value();
        if(SpokelandsTerrain.contains(root.getX(),root.getZ())||FarlandsTerrain.stage(root.getX(),root.getZ())!=FarlandsTerrain.Stage.SPLIT)
            return feature.generate(world,world.toServerWorld().getChunkManager().getChunkGenerator(),Random.create(hash),root);
        var writes=new java.util.LinkedHashMap<BlockPos,BlockState>();
        var captured=(StructureWorldAccess)java.lang.reflect.Proxy.newProxyInstance(FarlandsDecoration.class.getClassLoader(),new Class[]{StructureWorldAccess.class},(proxy,method,args)->{
            boolean write=args!=null&&args.length>=3&&args[0] instanceof BlockPos&&args[1] instanceof BlockState;
            if(write){
                var pos=(BlockPos)args[0];var state=(BlockState)args[1];
                if(FarlandsTemple.footprint(pos.getX(),pos.getZ()))return false;
                if(state.contains(net.minecraft.state.property.Properties.PERSISTENT)){
                    state=state.with(net.minecraft.state.property.Properties.PERSISTENT,true);args=args.clone();args[1]=state;
                }
            }
            Object result;try{result=method.invoke(world,args);}catch(java.lang.reflect.InvocationTargetException ex){throw ex.getCause();}
            if(write&&Boolean.TRUE.equals(result))writes.put(((BlockPos)args[0]).toImmutable(),(BlockState)args[1]);
            return result;
        });
        boolean result=feature.generate(captured,world.toServerWorld().getChunkManager().getChunkGenerator(),Random.create(hash),root);
        repeatSplitDecoration(world,writes);return result;
    }
}
