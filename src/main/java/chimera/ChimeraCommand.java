package chimera;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Handles {@code /chimera summon}, {@code /chimera dismiss} and {@code /chimera bubble}.
 */
public final class ChimeraCommand implements CommandExecutor {

    private static final String PREFIX = "[Chimera] ";
    private static final String TEST_BUBBLE = "你好会上班呀～";

    private final CompanionManager companions;

    public ChimeraCommand(CompanionManager companions) {
        this.companions = companions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(PREFIX + "该指令只能由玩家执行。"));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Component.text(PREFIX + "用法：/" + label + " <summon|dismiss|bubble>"));
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
            default -> player.sendMessage(Component.text(PREFIX + "用法：/" + label + " <summon|dismiss|bubble>"));
        }
        return true;
    }
}
