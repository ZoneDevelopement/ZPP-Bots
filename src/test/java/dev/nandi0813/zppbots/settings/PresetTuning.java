package dev.nandi0813.zppbots.settings;

import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.BuiltInTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/** Test fixtures: the shipped tuning as a JSON document tests can edit, plus the expected preset records. */
public final class PresetTuning {
    private PresetTuning() {}

    public static Behavior behavior() {
        return new Behavior(
                Behavior.PlayStyleSetting.DEFAULT,
                List.of(Behavior.PlayStyleSetting.values()),
                true,
                true,
                true,
                false,
                true,
                BotSettings.BunnyHopMode.ON,
                BotSettings.CartDoctrine.RELENTLESS,
                true,
                3.0,
                BotSettings.BridgeMode.NINJA,
                BotSettings.CartMode.SMART,
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                true,
                false,
                true,
                4.0,
                true,
                true,
                true,
                true,
                100.0,
                BotSettings.HealingMode.ADVANCED,
                BotSettings.OffhandPreference.AUTO,
                4.0,
                true,
                false,
                4.0,
                true,
                true,
                true,
                true,
                100,
                50,
                30,
                true,
                true,
                techniques(),
                defaultChances());
    }

    private static Map<BotSettings.PlayStyle, Integer> defaultChances() {
        Map<BotSettings.PlayStyle, Integer> chances = new EnumMap<>(BotSettings.PlayStyle.class);
        chances.put(BotSettings.PlayStyle.DEFAULT, 25);
        chances.put(BotSettings.PlayStyle.SAFE, 15);
        chances.put(BotSettings.PlayStyle.DEFENSIVE, 15);
        chances.put(BotSettings.PlayStyle.BALANCED, 20);
        chances.put(BotSettings.PlayStyle.AGGRESSIVE, 25);
        chances.put(BotSettings.PlayStyle.ADAPTIVE, 0);
        return chances;
    }

    public static TechniqueConfig techniques() {
        TechniqueConfig techniques = new TechniqueConfig();
        techniques.maceSwap = false;
        techniques.spearMaceSwap = false;
        techniques.swordWbSwap = false;
        techniques.breachSwap = false;
        techniques.spearReachSwap = false;
        techniques.windchargeMace = true;
        techniques.wcCrouchBoost = false;
        techniques.wcFullCooldownDiscipline = true;
        techniques.wcHitPreference = TechniqueConfig.HitPreference.VERTICAL;
        techniques.hotbarShieldTracking = false;
        techniques.elytraWcBoost = false;
        techniques.spearDash = true;
        techniques.dashChain = true;
        techniques.spearMacing = true;
        techniques.wcReset = true;
        techniques.wcSelfReset = false;
        techniques.pearlCatch = true;
        techniques.pearlCatchVariant = TechniqueConfig.PearlCatchVariant.SMART;
        techniques.pearlCatchGlide = true;
        techniques.pearlGrapple = true;
        techniques.diveBomb = true;
        techniques.elytraStunSlam = true;
        techniques.elytraPreserve = false;
        techniques.groundBackstab = false;
        techniques.elytraBackstab = false;
        techniques.stunSlam = true;
        techniques.stunSlamVariant = TechniqueConfig.StunSlamVariant.OPTIMAL;
        techniques.spearStunSlam = false;
        techniques.shieldDrain = false;
        techniques.detapSpearMace = false;
        techniques.detapBreach = false;
        techniques.spearDoublePop = false;
        techniques.spearDoublePopOffhand = false;
        techniques.wcCancel = false;
        techniques.rocketMace = true;
        techniques.rocketMaceVariant = TechniqueConfig.RocketMaceVariant.V1_CLIMB_DROP;
        return techniques;
    }

