package dev.greatsea;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static dev.greatsea.GameplaySettings.Rule.*;
import static org.junit.jupiter.api.Assertions.*;

class GameplayCommandsTest {
    @TempDir Path directory;

    private record Sender(boolean operator, List<String> replies) {
        Sender(boolean operator) {
            this(operator, new ArrayList<>());
        }
    }

    private static CommandDispatcher<Sender> dispatcher(GameplaySettings settings) {
        var commands = new CommandDispatcher<Sender>();
        commands.register(LiteralArgumentBuilder.<Sender>literal("unstableterrain").requires(Sender::operator)
                .then(GameplayCommands.settings(settings, (sender, reply) -> sender.replies().add(reply))));
        return commands;
    }

    @Test void onlyOperatorsCanReadOrChangeSettings() throws Exception {
        Path file = directory.resolve("gameplay.properties");
        var settings = GameplaySettings.load(file);
        var commands = dispatcher(settings);
        var player = new Sender(false);
        var operator = new Sender(true);

        assertThrows(CommandSyntaxException.class, () -> commands.execute("unstableterrain settings", player));
        assertThrows(CommandSyntaxException.class, () -> commands.execute("unstableterrain settings mobSpawning", player));
        assertThrows(CommandSyntaxException.class, () -> commands.execute("unstableterrain settings mobSpawning true", player));
        commands.execute("unstableterrain settings", operator);
        assertTrue(operator.replies().getFirst().contains("mobSpawning = false"));
        assertThrows(CommandSyntaxException.class, () -> commands.execute("unstableterrain settings mobSpawning yes", operator));
        assertFalse(Files.exists(file));

        commands.execute("unstableterrain settings mobSpawning true", operator);
        commands.execute("unstableterrain settings farlandsFireworks true", operator);
        commands.execute("unstableterrain settings underworldAdventure false", operator);
        commands.execute("unstableterrain settings farlandsPortals true", operator);
        var reopened = GameplaySettings.load(file);
        assertTrue(settings.get(MOB_SPAWNING));
        assertTrue(reopened.get(MOB_SPAWNING));
        assertTrue(reopened.get(FARLANDS_FIREWORKS));
        assertFalse(reopened.get(UNDERWORLD_ADVENTURE));
        assertTrue(reopened.get(FARLANDS_PORTALS));
    }

    @Test void aFailedSaveKeepsThePreviousLiveValue() throws Exception {
        Path file = directory.resolve("blocked/gameplay.properties");
        var settings = GameplaySettings.load(file);
        Files.writeString(file.getParent(), "not a directory");
        var commands = dispatcher(settings);
        assertThrows(CommandSyntaxException.class, () ->
                commands.execute("unstableterrain settings underworldAdventure false", new Sender(true)));
        assertTrue(settings.get(UNDERWORLD_ADVENTURE));
    }
}
