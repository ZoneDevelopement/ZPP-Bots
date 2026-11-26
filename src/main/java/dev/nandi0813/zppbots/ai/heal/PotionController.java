package dev.nandi0813.zppbots.ai.heal;

import dev.nandi0813.zppbots.ai.ranged.EnderPearlController;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

public final class PotionController {

    private static final List<PotionEffectType> BUFF_EFFECTS = List.of(
            PotionEffectType.STRENGTH,
            PotionEffectType.SPEED,
            PotionEffectType.FIRE_RESISTANCE,
            PotionEffectType.REGENERATION,
            PotionEffectType.INVISIBILITY);
    private static final int DRINK_TICKS = 32;
    private static final int HONEY_DRINK_TICKS = 40;
    private static final int SPLASH_WINDUP_TICKS = 3;
    private static final double MAX_SPLASH_FALL = 1.5;

    private final BotContext ctx;
    private final Player bot;
    private final Deque<PotionEffectType> potionQueue = new ArrayDeque<>();
    private int queueGapTicks = 0;
    private boolean isDrinking = false;
    private int drinkTicksLeft = 0;
    private int splashWindup = 0;
    private int splashSlot = -1;
    private PotionEffectType splashEffect = null;
    private boolean splashChaining = false;
    private boolean hadTarget = false;
    private int rescanTicks = 0;
    private boolean prevStrength = false;
    private boolean prevSpeed = false;
    private boolean prevFireRes = false;
    private boolean prevRegen = false;
    private boolean prevInvis = false;

