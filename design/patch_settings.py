#!/usr/bin/env python3
"""Дополняет экран настроек двумя карточками в пустой нижней части.

В сгенерированном макете после «Статистики» оставалось большое пустое поле,
из-за чего экран выглядел незаконченным. Добавляем «Данные» и «О приложении»
в том же ритме, что и остальные карточки.
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "mockups" / "raw" / "05-settings.png"
DST = ROOT / "mockups" / "05-settings.png"
FONT = ROOT / "fonts"

WHITE = (255, 255, 255)
INK = (23, 33, 28)
MUTED = (83, 100, 92)
GREEN = (14, 107, 79)
MINT = (219, 241, 228)
EDGE = (243, 239, 229)


def font(weight: int, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONT / f"Manrope-{weight}.ttf"), size)


def card(d: ImageDraw.ImageDraw, top: int, height: int = 146) -> None:
    d.rounded_rectangle((120, top, 808, top + height), radius=26, fill=WHITE, outline=EDGE, width=1)


def lock(d: ImageDraw.ImageDraw, cx: int, cy: int) -> None:
    d.ellipse((cx - 24, cy - 24, cx + 24, cy + 24), fill=MINT)
    d.rounded_rectangle((cx - 10, cy - 2, cx + 10, cy + 14), radius=4, outline=GREEN, width=3)
    d.arc((cx - 8, cy - 16, cx + 8, cy + 2), start=180, end=360, fill=GREEN, width=3)


def info(d: ImageDraw.ImageDraw, cx: int, cy: int) -> None:
    d.ellipse((cx - 24, cy - 24, cx + 24, cy + 24), fill=MINT)
    d.ellipse((cx - 12, cy - 12, cx + 12, cy + 12), outline=GREEN, width=3)
    d.line((cx, cy - 5, cx, cy + 5), fill=GREEN, width=3)
    d.ellipse((cx - 2, cy - 10, cx + 2, cy - 6), fill=GREEN)


def chevron(d: ImageDraw.ImageDraw, x: int, y: int) -> None:
    d.line((x - 5, y - 8, x + 3, y), fill=INK, width=3)
    d.line((x + 3, y, x - 5, y + 8), fill=INK, width=3)


def main() -> None:
    im = Image.open(SRC).convert("RGB")
    d = ImageDraw.Draw(im)

    # --- Данные ---
    top = 1112
    card(d, top)
    lock(d, 166, top + 73)
    d.text((214, top + 48), "Данные", font=font(600, 26), fill=INK, anchor="lm")
    d.text((214, top + 88), "Хранятся только на устройстве", font=font(500, 19), fill=MUTED, anchor="lm")

    # --- О приложении ---
    top = 1274
    card(d, top)
    info(d, 166, top + 73)
    d.text((214, top + 48), "О приложении", font=font(600, 26), fill=INK, anchor="lm")
    d.text((214, top + 88), "Версия 0.1.0", font=font(500, 19), fill=MUTED, anchor="lm")
    chevron(d, 768, top + 73)

    im.save(DST, optimize=True)
    print(f"patched -> {DST}")


if __name__ == "__main__":
    main()