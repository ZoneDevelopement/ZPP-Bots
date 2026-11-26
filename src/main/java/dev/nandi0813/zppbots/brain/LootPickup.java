package dev.nandi0813.zppbots.brain;

import dev.nandi0813.zppbots.mc.AABB;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Predicate;

/** Walks to a dead enemy's drops and to dropped shulker boxes and ender chests. */
final class LootPickup {

    final BotBrain brain;
    private final BotContext ctx;
    private final Player bot;

    LootPickup(BotBrain brain) {
        this.brain = brain;
        this.ctx = brain.ctx;
        this.bot = brain.bot;
    }

    boolean tickKillLootPickup() {
        if (brain.killLootPos == null) {
            brain.killLootTicks = 0;
            return false;
        } else if (!ctx.settings().pickupLoot()) {
            return false;
        } else if (brain.targeting.enemyWithin(ctx.tuning().number(TuningKey.LOOT_ENEMY_CLEAR_RANGE).doubleValue())) {
            return false;
        } else {
            Item drop = findDropNear(brain.killLootPos, ctx.tuning().number(TuningKey.LOOT_KILL_RADIUS).doubleValue());
            if (drop == null) {
                brain.killLootTicks = 0;
                brain.killLootPos = null;
                return false;
            } else {
                Vec3 dpos = Ent.pos(drop);
                if (Ent.pos(bot).distanceTo(dpos) > ctx.tuning().number(TuningKey.LOOT_ARRIVE_DIST).doubleValue()) {
                    brain.pathFollower.chase(dpos, dpos);
                    ctx.controls().setSprinting(true);
                    return true;
                } else {
                    brain.pathFollower.reset();
                    brain.armorManager.equipFromInventory();
                    return true;
                }
            }
        }
    }

    boolean tickContainerPickup() {
        if (!ctx.settings().pickupLoot()) {
            return false;
        } else {
            Item box = findNearbyContainerDrop();
            if (box == null) {
                return false;
            } else if (brain.targeting.enemyWithin(ctx.tuning().number(TuningKey.LOOT_ENEMY_CLEAR_RANGE).doubleValue())) {
                return false;
            } else {
                if (!box.getUniqueId().equals(brain.containerPickupId)) {
                    brain.containerPickupId = box.getUniqueId();
                    brain.containerPickupTicks = 0;
                }

                if (++brain.containerPickupTicks > ctx.tuning().integer(TuningKey.LOOT_CONTAINER_TIMEOUT_TICKS)) {
                    // skipDrop: stop chasing a drop the bot cannot reach
                    if (brain.ownJunkDrops.size() > 64) {
                        brain.ownJunkDrops.clear();
                    }

                    brain.ownJunkDrops.add(box.getUniqueId());
                    brain.containerPickupId = null;
                    brain.containerPickupTicks = 0;
                    brain.pathFollower.reset();
                    return false;
                } else {
                    Vec3 dpos = Ent.pos(box);
                    if (Ent.pos(bot).distanceTo(dpos) > ctx.tuning().number(TuningKey.LOOT_ARRIVE_DIST).doubleValue()) {
                        brain.pathFollower.chase(dpos, dpos);
                        ctx.controls().setSprinting(true);
                        return true;
                    } else {
                        brain.pathFollower.reset();
                        return true;
                    }
                }
            }
        }
    }

    Item findNearbyContainerDrop() {
        return closest(Ent.box(bot).inflate(ctx.tuning().number(TuningKey.LOOT_CONTAINER_RADIUS).doubleValue()), it -> isRegearContainer(it.getItemStack()));
    }

    static boolean isRegearContainer(ItemStack st) {
        if (ItemKinds.isEmpty(st)) {
            return false;
        } else {
            return st.getType() == Material.ENDER_CHEST || Tag.SHULKER_BOXES.isTagged(st.getType());
        }
    }

    Item findDropNear(Vec3 center, double r) {
        return closest(new AABB(center.subtract(r, r, r), center.add(r, r, r)), it -> true);
    }

    Item findNearbyDrop() {
        return closest(Ent.box(bot).inflate(ctx.tuning().number(TuningKey.LOOT_DROP_RADIUS).doubleValue()), it -> true);
    }

    private Item closest(AABB box, Predicate<Item> extra) {
        List<Item> items = ctx.world().entities(Item.class, box,
                it -> Ent.alive(it) && it.getPickupDelay() <= 0 && !brain.ownJunkDrops.contains(it.getUniqueId()) && extra.test(it));
        Item best = null;
        double bestD = Double.MAX_VALUE;

        for (Item it : items) {
            double d = Ent.distSqr(bot, it);
            if (d < bestD) {
                bestD = d;
                best = it;
            }
        }

        return best;
    }
}
