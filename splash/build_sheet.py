#!/usr/bin/env python3
"""Собирает обзорный лист вариантов splash screen из отдельных PNG.

Раскладка повторяет icons/variants-sheet-*.png: тёплый кремовый фон, заголовок
Manrope, полоса палитры и сетка карточек. Число колонок и размер карточки
подбираются по количеству вариантов, ряды центрируются по ширине листа.

Запуск:  python splash/build_sheet.py
Результат: splash/variants-sheet.png
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
FONTS = HERE.parent / "design" / "fonts"

PAPER = (244, 241, 232)
INK = (23, 33, 28)
MUTED = (83, 100, 92)
CARD = (255, 255, 255)
SHADOW = (232, 227, 214)

# имя файла, заголовок, подпись
VARIANTS = [
    ("v1-ring-on-paper", "Кольцо на бумаге", "Светлая тема · фон #F4F1E8"),
    ("v2-emerald-shutter", "Изумрудный затвор", "Фон #0E6B4F · глиф #7FD8B0"),
    ("v3-night-ring", "Ночное кольцо", "Тёмная тема · фон #101613"),
    ("v4-lime-flash", "Лаймовая вспышка", "Фон #C8F26A · знак #0E6B4F"),
    ("v5-plate-line", "Тонкая линия", "Линейная графика на бумаге"),
    ("v6-midday-gradient", "Полдень", "Градиент изумруд — хвоя, кольцо"),
    ("v7-midday-shutter", "Полдень с затвором", "Выбран · градиент и диафрагма"),
    ("v8-snapmeal", "SnapMeal", "Финальный ассет с новым названием"),
]

PALETTE = [
    ("#F4F1E8", (244, 241, 232)),
    ("#0E6B4F", (14, 107, 79)),
    ("#7FD8B0", (127, 216, 176)),
    ("#C8F26A", (200, 242, 106)),
    ("#FFB77D", (255, 183, 125)),
    ("#101613", (16, 22, 19)),
]


def font(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / name), size)


def normalize(path: Path) -> None:
    """Модель отдаёт JPEG даже при --size; приводим файлы к настоящему PNG."""
    with path.open("rb") as handle:
        if handle.read(8) == b"\x89PNG\r\n\x1a\n":
            return
    Image.open(path).convert("RGB").save(path, "PNG", optimize=True)
    print(f"re-encoded to PNG: {path.name}")


def rounded_cover(path: Path, width: int, radius: int) -> Image.Image:
    """Масштабирует splash по ширине и скругляет углы под маску телефона."""
    normalize(path)
    src = Image.open(path).convert("RGB")
    scale = width / src.width
    img = src.resize((width, int(round(src.height * scale))), Image.LANCZOS)
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, img.width, img.height), radius, fill=255)
    out = Image.new("RGB", img.size, CARD)
    out.paste(img, (0, 0), mask)
    return out


def main() -> None:
    margin, gap = 70, 40
    cols = 4 if len(VARIANTS) > 6 else 3
    card_w = 380 if cols == 4 else 460
    image_w = card_w - 100
    gap = 40 if cols == 4 else gap
    sheet_w = margin * 2 + card_w * cols + gap * (cols - 1)

    probe = Image.open(HERE / f"{VARIANTS[0][0]}.png")
    image_h = int(round(image_w * probe.height / probe.width))
    card_h = 26 + image_h + 22 + 40 + 40 + 40
    rows = (len(VARIANTS) + cols - 1) // cols

    header_h = 330
    sheet_h = header_h + rows * card_h + (rows - 1) * gap + margin

    sheet = Image.new("RGB", (sheet_w, sheet_h), PAPER)
    draw = ImageDraw.Draw(sheet)

    draw.text((margin, 62), "FitApp — варианты splash screen", font=font("Manrope-800.ttf", 58), fill=INK)
    draw.text(
        (margin, 146),
        "v8 — финальный ассет SnapMeal; v1–v7 — история направления под прежним именем.",
        font=font("Manrope-400.ttf", 27),
        fill=MUTED,
    )

    chip_x = margin
    for hex_name, rgb in PALETTE:
        draw.rounded_rectangle((chip_x, 214, chip_x + 62, 256), 10, fill=rgb, outline=SHADOW, width=1)
        draw.text((chip_x + 74, 220), hex_name, font=font("Manrope-500.ttf", 23), fill=INK)
        chip_x += 74 + 128

    title_size, caption_size, name_size = (27, 19, 17) if cols == 4 else (31, 21, 19)

    for index, (name, title, caption) in enumerate(VARIANTS):
        row, col = divmod(index, cols)
        in_row = min(cols, len(VARIANTS) - row * cols)
        row_w = in_row * card_w + (in_row - 1) * gap
        x = (sheet_w - row_w) // 2 + col * (card_w + gap)
        y = header_h + row * (card_h + gap)

        draw.rounded_rectangle((x + 4, y + 6, x + card_w - 4, y + card_h - 2), 30, fill=SHADOW)
        draw.rounded_rectangle((x, y, x + card_w, y + card_h - 8), 30, fill=CARD)

        shot = rounded_cover(HERE / f"{name}.png", image_w, 26)
        sheet.paste(shot, (x + (card_w - image_w) // 2, y + 26))

        title_y = y + 26 + image_h + 22
        draw.text((x + 26, title_y), f"{index + 1}. {title}", font=font("Manrope-700.ttf", title_size), fill=INK)
        draw.text((x + 26, title_y + 40), caption, font=font("Manrope-400.ttf", caption_size), fill=MUTED)
        draw.text((x + 26, title_y + 68), name, font=font("Manrope-400.ttf", name_size), fill=(150, 160, 152))

    target = HERE / "variants-sheet.png"
    sheet.save(target, "PNG", optimize=True)
    print(f"{target}  {sheet.width}x{sheet.height}")


if __name__ == "__main__":
    main()