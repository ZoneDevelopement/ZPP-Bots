package dev.nandi0813.zppbots.settings;

import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import java.util.List;
import java.util.Map;

public record Behavior(
        PlayStyleSetting playStyle,
        List<PlayStyleSetting> playerPlayStyles,
        boolean equipArmor,
        boolean usePotions,
        boolean revengeMode,
        boolean retreat,
        boolean useCobwebs,
        BotSettings.BunnyHopMode bunnyHopMode,
        BotSettings.CartDoctrine cartDoctrine,
        boolean useCarting,
        double attackRange,
        BotSettings.BridgeMode bridgeMode,
        BotSettings.CartMode cartMode,
        boolean ignoreCreative,
        boolean openDoors,
        boolean wanderWhenIdle,
        boolean useEnderPearl,
        boolean pearlChasing,
        boolean chorusEscape,
        boolean fireEscape,
        boolean lavaEscape,
        boolean enderChestRegear,
        boolean shulkerSteal,
        boolean shulkerRegear,
        boolean spreadOut,
        boolean webEscape,
        boolean waterBucketClutch,
        boolean useBows,
        boolean hitWeb,
        boolean pickupLoot,
        double buildUpMinHeight,
        boolean speedNoJump,
        boolean maceTotem,
        boolean chaseSpread,
        boolean placeBlocksMoving,
        double lootPackRadius,
        BotSettings.HealingMode healingMode,
        BotSettings.OffhandPreference offhandPreference,
        double offhandTotemHearts,
        boolean strafeInCombo,
        boolean useOrbitalStrike,
        double retreatHealthHearts,
        boolean useScaffolding,
        boolean useElytraMace,
        boolean useElytraSpear,
        boolean useWebDrain,
        int webDrainChance,
        int jumpResetChance,
        int counterStunChance,
        boolean shieldOnMaceDive,
        boolean buildFight,
        TechniqueConfig techniques,
        Map<BotSettings.PlayStyle, Integer> playStyleChances) {

    public enum PlayStyleSetting {
        DEFAULT,
        SAFE,
        AGGRESSIVE,
        ADAPTIVE,
        DEFENSIVE,
        BALANCED,
        RANDOM
    }

    public static Behavior fromProfile(
            BotTuning.Profile profile,
            PlayStyleSetting playStyle,
            List<PlayStyleSetting> playerPlayStyles) {
        Map<BotSettings.PlayStyle, Integer> chances = Map.of(
                BotSettings.PlayStyle.DEFAULT, profile.integer(TuningKey.STYLE_WEIGHT_DEFAULT),
                BotSettings.PlayStyle.SAFE, profile.integer(TuningKey.STYLE_WEIGHT_SAFE),
                BotSettings.PlayStyle.AGGRESSIVE, profile.integer(TuningKey.STYLE_WEIGHT_AGGRESSIVE),
                BotSettings.PlayStyle.ADAPTIVE, profile.integer(TuningKey.STYLE_WEIGHT_ADAPTIVE),
                BotSettings.PlayStyle.DEFENSIVE, profile.integer(TuningKey.STYLE_WEIGHT_DEFENSIVE),
                BotSettings.PlayStyle.BALANCED, profile.integer(TuningKey.STYLE_WEIGHT_BALANCED));
        return new Behavior(
                playStyle,
                List.copyOf(playerPlayStyles),
                profile.flag(TuningKey.BEHAVIOR_EQUIP_ARMOR),
                profile.flag(TuningKey.BEHAVIOR_USE_POTIONS),
                profile.flag(TuningKey.BEHAVIOR_REVENGE_MODE),
                profile.flag(TuningKey.BEHAVIOR_RETREAT),
                profile.flag(TuningKey.BEHAVIOR_USE_COBWEBS),
                bunnyHopMode(profile.integer(TuningKey.BEHAVIOR_BUNNY_HOP_MODE)),
                cartDoctrine(profile.integer(TuningKey.BEHAVIOR_CART_DOCTRINE)),
                profile.flag(TuningKey.BEHAVIOR_USE_CARTING),
                profile.number(TuningKey.BEHAVIOR_ATTACK_RANGE).doubleValue(),
                bridgeMode(profile.integer(TuningKey.BEHAVIOR_BRIDGE_MODE)),
                cartMode(profile.integer(TuningKey.BEHAVIOR_CART_MODE)),
                profile.flag(TuningKey.BEHAVIOR_IGNORE_CREATIVE),
                profile.flag(TuningKey.BEHAVIOR_OPEN_DOORS),
                profile.flag(TuningKey.BEHAVIOR_WANDER_WHEN_IDLE),
                profile.flag(TuningKey.BEHAVIOR_USE_ENDER_PEARL),
                profile.flag(TuningKey.BEHAVIOR_PEARL_CHASING),
                profile.flag(TuningKey.BEHAVIOR_CHORUS_ESCAPE),
                profile.flag(TuningKey.BEHAVIOR_FIRE_ESCAPE),
                profile.flag(TuningKey.BEHAVIOR_LAVA_ESCAPE),
                profile.flag(TuningKey.BEHAVIOR_ENDER_CHEST_REGEAR),
                profile.flag(TuningKey.BEHAVIOR_SHULKER_STEAL),
                profile.flag(TuningKey.BEHAVIOR_SHULKER_REGEAR),
                profile.flag(TuningKey.BEHAVIOR_SPREAD_OUT),
                profile.flag(TuningKey.BEHAVIOR_WEB_ESCAPE),
                profile.flag(TuningKey.BEHAVIOR_WATER_BUCKET_CLUTCH),
                profile.flag(TuningKey.BEHAVIOR_USE_BOWS),
                profile.flag(TuningKey.BEHAVIOR_HIT_WEB),
                profile.flag(TuningKey.BEHAVIOR_PICKUP_LOOT),
                profile.number(TuningKey.BEHAVIOR_BUILD_UP_MIN_HEIGHT).doubleValue(),
                profile.flag(TuningKey.BEHAVIOR_SPEED_NO_JUMP),
                profile.flag(TuningKey.BEHAVIOR_MACE_TOTEM),
                profile.flag(TuningKey.BEHAVIOR_CHASE_SPREAD),
                profile.flag(TuningKey.BEHAVIOR_PLACE_BLOCKS_MOVING),
                profile.number(TuningKey.BEHAVIOR_LOOT_PACK_RADIUS).doubleValue(),
                healingMode(profile.integer(TuningKey.BEHAVIOR_HEALING_MODE)),
                offhandPreference(profile.integer(TuningKey.BEHAVIOR_OFFHAND_PREFERENCE)),
                profile.number(TuningKey.BEHAVIOR_OFFHAND_TOTEM_HEARTS).doubleValue(),
                profile.flag(TuningKey.BEHAVIOR_STRAFE_IN_COMBO),
                profile.flag(TuningKey.BEHAVIOR_USE_ORBITAL_STRIKE),
                profile.number(TuningKey.BEHAVIOR_RETREAT_HEALTH_HEARTS).doubleValue(),
                profile.flag(TuningKey.BEHAVIOR_USE_SCAFFOLDING),
                profile.flag(TuningKey.BEHAVIOR_USE_ELYTRA_MACE),
                profile.flag(TuningKey.BEHAVIOR_USE_ELYTRA_SPEAR),
                profile.flag(TuningKey.BEHAVIOR_USE_WEB_DRAIN),
                profile.integer(TuningKey.BEHAVIOR_WEB_DRAIN_CHANCE),
                profile.integer(TuningKey.BEHAVIOR_JUMP_RESET_CHANCE),
                profile.integer(TuningKey.BEHAVIOR_COUNTER_STUN_CHANCE),
                profile.flag(TuningKey.BEHAVIOR_SHIELD_ON_MACE_DIVE),
                profile.flag(TuningKey.BEHAVIOR_BUILD_FIGHT),
                TechniqueConfig.fromProfile(profile),
                chances);
    }

    private static BotSettings.BunnyHopMode bunnyHopMode(int code) {
        return switch (code) {
            case 1 -> BotSettings.BunnyHopMode.OFF;
            case 2 -> BotSettings.BunnyHopMode.ON;
            case 3 -> BotSettings.BunnyHopMode.SMART;
            default -> throw invalid(TuningKey.BEHAVIOR_BUNNY_HOP_MODE);
        };
    }

    private static BotSettings.CartDoctrine cartDoctrine(int code) {
        return switch (code) {
            case 1 -> BotSettings.CartDoctrine.GUIDE;
            case 2 -> BotSettings.CartDoctrine.RELENTLESS;
            default -> throw invalid(TuningKey.BEHAVIOR_CART_DOCTRINE);
        };
    }

    private static BotSettings.BridgeMode bridgeMode(int code) {
        return switch (code) {
            case 1 -> BotSettings.BridgeMode.NINJA;
            case 2 -> BotSettings.BridgeMode.GOD;
            case 3 -> BotSettings.BridgeMode.MOONWALK;
            default -> throw invalid(TuningKey.BEHAVIOR_BRIDGE_MODE);
        };
    }

    private static BotSettings.CartMode cartMode(int code) {
        return switch (code) {
            case 1 -> BotSettings.CartMode.PRE_CART;
            case 2 -> BotSettings.CartMode.PRE_RAIL;
            case 3 -> BotSettings.CartMode.INSTA_CART;
            case 4 -> BotSettings.CartMode.XBOW_CART;
            case 5 -> BotSettings.CartMode.SMART;
            default -> throw invalid(TuningKey.BEHAVIOR_CART_MODE);
        };
    }

    private static BotSettings.HealingMode healingMode(int code) {
        return switch (code) {
            case 1 -> BotSettings.HealingMode.ADVANCED;
            case 2 -> BotSettings.HealingMode.OLD;
            default -> throw invalid(TuningKey.BEHAVIOR_HEALING_MODE);
        };
    }

    private static BotSettings.OffhandPreference offhandPreference(int code) {
        return switch (code) {
            case 1 -> BotSettings.OffhandPreference.AUTO;
            case 2 -> BotSettings.OffhandPreference.TOTEM;
            case 3 -> BotSettings.OffhandPreference.SHIELD;
            default -> throw invalid(TuningKey.BEHAVIOR_OFFHAND_PREFERENCE);
        };
    }

    private static IllegalArgumentException invalid(TuningKey key) {
        return new IllegalArgumentException(key.key());
    }
}
