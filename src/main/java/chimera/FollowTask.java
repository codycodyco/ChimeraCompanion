package chimera;

import chimera.trigger.TriggerService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Keeps every companion near its owner.
 *
 * <p>One shared synchronous task walks the online players instead of one task per player. Follow
 * movement itself is left to the cat's own tamed-owner AI; this task only keeps the bubble, random
 * dialogue and sound timers running, teleports the companion over when it is in another world or
 * further away than the teleport threshold, and feeds the {@code companion.catch_up} trigger.
 *
 * <p>Dead owners are skipped because their companion is frozen in place by the death listener.
 */
public final class FollowTask extends BukkitRunnable {

    public static final long PERIOD_TICKS = 10;

    private static final double STOP_DISTANCE = 2.5;
    private static final double TELEPORT_DISTANCE = 12.0;

    private final CompanionManager companions;
    private final TriggerService triggers;

    public FollowTask(CompanionManager companions, TriggerService triggers) {
        this.companions = companions;
        this.triggers = triggers;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Cat companion = companions.companionOf(player);
            if (companion == null) {
                continue;
            }
            UUID playerId = player.getUniqueId();
            if (!companion.isValid()) {
                companions.clearBubble(playerId);
                companions.clearModel(playerId);
                continue;
            }

            // Keep an active bubble glued to the companion model and drop it once it expires.
            companions.tickBubble(player);
            companions.tickDialogue(player);
            companions.tickModel(playerId, companion);

            // Dead owners leave the companion frozen where it is until the respawn event moves it.
            if (player.getHealth() <= 0.0D) {
                continue;
            }

            // The companion lives in another world, so it has to be moved over instead of walked.
            if (!player.getWorld().equals(companion.getWorld())) {
                companions.clearBubble(playerId);
                companion.teleport(CompanionManager.behind(player.getLocation()));
                companions.recreateModel(playerId, companion);
                companions.clearChase(playerId);
                triggers.showTrigger(player, "companion.catch_up");
                continue;
            }

            double distance = companion.getLocation().distance(player.getLocation());
            if (distance > TELEPORT_DISTANCE) {
                companion.teleport(CompanionManager.behind(player.getLocation()));
                companions.clearChase(playerId);
                triggers.showTrigger(player, "companion.catch_up");
            } else if (distance < STOP_DISTANCE) {
                companions.clearChase(playerId);
            } else if (companions.tickChase(playerId, Bukkit.getCurrentTick())) {
                triggers.showTrigger(player, "companion.catch_up");
            }
        }
    }
}
