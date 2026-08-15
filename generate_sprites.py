#!/usr/bin/env python3
"""Generate detailed pixel art sprites for IC2M mod."""

from PIL import Image, ImageDraw
import math

SIZE = 32

def shade(color, factor):
    """Shade a color by factor (0=black, 1=original, 2=white)."""
    return tuple(min(255, int(c * factor)) for c in color)

def gradient_rect(draw, bbox, color_top, color_bot):
    """Draw a vertical gradient rectangle."""
    x0, y0, x1, y1 = bbox
    for y in range(y0, y1):
        t = (y - y0) / max(1, y1 - y0 - 1)
        c = tuple(int(a + (b - a) * t) for a, b in zip(color_top, color_bot))
        draw.line([(x0, y), (x1, y)], fill=c)

def draw_border(draw, bbox, color, width=1):
    """Draw a beveled border."""
    x0, y0, x1, y1 = bbox
    # Top and left - lighter
    lighter = shade(color, 1.4)
    draw.line([(x0, y0), (x1, y0)], fill=lighter, width=width)
    draw.line([(x0, y0), (x0, y1)], fill=lighter, width=width)
    # Bottom and right - darker
    darker = shade(color, 0.6)
    draw.line([(x0, y1), (x1, y1)], fill=darker, width=width)
    draw.line([(x1, y0), (x1, y1)], fill=darker, width=width)

def draw_circle_shaded(img, cx, cy, r, color):
    """Draw a shaded circle with highlight."""
    for y in range(cy - r, cy + r + 1):
        for x in range(cx - r, cx + r + 1):
            dx, dy = x - cx, y - cy
            dist = math.sqrt(dx*dx + dy*dy)
            if dist <= r:
                # Highlight from top-left
                light = 1.2 - 0.4 * (dist / r)
                if dy < -r * 0.3:
                    light += 0.3
                elif dy > r * 0.3:
                    light -= 0.3
                c = shade(color, light)
                img.putpixel((x, y), c + (255,))

def draw_diamond(img, cx, cy, size, color):
    """Draw a diamond shape."""
    for y in range(cy - size, cy + size + 1):
        for x in range(cx - size, cx + size + 1):
            if abs(x - cx) + abs(y - cy) <= size:
                light = 1.0 + 0.3 * (1 - abs(x - cx) / size)
                if y < cy:
                    light += 0.2
                c = shade(color, light)
                img.putpixel((x, y), c + (255,))

def draw_bolt(img, cx, cy, color):
    """Draw a small lightning bolt."""
    points = [
        (cx - 1, cy - 4), (cx + 2, cy - 4),
        (cx - 1, cy - 1), (cx + 2, cy - 1),
        (cx + 1, cy + 1), (cx - 2, cy + 1),
        (cx + 1, cy + 4), (cx - 2, cy + 4),
    ]
    # Draw as filled region
    for dy in range(-4, 5):
        for dx in range(-3, 4):
            px, py = cx + dx, cy + dy
            if 0 <= px < SIZE and 0 <= py < SIZE:
                # Check if inside bolt shape
                if (dy <= -1 and -3 <= dx <= 1) or \
                   (dy == 0 and -2 <= dx <= 1) or \
                   (dy >= 1 and -1 <= dx <= 3):
                    light = 1.0 + 0.3 * (1 - abs(dx) / 3)
                    c = shade(color, light)
                    img.putpixel((px, py), c + (255,))


# === ITEM SPRITES ===

def make_efficiency_upgrade():
    """Green upgrade chip with lightning bolt."""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Background - dark green chip
    gradient_rect(draw, (4, 4, 27, 27), (30, 80, 30), (20, 50, 20))
    draw_border(draw, (4, 4, 27, 27), (50, 120, 50))
    # Inner circuit lines
    for y in [10, 16, 22]:
        draw.line([(7, y), (24, y)], fill=(80, 200, 80, 255), width=1)
    for x in [10, 16, 22]:
        draw.line([(x, 7), (x, 24)], fill=(80, 200, 80, 255), width=1)
    # Lightning bolt in center
    draw_bolt(img, 15, 15, (120, 255, 120))
    return img

