package dev.nandi0813.zppbots.ai.mace;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;

/** Holds a totem in the main hand when a forecast mace dive would kill the bot and the offhand has none. */
public final class MaceTotemGuard {

    private static final String MAIN_HAND_TOTEM = "MAIN_HAND_TOTEM";
    private final BotContext ctx;
    private final Player bot;
    private final MaceDiveForecast forecast;
    private boolean holding = false;
    private int restoreSlot = -1;
    private int holdTicks = 0;
    private int quietTicks = 0;

    public MaceTotemGuard(BotContext ctx, MaceDiveForecast forecast) {
        this.ctx = ctx;
        this.bot = ctx.player();
        this.forecast = forecast;
    }

    public boolean isHolding() {
        return holding;
    }

    public boolean tick(int tickCount) {
        MaceDiveForecast.Dive dive = forecast.soonest();
        boolean shieldCovers = bot.isBlocking() && dive != null && !dive.axe();
        boolean incoming = dive != null
                && dive.ticks() <= ctx.tuning().integer(TuningKey.MACE_TOTEM_RAISE_TICKS)
                && lethal(dive)
                && !shieldCovers;
        if (holding) {
            holdTicks++;
            quietTicks = incoming ? 0 : quietTicks + 1;
            if (quietTicks < ctx.tuning().integer(TuningKey.MACE_TOTEM_RELEASE_TICKS)
                    && holdTicks < ctx.tuning().integer(TuningKey.MACE_TOTEM_MAX_HOLD_TICKS)
                    && !hasOffhandTotem()
                    && ctx.settings().maceTotem()) {
                int slot = findTotemSlot();
                if (slot < 0) {
                    release();
                    return false;
                } else {
                    raise(slot);
                    return true;
                }
            } else {
                release();
                return false;
            }
        } else if (incoming && !hasOffhandTotem() && ctx.settings().maceTotem()) {
            int slot = findTotemSlot();
            if (slot < 0) {
                return false;
            } else {
                holding = true;
                holdTicks = 0;
                quietTicks = 0;
                restoreSlot = InventoryUtils.selected(ctx);
                raise(slot);
                ctx.log().decision("MaceTotemGuard", MAIN_HAND_TOTEM, "totem raised for incoming mace dive");
                return true;
            }
        } else {
            return false;
        }
    }

    public void reassert() {
        if (holding) {
            int slot = findTotemSlot();
            if (slot >= 0) {
                raise(slot);
            }
        }
    }

    public void reset() {
        if (holding) {
            release();
        }
    }

    private boolean lethal(MaceDiveForecast.Dive dive) {
        double hp = bot.getHealth() + bot.getAbsorptionAmount();
        return lethal(ctx.tuning(), dive.damage(), hp);
    }

    static boolean lethal(BotTuning.Profile profile, double damage, double hp) {
        return damage * profile.number(TuningKey.MACE_TOTEM_DAMAGE_MARGIN).doubleValue() >= hp;
    }

    private boolean hasOffhandTotem() {
        return ItemKinds.is(bot.getInventory().getItemInOffHand(), Material.TOTEM_OF_UNDYING);
    }

    private int findTotemSlot() {
        PlayerInventory inv = bot.getInventory();
        int held = InventoryUtils.selected(ctx);
        if (ItemKinds.is(inv.getItem(held), Material.TOTEM_OF_UNDYING)) {
            return held;
        } else {
            for (int i = 0; i < 36; i++) {
                if (ItemKinds.is(inv.getItem(i), Material.TOTEM_OF_UNDYING)) {
                    return i;
                }
            }

            return -1;
        }
    }

    private void raise(int slot) {
        int hotbar = slot < 9 ? slot : InventoryUtils.moveToHotbarSafe(ctx, slot);
        if (hotbar >= 0 && hotbar <= 8) {
            if (InventoryUtils.selected(ctx) != hotbar) {
                InventoryUtils.select(ctx, hotbar);
            }

            if (bot.isHandRaised() && bot.getActiveItemHand() == EquipmentSlot.HAND) {
                ctx.hands().releaseUseItem();
            }
        }
    }

    private void release() {
        holding = false;
        if (restoreSlot >= 0 && restoreSlot <= 8 && InventoryUtils.selected(ctx) != restoreSlot) {
            InventoryUtils.select(ctx, restoreSlot);
        }

        restoreSlot = -1;
        holdTicks = 0;
        quietTicks = 0;
    }
}
