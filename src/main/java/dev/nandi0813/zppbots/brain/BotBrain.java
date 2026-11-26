package dev.nandi0813.zppbots.brain;

import dev.nandi0813.zppbots.ai.buildfight.BuildFightController;
import dev.nandi0813.zppbots.ai.elytra.ElytraFollowController;
import dev.nandi0813.zppbots.ai.elytra.ElytraMaceController;
import dev.nandi0813.zppbots.ai.elytra.ElytraSpearController;
import dev.nandi0813.zppbots.ai.elytra.RocketlessElytraController;
import dev.nandi0813.zppbots.ai.elytra.WindBurstElytraController;
import dev.nandi0813.zppbots.ai.escape.BlockClutchController;
import dev.nandi0813.zppbots.ai.escape.ChorusEscapeController;
import dev.nandi0813.zppbots.ai.escape.CobwebEscapeController;
import dev.nandi0813.zppbots.ai.escape.FireEscapeController;
import dev.nandi0813.zppbots.ai.escape.WaterBucketClutchController;
import dev.nandi0813.zppbots.ai.explosive.AnchorPvpController;
import dev.nandi0813.zppbots.ai.explosive.CartController;
import dev.nandi0813.zppbots.ai.explosive.CrystalPvpController;
import dev.nandi0813.zppbots.ai.gear.ArmorManager;
import dev.nandi0813.zppbots.ai.gear.InvisibleSuitUp;
import dev.nandi0813.zppbots.ai.gear.LoadoutManager;
import dev.nandi0813.zppbots.ai.gear.OffhandManager;
import dev.nandi0813.zppbots.ai.gear.RestockController;
import dev.nandi0813.zppbots.ai.gear.ShulkerRaidController;
import dev.nandi0813.zppbots.ai.heal.HealingController;
import dev.nandi0813.zppbots.ai.heal.MendingController;
import dev.nandi0813.zppbots.ai.heal.PotionController;
import dev.nandi0813.zppbots.ai.mace.MaceController;
import dev.nandi0813.zppbots.ai.mace.MaceDiveForecast;
import dev.nandi0813.zppbots.ai.mace.MaceTotemGuard;
import dev.nandi0813.zppbots.ai.mace.OrbitalStrikeController;
import dev.nandi0813.zppbots.ai.mace.SpearMaceController;
import dev.nandi0813.zppbots.ai.mace.TridentMaceController;
import dev.nandi0813.zppbots.ai.melee.ApproachStyle;
import dev.nandi0813.zppbots.ai.melee.AxeController;
import dev.nandi0813.zppbots.ai.melee.ComboBreakController;
import dev.nandi0813.zppbots.ai.melee.FightState;
import dev.nandi0813.zppbots.ai.melee.ShieldController;
import dev.nandi0813.zppbots.ai.melee.StrafeController;
import dev.nandi0813.zppbots.ai.melee.SwapTechniques;
import dev.nandi0813.zppbots.ai.melee.SwingReach;
import dev.nandi0813.zppbots.ai.melee.WTapController;
import dev.nandi0813.zppbots.ai.melee.WeaponSelector;
import dev.nandi0813.zppbots.ai.move.BoatTravelController;
import dev.nandi0813.zppbots.ai.move.BunnyHopController;
import dev.nandi0813.zppbots.ai.move.IdleHumanizer;
import dev.nandi0813.zppbots.ai.move.JumpPlaceController;
import dev.nandi0813.zppbots.ai.move.PathFollower;
import dev.nandi0813.zppbots.ai.move.ScaffoldController;
import dev.nandi0813.zppbots.ai.move.TunnelController;
import dev.nandi0813.zppbots.ai.move.WanderController;
import dev.nandi0813.zppbots.ai.ranged.ArrowDodge;
import dev.nandi0813.zppbots.ai.ranged.CrossbowChargeController;
import dev.nandi0813.zppbots.ai.ranged.EnderPearlController;
import dev.nandi0813.zppbots.ai.ranged.PearlCatchController;
import dev.nandi0813.zppbots.ai.ranged.RangedController;
import dev.nandi0813.zppbots.ai.web.BubbleWebController;
import dev.nandi0813.zppbots.ai.web.CobwebTrapController;
import dev.nandi0813.zppbots.ai.web.HeadWebController;
import dev.nandi0813.zppbots.ai.web.PredictionWebController;
import dev.nandi0813.zppbots.ai.web.WebDrainController;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.BotSettings;
import dev.nandi0813.zppbots.team.MatchSquads;
import dev.nandi0813.zppbots.team.perf.BotAiTier;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.ElytraFlight;
import dev.nandi0813.zppbots.util.MeleeWeaponGuard;
import dev.nandi0813.zppbots.util.UseSlowdown;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The one object per bot that owns every controller and runs the fight phases.
 * Phases live in this package and reach the state below directly.
 */
