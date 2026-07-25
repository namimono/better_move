# 03 — Blockbench 正式穿戴与物品栏贴图

**What to build:** 用 Blockbench 按原版护甲 UV 画出五档（铜、铁、金、钻石、下界合金）统一推进器剪影的正式穿戴外观与配套物品栏图标，导出 PNG 落到约定资源位置。可与 01 并行；不依赖渲染代码是否已接线。

**Blocked by:** None — can start immediately

**Status:** resolved

- [x] 五档穿戴外观 PNG：统一推进器剪影，材质色/纹路随等级变化
- [x] 五档物品栏图标 PNG：沿用既有资源、等级可辨（后续决定不与穿戴外观强制同风格）
- [x] 资源命名/路径与规格中的定位约定一致，替换后无需改渲染逻辑即可生效（或与 01 的占位同路径）
- [x] 客户端加载无紫黑缺失贴图；并排对比五档可分辨等级

## Answer

### 资源路径（对齐 01 / `BoosterAppearanceTextures`）

五档共用推进器剪影；只换材质色与细节色。直接替换 01 占位路径，无需改渲染逻辑：

| 用途 | 路径 |
| --- | --- |
| 穿戴外观（护甲 layer_2，64×32） | `assets/boostermod/textures/models/armor/booster_leggings_{tier}_layer_2.png` |
| 物品栏图标（沿用既有，非 Blockbench 重绘） | `assets/boostermod/textures/item/booster_leggings_{tier}.png` |

对应定位：

- 穿戴：`boostermod:textures/models/armor/booster_leggings_{tier}_layer_2.png`
- 物品栏：`boostermod:item/booster_leggings_{tier}`

### 制作说明

- UV 遮罩对齐原版 `iron_layer_2`（64×32，不透明像素 280）。
- 统一剪影细节：外侧喷嘴、腿背通风口、腰带与背部推进单元、膝部金属片。
- 五档调色：铜（氧化青绿喷嘴）/ 铁（蓝喷嘴）/ 金（橙喷嘴）/ 钻石（亮青）/ 下界合金（暗底橙红喷嘴）。
- 在 Blockbench 项目 `booster-worn-appearance` 中做喷嘴描边与铆钉等像素润色后导出。

### 交付文件

```
src/main/resources/assets/boostermod/textures/models/armor/booster_leggings_{copper,iron,gold,diamond,netherite}_layer_2.png
```

物品栏图标路径仍由 `BoosterAppearanceTextures.inventoryIcon` 指向既有资源，但 PNG 内容保留 03 之前的原套，不随 Blockbench 穿戴图替换。

## Comments

- 后续调整：物品栏 UI 图标改回原来那一套；穿戴外观继续用 Blockbench 正式图。接受穿戴与物品栏风格不统一。
