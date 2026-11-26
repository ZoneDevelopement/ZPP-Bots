package dev.nandi0813.zppbots.ai.web;

import dev.nandi0813.zppbots.ai.heal.HealingController;
import dev.nandi0813.zppbots.ai.ranged.EnderPearlController;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
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

public final class BubbleWebController {

    private static final int PLACE_WINDUP = 2;
    private static final int HEAL_MAX_TICKS = 120;
    private static final int PEARL_MAX_TICKS = 40;
    private static final int PLACE_FAIL_CAP = 20;

    private final BotContext ctx;
    private final Player bot;
    private Phase phase = Phase.IDLE;
    private int phaseTick = 0;
    private int placeFails = 0;
    private int cooldown = 0;
    private int prevSlot = -1;

    public BubbleWebController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean isActive() {
        return phase != Phase.IDLE;
    }

    public boolean canStart() {
        return cooldown == 0
                && phase == Phase.IDLE
                && bot.isOnGround()
                && countItem(Material.COBWEB) >= 2
                && countItem(Material.ENDER_PEARL) >= 1;
    }

    public boolean tick(LivingEntity target, HealingController healingController, EnderPearlController enderPearlController) {
        if (cooldown > 0) {
            cooldown--;
        }

        if (phase == Phase.IDLE) {
            if (!canStart()) {
                return false;
            }

            prevSlot = InventoryUtils.selected(ctx);
            phase = Phase.FEET;
            phaseTick = 0;
        }

        ctx.controls().stopMoving();
        switch (phase) {
            case FEET:
                return tickPlace(Ent.blockPos(bot), Phase.HEAD);
            case HEAD:
                return tickPlace(Ent.blockPos(bot).above(), Phase.HEAL);
            case HEAL:
                if (target != null && Ent.alive(target) && boxIntact()) {
                    boolean healed = healingController.tick(true, target, false);
                    boolean full = bot.getHealth()
                            >= ctx.tuning().number(TuningKey.BUBBLE_WEB_HEAL_TARGET_HP).floatValue();
                    if (!full && (healed || phaseTick <= 8) && phaseTick < HEAL_MAX_TICKS) {
                        phaseTick++;
                        return true;
                    } else {
                        healingController.abortHealing();
                        advance(Phase.PEARL);
                        return true;
                    }
                } else {
                    healingController.abortHealing();
                    advance(Phase.PEARL);
                    return true;
                }
            case PEARL:
                if (target != null && Ent.alive(target) && enderPearlController.tryThrow(target)) {
                    finish();
                    return false;
                } else {
                    if (target != null && Ent.alive(target) && ++phaseTick < PEARL_MAX_TICKS) {
                        return true;
                    }

                    finish();
                    return false;
                }
            default:
                return false;
        }
    }

    private boolean tickPlace(BlockPos pos, Phase next) {
        if (ctx.world().type(pos) == Material.COBWEB) {
            advance(next);
            return true;
        } else {
            int slot = equipCobweb();
            if (slot < 0) {
                finish();
                return false;
            } else {
                InventoryUtils.select(ctx, slot);
                lookAt(pos);
                if (phaseTick < PLACE_WINDUP) {
                    phaseTick++;
                    return true;
                } else {
                    placeWebAt(pos, slot);
                    if (ctx.world().type(pos) == Material.COBWEB) {
                        advance(next);
                    } else if (++placeFails >= PLACE_FAIL_CAP) {
                        finish();
                    }

                    return phase != Phase.IDLE;
                }
            }
        }
    }

    private void placeWebAt(BlockPos pos, int slot) {
        ItemStack web = bot.getInventory().getItem(slot);
        if (ItemKinds.is(web, Material.COBWEB) && ctx.world().isAir(pos)) {
            BlockPos clicked = pos.below();
            Vec3 hitVec = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            ctx.hands().useItemOn(EquipmentSlot.HAND, clicked, Direction.UP, hitVec);
            ctx.hands().swing(EquipmentSlot.HAND);
        }
    }

    private boolean boxIntact() {
        BlockPos feet = Ent.blockPos(bot);
        return ctx.world().type(feet) == Material.COBWEB && ctx.world().type(feet.above()) == Material.COBWEB;
    }

    private int equipCobweb() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.COBWEB)) {
                return InventoryUtils.moveToHotbarSafe(ctx, i);
            }
        }

        return -1;
    }

    private int countItem(Material item) {
        int n = 0;
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            if (ItemKinds.is(inv.getItem(i), item)) {
                n += inv.getItem(i).getAmount();
            }
        }

        return n;
    }

    private void lookAt(BlockPos pos) {
        Vec3 eye = Ent.eyePos(bot);
        double dx = pos.getX() + 0.5 - eye.x();
        double dy = pos.getY() + 0.5 - eye.y();
        double dz = pos.getZ() + 0.5 - eye.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        ctx.controls().look((float) Math.toDegrees(Math.atan2(-dx, dz)), (float) (-Math.toDegrees(Math.atan2(dy, horiz))));
    }

    private void advance(Phase next) {
        phase = next;
        phaseTick = 0;
        placeFails = 0;
    }

    private void finish() {
        if (prevSlot >= 0 && prevSlot < 9) {
            InventoryUtils.select(ctx, prevSlot);
        }

        phase = Phase.IDLE;
        phaseTick = 0;
        placeFails = 0;
        prevSlot = -1;
        cooldown = ctx.tuning().integer(TuningKey.BUBBLE_WEB_COOLDOWN);
    }

    public void reset() {
        if (bot.isHandRaised()) {
            ctx.hands().releaseUseItem();
        }

        phase = Phase.IDLE;
        phaseTick = 0;
        placeFails = 0;
        prevSlot = -1;
    }

    private enum Phase {
        IDLE,
        FEET,
        HEAD,
        HEAL,
        PEARL
    }
}
