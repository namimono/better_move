# Research: Explosion API for overload impact (MC 1.21.1 / Mojmap)

**Ticket**: `.scratch/charge-overload/issues/06-research-explosion-api.md`  
**Mappings**: this repo uses `loom.officialMojangMappings()` → **Mojmap** names (`ServerLevel` / `Level.explode`, not Yarn `createExplosion`).  
**MC version**: 1.21.1 (Fabric Loom mapped sources via `./gradlew genSources`).

## Product constraints (charting — must not violate)

- Only **overload** boosts can explode.
- **First** solid block or entity hit → **one** explosion, then boost ends.
- Water / lava / other fluids do **not** explode and do **not** end the boost for that reason.
- Self-damage **2 hearts (4 HP) only when explosion happens**.
- No explode on release-in-place; no explode if the boost finishes without a solid/entity hit.

---

## 1. Server-side explosion API (Mojmap)

### Entry points

All overloads live on `net.minecraft.world.level.Level` (usable as `ServerLevel`). Core pipeline ends in:

```java
// Level.explode(... full form ...)  // Mojmap
Explosion.BlockInteraction blockInteraction = switch (explosionInteraction) {
    case NONE -> Explosion.BlockInteraction.KEEP;
    case BLOCK -> this.getDestroyType(GameRules.RULE_BLOCK_EXPLOSION_DROP_DECAY);
    case MOB -> this.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
            ? this.getDestroyType(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY)
            : Explosion.BlockInteraction.KEEP;
    case TNT -> this.getDestroyType(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY);
    case TRIGGER -> Explosion.BlockInteraction.TRIGGER_BLOCK;
};
Explosion explosion = new Explosion(
        this, entity, damageSource, explosionDamageCalculator,
        x, y, z, power, createFire, blockInteraction,
        particle, emitterParticle, sound);
explosion.explode();
explosion.finalizeExplosion(particles);
return explosion;
```

**Convenient overloads** (all named `explode` on Mojmap; Yarn: `createExplosion`):

| Signature (abbrev.) | Notes |
| --- | --- |
| `explode(Entity, x, y, z, power, ExplosionInteraction)` | No fire; default damage source; default particles/sound |
| `explode(Entity, x, y, z, power, createFire, ExplosionInteraction)` | Adds fire flag |
| `explode(Entity, DamageSource, ExplosionDamageCalculator, x,y,z, power, createFire, ExplosionInteraction)` | Full control without custom VFX |
| `explode(..., Interaction, ParticleOptions, ParticleOptions, Holder<SoundEvent>)` | Custom VFX/SFX |
| `explode(..., Interaction, boolean particles, ParticleOptions, ParticleOptions, Holder<SoundEvent>)` | Full form |

Default VFX when omitted: `ParticleTypes.EXPLOSION` + `ParticleTypes.EXPLOSION_EMITTER`, `SoundEvents.GENERIC_EXPLODE`.

### Parameters

| Param | Meaning |
| --- | --- |
| `entity` | Direct source entity of the explosion (`Explosion.source`). Used for game event, default damage attribution, and **excluded** from `level.getEntities(source, aabb)` damage/knockback loop. |
| `x,y,z` / `Vec3` | Center. TNT uses `getY(0.0625)` (slightly above feet). |
| `power` (`float`) | Blast radius scale (vanilla field name `radius` inside `Explosion`). Affects block ray strength and entity damage formula. |
| `createFire` | If true, randomly places fire on destroyed air cells above solid. TNT/creeper use `false`. |
| `ExplosionInteraction` | Selects block interaction / gamerules (see below). |
| `DamageSource` | If null, defaults via `Explosion.getDefaultDamageSource(level, entity)` → `level.damageSources().explosion(entity, indirect)`. |
| `ExplosionDamageCalculator` | If null, default calculator (blocks explode, all entities damageable). |

### `Level.ExplosionInteraction` (Mojmap) / Yarn `World.ExplosionSourceType`

| Value | Block destruction |
| --- | --- |
| `NONE` | `KEEP` — no block interact |
| `BLOCK` | Destroy per `RULE_BLOCK_EXPLOSION_DROP_DECAY` |
| `MOB` | Destroy **only if** `RULE_MOBGRIEFING`; else `KEEP` |
| `TNT` | Destroy per `RULE_TNT_EXPLOSION_DROP_DECAY` (does **not** check `mobGriefing`) |
| `TRIGGER` | `TRIGGER_BLOCK` (bed/respawn-anchor style) |

