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


# ---- colour blending helpers ----------------------------------------------
def _rgb(h):
    h = h.lstrip('#')
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def _hx(r, g, b):
    cl = lambda v: max(0, min(255, int(round(v))))
    return '#%02x%02x%02x' % (cl(r), cl(g), cl(b))


def blend(h1, h2, t):
    r1, g1, b1 = _rgb(h1)
    r2, g2, b2 = _rgb(h2)
    return _hx(r1 + (r2 - r1) * t, g1 + (g2 - g1) * t, b1 + (b2 - b1) * t)


def qcol(h, step=16):
    r, g, b = _rgb(h)
    q = lambda v: round(v / step) * step
    return _hx(q(r), q(g), q(b))


# ---- eyes: smooth spherical red gems with a socket-bone glow ----------------
# Mirror the eye band so both sockets share one shape, then render each eye as a
# shaded sphere (radial gradient, brightest at the upper-left light point,
# darkening smoothly toward the back — no harsh horizontal split), with a 1px
# pinpoint specular and red light spilling onto the surrounding bone.
EYE_RED = {'#f0563a', '#d8203a', '#8c1020', '#4a0d10'}
eye_px = [(x, y) for (x, y), c in flat.items() if c.lower() in EYE_RED]
if eye_px:
    ys = [y for _, y in eye_px]
    axis = int(round(CX))
    for y in range(min(ys) - 4, max(ys) + 5):
        for x in range(axis, axis + 20):
            mx = 2 * axis - 1 - x
            src = flat.get((mx, y))
            if src is not None:
                flat[(x, y)] = src
            elif (x, y) in flat:
                del flat[(x, y)]

left = [(x, y) for (x, y), c in flat.items() if c.lower() in EYE_RED and x < CX]
if left:
    lxs, lys = [p[0] for p in left], [p[1] for p in left]
    elx, ely = (min(lxs) + max(lxs)) / 2.0, (min(lys) + max(lys)) / 2.0
    R = max(max(lxs) - min(lxs), max(lys) - min(lys)) / 2.0 + 0.5
    Lx, Ly, Lz = -0.55, -0.50, 0.67
    _l = math.sqrt(Lx * Lx + Ly * Ly + Lz * Lz)
    Lx, Ly, Lz = Lx / _l, Ly / _l, Lz / _l

    def red_ramp(i):
        for thr, col in ((1.02, '#ff9468'), (0.86, '#f4593c'), (0.66, '#dc2238'),
                         (0.46, '#a8182a'), (0.28, '#70101f'), (0.14, '#460c16')):
            if i > thr:
                return col
        return '#2a0810'

    for cx, cy in ((elx, ely), (2 * CX - elx, ely)):
        # red light spilling onto the surrounding socket bone, in a few discrete
        # rings (kept off the deep socket black) so the palette stays small
        for y in range(int(cy - R - 6), int(cy + R + 7)):
            for x in range(int(cx - R - 6), int(cx + R + 7)):
                if not (0 <= x < N and 0 <= y < N):
                    continue
                d = math.hypot(x - cx, y - cy)
                cur = flat.get((x, y))
                if cur is None or d <= R + 0.5 or lum(cur) < 52:
                    continue
                if d <= R + 2.2:
                    t = 0.40
                elif d <= R + 4.0:
                    t = 0.24
                elif d <= R + 6.0:
                    t = 0.12
                else:
                    continue
                flat[(x, y)] = qcol(blend(cur, '#d11f30', t))
        # the gem: a smooth radial sphere; track the brightest texel for the glint
        spec, best = None, -9.0
        for y in range(int(cy - R - 1), int(cy + R + 2)):
            for x in range(int(cx - R - 1), int(cx + R + 2)):
                if not (0 <= x < N and 0 <= y < N):
                    continue
                dx, dy = (x - cx) / R, (y - cy) / R
                rr = dx * dx + dy * dy
                if rr <= 1.0:
                    ndl = max(0.0, dx * Lx + dy * Ly + math.sqrt(1 - rr) * Lz)
                    flat[(x, y)] = red_ramp(0.24 + ndl)
                    if ndl > best:
                        best, spec = ndl, (x, y)
                elif rr <= 1.30:
                    flat[(x, y)] = '#1c0610'        # dark rim seats it in the socket
        if spec:
            flat[spec] = '#ffffff'                  # pinpoint specular highlight

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
