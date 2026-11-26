package dev.nandi0813.zppbots.ai.ranged;

import dev.nandi0813.zppbots.brain.BlockHit;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.util.ArenaGuard;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PearlAim {

    private static final double SPEED = 1.5;
    private static final double GRAVITY = 0.03;
    private static final double DRAG = 0.99;
    private static final int MAX_TICKS = 120;
    private static final double LAND_SLACK = 1.6;

    private PearlAim() {
    }

    public static Shot solve(BotContext ctx, Vec3 aim) {
        Player bot = ctx.player();
        Vec3 botPos = Ent.pos(bot);
        Vec3 start = new Vec3(botPos.x(), Ent.eyePos(bot).y() - 0.1, botPos.z());
        Vec3 known = ctx.match().velocityOf(bot);
        Vec3 carry = new Vec3(known.x(), bot.isOnGround() ? 0.0 : known.y(), known.z());
        double dx = aim.x() - start.x();
        double dz = aim.z() - start.z();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.3) {
            return null;
        }
        double ux = dx / dist;
        double uz = dz / dist;
        double along = carry.x() * ux + carry.z() * uz;
        double sideX = carry.x() - along * ux;
        double sideZ = carry.z() - along * uz;
        double side = Math.sqrt(sideX * sideX + sideZ * sideZ);
        double prevPitch = Double.NaN;
        double prevMiss = Double.NaN;
        List<Double> roots = new ArrayList<>();

        for (double p = -89.0; p <= 89.0; p++) {
            double miss = missAt(p, dist, aim.y() - start.y(), side, along, carry.y());
            if (!Double.isNaN(miss) && !Double.isNaN(prevMiss) && Math.signum(miss) != Math.signum(prevMiss)) {
                roots.add(refine(prevPitch, p, dist, aim.y() - start.y(), side, along, carry.y()));
            }
            prevPitch = p;
            prevMiss = miss;
        }

        roots.sort(Comparator.reverseOrder());

        for (double pitch : roots) {
            double cosP = Math.cos(Math.toRadians(pitch));
            double h = SPEED * cosP;
            double a = h * h - side * side;
            if (a < 0.0) {
                continue;
            }
            double vx = ux * Math.sqrt(a) - sideX;
            double vz = uz * Math.sqrt(a) - sideZ;
            float yaw = (float) Math.toDegrees(Math.atan2(-vx, vz));
            Vec3 v0 = new Vec3(-Math.sin(Math.toRadians(yaw)) * cosP, -Math.sin(Math.toRadians(pitch)), Math.cos(Math.toRadians(yaw)) * cosP)
                    .scale(SPEED)
                    .add(carry);
            Shot shot = replay(ctx, start, v0, aim, dist, yaw, (float) pitch);
            if (shot != null) {
                return shot;
            }
        }
        return null;
    }

    public static Vec3 landing(BotContext ctx, float yaw, float pitch) {
        Player bot = ctx.player();
        Vec3 botPos = Ent.pos(bot);
        Vec3 start = new Vec3(botPos.x(), Ent.eyePos(bot).y() - 0.1, botPos.z());
        Vec3 known = ctx.match().velocityOf(bot);
        double cosP = Math.cos(Math.toRadians(pitch));
        Vec3 v = new Vec3(-Math.sin(Math.toRadians(yaw)) * cosP, -Math.sin(Math.toRadians(pitch)), Math.cos(Math.toRadians(yaw)) * cosP)
                .scale(SPEED)
                .add(known.x(), bot.isOnGround() ? 0.0 : known.y(), known.z());
        Vec3 pos = start;

        for (int t = 0; t < MAX_TICKS; t++) {
            v = new Vec3(v.x() * DRAG, (v.y() - GRAVITY) * DRAG, v.z() * DRAG);
            Vec3 next = pos.add(v);
            if (ArenaGuard.pearlOut(ctx, next, 0.0)) {
                return null;
            }
            BlockHit hit = ctx.world().clip(pos, next, false, false);
            if (hit.hit()) {
                return ArenaGuard.pearlOut(ctx, hit.location(), ArenaGuard.PEARL_INSET) ? null : hit.location();
            }
            if (next.y() < ctx.world().minY()) {
                return null;
            }
            pos = next;
        }
        return null;
    }

    static double missAt(double pitch, double dist, double dy, double side, double along, double carryY) {
        double cosP = Math.cos(Math.toRadians(pitch));
        double h = SPEED * cosP;
        double a = h * h - side * side;
        if (a < 0.0) {
            return Double.NaN;
        }
        double vh = Math.sqrt(a) + along;
        if (vh <= 1.0E-4) {
            return Double.NaN;
        }
        double vy = -SPEED * Math.sin(Math.toRadians(pitch)) + carryY;
        double x = 0.0;
        double y = 0.0;

        for (int t = 0; t < MAX_TICKS; t++) {
            vy = (vy - GRAVITY) * DRAG;
            vh *= DRAG;
            double nx = x + vh;
            double ny = y + vy;
            if (nx >= dist) {
                double f = (dist - x) / (nx - x);
                return y + (ny - y) * f - dy;
            }

            x = nx;
            y = ny;
            if (ny - dy < -64.0) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    static double refine(double lo, double hi, double dist, double dy, double side, double along, double carryY) {
        double mlo = missAt(lo, dist, dy, side, along, carryY);

        for (int i = 0; i < 24; i++) {
            double mid = (lo + hi) / 2.0;
            double m = missAt(mid, dist, dy, side, along, carryY);
            if (Double.isNaN(m)) {
                break;
            }

            if (Math.signum(m) == Math.signum(mlo)) {
                lo = mid;
                mlo = m;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2.0;
    }

    private static Shot replay(BotContext ctx, Vec3 start, Vec3 v0, Vec3 aim, double dist, float yaw, float pitch) {
        Vec3 pos = start;
        Vec3 v = v0;

        for (int t = 1; t <= MAX_TICKS; t++) {
            v = new Vec3(v.x() * DRAG, (v.y() - GRAVITY) * DRAG, v.z() * DRAG);
            Vec3 next = pos.add(v);
            if (ArenaGuard.pearlOut(ctx, next, 0.0)) {
                return null;
            }
            BlockHit hit = ctx.world().clip(pos, next, false, false);
            if (hit.hit()) {
                Vec3 at = hit.location();
                double hx = at.x() - aim.x();
                double hz = at.z() - aim.z();
                if (Math.sqrt(hx * hx + hz * hz) <= LAND_SLACK && Math.abs(at.y() - aim.y()) <= 2.5
                        && !ArenaGuard.pearlOut(ctx, at, ArenaGuard.PEARL_INSET)) {
                    return new Shot(yaw, pitch, t, at);
                }
                return null;
            }

            double sx = next.x() - start.x();
            double sz = next.z() - start.z();
            if (sx * sx + sz * sz >= dist * dist) {
                if (ArenaGuard.pearlOut(ctx, next, ArenaGuard.PEARL_INSET)) {
                    return null;
                }
                return new Shot(yaw, pitch, t, next);
            }
            pos = next;
        }
        return null;
    }

    public record Shot(float yaw, float pitch, int ticks, Vec3 landing) {
    }
}
