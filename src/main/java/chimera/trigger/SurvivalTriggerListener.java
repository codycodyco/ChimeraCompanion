package chimera.trigger;

import chimera.trigger.detector.EatingTrigger;
import chimera.trigger.detector.MiningTrigger;
import chimera.trigger.detector.ToolBreakTrigger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

/**
 * 接收生存类 Bukkit 事件，并交给对应的检测器判断是否触发。
 *
 * <p>触发条件本身在 {@code detector} 包中，这里只负责事件与触发 ID 的对应关系。
 */
public final class SurvivalTriggerListener implements Listener {

    private final TriggerService triggers;
    private final MiningTrigger mining = new MiningTrigger();
    private final ToolBreakTrigger toolBreak = new ToolBreakTrigger();
    private final EatingTrigger eating = new EatingTrigger();

    public SurvivalTriggerListener(TriggerService triggers) {
        this.triggers = triggers;
    }

    /**
     * {@code MONITOR} 优先级让我们在其他插件处理完之后再判断；
     * {@code ignoreCancelled} 保证被领地等插件取消的破坏不会触发。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (mining.matches(event)) {
            triggers.showTrigger(event.getPlayer(), "mining.valuable");
        }
    }

    @EventHandler
    public void onItemBreak(PlayerItemBreakEvent event) {
        if (toolBreak.matches(event)) {
            triggers.showTrigger(event.getPlayer(), "survival.tool_break");
        }
    }

    /**
     * {@code ignoreCancelled} 保证被其他插件取消的进食不会触发。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        if (eating.matches(event)) {
            triggers.showTrigger(event.getPlayer(), "survival.eat");
        }
    }
}