`getDestroyType(rule)` → `DESTROY_WITH_DECAY` if rule true, else `DESTROY`.

### Power vs TNT / creeper (vanilla call sites)

**PrimedTnt** (`net.minecraft.world.entity.item.PrimedTnt#explode`):

```java
float f = 4.0F;
this.level().explode(
    this,
    Explosion.getDefaultDamageSource(this.level(), this),
    this.usedPortal ? USED_PORTAL_DAMAGE_CALCULATOR : null,
    this.getX(), this.getY(0.0625), this.getZ(),
    4.0F,
    false,
    Level.ExplosionInteraction.TNT
);
```

**Creeper** (`Creeper#explodeCreeper`):

```java
float f = this.isPowered() ? 2.0F : 1.0F;
this.level().explode(
    this, this.getX(), this.getY(), this.getZ(),
    this.explosionRadius * f,  // default explosionRadius = 3 → 3.0 normal, 6.0 charged
    Level.ExplosionInteraction.MOB
);
```

| Source | Power | Fire | Interaction |
| --- | --- | --- | --- |
| TNT | **4.0F** | false | `TNT` |
| Creeper (normal) | **3.0F** | false | `MOB` (+ `mobGriefing`) |
| Creeper (charged) | **6.0F** | false | `MOB` |

**Default recommendation for overload**: power **`4.0F`**, fire **`false`**, interaction **`Level.ExplosionInteraction.TNT`** (closest “TNT-style” block break + no fire).  
If product later wants greifing toggle parity with creepers, switch interaction to `MOB` (still power 4.0 or 3.0).

### Entity damage / block break behavior

`Explosion.explode()`:

1. `GameEvent.EXPLODE` at center.
2. Samples 16³ shell rays; resistance from blocks **and fluids** attenuates ray (`getBlockExplosionResistance`).
3. Collects block positions into `toBlow`.
4. Damages entities in AABB of size `radius * 2` via  
   `level.getEntities(this.source, aabb)` — **source entity is excluded**.
5. Per entity (if `!ignoreExplosion` and in range): optional `hurt(damageSource, amount)` then knockback; players record hit vector unless spectator / creative flying.

Default damage amount (`ExplosionDamageCalculator#getEntityDamageAmount`):

```text
f = radius * 2
exposure term e = (1 - dist/f) * seenPercent
damage = (e² + e) / 2 * 7 * f + 1
```

`finalizeExplosion(particles)` plays sound, spawns particles, destroys blocks if `interactsWithBlocks()`, optional fire.

**Creative / invulnerable**: `Player.hurt` early-outs when `abilities.invulnerable` unless damage bypasses invulnerability — creative players take no explosion damage. Block break still follows interaction/gamerules (not “player can build” checks inside vanilla explode).

