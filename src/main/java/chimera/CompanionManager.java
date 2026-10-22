package chimera;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns the live companion entity of each online player, the bubble displayed above it and the
 * timing of its random lines and sounds.
 *
 * <p>Companions are temporary entities: they are never persisted with the world, they cannot
 * fight, collide or pick up items, and they are removed as soon as they are dismissed or their
 * owner leaves the server. Bubbles are temporary as well and disappear on their own.
 */
public final class CompanionManager {

    private static final double BEHIND_DISTANCE = 1.5;

    private static final int BUBBLE_TICKS = 80;
    private static final double BUBBLE_HEIGHT = 1.2;

    private static final int SOUND_INTERVAL_MIN_SECONDS = 45;
    private static final int SOUND_INTERVAL_MAX_SECONDS = 120;
    private static final float SOUND_VOLUME = 0.6F;
    private static final float SOUND_PITCH = 1.0F;

    /** 连续追赶达到该 tick 数（15 秒）后尝试触发 companion.catch_up。 */
    private static final int CHASE_TRIGGER_TICKS = 15 * 20;

    /** 绑定在猫身上的模型缩放；猫高约 0.7 格，模型原始高约 1.13 格。 */
    private static final float MODEL_SCALE = 0.7F;
    /** 模型朝向相对猫的水平朝向的固定偏移（度），实测模型正面方向后可调。 */
    private static final float MODEL_YAW_OFFSET = 0.0F;
    /** 模型相对猫脚底的高度偏移。 */
    private static final double MODEL_Y_OFFSET = 0.0;
    /** 模型包围盒中心的水平坐标（格），用于把模型对齐到猫的位置。 */
    private static final float MODEL_CENTER_X = 0.5F;
    private static final float MODEL_CENTER_Z = 0.632F;
    /** 模型包围盒中心的高度（格），仅静态测试命令用来让模型居中。 */
    static final float MODEL_CENTER_Y = 0.564F;

    private List<String> messages;
    private int intervalMinSeconds;
    private int intervalMaxSeconds;

    private final Map<UUID, Cat> companions = new HashMap<>();
    private final Map<UUID, Bubble> bubbles = new HashMap<>();
    private final Map<UUID, Talk> talks = new HashMap<>();
    private final Map<UUID, Integer> nextSoundTicks = new HashMap<>();
    private final Map<UUID, Chase> chases = new HashMap<>();
    private final Map<UUID, ItemDisplay> models = new HashMap<>();

    private record Bubble(TextDisplay display, int expiresAtTick) {
    }

    private record Talk(int nextSpeakTick, int lastIndex) {
    }

    /** 一轮追赶：startTick 为开始追赶的 tick，attempted 表示本轮已经尝试过触发。 */
    private record Chase(int startTick, boolean attempted) {
    }

    public CompanionManager(List<String> messages, int intervalMinSeconds, int intervalMaxSeconds) {
        reload(messages, intervalMinSeconds, intervalMaxSeconds);
    }

    /**
     * Replaces the dialogue settings, used both at startup and when the config is reloaded.
     */
    public void reload(List<String> messages, int intervalMinSeconds, int intervalMaxSeconds) {
        this.messages = messages;
        // Keep a usable range even when the config was edited into an invalid state.
        this.intervalMinSeconds = Math.max(1, intervalMinSeconds);
        this.intervalMaxSeconds = Math.max(this.intervalMinSeconds, intervalMaxSeconds);
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
        cat.setPersistent(false);
        cat.setInvulnerable(true);
        cat.setCollidable(false);
        cat.setSilent(true);
        cat.setCanPickupItems(false);

        companions.put(playerId, cat);
        createModel(playerId, cat);
        talks.put(playerId, new Talk(scheduleNext(), -1));
        nextSoundTicks.put(playerId, scheduleNextSound());
        chases.remove(playerId);
        return true;
    }

