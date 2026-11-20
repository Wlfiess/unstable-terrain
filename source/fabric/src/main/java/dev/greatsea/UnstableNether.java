package dev.greatsea;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;

/** The saved Nether settings key opts in independently of the Great Sea settings. */
public final class UnstableNether {
    public static final Identifier ID=Identifier.of("greatsea","unstable_nether");
    public static final RegistryKey<ChunkGeneratorSettings> SETTINGS=
            RegistryKey.of(RegistryKeys.CHUNK_GENERATOR_SETTINGS,ID);
    public static final int MIN_Y=-320,HEIGHT=576,UNDERWORLD_TOP=-128;
    private UnstableNether() {}
    public static void initialize() {
        Registry.register(Registries.BIOME_SOURCE,ID,UnderworldBiomeSource.CODEC);
    }
    public static boolean supported(ChunkGenerator generator) {
        return generator instanceof NoiseChunkGenerator noise && noise.matchesSettings(SETTINGS);
    }
    public static boolean supported(ServerWorld world) {
        return world.getRegistryKey().equals(World.NETHER) && supported(world.getChunkManager().getChunkGenerator());
    }
}
