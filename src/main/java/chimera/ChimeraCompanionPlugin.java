package chimera;

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

    private final CompanionManager companions = new CompanionManager();

    private BukkitTask followTask;

    @Override
    public void onEnable() {
        getCommand("chimera").setExecutor(new ChimeraCommand(companions));
        getServer().getPluginManager().registerEvents(this, this);
        followTask = new FollowTask(companions)
                .runTaskTimer(this, FollowTask.PERIOD_TICKS, FollowTask.PERIOD_TICKS);
    }

    @Override
    public void onDisable() {
        followTask.cancel();
        companions.dismissAll();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        companions.dismiss(event.getPlayer().getUniqueId());
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
