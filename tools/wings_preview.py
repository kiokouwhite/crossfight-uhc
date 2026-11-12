"""Dessine les ailes de dragon de Corrin avec les memes calculs que util/DragonWings.java.

Usage :  python3 tools/wings_preview.py sortie.png
Ligne du haut : l ancien calcul (repere en miroir). Ligne du bas : le calcul actuel.
Vues de dos et de trois quarts avant, pour deux orientations du joueur.
"""
import math, sys
import numpy as np
from PIL import Image, ImageDraw

FINGERS = [(62, 4.0), (40, 3.6), (20, 3.0), (2, 2.3), (-14, 1.6)]
IRON, PINK, WHITE = (190, 190, 200), (240, 170, 200), (235, 240, 250)

def parts():
    out = []
    for right in (True, False):
        for deg, ln in FINGERS:
            a = math.radians(deg)
            out.append((right, math.cos(a) * ln / 2, math.sin(a) * ln / 2, a, ln, 0.07, 0.07, IRON))
            out.append((right, math.cos(a) * ln, math.sin(a) * ln, a, 0.16, 0.12, 0.12, IRON))
        for i in range(len(FINGERS) - 1):
            a0, a1 = math.radians(FINGERS[i][0]), math.radians(FINGERS[i + 1][0])
            mid, gap = (a0 + a1) / 2, abs(a0 - a1)
            mx = min(FINGERS[i][1], FINGERS[i + 1][1]) - 0.15
            r = 0.4
            while r <= mx:
                out.append((right, math.cos(mid) * r, math.sin(mid) * r, mid, 0.55, 2 * r * math.sin(gap / 2) + 0.1, 0.03, PINK if r < 1.3 else WHITE))
                r += 0.5
        out.append((right, 0.15, 0.1, 0, 0.3, 0.25, 0.25, IRON))
    return out

def quat_from_matrix(m):  # JOML setFromNormalized, m = colonnes
    m00, m01, m02 = m[:, 0]; m10, m11, m12 = m[:, 1]; m20, m21, m22 = m[:, 2]
    tr = m00 + m11 + m22
    if tr >= 0:
        t = math.sqrt(tr + 1); w = t * 0.5; t = 0.5 / t
        x, y, z = (m12 - m21) * t, (m20 - m02) * t, (m01 - m10) * t
    elif m00 >= m11 and m00 >= m22:
        t = math.sqrt(m00 - (m11 + m22) + 1); x = t * 0.5; t = 0.5 / t
        y, z, w = (m10 + m01) * t, (m02 + m20) * t, (m12 - m21) * t
    elif m11 > m22:
        t = math.sqrt(m11 - (m22 + m00) + 1); y = t * 0.5; t = 0.5 / t
        z, x, w = (m21 + m12) * t, (m10 + m01) * t, (m20 - m02) * t
    else:
        t = math.sqrt(m22 - (m00 + m11) + 1); z = t * 0.5; t = 0.5 / t
        x, y, w = (m02 + m20) * t, (m21 + m12) * t, (m01 - m10) * t
    return np.array([x, y, z, w])

def qrot(q, v):
    x, y, z, w = q; u = np.array([x, y, z])
    return v + 2 * np.cross(u, np.cross(u, v) + w * v)

def boxes(yaw_deg, flap, fixed):
    yaw = math.radians(yaw_deg)
    fwd = np.array([-math.sin(yaw), 0, math.cos(yaw)]); side = np.array([math.cos(yaw), 0, math.sin(yaw)])
    tilt = math.radians(32); up = np.array([0, 1, 0]) * math.cos(tilt) - fwd * math.sin(tilt)
    normal = np.cross(side, up); normal /= np.linalg.norm(normal)
    origin = np.array([0, 1.25, 0]) - fwd * 0.3
    out = []
    for right, u, v, ang, ln, width, thick, col in parts():
        cu, cv = u * math.cos(flap) - v * math.sin(flap), u * math.sin(flap) + v * math.cos(flap)
        pos = origin + side * (cu if right else -cu) + up * cv
        a = ang + flap
        d = side * (math.cos(a) if right else -math.cos(a)) + up * math.sin(a); d /= np.linalg.norm(d)
        b = np.cross(d, normal) if fixed else np.cross(normal, d); b /= np.linalg.norm(b)
        q = quat_from_matrix(np.column_stack([d, normal, b]))
        scale = np.array([ln, thick, width]); tr = -qrot(q, scale * 0.5)
        corners = [pos + tr + qrot(q, scale * np.array(c)) for c in ((0,0,0),(1,0,0),(1,1,0),(0,1,0),(0,0,1),(1,0,1),(1,1,1),(0,1,1))]
        out.append((corners, col))
    # le joueur : une silhouette simple
    for lo, hi, col in (((-0.25, 0, -0.12), (0.25, 1.4, 0.12), (70, 90, 150)), ((-0.25, 1.4, -0.25), (0.25, 1.9, 0.25), (225, 190, 160))):
        cs = []
        for c in ((0,0,0),(1,0,0),(1,1,0),(0,1,0),(0,0,1),(1,0,1),(1,1,1),(0,1,1)):
            loc = np.array([lo[i] + (hi[i] - lo[i]) * c[i] for i in range(3)])
            cs.append(side * loc[0] + np.array([0, 1, 0]) * loc[1] + fwd * loc[2])
        out.append((cs, col))
    return out, fwd

def render(bx, cam_dir, w=520, h=420, scale=50):
    cam_dir = cam_dir / np.linalg.norm(cam_dir); right = np.cross(np.array([0, 1, 0]), cam_dir); right /= np.linalg.norm(right); upv = np.cross(cam_dir, right)
    img = Image.new("RGB", (w, h), (150, 190, 240)); dr = ImageDraw.Draw(img, "RGBA")
    faces = []
    for corners, col in bx:
        for f in ((0,1,2,3),(4,5,6,7),(0,1,5,4),(2,3,7,6),(1,2,6,5),(0,3,7,4)):
            pts = [corners[i] for i in f]
            faces.append((np.mean([p @ cam_dir for p in pts]), [(w / 2 + (p @ right) * scale, h - 60 - (p @ upv) * scale) for p in pts], col))
    for _, poly, col in sorted(faces, key=lambda x: -x[0]):
        dr.polygon(poly, fill=col + ((150,) if col in (PINK, WHITE) else (255,)), outline=(60, 60, 70, 120))
    return img

out = sys.argv[1]
rows = []
for fixed in (False, True):
    views = []
    for yaw in (0, 130):
        bx, fwd = boxes(yaw, 0.2, fixed)
        views.append(render(bx, -fwd))                               # vu de dos
        views.append(render(bx, fwd * 0.75 + np.cross(np.array([0, 1, 0]), fwd) * 0.65 + np.array([0, -0.15, 0])))  # trois quarts avant
    row = Image.new("RGB", (4 * 520, 420)); [row.paste(v, (i * 520, 0)) for i, v in enumerate(views)]; rows.append(row)
sheet = Image.new("RGB", (4 * 520, 840)); sheet.paste(rows[0], (0, 0)); sheet.paste(rows[1], (0, 420)); sheet.save(out)
