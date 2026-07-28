# 02 — 打通单堵直线墙体

**What to build:** 安装破壁升级项的玩家可以通过一次普通推进打通单堵直线墙体并继续前进，同时保留合理的掉落和不可破坏边界；未安装升级项的玩家行为不变。

**Blocked by:** 01 — 新增可获取、可安装的破壁升级项.

**Status:** ready-for-human

- [x] 未安装破壁升级项时，普通非过载推进碰到水平墙体仍按现有规则结束。
- [x] 安装破壁升级项后，推进能够穿过由正常可破坏方块构成的单堵直线墙体。
- [x] 直线破壁只清除玩家身体通过所需的方块，形成约 1 格宽、2 格高的可通行通道。
- [x] 石头、矿石等通常要求正确工具的方块仍产生无附魔基础掉落，不应用玩家手持物、精准采集或时运效果。
- [x] 方块更新、方块实体清理和掉落实体生成遵守正常世界语义。
- [x] 基岩等破坏速度为负的不可破坏方块保持不变并终止当前推进。
- [x] 服务端 GameTest 从玩家位置、推进状态、方块状态和掉落实体证明上述行为。

## Comments

### 2026-07-28 — agent

已交付 issue 02：

- `WallBreakSupport`：沿碰撞体扫掠清可破坏阻挡物；无附魔钻石镐上下文生成基础掉落；不可破坏方块终止推进。
- `BoosterMotionTicker`：破壁资格分支；撞墙清方并恢复前向速度；GameTest FakePlayer 经世界实体回退解析。
- `WallBreakBoostGameTest` 5 项 + 补注册 `WallBreakUpgradeGameTest`；`PhysicsFakePlayer` 提供位移碰撞。
- `./gradlew test`、`./gradlew runGameTest`（63）、`./gradlew build` 均通过。
