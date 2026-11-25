#!/usr/bin/env python3
"""Точечная ретушь текста на макетах: отчёт дня и онбординг с ключом.

Модель рисует стиль уверенно, но длинные русские абзацы иногда превращает
в псевдослова. Скрипт заменяет только эти фрагменты настоящим текстом,
сохраняя фон, кнопки и остальную композицию.
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
MOCKUPS = ROOT / "mockups"
FONT = ROOT / "fonts"

INK = (23, 33, 28)
BODY = (45, 47, 43)
MUTED = (74, 90, 82)
GREEN_DARK = (11, 79, 58)
GREEN = (14, 107, 79)
BUTTON = (6, 103, 85)
CREAM = (255, 251, 245)
VERDICT_BG = (225, 239, 221)


def font(weight: int, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONT / f"Manrope-{weight}.ttf"), size)


def patch_report() -> None:
    path = MOCKUPS / "04-report.png"
    im = Image.open(path).convert("RGB")
    d = ImageDraw.Draw(im)

    # Вердикт: «в пределах цели» вместо псевдослов.
    d.rectangle((150, 466, 640, 514), fill=VERDICT_BG)
    d.text((172, 490), "в пределах цели", font=font(600, 26), fill=GREEN_DARK, anchor="lm")

    # Комментарий: две фразы, без новых чисел.
    d.rectangle((145, 1508, 690, 1610), fill=(255, 255, 255))
    d.text(
        (162, 1532),
        "День ровный: белка достаточно, ужин не поздний.",
        font=font(500, 19),
        fill=BODY,
        anchor="lm",
    )
    d.text(
        (162, 1566),
        "Завтра можно добавить овощи к обеду.",
        font=font(500, 19),
        fill=BODY,
        anchor="lm",
    )

    # Кнопка обновления: вместо «плюса» — круговая стрелка в тональном круге.
    cx, cy, r = 730, 1555, 30
    d.rectangle((cx - 40, cy - 40, cx + 40, cy + 40), fill=(255, 255, 255))
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(220, 241, 229))
    d.arc((cx - 16, cy - 16, cx + 16, cy + 16), start=35, end=315, fill=GREEN, width=4)
    d.polygon(
        [(cx + 2, cy - 22), (cx + 14, cy - 18), (cx + 4, cy - 12)],
        fill=GREEN,
    )

    im.save(path, optimize=True)
    print(f"patched -> {path}")


def patch_key() -> None:
    path = MOCKUPS / "07-onboarding-key.png"
    im = Image.open(path).convert("RGB")
    d = ImageDraw.Draw(im)

    # Пояснение под заголовком.
    d.rectangle((140, 582, 800, 728), fill=CREAM)
    d.text(
        (464, 630),
        "Введите ключ доступа, чтобы приложение",
        font=font(500, 23),
        fill=MUTED,
        anchor="mm",
    )
    d.text(
        (464, 672),
        "могло распознавать еду на фотографиях.",
        font=font(500, 23),
        fill=MUTED,
        anchor="mm",
    )

    # Опечатка на главной кнопке.
    d.rectangle((250, 1342, 690, 1408), fill=BUTTON)
    d.text(
        (464, 1375),
        "Проверить ключ",
        font=font(700, 34),
        fill=(255, 255, 255),
        anchor="mm",
    )

    im.save(path, optimize=True)
    print(f"patched -> {path}")


def patch_stats() -> None:
    path = MOCKUPS / "09-stats.png"
    im = Image.open(path).convert("RGB")
    d = ImageDraw.Draw(im)

    # «Правки но дням» -> «Правки по дням»
    d.rectangle((90, 638, 420, 692), fill=(255, 255, 255))
    d.text((100, 664), "Правки по дням", font=font(700, 30), fill=INK, anchor="lm")

    # Псевдослова вместо «Последние правки»
    d.rectangle((90, 1070, 440, 1122), fill=(255, 255, 255))
    d.text((100, 1095), "Последние правки", font=font(700, 26), fill=INK, anchor="lm")

    im.save(path, optimize=True)
    print(f"patched -> {path}")


if __name__ == "__main__":
    patch_report()
    patch_key()
    patch_stats()