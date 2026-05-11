#!/usr/bin/env python3
"""生成冲刺护腿 16x16 纹理（stdlib，无需 Pillow）。与 gen-textures.ps1 配色思路一致。"""
from __future__ import annotations

import struct
import zlib
from pathlib import Path


def _chunk(chunk_type: bytes, data: bytes) -> bytes:
    crc = zlib.crc32(chunk_type + data) & 0xFFFFFFFF
    return struct.pack(">I", len(data)) + chunk_type + data + struct.pack(">I", crc)


def write_png_simple(path: Path, w: int, h: int, flat_rgba: bytes) -> None:
    """flat_rgba 长度 w*h*4，行优先。"""
    rows = []
    for y in range(h):
        row = bytearray([0])
        base = y * w * 4
        for x in range(w):
            i = base + x * 4
            row.extend(flat_rgba[i : i + 4])
        rows.append(bytes(row))
    raw = b"".join(rows)
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    png = (
        b"\x89PNG\r\n\x1a\n"
        + _chunk(b"IHDR", ihdr)
        + _chunk(b"IDAT", zlib.compress(raw, 9))
        + _chunk(b"IEND", b"")
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def make_tool_texture(handle: tuple[int, int, int], head: tuple[int, int, int], light: tuple[int, int, int], dark: tuple[int, int, int]) -> bytes:
    w, h = 16, 16
    pix = bytearray(w * h * 4)

    def setp(x: int, y: int, c: tuple[int, int, int]) -> None:
        i = (y * w + x) * 4
        pix[i : i + 3] = bytes(c)
        pix[i + 3] = 255

    hp = [(13, 14), (13, 13), (12, 13), (12, 12), (11, 12), (11, 11), (10, 11), (10, 10), (9, 10), (9, 9), (8, 9), (8, 8), (7, 8), (7, 7), (6, 7), (6, 6)]
    for x, y in hp:
        setp(x, y, handle)
    sp = [(14, 15), (14, 14), (13, 12), (12, 11), (11, 10), (10, 9), (9, 8), (8, 7), (7, 6)]
    for x, y in sp:
        if x < w and y < h:
            setp(x, y, dark)
    hf = [(3, 5), (4, 5), (2, 4), (3, 4), (4, 4), (5, 4), (2, 3), (3, 3), (4, 3), (5, 3), (3, 2), (4, 2)]
    for x, y in hf:
        setp(x, y, head)
    ho = [(3, 1), (4, 1), (2, 2), (5, 2), (1, 3), (6, 3), (1, 4), (6, 4), (2, 5), (5, 5), (3, 6), (4, 6)]
    for x, y in ho:
        setp(x, y, dark)
    setp(3, 2, light)
    setp(2, 3, light)
    return bytes(pix)


def main() -> None:
    root = Path(__file__).resolve().parents[1] / "src/main/resources/assets/bettermove"
    out_dir = root / "textures/item"
    tiers = [
        ("dash_tool_wood", (0x6E, 0x4A, 0x23), (0xA5, 0x70, 0x33), (0xD5, 0xA6, 0x6B), (0x3D, 0x28, 0x11)),
        ("dash_tool_stone", (0x6E, 0x4A, 0x23), (0x8A, 0x8A, 0x8A), (0xC0, 0xC0, 0xC0), (0x3C, 0x3C, 0x3C)),
        ("dash_tool_copper", (0x6E, 0x4A, 0x23), (0xC4, 0x6B, 0x43), (0xF0, 0xA5, 0x7C), (0x5A, 0x2C, 0x18)),
        ("dash_tool_iron", (0x6E, 0x4A, 0x23), (0xD8, 0xD8, 0xD8), (0xFF, 0xFF, 0xFF), (0x5C, 0x5C, 0x5C)),
        ("dash_tool_gold", (0x6E, 0x4A, 0x23), (0xF9, 0xD7, 0x4A), (0xFF, 0xF5, 0xB0), (0x8A, 0x6A, 0x12)),
        ("dash_tool_diamond", (0x6E, 0x4A, 0x23), (0x5E, 0xDB, 0xD3), (0xB8, 0xFF, 0xFA), (0x1F, 0x6F, 0x69)),
        ("dash_tool_netherite", (0x6E, 0x4A, 0x23), (0x4A, 0x41, 0x44), (0x7A, 0x6D, 0x70), (0x1B, 0x15, 0x17)),
    ]
    for name, handle, head, light, dark in tiers:
        data = make_tool_texture(handle, head, light, dark)
        p = out_dir / f"{name}.png"
        write_png_simple(p, 16, 16, data)
        print("wrote", p)
    icon = make_tool_texture((0x6E, 0x4A, 0x23), (0x5E, 0xDB, 0xD3), (0xB8, 0xFF, 0xFA), (0x1F, 0x6F, 0x69))
    write_png_simple(root / "icon.png", 16, 16, icon)
    print("wrote", root / "icon.png")


if __name__ == "__main__":
    main()
