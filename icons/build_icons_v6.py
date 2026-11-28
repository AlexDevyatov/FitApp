#!/usr/bin/env python3
"""Варианты иконки SnapMeal на основе сплэша.

Знак берётся не «похожий», а тот же самый: path диафрагмы читается из
`app/src/main/res/drawable/ic_splash_logo.xml`, то есть иконка и сплэш — одна
геометрия. Цвета сняты пипеткой с `artifacts/release/github-release-splash.png`:
изумруд `#0F543B`/`#0D503A`, мята `#98E2C3`, лаймовое и персиковое свечение.

Каждый вариант — 1024×1024 (полный кадр adaptive icon 108 dp) плюс то, что реально
видит лончер: центральные 72 dp из кадра под круговой маской, и превью 48 dp.

Запуск: python icons/build_icons_v6.py
"""

from __future__ import annotations

import re
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
LOGO_XML = ROOT / "app" / "src" / "main" / "res" / "drawable" / "ic_splash_logo.xml"
FONTS = ROOT / "design" / "fonts"

SIZE = 1024
SS = 2  # суперсэмплинг: рисуем в 2048 и уменьшаем
# Лончер показывает центральные 72 dp из 108 dp и режет их своей маской. Знак
# 56 dp оставляет вокруг себя видимый ободок фона; при прежних 72 dp знак
# совпадал с маской, и на круглой иконке был виден только затвор.
VISIBLE_FRACTION = 72 / 108  # диаметр маски лончера к стороне кадра
MARK_FRACTION = 0.52  # 56 dp из 108 dp
RING_MARK_FRACTION = 0.46  # в варианте с кольцом знак меньше: остаётся место кольцу
RING_OUTER_FRACTION = 0.58  # внешний диаметр кольца — 63 dp
RING_WIDTH_FRACTION = 0.017  # толщина кольца — 1.8 dp

# --- палитра, снятая со сплэша и из Theme.kt --------------------------------
EMERALD_TOP = "#0F543B"
EMERALD_BOTTOM = "#0D503A"
EMERALD = "#0E6B4F"
MINT = "#98E2C3"
MINT_SOFT = "#7FD8B0"
LIME = "#C8F26A"
LIME_GLOW = "#BFE56F"
PEACH = "#FFB77D"
CREAM = "#F4F1E8"
INK = "#101613"

VARIANTS = [
    dict(
        slug="v6-01-splash-mint",
        title="Сплэш как есть",
        idea="Мята на изумруде из сплэша",
        bg=("solid", EMERALD_TOP),
        mark=MINT,
        res="splash",
        alias="IconSplash",
    ),
    dict(
        slug="v6-02-splash-glow",
        title="Сплэш со свечением",
        idea="Градиент и лаймово-персиковые пятна",
        bg=("splash", None),
        mark=MINT,
        res="glow",
        alias="IconGlow",
    ),
    dict(
        slug="v6-03-cream-emerald",
        title="Крем и изумруд",
        idea="Инверсия: тёмный знак на кремовом",
        bg=("solid", CREAM),
        mark=EMERALD,
        res="cream",
        alias="IconCream",
    ),
    dict(
        slug="v6-04-lime-emerald",
        title="Лайм на изумруде",
        idea="Акцентный лайм темы",
        bg=("solid", EMERALD),
        mark=LIME,
        res="lime",
        alias="IconLime",
    ),
    dict(
        slug="v6-05-ink-mint",
        title="Ночь",
        idea="Мята на тёмном фоне тёмной темы",
        bg=("solid", INK),
        mark=MINT,
        res="night",
        alias="IconNight",
    ),
    dict(
        slug="v6-06-ring-mint",
        title="Затвор в кольце",
        idea="Диафрагма + кольцо объектива",
        bg=("solid", EMERALD_TOP),
        mark=MINT,
        ring=True,
        res="ring",
        alias="IconRing",
    ),
]


# --- геометрия знака ---------------------------------------------------------


