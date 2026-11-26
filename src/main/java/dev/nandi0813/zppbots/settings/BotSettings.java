package dev.nandi0813.zppbots.settings;

import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public final class BotSettings {
    public static final double VANILLA_ATTACK_RANGE = 3.0;

    private final Behavior behavior;
    private final Difficulty difficulty;
    private final DifficultyParams params;
    private final Random random;
    private final PlayStyle playStyle;
    private final BotTuning.Profile profile;
    private boolean retreatOrdered;
    private PlayStyle adaptiveResolved = PlayStyle.DEFAULT;

    /** The style comes from the match setup instead of {@code BEHAVIOR.PLAY-STYLE}. */
    public BotSettings(Behavior behavior, Difficulty difficulty, DifficultyParams params, Random random,
                       Behavior.PlayStyleSetting style, BotTuning.Profile profile) {
        this.behavior = behavior;
        this.difficulty = difficulty;
        this.params = params;
        this.random = random;
        this.profile = Objects.requireNonNull(profile, "profile");
        this.playStyle = resolvePlayStyle(behavior, random, style);
    }

    public static BotSettings fromProfile(
            BotTuning.Profile profile,
            int level,
            Behavior.PlayStyleSetting style,
            List<Behavior.PlayStyleSetting> playerPlayStyles,
            Random random) {
        Objects.requireNonNull(profile, "profile");
        Behavior behavior = Behavior.fromProfile(profile, style, playerPlayStyles);
        return new BotSettings(
                behavior,
                Difficulty.ofLevel(level),
                DifficultyParams.fromProfile(profile),
                random,
                style,
                profile);
    }

    private static PlayStyle resolvePlayStyle(Behavior behavior, Random random, Behavior.PlayStyleSetting setting) {
        if (setting == Behavior.PlayStyleSetting.RANDOM) {
            PlayStyle rolled = rollPlayStyle(behavior, random);
            return rolled == null ? PlayStyle.DEFAULT : rolled;
        }
        return PlayStyle.valueOf(setting.name());
    }

    private static PlayStyle rollPlayStyle(Behavior behavior, Random random) {
        int total = 0;
        for (int w : behavior.playStyleChances().values()) {
            total += Math.max(0, w);
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (PlayStyle style : PlayStyle.values()) {
            int w = Math.max(0, behavior.playStyleChances().getOrDefault(style, 0));
            if (w != 0) {
                if (roll < w) {
                    return style;
                }
                roll -= w;
            }
        }
        return PlayStyle.DEFAULT;
    }

    public PlayStyle adaptiveResolved() {
        return adaptiveResolved;
    }

    public void setAdaptiveResolved(PlayStyle style) {
        if (style != null && style != PlayStyle.ADAPTIVE) {
            adaptiveResolved = style;
        }
    }

    public boolean isAdaptive() {
        return playStyle == PlayStyle.ADAPTIVE;
    }

    /** The styles a player may pick in the setup menu. */
    public java.util.List<Behavior.PlayStyleSetting> playerPlayStyles() {
        return behavior.playerPlayStyles();
    }

    public PlayStyle playStyle() {
        return playStyle == PlayStyle.ADAPTIVE ? adaptiveResolved : playStyle;
    }

    /** The setup style as chosen (RANDOM already rolled); ADAPTIVE stays ADAPTIVE. */
    public PlayStyle configuredPlayStyle() {
        return playStyle;
    }

    public boolean retreat() {
        if (retreatOrdered()) {
            return true;
        }
        return switch (playStyle()) {
            case SAFE, DEFENSIVE -> true;
            case AGGRESSIVE -> false;
            default -> behavior.retreat();
        };
    }

    public boolean retreatOrdered() {
        return retreatOrdered;
    }

    public void setRetreatOrdered(boolean retreatOrdered) {
        this.retreatOrdered = retreatOrdered;
    }

    public boolean autoTarget() {
        return false;
    }

    public boolean targetPlayers() {
        return true;
    }

    public boolean targetBots() {
        return true;
    }

    public boolean targetMobs() {
        return false;
    }

    public boolean botLeaveOnDeath() {
        return false;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public DifficultyParams difficultyParams() {
        return params;
    }

    public double retreatHealthHearts() {
        double base = behavior.retreatHealthHearts();
        return switch (playStyle()) {
            case SAFE -> Math.min(
                    profile.number(TuningKey.STYLE_RETREAT_MAX).doubleValue(),
                    base + profile.number(TuningKey.STYLE_SAFE_RETREAT_ADD).doubleValue());
            case DEFENSIVE -> Math.min(
                    profile.number(TuningKey.STYLE_RETREAT_MAX).doubleValue(),
                    base + profile.number(TuningKey.STYLE_DEFENSIVE_RETREAT_ADD).doubleValue());
            case AGGRESSIVE -> Math.max(
                    profile.number(TuningKey.STYLE_RETREAT_MIN).doubleValue(),
                    base * profile.number(TuningKey.STYLE_AGGRESSIVE_RETREAT_MULTIPLIER).doubleValue());
            default -> base;
        };
    }

    public boolean useSpearDash() {
        return techniques().spearDash;
    }

    public boolean useStunSlam() {
        return techniques().stunSlam;
    }

    public int stunSlamChance() {
        return difficultyParams().stunSlamChance;
    }

    public boolean usePearlCatch() {
        return techniques().pearlCatch;
    }

    public TechniqueConfig.PearlCatchVariant pearlCatchType() {
        return techniques().pearlCatchVariant;
    }

    public boolean elytraGlide() {
        return techniques().pearlCatchGlide;
    }

    public float getAccuracy() {
        return (float) difficultyParams().accuracy;
    }

    public boolean roll() {
        return random.nextDouble() < getAccuracy();
    }

    public float getClutchAccuracy() {
        return (float) difficultyParams().clutchAccuracy;
    }

    public boolean rollClutch() {
        return random.nextDouble() < getClutchAccuracy();
    }

    public boolean rollWebDrain() {
        if (!useWebDrain()) {
            return false;
        }
        return rollPercent(webDrainChance());
    }

    public boolean rollJumpReset() {
        return rollPercent(jumpResetChance());
    }

    public boolean rollCounterStun() {
        return rollPercent(counterStunChance());
    }

    public boolean rollStunSlamChanceOnly() {
        return rollPercent(stunSlamChance());
    }

    public boolean rollStunSlam() {
        if (!useStunSlam()) {
            return false;
        }
        return rollPercent(stunSlamChance());
    }

    private boolean rollPercent(int chance) {
        if (chance <= 0) {
            return false;
        }
        return chance >= 100 || random.nextDouble() * 100.0 < chance;
    }

    public boolean equipArmor() {
        return behavior.equipArmor();
    }

    public boolean usePotions() {
        return behavior.usePotions();
    }

    public boolean useCobwebs() {
        return behavior.useCobwebs();
    }

    public BunnyHopMode bunnyHopMode() {
        return behavior.bunnyHopMode();
    }

    public boolean revengeMode() {
        return behavior.revengeMode();
    }

    public boolean useCarting() {
        return behavior.useCarting();
    }

    public BridgeMode bridgeMode() {
        return behavior.bridgeMode();
    }

    public double attackRange() {
        return behavior.attackRange();
    }

    public CartMode cartMode() {
        return behavior.cartMode();
    }

    public boolean ignoreCreative() {
        return behavior.ignoreCreative();
    }

    public boolean useEnderPearl() {
        return behavior.useEnderPearl();
    }

    public boolean pearlChasing() {
        return behavior.pearlChasing();
    }

    public boolean useScaffolding() {
        return behavior.useScaffolding();
    }

    public boolean buildFight() {
        return behavior.buildFight();
    }

    public boolean chorusEscape() {
        return behavior.chorusEscape();
    }

    public boolean fireEscape() {
        return behavior.fireEscape();
    }

    public boolean lavaEscape() {
        return behavior.lavaEscape();
    }

    public boolean shulkerSteal() {
        return behavior.shulkerSteal();
    }

    public boolean spreadOut() {
        return behavior.spreadOut();
    }

    public boolean shulkerRegear() {
        return behavior.shulkerRegear();
    }

    public boolean enderChestRegear() {
        return behavior.enderChestRegear();
    }

    public boolean webEscape() {
        return behavior.webEscape();
    }

    public boolean waterBucketClutch() {
        return behavior.waterBucketClutch();
    }

    public boolean hitWeb() {
        return behavior.hitWeb();
    }

    public boolean pickupLoot() {
        return behavior.pickupLoot();
    }

    public boolean speedNoJump() {
        return behavior.speedNoJump();
    }

    public boolean maceTotem() {
        return behavior.maceTotem();
    }

    public boolean chaseSpread() {
        return behavior.chaseSpread();
    }

    public boolean placeBlocksMoving() {
        return behavior.placeBlocksMoving();
    }

    public double buildUpMinHeight() {
        return behavior.buildUpMinHeight();
    }

    public double lootPackRadius() {
        return behavior.lootPackRadius();
    }

    public HealingMode healingMode() {
        return behavior.healingMode();
    }

    public OffhandPreference offhandPreference() {
        return behavior.offhandPreference();
    }

    public double offhandTotemHearts() {
        return behavior.offhandTotemHearts();
    }

    public boolean strafeInCombo() {
        return behavior.strafeInCombo();
    }

    public CartDoctrine cartDoctrine() {
        return behavior.cartDoctrine();
    }

    public boolean useBows() {
        return behavior.useBows();
    }

    public boolean useOrbitalStrike() {
        return behavior.useOrbitalStrike();
    }

    public boolean wanderWhenIdle() {
        return behavior.wanderWhenIdle();
    }

    public boolean useElytraMace() {
        return behavior.useElytraMace();
    }

    public boolean useElytraSpear() {
        return behavior.useElytraSpear();
    }

    public boolean useWebDrain() {
        return behavior.useWebDrain();
    }

    public int webDrainChance() {
        return behavior.webDrainChance();
    }

    public int jumpResetChance() {
        return behavior.jumpResetChance();
    }

    public int counterStunChance() {
        return behavior.counterStunChance();
    }

    public boolean shieldOnMaceDive() {
        return behavior.shieldOnMaceDive();
    }

    public boolean openDoors() {
        return behavior.openDoors();
    }

    public TechniqueConfig techniques() {
        return behavior.techniques();
    }

    public enum BridgeMode {
        NINJA,
        GOD,
        MOONWALK
    }

    public enum BunnyHopMode {
        OFF,
        ON,
        SMART
    }

    public enum CartDoctrine {
        GUIDE,
        RELENTLESS
    }

    public enum CartMode {
        PRE_CART,
        PRE_RAIL,
        INSTA_CART,
        XBOW_CART,
        SMART
    }

    public enum HealingMode {
        ADVANCED,
        OLD
    }

    public enum OffhandPreference {
        AUTO,
        TOTEM,
        SHIELD
    }

    public enum PlayStyle {
        DEFAULT,
        SAFE,
        AGGRESSIVE,
        ADAPTIVE,
        DEFENSIVE,
        BALANCED
    }
}