**Source**: Loom-mapped `Level.java`, `Explosion.java`, `PrimedTnt.java`, `Creeper.java` for 1.21.1; Fabric Yarn javadoc cross-check [`World.createExplosion`](https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/net/minecraft/world/World.html); multi-mapping table [mappings.dev Level](https://mappings.dev/1.21.1/net/minecraft/world/level/Level.html).

---

## 2. Fixed 4 HP self-damage (avoid double / i-frames)

### Do **not** rely on the explosion to hurt the booster

If `entity` (source) is the **player**, vanilla **excludes** that player from the entity damage loop. That is desirable: explosion handles **AOE others + blocks**; self-damage is applied separately as a flat 4 HP.

If source were `null`, the player would be eligible for **variable** explosion damage at center (often lethal at power 4) — wrong for “exactly 2 hearts”.

### Recommended self-damage

```java
// After explode(...), only if explosion actually ran for overload impact:
player.invulnerableTime = 0; // clear i-frames so same-tick hurt is not swallowed
player.hurt(player.damageSources().generic(), 4.0F);
```

Why `generic()`:

- Datapack tag `minecraft:tags/damage_type/bypasses_armor` **includes** `minecraft:generic` → armor does not reduce the 4 HP.
- Still respects creative `abilities.invulnerable` (no self-hurt in creative) and death/sleep paths via `LivingEntity`/`Player#hurt`.
- Does **not** put the kill credit as “blown up by &lt;self&gt;” unless desired; message is generic. Acceptable for cost-of-overload.

### Double damage / i-frame notes

| Risk | Mitigation |
| --- | --- |
| Explosion damages player **and** `hurt(4)` | Pass **player as source** so they are excluded from AOE damage list. |
| Prior hurt sets `invulnerableTime = 20` and swallows 4 HP | Set `invulnerableTime = 0` immediately before self-hurt. |
| Self-hurt then blocks later same-tick damage | Expected; order = explode (AOE) **then** self-hurt. |
| Using `player_explosion` / explosion damage for self | Scaled/Blast protection; **not** fixed 4 HP. Avoid. |

`LivingEntity.hurt`: if `invulnerableTime > 10` and source does not `BYPASSES_COOLDOWN`, damage ≤ `lastHurt` is ignored (only excess applies). Zeroing `invulnerableTime` avoids that.

---

## 3. Collision detection: solid / entity / fluid

### Engine facts (Mojmap `Entity`)

Flags set during `Entity.move` after shape collision resolution:

- `horizontalCollision` — X/Z movement clipped by **block** (or hard) collision shapes.
- `verticalCollision` / `verticalCollisionBelow` — Y clipped.
- Fluids: `LiquidBlock#getCollisionShape` is normally **`Shapes.empty()`** (only a special stand-on fluid case). Entering water/lava does **not** set `horizontalCollision` from fluid cells.
- Entity–entity: most mobs do **not** solid-block the player; **do not** expect `horizontalCollision` alone for entity hits.

`isInWater()` / `isInLava()` / fluid tags detect **immersion**, not “impact”. They must **not** trigger explode or end-for-fluid.

### Local code today

`BoosterMotionTicker.ActiveBoost#step` (server, `ServerTickEvents.END_SERVER_TICK` after entity movement):

```152:155:src/main/java/com/boostermod/item/BoosterMotionTicker.java
            if (player.horizontalCollision) {
                emitEndParticles(player.position());
                return true;
            }
```

- Ends boost on **horizontal solid** only; no entity scan; no vertical-only end.
- Combat: `BoostStrikeHandler` awards on melee during boost window; charting says **on entity hit, explosion wins over strike that frame** (implement by exploding + ending boost before any strike settle, or flag “overloaded impact this tick”).

Pre-boost probe in `BoosterLeggingsItem` uses `level.getBlockCollisions` / `getEntityCollisions` — useful reference for entity AABB tests, not for mid-flight fluids.

### Recommended impact predicates (overload only)

```text
solidHit  := player.horizontalCollision
             || player.verticalCollision   // ceiling/floor solids; see caveats
entityHit := !level.getEntities(player, player.getBoundingBox().inflate(ε),
                 e -> e.isAlive() && e.isPickable() && player.canCollideWith(e)
                      /* and/or LivingEntity filter; exclude own projectiles */).isEmpty()
fluidOnly := player.isInWater() || player.isInLava() || fluidHeight > 0
             // informational only — NEVER triggers explode/end by itself
impact    := solidHit || entityHit
```

Caveats:

1. **Ground launch**: first ticks may touch floor (`verticalCollisionBelow`). Prefer either (a) ignore `verticalCollisionBelow` for the first N ticks of `groundLaunch`, or (b) only treat `verticalCollision` when look/motion has a significant downward/upward component, or (c) stick to **`horizontalCollision` + entity** for v1 (matches current end condition + product “撞墙/撞怪”). **Recommend v1: `horizontalCollision || entityHit`** to match existing boost-end feel; add ceiling later if needed.
2. **ε inflate**: small (e.g. `0.05`–`0.2`) so grazing counts without huge sweep.
3. Fluids never contribute to `solidHit`; no special fluid cancel required beyond “do not end for water”.

---

## 4. Integration with `BoosterMotionTicker` boost end

### Hook site

In `ActiveBoost.step(ServerPlayer)` **before** thrust application, overload branch:

```text
if (overload) {
  if (impact) {          // first solid/entity
    doOverloadExplosion(level, player);
    emitEndParticles(...);
    return true;         // → stopBoost via tickServer
  }
} else if (player.horizontalCollision) {
  emitEndParticles(...);
  return true;
}
// existing thrustTicks expiry...
```

`stopBoost` already:

- removes step-height modifier  
- calls `BoostStrikeSupport.onBoostEnd` (starts grace)  
- clears granted no-gravity  

Call **explosion + self-damage before `return true`** (still inside `step`, player still “boosting” for any queries that frame). Flag `exploded` on `ActiveBoost` so a second impact path cannot re-fire (single explosion).

### What must not explode

| Event | Explode? | End boost? |
| --- | --- | --- |
| Overload + first solid/entity | Yes | Yes |
| Overload + swim through water/lava only | No | No (continue) |
| Overload + thrust timeout / no hit | No | Yes (normal end) |
| Overload + key release already started boost | N/A (release starts boost; no mid-air “cancel explode”) | — |
| Non-overload boost + wall | No | Yes (current) |
| Burrow end (separate product rule) | Yes once at burrow end if overload | N/A (not motion ticker) |

Burrow-on-overload is a **different** call site (`applyBurrow` / post-descend) using the same `explode` + self-hurt helper; not `horizontalCollision`.

### Strike priority

On entity impact frame: run explosion path and end boost; do not depend on `AttackEntityCallback` for the boom. Optional: skip strike settle that tick if `justOverloadExploded`.

---

## 5. Recommended default call sequence (ONE sequence)

**Helper** (pseudo-Java, Mojmap, server-only):

```java
static final float OVERLOAD_EXPLOSION_POWER = 4.0F; // TNT parity
static final float OVERLOAD_SELF_DAMAGE = 4.0F;     // 2 hearts

/** Call once on first solid/entity hit during an overload ActiveBoost. */
static void detonateOverloadImpact(ServerLevel level, ServerPlayer player) {
    Vec3 pos = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
    // 1) AOE: player as source → player excluded from entity damage list;
    //    TNT interaction → block break without mobGriefing gate; no fire.
    level.explode(
            player,
            pos.x, pos.y, pos.z,
            OVERLOAD_EXPLOSION_POWER,
            false,
            Level.ExplosionInteraction.TNT
    );
    // 2) Fixed self-cost (armor-bypassing generic); clear i-frames first.
    if (!player.isCreative() && !player.isSpectator()) {
        player.invulnerableTime = 0;
        player.hurt(player.damageSources().generic(), OVERLOAD_SELF_DAMAGE);
    }
}

// Inside ActiveBoost.step, overload only:
// if (player.horizontalCollision || hitsEntity(level, player)) {
//     detonateOverloadImpact(level, player);
//     emitEndParticles(player.position());
//     return true; // stopBoost
// }
```

**Detection helper**:

```java
static boolean hitsEntity(ServerLevel level, ServerPlayer player) {
    AABB box = player.getBoundingBox().inflate(0.1);
    return !level.getEntities(
            player,
            box,
            e -> e.isAlive() && !(e instanceof Player p && p.isSpectator())
                    && e.isPickable()
    ).isEmpty();
}
```

### Sequence checklist

1. Confirm boost is **overload** (flag on `ActiveBoost` / start args).  
2. Each END_SERVER_TICK after movement: test `horizontalCollision` **or** entity AABB.  
3. Ignore fluid immersion as impact.  
4. On first impact: `level.explode(player, x, y, z, 4.0F, false, TNT)` → `invulnerableTime=0` → `hurt(generic, 4)` → end boost (`return true` → `stopBoost`).  
5. Never explode on timeout-only or non-overload paths.

---

## 6. Sources

| Source | Role |
| --- | --- |
| Loom genSources Mojmap: `net.minecraft.world.level.Level`, `Explosion`, `ExplosionDamageCalculator` | Primary explode pipeline |
| Mojmap: `PrimedTnt`, `Creeper` | Power 4.0 / 3.0 and interaction enums |
| Mojmap: `Entity` (collision flags), `LiquidBlock` (empty collision shape) | Solid vs fluid |
| Mojmap: `LivingEntity#hurt`, `Player#hurt`, datapack `bypasses_armor` | Self-damage 4 HP |
| Mojmap: `DamageSources#explosion` | Default blast damage types |
| Repo: `BoosterMotionTicker`, `BoosterLeggingsItem`, `BoostStrikeSupport` / `BoostStrikeHandler` | Integration |
| Yarn 1.21.1 javadoc + [mappings.dev](https://mappings.dev/1.21.1/net/minecraft/world/level/Level.html) | Name bridge Yarn↔Mojmap |

---

## 7. Open / non-blocking notes

- Multiplayer region protection mods: vanilla `explode` does not consult claim plugins; follow-up if servers need `canPlayerModifyAt` filtering (custom `ExplosionDamageCalculator` or event cancel).
- Exact entity filter (armor stands, items, boats) can be tightened at implement time; product says 生物/方块.
- Vertical solid impacts optional for v1 (see §3).
)
