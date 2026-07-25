# 01 — 护腿槽可见 + 物品栏图标 + 定位接缝

**What to build:** 玩家把铜～下界合金五档推进器装进护腿槽后能看见穿戴外观；物品栏里能看到对应等级的物品栏图标（不再紫黑）。外观只跟等级走、不加护甲值。提供可单测的「等级 → 贴图定位」接缝。贴图可用占位图；若 03 已产出正式图则可直接用。

**Blocked by:** None — can start immediately

**Status:** resolved

- [x] 五档在护腿槽装备时均有可见的穿戴外观，剪影一致、材质可辨
- [x] 五档物品栏图标均存在且指向正确资源，无缺失贴图
- [x] 等级 → 穿戴/物品栏贴图定位有单元测试（五档路径正确且互异）
- [x] 装备后仍不提供护甲值（对齐 ADR-0001）
- [x] 安装升级项或推进/蓄力/过载不改变穿戴贴图（本票范围内保持静态）

## Answer

- 新增 `BoosterAppearanceTextures`（等级 → 穿戴外观 / 物品栏图标 `ResourceLocation`），单测覆盖五档路径正确且互异；定位只吃 `BoosterTier`，与升级项无关。
- 客户端 `BoosterLegsArmorRenderer` 经 Fabric `ArmorRenderer` 在护腿槽画内层护甲模型 + `*_layer_2.png` 占位穿戴贴图；物品仍非 `ArmorItem`（ADR-0001 回归测试）。
- 物品栏图标沿用已有 `textures/item/booster_leggings_*.png`；穿戴占位在 `textures/models/armor/booster_leggings_*_layer_2.png`，可供 03 直接替换。