    public PotionController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean tick(LivingEntity target, double dist, boolean allowDrinking) {
        if (!ctx.settings().usePotions()) {
            hadTarget = false;
            if (isSplashing()) {
                endSplashSession();
            }

            return false;
        } else {
            if (splashWindup > 0 && splashChaining) {
                float hpNow = (float) bot.getHealth();
                if (hpNow < ctx.tuning().number(TuningKey.POTION_TURTLE_MASTER_HP).floatValue()
                        && !hasEffect(PotionEffectType.RESISTANCE)
                        && canUsePotionNow(PotionEffectType.RESISTANCE, allowDrinking)
                        || hpNow <= ctx.tuning().number(TuningKey.POTION_INSTANT_HEAL_HP).floatValue()
                        && canUsePotionNow(PotionEffectType.INSTANT_HEALTH, allowDrinking)) {
                    PotionEffectType pending = splashEffect;
                    endSplashSession();
                    if (pending != null && !potionQueue.contains(pending)) {
                        potionQueue.addFirst(pending);
                    }
                }
            }

            if (splashWindup > 0) {
                PlayerInventory inv = bot.getInventory();
                splashSlot = reacquireSplashSlot(inv);
                if (splashSlot < 0) {
                    endSplashSession();
                    return false;
                } else {
                    InventoryUtils.select(ctx, splashSlot);
                    ctx.controls().setSneaking(true);
                    lookDown(target);
                    splashWindup--;
                    if (splashWindup == 0) {
                        ctx.hands().useItem(EquipmentSlot.HAND);
                        ctx.hands().swing(EquipmentSlot.HAND);
                        splashSlot = -1;
                        splashEffect = null;
                        if (!splashChaining || !beginNextSplashFromQueue()) {
                            endSplashSession();
                            queueGapTicks = gapTicks();
                        }
                    }

                    return true;
                }
            } else {
                if (isDrinking) {
                    drinkTicksLeft--;
                    if (drinkTicksLeft > 0 && bot.isHandRaised()) {
                        return true;
                    }

                    isDrinking = false;
                    queueGapTicks = gapTicks();
                }

                if (allowDrinking && bot.hasPotionEffect(PotionEffectType.POISON)) {
                    int honeySlot = findItem(Material.HONEY_BOTTLE, bot.getInventory());
                    if (honeySlot >= 0) {
                        drinkItem(honeySlot, bot.getInventory(), HONEY_DRINK_TICKS);
                        return true;
                    }
                }

                float hp = (float) bot.getHealth();
                if (hp < ctx.tuning().number(TuningKey.POTION_TURTLE_MASTER_HP).floatValue()
                        && !hasEffect(PotionEffectType.RESISTANCE)
                        && tryUsePotionNow(PotionEffectType.RESISTANCE, allowDrinking, target)) {
                    queueGapTicks = gapTicks();
                    return isDrinking || splashWindup > 0;
                } else if (hp <= ctx.tuning().number(TuningKey.POTION_INSTANT_HEAL_HP).floatValue()
                        && tryUsePotionNow(PotionEffectType.INSTANT_HEALTH, allowDrinking, target)) {
                    queueGapTicks = gapTicks();
                    return isDrinking || splashWindup > 0;
                } else if (hp <= ctx.difficulty().potionFleeHealHp && target != null
                        && (dist >= ctx.tuning().number(TuningKey.POTION_FLEE_HEAL_DIST).doubleValue()
                        || HealingController.isFleeing(ctx.match().velocityOf(target), Ent.pos(bot), Ent.pos(target),
                        ctx.tuning().number(TuningKey.HEAL_FLEE_MIN_SPEED).doubleValue(),
                        ctx.tuning().number(TuningKey.HEAL_FLEE_DOT).doubleValue()))
                        && tryUsePotionNow(PotionEffectType.INSTANT_HEALTH, allowDrinking, target)) {
                    queueGapTicks = gapTicks();
                    return isDrinking || splashWindup > 0;
                } else {
                    boolean hasTarget = target != null;
                    if (!hasTarget) {
                        checkEffectExpiry();
                        potionQueue.clear();
                        hadTarget = false;
                        return false;
                    } else {
                        if (hasTarget && !hadTarget) {
                            enqueueAvailableBuffs();
                            rescanTicks = ctx.tuning().integer(TuningKey.POTION_REBUFF_RESCAN_TICKS);
                        }

                        hadTarget = hasTarget;
                        if (hasTarget && --rescanTicks <= 0) {
                            enqueueAvailableBuffs();
                            rescanTicks = ctx.tuning().integer(TuningKey.POTION_REBUFF_RESCAN_TICKS);
                        }

                        checkEffectExpiry();
                        if (queueGapTicks > 0) {
                            queueGapTicks--;
                            return false;
                        } else {
                            potionQueue.removeIf(e -> !needsBuff(e));
                            if (canSplashAtFeet() && beginNextSplashFromQueue()) {
                                ctx.controls().setSneaking(true);
                                lookDown(target);
                                splashWindup = SPLASH_WINDUP_TICKS;
                                splashChaining = true;
                                return true;
                            } else {
                                if (allowDrinking) {
                                    Iterator<PotionEffectType> it = potionQueue.iterator();

                                    while (it.hasNext()) {
                                        PotionEffectType effect = it.next();
                                        int slot = findPotion(Material.POTION, effect, bot.getInventory());
                                        if (slot >= 0) {
                                            it.remove();
                                            drinkPotion(slot, bot.getInventory());
                                            return true;
                                        }

                                        if (findPotion(Material.SPLASH_POTION, effect, bot.getInventory()) < 0) {
                                            it.remove();
                                        }
                                    }
                                }

                                return false;
                            }
                        }
                    }
                }
            }
        }
    }

    private void lookDown(LivingEntity target) {
        float yaw = bot.getLocation().getYaw();
        if (target != null && ctx.tuning().flag(TuningKey.POTION_SPLASH_AWAY)) {
            yaw = EnderPearlController.awayYaw(bot.getX() - target.getX(), bot.getZ() - target.getZ());
        }

        ctx.controls().look(yaw, ctx.tuning().number(TuningKey.POTION_SPLASH_PITCH).floatValue());
    }

    private void enqueueAvailableBuffs() {
        for (PotionEffectType effect : BUFF_EFFECTS) {
            if (needsBuff(effect) && !potionQueue.contains(effect) && hasPotionInInventory(effect)) {
                potionQueue.add(effect);
            }
        }
    }

