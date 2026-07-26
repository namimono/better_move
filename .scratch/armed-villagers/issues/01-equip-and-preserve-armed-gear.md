# 01 — 村民拾取并保全武装装备

**What to build:** 让原版成年村民能够通过地面物品交互获得武装装备：在规则允许且对应装备槽为空时，分别拾取推进器和剑并立即装备。装备状态应持久、可预测且可回收，同时不改变村民原有的食物、种子和职业物品拾取行为。

**Blocked by:** None — can start immediately

**Status:** ready-for-human

- [x] Fabric 服务端 GameTest 具备可重复运行的项目入口，现有 JUnit 测试和新增 GameTest 均可独立执行。
- [x] `mobGriefing` 开启时，原版成年村民会拾取地面推进器并立即装备至空的护腿槽，不先放入内部物品栏。
- [x] `mobGriefing` 开启时，原版成年村民会拾取 `swords` 物品标签内的剑并立即装备至空的主手，包括正确声明该标签的其他模组物品。
- [x] 职业村民、无业村民和傻子村民均可拾取并装备；幼年村民、流浪商人、僵尸村民及其他实体不获得该能力。
- [x] 护腿槽或主手已占用时，村民拒绝拾取对应类型的新物品，不替换、比较或吞掉已有及地面装备。
- [x] `mobGriefing=false` 时禁止新拾取推进器和剑，但规则切换不会卸下已经装备的物品。
- [x] 新增装备分支不破坏村民原有的食物、种子和职业物品拾取行为。
- [x] 通过拾取得到的推进器和剑使用原生装备槽持久保存；村民死亡时两件装备必定掉落，并保留耐久、附魔和推进器升级项等完整物品数据。
- [x] 项目构建、现有 JUnit 测试及本 ticket 的 GameTest 全部通过。

## Comments

### 2026-07-26 — agent

已交付 issue 01：

- `ArmedVillagerEquipment` + `VillagerArmedEquipmentMixin`：成年村民拾取推进器/剑并直接进护腿/主手，槽占用拒绝，拾取后 `setGuaranteedDrop`。
- `src/gametest/` + `./gradlew runGameTest`（亦随 `build`/`check`）：13 项 GameTest 全过；`./gradlew test` 与 `./gradlew build` 通过。
- 模组 `swords` 标签识别走 `ItemTags.SWORDS`；未另造假模组剑物品做 GameTest（原版剑覆盖标签路径）。
