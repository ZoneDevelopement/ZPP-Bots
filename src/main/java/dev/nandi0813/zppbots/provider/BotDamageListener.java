package dev.nandi0813.zppbots.provider;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/** Feeds landed hits to the match view and revenge target of every bot that can see the victim or the attacker; notes cancelled regeneration. */
public final class BotDamageListener implements Listener {

    private final NativeBotProvider provider;

    public BotDamageListener(NativeBotProvider provider) {
        this.provider = provider;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity attacker = event.getDamager();
        if (attacker instanceof Projectile projectile) {
            attacker = projectile.getShooter() instanceof LivingEntity shooter ? shooter : null;
        }
        if (!(attacker instanceof LivingEntity) || !(event.getEntity() instanceof LivingEntity victim)) return;
        for (NativeBotOpponent opponent : provider.opponents()) {
            if (opponent.tracks(victim) || opponent.tracks(attacker)) opponent.recordAttack(victim, attacker.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRegen(EntityRegainHealthEvent event) {
        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED || !event.isCancelled()) return;
        for (NativeBotOpponent opponent : provider.opponents()) opponent.noteRegenCancelled(event.getEntity());
    }
}
