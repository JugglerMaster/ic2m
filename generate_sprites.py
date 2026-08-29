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
    """Single solar pane with a white frame (tier 1)."""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Solid white frame.
    draw.rectangle([1, 1, 30, 30], fill=(235, 235, 240))
    # Single dark-blue cell inside the frame.
    gradient_rect(draw, (4, 4, 27, 27), shade((30, 50, 120), 1.3), shade((30, 50, 120), 0.7))
    draw.line([(15, 4), (15, 27)], fill=shade((30, 50, 120), 0.5), width=1)
    draw.line([(4, 15), (27, 15)], fill=shade((30, 50, 120), 0.5), width=1)
    # Highlight reflection.
    draw.line([(5, 5), (12, 5)], fill=(255, 255, 255, 70), width=1)
    draw.line([(5, 5), (5, 12)], fill=(255, 255, 255, 50), width=1)
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

def draw_rotor(draw, cx, cy, r, accent):
    """Draw a single grinding rotor: dark ring, hub, spokes and accent teeth."""
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(70, 70, 80))
    draw.ellipse([cx - r + 2, cy - r + 2, cx + r - 2, cy + r - 2], fill=(110, 110, 120))
    for angle in range(0, 360, 60):
        rad = math.radians(angle)
        x0 = cx + int((r - 2) * math.cos(rad))
        y0 = cy + int((r - 2) * math.sin(rad))
        x1 = cx + int((r + 1) * math.cos(rad))
        y1 = cy + int((r + 1) * math.sin(rad))
        draw.line([(x0, y0), (x1, y1)], fill=(150, 150, 162), width=1)
    for angle in range(0, 360, 30):
        rad = math.radians(angle)
        tx = cx + int(r * math.cos(rad))
        ty = cy + int(r * math.sin(rad))
        draw.point((tx, ty), fill=accent)
    draw.ellipse([cx - 2, cy - 2, cx + 2, cy + 2], fill=(190, 190, 205))

