package dev.nandi0813.zppbots.ai.heal;

import dev.nandi0813.zppbots.ai.buildfight.BuildFightController;
import dev.nandi0813.zppbots.ai.ranged.EnderPearlController;
import dev.nandi0813.zppbots.ai.web.CobwebTrapController;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.BotSettings;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import dev.nandi0813.zppbots.util.LedgeGuard;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class HealingController {

    private static final String HEAL_TIMEOUT = "HEAL_TIMEOUT";
    private static final String ONE_GAPPLE_DONE = "ONE_GAPPLE_DONE";
    private static final String FOOD_CONSUMED = "FOOD_CONSUMED";
    private static final String FOOD_EXHAUSTED = "FOOD_EXHAUSTED";
    private static final String EAT_TIMEOUT = "EAT_TIMEOUT";
    private static final String FORCE_CONSUME_START = "FORCE_CONSUME_START";
    private static final String FORCE_CONSUME_DONE = "FORCE_CONSUME_DONE";
    private static final String EAT_START = "EAT_START";
    private static final String HEAL_GAP_PEARL = "HEAL_GAP_PEARL";
    private static final String HEAL_GAP_WEB = "HEAL_GAP_WEB";
    private static final String HEAL_GAP_KNOCKBACK = "HEAL_GAP_KNOCKBACK";
    private static final String HEAL_START = "HEAL_START";
    private static final String HEAL_DONE = "HEAL_DONE";
    private static final int FORCE_CONSUME_TICKS = 40;
    private static final int EAT_TIMEOUT_TICKS = 80;
    private static final int MAX_HEAL_TICKS = 400;
    private static final int SAFE_DROP = 4;
    private static final float[] FLEE_OFFSETS = {0.0F, 25.0F, -25.0F, 50.0F, -50.0F, 75.0F, -75.0F, 105.0F, -105.0F, 135.0F, -135.0F};

    private final BotContext ctx;
    private final Player bot;
    private boolean shieldBroken = false;
    private boolean isHealing = false;
    private boolean hungerOnlyHeal = false;
    private boolean oneGapMode = false;
    private boolean retreatEnabled = false;
    private int healTicks = 0;
    private int runFirstTicks = 0;
    private int eatingSlot = -1;
    private int eatingTicks = 0;
    private int eatingStartCount = 0;
    private int reentryCd = 0;
    private boolean forceConsumed = false;
    private int eatCooldownLeft = 0;
    private boolean syncHealRequested = false;
    private int targetFleeingTicks = 0;
    private int gapBuyCooldown = 0;
    private EnderPearlController enderPearl;
    private CobwebTrapController cobwebTrap;
    private BuildFightController buildFight;
    private boolean prefightEating = false;

    public HealingController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public void setShieldBroken(boolean v) {
        shieldBroken = v;
    }

    private float shieldBrokenBonus() {
        return shieldBroken ? ctx.tuning().number(TuningKey.HEAL_SHIELD_BROKEN_BONUS).floatValue() : 0.0F;
    }

    private float lowHpThreshold() {
        return (float) (ctx.settings().retreatHealthHearts() * 2.0) + shieldBrokenBonus();
    }

    private float safeHp() {
        return Math.max(
                ctx.tuning().number(TuningKey.HEAL_SAFE_HP).floatValue(),
                lowHpThreshold() + ctx.tuning().number(TuningKey.HEAL_SAFE_MARGIN).floatValue());
    }

    public boolean isHealing() {
        return isHealing;
    }

    /** Eating a food item inside a heal, which pearls, weapon swaps and hits must not interrupt. */
    public boolean isCommittedEating() {
        return isHealing && eatingSlot >= 0;
    }

    public void requestSyncHeal() {
        syncHealRequested = true;
    }

    public boolean starving() {
        return bot.getFoodLevel() <= ctx.tuning().integer(TuningKey.HEAL_STARVING_LEVEL);
    }

    public boolean isRetreating() {
        return isHealing;
    }

    public void abortHealing() {
        exitHealing();
    }

    public void forceAbortWithLockout(int ticks) {
        reentryCd = Math.max(reentryCd, ticks);
        exitHealing();
    }

    public boolean tick(boolean inCombat, LivingEntity target, boolean crystalling) {
        float hp = (float) bot.getHealth();
        int foodLevel = bot.getFoodLevel();
        boolean syncReq = syncHealRequested;
        syncHealRequested = false;
        if (gapBuyCooldown > 0) {
            gapBuyCooldown--;
        }

        if (crystalling && countTotems() >= 1 && !starving()) {
            if (isHealing) {
                exitHealing();
            }

            return false;
        } else {
            if (reentryCd > 0) {
                reentryCd--;
            }

            if (!isHealing) {
                if (reentryCd > 0
                        && hp > ctx.tuning().number(TuningKey.HEAL_REENTRY_OVERRIDE_HP).floatValue()
                        && !starving()) {
                    return false;
                }

                float thresholdBoost = 0.0F;
                boolean aheadOnHp = false;
                if (target != null && Ent.alive(target)) {
                    float targetHp = (float) target.getHealth();
                    if (hp + ctx.tuning().number(TuningKey.HEAL_BEHIND_HP_GAP).floatValue() < targetHp) {
                        thresholdBoost = ctx.tuning().number(TuningKey.HEAL_BEHIND_THRESHOLD_BOOST).floatValue();
                    } else if (hp > targetHp + ctx.tuning().number(TuningKey.HEAL_AHEAD_HP_GAP).floatValue()) {
                        aheadOnHp = true;
                    }
                }

                boolean lowHp = hp <= lowHpThreshold() + thresholdBoost;
                boolean lowHunger = foodLevel <= ctx.tuning().integer(TuningKey.HEAL_HUNGER_EAT_LEVEL);
                boolean sync = syncReq
                        && hp < Ent.maxHealth(bot) - ctx.tuning().number(TuningKey.HEAL_SYNC_MISSING_HP).floatValue();
                if (target != null && Ent.alive(target)
                        && target.getHealth() <= ctx.tuning().number(TuningKey.HEAL_TARGET_FINISH_HP).floatValue()
                        && hp > ctx.tuning().number(TuningKey.HEAL_REENTRY_OVERRIDE_HP).floatValue()
                        && !lowHunger) {
                    return false;
                }

                boolean distanceHeal = !lowHp
                        && !lowHunger
                        && !sync
                        && target != null
                        && Ent.alive(target)
                        && Ent.dist(bot, target) >= ctx.tuning().number(TuningKey.HEAL_DISTANCE_RANGE).doubleValue()
                        && hp <= Ent.maxHealth(bot)
                                - ctx.tuning().number(TuningKey.HEAL_DISTANCE_MISSING_HP).floatValue();
                if (lowHp || lowHunger || sync || distanceHeal) {
                    boolean hungerOnly = lowHunger && !lowHp && !sync;
                    int slot = hungerOnly ? findBestRegularFood(-1) : findBestFood();
                    if (hungerOnly && slot < 0) {
                        slot = findBestFood();
                    }

                    if (slot >= 0) {
                        boolean retreat = lowHp && ctx.settings().retreat() && inCombat;
                        hungerOnlyHeal = hungerOnly;
                        oneGapMode = (aheadOnHp || distanceHeal) && !hungerOnly;
                        boolean flee = retreat && !aheadOnHp && !distanceHeal;
                        if (flee
                                && !ctx.settings().retreatOrdered()
                                && ctx.settings().healingMode() == BotSettings.HealingMode.ADVANCED
                                && target != null
                                && buyHealingGap(target)) {
                            flee = false;
                        }

                        // wall first: switching to a block after the eat starts would cancel it
                        if (!hungerOnly && target != null && buildFight != null && isHealFood(bot.getInventory().getItem(slot))) {
                            buildFight.wallForHeal(target);
                        }
                        enterHealing(slot, flee);
                    }
                }
            }

            if (!isHealing) {
                return false;
            } else {
                healTicks++;
                if (hungerOnlyHeal
                        ? foodLevel <= ctx.tuning().integer(TuningKey.HEAL_HUNGER_EAT_LEVEL)
                        : !(hp >= safeHp())) {
                    if (healTicks > MAX_HEAL_TICKS) {
                        ctx.log().decision("HealingController", HEAL_TIMEOUT, "heal timed out.", true);
                        reentryCd = ctx.tuning().integer(TuningKey.HEAL_REENTRY_AFTER_TIMEOUT);
                        exitHealing();
                        return false;
                    } else {
                        if (!tickRunFirst(target)) {
                            tickRetreat(target);
                            tickEating();
                        }
                        return isHealing;
                    }
                } else {
                    exitHealing();
                    return false;
                }
            }
        }
    }

    public void reset() {
        exitHealing();
        if (prefightEating) {
            stopEatAction();
            prefightEating = false;
        }
    }

    /** Eats one plain golden apple before the round goes live; a started eat finishes even if the round starts meanwhile. */
    public boolean tickPrefightGapple() {
        PlayerInventory inv = bot.getInventory();
        if (!prefightEating) {
            if (ctx.match().live()
                    || isHealing
                    || eatingSlot >= 0
                    || ctx.settings().difficulty().level() < ctx.tuning().integer(TuningKey.HEAL_PREFIGHT_GAPPLE_MIN_LEVEL)
                    || bot.hasPotionEffect(PotionEffectType.ABSORPTION)
                    || countPlainGoldenApples() < ctx.tuning().integer(TuningKey.HEAL_PREFIGHT_GAPPLE_MIN_COUNT)) {
                return false;
            }

            prefightEating = true;
            beginEatSlot(findItemExcl(Material.GOLDEN_APPLE, -1), inv);
            return true;
        }

        if (eatingSlot >= 0 && eatingSlot < 9) {
            InventoryUtils.select(ctx, eatingSlot);
        }

        ItemStack held = inv.getItemInMainHand();
        if (eatingSlot < 0 || !ItemKinds.is(held, Material.GOLDEN_APPLE) || held.getAmount() < eatingStartCount) {
            stopEatAction();
            prefightEating = false;
            return false;
        }

        eatingTicks++;
        if (eatingTicks > EAT_TIMEOUT_TICKS) {
            stopEatAction();
            prefightEating = false;
            return false;
        } else if (eatingTicks >= FORCE_CONSUME_TICKS && !forceConsumed) {
            forceConsumeFood(held);
            forceConsumed = true;
        } else if (!bot.isHandRaised()) {
            ctx.hands().useItem(EquipmentSlot.HAND);
        }

        return true;
    }

    private int countTotems() {
        PlayerInventory inv = bot.getInventory();
        int count = 0;

        for (int i = 0; i <= 40; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.TOTEM_OF_UNDYING)) {
                count++;
            }
        }

        return count;
    }

    private void tickRetreat(LivingEntity target) {
        if (retreatEnabled && target != null) {
            if (isTargetFleeing(target)) {
                targetFleeingTicks++;
            } else {
                targetFleeingTicks = 0;
            }

            double dist = Ent.dist(bot, target);
            boolean farAway = dist > ctx.tuning().number(TuningKey.HEAL_PURSUE_FAR_DIST).doubleValue();
            if (!farAway && targetFleeingTicks < ctx.tuning().integer(TuningKey.HEAL_FLEE_CONFIRM_TICKS)) {
                if (dist >= ctx.tuning().number(TuningKey.HEAL_RETREAT_SAFE_DIST).doubleValue()) {
                    ctx.controls().stopMoving();
                    clearSprint();
                } else {
                    fleeFrom(target);
                }
            } else {
                if (dist > ctx.tuning().number(TuningKey.HEAL_PURSUE_HOLD_DIST).doubleValue()) {
                    pursueToward(target);
                } else {
                    ctx.controls().stopMoving();
                    clearSprint();
                }
            }
        } else {
            ctx.controls().stopMoving();
            clearSprint();
        }
    }

    /** Sprints away without eating while the threat is close; eating would slow the bot to a walk under hits. */
    private boolean tickRunFirst(LivingEntity target) {
        if (!runFirst(retreatEnabled && target != null && ctx.tuning().flag(TuningKey.HEAL_RUN_FIRST),
                target == null ? Double.MAX_VALUE : Ent.dist(bot, target),
                ctx.tuning().number(TuningKey.HEAL_RUN_FIRST_DIST).doubleValue(),
                runFirstTicks, ctx.tuning().integer(TuningKey.HEAL_RUN_FIRST_MAX_TICKS))) {
            return false;
        }

        runFirstTicks++;
        stopEatAction();
        fleeFrom(target);
        ctx.controls().setSprinting(true);
        return true;
    }

    static boolean runFirst(boolean enabled, double dist, double runDist, int ticks, int maxTicks) {
        return enabled && dist < runDist && ticks < maxTicks;
    }

    private boolean isTargetFleeing(LivingEntity target) {
        return isFleeing(ctx.match().velocityOf(target), Ent.pos(bot), Ent.pos(target),
                ctx.tuning().number(TuningKey.HEAL_FLEE_MIN_SPEED).doubleValue(),
                ctx.tuning().number(TuningKey.HEAL_FLEE_DOT).doubleValue());
    }

    /** Whether a target moving at {@code v} is heading away from the bot fast enough to count as running. */
    public static boolean isFleeing(Vec3 v, Vec3 botPos, Vec3 targetPos, double minSpeed, double fleeDot) {
        double speed = Math.hypot(v.x(), v.z());
        if (speed < minSpeed) {
            return false;
        } else {
            double dx = targetPos.x() - botPos.x();
            double dz = targetPos.z() - botPos.z();
            double len = Math.hypot(dx, dz);
            if (len < 0.01) {
                return false;
            } else {
                double dot = (v.x() * dx + v.z() * dz) / (speed * len);
                return dot > fleeDot;
            }
        }
    }

    private void pursueToward(LivingEntity target) {
        clearSprint();
        Vec3 botPos = Ent.pos(bot);
        Vec3 targetPos = Ent.pos(target);
        double dx = targetPos.x() - botPos.x();
        double dz = targetPos.z() - botPos.z();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        look(yaw);
        if (LedgeGuard.wouldFallDangerously(ctx, dx, dz, SAFE_DROP)) {
            ctx.controls().stopMoving();
        } else {
            maybeStepJump(yaw);
            ctx.controls().moveForward();
        }
    }

    private void fleeFrom(LivingEntity threat) {
        clearSprint();
        Vec3 botPos = Ent.pos(bot);
        Vec3 threatPos = Ent.pos(threat);
        double awayX = botPos.x() - threatPos.x();
        double awayZ = botPos.z() - threatPos.z();
        float idealYaw = (float) Math.toDegrees(Math.atan2(-awayX, awayZ));
        float chosenYaw = idealYaw;
        boolean foundOpen = false;

        for (float off : FLEE_OFFSETS) {
            float yaw = idealYaw + off;
            if (isDirectionOpen(yaw)) {
                chosenYaw = yaw;
                foundOpen = true;
                break;
            }
        }

        look(chosenYaw);
        if (!foundOpen) {
            if (bot.isOnGround()) {
                ctx.controls().jumpOnce();
            }

            ctx.controls().stopMoving();
        } else {
            maybeStepJump(chosenYaw);
            ctx.controls().moveForward();
        }
    }

    private boolean isDirectionOpen(float yaw) {
        double rad = Math.toRadians(yaw);
        double dx = -Math.sin(rad);
        double dz = Math.cos(rad);
        if (LedgeGuard.wouldFallDangerously(ctx, dx, dz, SAFE_DROP)) {
            return false;
        } else {
            Vec3 botPos = Ent.pos(bot);
            BlockPos feet = BlockPos.containing(botPos.x() + dx, botPos.y(), botPos.z() + dz);
            BlockPos head = feet.above();
            BlockPos above = feet.above(2);
            boolean feetBlocked = isSolid(ctx, feet);
            boolean headBlocked = isSolid(ctx, head);
            return !feetBlocked && !headBlocked ? true : feetBlocked && !headBlocked && !isSolid(ctx, above);
        }
    }

    private void maybeStepJump(float yaw) {
        if (bot.isOnGround()) {
            double rad = Math.toRadians(yaw);
            double dx = -Math.sin(rad);
            double dz = Math.cos(rad);
            Vec3 botPos = Ent.pos(bot);
            BlockPos feet = BlockPos.containing(botPos.x() + dx, botPos.y(), botPos.z() + dz);
            if (isSolid(ctx, feet) && !isSolid(ctx, feet.above())) {
                ctx.controls().jumpOnce();
            }
        }
    }

    private static boolean isSolid(BotContext ctx, BlockPos pos) {
        return ctx.world().blocksMotion(pos);
    }

    private void look(float yaw) {
        ctx.controls().look(yaw, bot.getLocation().getPitch());
    }

    private void clearSprint() {
        ctx.controls().setSprinting(false);
    }

    private void tickEating() {
        PlayerInventory inv = bot.getInventory();
        if (eatingSlot >= 0 && eatingSlot < 9) {
            InventoryUtils.select(ctx, eatingSlot);
        }

        ItemStack held = inv.getItemInMainHand();
        boolean consumedOne = eatingSlot >= 0 && (!isFoodItem(held) || held.getAmount() < eatingStartCount);
        if (consumedOne) {
            stopEatAction();
            if (oneGapMode) {
                ctx.log().decision("HealingController", ONE_GAPPLE_DONE, "one-gap heal done - back to pressure.");
                exitHealing();
            } else {
                float predictedHp = predictHpFromEffects();
                if (predictedHp >= safeHp()) {
                    ctx.log().decision("HealingController", FOOD_CONSUMED, "food consumed, predicted hp=" + predictedHp + " - re-engaging.");
                    exitHealing();
                } else {
                    eatCooldownLeft = ctx.tuning().integer(TuningKey.HEAL_EAT_COOLDOWN_TICKS);
                }
            }
        } else if (eatCooldownLeft > 0) {
            eatCooldownLeft--;
        } else if (eatingSlot < 0) {
            int next = hungerOnlyHeal ? findBestRegularFood(-1) : findBestFood();
            if (hungerOnlyHeal && next < 0) {
                next = findBestFood();
            }

            if (next < 0) {
                ctx.log().decision("HealingController", FOOD_EXHAUSTED, "out of food (hp=" + bot.getHealth() + ").");
                reentryCd = ctx.tuning().integer(TuningKey.HEAL_REENTRY_NO_FOOD);
                exitHealing();
            } else {
                beginEatSlot(next, inv);
            }
        } else {
            eatingTicks++;
            if (eatingTicks > EAT_TIMEOUT_TICKS) {
                ctx.log().decision("HealingController", EAT_TIMEOUT, "eat hard-timeout on " + held.getType() + ".", true);
                int stuck = eatingSlot;
                stopEatAction();
                int alt = findBestFoodExcl(stuck);
                if (alt < 0) {
                    reentryCd = ctx.tuning().integer(TuningKey.HEAL_REENTRY_NO_ALT);
                    exitHealing();
                } else {
                    beginEatSlot(alt, inv);
                }
            } else if (eatingTicks >= FORCE_CONSUME_TICKS && !forceConsumed) {
                ctx.log().decision("HealingController", FORCE_CONSUME_START, "force-consuming " + held.getType() + " (vanilla didn't eat).");
                forceConsumeFood(held);
                forceConsumed = true;
            } else {
                if (!bot.isHandRaised()) {
                    ctx.hands().useItem(EquipmentSlot.HAND);
                }
            }
        }
    }

    private void forceConsumeFood(ItemStack food) {
        if (bot.isHandRaised() && bot.getActiveItem().getType() == food.getType()) {
            bot.completeUsingActiveItem();
            ctx.log().decision("HealingController", FORCE_CONSUME_DONE, "force-consumed food (hp=" + bot.getHealth() + ", hunger=" + bot.getFoodLevel() + ").");
        }
    }

    private float predictHpFromEffects() {
        float hp = (float) bot.getHealth();
        int hunger = bot.getFoodLevel();
        float sat = bot.getSaturation();
        // With natural regeneration off the core cancels the saturation heal, so saturation is not HP.
        if (!ctx.naturalRegenOff() && hunger >= 18 && sat > 0.0F) {
            hp += Math.min(sat, (float) Ent.maxHealth(bot) - hp);
        }

        PotionEffect regen = bot.getPotionEffect(PotionEffectType.REGENERATION);
        if (regen != null) {
            hp += regenHeals(regen.getDuration(), regen.getAmplifier());
        }

        float absorption = (float) bot.getAbsorptionAmount();
        hp += absorption;
        return Math.min((float) Ent.maxHealth(bot) + absorption, hp);
    }

    static float regenHeals(int ticks, int amplifier) {
        int interval = Math.max(1, 50 / (amplifier + 1));
        return (float) ticks / interval;
    }

    private float predictHpAfterEating(ItemStack food) {
        float hp = (float) bot.getHealth();
        if (ItemKinds.isEmpty(food)) {
            return hp;
        } else {
            FoodProperties fp = food.getData(DataComponentTypes.FOOD);
            if (fp == null) {
                return hp;
            } else {
                int curHunger = bot.getFoodLevel();
                float curSat = bot.getSaturation();
                int newHunger = Math.min(20, curHunger + fp.nutrition());
                float newSat = Math.min((float) newHunger, curSat + fp.saturation());
                if (newHunger >= 18 && newSat > 0.0F) {
                    hp += newSat;
                }

                if (food.getType() == Material.GOLDEN_APPLE) {
                    hp += 4.0F;
                    hp += 4.0F;
                } else if (food.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
                    hp += 20.0F;
                    hp += 8.0F;
                }

                return Math.min((float) Ent.maxHealth(bot) + 16.0F, hp);
            }
        }
    }

    private void beginEatSlot(int slot, PlayerInventory inv) {
        if (slot >= 9) {
            slot = InventoryUtils.moveToHotbarSafe(ctx, slot);
        }

        eatingSlot = slot;
        eatingTicks = 0;
        ItemStack atSlot = inv.getItem(slot);
        eatingStartCount = atSlot == null ? 0 : atSlot.getAmount();
        forceConsumed = false;
        InventoryUtils.select(ctx, slot);
        if (bot.isHandRaised()) {
            ctx.hands().releaseUseItem();
        }

        ctx.hands().useItem(EquipmentSlot.HAND);
        ctx.log().decision("HealingController", EAT_START, "eating " + inv.getItemInMainHand().getType() + " (hp=" + bot.getHealth() + ", hunger=" + bot.getFoodLevel() + ").");
    }

    private void stopEatAction() {
        if (bot.isHandRaised()) {
            ctx.hands().releaseUseItem();
        }

        eatingSlot = -1;
        eatingTicks = 0;
        forceConsumed = false;
    }

    private int findBestFood() {
        return findBestFoodExcl(-1);
    }

    private int findBestFoodExcl(int exclude) {
        int ega = findItemExcl(Material.ENCHANTED_GOLDEN_APPLE, exclude);
        if (ega >= 0) {
            return ega;
        }

        int head = findGoldenHeadExcl(exclude);
        if (head >= 0) {
            return head;
        }

        int ga = findItemExcl(Material.GOLDEN_APPLE, exclude);
        if (ga >= 0) {
            return ga;
        }

        // Plain food heals nothing without natural regeneration, so it is only eaten for hunger.
        return ctx.naturalRegenOff() ? -1 : findBestRegularFood(exclude);
    }

    private int findGoldenHeadExcl(int exclude) {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 36; i++) {
            if (i != exclude && isGoldenHead(inv.getItem(i))) {
                return i;
            }
        }

        return -1;
    }

    private int findBestRegularFood(int exclude) {
        PlayerInventory inv = bot.getInventory();
        int bestSlot = -1;
        float bestSat = -1.0F;

        for (int pass = 0; pass < 2; pass++) {
            int from = pass == 0 ? 0 : 9;
            int to = pass == 0 ? 9 : 36;

            for (int i = from; i < to; i++) {
                if (i != exclude) {
                    ItemStack stack = inv.getItem(i);
                    if (isFoodItem(stack) && stack.getType() != Material.GOLDEN_APPLE && stack.getType() != Material.ENCHANTED_GOLDEN_APPLE) {
                        FoodProperties fp = stack.getData(DataComponentTypes.FOOD);
                        if (fp != null && fp.saturation() > bestSat) {
                            bestSat = fp.saturation();
                            bestSlot = i;
                        }
                    }
                }
            }

            if (bestSlot >= 0) {
                break;
            }
        }

        return bestSlot;
    }

    private int findItemExcl(Material item, int exclude) {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 9; i++) {
            if (i != exclude && ItemKinds.is(inv.getItem(i), item)) {
                return i;
            }
        }

        for (int i = 9; i < 36; i++) {
            if (i != exclude && ItemKinds.is(inv.getItem(i), item)) {
                return i;
            }
        }

        return -1;
    }

    private int countPlainGoldenApples() {
        PlayerInventory inv = bot.getInventory();
        int count = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (ItemKinds.is(stack, Material.GOLDEN_APPLE)) {
                count += stack.getAmount();
            }
        }

        return count;
    }

    // The core's golden head is a player head with a consume component and no food component.
    private static boolean isGoldenHead(ItemStack stack) {
        return ItemKinds.is(stack, Material.PLAYER_HEAD) && stack.hasData(DataComponentTypes.CONSUMABLE);
    }

    private static boolean isFoodItem(ItemStack stack) {
        return !ItemKinds.isEmpty(stack) && (stack.getData(DataComponentTypes.FOOD) != null || isGoldenHead(stack));
    }

    private static boolean isHealFood(ItemStack stack) {
        return ItemKinds.is(stack, Material.GOLDEN_APPLE) || ItemKinds.is(stack, Material.ENCHANTED_GOLDEN_APPLE) || isGoldenHead(stack);
    }

    public void setGapTools(EnderPearlController pearl, CobwebTrapController web) {
        enderPearl = pearl;
        cobwebTrap = web;
    }

    public void setBuildFight(BuildFightController build) {
        buildFight = build;
    }

    private boolean buyHealingGap(LivingEntity target) {
        if (gapBuyCooldown > 0) {
            return false;
        } else {
            double dist = Ent.dist(bot, target);
            if (dist > ctx.tuning().number(TuningKey.HEAL_GAP_BUY_FAR_DIST).doubleValue()) {
                return true;
            } else if (enderPearl != null && enderPearl.tryThrow(target)) {
                gapBuyCooldown = ctx.tuning().integer(TuningKey.HEAL_GAP_BUY_COOLDOWN);
                ctx.log().decision("HealingController", HEAL_GAP_PEARL, "bought a heal gap with a short pearl.");
                return true;
            } else if (cobwebTrap != null && cobwebTrap.tryEmergencyWeb(target)) {
                gapBuyCooldown = ctx.tuning().integer(TuningKey.HEAL_GAP_BUY_COOLDOWN);
                ctx.log().decision("HealingController", HEAL_GAP_WEB, "bought a heal gap with a web.");
                return true;
            } else if (dist <= ctx.tuning().number(TuningKey.HEAL_GAP_BUY_NEAR_DIST).doubleValue()
                    && bot.hasLineOfSight(target)) {
                Vec3 botPos = Ent.pos(bot);
                Vec3 targetPos = Ent.pos(target);
                double dx = targetPos.x() - botPos.x();
                double dz = targetPos.z() - botPos.z();
                double dy = targetPos.y() + target.getHeight() * 0.5 - Ent.eyePos(bot).y();
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
                ctx.controls().look(yaw, pitch);
                ctx.controls().setSprinting(true);
                ctx.hands().attack(target);
                ctx.hands().swing(EquipmentSlot.HAND);
                gapBuyCooldown = ctx.tuning().integer(TuningKey.HEAL_GAP_BUY_SHORT_COOLDOWN);
                ctx.log().decision("HealingController", HEAL_GAP_KNOCKBACK, "bought a heal gap with a knockback hit.");
                return true;
            } else {
                return false;
            }
        }
    }

    private void enterHealing(int foodSlot, boolean retreat) {
        isHealing = true;
        retreatEnabled = retreat;
        healTicks = 0;
        runFirstTicks = 0;
        if (bot.isHandRaised()) {
            ctx.hands().releaseUseItem();
        }

        clearSprint();
        beginEatSlot(foodSlot, bot.getInventory());
        ctx.log().decision("HealingController", HEAL_START, "heal start (hp=" + bot.getHealth() + ", hunger=" + bot.getFoodLevel() + ", retreat=" + retreat + ").");
    }

    private void exitHealing() {
        oneGapMode = false;
        syncHealRequested = false;
        if (isHealing) {
            stopEatAction();
            isHealing = false;
            retreatEnabled = false;
            healTicks = 0;
            eatCooldownLeft = 0;
            targetFleeingTicks = 0;
            ctx.controls().stopMoving();
            clearSprint();
            ctx.log().decision("HealingController", HEAL_DONE, "heal done (hp=" + bot.getHealth() + ", hunger=" + bot.getFoodLevel() + ").");
        }
    }
}
