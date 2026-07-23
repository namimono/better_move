# Booster Mod

Minecraft 1.21.1 Fabric 模组。核心装备是可升级的推进器护腿，玩家穿在腿部槽位后，按 `Z` 触发一段带喷射推进感的位移。

## 当前定位

- 主题：从原来的 `Better Move / Dash Leggings` 调整为 `Booster Mod / Booster Leggings`
- 手感：以“点火 -> 持续推力 -> 断推收尾”的喷射推进为核心
- 装备：木、石、铜、铁、金、钻石、下界合金 7 个等级
- 操作：默认按键 `Z`，按下时读取当前移动输入方向作为推进方向

## 当前标识

- `modid`: `boostermod`
- Java 包名：`com.boostermod`
- 物品注册名：`booster_leggings_*`
- 资源目录：`assets/boostermod/...`
- 网络包 id：`booster_request`

## 开发

- 构建：`./gradlew build`
- 运行服务端：`./gradlew runServer`
- 运行客户端：`./gradlew runClient`

项目基于 Java 21、Fabric Loom、Fabric API。当前没有自动化测试，主要依赖编译通过和进游戏手测。

## 文档

- [终端命令说明](docs/终端命令说明.md) — `/boostermod` 全部子命令（HUD / 震动 / 破击叠层 / 推进数值）
- 其它设计与进度稿见 `docs/`
