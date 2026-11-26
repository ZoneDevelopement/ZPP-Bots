package dev.nandi0813.zppbots.path;

import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.bukkit.Material;

public class PathSettings {
    private final Set<Material> avoidedBlocks = new LinkedHashSet<>();
    private PathSettings.MoveType moveType;
    private double maxHorizontalDistance;
    private double maxVerticalDistance;
    private double nodeHorizontalDistance;
    private double nodeVerticalDistance;
    private boolean stopFollowing;
    private double horizontalMoveCost;
    private double verticalMoveCost;
    private double swimBase;
    private double swimEfficiencyFactor;
    private double swimGraceFactor;
    private double swimMinimum;
    private double swimCostMultiplier;
    private boolean debug = false;

    public PathSettings(BotTuning.Profile profile) {
        Objects.requireNonNull(profile, "profile");
        this.moveType = MoveType.values()[profile.integer(TuningKey.PATH_MOVE_TYPE)];
        this.maxHorizontalDistance = profile.number(TuningKey.PATH_MAX_HORIZONTAL_DISTANCE).doubleValue();
        this.maxVerticalDistance = profile.number(TuningKey.PATH_MAX_VERTICAL_DISTANCE).doubleValue();
        this.nodeHorizontalDistance = profile.number(TuningKey.PATH_NODE_HORIZONTAL_DISTANCE).doubleValue();
        this.nodeVerticalDistance = profile.number(TuningKey.PATH_NODE_VERTICAL_DISTANCE).doubleValue();
        this.stopFollowing = profile.flag(TuningKey.PATH_STOP_FOLLOWING);
        this.horizontalMoveCost = profile.number(TuningKey.PATH_HORIZONTAL_MOVE_COST).doubleValue();
        this.verticalMoveCost = profile.number(TuningKey.PATH_VERTICAL_MOVE_COST).doubleValue();
        this.swimBase = profile.number(TuningKey.PATH_SWIM_BASE).doubleValue();
        this.swimEfficiencyFactor = profile.number(TuningKey.PATH_SWIM_EFFICIENCY_FACTOR).doubleValue();
        this.swimGraceFactor = profile.number(TuningKey.PATH_SWIM_GRACE_FACTOR).doubleValue();
        this.swimMinimum = profile.number(TuningKey.PATH_SWIM_MINIMUM).doubleValue();
        this.swimCostMultiplier = this.swimBase;

        addAvoided(profile, TuningKey.PATH_AVOID_LAVA, Material.LAVA);
        addAvoided(profile, TuningKey.PATH_AVOID_MAGMA, Material.MAGMA_BLOCK);
        addAvoided(profile, TuningKey.PATH_AVOID_FIRE, Material.FIRE);
        addAvoided(profile, TuningKey.PATH_AVOID_SOUL_FIRE, Material.SOUL_FIRE);
        addAvoided(profile, TuningKey.PATH_AVOID_CACTUS, Material.CACTUS);
        addAvoided(profile, TuningKey.PATH_AVOID_BERRY, Material.SWEET_BERRY_BUSH);
        addAvoided(profile, TuningKey.PATH_AVOID_WITHER_ROSE, Material.WITHER_ROSE);
        addAvoided(profile, TuningKey.PATH_AVOID_COBWEB, Material.COBWEB);
        addAvoided(profile, TuningKey.PATH_AVOID_SCULK, Material.SCULK_SHRIEKER);
        addAvoided(profile, TuningKey.PATH_AVOID_RAIL, Material.RAIL);
        addAvoided(profile, TuningKey.PATH_AVOID_POWERED_RAIL, Material.POWERED_RAIL);
        addAvoided(profile, TuningKey.PATH_AVOID_DETECTOR_RAIL, Material.DETECTOR_RAIL);
        addAvoided(profile, TuningKey.PATH_AVOID_ACTIVATOR_RAIL, Material.ACTIVATOR_RAIL);
    }

    private void addAvoided(BotTuning.Profile profile, TuningKey key, Material material) {
        if (profile.flag(key)) this.avoidedBlocks.add(material);
    }

