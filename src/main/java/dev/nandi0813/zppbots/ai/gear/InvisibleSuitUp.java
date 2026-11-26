package dev.nandi0813.zppbots.ai.gear;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.ItemKinds;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;

public final class InvisibleSuitUp {

    private static final String EQUIP_ARMOR = "EQUIP_ARMOR";

    private static final EquipmentSlot[] ORDER = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.HEAD, EquipmentSlot.CHEST};

    private final BotContext ctx;
    private final Player bot;
    private State state;

    public InvisibleSuitUp(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean holdsArmour() {
        State s = state;
        if (s != null && s.step >= 0) {
            return true;
        } else if (!bot.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            return false;
        } else {
            return s != null && s.released ? false : wearsNoArmour();
        }
    }

    public boolean callToArms() {
        return start(false);
    }

    public void tick() {
        State s = state;
        if (s != null && s.step < 0 && !bot.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            state = null;
        } else if (s != null && s.step >= 0) {
            if (s.timer-- <= 0) {
                while (s.step < ORDER.length) {
                    EquipmentSlot slot = ORDER[s.step++];
                    if (equipBest(slot)) {
                        s.timer = s.fast ? 2 : 4 + ctx.random().nextInt(4);
                        return;
                    }
                }

                s.step = -1;
                s.released = true;
            }
        } else {
            if (bot.getNoDamageTicks() > 0 && holdsArmour()) {
                start(true);
            }
        }
    }

    private boolean start(boolean fast) {
        if (holdsArmour() && hasArmourToWear()) {
            if (state == null) {
                state = new State();
            }
            State s = state;
            if (s.step < 0) {
                s.step = 0;
                s.timer = fast ? 0 : 2 + ctx.random().nextInt(6);
            }

            s.fast |= fast;
            return true;
        } else {
            return false;
        }
    }

    private boolean wearsNoArmour() {
        for (EquipmentSlot slot : ORDER) {
            if (!ItemKinds.isEmpty(bot.getEquipment().getItem(slot))) {
                return false;
            }
        }

        return true;
    }

    private boolean hasArmourToWear() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            if (ArmorEvaluator.isArmor(inv.getItem(i))) {
                return true;
            }
        }

        return false;
    }

    private boolean equipBest(EquipmentSlot slot) {
        PlayerInventory inv = bot.getInventory();
        ItemStack worn = bot.getEquipment().getItem(slot);
        int best = -1;
        double bestScore = ArmorEvaluator.score(worn);

        for (int i = 0; i < 36; i++) {
            ItemStack cand = inv.getItem(i);
            if (ArmorEvaluator.isArmor(cand) && ArmorEvaluator.getSlot(cand) == slot) {
                double sc = ArmorEvaluator.score(cand);
                if (sc > bestScore) {
                    bestScore = sc;
                    best = i;
                }
            }
        }

        if (best < 0) {
            return false;
        } else {
            ItemStack piece = inv.getItem(best).clone();
            inv.setItem(best, ItemKinds.isEmpty(worn) ? null : worn.clone());
            bot.getEquipment().setItem(slot, piece);
            ctx.log().decision("InvisibleSuitUp", EQUIP_ARMOR, "suits up: " + piece.getType());
            return true;
        }
    }

    private static final class State {
        int step = -1;
        int timer;
        boolean fast;
        boolean released;
    }
}
