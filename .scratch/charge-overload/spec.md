# Spec: 过载蓄力升级项

Status: ready-for-agent

## Problem Statement

推进器护腿目前只有点按瞬发推进。玩家无法通过按住推进键蓄力来换取更远距离，也没有「过载」这档有代价的高风险释放。产品已决定用新升级项「过载蓄力」补上这条玩法，但实现前需要一份可照着做的规格：物品身份、输入与网络权威、距离与爆炸数值、生命周期边界、反馈验收与叠化规则都已在研究 map 中钉死，本规格把它们收成实现与验收的单一真相源。

## Solution

新增升级项 **过载蓄力**（`CHARGE` / `charge_upgrade`）。装上后：按住推进键进入蓄力，松开释放；0–3s 线性加远，3–5s 进入过载（距离封顶），满 5s 强制释放。过载推进首次碰到固体方块或实体时触发一次过载爆炸并自伤 2 心；取消蓄力免费。未安装时行为与现状点按瞬发完全一致。蓄力时长由服务端时钟权威计算；客户端只负责按键边沿与反馈。

主测试接缝为 **过载蓄力会话策略**（`ChargeSession` / 等价模块）：时间窗、倍率、过载标记、取消/强制释放/迟到包、资源是否结算等纯规则都经这一接口表达与验证。网络、运动 tick、爆炸、HUD 作为适配器挂在其上。

## User Stories

