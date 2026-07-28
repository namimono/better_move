# 05 — 补齐创造反馈与玩法边界

**What to build:** 创造模式玩家能够感知与生存模式相同的破壁代价节奏但不损失生命，同时完整锁定破壁推进与现有资源、实体伤害和升级能力之间的边界。

**Blocked by:** 04 — 落实生命代价与装备生命周期.

**Status:** ready-for-human

- [x] 创造模式玩家在首次进入墙体及此后每 10 个持续破壁 tick 收到可见、可听的受伤反馈，但真实生命值始终不变。
- [x] 创造反馈不通过先扣血再回血实现，切换游戏模式后不会留下错误生命状态。
- [x] 一次推进仍只结算现有耐久、饥饿和冷却成本；破坏方块数量和破壁时长不会增加耐久或饥饿消耗。
- [x] 普通破壁推进碰到实体时不会自动造成身体碰撞伤害，推进破击仍只由主动近战命中触发。
- [x] 破壁升级项不会改变遁地、Hyper、普通过载蓄力及其他未在规格中声明组合行为的既有规则。
- [x] 服务端 GameTest 覆盖创造反馈、生命值不变、资源结算和实体无碰撞伤害，并通过完整构建与既有测试回归。
- [ ] 人工冒烟验证核心破壁体验稳定，且未引入破壁专用 HUD、持续过载爆炸或其他规格外能力。

## Comments

### 2026-07-28 — agent

已交付 issue 05（自动化部分）：

- `WallBreakSupport.applyHealthCost`：创造模式只走 `playHurtFeedback`（`animateHurt` + `indicateDamage` + `PLAYER_HURT`），不改真实生命、不先扣再回；生存/冒险仍直接扣 1 点并共用同一反馈。
- 资源边界：破壁清方路径不额外 `hurtAndBreak` / `causeFoodExhaustion`；过载推进仍 `wallBreak = !overloaded`。
- 实体：破壁运动不新增身体碰撞伤害（GameTest 猪路径血量不变）。
- GameTest `wallBreak_creativeBoundary` 6 项：创造反馈/间隔、假伤切换模式、耐久饥饿、实体无伤、过载不走破壁扣血。
- `./gradlew test`、`./gradlew runGameTest`（87）、`./gradlew build` 均通过。
- 仍待人工：创造模式受伤动画/音效/屏幕反馈与核心破壁体验冒烟。
