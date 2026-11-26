package dev.nandi0813.zppbots.ai.elytra;

import dev.nandi0813.zppbots.ai.gear.ArmorEvaluator;
import dev.nandi0813.zppbots.ai.gear.ItemEvaluator;
import dev.nandi0813.zppbots.ai.mace.DiveCover;
import dev.nandi0813.zppbots.ai.mace.DiveLead;
import dev.nandi0813.zppbots.ai.mace.MaceSmashGates;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.TechniqueConfig;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.ElytraFlight;
import dev.nandi0813.zppbots.util.EnchantmentUtils;
import dev.nandi0813.zppbots.util.InventoryUtils;
import dev.nandi0813.zppbots.util.StunSlam;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;

public final class ElytraMaceController {

    private static final String DIVE_COLLISION_ABORT = "DIVE_COLLISION_ABORT";
    private static final String CHASE_COLLISION_ABORT = "CHASE_COLLISION_ABORT";

    private static final TuningKey[] LVL_COOLDOWN_MULT = {
            TuningKey.ELYTRA_MACE_COOLDOWN_MULT_L1,
            TuningKey.ELYTRA_MACE_COOLDOWN_MULT_L2,
            TuningKey.ELYTRA_MACE_COOLDOWN_MULT_L3,
            TuningKey.ELYTRA_MACE_COOLDOWN_MULT_L4,
            TuningKey.ELYTRA_MACE_COOLDOWN_MULT_L5
    };
    private static final TuningKey[] LVL_RECHAIN_CHANCE = {
            TuningKey.ELYTRA_MACE_RECHAIN_CHANCE_L1,
            TuningKey.ELYTRA_MACE_RECHAIN_CHANCE_L2,
            TuningKey.ELYTRA_MACE_RECHAIN_CHANCE_L3,
            TuningKey.ELYTRA_MACE_RECHAIN_CHANCE_L4,
            TuningKey.ELYTRA_MACE_RECHAIN_CHANCE_L5
    };
    private static final TuningKey[] LVL_AIM_ERROR = {
            TuningKey.ELYTRA_MACE_AIM_ERROR_L1,
            TuningKey.ELYTRA_MACE_AIM_ERROR_L2,
            TuningKey.ELYTRA_MACE_AIM_ERROR_L3,
            TuningKey.ELYTRA_MACE_AIM_ERROR_L4,
            TuningKey.ELYTRA_MACE_AIM_ERROR_L5
    };
    private static final TuningKey[] LVL_OVERSHOOT_HEIGHT = {
            TuningKey.ELYTRA_MACE_OVERSHOOT_HEIGHT_L1,
            TuningKey.ELYTRA_MACE_OVERSHOOT_HEIGHT_L2,
            TuningKey.ELYTRA_MACE_OVERSHOOT_HEIGHT_L3,
            TuningKey.ELYTRA_MACE_OVERSHOOT_HEIGHT_L4,
            TuningKey.ELYTRA_MACE_OVERSHOOT_HEIGHT_L5
    };
    private static final TuningKey[] LVL_LATE_UNEQUIP = {
            TuningKey.ELYTRA_MACE_LATE_UNEQUIP_L1,
            TuningKey.ELYTRA_MACE_LATE_UNEQUIP_L2,
            TuningKey.ELYTRA_MACE_LATE_UNEQUIP_L3,
            TuningKey.ELYTRA_MACE_LATE_UNEQUIP_L4,
            TuningKey.ELYTRA_MACE_LATE_UNEQUIP_L5
    };
    private static final TuningKey[] LVL_MISS_ROCKET = {
            TuningKey.ELYTRA_MACE_MISS_ROCKET_L1,
            TuningKey.ELYTRA_MACE_MISS_ROCKET_L2,
            TuningKey.ELYTRA_MACE_MISS_ROCKET_L3,
            TuningKey.ELYTRA_MACE_MISS_ROCKET_L4,
            TuningKey.ELYTRA_MACE_MISS_ROCKET_L5
    };
    private static final double UNEQUIP_HEIGHT = 3.0;
    private static final double HEAD_LOCK_HEIGHT = 12.0;
    private static final double MIN_FALL_DIST = 1.5;
    private static final int MAX_PHASE_TICKS = 200;
    private static final int EQUIP_SETTLE_TICKS = 3;
    private static final float CLIMB_PITCH = -90.0F;
    private static final int MIN_ROCKETS = 2;
    private static final int MAX_ROCKETS = 1;
    private static final int MIN_AIR_ROCKETS = 3;
    private static final int AIR_CLIMB_ROCKETS = 8;
    private static final int AIR_ROCKET_INTERVAL = 8;
    private static final int GLIDE_DOWN_MAX_TICKS = 400;
    private static final float GLIDE_DOWN_PITCH = 20.0F;

