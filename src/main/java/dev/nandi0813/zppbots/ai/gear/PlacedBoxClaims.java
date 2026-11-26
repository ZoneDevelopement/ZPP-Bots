package dev.nandi0813.zppbots.ai.gear;

import dev.nandi0813.zppbots.mc.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shulker boxes bots placed, keyed by position; one instance per match instead of a static map. Main thread only. */
public final class PlacedBoxClaims {

    private final Map<BlockPos, UUID> owners = new HashMap<>();

    public void claim(BlockPos pos, UUID owner) {
        owners.put(pos, owner);
    }

    /** Removes the claim only if {@code owner} holds it. */
    public void release(BlockPos pos, UUID owner) {
        owners.remove(pos, owner);
    }

    public UUID ownerOf(BlockPos pos) {
        return owners.get(pos);
    }
}
