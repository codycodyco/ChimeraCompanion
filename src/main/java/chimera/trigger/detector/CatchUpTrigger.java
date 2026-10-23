package chimera.trigger.detector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 判定目标：跟宠是否已经连续追赶主人 15 秒。
 *
 * <p>第一次调用只记录起点；达到阈值后本轮只返回一次 {@code true}。
 * 追上主人、停止追赶、完成传送或跟宠移除时清除状态。
 */
public final class CatchUpTrigger {

    /** 连续追赶达到该 tick 数（15 秒）后尝试触发 companion.catch_up。 */
    private static final int CHASE_TRIGGER_TICKS = 15 * 20;

    private final Map<UUID, Chase> chases = new HashMap<>();

    /** 一轮追赶：startTick 为开始追赶的 tick，attempted 表示本轮已经尝试过触发。 */
    private record Chase(int startTick, boolean attempted) {
    }

    /**
     * @return 本轮追赶达到阈值且尚未尝试过触发时为 {@code true}
     */
    public boolean tick(UUID playerId, int nowTick) {
        Chase chase = chases.get(playerId);
        if (chase == null) {
            chases.put(playerId, new Chase(nowTick, false));
            return false;
        }
        if (chase.attempted() || nowTick - chase.startTick() < CHASE_TRIGGER_TICKS) {
            return false;
        }
        chases.put(playerId, new Chase(chase.startTick(), true));
        return true;
    }

    public void clear(UUID playerId) {
        chases.remove(playerId);
    }

    public void clearAll() {
        chases.clear();
    }
}