1. As a 生存玩家, I want 把过载蓄力装进推进器升级槽, so that 我的推进键变成按住蓄力、松开释放
2. As a 玩家, I want 未安装过载蓄力时仍是点按瞬发, so that 现有手感不被强制改变
3. As a 玩家, I want 按住推进键开始蓄力, so that 我可以主动选择蓄多久再飞
4. As a 玩家, I want 松开推进键完成释放并起飞, so that 蓄力结束变成一次推进
5. As a 玩家, I want 极短蓄力释放时距离≈现状一次推进, so that 点按习惯不会突然变弱
6. As a 玩家, I want 蓄力越久（在蓄力时长内）飞得越远, so that 蓄力有明确回报
7. As a 玩家, I want 蓄力时长结束进入过载后距离不再继续加远, so that 过载是危险档而不是无限加距
8. As a 玩家, I want 过载窗口耗尽时系统强制释放, so that 我不会无限按住卡在蓄力态
9. As a 玩家, I want 在热键栏旁推进 HUD 同区看到单条两段色蓄力轨, so that 我知道自己处于蓄力还是过载
10. As a 玩家, I want 蓄力轨只在蓄力（含过载窗口）时出现并有淡入/缩放, so that 不会和 CD/耐久/破击抢阅读，也不会硬切闪现
11. As a 玩家, I want 蓄力与过载有可区分的人物粒子, so that 第三人称/他人也能看出阶段
12. As a 玩家, I want 不准星处出现蓄力进度, so that 瞄准视野不被挡
13. As a 玩家, I want 过载推进撞到固体方块时炸一次, so that 过载有落地风险与破坏力
14. As a 玩家, I want 过载推进撞到实体时炸一次, so that 撞击生物也有过载代价与效果
15. As a 玩家, I want 过载爆炸只发生一次随后结束推进, so that 不会连环炸
16. As a 玩家, I want 穿过水/岩浆等液体时不触发过载爆炸也不因此结束推进, so that 液体不会误伤玩法
17. As a 玩家, I want 松手释放时不在原地爆炸, so that 爆炸只绑定撞击而不是释放瞬间
18. As a 玩家, I want 过载推进全程未碰固体/实体时不炸不自伤, so that 空中耗尽推力没有惩罚
19. As a 玩家, I want 过载爆炸时固定自伤 2 心, so that 过载有明确个人代价
20. As a 玩家, I want 取消蓄力不消耗冷却、饥饿与耐久, so that 开 UI/卸装/死亡等中止是免费的
21. As a 玩家, I want 只有成功释放并起飞后才写冷却、扣饥饿与耐久, so that 失败释放不会白扣资源
22. As a 玩家, I want 冷却中不能开始蓄力, so that 冷却规则与现状一致
23. As a 玩家, I want 装了无冷却时仍可蓄力释放且不写冷却, so that 与无冷却升级项可叠
24. As a 玩家, I want 推进中再按推进键被忽略, so that 不会打断或叠开第二次推进
25. As a 玩家, I want 已装过载蓄力时无蓄力会话的开火包被忽略, so that 不能绕过蓄力走点按旁路
26. As a 玩家, I want 与空中冲刺、随机距离、垂直起飞、Hyper 可同时生效, so that 过载蓄力是叠加能力而不是互斥体系
27. As a 玩家, I want 过载遁地时先完整遁地、结束瞬间在当前位置炸一次, so that 遁地与过载爆炸有明确顺序
28. As a 玩家, I want 过载遁地 0 格失败时仍然爆炸, so that 过载代价不会因为遁地失败消失
29. As a 玩家, I want 未过载的遁地只遁地不炸, so that 爆炸严格绑定过载
30. As a 玩家, I want 过载撞实体的那一帧爆炸优先于推进破击, so that 破击不会和过载爆炸抢同一帧结算
31. As a 玩家, I want 贴墙时仍能开始推进、撞墙就挡住, so that 不会感觉「推进器失灵」
32. As a 玩家, I want 旁观/死亡/睡觉时不能蓄力，蓄力中进入这些状态则取消蓄力, so that 非法状态不会起飞
33. As a 玩家, I want 打开任意容器/GUI/升级 UI 时取消蓄力, so that UI 操作不会误释放
34. As a 玩家, I want 卸下护腿、Trinket 或过载蓄力升级项时取消蓄力, so that 失去能力后不会残留会话
35. As a 玩家, I want 切换维度或同维传送时取消蓄力, so that 传送不会带着半截蓄力起飞
36. As a 玩家, I want 饱食不足时不能蓄力/释放（非创造）, so that 与现有推进资源门槛一致
37. As a 玩家, I want 空中且无空中冲刺时仍可蓄力，但释放若不满足起飞条件则失败结束会话且不结算, so that 蓄力本身不被环境特判禁掉
38. As a 玩家, I want 创造模式下蓄力与释放可用且资源结算跟现有推进, so that 创造体验一致
39. As a 玩家, I want 游泳/潜水/岩浆/鞘翅中不额外禁止蓄力, so that 不新增环境黑名单
40. As a 玩家, I want 服务端强制释放后迟到的松手包被忽略, so that 网络抖动不会双释放或误结算
41. As a 玩家, I want 同 tick 取消与强制释放冲突时取消优先, so that 开 UI 等取消不会被超时起飞顶掉
42. As a 玩家, I want 在合成里用与空中冲刺同级成本做出过载蓄力, so that 获取难度符合「偏便宜」档
43. As a 玩家, I want 物品显示名为 Charge Upgrade / 过载蓄力，并有短 tooltip 说明按住蓄力与过载撞击可炸, so that 我开箱就能理解核心规则
44. As a 玩家, I want 同类型升级项不能重复安装且 stacksTo(1), so that 槽位规则与其它升级项一致
45. As a 服务端管理员, I want 蓄力时长由服务端时钟计算而不信客户端 tick 数, so that 作弊面更小
46. As a 附近玩家, I want 看到他人蓄力/过载粒子与原版爆炸声画, so that 多人场面可读
47. As a 实现者, I want 距离倍率同时乘在 impulse 与 thrustPerTick 上且 thrustTicks 不变, so that 「更远」来自更强冲量而非更长推力时间
48. As a 实现者, I want 过载爆炸用 power 3、MOB 交互、跟 mobGriefing、不点火, so that 破坏力接近苦力怕并尊重游戏规则
49. As a 玩家, I want 自伤走 generic 4.0 且在爆炸后清 invulnerableTime 再扣, so that 2 心代价稳定打出、不被无敌帧吞掉
50. As a 玩家, I want HUD/粒子达到 P0 即可，色值与音效细节可后调, so that 功能可先交付再打磨观感

