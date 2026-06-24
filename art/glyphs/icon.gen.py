#!/usr/bin/env python3
"""Compose Tribulation's skull mod icon as a native 128px .glyph grid.

Follows the Concord medallion pattern (cf. Meridian astrolabe, Mercantile
scale): a crimson rim-glow, a carved stone bezel, a dark blood-brick field and
an inner rim shadow. The centre motif is the shipped HUD skull itself
(art/glyphs/hud-skull-32.glyph) — the canonical Tribulation skull — stamped up
into the field and ink-outlined, so the icon and the HUD badge are the same
skull. glyph.py rasterizes the emitted .glyph deterministically.
"""
import math
import os

N = 128
CX = CY = (N - 1) / 2.0
HERE = os.path.dirname(os.path.abspath(__file__))

COL = {
    'ink':     '#0a0a0a',
    'glow1':   '#dc143ccc',
    'glow2':   '#b01030a0',
    'glow3':   '#8b000050',
    'st_sh':   '#191317',
    'st_dark': '#2c2429',
    'st_mid':  '#473b3f',
    'st_lit':  '#6a585c',
    'st_spec': '#8a7579',
    'br_deep': '#150809',
    'br':      '#24100f',
    'br_lit':  '#301614',
    'mortar':  '#0c0506',
    'vig':     '#0a0405',
}

G = [[None] * N for _ in range(N)]


def dist(x, y):
    return math.hypot(x - CX, y - CY)


def ang(x, y):
    return math.atan2(y - CY, x - CX)


R_IN = 47.0
R_OUT = 57.0

# ---- 1. crimson glow halo --------------------------------------------------
for y in range(N):
    for x in range(N):
        d = dist(x, y)
        if R_OUT < d <= R_OUT + 2:
            G[y][x] = 'glow1'
        elif R_OUT + 2 < d <= R_OUT + 4:
            G[y][x] = 'glow2'
        elif R_OUT + 4 < d <= R_OUT + 6.5:
            G[y][x] = 'glow3'

# ---- 2. stone bezel annulus ------------------------------------------------
for y in range(N):
    for x in range(N):
        d = dist(x, y)
        if R_IN <= d <= R_OUT:
            a = ang(x, y)
            base = math.cos(a - math.radians(225)) + \
                0.3 * (0.6 * math.sin(a * 8) + 0.4 * math.sin(a * 15 + 1.1))
            if d >= R_OUT - 1.2 or d <= R_IN + 1.0:
                G[y][x] = 'ink'
            elif base > 0.85:
                G[y][x] = 'st_spec'
            elif base > 0.25:
                G[y][x] = 'st_lit'
            elif base > -0.35:
                G[y][x] = 'st_mid'
            elif base > -0.8:
                G[y][x] = 'st_dark'
            else:
                G[y][x] = 'st_sh'