    private void checkEffectExpiry() {
        boolean curStrength = hasEffect(PotionEffectType.STRENGTH);
        boolean curSpeed = hasEffect(PotionEffectType.SPEED);
        boolean curFireRes = hasEffect(PotionEffectType.FIRE_RESISTANCE);
        boolean curRegen = hasEffect(PotionEffectType.REGENERATION);
        boolean curInvis = hasEffect(PotionEffectType.INVISIBILITY);
        maybeRequeue(PotionEffectType.STRENGTH, prevStrength, curStrength);
        maybeRequeue(PotionEffectType.SPEED, prevSpeed, curSpeed);
        maybeRequeue(PotionEffectType.FIRE_RESISTANCE, prevFireRes, curFireRes);
        maybeRequeue(PotionEffectType.REGENERATION, prevRegen, curRegen);
        maybeRequeue(PotionEffectType.INVISIBILITY, prevInvis, curInvis);
        prevStrength = curStrength;
        prevSpeed = curSpeed;
        prevFireRes = curFireRes;
        prevRegen = curRegen;
        prevInvis = curInvis;
    }

    private void maybeRequeue(PotionEffectType effect, boolean hadIt, boolean hasIt) {
        if (hadIt && !hasIt && !potionQueue.contains(effect) && hasPotionInInventory(effect)) {
            potionQueue.add(effect);
        }
    }

    private boolean tryUsePotionNow(PotionEffectType wantedEffect, boolean allowDrinking, LivingEntity target) {
        PlayerInventory inv = bot.getInventory();
        int slot = canSplashAtFeet() ? findPotion(Material.SPLASH_POTION, wantedEffect, inv) : -1;
        if (slot >= 0) {
            throwSplashPotion(slot, inv, wantedEffect, target);
            return true;
        } else {
            if (allowDrinking) {
                slot = findPotion(Material.POTION, wantedEffect, inv);
                if (slot >= 0) {
                    drinkPotion(slot, inv);
                    return true;
                }
            }

            return false;
        }
    }

    private boolean canUsePotionNow(PotionEffectType effect, boolean allowDrinking) {
        PlayerInventory inv = bot.getInventory();
        return canSplashAtFeet() && findPotion(Material.SPLASH_POTION, effect, inv) >= 0
                || allowDrinking && findPotion(Material.POTION, effect, inv) >= 0;
    }

    private boolean hasPotionInInventory(PotionEffectType effect) {
        PlayerInventory inv = bot.getInventory();
        return findPotion(Material.SPLASH_POTION, effect, inv) >= 0 || findPotion(Material.POTION, effect, inv) >= 0;
    }

    private int findPotion(Material potionItem, PotionEffectType wantedEffect, PlayerInventory inv) {
        int best = -1;
        int bestAmp = -1;

        for (int pass = 0; pass < 2; pass++) {
            int from = pass == 0 ? 0 : 9;
            int to = pass == 0 ? 9 : 36;

            for (int i = from; i < to; i++) {
                ItemStack stack = inv.getItem(i);
                if (ItemKinds.is(stack, potionItem) && grants(stack, wantedEffect)) {
                    int amp = amplifierOf(stack, wantedEffect);
                    if (amp > bestAmp) {
                        best = i;
                        bestAmp = amp;
                    }
                }
            }
        }

        return best;
    }

    private boolean canSplashAtFeet() {
        if (bot.isOnGround()) {
            return true;
        } else if (bot.isGliding()) {
            return false;
        } else {
            BlockPos p = Ent.blockPos(bot);

            for (int d = 1; d <= (int) Math.ceil(MAX_SPLASH_FALL); d++) {
                if (!ctx.world().isAir(p.below(d))) {
                    return true;
                }
            }

            return false;
        }
    }

    private static List<PotionEffect> effectsOf(ItemStack stack) {
        List<PotionEffect> all = new ArrayList<>();
        if (stack != null && stack.getItemMeta() instanceof PotionMeta meta) {
            PotionType base = meta.getBasePotionType();
            if (base != null) {
                all.addAll(base.getPotionEffects());
            }
            all.addAll(meta.getCustomEffects());
        }
        return all;
    }

    private static boolean grants(ItemStack stack, PotionEffectType effect) {
        if (effect == null) {
            return false;
        } else {
            for (PotionEffect inst : effectsOf(stack)) {
                if (inst.getType().equals(effect)) {
                    return true;
                }
            }

            return false;
        }
    }

