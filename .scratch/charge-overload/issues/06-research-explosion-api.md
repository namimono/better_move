# Research: explosion API for overload impact

Type: research  
Status: resolved  
Blocked by:  

## Question

在 **Minecraft 1.21.1（Yarn/Mojmap 以本仓库 Loom 映射为准）** 上，如何实现「过载推进首次碰固体/实体 → 一次 TNT/苦力怕风格爆炸 + 玩家自伤 4 HP」？

需回答（primary source）：

1. 创建爆炸的服务端 API（`ServerLevel.explode` 或等价）、参数含义、power 与苦力怕/TNT 对照。
2. 如何造成方块破坏 + 实体伤害；如何尊重 `mobGriefing` / 创造模式 / 友好火焰等。
3. 对 **引爆者玩家** 扣固定 4 HP：用爆炸本身还是 `hurt` 另扣；避免双倍伤或无敌帧吞伤。
4. 碰撞检测：推进中区分固体方块、实体、流体；流体不触发的可靠判据（`isInWater` / fluid tags / collision shape）。
5. 与 `BoosterMotionTicker` 现有 `horizontalCollision` 结束推进的衔接点。

产出：research 笔记路径 + 推荐默认调用序列（伪代码级即可）。

## Answer

**笔记**: [`docs/research/charge-overload-explosion.md`](../../../docs/research/charge-overload-explosion.md)

**API (Mojmap 1.21.1)**: `ServerLevel`/`Level.explode(Entity, x, y, z, power, createFire, Level.ExplosionInteraction)`. Vanilla TNT = power `4.0F`, fire `false`, interaction `TNT`; creeper normal = `3.0F` + `MOB` (gated by `mobGriefing`).

**默认序列（过载 + 首次固体/实体撞击）**:
1. 检测：`player.horizontalCollision` **或** `level.getEntities(player, bb.inflate(0.1), …)` 非空；**不要**用 `isInWater`/`isInLava` 触发或结束。
2. `level.explode(player, x, midY, z, 4.0F, false, Level.ExplosionInteraction.TNT)` — 玩家作 source，自身不进 AOE 伤列表。
3. `player.invulnerableTime = 0` 后 `player.hurt(player.damageSources().generic(), 4.0F)`（`generic` 在 `bypasses_armor`，固定 2 心）。
4. 在 `BoosterMotionTicker.ActiveBoost#step` 里 `return true` → 既有 `stopBoost`。

**不炸**：非过载、仅流体、推力耗尽未碰、松手原地。实体撞击优先于该帧破击结算。
