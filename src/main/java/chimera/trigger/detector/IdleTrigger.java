package chimera.trigger.detector;

import chimera.CompanionManager;
import chimera.trigger.TriggerService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 判定目标：玩家是否已经连续挂机 5 分钟。
 *
 * <p>每 20 秒检查一次。只有同时满足「有已召唤的跟宠」和「空闲时长达到 5 分钟」才会尝试触发；
 * 同一次连续挂机只提醒一次，触发失败（例如被全局冷却拒绝）不会被记为已提醒，下一轮继续尝试。
 * 玩家恢复活动或失去跟宠后移除标记，使下一次挂机能再次提醒。
 *
 * <p>只读取 {@link Player#getIdleDuration()}，不调用 {@code resetIdleDuration()}，
 * 因此不会干扰服务端原本的挂机踢出机制。
 */
public final class IdleTrigger {

    private static final Duration IDLE_THRESHOLD = Duration.ofMinutes(5);

    private final CompanionManager companions;
    private final TriggerService triggers;

    /** 已在本轮挂机中成功提醒过的玩家；恢复活动或失去跟宠后移除。 */
    private final Set<UUID> reminded = new HashSet<>();

    public IdleTrigger(CompanionManager companions, TriggerService triggers) {
        this.companions = companions;
        this.triggers = triggers;
    }

    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (companions.companionOf(player) == null
                    || player.getIdleDuration().compareTo(IDLE_THRESHOLD) < 0) {
                reminded.remove(playerId);
                continue;
            }
            if (reminded.contains(playerId)) {
                continue;
            }
            if (triggers.showTrigger(player, "survival.idle")) {
                reminded.add(playerId);
            }
        }
    }

    public void clear(UUID playerId) {
        reminded.remove(playerId);
    }

    public void clearAll() {
        reminded.clear();
    }
}
