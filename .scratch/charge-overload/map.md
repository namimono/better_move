# Wayfinder map: 蓄力与过载升级

Labels: `wayfinder:map`

## Destination

一份 **可直接开写代码的决策闭环**：蓄力 / 过载作为推进器 **升级项** 的产品规则、领域词、关键数值边界、网络与爆炸可行性均已决清；实现 session 不再卡在「要不要 / 怎么表现 / 和谁叠」上。  
**本 map 不交付可运行代码。**（研究 destination 已达；实现已按 [`spec.md`](spec.md) 交付并完结，见下方 Decisions。）

## Notes

- **领域**：Booster Mod（Minecraft 1.21.1 Fabric）；升级槽模型见 `BoosterUpgradeType` / `BoosterUpgradeHelper`；推进链路见 `BoosterInputHandler` → `BoosterRequestPayload` → `BoosterLeggingsItem.tryBoostFromKey` → `BoosterMotionTicker`。
- **每 session 应读**：本 map；`docs/过载&&蓄力升级实施方案.md`（原始 7 条）；已关闭票的 Answer；`CONTEXT.md`（若已有词条）；相关 ADR。
- **技能**：决策类默认 `/grilling` + `/domain-modeling`；外部 API 事实用 `/research`；需要可感行为时用 `/prototype`。
- **Charting 已锁定的产品约束**（实现与后续票不得无故推翻；要改先开新票）：
  1. **形态**：新升级项；未安装 = 现状点按瞬发。
  2. **输入**：按住 Z 蓄力，松开释放；满 **5s**（3s 蓄力 + 2s 过载）**强制释放**；开 UI / 卸装 / 死亡等 **取消蓄力**（不推进）。
  3. **距离**：0～3s **线性** 加远；3～5s 过载 **距离封顶**；点按极短蓄力 ≈ 现状基础距离。
  4. **过载爆炸**：仅过载推进；**首次**碰固体方块或实体炸 **一次** 后结束；水/岩浆等液体不炸、不因此结束；松手不原地炸；**自伤 2 心仅在爆炸时**；全程未碰则不炸不自伤。
  5. **叠化**：空中冲刺 / 无冷却 / 随机距离 / 垂直起飞 / Hyper 可叠；推进中再按 Z **忽略**；碰实体时爆炸优先于破击（该帧不破击）。
  6. **过载 + 遁地**：先完整遁地，**结束瞬间在当前位置炸一次**；遁地 0 格失败 **仍炸**；未过载 + 遁地只遁地不炸。
  7. **资源**：成功释放起飞才写冷却 / 扣饥饿 / 耐久；取消免费；冷却中不能开蓄力；爆炸自伤与资源分离。
  8. **贴墙探测**：去掉「前向探测失败则不推进」；贴墙也允许推进，撞墙就挡住（**全局**，不限本升级）——避免「推进器失灵」感。
- **原始需求**：`docs/过载&&蓄力升级实施方案.md`。

## Decisions so far

- [Research: explosion API for overload impact](issues/06-research-explosion-api.md) — Mojmap `explode` + 另扣自伤；流体不触发；钩 `BoosterMotionTicker.step`（power/交互以 balance 票为准，非笔记示例 4 TNT）。
- [Research: hold-to-charge networking on Fabric 1.21](issues/05-research-charge-networking.md) — Server-clocked Start+Release (new empty C2S start; reuse `BoosterRequestPayload` for fire); client `isDown` edges; no client chargeTicks authority.
- [Upgrade item identity and craft](issues/02-upgrade-item-identity.md) — `CHARGE`/`charge_upgrade`; Charge Upgrade / **过载蓄力**; short tooltip; craft ≈ air dash; `stacksTo(1)` + no duplicate type.
- [Domain vocabulary for charge and overload](issues/01-domain-vocabulary.md) — `CONTEXT.md` 词表：过载蓄力/蓄力/过载/蓄力时长/过载窗口/释放/强制释放/过载爆炸/取消蓄力；边界：推进·升级项·遁地·推进破击·Hyper。
- [Charge and overload feedback acceptance bar](issues/04-charge-ui-vfx-bar.md) — P0：同区单条两段色蓄力轨 + 淡入/缩放出场 + 可区分粒子；不准星；爆炸用原版；色值/音效/像素布局自由。
- [Charge distance and explosion balance numbers](issues/03-balance-numbers.md) — 0s 1.0× → 3s 1.8× 线性；impulse+thrustPerTick 同乘；爆炸 power 3 MOB+mobGriefing；自伤 generic 4。
- [Edge-case matrix for charge lifecycle](issues/07-edge-cases-matrix.md) — 蓄力生命周期矩阵：模式/环境/冷却饱食/UI卸装传送/空中无空中冲刺/强制释放竞态；取消免费；失败释放不结算。
- [Handoff readiness for implementation](issues/08-handoff-readiness.md) — Destination 已达；一页纸 handoff（读序、锁定摘要、实现顺序、非目标）；route 走完。
- [Spec: 过载蓄力升级项](spec.md) — 实现与验收完成，`Status: done`（2026-07-25）；单测 `./gradlew test` 通过，手测矩阵确认。

## Not yet specified

- 多人服领地 / 区域保护插件下爆炸破坏的兼容策略（原版侧已定跟 `mobGriefing`；第三方插件另议）。
- 正式 UI 贴图与音效素材制作流程（决策只钉「要有什么反馈」，不钉美术管线）。
- 实现拆分几个 PR / 是否先做无 VFX 的逻辑垂直切片（map 走完后的执行规划，非本 destination 必选）。

## Out of scope

- 在本 map 内写完并合并功能代码。
- 把蓄力 / 过载做成全员基础能力（已否决）。
- 过载段继续加距离、松手原地炸、多次爆炸（已否决）。
- 特殊技巧体系大改（Chain / Wall Reflect 等）；仅保留与 Hyper 可叠的既定态度。
- 其它未提及的新升级项（过热独立机制、摔落免疫等）。
