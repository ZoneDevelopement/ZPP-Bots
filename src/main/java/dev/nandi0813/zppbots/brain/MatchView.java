package dev.nandi0813.zppbots.brain;

import dev.nandi0813.api.Interface.BotFightView;
import dev.nandi0813.zppbots.mc.AABB;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The bot's copy of the round state from the last {@link BotFightView}, plus tracked velocities and attackers. */
public final class MatchView {

    private static final int ATTACK_MEMORY_TICKS = 100;

    private int team;
    private List<Player> enemies = List.of();
    private List<Player> allies = List.of();
    private boolean live;
    private boolean canMove;
    private int roundTicks;
    private int round;
    private String matchId = "";
    private String ladderName = "";
    private AABB bounds = new AABB(0, 0, 0, 0, 0, 0);
    private int deadZoneY = Integer.MIN_VALUE;

    private final Map<UUID, Vec3> lastPos = new HashMap<>();
    private final Map<UUID, UUID> lastWorld = new HashMap<>();
    private final Map<UUID, Vec3> velocity = new HashMap<>();
    private final Map<UUID, Attack> attacks = new HashMap<>();

    private record Attack(UUID attacker, int tick) {
    }

    public MatchView() {
    }

    public void update(BotFightView view, Player self) {
        team = view.getTeam();
        enemies = List.copyOf(view.getEnemies());
        allies = List.copyOf(view.getAllies());
        live = view.isLive();
        canMove = live || canMove(view);
        roundTicks = view.getRoundTicks();
        round = view.getRoundNumber();
        matchId = view.getMatchId();
        ladderName = view.getLadderName();
        bounds = AABB.of(view.getArenaBounds());
        deadZoneY = view.getDeadZoneY();

        Map<UUID, Vec3> previous = new HashMap<>(lastPos);
        Map<UUID, UUID> previousWorld = new HashMap<>(lastWorld);
        lastPos.clear();
        lastWorld.clear();
        velocity.clear();
        track(self, previous, previousWorld);
        for (Player enemy : enemies) track(enemy, previous, previousWorld);
        for (Player ally : allies) track(ally, previous, previousWorld);
    }

    /** Older cores lack {@code canMove()}; they never allow start movement. */
    private static boolean canMove(BotFightView view) {
        try {
            return view.canMove();
        } catch (AbstractMethodError | NoSuchMethodError unsupported) {
            return false;
        }
    }

    private void track(Player entity, Map<UUID, Vec3> previous, Map<UUID, UUID> previousWorld) {
        UUID id = entity.getUniqueId();
        Vec3 now = Ent.pos(entity);
        Vec3 before = previous.get(id);
        boolean sameWorld = entity.getWorld().getUID().equals(previousWorld.get(id));
        velocity.put(id, before != null && sameWorld ? now.subtract(before) : Ent.velocity(entity));
        lastPos.put(id, now);
        lastWorld.put(id, entity.getWorld().getUID());
    }

    public int team() {
        return team;
    }

    public List<Player> enemies() {
        return enemies;
    }

    public List<Player> allies() {
        return allies;
    }

    public boolean live() {
        return live;
    }

    public boolean canMove() {
        return canMove;
    }

    public int roundTicks() {
        return roundTicks;
    }

    public int round() {
        return round;
    }

    public String matchId() {
        return matchId;
    }

    public String ladderName() {
        return ladderName;
    }

    public AABB bounds() {
        return bounds;
    }

    public int deadZoneY() {
        return deadZoneY;
    }

    public boolean isEnemy(Entity entity) {
        return enemies.contains(entity);
    }

    public boolean isAlly(Entity entity) {
        return allies.contains(entity);
    }

    public Vec3 velocityOf(Entity entity) {
        Vec3 tracked = velocity.get(entity.getUniqueId());
        return tracked != null ? tracked : Ent.velocity(entity);
    }

    public void recordAttack(UUID victim, UUID attacker, int tick) {
        attacks.put(victim, new Attack(attacker, tick));
    }

    public LivingEntity lastAttackerOf(LivingEntity victim, int tick) {
        return lastAttackerOf(victim, tick, ATTACK_MEMORY_TICKS);
    }

    public LivingEntity lastAttackerOf(LivingEntity victim, int tick, int maxAge) {
        Attack attack = attacks.get(victim.getUniqueId());
        if (attack == null || tick - attack.tick() > maxAge) return null;
        return Bukkit.getEntity(attack.attacker()) instanceof LivingEntity attacker && attacker.isValid() ? attacker : null;
    }
}
