"""Generate Android launcher icons from the GRT logo.

Usage: python tools/generate_app_icons.py <logo_path> <res_dir>

Creates:
  drawable-nodpi/ic_launcher_logo_foreground.png   adaptive-icon foreground (432x432)
  mipmap-<density>/ic_launcher.png, ic_launcher_round.png   legacy icons (API 24/25)

The badge/background color is sampled from the logo's top-left corner so the
logo blends seamlessly into the icon.
"""
import os
import sys

from PIL import Image, ImageDraw

SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FG = 432
FG_BADGE_RADIUS = 132  # 66% safe zone of the 432px adaptive canvas


def fit(img: Image.Image, box: int) -> Image.Image:
    """Scale image to fit inside a box x box square, keeping aspect ratio."""
    w, h = img.size
    scale = min(box / w, box / h)
    return img.resize((int(w * scale), int(h * scale)), Image.LANCZOS)


def main() -> None:
    logo_path, res_dir = sys.argv[1], sys.argv[2]
    logo = Image.open(logo_path).convert("RGBA")

    # Sample the logo's own background color (top-left corner).
    corner = logo.getpixel((2, 2))
    bg_rgb = (corner[0], corner[1], corner[2])

    # ---- Adaptive icon foreground ------------------------------------
    fg = Image.new("RGBA", (FG, FG), (0, 0, 0, 0))
    draw = ImageDraw.Draw(fg)
    cx = cy = FG // 2
    draw.ellipse(
        (cx - FG_BADGE_RADIUS, cy - FG_BADGE_RADIUS, cx + FG_BADGE_RADIUS, cy + FG_BADGE_RADIUS),
        fill=bg_rgb + (255,),
    )
    inner = int(2 * FG_BADGE_RADIUS * 0.80)
    scaled = fit(logo, inner)
    fg.paste(scaled, (cx - scaled.width // 2, cy - scaled.height // 2), scaled)

    out_dir = os.path.join(res_dir, "drawable-nodpi")
    os.makedirs(out_dir, exist_ok=True)
    fg.save(os.path.join(out_dir, "ic_launcher_logo_foreground.png"))

    # ---- Legacy square + round icons per density ----------------------
    for name, size in SIZES.items():
        density_dir = os.path.join(res_dir, "mipmap-" + name)
        os.makedirs(density_dir, exist_ok=True)
        pad = int(size * 0.12)
        scaled = fit(logo, size - 2 * pad)

        square = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        d = ImageDraw.Draw(square)
        d.rounded_rectangle((0, 0, size - 1, size - 1), radius=int(size * 0.18), fill=bg_rgb + (255,))
        square.paste(scaled, ((size - scaled.width) // 2, (size - scaled.height) // 2), scaled)
        square.save(os.path.join(density_dir, "ic_launcher.png"))

        round_icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        d = ImageDraw.Draw(round_icon)
        d.ellipse((0, 0, size - 1, size - 1), fill=bg_rgb + (255,))
        round_icon.paste(scaled, ((size - scaled.width) // 2, (size - scaled.height) // 2), scaled)
        round_icon.save(os.path.join(density_dir, "ic_launcher_round.png"))

    print("Icons written to", res_dir)


if __name__ == "__main__":
    main()
