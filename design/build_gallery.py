#!/usr/bin/env python3
"""Собирает design/index.html из шаблона, встраивая шрифты base64.

Так презентация остаётся одним файлом: Manrope не зависит от системных
шрифтов и работает при открытии по file:// без CORS-сюрпризов.

    python3 design/build_gallery.py
"""

from pathlib import Path
import base64

ROOT = Path(__file__).resolve().parent
TEMPLATE = ROOT / "index.template.html"
OUTPUT = ROOT / "index.html"
WEIGHTS = (400, 500, 600, 700, 800)


def build() -> None:
    html = TEMPLATE.read_text(encoding="utf-8")
    faces = []
    for weight in WEIGHTS:
        data = base64.b64encode((ROOT / f"fonts/Manrope-{weight}.ttf").read_bytes()).decode("ascii")
        faces.append(
            "@font-face{font-family:'Manrope';font-style:normal;font-weight:%d;"
            "font-display:swap;src:url(data:font/ttf;base64,%s) format('truetype');}"
            % (weight, data)
        )
    OUTPUT.write_text(html.replace("/*__FONTS__*/", "\n".join(faces)), encoding="utf-8")
    print(f"built -> {OUTPUT} ({OUTPUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    build()