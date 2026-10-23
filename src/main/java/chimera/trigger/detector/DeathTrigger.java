package chimera.trigger.detector;

import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Enemy;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 判定目标：玩家死亡，并按死因记录「复活后要说的话」。
 *
 * <p>当前识别的死因有四种：
 * <ul>
 *   <li>{@code death.fall}：致死伤害类型为 {@code FALL}（摔死）</li>
 *   <li>{@code death.lava}：致死伤害类型为 {@code LAVA}（岩浆，不含离开岩浆后的持续燃烧）</li>
 *   <li>{@code death.drown}：致死伤害类型为 {@code DROWN}（溺水）</li>
 *   <li>{@code death.mob}：致死伤害的归因实体是 {@link Enemy}（僵尸、骷髅、苦力怕等怪物；
 *       玩家击杀、动物击杀都不算）</li>
 * </ul>
 * 台词不在死亡界面显示，而是暂存到复活事件里再播放。
 */
public final class DeathTrigger {

    private final Map<UUID, String> pending = new HashMap<>();

    public void remember(PlayerDeathEvent event) {
        String triggerId = classify(event.getDamageSource());
        if (triggerId != null) {
            pending.put(event.getEntity().getUniqueId(), triggerId);
        }
    }

    /**
     * @return 该玩家暂存的死亡台词 ID，没有则返回 {@code null}
     */
    public String take(UUID playerId) {
        return pending.remove(playerId);
    }

    public void clear(UUID playerId) {
        pending.remove(playerId);
    }

    public void clearAll() {
        pending.clear();
    }

    private static String classify(DamageSource source) {
        DamageType damageType = source.getDamageType();
        if (damageType == DamageType.FALL) {
            // 摔死
            return "death.fall";
        }
        if (damageType == DamageType.LAVA) {
            // 岩浆烧死
            return "death.lava";
        }
        if (damageType == DamageType.DROWN) {
            // 溺水
            return "death.drown";
        }
        if (source.getCausingEntity() instanceof Enemy) {
            // 被怪物击杀：归因实体必须是 Enemy，玩家击杀或环境伤害不算
            return "death.mob";
        }
        return null;
    }
}