def make_macerator(tier=1):
    """Macerator. Higher tiers are a larger machine with more grinding rotors
    and richer tier accents (MV blue -> HV gold), instead of a tiled copy."""
    size = (1 << (tier - 1)) * SIZE
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    accent = [(150, 150, 160), (130, 200, 255), (240, 200, 70)][min(tier, 3) - 1]
    b = 3
    # Body
    gradient_rect(draw, (b, b, size - b, size - b), (140, 140, 150), (90, 90, 100))
    draw_border(draw, (b, b, size - b, size - b), (160, 160, 170))
    # Tier accent frame
    draw.rectangle([1, 1, size - 2, size - 1], outline=accent, width=2)
    # Input hopper (top) and output (bottom)
    hw = size // 3
    hx0 = (size - hw) // 2
    draw.rectangle([hx0, 1, hx0 + hw, b + 4], fill=(80, 80, 90))
    draw.rectangle([hx0 + hw // 4, 2, hx0 + 3 * hw // 4, b + 3], fill=(60, 60, 70))
    draw.rectangle([hx0, size - b - 4, hx0 + hw, size - 1], fill=(80, 80, 90))
    draw.rectangle([hx0 + hw // 4, size - b - 3, hx0 + 3 * hw // 4, size - 2], fill=(60, 60, 70))
    # Grinding rotors: more of them as the tier grows.
    cols, rows = {1: (2, 1), 2: (2, 2), 3: (3, 2)}[min(tier, 3)]
    x0, y0, x1, y1 = b + 6, b + 8, size - b - 6, size - b - 8
    cx_step = (x1 - x0) / cols
    cy_step = (y1 - y0) / rows
    for r in range(rows):
        for c in range(cols):
            cx = int(x0 + cx_step * (c + 0.5))
            cy = int(y0 + cy_step * (r + 0.5))
            rr = max(3, int(min(cx_step, cy_step) * 0.38))
            draw_rotor(draw, cx, cy, rr, accent)
    return img

def make_alloy_furnace(tier=1):
    """Alloy furnace redrawn as an industrial smelting oven: a metal cabinet with a
    glowing front window. Higher tiers glow hotter (orange -> blue -> white-blue)."""
    size = (1 << (tier - 1)) * SIZE
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    m = 3
    # Cabinet body.
    gradient_rect(draw, (m, m, size - m - 1, size - m - 1), (98, 102, 112), (54, 58, 68))
    draw_border(draw, (m, m, size - m - 1, size - m - 1), (150, 156, 168))
    # Heat palette per tier: orange -> blue -> white-blue (hotter).
    heat = [
        [(255, 150, 30), (255, 205, 70)],
        [(255, 130, 60), (150, 200, 255)],
        [(190, 225, 255), (240, 250, 255)],
    ][min(tier, 3) - 1]
    # Top vent louvers.
    vy0 = m + max(3, int(size * 0.10))
    for i in range(2 + tier):
        yy = vy0 + i * max(2, int(size * 0.04))
        draw.line([(m + 6, yy), (size - m - 6, yy)], fill=(40, 44, 52), width=1)
    # Oven door (rounded rectangle) filling the lower front.
    dw = int(size * 0.62)
    dh = int(size * 0.54)
    dx0 = int((size - dw) / 2)
    dy0 = int(size * 0.36)
    dr = max(2, int(size * 0.07))
    draw.rounded_rectangle([dx0, dy0, dx0 + dw, dy0 + dh], radius=dr,
                           fill=(64, 68, 78), outline=(28, 30, 38), width=max(1, int(size * 0.03)))
    # Glowing window inside the door.
    wx0 = int(dx0 + dw * 0.14); wy0 = int(dy0 + dh * 0.14)
    wx1 = int(dx0 + dw * 0.86); wy1 = int(dy0 + dh * 0.86)
    wr = max(2, int(size * 0.05))
    draw.rounded_rectangle([wx0, wy0, wx1, wy1], radius=wr, fill=heat[0])
    draw.rounded_rectangle([int(wx0 + dw * 0.13), int(wy0 + dh * 0.13),
                            int(wx1 - dw * 0.13), int(wy1 - dh * 0.13)],
                           radius=max(1, int(size * 0.04)), fill=heat[1])
    # Door handle.
    hw = max(3, int(dw * 0.10))
    hy = int(dy0 - max(2, int(size * 0.03)))
    draw.rectangle([int(dx0 + dw / 2 - hw / 2), hy,
                    int(dx0 + dw / 2 + hw / 2), int(dy0 + 2)], fill=(185, 189, 197))
    # Tier accents: side status lights as it scales up.
    if tier >= 2:
        for (lx, ly) in [(m + 4, int(size * 0.5)), (size - m - 8, int(size * 0.5))]:
            draw.ellipse([lx, ly, lx + 4, ly + 4], fill=heat[1])
    return img

def make_cable(body, core, accent, high_voltage=False):
    """Compact cable node with a colored voltage/core indicator."""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Compact housing and four cable terminals.
    gradient_rect(draw, (5, 5, 26, 26), shade(body, 1.2), shade(body, 0.65))
    draw_border(draw, (5, 5, 26, 26), body)
    for box in [(1, 13, 7, 18), (24, 13, 31, 18), (13, 1, 18, 7), (13, 24, 18, 31)]:
        draw.rectangle(box, fill=shade(body, 0.55))
        draw.rectangle([box[0] + 1, box[1] + 1, box[2] - 1, box[3] - 1], fill=accent)
    # Core ring and center glow distinguish voltage tiers.
    draw.ellipse([9, 9, 22, 22], fill=shade(body, 0.5), outline=shade(accent, 1.2), width=1)
    draw.ellipse([12, 12, 19, 19], fill=core, outline=shade(core, 1.4), width=1)
    if high_voltage:
        draw.line([(14, 14), (17, 17)], fill=(255, 255, 255, 190), width=1)
        draw.line([(17, 14), (14, 17)], fill=(255, 255, 255, 190), width=1)
    else:
        draw.point((15, 15), fill=(255, 255, 255, 220))
    return img

def make_transformer():
    """Industrial transformer. The FRONT (east/right edge at rotation 0) is the
    output side and shows a bold green arrow; the other three sides are inputs.
    Because the block is rotatable, the arrow always points to the output side."""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Housing.
    gradient_rect(draw, (3, 3, 28, 28), (170, 125, 55), (80, 55, 30))
    draw_border(draw, (3, 3, 28, 28), (200, 155, 70))
    # Transformer coils (center).
    for x in (13, 16, 19):
        draw.rectangle([x - 1, 9, x + 1, 23], outline=(235, 190, 90, 255), width=1)
    # Step up/down conversion mark in the middle.
    draw.line([(16, 11), (16, 21)], fill=(255, 225, 130, 255), width=1)
    draw.polygon([(13, 13), (19, 13), (16, 9)], fill=(255, 225, 130, 255))
    draw.polygon([(13, 19), (19, 19), (16, 23)], fill=(255, 225, 130, 255))
    # Input ports on the three non-output sides (west, north, south).
    for box in [(1, 13, 6, 18), (13, 1, 18, 6), (13, 25, 18, 30)]:
        draw.rectangle(box, fill=(70, 120, 150, 255))
        draw.rectangle([box[0] + 1, box[1] + 1, box[2] - 1, box[3] - 1], fill=(120, 190, 220, 255))
    # OUTPUT port + bold green arrow on the front (east/right) edge.
    draw.rectangle([25, 12, 31, 19], fill=(50, 130, 70, 255))
    draw.rectangle([26, 13, 30, 18], fill=(90, 230, 120, 255))
    draw.rectangle([23, 14, 27, 17], fill=(130, 255, 160, 255))
    draw.polygon([(27, 10), (27, 21), (31, 15)], fill=(150, 255, 170, 255))
    return img


def make_upgrade_node():
    """Upgrade node: white machine housing with a green up arrow."""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # White housing.
    gradient_rect(draw, (3, 3, 28, 28), (228, 231, 235), (188, 193, 201))
    draw_border(draw, (3, 3, 28, 28), (208, 212, 219))
    # Dark inset panel.
    draw.rectangle([7, 7, 24, 24], fill=(58, 62, 70))
    draw.rectangle([8, 8, 23, 23], fill=(38, 42, 50))
    # Green up arrow.
    arrow = [(16, 10), (22, 19), (18, 19), (18, 23), (14, 23), (14, 19), (10, 19)]
    draw.polygon(arrow, fill=(90, 220, 120))
    draw.line([(10, 24), (22, 24)], fill=(90, 220, 120, 210), width=2)
    # Corner bolts.
    for (x, y) in [(5, 5), (26, 5), (5, 26), (26, 26)]:
        draw.ellipse([x - 1, y - 1, x + 1, y + 1], fill=(160, 165, 175))
    return img


def make_power_node(tier=1):
    """Power node: junction block bridging the cable network to machines.
    Accent colour follows the voltage tier (LV/MV/HV)."""
    accent = [(235, 200, 80), (130, 200, 255), (240, 200, 70)][min(tier, 3) - 1]
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Housing.
    gradient_rect(draw, (3, 3, 28, 28), (70, 72, 82), (42, 44, 52))
    draw_border(draw, (3, 3, 28, 28), (96, 100, 112))
    # Four connection ports on each face.
    for box in [(1, 13, 7, 18), (24, 13, 31, 18), (13, 1, 18, 7), (13, 24, 18, 31)]:
        draw.rectangle(box, fill=(54, 56, 64))
        draw.rectangle([box[0] + 1, box[1] + 1, box[2] - 1, box[3] - 1], fill=accent)
    # Central hub.
    draw.ellipse([10, 10, 21, 21], fill=(42, 44, 52), outline=accent, width=1)
    draw.ellipse([13, 13, 18, 18], fill=accent)
    draw.point((15, 15), fill=(255, 255, 255, 230))
    return img


def make_batbox(bs=2):
    """BatBox (tier 2 RE storage): wooden crate with a red energy cell."""
    w = bs * SIZE
    img = Image.new('RGBA', (w, w), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Wooden body.
    gradient_rect(draw, (3, 3, w - 4, w - 4), (122, 86, 46), (80, 55, 30))
    draw_border(draw, (3, 3, w - 4, w - 4), (150, 110, 60))
    # Plank seams.
    for i in (1, 2):
        draw.line([(i * SIZE, 4), (i * SIZE, w - 5)], fill=(60, 40, 20, 120), width=1)
        draw.line([(4, i * SIZE), (w - 5, i * SIZE)], fill=(60, 40, 20, 120), width=1)
    # Metal corner brackets.
    for (bx, by) in [(4, 4), (w - 14, 4), (4, w - 14), (w - 14, w - 14)]:
        draw.rectangle([bx, by, bx + 9, by + 9], fill=(92, 97, 107))
        draw.rectangle([bx + 1, by + 1, bx + 8, by + 8], fill=(122, 127, 137))
    # Central red energy cell.
    ccx, ccy = w // 2, w // 2
    draw.rectangle([ccx - 12, ccy - 18, ccx + 12, ccy + 18], fill=(150, 40, 35))
    draw.rectangle([ccx - 10, ccy - 16, ccx + 10, ccy + 16], outline=(205, 75, 62), width=1)
    for y in (ccy - 8, ccy, ccy + 8):
        draw.line([(ccx - 9, y), (ccx + 9, y)], fill=(120, 30, 28), width=1)
    draw.line([(ccx - 4, ccy - 12), (ccx + 4, ccy - 12)], fill=(255, 255, 255, 200), width=1)
    draw.line([(ccx, ccy - 16), (ccx, ccy - 8)], fill=(255, 255, 255, 200), width=1)
    draw.line([(ccx - 4, ccy + 12), (ccx + 4, ccy + 12)], fill=(255, 255, 255, 200), width=1)
    draw.ellipse([ccx - 3, ccy + 20, ccx + 3, ccy + 26], fill=(80, 220, 80))
    return img


def make_mfsu(bs=4):
    """MFSu (tier 3 RE storage): advanced metallic unit with a glowing core and corner energy cells."""
    w = bs * SIZE
    img = Image.new('RGBA', (w, w), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    m = 4
    # Metallic body.
    gradient_rect(draw, (m, m, w - m - 1, w - m - 1), (92, 102, 117), (50, 58, 70))
    draw_border(draw, (m, m, w - m - 1, w - m - 1), (132, 142, 157))
    # Tile seams (4x4).
    for i in (1, 2, 3):
        draw.line([(i * SIZE, m + 1), (i * SIZE, w - m - 1)], fill=(30, 35, 45, 120), width=1)
        draw.line([(m + 1, i * SIZE), (w - m - 1, i * SIZE)], fill=(30, 35, 45, 120), width=1)
    # Top antenna.
    ccx, ccy = w // 2, w // 2
    draw.rectangle([ccx - 2, m + 4, ccx + 2, m + SIZE // 2], fill=(150, 150, 160))
    draw.ellipse([ccx - 4, m, ccx + 4, m + 8], fill=(220, 80, 60))
    # Glowing core.
    r = w // 3
    draw.ellipse([ccx - r, ccy - r, ccx + r, ccy + r], fill=(40, 70, 90))
    draw.ellipse([ccx - r, ccy - r, ccx + r, ccy + r], outline=(120, 200, 230), width=2)
    draw.ellipse([ccx - r + 8, ccy - r + 8, ccx + r - 8, ccy + r - 8], fill=(60, 150, 200))
    draw.ellipse([ccx - r + 14, ccy - r + 14, ccx + r - 14, ccy + r - 14], fill=(120, 220, 255))
    draw.line([(ccx - r, ccy), (ccx + r, ccy)], fill=(200, 240, 255, 180), width=2)
    draw.line([(ccx, ccy - r), (ccx, ccy + r)], fill=(200, 240, 255, 180), width=2)
    # Corner energy cells in every corner so the whole unit reads complete (no clipped bottom-right).
    for (cx, cy) in [(m + 15, m + 15), (w - m - 15, m + 15),
                     (m + 15, w - m - 15), (w - m - 15, w - m - 15)]:
        draw.rectangle([cx - 10, cy - 10, cx + 10, cy + 10], fill=(20, 30, 40))
        draw.rectangle([cx - 9, cy - 9, cx + 9, cy + 9], outline=(120, 200, 230), width=1)
        draw.rectangle([cx - 6, cy - 6, cx + 6, cy + 6], fill=(60, 200, 120))
        draw.point((cx, cy), fill=(200, 255, 220))
    return img


def make_tiered(base_fn, tier, frame_color):
    """Compose a grid of base 32x32 machine sprites into one larger,
    properly-sized sprite for a tier block. Block footprint for tier N is
    2^(N-1) tiles (1, 2, 4), so the sprite is that many base tiles squared,
    with a tier-colored outer frame."""
    bs = 1 << (tier - 1)
    base = base_fn().convert('RGBA')
    size = bs * SIZE
    img = Image.new('RGBA', (size, size), (46, 48, 56, 255))
    for r in range(bs):
        for c in range(bs):
            img.paste(base, (c * SIZE, r * SIZE), base)
    draw = ImageDraw.Draw(img)
    # Faint dividers so sub-units read as cells of one machine.
    for i in range(1, bs):
        draw.line([(i * SIZE, 2), (i * SIZE, size - 3)], fill=(0, 0, 0, 70), width=1)
        draw.line([(2, i * SIZE), (size - 3, i * SIZE)], fill=(0, 0, 0, 70), width=1)
    # Thick tier-colored outer frame to unify the whole block.
    draw.rectangle([1, 1, size - 2, size - 1], outline=frame_color, width=2)
    draw.rectangle([0, 0, size - 1, size - 1], outline=shade(frame_color, 0.6), width=1)
    return img


# === POWER ARMOR SUIT (mech) SPRITES ===

def make_power_armor_body():
    """Mech body / torso of the power armor suit. IC2-style: mostly white plating
    with a few small brown/black accent pixels (visor, joints, belt)."""
    img = Image.new('RGBA', (40, 40), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    white = (238, 240, 244, 255)
    outline = (200, 205, 212, 255)
    dark = (28, 28, 34, 255)     # black accent pixels
    brown = (110, 74, 42, 255)   # brown accent pixels
    # torso
    draw.rectangle([13, 11, 27, 30], fill=white, outline=outline)
    # cockpit / head
    draw.rectangle([16, 4, 24, 12], fill=white, outline=outline)
    # visor (small black pixels)
    draw.rectangle([17, 6, 23, 9], fill=dark)
    # shoulders
    draw.rectangle([9, 12, 15, 21], fill=white, outline=outline)
    draw.rectangle([25, 12, 31, 21], fill=white, outline=outline)
    # arms
    draw.rectangle([7, 14, 12, 29], fill=white, outline=outline)
    draw.rectangle([28, 14, 33, 29], fill=white, outline=outline)
    # small brown belt pixels
    draw.rectangle([14, 20, 26, 21], fill=brown)
    draw.rectangle([10, 16, 11, 17], fill=brown)   # shoulder rivets
    draw.rectangle([29, 16, 30, 17], fill=brown)
    # small black joint pixels
    draw.rectangle([13, 24, 14, 25], fill=dark)
    draw.rectangle([26, 24, 27, 25], fill=dark)
    return img


def make_power_armor_leg():
    """Mech legs of the power armor suit. IC2-style: white with dark joints."""
    img = Image.new('RGBA', (40, 40), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    white = (238, 240, 244, 255)
    outline = (200, 205, 212, 255)
    dark = (28, 28, 34, 255)
    brown = (110, 74, 42, 255)
    # two legs
    draw.rectangle([13, 18, 19, 36], fill=white, outline=outline)
    draw.rectangle([21, 18, 27, 36], fill=white, outline=outline)
    # feet
    draw.rectangle([11, 34, 21, 38], fill=white, outline=outline)
    draw.rectangle([19, 34, 29, 38], fill=white, outline=outline)
    # knee joints (small dark pixels)
    draw.rectangle([14, 25, 18, 27], fill=dark)
    draw.rectangle([22, 25, 26, 27], fill=dark)
    # small brown hip accents
    draw.rectangle([13, 19, 19, 20], fill=brown)
    draw.rectangle([21, 19, 27, 20], fill=brown)
    return img

def make_power_armor_base():
    """Stationary base shadow of the power armor suit."""
    img = Image.new('RGBA', (40, 40), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.ellipse([8, 30, 32, 40], fill=(50, 70, 90, 130))
    return img


def make_armor_bench():
    """Power armor bench: a 2x2 workbench with a glowing chestplate being assembled on top."""
    size = 2 * SIZE  # 2x2 block -> 64x64
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    m = 3
    # Bench cabinet body (lower 55%).
    top = int(size * 0.46)
    gradient_rect(draw, (m, top, size - m - 1, size - m - 1), (120, 124, 134), (70, 74, 84))
    draw_border(draw, (m, top, size - m - 1, size - m - 1), (162, 167, 177))
    # Cabinet door seam + handle.
    draw.line([(size // 2, top + 4), (size // 2, size - m - 3)], fill=(50, 54, 64), width=1)
    draw.rectangle([size // 2 - 1, int(size * 0.62), size // 2 + 1, int(size * 0.72)], fill=(190, 194, 202))
    # Tabletop slab.
    draw.rectangle([m - 1, top - 5, size - m, top + 1], fill=(150, 154, 164))
    draw.rectangle([m - 1, top - 5, size - m, top - 3], fill=(186, 190, 199))
    # Legs.
    for lx in (m + 4, size - m - 7):
        draw.rectangle([lx, top + 2, lx + 3, size - m - 1], fill=(95, 99, 109))
    # Glowing armor chestplate resting on the bench.
    cx, cy = size // 2, int(size * 0.30)
    draw.ellipse([cx - 15, cy - 12, cx + 15, cy + 13], fill=(34, 60, 80))
    draw.ellipse([cx - 12, cy - 9, cx + 12, cy + 10], fill=(70, 190, 220))
    draw.ellipse([cx - 12, cy - 9, cx + 12, cy + 10], outline=(180, 240, 255), width=1)
    draw.ellipse([cx - 6, cy - 4, cx + 6, cy + 5], fill=(200, 245, 255))
    # Left tool: wrench.
    draw.rectangle([12, int(size * 0.52), 14, int(size * 0.72)], fill=(150, 154, 164))
    draw.ellipse([8, int(size * 0.50), 20, int(size * 0.62)], fill=(170, 174, 184))
    draw.ellipse([11, int(size * 0.53), 17, int(size * 0.59)], fill=(60, 64, 74))
    # Right tool: gear.
    draw.ellipse([size - 22, int(size * 0.56), size - 8, int(size * 0.70)], fill=(150, 154, 164))
    draw.ellipse([size - 19, int(size * 0.59), size - 11, int(size * 0.67)], fill=(70, 74, 84))
    # Power indicator (teal) on the cabinet front.
    draw.ellipse([size // 2 - 3, size - 11, size // 2 + 3, size - 5], fill=(90, 220, 230))
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
        "sprites/blocks/ic2-macerator.png": make_macerator(1),
        "sprites/blocks/ic2-alloy-furnace.png": make_alloy_furnace(1),
        "sprites/blocks/ic2-solar-panel-2.png": make_tiered(make_solar_panel, 2, (130, 200, 255)),
        "sprites/blocks/ic2-solar-panel-3.png": make_tiered(make_solar_panel, 3, (240, 200, 70)),
        "sprites/blocks/ic2-macerator-2.png": make_macerator(2),
        "sprites/blocks/ic2-macerator-3.png": make_macerator(3),
        "sprites/blocks/ic2-re-battery-2.png": make_batbox(2),
        "sprites/blocks/ic2-re-battery-3.png": make_mfsu(4),
        "sprites/blocks/ic2-alloy-furnace-2.png": make_alloy_furnace(2),
        "sprites/blocks/ic2-alloy-furnace-3.png": make_alloy_furnace(3),
        "sprites/blocks/ic2-lv-cable.png": make_cable((85, 95, 105), (70, 180, 130), (110, 125, 135)),
        "sprites/blocks/ic2-mv-cable.png": make_cable((70, 95, 75), (123, 255, 90), (110, 160, 120)),
        "sprites/blocks/ic2-hv-cable.png": make_cable((80, 75, 85), (220, 85, 55), (135, 110, 125), True),
        "sprites/blocks/ic2-transformer.png": make_transformer(),
        "sprites/blocks/ic2-upgrade-node.png": make_upgrade_node(),
        "sprites/blocks/ic2-power-node.png": make_power_node(1),
        "sprites/blocks/ic2-insulated-lv-cable.png": make_cable((55, 105, 105), (70, 220, 185), (100, 180, 170)),
        "sprites/blocks/ic2-reinforced-hv-cable.png": make_cable((75, 65, 110), (190, 95, 235), (135, 115, 180), True),
        "sprites/blocks/ic2-low-loss-hv-cable.png": make_cable((55, 95, 125), (80, 205, 245), (105, 165, 205), True),
        "sprites/blocks/ic2-superconductor-cable.png": make_cable((125, 135, 150), (220, 245, 255), (180, 205, 225), True),
        "sprites/ic2m-power-armor.png": make_power_armor_body(),
        "sprites/ic2m-power-armor-leg.png": make_power_armor_leg(),
        "sprites/ic2m-power-armor-base.png": make_power_armor_base(),
        "sprites/blocks/ic2m-power-armor-bench.png": make_armor_bench(),
    }

    for path, img in sprites.items():
        img.save(path)
        print(f"Created {path}")

    print("All sprites generated!")
