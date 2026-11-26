package dev.nandi0813.zppbots.mc;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;

/** Bukkit entity helpers in Mojang terms (position, eye, velocity, box, attributes). */
public final class Ent {

    private Ent() {
    }

    public static Vec3 pos(Entity entity) {
        return Vec3.of(entity.getLocation());
    }

    public static Vec3 eyePos(LivingEntity entity) {
        return Vec3.of(entity.getEyeLocation());
    }

    public static Vec3 velocity(Entity entity) {
        return Vec3.of(entity.getVelocity());
    }

    public static BlockPos blockPos(Entity entity) {
        return BlockPos.of(entity.getLocation());
    }

    public static AABB box(Entity entity) {
        return AABB.of(entity.getBoundingBox());
    }

    public static Vec3 look(LivingEntity entity) {
        return Vec3.of(entity.getEyeLocation().getDirection());
    }

    public static boolean alive(Entity entity) {
        return entity.isValid() && !entity.isDead();
    }

    public static double dist(Entity a, Entity b) {
        return Math.sqrt(distSqr(a, b));
    }

    public static double distSqr(Entity a, Entity b) {
        if (!a.getWorld().equals(b.getWorld())) return Double.MAX_VALUE;
        return pos(a).distanceToSqr(pos(b));
    }

    public static double maxHealth(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(Attribute.MAX_HEALTH);
        return instance == null ? 20.0 : instance.getValue();
    }

    public static double attr(LivingEntity entity, Attribute attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? 0.0 : instance.getValue();
    }

    public static float attackDelay(HumanEntity player) {
        double speed = attr(player, Attribute.ATTACK_SPEED);
        if (speed <= 0.0) return 20.0f;
        return (float) (1.0 / speed * 20.0);
    }

    public static float attackStrength(HumanEntity player, float adjustTicks) {
        return strengthFrom(player.getAttackCooldown(), adjustTicks, attackDelay(player));
    }

    /** Full charge is exactly 1.0; the formula would otherwise undershoot it by the half-tick adjust. */
    static float strengthFrom(float cooldown, float adjustTicks, float attackDelay) {
        if (cooldown >= 1.0f) return 1.0f;
        return Mth.clamp(cooldown + (adjustTicks - 0.5f) / attackDelay, 0.0f, 1.0f);
    }
}
