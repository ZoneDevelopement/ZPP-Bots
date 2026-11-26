package dev.nandi0813.zppbots.ai.elytra;

import dev.nandi0813.zppbots.ai.gear.ArmorEvaluator;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.ElytraFlight;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class ElytraFollowController {

    private final BotContext ctx;
    private final Player bot;
    private boolean flying = false;
    private int chestSaveSlot = -1;
    private int rocketCd = 0;

    public ElytraFollowController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean isFlying() {
        return flying;
    }

    public boolean tickFly(Vec3 slotXZ, double leaderY) {
        if (!ensureElytra()) {
            land();
            return false;
        }
        flying = true;
        if (rocketCd > 0) {
            rocketCd--;
        }
        if (bot.isOnGround()) {
            ctx.controls().jumpOnce();
            return true;
        }
        double altitude = ctx.tuning().number(TuningKey.ELYTRA_FOLLOW_ALTITUDE).doubleValue();
        Vec3 goal = new Vec3(slotXZ.x(), leaderY + altitude, slotXZ.z());
        Vec3 to = goal.subtract(Ent.pos(bot));
        double horiz = Math.sqrt(to.x() * to.x() + to.z() * to.z());
        float yaw = yawTo(to);
        float pitch = pitchTo(to, horiz);
        float[] safe = ElytraObstacleGuard.towards(ctx, yaw, pitch, to.length());
        ctx.controls().look(safe[0], safe[1]);
        if (!bot.isGliding()) {
            ElytraFlight.beginGlide(ctx);
            if (rocketCd <= 0 && hasRocket()) {
                fireRocket();
                rocketCd = ctx.tuning().integer(TuningKey.ELYTRA_FOLLOW_ROCKET_INTERVAL);
            }
            return true;
        }
        boolean behind = horiz > ctx.tuning().number(TuningKey.ELYTRA_FOLLOW_CATCHUP_DIST).doubleValue();
        double altTolerance = ctx.tuning().number(TuningKey.ELYTRA_FOLLOW_ALT_TOLERANCE).doubleValue();
        boolean tooLow = Ent.pos(bot).y() < goal.y() - altTolerance;
        boolean closeOrAhead = horiz < ctx.tuning().number(TuningKey.ELYTRA_FOLLOW_HOLD_DIST).doubleValue();
        if (rocketCd <= 0 && !closeOrAhead && (behind || tooLow) && hasRocket()) {
            fireRocket();
            rocketCd = ctx.tuning().integer(TuningKey.ELYTRA_FOLLOW_ROCKET_INTERVAL);
        }
        return true;
    }

    static float yawTo(Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-to.x(), to.z()));
    }

    static float pitchTo(Vec3 to, double horiz) {
        float pitch = (float) (-Math.toDegrees(Math.atan2(to.y(), Math.max(0.5, horiz))));
        return Math.max(-45.0F, Math.min(45.0F, pitch));
    }

    private boolean hasRocket() {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 36; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.FIREWORK_ROCKET)) {
                return true;
            }
        }
        return false;
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

    public void land() {
        if (!flying) {
            return;
        }
        flying = false;
        rocketCd = 0;
        if (chestSaveSlot >= 0) {
            PlayerInventory inv = bot.getInventory();
            ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
            int plateSlot = chestSaveSlot;
            if (!isChestPiece(inv.getItem(plateSlot))) {
                plateSlot = -1;
                for (int i = 0; i < 36; i++) {
                    if (isChestPiece(inv.getItem(i))) {
                        plateSlot = i;
                        break;
                    }
                }
            }
            if (plateSlot >= 0) {
                ItemStack saved = inv.getItem(plateSlot).clone();
                inv.setItem(plateSlot, ItemKinds.isEmpty(chest) ? null : chest.clone());
                bot.getEquipment().setItem(EquipmentSlot.CHEST, saved);
            }
            chestSaveSlot = -1;
        }
        if (bot.isGliding()) {
            bot.setGliding(false);
        }
    }

    public void reset() {
        land();
    }

    private boolean ensureElytra() {
        ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
        if (ElytraFlight.isUsable(chest)) {
            return true;
        }
        int slot = findElytra();
        if (slot < 0) {
            return false;
        }
        PlayerInventory inv = bot.getInventory();
        ItemStack elytra = inv.getItem(slot).clone();
        inv.setItem(slot, null);
        bot.getEquipment().setItem(EquipmentSlot.CHEST, elytra);
        if (ItemKinds.is(chest, Material.ELYTRA)) {
            inv.setItem(slot, chest.clone());
        } else if (!ItemKinds.isEmpty(chest)) {
            inv.setItem(slot, chest.clone());
            chestSaveSlot = slot;
        } else {
            chestSaveSlot = -1;
        }
        return true;
    }

    private int findElytra() {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 36; i++) {
            if (ElytraFlight.isUsable(inv.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isChestPiece(ItemStack stack) {
        return ArmorEvaluator.getSlot(stack) == EquipmentSlot.CHEST && !ItemKinds.is(stack, Material.ELYTRA);
    }
}
