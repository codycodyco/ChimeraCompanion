package dev.cody.chimera;

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

    private static final double SUMMON_DISTANCE = 1.5;

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

        Allay allay = player.getWorld().spawn(summonLocation(player), Allay.class);
        allay.setPersistent(false);
        allay.setInvulnerable(true);
        allay.setCollidable(false);
        allay.setSilent(true);
        allay.setAware(false);
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

    private static Location summonLocation(Player player) {
        Location location = player.getLocation();
        return location.subtract(location.getDirection().setY(0).normalize().multiply(SUMMON_DISTANCE));
    }
}
