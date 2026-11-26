package dev.nandi0813.zppbots.brain;

import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.settings.BotSettings;
import dev.nandi0813.zppbots.team.perf.BotAiTier;
import dev.nandi0813.zppbots.team.perf.BotAiTierResolver;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.entity.Player;

final class ReadAndEscapePhase {

    private static final String COMBAT_STATUS = "COMBAT_STATUS";

    final BotBrain brain;
    private final BotContext ctx;
    private final Player bot;

    ReadAndEscapePhase(BotBrain brain) {
        this.brain = brain;
        this.ctx = brain.ctx;
        this.bot = brain.bot;
    }

    /** Bukkit has no hurtTime: the invulnerability counter jumps on every accepted hit, as in FightState. */
    static int hurtTime(Player p) {
        return p.getNoDamageTicks();
    }

    /** {@code hurtTime == 0}: the 10-tick hurt animation is the first half of the invulnerability window. */
    static boolean hurtAnimationOver(Player p) {
        return p.getNoDamageTicks() <= p.getMaximumNoDamageTicks() / 2;
    }

    /** Offhand shield/totem hysteresis: drop the shield at or below {@code lowHp}, take it back the tuned margin above. */
    static boolean nextShieldMode(BotTuning.Profile profile, boolean shieldMode, float hp, float lowHp) {
        if (shieldMode) {
            return hp > lowHp;
        }

        return hp >= lowHp + profile.number(TuningKey.READ_SHIELD_RETURN_HP).floatValue();
    }

