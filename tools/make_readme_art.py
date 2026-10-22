"""Draws the README's banner and stats card, and the screenshots, from renders of the app.

The look follows the Clean Your TV README: a dark slate background, a mint accent, Fredoka for headings and Outfit
for text. The fonts are not in this repository; download them from Google Fonts (both under the SIL Open Font
License) and pass the folder holding Fredoka.ttf and Outfit.ttf (the variable fonts).

Usage: python -I tools/make_readme_art.py <fonts-dir> <hero.png> <shot1.png> <shot2.png> <shot3.png> <shot4.png>
  Renders are 1920x1080 screen captures (tools/capture_at.py). A shot may be given as path@x,y,w,h to crop it.
Writes docs/banner.png, docs/card.png and docs/shot-1.jpg .. docs/shot-4.jpg.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

BG_TOP = (24, 27, 34)
BG_BOTTOM = (16, 18, 23)
WHITE = (247, 248, 250)
MINT = (207, 255, 207)
GRAY = (150, 156, 168)
RULE = (52, 57, 68)


def font(folder, name, size, weight):
    f = ImageFont.truetype(os.path.join(folder, name), size)
    f.set_variation_by_axes([weight] + ([100] if name.startswith("Fredoka") else []))
    return f


def background(w, h):
    img = Image.new("RGB", (w, h))
    px = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        px.line([(0, y), (w, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(BG_TOP, BG_BOTTOM)))
    return img


def load(spec):
    path, _, crop = spec.partition("@")
    img = Image.open(path).convert("RGB")
    if crop:
        x, y, w, h = (int(v) for v in crop.split(","))
        img = img.crop((x, y, x + w, y + h))
    return img


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0] - 1, img.size[1] - 1], radius, fill=255)
    out = Image.new("RGBA", img.size)
    out.paste(img, (0, 0), mask)
    return out


def banner(fonts, hero, out):
    w, h = 1600, 440
    img = background(w, h)
    # The picture: a square from the middle of a real frame, as an app tile with a soft mint glow behind it.
    side = 300
    src = hero.crop(((hero.width - hero.height) // 2, 0, (hero.width + hero.height) // 2, hero.height)).resize((side, side), Image.LANCZOS)
    tile = rounded(src, 48)
    x0, y0 = 190, (h - side) // 2
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(glow).rounded_rectangle([x0 - 6, y0 - 6, x0 + side + 6, y0 + side + 6], 54, fill=MINT + (70,))
    glow = glow.filter(ImageFilter.GaussianBlur(26))
    img.paste(glow, (0, 0), glow)
    img.paste(tile, (x0, y0), tile)
    ImageDraw.Draw(img).rounded_rectangle([x0, y0, x0 + side, y0 + side], 48, outline=(70, 76, 90), width=2)
    d = ImageDraw.Draw(img)
    tx = x0 + side + 80
    title = font(fonts, "Fredoka.ttf", 132, 600)
    d.text((tx, 92), "3D ", font=title, fill=MINT)
    d.text((tx + d.textlength("3D ", font=title), 92), "Pipes", font=title, fill=WHITE)
    d.text((tx + 4, 262), "The nostalgic XP screensaver, on your TV.", font=font(fonts, "Outfit.ttf", 46, 600), fill=WHITE)
    d.text((tx + 4, 330), "Painstakingly recreated to match the original.",
           font=font(fonts, "Outfit.ttf", 30, 400), fill=GRAY)
    img.save(out, optimize=True)


def card(fonts, hero, out):
    w = 1600
    stats = [("245", "SCENES MEASURED"), ("16", "MATERIALS"), ("30", "STEPS A SECOND"), ("181 KB", "APP")]
    pic_w = w - 2 * 90
    pic_h = round(pic_w * hero.height / hero.width)
    h = 300 + pic_h + 90
    img = background(w, h)
    d = ImageDraw.Draw(img)
    big = font(fonts, "Fredoka.ttf", 92, 600)
    small = font(fonts, "Outfit.ttf", 26, 500)
    col = w / len(stats)
    for i, (value, label) in enumerate(stats):
        cx = col * i + col / 2
        d.text((cx - d.textlength(value, font=big) / 2, 52), value, font=big, fill=MINT)
        spaced = " ".join(label)
        d.text((cx - d.textlength(spaced, font=small) / 2, 175), spaced, font=small, fill=GRAY)
        if i:
            d.line([(col * i, 70), (col * i, 200)], fill=RULE, width=2)
    d.line([(90, 260), (w - 90, 260)], fill=RULE, width=2)
    pic = rounded(hero.resize((pic_w, pic_h), Image.LANCZOS), 20)
    img.paste(pic, (90, 300), pic)
    d.rounded_rectangle([90, 300, 90 + pic_w, 300 + pic_h], 20, outline=RULE, width=2)
    img.save(out, optimize=True)


def main():
    fonts, hero_path, *shots = sys.argv[1:]
    hero = load(hero_path)
    docs = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "docs")
    banner(fonts, hero, os.path.join(docs, "banner.png"))
    card(fonts, hero, os.path.join(docs, "card.png"))
    for i, spec in enumerate(shots, 1):
        load(spec).resize((960, 540), Image.LANCZOS).save(os.path.join(docs, f"shot-{i}.jpg"), quality=90)


if __name__ == "__main__":
    main()