    public void copyFrom(PathSettings other) {
        this.avoidedBlocks.clear();
        this.avoidedBlocks.addAll(other.avoidedBlocks);
        this.moveType = other.moveType;
        this.maxHorizontalDistance = other.maxHorizontalDistance;
        this.maxVerticalDistance = other.maxVerticalDistance;
        this.nodeHorizontalDistance = other.nodeHorizontalDistance;
        this.nodeVerticalDistance = other.nodeVerticalDistance;
        this.stopFollowing = other.stopFollowing;
        this.horizontalMoveCost = other.horizontalMoveCost;
        this.verticalMoveCost = other.verticalMoveCost;
        this.swimBase = other.swimBase;
        this.swimEfficiencyFactor = other.swimEfficiencyFactor;
        this.swimGraceFactor = other.swimGraceFactor;
        this.swimMinimum = other.swimMinimum;
        this.swimCostMultiplier = other.swimCostMultiplier;
        this.debug = other.debug;
    }

    public boolean isNotAvoided(Material block) {
        return !this.avoidedBlocks.contains(block);
    }

    public Set<Material> getAvoidedBlocks() {
        return Collections.unmodifiableSet(this.avoidedBlocks);
    }

    public void addAvoidedBlock(Material block) {
        this.avoidedBlocks.add(block);
    }

    public boolean removeAvoidedBlock(Material block) {
        return this.avoidedBlocks.remove(block);
    }

    public void clearAvoidedBlocks() {
        this.avoidedBlocks.clear();
    }

    public PathSettings.MoveType getMoveType() {
        return this.moveType;
    }

    public void setMoveType(PathSettings.MoveType moveType) {
        this.moveType = moveType;
    }

    public double getMaxHorizontalDistance() {
        return this.maxHorizontalDistance;
    }

    public void setMaxHorizontalDistance(double value) {
        if (!(value <= 0.0)) {
            this.maxHorizontalDistance = value;
        }
    }

    public double getMaxVerticalDistance() {
        return this.maxVerticalDistance;
    }

    public void setMaxVerticalDistance(double value) {
        this.maxVerticalDistance = value;
    }

    public double getNodeHorizontalDistance() {
        return this.nodeHorizontalDistance;
    }

    public void setNodeHorizontalDistance(double value) {
        if (!(value <= 0.0)) {
            this.nodeHorizontalDistance = value;
        }
    }

    public double getNodeVerticalDistance() {
        return this.nodeVerticalDistance;
    }

    public void setNodeVerticalDistance(double value) {
        this.nodeVerticalDistance = value;
    }

    public boolean isStopFollowing() {
        return this.stopFollowing;
    }

    public void setStopFollowing(boolean value) {
        this.stopFollowing = value;
    }

    public double getHorizontalMoveCost() {
        return this.horizontalMoveCost;
    }

    public void setHorizontalMoveCost(double value) {
        if (!(value <= 0.0)) {
            this.horizontalMoveCost = value;
        }
    }

    public double getVerticalMoveCost() {
        return this.verticalMoveCost;
    }

    public void setVerticalMoveCost(double value) {
        if (!(value <= 0.0)) {
            this.verticalMoveCost = value;
        }
    }

    public double getSwimCostMultiplier() {
        return this.swimCostMultiplier;
    }

    public void setSwimCostMultiplier(double value) {
        if (!(value <= 0.0)) {
            this.swimCostMultiplier = value;
        }
    }

    public void calculateSwimCost(double waterMovementEfficiency, boolean hasDolphinsGrace) {
        double base = this.swimBase;
        base *= 1.0 - waterMovementEfficiency * this.swimEfficiencyFactor;
        if (hasDolphinsGrace) {
            base *= this.swimGraceFactor;
        }

        this.swimCostMultiplier = Math.max(this.swimMinimum, base);
    }

    public boolean isDebug() {
        return this.debug;
    }

    public void setDebug(boolean value) {
        this.debug = value;
    }

    public boolean isWithinTarget(double hDist, double vDist) {
        boolean hOk = hDist <= this.maxHorizontalDistance;
        boolean vOk = this.maxVerticalDistance < 0.0 || vDist <= this.maxVerticalDistance;
        return hOk && vOk;
    }

    public boolean isWithinNode(double hDist, double vDist) {
        boolean hOk = hDist <= this.nodeHorizontalDistance;
        boolean vOk = this.nodeVerticalDistance < 0.0 || vDist <= this.nodeVerticalDistance;
        return hOk && vOk;
    }

    public static enum MoveType {
        WALK,
        SPRINT,
        SPRINT_JUMP;

        public String displayName() {
            return switch (this) {
                case WALK -> "walk";
                case SPRINT -> "sprint";
                case SPRINT_JUMP -> "sprint-jump";
            };
        }
    }
}
