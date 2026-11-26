package dev.nandi0813.zppbots.platform;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Synchronous, main-thread-only operations for a native bot body. */
public interface BotPlatform {

    BotActor spawn(BodyToken identity, Location location, Map<EquipmentSlot, ItemStack> equipment);

    /** Resets alive-only placement and equipment; a full round reset requires a fresh actor identity before LIVE. */
    void reset(BotActor actor, Location location, Map<EquipmentSlot, ItemStack> equipment);

    /** Applies the movement keys and runs one physics step, exactly once per Bukkit tick. */
    void tick(BotActor actor, BotFrame frame);

    /** Immediate packet-faithful actions of this body. */
    BotHands hands(BotActor actor);

    /** Releases controls without clearing momentum. */
    void pause(BotActor actor);

    Snapshot snapshot(BotActor actor);

    /** Removes the native body; the owner releases its identity after native callbacks complete. */
    void remove(BotActor actor);

    /** Shows the actor to this viewer only. No global human player-list registration is used. */
    void show(BotActor actor, Player viewer);

    /** Hides the actor and removes its body tracking for this viewer. */
    void hide(BotActor actor, Player viewer);

    /** Re-sends the body to every current viewer (after a profile change). */
    void refresh(BotActor actor);

    public record Snapshot(UUID entityId, UUID worldId, double x, double y, double z, float yaw, float pitch,
                          double velocityX, double velocityY, double velocityZ, boolean onGround,
                          boolean sprinting, double health, int food, float saturation, float attackCooldown,
                          double absorption, float exhaustion, int heldSlot, boolean usingItem, EquipmentSlot activeHand,
                          int useTicks, int useRemainingTicks, boolean blocking, List<org.bukkit.potion.PotionEffect> effects,
                          Map<EquipmentSlot, ItemStack> equipment) {
        public Snapshot {
            effects = List.copyOf(effects);
            EnumMap<EquipmentSlot, ItemStack> copy = new EnumMap<>(EquipmentSlot.class);
            equipment.forEach((slot, item) -> copy.put(slot, item.clone()));
            equipment = Collections.unmodifiableMap(copy);
        }

        @Override
        public Map<EquipmentSlot, ItemStack> equipment() {
            EnumMap<EquipmentSlot, ItemStack> copy = new EnumMap<>(EquipmentSlot.class);
            equipment.forEach((slot, item) -> copy.put(slot, item.clone()));
            return Collections.unmodifiableMap(copy);
        }
    }
}
