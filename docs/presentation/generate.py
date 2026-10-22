#!/usr/bin/env python3
"""生成 QO 智能直驱 README 的 SVG 视觉素材，仅使用 Python 标准库。"""

import argparse
from html import escape
from pathlib import Path


PALETTES = {
    "light": {
        "bg": "#F4F7FC", "end": "#E8F0FF", "ink": "#13243F",
        "muted": "#536883", "accent": "#245CE0", "line": "#CBD9F0",
        "panel": "#FFFFFF", "soft": "#DDE9FF", "edge": "#DDE5F2",
    },
    "dark": {
        "bg": "#101827", "end": "#172C4D", "ink": "#F1F5FF",
        "muted": "#ACBDD7", "accent": "#8EBAFF", "line": "#314D73",
        "panel": "#1B2B44", "soft": "#233F65", "edge": "#2B4262",
    },
}
FONT = "'Noto Sans CJK SC','PingFang SC','Microsoft YaHei',sans-serif"


def text(x, y, value, size, color, weight=400, extra=""):
    return (f'<text x="{x}" y="{y}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}" {extra}>{escape(value)}</text>')


def rect(x, y, w, h, r, fill, stroke="none"):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" '
            f'rx="{r}" fill="{fill}" stroke="{stroke}"/>')


def document(w, h, p, title, description, content):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" role="img" aria-labelledby="title desc">
<title id="title">{escape(title)}</title>
<desc id="desc">{escape(description)}</desc>
<defs>
  <linearGradient id="surface" x1="0" y1="0" x2="1" y2="1">
    <stop stop-color="{p['bg']}"/><stop offset="1" stop-color="{p['end']}"/>
  </linearGradient>
  <linearGradient id="electric" x1="0" y1="1" x2="1" y2="0">
    <stop stop-color="#3566E8"/><stop offset="1" stop-color="#79BEFF"/>
  </linearGradient>
</defs>
{rect(1, 1, w-2, h-2, 28, 'url(#surface)', p['edge'])}
<g font-family="{FONT}">
{content}
</g>
</svg>
'''


def mark(x, y, size):
    return f'''<g transform="translate({x} {y}) scale({size/64})">
{rect(0, 0, 64, 64, 18, 'url(#electric)')}
<path d="M37 10 19 35h13l-5 19 20-28H34Z" fill="#FFFFFF"/>
</g>'''


def power_symbol(x, y, scale, p):
    return f'''<g transform="translate({x} {y}) scale({scale})">
<circle r="156" fill="none" stroke="{p['line']}"/>
<circle r="131" fill="none" stroke="{p['line']}" stroke-dasharray="2 13"/>
<path d="M-156 0H-199a18 18 0 0 1-18-18v-45M156 0h43a18 18 0 0 0 18-18v-45" fill="none" stroke="{p['line']}" stroke-width="2"/>
<circle cx="-217" cy="-68" r="5" fill="{p['accent']}"/>
<circle cx="217" cy="-68" r="5" fill="{p['accent']}"/>
<circle r="107" fill="{p['panel']}" stroke="{p['edge']}"/>
<path d="M-66-64a92 92 0 1 0 132 0" fill="none" stroke="url(#electric)" stroke-width="12" stroke-linecap="round"/>
<path d="M14-72-41 10h34l-13 65L43-14H7Z" fill="url(#electric)"/>
</g>'''


def hero(theme, mobile=False):
    p = PALETTES[theme]
    if mobile:
        w, h = 720, 820
        content = mark(44, 42, 52)
        content += text(114, 65, "iQSOO  /  OPEN SOURCE", 20, p['accent'], 700,
                        'letter-spacing="2"')
        content += text(114, 94, "新好友开发 · iqsoo.com 开源发布", 21, p['muted'])
        content += text(44, 200, "QO 智能直驱", 66, p['ink'], 700)
        content += text(46, 252, "原生直驱，按需接管。", 31, p['muted'], 500)
        content += power_symbol(360, 470, 0.95, p)
        content += text(360, 686, "Android 16  /  Shizuku  /  无需 Root", 25,
                        p['muted'], 500, 'text-anchor="middle"')
        content += f'<path d="M44 724H676" stroke="{p["line"]}"/>'
        content += text(44, 773, "iQOO Neo11 · OriginOS", 24, p['ink'], 500)
        content += text(676, 773, "01 / QO", 20, p['accent'], 700,
                        'text-anchor="end" letter-spacing="2"')
    else:
        w, h = 1200, 500
        content = mark(48, 42, 52)
        content += text(118, 65, "iQSOO  /  OPEN SOURCE", 19, p['accent'], 700,
                        'letter-spacing="2"')
        content += text(118, 94, "新好友开发 · iqsoo.com 开源发布", 20, p['muted'])
        content += text(48, 219, "QO 智能直驱", 68, p['ink'], 700)
        content += text(50, 279, "原生直驱，按需接管。", 32, p['muted'], 500)
        content += text(50, 347, "Android 16  /  Shizuku  /  无需 Root", 23,
                        p['muted'], 500)
        content += power_symbol(938, 230, 0.92, p)
        content += f'<path d="M48 409H1152" stroke="{p["line"]}"/>'
        content += text(48, 458, "iQOO Neo11 · OriginOS", 22, p['ink'], 500)
        content += text(1152, 458, "QO DIRECT CHARGE", 19, p['accent'], 700,
                        'text-anchor="end" letter-spacing="2"')
    return document(w, h, p, "QO 智能直驱", "新好友开发，来自 iqsoo.com 的开源项目。Android 16 原生直驱智能管理。", content)


def website(theme, mobile=False):
    p = PALETTES[theme]
    w, h = (720, 330) if mobile else (1200, 240)
    content = text(44, 63, "新好友 · iQSOO", 25 if mobile else 23, p['muted'], 500)
    content += text(44, 145 if mobile else 135, "www.iqsoo.com", 62 if mobile else 61,
                    p['ink'], 700)
    content += text(46, 205 if mobile else 185, "使用教程 · 项目更新 · 实用工具", 27 if mobile else 23,
                    p['muted'])
    x, y = (610, 265) if mobile else (1088, 120)
    content += f'<circle cx="{x}" cy="{y}" r="31" fill="{p["accent"]}"/>'
    arrow = p['bg']
    content += f'<path d="M{x-11} {y}h22m-9-9 9 9-9 9" fill="none" stroke="{arrow}" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>'
    if mobile:
        content += text(46, 277, "访问官网", 25, p['accent'], 700)
    else:
        content += text(1010, 128, "访问官网", 23, p['accent'], 700,
                        'text-anchor="end"')
    return document(w, h, p, "访问 iQSOO 官网", "前往 www.iqsoo.com，查看使用教程、项目更新与更多实用工具。", content)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path,
                        default=Path(__file__).resolve().parents[1] / "assets")
    target = parser.parse_args().output
    target.mkdir(parents=True, exist_ok=True)
    for kind, build in (("hero", hero), ("website", website)):
        for theme in PALETTES:
            for mobile in (False, True):
                name = kind + ("-mobile" if mobile else "") + ("-dark" if theme == "dark" else "") + ".svg"
                (target / name).write_text(build(theme, mobile), encoding="utf-8")


if __name__ == "__main__":
    main()
