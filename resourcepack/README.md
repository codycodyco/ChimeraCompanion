# ChimeraCompanion 资源包（P01/P02 测试用）

目标客户端：Minecraft Java 26.2，资源包格式 **88**。

## 目录结构

```
resourcepack/
├── pack.mcmeta                              # 资源包元数据，格式 88
└── assets/
    └── chimera/
        ├── items/
        │   └── companion.json               # 物品模型定义：chimera:companion
        ├── models/
        │   └── item/
        │       └── companion.json           # Blockbench 导出的原始模型
        └── textures/
            └── item/
                └── companion.png            # 128x128 调色板贴图
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

## 打包

在项目根目录执行：

```bash
./gradlew resourcePack
```

输出：`build/resourcepack/ChimeraCompanion-ResourcePack.zip`（`pack.mcmeta` 位于压缩包根目录）。

## 客户端加载（单人/本地测试）

1. 把 `ChimeraCompanion-ResourcePack.zip` 复制到 `.minecraft/resourcepacks/`。
2. 游戏内：选项 → 资源包 → 将「ChimeraCompanion 测试模型」移到右侧并启用。
3. 进服后执行 `/chimera model` 生成测试模型。

## 服务器下发（多人测试）

把 zip 放到任意 HTTP 可访问地址，然后在 `server.properties` 里设置：

```properties
resource-pack=https://example.com/ChimeraCompanion-ResourcePack.zip
resource-pack-sha1=<zip 的 sha1>
resource-pack-prompt={"text":"需要 ChimeraCompanion 资源包才能看到小奇美拉","color":"gold"}
```

## 注意

- 模型 JSON 中已移除 `"parent": "builtin/entity"`，否则自定义 `item_model` 不会被渲染。
- `texture_size` 是 Blockbench 专用字段，游戏不会读取；UV 按 0–16 映射整张贴图。
- 贴图右上角色块里原本混入了附件预览的图标，已用该色块自身的颜色覆盖，避免模型左眼出现异常色块。
