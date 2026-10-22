package chimera;

import org.bukkit.Location;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the live companion entity of each online player.
 *
 * <p>Companions are temporary entities: they are never persisted with the world, they cannot
 * fight, collide or pick up items, and they are removed as soon as they are dismissed or their
 * owner leaves the server.
 */
public final class CompanionManager {

    private static final double BEHIND_DISTANCE = 1.5;

    private final Map<UUID, Allay> companions = new HashMap<>();

    /**
     * Spawns a companion right behind the player.
     *
     * @return {@code false} if the player already has a living companion
     */
    public boolean summon(Player player) {
        UUID playerId = player.getUniqueId();
        Allay current = companions.get(playerId);
        if (current != null) {
            if (current.isValid()) {
                return false;
            }
            // The entity was removed by something else, so the stale entry is dropped.
            companions.remove(playerId);
        }

        Allay allay = player.getWorld().spawn(behind(player.getLocation()), Allay.class);
        allay.setPersistent(false);
        allay.setInvulnerable(true);
        allay.setCollidable(false);
        allay.setSilent(true);
        allay.setCanPickupItems(false);
        allay.setCanDuplicate(false);

        companions.put(playerId, allay);
        return true;
    }

    /**
     * Removes the companion owned by the given player.
     *
     * @return {@code false} if the player had no tracked companion
     */
    public boolean dismiss(UUID playerId) {
        Allay allay = companions.remove(playerId);
        if (allay == null) {
            return false;
        }
        allay.remove();
        return true;
    }

    /**
     * Removes every tracked companion, used when the plugin shuts down.
     */
    public void dismissAll() {
        for (Allay allay : companions.values()) {
            allay.remove();
        }
        companions.clear();
    }

    /**
     * @return the companion owned by the player, or {@code null} if it has none
     */
    public Allay companionOf(Player player) {
        return companions.get(player.getUniqueId());
    }

    /**
     * The point 1.5 blocks behind the given player location, used as both the summon spot and the
     * follow target. The location is not modified.
     */
    static Location behind(Location playerLocation) {
        Location target = playerLocation.clone();
        return target.subtract(target.getDirection().setY(0).normalize().multiply(BEHIND_DISTANCE));
    }
}