def parse_paths() -> list[list[tuple[float, float]]]:
    xml = LOGO_XML.read_text(encoding="utf-8")
    match = re.search(r'fillColor="#FF98E2C3"\s+android:pathData="([^"]+)"', xml, re.S)
    if match is None:
        raise SystemExit("в ic_splash_logo.xml не нашлась диафрагма")
    data = match.group(1)
    paths = []
    for chunk in data.split("M")[1:]:
        pairs = re.findall(r"L?(-?\d+\.?\d*),(-?\d+\.?\d*)", chunk)
        points = [(float(x), float(y)) for x, y in pairs]
        if len(points) >= 3:
            paths.append(points)
    return paths


def mark_points(paths: list[list[tuple[float, float]]], size: int, fraction: float) -> list[list[tuple[float, float]]]:
    xs = [x for path in paths for x, _ in path]
    ys = [y for path in paths for _, y in path]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    scale = (size * fraction) / max(max(xs) - min(xs), max(ys) - min(ys))
    return [
        [((x - cx) * scale + size / 2, (y - cy) * scale + size / 2) for x, y in path]
        for path in paths
    ]


# --- фон ---------------------------------------------------------------------


def vertical_gradient(size: int, top: str, bottom: str) -> Image.Image:
    top_rgb = np.array([int(top[i : i + 2], 16) for i in (1, 3, 5)], dtype=np.float32)
    bottom_rgb = np.array([int(bottom[i : i + 2], 16) for i in (1, 3, 5)], dtype=np.float32)
    ramp = np.linspace(0.0, 1.0, size, dtype=np.float32)[:, None]
    rows = top_rgb * (1 - ramp) + bottom_rgb * ramp
    canvas = np.repeat(rows[:, None, :], size, axis=1)
    return Image.fromarray(canvas.round().astype(np.uint8), "RGB")


def radial_glow(size: int, cx: float, cy: float, radius: float, color: str) -> Image.Image:
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    distance = np.hypot(xx - cx * size, yy - cy * size)
    alpha = np.clip(1.0 - distance / (radius * size), 0.0, 1.0)
    rgb = np.array([int(color[i : i + 2], 16) for i in (1, 3, 5)], dtype=np.float32)
    canvas = np.zeros((size, size, 3), dtype=np.float32) + rgb
    return Image.fromarray(canvas.round().astype(np.uint8), "RGB"), alpha


def splash_background(size: int) -> Image.Image:
    """Градиент и два пятна — как в ic_splash_bg.xml, но кадрированные под иконку."""
    base = vertical_gradient(size, EMERALD_TOP, EMERALD_BOTTOM).convert("RGB")
    layers = (
        (-0.04, -0.03, 0.54, LIME_GLOW),
        (1.09, 0.97, 0.58, PEACH),
    )
    for cx, cy, radius, color in layers:
        glow, alpha = radial_glow(size, cx, cy, radius, color)
        base = Image.composite(glow, base, Image.fromarray((alpha * 255).astype(np.uint8), "L"))
    return base


def background(spec: dict, size: int) -> Image.Image:
    kind, color = spec["bg"]
    if kind == "splash":
        return splash_background(size)
    return vertical_gradient(size, color, color)


# --- сборка ------------------------------------------------------------------


def draw_icon(spec: dict) -> Image.Image:
    size = SIZE * SS
    canvas = background(spec, size)
    draw = ImageDraw.Draw(canvas)
    fraction = RING_MARK_FRACTION if spec.get("ring") else MARK_FRACTION
    points = mark_points(PARSE, size, fraction)

    if spec.get("ring"):
        # Кольцо рисуем маской (внешний круг минус внутренний): outline у PIL
        # на больших радиусах оставляет разрывы.
        radius = size * RING_OUTER_FRACTION / 2
        width = size * RING_WIDTH_FRACTION
        annulus = Image.new("L", (size, size), 0)
        ring_draw = ImageDraw.Draw(annulus)
        ring_draw.ellipse(
            (size / 2 - radius, size / 2 - radius, size / 2 + radius, size / 2 + radius), fill=255
        )
        inner = radius - width
        ring_draw.ellipse(
            (size / 2 - inner, size / 2 - inner, size / 2 + inner, size / 2 + inner), fill=0
        )
        canvas.paste(Image.new("RGB", (size, size), spec["mark"]), (0, 0), annulus)

    for path in points:
        draw.polygon(path, fill=spec["mark"])

    return canvas.resize((SIZE, SIZE), Image.LANCZOS)