    /**
     * Removes the companion owned by the given player.
     *
     * @return {@code false} if the player had no tracked companion
     */
    public boolean dismiss(UUID playerId) {
        clearBubble(playerId);
        clearModel(playerId);
        talks.remove(playerId);
        nextSoundTicks.remove(playerId);
        chases.remove(playerId);
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
        for (ItemDisplay model : models.values()) {
            model.remove();
        }
        models.clear();
        for (Bubble bubble : bubbles.values()) {
            bubble.display().remove();
        }
        bubbles.clear();
        talks.clear();
        nextSoundTicks.clear();
        chases.clear();
    }

    /**
     * Shows a bubble with the given text above the player's companion, and mirrors the same line
     * into the owner's chat. Showing it again reuses the existing display instead of stacking a
     * second one.
     *
     * @return {@code false} if the player has no companion to put the bubble above
     */
    public boolean showBubble(Player player, String text) {
        Cat companion = companionOf(player);
        if (companion == null) {
            return false;
        }
        playSound(companion);
        // 头顶气泡和聊天栏使用同一句台词，不重新随机。
        player.sendMessage(Component.text("[说怪话] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(text, NamedTextColor.WHITE)));

        UUID playerId = player.getUniqueId();
        int expiresAtTick = Bukkit.getCurrentTick() + BUBBLE_TICKS;
        Bubble current = bubbles.get(playerId);
        if (current != null) {
            current.display().text(Component.text(text));
            current.display().teleport(bubbleLocation(companion));
            bubbles.put(playerId, new Bubble(current.display(), expiresAtTick));
            return true;
        }

        TextDisplay display = companion.getWorld().spawn(bubbleLocation(companion), TextDisplay.class, textDisplay -> {
            textDisplay.text(Component.text(text));
            textDisplay.setBillboard(Display.Billboard.CENTER);
            textDisplay.setBackgroundColor(Color.fromARGB(0x80000000));
            // Let the client interpolate between follow task updates so the bubble does not jump.
            textDisplay.setTeleportDuration((int) FollowTask.PERIOD_TICKS);
            textDisplay.setPersistent(false);
        });
        bubbles.put(playerId, new Bubble(display, expiresAtTick));
        return true;
    }

    /**
     * Moves the player's bubble along with its companion and removes it once it has expired.
     */
    public void tickBubble(Player player, Cat companion) {
        UUID playerId = player.getUniqueId();
        Bubble bubble = bubbles.get(playerId);
        if (bubble == null) {
            return;
        }
        if (Bukkit.getCurrentTick() >= bubble.expiresAtTick()) {
            bubble.display().remove();
            bubbles.remove(playerId);
            return;
        }
        bubble.display().teleport(bubbleLocation(companion));
    }

    /**
     * Makes the player's companion say a random configured line once its own timer has run out,
     * then schedules the next line.
     */
    public void tickDialogue(Player player) {
        UUID playerId = player.getUniqueId();
        Talk talk = talks.get(playerId);
        if (talk == null || Bukkit.getCurrentTick() < talk.nextSpeakTick()) {
            return;
        }
        if (messages.isEmpty()) {
            return;
        }

        int index = pickIndex(talk.lastIndex());
        showBubble(player, messages.get(index));
        talks.put(playerId, new Talk(scheduleNext(), index));
    }

    /**
     * Picks a random message index, stepping past the previous one instead of repeating it.
     */
    private int pickIndex(int lastIndex) {
        int index = ThreadLocalRandom.current().nextInt(messages.size());
        if (messages.size() > 1 && index == lastIndex) {
            index = (index + 1) % messages.size();
        }
        return index;
    }

    private int scheduleNext() {
        int span = intervalMaxSeconds - intervalMinSeconds;
        int seconds = intervalMinSeconds + ThreadLocalRandom.current().nextInt(span + 1);
        return Bukkit.getCurrentTick() + seconds * 20;
    }

    /**
     * Plays the allay sound once the companion's timer has run out, then schedules the next play.
     */
    public void tickSound(Player player, Cat companion) {
        UUID playerId = player.getUniqueId();
        Integer nextTick = nextSoundTicks.get(playerId);
        if (nextTick == null || Bukkit.getCurrentTick() < nextTick) {
            return;
        }
        playSound(companion);
        nextSoundTicks.put(playerId, scheduleNextSound());
    }

    private int scheduleNextSound() {
        int span = SOUND_INTERVAL_MAX_SECONDS - SOUND_INTERVAL_MIN_SECONDS;
        int seconds = SOUND_INTERVAL_MIN_SECONDS + ThreadLocalRandom.current().nextInt(span + 1);
        return Bukkit.getCurrentTick() + seconds * 20;
    }

    /**
     * Plays the sound where the companion currently is, so nearby players hear it.
     */
    private static void playSound(Cat companion) {
        companion.getWorld().playSound(companion.getLocation(),
                Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, SOUND_VOLUME, SOUND_PITCH);
    }

    /**
     * 更新玩家的追赶状态：第一次调用只记录起点，之后判断是否已连续追赶 15 秒。
     *
     * @return 本轮追赶达到阈值且尚未尝试过触发时为 {@code true}
     */
    public boolean tickChase(UUID playerId, int nowTick) {
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

    /**
     * 清除追赶状态：追上主人、停止追赶、完成传送或跟宠移除时调用。
     */
    public void clearChase(UUID playerId) {
        chases.remove(playerId);
    }

    /**
     * 为猫创建模型实体，位置与朝向立即与猫对齐。
     */
    private void createModel(UUID playerId, Cat cat) {
        Location at = cat.getLocation().add(0, MODEL_Y_OFFSET, 0);
        ItemDisplay display = cat.getWorld().spawn(at, ItemDisplay.class);
        display.setItemStack(companionModelItem());
        display.setPersistent(false);
        display.setTeleportDuration((int) FollowTask.PERIOD_TICKS);
        display.setTransformation(modelTransformation(MODEL_SCALE, cat.getLocation().getYaw()));
        models.put(playerId, display);
    }

    /**
     * 移除玩家对应的模型实体。
     */
    public void clearModel(UUID playerId) {
        ItemDisplay display = models.remove(playerId);
        if (display != null) {
            display.remove();
        }
    }

    /**
     * 在猫当前所在世界重新创建模型，用于跨世界传送后替换旧世界的模型。
     */
    public void recreateModel(UUID playerId, Cat cat) {
        clearModel(playerId);
        createModel(playerId, cat);
    }

    /**
     * 让模型跟随猫的位置和水平朝向；每 10 tick 一次的传送由客户端插值成平滑移动。
     */
    public void tickModel(UUID playerId, Cat cat) {
        ItemDisplay display = models.get(playerId);
        if (display == null) {
            return;
        }
        display.teleport(cat.getLocation().add(0, MODEL_Y_OFFSET, 0));
        display.setTransformation(modelTransformation(MODEL_SCALE, cat.getLocation().getYaw()));
    }

    /**
     * 以模型包围盒中心为基准，构造「绕 Y 轴旋转、缩放、平移到显示实体原点」的变换，
     * 使模型脚底落在显示实体位置上。
     */
    static Transformation modelTransformation(float scale, float yaw) {
        float radians = (float) Math.toRadians(yaw + MODEL_YAW_OFFSET);
        Vector3f translation = new Vector3f(-MODEL_CENTER_X, 0.0F, -MODEL_CENTER_Z)
                .mul(scale)
                .rotateY(radians);
        return new Transformation(
                translation,
                new Quaternionf().rotateY(radians),
                new Vector3f(scale, scale, scale),
                new Quaternionf());
    }

    /**
     * 构造带 {@code chimera:companion} 物品模型组件的物品。
     */
    static ItemStack companionModelItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("chimera", "companion"));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Removes the player's bubble, if one is showing.
     */
    public void clearBubble(UUID playerId) {
        Bubble bubble = bubbles.remove(playerId);
        if (bubble != null) {
            bubble.display().remove();
        }
    }

    /**
     * @return the companion owned by the player, or {@code null} if it has none
     */
    public Cat companionOf(Player player) {
        return companions.get(player.getUniqueId());
    }

    private static Location bubbleLocation(Cat companion) {
        return companion.getLocation().add(0, BUBBLE_HEIGHT, 0);
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
