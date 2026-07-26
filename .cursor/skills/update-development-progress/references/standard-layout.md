# Development Progress Layout

Adapt this skeleton; keep only sections that carry information.

```markdown
# <Feature> · 开发进度

> 更新日期：YYYY-MM-DD
> 对应方案：[plan](./plan.md)
> 故障排查：[troubleshooting](./troubleshooting.md)
> 代码 Review：[review](./review.md)
> 基线分支：`<branch>`
> 平台 / 范围：<scope>

---

## 1. 总体进度

| 阶段 | 方案内容 | 状态 | 说明 |
|---|---|---|---|
| <phase> | <scope> | **代码已实现，待验证** | <evidence / gap> |

**综合判断：<current state, evidence, and the next material gap>.**

## 2. 已实现能力

- <observable capability and conditions>

## 3. 已落地的架构改动

| 模块 | 路径 | 职责 |
|---|---|---|
| <module> | `<path>` | <responsibility> |

## 4. 已知偏差 / 限制

1. **<topic>**：<why it differs, impact, and follow-up>。

## 5. 验证与修复记录

| 类型 / 级别 | 场景或问题 | 状态 | 证据 / 修复 |
|---|---|---|---|
| <test/review> | <case> | **待验证** | <command, result, or next action> |

## 6. 未完成 / 待办

### P0 · 正确性或稳定性

- [ ] <concrete action and acceptance condition>

### P1 · 治理、性能或维护性

- [ ] <concrete action and acceptance condition>

## 7. 验收对照

| 验收项 | 状态 |
|---|---|
| <criterion> | <implemented / verified / pending and evidence> |

## 8. 建议下一迭代顺序

1. <highest-value next action>

## 9. 变更文件速查（本轮）

**新增**

- `<path>`（<purpose>）

**大幅修改**

- `<path>`（<behavioral change>）
```

Use “代码已实现，待验证” rather than “已完成” whenever the required verification has not been performed.
