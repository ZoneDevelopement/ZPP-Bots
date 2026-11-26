package dev.nandi0813.zppbots.ai.mace;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class OrbitalStrikeController {

    private static final int CONFIRM_DELAY = 4;
    private static final int CONFIRM_TRIES = 2;

    private final BotContext ctx;
    private final Player bot;
    private Phase phase = Phase.IDLE;
    private int phaseTick = 0;
    private int cooldown = 0;
    private int runTicks = 0;
    private Vec3 strikePos = null;
    private Kind confirmKind = null;
    private LivingEntity confirmTarget = null;
    private int confirmTries = 0;

    public OrbitalStrikeController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean isActive() {
        return phase != Phase.SURVIVE || phaseTick > 0;
    }

    public void reset() {
        if (phase == Phase.SURVIVE && bot.isHandRaised()) {
            ctx.hands().releaseUseItem();
        }

        if (phase == Phase.RUN) {
            ctx.controls().stopMoving();
        }

        strikePos = null;
        confirmKind = null;
        confirmTarget = null;
        phase = Phase.IDLE;
        phaseTick = 0;
    }

    public boolean tick(LivingEntity target, double dist) {
        if (cooldown > 0) {
            cooldown--;
        }

        if (phase == Phase.SURVIVE) {
            return tickSurvive();
        } else if (phase == Phase.RUN) {
            return tickRun();
        } else if (phase == Phase.CONFIRM) {
            return tickConfirm();
        } else if (target != null && Ent.alive(target) && cooldown <= 0) {
            int slot = findOrbitalRodSlot();
            if (slot < 0) {
                return false;
            } else {
                Kind kind = kindOf(bot.getInventory().getItem(slot));
                boolean aim = kind.aimed();
                if (kind.overworldOnly && ctx.world().bukkit().getEnvironment() != World.Environment.NORMAL) {
                    return false;
                } else if (dist < ctx.tuning().number(kind.minKey).doubleValue()
                        || dist > ctx.tuning().number(kind.maxKey).doubleValue()) {
                    return false;
                } else if (aim && !bot.hasLineOfSight(target)) {
                    return false;
                } else {
                    InventoryUtils.select(ctx, InventoryUtils.moveToHotbarSafe(ctx, slot));
                    ctx.controls().stopMoving();
                    aimAt(kind, target);
                    ItemStack rod = bot.getInventory().getItemInMainHand();
                    if (ItemKinds.is(rod, Material.FISHING_ROD)) {
                        ctx.hands().swing(EquipmentSlot.HAND);
                        ctx.hands().useItem(EquipmentSlot.HAND);
                    }

                    cooldown = ctx.tuning().integer(TuningKey.ORBITAL_COOLDOWN);
                    if (orbitalPlusMode(bot.getInventory().getItemInMainHand()) != null) {
                        phase = Phase.CONFIRM;
                        phaseTick = 0;
                        confirmKind = kind;
                        confirmTarget = target;
                        confirmTries = 0;
                        return true;
                    } else {
                        return afterFire(kind, target);
                    }
                }
            }
        } else {
            return false;
        }
    }

    private boolean afterFire(Kind kind, LivingEntity target) {
        boolean aim = kind.aimed();
        if (kind.runTicks > 0) {
            phase = Phase.RUN;
            runTicks = kind.runTicks;
            strikePos = Ent.pos(target);
            return true;
        } else if (aim) {
            return true;
        } else {
            phase = Phase.SURVIVE;
            phaseTick = 0;
            return true;
        }
    }

    private boolean tickConfirm() {
        phaseTick++;
        ctx.controls().stopMoving();
        LivingEntity target = confirmTarget;
        Kind kind = confirmKind;
        ItemStack rod = bot.getInventory().getItemInMainHand();
        if (target != null && kind != null && Ent.alive(target) && ItemKinds.is(rod, Material.FISHING_ROD)) {
            aimAt(kind, target);
            if (orbitalPlusMode(rod) == null) {
                phase = Phase.IDLE;
                confirmKind = null;
                confirmTarget = null;
                return afterFire(kind, target);
            } else if (phaseTick % CONFIRM_DELAY != 0) {
                return true;
            } else if (confirmTries++ >= CONFIRM_TRIES) {
                phase = Phase.IDLE;
                confirmKind = null;
                confirmTarget = null;
                return false;
            } else {
                ctx.hands().swing(EquipmentSlot.HAND);
                ctx.hands().useItem(EquipmentSlot.HAND);
                return true;
            }
        } else {
            phase = Phase.IDLE;
            confirmKind = null;
            confirmTarget = null;
            return false;
        }
    }

    private boolean tickSurvive() {
        phaseTick++;
        ctx.controls().stopMoving();
        ctx.controls().setSprinting(false);
        int shieldSlot = findShieldSlot();
        if (shieldSlot >= 0) {
            InventoryUtils.select(ctx, InventoryUtils.moveToHotbarSafe(ctx, shieldSlot));
            ctx.controls().look(bot.getLocation().getYaw(), 90.0F);
            if (!bot.isHandRaised()) {
                ctx.hands().useItem(EquipmentSlot.HAND);
            }
        }

        if (phaseTick >= ctx.tuning().integer(TuningKey.ORBITAL_SURVIVE_TICKS)) {
            if (bot.isHandRaised()) {
                ctx.hands().releaseUseItem();
            }

            phase = Phase.IDLE;
            phaseTick = 0;
            return false;
        } else {
            return true;
        }
    }

    private boolean tickRun() {
        if (--runTicks > 0 && strikePos != null) {
            Vec3 bp = Ent.pos(bot);
            double dx = bp.x() - strikePos.x();
            double dz = bp.z() - strikePos.z();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            ctx.controls().look(yaw, 0.0F);
            ctx.controls().setSprinting(true);
            ctx.controls().setMovement(1.0F, 0.0F);
            return true;
        } else {
            ctx.controls().stopMoving();
            phase = Phase.IDLE;
            strikePos = null;
            return false;
        }
    }

    private int findOrbitalRodSlot() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            if (isOrbitalRod(inv.getItem(i))) {
                return i;
            }
        }

        return -1;
    }

    private static boolean isOrbitalRod(ItemStack st) {
        if (!ItemKinds.isEmpty(st) && st.getType() == Material.FISHING_ROD) {
            Map<String, String> t = customData(st);
            if (t.isEmpty()) {
                return false;
            } else if (cannonRodName(st, t) != null) {
                return true;
            } else if (orbitalPlusMode(t) != null) {
                return true;
            } else {
                for (String k : t.keySet()) {
                    String lk = k.toLowerCase(Locale.ROOT);
                    if (lk.startsWith("stab") || lk.startsWith("nuke") || lk.startsWith("c_nuke")) {
                        return true;
                    }
                }

                return false;
            }
        } else {
            return false;
        }
    }

    private static String cannonRodName(ItemStack st, Map<String, String> t) {
        if (!t.containsKey("Orbital_Cannon") && !t.containsKey("wolf_cannon")) {
            return null;
        } else {
            ItemMeta meta = st.getItemMeta();
            Component name = meta == null ? null : meta.customName();
            if (name == null) {
                return null;
            } else {
                return cannonName(PlainTextComponentSerializer.plainText().serialize(name));
            }
        }
    }

    static String cannonName(String name) {
        return switch (name) {
            case "Nuke Shot", "Stab Shot", "Law-Nuke Shot", "Wither Nuke Shot", "Arrow Shot", "Wolf Rod" -> name;
            default -> null;
        };
    }

    private static String orbitalPlusMode(ItemStack st) {
        if (!ItemKinds.isEmpty(st) && st.getType() == Material.FISHING_ROD) {
            return orbitalPlusMode(customData(st));
        } else {
            return null;
        }
    }

    static String orbitalPlusMode(Map<String, String> t) {
        String mode = stringValue(t, "OrbitalMode");

        return switch (mode) {
            case "stab", "nuke", "railgun", "wolf" -> mode;
            default -> null;
        };
    }

    private static Kind kindOf(ItemStack st) {
        Map<String, String> t = customData(st);
        String plus = orbitalPlusMode(t);
        if (plus != null) {
            return switch (plus) {
                case "nuke" -> Kind.OPLUS_NUKE;
                case "stab" -> Kind.OPLUS_STAB;
                case "railgun" -> Kind.OPLUS_RAILGUN;
                default -> Kind.OPLUS_WOLF;
            };
        } else {
            String cannon = cannonRodName(st, t);
            if (cannon != null) {
                return switch (cannon) {
                    case "Stab Shot" -> Kind.STAB;
                    case "Wither Nuke Shot" -> Kind.WITHER_NUKE;
                    case "Arrow Shot" -> Kind.ARROW;
                    case "Wolf Rod" -> Kind.WOLF;
                    default -> Kind.NUKE;
                };
            } else {
                return isAimRod(t) ? Kind.STEPAN_AIM : Kind.STEPAN_SELF;
            }
        }
    }

    private static boolean isAimRod(Map<String, String> t) {
        for (String k : t.keySet()) {
            if (k.toLowerCase(Locale.ROOT).contains("aim")) {
                return true;
            }
        }

        return false;
    }

    private int findShieldSlot() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.SHIELD)) {
                return i;
            }
        }

        return -1;
    }

    private void aimAt(Kind kind, LivingEntity target) {
        if (kind.atEntity()) {
            faceEntity(target);
        } else if (kind.aimed()) {
            faceGroundAt(target);
        } else {
            faceCastForward();
        }
    }

    private void faceEntity(LivingEntity target) {
        Vec3 bp = Ent.pos(bot);
        Vec3 tp = Ent.pos(target);
        double dx = tp.x() - bp.x();
        double dy = Ent.eyePos(target).y() - Ent.eyePos(bot).y();
        double dz = tp.z() - bp.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        ctx.controls().look((float) Math.toDegrees(Math.atan2(-dx, dz)), (float) (-Math.toDegrees(Math.atan2(dy, Math.max(horiz, 0.001)))));
    }

    private void faceGroundAt(LivingEntity target) {
        Vec3 bp = Ent.pos(bot);
        Vec3 tp = Ent.pos(target);
        double dx = tp.x() - bp.x();
        double dy = tp.y() - 0.1 - Ent.eyePos(bot).y();
        double dz = tp.z() - bp.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        ctx.controls().look((float) Math.toDegrees(Math.atan2(-dx, dz)), (float) (-Math.toDegrees(Math.atan2(dy, Math.max(horiz, 0.001)))));
    }

    private void faceCastForward() {
        ctx.controls().look(bot.getLocation().getYaw(), 0.0F);
    }

    /** Top-level keys of the stack's custom_data compound, with their raw SNBT values; empty when absent. */
    private static Map<String, String> customData(ItemStack st) {
        ItemMeta meta = st == null ? null : st.getItemMeta();
        if (meta == null) {
            return Collections.emptyMap();
        }
        return parseCompound(customDataSnbt(meta.getAsComponentString()));
    }

    /** The {...} text of the custom_data component inside an item component string, or null. */
    static String customDataSnbt(String componentString) {
        if (componentString == null) {
            return null;
        }
        int at = componentString.indexOf("custom_data=");
        if (at < 0) {
            return null;
        }
        int start = componentString.indexOf('{', at);
        if (start != at + "custom_data=".length()) {
            return null;
        }
        int end = skipValue(componentString, start);
        return end < 0 ? null : componentString.substring(start, end);
    }

    /** Parses {key:value,...} into top-level keys and raw value text; malformed input yields what was read so far. */
    static Map<String, String> parseCompound(String snbt) {
        Map<String, String> out = new LinkedHashMap<>();
        if (snbt == null || snbt.length() < 2 || snbt.charAt(0) != '{') {
            return out;
        }
        int i = 1;
        int n = snbt.length();
        while (i < n && snbt.charAt(i) != '}') {
            int keyEnd;
            String key;
            if (snbt.charAt(i) == '"' || snbt.charAt(i) == '\'') {
                keyEnd = skipValue(snbt, i);
                if (keyEnd < 0) {
                    return out;
                }
                key = snbt.substring(i + 1, keyEnd - 1);
            } else {
                keyEnd = i;
                while (keyEnd < n && snbt.charAt(keyEnd) != ':' && snbt.charAt(keyEnd) != '}') {
                    keyEnd++;
                }
                key = snbt.substring(i, keyEnd).trim();
            }
            if (keyEnd >= n || snbt.charAt(keyEnd) != ':') {
                return out;
            }
            int valueStart = keyEnd + 1;
            int valueEnd = skipValue(snbt, valueStart);
            if (valueEnd < 0) {
                return out;
            }
            out.put(key, snbt.substring(valueStart, valueEnd).trim());
            i = valueEnd;
            if (i < n && snbt.charAt(i) == ',') {
                i++;
            }
            while (i < n && snbt.charAt(i) == ' ') {
                i++;
            }
        }
        return out;
    }

    /** End index (exclusive) of the value starting at from: a quoted string, a balanced {}/[] block, or a bare token. */
    private static int skipValue(String s, int from) {
        int n = s.length();
        int i = from;
        while (i < n && s.charAt(i) == ' ') {
            i++;
        }
        if (i >= n) {
            return -1;
        }
        char c = s.charAt(i);
        if (c == '"' || c == '\'') {
            for (int j = i + 1; j < n; j++) {
                if (s.charAt(j) == '\\') {
                    j++;
                } else if (s.charAt(j) == c) {
                    return j + 1;
                }
            }
            return -1;
        }
        if (c == '{' || c == '[') {
            int depth = 0;
            for (int j = i; j < n; j++) {
                char d = s.charAt(j);
                if (d == '"' || d == '\'') {
                    j = skipValue(s, j) - 1;
                    if (j < 0) {
                        return -1;
                    }
                } else if (d == '{' || d == '[') {
                    depth++;
                } else if (d == '}' || d == ']') {
                    if (--depth == 0) {
                        return j + 1;
                    }
                }
            }
            return -1;
        }
        int j = i;
        while (j < n && s.charAt(j) != ',' && s.charAt(j) != '}' && s.charAt(j) != ']') {
            j++;
        }
        return j;
    }

    private static String stringValue(Map<String, String> t, String key) {
        String raw = t.get(key);
        if (raw == null) {
            return "";
        }
        if (raw.length() >= 2 && (raw.charAt(0) == '"' || raw.charAt(0) == '\'')) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    enum Kind {
        STEPAN_AIM(TuningKey.ORBITAL_STEPAN_AIM_MIN_DIST, TuningKey.ORBITAL_STEPAN_AIM_MAX_DIST, 0, false),
        STEPAN_SELF(TuningKey.ORBITAL_STEPAN_SELF_MIN_DIST, TuningKey.ORBITAL_STEPAN_SELF_MAX_DIST, 0, false),
        NUKE(TuningKey.ORBITAL_NUKE_MIN_DIST, TuningKey.ORBITAL_NUKE_MAX_DIST, 110, true),
        WITHER_NUKE(TuningKey.ORBITAL_WITHER_NUKE_MIN_DIST, TuningKey.ORBITAL_WITHER_NUKE_MAX_DIST, 80, false),
        STAB(TuningKey.ORBITAL_STAB_MIN_DIST, TuningKey.ORBITAL_STAB_MAX_DIST, 40, true),
        ARROW(TuningKey.ORBITAL_ARROW_MIN_DIST, TuningKey.ORBITAL_ARROW_MAX_DIST, 0, true),
        WOLF(TuningKey.ORBITAL_WOLF_MIN_DIST, TuningKey.ORBITAL_WOLF_MAX_DIST, 0, true),
        OPLUS_NUKE(TuningKey.ORBITAL_OPLUS_NUKE_MIN_DIST, TuningKey.ORBITAL_OPLUS_NUKE_MAX_DIST, 100, false),
        OPLUS_STAB(TuningKey.ORBITAL_OPLUS_STAB_MIN_DIST, TuningKey.ORBITAL_OPLUS_STAB_MAX_DIST, 40, false),
        OPLUS_RAILGUN(TuningKey.ORBITAL_OPLUS_RAILGUN_MIN_DIST, TuningKey.ORBITAL_OPLUS_RAILGUN_MAX_DIST, 0, false),
        OPLUS_WOLF(TuningKey.ORBITAL_OPLUS_WOLF_MIN_DIST, TuningKey.ORBITAL_OPLUS_WOLF_MAX_DIST, 0, false);

        final TuningKey minKey;
        final TuningKey maxKey;
        final int runTicks;
        final boolean overworldOnly;

        Kind(TuningKey minKey, TuningKey maxKey, int runTicks, boolean overworldOnly) {
            this.minKey = minKey;
            this.maxKey = maxKey;
            this.runTicks = runTicks;
            this.overworldOnly = overworldOnly;
        }

        boolean aimed() {
            return this != STEPAN_SELF;
        }

        boolean atEntity() {
            return this == STEPAN_AIM || this == OPLUS_RAILGUN;
        }
    }

    private enum Phase {
        IDLE,
        CASTING,
        CONFIRM,
        SURVIVE,
        RUN
    }
}