## Implementation Decisions

1. **形态**：新升级项 `BoosterUpgradeType.CHARGE`，物品 id `charge_upgrade`（`boostermod:charge_upgrade`）。未安装 = 现状点按瞬发；已安装 = 仅蓄力路径，无会话的开火忽略。
2. **展示**：EN `Charge Upgrade` / ZH `过载蓄力`；tooltip 短一行（EN: Hold Boost to charge; full charge overloads and can explode on impact. / ZH: 按住推进键蓄力；蓄满后过载，撞击时可爆炸。）；`stacksTo(1)`；升级槽同类型禁重；合成成本对齐空中冲刺档（具体材料形状实现时对齐该档即可）。
3. **领域用词**：一律使用 `CONTEXT.md` 词表——过载蓄力、蓄力、过载、蓄力时长、过载窗口、释放、强制释放、过载爆炸、取消蓄力、推进、升级项、遁地、推进破击、Hyper。避免「自动释放 / 充能 / 过热」等已否决同义。
4. **时间窗**：蓄力时长 60 tick（3s）；过载窗口再 40 tick（2s）；满 100 tick 强制释放。服务端 `ChargeSession.startTick` 权威；`chargeTicks = clamp(now - start, 0, 100)`。
5. **网络**：新空 C2S `BoosterChargeStartPayload`（可选 cancel 包）；开火复用现有 `BoosterRequestPayload`（方向 / jump / landing 字段不变）。不采用客户端 `chargeTicks` 权威，不采用每 tick 心跳（v1）。
6. **客户端输入**：有过载蓄力时对推进键做 `isDown` 边沿（PRESS→start，RELEASE→fire）；无升级项保留 `consumeClick`。蓄力期间排空 `consumeClick`，避免松手后多打一发瞬发。开 UI（`screen != null`）等走取消。
7. **拒绝点**：客户端可软拒绝以改善手感；服务端硬拒绝——未装备推进器、未装过载蓄力、冷却中（除非无冷却）、已在推进、旁观/死亡/睡觉、饱食不足（非创造）、重复会话等。释放失败：清会话、不推进、不结算。
8. **距离倍率**：`m = 1.0 + 0.8 * clamp(chargeSec / 3.0, 0, 1)`；过载段封顶 1.8×。应用到 `impulse *= m` 与 `thrustPerTick *= m`；`thrustTicks` 不变。过载标记（chargeTicks ≥ 60）写入本次推进会话，供爆炸路径消费。
9. **资源**：仅成功释放并起飞后写冷却 / 扣饥饿 / 耐久；创造 `instabuild` 跟现有推进；取消蓄力与失败释放免费。
10. **过载爆炸**：仅过载推进；首次固体方块或实体撞击触发一次；液体不触发也不因此结束；松手不原地炸；未碰则不炸不自伤。调用：`level.explode(player, x, midY, z, 3.0F, false, Level.ExplosionInteraction.MOB)`，然后 `invulnerableTime = 0` + `hurt(generic, 4.0F)`。钩在运动步进（首次 solid/entity）与遁地结束路径。
11. **过载 + 遁地**：先完整遁地，结束瞬间在当前位置炸一次；遁地 0 格失败仍炸；未过载 + 遁地只遁地不炸。
12. **推进破击**：过载撞实体的同一帧，过载爆炸优先，该帧不结算破击。
13. **叠化**：空中冲刺 / 无冷却 / 随机距离 / 垂直起飞 / Hyper 可叠；推进中再按 Z 忽略。
14. **全局贴墙**：去掉「前向探测失败则不推进」；贴墙允许推进，撞墙挡住。此条为全局行为变更，不限本升级项。
15. **反馈 P0**：热键栏旁推进 HUD 同区单条两段色蓄力轨（蓄力段一色，过载第二色，可加脉冲）；仅蓄力中显示；淡入淡出和/或缩放；蓄力/过载可区分粒子；不准星；爆炸用原版声画。色值、像素精位、镜头抖、FOV、循环音效非 P0。
16. **主模块接缝**：引入 **过载蓄力会话策略** 模块（建议名 `ChargeSession`），对外小接口覆盖：开始 / 每 tick（含强制释放判定）/ 释放 / 取消；输出至少包括——是否仍在会话、chargeTicks、倍率 `m`、是否过载、是否应强制释放、释放是否允许尝试起飞、资源是否应在成功起飞后结算。世界侧条件（装备、冷却、推进中、游戏模式等）作为输入，不在调用方复制时间/倍率规则。
17. **挂点（概念层，非文件契约）**：升级类型与物品注册；客户端输入；C2S 注册与接收；推进释放入口（倍率与过载标记）；运动 tick / 遁地结束（爆炸）；破击同帧优先级；HUD/粒子；前向探测移除。
18. **冲突裁定**：原始需求文档仅作背景；与 map Notes / 已关闭票冲突时以 Notes + 票 Answer 为准。爆炸 research 笔记若仍写 4.0F+TNT，以实现/本规格的 **3.0F + MOB** 为准。
19. **建议实现顺序**（非强制 PR 切分）：物品骨架 → 网络+会话 → 输入分支 → 释放缩放 → 过载爆炸（含遁地）→ 全局贴墙 → HUD/粒子 P0 → 按边界矩阵手测。

