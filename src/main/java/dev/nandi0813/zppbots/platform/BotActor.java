package dev.nandi0813.zppbots.platform;

import org.bukkit.entity.Player;

import java.util.UUID;

public record BotActor(BodyToken identity, Player player) {

    public BotActor {
        if (identity == null || player == null || !identity.playerId().equals(player.getUniqueId())) {
            throw new IllegalArgumentException("Bot actor identity must match its player");
        }
    }

    public UUID entityId() {
        return player.getUniqueId();
    }
}