def launcher_crop(icon: Image.Image) -> Image.Image:
    """Иконка глазами лончера: центральные 72 dp из 108 dp, обрезанные кругом.

    Раньше здесь была маска почти на весь кадр (108 dp), поэтому превью выглядели
    крупнее и «полнее», чем на рабочем столе: маска лончера всегда 72 dp.
    """
    size = icon.size[0]
    side = int(round(size * VISIBLE_FRACTION))
    left = (size - side) // 2
    square = icon.convert("RGBA").crop((left, left, left + side, left + side))
    mask = Image.new("L", (side, side), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, side - 1, side - 1), fill=255)
    square.putalpha(mask)
    return square


def label(draw: ImageDraw.ImageDraw, xy: tuple[int, int], text: str, size: int, fill: str, font_name: str = "Manrope-500.ttf") -> None:
    font = ImageFont.truetype(str(FONTS / font_name), size)
    draw.text(xy, text, font=font, fill=fill)


def build_sheet(icons: list[tuple[dict, Image.Image]], target: Path) -> None:
    cell, gap, pad, header = 380, 34, 40, 132
    cols = 3
    rows = (len(icons) + cols - 1) // cols
    width = pad * 2 + cols * cell + (cols - 1) * gap
    height = pad + header + rows * (cell + 118) + (rows - 1) * gap + pad
    sheet = Image.new("RGB", (width, height), "#232B27")
    draw = ImageDraw.Draw(sheet)

    label(draw, (pad, pad - 6), "SnapMeal · варианты иконки v6", 34, "#F4F1E8", "Manrope-700.ttf")
    label(
        draw,
        (pad, pad + 44),
        "знак — диафрагма со сплэша; в кадре то, что видит лончер: 72 из 108 dp, круглая маска",
        22,
        "#9BA9A2",
    )

    chips = [("изумруд", "#0F543B"), ("мята", MINT), ("лайм", LIME), ("персик", PEACH), ("крем", CREAM),
             ("ночь", INK)]
    x = pad
    for name, color in chips:
        draw.rounded_rectangle((x, pad + 84, x + 34, pad + 118), radius=8, fill=color,
                               outline="#3F4A44", width=2)
        label(draw, (x + 42, pad + 88), name, 18, "#9BA9A2")
        x += 42 + int(draw.textlength(name, font=ImageFont.truetype(str(FONTS / "Manrope-500.ttf"), 18))) + 26

    for index, (spec, icon) in enumerate(icons):
        row, col = divmod(index, cols)
        x = pad + col * (cell + gap)
        y = pad + header + row * (cell + 118 + gap)
        shot = launcher_crop(icon).resize((cell, cell), Image.LANCZOS)
        sheet.paste(shot, (x, y), shot)
        small = launcher_crop(icon).resize((72, 72), Image.LANCZOS)
        sheet.paste(small, (x + cell - 72, y + cell - 72), small)
        label(draw, (x, y + cell + 14), spec["slug"], 22, "#F4F1E8", "Manrope-700.ttf")
        label(draw, (x, y + cell + 46), spec["title"], 20, "#7FD8B0")
        label(draw, (x, y + cell + 74), spec["idea"], 18, "#9BA9A2")

    sheet.save(target)


PARSE = parse_paths()


