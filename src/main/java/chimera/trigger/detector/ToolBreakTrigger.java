package chimera.trigger.detector;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.event.player.PlayerItemBreakEvent;

/**
 * 判定目标：玩家手中的工具是否因耐久耗尽而损坏。
 *
 * <p>镐、斧、锹、锄、剑使用 Paper 的 ITEMS_* 标签判断，可覆盖所有材质；
 * 剪刀和钓鱼竿不在这些标签内，单独用材质判断。
 * 盔甲、鞘翅、弓、弩、打火石、重锤、三叉戟都不属于上述范围，因此不会触发。
 */
public final class ToolBreakTrigger {

    public boolean matches(PlayerItemBreakEvent event) {
        return isTool(event.getBrokenItem().getType());
    }

    private static boolean isTool(Material material) {
        return Tag.ITEMS_PICKAXES.isTagged(material)
                || Tag.ITEMS_AXES.isTagged(material)
                || Tag.ITEMS_SHOVELS.isTagged(material)
                || Tag.ITEMS_HOES.isTagged(material)
                || Tag.ITEMS_SWORDS.isTagged(material)
                || material == Material.SHEARS
                || material == Material.FISHING_ROD;
    }
}
