package chimera;

import com.destroystokyo.paper.entity.Pathfinder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Keeps every companion near its owner.
 *
 * <p>One shared synchronous task walks the online players instead of one task per player. For each
 * companion the distance to its owner decides between standing still, pathfinding to the point
 * behind the owner, and teleporting there. A companion in a different world than its owner is
 * teleported over, and dead owners are skipped because their companion is frozen in place by the
 * death listener.
 */
public final class FollowTask extends BukkitRunnable {

    public static final long PERIOD_TICKS = 10;

    private static final double STOP_DISTANCE = 2.5;
    private static final double TELEPORT_DISTANCE = 12.0;
    private static final double PATH_SPEED = 1.2;

    private final CompanionManager companions;

    public FollowTask(CompanionManager companions) {
        this.companions = companions;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Allay companion = companions.companionOf(player);
            if (companion == null) {
                continue;
            }
            UUID playerId = player.getUniqueId();
            if (!companion.isValid()) {
                companions.clearBubble(playerId);
                continue;
            }

            // Keep an active bubble glued to the companion and drop it once it expires.
            companions.tickBubble(player, companion);
            companions.tickDialogue(player);
            companions.tickSound(player, companion);

            // Dead owners leave the companion frozen where it is until the respawn event moves it.
            if (player.getHealth() <= 0.0D) {
                continue;
            }

            // The companion lives in another world, so it has to be moved over instead of walked.
            if (!player.getWorld().equals(companion.getWorld())) {
                companions.clearBubble(playerId);
                companion.getPathfinder().stopPathfinding();
                companion.teleport(CompanionManager.behind(player.getLocation()));
                continue;
            }

            double distance = companion.getLocation().distance(player.getLocation());
            Pathfinder pathfinder = companion.getPathfinder();

            if (distance > TELEPORT_DISTANCE) {
                // Drop the old path first, otherwise the allay keeps walking back to where it was.
                pathfinder.stopPathfinding();
                companion.teleport(CompanionManager.behind(player.getLocation()));
            } else if (distance < STOP_DISTANCE) {
                pathfinder.stopPathfinding();
            } else {
                pathfinder.moveTo(CompanionManager.behind(player.getLocation()), PATH_SPEED);
            }
        }
    }
}
