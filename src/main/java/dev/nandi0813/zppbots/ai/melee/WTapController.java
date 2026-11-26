package dev.nandi0813.zppbots.ai.melee;

import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;

public final class WTapController {

    private final BotTuning.Profile profile;
    private boolean sprintStopped = false;

    public WTapController(BotTuning.Profile profile) {
        this.profile = profile;
    }

    public boolean shouldStopSprint(float chargeProgress) {
        if (chargeProgress < profile.number(TuningKey.WTAP_RESET_CHARGE).floatValue()) {
            sprintStopped = false;
        }
        if (!sprintStopped && chargeProgress >= profile.number(TuningKey.WTAP_STOP_CHARGE).floatValue()) {
            sprintStopped = true;
        }
        return sprintStopped;
    }

    public void onAttack() {
        sprintStopped = false;
    }

    public static boolean canAttack(BotTuning.Profile profile, float chargeProgress) {
        return chargeProgress >= profile.number(TuningKey.WTAP_ATTACK_CHARGE).floatValue();
    }
}
