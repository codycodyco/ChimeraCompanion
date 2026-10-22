# ChimeraCompanion 可动模型拆分包

基于你定稿的 `ChimeraCompanion_Editable (1).bbmodel`，**未重新设计角色，未更改贴图、立方体坐标或结构**。将 72 个 Cube 分配为 6 个可独立旋转的模型。

## 内容

- `resourcepack/`：完整的 Java 26.2 资源包（`pack.mcmeta`、7 个 item 映射、7 个几何模型、**一张共享贴图**）。
- `blockbench_parts/`：六个独立可编辑的 `.bbmodel` 工程，仍内嵌源贴图。
- `source/`：你上传的定稿模型原文件备份。
- `rig_pivots.json`：每个部件的 Blockbench 旋转原点与换算后局部坐标。

## 模型 ID

| 部件 | 模型 ID | Cube 数量 |
| --- | --- | ---: |
| body | `chimera:body` | 19 |
| head | `chimera:head` | 42 |
| front_left_leg | `chimera:front_left_leg` | 3 |
| front_right_leg | `chimera:front_right_leg` | 3 |
| back_left_leg | `chimera:back_left_leg` | 2 |
| back_right_leg | `chimera:back_right_leg` | 3 |
| 原整体模型 | `chimera:companion` | 72 |

`body` 包含主体 + 尾巴 + 铃铛；`head` 包含头部 + 两只耳朵；四条腿各自独立。

## 安装与测试

1. 把 `resourcepack/` 目录**里面的内容**（`pack.mcmeta` 与 `assets`）压缩成 zip，或直接使用同目录提供的 `ChimeraCompanion_ResourcePack_26_2.zip`。
2. Java 客户端加载资源包。
3. DSH / 插件生成 6 个 ItemDisplay，每个对应一个 `ItemMeta.setItemModel(NamespacedKey("chimera", partName))`。物品材质可用 `PAPER`，展示模式统一 `FIXED`。
4. 第一轮**不要旋转任何关节**，先让所有部件都和当前完整模型使用**完全相同的位置、方向、高度补偿及 Transform**。六个模型拼起来应与原 `chimera:companion` 一致。
5. 静态拼接验收通过后，单独旋转 `head`，并基于 `rig_pivots.json` 中的 Pivot 进行局部旋转补偿。

## 旋转原点特别注意

- 拆分 JSON 的几何坐标保持在**原模型公共坐标系**，并没有把每个部件重新移到 `[0,0,0]`。
- 原 `ItemDisplay` 本身没有 Blockbench 的骨骼定义。`rig_pivots.json` 只提供**旋转原点数据**，你需要在 Paper 端写少量 `Transformation` 逻辑。
- 假设某个部件的局部旋转为 R，Pivot 为 p，那么围绕 Pivot 旋转时的附加平移应为 `p - R*p`。`p` 的单位是模型空间的方块，而不是像素，且应该与 `ItemDisplay` 的原先缩放、根朝向一致。
- `FIXED` 物品展示变换和已有模型缩放可能导致本地坐标与世界坐标的变换差异；先静态拼装并做 +15° 的单轴测试，验收后再补全四足步态。

## 注意

- 这是 **Minecraft Java 26.2** 的资源包，`pack.mcmeta` 采用资源包格式 88。
- 不包含基岩版模型转换。
- 虽然所有 JSON/贴图引用、UV 坐标和 Cube 数量已经离线验证，**这里未启动 Minecraft 客户端实测渲染**。
- 分件以后不必安装 RP Engine；但单独的模型 JSON **不会自动播放 Blockbench 骨骼动画**。
- 如果项目中 `resourcepack/` 已有其它资产，合并 `assets/chimera/` 和 `pack.mcmeta`，不要直接覆盖现有资源包。
