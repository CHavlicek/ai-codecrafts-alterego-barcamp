#!/usr/bin/env python3
"""
Verbund rebrand — poster-frame.png generator ("AI @ Verbund 2026").

Regenerates the bundled poster-frame chrome that wraps every generated
alter-ego poster, in the VERBUND look & feel (VERBUND-blau on white,
energy-green + hydro-cyan accents). Replaces the previous SQUER /
<CODE/CRAFTS> 2026 chrome.

Design contract preserved from the old asset so the backend loader +
overlay + all tests keep passing without code changes:

  * Canvas 768 x 1152 (2:3 portrait), RGBA.
  * Transparent inner cutout (alpha=0) at the EXACT same geometry the
    old asset used: x=88, y=94, w=586, h=791. `PosterFrameAssetLoader`
    discovers this at startup; keeping it identical keeps the loader's
    plausible-band assertions and the fit geometry unchanged.
  * Opaque, non-black chrome at pixel (68, 38) — the old "SQUER-mark"
    coordinate the overlay ITs probe. The VERBUND wordmark now occupies
    the top-left, so that pixel stays opaque and coloured.
  * The bottom region below the cutout is left clear (flat navy) so the
    017 text-overlay step (name / role / quote) renders legibly.

Run from the repo root:
  /tmp/pil-venv/bin/python \
    backend/src/main/resources/branding/generate_poster_frame.py

Requires: pillow, cairosvg (see branding/README.md).
"""

import io
import os

import cairosvg
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
FRONTEND_BRAND = os.path.normpath(
    os.path.join(HERE, "../../../../../frontend/src/assets/brand")
)

# --- Canvas + cutout geometry (must match old asset exactly) -----------
W, H = 768, 1152
INNER_X, INNER_Y, INNER_W, INNER_H = 88, 94, 586, 791

# --- Verbund brand palette --------------------------------------------
VERBUND_BLUE = (0, 70, 142, 255)      # #00468E — VERBUND-blau
DEEP_NAVY = (0, 43, 84, 255)          # #002B54
HYDRO_CYAN = (0, 144, 199, 255)       # #0090C7
ENERGY_GREEN = (90, 170, 70, 255)     # #5AAA46
WHITE = (255, 255, 255, 255)
OFF_WHITE = (244, 247, 251, 255)      # #F4F7FB

UNBOUNDED = os.path.join(HERE, "fonts", "Unbounded-Bold.ttf")
GEIST = os.path.join(HERE, "fonts", "Geist-Regular.ttf")


def load_svg(path, width):
    """Rasterize an SVG at the given pixel width, preserving aspect."""
    png_bytes = cairosvg.svg2png(url=path, output_width=width)
    return Image.open(io.BytesIO(png_bytes)).convert("RGBA")


def recolor(img, rgb):
    """Recolor an RGBA glyph image to a solid rgb, keeping its alpha."""
    r, g, b = rgb
    solid = Image.new("RGBA", img.size, (r, g, b, 0))
    solid.putalpha(img.getchannel("A"))
    return solid


def paste_center(base, overlay, cx, cy):
    x = int(cx - overlay.width / 2)
    y = int(cy - overlay.height / 2)
    base.alpha_composite(overlay, (x, y))


