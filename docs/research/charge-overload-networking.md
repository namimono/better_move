# Research: hold-to-charge networking (MC 1.21.1 + Fabric)

**Scope**: networking + input model only. No feature implementation.  
**Stack**: Minecraft 1.21.1, Fabric Loader 0.19.2, Fabric API `0.116.11+1.21.1`, Loom official Mojang mappings (`loom.officialMojangMappings()`).  
**Related local code**: `BoosterInputHandler`, `BoosterRequestPayload`, `BoosterSteerPayload`, `BoosterMod` packet registration, `BoosterLeggingsItem.tryBoostFromKey`.

---

## 1. Client: reliable press / hold / release for boost key

### Primary facts (vanilla `KeyMapping`)

From mapped client jar (`minecraft-clientonly` 1.21.1, Mojang names):

| API | Behavior |
|-----|----------|
| `KeyMapping.isDown()` | Continuous **held** flag (`private boolean isDown`). |
| `KeyMapping.consumeClick()` | Edge queue: returns true once per press while `clickCount > 0`, then decrements. |
| `KeyMapping.click(Key)` | On physical press: increments `clickCount`. |
| `KeyMapping.set(Key, boolean)` | On press/release event: `setDown(pressed)`. |
| `KeyMapping.release()` / `releaseAll()` | Clears `clickCount` and forces `setDown(false)`. |

