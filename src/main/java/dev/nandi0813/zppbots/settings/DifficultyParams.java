package dev.nandi0813.zppbots.settings;

import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import java.util.Objects;
import java.util.Random;

public final class DifficultyParams {
    public double missChance;
    public double accuracy;
    public double clutchAccuracy;
    public int reactionMinTicks;
    public int reactionMaxTicks;
    public int shieldReactionMs;
    public int shieldReactionJitterMs;
    public double shieldDisableCharge;
    public double strafeAngle;
    public double critThreshold;
    public int anchorIntervalTicks;
    public int stunSlamChance;
    public double potIntervalSeconds;
    public double webDrainDelaySeconds;
    public double webEscapeDelaySeconds;
    public int reachFlips;
    public int cartSkipChance;
    public int cartCooldownTicks;
    public int crystalCooldownTicks;
    public int anchorCooldownTicks;
    public int elytraMaceLevel;
    public int crystalPvpLevel;
    public double buildMoveChance;
    public int lavaTrapLevel;
    public double healCommitBreakHp;
    public boolean pearlChain;
    public double potionFleeHealHp;
    public int totemRegrabExtraTicks;

    public int potIntervalTicks() {
        return Math.max(1, (int) Math.round(this.potIntervalSeconds * 20.0));
    }

    public int webDrainDelayTicks() {
        return Math.max(0, (int)Math.round(this.webDrainDelaySeconds * 20.0));
    }

    public int webEscapeDelayTicks() {
        return Math.max(0, (int)Math.round(this.webEscapeDelaySeconds * 20.0));
    }

    public static DifficultyParams fromProfile(BotTuning.Profile profile) {
        Objects.requireNonNull(profile, "profile");
        DifficultyParams p = new DifficultyParams();
        p.missChance = profile.number(TuningKey.MISS_CHANCE).doubleValue();
        p.accuracy = profile.number(TuningKey.ACCURACY).doubleValue();
        p.clutchAccuracy = profile.number(TuningKey.CLUTCH_ACCURACY).doubleValue();
        p.reactionMinTicks = profile.integer(TuningKey.REACTION_MIN_TICKS);
        p.reactionMaxTicks = profile.integer(TuningKey.REACTION_MAX_TICKS);
        p.shieldReactionMs = profile.integer(TuningKey.SHIELD_REACTION_MS);
        p.shieldReactionJitterMs = profile.integer(TuningKey.SHIELD_REACTION_JITTER_MS);
        p.shieldDisableCharge = profile.number(TuningKey.SHIELD_DISABLE_CHARGE).doubleValue();
        p.strafeAngle = profile.number(TuningKey.STRAFE_ANGLE).doubleValue();
        p.critThreshold = profile.number(TuningKey.CRIT_THRESHOLD).doubleValue();
        p.anchorIntervalTicks = profile.integer(TuningKey.ANCHOR_INTERVAL_TICKS);
        p.stunSlamChance = profile.integer(TuningKey.STUN_SLAM_CHANCE);
        p.potIntervalSeconds = profile.number(TuningKey.POT_INTERVAL_SECONDS).doubleValue();
        p.webDrainDelaySeconds = profile.number(TuningKey.WEB_DRAIN_DELAY_SECONDS).doubleValue();
        p.webEscapeDelaySeconds = profile.number(TuningKey.WEB_ESCAPE_DELAY_SECONDS).doubleValue();
        p.reachFlips = profile.integer(TuningKey.REACH_FLIPS);
        p.cartSkipChance = profile.integer(TuningKey.CART_SKIP_CHANCE);
        p.cartCooldownTicks = profile.integer(TuningKey.CART_COOLDOWN_TICKS);
        p.crystalCooldownTicks = profile.integer(TuningKey.CRYSTAL_COOLDOWN_TICKS);
        p.anchorCooldownTicks = profile.integer(TuningKey.ANCHOR_COOLDOWN_TICKS);
        p.elytraMaceLevel = profile.integer(TuningKey.ELYTRA_MACE_LEVEL);
        p.crystalPvpLevel = profile.integer(TuningKey.CRYSTAL_PVP_LEVEL);
        p.buildMoveChance = profile.number(TuningKey.BUILD_MOVE_CHANCE).doubleValue();
        p.lavaTrapLevel = profile.integer(TuningKey.LAVA_TRAP_LEVEL);
        p.healCommitBreakHp = profile.number(TuningKey.HEAL_COMMIT_BREAK_HP).doubleValue();
        p.pearlChain = profile.flag(TuningKey.PEARL_CHAIN);
        p.potionFleeHealHp = profile.number(TuningKey.POTION_FLEE_HEAL_HP).doubleValue();
        p.totemRegrabExtraTicks = profile.integer(TuningKey.TOTEM_REGRAB_EXTRA_TICKS);
        return p;
    }

    public DifficultyParams copy() {
        DifficultyParams p = new DifficultyParams();
        p.missChance = this.missChance;
        p.accuracy = this.accuracy;
        p.clutchAccuracy = this.clutchAccuracy;
        p.reactionMinTicks = this.reactionMinTicks;
        p.reactionMaxTicks = this.reactionMaxTicks;
        p.shieldReactionMs = this.shieldReactionMs;
        p.shieldReactionJitterMs = this.shieldReactionJitterMs;
        p.shieldDisableCharge = this.shieldDisableCharge;
        p.strafeAngle = this.strafeAngle;
        p.critThreshold = this.critThreshold;
        p.anchorIntervalTicks = this.anchorIntervalTicks;
        p.stunSlamChance = this.stunSlamChance;
        p.potIntervalSeconds = this.potIntervalSeconds;
        p.webDrainDelaySeconds = this.webDrainDelaySeconds;
        p.webEscapeDelaySeconds = this.webEscapeDelaySeconds;
        p.elytraMaceLevel = this.elytraMaceLevel;
        p.crystalPvpLevel = this.crystalPvpLevel;
        p.reachFlips = this.reachFlips;
        p.cartSkipChance = this.cartSkipChance;
        p.cartCooldownTicks = this.cartCooldownTicks;
        p.crystalCooldownTicks = this.crystalCooldownTicks;
        p.anchorCooldownTicks = this.anchorCooldownTicks;
        p.buildMoveChance = this.buildMoveChance;
        p.lavaTrapLevel = this.lavaTrapLevel;
        p.healCommitBreakHp = this.healCommitBreakHp;
        p.pearlChain = this.pearlChain;
        p.potionFleeHealHp = this.potionFleeHealHp;
        p.totemRegrabExtraTicks = this.totemRegrabExtraTicks;
        return p;
    }

    public int rollReactionTicks(Random rng) {
        int span = Math.max(0, this.reactionMaxTicks - this.reactionMinTicks);
        return this.reactionMinTicks + (span == 0 ? 0 : rng.nextInt(span + 1));
    }

    public int rollShieldReactionTicks(Random rng) {
        int jitter = this.shieldReactionJitterMs <= 0 ? 0 : rng.nextInt(this.shieldReactionJitterMs * 2 + 1) - this.shieldReactionJitterMs;
        return Math.max(1, Math.round((this.shieldReactionMs + jitter) / 50.0F));
    }
}
