from pathlib import Path
from typing import List

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[1]
ICON_PATH = ROOT / "fastlane/metadata/android/en-US/images/icon.png"
OUTPUT_PATH = ROOT / "fastlane/metadata/android/en-US/images/featureGraphic.png"

WIDTH = 1024
HEIGHT = 500


def find_font(candidates: List[str], size: int):
    for candidate in candidates:
        path = Path(candidate)
        if path.exists():
            return ImageFont.truetype(str(path), size=size)
    return ImageFont.load_default()


def rounded_gradient_background() -> Image.Image:
    bg = Image.new("RGBA", (WIDTH, HEIGHT), (248, 244, 236, 255))
    px = bg.load()
    for y in range(HEIGHT):
        for x in range(WIDTH):
            tx = x / (WIDTH - 1)
            ty = y / (HEIGHT - 1)
            cream = (249, 243, 230)
            sky = (214, 242, 251)
            mint = (232, 247, 238)
            r = int(cream[0] * (1 - tx) + sky[0] * tx * 0.9 + mint[0] * ty * 0.1)
            g = int(cream[1] * (1 - tx) + sky[1] * tx * 0.95 + mint[1] * ty * 0.25)
            b = int(cream[2] * (1 - tx) + sky[2] * tx + mint[2] * ty * 0.35)
            px[x, y] = (r, g, b, 255)

    overlay = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    draw.ellipse((740, -40, 1070, 290), fill=(255, 255, 255, 75))
    draw.ellipse((640, 220, 1010, 560), fill=(201, 238, 247, 95))
    draw.ellipse((-80, 350, 260, 640), fill=(255, 247, 224, 85))
    draw.rounded_rectangle((24, 24, WIDTH - 24, HEIGHT - 24), radius=38, outline=(255, 255, 255, 150), width=3)
    return Image.alpha_composite(bg, overlay)


def add_soft_shapes(canvas: Image.Image) -> None:
    draw = ImageDraw.Draw(canvas)
    pebble_colors = [
        (229, 210, 186, 120),
        (212, 223, 226, 110),
        (243, 217, 198, 110),
        (208, 239, 233, 105),
    ]
    pebbles = [
        (640, 66, 694, 102),
        (720, 376, 782, 416),
        (836, 80, 902, 122),
        (132, 396, 194, 438),
        (560, 408, 616, 444),
    ]
    for box, color in zip(pebbles, pebble_colors * 2):
        draw.rounded_rectangle(box, radius=18, fill=color)


def paste_icon(canvas: Image.Image) -> None:
    icon = Image.open(ICON_PATH).convert("RGBA")
    icon = icon.resize((276, 276), Image.LANCZOS)

    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    shadow_layer = Image.new("RGBA", icon.size, (91, 73, 57, 70))
    shadow.alpha_composite(shadow_layer, (96, 122))
    shadow = shadow.filter(ImageFilter.GaussianBlur(22))
    canvas.alpha_composite(shadow)
    canvas.alpha_composite(icon, (84, 96))


def draw_text_block(canvas: Image.Image) -> None:
    draw = ImageDraw.Draw(canvas)
    title_font = find_font(
        [
            "/System/Library/Fonts/AppleSDGothicNeo.ttc",
            "/System/Library/Fonts/Supplemental/AppleGothic.ttf",
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
        ],
        58,
    )
    body_font = find_font(
        [
            "/System/Library/Fonts/AppleSDGothicNeo.ttc",
            "/System/Library/Fonts/Supplemental/AppleGothic.ttf",
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
        ],
        25,
    )
    pill_font = find_font(
        [
            "/System/Library/Fonts/Supplemental/Avenir Next Demi Bold.ttf",
            "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
        ],
        24,
    )

    title = "조약돌 소리나는\n키보드 어플"
    x = 422
    y = 102

    shadow_color = (255, 252, 247, 210)
    text_color = (97, 76, 59, 255)
    accent_color = (173, 136, 109, 255)

    for ox, oy in [(0, 3), (0, 2)]:
        draw.multiline_text((x, y + oy), title, font=title_font, fill=shadow_color, spacing=10)
    draw.multiline_text((x, y), title, font=title_font, fill=text_color, spacing=10)

    subtitle = "부드럽고 귀여운 타건감"
    draw.text((x + 4, 280), subtitle, font=body_font, fill=accent_color)

    pill_box = (x, 328, x + 148, 380)
    draw.rounded_rectangle(pill_box, radius=24, fill=(255, 255, 255, 230), outline=(206, 215, 223, 255), width=2)
    draw.rounded_rectangle((pill_box[0] + 4, pill_box[1] + 4, pill_box[2] - 4, pill_box[3] - 4), radius=20, outline=(255, 255, 255, 160), width=1)
    tw = draw.textbbox((0, 0), "ASMR", font=pill_font)
    text_w = tw[2] - tw[0]
    text_h = tw[3] - tw[1]
    draw.text(
        (pill_box[0] + ((pill_box[2] - pill_box[0] - text_w) / 2), pill_box[1] + ((pill_box[3] - pill_box[1] - text_h) / 2) - 2),
        "ASMR",
        font=pill_font,
        fill=(106, 124, 138, 255),
    )


def add_frame_border(canvas: Image.Image) -> Image.Image:
    framed = Image.new("RGBA", (WIDTH, HEIGHT), (245, 240, 233, 255))
    shadow = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(shadow)
    sdraw.rounded_rectangle((18, 18, WIDTH - 18, HEIGHT - 18), radius=42, fill=(95, 83, 70, 32))
    shadow = shadow.filter(ImageFilter.GaussianBlur(16))
    framed.alpha_composite(shadow)

    mask = Image.new("L", (WIDTH, HEIGHT), 0)
    mdraw = ImageDraw.Draw(mask)
    mdraw.rounded_rectangle((22, 22, WIDTH - 22, HEIGHT - 22), radius=38, fill=255)
    framed.paste(canvas, (0, 0), mask)
    return framed


def main() -> None:
    canvas = rounded_gradient_background()
    add_soft_shapes(canvas)
    paste_icon(canvas)
    draw_text_block(canvas)
    final_image = add_frame_border(canvas)
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    final_image.save(OUTPUT_PATH)
    print(f"Saved {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
