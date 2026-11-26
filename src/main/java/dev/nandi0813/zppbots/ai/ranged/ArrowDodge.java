package dev.nandi0813.zppbots.ai.ranged;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Sidesteps a target that is drawing a bow at the bot, so the arrow misses. */
public final class ArrowDodge {

    private static final String ARROW_DODGE = "ARROW_DODGE";

    private final BotContext ctx;
    private final Player bot;
    private int drawTicks = 0;
    private int direction = 0;
    private int flipTicksLeft = 0;

    public ArrowDodge(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    /** Strafe input for this tick: 1 or -1 while dodging, 0 otherwise. */
    public float strafe(LivingEntity target, double dist) {
        boolean drawing = target.isHandRaised() && ItemKinds.is(target.getActiveItem(), Material.BOW);
        drawTicks = drawing ? drawTicks + 1 : 0;
        boolean dodge = ctx.settings().difficulty().level() >= ctx.tuning().integer(TuningKey.ARROW_DODGE_MIN_LEVEL)
                && shouldDodge(drawTicks, dist, ctx.tuning().integer(TuningKey.ARROW_DODGE_MIN_DRAW_TICKS), ctx.tuning().number(TuningKey.ARROW_DODGE_MIN_DIST).doubleValue())
                && target.hasLineOfSight(bot);
        if (!dodge) {
            direction = 0;
            return 0.0F;
        }

        if (direction == 0) {
            direction = ctx.random().nextBoolean() ? 1 : -1;
            flipTicksLeft = nextFlipTicks();
            ctx.log().decision("ArrowDodge", ARROW_DODGE, "[Bow] sidestepping the arrows of " + target.getName());
        } else if (--flipTicksLeft <= 0) {
            direction = -direction;
            flipTicksLeft = nextFlipTicks();
        }

        return direction;
    }

    static boolean shouldDodge(int drawTicks, double dist, int minDraw, double minDist) {
        return drawTicks >= minDraw && dist >= minDist;
    }

    private int nextFlipTicks() {
        return ctx.tuning().integer(TuningKey.ARROW_DODGE_FLIP_MIN)
                + ctx.random().nextInt(ctx.tuning().integer(TuningKey.ARROW_DODGE_FLIP_SPAN));
    }
}
