package dev.nandi0813.zppbots.smoke;

import dev.nandi0813.zppbots.platform.BotActor;
import dev.nandi0813.zppbots.platform.BotFrame;
import dev.nandi0813.zppbots.platform.BotHands;
import dev.nandi0813.zppbots.platform.BotPlatform;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.RayTraceResult;

import java.util.Objects;
import java.util.UUID;

/**
 * Drives a body with a {@link BotInput} through {@link BotHands} and one {@link BotFrame}, the way the removed
 * platform input path did. The attack lands before the physics step, so it sees last tick's sprint state.
 */
final class LegacyDrive {

    private static final double REACH = 3.0;

    private LegacyDrive() {
    }

    static void tick(BotPlatform platform, BotActor actor, BotInput input) {
        Objects.requireNonNull(input, "input");
        Player player = actor.player();
        BotHands hands = platform.hands(actor);
        hands.look(player.getYaw() + input.yawDelta(), player.getPitch() + input.pitchDelta());
        if (input.heldSlot() >= 0) hands.selectSlot(input.heldSlot());
        if (input.inventoryFrom() >= 0 && !player.isHandRaised()) {
            PlayerInventory inventory = player.getInventory();
            ItemStack from = inventory.getItem(input.inventoryFrom());
            ItemStack to = inventory.getItem(input.inventoryTo());
            inventory.setItem(input.inventoryFrom(), to);
            inventory.setItem(input.inventoryTo(), from);
        }
        if (input.useAction() == BotInput.UseAction.RELEASE) {
            hands.releaseUseItem();
        } else if ((input.useAction() == BotInput.UseAction.START_MAIN || input.useAction() == BotInput.UseAction.START_OFF)
                && !player.isHandRaised()) {
            hands.useItem(input.useAction() == BotInput.UseAction.START_MAIN ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND);
        }
        if (input.attack() && !player.isHandRaised()) {
            LivingEntity target = rayTraced(player, input.attackTarget());
            if (target != null) hands.attack(target);
            hands.swing(EquipmentSlot.HAND);
        }
        platform.tick(actor, new BotFrame(
                input.forward() == input.backward() ? 0.0f : input.forward() ? 1.0f : -1.0f,
                input.left() == input.right() ? 0.0f : input.left() ? 1.0f : -1.0f,
                input.jump(), input.sneak(), input.sprint()));
    }

    private static LivingEntity rayTraced(Player player, UUID targetId) {
        if (targetId == null) return null;
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTrace(eye, eye.getDirection(), REACH, FluidCollisionMode.NEVER, false,
                0.0, entity -> entity != player && entity.isValid()
                        && (!(entity instanceof LivingEntity living) || !living.isDead())
                        && (!(entity instanceof Player other) || other.getGameMode() != GameMode.SPECTATOR));
        return result != null && result.getHitEntity() instanceof LivingEntity target
                && target.getUniqueId().equals(targetId) ? target : null;
    }
}
