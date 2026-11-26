package dev.nandi0813.zppbots.settings;

import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;

public final class TechniqueConfig {
    public boolean maceSwap;
    public boolean spearMaceSwap;
    public boolean swordWbSwap;
    public boolean breachSwap;
    public boolean spearReachSwap;
    public boolean windchargeMace;
    public boolean wcCrouchBoost;
    public boolean wcFullCooldownDiscipline;
    public HitPreference wcHitPreference;
    public boolean hotbarShieldTracking;
    public boolean elytraWcBoost;
    public boolean spearDash;
    public boolean dashChain;
    public boolean spearMacing;
    public boolean wcReset;
    public boolean wcSelfReset;
    public boolean pearlCatch;
    public PearlCatchVariant pearlCatchVariant;
    public boolean pearlCatchGlide;
    public boolean pearlGrapple;
    public boolean diveBomb;
    public boolean elytraStunSlam;
    public boolean elytraPreserve;
    public boolean groundBackstab;
    public boolean elytraBackstab;
    public boolean stunSlam;
    public StunSlamVariant stunSlamVariant;
    public boolean spearStunSlam;
    public boolean shieldDrain;
    public boolean detapSpearMace;
    public boolean detapBreach;
    public boolean spearDoublePop;
    public boolean spearDoublePopOffhand;
    public boolean wcCancel;
    public boolean rocketMace;
    public RocketMaceVariant rocketMaceVariant;

    public static TechniqueConfig fromProfile(BotTuning.Profile profile) {
        TechniqueConfig techniques = new TechniqueConfig();
        techniques.maceSwap = profile.flag(TuningKey.TECHNIQUE_MACE_SWAP);
        techniques.spearMaceSwap = profile.flag(TuningKey.TECHNIQUE_SPEAR_MACE_SWAP);
        techniques.swordWbSwap = profile.flag(TuningKey.TECHNIQUE_SWORD_WB_SWAP);
        techniques.breachSwap = profile.flag(TuningKey.TECHNIQUE_BREACH_SWAP);
        techniques.spearReachSwap = profile.flag(TuningKey.TECHNIQUE_SPEAR_REACH_SWAP);
        techniques.windchargeMace = profile.flag(TuningKey.TECHNIQUE_WINDCHARGE_MACE);
        techniques.wcCrouchBoost = profile.flag(TuningKey.TECHNIQUE_WC_CROUCH_BOOST);
        techniques.wcFullCooldownDiscipline = profile.flag(TuningKey.TECHNIQUE_WC_FULL_COOLDOWN_DISCIPLINE);
        techniques.wcHitPreference = hitPreference(profile.integer(TuningKey.TECHNIQUE_WC_HIT_PREFERENCE));
        techniques.hotbarShieldTracking = profile.flag(TuningKey.TECHNIQUE_HOTBAR_SHIELD_TRACKING);
        techniques.elytraWcBoost = profile.flag(TuningKey.TECHNIQUE_ELYTRA_WC_BOOST);
        techniques.spearDash = profile.flag(TuningKey.TECHNIQUE_SPEAR_DASH);
        techniques.dashChain = profile.flag(TuningKey.TECHNIQUE_DASH_CHAIN);
        techniques.spearMacing = profile.flag(TuningKey.TECHNIQUE_SPEAR_MACING);
        techniques.wcReset = profile.flag(TuningKey.TECHNIQUE_WC_RESET);
        techniques.wcSelfReset = profile.flag(TuningKey.TECHNIQUE_WC_SELF_RESET);
        techniques.pearlCatch = profile.flag(TuningKey.TECHNIQUE_PEARL_CATCH);
        techniques.pearlCatchVariant = pearlCatchVariant(profile.integer(TuningKey.TECHNIQUE_PEARL_CATCH_VARIANT));
        techniques.pearlCatchGlide = profile.flag(TuningKey.TECHNIQUE_PEARL_CATCH_GLIDE);
        techniques.pearlGrapple = profile.flag(TuningKey.TECHNIQUE_PEARL_GRAPPLE);
        techniques.diveBomb = profile.flag(TuningKey.TECHNIQUE_DIVE_BOMB);
        techniques.elytraStunSlam = profile.flag(TuningKey.TECHNIQUE_ELYTRA_STUN_SLAM);
        techniques.elytraPreserve = profile.flag(TuningKey.TECHNIQUE_ELYTRA_PRESERVE);
        techniques.groundBackstab = profile.flag(TuningKey.TECHNIQUE_GROUND_BACKSTAB);
        techniques.elytraBackstab = profile.flag(TuningKey.TECHNIQUE_ELYTRA_BACKSTAB);
        techniques.stunSlam = profile.flag(TuningKey.TECHNIQUE_STUN_SLAM);
        techniques.stunSlamVariant = stunSlamVariant(profile.integer(TuningKey.TECHNIQUE_STUN_SLAM_VARIANT));
        techniques.spearStunSlam = profile.flag(TuningKey.TECHNIQUE_SPEAR_STUN_SLAM);
        techniques.shieldDrain = profile.flag(TuningKey.TECHNIQUE_SHIELD_DRAIN);
        techniques.detapSpearMace = profile.flag(TuningKey.TECHNIQUE_DETAP_SPEAR_MACE);
        techniques.detapBreach = profile.flag(TuningKey.TECHNIQUE_DETAP_BREACH);
        techniques.spearDoublePop = profile.flag(TuningKey.TECHNIQUE_SPEAR_DOUBLE_POP);
        techniques.spearDoublePopOffhand = profile.flag(TuningKey.TECHNIQUE_SPEAR_DOUBLE_POP_OFFHAND);
        techniques.wcCancel = profile.flag(TuningKey.TECHNIQUE_WC_CANCEL);
        techniques.rocketMace = profile.flag(TuningKey.TECHNIQUE_ROCKET_MACE);
        techniques.rocketMaceVariant = rocketMaceVariant(profile.integer(TuningKey.TECHNIQUE_ROCKET_MACE_VARIANT));
        return techniques;
    }

