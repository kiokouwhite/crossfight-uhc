#!/usr/bin/env python3
"""Banc d'essai de la reference : simule la pose d'apres une decision (sol, fill, skip, dig), mesure ce qui reste, dessine.

La pose (ce que fait le plugin, sur un terrain de map plat au niveau `sol`) :
  - colonne non posee : terrain de la map sous le niveau, rien au-dessus ;
  - case de `fill` : au niveau et au-dessus, du comblement ; en dessous, le terrain de la map ;
  - au niveau et au-dessus : la case du lieu, telle quelle (air compris) ;
  - sous le niveau, jusqu'a PROFONDEUR : la case du lieu si elle n'est pas de l'air ; de l'air si l'indice est >= dig (un
    creux) ; sinon le terrain de la map ;
  - plus bas : le terrain de la map.
Codes du resultat : 0 air, 1 terrain de la map, 2 plein du lieu, 3 comblement, 4 non plein du lieu (plante, eau...).

Usage : python3 -I banc2.py [<volume> ...]      (ecrit dans ../resultats/)
"""
import json, os, sys, time
import numpy as np
from scipy import ndimage
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "outils"))
sys.path.insert(0, HERE)
import socle_lib as sl
import socle

OUT = os.environ.get("SOCLE_RESULTATS", os.path.join(HERE, "resultats"))
PLACES = ["arcadia", "beacon", "central_town", "forest_of_hope", "garreg_mach", "jouki", "pearl", "shiganshina", "terrace"]
EXTRAS = ["x_arcadia_bord", "x_garreg_entier", "x_shiganshina_560", "x_terrace_560"]


def simulate(v, sol, fill, skip, dig, c=None, cap=None):
    H, D, W = v.ids.shape
    c = c or socle.Columns(v)
    solid = c.lut_solid[v.ids]
    notair = ~c.lut_air[v.ids]
    idx = np.arange(H)[:, None, None]
    out = np.zeros((H, D, W), dtype=np.uint8)
    out[(idx < sol) & np.ones((1, D, W), dtype=bool)] = 1
    put = ~skip[None]
    src = np.where(solid, 2, np.where(notair, 4, 0)).astype(np.uint8)
    above = (idx >= sol) & put
    out[above] = src[above]
    out[above & fill] = 3
    deep = (idx < sol) & (idx >= sol - socle.PROFONDEUR) & put & ~fill
    copied = deep & notair
    out[copied] = src[copied]
    carved = deep & ~notair & (idx >= dig[None])
    out[carved] = 0
    if cap is not None:
        cy = cap[0]
        zz, xx = np.nonzero((cy >= 0) & ~skip & (cy >= sol - socle.PROFONDEUR))
        out[cy[zz, xx], zz, xx] = 2
    return out


def residual(v, after, sol, c):
    """Ce qui reste de suspect : les espaces couverts, vides au sol, dont le sol est le terrain de la map, du comblement ou de
    la terre du lieu, et le couvercle un plein du lieu ; par zones (4 voisins, vides qui se recouvrent)."""
    H, D, W = v.ids.shape
    idx = np.arange(H)[:, None, None]
    zz, xx = np.indices((D, W))
    full = (after == 1) | (after == 2) | (after == 3)
    thing = c.lut_thing[v.ids] & (after == 4)
    soil = c.lut_soil[v.ids]
    # le plus bas vide couvert a partir du niveau - 1 (le sol peut etre le terrain)
    start = max(0, sol - 1)
    free = ~full & (idx >= start)
    c0 = np.where(free.any(axis=0), np.argmax(free, axis=0), H)
    cover = full & (idx > c0[None])
    q = np.where(cover.any(axis=0), np.argmax(cover, axis=0), H)
    covered = (q < H) & (c0 < H)
    fl = after[np.clip(c0 - 1, 0, H - 1), zz, xx]
    flsoil = soil[np.clip(c0 - 1, 0, H - 1), zz, xx]
    bad_floor = (fl == 1) | (fl == 3) | ((fl == 2) & flsoil)
    low = (thing & (idx >= c0[None]) & (idx < c0[None] + 3) & (idx < q[None])).any(axis=0)
    cand = covered & bad_floor & ~low & ((q - c0) >= 2)
    label, count = socle.regions(cand, c0, q)
    zones = []
    mask = np.zeros((D, W), dtype=bool)
    if count:
        objs = ndimage.find_objects(label + 1)
        for k in range(count):
            slc = objs[k]
            m = label[slc] == k
            area = int(m.sum())
            if area < 25:
                continue
            thick = int(socle.chessboard(m).max())
            if thick < 3:
                continue
            cz, cx = [int(round(t)) for t in ndimage.center_of_mass(m)]
            hh = (q - c0)[slc][m]
            f = fl[slc][m]
            mask[slc] |= m
            zones.append({"aire": area, "ep": thick, "centre": [cx + slc[1].start, cz + slc[0].start], "h_med": int(np.median(hh)), "h_max": int(hh.max()),
                          "au_dessus_du_niveau": int(np.median(c0[slc][m]) - sol),
                          "sol": {"terrain": round(float((f == 1).mean()), 2), "comblement": round(float((f == 3).mean()), 2), "terre du lieu": round(float((f == 2).mean()), 2)}})
    return sorted(zones, key=lambda z: -z["aire"]), mask


