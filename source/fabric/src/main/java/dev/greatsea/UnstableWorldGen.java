package dev.greatsea;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.WorldPreset;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** The saved noise-settings registry key is the durable opt-in for this world type. */
public final class UnstableWorldGen {
    public static final Identifier ID=Identifier.of("greatsea","unstable");
    public static final RegistryKey<WorldPreset> PRESET=RegistryKey.of(RegistryKeys.WORLD_PRESET,ID);
    public static final RegistryKey<ChunkGeneratorSettings> SETTINGS=RegistryKey.of(RegistryKeys.CHUNK_GENERATOR_SETTINGS,ID);
    // The Unstable settings deliberately have the same vanilla values as Normal. Equality
    // cannot distinguish them; retain registry-entry identity for climate samplers instead.
    private static final Set<ChunkGeneratorSettings> OPTED_IN=Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>()));
    private UnstableWorldGen() {}

    public static boolean isUnstable(ChunkGenerator generator) {
        return generator instanceof NoiseChunkGenerator noise && noise.matchesSettings(SETTINGS);
    }
    public static boolean isUnstable(ChunkGeneratorSettings settings) { return OPTED_IN.contains(settings); }
    public static void register(RegistryEntry<ChunkGeneratorSettings> settings) {
        if (settings.matchesKey(SETTINGS)) OPTED_IN.add(settings.value());
    }
}
