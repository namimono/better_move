# 02 — 饰品槽同样可见

**What to build:** 安装 Trinkets 时，玩家把推进器装进饰品槽也能看见与护腿槽同一套按等级区分的穿戴外观；可与原版裤子并存。

**Blocked by:** 01 — 护腿槽可见 + 物品栏图标 + 定位接缝

**Status:** resolved

- [x] 饰品槽装备五档推进器时均绘制穿戴外观
- [x] 与护腿槽使用同一套等级贴图定位（不另搞第二套路径规则）
- [x] 饰品槽推进器与原版护腿同时装备时，推进器穿戴外观仍可见
- [x] 未安装 Trinkets 时不影响 01 的护腿槽行为

## Answer

- 抽出 `BoosterWornLegsAppearance`：护腿槽 / 饰品槽共用内层护甲模型绘制，贴图只走 `BoosterAppearanceTextures.wornTexture(tier)`。
- 客户端 `BoosterTrinketRenderer` + `BoosterTrinketsClientCompat` 经 `TrinketRendererRegistry` 注册五档；`BoosterModClient` 仅在检测到 Trinkets 时反射加载（对齐服务端 `BoosterTrinketsCompat` 软依赖）。
- 未安装 Trinkets 时仍只走 `BoosterLegsArmorRenderer`；叠穿分层/Z-fighting 精修仍按规格 Out of Scope。
