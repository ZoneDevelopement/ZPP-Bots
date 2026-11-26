package dev.nandi0813.zppbots.team;

import dev.nandi0813.zppbots.ai.move.FollowFormation;

public final class TeamTactics {
    public FollowFormation.Type formation = FollowFormation.Type.NONE;
    public int formationSize = 10;
    public double spacing = 2.0;
    public double movementSpeed = 1.0;
    public String boss = "";
    public boolean protectBoss = true;
    public boolean focusBossTarget = true;
    public boolean followBoss = true;
    public boolean maintainFormation = true;
    public boolean retreatWhenLow = false;
    public ShieldBehaviour shieldBehaviour = ShieldBehaviour.FREE;
    public RangedPositioning rangedPositioning = RangedPositioning.ANYWHERE;
    public TargetPriority targetPriority = TargetPriority.INDEPENDENT;
    public double regroupDistance = 15.0;
    public double maxDistanceFromBoss = 20.0;
    public String hornPlayer = "";
    public String hornType = "";
    public FollowFormation.Type hornFormation = FollowFormation.Type.LINE;

    public boolean hasBoss() {
        return boss != null && !boss.isBlank();
    }

    public boolean hornArmed() {
        return hornType != null && !hornType.isBlank();
    }

    public boolean active() {
        return formation != FollowFormation.Type.NONE || hasBoss() || hornArmed() || targetPriority != TargetPriority.INDEPENDENT;
    }

    public enum GroupAction {
        NONE,
        JUMP,
        SWING,
        CROUCH,
        WAVE
    }

    public enum RangedPositioning {
        ANYWHERE,
        BEHIND_FRONTLINE,
        INSIDE
    }

    public enum ShieldBehaviour {
        FREE,
        SHIELDS_UP,
        SHIELD_WALL
    }

    public enum TargetPriority {
        INDEPENDENT,
        BOSS_TARGET,
        WEAKEST,
        BOSS_ATTACKER
    }
}