public final class BotBrain {

    final BotContext ctx;
    final Player bot;

    double attackRange = BotSettings.VANILLA_ATTACK_RANGE;

    final WTapController wTap;
    final PathFollower pathFollower;
    final ArmorManager armorManager;
    final OffhandManager offhandManager;
    final InvisibleSuitUp suitUp;
    final ShieldController shieldController;
    final WeaponSelector weaponSelector;
    final RangedController rangedController;
    final HealingController healingController;
    final LoadoutManager loadoutManager;
    final StrafeController strafeController;
    final ArrowDodge arrowDodge;
    final ApproachStyle approachStyle;
    final FightState fightState;
    final SwingReach swingReach;
    final PotionController potionController;
    final BunnyHopController bunnyHopController;
    final CobwebEscapeController cobwebEscapeController;
    final ChorusEscapeController chorusEscapeController;
    final BubbleWebController bubbleWebController;
    final RocketlessElytraController rocketlessElytraController;
    final FireEscapeController fireEscapeController;
    final CobwebTrapController cobwebTrapController;
    final WaterBucketClutchController waterBucketClutchController;
    final BlockClutchController blockClutchController;
    final WebDrainController webDrainController;
    final ElytraMaceController elytraMaceController;
    final ElytraSpearController elytraSpearController;
    final WindBurstElytraController windBurstElytraController;
    final TridentMaceController tridentMaceController;
    final MaceController maceController;
    final SpearMaceController spearMaceController;
    boolean lastMaceComboWasSpear = false;
    final PearlCatchController pearlCatchController;
    final PredictionWebController predictionWebController;
    final HeadWebController headWebController;
    final MendingController mendingController;
    int mendDisengageTicks = 0;
    int mendDisengageCooldown = 0;
    boolean mendIntent = false;
    final ScaffoldController scaffoldController;
    final BuildFightController buildFight;
    final BoatTravelController boatTravelController;
    final JumpPlaceController jumpPlaceController;
    final TunnelController tunnelController;
    final OrbitalStrikeController orbitalStrikeController;
    final MaceDiveForecast diveForecast;
    final MaceTotemGuard maceTotemGuard;
    LivingEntity fallDiver = null;
    final IdleHumanizer idleHumanizer;
    boolean maceTotemUp = false;
    final CartController cartController;
    final SwapTechniques swapTechniques;
    int blockingTicks = 0;
    int backstabTicks = 0;
    int shieldHoldTicks = 0;
    int bowShotBase = 0;
    int idleNavTick = Integer.MIN_VALUE;
    int shieldReraiseCd = 0;
    final CrossbowChargeController crossbowChargeController;
    final CrystalPvpController crystalPvpController;
    final AnchorPvpController anchorPvpController;
    final EnderPearlController enderPearlController;
    final ComboBreakController comboBreakController;
    final ElytraFollowController elytraFollowController;
    final RestockController restockController;
    final ShulkerRaidController shulkerRaidController;
    final WanderController wanderController;
    final AxeController axeController;

