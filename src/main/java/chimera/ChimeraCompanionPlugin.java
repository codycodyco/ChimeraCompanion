package chimera;

import ai.resourcepack.engine.RPEnginePlugin;
import ai.resourcepack.engine.api.AddonContent;
import chimera.trigger.SurvivalTriggerListener;
import chimera.trigger.TriggerService;
import chimera.trigger.detector.DeathTrigger;
import chimera.trigger.detector.IdleTrigger;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 插件入口：初始化配置与组件、注册命令和监听器、调度任务，并在启动时安装资源包。
 */
public final class ChimeraCompanionPlugin extends JavaPlugin {

    /** 挂机检查周期：20 秒。 */
    private static final long IDLE_CHECK_PERIOD_TICKS = 20L * 20;

    private CompanionManager companions;

    private TriggerService triggers;

    private CompanionLifecycleListener lifecycleListener;

    private DeathTrigger deathTrigger;

    private IdleTrigger idleTrigger;

    private BukkitTask followTask;

    private BukkitTask idleTask;

    /** 每个玩家当前的模型测试实体，重复执行指令时替换。 */
    private final Map<UUID, ItemDisplay> testModels = new HashMap<>();

    @Override
    public void onEnable() {
        if (!installResourcePack()) {
            return;
        }
        saveDefaultConfig();
        companions = new CompanionManager(
                getConfig().getStringList("dialogue.messages"),
                getConfig().getInt("dialogue.interval-min", 60),
                getConfig().getInt("dialogue.interval-max", 180));
        triggers = new TriggerService(this, companions);
        deathTrigger = new DeathTrigger();
        idleTrigger = new IdleTrigger(companions, triggers);
        lifecycleListener = new CompanionLifecycleListener(
                this, companions, triggers, deathTrigger, idleTrigger);
        SurvivalTriggerListener survivalListener = new SurvivalTriggerListener(triggers);

        getCommand("chimera").setExecutor(
                new ChimeraCommand(companions, triggers, this, lifecycleListener));
        getServer().getPluginManager().registerEvents(lifecycleListener, this);
        getServer().getPluginManager().registerEvents(survivalListener, this);

        followTask = new FollowTask(companions, triggers)
                .runTaskTimer(this, FollowTask.PERIOD_TICKS, FollowTask.PERIOD_TICKS);
        idleTask = getServer().getScheduler().runTaskTimer(this, idleTrigger::run,
                IDLE_CHECK_PERIOD_TICKS, IDLE_CHECK_PERIOD_TICKS);
        getServer().getScheduler().runTaskLater(this, lifecycleListener::restoreOnlinePlayers, 1L);
    }

    /**
     * Installs the resource pack carried in this plugin's jar into RP Engine.
     *
     * <p>{@link AddonContent#install} writes only when this jar's version is newer than the
     * installed content, so {@code reload()} is called only when something actually changed.
     *
     * @return {@code false} when the content could not be installed and the plugin was disabled
     */
    private boolean installResourcePack() {
        RPEnginePlugin engine = (RPEnginePlugin) getServer().getPluginManager().getPlugin("RPEngine");
        try {
            if (AddonContent.install(this, engine, "chimera")) {
                getLogger().info("Installed Chimera content into RP Engine, reloading its resource packs.");
                engine.reload();
            }
            return true;
        } catch (IOException e) {
            getLogger().log(Level.SEVERE,
                    "Could not install the Chimera resource pack into RP Engine; disabling ChimeraCompanion.", e);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    @Override
    public void onDisable() {
        // A failed resource install disables the plugin before the runtime state below exists.
        if (followTask == null) {
            return;
        }
        followTask.cancel();
        idleTask.cancel();
        companions.dismissAll();
        triggers.clearAll();
        deathTrigger.clearAll();
        idleTrigger.clearAll();
        for (ItemDisplay display : testModels.values()) {
            display.remove();
        }
        testModels.clear();
    }

    /**
     * Re-reads config.yml and applies the settings that are otherwise only read at startup.
     */
    public void reloadCompanionConfig() {
        reloadConfig();
        companions.reload(
                getConfig().getStringList("dialogue.messages"),
                getConfig().getInt("dialogue.interval-min", 60),
                getConfig().getInt("dialogue.interval-max", 180));
        triggers.reload();
    }

    /**
     * 在玩家面前生成一个独立的 ItemDisplay，用于验证 {@code chimera:companion} 模型。
     * 重复执行会先移除上一次生成的测试模型。
     *
     * @param scale 模型缩放
     * @param yaw   模型朝向（度）
     */
    public void showTestModel(Player player, float scale, float yaw) {
        UUID playerId = player.getUniqueId();
        ItemDisplay previous = testModels.remove(playerId);
        if (previous != null) {
            previous.remove();
        }

        Location at = player.getEyeLocation()
                .add(player.getLocation().getDirection().normalize().multiply(2.0))
                .subtract(0, CompanionManager.MODEL_CENTER_Y * scale, 0);
        ItemDisplay display = player.getWorld().spawn(at, ItemDisplay.class);
        display.setItemStack(CompanionManager.companionModelItem());
        display.setPersistent(false);
        display.setTransformation(CompanionManager.modelTransformation(scale, yaw));

        testModels.put(playerId, display);
    }

    /**
     * 玩家退出时移除其测试模型。
     */
    void discardTestModel(UUID playerId) {
        ItemDisplay display = testModels.remove(playerId);
        if (display != null) {
            display.remove();
        }
    }
}