    private final BotContext ctx;
    private final Player bot;
    private final StunSlam stunSlam = new StunSlam();
    private final DiveLead lead;
    private final DiveCover cover;
    private final ElytraObstacleGuard.CrashDetector crashGuard;
    private int climbRocketDelay = 0;
    private double apexJitter = 0.0;
    private float climbJitter = 0.0F;
    private Phase phase = Phase.IDLE;
    private int phaseTick = 0;
    private int cooldown = 0;
    private int rocketsUsed = 0;
    private boolean aerialChase = false;
    private int lastRocketTick = -100;
    private int maceSlot = -1;
    private int chestSaveSlot = -1;
    private int elytraFromSlot = -1;
    private int prevSelected = 0;
    private boolean hadOriginalChestplate = false;
    private double diveOffX;
    private double diveOffZ;
    private Runnable onSmashLanded;
    private TechniqueConfig.RocketMaceVariant rocketVariant = TechniqueConfig.RocketMaceVariant.V1_CLIMB_DROP;
    private long aimSlot = Long.MIN_VALUE;
    private double aimErrX;
    private double aimErrZ;

    public ElytraMaceController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
        this.lead = new DiveLead(ctx);
        this.cover = new DiveCover(ctx);
        this.crashGuard = new ElytraObstacleGuard.CrashDetector(ctx);
    }

    private double smashHitRange() {
        return ctx.settings().attackRange();
    }

    private int elytraMaceLevel() {
        return ElytraSkill.level(ctx);
    }

    public void setOnSmashLanded(Runnable cb) {
        onSmashLanded = cb;
    }

    public boolean hasFireworksForCombo() {
        return ctx.settings().techniques().rocketMace && countItem(Material.FIREWORK_ROCKET) >= 2;
    }

    private void resolveRocketVariant() {
        TechniqueConfig.RocketMaceVariant want = ctx.settings().techniques().rocketMaceVariant;
        rocketVariant = want;
        if (want != TechniqueConfig.RocketMaceVariant.V1_CLIMB_DROP && !hasDurationIiiRocket()) {
            rocketVariant = TechniqueConfig.RocketMaceVariant.V1_CLIMB_DROP;
        }
        if (rocketVariant == TechniqueConfig.RocketMaceVariant.V4_WINDBURST_LOOP && !hasWindBurstMace()) {
            rocketVariant = TechniqueConfig.RocketMaceVariant.V1_CLIMB_DROP;
        }
    }

    private boolean hasDurationIiiRocket() {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (ItemKinds.is(st, Material.FIREWORK_ROCKET)
                    && st.getItemMeta() instanceof FireworkMeta fw
                    && fw.getPower() >= 3) {
                return true;
            }
        }
        return false;
    }

    private boolean hasWindBurstMace() {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            if (ItemKinds.is(st, Material.MACE) && EnchantmentUtils.getEnchantLevel(st, "wind_burst") >= 1) {
                return true;
            }
        }
        return false;
    }

    private boolean skipsApexDance() {
        return rocketVariant == TechniqueConfig.RocketMaceVariant.V2_BOOST_TURN_HIT
                || rocketVariant == TechniqueConfig.RocketMaceVariant.V4_WINDBURST_LOOP;
    }

    public boolean hasGearForCombo() {
        TechniqueConfig tech = ctx.settings().techniques();
        if (!tech.diveBomb || !tech.rocketMace) {
            return false;
        } else if (findMaceInHotbar() < 0) {
            return false;
        } else if (countItem(Material.FIREWORK_ROCKET) < 2) {
            return false;
        }
        return findElytraInInventory() >= 0 || ElytraFlight.isUsable(bot.getEquipment().getItem(EquipmentSlot.CHEST));
    }

    private boolean canRechainImmediately(LivingEntity target, double dist) {
        if (target == null || !Ent.alive(target)) {
            return false;
        } else if (dist > ctx.tuning().number(TuningKey.ELYTRA_MACE_TRIGGER_RANGE).doubleValue()) {
            return false;
        } else if (findMaceInHotbar() < 0) {
            return false;
        }
        return countItem(Material.FIREWORK_ROCKET) >= 2 && findElytraInInventory() >= 0;
    }

    public boolean tick(LivingEntity target, double dist) {
        if (cooldown > 0) {
            cooldown--;
        }
        return switch (phase) {
            case IDLE -> tickIdle(target, dist);
            case EQUIPPING -> tickEquipping();
            case LAUNCHING -> tickLaunching();
            case CLIMBING -> tickClimbing(target);
            case APEX_UNEQUIP -> tickApexUnequip(target);
            case APEX_REEQUIP -> tickApexReequip(target);
            case DIVING -> tickDiving(target);
            case UNEQUIPPING -> tickUnequipping(target);
            case SMASHING -> tickSmashing(target);
            case AERIAL_PURSUE -> tickAerialPursue(target);
            case GLIDE_DOWN -> tickGlideDown();
        };
    }

    public void reset() {
        if (phase != Phase.IDLE) {
            if (phase != Phase.GLIDE_DOWN) {
                abort();
            }
            if (phase == Phase.GLIDE_DOWN) {
                tickGlideDown();
            }
        }
    }

    public boolean isExecuting() {
        return phase != Phase.IDLE;
    }

    private boolean tickIdle(LivingEntity target, double dist) {
        if (!ctx.settings().techniques().diveBomb) {
            return false;
        }
        ensureChestplateOnGround();
        if (!ctx.settings().techniques().rocketMace) {
            return false;
        } else if (target == null || !Ent.alive(target)) {
            return false;
        } else if (cooldown > 0) {
            return false;
        } else if (!bot.isOnGround()) {
            return false;
        }
        boolean targetAir = isTargetAirborne(target);
        if (targetAir) {
            if (dist > ctx.tuning().number(TuningKey.ELYTRA_MACE_AIR_TRIGGER_RANGE).doubleValue()) {
                return false;
            }
        } else if (dist > ctx.tuning().number(TuningKey.ELYTRA_MACE_TRIGGER_RANGE).doubleValue()) {
            return false;
        }
        maceSlot = findMaceInHotbar();
        if (maceSlot < 0) {
            return false;
        }
        int needRockets = targetAir ? MIN_AIR_ROCKETS : MIN_ROCKETS;
        if (countItem(Material.FIREWORK_ROCKET) < needRockets) {
            return false;
        }
        ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
        if (ItemKinds.is(chest, Material.ELYTRA)) {
            return false;
        }
        elytraFromSlot = findElytraInInventory();
        if (elytraFromSlot < 0) {
            return false;
        }
        hadOriginalChestplate = !ItemKinds.isEmpty(chest);
        if (!targetAir && !cover.reachable(target, ctx.tick())) {
            return false;
        } else if (!DiveCover.openAbove(ctx, bot, DiveCover.LAUNCH_HEADROOM)) {
            return false;
        } else if (ElytraSkill.skipLaunch(ctx)) {
            cooldown = ElytraSkill.hesitateTicks(ctx);
            return false;
        }
        aerialChase = targetAir;
        prevSelected = InventoryUtils.selected(ctx);
        enterPhase(Phase.EQUIPPING);
        return true;
    }

    private boolean tickEquipping() {
        phaseTick++;
        if (phaseTick > MAX_PHASE_TICKS) {
            abort();
            return false;
        } else if (elytraFromSlot == -1) {
            enterPhase(Phase.LAUNCHING);
            return true;
        }
        PlayerInventory inv = bot.getInventory();
        if (phaseTick == 1) {
            ItemStack currentChest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
            ItemStack elytra = inv.getItem(elytraFromSlot).clone();
            inv.setItem(elytraFromSlot, null);
            bot.getEquipment().setItem(EquipmentSlot.CHEST, elytra);
            if (!ItemKinds.isEmpty(currentChest)) {
                chestSaveSlot = findFreeHotbarSlot();
                if (chestSaveSlot < 0) {
                    chestSaveSlot = elytraFromSlot;
                }
                inv.setItem(chestSaveSlot, currentChest.clone());
            } else {
                chestSaveSlot = -1;
            }
        }
        if (phaseTick < EQUIP_SETTLE_TICKS) {
            return true;
        }
        ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
        if (!ItemKinds.is(chest, Material.ELYTRA)) {
            abort();
            return false;
        }
        enterPhase(Phase.LAUNCHING);
        return true;
    }

    private boolean tickLaunching() {
        phaseTick++;
        if (phaseTick > 30) {
            abort();
            return false;
        }
        ctx.controls().stopMoving();
        ctx.controls().setSprinting(false);
        if (bot.isOnGround()) {
            ctx.controls().jumpOnce();
            return true;
        } else if (!bot.isGliding()) {
            ctx.controls().jumpOnce();
            return true;
        }
        resolveRocketVariant();
        enterPhase(Phase.CLIMBING);
        return true;
    }

    private boolean tickClimbing(LivingEntity target) {
        phaseTick++;
        if (phaseTick == 1) {
            rocketsUsed = 0;
            lastRocketTick = -100;
            double jitterMin = ctx.tuning().number(TuningKey.ELYTRA_MACE_APEX_JITTER_MIN).doubleValue();
            double jitterSpan = ctx.tuning().number(TuningKey.ELYTRA_MACE_APEX_JITTER_SPAN).doubleValue();
            int rocketDelayMin = ctx.tuning().integer(TuningKey.ELYTRA_MACE_MISS_ROCKET_DELAY_MIN);
            int rocketDelaySpan = ctx.tuning().integer(TuningKey.ELYTRA_MACE_MISS_ROCKET_DELAY_SPAN);
            apexJitter = jitterMin + ctx.random().nextDouble() * jitterSpan;
            climbJitter = (float) (ctx.random().nextDouble()
                    * ctx.tuning().number(TuningKey.ELYTRA_MACE_CLIMB_PITCH_SPREAD).doubleValue());
            climbRocketDelay = ctx.random().nextDouble()
                    < ctx.tuning().number(LVL_MISS_ROCKET[elytraMaceLevel() - 1]).doubleValue()
                    ? rocketDelayMin + ctx.random().nextInt(rocketDelaySpan)
                    : 0;
        }
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (phaseTick > MAX_PHASE_TICKS) {
            abort();
            return false;
        } else if (!bot.isGliding() && phaseTick > 10) {
            abort();
            return false;
        } else if (lostCover(target)) {
            abort();
            return false;
        }
        double overshoot = ctx.tuning().number(LVL_OVERSHOOT_HEIGHT[elytraMaceLevel() - 1]).doubleValue();
        double apexY = apexY(ctx.tuning(), Ent.pos(target).y(), aerialChase, overshoot, apexJitter);
        double botY = Ent.pos(bot).y();
        if (botY >= apexY) {
            if (!aerialChase && skipsApexDance()) {
                aimAt(target, true);
                enterPhase(Phase.DIVING);
                return true;
            }
            enterPhase(aerialChase ? Phase.AERIAL_PURSUE : Phase.APEX_UNEQUIP);
            return true;
        } else if (aerialChase || rocketsUsed < 1 || phaseTick < 8 + climbRocketDelay || !(Ent.velocity(bot).y() <= 0.0)) {
            Vec3 d = Ent.pos(target).subtract(Ent.pos(bot));
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x(), d.z()));
            float[] safe = ElytraObstacleGuard.towards(ctx, yaw, CLIMB_PITCH + climbJitter,
                    Ent.eyePos(target).subtract(Ent.eyePos(bot)).length());
            ctx.controls().look(safe[0], safe[1]);
            if (aerialChase) {
                if (bot.isGliding() && rocketsUsed < AIR_CLIMB_ROCKETS
                        && phaseTick - lastRocketTick >= AIR_ROCKET_INTERVAL
                        && countItem(Material.FIREWORK_ROCKET) > 0) {
                    double missRocket = ctx.tuning().number(LVL_MISS_ROCKET[elytraMaceLevel() - 1]).doubleValue();
                    if (ctx.random().nextDouble() >= missRocket) {
                        fireRocket();
                        rocketsUsed++;
                    }
                    lastRocketTick = phaseTick;
                }
                if (countItem(Material.FIREWORK_ROCKET) == 0 && botY < Ent.pos(target).y() - 2.0) {
                    abort();
                    return false;
                }
            } else if (rocketsUsed < 1 && phaseTick == 1 + climbRocketDelay) {
                fireRocket();
                rocketsUsed++;
            }
            return true;
        } else if (skipsApexDance()) {
            aimAt(target, true);
            enterPhase(Phase.DIVING);
            return true;
        }
        enterPhase(Phase.APEX_UNEQUIP);
        return true;
    }

    static double apexY(BotTuning.Profile profile, double targetY, boolean aerial, double overshoot, double jitter) {
        return (aerial
                ? targetY + profile.number(TuningKey.ELYTRA_MACE_AIR_APEX_OFFSET).doubleValue() + overshoot * 0.5
                : targetY + profile.number(TuningKey.ELYTRA_MACE_TARGET_HEIGHT).doubleValue() + overshoot) + jitter;
    }

    private boolean tickApexUnequip(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (phaseTick > 30) {
            abort();
            return false;
        } else if (lostCover(target)) {
            abort();
            return false;
        }
        if (phaseTick == 1) {
            if (chestSaveSlot < 0) {
                chestSaveSlot = findFreeHotbarSlot();
                if (chestSaveSlot < 0) {
                    chestSaveSlot = 8;
                }
            }
            swapChestEquipment();
        }
        aimAt(target, true);
        if (phaseTick > 3 && !bot.isGliding()) {
            enterPhase(Phase.APEX_REEQUIP);
        }
        return true;
    }

    private boolean tickApexReequip(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (phaseTick > 10) {
            abort();
            return false;
        } else if (lostCover(target)) {
            abort();
            return false;
        }
        if (phaseTick == 1) {
            swapChestEquipment();
        }
        aimAt(target, true);
        if (phaseTick >= 2) {
            enterPhase(Phase.DIVING);
        }
        return true;
    }

    private boolean tickDiving(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (phaseTick > MAX_PHASE_TICKS) {
            abort();
            return false;
        } else if (lostCover(target)) {
            abort();
            return false;
        }
        if (phaseTick == 1) {
            double[] off = ElytraSkill.diveOffset(ctx);
            diveOffX = off[0];
            diveOffZ = off[1];
        }
        if (crashGuard.crashed()) {
            ctx.log().decision("ElytraMaceController", DIVE_COLLISION_ABORT, "flew into something, breaking off the elytra dive");
            abort();
            return false;
        }
        ElytraFlight.beginGlide(ctx);
        Vec3 pos = Ent.pos(bot);
        double heightAboveTarget = pos.y() - Ent.pos(target).y();
        ctx.controls().stopMoving();
        ctx.controls().setSprinting(false);
        int eta = DiveLead.ticksToDrop(Math.max(0.0, heightAboveTarget - 1.0), Ent.velocity(bot).y());
        Vec3 aimPt = lead.lead(target, eta).add(diveOffX, 0.0, diveOffZ);
        double hx = aimPt.x() - pos.x();
        double hz = aimPt.z() - pos.z();
        double hd = Math.sqrt(hx * hx + hz * hz);
        boolean targetStationary = !lead.moving(target);
        // Direct setDeltaMovement drift is not used: the glide is steered by look only.
        if (targetStationary) {
            if (hd <= 1.2) {
                aimStraightDown();
            } else {
                aimAt(target, true);
            }
        } else {
            aimAtHead(target, aimPt);
        }
        InventoryUtils.select(ctx, maceSlot);
        double late = ctx.tuning().number(LVL_LATE_UNEQUIP[elytraMaceLevel() - 1]).doubleValue();
        if (heightAboveTarget <= UNEQUIP_HEIGHT - late || Ent.dist(bot, target) <= 9.0 - late) {
            enterPhase(Phase.UNEQUIPPING);
        }
        return true;
    }

    private boolean tickAerialPursue(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (phaseTick > MAX_PHASE_TICKS) {
            abort();
            return false;
        } else if (crashGuard.crashed()) {
            ctx.log().decision("ElytraMaceController", CHASE_COLLISION_ABORT, "flew into something, breaking off the elytra chase");
            abort();
            return false;
        }
        if (!bot.isGliding()) {
            ElytraFlight.beginGlide(ctx);
        }
        InventoryUtils.select(ctx, maceSlot);
        Vec3 d = Ent.pos(target).subtract(Ent.pos(bot));
        double horiz = Math.sqrt(d.x() * d.x() + d.z() * d.z());
        double dist3d = Ent.dist(bot, target);
        if (horiz <= ctx.tuning().number(TuningKey.ELYTRA_MACE_SMASH_RANGE).doubleValue() && dist3d <= 9.0) {
            enterPhase(Phase.UNEQUIPPING);
            return true;
        } else if (target.isOnGround()) {
            aerialChase = false;
            enterPhase(Phase.DIVING);
            return true;
        }
        aimAtHead(target);
        ctx.controls().stopMoving();
        ctx.controls().setSprinting(false);
        // The setDeltaMovement pull toward the target is replaced by real rocket boosts along the aim.
        if (bot.isGliding() && phaseTick - lastRocketTick >= AIR_ROCKET_INTERVAL
                && countItem(Material.FIREWORK_ROCKET) > 0) {
            fireRocket();
            lastRocketTick = phaseTick;
        }
        return true;
    }

    private boolean tickUnequipping(LivingEntity target) {
        phaseTick++;
        if (target != null && Ent.alive(target)) {
            aimAtHead(target);
        }
        if (phaseTick == 1) {
            PlayerInventory inv = bot.getInventory();
            ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
            if (ItemKinds.is(chest, Material.ELYTRA)) {
                int wearFrom = -1;
                if (hadOriginalChestplate) {
                    wearFrom = chestSaveSlot >= 0 && isChestplate(inv.getItem(chestSaveSlot))
                            ? chestSaveSlot
                            : findBestChestplateInInventory();
                }
                if (wearFrom >= 0) {
                    bot.getEquipment().setItem(EquipmentSlot.CHEST, inv.getItem(wearFrom).clone());
                    inv.setItem(wearFrom, chest.clone());
                } else {
                    int elytraStore = findElytraStoreSlot();
                    if (elytraStore >= 0) {
                        bot.getEquipment().setItem(EquipmentSlot.CHEST, null);
                        inv.setItem(elytraStore, chest.clone());
                    }
                }
                bot.setGliding(false);
            }
            InventoryUtils.select(ctx, maceSlot);
        }
        if (phaseTick > 10 && bot.isGliding()) {
            abort();
            return false;
        }
        if (!bot.isGliding()) {
            enterPhase(Phase.SMASHING);
        }
        return true;
    }

    private boolean tickSmashing(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (phaseTick > 60) {
            abort();
            return false;
        }
        InventoryUtils.select(ctx, maceSlot);
        aimAtHead(target);
        double dist = Ent.dist(bot, target);
        boolean inRange = dist <= smashHitRange();
        boolean hasFall = bot.getFallDistance() > MIN_FALL_DIST && MaceSmashGates.botMayUseSmashFallBonus(ctx);
        boolean landed = bot.isOnGround() && phaseTick > 3;
        ctx.controls().stopMoving();
        ctx.controls().setSprinting(false);
        if (!inRange && !bot.isOnGround()) {
            Vec3 pos = Ent.pos(bot);
            double botVy = Ent.velocity(bot).y();
            int fallTicks = DiveLead.ticksToDrop(pos.y() - Ent.eyePos(target).y(), botVy);
            Vec3 aimPt = lead.lead(target, fallTicks).add(diveOffX, 0.0, diveOffZ);
            double hx = aimPt.x() - pos.x();
            double hz = aimPt.z() - pos.z();
            double hd = Math.sqrt(hx * hx + hz * hz);
            if (ElytraSkill.level(ctx) < 5 && pos.y() - Ent.pos(target).y() > 4.0) {
                double canClose = fallTicks * 0.42 * ElytraSkill.steer(ctx) + smashHitRange();
                if (hd > canClose) {
                    abort();
                    return false;
                }
            }
            // setDeltaMovement drift replaced by air strafing along the head-aimed yaw.
            if (hd > 0.05) {
                ctx.controls().setForward((float) ElytraSkill.steer(ctx));
            }
        }
        if (inRange && hasFall) {
            if (bot.hasLineOfSight(target)) {
                ctx.controls().setSprinting(true);
                TechniqueConfig ssCfg = ctx.settings().techniques();
                boolean mayAirSlam = ssCfg.elytraStunSlam
                        && elytraMaceLevel() >= ctx.tuning().integer(TuningKey.ELYTRA_MACE_MIN_AIR_STUNSLAM_LEVEL);
                StunSlam.Result ssRes = stunSlam.tick(ctx, target, findAxeSlot(), maceSlot, ssCfg.stunSlamVariant, mayAirSlam);
                if (ssRes == StunSlam.Result.HOLD) {
                    return true;
                }
                if (ssRes != StunSlam.Result.SMASHED) {
                    InventoryUtils.select(ctx, maceSlot);
                    if (!ElytraSkill.whiff(ctx)) {
                        ctx.hands().attack(target);
                    }
                    ctx.hands().swing(EquipmentSlot.HAND);
                }
                if (onSmashLanded != null) {
                    onSmashLanded.run();
                }
                InventoryUtils.select(ctx, prevSelected);
                int lvl = elytraMaceLevel();
                boolean rechain = canRechainImmediately(target, dist)
                        && ctx.random().nextDouble() < ctx.tuning().number(LVL_RECHAIN_CHANCE[lvl - 1]).doubleValue();
                cooldown = smashCooldown(ctx.tuning(), lvl, rechain);
                aerialChase = false;
                if (bot.isOnGround()) {
                    ensureChestplateOnGround();
                }
                enterPhase(Phase.IDLE);
                return false;
            } else if (landed) {
                abort();
                return false;
            }
            return true;
        } else if (landed) {
            abort();
            return false;
        }
        return true;
    }

    static int smashCooldown(BotTuning.Profile profile, int level, boolean rechain) {
        int ticks = rechain
                ? profile.integer(TuningKey.ELYTRA_MACE_RECHAIN_COOLDOWN_TICKS)
                : profile.integer(TuningKey.ELYTRA_MACE_COOLDOWN_TICKS);
        return (int) Math.round(ticks * profile.number(LVL_COOLDOWN_MULT[level - 1]).doubleValue());
    }

    private void enterPhase(Phase p) {
        phase = p;
        phaseTick = 0;
    }

    private void abort() {
        crashGuard.reset();
        if (!bot.isOnGround()) {
            enterPhase(Phase.GLIDE_DOWN);
        } else {
            restoreChest();
            InventoryUtils.select(ctx, prevSelected);
            cooldown = (int) Math.round(ctx.tuning().number(TuningKey.ELYTRA_MACE_ABORT_COOLDOWN_BASE).doubleValue()
                    * ctx.tuning().number(LVL_COOLDOWN_MULT[elytraMaceLevel() - 1]).doubleValue());
            aerialChase = false;
            enterPhase(Phase.IDLE);
        }
    }

    private boolean maybeTickElytraOff(double heightAboveGround) {
        if (!ctx.settings().techniques().elytraPreserve) {
            return false;
        } else if (heightAboveGround < 8.0) {
            return false;
        } else if (Ent.velocity(bot).y() < -0.5) {
            return false;
        }
        int cycle = phaseTick % 19;
        if (cycle >= 15) {
            if (bot.isGliding()) {
                bot.setGliding(false);
            }
            return true;
        }
        if (cycle == 0 && !bot.isGliding()) {
            engageFallFlying();
        }
        return false;
    }

    private boolean tickGlideDown() {
        BlockPos bp = Ent.blockPos(bot);
        boolean glideHeldOff = maybeTickElytraOff(Ent.pos(bot).y() - ctx.world().surfaceY(bp.getX(), bp.getZ()));
        phaseTick++;
        if (phaseTick > GLIDE_DOWN_MAX_TICKS) {
            forceGroundReset();
            return false;
        } else if (!bot.isOnGround() && !bot.isInWater() && !bot.isInLava()) {
            ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
            if (!ItemKinds.is(chest, Material.ELYTRA)) {
                int elytraSlot = findElytraInInventory();
                if (elytraSlot >= 0) {
                    PlayerInventory inv = bot.getInventory();
                    ItemStack worn = ItemKinds.isEmpty(chest) ? null : chest.clone();
                    ItemStack elytra = inv.getItem(elytraSlot).clone();
                    inv.setItem(elytraSlot, worn);
                    bot.getEquipment().setItem(EquipmentSlot.CHEST, elytra);
                }
            }
            if (!glideHeldOff) {
                engageFallFlying();
            }
            ctx.controls().look(bot.getLocation().getYaw(), GLIDE_DOWN_PITCH);
            ctx.controls().stopMoving();
            ctx.controls().setSprinting(false);
            return true;
        }
        forceGroundReset();
        return false;
    }

    private void forceGroundReset() {
        if (bot.isGliding()) {
            bot.setGliding(false);
        }
        aerialChase = false;
        if (bot.isOnGround()) {
            ensureChestplateOnGround();
        } else {
            restoreChest();
        }
        InventoryUtils.select(ctx, prevSelected);
        cooldown = (int) Math.round(ctx.tuning().number(TuningKey.ELYTRA_MACE_ABORT_COOLDOWN_BASE).doubleValue()
                * ctx.tuning().number(LVL_COOLDOWN_MULT[elytraMaceLevel() - 1]).doubleValue());
        enterPhase(Phase.IDLE);
    }

    private void engageFallFlying() {
        if (!bot.isGliding()) {
            ElytraFlight.beginGlide(ctx);
        }
    }

    private void restoreChest() {
        ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
        if (!ItemKinds.is(chest, Material.ELYTRA)) {
            return;
        }
        PlayerInventory inv = bot.getInventory();
        ItemStack elytra = chest.clone();
        if (hadOriginalChestplate && chestSaveSlot >= 0 && isChestplate(inv.getItem(chestSaveSlot))) {
            ItemStack toWear = inv.getItem(chestSaveSlot).clone();
            bot.getEquipment().setItem(EquipmentSlot.CHEST, toWear);
            inv.setItem(chestSaveSlot, elytra);
        } else {
            int plateSlot = findBestChestplateInInventory();
            if (plateSlot >= 0) {
                bot.getEquipment().setItem(EquipmentSlot.CHEST, inv.getItem(plateSlot).clone());
                inv.setItem(plateSlot, elytra);
            } else {
                int elytraStore = findElytraStoreSlot();
                if (elytraStore < 0) {
                    return;
                }
                bot.getEquipment().setItem(EquipmentSlot.CHEST, null);
                inv.setItem(elytraStore, elytra);
            }
        }
    }

    private void ensureChestplateOnGround() {
        if (!bot.isOnGround()) {
            return;
        }
        ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
        if (!ItemKinds.is(chest, Material.ELYTRA)) {
            return;
        }
        if (bot.isGliding()) {
            bot.setGliding(false);
        }
        PlayerInventory inv = bot.getInventory();
        int plateSlot = findBestChestplateInInventory();
        ItemStack elytra = chest.clone();
        if (plateSlot >= 0) {
            bot.getEquipment().setItem(EquipmentSlot.CHEST, inv.getItem(plateSlot).clone());
            inv.setItem(plateSlot, elytra);
        } else {
            int store = findElytraStoreSlot();
            if (store >= 0) {
                bot.getEquipment().setItem(EquipmentSlot.CHEST, null);
                inv.setItem(store, elytra);
            }
        }
    }

    private void swapChestEquipment() {
        if (chestSaveSlot >= 0) {
            PlayerInventory inv = bot.getInventory();
            ItemStack chestSlotItem = bot.getEquipment().getItem(EquipmentSlot.CHEST);
            ItemStack savedItem = inv.getItem(chestSaveSlot);
            bot.getEquipment().setItem(EquipmentSlot.CHEST, savedItem == null ? null : savedItem.clone());
            inv.setItem(chestSaveSlot, ItemKinds.isEmpty(chestSlotItem) ? null : chestSlotItem.clone());
        }
    }

    private void fireRocket() {
        PlayerInventory inv = bot.getInventory();
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.FIREWORK_ROCKET)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            for (int i = 9; i < 36; i++) {
                if (ItemKinds.is(inv.getItem(i), Material.FIREWORK_ROCKET)) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) {
                return;
            }
            slot = InventoryUtils.moveToHotbarSafe(ctx, slot);
        }
        int prev = InventoryUtils.selected(ctx);
        InventoryUtils.select(ctx, slot);
        ctx.hands().useItem(EquipmentSlot.HAND);
        InventoryUtils.select(ctx, prev);
    }

    private void aimAt(LivingEntity target, boolean steep) {
        Vec3 eye = Ent.eyePos(bot);
        Vec3 t = Ent.pos(target);
        double dx = t.x() - eye.x();
        double dy = Ent.eyePos(target).y() - eye.y();
        double dz = t.z() - eye.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        if (steep) {
            pitch = Math.max(pitch, 60.0F);
        }
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float[] safe = ElytraObstacleGuard.towards(ctx, yaw, pitch, dist);
        ctx.controls().look(safe[0], safe[1]);
    }

    private void aimAtHead(LivingEntity target) {
        aimAtHead(target, Ent.pos(target));
    }

    private void aimAtHead(LivingEntity target, Vec3 feet) {
        double err = ctx.tuning().number(LVL_AIM_ERROR[elytraMaceLevel() - 1]).doubleValue();
        double ex = 0.0;
        double ez = 0.0;
        if (err > 0.0) {
            // Aim error is re-rolled every 10 ticks (no Random seeded per slot); drawn from ctx.random().
            long slot = ctx.world().gameTime() / 10L;
            if (slot != aimSlot) {
                aimSlot = slot;
                aimErrX = (ctx.random().nextDouble() - 0.5) * 2.0;
                aimErrZ = (ctx.random().nextDouble() - 0.5) * 2.0;
            }
            ex = aimErrX * err;
            ez = aimErrZ * err;
        }
        Vec3 eye = Ent.eyePos(bot);
        double dx = feet.x() + ex - eye.x();
        double dy = feet.y() + target.getEyeHeight() - eye.y();
        double dz = feet.z() + ez - eye.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        float[] safe = ElytraObstacleGuard.towards(ctx, yaw, pitch, Math.sqrt(dx * dx + dy * dy + dz * dz));
        ctx.controls().look(safe[0], safe[1]);
    }

    private void aimStraightDown() {
        ctx.controls().look(bot.getLocation().getYaw(), 90.0F);
    }

    private int findAxeSlot() {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 9; i++) {
            if (ItemKinds.isAxe(inv.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private int findMaceInHotbar() {
        PlayerInventory inv = bot.getInventory();
        int best = -1;
        double bestScore = -1.0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (ItemKinds.is(s, Material.MACE)) {
                double score = ItemEvaluator.scoreMace(s);
                if (score > bestScore) {
                    bestScore = score;
                    best = i;
                }
            }
        }
        return best;
    }

    private int findBestChestplateInInventory() {
        PlayerInventory inv = bot.getInventory();
        int best = -1;
        double bestScore = -1.0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (isChestplate(s)) {
                double score = ArmorEvaluator.score(s);
                if (score > bestScore) {
                    bestScore = score;
                    best = i;
                }
            }
        }
        return best;
    }

    private static boolean isChestplate(ItemStack s) {
        return !ItemKinds.is(s, Material.ELYTRA) && ArmorEvaluator.getSlot(s) == EquipmentSlot.CHEST;
    }

    private int findElytraInInventory() {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 36; i++) {
            if (ElytraFlight.isUsable(inv.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private int findFreeHotbarSlot() {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 9; i++) {
            if (ItemKinds.isEmpty(inv.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private int findElytraStoreSlot() {
        int slot = findFreeHotbarSlot();
        if (slot >= 0) {
            return slot;
        }
        PlayerInventory inv = bot.getInventory();
        for (int i = 9; i < 36; i++) {
            if (ItemKinds.isEmpty(inv.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean lostCover(LivingEntity target) {
        return !aerialChase && target.isOnGround() && !cover.reachable(target, ctx.tick());
    }

    private boolean isTargetAirborne(LivingEntity target) {
        if (target == null || target.isOnGround()) {
            return false;
        } else if (target.isGliding()) {
            return true;
        }
        return target.isRiptiding() || Ent.pos(target).y() - Ent.pos(bot).y() > 4.0;
    }

    private int countItem(Material material) {
        int total = 0;
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (ItemKinds.is(s, material)) {
                total += s.getAmount();
            }
        }
        return total;
    }

    private enum Phase {
        IDLE,
        EQUIPPING,
        LAUNCHING,
        CLIMBING,
        APEX_UNEQUIP,
        APEX_REEQUIP,
        DIVING,
        UNEQUIPPING,
        SMASHING,
        AERIAL_PURSUE,
        GLIDE_DOWN
    }
}
