package chimera;

import chimera.trigger.TriggerService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Handles {@code /chimera summon}, {@code /chimera dismiss}, {@code /chimera bubble},
 * {@code /chimera testtrigger <id>} and {@code /chimera reload}.
 */
public final class ChimeraCommand implements CommandExecutor {

    private static final String PREFIX = "[Chimera] ";
    private static final String TEST_BUBBLE = "你好会上班呀～";

    private final CompanionManager companions;
    private final TriggerService triggers;
    private final ChimeraCompanionPlugin plugin;

    public ChimeraCommand(CompanionManager companions, TriggerService triggers, ChimeraCompanionPlugin plugin) {
        this.companions = companions;
        this.triggers = triggers;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(PREFIX + "该指令只能由玩家执行。"));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text(PREFIX + "用法：/" + label
                    + " <summon|dismiss|bubble|testtrigger|reload|model>"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "summon" -> {
                if (companions.summon(player)) {
                    player.sendMessage(Component.text(PREFIX + "小奇美拉出现啦～"));
                } else {
                    player.sendMessage(Component.text(PREFIX + "你已经有小奇美拉了。"));
                }
            }
            case "dismiss" -> {
                if (companions.dismiss(player.getUniqueId())) {
                    triggers.clear(player.getUniqueId());
                    player.sendMessage(Component.text(PREFIX + "小奇美拉回去休息了。"));
                } else {
                    player.sendMessage(Component.text(PREFIX + "你现在没有小奇美拉。"));
                }
            }
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
            default -> player.sendMessage(Component.text(PREFIX + "用法：/" + label
                    + " <summon|dismiss|bubble|testtrigger|reload|model>"));
        }
        return true;
    }
}
