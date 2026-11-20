package dev.greatsea;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.io.IOException;
import java.util.function.BiConsumer;

/** Adds the same settings syntax and responses to Fabric and Paper. */
final class GameplayCommands {
    private static final SimpleCommandExceptionType SAVE_ERROR =
            new SimpleCommandExceptionType(new LiteralMessage("Could not save the Unstable Terrain setting."));

    private GameplayCommands() {}

    static <S> LiteralArgumentBuilder<S> settings(GameplaySettings store, BiConsumer<S, String> reply) {
        var settings = LiteralArgumentBuilder.<S>literal("settings").executes(context -> {
            var message = new StringBuilder("Unstable Terrain settings");
            for (var rule : GameplaySettings.Rule.values()) {
                message.append('\n').append(rule.name).append(" = ").append(store.get(rule));
            }
            reply.accept(context.getSource(), message.toString());
            return 1;
        });
        for (var rule : GameplaySettings.Rule.values()) {
            var entry = LiteralArgumentBuilder.<S>literal(rule.name).executes(context -> {
                reply.accept(context.getSource(), rule.name + " = " + store.get(rule));
                return 1;
            });
            entry.then(RequiredArgumentBuilder.<S, Boolean>argument("value", BoolArgumentType.bool())
                    .executes(context -> {
                        boolean value = BoolArgumentType.getBool(context, "value");
                        try {
                            store.set(rule, value);
                        } catch (IOException error) {
                            GreatSea.LOGGER.warn("Could not save gameplay setting {}", rule.name, error);
                            throw SAVE_ERROR.create();
                        }
                        reply.accept(context.getSource(), rule.name + " = " + value + " (saved)");
                        return 1;
                    }));
            settings.then(entry);
        }
        return settings;
    }
}
