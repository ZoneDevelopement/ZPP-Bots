package dev.nandi0813.zppbots.ai.mace;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Predicts when an airborne enemy carrying a mace or spear lands on the bot. Per-pass shared
 * off-ground cache (beginAiPass/endAiPass/afterTick) is not ported: it only skipped grounded players, which
 * {@link #visit} already rejects.
 */
public final class MaceDiveForecast {

    private static final String INCOMING_MACE_DIVE = "INCOMING_MACE_DIVE";
    private static final double SCAN_RANGE = 48.0;
    private static final double MIN_HEIGHT = 1.5;
    private static final double REACH = 3.3;
    private static final double SMASH_FALL = 1.5;
    private static final int HORIZON = 40;

    private final BotContext ctx;
    private final Player bot;
    private final Map<UUID, Vec3> lastPos = new HashMap<>();
    private final Map<UUID, Vec3> vel = new HashMap<>();
    private final Map<UUID, Dive> dives = new HashMap<>();
    private long lastTick = Long.MIN_VALUE;
    private Dive soonest = null;
    private UUID reported = null;

    public MaceDiveForecast(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public Dive soonest() {
        return soonest;
    }

    public Dive of(Entity diver) {
        return diver == null ? null : dives.get(diver.getUniqueId());
    }

    public void update() {
        long now = ctx.world().gameTime();
        if (now != lastTick) {
            long gap = lastTick == Long.MIN_VALUE ? 0L : now - lastTick;
            lastTick = now;
            dives.clear();
            soonest = null;
            Vec3 me = Ent.pos(bot);
            double r2 = SCAN_RANGE * SCAN_RANGE;
            boolean tracking = !lastPos.isEmpty();
            List<Player> players = ctx.world().players();
            for (Player p : players) {
                visit(p, me, r2, tracking, gap);
            }

            if (soonest != null && !soonest.diver().getUniqueId().equals(reported)) {
                reported = soonest.diver().getUniqueId();
                ctx.log().decision("MaceDiveForecast", INCOMING_MACE_DIVE, "sees " + soonest.diver().getName() + " diving on it: strike in " + soonest.ticks()
                        + " ticks, ~" + String.format("%.1f", soonest.damage()) + " damage");
            } else if (soonest == null) {
                reported = null;
            }

            if (lastPos.size() > 128) {
                lastPos.clear();
                vel.clear();
            }
        }
    }

    private void visit(Player p, Vec3 me, double r2, boolean tracking, long gap) {
        if (p != bot) {
            boolean candidate = !p.isOnGround() && p.getLocation().getY() - me.y() >= MIN_HEIGHT && Ent.distSqr(p, bot) <= r2;
            if (candidate || tracking) {
                if (Ent.alive(p) && p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE) {
                    UUID id = p.getUniqueId();
                    if (!candidate) {
                        if (lastPos.remove(id) != null) {
                            vel.remove(id);
                        }
                    } else {
                        Vec3 pos = Ent.pos(p);
                        Vec3 prev = lastPos.put(id, pos);
                        Vec3 v = vel.getOrDefault(id, Vec3.ZERO);
                        if (prev != null) {
                            if (gap > 0L) {
                                Vec3 step = pos.subtract(prev).scale(1.0 / gap);
                                if (step.lengthSqr() > 36.0) {
                                    step = Vec3.ZERO;
                                }

                                v = v.scale(0.35).add(step.scale(0.65));
                                vel.put(id, v);
                            }

                            boolean mace = carries(p, true);
                            if (mace || carries(p, false)) {
                                if (!ctx.match().isAlly(p)) {
                                    Dive d = simulate(p, pos, v, mace);
                                    if (d != null) {
                                        dives.put(id, d);
                                        if (soonest == null || d.ticks() < soonest.ticks()) {
                                            soonest = d;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private Dive simulate(Player p, Vec3 pos, Vec3 v, boolean mace) {
        Vec3 me = Ent.pos(bot);
        Strike s = strike(pos, v, Math.max(0.0, p.getFallDistance()), p.getEyeHeight(), p.isGliding(), mace,
                new Vec3(me.x(), me.y() + bot.getHeight() * 0.5, me.z()), me.y());
        if (s == null) {
            return null;
        }
        return new Dive(p, s.ticks(), s.fall(), estimateDamage(s.fall(), mace), carriesAxe(p));
    }

    /** Steps the diver forward until it can reach the target; null when it hits the ground first or never arrives. */
    static Strike strike(Vec3 pos, Vec3 v, double fall0, double eye, boolean gliding, boolean mace, Vec3 target, double botY) {
        double steerPerTick2 = gliding ? 0.05 : 0.015;
        double x = pos.x();
        double y = pos.y();
        double z = pos.z();
        double vx = v.x();
        double vy = v.y();
        double vz = v.z();
        double fall = fall0;

        for (int t = 0; t <= HORIZON; t++) {
            if (t > 0) {
                if (!gliding) {
                    vy = (vy - 0.08) * 0.98;
                }

                x += vx;
                y += vy;
                z += vz;
                if (vy < 0.0) {
                    fall += -vy;
                }
            }

            if (y < botY - 1.0) {
                return null;
            }

            double dx = target.x() - x;
            double dz = target.z() - z;
            double horiz = Math.max(0.0, Math.sqrt(dx * dx + dz * dz) - steerPerTick2 * t * t);
            double dy = target.y() - (y + eye);
            boolean reach = horiz * horiz + dy * dy <= REACH * REACH;
            if (reach && (!mace || fall >= SMASH_FALL) && vy < 0.05) {
                return new Strike(t, fall);
            }
        }

        return null;
    }

    private float estimateDamage(double fall, boolean mace) {
        PotionEffect res = bot.getPotionEffect(PotionEffectType.RESISTANCE);
        return estimateDamage(fall, mace, (int) Ent.attr(bot, Attribute.ARMOR), Ent.attr(bot, Attribute.ARMOR_TOUGHNESS),
                res == null ? -1 : res.getAmplifier());
    }

    /** resistanceAmplifier is -1 without the effect. */
    static float estimateDamage(double fall, boolean mace, double armor, double tough, int resistanceAmplifier) {
        double raw;
        if (mace) {
            double bonus = fall <= 3.0 ? 4.0 * fall : (fall <= 8.0 ? 12.0 + 2.0 * (fall - 3.0) : 22.0 + (fall - 8.0));
            raw = 9.0 + bonus;
        } else {
            raw = 10.0;
        }

        double f = Math.min(20.0, Math.max(armor * 0.2, armor - raw / (2.0 + tough / 4.0)));
        double dmg = raw * (1.0 - f / 25.0);
        if (resistanceAmplifier >= 0) {
            dmg *= Math.max(0.0, 1.0 - 0.2 * (resistanceAmplifier + 1));
        }

        return (float) dmg;
    }

    private static boolean carriesAxe(Player p) {
        for (int i = 0; i < 36; i++) {
            if (ItemKinds.isAxe(p.getInventory().getItem(i))) {
                return true;
            }
        }

        return ItemKinds.isAxe(p.getInventory().getItemInOffHand());
    }

    private static boolean carries(Player p, boolean mace) {
        for (int i = 0; i < 36; i++) {
            if (is(p.getInventory().getItem(i), mace)) {
                return true;
            }
        }

        return is(p.getInventory().getItemInOffHand(), mace) || is(p.getInventory().getItemInMainHand(), mace);
    }

    private static boolean is(ItemStack s, boolean mace) {
        return mace ? ItemKinds.is(s, Material.MACE) : StunSlamDummy.isMaceOrSpear(s) && !ItemKinds.is(s, Material.MACE);
    }

    public record Dive(Player diver, int ticks, double fall, float damage, boolean axe) {
    }

    record Strike(int ticks, double fall) {
    }
}
