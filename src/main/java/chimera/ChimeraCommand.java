package chimera;

import chimera.trigger.TriggerService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Handles {@code /chimera} (summon/dismiss toggle), {@code /chimera bubble},
 * {@code /chimera testtrigger <id>}, {@code /chimera reload} and {@code /chimera model}.
 */
public final class ChimeraCommand implements CommandExecutor {

    private static final String PREFIX = "[Chimera] ";
    private static final String TEST_BUBBLE = "你好会上班呀～";
    private static final String USAGE = "用法：/chimera [bubble|testtrigger|reload|model]（直接输入 /chimera 召唤/收回）";

    private final CompanionManager companions;
    private final TriggerService triggers;
    private final ChimeraCompanionPlugin plugin;
    private final CompanionLifecycleListener lifecycleListener;

    public ChimeraCommand(CompanionManager companions, TriggerService triggers,
                          ChimeraCompanionPlugin plugin, CompanionLifecycleListener lifecycleListener) {
        this.companions = companions;
        this.triggers = triggers;
        this.plugin = plugin;
        this.lifecycleListener = lifecycleListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(PREFIX + "该指令只能由玩家执行。"));
            return true;
        }
        if (args.length == 0) {
            toggleCompanion(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "bubble" -> {
                if (companions.showBubble(player, TEST_BUBBLE)) {
                    player.sendMessage(Component.text(PREFIX + "小奇美拉冒了个泡泡。"));
                } else {
                    player.sendMessage(Component.text(PREFIX + "先召唤小奇美拉吧。"));
                }
            }
            case "testtrigger" -> {
                if (args.length == 2) {
                    triggers.showTrigger(player, args[1].toLowerCase(Locale.ROOT));
                } else {
                    player.sendMessage(Component.text(PREFIX + "用法：/" + label + " testtrigger <id>"));
                }
            }
            case "reload" -> {
                if (player.hasPermission("chimera.admin")) {
                    plugin.reloadCompanionConfig();
                    player.sendMessage(Component.text(PREFIX + "配置已重新加载。"));
                } else {
                    player.sendMessage(Component.text(PREFIX + "你没有权限执行该指令。"));
                }
            }
            case "model" -> {
                if (!player.hasPermission("chimera.admin")) {
                    player.sendMessage(Component.text(PREFIX + "你没有权限执行该指令。"));
                } else if (args.length > 3) {
                    player.sendMessage(Component.text(PREFIX + "用法：/" + label + " model [scale] [yaw]"));
                } else {
                    try {
                        float scale = args.length >= 2 ? Float.parseFloat(args[1]) : 1.0F;
                        float yaw = args.length >= 3 ? Float.parseFloat(args[2]) : 0.0F;
                        plugin.showTestModel(player, scale, yaw);
                        player.sendMessage(Component.text(PREFIX
                                + "已生成测试模型：scale=" + scale + "，yaw=" + yaw));
                    } catch (NumberFormatException e) {
                        player.sendMessage(Component.text(PREFIX + "用法：/" + label + " model [scale] [yaw]"));
                    }
                }
            }
            default -> player.sendMessage(Component.text(PREFIX + USAGE));
        }
        return true;
    }

    /**
     * 没有跟宠时召唤，已有跟宠时收回；并同步玩家的自动召唤标记。
     */
    private void toggleCompanion(Player player) {
        if (companions.companionOf(player) != null) {
            companions.dismiss(player.getUniqueId());
            triggers.clear(player.getUniqueId());
            lifecycleListener.setCompanionEnabled(player, false);
            player.sendMessage(Component.text(PREFIX + "小奇美拉回去休息了。"));
        } else {
            companions.summon(player);
            lifecycleListener.setCompanionEnabled(player, true);
            player.sendMessage(Component.text(PREFIX + "小奇美拉出现啦～"));
        }
    }
}
