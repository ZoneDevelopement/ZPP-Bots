package dev.nandi0813.zppbots.platform;

import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.mc.Vec3;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Immediate client-like actions of one bot body, sent through its packet listener. Main thread only.
 * A cancelled or rejected action returns false. Hands are {@link EquipmentSlot#HAND} or
 * {@link EquipmentSlot#OFF_HAND}; any other slot throws IllegalArgumentException.
 */
public interface BotHands {

    /** Sets yaw (wrapped), pitch (clamped to +-90) and head yaw now. */
    void look(float yaw, float pitch);

    boolean useItem(EquipmentSlot hand);

    boolean useItemOn(EquipmentSlot hand, BlockPos pos, Direction face, Vec3 hit);

    /** Only acts when a hand is raised. */
    void releaseUseItem();

    boolean attack(Entity target);

    void swing(EquipmentSlot hand);

    boolean interact(Entity target, EquipmentSlot hand, Vec3 relativeHit);

    void drop(boolean wholeStack);

    void swapHands();

    void startDestroy(BlockPos pos, Direction face);

    void stopDestroy(BlockPos pos, Direction face);

    void abortDestroy(BlockPos pos, Direction face);

    /** Slot 0-8, IllegalArgumentException otherwise; no-op if already selected. */
    void selectSlot(int slot);

    /** Spear stab; no-op if the server has no STAB action. */
    void stab();

    /** Bukkit leaveVehicle(). */
    void dismount();

    /** Native tryToStartFallFlying(). */
    boolean startGliding();
}
