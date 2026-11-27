#!/usr/bin/env python3
"""Собирает векторный splash SnapMeal из двух drawable.

Делим не из вредности: у фона и у логотипа разная судьба при растягивании.
- `ic_splash_bg.xml` — градиент и световые пятна. Тянется на весь экран (FillBounds),
  а любая мягкая растяжка градиента невидима.
- `ic_splash_logo.xml` — диафрагма и надписи. Масштабируется с сохранением пропорций
  (Fit), поэтому круглая диафрагма остаётся круглой на любом соотношении сторон.

Геометрия и цвета сняты с макета `splash/v8-snapmeal.png` замерами по пикселям.
Текст — обводки Manrope через fontTools, то есть растра в сборке нет вовсе.

Запуск:  python splash/make_splash_vector.py
"""

from __future__ import annotations

import math
from pathlib import Path

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
FONTS = ROOT / "design" / "fonts"
RES = ROOT / "app" / "src" / "main" / "res" / "drawable"
BG_TARGET = RES / "ic_splash_bg.xml"
LOGO_TARGET = RES / "ic_splash_logo.xml"

# --- фон: 9:20, чтобы на современных телефонах почти не было обрезки ----------
BG_W, BG_H = 1080.0, 2408.0
BG_TOP = "#FF10553C"
BG_BOTTOM = "#FF0D4F3A"

# центр X, центр Y, радиус, цвет
BLOBS = (
    (-40.0, -60.0, 560.0, "#FFBFE56F"),
    (1180.0, 2320.0, 620.0, "#FFFFB77D"),
)

# --- логотип: полоса контента из макета (диафрагма → подпись) ------------------
LOGO_W, LOGO_H = 1080.0, 792.0

APERTURE_CENTER = (539.0, 262.0)
APERTURE_DIAMETER = 517.0
APERTURE_UNITS = 66.0
APERTURE_R = APERTURE_UNITS / 2
APERTURE_HOLE_R = 13.5
APERTURE_SLIT_DELTA = 47.0
APERTURE_SLIT_HALF = 1.15
MINT = "#FF98E2C3"

WORDMARK = dict(text="SnapMeal", font="Manrope-700.ttf", width=646.0, left=216.0, top=592.0)
TAGLINE = dict(text="Дневник питания по фото", font="Manrope-500.ttf", width=526.0, left=284.0, top=756.0)


# --- общее -------------------------------------------------------------------


def circle_data(cx: float, cy: float, r: float) -> str:
    return (
        f"M{cx - r:.1f},{cy:.1f} "
        f"a{r:.1f},{r:.1f} 0 1,1 {2 * r:.1f},0 "
        f"a{r:.1f},{r:.1f} 0 1,1 {-2 * r:.1f},0 Z"
    )


