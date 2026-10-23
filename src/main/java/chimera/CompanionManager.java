package chimera;

import chimera.trigger.detector.CatchUpTrigger;
import org.bukkit.Location;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the live companion entity of each online player and coordinates its model and dialogue
 * components.
 *
 * <p>Companions are temporary entities: they are never persisted with the world, they cannot
 * fight, collide or pick up items, and they are removed as soon as they are dismissed or their
 * owner leaves the server. Bubbles are temporary as well and disappear on their own.
 */
public final class CompanionManager {

    private static final double BEHIND_DISTANCE = 1.5;

    private final Map<UUID, Cat> companions = new HashMap<>();
    private final CompanionModel models = new CompanionModel();
    private final CompanionDialogue dialogue = new CompanionDialogue(models);
    private final CatchUpTrigger catchUp;

    public CompanionManager(List<String> messages, int intervalMinSeconds, int intervalMaxSeconds,
                            CatchUpTrigger catchUp) {
        this.catchUp = catchUp;
        reload(messages, intervalMinSeconds, intervalMaxSeconds);
    }

    /**
     * Replaces the dialogue settings, used both at startup and when the config is reloaded.
     */
    public void reload(List<String> messages, int intervalMinSeconds, int intervalMaxSeconds) {
        dialogue.reload(messages, intervalMinSeconds, intervalMaxSeconds);
    }

    /**
     * Spawns a companion right behind the player.
     *
     * @return {@code false} if the player already has a living companion
     */
    public boolean summon(Player player) {
        UUID playerId = player.getUniqueId();
        Cat current = companions.get(playerId);
        if (current != null) {
            if (current.isValid()) {
                return false;
            }
            // The entity was removed by something else, so the stale entry is dropped.
            companions.remove(playerId);
        }

        Cat cat = player.getWorld().spawn(behind(player.getLocation()), Cat.class);
        cat.setOwner(player);
        cat.setSitting(false);
        cat.setInvisible(true);
        cat.setPersistent(false);
        cat.setInvulnerable(true);
        cat.setCollidable(false);
        cat.setSilent(true);
        cat.setCanPickupItems(false);

        companions.put(playerId, cat);
        models.create(playerId, cat);
        dialogue.start(playerId);
        catchUp.clear(playerId);
        return true;
    }

    /**
     * Removes the companion owned by the given player.
     *
     * @return {@code false} if the player had no tracked companion
     */
    public boolean dismiss(UUID playerId) {
        dialogue.stop(playerId);
        models.clear(playerId);
        catchUp.clear(playerId);
        Cat cat = companions.remove(playerId);
        if (cat == null) {
            return false;
        }
        cat.remove();
        return true;
    }

    /**
     * Removes every tracked companion and bubble, used when the plugin shuts down.
     */
    public void dismissAll() {
        for (Cat cat : companions.values()) {
            cat.remove();
        }
        companions.clear();
        models.clearAll();
        dialogue.clearAll();
        catchUp.clearAll();
    }

    /**
     * Shows a bubble with the given text above the player's companion.
     *
     * @return {@code false} if the player has no companion to put the bubble above
     */
    public boolean showBubble(Player player, String text) {
        return dialogue.show(player, text);
    }

    /**
     * Moves the player's bubble along with its companion model and removes it once it has expired.
     */
    public void tickBubble(Player player) {
        dialogue.tickBubble(player);
    }

    /**
     * Removes the player's bubble, if one is showing.
     */
    public void clearBubble(UUID playerId) {
        dialogue.clearBubble(playerId);
    }

    /**
     * 玩家右键插件管理的 Interaction 时播放声音并触发一次歪头。
     *
     * @return 是否命中了本插件的 Interaction
     */
    public boolean playInteractionTilt(Entity entity) {
        UUID playerId = models.interactionOwner(entity);
        if (playerId == null) {
            return false;
        }
        dialogue.playSound(entity.getLocation());
        models.tiltForClick(playerId);
        return true;
    }

    /**
     * Makes the player's companion say a random configured line once its own timer has run out,
     * then schedules the next line.
     */
    public void tickDialogue(Player player) {
        dialogue.tickDialogue(player);
    }

    /**
     * 移除玩家对应的全部模型部件。
     */
    public void clearModel(UUID playerId) {
        models.clear(playerId);
    }

    /**
     * 在猫当前所在世界重新创建模型，用于跨世界传送后替换旧世界的模型。
     */
    public void recreateModel(UUID playerId, Cat cat) {
        models.recreate(playerId, cat);
    }

    /**
     * 让全部模型部件跟随猫的位置和水平朝向。
     */
    public void tickModel(UUID playerId, Cat cat) {
        models.tick(playerId, cat);
    }

    /**
     * 在玩家面前生成一个独立的 ItemDisplay，用于验证 {@code chimera:companion} 模型。
     * 重复执行会先移除上一次生成的测试模型。
     */
    public void showTestModel(Player player, float scale, float yaw) {
        models.showTestModel(player, scale, yaw);
    }

    /**
     * 玩家退出时移除其测试模型。
     */
    public void clearTestModel(UUID playerId) {
        models.clearTestModel(playerId);
    }

    /**
     * 移除全部测试模型，用于插件关闭。
     */
    public void clearTestModels() {
        models.clearTestModels();
    }

    /**
     * @return the companion owned by the player, or {@code null} if it has none
     */
    public Cat companionOf(Player player) {
        return companions.get(player.getUniqueId());
    }

    /**
     * 判断实体是否是插件创建的跟宠猫，用于只拦截这些猫的交互。
     */
    public boolean isCompanion(Entity entity) {
        for (Cat cat : companions.values()) {
            if (cat.getUniqueId().equals(entity.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The point 1.5 blocks behind the given player location, used as both the summon spot and the
     * follow target. The location is not modified.
     */
    static Location behind(Location playerLocation) {
        Location target = playerLocation.clone();
        return target.subtract(target.getDirection().setY(0).normalize().multiply(BEHIND_DISTANCE));
    }
}