def main():
    # Start fully opaque white — this is the printed card stock.
    img = Image.new("RGBA", (W, H), OFF_WHITE)
    draw = ImageDraw.Draw(img)

    # --- Top brand band ------------------------------------------------
    band_h = INNER_Y  # 94px — sits above the cutout
    draw.rectangle([0, 0, W, band_h], fill=VERBUND_BLUE)
    # Thin energy-green rule under the band.
    draw.rectangle([0, band_h - 4, W, band_h], fill=ENERGY_GREEN)

    # VERBUND wordmark (real logo), white, top-left. Covers pixel (68,38).
    verbund_svg = os.path.join(FRONTEND_BRAND, "verbund-logo.svg")
    verbund = load_svg(verbund_svg, width=196)
    verbund_white = recolor(verbund, WHITE[:3])
    img.alpha_composite(verbund_white, (32, int((band_h - verbund_white.height) / 2)))

    # "AI @ Verbund 2026" event wordmark, white, top-right.
    ev_font = ImageFont.truetype(UNBOUNDED, 22)
    ev_text = "AI @ VERBUND 2026"
    tb = draw.textbbox((0, 0), ev_text, font=ev_font)
    draw.text(
        (W - 32 - (tb[2] - tb[0]), int((band_h - (tb[3] - tb[1])) / 2) - tb[1]),
        ev_text,
        font=ev_font,
        fill=WHITE,
    )

    # --- Side-margin energy accents -----------------------------------
    # Thin vertical rules in the off-white side margins evoke Verbund's
    # "flowing energy / transmission" motif and keep the margins from
    # reading as dead space. Blue rule + a shorter green companion.
    # Pre-blended light tints (ImageDraw ignores source alpha on RGBA, so
    # blend against the off-white ground manually for a subtle look).
    blue_tint = (170, 190, 214, 255)   # VERBUND-blau @ ~22% on off-white
    green_tint = (198, 220, 190, 255)  # energy-green @ ~22% on off-white
    cutout_bottom = INNER_Y + INNER_H
    for margin_cx in (44, W - 44):
        draw.line([(margin_cx, band_h + 24), (margin_cx, cutout_bottom)], fill=blue_tint, width=2)
        draw.line(
            [(margin_cx + 6, band_h + 24), (margin_cx + 6, cutout_bottom)],
            fill=green_tint,
            width=2,
        )

    # --- Photo cutout border ------------------------------------------
    # Draw a navy frame ring around the cutout, then punch the hole.
    ring = 6
    draw.rectangle(
        [INNER_X - ring, INNER_Y - ring, INNER_X + INNER_W + ring, INNER_Y + INNER_H + ring],
        outline=VERBUND_BLUE,
        width=ring,
    )
    # Inner hairline accent (hydro cyan) just inside the navy ring.
    draw.rectangle(
        [INNER_X - 1, INNER_Y - 1, INNER_X + INNER_W, INNER_Y + INNER_H],
        outline=HYDRO_CYAN,
        width=2,
    )

    # --- Bottom chrome region -----------------------------------------
    # Flat navy panel below the cutout carries the text overlay + logos.
    bottom_y = INNER_Y + INNER_H  # 885
    draw.rectangle([0, bottom_y, W, H], fill=VERBUND_BLUE)
    # Energy-green rule at the top edge of the bottom panel.
    draw.rectangle([0, bottom_y, W, bottom_y + 4], fill=ENERGY_GREEN)

    # fifty1 wordmark, white, centred in the very bottom margin (below the
    # text-overlay safe area so it never collides with name/role/quote).
    fifty1 = Image.open(os.path.join(FRONTEND_BRAND, "fifty1-logo-black.png")).convert("RGBA")
    # 25% smaller than the original 150px so the footer logo sits further
    # clear of the 017 name/role/quote text overlay above it.
    target_w = 112
    scale = target_w / fifty1.width
    fifty1 = fifty1.resize((target_w, int(fifty1.height * scale)), Image.LANCZOS)
    fifty1_white = recolor(fifty1, WHITE[:3])
    # "Powered by fifty1" — small label + logo, bottom-centre.
    lbl_font = ImageFont.truetype(GEIST, 15)
    lbl = "Powered by"
    lb = draw.textbbox((0, 0), lbl, font=lbl_font)
    footer_cy = H - 34
    lbl_w = lb[2] - lb[0]
    gap = 12
    total_w = lbl_w + gap + fifty1_white.width
    start_x = (W - total_w) / 2
    draw.text(
        (start_x, footer_cy - (lb[3] - lb[1]) / 2 - lb[1]),
        lbl,
        font=lbl_font,
        fill=(255, 255, 255, 200),
    )
    paste_center(
        img,
        fifty1_white,
        start_x + lbl_w + gap + fifty1_white.width / 2,
        footer_cy,
    )

    # --- Punch the transparent inner cutout ---------------------------
    # Set the cutout region to alpha=0 so the loader discovers it and the
    # character image composites through.
    px = img.load()
    for y in range(INNER_Y, INNER_Y + INNER_H):
        for x in range(INNER_X, INNER_X + INNER_W):
            px[x, y] = (0, 0, 0, 0)

    out = os.path.join(HERE, "poster-frame.png")
    img.save(out, optimize=True)

    # --- Self-check ----------------------------------------------------
    check = Image.open(out).convert("RGBA")
    cpx = check.load()
    p = cpx[68, 38]
    assert p[3] == 255 and (p[0] or p[1] or p[2]), f"(68,38) must be opaque non-black, got {p}"
    assert cpx[INNER_X + 10, INNER_Y + 10][3] == 0, "inner cutout must be transparent"
    # Confirm cutout bbox unchanged.
    minx = miny = 10 ** 9
    maxx = maxy = -1
    w2, h2 = check.size
    for y in range(h2):
        for x in range(w2):
            if cpx[x, y][3] == 0:
                minx, miny = min(minx, x), min(miny, y)
                maxx, maxy = max(maxx, x), max(maxy, y)
    print(f"canvas={w2}x{h2}")
    print(f"cutout x={minx} y={miny} w={maxx - minx + 1} h={maxy - miny + 1}")
    print(f"pixel(68,38)={p}")
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
