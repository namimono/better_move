# Handoff readiness for implementation

Type: grilling  
Status: resolved  
Blocked by: 01, 02, 03, 04, 05, 06, 07  

## Question

审视 map Notes + 全部已关闭票：是否还存在 **不解决就无法负责任地开写** 的产品/技术决策？

- 若有：列出并开成新票（或升 fog），本票保持 open 直到清完。
- 若无：确认 destination 已达；写出给实现 session 的 **一页纸 handoff**（读哪些票、锁定约束摘要、建议实现顺序、明确非目标），作为本票 Answer 并关闭。

关闭本票 = 本 wayfinder map 的 route 走完。

## Answer

**Destination 已达。** 无「不解决就无法负责任开写」的产品/技术决策。以下为一页纸 handoff（实现 session 的入口）。

---

### 1. 先读什么（顺序）

| 顺序 | 路径 | 用途 |
|------|------|------|
| 1 | [蓄力与过载升级 map](../map.md) **Notes** | Charting 锁定的 8 条产品约束（不得无故推翻） |
| 2 | [`CONTEXT.md`](../../../CONTEXT.md) | 领域词（过载蓄力 / 蓄力 / 过载 / 释放 / 强制释放 / 过载爆炸 / 取消蓄力…） |
| 3 | [Edge-case matrix](07-edge-cases-matrix.md) | 生命周期矩阵（模式/环境/CD/UI/竞态） |
| 4 | [Balance numbers](03-balance-numbers.md) | 距离倍率、爆炸 power、自伤 |
| 5 | [Networking research](05-research-charge-networking.md) + [`docs/research/charge-overload-networking.md`](../../../docs/research/charge-overload-networking.md) | Start+Release、服务端时钟 |
| 6 | [Explosion research](06-research-explosion-api.md) + [`docs/research/charge-overload-explosion.md`](../../../docs/research/charge-overload-explosion.md) | `explode` 调用序列与碰撞钩子 |
| 7 | [Upgrade identity](02-upgrade-item-identity.md) | `CHARGE` / `charge_upgrade` / 名 / 配方档 |
| 8 | [Feedback acceptance bar](04-charge-ui-vfx-bar.md) | HUD/粒子 P0 验收 |

原始需求 `docs/过载&&蓄力升级实施方案.md` 仅作背景；**冲突时以 map Notes + 关闭票 Answer 为准**。

**优先级冲突（已知）**：research 爆炸笔记示例曾写 `4.0F` + `TNT`；**实现以 balance 票为准：`3.0F` + `Level.ExplosionInteraction.MOB` + `createFire=false`**，自伤 `generic` 4.0 另扣。

---

### 2. 锁定约束摘要

**形态**

- 新升级项 `BoosterUpgradeType.CHARGE` / item id `charge_upgrade`；未装 = 现状点按瞬发。
- 显示名 EN `Charge Upgrade` / ZH `过载蓄力`；tooltip 短一行（见 identity 票）；`stacksTo(1)` + 同类型禁重；配方档 = 空中冲刺同级。

**输入与时间**

- 按住 Z 蓄力，松手释放；0–3s 蓄力时长，3–5s 过载窗口；满 5s（100 tick）**强制释放**。
- 网络：**服务端时钟** `ChargeSession.startTick`；C2S 新空包 `BoosterChargeStartPayload`；开火复用 `BoosterRequestPayload`；**不**信客户端 chargeTicks；可选 cancel 包。
- 有过载蓄力时：无会话的 fire **忽略**（无点按旁路）。

**距离**

- `m = 1.0 + 0.8 * clamp(chargeSec / 3.0, 0, 1)`；过载段封顶 1.8×。
- `impulse *= m` 且 `thrustPerTick *= m`；`thrustTicks` 不变。

**过载爆炸**

- 仅过载推进（chargeTicks ≥ 60）；首次固体/实体撞 → 炸一次并结束推进；液体不炸不因此结束；松手不原地炸；全程未碰不炸不自伤。
- `level.explode(player, …, 3.0F, false, MOB)`；然后清 `invulnerableTime` + `hurt(generic, 4.0F)`。
- 过载 + 遁地：先完整遁地，**结束瞬间在当前位置炸一次**（0 格失败仍炸）；未过载 + 遁地只遁地。
- 碰实体：爆炸优先于推进破击（该帧不破击）。

**资源与取消**

- 仅成功释放起飞才写冷却 / 扣饥饿 / 耐久；取消免费；冷却中不能开蓄力。
- 开 UI / 卸装 / 死亡 / 旁观 / 睡觉 / 传送 / 饱食不足等 → 取消蓄力（见矩阵）。

**叠化 / 全局**

- 空中冲刺、无冷却、随机距离、垂直起飞、Hyper **可叠**；推进中再按 Z **忽略**。
- **贴墙探测**：去掉「前向探测失败则不推进」（**全局**，不限本升级）。

**反馈 P0**

- 热键栏旁推进 HUD 同区单条两段色蓄力轨；仅蓄力中显示；淡入淡出或缩放；蓄力/过载可区分粒子；不准星；爆炸用原版声画。

---

### 3. 建议实现顺序

1. **物品骨架**：`CHARGE` 枚举 + 注册 item/lang/recipe（对齐 `air_dash_upgrade`）+ 升级槽识别。
2. **网络 + 会话**：`BoosterChargeStartPayload`；服务端 `ChargeSession`；强制释放 tick；边缘矩阵的硬拒绝点。
3. **输入分支**：有过载蓄力 → `isDown` 边沿；无 → 保留 `consumeClick`。
4. **释放缩放**：`tryBoostFromKey` / balance 应用路径乘 `m`；过载 flag 写入 motion session。
5. **过载爆炸**：`BoosterMotionTicker.step` 首次 solid/entity 钩子 + 遁地结束路径；破击同帧优先级。
6. **全局贴墙**：去掉前向探测失败阻断（与升级解耦，可同 PR 或紧随）。
7. **HUD + 粒子 P0**：同区蓄力轨两段色 + 出场退场；可区分粒子。
8. **手测矩阵**：创造/旁观/CD/空中无空中冲刺/强制释放 vs 迟到松手/过载遁地/液体不炸。

PR 拆分、是否先做无 VFX 垂直切片 → 执行规划（map fog），非本 handoff 必选。

**主要挂点（现有代码）**

- `BoosterUpgradeType` / `BoosterUpgradeHelper` / 注册与配方  
- `BoosterInputHandler`（client）  
- `BoosterRequestPayload` + `BoosterMod` 网络注册  
- `BoosterLeggingsItem.tryBoostFromKey`  
- `BoosterMotionTicker`  
- `BoosterCooldownHud` / `BoosterHudRenderer`  
- 破击：`BoostStrikeHandler` / 碰撞同帧

---

### 4. 明确非目标（本功能 v1 / 本 map）

- 本 map 内写完并合并功能代码（map 已结束；实现另开 session）。
- 蓄力/过载做成全员基础能力。
- 过载段继续加远、松手原地炸、多次爆炸。
- 特殊技巧体系大改（Chain / Wall Reflect 等）。
- 其它新升级项（过热、摔落免疫等）。
- 领地/区域保护插件兼容策略；正式美术素材管线；非 P0 镜头抖/FOV/循环音效。
- 客户端权威蓄力时长；每 tick charge 心跳（v1 不采用）。

---

### 5. Fog（实现可后置，不挡开工）

见 map **Not yet specified**：插件兼容、美术管线、PR 拆分。

---

**结论**：route 走完。实现 session 从本 Answer + map Notes 开工即可。