    LivingEntity target;
    int stanceStuckTicks = 0;
    int consumableHeldTicks = 0;
    int tickCount;
    boolean prevMacePvpActive = false;
    LivingEntity lastCombatTarget = null;
    int killLootTicks = 0;
    Vec3 killLootPos = null;
    final Set<UUID> ownJunkDrops = new HashSet<>();
    UUID containerPickupId = null;
    int containerPickupTicks = 0;
    boolean offhandWantShield = false;
    boolean offhandShieldMode = true;
    UUID revengeTargetId = null;
    boolean jumpedForCrit = false;
    int critJumpTick = 0;
    int critSwingHold = 0;
    boolean pendingAttack = false;
    boolean swingIsMiss = false;
    float meleeForwardImpulse = 1.0F;
    float meleeStrafeImpulse = 0.0F;
    int dodgeTicksLeft = 0;
    int fallShieldTicksLeft = 0;
    boolean fallDiveActive = false;
    int fallShieldHoldTicks = 0;
    float dodgeYaw = 0.0F;
    boolean fallShieldUsesOffhand = false;
    int mainHandShieldSlot = -1;
    int mainHandShieldSrc = -1;
    boolean wTapThisCycle = true;
    boolean critThisCycle = true;
    boolean comboThisCycle = false;
    int reactionCountdown = -1;
    int healPunishWindow = 0;
    int armorPunishWindow = 0;
    int targetPingMs = 0;
    boolean targetHighPing = false;
    int answerSwingWindow = 0;
    float prevTargCharge = 0.0F;
    int hitSelectHold = 0;
    boolean hitSelectThisCycle = false;
    int oppJumpResets = 0;
    int uppercutRolledChain = -1;
    boolean uppercutThisChain = false;
    int jrJitter = 0;
    int noKbHoldW = 0;
    int prevTargetHurtTime = 0;
    int targetHitObserve = 0;
    float comboCadenceBoost = 0.0F;
    int releaseAfterHit = 0;
    int sprintHitHold = 0;
    int shieldBaitCooldown = 0;
    int overextendWindow = 0;
    int jumpResetCooldown = 0;
    boolean inCombo = false;
    int comboHitsLeft = 0;
    Vec3 fleeLastPos = null;
    Entity fleeLastTarget = null;
    int fleeLastTick = -1;
    Vec3 fleeVel = Vec3.ZERO;
    int swingHold = 0;
    boolean prevTargetOnGround = true;
    LivingEntity readTarget = null;
    int targetLandWindow = 0;
    boolean prevTargetBlocking = false;
    int mirrorPunishWindow = 0;
    boolean prevShieldDisabled = false;
    boolean shieldBrokenNow = false;
    int autoCritWindow = 0;
    int critFollowUpWindow = 0;
    int critChainWindow = 0;
    int prevHurtTime = 0;
    boolean jumpResetPending = false;
    int crouchDodgeTicks = 0;
    int knockbackTicks = 0;
    int kbYieldWindow = 0;
    int counterStunWindow = 0;
    int comboLockHits = 0;
    int comboLockDecay = 0;
    BotAiTier aiTier = BotAiTier.FULL;
    double dist;
    boolean specialExecuting;
    boolean cpvpMode;
    boolean elytraOffense;
    boolean healing;
    boolean cartHealthOk;
    WeaponSelector.WeaponType weapon;
    boolean spearMaceOffered;
    boolean blocking;
    boolean targetEating;
    boolean fireEscapeBusy;
    String closerThanShare;
    String closerThanRevenge;
    float straightYaw = 0.0F;
    float straightPitch = 0.0F;
    int feintTimer = 0;
    int feintCooldown = 0;
    int feintDir = 0;
    double adaptiveScore = 0.0;
    int hitReact = 0;
    int hitReactTicks = 0;
    int hitReactDelay = 0;
    boolean earlyCritThisCycle = false;
    int crystalAfterChainUntil = 0;
    int oppAnchorTick = -10000;
    int oppCrystalTick = -10000;
    boolean anchorAfterCrystal = false;
    int cpvpStrafeDir = 1;
    int cpvpStrafeTicks = 0;
    int lastAnchorActiveTick = -10000;

    final ReadAndEscapePhase readAndEscape;
    final UpkeepPhase upkeep;
    final SurvivalPhase survival;
    final NoTargetPhase noTarget;
    final SpecialsPhase specials;
    final WeaponChoicePhase weaponChoice;
    final MeleeCombat melee;
    final Targeting targeting;
    final LootPickup loot;

