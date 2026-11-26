package dev.nandi0813.zppbots.smoke;

import dev.nandi0813.api.Interface.BotRegistry;
import dev.nandi0813.api.ZonePracticeApi;
import dev.nandi0813.zppbots.platform.BodyToken;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/** Bot identity helpers for smoke fixtures, backed by the public BotRegistry. */
final class SmokeIdentity {

    private SmokeIdentity() {
    }

    private static BotRegistry registry() {
        return ZonePracticeApi.getInstance().getBotRegistry();
    }

    static BodyToken reserve(Plugin owner, UUID sessionId, long generation) {
        return new BodyToken(sessionId, registry().reserveEntityId(owner), generation);
    }

    static void release(BodyToken token) {
        if (token != null) registry().releaseEntityId(token.playerId());
    }

    static boolean owns(BodyToken token) {
        return token != null && registry().isBot(token.playerId());
    }

    static boolean isManaged(UUID entityId) {
        return entityId != null && registry().isBot(entityId);
    }

    /** Core reserved-identity count, read reflectively for leak checks. */
    static int size() {
        try {
            Class<?> identity = Class.forName("dev.nandi0813.practice.manager.bot.BotIdentity", true,
                    ZonePracticeApi.getInstance().getClass().getClassLoader());
            return (int) identity.getMethod("size").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Core bot identity count is unavailable", e);
        }
    }
}