    public static DifficultyParams forPreset(Difficulty d) {
        DifficultyParams p = new DifficultyParams();
        switch (d) {
            case EASY:
                p.missChance = 0.35;
                p.accuracy = 0.35;
                p.clutchAccuracy = 0.1;
                p.reactionMinTicks = 6;
                p.reactionMaxTicks = 14;
                p.shieldReactionMs = 700;
                p.shieldReactionJitterMs = 150;
                p.shieldDisableCharge = 0.7;
                p.strafeAngle = 50.0;
                p.critThreshold = -0.15;
                p.anchorIntervalTicks = 30;
                p.stunSlamChance = 20;
                p.potIntervalSeconds = 1.0;
                p.webDrainDelaySeconds = 1.5;
                p.webEscapeDelaySeconds = 1.5;
                p.elytraMaceLevel = 1;
                p.reachFlips = 10;
                p.cartSkipChance = 80;
                p.cartCooldownTicks = 15;
                p.crystalCooldownTicks = 12;
                p.anchorCooldownTicks = 20;
                p.crystalPvpLevel = 1;
                p.buildMoveChance = 0.0;
                p.lavaTrapLevel = 3;
                p.healCommitBreakHp = 0;
                p.pearlChain = false;
                p.potionFleeHealHp = 8;
                p.totemRegrabExtraTicks = 12;
                break;
            case MEDIUM:
                p.missChance = 0.25;
                p.accuracy = 0.55;
                p.clutchAccuracy = 0.35;
                p.reactionMinTicks = 3;
                p.reactionMaxTicks = 7;
                p.shieldReactionMs = 400;
                p.shieldReactionJitterMs = 100;
                p.shieldDisableCharge = 0.6;
                p.strafeAngle = 38.0;
                p.critThreshold = -0.08;
                p.anchorIntervalTicks = 20;
                p.stunSlamChance = 45;
                p.potIntervalSeconds = 0.7;
                p.webDrainDelaySeconds = 1.0;
                p.webEscapeDelaySeconds = 1.0;
                p.elytraMaceLevel = 2;
                p.reachFlips = 10;
                p.cartSkipChance = 60;
                p.cartCooldownTicks = 12;
                p.crystalCooldownTicks = 9;
                p.anchorCooldownTicks = 13;
                p.crystalPvpLevel = 2;
                p.buildMoveChance = 0.02;
                p.lavaTrapLevel = 3;
                p.healCommitBreakHp = 0;
                p.pearlChain = false;
                p.potionFleeHealHp = 10;
                p.totemRegrabExtraTicks = 12;
                break;
            case HARD:
                p.missChance = 0.1;
                p.accuracy = 0.75;
                p.clutchAccuracy = 0.6;
                p.reactionMinTicks = 1;
                p.reactionMaxTicks = 3;
                p.shieldReactionMs = 200;
                p.shieldReactionJitterMs = 50;
                p.shieldDisableCharge = 0.55;
                p.strafeAngle = 28.0;
                p.critThreshold = -0.03;
                p.anchorIntervalTicks = 15;
                p.stunSlamChance = 70;
                p.potIntervalSeconds = 0.6;
                p.webDrainDelaySeconds = 0.6;
                p.webEscapeDelaySeconds = 0.6;
                p.elytraMaceLevel = 3;
                p.reachFlips = 10;
                p.cartSkipChance = 40;
                p.cartCooldownTicks = 9;
                p.crystalCooldownTicks = 6;
                p.anchorCooldownTicks = 10;
                p.crystalPvpLevel = 3;
                p.buildMoveChance = 0.05;
                p.lavaTrapLevel = 3;
                p.healCommitBreakHp = 0;
                p.pearlChain = true;
                p.potionFleeHealHp = 12;
                p.totemRegrabExtraTicks = 8;
                break;
            case EXPERT:
                p.missChance = 0.05;
                p.accuracy = 0.85;
                p.clutchAccuracy = 0.85;
                p.reactionMinTicks = 1;
                p.reactionMaxTicks = 1;
                p.shieldReactionMs = 100;
                p.shieldReactionJitterMs = 50;
                p.shieldDisableCharge = 0.4;
                p.strafeAngle = 22.0;
                p.critThreshold = -0.01;
                p.anchorIntervalTicks = 12;
                p.stunSlamChance = 90;
                p.potIntervalSeconds = 0.2;
                p.webDrainDelaySeconds = 0.3;
                p.webEscapeDelaySeconds = 0.3;
                p.elytraMaceLevel = 4;
                p.reachFlips = 8;
                p.cartSkipChance = 20;
                p.cartCooldownTicks = 6;
                p.crystalCooldownTicks = 4;
                p.anchorCooldownTicks = 8;
                p.crystalPvpLevel = 4;
                p.buildMoveChance = 0.2;
                p.lavaTrapLevel = 3;
                p.healCommitBreakHp = 3;
                p.pearlChain = true;
                p.potionFleeHealHp = 12;
                p.totemRegrabExtraTicks = 4;
                break;
            case PERFECT:
                p.missChance = 0.0;
                p.accuracy = 1.0;
                p.clutchAccuracy = 1.0;
                p.reactionMinTicks = 0;
                p.reactionMaxTicks = 0;
                p.shieldReactionMs = 50;
                p.shieldReactionJitterMs = 0;
                p.shieldDisableCharge = 0.1;
                p.strafeAngle = 18.0;
                p.critThreshold = 0.0;
                p.anchorIntervalTicks = 10;
                p.stunSlamChance = 100;
                p.potIntervalSeconds = 0.05;
                p.webDrainDelaySeconds = 0.1;
                p.webEscapeDelaySeconds = 0.1;
                p.elytraMaceLevel = 5;
                p.reachFlips = 4;
                p.cartSkipChance = 0;
                p.cartCooldownTicks = 3;
                p.crystalCooldownTicks = 2;
                p.anchorCooldownTicks = 6;
                p.crystalPvpLevel = 5;
                p.buildMoveChance = 0.5;
                p.lavaTrapLevel = 3;
                p.healCommitBreakHp = 5;
                p.pearlChain = true;
                p.potionFleeHealHp = 14;
                p.totemRegrabExtraTicks = 2;
        }

        return p;
    }


    /** The shipped tuning as {@code defaults} (difficulty 3) plus {@code difficulty/<n>} overrides for keys that differ by level. */
    public static JSONObject root(long version) {
        JSONObject defaults = new JSONObject();
        JSONObject difficulty = new JSONObject();
        for (int level = 1; level <= 5; level++) difficulty.put(Integer.toString(level), new JSONObject());
        for (TuningKey key : TuningKey.values()) {
            defaults.put(key.key(), BuiltInTuning.raw(key, 3));
            for (int level = 1; level <= 5; level++) {
                if (key.key().startsWith("settings.difficulty.") || !BuiltInTuning.raw(key, level).equals(BuiltInTuning.raw(key, 3))) {
                    difficulty.getJSONObject(Integer.toString(level)).put(key.key(), BuiltInTuning.raw(key, level));
                }
            }
        }
        return new JSONObject().put("version", version).put("defaults", defaults).put("difficulty", difficulty);
    }

    public static BotTuning.Profile profile(JSONObject root, int difficulty) {
        JSONObject defaults = root.getJSONObject("defaults");
        JSONObject level = root.getJSONObject("difficulty").getJSONObject(Integer.toString(difficulty));
        Map<TuningKey, Object> values = new EnumMap<>(TuningKey.class);
        for (TuningKey key : TuningKey.values()) {
            JSONObject source = level.has(key.key()) ? level : defaults;
            if (!source.has(key.key())) continue;
            Object value = source.get(key.key());
            values.put(key, value instanceof Number number ? new BigDecimal(number.toString()) : value);
        }
        return BotTuning.of(values);
    }
}