# ---- 3. blood brickwork field ----------------------------------------------
BRH, BRW = 8, 16
for y in range(N):
    for x in range(N):
        d = dist(x, y)
        if d >= R_IN - 1.0:
            continue
        row = int((y - (CY - R_IN)) // BRH)
        off = (BRW // 2) if (row % 2) else 0
        my = ((y - (CY - R_IN)) % BRH) < 1
        mx = ((x - off) % BRW) < 1
        if my or mx:
            G[y][x] = 'mortar'
        else:
            tone = (row * 3 + int((x - off) // BRW)) % 5
            G[y][x] = 'br_lit' if tone == 0 else ('br_deep' if tone == 3 else 'br')
        if d > R_IN - 5:
            G[y][x] = 'vig' if not (my or mx) else 'mortar'

for y in range(N):
    for x in range(N):
        if R_IN - 1.5 <= dist(x, y) < R_IN:
            G[y][x] = 'ink'

# ---------------------------------------------------------------------------
# 4. centre motif — the shipped HUD skull, stamped up and ink-outlined
# ---------------------------------------------------------------------------
def load_glyph(path):
    """Parse a .glyph into (size, grid[y][x]=hexcolor or None)."""
    legend, rows, in_legend, in_frame, size = {}, [], False, False, None
    with open(path) as f:
        for raw in f:
            line = raw.rstrip('\n')
            s = line.strip()
            if s.startswith('size:'):
                size = int(s.split(':')[1])
            elif s == 'legend:':
                in_legend, in_frame = True, False
            elif s in ('frame:', 'grid:'):
                in_legend, in_frame = False, True
            elif in_legend and s and not s.startswith('#'):
                parts = s.split()
                legend[parts[0]] = parts[1]
            elif in_frame and s and not s.startswith('#'):
                rows.append(line.strip())
    grid = [[None if c == '.' else legend.get(c) for c in r] for r in rows]
    return size, grid


sz, skull = load_glyph(os.path.join(HERE, 'hud-skull-32.glyph'))

xs = [x for y in range(sz) for x in range(sz) if skull[y][x]]
ys = [y for y in range(sz) for x in range(sz) if skull[y][x]]
scx, scy = (min(xs) + max(xs)) / 2.0, (min(ys) + max(ys)) / 2.0

def remap_skull(h):
    """Quantize the HUD skull's many soft tones to a punchy, crisp ramp so the
    3x upscale reads as clean pixel art, not a blurry gradient."""
    h = h.lower()
    r, g, b = int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16)
    L = 0.299 * r + 0.587 * g + 0.114 * b
    if r - max(g, b) > 26:                       # red eye-fire
        if L > 118:
            return '#f0563a'
        if L > 68:
            return '#d8203a'
        if L > 38:
            return '#8c1020'
        return '#4a0d10'
    if L < 48:                                    # outline / deep sockets
        return '#0a0a0a'
    if L > 222:                                   # bone ramp (6 steps)
        return '#efe6d2'
    if L > 198:
        return '#dccfb4'
    if L > 166:
        return '#c2b498'
    if L > 130:
        return '#9c8f77'
    if L > 94:
        return '#6e6450'
    return '#473d2d'


SCALE = 3                       # 32px tile -> crisp 3x blocks ~ fills inner field
# integer origin so the blocks tile contiguously (source centre -> medallion centre)
OX0 = int(round(CX - (scx + 0.5) * SCALE))
OY0 = int(round(CY - (scy + 0.5) * SCALE))
S = {}
for sy in range(sz):
    for sx in range(sz):
        col = skull[sy][sx]
        if not col:
            continue
        col = remap_skull(col)
        for oy in range(SCALE):
            for ox in range(SCALE):
                xi, yi = OX0 + sx * SCALE + ox, OY0 + sy * SCALE + oy
                if 0 <= xi < N and 0 <= yi < N:
                    S[(xi, yi)] = col

# ink-outline the skull silhouette so it reads off the brick field
for (x, y) in list(S.keys()):
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, ny = x + dx, y + dy
        if (nx, ny) not in S and 0 <= nx < N and 0 <= ny < N:
            G[ny][nx] = 'ink'

# ---- flatten to a hex grid -------------------------------------------------
flat = {}
for y in range(N):
    for x in range(N):
        v = G[y][x]
        if v is not None:
            flat[(x, y)] = COL.get(v, v)         # token -> hex, or raw hex
for (x, y), col in S.items():                    # motif on top
    flat[(x, y)] = col


def lum(h):
    h = h.lstrip('#')
    return 0.299 * int(h[0:2], 16) + 0.587 * int(h[2:4], 16) + 0.114 * int(h[4:6], 16)


# ---- quality touch 1: ember glow welling out of the eye sockets ------------
EYE_REDS = {'#f0563a', '#d8203a', '#8c1020', '#4a0d10'}
eye_px = [(x, y) for (x, y), c in flat.items() if c.lower() in EYE_REDS]
if eye_px:
    left = [(x, y) for x, y in eye_px if x < CX]
    right = [(x, y) for x, y in eye_px if x >= CX]
    for grp in (left, right):
        if not grp:
            continue
        ecx = sum(x for x, _ in grp) / len(grp)
        ecy = sum(y for _, y in grp) / len(grp)
        for y in range(int(ecy - 8), int(ecy + 8)):
            for x in range(int(ecx - 8), int(ecx + 8)):
                if not (0 <= x < N and 0 <= y < N):
                    continue
                d = math.hypot(x - ecx, y - ecy)
                cur = flat.get((x, y))
                if cur is None or cur.lower() in EYE_REDS:
                    continue
                if lum(cur) > 70:                # don't wash over lit bone
                    continue
                if d <= 2.2:
                    flat[(x, y)] = '#ff6a42'
                elif d <= 3.6:
                    flat[(x, y)] = '#d2203399'
                elif d <= 4.9:
                    flat[(x, y)] = '#8c102055'

# ---- quality touch 1b: force both sockets to be mirror-identical -----------
# The hand-traced HUD skull has slightly mismatched left/right sockets; mirror
# the (cleaner) image-left eye onto the right so the glow, shading and palette
# are uniform across both — symmetrical lighting, no lopsided socket.
if eye_px:
    ys = [y for _, y in eye_px]
    y0, y1 = min(ys) - 4, max(ys) + 4
    axis = int(round(CX))                       # 64; the 63|64 seam is CX=63.5
    EYE_HALF = 20
    for y in range(y0, y1 + 1):
        for x in range(axis, axis + EYE_HALF):
            mx = 2 * axis - 1 - x               # mirror across the seam (127 - x)
            src = flat.get((mx, y))
            if src is not None:
                flat[(x, y)] = src
            elif (x, y) in flat:
                del flat[(x, y)]

# ---- quality touch 2: soft contact shadow seating the skull on the brick ---
FIELD = {COL['br'], COL['br_deep'], COL['br_lit'], COL['mortar'], COL['vig']}
for (x, y) in list(S.keys()):
    for dx, dy in ((1, 1), (0, 2), (1, 2), (2, 1), (-1, 2), (0, 1)):
        nx, ny = x + dx, y + dy
        if (nx, ny) in S or not (0 <= nx < N and 0 <= ny < N):
            continue
        if flat.get((nx, ny)) in FIELD:
            flat[(nx, ny)] = '#070304'

# ---- emit ------------------------------------------------------------------
pool = "@$%&*+=oOxX0123456789abcdefghijklmnpqrstuvwzABCDEFGHIJKLMNPQRSTUVWZ?!~^/<>[]{};:"

used = []
for y in range(N):
    for x in range(N):
        c = flat.get((x, y))
        if c and c not in used:
            used.append(c)
assert len(used) <= len(pool), f"too many colors: {len(used)}"
ch = {c: pool[i] for i, c in enumerate(used)}

lines = ["# Tribulation skull mod-icon — generated by icon.gen.py",
         f"size: {N}", "", "legend:", "  . transparent"]
for c in used:
    lines.append(f"  {ch[c]} {c}")
lines += ["", "frame:"]
for y in range(N):
    lines.append("  " + "".join(ch[flat[(x, y)]] if (x, y) in flat else "." for x in range(N)))
with open(os.path.join(HERE, "icon.glyph"), "w") as f:
    f.write("\n".join(lines) + "\n")
print(f"wrote art/glyphs/icon.glyph  ({len(used)} colors)")