Vanilla uses **`isDown()` for continuous holds** (e.g. sprint: `LocalPlayer` reads `options.keySprint.isDown()`).  
Fabric docs show **`while (key.consumeClick())` for discrete “pressed this tick” actions** ([Key Mappings](https://docs.fabricmc.net/develop/key-mappings)).

### Current mod model

`BoosterInputHandler.tickBoostKey` (on `ClientTickEvents.START_CLIENT_TICK`):

```java
while (boostKey.consumeClick()) {
    ClientPlayNetworking.send(new BoosterRequestPayload(...));
}
```

This is correct for **tap → instant boost**, wrong for **hold-to-charge**: hold never re-fires `consumeClick`, and release is not observed.

### Recommended client detection

Use **edge detection over `isDown()`** each client tick (same phase as today: `START_CLIENT_TICK`, so boost still precedes same-frame attack for 破击):

```text
down = boostKey.isDown()
if (down && !wasDown)  → PRESS
if (!down && wasDown)  → RELEASE
if (down && wasDown)   → HELD (no packet required if server clocks charge)
wasDown = down
```

Implementation notes:

1. **Charge upgrade path**: branch on client-readable upgrade presence (equipped stack NBT / components already used for tooltips). PRESS → start-charge; RELEASE → fire (`BoosterRequestPayload` or release payload).
2. **No upgrade path**: keep `while (consumeClick())` instant fire (product: 未安装 = 现状点按瞬发).
3. **Drain `consumeClick()` while charging** so leftover `clickCount` does not fire an extra instant request after release.
4. **GUI / focus**: vanilla `KeyMapping.releaseAll()` clears held keys when appropriate; also treat `client.screen != null` as cancel (product: 开 UI 取消).
5. **Do not use raw GLFW** bypassing `KeyMapping` — remaps, conflicts, and mouse-bound keys would break.

---

## 2. C2S packet shapes (options + cheat surface)

Fabric 1.20.5+ / this repo’s API (confirmed in Fabric Networking API v1 sources shipped with `0.116.11+1.21.1`):

- Define `record … implements CustomPacketPayload` + `Type` + `StreamCodec`.
- Register with `PayloadTypeRegistry.playC2S().register(TYPE, CODEC)` **before** receivers.
- Send: `ClientPlayNetworking.send(payload)`.
- Receive: `ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> …)`.
- **Server `PlayPayloadHandler` runs on the server thread** and may touch world/player safely (API javadoc). Existing code’s extra `context.server().execute(...)` is redundant but harmless.

Official docs pattern: [Fabric Networking](https://docs.fabricmc.net/develop/networking), [Wiki 1.20.5+ payloads](https://wiki.fabricmc.net/tutorial:networking#networking_in_1205).  
Note: newer Fabric docs may name registries `serverboundPlay` / `clientboundPlay`; **this project’s Fabric API version uses `playC2S` / `playS2C`**.

### Option A — Start + Release (server clock) ✅ recommended

| Packet | When | Body |
|--------|------|------|
| `BoosterChargeStartPayload` (new) | PRESS with charge upgrade | Empty / version byte only |
| Optional `BoosterChargeCancelPayload` | UI / local cancel | Empty |
| Existing `BoosterRequestPayload` | RELEASE fire (and auto-release can be server-only) | Keep `dirX, dirZ, jumpTicksAgo, landingTicksAgo` |

- **Authority**: server stores `startTick = server.getTickCount()` on START; on fire, `chargeTicks = clamp(now - start, 0, 100)`.
- **Bandwidth**: ≤2 C2S per charge (+ rare cancel).
- **Cheat surface**: client cannot claim longer hold than real server elapsed time. Instant RELEASE after START → ~0–few ticks (base distance). START without RELEASE → auto-fire at 100 ticks (intended) or cancel.
- **Latency**: high RTT can slightly lengthen measured charge (release packet late). Cap at 100 ticks (5s). Optional later: hold heartbeats to freeze charge on missing “still held”.

### Option B — Per-tick charge heartbeats

- Client sends “still charging” every tick or every N ticks.
- **Pros**: can detect “ghost holds” / freeze charge if packets stop.  
- **Cons**: spam (~20 pkt/s/player), more rate-limit surface, still need START/end for clean FSM. **Not needed** for 3s+2s product windows if server clocks from START and auto-releases.

### Option C — Single fire packet with client `chargeTicks`

- One C2S carrying claimed duration.  
- **Cheatable**: tap and claim 100 ticks. Server has no independent hold clock.  
- **Reject as primary**. At most a client prediction hint that server ignores for distance.

### Option D — Extend only `BoosterRequestPayload` with mode/ticks

- Minimal types, but either loses authority (client ticks) or still needs a prior START state → same as A with muddier payload roles.

**`BoosterSteerPayload`**: leave alone. Server already no-ops steer (`BoosterMotionTicker.setSteerInput` empty; look-only boost). Not part of charge path.

---

## 3. Server-authoritative charge timing (3s + 2s)

Constants (20 tps):

| Window | Seconds | Ticks |
|--------|---------|-------|
| Charge (distance ramp) | 0–3s | 0–60 |
| Overload hold | 3–5s | 60–100 |
| Auto-release | 5s | 100 |

### Server session (per player UUID)

```text
ChargeSession {
  startTick: int
  // optional: equipped item identity for unequip detection
}
```

**On START (C2S):**

1. Reject if no booster equipped.
2. Reject if missing charge/overload upgrade (server re-check; never trust client branch alone).
3. Reject if `BoosterMotionTicker.isBoosting(player)`.
4. Reject if on cooldown (unless `NO_COOLDOWN`).
5. Reject if already in `ChargeSession`.
6. Optionally reject low food (same as boost) — product: resources only on successful launch, but blocking start on hunger matches “can’t boost” UX; either is fine if consistent.
7. Accept → record `startTick`.

**Each server tick (pair with existing `ServerTickEvents.END_SERVER_TICK`):**

1. If no session → skip.
2. Cancel (free, no boost) if: dead / removed, no booster, upgrade removed, container/menu open if product requires, dimension change, etc.
3. If `now - startTick >= 100` → **auto-release**: clear session, call boost path with `chargeTicks = 100` (full overload). Direction: existing server `resolveBoostDirection` / look vector (see below) — no client packet required.
4. Else keep session; optional S2C sync for other players’ VFX (out of core authority).

**On RELEASE / fire (`BoosterRequestPayload` while session active):**

1. `chargeTicks = min(now - startTick, 100)`.
2. Clear session.
3. Run existing validation + boost apply, scaled by `chargeTicks` (distance linear 0–60; overload flag if `>= 60`).

**On CANCEL:**

- Clear session; no cooldown / hunger / durability.

**Hyper window**: keep client `landingTicksAgo` on the **fire** packet only (same as today). Server still re-checks `player.onGround()` in `isHyperBoost`.

**Note on `dirX`/`dirZ`:** `tryBoostFromKey` currently **does not use** `clientDirX/Z` for motion; direction comes from `player.getViewVector` via `resolveBoostDirection`. Fire packet still carries them for hyper metadata fields; auto-release needs no client direction. Implementation may continue ignoring dir for boost vector unless product reintroduces input-based direction.

---

## 4. Minimal extension of existing payloads

| Piece | Change |
|-------|--------|
| `BoosterRequestPayload` | **Keep as fire intent.** No required field adds for v1 if charge duration is server-only. Optional later: unused. |
| `BoosterSteerPayload` | **No change.** |
| Registration in `BoosterMod` | Register new C2S type(s) next to existing `playC2S` registrations; add `ServerPlayNetworking` receiver. |
| New C2S | `BoosterChargeStartPayload` (empty record) ± `BoosterChargeCancelPayload` (or one payload + `enum Action { START, CANCEL }`). |
| Optional S2C | `BoosterChargeStatePayload` (charging, ticks, overload) for HUD/others — not required for authority. |
| Client input | `BoosterInputHandler`: dual-mode (consumeClick vs isDown edges). |

Dual-mode client sketch:

```text
if (!hasChargeUpgradeLocally):
  while consumeClick → BoosterRequestPayload   // status quo
else:
  drain consumeClick
  PRESS  → ChargeStart
  RELEASE → BoosterRequestPayload (if local charging)
  cancel conditions → ChargeCancel
```

Server still enforces upgrade on START and on fire.

---

## 5. Reject points: which side?

| Condition | Client (soft UX) | Server (hard authority) |
|-----------|------------------|-------------------------|
| No booster equipped | Skip send | **Reject** START / fire |
| No charge upgrade | Instant-tap path | **Reject** START; fire = legacy instant |
| On cooldown | Don’t START; don’t fire | **Reject** START; **Reject** fire |
| Already boosting | Ignore PRESS (product) | **Reject** START / fire (`isBoosting`) |
| Already charging | Ignore extra START | **Reject** duplicate START |
| Low food | Optional soft block | **Reject** fire (existing); START optional |
| Open UI / death / unequip | Local cancel + CANCEL packet | **Cancel session** server-side |
| Air dash rules / burrow | N/A for charge clock | Existing checks on **fire** only |
| During charge, PRESS again | No-op | N/A |

**Rule of thumb**: client soft-guards reduce bad packets; **every reject that affects fairness must re-run on server**. Cooldown write / hunger / durability only on **successful fire** (product: 取消免费).

---

## Recommended default (single approach)

### **Server-clocked Start + Release; reuse `BoosterRequestPayload` for fire**

1. **Client hold detection** via `KeyMapping.isDown()` edge tracking when charge upgrade is present; otherwise keep `consumeClick()` instant fire.  
2. **New empty C2S** `BoosterChargeStartPayload` (+ optional cancel).  
3. **Fire** = existing `BoosterRequestPayload` (release or server auto at 100 ticks).  
4. **Server `ChargeSession`** with `startTick`; `chargeTicks = clamp(now - start, 0, 100)`; distance/overload derived only from that.  
5. **Hard rejects on server** for equip / upgrade / cooldown / boosting / duplicate session; cancel free.  
6. **No per-tick heartbeats** in v1; no client-reported charge duration.

### Tradeoffs

| Pros | Cons |
|------|------|
| Matches Fabric payload API already used in-repo | One new packet type (+ cancel if not folded) |
| Minimal change to fire path / hyper fields | Client must know upgrade for hold UX (still revalidated server-side) |
| Strong anti-cheat on duration | RTT can add a few ticks to measured charge (capped at 100) |
| Auto-release without client packet | Ghost START until timeout if client dies mid-wire — mitigated by server cancel checks |
| Low bandwidth | Optional multiplayer charge VFX needs separate S2C later |

### Explicit non-defaults

- Do **not** trust client `chargeTicks` as authority.  
- Do **not** send charge heartbeats every tick in v1.  
- Do **not** overload `BoosterSteerPayload` for charge.  
- Do **not** replace `isDown` edges with only `consumeClick` for hold.

---

## Sources

1. **Local Mojang-mapped client** (Loom cache): `net.minecraft.client.KeyMapping` — `isDown()`, `consumeClick()`, `clickCount`, `setDown`, `release`/`releaseAll` (MC 1.21.1).  
2. **Local Mojang-mapped client**: `LocalPlayer` uses `keySprint.isDown()` for continuous sprint (hold model reference).  
3. **Fabric API** `fabric-networking-api-v1` **4.3.0+c7469b2119** sources (via fabric-api `0.116.11+1.21.1`): `PayloadTypeRegistry.playC2S/playS2C`, `ServerPlayNetworking` / `ClientPlayNetworking`, `PlayPayloadHandler` server-thread guarantee.  
4. [Fabric Documentation — Networking](https://docs.fabricmc.net/develop/networking).  
5. [Fabric Documentation — Key Mappings](https://docs.fabricmc.net/develop/key-mappings) (`consumeClick` + client tick pattern).  
6. [Fabric Wiki — Networking in 1.20.5+](https://wiki.fabricmc.net/tutorial:networking#networking_in_1205) (`CustomPayload` / `PayloadTypeRegistry`).  
7. **In-repo**: `BoosterInputHandler`, `BoosterRequestPayload`, `BoosterSteerPayload`, `BoosterMod` registration, `BoosterLeggingsItem.tryBoostFromKey`, `BoosterMotionTicker`.  
8. Product constraints: `.scratch/charge-overload/map.md` (hold Z, 3s+2s auto-release, cancel free, cooldown blocks start).

---

## Implementation handoff checklist

- [ ] Add `BoosterChargeStartPayload` (+ cancel if desired); register C2S + receiver.  
- [ ] Server map `UUID → ChargeSession`; tick auto-release @ 100.  
- [ ] Wire RELEASE path: if session active, compute `chargeTicks` then boost.  
- [ ] Dual-mode `BoosterInputHandler` (`isDown` edges vs `consumeClick`).  
- [ ] Server reject matrix (upgrade / CD / boosting / equip).  
- [ ] Keep `BoosterRequestPayload` codec stable for non-upgrade clients.  
- [ ] Optional: S2C charge feedback for HUD.
