package chimera;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns random dialogue timing, line selection, chat feedback, the bubble above the companion and
 * its voice sound. Head tilt is delegated to {@link CompanionModel}.
 */
final class CompanionDialogue {

    private static final int BUBBLE_TICKS = 80;
    private static final double BUBBLE_HEIGHT = 1.2;

    private final CompanionModel models;
    private final Map<UUID, Bubble> bubbles = new HashMap<>();
    private final Map<UUID, Talk> talks = new HashMap<>();

    private List<String> messages;
    private int intervalMinSeconds;
    private int intervalMaxSeconds;

    private record Bubble(TextDisplay display, int expiresAtTick) {
    }

    private record Talk(int nextSpeakTick, int lastIndex) {
    }

    CompanionDialogue(CompanionModel models) {
        this.models = models;
    }

    /**
     * Replaces the dialogue settings, used both at startup and when the config is reloaded.
     */
    void reload(List<String> messages, int intervalMinSeconds, int intervalMaxSeconds) {
        this.messages = messages;
        // Keep a usable range even when the config was edited into an invalid state.
        this.intervalMinSeconds = Math.max(1, intervalMinSeconds);
        this.intervalMaxSeconds = Math.max(this.intervalMinSeconds, intervalMaxSeconds);
    }

    /**
     * Starts the random dialogue timer for a newly summoned companion.
     */
    void start(UUID playerId) {
        talks.put(playerId, new Talk(scheduleNext(), -1));
    }

    /**
     * Drops the bubble and dialogue timer of a dismissed companion.
     */
    void stop(UUID playerId) {
        clearBubble(playerId);
        talks.remove(playerId);
    }

    /**
     * Shows a bubble with the given text above the player's companion, and mirrors the same line
     * into the owner's chat. Showing it again reuses the existing display instead of stacking a
     * second one.
     *
     * @return {@code false} if the player has no companion to put the bubble above
     */
    boolean show(Player player, String text) {
        UUID playerId = player.getUniqueId();
        Location modelLocation = models.modelLocation(playerId);
        if (modelLocation == null) {
            return false;
        }
        // 说话语音：由 sounds.json 在 19 段中随机，替换原来的悦灵叫声。
        playSound(modelLocation);
        // 说话时触发一次歪头；随机台词和行为触发都走这里。
        models.tiltForSpeech(playerId);
        // 头顶气泡和聊天栏使用同一句台词，不重新随机。
        player.sendMessage(Component.text("[说怪话] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(text, NamedTextColor.WHITE)));

        int expiresAtTick = Bukkit.getCurrentTick() + BUBBLE_TICKS;
        Bubble current = bubbles.get(playerId);
        if (current != null) {
            current.display().text(Component.text(text));
            current.display().teleport(bubbleLocation(modelLocation));
            bubbles.put(playerId, new Bubble(current.display(), expiresAtTick));
            return true;
        }

        TextDisplay display = modelLocation.getWorld().spawn(
                bubbleLocation(modelLocation), TextDisplay.class, textDisplay -> {
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
    void tickBubble(Player player) {
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
        bubble.display().teleport(bubbleLocation(models.modelLocation(playerId)));
    }

    /**
     * Makes the player's companion say a random configured line once its own timer has run out,
     * then schedules the next line.
     */
    void tickDialogue(Player player) {
        UUID playerId = player.getUniqueId();
        Talk talk = talks.get(playerId);
        if (talk == null || Bukkit.getCurrentTick() < talk.nextSpeakTick()) {
            return;
        }
        if (messages.isEmpty()) {
            return;
        }

        int index = pickIndex(talk.lastIndex());
        show(player, messages.get(index));
        talks.put(playerId, new Talk(scheduleNext(), index));
    }

    /**
     * Plays the companion voice at the given location, used for right-click interaction.
     */
    void playSound(Location location) {
        location.getWorld().playSound(location, "chimera:speak", SoundCategory.NEUTRAL, 0.8F, 1.0F);
    }

    /**
     * Removes the player's bubble, if one is showing.
     */
    void clearBubble(UUID playerId) {
        Bubble bubble = bubbles.remove(playerId);
        if (bubble != null) {
            bubble.display().remove();
        }
    }

    /**
     * Removes every bubble and dialogue timer, used when the plugin shuts down.
     */
    void clearAll() {
        for (Bubble bubble : bubbles.values()) {
            bubble.display().remove();
        }
        bubbles.clear();
        talks.clear();
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

    private static Location bubbleLocation(Location modelLocation) {
        return modelLocation.clone().add(0, BUBBLE_HEIGHT, 0);
    }
}
