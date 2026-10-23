package chimera.trigger.detector;

import org.bukkit.event.player.PlayerItemConsumeEvent;

/**
 * 判定目标：玩家是否完成了一次「吃东西」。
 *
 * <p>用 {@code Material#isEdible()} 判断是否为原版食物：药水和牛奶虽然有饮用动画，
 * 但没有食物属性，返回 false，因此不会触发。
 */
public final class EatingTrigger {

    public boolean matches(PlayerItemConsumeEvent event) {
        return event.getItem().getType().isEdible();
    }
}
