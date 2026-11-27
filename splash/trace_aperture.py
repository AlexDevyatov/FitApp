#!/usr/bin/env python3
"""Снимает диафрагму с макета векторным контуром.

Геометрическая реконструкция «шесть лепестков из дуг» давала похожее, но не то же:
у макета лепестки не идеально симметричны, и любая формула расходится с картинкой.
Здесь контур снимается с пиксельной маски (matplotlib contour на уровне 0.5) и
упрощается Дугласом—Пекером: диафрагма на сплэше совпадает с макетом, а не напоминает его.

Лепестки в маске не связаны между собой (прорези доходят до края), поэтому контуров
шесть — по одному на лепесток. Векторная геометрия от этого не страдает: заливка
один path с шестью замкнутыми подконтурами.

Запуск:  python splash/trace_aperture.py  → печатает pathData (его берёт make_splash_vector.py)
"""

from __future__ import annotations

import math
from pathlib import Path

import matplotlib

matplotlib.use("Agg")

import numpy as np
from matplotlib import pyplot as plt
from PIL import Image

HERE = Path(__file__).resolve().parent
MOCKUP = HERE / "v8-snapmeal.png"

# Окно трассировки в пикселях макета: диафрагма занимает 283..796 по X и 562..1091 по Y.
# Верх окна и есть начало координат полотна логотипа: так над диафрагмой остаётся
# 10 единиц запаса и контур не упирается в край viewport (иначе верх сплющивался).
CROP = (270, 552, 810, 1104)
LUMA_THRESHOLD = 140
SIMPLIFY_EPS = 0.9


def load_mask() -> np.ndarray:
    image = Image.open(MOCKUP).convert("RGB").crop(CROP)
    pixels = np.asarray(image, dtype=np.float32)
    luma = 0.299 * pixels[:, :, 0] + 0.587 * pixels[:, :, 1] + 0.114 * pixels[:, :, 2]
    return luma > LUMA_THRESHOLD


def simplify(points: np.ndarray, eps: float) -> list[tuple[float, float]]:
    """Ramer—Douglas—Peucker без рекурсии."""
    pts = [tuple(map(float, point)) for point in points]
    if len(pts) > 2 and math.dist(pts[0], pts[-1]) < 1.0:
        # Контур замкнут: без этого опорный отрезок нулевой и RDP не делит его вовсе.
        pts = pts[:-1]
    if len(pts) < 3:
        return pts
    keep = [False] * len(pts)
    keep[0] = keep[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        lo, hi = stack.pop()
        if hi <= lo + 1:
            continue
        ax, ay = pts[lo]
        bx, by = pts[hi]
        dx, dy = bx - ax, by - ay
        norm = math.hypot(dx, dy) or 1e-9
        worst, index = 0.0, lo
        for i in range(lo + 1, hi):
            px, py = pts[i]
            distance = abs(dy * (px - ax) - dx * (py - ay)) / norm
            if distance > worst:
                worst, index = distance, i
        if worst > eps:
            keep[index] = True
            stack.append((lo, index))
            stack.append((index, hi))
    return [point for point, kept in zip(pts, keep) if kept]


def blade_contours() -> list[np.ndarray]:
    mask = load_mask().astype(float)
    figure, axes = plt.subplots()
    contour_set = axes.contour(mask, levels=[0.5])
    segments = list(contour_set.allsegs[0])
    plt.close(figure)
    segments.sort(key=len, reverse=True)
    return [seg for seg in segments if len(seg) > 40]


def path_data() -> str:
    # Контур снят в координатах окна, а окно и есть полотно логотипа: сдвиг только по X,
    # потому что окно начинается от левого края макета.
    x_shift = CROP[0]
    parts: list[str] = []
    for segment in blade_contours():
        points = simplify(segment, SIMPLIFY_EPS)
        if len(points) < 3:
            continue
        start = points[0]
        chunk = [f"M{start[0] + x_shift:.1f},{start[1]:.1f}"]
        chunk += [f"L{x + x_shift:.1f},{y:.1f}" for x, y in points[1:]]
        chunk.append("Z")
        parts.append(" ".join(chunk))
    return " ".join(parts)


if __name__ == "__main__":
    contours = blade_contours()
    data = path_data()
    print(f"лепестков: {len(contours)}, длина pathData: {len(data)} символов")
    for index, segment in enumerate(contours):
        xs, ys = segment[:, 0], segment[:, 1]
        print(
            f"  лепесток {index + 1}: bbox x {xs.min():.0f}..{xs.max():.0f}, "
            f"y {ys.min() + CROP[1]:.0f}..{ys.max() + CROP[1]:.0f}"
        )