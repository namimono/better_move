# Upgrade item identity and craft

Type: grilling  
Status: resolved  
Blocked by: 01  

## Question

本升级项的 **物品 identity** 是什么？

- 英文 id / `BoosterUpgradeType` 枚举名（需与现有 `*_upgrade` 风格一致）
- 中英文显示名与 tooltip 要点（玩家如何理解蓄力 vs 过载）
- 合成配方大致成本档位（相对空中冲刺 / 破击：便宜 / 同级 / 更贵）
- 是否 `stacksTo(1)`、同类型禁重（默认跟随现有升级项规范即可确认）

不在此票定数值曲线（见 balance 票）。

## Answer

- **Enum / item id**: `CHARGE` / `charge_upgrade`（`boostermod:charge_upgrade`）
- **Display name**: EN `Charge Upgrade`；ZH `过载蓄力`（与 [Domain vocabulary for charge and overload](01-domain-vocabulary.md) 对齐；旧稿「蓄力升级项」作废）
- **Tooltip**（短一行，实现可微调措辞，信息量钉死为短）:
  - EN: Hold Boost to charge; full charge overloads and can explode on impact.
  - ZH: 按住推进键蓄力；蓄满后过载，撞击时可爆炸。
- **Craft cost tier**: 与 **空中冲刺**（`air_dash_upgrade`）**同级（偏便宜）**；本票不定具体材料/形状，实现时对齐该档。
- **Stack / slot rules**: 与现有升级项一致 — `stacksTo(1)`；升级槽同类型禁重（有/无布尔，不叠层）。
- **Out of this ticket**: 距离曲线、时间窗、爆炸半径、自伤等数值 → balance / 其它票。
