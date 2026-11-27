#!/usr/bin/env python3
"""Считает вектор диафрагмы для splash и пишет VectorDrawable.

Геометрия: вьюпорт 108×108 dp, полезная зона 66×66 по центру (правило adaptive icon).
Круг R=33, внутри отверстие-шестиугольник и шесть прямых прорезей, идущих от вершин
шестиугольника к внешней окружности со сдвигом DELTA по часовой стрелке. Всё это —
один path с fillType="evenOdd": диск, шестиугольник и прорези дают ровно то, что нужно
(лепестки), причём отверстие и прорези прозрачные — сквозь них виден градиент
брендированного слоя, а системный splash показывает фон-изумруд.

Запуск:  python splash/make_shutter_vector.py
Результат: app/src/main/res/drawable/ic_splash_shutter.xml + preview в splash/
"""

from __future__ import annotations

import math
from pathlib import Path

HERE = Path(__file__).resolve().parent
TARGET = HERE.parent / "app" / "src" / "main" / "res" / "drawable" / "ic_splash_shutter.xml"

VIEWPORT = 108.0
CENTER = VIEWPORT / 2
OUTER_R = 33.0
HOLE_R = 13.5      # радиус описанной окружности шестиугольного отверстия
SLIT_DELTA = 47.0  # на сколько градусов сдвинут внешний конец прорези
SLIT_HALF = 1.15   # половина толщины прорези, dp
BLADES = 6
FILL = "#7FD8B0"


def polar(radius: float, angle_deg: float) -> tuple[float, float]:
    angle = math.radians(angle_deg - 90.0)  # 0° — вверх, дальше по часовой стрелке
    return CENTER + radius * math.cos(angle), CENTER + radius * math.sin(angle)


def circle_path(radius: float) -> str:
    return (
        f"M{CENTER:.2f},{CENTER - radius:.2f} "
        f"a{radius:.2f},{radius:.2f} 0 1,1 0,{2 * radius:.2f} "
        f"a{radius:.2f},{radius:.2f} 0 1,1 0,{-2 * radius:.2f} Z"
    )


def hexagon_path() -> str:
    points = [polar(HOLE_R, index * 360.0 / BLADES) for index in range(BLADES)]
    start = f"M{points[0][0]:.2f},{points[0][1]:.2f}"
    rest = "".join(f" L{x:.2f},{y:.2f}" for x, y in points[1:])
    return f"{start}{rest} Z"


def slit_quad(index: int) -> list[tuple[float, float]]:
    angle = index * 360.0 / BLADES
    x0, y0 = polar(HOLE_R, angle)
    x1, y1 = polar(OUTER_R, angle + SLIT_DELTA)

    dx, dy = x1 - x0, y1 - y0
    length = math.hypot(dx, dy)
    nx, ny = -dy / length * SLIT_HALF, dx / length * SLIT_HALF

    return [(x0 + nx, y0 + ny), (x1 + nx, y1 + ny), (x1 - nx, y1 - ny), (x0 - nx, y0 - ny)]


def path_data() -> str:
    parts = [circle_path(OUTER_R), hexagon_path()]
    for index in range(BLADES):
        quad = slit_quad(index)
        start = f"M{quad[0][0]:.2f},{quad[0][1]:.2f}"
        rest = "".join(f" L{x:.2f},{y:.2f}" for x, y in quad[1:])
        parts.append(f"{start}{rest} Z")
    return " ".join(parts)


VECTOR = """<?xml version="1.0" encoding="utf-8"?>
<!-- Диафрагма для splash: 6 лепестков, отверстие и прорези прозрачные (fillType=evenOdd).
     Сгенерировано splash/make_shutter_vector.py — правь скрипт, а не файл руками. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <path
        android:fillColor="{fill}"
        android:fillType="evenOdd"
        android:pathData="{data}" />
</vector>
"""


def render_preview(target: Path) -> None:
    from PIL import Image, ImageDraw

    size = 1080
    scale = size / VIEWPORT
    mint, background = (127, 216, 176), (14, 107, 79)
    img = Image.new("RGB", (size, size), background)
    draw = ImageDraw.Draw(img)

    draw.ellipse(
        [CENTER * scale - OUTER_R * scale, CENTER * scale - OUTER_R * scale,
         CENTER * scale + OUTER_R * scale, CENTER * scale + OUTER_R * scale],
        fill=mint,
    )
    draw.polygon([(x * scale, y * scale) for x, y in
                  (polar(HOLE_R, index * 60) for index in range(BLADES))], fill=background)
    for index in range(BLADES):
        draw.polygon([(x * scale, y * scale) for x, y in slit_quad(index)], fill=background)

    img.save(target)
    print(f"{target}  {img.width}x{img.height}")


def main() -> None:
    data = path_data()
    TARGET.write_text(VECTOR.format(fill=FILL, data=data), encoding="utf-8")
    print(f"{TARGET}  ({len(data)} chars)")
    render_preview(HERE / "_shutter-vector-preview.png")


if __name__ == "__main__":
    main()