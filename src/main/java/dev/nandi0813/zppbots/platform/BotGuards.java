package dev.nandi0813.zppbots.platform;

import dev.nandi0813.api.ZonePracticeApi;
import org.bukkit.Bukkit;

import java.util.UUID;

public final class BotGuards {

    private BotGuards() {
    }

    public static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Bot bodies must be managed on the main thread");
        }
    }

    public static boolean isBot(UUID playerId) {
        return playerId != null && ZonePracticeApi.isAvailable()
                && ZonePracticeApi.getInstance().getBotRegistry().isBot(playerId);
    }
}