def vector_xml(spec: dict) -> str:
    """Готовый adaptive-icon foreground: тот же контур в системе 108 dp.

    Знак 56 dp из 108 dp: лончер показывает центральные 72 dp и режет их маской,
    поэтому вокруг знака остаётся ободок фона и он не упирается в край.
    """
    dp = 108
    fraction = RING_MARK_FRACTION if spec.get("ring") else MARK_FRACTION
    points = mark_points(PARSE, dp, fraction)
    parts = []
    for path in points:
        chunk = [f"M{path[0][0]:.2f},{path[0][1]:.2f}"]
        chunk += [f"L{x:.2f},{y:.2f}" for x, y in path[1:]]
        chunk.append("Z")
        parts.append(" ".join(chunk))

    ring = ""
    if spec.get("ring"):
        radius = dp * RING_OUTER_FRACTION / 2
        ring = (
            f'\n    <path\n        android:fillColor="{spec["mark"]}"\n'
            f'        android:pathData="M{dp / 2 - radius:.2f},{dp / 2:.2f} '
            f'a{radius:.2f},{radius:.2f} 0 1,1 {2 * radius:.2f},0 '
            f'a{radius:.2f},{radius:.2f} 0 1,1 {-2 * radius:.2f},0 Z '
            f'M{dp / 2 - radius + dp * RING_WIDTH_FRACTION:.2f},{dp / 2:.2f} '
            f'a{radius - dp * RING_WIDTH_FRACTION:.2f},{radius - dp * RING_WIDTH_FRACTION:.2f} 0 1,0 '
            f'{2 * (radius - dp * RING_WIDTH_FRACTION):.2f},0 '
            f'a{radius - dp * RING_WIDTH_FRACTION:.2f},{radius - dp * RING_WIDTH_FRACTION:.2f} 0 1,0 '
            f'{-2 * (radius - dp * RING_WIDTH_FRACTION):.2f},0 Z"\n        android:fillType="evenOdd" />'
        )

    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Знак SnapMeal: диафрагма со сплэша, вписанная в зону контента adaptive icon (72 из 108 dp).
     Сгенерировано icons/build_icons_v6.py из res/drawable/ic_splash_logo.xml. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="{spec["mark"]}"
        android:pathData="{" ".join(parts)}" />{ring}
</vector>
"""


def write_resources(spec: dict, folder: Path) -> None:
    kind, color = spec["bg"]
    (folder / "res" / "drawable").mkdir(parents=True, exist_ok=True)
    (folder / "res" / "values").mkdir(parents=True, exist_ok=True)
    (folder / "res" / "drawable" / "ic_launcher_foreground.xml").write_text(
        vector_xml(spec), encoding="utf-8"
    )
    background = EMERALD_TOP if kind == "splash" else color
    (folder / "res" / "values" / "ic_launcher_background.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<resources>\n"
        f'    <color name="ic_launcher_background">{background}</color>\n'
        "</resources>\n",
        encoding="utf-8",
    )


# --- установка в приложение --------------------------------------------------
#
# Шесть иконок живут в приложении одновременно: у каждой свой activity-alias
# с MAIN/LAUNCHER в манифесте, а экран настроек переключает их через
# PackageManager. Макет — copy-paste из этого скрипта, руками paths не правятся.

APP_RES = ROOT / "app" / "src" / "main" / "res"
APP_DRAWABLE = APP_RES / "drawable"
APP_MIPMAP = APP_RES / "mipmap-anydpi-v26"

GLOW_BACKGROUND = """<?xml version="1.0" encoding="utf-8"?>
<!-- Фон иконки «Свечение»: тот же градиент и те же пятна, что в ic_splash_bg.xml,
     но в системе 108 dp и с центром пятен за пределами кадра.
     Сгенерировано icons/build_icons_v6.py — правь скрипт, а не файл руками. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <path android:pathData="M0,0 H108 V108 H0 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="0"
                android:endY="108">
                <item android:offset="0" android:color="{top}" />
                <item android:offset="1" android:color="{bottom}" />
            </gradient>
        </aapt:attr>
    </path>
    <path android:pathData="{lime_circle}">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="{lime_x:.1f}"
                android:centerY="{lime_y:.1f}"
                android:gradientRadius="{lime_r:.1f}">
                <item android:offset="0" android:color="{lime}" />
                <item android:offset="1" android:color="#00BFE56F" />
            </gradient>
        </aapt:attr>
    </path>
    <path android:pathData="{peach_circle}">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="{peach_x:.1f}"
                android:centerY="{peach_y:.1f}"
                android:gradientRadius="{peach_r:.1f}">
                <item android:offset="0" android:color="{peach}" />
                <item android:offset="1" android:color="#00FFB77D" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
