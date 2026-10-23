# ChimeraCompanion 资源包

目标客户端：Minecraft Java 26.2，资源包格式 **88**。

## 服务器安装（服主）

服务器只需要安装两个插件 JAR：

1. `RPEngine.jar` → `plugins/`
2. `ChimeraCompanion-0.1.0.jar` → `plugins/`

重启服务器即可。ChimeraCompanion 启动时会通过 RP Engine 的 `AddonContent.install()`
把本目录的 `assets/` 自动安装到 `plugins/RPEngine/content/chimera/resourcepack/assets/`，
再由 RP Engine 重新构建并下发给客户端。

**不需要手动下载、解压或启用 `ChimeraCompanion-ResourcePack.zip`，也不需要修改
`server.properties` 里的 `resource-pack` 配置。** 首次安装或 ChimeraCompanion 版本更新后
才会写入资源并重载 RP Engine；版本未变化时不会重复安装或重建资源包。

## 目录结构

```
resourcepack/
├── pack.mcmeta                              # 手动打包时的资源包元数据，格式 88
└── assets/
    └── chimera/
        ├── items/                           # 7 个 item_model 定义
        ├── models/item/                     # 7 个 Blockbench 导出的模型
        ├── sounds.json                      # chimera:speak 的 19 段随机语音
        ├── sounds/speak/                    # 19 个 .ogg 语音文件
        └── textures/item/                   # 模型贴图与调色板
```

引用链：

```
item_model 组件 = chimera:companion
  → assets/chimera/items/companion.json
    → model: chimera:item/companion
      → assets/chimera/models/item/companion.json
        → texture: chimera:item/companion
          → assets/chimera/textures/item/companion.png
```

六个 `ItemDisplay` 部件（body、head、四条腿）继续使用各自的
`chimera:<part>` 物品模型，并由插件现有的动画代码驱动；RP Engine 只负责资源包安装与下发，
不接管模型动画。

## 开发：手动构建资源包

正常服务器安装不需要这一步。需要单独测试资源包时，在项目根目录执行：

```bash
./gradlew resourcePack
```

输出：`build/resourcepack/ChimeraCompanion-ResourcePack.zip`（`pack.mcmeta` 位于压缩包根目录）。

客户端加载（单人/本地测试）：

1. 把 `ChimeraCompanion-ResourcePack.zip` 复制到 `.minecraft/resourcepacks/`。
2. 游戏内：选项 → 资源包 → 将「ChimeraCompanion 测试模型」移到右侧并启用。
3. 进服后执行 `/chimera model` 生成测试模型。

## 注意

- 构建插件 JAR 时，`resourcepack/assets/` 会被 Gradle 复制到
  `content/chimera/resourcepack/assets/`；`content/chimera/pack.yml` 由 RP Engine 读取。
- 模型 JSON 中已移除 `"parent": "builtin/entity"`，否则自定义 `item_model` 不会被渲染。
- `texture_size` 是 Blockbench 专用字段，游戏不会读取；UV 按 0–16 映射整张贴图。
- 贴图右上角色块里原本混入了附件预览的图标，已用该色块自身的颜色覆盖，避免模型左眼出现异常色块。
