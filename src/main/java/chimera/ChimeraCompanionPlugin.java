package chimera;

import chimera.trigger.TriggerService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Registers the companion command, drives the follow task and keeps companions in sync with their
 * owners across death, respawn and world changes.
 */
public final class ChimeraCompanionPlugin extends JavaPlugin implements Listener {

    /**
     * 判定目标：玩家挖掉的是否为「贵重矿石」。
     *
     * <p>计入：铁、金、红石、青金石、钻石、绿宝石矿石（含深板岩变种）、下界金矿、远古残骸。
     * <p>不计入：铜矿、煤矿、下界石英。
     */
    private static final Set<Material> VALUABLE_ORES = Set.of(
            Material.IRON_ORE,
            Material.DEEPSLATE_IRON_ORE,
            Material.GOLD_ORE,
            Material.DEEPSLATE_GOLD_ORE,
            Material.NETHER_GOLD_ORE,
            Material.REDSTONE_ORE,
            Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE,
            Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE,
            Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE,
            Material.DEEPSLATE_EMERALD_ORE,
            Material.ANCIENT_DEBRIS);

    private CompanionManager companions;

    private TriggerService triggers;

    private BukkitTask followTask;

    private final Map<UUID, String> pendingDeathTriggers = new HashMap<>();

    /** 已在本轮挂机中成功提醒过的玩家；恢复活动或失去跟宠后移除。 */
    private final Set<UUID> idleReminded = new HashSet<>();

    /** 每个玩家当前的模型测试实体，重复执行指令时替换。 */
    private final Map<UUID, ItemDisplay> testModels = new HashMap<>();

    /** 挂机检查周期：20 秒。 */
    private static final long IDLE_CHECK_PERIOD_TICKS = 20L * 20;

    /** 连续空闲达到该时长即视为挂机。 */
    private static final Duration IDLE_THRESHOLD = Duration.ofMinutes(5);

