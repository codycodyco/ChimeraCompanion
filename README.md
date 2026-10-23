# ChimeraCompanion

一只会跟随玩家、随机说话并用聊天气泡吐槽玩家的小奇美拉跟宠。适用于 Paper 26.2。

## 安装

服务器只需要两个插件 JAR：

1. `RPEngine.jar` → `plugins/`
2. `ChimeraCompanion-0.1.0.jar` → `plugins/`

重启服务器即可。ChimeraCompanion 会在启动时通过 RP Engine 的
`AddonContent.install()` 自动安装模型、贴图和 19 段语音资源，并在资源有更新时让
RP Engine 重新构建、下发资源包。

**服主不需要手动下载或启用 `ChimeraCompanion-ResourcePack.zip`，也不需要修改
`server.properties` 的 `resource-pack` 配置。**

资源文件的开发说明与单独手动构建方式见 [`resourcepack/README.md`](resourcepack/README.md)。

## 依赖

- Paper 26.2 / Java 25
- [RP Engine](https://modrinth.com/plugin/rp-engine)（`depend`）

## 指令

| 指令 | 说明 |
| --- | --- |
| `/chimera` | 召唤或收回跟宠 |
| `/chimera reload` | 重新读取 `config.yml` |
| `/chimera bubble` | 显示一条测试气泡 |
| `/chimera model` | 生成模型测试实体 |
| `/chimera testtrigger <id>` | 手动触发一条行为台词 |

`chimera.admin` 权限默认仅管理员拥有。

## 配置

`config.yml` 包含随机台词、行为触发台词与冷却设置。文件内有逐项注释。