    boolean tickReadAndEscape() {
        BotSettings es = ctx.settings();
        brain.attackRange = Math.min(es.attackRange(), BotSettings.VANILLA_ATTACK_RANGE);
        float offhandHp = (float) bot.getHealth();
        BotSettings.OffhandPreference offhandPref = es.offhandPreference();
        if (offhandPref == BotSettings.OffhandPreference.TOTEM) {
            brain.offhandShieldMode = false;
            brain.offhandWantShield = false;
        } else if (offhandPref == BotSettings.OffhandPreference.SHIELD) {
            float totemHp = (float) (es.offhandTotemHearts() * 2.0);
            brain.offhandShieldMode = nextShieldMode(ctx.tuning(), brain.offhandShieldMode, offhandHp, totemHp);
            brain.offhandWantShield = brain.offhandShieldMode;
        } else {
            float offhandLowHp = (float) (es.retreatHealthHearts() * 2.0);
            brain.offhandShieldMode = nextShieldMode(ctx.tuning(), brain.offhandShieldMode, offhandHp, offhandLowHp);
            brain.offhandWantShield = brain.offhandShieldMode && brain.melee.isPureSwordKit();
        }

        if (!brain.offhandWantShield) {
            brain.offhandManager.tickTotem();
        }

        brain.enderPearlController.tickPearlState(brain.target);
        boolean sleepSkip = brain.aiTier == BotAiTier.SLEEP && brain.target == null && hurtAnimationOver(bot) && (brain.tickCount & 3) != 0;
        if (!sleepSkip) {
            brain.target = brain.targeting.findTarget();
        }

        brain.dist = brain.target != null ? Ent.dist(bot, brain.target) : Double.MAX_VALUE;
        brain.fightState.tick(brain.target);
        if (!brain.fightState.beingCombod()) {
            brain.uppercutRolledChain = -1;
        }

        boolean macePvpActive = brain.maceController.isExecuting()
                || brain.elytraMaceController.isExecuting()
                || brain.tridentMaceController.isExecuting()
                || brain.elytraSpearController.isExecuting()
                || brain.spearMaceController.isExecuting()
                || brain.pearlCatchController.isExecuting()
                || brain.rocketlessElytraController.isExecuting()
                || brain.windBurstElytraController.isExecuting();
        brain.specialExecuting = macePvpActive
                || brain.cartController.isExecuting()
                || brain.crystalPvpController.isExecuting()
                || brain.anchorPvpController.isExecuting()
                || brain.spearMaceController.isExecuting()
                || brain.elytraMaceController.isExecuting()
                || brain.windBurstElytraController.isExecuting()
                || brain.tridentMaceController.isExecuting()
                || brain.pearlCatchController.isExecuting();
        if (!sleepSkip) {
            brain.aiTier = BotAiTierResolver.resolve(ctx, brain.target, brain.dist, macePvpActive, brain.specialExecuting);
        }

        brain.cpvpMode = brain.aiTier == BotAiTier.FULL && brain.specials.inCrystalPvpMode(brain.target);
        boolean freshHit = hurtTime(bot) > brain.prevHurtTime;
        brain.prevHurtTime = hurtTime(bot);
        if (freshHit) {
            brain.specials.noteBlastFrom(brain.target);
            brain.kbYieldWindow = 1;
            brain.comboLockHits++;
            brain.comboLockDecay = ctx.tuning().integer(TuningKey.READ_COMBO_LOCK_DECAY_TICKS);
            brain.melee.chooseHitReaction(brain.cpvpMode, brain.specialExecuting);
            if (brain.hitReact == 0 && brain.counterStunWindow == 0 && brain.melee.isHardIncomingHit() && es.rollCounterStun()) {
                brain.counterStunWindow = ctx.tuning().integer(TuningKey.READ_COUNTER_STUN_TICKS);
                brain.sprintHitHold = 0;
                brain.releaseAfterHit = 0;
                brain.melee.sprintOffForCrit();
                if (bot.isOnGround()) {
                    ctx.controls().jumpOnce();
                }
            }
        } else if (brain.kbYieldWindow > 0) {
            brain.kbYieldWindow--;
        }

        if (brain.counterStunWindow > 0) {
            brain.counterStunWindow--;
        }

        if (brain.hitReactTicks > 0 && --brain.hitReactTicks == 0) {
            brain.hitReact = 0;
        }

        if (brain.noKbHoldW > 0 && brain.kbYieldWindow == 0) {
            brain.noKbHoldW--;
        }

        if (brain.comboLockDecay > 0) {
            brain.comboLockDecay--;
        } else {
            brain.comboLockHits = 0;
        }

        brain.jumpResetPending = false;
        if (brain.cpvpMode || brain.specialExecuting) {
            brain.crouchDodgeTicks = 0;
        }

        ctx.controls().setSneaking(brain.crouchDodgeTicks > 0);
        if (brain.crouchDodgeTicks > 0) {
            brain.crouchDodgeTicks--;
        }

        if (brain.aiTier != BotAiTier.SLEEP && brain.tickCount % 100 == 0) {
            ctx.log().decision("ReadAndEscapePhase", COMBAT_STATUS, "hp=" + bot.getHealth() + " | tier=" + brain.aiTier + " | revengeTarget=" + brain.revengeTargetId
                    + " | autoTarget=" + es.autoTarget() + " | lastHurtBy=" + ctx.match().lastAttackerOf(bot, ctx.tick())
                    + " | playerCount=" + ctx.world().players().size());
        }

        if (brain.prevMacePvpActive && !macePvpActive) {
            brain.waterBucketClutchController.suppressThisFall();
        }

        brain.prevMacePvpActive = macePvpActive;
        if (!macePvpActive && !brain.cpvpMode && brain.waterBucketClutchController.tick()) {
            return true;
        } else if (!macePvpActive && brain.blockClutchController.tick()) {
            return true;
        } else if (brain.fireEscapeBusy) {
            return true;
        } else if (es.fireEscape() && brain.fireEscapeController.tick()) {
            return true;
        } else if (es.lavaEscape() && brain.fireEscapeController.walkOutOfLava()) {
            return true;
        } else {
            return !brain.bubbleWebController.isActive() && brain.chorusEscapeController.tick()
                    ? true
                    : !brain.bubbleWebController.isActive() && brain.cobwebEscapeController.tick();
        }
    }
}
