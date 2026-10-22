package chimera;

import chimera.trigger.TriggerService;
import org.bukkit.entity.Allay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Registers the companion command, drives the follow task and keeps companions in sync with their
 * owners across death, respawn and world changes.
 */
public final class ChimeraCompanionPlugin extends JavaPlugin implements Listener {

    private CompanionManager companions;

    private TriggerService triggers;

    private BukkitTask followTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        companions = new CompanionManager(
                getConfig().getStringList("dialogue.messages"),
                getConfig().getInt("dialogue.interval-min", 60),
                getConfig().getInt("dialogue.interval-max", 180));
        triggers = new TriggerService(this, companions);
        getCommand("chimera").setExecutor(new ChimeraCommand(companions, triggers));
        getServer().getPluginManager().registerEvents(this, this);
        followTask = new FollowTask(companions)
                .runTaskTimer(this, FollowTask.PERIOD_TICKS, FollowTask.PERIOD_TICKS);
    }

    @Override
    public void onDisable() {
        followTask.cancel();
        companions.dismissAll();
        triggers.clearAll();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        companions.dismiss(event.getPlayer().getUniqueId());
        triggers.clear(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Allay companion = companions.companionOf(event.getEntity());
        if (companion == null) {
            return;
        }
        // Stay exactly where the player died: no leftover path and no idle wandering.
        companion.getPathfinder().stopPathfinding();
        companion.setAware(false);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Allay companion = companions.companionOf(event.getPlayer());
        if (companion == null) {
            return;
        }
        companion.setAware(true);
        companion.teleport(CompanionManager.behind(event.getRespawnLocation()));
    }
}
