#!/usr/bin/env python3
"""Regenerate the Phantom brand PNGs from geometric primitives.

The brand mark is a flat geometric glyph (amethyst hex-shield + cyan bolt
on jet black) — no SVG rasterizer needed, so this runs anywhere Pillow does:
    python3 make-icons.py
Outputs: icon16.png, icon48.png, icon128.png (Chrome toolbar / listing).
"""
from PIL import Image, ImageDraw

AMETHYST = (139, 92, 246, 255)
CYAN = (0, 229, 255, 255)
JET = (10, 10, 16, 255)

HEXAGON = [(53, 32), (42.5, 50.2), (21.5, 50.2), (11, 32), (21.5, 13.8), (42.5, 13.8)]
BOLT = [(37, 15), (25, 36), (33, 36), (29, 49), (43, 30), (35, 30)]


def render(px: int) -> Image.Image:
    k = px / 64.0
    img = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([2 * k, 2 * k, 62 * k, 62 * k], radius=int(14 * k), fill=JET)
    hex_pts = [(x * k, y * k) for x, y in HEXAGON]
    d.line(hex_pts + [hex_pts[0]], fill=AMETHYST, width=max(1, int(4.5 * k)), joint="curve")
    d.polygon([(x * k, y * k) for x, y in BOLT], fill=CYAN)
    return img


if __name__ == "__main__":
    master = render(512)
    for size in (128, 48, 16):
        out = master.resize((size, size), Image.LANCZOS)
        out.save(f"icon{size}.png")
        print(f"wrote icon{size}.png")