    private static HitPreference hitPreference(int code) {
        return switch (code) {
            case 1 -> HitPreference.VERTICAL;
            case 2 -> HitPreference.HORIZONTAL;
            case 3 -> HitPreference.AUTO;
            default -> throw invalid(TuningKey.TECHNIQUE_WC_HIT_PREFERENCE);
        };
    }

    private static PearlCatchVariant pearlCatchVariant(int code) {
        return switch (code) {
            case 1 -> PearlCatchVariant.SMART;
            case 2 -> PearlCatchVariant.STANDARD;
            case 3 -> PearlCatchVariant.HIGHER;
            case 4 -> PearlCatchVariant.INSTANT;
            case 5 -> PearlCatchVariant.ANGLE;
            default -> throw invalid(TuningKey.TECHNIQUE_PEARL_CATCH_VARIANT);
        };
    }

    private static StunSlamVariant stunSlamVariant(int code) {
        return switch (code) {
            case 1 -> StunSlamVariant.OPTIMAL;
            case 2 -> StunSlamVariant.EASY;
            case 3 -> StunSlamVariant.LEGACY_1TICK;
            default -> throw invalid(TuningKey.TECHNIQUE_STUN_SLAM_VARIANT);
        };
    }

    private static RocketMaceVariant rocketMaceVariant(int code) {
        return switch (code) {
            case 1 -> RocketMaceVariant.V1_CLIMB_DROP;
            case 2 -> RocketMaceVariant.V2_BOOST_TURN_HIT;
            case 3 -> RocketMaceVariant.V3_UNEQUIP_TURN_REEQUIP;
            case 4 -> RocketMaceVariant.V4_WINDBURST_LOOP;
            default -> throw invalid(TuningKey.TECHNIQUE_ROCKET_MACE_VARIANT);
        };
    }

    private static IllegalArgumentException invalid(TuningKey key) {
        return new IllegalArgumentException(key.key());
    }

