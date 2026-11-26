package dev.nandi0813.zppbots.brain;

import dev.nandi0813.zppbots.ai.melee.WeaponSelector;
import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.DifficultyParams;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeleeCombatTest {

    private static final BotTuning.Profile MEDIUM = profile(PresetTuning.root(1));

    @Test
    void reactionDelayStaysInBounds() {
        DifficultyParams p = new DifficultyParams();
        p.reactionMinTicks = 2;
        p.reactionMaxTicks = 5;
        Random rng = new Random(1);
        for (int i = 0; i < 200; i++) {
            int d = MeleeCombat.rollReactionDelay(p, rng);
            assertTrue(d >= 2 && d <= 5, "delay " + d);
        }
    }

    @Test
    void comboFireAtAddsBoostAndPingAndCapsAtOne() {
        assertEquals(0.9F, MeleeCombat.comboFireAt(MEDIUM, 0.0F, false), 1.0E-6F);
        assertEquals(0.98F, MeleeCombat.comboFireAt(MEDIUM, 0.0F, true), 1.0E-6F);
        assertEquals(1.0F, MeleeCombat.comboFireAt(MEDIUM, 0.15F, true), 1.0E-6F);
    }

    @Test
    void fullChargeFiresComboEvenWithoutBoost() {
        float fullCharge = 1.0F;
        assertTrue(fullCharge >= MeleeCombat.comboFireAt(MEDIUM, 0.10F, false));
        assertTrue(fullCharge >= MeleeCombat.comboFireAt(MEDIUM, 0.15F, true));
    }

    @Test
    void swingRotationWithoutMissChanceIsStraight() {
        float[] r = MeleeCombat.pickSwingRotation(MEDIUM, 30.0F, 10.0F, 0.0F, new Random(3));
        assertEquals(30.0F, r[0]);
        assertEquals(10.0F, r[1]);
        assertEquals(0.0F, r[2]);
    }

    @Test
    void swingRotationMissStaysWithinOffsets() {
        for (int i = 0; i < 100; i++) {
            float[] r = MeleeCombat.pickSwingRotation(MEDIUM, 0.0F, 85.0F, 1.0F, new Random(i));
            assertEquals(1.0F, r[2]);
            assertTrue(Math.abs(r[0]) <= 8.0F + 1.0E-4F);
            assertTrue(r[1] <= 90.0F && r[1] >= 79.0F - 1.0E-4F);
        }
    }

    private static MeleeCombat.MoveInputs inputs(WeaponSelector.WeaponType weapon, float charge) {
        return new MeleeCombat.MoveInputs(
                weapon, charge, false, false, false, false, false, false, false, false, 0, 0, 0, true, true, false, false, 0);
    }

    private static MeleeCombat.Move move(MeleeCombat.MoveInputs in) {
        return MeleeCombat.decide(MEDIUM, in, () -> false).move();
    }

    private static MeleeCombat.MoveInputs with(MeleeCombat.MoveInputs i, java.util.function.UnaryOperator<Builder> f) {
        return f.apply(new Builder(i)).build();
    }

    /** Tiny mutable copy of MoveInputs for the tests. */
    private static final class Builder {
        WeaponSelector.WeaponType weapon;
        float charge;
        boolean targetBlocking, seesShield, shouldDisable, hasFlickAxe, targetInWeb, botInWeb, inCombo, jumped;
        int autoCrit, mirror, answer;
        boolean onGround, canCrit, beingCombod, finishable;
        int nearby;

        Builder(MeleeCombat.MoveInputs i) {
            weapon = i.weapon();
            charge = i.charge();
            targetBlocking = i.targetBlocking();
            seesShield = i.seesShield();
            shouldDisable = i.shouldDisable();
            hasFlickAxe = i.hasFlickAxe();
            targetInWeb = i.targetInWeb();
            botInWeb = i.botInWeb();
            inCombo = i.inCombo();
            jumped = i.jumpedForCrit();
            autoCrit = i.autoCritWindow();
            mirror = i.mirrorPunishWindow();
            answer = i.answerSwingWindow();
            onGround = i.onGround();
            canCrit = i.canCrit();
            beingCombod = i.beingCombod();
            finishable = i.targetFinishable();
            nearby = i.nearbyEnemies();
        }

        MeleeCombat.MoveInputs build() {
            return new MeleeCombat.MoveInputs(weapon, charge, targetBlocking, seesShield, shouldDisable, hasFlickAxe,
                    targetInWeb, botInWeb, inCombo, jumped, autoCrit, mirror, answer, onGround, canCrit, beingCombod,
                    finishable, nearby);
        }
    }

    @Test
    void decideMoveDefaultsToCritJumpThenFallback() {
        MeleeCombat.MoveInputs sword = inputs(WeaponSelector.WeaponType.SWORD, 1.0F);
        MeleeCombat.Decision d = MeleeCombat.decide(MEDIUM, sword, () -> false);
        assertEquals(MeleeCombat.Move.CRIT_JUMP, d.move());
        assertTrue(d.tryStartCombo());
        assertEquals(MeleeCombat.Move.FALLBACK, move(with(sword, b -> {
            b.canCrit = false;
            return b;
        })));
    }

    @Test
    void decideMoveShieldReactions() {
        MeleeCombat.MoveInputs sword = with(inputs(WeaponSelector.WeaponType.SWORD, 1.0F), b -> {
            b.targetBlocking = true;
            b.seesShield = true;
            b.shouldDisable = true;
            b.hasFlickAxe = true;
            return b;
        });
        assertEquals(MeleeCombat.Move.AXE_FLICK, move(sword));
        assertEquals(MeleeCombat.Move.CRIT_JUMP, move(with(sword, b -> {
            b.hasFlickAxe = false;
            return b;
        })));
        MeleeCombat.MoveInputs axe = with(sword, b -> {
            b.weapon = WeaponSelector.WeaponType.AXE;
            return b;
        });
        assertEquals(MeleeCombat.Move.AXE_DISABLE, move(axe));
        assertEquals(MeleeCombat.Move.HOLD, move(with(axe, b -> {
            b.shouldDisable = false;
            return b;
        })));
    }

    @Test
    void decideMoveComboAndPhaseTwo() {
        MeleeCombat.MoveInputs sword = inputs(WeaponSelector.WeaponType.SWORD, 0.5F);
        assertEquals(MeleeCombat.Move.COMBO, move(with(sword, b -> {
            b.inCombo = true;
            return b;
        })));
        assertEquals(MeleeCombat.Move.CRIT_PHASE2, move(with(sword, b -> {
            b.jumped = true;
            return b;
        })));
        assertEquals(MeleeCombat.Move.AUTO_CRIT, move(with(sword, b -> {
            b.autoCrit = 3;
            b.charge = 1.0F;
            return b;
        })));
        assertEquals(MeleeCombat.Move.SWEEP, move(with(sword, b -> {
            b.canCrit = false;
            b.nearby = 2;
            return b;
        })));
    }

    @Test
    void decideMoveWebClearsComboAndFinishablePins() {
        MeleeCombat.MoveInputs web = with(inputs(WeaponSelector.WeaponType.SWORD, 0.5F), b -> {
            b.inCombo = true;
            b.targetInWeb = true;
            return b;
        });
        MeleeCombat.Decision d = MeleeCombat.decide(MEDIUM, web, () -> false);
        assertEquals(MeleeCombat.Move.CRIT_JUMP, d.move());
        assertTrue(d.clearCombo());
        MeleeCombat.Decision pin = MeleeCombat.decide(MEDIUM, with(inputs(WeaponSelector.WeaponType.SWORD, 1.0F), b -> {
            b.finishable = true;
            b.canCrit = false;
            return b;
        }), () -> false);
        assertEquals(MeleeCombat.Move.PIN_FLEEING, pin.move());
        assertTrue(pin.clearCombo());
    }

    @Test
    void uppercutRollOnlyAskedWhenBeingCombodAndCritPossible() {
        int[] rolls = {0};
        MeleeCombat.MoveInputs base = inputs(WeaponSelector.WeaponType.SWORD, 0.5F);
        MeleeCombat.decide(MEDIUM, base, () -> {
            rolls[0]++;
            return true;
        });
        assertEquals(0, rolls[0]);
        MeleeCombat.MoveInputs combod = with(base, b -> {
            b.beingCombod = true;
            return b;
        });
        MeleeCombat.Decision d = MeleeCombat.decide(MEDIUM, combod, () -> {
            rolls[0]++;
            return true;
        });
        assertEquals(1, rolls[0]);
        assertEquals(MeleeCombat.Move.CRIT_JUMP, d.move());
        assertTrue(d.clearCombo());
    }

    @Test
    void shouldStartComboGates() {
        assertTrue(MeleeCombat.shouldStartCombo(MEDIUM, true, false, 0, true, true, 2.0, 3.0, 20.0, false, 0.1));
        assertFalse(MeleeCombat.shouldStartCombo(MEDIUM, false, false, 0, true, false, 2.0, 3.0, 20.0, false, 0.1));
        assertFalse(MeleeCombat.shouldStartCombo(MEDIUM, true, false, 5, true, false, 2.0, 3.0, 20.0, false, 0.1));
        assertFalse(MeleeCombat.shouldStartCombo(MEDIUM, true, false, 0, false, true, 2.0, 3.0, 20.0, false, 0.1));
        assertFalse(MeleeCombat.shouldStartCombo(MEDIUM, true, false, 0, true, false, 4.0, 3.0, 20.0, false, 0.1));
        assertFalse(MeleeCombat.shouldStartCombo(MEDIUM, true, false, 0, true, false, 2.0, 3.0, 3.0, false, 0.1));
        assertFalse(MeleeCombat.shouldStartCombo(MEDIUM, true, false, 0, true, false, 2.0, 3.0, 20.0, true, 0.1));
        assertFalse(MeleeCombat.shouldStartCombo(MEDIUM, true, false, 0, true, false, 2.0, 3.0, 20.0, false, 0.5));
    }

    @Test
    void comboHoldsInsideSampledSwingReach() {
        assertEquals(2.7, MeleeCombat.comboHoldDistance(MEDIUM, 3.0), 1.0E-9);
        assertEquals(2.4, MeleeCombat.comboHoldDistance(MEDIUM, 2.5), 1.0E-9);
        assertTrue(MeleeCombat.comboHoldDistance(MEDIUM, 2.5) < 2.5);
    }

    @Test
    void wtapHoldsOnlyInsideSwingReach() {
        assertTrue(MeleeCombat.wtapHolds(2.4, 2.5));
        assertTrue(MeleeCombat.wtapHolds(2.5, 2.5));
        assertFalse(MeleeCombat.wtapHolds(2.8, 2.5));
    }

    @Test
    void canAttemptCritGates() {
        assertTrue(MeleeCombat.canAttemptCrit(MEDIUM, false, false, false, 0.65));
        assertFalse(MeleeCombat.canAttemptCrit(MEDIUM, true, false, false, 0.0));
        assertFalse(MeleeCombat.canAttemptCrit(MEDIUM, false, true, false, 0.0));
        assertFalse(MeleeCombat.canAttemptCrit(MEDIUM, false, false, true, 0.0));
        assertFalse(MeleeCombat.canAttemptCrit(MEDIUM, false, false, false, 0.66));
    }

    @Test
    void changedMeleeTuningReachesDecisionAndGates() {
        JSONObject fire = PresetTuning.root(2);
        set(fire, TuningKey.MELEE_COMBO_FIRE_BASE, 0.8);
        assertEquals(0.8F, MeleeCombat.comboFireAt(profile(fire), 0.0F, false), 1.0E-6F);

        JSONObject strong = PresetTuning.root(3);
        set(strong, TuningKey.MELEE_STRONG_CHARGE, 0.5);
        MeleeCombat.MoveInputs autoCrit = with(inputs(WeaponSelector.WeaponType.SWORD, 0.5F), b -> {
            b.autoCrit = 3;
            return b;
        });
        assertEquals(MeleeCombat.Move.CRIT_JUMP, move(autoCrit));
        assertEquals(MeleeCombat.Move.AUTO_CRIT, MeleeCombat.decide(profile(strong), autoCrit, () -> false).move());

        JSONObject start = PresetTuning.root(4);
        set(start, TuningKey.MELEE_COMBO_START_MIN_HP, 5);
        assertTrue(MeleeCombat.shouldStartCombo(MEDIUM, true, false, 0, true, true, 2.0, 3.0, 4.0, false, 0.1));
        assertFalse(MeleeCombat.shouldStartCombo(profile(start), true, false, 0, true, true, 2.0, 3.0, 4.0, false, 0.1));
    }

    @Test
    void outOfRangeMeleeTuningIsRejected() {
        JSONObject flee = PresetTuning.root(5);
        set(flee, TuningKey.MELEE_FLEE_DOT, 1.5);
        assertThrows(IllegalArgumentException.class, () -> profile(flee));

        JSONObject span = PresetTuning.root(6);
        set(span, TuningKey.MELEE_FEINT_TICKS_SPAN, 0);
        assertThrows(IllegalArgumentException.class, () -> profile(span));
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
