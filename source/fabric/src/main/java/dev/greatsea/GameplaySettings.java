package dev.greatsea;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;

/** Live settings shared by the server hooks and the administration command. */
public final class GameplaySettings {
    public enum Rule {
        MOB_SPAWNING("mobSpawning", false),
        FARLANDS_FIREWORKS("farlandsFireworks", false),
        UNDERWORLD_ADVENTURE("underworldAdventure", true),
        FARLANDS_PORTALS("farlandsPortals", false);

        final String name;
        final boolean defaultValue;

        Rule(String name, boolean defaultValue) {
            this.name = name;
            this.defaultValue = defaultValue;
        }

        public boolean enabled() {
            return current.enabled.contains(this);
        }
    }

    private static volatile GameplaySettings current = new GameplaySettings(null, defaults());
    private final Path file;
    private volatile Set<Rule> enabled;

    private GameplaySettings(Path file, Set<Rule> enabled) {
        this.file = file;
        this.enabled = Set.copyOf(enabled);
    }

    public static void initialize(Path configDirectory) {
        Path file = configDirectory.resolve("unstableterrain-gameplay.properties");
        try {
            current = load(file);
        } catch (IOException | IllegalArgumentException error) {
            GreatSea.LOGGER.warn("Could not read gameplay settings from {}. Using defaults.", file, error);
            current = new GameplaySettings(file, defaults());
        }
    }

    static GameplaySettings current() {
        return current;
    }

    static GameplaySettings load(Path file) throws IOException {
        var enabled = defaults();
        if (Files.exists(file)) {
            var properties = new Properties();
            try (var reader = Files.newBufferedReader(file)) {
                properties.load(reader);
            }
            for (var rule : Rule.values()) {
                String value = properties.getProperty(rule.name);
                if ("true".equalsIgnoreCase(value)) enabled.add(rule);
                else if ("false".equalsIgnoreCase(value)) enabled.remove(rule);
            }
        }
        return new GameplaySettings(file, enabled);
    }

    private static EnumSet<Rule> defaults() {
        var enabled = EnumSet.noneOf(Rule.class);
        for (var rule : Rule.values()) {
            if (rule.defaultValue) enabled.add(rule);
        }
        return enabled;
    }

    boolean get(Rule rule) {
        return enabled.contains(rule);
    }

    synchronized void set(Rule rule, boolean value) throws IOException {
        if (file == null) throw new IOException("Gameplay settings are not initialized");
        var updated = EnumSet.noneOf(Rule.class);
        updated.addAll(enabled);
        if (value) updated.add(rule);
        else updated.remove(rule);

        var content = new StringBuilder();
        for (var setting : Rule.values()) {
            content.append(setting.name).append('=').append(updated.contains(setting)).append('\n');
        }

        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "unstableterrain-gameplay-", ".tmp");
        try {
            Files.writeString(temporary, content);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            enabled = Set.copyOf(updated);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