    public void setBackstab(boolean on) {
        this.groundBackstab = on;
        this.elytraBackstab = on;
        if (on) {
            this.stunSlam = false;
            this.spearStunSlam = false;
            this.elytraStunSlam = false;
        }
    }

    public void setStunSlam(boolean on) {
        this.stunSlam = on;
        if (on) {
            this.groundBackstab = false;
            this.elytraBackstab = false;
        }
    }

    public void setGroundBackstab(boolean on) {
        this.groundBackstab = on;
        if (on) {
            this.stunSlam = false;
            this.spearStunSlam = false;
            this.elytraStunSlam = false;
        }
    }

    public void setElytraBackstab(boolean on) {
        this.elytraBackstab = on;
        if (on) {
            this.stunSlam = false;
            this.spearStunSlam = false;
            this.elytraStunSlam = false;
        }
    }

    public void setSpearStunSlam(boolean on) {
        this.spearStunSlam = on;
        if (on) {
            this.groundBackstab = false;
            this.elytraBackstab = false;
        }
    }

    public void setElytraStunSlam(boolean on) {
        this.elytraStunSlam = on;
        if (on) {
            this.groundBackstab = false;
            this.elytraBackstab = false;
        }
    }

    public boolean backstabEnabled() {
        return this.groundBackstab || this.elytraBackstab;
    }

    public boolean stunSlamEnabled() {
        return this.stunSlam || this.spearStunSlam || this.elytraStunSlam;
    }

    public TechniqueConfig copy() {
        TechniqueConfig c = new TechniqueConfig();
        c.maceSwap = this.maceSwap;
        c.spearMaceSwap = this.spearMaceSwap;
        c.swordWbSwap = this.swordWbSwap;
        c.breachSwap = this.breachSwap;
        c.spearReachSwap = this.spearReachSwap;
        c.windchargeMace = this.windchargeMace;
        c.wcCrouchBoost = this.wcCrouchBoost;
        c.wcFullCooldownDiscipline = this.wcFullCooldownDiscipline;
        c.wcHitPreference = this.wcHitPreference;
        c.hotbarShieldTracking = this.hotbarShieldTracking;
        c.elytraWcBoost = this.elytraWcBoost;
        c.spearDash = this.spearDash;
        c.dashChain = this.dashChain;
        c.spearMacing = this.spearMacing;
        c.wcReset = this.wcReset;
        c.wcSelfReset = this.wcSelfReset;
        c.pearlCatch = this.pearlCatch;
        c.pearlCatchVariant = this.pearlCatchVariant;
        c.pearlCatchGlide = this.pearlCatchGlide;
        c.pearlGrapple = this.pearlGrapple;
        c.diveBomb = this.diveBomb;
        c.elytraStunSlam = this.elytraStunSlam;
        c.elytraPreserve = this.elytraPreserve;
        c.groundBackstab = this.groundBackstab;
        c.elytraBackstab = this.elytraBackstab;
        c.stunSlam = this.stunSlam;
        c.stunSlamVariant = this.stunSlamVariant;
        c.spearStunSlam = this.spearStunSlam;
        c.shieldDrain = this.shieldDrain;
        c.detapSpearMace = this.detapSpearMace;
        c.detapBreach = this.detapBreach;
        c.spearDoublePop = this.spearDoublePop;
        c.spearDoublePopOffhand = this.spearDoublePopOffhand;
        c.wcCancel = this.wcCancel;
        c.rocketMace = this.rocketMace;
        c.rocketMaceVariant = this.rocketMaceVariant;
        return c;
    }

    public enum HitPreference {
        VERTICAL,
        HORIZONTAL,
        AUTO;
    }

    public enum PearlCatchVariant {
        SMART,
        STANDARD,
        HIGHER,
        INSTANT,
        ANGLE;
    }

    public enum RocketMaceVariant {
        V1_CLIMB_DROP,
        V2_BOOST_TURN_HIT,
        V3_UNEQUIP_TURN_REEQUIP,
        V4_WINDBURST_LOOP;
    }

    public enum StunSlamVariant {
        OPTIMAL,
        EASY,
        LEGACY_1TICK;
    }
}