    private int reacquireSplashSlot(PlayerInventory inv) {
        if (splashSlot >= 0
                && splashSlot < 9
                && ItemKinds.is(inv.getItem(splashSlot), Material.SPLASH_POTION)
                && grants(inv.getItem(splashSlot), splashEffect)) {
            return splashSlot;
        } else if (splashEffect == null) {
            return -1;
        } else {
            int slot = findPotion(Material.SPLASH_POTION, splashEffect, inv);
            return slot < 0 ? -1 : ensureHotbar(slot);
        }
    }

    private void endSplashSession() {
        ctx.controls().setSneaking(false);
        splashWindup = 0;
        splashSlot = -1;
        splashEffect = null;
        splashChaining = false;
    }

    public boolean isSplashing() {
        return splashWindup > 0 || splashChaining;
    }

    public boolean isDrinking() {
        return isDrinking;
    }

    private void throwSplashPotion(int slot, PlayerInventory inv, PotionEffectType effect, LivingEntity target) {
        slot = ensureHotbar(slot);
        InventoryUtils.select(ctx, slot);
        ctx.controls().setSneaking(true);
        lookDown(target);
        splashSlot = slot;
        splashEffect = effect;
        splashWindup = SPLASH_WINDUP_TICKS;
        splashChaining = false;
        isDrinking = false;
    }

    private boolean beginNextSplashFromQueue() {
        PlayerInventory inv = bot.getInventory();
        Iterator<PotionEffectType> it = potionQueue.iterator();

        while (it.hasNext()) {
            PotionEffectType effect = it.next();
            if (!needsBuff(effect)) {
                it.remove();
            } else {
                int slot = findPotion(Material.SPLASH_POTION, effect, inv);
                if (slot >= 0) {
                    it.remove();
                    slot = ensureHotbar(slot);
                    InventoryUtils.select(ctx, slot);
                    splashSlot = slot;
                    splashEffect = effect;
                    splashWindup = potChainTicks();
                    return true;
                }
            }
        }

        return false;
    }

    private int potChainTicks() {
        return ctx.difficulty().potIntervalTicks();
    }

    private int gapTicks() {
        return Math.max(ctx.tuning().integer(TuningKey.POTION_QUEUE_GAP_TICKS), potChainTicks());
    }

    private void drinkPotion(int slot, PlayerInventory inv) {
        drinkItem(slot, inv, DRINK_TICKS);
    }

    private void drinkItem(int slot, PlayerInventory inv, int ticks) {
        slot = ensureHotbar(slot);
        InventoryUtils.select(ctx, slot);
        ctx.hands().useItem(EquipmentSlot.HAND);
        isDrinking = true;
        drinkTicksLeft = ticks;
    }

    private int findItem(Material item, PlayerInventory inv) {
        for (int i = 0; i < 36; i++) {
            if (ItemKinds.is(inv.getItem(i), item)) {
                return i;
            }
        }

        return -1;
    }

    private int ensureHotbar(int slot) {
        return InventoryUtils.moveToHotbarSafe(ctx, slot);
    }

    private boolean hasEffect(PotionEffectType effect) {
        return bot.hasPotionEffect(effect);
    }

    private boolean needsBuff(PotionEffectType effect) {
        PotionEffect cur = bot.getPotionEffect(effect);
        return cur == null ? true : bestCarriedAmplifier(effect) > cur.getAmplifier();
    }

    private int bestCarriedAmplifier(PotionEffectType effect) {
        PlayerInventory inv = bot.getInventory();
        int best = -1;

        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (ItemKinds.is(st, Material.SPLASH_POTION) || ItemKinds.is(st, Material.POTION)) {
                best = Math.max(best, amplifierOf(st, effect));
            }
        }

        return best;
    }

    private static int amplifierOf(ItemStack stack, PotionEffectType effect) {
        int best = -1;

        for (PotionEffect inst : effectsOf(stack)) {
            if (inst.getType().equals(effect)) {
                best = Math.max(best, inst.getAmplifier());
            }
        }

        return best;
    }
}
