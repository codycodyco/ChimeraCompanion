# ChimeraCompanion — 行为触发模块最小增量开发计划

> 目标平台：Paper 26.2。基于已经运行的跟宠、跨世界跟随、TextDisplay 气泡、随机台词与随机音效功能增量开发。此计划是开发任务单，不代表当前仓库已有相应类。

## 0. 开发约束

- 一次只交付一个可测试增量；每个任务通过人工 review 与游戏内验证后才进入下一任务。
- 先阅读 `AGENTS.md`、现有 `CompanionManager`、气泡显示方法与配置，不擅自重构原有功能。
- 默认只修改本任务必要的 1～3 个主要源文件；代码量目标约 200 行以内，**不是硬性代码行数限制**。
- 不提前新增规则引擎、反射扫描、工厂、事件总线、数据库、异步线程、插件依赖、复杂恢复逻辑。
- 使用主线程事件与原有跟宠同步调度器；必要的空值检查、事件取消判断、退出清理必须保留。
- 事件监听使用 `@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)`（仅针对可取消事件）；绝不在 `MONITOR` 中修改原事件。
- 不自动 commit / push；每次完成时汇报：修改文件、为什么需要、测试结果、当前局限。
- 玩家 UUID 用于临时运行状态，不持久化事件触发历史；仅对当前召唤了跟宠的玩家说话。

## 1. 目标行为与 API

| 事件 ID | 触发 | API / 判断 |
|---|---|---|
| `death.fall` | 高处摔死 | `PlayerDeathEvent.getDamageSource().getDamageType() == DamageType.FALL` |
| `death.lava` | 岩浆直接致死 | `DamageType.LAVA`（持续燃烧 `ON_FIRE` **不算**岩浆死亡） |
| `death.drown` | 溺水死亡 | `DamageType.DROWN` |
| `death.mob` | 被怪物最终击杀 | `DamageSource.getCausingEntity()` 为 `Enemy`；不包含普通玩家攻击，具体范围需测试 |
| `death.void` | 虚空死亡 | `DamageType.OUT_OF_WORLD` |
| `mining.diamond` | 玩家破坏钻石矿 | `BlockBreakEvent` + `DIAMOND_ORE` / `DEEPSLATE_DIAMOND_ORE` |
| `mining.debris` | 破坏远古残骸 | `ANCIENT_DEBRIS` |
| `survival.tool_break` | 手持工具实际损坏 | `PlayerItemBreakEvent`；首版按该事件涵盖的耐久物品处理，不另做工具分类 |
| `survival.fish` | 钓鱼成功获得物品 | `PlayerFishEvent.State.CAUGHT_FISH`，此状态可能是宝藏/垃圾，不限鱼 |
| `survival.totem` | 玩家成功触发不死图腾 | `EntityResurrectEvent` + `Player` + 事件未取消 |
| `survival.eat` | 玩家完成食物消耗 | `PlayerItemConsumeEvent` + `getItem().getType().isEdible()` |
| `survival.afk` | 玩家连续空闲 5 分钟 | `Player.getIdleDuration()`，每个空闲周期仅提示一次 |

**优先级与相互影响：**死亡属于重要提示，不受普通行为全局冷却限制；其它行为统一共享玩家级冷却（首版 60 秒）。无排队、无重试；冷却命中即丢弃。随机闲聊仍由原有系统管理；如果出现偶发覆盖，记录实测后另开小任务处理。

## 2. 最小目录结构

现有源码布局优先，不必强制迁移：

```text
src/main/java/.../chimera/
├── CompanionManager.java       # 已存在；保持现有生命周期
├── DialogueService.java        # 已存在；复用气泡方法
└── trigger/
    ├── TriggerService.java     # 唯一公共入口、冷却和配置
    ├── DeathListener.java      # 5 种死亡、复活后显示
    ├── MiningListener.java     # 钻石、残骸
    ├── SurvivalListener.java   # 工具、钓鱼、图腾、进食
    └── IdleChecker.java        # 最后阶段才创建；可改放现有 manager
```

不要预创建空类。死亡/挖矿/生存拆分类是为了便于人读代码，**不是**为了抽象成通用事件框架。

## 3. 配置最小契约

`config.yml` 保持随机台词已有配置不变；新加 `triggers`：

```yaml
triggers:
  cooldown-seconds: 60
  death:
    fall:
      - "老板好喜欢地面呢～"
    lava:
      - "老板是在泡温泉吗～"
    drown:
      - "老板在水下住得还习惯吗～"
    mob:
      - "老板交到新朋友啦～"
    void:
      - "老板在探索世界外面呀～"
  mining:
    diamond:
      - "老板运气真好呢～"
    debris:
      - "老板终于挖到宝贝啦～"
  survival:
    tool_break:
      - "老板用得真仔细呢～"
    fish:
      - "老板好有耐心呀～"
    totem:
      - "老板差点就成功了呢～"
    eat:
      - "老板胃口真好呢～"
    afk:
      - "真会休息呢～"
```

