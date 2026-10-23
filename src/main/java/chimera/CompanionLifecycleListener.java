package chimera;

import chimera.trigger.TriggerService;
import chimera.trigger.detector.DeathTrigger;
import chimera.trigger.detector.IdleTrigger;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * 处理跟宠与玩家生命周期相关的事件：加入、退出、死亡、复活、右键交互和自动召唤状态。
 */
public final class CompanionLifecycleListener implements Listener {

    private final ChimeraCompanionPlugin plugin;
    private final CompanionManager companions;
    private final TriggerService triggers;
    private final DeathTrigger deathTrigger;
    private final IdleTrigger idleTrigger;

    /** 玩家是否希望下次进服自动召唤；只由 /chimera 主动召唤/收回改写。 */
    private final NamespacedKey companionEnabledKey;

    public CompanionLifecycleListener(ChimeraCompanionPlugin plugin, CompanionManager companions,
                                      TriggerService triggers, DeathTrigger deathTrigger,
                                      IdleTrigger idleTrigger) {
        this.plugin = plugin;
        this.companions = companions;
        this.triggers = triggers;
        this.deathTrigger = deathTrigger;
        this.idleTrigger = idleTrigger;
        this.companionEnabledKey = new NamespacedKey(plugin, "companion_enabled");
    }

    /**
     * 插件启动/重载时已有在线玩家，等一个 tick 后按持久标记恢复。
     */
    public void restoreOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            restoreCompanion(player);
        }
    }

    /**
     * 玩家退出时清理其跟宠、触发冷却以及尚未显示的死亡台词，避免残留状态。
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        companions.dismiss(playerId);
        triggers.clear(playerId);
        deathTrigger.clear(playerId);
        idleTrigger.clear(playerId);
        companions.clearTestModel(playerId);
    }

    /**
     * 玩家进服后，如果之前处于召唤状态，延迟一个 tick 恢复跟宠。
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!isCompanionEnabled(player)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                restoreCompanion(player);
            }
        }, 1L);
    }

    private void restoreCompanion(Player player) {
        if (player.isOnline() && isCompanionEnabled(player)) {
            companions.summon(player);
        }
    }

    /**
     * @return 玩家是否希望下次进服自动召唤
     */
    public boolean isCompanionEnabled(Player player) {
        return player.getPersistentDataContainer().has(companionEnabledKey, PersistentDataType.BYTE);
    }

    /**
     * 记录/移除玩家的自动召唤标记；只由 /chimera 召唤或收回时调用。
     */
    public void setCompanionEnabled(Player player, boolean enabled) {
        if (enabled) {
            player.getPersistentDataContainer().set(companionEnabledKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            player.getPersistentDataContainer().remove(companionEnabledKey);
        }
    }

    /**
     * 取消对插件跟宠猫的右键交互，避免玩家让它坐下、喂食或改名。
     * 只匹配已登记的跟宠，服务器上的普通猫不受影响。
     */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (companions.isCompanion(event.getRightClicked())) {
            event.setCancelled(true);
            return;
        }
        // 只处理主手，避免一次右键触发两次歪头。
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (companions.playInteractionTilt(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    /**
     * 玩家死亡时冻结跟宠，并记录复活后要播放的死因台词。
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Cat companion = companions.companionOf(player);
        if (companion == null) {
            return;
        }
        // 让跟宠停在死亡位置：关闭 AI，避免它在玩家死亡期间走动。
        companion.setAware(false);
        deathTrigger.remember(event);
    }

    /**
     * 玩家复活时：解除跟宠冻结、把它传送到复活点，然后播放死亡时记录的台词（如果有）。
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String triggerId = deathTrigger.take(player.getUniqueId());

        Cat companion = companions.companionOf(player);
        if (companion == null) {
            return;
        }
        companion.setAware(true);
        companion.teleport(CompanionManager.behind(event.getRespawnLocation()));
        // 复活可能跨世界，模型跟着重新创建，避免留在旧世界。
        companions.recreateModel(player.getUniqueId(), companion);

        if (triggerId != null) {
            triggers.showTrigger(player, triggerId);
        }
    }
}
