package dev.greatsea;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Native Paper generation adapter around the shared Fabric terrain engine. */
public final class PaperGenerator extends ChunkGenerator {
    final boolean nether;
    final ServerLevel level;
    final NoiseBasedChunkGenerator vanilla,normal,amplified;
    final RandomState amplifiedNoise;
    PaperGenerator(ServerLevel level,NoiseBasedChunkGenerator vanilla,boolean nether){
        super(new PaperBiomes(vanilla.getBiomeSource(),level,nether));
        this.level=level;this.nether=nether;this.vanilla=vanilla;
        normal=new NoiseBasedChunkGenerator(getBiomeSource(),vanilla.generatorSettings());
        if(nether){amplified=null;amplifiedNoise=null;}
        else{
            var settings=level.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.AMPLIFIED);
            amplified=new NoiseBasedChunkGenerator(getBiomeSource(),settings);
            amplifiedNoise=RandomState.create(settings.value(),level.registryAccess().lookupOrThrow(Registries.NOISE),level.getSeed());
            AmplifiedTerrain.blendNoise(level.getChunkSource().randomState(),amplifiedNoise);
            VanillaOceanTerrain.register(level.getSeed(),level.getChunkSource().randomState());
            var skySettings=level.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.FLOATING_ISLANDS);
            VanillaSkylands.register(level.getSeed(),RandomState.create(skySettings.value(),level.registryAccess().lookupOrThrow(Registries.NOISE),level.getSeed()));
        }
    }
    private boolean far(ChunkAccess chunk){return !nether && FarlandsGeneration.intersects(chunk);}
    private boolean sea(ChunkAccess chunk){return !nether && SeaGeneration.intersects(chunk);}
    private NoiseBasedChunkGenerator delegate(ChunkAccess chunk){return !nether && AmplifiedTerrain.contains(chunk)?amplified:normal;}
    private RandomState noise(ChunkAccess chunk,RandomState original){return delegate(chunk)==amplified?amplifiedNoise:original;}
    @SuppressWarnings({"rawtypes","unchecked"}) @Override protected MapCodec<? extends ChunkGenerator> codec(){return (MapCodec)NoiseBasedChunkGenerator.CODEC;}
    @Override public Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> getTypeNameForDataFixer(){return vanilla.getTypeNameForDataFixer();}
    @Override public CompletableFuture<ChunkAccess> createBiomes(RandomState state,Blender blender,StructureManager structures,ChunkAccess chunk){return delegate(chunk).createBiomes(noise(chunk,state),blender,structures,chunk);}
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender,RandomState state,StructureManager structures,ChunkAccess chunk){
        if(far(chunk) && FarlandsGeneration.fullyManaged(chunk))return CompletableFuture.completedFuture(FarlandsGeneration.fill(chunk,level.getSeed()));
        if(sea(chunk) && !SeaGeneration.needsVanilla(chunk,level.getSeed()))return CompletableFuture.completedFuture(SeaGeneration.fill(chunk,level.getSeed(),false));
        return delegate(chunk).fillFromNoise(blender,noise(chunk,state),structures,chunk).thenApply(c->{
            if(far(c))FarlandsGeneration.fill(c,level.getSeed());
            else if(sea(c))SeaGeneration.fill(c,level.getSeed(),true);
            if(!nether)MistTerrain.freeze(c,level.getSeed());
            return c;
        });
    }
    @Override public void buildSurface(WorldGenRegion region,StructureManager structures,RandomState state,ChunkAccess chunk){
        if(far(chunk) && FarlandsGeneration.fullyManaged(chunk))return;
        if(sea(chunk) && !SeaGeneration.needsVanilla(chunk,level.getSeed()))return;
        if(sea(chunk)){
            var chosen=delegate(chunk);var localNoise=noise(chunk,state);
            chosen.buildSurface(chunk,new WorldGenerationContext(chosen,region,level),localNoise,structures,
                SeaGeneration.nativeSurfaceBiomes(region.getBiomeManager(),vanilla.getBiomeSource(),localNoise.sampler()),
                region.registryAccess().lookupOrThrow(Registries.BIOME),Blender.of(region));
        }else delegate(chunk).buildSurface(region,structures,noise(chunk,state),chunk);
        if(nether)UnderworldTerrain.fill(chunk,level.getSeed());
        else if(far(chunk))FarlandsGeneration.fill(chunk,level.getSeed());
        else if(sea(chunk))SeaGeneration.finishShoreSurface(chunk);
    }
    @Override public void applyCarvers(WorldGenRegion region,long seed,RandomState state,BiomeManager biomes,StructureManager structures,ChunkAccess chunk){
        if(far(chunk) || sea(chunk))return;
        delegate(chunk).applyCarvers(region,seed,noise(chunk,state),biomes,structures,chunk);
    }
    @Override public void applyBiomeDecoration(WorldGenLevel region,ChunkAccess chunk,StructureManager structures){
        var protectedRegion=PaperBridge.protectedFeatures(region,nether);
        if(far(chunk)){FarlandsStructures.generate(this,protectedRegion,chunk,structures);FarlandsDecoration.decorate(chunk,protectedRegion);clean(chunk,!nether);return;}
        if(sea(chunk)){
            if(OceanStructures.needsNativeFeatures(chunk))super.addVanillaDecorations(protectedRegion,chunk,structures);
            else OceanStructures.generate(this,protectedRegion,chunk,structures);
            SeaDecoration.decorate(chunk,protectedRegion);
        }else{
            super.addVanillaDecorations(protectedRegion,chunk,structures);
            if(nether)UnderworldTerrain.decorate(chunk,protectedRegion);
        }
        if(!nether)MistTerrain.finish(chunk,protectedRegion);
        clean(chunk,!nether);
    }
    static void clean(ChunkAccess chunk){clean(chunk,true);}
    static void clean(ChunkAccess chunk,boolean overworld){
        var sections=chunk.getSections();var pos=new BlockPos.MutableBlockPos();
        for(int s=0;s<sections.length;s++){
            var section=sections[s];if(!section.maybeHas(state->state.is(Blocks.ANCIENT_DEBRIS)||PaperBridge.ominous(state)||GeneratedIcePolicy.isIce(state)))continue;
            int base=chunk.getMinY()+s*16;
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                int bx=chunk.getPos().getMinBlockX()+x,bz=chunk.getPos().getMinBlockZ()+z;
                int by=base+y;
                if(GeneratedIcePolicy.isIce(section.getBlockState(x,y,z))&&!(overworld&&MistRegion.contains(bx,bz))){
                    boolean ocean=GeneratedIcePolicy.ocean(chunk.getNoiseBiome(bx>>2,by>>2,bz>>2));
                    int waterLevel=overworld&&SeaTerrain.contains(GreatSea.CONFIG,bx,bz)?GreatSea.CONFIG.seaLevel:63;
                    var replacement=GeneratedIcePolicy.generated(section.getBlockState(x,y,z),ocean,by>chunk.getMinY()&&chunk.getFluidState(pos.set(bx,by-1,bz)).is(net.minecraft.tags.FluidTags.WATER),by,waterLevel);
                    chunk.setBlockState(pos.set(bx,by,bz),replacement,0);
                }
                var state=section.getBlockState(x,y,z);if(state.is(Blocks.ANCIENT_DEBRIS)||PaperBridge.ominous(state)&&!(overworld&&ForbiddenWorldBlocks.allowsOminous(bx,bz)))chunk.setBlockState(pos.set(bx,base+y,bz),Blocks.AIR.defaultBlockState(),3);
            }
        }
    }
    @Override public void createStructures(RegistryAccess registries,ChunkGeneratorStructureState placement,StructureManager structures,ChunkAccess chunk,StructureTemplateManager templates,ResourceKey<Level> dimension){
        if(far(chunk)){
            var pos=chunk.getPos();if(!FarlandsStructures.enabled(pos.getMiddleBlockX(),pos.getMiddleBlockZ()))return;
            super.createStructures(registries,placement,structures,chunk,templates,dimension);
            var registry=registries.lookupOrThrow(Registries.STRUCTURE);
            for(var entry:new HashMap<>(chunk.getAllStarts()).entrySet())
                if(!FarlandsStructures.accepts(registry.getResourceKey(entry.getKey()).orElseThrow(),pos.getMiddleBlockX(),pos.getMiddleBlockZ()))chunk.setStartForStructure(entry.getKey(),StructureStart.INVALID_START);
            if(SpokelandsTerrain.contains(pos.getMiddleBlockX(),pos.getMiddleBlockZ()))for(var set:registries.lookupOrThrow(Registries.STRUCTURE_SET)){
                if(!set.placement().isStructureChunk(placement,pos.x,pos.z))continue;
                for(var entry:set.structures())if(entry.structure().unwrapKey().map(key->FarlandsStructures.accepts(key,pos.getMiddleBlockX(),pos.getMiddleBlockZ())&&key.identifier().getPath().startsWith("mineshaft")).orElse(false))
                    FarlandsStructures.createSpokeMines(entry,structures,registries,level.getChunkSource().randomState(),templates,level.getSeed(),chunk,pos,SectionPos.of(pos,chunk.getMinSectionY()),dimension,this);
            }
            FarlandsStructures.prepareTrials(chunk,level.getSeed(),registries.lookupOrThrow(Registries.STRUCTURE));
            return;
        }
        if(sea(chunk)&&!OceanStructures.offshore(level.getSeed(),chunk.getPos().getMiddleBlockX(),chunk.getPos().getMiddleBlockZ()))return;
        if(sea(chunk))super.createStructures(registries,placement,structures,chunk,templates,dimension);
        else delegate(chunk).createStructures(registries,placement,structures,chunk,templates,dimension);
        FarlandsStructures.prepareTrials(chunk,level.getSeed(),registries.lookupOrThrow(Registries.STRUCTURE));
        if(!sea(chunk))return;
        var pos=chunk.getPos();
        var registry=registries.lookupOrThrow(Registries.STRUCTURE);
        for(var entry:new HashMap<>(chunk.getAllStarts()).entrySet()){
            var key=registry.getResourceKey(entry.getKey()).orElseThrow();
            if(!OceanStructures.oceanType(entry.getKey()) || !OceanStructures.acceptsStart(level.getSeed(),key,pos))chunk.setStartForStructure(entry.getKey(),StructureStart.INVALID_START);
        }
        if(!OceanStructures.offshore(level.getSeed(),pos.getMiddleBlockX(),pos.getMiddleBlockZ()))return;
        for(var set:registries.lookupOrThrow(Registries.STRUCTURE_SET)){
            if(!set.placement().isStructureChunk(placement,pos.x,pos.z))continue;
            for(var entry:set.structures())if(entry.structure().value() instanceof OceanMonumentStructure){
                var key=registry.getResourceKey(entry.structure().value()).orElseThrow();
                if(OceanStructures.acceptsStart(level.getSeed(),key,pos))OceanStructures.createMonumentStart(entry,structures,registries,level.getChunkSource().randomState(),templates,level.getSeed(),chunk,pos,SectionPos.of(pos,chunk.getMinSectionY()),dimension,this);
            }
        }
    }
    @Override public void spawnOriginalMobs(WorldGenRegion region){normal.spawnOriginalMobs(region);}
    // Native Nether feature placement retains its original Y=0..127 range.
    // The dimension/chunk bounds supply the extended Underworld separately.
    @Override public int getGenDepth(){return vanilla.getGenDepth();}
    @Override public int getMinY(){return vanilla.getMinY();}
    @Override public int getSeaLevel(){return vanilla.getSeaLevel();}
    @Override public void addDebugScreenInfo(List<String> text,RandomState state,BlockPos pos){vanilla.addDebugScreenInfo(text,state,pos);}
    @Override public int getBaseHeight(int x,int z,Heightmap.Types type,LevelHeightAccessor world,RandomState state){
        if(!nether&&MistRegion.frost(level.getSeed(),x,z)&&(type==Heightmap.Types.OCEAN_FLOOR||type==Heightmap.Types.OCEAN_FLOOR_WG)){
            var col=getBaseColumn(x,z,world,state);for(int y=world.getMaxY();y>=world.getMinY();y--)if(type.isOpaque().test(col.getBlock(y)))return y+1;
        }
        if(!nether&&SeaTerrain.contains(GreatSea.CONFIG,x,z)&&!SeaGeneration.supported(this))return vanilla.getBaseHeight(x,z,type,world,state);
        if(!nether && (FarlandsWorld.contains(x,z)||SeaTerrain.contains(GreatSea.CONFIG,x,z))){
            var column=getBaseColumn(x,z,world,state);
            for(int y=world.getMaxY();y>=world.getMinY();y--)if(type.isOpaque().test(column.getBlock(y)))return y+1;
            return world.getMinY();
        }
        return !nether && AmplifiedTerrain.contains(x,z)?amplified.getBaseHeight(x,z,type,world,amplifiedNoise):vanilla.getBaseHeight(x,z,type,world,state);
    }
    @Override public NoiseColumn getBaseColumn(int x,int z,LevelHeightAccessor world,RandomState state){
        // Farlands columns are entirely supplied by the shared terrain engine.
        // Building a discarded vanilla noise column first delays structure queries.
        if(!nether && FarlandsWorld.contains(x,z)) {
            BlockState[] blocks=new BlockState[world.getHeight()];int bottom=world.getMinY();
            for(int i=0;i<blocks.length;i++)blocks[i]=FarlandsGeneration.state(level.getSeed(),x,bottom+i,z);
            return new NoiseColumn(bottom,blocks);
        }
        var original=!nether && AmplifiedTerrain.contains(x,z)?amplified.getBaseColumn(x,z,world,amplifiedNoise):vanilla.getBaseColumn(x,z,world,state);
        if(!nether&&MistRegion.frost(level.getSeed(),x,z)){
            BlockState[] blocks=new BlockState[world.getHeight()];for(int i=0;i<blocks.length;i++)blocks[i]=MistTerrain.frozen(original.getBlock(world.getMinY()+i));return new NoiseColumn(world.getMinY(),blocks);
        }
        if(!nether&&SeaTerrain.contains(GreatSea.CONFIG,x,z)&&!SeaGeneration.supported(this))return original;
        if(!nether && !FarlandsWorld.contains(x,z) && !SeaTerrain.contains(GreatSea.CONFIG,x,z))return original;
        BlockState[] blocks=new BlockState[world.getHeight()];int bottom=world.getMinY();
        if(nether){for(int i=0;i<blocks.length;i++)blocks[i]=bottom+i<0?UnderworldTerrain.state(level.getSeed(),x,bottom+i,z):original.getBlock(bottom+i);}
        else if(FarlandsWorld.contains(x,z)){for(int i=0;i<blocks.length;i++)blocks[i]=FarlandsGeneration.state(level.getSeed(),x,bottom+i,z);}
        else{
            int old=bottom;for(int y=world.getMaxY();y>=bottom;y--)if(original.getBlock(y).isSolid()){old=y;break;}
            var config=GreatSea.CONFIG;boolean ocean=SeaTerrain.deepFloor(config,level.getSeed(),x,z);
            boolean gravel=ocean&&OceanFloorMaterials.gravel(config,level.getSeed(),x,z);
            int top=(int)Math.round(SeaTerrain.height(config,level.getSeed(),x,z,old,ocean?VanillaOceanTerrain.floorHeight(config,level.getSeed(),x,z):SeaTerrain.floorBaseline(config)));
            int ceiling=(int)Math.round(MainlandJoin.ceiling(config,old,top));boolean dry=SeaTerrain.shoreDistance(config,level.getSeed(),x,z)<config.beachWidth;
            for(int i=0;i<blocks.length;i++){
                int y=bottom+i;blocks[i]=dry&&y>top?(y<=ceiling?original.getBlock(y):Blocks.AIR.defaultBlockState()):ocean?VanillaOceanTerrain.state(config,level.getSeed(),x,y,z,top,bottom,gravel):SeaGeneration.state(level.getSeed(),x,y,z,top,bottom);
            }
        }
        return new NoiseColumn(bottom,blocks);
    }
}