def make_capacity_upgrade():
    """Blue upgrade chip with battery symbol."""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Background - dark blue chip
    gradient_rect(draw, (4, 4, 27, 27), (30, 40, 80), (20, 25, 60))
    draw_border(draw, (4, 4, 27, 27), (60, 80, 160))
    # Inner circuit lines
    for y in [10, 16, 22]:
        draw.line([(7, y), (24, y)], fill=(80, 120, 220, 255), width=1)
    for x in [10, 16, 22]:
        draw.line([(x, 7), (x, 24)], fill=(80, 120, 220, 255), width=1)
    # Battery symbol
    draw.rectangle([12, 11, 19, 20], outline=(100, 160, 255, 255), width=1)
    draw.rectangle([14, 8, 17, 11], fill=(100, 160, 255, 255))
    draw.rectangle([13, 13, 18, 19], fill=(100, 160, 255, 255))
    return img

def make_wrench():
    """Grey wrench tool."""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    # Wrench handle (diagonal)
    for i in range(20):
        x = 8 + i
        y = 24 - i
        if 0 <= x < SIZE and 0 <= y < SIZE:
            for d in range(-1, 2):
                px, py = x + d, y
                if 0 <= px < SIZE and 0 <= py < SIZE:
                    img.putpixel((px, py), (160, 160, 170, 255))
                px, py = x, y + d
                if 0 <= px < SIZE and 0 <= py < SIZE:
                    img.putpixel((px, py), (140, 140, 150, 255))
    # Wrench head (open end)
    draw = ImageDraw.Draw(img)
    draw.arc([6, 2, 16, 12], 0, 360, fill=(180, 180, 190, 255), width=2)
    # Wrench handle end
    draw.rectangle([22, 2, 26, 6], fill=(120, 120, 130, 255))
    draw_border(draw, (22, 2, 26, 6), (160, 160, 170))
    return img

def make_dust(color, name):
    """Generic dust pile sprite."""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Dust pile - elliptical mound
    draw.ellipse([6, 10, 25, 26], fill=shade(color, 0.8))
    draw.ellipse([8, 8, 23, 22], fill=color)
    draw.ellipse([10, 10, 21, 18], fill=shade(color, 1.2))
    # Sparkle highlights
    draw.point((12, 12), fill=shade(color, 1.6))
    draw.point((18, 11), fill=shade(color, 1.5))
    draw.point((15, 14), fill=shade(color, 1.4))
    # Scattered particles
    import random
    random.seed(hash(name))
    for _ in range(8):
        px = random.randint(5, 26)
        py = random.randint(18, 28)
        sz = random.randint(1, 2)
        draw.ellipse([px, py, px+sz, py+sz], fill=shade(color, 0.7 + random.random() * 0.6))
    return img


# === BLOCK SPRITES ===

def make_solar_panel():
    """Solar panel with grid lines and blue cells."""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Metal frame
    draw.rectangle([1, 1, 30, 30], fill=(80, 80, 90))
    draw_border(draw, (1, 1, 30, 30), (100, 100, 120))
    # Solar cells (3x3 grid)
    cell_colors = [(30, 40, 100), (25, 35, 90), (35, 45, 110),
                   (28, 38, 95), (32, 42, 105), (26, 36, 92),
                   (33, 43, 108), (29, 39, 98), (31, 41, 102)]
    idx = 0
    for row in range(3):
        for col in range(3):
            x0 = 4 + col * 9
            y0 = 4 + row * 9
            c = cell_colors[idx % len(cell_colors)]
            gradient_rect(draw, (x0, y0, x0 + 7, y0 + 7), shade(c, 1.3), shade(c, 0.7))
            draw.line([(x0, y0 + 7), (x0 + 7, y0 + 7)], fill=shade(c, 0.5), width=1)
            draw.line([(x0 + 7, y0), (x0 + 7, y0 + 7)], fill=shade(c, 0.5), width=1)
            idx += 1
    # Highlight reflection
    draw.line([(5, 5), (12, 5)], fill=(255, 255, 255, 60), width=1)
    draw.line([(5, 5), (5, 12)], fill=(255, 255, 255, 40), width=1)
    return img

def make_battery():
    """Battery with energy indicator."""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Battery body
    gradient_rect(draw, (4, 6, 27, 26), (180, 160, 40), (120, 100, 20))
    draw_border(draw, (4, 6, 27, 26), (200, 180, 50))
    # Battery terminals
    draw.rectangle([12, 3, 19, 6], fill=(180, 180, 190))
    draw.rectangle([13, 2, 18, 4], fill=(200, 200, 210))
    # Energy level indicator (filled)
    gradient_rect(draw, (7, 10, 24, 23), (50, 200, 50), (30, 140, 30))
    # + and - symbols
    draw.line([(8, 15), (12, 15)], fill=(255, 255, 255, 200), width=1)
    draw.line([(10, 13), (10, 17)], fill=(255, 255, 255, 200), width=1)
    draw.line([(19, 15), (23, 15)], fill=(255, 255, 255, 200), width=1)
    return img

