package dev.nandi0813.zppbots.ai.move;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Mth;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class JumpPlaceController {

    private final BotContext ctx;
    private final Player bot;
    private boolean active = false;
    private boolean placedThisJump = false;

    public JumpPlaceController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public void reset() {
        active = false;
        placedThisJump = false;
    }

    public void tick(LivingEntity target, double dist) {
        active = activeFor(ctx.tuning(), active, dist);

        if (active && !bot.isInWater() && !bot.isInLava() && !bot.isGliding() && !bot.isInsideVehicle()) {
            int slot = findBlock();
            if (slot >= 0) {
                if (bot.isOnGround()) {
                    placedThisJump = false;
                    ctx.controls().setSprinting(true);
                    ctx.controls().jumpOnce();
                } else if (!placedThisJump) {
                    Vec3 v = Ent.velocity(bot);
                    if (!(v.y() >= 0.0)) {
                        Vec3 pos = Ent.pos(bot);
                        double x = pos.x();
                        double y = pos.y();
                        double z = pos.z();
                        double vx = v.x();
                        double vy = v.y();
                        double vz = v.z();

                        for (int step = 0; step < ctx.tuning().integer(TuningKey.JUMP_PLACE_PREDICTION_STEPS); step++) {
                            double nx = x + vx;
                            double ny = y + vy;
                            double nz = z + vz;
                            BlockPos ground = groundBelow(nx, y, nz);
                            if (ground == null) {
                                return;
                            }

                            BlockPos place = ground.above();
                            double top = place.getY() + 1.0;
                            if (ny < top) {
                                if (y < top) {
                                    return;
                                }

                                if (ctx.world().canReplace(place) && !ctx.world().isWater(place) && !ctx.world().isLava(place)) {
                                    placeOn(ground, slot);
                                    return;
                                }

                                return;
                            }

                            x = nx;
                            y = ny;
                            z = nz;
                            vx *= 0.91;
                            vz *= 0.91;
                            vy = (vy - 0.08) * 0.98;
                        }
                    }
                }
            }
        }
    }

    private BlockPos groundBelow(double x, double fromY, double z) {
        BlockPos p = new BlockPos(Mth.floor(x), Mth.floor(fromY), Mth.floor(z));

        for (int i = 0; i <= ctx.tuning().integer(TuningKey.JUMP_PLACE_GROUND_SCAN); i++) {
            p = p.below();
            if (!ctx.world().collisionBoxes(p).isEmpty()) {
                return p;
            }
        }

        return null;
    }

    private void placeOn(BlockPos ground, int slot) {
        EquipmentSlot hand = slot == 40 ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        int prev = InventoryUtils.selected(ctx);
        if (hand == EquipmentSlot.HAND) {
            InventoryUtils.select(ctx, InventoryUtils.moveToHotbarSafe(ctx, slot));
        }

        Vec3 hit = Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0);
        Vec3 pos = Ent.pos(bot);
        double dx = hit.x() - pos.x();
        double dy = hit.y() - Ent.eyePos(bot).y();
        double dz = hit.z() - pos.z();
        ctx.controls().look(bot.getLocation().getYaw(), (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))));
        ctx.hands().useItemOn(hand, ground, Direction.UP, hit);
        ctx.hands().swing(hand);
        placedThisJump = true;
        if (hand == EquipmentSlot.HAND && prev >= 0 && prev < 9) {
            InventoryUtils.select(ctx, prev);
        }
    }

    private int findBlock() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            if (usable(inv.getItem(i))) {
                return i;
            }
        }

        return usable(inv.getItemInOffHand()) ? 40 : -1;
    }

    private boolean usable(ItemStack st) {
        return ScaffoldController.isPillarBlock(ctx, st)
                && allowedMaterial(ctx.tuning(), st.getType());
    }

    static boolean activeFor(BotTuning.Profile profile, boolean wasActive, double dist) {
        if (dist > profile.number(TuningKey.JUMP_PLACE_START_DIST).doubleValue()) {
            return true;
        }
        if (dist <= profile.number(TuningKey.JUMP_PLACE_STOP_DIST).doubleValue()) {
            return false;
        }
        return wasActive;
    }

    static boolean allowedMaterial(BotTuning.Profile profile, Material material) {
        return switch (material) {
            case OBSIDIAN -> !profile.flag(TuningKey.JUMP_PLACE_AVOID_OBSIDIAN);
            case CRYING_OBSIDIAN -> !profile.flag(TuningKey.JUMP_PLACE_AVOID_CRYING_OBSIDIAN);
            case RESPAWN_ANCHOR -> !profile.flag(TuningKey.JUMP_PLACE_AVOID_ANCHOR);
            case GLOWSTONE -> !profile.flag(TuningKey.JUMP_PLACE_AVOID_GLOWSTONE);
            default -> true;
        };
    }
}
