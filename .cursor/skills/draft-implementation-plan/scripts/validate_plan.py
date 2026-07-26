#!/usr/bin/env python3
"""Validate the structure and obvious completeness of an implementation plan."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


PRD_TOPICS = {
    "业务背景": ("业务背景",),
    "需求目标": ("需求目标",),
    "需求范围": ("需求范围", "本期包含"),
    "主流程或功能需求": ("主业务流程", "业务流程", "功能需求"),
    "业务规则": ("核心业务规则", "总体业务规则"),
    "验收标准": ("验收标准",),
    "已确认事项": ("已确认事项",),
    "待确认事项": ("待确认事项",),
    "PRD Break 结论": ("PRD Break 结论",),
}

TECH_TOPICS = {
    "目标与原则": ("方案目标", "设计原则"),
    "系统边界": ("系统边界", "改造范围"),
    "架构或数据流": ("整体架构", "数据流"),
    "数据设计": ("数据库设计", "数据模型", "领域字段", "表结构"),
    "核心流程": ("核心业务流程",),
    "接口改造": ("接口改造", "接口设计"),
    "交互改造": ("前端交互", "前端与交互", "页面改造"),
    "兼容异常一致性": ("兼容性", "异常", "一致性"),
    "发布与验证": ("发布", "验证方案", "核心验证点"),
    "不实现能力": ("本期不实现", "不改造范围"),
}

PLACEHOLDER_PATTERNS = (
    re.compile(r"\{\{.+?\}\}"),
    re.compile(r"\b(?:TODO|TBD|FIXME)\b", re.IGNORECASE),
    re.compile(r"待补充|待完善|此处填写"),
)

ACCEPTANCE_HEADER = ("编号", "给定", "当", "则", "测试结果")
LEGACY_ACCEPTANCE_HEADER = ("编号", "给定", "当", "则")
TEST_RESULT_PREFIXES = ("未执行", "通过", "不通过", "阻塞", "不适用")


def contains_any(text: str, candidates: tuple[str, ...]) -> bool:
    return any(candidate.lower() in text.lower() for candidate in candidates)


def find_missing(text: str, topics: dict[str, tuple[str, ...]]) -> list[str]:
    return [name for name, candidates in topics.items() if not contains_any(text, candidates)]


def markdown_cells(line: str) -> tuple[str, ...] | None:
    stripped = line.strip()
    if not stripped.startswith("|") or not stripped.endswith("|"):
        return None
    cells = []
    for cell in stripped[1:-1].split("|"):
        cells.append(re.sub(r"[*_`]", "", cell).strip())
    return tuple(cells)


def acceptance_groups(text: str) -> list[tuple[str, list[str]]]:
    lines = text.splitlines()
    section_start = next(
        (index for index, line in enumerate(lines) if re.match(r"^###\s+", line) and "验收标准" in line),
        None,
    )
    if section_start is None:
        return []

    section_end = next(
        (index for index in range(section_start + 1, len(lines)) if re.match(r"^#{1,3}\s+", lines[index])),
        len(lines),
    )
    section = lines[section_start + 1 : section_end]
    subgroup_starts = [index for index, line in enumerate(section) if re.match(r"^####\s+", line)]
    if not subgroup_starts:
        return [("验收标准", section)]

    groups: list[tuple[str, list[str]]] = []
    for position, start in enumerate(subgroup_starts):
        end = subgroup_starts[position + 1] if position + 1 < len(subgroup_starts) else len(section)
        name = re.sub(r"^####\s+", "", section[start]).replace("**", "").strip()
        groups.append((name, section[start + 1 : end]))
    return groups


def validate_acceptance_tables(text: str) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []
    groups = acceptance_groups(text)
    if not groups:
        return errors, ["未发现独立的 PRD Break 验收标准章节"]

    all_ids: list[str] = []
    for group_name, lines in groups:
        parsed = [markdown_cells(line) for line in lines]
        header_positions = [index for index, cells in enumerate(parsed) if cells == ACCEPTANCE_HEADER]
        legacy_header = any(cells == LEGACY_ACCEPTANCE_HEADER for cells in parsed)
        if not header_positions:
            if legacy_header:
                warnings.append(f"验收分组“{group_name}”缺少“测试结果”列")
            else:
                warnings.append(f"验收分组“{group_name}”未使用五列 Given-When-Then 表格")
            continue

        group_row_count = 0
        for header_index in header_positions:
            for cells in parsed[header_index + 1 :]:
                if cells is None:
                    if group_row_count:
                        break
                    continue
                if all(re.fullmatch(r":?-{3,}:?", cell) for cell in cells):
                    continue
                if len(cells) != len(ACCEPTANCE_HEADER):
                    break
                ac_id, given, when, then, result = cells
                if not ac_id.startswith("AC-"):
                    continue
                group_row_count += 1
                all_ids.append(ac_id)
                if not given or not when or not then:
                    warnings.append(f"验收用例 {ac_id} 的给定/当/则存在空值")
                if not result:
                    warnings.append(f"验收用例 {ac_id} 的测试结果为空，应初始化为“未执行”")
                elif not result.startswith(TEST_RESULT_PREFIXES):
                    warnings.append(f"验收用例 {ac_id} 使用了非标准测试结果：{result}")
        if not group_row_count:
            warnings.append(f"验收分组“{group_name}”没有以 AC- 开头的用例行")

    duplicates = sorted({ac_id for ac_id in all_ids if all_ids.count(ac_id) > 1})
    if duplicates:
        errors.append("验收用例编号重复：" + "、".join(duplicates))
    return errors, warnings


def validate(path: Path) -> dict[str, object]:
    text = path.read_text(encoding="utf-8")
    errors: list[str] = []
    warnings: list[str] = []

    if not re.search(r"(?m)^#\s+\S+", text):
        errors.append("缺少一级文档标题")
    if "PRD Break" not in text:
        errors.append("缺少 PRD Break 阶段")
    if not contains_any(text, ("改造方案", "技术方案")):
        errors.append("缺少改造方案（技术方案）阶段")

    placeholders = sorted({match.group(0) for pattern in PLACEHOLDER_PATTERNS for match in pattern.finditer(text)})
    if placeholders:
        preview = "、".join(placeholders[:8])
        suffix = "……" if len(placeholders) > 8 else ""
        errors.append(f"存在未清理占位符：{preview}{suffix}")

    missing_prd = find_missing(text, PRD_TOPICS)
    missing_tech = find_missing(text, TECH_TOPICS)
    if missing_prd:
        warnings.append("PRD Break 可能缺少主题：" + "、".join(missing_prd))
    if missing_tech:
        warnings.append("技术方案可能缺少主题：" + "、".join(missing_tech))

    acceptance_errors, acceptance_warnings = validate_acceptance_tables(text)
    errors.extend(acceptance_errors)
    warnings.extend(acceptance_warnings)

    if "本期包含" not in text or "本期不包含" not in text:
        warnings.append("建议同时明确“本期包含”和“本期不包含”")
    if not contains_any(text, ("历史数据", "存量数据", "历史与版本兼容")):
        warnings.append("未发现历史数据兼容策略")
    if not contains_any(text, ("幂等", "防重", "重复请求", "不涉及幂等")):
        warnings.append("未发现幂等或防重说明")
    if not contains_any(text, ("回滚", "可逆", "无需回滚")):
        warnings.append("未发现回滚说明")

    return {
        "path": str(path),
        "errors": errors,
        "warnings": warnings,
        "ok": not errors,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="检查需求实施方案的结构和明显遗漏")
    parser.add_argument("plan", type=Path, help="待检查的 Markdown 实施方案")
    parser.add_argument("--strict", action="store_true", help="将警告也视为失败")
    parser.add_argument("--json", action="store_true", help="以 JSON 输出结果")
    args = parser.parse_args()

    if not args.plan.is_file():
        print(f"ERROR: 文件不存在：{args.plan}", file=sys.stderr)
        return 2

    try:
        result = validate(args.plan)
    except UnicodeDecodeError:
        print(f"ERROR: 文件不是 UTF-8 文本：{args.plan}", file=sys.stderr)
        return 2

    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        print(f"文件：{result['path']}")
        for error in result["errors"]:
            print(f"ERROR: {error}")
        for warning in result["warnings"]:
            print(f"WARN: {warning}")
        if result["ok"] and not result["warnings"]:
            print("OK: 结构检查通过，未发现警告")
        elif result["ok"]:
            print("OK: 基础结构检查通过，请人工处理警告")

    failed = bool(result["errors"]) or (args.strict and bool(result["warnings"]))
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