"""

ADAPTIVE_ICON = """<?xml version="1.0" encoding="utf-8"?>
<!-- Иконка SnapMeal «{title}». Один из шести activity-alias в манифесте:
     лончер показывает тот, что включён через PackageManager.
     Сгенерировано icons/build_icons_v6.py. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="{background}" />
    <foreground android:drawable="{foreground}" />
    <monochrome android:drawable="{foreground}" />
</adaptive-icon>
"""

ICON_COLORS = """<?xml version="1.0" encoding="utf-8"?>
<!-- Цвета фонов иконок SnapMeal. Файл сгенерирован icons/build_icons_v6.py:
     править руками бессмысленно — перезапишется. -->
<resources>
{colors}</resources>
"""


def circle_path(cx: float, cy: float, r: float) -> str:
    return (
        f"M{cx - r:.1f},{cy:.1f} "
        f"a{r:.1f},{r:.1f} 0 1,1 {2 * r:.1f},0 "
        f"a{r:.1f},{r:.1f} 0 1,1 {-2 * r:.1f},0 Z"
    )


def write_app_icons() -> None:
    APP_DRAWABLE.mkdir(parents=True, exist_ok=True)
    APP_MIPMAP.mkdir(parents=True, exist_ok=True)

    colors = []
    for spec in VARIANTS:
        suffix = "" if spec["res"] == "splash" else f'_{spec["res"]}'
        foreground = f"ic_launcher_foreground{suffix}"
        (APP_DRAWABLE / f"{foreground}.xml").write_text(vector_xml(spec), encoding="utf-8")

        if spec["bg"][0] == "splash":
            background_ref = "@drawable/ic_launcher_background_glow"
        else:
            background_ref = f'@color/ic_launcher_background{suffix}'
            colors.append(
                f'    <color name="ic_launcher_background{suffix}">{spec["bg"][1]}</color>\n'
            )

        mipmap = "ic_launcher" if spec["res"] == "splash" else f"ic_launcher_{spec['res']}"
        (APP_MIPMAP / f"{mipmap}.xml").write_text(
            ADAPTIVE_ICON.format(
                title=spec["title"],
                background=background_ref,
                foreground=f"@drawable/{foreground}",
            ),
            encoding="utf-8",
        )

    glow = GLOW_BACKGROUND.format(
        top=EMERALD_TOP,
        bottom=EMERALD_BOTTOM,
        lime=LIME_GLOW,
        peach=PEACH,
        lime_x=-0.04 * 108,
        lime_y=-0.03 * 108,
        lime_r=0.54 * 108,
        peach_x=1.09 * 108,
        peach_y=0.97 * 108,
        peach_r=0.58 * 108,
        lime_circle=circle_path(-0.04 * 108, -0.03 * 108, 0.54 * 108),
        peach_circle=circle_path(1.09 * 108, 0.97 * 108, 0.58 * 108),
    )
    (APP_DRAWABLE / "ic_launcher_background_glow.xml").write_text(glow, encoding="utf-8")
    (APP_RES / "values" / "ic_launcher_colors.xml").write_text(
        ICON_COLORS.format(colors="".join(colors)), encoding="utf-8"
    )
    print(f"приложение: {len(VARIANTS)} иконок в {APP_RES}")


def main() -> None:
    built = []
    for spec in VARIANTS:
        folder = HERE / spec["slug"]
        folder.mkdir(exist_ok=True)
        icon = draw_icon(spec)
        icon.save(folder / "icon-1024.png")
        launcher_crop(icon).save(folder / "icon-circle.png")
        launcher_crop(icon).resize((192, 192), Image.LANCZOS).save(folder / "icon-48dp.png")
        write_resources(spec, folder)
        built.append((spec, icon))
        print(f"{folder.name}: {spec['title']}")

    build_sheet(built, HERE / "variants-sheet-v6.png")
    print(f"лист: {HERE / 'variants-sheet-v6.png'}")
    write_app_icons()


if __name__ == "__main__":
    main()