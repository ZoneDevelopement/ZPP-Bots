package dev.nandi0813.zppbots.ai.melee;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class FightState {

    private final BotContext ctx;
    private int targetId = -1;
    private boolean inFight = false;
    private boolean firstHitDone = false;
    private int lastExchangeTick = Integer.MIN_VALUE;
    private int tick = 0;
    private int fightStartTick = 0;
    private int lastTargetHurt = 0;
    private int lastSelfHurt = 0;
    private int ourChain = 0;
    private int ourLastHitTick = Integer.MIN_VALUE;
    private int theirChain = 0;
    private int theirLastHitTick = Integer.MIN_VALUE;
    private int ourLongestChain = 0;

    public FightState(BotContext ctx) {
        this.ctx = ctx;
    }

    /** Bukkit has no hurtTime; the invulnerability counter jumps on every accepted hit, which is what the hurt-time check reads. */
    private static int hurtTime(LivingEntity entity) {
        return entity.getNoDamageTicks();
    }

    public void tick(LivingEntity target) {
        LivingEntity bot = ctx.player();
        tick++;
        if (target == null) {
            inFight = false;
            targetId = -1;
            resetChains();
        } else if (target.getEntityId() != targetId) {
            targetId = target.getEntityId();
            inFight = true;
            firstHitDone = false;
            fightStartTick = tick;
            lastExchangeTick = Integer.MIN_VALUE;
            lastTargetHurt = hurtTime(target);
            lastSelfHurt = hurtTime(bot);
            resetChains();
        } else {
            inFight = true;
            boolean theyHurt = hurtTime(target) > lastTargetHurt;
            boolean weHurt = hurtTime(bot) > lastSelfHurt;
            lastTargetHurt = hurtTime(target);
            lastSelfHurt = hurtTime(bot);
            if (theyHurt || weHurt) {
                lastExchangeTick = tick;
                firstHitDone = true;
            }

            boolean theyGotHit = theyHurt && damagedBy(target, bot);
            boolean weGotHit = weHurt && damagedBy(bot, target);
            if (tick - ourLastHitTick > ctx.tuning().integer(TuningKey.FIGHT_COMBO_LINK_TICKS)) {
                ourChain = 0;
            }
            if (tick - theirLastHitTick > ctx.tuning().integer(TuningKey.FIGHT_COMBO_LINK_TICKS)) {
                theirChain = 0;
            }

            if (theyGotHit && weGotHit) {
                ourChain = 0;
                theirChain = 0;
            } else if (theyGotHit) {
                ourChain++;
                ourLastHitTick = tick;
                theirChain = 0;
                if (ourChain > ourLongestChain) {
                    ourLongestChain = ourChain;
                }
            } else if (weGotHit) {
                theirChain++;
                theirLastHitTick = tick;
                ourChain = 0;
            }
        }
    }

    private static boolean damagedBy(LivingEntity victim, LivingEntity attacker) {
        EntityDamageEvent cause = victim.getLastDamageCause();
        if (!(cause instanceof EntityDamageByEntityEvent byEntity)) {
            return false;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter == attacker;
        }
        return damager == attacker;
    }

    private void resetChains() {
        ourChain = theirChain = ourLongestChain = 0;
        ourLastHitTick = theirLastHitTick = Integer.MIN_VALUE;
    }

    public boolean inFight() {
        return inFight;
    }

    public boolean firstHitDone() {
        return firstHitDone;
    }

    public boolean inEncounter() {
        return inFight && tick - lastExchangeTick <= ctx.tuning().integer(TuningKey.FIGHT_ENCOUNTER_TICKS);
    }

    public boolean isOpening() {
        return inFight && !firstHitDone && tick - fightStartTick <= ctx.tuning().integer(TuningKey.FIGHT_OPENING_TICKS);
    }

    public int comboHits() {
        return ourChain;
    }

    public int incomingComboHits() {
        return theirChain;
    }

    public boolean comboing() {
        return ourChain >= ctx.tuning().integer(TuningKey.FIGHT_COMBO_MIN_HITS);
    }

    public boolean beingCombod() {
        return theirChain >= ctx.tuning().integer(TuningKey.FIGHT_COMBO_MIN_HITS);
    }

    public int ticksSinceOurHit() {
        return ourLastHitTick == Integer.MIN_VALUE ? Integer.MAX_VALUE : tick - ourLastHitTick;
    }

    public int ticksSinceHitTaken() {
        return theirLastHitTick == Integer.MIN_VALUE ? Integer.MAX_VALUE : tick - theirLastHitTick;
    }

    public int longestCombo() {
        return ourLongestChain;
    }
}
