#!/usr/bin/env python3
"""Ретушь календарного макета: ровная сетка ноября 2026, корректная легенда.

Генеративная модель хорошо рисует стиль, но путает числа в сетке месяца.
Скрипт перерисовывает только внутренности карточки (дни, точки, легенду)
и заголовок тренда, сохраняя фон, тени и остальной экран.
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "mockups" / "02-calendar.png"
FONT = ROOT / "fonts"

CX = [184, 277, 370, 464, 556, 649, 741]  # центры семи колонок
TOP = 430  # центр первой строки чисел
ROW = 90
TEXT = (32, 35, 31)
MUTED = (83, 100, 92)
GREEN = (14, 107, 79)
AMBER = (239, 138, 0)
GREY = (216, 214, 204)
WHITE = (255, 255, 255)


def font(weight: int, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONT / f"Manrope-{weight}.ttf"), size)


def main() -> None:
    im = Image.open(SRC).convert("RGB")
    d = ImageDraw.Draw(im)

    # --- Чистим область подписи дней недели и сетку ---
    d.rectangle((134, 310, 796, 358), fill=WHITE)
    d.rectangle((134, 390, 796, 935), fill=WHITE)
    d.rectangle((134, 962, 796, 1032), fill=WHITE)
    d.rectangle((140, 1118, 560, 1162), fill=WHITE)
    d.rectangle((140, 1164, 640, 1196), fill=WHITE)

    # --- Дни недели ---
    for label, cx in zip(["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"], CX):
        d.text((cx, 334), label, font=font(500, 23), fill=MUTED, anchor="mm")

    # --- Ноябрь 2026: 1-е — воскресенье, неделя с понедельника ---
    leading = 6
    empty = {2, 3, 7, 8, 16, 22, 26, 27, 28, 29, 30}
    little = {5, 11, 19, 24}
    for day in range(1, 31):
        cell = leading + day - 1
        row, col = divmod(cell, 7)
        cx, cy = CX[col], TOP + row * ROW
        if day == 25:
            r = 27
            d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=GREEN)
            number_color = WHITE
            number_font = font(600, 26)
        else:
            number_color = TEXT
            number_font = font(600, 30)
        d.text((cx, cy), str(day), font=number_font, fill=number_color, anchor="mm")

        dot = GREEN if day not in empty | little else (AMBER if day in little else GREY)
        dr = 6
        d.ellipse((cx - dr, cy + 32 - dr, cx + dr, cy + 32 + dr), fill=dot)

    # --- Легенда ---
    legend = [
        (184, GREEN, "Полный день"),
        (424, AMBER, "Мало данных"),
        (640, GREY, "Пусто"),
    ]
    for x, color, label in legend:
        r = 7
        d.ellipse((x - r, 995 - r, x + r, 995 + r), fill=color)
        d.text((x + 18, 995), label, font=font(500, 21), fill=TEXT, anchor="lm")

    # --- Заголовок тренда (в генерации была опечатка) ---
    d.text((158, 1140), "Калории по дням", font=font(700, 30), fill=(23, 33, 28), anchor="lm")
    d.text((158, 1180), "Цель 2 100 ккал", font=font(500, 20), fill=MUTED, anchor="lm")

    im.save(SRC, optimize=True)
    print(f"patched -> {SRC}")


if __name__ == "__main__":
    main()