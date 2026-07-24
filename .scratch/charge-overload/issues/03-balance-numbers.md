# Charge distance and explosion balance numbers

Type: grilling  
Status: resolved  
Blocked by:  

## Question

锁定实现可编码的 **数值边界**（允许写「先用 X，后续 balance 命令可调」，但必须有默认值）：

1. **0s 蓄力释放**：相对当前 `BoosterBalanceProfile` 的距离倍率（推荐默认 `1.0` = 现状）。
2. **3s 满蓄（非过载上限距离）**：相对现状的倍率上限（需可感知「更远」，例如 `1.5`～`2.5` 区间内选一个默认）。
3. 曲线已锁定为 **线性**；确认是乘在 impulse、thrustTicks、还是二者同乘。
4. **过载爆炸**：`Explosion` power（苦力怕约 3、TNT 约 4）默认取多少；是否破坏方块（已倾向破坏）；是否受 `mobGriefing` 约束。
5. **自伤**：固定 4.0 生命（2 心），伤害类型（generic / explosion / 自定义）倾向。

Charting 约束：过载段 **不再** 加距离；自伤仅爆炸时。

## Answer

默认数值（实现可先硬编码；后续若加 balance 配置，以这些为默认）：

| 项 | 值 |
|----|-----|
| 蓄力 0s 释放倍率 | **1.0×**（= 现状一次推进） |
| 蓄力 3s 满蓄倍率 | **1.8×** |
| 3～5s 过载段距离 | **封顶在 1.8×**（不再加远） |
| 插值 | **线性**：`m = 1.0 + 0.8 * clamp(chargeSec / 3.0, 0, 1)` |
| 作用层 | **`impulse *= m` 且 `thrustPerTick *= m`；`thrustTicks` 不变** |
| 过载爆炸 power | **3.0F** |
| 破方 / 交互 | **是**；`Level.ExplosionInteraction.MOB`（跟 **`mobGriefing`**）；`createFire = false` |
| 自伤 | **`player.hurt(generic, 4.0F)`**（2 心）；爆炸后清 `invulnerableTime`；仅爆炸发生时 |

与 research 笔记衔接：AOE 用 `level.explode(player, …, 3.0F, false, MOB)`；自伤不走爆炸 AOE（source 为玩家时已被排除）。
