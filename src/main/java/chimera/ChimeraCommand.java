package chimera;

import chimera.trigger.TriggerService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Handles {@code /chimera summon}, {@code /chimera dismiss}, {@code /chimera bubble} and
 * {@code /chimera testtrigger <id>}.
 */
public final class ChimeraCommand implements CommandExecutor {

    private static final String PREFIX = "[Chimera] ";
    private static final String TEST_BUBBLE = "你好会上班呀～";

    private final CompanionManager companions;
    private final TriggerService triggers;

    public ChimeraCommand(CompanionManager companions, TriggerService triggers) {
        this.companions = companions;
        this.triggers = triggers;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(PREFIX + "该指令只能由玩家执行。"));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text(PREFIX + "用法：/" + label
                    + " <summon|dismiss|bubble|testtrigger <id>>"));
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
            default -> player.sendMessage(Component.text(PREFIX + "用法：/" + label
                    + " <summon|dismiss|bubble|testtrigger <id>>"));
        }
        return true;
    }
}
