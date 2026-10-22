package dev.cody.chimera;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Registers the companion command and keeps companions from outliving their owners.
 */
public final class ChimeraCompanionPlugin extends JavaPlugin implements Listener {

    private final CompanionManager companions = new CompanionManager();

    @Override
    public void onEnable() {
        getCommand("chimera").setExecutor(new ChimeraCommand(companions));
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        companions.dismissAll();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        companions.dismiss(event.getPlayer().getUniqueId());
    }
}