    public BotBrain(BotContext ctx, MatchSquads squads) {
        this.ctx = ctx;
        this.bot = ctx.player();
        this.attackRange = Math.min(ctx.settings().attackRange(), BotSettings.VANILLA_ATTACK_RANGE);
        this.wTap = new WTapController(ctx.tuning());
        this.pathFollower = new PathFollower(ctx);
        this.suitUp = new InvisibleSuitUp(ctx);
        this.armorManager = new ArmorManager(ctx, suitUp);
        this.offhandManager = new OffhandManager(ctx);
        this.shieldController = new ShieldController(ctx);
        this.weaponSelector = new WeaponSelector(ctx);
        this.rangedController = new RangedController(ctx);
        this.crossbowChargeController = new CrossbowChargeController(ctx);
        this.healingController = new HealingController(ctx);
        this.loadoutManager = new LoadoutManager(ctx);
        this.strafeController = new StrafeController(ctx);
        this.arrowDodge = new ArrowDodge(ctx);
        this.approachStyle = new ApproachStyle(ctx);
        this.fightState = new FightState(ctx);
        this.swingReach = new SwingReach(ctx);
        this.potionController = new PotionController(ctx);
        this.bunnyHopController = new BunnyHopController(ctx);
        this.axeController = new AxeController(ctx);
        this.cobwebEscapeController = new CobwebEscapeController(ctx);
        this.chorusEscapeController = new ChorusEscapeController(ctx);
        this.bubbleWebController = new BubbleWebController(ctx);
        this.fireEscapeController = new FireEscapeController(ctx);
        this.cobwebTrapController = new CobwebTrapController(ctx);
        this.waterBucketClutchController = new WaterBucketClutchController(ctx);
        this.blockClutchController = new BlockClutchController(ctx);
        this.webDrainController = new WebDrainController(ctx);
        this.elytraMaceController = new ElytraMaceController(ctx);
        this.elytraSpearController = new ElytraSpearController(ctx);
        this.windBurstElytraController = new WindBurstElytraController(ctx);
        this.elytraMaceController.setOnSmashLanded(this.windBurstElytraController::armAfterSmash);
        this.rocketlessElytraController = new RocketlessElytraController(ctx);
        this.rocketlessElytraController.setOnSmashLanded(this.windBurstElytraController::armAfterSmash);
        this.tridentMaceController = new TridentMaceController(ctx);
        this.maceController = new MaceController(ctx);
        this.spearMaceController = new SpearMaceController(ctx, offhandManager);
        this.pearlCatchController = new PearlCatchController(ctx, offhandManager);
        this.predictionWebController = new PredictionWebController(ctx);
        this.headWebController = new HeadWebController(ctx);
        this.mendingController = new MendingController(ctx);
        this.scaffoldController = new ScaffoldController(ctx);
        this.buildFight = new BuildFightController(ctx);
        this.boatTravelController = new BoatTravelController(ctx);
        this.jumpPlaceController = new JumpPlaceController(ctx);
        this.tunnelController = new TunnelController(ctx);
        this.orbitalStrikeController = new OrbitalStrikeController(ctx);
        this.diveForecast = new MaceDiveForecast(ctx);
        this.maceTotemGuard = new MaceTotemGuard(ctx, diveForecast);
        this.idleHumanizer = new IdleHumanizer(ctx);
        this.cartController = new CartController(ctx, offhandManager);
        this.swapTechniques = new SwapTechniques(ctx);
        this.crystalPvpController = new CrystalPvpController(ctx);
        this.anchorPvpController = new AnchorPvpController(ctx);
        this.enderPearlController = new EnderPearlController(ctx);
        this.comboBreakController = new ComboBreakController(ctx);
        this.elytraFollowController = new ElytraFollowController(ctx);
        this.restockController = new RestockController(ctx, enderPearlController, suitUp, ctx.squad().restockPlan(), squads.boxClaims());
        this.shulkerRaidController = new ShulkerRaidController(ctx, squads.boxClaims());
        this.wanderController = new WanderController(ctx);
        this.healingController.setGapTools(enderPearlController, cobwebTrapController);
        this.healingController.setBuildFight(buildFight);

        this.readAndEscape = new ReadAndEscapePhase(this);
        this.upkeep = new UpkeepPhase(this);
        this.survival = new SurvivalPhase(this);
        this.noTarget = new NoTargetPhase(this);
        this.specials = new SpecialsPhase(this);
        this.weaponChoice = new WeaponChoicePhase(this);
        this.melee = new MeleeCombat(this);
        this.targeting = new Targeting(this);
        this.loot = new LootPickup(this);
    }

    public boolean isMendIntent() {
        return mendIntent;
    }

    boolean targetBlocking() {
        return target instanceof HumanEntity human && human.isBlocking();
    }

    boolean maySpecial(boolean alreadyExecuting) {
        return alreadyExecuting || !mendIntent;
    }

