package chimera;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the live companion entity of each online player, plus the bubble displayed above it.
 *
 * <p>Companions are temporary entities: they are never persisted with the world, they cannot
 * fight, collide or pick up items, and they are removed as soon as they are dismissed or their
 * owner leaves the server. Bubbles are temporary as well and disappear on their own.
 */
public final class CompanionManager {

    private static final double BEHIND_DISTANCE = 1.5;

    private static final String BUBBLE_TEXT = "你好会上班呀～";
    private static final int BUBBLE_TICKS = 80;
    private static final double BUBBLE_HEIGHT = 1.2;

    private final Map<UUID, Allay> companions = new HashMap<>();
    private final Map<UUID, Bubble> bubbles = new HashMap<>();

    private record Bubble(TextDisplay display, int expiresAtTick) {
    }

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
        clearBubble(playerId);
        Allay allay = companions.remove(playerId);
        if (allay == null) {
            return false;
        }
        allay.remove();
        return true;
    }

    /**
     * Removes every tracked companion and bubble, used when the plugin shuts down.
     */
    public void dismissAll() {
        for (Allay allay : companions.values()) {
            allay.remove();
        }
        companions.clear();
        for (Bubble bubble : bubbles.values()) {
            bubble.display().remove();
        }
        bubbles.clear();
    }

    /**
     * Shows the fixed test bubble above the player's companion. Showing it again refreshes the
     * existing bubble instead of stacking a second one.
     *
     * @return {@code false} if the player has no companion to put the bubble above
     */
    public boolean showBubble(Player player) {
        Allay companion = companionOf(player);
        if (companion == null) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        int expiresAtTick = Bukkit.getCurrentTick() + BUBBLE_TICKS;
        Bubble current = bubbles.get(playerId);
        if (current != null) {
            current.display().teleport(bubbleLocation(companion));
            bubbles.put(playerId, new Bubble(current.display(), expiresAtTick));
            return true;
        }

        TextDisplay display = companion.getWorld().spawn(bubbleLocation(companion), TextDisplay.class, textDisplay -> {
            textDisplay.text(Component.text(BUBBLE_TEXT));
            textDisplay.setBillboard(Display.Billboard.CENTER);
            textDisplay.setBackgroundColor(Color.fromARGB(0x80000000));
            // Let the client interpolate between follow task updates so the bubble does not jump.
            textDisplay.setTeleportDuration((int) FollowTask.PERIOD_TICKS);
            textDisplay.setPersistent(false);
        });
        bubbles.put(playerId, new Bubble(display, expiresAtTick));
        return true;
    }

    /**
     * Moves the player's bubble along with its companion and removes it once it has expired.
     */
    public void tickBubble(UUID playerId, Allay companion) {
        Bubble bubble = bubbles.get(playerId);
        if (bubble == null) {
            return;
        }
        if (Bukkit.getCurrentTick() >= bubble.expiresAtTick()) {
            bubble.display().remove();
            bubbles.remove(playerId);
            return;
        }
        bubble.display().teleport(bubbleLocation(companion));
    }

    /**
     * Removes the player's bubble, if one is showing.
     */
    public void clearBubble(UUID playerId) {
        Bubble bubble = bubbles.remove(playerId);
        if (bubble != null) {
            bubble.display().remove();
        }
    }

    /**
     * @return the companion owned by the player, or {@code null} if it has none
     */
    public Allay companionOf(Player player) {
        return companions.get(player.getUniqueId());
    }

    private static Location bubbleLocation(Allay companion) {
        return companion.getLocation().add(0, BUBBLE_HEIGHT, 0);
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