## Testing Decisions

1. **好测试的标准**：只断言经 **过载蓄力会话策略** 接口可见的外部行为（状态迁移、倍率、过载标记、强制释放、取消优先、迟到释放忽略、资源结算许可），不断言内部字段布局、包类名或 HUD 像素。
2. **主测模块**：过载蓄力会话策略（唯一主接缝）。用固定 tick 时钟与显式事件驱动；覆盖——0s/3s/5s 倍率与过载边界；取消免费；失败释放不结算；强制释放；取消 vs 强制释放同 tick；会话已清后的释放忽略。
3. **适配器层**：网络边沿、`explode` 调用、遁地结束炸、破击同帧优先级、贴墙推进、HUD 两段色——仓库当前无自动化测试基础设施，以手测矩阵验收（创造/旁观/CD/空中无空中冲刺/强制释放 vs 迟到松手/过载遁地/液体不炸/贴墙可推进）。若后续引入测试框架，仍优先加会话策略测试，不为渲染与原版爆炸 API 硬造脆弱测试。
4. **先验**：项目目前 `./gradlew test` 为 NO-SOURCE；本功能不要求先搭完整游戏内集成测试框架才能开工。新增纯 Java 单元测试仅针对会话策略即可。

## Out of Scope

- 把蓄力/过载做成全员基础能力（未装升级项仍点按）
- 过载段继续加远、松手原地炸、多次爆炸
- 特殊技巧体系大改（Chain / Wall Reflect 等）；仅保留与 Hyper 可叠
- 其它新升级项（过热独立机制、摔落免疫等）
- 领地/区域保护等第三方插件下的爆炸破坏兼容策略（原版侧已定跟 `mobGriefing`）
- 正式 UI 贴图与音效素材制作管线；非 P0 的镜头抖 / FOV / 蓄力循环音效
- 客户端权威蓄力时长；每 tick charge 心跳（v1）
- 本规格不要求一次交付完美美术，达到反馈 P0 即验收通过

## Further Notes

- 决策来源：`.scratch/charge-overload/map.md` Notes（8 条产品约束）及 issues 01–08 全部 resolved Answer；详细 API 事实见 `docs/research/charge-overload-networking.md` 与 `docs/research/charge-overload-explosion.md`。
- 实现入口可读序见 `issues/08-handoff-readiness.md` Answer；本 `spec.md` 为 AFK/实现 session 的规格真源，handoff 票作导航。
- Fog（不挡开工）：插件兼容、美术管线、PR 是否先做无 VFX 垂直切片——执行时自行决定即可。