    private BukkitTask idleTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        companions = new CompanionManager(
                getConfig().getStringList("dialogue.messages"),
                getConfig().getInt("dialogue.interval-min", 60),
                getConfig().getInt("dialogue.interval-max", 180));
        triggers = new TriggerService(this, companions);
        getCommand("chimera").setExecutor(new ChimeraCommand(companions, triggers, this));
        getServer().getPluginManager().registerEvents(this, this);
        followTask = new FollowTask(companions, triggers)
                .runTaskTimer(this, FollowTask.PERIOD_TICKS, FollowTask.PERIOD_TICKS);
        idleTask = getServer().getScheduler().runTaskTimer(this, this::checkIdle,
                IDLE_CHECK_PERIOD_TICKS, IDLE_CHECK_PERIOD_TICKS);
    }

    @Override
    public void onDisable() {
        followTask.cancel();
        idleTask.cancel();
        companions.dismissAll();
        triggers.clearAll();
        pendingDeathTriggers.clear();
        idleReminded.clear();
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
     * 玩家退出时清理其跟宠、触发冷却以及尚未显示的死亡台词，避免残留状态。
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        companions.dismiss(playerId);
        triggers.clear(playerId);
        pendingDeathTriggers.remove(playerId);
        idleReminded.remove(playerId);
        ItemDisplay testModel = testModels.remove(playerId);
        if (testModel != null) {
            testModel.remove();
        }
    }

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
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Cat companion = companions.companionOf(player);
        if (companion == null) {
            return;
        }
        // 让跟宠停在死亡位置：关闭 AI，避免它在玩家死亡期间走动。
        companion.setAware(false);

        // 按死因记住对应的台词 ID，等复活后再交给 TriggerService 显示。
        DamageType damageType = event.getDamageSource().getDamageType();
        if (damageType == DamageType.FALL) {
            // 摔死
            pendingDeathTriggers.put(player.getUniqueId(), "death.fall");
        } else if (damageType == DamageType.LAVA) {
            // 岩浆烧死
            pendingDeathTriggers.put(player.getUniqueId(), "death.lava");
        } else if (damageType == DamageType.DROWN) {
            // 溺水
            pendingDeathTriggers.put(player.getUniqueId(), "death.drown");
        } else if (event.getDamageSource().getCausingEntity() instanceof Enemy) {
            // 被怪物击杀：归因实体必须是 Enemy，玩家击杀或环境伤害不算
            pendingDeathTriggers.put(player.getUniqueId(), "death.mob");
        }
    }

    /**
     * 判定目标：玩家挖掉的方块是否为贵重矿石（见 {@link #VALUABLE_ORES}）。
     *
     * <p>{@code MONITOR} 优先级让我们在其他插件处理完之后再判断；
     * {@code ignoreCancelled} 保证被领地等插件取消的破坏不会触发。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (VALUABLE_ORES.contains(event.getBlock().getType())) {
            triggers.showTrigger(event.getPlayer(), "mining.valuable");
        }
    }

    /**
     * 判定目标：玩家手中的工具是否因耐久耗尽而损坏。
     *
     * <p>是否属于工具由 {@link #isTool(Material)} 决定，损坏事件本身只提供物品类型。
     */
    @EventHandler
    public void onItemBreak(PlayerItemBreakEvent event) {
        if (isTool(event.getBrokenItem().getType())) {
            triggers.showTrigger(event.getPlayer(), "survival.tool_break");
        }
    }

    /**
     * 判定目标：损坏的物品是否为工具。
     *
     * <p>镐、斧、锹、锄、剑使用 Paper 的 ITEMS_* 标签判断，可覆盖所有材质；
     * 剪刀和钓鱼竿不在这些标签内，单独用材质判断。
     * 盔甲、鞘翅、弓、弩、打火石、重锤、三叉戟都不属于上述范围，因此不会触发。
     */
    private static boolean isTool(Material material) {
        return Tag.ITEMS_PICKAXES.isTagged(material)
                || Tag.ITEMS_AXES.isTagged(material)
                || Tag.ITEMS_SHOVELS.isTagged(material)
                || Tag.ITEMS_HOES.isTagged(material)
                || Tag.ITEMS_SWORDS.isTagged(material)
                || material == Material.SHEARS
                || material == Material.FISHING_ROD;
    }

    /**
     * 判定目标：玩家是否完成了一次「吃东西」。
     *
     * <p>用 {@code Material#isEdible()} 判断是否为原版食物：药水和牛奶虽然有饮用动画，
     * 但没有食物属性，返回 false，因此不会触发。
     * {@code ignoreCancelled} 保证被其他插件取消的进食不会触发。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType().isEdible()) {
            triggers.showTrigger(event.getPlayer(), "survival.eat");
        }
    }

    /**
     * 玩家复活时：解除跟宠冻结、把它传送到复活点，然后播放死亡时记录的台词（如果有）。
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String triggerId = pendingDeathTriggers.remove(player.getUniqueId());

        Cat companion = companions.companionOf(player);
        if (companion == null) {
            return;
        }
        companion.setAware(true);
        companion.teleport(CompanionManager.behind(event.getRespawnLocation()));
        // 复活可能跨世界，模型跟着重新创建，避免留在旧世界。
        companions.recreateModel(player.getUniqueId(), companion);

        if (triggerId != null) {
            triggers.showTrigger(player, triggerId);
        }
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
     * 判定目标：玩家是否已经连续挂机 5 分钟。
     *
     * <p>每 20 秒检查一次。只有同时满足「有已召唤的跟宠」和「空闲时长达到 5 分钟」才会尝试触发；
     * 同一次连续挂机只提醒一次，触发失败（例如被全局冷却拒绝）不会被记为已提醒，下一轮继续尝试。
     * 玩家恢复活动或收回跟宠后移除标记，使下一次挂机能再次提醒。
     *
     * <p>只读取 {@link Player#getIdleDuration()}，不调用 {@code resetIdleDuration()}，
     * 因此不会干扰服务端原本的挂机踢出机制。
     */
    private void checkIdle() {
        for (Player player : getServer().getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (companions.companionOf(player) == null
                    || player.getIdleDuration().compareTo(IDLE_THRESHOLD) < 0) {
                idleReminded.remove(playerId);
                continue;
            }
            if (idleReminded.contains(playerId)) {
                continue;
            }
            if (triggers.showTrigger(player, "survival.idle")) {
                idleReminded.add(playerId);
            }
        }
    }
}
