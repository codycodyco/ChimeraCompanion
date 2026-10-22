package chimera.trigger;

import chimera.CompanionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Single entry point for behaviour triggered lines.
 *
 * <p>Reads the lines configured for a trigger id, enforces one shared cooldown per player and lets
 * the companion say the line through the existing bubble display. It does not listen to any game
 * event itself.
 */
public final class TriggerService {

    private final JavaPlugin plugin;
    private final CompanionManager companions;
    private final long cooldownTicks;
    private final Map<UUID, Long> lastTriggerTicks = new HashMap<>();

    public TriggerService(JavaPlugin plugin, CompanionManager companions) {
        this.plugin = plugin;
        this.companions = companions;
        this.cooldownTicks = plugin.getConfig().getLong("triggers.cooldown-seconds", 60L) * 20L;
    }

    /**
     * Shows a random line configured for the given trigger id.
     *
     * <p>Does nothing when the player is still on cooldown, when the trigger has no lines, or when
     * the player has no summoned companion. The cooldown only advances after a line was shown.
     *
     * @return {@code true} if a line was shown
     */
    public boolean showTrigger(Player player, String triggerId) {
        UUID playerId = player.getUniqueId();
        Long lastTick = lastTriggerTicks.get(playerId);
        if (lastTick != null && Bukkit.getCurrentTick() - lastTick < cooldownTicks) {
            return false;
        }

        List<String> messages = plugin.getConfig().getStringList("triggers." + triggerId + ".messages");
        if (messages.isEmpty()) {
            return false;
        }

        String message = messages.get(ThreadLocalRandom.current().nextInt(messages.size()));
        if (!companions.showBubble(player, message)) {
            return false;
        }

        lastTriggerTicks.put(playerId, (long) Bukkit.getCurrentTick());
        return true;
    }

    /**
     * Drops the cooldown of a player whose companion session has ended.
     */
    public void clear(UUID playerId) {
        lastTriggerTicks.remove(playerId);
    }

    /**
     * Drops every cooldown, used when the plugin shuts down.
     */
    public void clearAll() {
        lastTriggerTicks.clear();
    }
}
