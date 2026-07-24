# Research: hold-to-charge networking on Fabric 1.21

Type: research  
Status: resolved  
Blocked by:  

## Question

在 **Minecraft 1.21.1 + Fabric** 上，长按键蓄力、松手释放的权威做法是什么？

需回答（带 primary source 引用）：

1. 客户端如何可靠检测 boost 键 **按下 / 持续按住 / 松开**（相对现有 `KeyMapping.consumeClick()` 点按模型）。
2. 推荐的 C2S 包形态：start-charge / release-charge / 每 tick charge 心跳，或单包带 client charge ticks；各方案作弊面与抖包。
3. 服务端如何 **权威计算蓄力时长**（不盲信客户端），与 3s / 2s 窗口、自动释放、取消条件对齐。
4. 与现有 `BoosterRequestPayload` / `BoosterSteerPayload` 的最小侵入扩展路径。
5. 冷却中、推进中、未装备升级时的拒绝点应在哪一侧。

产出：research 笔记路径 + 对实现 session 的推荐默认方案（一种，附取舍）。

## Answer

**Default: server-clocked Start + Release; reuse `BoosterRequestPayload` for fire.**

1. **Input**: charge-upgrade clients edge-detect `KeyMapping.isDown()` (PRESS/HELD/RELEASE); no-upgrade keeps `consumeClick()`. Vanilla hold model matches sprint (`isDown`); Fabric docs use `consumeClick` for discrete taps. Drain `consumeClick` while charging.
2. **C2S**: new empty `BoosterChargeStartPayload` (+ optional cancel). Fire = existing `BoosterRequestPayload`. Reject single-packet client `chargeTicks` (cheatable) and per-tick heartbeats for v1 (bandwidth).
3. **Authority**: server `ChargeSession.startTick`; `chargeTicks = clamp(now - start, 0, 100)` (60 = 3s overload entry, 100 = auto-release). Cancel free on UI/death/unequip; no client duration trust.
4. **Extension**: leave `BoosterRequestPayload` / `BoosterSteerPayload` codecs alone; register one new C2S type beside existing `PayloadTypeRegistry.playC2S()` / `ServerPlayNetworking` wiring in `BoosterMod`.
5. **Rejects**: soft on client for UX; **hard on server** for no equip / no upgrade / cooldown / already boosting / duplicate session. Resources only on successful fire.

Full note + sources: [`docs/research/charge-overload-networking.md`](../../../docs/research/charge-overload-networking.md)
