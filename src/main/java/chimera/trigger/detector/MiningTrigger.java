package chimera.trigger.detector;

import org.bukkit.Material;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Set;

/**
 * 判定目标：玩家挖掉的是否为「贵重矿石」。
 *
 * <p>计入：铁、金、红石、青金石、钻石、绿宝石矿石（含深板岩变种）、下界金矿、远古残骸。
 * <p>不计入：铜矿、煤矿、下界石英。
 */
public final class MiningTrigger {

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

    public boolean matches(BlockBreakEvent event) {
        return VALUABLE_ORES.contains(event.getBlock().getType());
    }
}
