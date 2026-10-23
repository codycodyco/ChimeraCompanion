# ChimeraCompanion

一个在 Minecraft 中模拟《崩坏：星穹铁道》「说怪话」的奇美拉跟宠插件。奇美拉会跟随玩家，并根据玩家的行为说话、播放语音和显示聊天气泡。

## 前置插件

- [RP Engine](https://modrinth.com/plugin/rp-engine)（必需）
- 服务端：Paper 26.2 / Java 25

将 RP Engine 和 ChimeraCompanion 的 JAR 放入 `plugins/` 并重启服务器即可，模型与音效资源由 RP Engine 自动管理。

## 指令

| 指令 | 说明 |
| --- | --- |
| `/chimera` | 召唤或收回奇美拉 |
| `/chimera reload` | 重载配置 |
| `/chimera bubble` | 测试聊天气泡 |
| `/chimera model` | 生成模型测试实体 |
| `/chimera testtrigger <id>` | 测试指定行为台词 |

管理指令需要 `chimera.admin` 权限（默认 OP）。