def make_macerator():
    """Macerator with grinding wheels."""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Body
    gradient_rect(draw, (3, 3, 28, 28), (140, 140, 150), (90, 90, 100))
    draw_border(draw, (3, 3, 28, 28), (160, 160, 170))
    # Grinding wheels (two circles)
    cx1, cy1 = 11, 16
    cx2, cy2 = 21, 16
    for cx, cy in [(cx1, cy1), (cx2, cy2)]:
        draw.ellipse([cx-5, cy-5, cx+5, cy+5], fill=(100, 100, 110))
        draw.ellipse([cx-4, cy-4, cx+4, cy+4], fill=(120, 120, 130))
        # Teeth pattern
        for angle in range(0, 360, 45):
            rad = math.radians(angle)
            tx = cx + int(3 * math.cos(rad))
            ty = cy + int(3 * math.sin(rad))
            draw.point((tx, ty), fill=(180, 180, 190))
    # Input hopper
    draw.rectangle([10, 3, 21, 7], fill=(80, 80, 90))
    draw.rectangle([11, 4, 20, 6], fill=(60, 60, 70))
    # Output
    draw.rectangle([12, 26, 19, 28], fill=(80, 80, 90))
    # Gear decoration
    draw.ellipse([13, 12, 19, 18], outline=(160, 160, 170), width=1)
    return img

def make_alloy_furnace():
    """Alloy furnace with fire and crucible."""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Body - dark iron
    gradient_rect(draw, (3, 3, 28, 28), (80, 60, 50), (50, 35, 30))
    draw_border(draw, (3, 3, 28, 28), (100, 80, 70))
    # Fire opening
    draw.rectangle([8, 14, 23, 25], fill=(30, 20, 15))
    # Flames
    flames = [(12, 22), (16, 20), (20, 22), (14, 18), (18, 19)]
    for fx, fy in flames:
        draw.rectangle([fx, fy, fx+1, fy+2], fill=(255, 160, 30))
        draw.point((fx, fy-1), fill=(255, 200, 50))
    # Crucible on top
    draw.rectangle([10, 8, 21, 14], fill=(120, 80, 40))
    draw.rectangle([11, 9, 20, 13], fill=(160, 100, 50))
    # Metal pour
    draw.line([(15, 14), (15, 16)], fill=(200, 150, 50), width=1)
    draw.line([(16, 14), (16, 16)], fill=(220, 170, 60), width=1)
    # Chimney
    draw.rectangle([14, 3, 17, 8], fill=(70, 70, 80))
    draw.rectangle([15, 4, 16, 7], fill=(50, 50, 60))
    return img


if __name__ == "__main__":
    import os
    os.makedirs("sprites/items", exist_ok=True)
    os.makedirs("sprites/blocks", exist_ok=True)

    sprites = {
        "sprites/items/efficiency-upgrade.png": make_efficiency_upgrade(),
        "sprites/items/capacity-upgrade.png": make_capacity_upgrade(),
        "sprites/items/copper-dust.png": make_dust((212, 133, 65), "copper"),
        "sprites/items/lead-dust.png": make_dust((100, 100, 120), "lead"),
        "sprites/items/graphite-dust.png": make_dust((60, 60, 65), "graphite"),
        "sprites/items/coal-dust.png": make_dust((40, 40, 45), "coal"),
        "sprites/items/titanium-dust.png": make_dust((120, 170, 230), "titanium"),
        "sprites/items/thorium-dust.png": make_dust((80, 140, 80), "thorium"),
        "sprites/blocks/ic2-solar-panel.png": make_solar_panel(),
        "sprites/blocks/ic2-battery.png": make_battery(),
        "sprites/blocks/ic2-macerator.png": make_macerator(),
        "sprites/blocks/ic2-alloy-furnace.png": make_alloy_furnace(),
    }

    for path, img in sprites.items():
        img.save(path)
        print(f"Created {path}")

    print("All sprites generated!")
