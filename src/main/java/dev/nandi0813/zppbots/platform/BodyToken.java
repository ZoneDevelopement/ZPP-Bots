package dev.nandi0813.zppbots.platform;

import java.util.UUID;

public record BodyToken(UUID sessionId, UUID playerId, long generation) {
    public BodyToken {
        if (sessionId == null || playerId == null || generation <= 0) {
            throw new IllegalArgumentException("Invalid bot body token");
        }
    }
}