`TriggerService` 用 `getStringList("triggers." + triggerId)` 获取对应列表，空列表就忽略。只设计一个 `cooldown-seconds`，等真实需求出现再决定是否需要每类独立冷却。

## 4. 增量任务（严格按顺序）

### T01：公共触发入口

**目标：**新增 `trigger/TriggerService.java`，只负责「判断已有跟宠 → 查找台词 → 玩家级普通事件冷却 → 随机取一句 → 调用既有气泡方法」。提供一个可由现有测试命令调用的入口验证，不增加真正的行为监听。

**建议接口：**`showTrigger(Player player, String key, boolean bypassCooldown)`；死亡复活传 `true`，其它触发传 `false`。这是为了规避引入优先级枚举/策略框架而保留的简单参数。单个 `Map<UUID, Long>` 记录普通行为最后一次成功发言时间。首次触发应成功，冷却只在实际显示后更新。

**验收：**临时命令触发显示、重复触发在 60 秒内静默；其他玩家不相互影响；没有跟宠无输出；旧随机台词和音效不受影响。

**避免：**额外任务、异步访问 Bukkit API、每类 Map、规则注册器、自动加载监听器。

### T02：死亡暂存与复活后显示

**目标：**新增 `DeathListener`，本阶段只识别 `DamageType.FALL` 并暂存 `death.fall`；死亡时在 `Map<UUID, String>` 保存待发 ID，复活后用 `PlayerPostRespawnEvent` 调用 T01 公共入口并立即移除暂存。其余死亡原因留到 T03/T04，避免临时版本错误归因。

**注意：**Paper 26.2 的 `PlayerPostRespawnEvent` 真实包名是 `com.destroystokyo.paper.event.player.PlayerPostRespawnEvent`。如果现有跟宠重建发生在该事件之后，可通过一次延后同步任务接入已有跟宠重建流程，**禁止循环重试**。退出和插件关闭时清理待发记录。只对死亡时已召唤跟宠的玩家暂存。

**验收：**死亡界面不显示气泡，复活后恰好出现一次；没有跟宠时无输出；退出后不残留待发状态。

### T03：4 种环境死亡

**目标：**在 `DeathListener` 增加 `DamageSource.getDamageType()` 的四个映射：`FALL`、`LAVA`、`DROWN`、`OUT_OF_WORLD`，其它死亡先不吐槽。保留 T02 的复活显示处理。

**验收：**四种死亡分别出现正确台词；玩家被 `/kill` 或普通火焰烧死时不要误报成岩浆；每次死亡仅保留一个 ID。不要解析死亡消息字符串。

### T04：怪物击杀玩家

**目标：**在 T03 的同一个死亡监听器中补 `death.mob`。优先识别 `getDamageSource().getCausingEntity()` 是否为 `org.bukkit.entity.Enemy`；对其他原因明确优先执行 T03 分类，避免重复触发。

**验收：**僵尸近战、骷髅弓箭击杀能够识别；另一个玩家击杀不算怪物；一个死亡仅一条台词。对无法归因的间接环境死亡不额外推测。

### T05：钻石矿与远古残骸

**目标：**新增 `MiningListener`，使用 `BlockBreakEvent`，识别 `DIAMOND_ORE`、`DEEPSLATE_DIAMOND_ORE`、`ANCIENT_DEBRIS`。调用普通冷却入口。

**验收：**三类方块正确映射两个 key；普通石头不触发；其他插件取消的破坏事件不触发；连续挖矿只在冷却允许时显示。这里指破坏矿块，而不是实际获得掉落物。

### T06：物品损坏

**目标：**新增 `SurvivalListener`；只监听 `PlayerItemBreakEvent`，调用 `survival.tool_break`。首版不区分镐、斧、剪刀、钓鱼竿等，以减少类型表与异常规则。

**验收：**物品耐久耗尽时触发；仅受到耐久伤害但没坏不触发；无跟宠无输出。

### T07：成功钓鱼

**目标：**同一 `SurvivalListener` 增加 `PlayerFishEvent`，只判断 `State.CAUGHT_FISH` 且事件未取消，调用 `survival.fish`。

**验收：**钓到物品时触发；单纯抛竿、鱼咬钩、空竿回收不触发；钓到宝藏仍应触发（`CAUGHT_FISH` 不限鱼类）。

### T08A：不死图腾

