package chimera;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
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


    /** 连续追赶达到该 tick 数（15 秒）后尝试触发 companion.catch_up。 */
    private static final int CHASE_TRIGGER_TICKS = 15 * 20;

    /** 绑定在猫身上的模型缩放；猫高约 0.7 格，模型原始高约 1.13 格。 */
    private static final float MODEL_SCALE = 0.7F;
    /**
     * 模型正面相对 Minecraft yaw 0 的固定偏移（度）。
     *
     * <p>实测当前模型在本地坐标下正面朝 +Z，与 Minecraft yaw 0 一致，因此偏移为 0。
     * 若以后换模型方向不同，只调这个常量即可（180 为完全反向）。
     */
    private static final float MODEL_YAW_OFFSET = 0.0F;
    /**
     * 模型相对猫脚底的高度偏移。
     *
     * <p>ItemDisplay 以「模型中心」作为变换枢轴，而模型的脚底在本地 y=0，
     * 两者相差约 0.564 格；缩放 0.7 后脚底落在显示位置下方约 0.4 格。
     * 不补这段高度，模型就会沉入地面。
     */
    private static final double MODEL_Y_OFFSET = 0.4;
    /** 模型包围盒中心的高度（格），仅静态测试命令用来让模型居中。 */
    static final float MODEL_CENTER_Y = 0.564F;

    /** 组成完整模型的六个部件；它们共用同一套根位置、缩放和变换。body 同时作为气泡/音效定位点。 */
    private static final List<String> MODEL_PARTS = List.of(
            "body",
            "head",
            "front_left_leg",
            "front_right_leg",
            "back_left_leg",
            "back_right_leg");

    /** 头部关节原点的 Z 坐标（模型局部坐标，单位格），来自 rig_pivots.json 的 head.pivot_local_blocks。 */
    private static final float HEAD_PIVOT_Z = -0.125F;
    /** 头部相对身体的最大左右旋转角度（度）。 */
    private static final float HEAD_MAX_ANGLE = 15.0F;
    /** 待机张望的固定目标角度，每 HEAD_STEP_TICKS tick 切换一次。 */
    private static final float[] HEAD_LOOK_ANGLES = {0.0F, HEAD_MAX_ANGLE, -HEAD_MAX_ANGLE};
    /** 目标角度切换间隔；留出 10 tick 余量让 10 tick 的插值完整播完再切换。 */
    private static final int HEAD_STEP_TICKS = 20;

    /** 四条腿的最大摆动角度（度）。 */
    private static final float LEG_MAX_ANGLE = 20.0F;
    /** 对角步态相位符号：左前与右后同相，右前与左后反相。顺序同 LEG_PIVOTS。 */
    private static final float[] LEG_PHASE_SIGNS = {1.0F, -1.0F, -1.0F, 1.0F};
    /** 四条腿的关节原点（模型局部坐标，单位格），来自 rig_pivots.json。 */
    private static final float[][] LEG_PIVOTS = {
            {-0.18125F, -0.25F, -0.10625F},   // front_left_leg
            {0.18125F, -0.25F, -0.10625F},    // front_right_leg
            {-0.18125F, -0.2375F, 0.3125F},   // back_left_leg
            {0.18125F, -0.2375F, 0.3125F}};   // back_right_leg
    /** 10 tick 内水平位移超过该值即视为猫在移动（0.1 格 / 10 tick）。 */
    private static final double WALK_EPSILON_SQUARED = 0.01;

    /** 说话歪头的局部 Z 轴角度（度）与持续时间（tick）。 */
    private static final float HEAD_TILT_ANGLE = 12.0F;
    private static final int HEAD_TILT_TICKS = 30;
    /** 右键互动的点击区域尺寸与歪头角度。 */
    private static final float INTERACTION_WIDTH = 0.65F;
    private static final float INTERACTION_HEIGHT = 0.9F;
    private static final float HEAD_CLICK_TILT_ANGLE = 15.0F;
    /** 靠近玩家时的最大左右/俯仰偏转（度）、检测距离（格）与目标更新阈值（十分之一度）。 */
    private static final float HEAD_LOOK_LIMIT = 30.0F;
    private static final float HEAD_PITCH_LIMIT = 25.0F;
    private static final double HEAD_LOOK_DISTANCE = 3.0;
    private static final int HEAD_UPDATE_TENTHS = 30;

    private List<String> messages;
    private int intervalMinSeconds;
    private int intervalMaxSeconds;

    private final Map<UUID, Cat> companions = new HashMap<>();
    private final Map<UUID, Bubble> bubbles = new HashMap<>();
    private final Map<UUID, Talk> talks = new HashMap<>();
    private final Map<UUID, Chase> chases = new HashMap<>();
    private final Map<UUID, List<ItemDisplay>> models = new HashMap<>();
    /** 每只跟宠上一次实际应用的头部目标，避免重复提交相同 Transformation。 */
    private final Map<UUID, HeadPose> headPoses = new HashMap<>();
    /** 每只跟宠当前的歪头动画（结束 tick 与角度），说话和右键共用。 */
    private final Map<UUID, HeadTilt> headTilts = new HashMap<>();
    /** 每只跟宠的右键点击区域。 */
    private final Map<UUID, Interaction> interactions = new HashMap<>();
    /** 每只跟宠上一次实际应用的腿部目标（0 静止、1/2 两个步态相位）。 */
    private final Map<UUID, Integer> legSteps = new HashMap<>();
    /** 每只跟宠上一轮的猫位置，用来判断猫是否真的在移动。 */
    private final Map<UUID, Location> lastLocations = new HashMap<>();

    private record Bubble(TextDisplay display, int expiresAtTick) {
    }

    private record Talk(int nextSpeakTick, int lastIndex) {
    }

    /** 一轮追赶：startTick 为开始追赶的 tick，attempted 表示本轮已经尝试过触发。 */
    private record Chase(int startTick, boolean attempted) {
    }

    /** 一次歪头：结束 tick、角度，以及开始时冻结的张望/俯仰角度。 */
    private record HeadTilt(int endTick, float angle, float lookAngle, float pitchAngle) {
    }

    /** 已应用的头部目标：左右、俯仰与歪头角度的十分之一。 */
    private record HeadPose(int lookTenths, int pitchTenths, int tiltTenths) {
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
        cat.setInvisible(true);
        cat.setPersistent(false);
        cat.setInvulnerable(true);
        cat.setCollidable(false);
        cat.setSilent(true);
        cat.setCanPickupItems(false);

        companions.put(playerId, cat);
        createModel(playerId, cat);
        talks.put(playerId, new Talk(scheduleNext(), -1));
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
        for (List<ItemDisplay> parts : models.values()) {
            for (ItemDisplay display : parts) {
                display.remove();
            }
        }
        for (Interaction interaction : interactions.values()) {
            interaction.remove();
        }
        interactions.clear();
        models.clear();
        headPoses.clear();
        headTilts.clear();
        legSteps.clear();
        lastLocations.clear();
        for (Bubble bubble : bubbles.values()) {
            bubble.display().remove();
        }
        bubbles.clear();
        talks.clear();
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
        List<ItemDisplay> parts = models.get(player.getUniqueId());
        if (parts == null) {
            return false;
        }
        ItemDisplay model = parts.get(0);
        // 说话语音：由 sounds.json 在 19 段中随机，替换原来的悦灵叫声。
        model.getWorld().playSound(model.getLocation(), "chimera:speak",
                SoundCategory.NEUTRAL, 0.8F, 1.0F);
        // 说话时触发一次歪头；随机台词和行为触发都走这里。
        UUID tiltPlayerId = player.getUniqueId();
        headTilts.put(tiltPlayerId, new HeadTilt(Bukkit.getCurrentTick() + HEAD_TILT_TICKS,
                HEAD_TILT_ANGLE, currentLookAngle(tiltPlayerId), currentPitch(tiltPlayerId)));
        // 头顶气泡和聊天栏使用同一句台词，不重新随机。
        player.sendMessage(Component.text("[说怪话] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(text, NamedTextColor.WHITE)));

        UUID playerId = player.getUniqueId();
        int expiresAtTick = Bukkit.getCurrentTick() + BUBBLE_TICKS;
        Bubble current = bubbles.get(playerId);
        if (current != null) {
            current.display().text(Component.text(text));
            current.display().teleport(bubbleLocation(model));
            bubbles.put(playerId, new Bubble(current.display(), expiresAtTick));
            return true;
        }

        TextDisplay display = model.getWorld().spawn(bubbleLocation(model), TextDisplay.class, textDisplay -> {
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
     * Moves the player's bubble along with its companion model and removes it once it has expired.
     */
    public void tickBubble(Player player) {
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
        bubble.display().teleport(bubbleLocation(models.get(playerId).get(0)));
    }

    /**
     * 玩家右键插件管理的 Interaction 时触发一次歪头。
     *
     * @return 是否命中了本插件的 Interaction
     */
    public boolean playInteractionTilt(Entity entity) {
        for (Map.Entry<UUID, Interaction> entry : interactions.entrySet()) {
            if (entry.getValue().getUniqueId().equals(entity.getUniqueId())) {
                UUID playerId = entry.getKey();
                entity.getWorld().playSound(entity.getLocation(), "chimera:speak",
                        SoundCategory.NEUTRAL, 0.8F, 1.0F);
                headTilts.put(playerId, new HeadTilt(Bukkit.getCurrentTick() + HEAD_TILT_TICKS,
                        HEAD_CLICK_TILT_ANGLE, currentLookAngle(playerId), currentPitch(playerId)));
                return true;
            }
        }
        return false;
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
        headPoses.remove(playerId);
        headTilts.remove(playerId);
        legSteps.put(playerId, 0);
        lastLocations.remove(playerId);
        Location at = cat.getLocation().add(0, MODEL_Y_OFFSET, 0);
        // 根朝向交给实体自身的 yaw（teleport 插值），Transformation 只保留缩放与关节局部旋转。
        at.setYaw(cat.getBodyYaw());
        at.setPitch(0.0F);
        Transformation base = baseTransformation(MODEL_SCALE);
        List<ItemDisplay> parts = new ArrayList<>(MODEL_PARTS.size());
        for (String part : MODEL_PARTS) {
            ItemDisplay display = cat.getWorld().spawn(at, ItemDisplay.class);
            display.setItemStack(modelItem(part));
            display.setBillboard(Display.Billboard.FIXED);
            display.setPersistent(false);
            display.setTeleportDuration((int) FollowTask.PERIOD_TICKS);
            display.setTransformation(base);
            if ("head".equals(part)) {
                // 只有头部的局部 Rotation 需要插值；身体/四肢的 Transformation 之后不再改写。
                display.setInterpolationDelay(0);
                display.setInterpolationDuration((int) FollowTask.PERIOD_TICKS);
            }
            parts.add(display);
        }
        models.put(playerId, parts);
        Interaction interaction = cat.getWorld().spawn(at, Interaction.class, entity -> {
            entity.setInteractionWidth(INTERACTION_WIDTH);
            entity.setInteractionHeight(INTERACTION_HEIGHT);
            entity.setResponsive(true);
            entity.setPersistent(false);
        });
        interactions.put(playerId, interaction);
    }

    /**
     * 移除玩家对应的全部模型部件。
     */
    public void clearModel(UUID playerId) {
        headPoses.remove(playerId);
        headTilts.remove(playerId);
        legSteps.remove(playerId);
        lastLocations.remove(playerId);
        Interaction interaction = interactions.remove(playerId);
        if (interaction != null) {
            interaction.remove();
        }
        List<ItemDisplay> parts = models.remove(playerId);
        if (parts == null) {
            return;
        }
        for (ItemDisplay display : parts) {
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
     * 让全部模型部件跟随猫的位置和水平朝向；每 10 tick 一次的传送由客户端插值成平滑移动。
     */
    public void tickModel(UUID playerId, Cat cat) {
        List<ItemDisplay> parts = models.get(playerId);
        if (parts == null) {
            return;
        }
        // 根位置和根朝向都通过 teleport 同步（entity yaw + teleportDuration 插值）。
        Location catLocation = cat.getLocation();
        Location previous = lastLocations.put(playerId, catLocation);
        boolean moving = previous != null
                && previous.getWorld().equals(catLocation.getWorld())
                && previous.distanceSquared(catLocation) > WALK_EPSILON_SQUARED;
        Location target = catLocation.clone().add(0, MODEL_Y_OFFSET, 0);
        target.setYaw(cat.getBodyYaw());
        target.setPitch(0.0F);
        Interaction interaction = interactions.get(playerId);
        if (interaction != null) {
            interaction.teleport(target);
        }
        // 优先级：歪头 > 注视 3 格内最近的玩家 > 随机张望。
        long tick = Bukkit.getCurrentTick();
        HeadTilt tilt = headTilts.get(playerId);
        boolean tilting = tilt != null && tick < tilt.endTick();
        float tiltAngle = tilting ? tilt.angle() : 0.0F;
        float lookAngle;
        float pitchAngle;
        if (tilting) {
            lookAngle = tilt.lookAngle();
            pitchAngle = tilt.pitchAngle();
        } else {
            Player lookedAt = nearestPlayer(cat);
            if (lookedAt != null) {
                lookAngle = lookAngleTo(cat, lookedAt);
                pitchAngle = pitchAngleTo(cat, lookedAt);
            } else {
                int lookStep = (int) ((tick / HEAD_STEP_TICKS) % HEAD_LOOK_ANGLES.length);
                lookAngle = HEAD_LOOK_ANGLES[lookStep];
                pitchAngle = 0.0F;
            }
        }
        HeadPose pose = new HeadPose(Math.round(lookAngle * 10.0F),
                Math.round(pitchAngle * 10.0F), Math.round(tiltAngle * 10.0F));
        HeadPose lastPose = headPoses.get(playerId);
        boolean headPoseChanged = lastPose == null
                || pose.tiltTenths() != lastPose.tiltTenths()
                || Math.abs(pose.lookTenths() - lastPose.lookTenths()) >= HEAD_UPDATE_TENTHS
                || Math.abs(pose.pitchTenths() - lastPose.pitchTenths()) >= HEAD_UPDATE_TENTHS;
        if (headPoseChanged) {
            headPoses.put(playerId, pose);
        }
        // 行走时按对角步态在两个相位之间切换；停下时目标回到 0。
        int legStep = moving ? (int) ((Bukkit.getCurrentTick() / FollowTask.PERIOD_TICKS) % 2) + 1 : 0;
        Integer lastLegStep = legSteps.get(playerId);
        boolean legStepChanged = lastLegStep == null || lastLegStep != legStep;
        if (legStepChanged) {
            legSteps.put(playerId, legStep);
        }
        Transformation head = headTransformation(MODEL_SCALE, lookAngle, pitchAngle, tiltAngle);
        for (int i = 0; i < parts.size(); i++) {
            ItemDisplay display = parts.get(i);
            display.teleport(target);
            String part = MODEL_PARTS.get(i);
            if (headPoseChanged && "head".equals(part)) {
                display.setTransformation(head);
                display.setInterpolationDelay(0);
                display.setInterpolationDuration((int) FollowTask.PERIOD_TICKS);
            }
            int leg = legIndexOf(part);
            if (legStepChanged && leg >= 0) {
                float angle = 0.0F;
                if (legStep != 0) {
                    angle = LEG_PHASE_SIGNS[leg] * LEG_MAX_ANGLE * (legStep == 1 ? 1.0F : -1.0F);
                }
                display.setTransformation(legTransformation(MODEL_SCALE, LEG_PIVOTS[leg], angle));
                display.setInterpolationDelay(0);
                display.setInterpolationDuration((int) FollowTask.PERIOD_TICKS);
            }
        }
    }

    /**
     * 以模型包围盒中心为基准，构造「绕 Y 轴旋转、缩放、平移到显示实体原点」的变换，
     * 使模型脚底落在显示实体位置上。
     */
    static Transformation modelTransformation(float scale, float yaw) {
        // Minecraft yaw 顺时针增大，而 JOML rotateY 逆时针增大，因此用 (offset - yaw)；
        // 平移保持为 0：ItemDisplay 的枢轴本来就是模型中心，不能再额外平移。
        float radians = (float) Math.toRadians(MODEL_YAW_OFFSET - yaw);
        return new Transformation(
                new Vector3f(),
                new Quaternionf().rotateY(radians),
                new Vector3f(scale, scale, scale),
                new Quaternionf());
    }

    /**
     * 在基础姿态上增加头部绕关节原点的局部 Y 轴旋转。
     *
     * <p>Display 的变换顺序是 T · R_left · S · R_right。要让模型局部坐标中的点绕
     * p 旋转 R，右旋转用 R，平移取 R_left · S · (p - R·p)；这样 θ = 0 时与原基础
     * 变换完全一致，也不会在上一帧的变换上累积旋转。
     */
    static Transformation headTransformation(float scale, float headAngle, float pitchAngle, float tiltAngle) {
        // yaw 用 Minecraft 的相对角度（顺时针，JOML 逆时针所以取负）；
        // 俯仰为正表示抬头，JOML 的 rotateX 正角是低头，因此也取负。
        Quaternionf headRotation = new Quaternionf()
                .rotateY((float) Math.toRadians(-headAngle))
                .rotateX((float) Math.toRadians(-pitchAngle))
                .rotateZ((float) Math.toRadians(tiltAngle));
        Vector3f pivot = new Vector3f(0.0F, 0.0F, HEAD_PIVOT_Z);
        Vector3f translation = pivot.sub(headRotation.transform(new Vector3f(pivot))).mul(scale);
        return new Transformation(
                translation,
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                headRotation);
    }

    /**
     * 让一条腿绕自身关节原点做局部 X 轴旋转。
     * 变换只包含局部旋转与缩放，根朝向由实体 yaw 负责。
     */
    static Transformation legTransformation(float scale, float[] pivot, float angle) {
        Quaternionf rotation = new Quaternionf().rotateX((float) Math.toRadians(angle));
        Vector3f p = new Vector3f(pivot[0], pivot[1], pivot[2]);
        Vector3f translation = p.sub(rotation.transform(new Vector3f(p))).mul(scale);
        return new Transformation(
                translation,
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                rotation);
    }

    /**
     * @return 部件在 LEG_PIVOTS 中的下标，非腿部部件返回 {@code -1}
     */
    private static int legIndexOf(String part) {
        return switch (part) {
            case "front_left_leg" -> 0;
            case "front_right_leg" -> 1;
            case "back_left_leg" -> 2;
            case "back_right_leg" -> 3;
            default -> -1;
        };
    }

    /**
     * 所有部件共用的静态基础变换：只有缩放，没有旋转和平移。
     * 根朝向由实体 yaw 负责，因此这个变换在创建后不需要再更新。
     */
    private static Transformation baseTransformation(float scale) {
        return new Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                new Quaternionf());
    }

    /**
     * 构造带 {@code chimera:companion} 物品模型组件的物品。
     */
    static ItemStack companionModelItem() {
        return modelItem("companion");
    }

    /**
     * 构造带指定 {@code chimera:<part>} 物品模型组件的物品。
     */
    private static ItemStack modelItem(String part) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("chimera", part));
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

    private static Location bubbleLocation(ItemDisplay model) {
        return model.getLocation().add(0, BUBBLE_HEIGHT, 0);
    }

    /**
     * 找出猫 3 格范围内最近的在线玩家。
     */
    private static Player nearestPlayer(Cat cat) {
        Location catLocation = cat.getLocation();
        double maxSquared = HEAD_LOOK_DISTANCE * HEAD_LOOK_DISTANCE;
        double nearestSquared = Double.MAX_VALUE;
        Player nearest = null;
        for (Entity entity : cat.getWorld().getNearbyEntities(catLocation,
                HEAD_LOOK_DISTANCE, HEAD_LOOK_DISTANCE, HEAD_LOOK_DISTANCE)) {
            if (!(entity instanceof Player player)) {
                continue;
            }
            double distanceSquared = player.getLocation().distanceSquared(catLocation);
            if (distanceSquared <= maxSquared && distanceSquared < nearestSquared) {
                nearestSquared = distanceSquared;
                nearest = player;
            }
        }
        return nearest;
    }

    /**
     * 玩家相对猫身体朝向的头部 Y 轴角度，限制在 ±30°。
     */
    private static float lookAngleTo(Cat cat, Player player) {
        double dx = player.getX() - cat.getX();
        double dz = player.getZ() - cat.getZ();
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float relative = normalizeDegrees(targetYaw - cat.getBodyYaw());
        return Math.max(-HEAD_LOOK_LIMIT, Math.min(HEAD_LOOK_LIMIT, relative));
    }

    /**
     * 玩家眼睛相对头部关节的俯仰角，抬头为正，限制在 ±25°。
     */
    private static float pitchAngleTo(Cat cat, Player player) {
        Location origin = headOrigin(cat);
        Location eye = player.getEyeLocation();
        double dx = eye.getX() - origin.getX();
        double dy = eye.getY() - origin.getY();
        double dz = eye.getZ() - origin.getZ();
        float pitch = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return Math.max(-HEAD_PITCH_LIMIT, Math.min(HEAD_PITCH_LIMIT, pitch));
    }

    /**
     * 头部关节在世界中的实际位置：模型位置 + 缩放后的关节原点按身体朝向旋转。
     */
    private static Location headOrigin(Cat cat) {
        double yaw = Math.toRadians(cat.getBodyYaw());
        double pivotZ = MODEL_SCALE * HEAD_PIVOT_Z;
        return new Location(cat.getWorld(),
                cat.getX() - pivotZ * Math.sin(yaw),
                cat.getY() + MODEL_Y_OFFSET,
                cat.getZ() + pivotZ * Math.cos(yaw));
    }

    private static float normalizeDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        } else if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    private float currentLookAngle(UUID playerId) {
        HeadPose pose = headPoses.get(playerId);
        return pose == null ? 0.0F : pose.lookTenths() / 10.0F;
    }

    private float currentPitch(UUID playerId) {
        HeadPose pose = headPoses.get(playerId);
        return pose == null ? 0.0F : pose.pitchTenths() / 10.0F;
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