def aperture_path() -> str:
    """Диск + шестиугольное отверстие + 6 прорезей одним контуром (evenOdd)."""
    k = APERTURE_DIAMETER / APERTURE_UNITS
    cx, cy = APERTURE_CENTER

    def polar(radius: float, angle_deg: float) -> tuple[float, float]:
        angle = math.radians(angle_deg - 90.0)
        return cx + radius * k * math.cos(angle), cy + radius * k * math.sin(angle)

    parts = [circle_data(cx, cy, APERTURE_R * k)]
    hole = [polar(APERTURE_HOLE_R, index * 60.0) for index in range(6)]
    parts.append("M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in hole) + " Z")

    for index in range(6):
        angle = index * 60.0
        x0, y0 = polar(APERTURE_HOLE_R, angle)
        x1, y1 = polar(APERTURE_R, angle + APERTURE_SLIT_DELTA)
        dx, dy = x1 - x0, y1 - y0
        length = math.hypot(dx, dy)
        nx, ny = -dy / length * APERTURE_SLIT_HALF * k, dx / length * APERTURE_SLIT_HALF * k
        quad = [(x0 + nx, y0 + ny), (x1 + nx, y1 + ny), (x1 - nx, y1 - ny), (x0 - nx, y0 - ny)]
        parts.append("M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in quad) + " Z")
    return " ".join(parts)


def text_outline(spec: dict) -> str:
    font = TTFont(FONTS / spec["font"])
    cmap = font.getBestCmap()
    hmtx = font["hmtx"]
    glyph_set = font.getGlyphSet()

    run: list[tuple[str, float]] = []
    offset = 0.0
    for char in spec["text"]:
        name = cmap.get(ord(char))
        if name is None:
            continue
        run.append((name, offset))
        offset += hmtx[name][0]

    x_min = y_min = math.inf
    x_max = y_max = -math.inf
    for name, dx in run:
        pen = BoundsPen(glyph_set)
        glyph_set[name].draw(pen)
        if pen.bounds is None:
            continue
        bx0, by0, bx1, by1 = pen.bounds
        x_min = min(x_min, bx0 + dx)
        x_max = max(x_max, bx1 + dx)
        y_min = min(y_min, by0)
        y_max = max(y_max, by1)

    scale = spec["width"] / (x_max - x_min)
    dx_base = spec["left"] - x_min * scale
    dy_base = spec["top"] + y_max * scale

    pieces: list[str] = []
    for name, dx in run:
        pen = SVGPathPen(glyph_set)
        transform = (scale, 0.0, 0.0, -scale, dx_base + dx * scale, dy_base)
        glyph_set[name].draw(TransformPen(pen, transform))
        commands = pen.getCommands()
        if commands:
            pieces.append(commands)
    return " ".join(pieces)


BG_VECTOR = """<?xml version="1.0" encoding="utf-8"?>
<!-- Фон сплэша SnapMeal: градиент и два световых пятна. Тянется на весь экран.
     Сгенерировано splash/make_splash_vector.py — правь скрипт, а не файл руками. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="360dp"
    android:height="803dp"
    android:viewportWidth="1080"
    android:viewportHeight="2408">

    <path android:pathData="M0,0 H1080 V2408 H0 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="0"
                android:endY="2408">
                <item android:offset="0" android:color="{top}" />
                <item android:offset="1" android:color="{bottom}" />
            </gradient>
        </aapt:attr>
    </path>
{blobs}</vector>
"""

BLOB = """
    <path android:pathData="{data}">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="{cx:.0f}"
                android:centerY="{cy:.0f}"
                android:gradientRadius="{radius:.0f}">
                <item android:offset="0" android:color="{color}" />
                <item android:offset="1" android:color="{transparent}" />
            </gradient>
        </aapt:attr>
    </path>
"""

LOGO_VECTOR = """<?xml version="1.0" encoding="utf-8"?>
<!-- Логотип сплэша SnapMeal: диафрагма и надписи в кривых Manrope.
     Масштабируется с сохранением пропорций, растра нет.
     Сгенерировано splash/make_splash_vector.py — правь скрипт, а не файл руками. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="360dp"
    android:height="264dp"
    android:viewportWidth="1080"
    android:viewportHeight="792">

    <path
        android:fillColor="{mint}"
        android:fillType="evenOdd"
        android:pathData="{aperture}" />

    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{wordmark}" />

    <path
        android:fillColor="#B8FFFFFF"
        android:pathData="{tagline}" />
</vector>
"""


def main() -> None:
    blobs = "".join(
        BLOB.format(
            cx=cx, cy=cy, radius=radius, color=color,
            # "#FFBFE56F" -> "#00BFE56F": та же краска, но полностью прозрачная.
            transparent="#00" + color[3:], data=circle_data(cx, cy, radius),
        )
        for cx, cy, radius, color in BLOBS
    )
    bg = BG_VECTOR.format(top=BG_TOP, bottom=BG_BOTTOM, blobs=blobs)
    BG_TARGET.write_text(bg, encoding="utf-8")
    print(f"{BG_TARGET}  ({len(bg) / 1024:.1f} KB)")

    logo = LOGO_VECTOR.format(
        mint=MINT,
        aperture=aperture_path(),
        wordmark=text_outline(WORDMARK),
        tagline=text_outline(TAGLINE),
    )
    LOGO_TARGET.write_text(logo, encoding="utf-8")
    print(f"{LOGO_TARGET}  ({len(logo) / 1024:.1f} KB)")


if __name__ == "__main__":
    main()