def evalue(name, inside=None, write=True, tag=""):
    t = time.time()
    v = sl.load(name)
    c = socle.Columns(v)
    sol, fill, skip, dig, cap, note, d = socle.decide(v, inside, details=True)
    H, D, W = v.ids.shape
    if inside is not None:
        skip = skip | ~inside                                      # hors du contour : rien n'est pose
    after = simulate(v, sol, fill, skip, dig, c, cap)
    zones, zmask = residual(v, after, sol, c)
    idx = np.arange(H)[:, None, None]
    notair = ~c.lut_air[v.ids]
    put = ~skip if inside is None else (~skip & inside)
    lost = (notair & (idx >= sol - socle.PROFONDEUR) & (idx < sol) & fill & put[None] & c.lut_thing[v.ids]).sum()
    buried = c.lut_thing[v.ids] & ~c.lut_solid[v.ids] & (idx < sol) & (idx >= sol - socle.PROFONDEUR) & (after == 1) & put[None]
    m = {"lieu": name, "note": note, "volume_xyz": [W, H, D], "sol_fiche": v.sol, "sol": sol, "decalage": sol - v.sol,
         "colonnes": int(D * W), "non_posees": int(skip.sum()), "comblees_au_dessus_du_niveau": int((after == 3).sum()),
         "colonnes_comblees": int(((after == 3).any(axis=0)).sum()), "colonnes_creusees": int((dig < sol).sum()),
         "cases_creusees": int(((after == 0) & (idx < sol)).sum()), "choses_du_lieu_enterrees": int(buried.sum()), "bouchons_sous_verre": int((cap[0] >= 0).sum()),
         "zones_suspectes": zones, "aire_suspecte": int(zmask.sum()), "zones_de_la_regle": d["zones"][:30], "secondes": round(time.time() - t, 1)}
    if write:
        os.makedirs(OUT, exist_ok=True)
        json.dump(m, open(os.path.join(OUT, name + tag + ".json"), "w"), indent=1, ensure_ascii=False)
        np.savez_compressed(os.path.join(OUT, name + tag + ".npz"), sol=np.int64(sol), fill=np.packbits(fill), shape=np.array(fill.shape), skip=skip, dig=dig, cap_y=cap[0], cap_id=cap[1])
        draw(v, after, sol, fill, skip, dig, zmask, os.path.join(OUT, name + tag))
    return m, (v, after, sol, fill, skip, dig, zmask)


def draw(v, after, sol, fill, skip, dig, zmask, stem):
    H, D, W = v.ids.shape
    col = v.colors()
    notair = v.ids != 0
    top = sl.highest(notair)
    zz, xx = np.indices(top.shape)
    img = col[v.ids[np.maximum(top, 0), zz, xx]].astype(np.float64) * 0.6
    img[top < 0] = 0
    fcols = (after == 3).any(axis=0)
    img[fcols] = img[fcols] * 0.35 + np.array([255, 40, 40]) * 0.65
    d = dig < sol
    img[d] = img[d] * 0.4 + np.array([40, 220, 220]) * 0.6
    img[skip] = img[skip] * 0.3 + np.array([40, 40, 255]) * 0.7
    img[zmask] = img[zmask] * 0.3 + np.array([255, 230, 0]) * 0.7
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((W * 2, D * 2), Image.NEAREST).save(stem + "_dessus.png")
    for kind, n_ in (("z", D), ("x", W)):
        for k, t in enumerate("abc"):
            at = int(n_ * (k + 1) / 4)
            section(v, after, sol, stem + f"_coupe_{kind}{t}.png", **{kind: at})


def section(v, after, sol, path, z=None, x=None, scale=3, y_lo=None, y_hi=None):
    col = v.colors()
    ids = v.ids[:, z, :] if z is not None else v.ids[:, :, x]
    a = after[:, z, :] if z is not None else after[:, :, x]
    img = col[ids].astype(np.uint8)
    img[a == 0] = (135, 170, 220)
    img[a == 1] = (70, 50, 35)
    img[a == 3] = (255, 40, 40)
    if 1 <= sol < a.shape[0]:
        img[sol - 1, ::6] = (255, 255, 0)
    lo = max(0, sol - 16) if y_lo is None else max(0, y_lo)
    hi = a.shape[0] if y_hi is None else min(a.shape[0], y_hi)
    im = Image.fromarray(img[lo:hi][::-1])
    im.resize((im.width * scale, im.height * scale), Image.NEAREST).save(path)
    return path


if __name__ == "__main__":
    for name in sys.argv[1:] or PLACES + EXTRAS:
        m, _ = evalue(name)
        print(f"{name:18s} niveau {m['sol']:4d} (fiche {m['sol_fiche']:3d}, {m['decalage']:+4d}) | comble {m['comblees_au_dessus_du_niveau']:8d} cases / {m['colonnes_comblees']:6d} col. | non posees {m['non_posees']:6d} | "
              f"creusees {m['colonnes_creusees']:6d} col. | choses enterrees {m['choses_du_lieu_enterrees']:6d} | suspect : {len(m['zones_suspectes'])} zones, {m['aire_suspecte']} col. | {m['secondes']:4.0f} s", flush=True)
        print(f"{'':18s} {m['note']}", flush=True)
