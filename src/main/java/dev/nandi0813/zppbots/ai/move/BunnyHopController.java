package dev.nandi0813.zppbots.ai.move;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import java.util.Random;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class BunnyHopController {

    private final BotContext ctx;
    private final Player bot;
    private boolean wasAirborne = false;
    private int groundTicks = 0;
    private int nextHopDelay = 0;
    private boolean skipThisHop = false;

    public BunnyHopController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    private void startHopCycle() {
        groundTicks = 0;
        nextHopDelay = hopDelay(ctx.tuning(), ctx.random());
        skipThisHop = skipHop(ctx.tuning(), ctx.random());
    }

    public static boolean runsInsteadOfHopping(BotContext ctx) {
        if (!ctx.settings().speedNoJump()) {
            return false;
        } else {
            PotionEffect speed = ctx.player().getPotionEffect(PotionEffectType.SPEED);
            return speed != null
                    && speed.getAmplifier() >= ctx.tuning().integer(TuningKey.BUNNY_RUN_SPEED_AMPLIFIER);
        }
    }

    static int hopDelay(BotTuning.Profile profile, Random random) {
        return random.nextInt(profile.integer(TuningKey.BUNNY_MAX_DELAY) + 1);
    }

    static boolean skipHop(BotTuning.Profile profile, Random random) {
        return random.nextDouble() < profile.number(TuningKey.BUNNY_SKIP_CHANCE).doubleValue();
    }

    public void tick(boolean smartAllowed) {
        if (!bot.isHandRaised() || ItemKinds.isSpear(bot.getInventory().getItemInMainHand())) {
            boolean shouldHop = switch (ctx.settings().bunnyHopMode()) {
                case OFF -> false;
                case ON -> true;
                case SMART -> smartAllowed;
            };
            if (runsInsteadOfHopping(ctx)) {
                shouldHop = false;
            }

            if (!bot.isOnGround()) {
                wasAirborne = true;
            } else {
                if (wasAirborne) {
                    wasAirborne = false;
                    startHopCycle();
                }

                groundTicks++;
                if (groundTicks >= nextHopDelay) {
                    if (shouldHop && bot.isSprinting() && !skipThisHop) {
                        ctx.controls().jumpOnce();
                    } else {
                        startHopCycle();
                    }
                }
            }
        }
    }
}