**目标：**同一 `SurvivalListener` 增加 `EntityResurrectEvent`；仅 `event.getEntity() instanceof Player` 且 `!event.isCancelled()` 时触发 `survival.totem`。

**验收：**成功图腾保命触发；无图腾直接死亡不触发；怪物图腾事件不触发。

### T08B：玩家进食

**目标：**同一 `SurvivalListener` 增加 `PlayerItemConsumeEvent`；仅 `event.getItem().getType().isEdible()` 且事件未取消时触发 `survival.eat`。首版仅识别材料默认的食物，不研究自定义物品的数据组件。

**验收：**面包、牛排、苹果可触发；药水、牛奶不触发；取消的消耗事件不触发。

### T09：挂机检测

**目标：**利用 `Player.getIdleDuration()`，对已召唤跟宠的在线玩家每 20 秒检查一次：`>= 5 分钟` 且本次空闲期尚未提醒，触发 `survival.afk` 并标记本次空闲已提醒；再次恢复活跃（idle < 5 分钟）清除标记。借用现有跟随周期任务的低频计数或现有通用时钟，避免另起线程。

**重要：**若普通行为全局冷却使本次挂机发言被跳过，不能标记为已提醒；仅在成功显示后标记。对站在原地但仍有操作的玩家，服务器空闲计时可能被重置。基岩版经 Geyser 的输入行为必须实测。

**验收：**约 5 分钟后出现一次；继续挂机不会重复；移动或交互后再挂机可再次触发；多个玩家状态独立；跟宠收回/退出后清理状态。

### T10：综合回归与代码审查

- 单人：所有 12 个触发都有对应台词；意外操作不会误触发。
- 双人：只有有跟宠的玩家收到自己的台词；全局冷却独立。
- 死亡：跨世界复活、玩家在死亡界面停留、连续死亡、退出时没有重复和幽灵气泡。
- 多世界：在主城、生存、资源世界各测一次；避免与 Multiverse/权限插件冲突。
- 基岩：经 Geyser 实机检查气泡是否显示；若不显示，记为独立兼容性任务，**不要借回归任务重构显示层**。
- 现有功能：召唤、收回、跟随、跨世界、随机台词、随机音效全部回归。
- Review：去掉任务外的接口、未使用字段、重复防御代码；不要为了减行数删除必须的资源清理。

## 5. 向 DSH 发送任务的固定模板

```text
请先阅读 AGENTS.md、ChimeraCompanion_TRIGGER_PLAN.md 与这次任务相关的现有源码。

当前只执行任务 TXX：[在这里粘贴本任务标题]。
严格遵循该任务的目标、限制和验收要求。

开发要求：
1. 先用不超过 8 行说明准备修改哪些文件、采用什么实现方式；先不要修改代码。
2. 我确认方案后再开始修改。
3. 仅写当前任务需要的最小实现；不要顺手开发其他触发、兼容框架或重构。
4. 能复用现有 CompanionManager/DialogueService/定时任务就复用。
5. 不做没有实际故障依据的防御性编程，但保留事件取消判断与生命周期清理。
6. 编译并运行相关现有测试，报告确实执行过的命令和结果。
7. 最后输出：修改文件、核心代码逻辑、人工验收步骤、已知限制。
8. 不自动 commit 或 push。
```

## 6. 每次审查命令

```bash
git status --short
git diff --check
git diff --stat
git diff
./gradlew build
```

通过人工验收后再提交。推荐提交信息：

```text
feat: add behavior trigger entry
feat: show death dialogue after respawn
feat: classify environmental deaths
feat: handle mob-caused deaths
feat: add mining dialogue triggers
feat: add tool break dialogue
feat: add fishing dialogue
feat: add totem dialogue
feat: add food dialogue
feat: add idle dialogue
```

## 7. API 官方参考

- Paper 26.2 Player：<https://jd.papermc.io/paper/26.2/org/bukkit/entity/Player.html>
- Paper 26.2 PlayerDeathEvent：<https://jd.papermc.io/paper/26.2/org/bukkit/event/entity/PlayerDeathEvent.html>
- Paper 26.2 DamageType：<https://jd.papermc.io/paper/26.2/org/bukkit/damage/DamageType.html>
- Paper 26.2 PlayerPostRespawnEvent：<https://jd.papermc.io/paper/26.2/com/destroystokyo/paper/event/player/PlayerPostRespawnEvent.html>
- Paper 26.2 PlayerFishEvent：<https://jd.papermc.io/paper/26.2/org/bukkit/event/player/PlayerFishEvent.html>
- Paper 26.2 Material：<https://jd.papermc.io/paper/26.2/org/bukkit/Material.html>
- Paper 事件监听说明：<https://docs.papermc.io/paper/dev/event-listeners/>
