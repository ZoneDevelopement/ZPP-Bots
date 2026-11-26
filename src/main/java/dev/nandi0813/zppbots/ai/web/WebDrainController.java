package dev.nandi0813.zppbots.ai.web;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class WebDrainController {

    private static final int WATER_SCAN_R = 2;
    private static final double REACH = 4.5;

    private final BotContext ctx;
    private final Player bot;
    private int cooldown = 0;
    private int reactTicks = -1;

    public WebDrainController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean tick(LivingEntity target) {
        if (cooldown > 0) {
            cooldown--;
            reactTicks = -1;
            return false;
        } else if (target != null && Ent.alive(target)) {
            if (!ctx.settings().useWebDrain()) {
                reactTicks = -1;
                return false;
            } else if (!targetInCobweb(target)) {
                reactTicks = -1;
                return false;
            } else {
                int bucketSlot = findEmptyBucket();
                if (bucketSlot < 0) {
                    reactTicks = -1;
                    return false;
                } else {
                    BlockPos water = findDrainableWater(target);
                    if (water == null) {
                        reactTicks = -1;
                        return false;
                    } else {
                        if (reactTicks < 0) {
                            reactTicks = ctx.difficulty().webDrainDelayTicks();
                        }

                        if (reactTicks > 0) {
                            reactTicks--;
                            return false;
                        } else {
                            reactTicks = -1;
                            if (!ctx.settings().rollWebDrain()) {
                                cooldown = ctx.tuning().integer(TuningKey.WEB_DRAIN_SKIP_COOLDOWN);
                                return false;
                            } else {
                                drain(water, bucketSlot);
                                cooldown = ctx.tuning().integer(TuningKey.WEB_DRAIN_COOLDOWN);
                                return true;
                            }
                        }
                    }
                }
            }
        } else {
            reactTicks = -1;
            return false;
        }
    }

    public boolean isExecuting() {
        return cooldown > 0;
    }

    public void reset() {
        cooldown = 0;
        reactTicks = -1;
    }

    private boolean targetInCobweb(LivingEntity target) {
        BlockPos feet = Ent.blockPos(target);
        return ctx.world().type(feet) == Material.COBWEB || ctx.world().type(feet.above()) == Material.COBWEB;
    }

    private int findEmptyBucket() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.BUCKET)) {
                return i;
            }
        }

        return -1;
    }

    private BlockPos findDrainableWater(LivingEntity target) {
        BlockPos base = Ent.blockPos(target);
        Vec3 eye = Ent.eyePos(bot);
        BlockPos best = null;
        double bestD = REACH * REACH;

        for (int dx = -WATER_SCAN_R; dx <= WATER_SCAN_R; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -WATER_SCAN_R; dz <= WATER_SCAN_R; dz++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    if (ctx.world().isWaterSource(p)) {
                        double d = eye.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                        if (d <= bestD) {
                            bestD = d;
                            best = p;
                        }
                    }
                }
            }
        }

        return best;
    }

    private void drain(BlockPos water, int bucketSlot) {
        PlayerInventory inv = bot.getInventory();
        int prevSel = InventoryUtils.selected(ctx);
        int useSlot = bucketSlot;
        boolean swapped = false;
        int storedAt = -1;
        if (bucketSlot > 8) {
            storedAt = bucketSlot;
            useSlot = prevSel;
            ItemStack bucket = copy(inv.getItem(bucketSlot));
            ItemStack held = copy(inv.getItem(prevSel));
            inv.setItem(prevSel, bucket);
            inv.setItem(bucketSlot, held);
            swapped = true;
        }

        InventoryUtils.select(ctx, useSlot);
        aimAt(water);
        ctx.hands().useItem(EquipmentSlot.HAND);
        ctx.hands().swing(EquipmentSlot.HAND);
        if (swapped) {
            ItemStack nowWater = copy(inv.getItem(useSlot));
            ItemStack original = copy(inv.getItem(storedAt));
            inv.setItem(useSlot, original);
            inv.setItem(storedAt, nowWater);
        }

        InventoryUtils.select(ctx, prevSel);
    }

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }

    private void aimAt(BlockPos water) {
        Vec3 eye = Ent.eyePos(bot);
        double dx = water.getX() + 0.5 - eye.x();
        double dy = water.getY() + 0.5 - eye.y();
        double dz = water.getZ() + 0.5 - eye.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        ctx.controls().look((float) Math.toDegrees(Math.atan2(-dx, dz)), (float) (-Math.toDegrees(Math.atan2(dy, horiz))));
    }
}
