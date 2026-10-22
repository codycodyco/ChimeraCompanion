package chimera;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

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

    private List<String> messages;
    private int intervalMinSeconds;
    private int intervalMaxSeconds;

    private final Map<UUID, Allay> companions = new HashMap<>();
    private final Map<UUID, Bubble> bubbles = new HashMap<>();
    private final Map<UUID, Talk> talks = new HashMap<>();
    private final Map<UUID, Integer> nextSoundTicks = new HashMap<>();

    private record Bubble(TextDisplay display, int expiresAtTick) {
    }

    private record Talk(int nextSpeakTick, int lastIndex) {
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
        Allay current = companions.get(playerId);
        if (current != null) {
            if (current.isValid()) {
                return false;
            }
            // The entity was removed by something else, so the stale entry is dropped.
            companions.remove(playerId);
        }

        Allay allay = player.getWorld().spawn(behind(player.getLocation()), Allay.class);
        allay.setPersistent(false);
        allay.setInvulnerable(true);
        allay.setCollidable(false);
        allay.setSilent(true);
        allay.setCanPickupItems(false);
        allay.setCanDuplicate(false);

        companions.put(playerId, allay);
        talks.put(playerId, new Talk(scheduleNext(), -1));
        nextSoundTicks.put(playerId, scheduleNextSound());
        return true;
    }

    /**
     * Removes the companion owned by the given player.
     *
     * @return {@code false} if the player had no tracked companion
     */
    public boolean dismiss(UUID playerId) {
        clearBubble(playerId);
        talks.remove(playerId);
        nextSoundTicks.remove(playerId);
        Allay allay = companions.remove(playerId);
        if (allay == null) {
            return false;
        }
        allay.remove();
        return true;
    }

    /**
     * Removes every tracked companion and bubble, used when the plugin shuts down.
     */
    public void dismissAll() {
        for (Allay allay : companions.values()) {
            allay.remove();
        }
        companions.clear();
        for (Bubble bubble : bubbles.values()) {
            bubble.display().remove();
        }
        bubbles.clear();
        talks.clear();
        nextSoundTicks.clear();
    }

    /**
     * Shows a bubble with the given text above the player's companion. Showing it again reuses the
     * existing display instead of stacking a second one.
     *
     * @return {@code false} if the player has no companion to put the bubble above
     */
    public boolean showBubble(Player player, String text) {
        Allay companion = companionOf(player);
        if (companion == null) {
            return false;
        }
        playSound(companion);
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
    public void tickBubble(Player player, Allay companion) {
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
    public void tickSound(Player player, Allay companion) {
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
    private static void playSound(Allay companion) {
        companion.getWorld().playSound(companion.getLocation(),
                Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, SOUND_VOLUME, SOUND_PITCH);
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
    public Allay companionOf(Player player) {
        return companions.get(player.getUniqueId());
    }

    private static Location bubbleLocation(Allay companion) {
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
