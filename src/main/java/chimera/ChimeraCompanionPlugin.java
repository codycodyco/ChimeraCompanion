package chimera;

import chimera.trigger.TriggerService;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registers the companion command, drives the follow task and keeps companions in sync with their
 * owners across death, respawn and world changes.
 */
public final class ChimeraCompanionPlugin extends JavaPlugin implements Listener {

    private CompanionManager companions;

    private TriggerService triggers;

    private BukkitTask followTask;

    private final Map<UUID, String> pendingDeathTriggers = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        companions = new CompanionManager(
                getConfig().getStringList("dialogue.messages"),
                getConfig().getInt("dialogue.interval-min", 60),
                getConfig().getInt("dialogue.interval-max", 180));
        triggers = new TriggerService(this, companions);
        getCommand("chimera").setExecutor(new ChimeraCommand(companions, triggers, this));
        getServer().getPluginManager().registerEvents(this, this);
        followTask = new FollowTask(companions)
                .runTaskTimer(this, FollowTask.PERIOD_TICKS, FollowTask.PERIOD_TICKS);
    }

    @Override
    public void onDisable() {
        followTask.cancel();
        companions.dismissAll();
        triggers.clearAll();
        pendingDeathTriggers.clear();
    }

    /**
     * Re-reads config.yml and applies the settings that are otherwise only read at startup.
     */
    public void reloadCompanionConfig() {
        reloadConfig();
        companions.reload(
                getConfig().getStringList("dialogue.messages"),
                getConfig().getInt("dialogue.interval-min", 60),
                getConfig().getInt("dialogue.interval-max", 180));
        triggers.reload();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        companions.dismiss(playerId);
        triggers.clear(playerId);
        pendingDeathTriggers.remove(playerId);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Allay companion = companions.companionOf(player);
        if (companion == null) {
            return;
        }
        // Stay exactly where the player died: no leftover path and no idle wandering.
        companion.getPathfinder().stopPathfinding();
        companion.setAware(false);

        // Remember the line so it can be shown once the player respawns.
        if (event.getDamageSource().getDamageType() == DamageType.LAVA) {
            pendingDeathTriggers.put(player.getUniqueId(), "death.lava");
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String triggerId = pendingDeathTriggers.remove(player.getUniqueId());

        Allay companion = companions.companionOf(player);
        if (companion == null) {
            return;
        }
        companion.setAware(true);
        companion.teleport(CompanionManager.behind(event.getRespawnLocation()));

        if (triggerId != null) {
            triggers.showTrigger(player, triggerId);
        }
    }
}
