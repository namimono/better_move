# Wayfinder map: 推进器穿戴外观

Labels: `wayfinder:map`

## Destination

五档推进器（铜～下界合金）有统一剪影的原版护甲感穿戴外观；物品栏沿用既有图标（可不与穿戴同风格）；护腿槽与饰品槽装备时均可见；不加护甲值。

## Notes

- 规格：[`spec.md`](spec.md)；ADR：`docs/adr/0001-worn-appearance-without-armor-defense.md`、`docs/adr/0002-render-booster-in-legs-and-trinket-slots.md`。
- 定位接缝：`BoosterAppearanceTextures`（等级 → 贴图路径）。

## Decisions so far

- [01 — 护腿槽可见 + 物品栏图标 + 定位接缝](issues/01-legs-slot-appearance.md) — `BoosterAppearanceTextures` + `BoosterLegsArmorRenderer`；占位路径 `booster_leggings_<tier>_layer_2.png` / `item/booster_leggings_<tier>`。
- [03 — Blockbench 正式穿戴与物品栏贴图](issues/03-blockbench-final-art.md) — 正式穿戴 PNG 替换占位；物品栏改回沿用既有图标，不要求与穿戴同风格。
- [02 — 饰品槽同样可见](issues/02-trinket-slot-appearance.md) — `BoosterWornLegsAppearance` 共用绘制；`BoosterTrinketRenderer` 软依赖注册五档；贴图仍走 `BoosterAppearanceTextures`。

## Fog of war

（无未解阻塞项；本 effort 三票已 resolved。）