    int findHotbarTag(Tag<Material> tag) {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && tag.isTagged(stack.getType())) {
                return i;
            }
        }
        return -1;
    }

    public boolean isShieldBroken() {
        return shieldBrokenNow;
    }

    boolean maceDiveIncoming(LivingEntity target) {
        if (!ctx.settings().shieldOnMaceDive()) {
            return false;
        } else if (diveForecast.soonest() != null) {
            return true;
        } else if (target == null) {
            return false;
        } else {
            double dy = target.getLocation().getY() - bot.getLocation().getY();
            if (!(dy < ctx.tuning().number(TuningKey.BRAIN_DIVE_MIN_HEIGHT).doubleValue()) && !(dy > ctx.tuning().number(TuningKey.BRAIN_DIVE_MAX_HEIGHT).doubleValue())) {
                if (melee.fallVelY(target) > ctx.tuning().number(TuningKey.BRAIN_DIVE_FALL_SPEED).doubleValue()) {
                    return false;
                } else {
                    double dx = target.getLocation().getX() - bot.getLocation().getX();
                    double dz = target.getLocation().getZ() - bot.getLocation().getZ();
                    return Math.sqrt(dx * dx + dz * dz) <= ctx.tuning().number(TuningKey.BRAIN_DIVE_HORIZ_RANGE).doubleValue();
                }
            } else {
                return false;
            }
        }
    }

    public void tick() {
        tickCount++;
        if (target != null) {
            melee.targetVelocity();
        }

        ElytraFlight.guard(ctx);
        suitUp.tick();
        UseSlowdown.tick(ctx);
        fireEscapeBusy = fireEscapeController.isActive() && fireEscapeController.tick();
        if (!ctx.match().canMove()) {
            ctx.controls().stopMoving();
        }

        diveForecast.update();
        maceTotemUp = maceTotemGuard.tick(tickCount);
        ctx.controls().setSpeedScale(1.0F);
        elytraFollowController.land();

        // Committed eat: skip phases that could switch slots before the eat finishes.
        boolean eatLocked = !cpvpMode && survival.eatLocked();
        try {
            if (!eatLocked && readAndEscape.tickReadAndEscape()) {
                return;
            }

            if (!eatLocked && upkeep.tickUpkeep()) {
                return;
            }

            if (survival.tickSurvival()) {
                return;
            }

            if (noTarget.tickNoTarget()) {
                return;
            }

            if (specials.tickSpecials()) {
                return;
            }

            if (weaponChoice.tickWeaponChoice()) {
                return;
            }

            if (melee.tickMeleeOpeners()) {
                return;
            }

            if (!melee.tickMeleeReads()) {
                melee.tickMeleeExchange();
                return;
            }
        } finally {
            ctx.squad().setCombatTarget(ctx, target);
            melee.enforceConsumingMovementLimits();
            float dodge = !healing && !specialExecuting && !cpvpMode && target != null && ctx.match().live() && bot.isOnGround() && !ctx.controls().frozen()
                    ? arrowDodge.strafe(target, dist) : 0.0F;
            if (dodge != 0.0F) {
                ctx.controls().setMovement(ctx.controls().getForward(), dodge);
            }

            if (jumpedForCrit || critSwingHold > 0) {
                melee.sprintOffForCrit();
                if (critSwingHold > 0) {
                    critSwingHold--;
                }
            } else if (sprintHitHold > 0) {
                melee.meleeMove(1.0F, 0.0F);
                ctx.controls().setSprinting(true);
                sprintHitHold--;
            }
        }
    }

    public LivingEntity getTarget() {
        return target;
    }

    public void notifyHurtBy(UUID attacker) {
        revengeTargetId = attacker;
    }

    /** Resets every controller, as between rounds; the bot keeps its squad slot. */
    public void reset() {
        pathFollower.reset();
        armorManager.resetNavigation();
        shieldController.reset();
        shieldController.clearModifiers();
        healingController.reset();
        rangedController.reset();
        crossbowChargeController.reset();
        axeController.reset();
        chorusEscapeController.reset();
        bubbleWebController.reset();
        cobwebTrapController.reset();
        webDrainController.reset();
        elytraMaceController.reset();
        elytraSpearController.reset();
        windBurstElytraController.reset();
        rocketlessElytraController.reset();
        tridentMaceController.reset();
        maceController.reset();
        spearMaceController.reset();
        pearlCatchController.reset();
        predictionWebController.reset();
        headWebController.reset();
        mendingController.abortMending();
        scaffoldController.reset();
        boatTravelController.reset();
        jumpPlaceController.reset();
        tunnelController.reset();
        orbitalStrikeController.reset();
        maceTotemGuard.reset();
        cartController.reset();
        swapTechniques.reset();
        crystalPvpController.reset();
        anchorPvpController.reset();
        comboBreakController.reset();
        elytraFollowController.reset();
        restockController.reset();
        shulkerRaidController.reset();
        wanderController.reset();
        offhandManager.unpark();
        ctx.squad().setCombatTarget(ctx, null);
    }

    public void shutdown() {
        reset();
        ElytraFlight.forget(ctx);
        MeleeWeaponGuard.forget(ctx);
    }
}
