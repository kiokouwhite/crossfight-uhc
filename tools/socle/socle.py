"""Le socle d'un lieu : la REFERENCE (game/Socle.java du plugin en est le portage, compare case par case).

    sol, fill, skip, dig, cap, note = decide(v, inside)

  v       un volume (socle_lib.Vol) : v.ids [y, z, x], proprietes des blocs
  inside  booleens [z, x] : les colonnes dans le contour du lieu (toutes si None)
  sol     l'indice y du volume qui vient au niveau du terrain de la map
  fill    booleens [y, z, x] : les cases qui sont du SOL et non du lieu (vide sous le lieu) : au-dessus du niveau on y met de
          la terre et de la pierre ; en dessous, le terrain de la map y reste (la case du lieu n'y est pas copiee)
  skip    booleens [z, x] : les colonnes qu'on ne pose pas
  dig     entiers [z, x] : sous le niveau, les cases d'indice >= dig sont copiees telles quelles, air compris (un creux a
          ciel ouvert : riviere, douve, cour basse) ; dig = sol : rien n'est creuse
  cap     (cap_y, cap_id) [z, x] : la case cap_y de la colonne (-1 : aucune) recoit le bloc cap_id (le sol du vide, remonte
          sous un couvercle de verre pour qu'on le voie toujours a travers)

Les etapes (toutes en entiers, sans rien d'aleatoire) :
 1. Les blocs : PLEIN = solide hors feuilles. TERRE = terre, herbe, sable, gres, gravier, argile, neige. CHOSE = ni air, ni
    liquide, ni plante sans importance (herbes, fleurs, feuilles, lianes...). LUMIERE = bloc qui eclaire.
    Les etapes 2 a 4 regardent toute la decoupe, contour ou non : ce qu'est le lieu ne depend pas de la facon dont on le taille.
 2. La base : le couple (hauteur du dessus du massif du bas, bloc de ce dessus) le plus frequent ; elle existe s'il couvre
    BASE_MIN des colonnes. Elle est un RESTE (le sol plat d'un monde vide, sur lequel rien n'est pose) si moins de RESTE_MAX
    des colonnes portent une chose sur elle ou un autre sol a sa hauteur. Un reste est retire : le lieu est ce qu'il y a
    au-dessus.
 3. Par colonne : le premier massif (du plus bas plein a la premiere case libre au-dessus : g0), puis le plus bas vide
    couvert [g0, q).
 4. Les vides sous le sol du lieu : les colonnes dont ce vide est couvert, vide de toute chose au sol et de toute lumiere.
    Voisines aux vides qui se recouvrent, elles forment des zones. Une zone est du VIDE si son sol est de terre (TERRE_MIN),
    si elle est large (EPAIS_OUVERT, ou EPAIS_FERME quand rien ne l'ouvre), basse pour sa largeur (2 h <= 3 ep, h <= PLAFOND)
    et si ce n'est pas une piece : une piece est fermee aux trois quarts et a des ouvertures (portes, vitres) ou touche un
    espace qui sert.
 5. Le sol G de chaque colonne : le dessus du premier massif ; pour une colonne de vide, le dessus de son couvercle ; l'eau
    posee dessus compte. Le niveau : le quart bas (NIVEAU) de G parmi les colonnes dont le plus bas plein est pres du bas du
    lieu (a moins de PLAFOND du dixieme le plus bas).
 6. Non posees : les colonnes sans rien a portee du niveau (plus bas que le niveau - 1 - CREUX, ou rien que le reste de base).
 7. Comblement : les vides de l'etape 4 ; et sous ce qui flotte au-dessus du niveau (rien dessous), si c'est massif
    (ouverture 5 x 5 apres fermeture 3 x 3) et a moins de PLAFOND du niveau.
 8. Creux : la ou la case du niveau est libre, on descend jusqu'au sol du lieu, s'il est a moins de CREUX du niveau.
"""
import numpy as np
from scipy import ndimage

BASE_MIN = (1, 5)        # part des colonnes au meme sol plat pour parler d'une base
RESTE_MAX = (1, 50)      # part des colonnes qui se servent de la base, en dessous de laquelle elle est un reste
TERRE_MIN = (1, 2)       # part du sol d'une zone qui doit etre de terre
EPAIS = 4                # rayon inscrit minimal d'un vide (un carre de 7 x 7 y tient)
EPAIS_FERME = 2          # rayon inscrit minimal d'un vide que rien n'ouvre
EPAIS_GRAND = 12         # rayon inscrit d'une grande etendue
RAMPANT = 3              # hauteur d'un vide ou l'on ne tient pas debout a l'aise : un vide sanitaire, un double sol
COUVERCLE_EPAIS = 4      # epaisseur (mediane) a partir de laquelle un couvercle est du sol et non un toit
BAS = 4                  # hauteur sous laquelle ce qui flotte, meme detache du lieu, est pose sur un socle
PLAFOND = 32             # hauteur maximale d'un vide comble
NIVEAU = (1, 4)          # le quart bas du sol
CREUX = 12               # profondeur maximale d'un creux sous le niveau
PROFONDEUR = 12          # le lieu est copie jusqu'a cette profondeur sous le niveau
OPENING = r"(glass|_pane$|_door$|trapdoor|fence|_gate$|iron_bars|_bars$|chain$|_wall$|ladder|scaffolding|barrier)"
TERRE_NOMS = ("gravel", "clay", "snow_block", "sandstone", "red_sandstone")
VEGETAL = r"(mushroom_block$|^mushroom_stem$|^bamboo$|^bamboo_block$|^cactus$)"


def kth(a, num, den):
    """La valeur de rang (n - 1) * num // den d'un tableau d'entiers trie."""
    a = np.sort(np.asarray(a).ravel())
    return int(a[(a.size - 1) * num // den])


def chessboard(mask):
    """Distance en echiquier de chaque case vraie a la plus proche case fausse (le dehors du tableau est faux)."""
    return ndimage.distance_transform_cdt(np.pad(mask, 1), metric="chessboard")[1:-1, 1:-1]


def box(mask, r, outside):
    """Vrai la ou tout le carre (2r+1) est vrai (erosion). `outside` : la valeur des cases hors du tableau."""
    p = np.pad(mask, r, constant_values=outside)
    c = np.cumsum(np.cumsum(p.astype(np.int32), axis=0), axis=1)
    c = np.pad(c, ((1, 0), (1, 0)))
    n = 2 * r + 1
    s = c[n:, n:] - c[:-n, n:] - c[n:, :-n] + c[:-n, :-n]
    return s == n * n


def erode(mask, r, outside=True):
    return box(mask, r, outside)


def dilate(mask, r):
    return ~box(~mask, r, True)


class Columns:
    """Les colonnes d'un volume, une fois le reste de base retire."""

    def __init__(self, v):
        H, D, W = v.ids.shape
        self.H, self.D, self.W = H, D, W
        leaves = v.lut(lambda p, n: "LEAVES" in p["tags"])
        self.lut_solid = v.lut(lambda p, n: p["solid"]) & ~leaves
        self.lut_air = v.lut(lambda p, n: p["air"])
        self.lut_liquid = v.lut(lambda p, n: p["liquid"])
        soft = v.lut(lambda p, n: bool({"REPLACEABLE", "FLOWERS", "LEAVES"} & p["tags"]))
        self.lut_thing = ~self.lut_air & ~self.lut_liquid & ~soft
        self.lut_light = v.lut(lambda p, n: p["light"] > 0)
        self.lut_soil = v.lut(lambda p, n: bool({"DIRT", "SAND"} & p["tags"]) or n.split("#")[0] in TERRE_NOMS)
        import re
        r = re.compile(OPENING)
        self.lut_opening = v.lut(lambda p, n: r.search(n.split("#")[0]) is not None)
        g = re.compile(VEGETAL)
        self.lut_plant = v.lut(lambda p, n: bool({"LOGS", "LEAVES", "WART_BLOCKS"} & p["tags"]) or g.search(n.split("#")[0]) is not None)
        self.lut_glass = v.lut(lambda p, n: "glass" in n and "pane" not in n)


def decide(v, inside=None, details=False):
    H, D, W = v.ids.shape
    ids = v.ids
    inside = np.ones((D, W), dtype=bool) if inside is None else inside.astype(bool)
    c = Columns(v)
    solid = c.lut_solid[ids]
    air = c.lut_air[ids]
    liquid = c.lut_liquid[ids]
    thing = c.lut_thing[ids]
    light = c.lut_light[ids]
    opening = c.lut_opening[ids]
    idx = np.arange(H)[:, None, None]
    zz, xx = np.indices((D, W))
    n_all = D * W

    def first(mask, start):
        """Pour chaque colonne, le plus petit indice >= start[z, x] ou mask est vrai ; H s'il n'y en a pas."""
        m = mask & (idx >= start[None])
        return np.where(m.any(axis=0), np.argmax(m, axis=0), H)

    zero = np.zeros((D, W), dtype=np.int64)

    # --- 2. la base, et si elle est un reste
    has0 = solid[0]
    b0 = np.where(has0, first(~solid, zero), 0)
    base, leftover, usage = None, False, 0
    sel = has0 & (b0 < H)
    if sel.any():
        key = b0[sel].astype(np.int64) * 65536 + ids[np.clip(b0 - 1, 0, H - 1), zz, xx][sel]
        vals, cnts = np.unique(key, return_counts=True)
        k = int(np.argmax(cnts))                                   # le plus frequent ; a egalite, la plus petite cle
        if cnts[k] * BASE_MIN[1] >= n_all * BASE_MIN[0]:
            base = (int(vals[k] // 65536), int(vals[k] % 65536))
    if base:
        bh, bm = base
        layer = ids[bh - 1] == bm
        on_it = (thing & (idx >= bh) & (idx < bh + 3)).any(axis=0)
        other = ~layer & solid[bh - 1]
        usage = int((layer & on_it).sum() + other.sum())
        leftover = usage * RESTE_MAX[1] <= n_all * RESTE_MAX[0]
        if leftover:
            solid = solid & (idx >= bh)
    bottom = base[0] if leftover else 0                           # sous cet indice, rien n'est au lieu

    # --- 3. les colonnes
    lo = first(solid, zero)                                        # le plus bas plein
    has = lo < H
    g0 = np.where(has, first(~solid, lo), H)                       # le dessus du premier massif
    q = np.where(has, first(solid, g0), H)                         # le couvercle du plus bas vide

    # --- 4. les vides sous le sol du lieu
    covered = has & (g0 < H) & (q < H)
    low = (thing & (idx >= g0[None]) & (idx < g0[None] + 3) & (idx < q[None])).any(axis=0)
    lit = (light & (idx >= g0[None]) & (idx < q[None])).any(axis=0)
    plant_cover = c.lut_plant[ids[np.clip(q, 0, H - 1), zz, xx]]    # sous un arbre (tronc, branche, champignon geant)
    cand = covered & ~low & ~lit
    floor_soil = c.lut_soil[ids[np.clip(g0 - 1, 0, H - 1), zz, xx]]
    label, count = regions(cand, g0, q)
    top = np.where(covered, first(~solid, np.where(covered, q, 0)), H)      # la premiere case libre au-dessus du couvercle
    cover_thick = top - q
    cover_soil = c.lut_soil[ids[np.clip(top - 1, 0, H - 1), zz, xx]]
    void = np.zeros((D, W), dtype=bool)
    infos = []
    if count:
        sides = [side(dz, dx, D, W, H, label, cand, covered, g0, q, solid, opening) for dz, dx in ((-1, 0), (1, 0), (0, -1), (0, 1))]
        n_open = sum((s == 1) for s in sides).astype(np.int64); n_opening = sum((s == 2) for s in sides).astype(np.int64)
        n_wall = sum((s == 3) for s in sides).astype(np.int64); n_used = sum((s == 4) for s in sides).astype(np.int64)
        objs = ndimage.find_objects(label + 1)
        for k in range(count):
            sl = objs[k]
            m = label[sl] == k
            area = int(m.sum())
            if area < 4:
                continue
            thick = int(chessboard(m).max())
            if thick < EPAIS_FERME:
                continue
            po, pg, pw, pu = int(n_open[sl][m].sum()), int(n_opening[sl][m].sum()), int(n_wall[sl][m].sum()), int(n_used[sl][m].sum())
            perim = po + pg + pw + pu
            soil = int(floor_soil[sl][m].sum())
            hmed = kth((q - g0)[sl][m], 1, 2)
            closed = pg + pw + pu
            room = 4 * closed >= 3 * perim and (50 * pg >= perim or 10 * pu >= perim) and perim > 0
            shut = 50 * (po + pg + pu) <= perim                     # rien ne l'ouvre (a un cinquantieme pres)
            top_soil = int(cover_soil[sl][m].sum())
            plants = int(plant_cover[sl][m].sum())
            cmed = kth(cover_thick[sl][m], 1, 2)
            ground_cover = 2 * top_soil >= area or cmed >= COUVERCLE_EPAIS
            pocket = shut and 2 * hmed <= 3 * thick
            crawl = thick >= EPAIS and hmed <= RAMPANT
            expanse = thick >= EPAIS_GRAND and thick >= 2 * hmed
            under_ground = thick >= EPAIS and ground_cover and hmed <= 2 * thick
            is_void = (soil * TERRE_MIN[1] >= area * TERRE_MIN[0] and hmed <= PLAFOND and not room and 2 * plants < area
                       and (pocket or crawl or expanse or under_ground))
            if is_void:
                void[sl] |= m
            if details and area >= 150:
                cz, cx = [int(round(t)) for t in ndimage.center_of_mass(m)]
                infos.append({"aire": area, "centre": [cx + sl[1].start, cz + sl[0].start], "ep": thick, "h": hmed, "terre": round(soil / area, 2),
                              "ouvert": round(po / max(1, perim), 2), "ouverture": round(pg / max(1, perim), 2), "mur": round(pw / max(1, perim), 2),
                              "sert": round(pu / max(1, perim), 2), "piece": bool(room), "couvercle": [cmed, round(top_soil / area, 2)],
                              "cas": "poche" if pocket else "rampant" if crawl else "etendue" if expanse else "sous le sol" if under_ground else "-", "vide": bool(is_void)})

    # --- 5. le sol de chaque colonne, le niveau
    G = np.where(void, top, g0)                                    # le dessus du couvercle pour une colonne de vide
    dry = first(~liquid, G)                                        # l'eau posee sur le sol compte : on monte a sa surface
    G = np.where(has & (G < H), dry, G)
    voters = inside & has & (G < H)
    sol = v.sol
    if voters.any():
        z0 = kth(lo[voters], 1, 10)
        near = voters & (lo <= z0 + PLAFOND)
        sol = kth(G[near], NIVEAU[0], NIVEAU[1])
    sol = max(0, min(H - 1, sol))

    # --- 6. les colonnes non posees
    # (un sol plus bas que le niveau, mais a portee de creux, est garde : on creusera jusqu'a lui)
    content = ~air & (idx >= max(sol - 1 - CREUX, bottom))
    skip = inside & ~content.any(axis=0)

    # --- 7. le comblement
    fill = np.zeros((H, D, W), dtype=bool)
    lowest = max(0, sol - PROFONDEUR)
    put = inside & ~skip
    fill |= (idx >= np.maximum(g0, lowest)[None]) & (idx < q[None]) & (void & put)[None]
    floating = has & (lo > bottom) if leftover else has & (lo > 0)
    plant_low = c.lut_plant[ids[np.clip(lo, 0, H - 1), zz, xx]]     # ce qui flotte et dont le dessous est vegetal : une branche, une frondaison
    mass = has & (lo <= sol + PLAFOND) & ~plant_low
    massive = dilate(erode(erode(dilate(mass, 1), 1), 2), 2)        # fermeture 3 x 3 puis ouverture 5 x 5
    # les corps : colonnes voisines dont les premiers massifs se touchent ; un massif s'arrete au premier bloc vegetal (un
    # arbre plante sur le lieu ne relie pas sa frondaison au lieu)
    rooted = has & ~plant_low
    gtop = np.where(rooted, first(~solid | c.lut_plant[ids], lo), 0)
    body, nb = bodies(rooted, lo, gtop)
    anchored = np.zeros(nb + 1, dtype=bool)
    anchored[np.unique(body[rooted & (lo <= sol)])] = True         # un corps est ancre s'il descend jusqu'au terrain
    anchored[0] = False
    reach = np.where(anchored[body], PLAFOND, BAS)
    under = put & floating & massive & (lo > sol) & (lo <= sol + reach)
    fill |= (idx >= lowest) & (idx < lo[None]) & under[None]
    below = put & floating & (lo <= sol)                           # sous ce qui flotte plus bas que le niveau : le terrain
    fill |= (idx >= lowest) & (idx < lo[None]) & below[None]
    if leftover:
        fill |= (idx >= lowest) & (idx < bottom) & put[None]       # le reste de base n'est pas copie
    fill &= ~solid | (idx < bottom)

    # --- 7 bis. sous un couvercle de verre, la derniere case du comblement reprend le sol du vide (on le voit a travers)
    glass_cover = c.lut_glass[ids[np.clip(q, 0, H - 1), zz, xx]]
    floor_id = ids[np.clip(g0 - 1, 0, H - 1), zz, xx]
    capped = void & put & glass_cover & (g0 >= 1) & c.lut_solid[floor_id] & (q - 1 >= lowest) & (q > g0)
    cap_y = np.where(capped, q - 1, -1)
    cap_id = np.where(capped, floor_id, 0)

    # --- 8. les creux
    dig = np.full((D, W), sol, dtype=np.int64)
    if sol > 0:
        free = ~solid & ~fill
        open_at = put & (free[sol] if sol < H else True)
        blocked = ~free & (idx < sol)
        f = np.where(blocked.any(axis=0), sol - np.argmax(blocked[sol - 1::-1], axis=0), 0)   # la plus basse case libre du creux
        real = (f >= 1) & solid[np.clip(f - 1, 0, H - 1), zz, xx] & (f - 1 >= bottom)
        ok = open_at & real & (f < sol) & (sol - f <= CREUX)
        dig = np.where(ok, f, sol)

    kind = "pas de base" if not base else ("reste de base retire" if leftover else "base utilisee")
    note = (f"{kind}{'' if not base else f' (hauteur {base[0]}, {usage} colonnes s en servent)'}, niveau {sol} (fiche {v.sol}), "
            f"{int((void & put).sum())} colonnes de vide sous le sol, {int(under.sum())} sous ce qui flotte, {int(skip.sum())} non posees, {int((dig < sol).sum())} creusees")
    if details:
        return sol, fill, skip, dig, (cap_y, cap_id), note, {"zones": sorted(infos, key=lambda r: -r["aire"]), "base": base, "leftover": leftover, "void": void, "G": G, "lo": lo,
                                                            "g0": g0, "q": q, "cand": cand, "under": under, "voters": voters, "names": v.names}
    return sol, fill, skip, dig, (cap_y, cap_id), note


def bodies(has, lo, g0):
    """Etiquettes (1..n, 0 dehors) des corps : colonnes pleines voisines (4 cotes) dont les premiers massifs [lo, g0) se touchent ou se recouvrent."""
    from scipy.sparse import coo_matrix
    from scipy.sparse.csgraph import connected_components
    D, W = has.shape
    node = -np.ones((D, W), dtype=np.int64)
    n = int(has.sum())
    label = np.zeros((D, W), dtype=np.int64)
    if not n:
        return label, 0
    node[has] = np.arange(n)
    rows, cols = [], []
    for dz, dx in ((0, 1), (1, 0)):
        a = (slice(0, D - dz), slice(0, W - dx)); b = (slice(dz, D), slice(dx, W))
        link = has[a] & has[b] & (lo[a] <= g0[b]) & (lo[b] <= g0[a])
        rows.append(node[a][link]); cols.append(node[b][link])
    r_, c_ = np.concatenate(rows), np.concatenate(cols)
    count, comp = connected_components(coo_matrix((np.ones(len(r_), dtype=np.int8), (r_, c_)), shape=(n, n)), directed=False)
    label[has] = comp + 1
    return label, int(count)


def regions(mask, g0, q):
    """Etiquettes (0..n-1, -1 dehors) des zones : colonnes de `mask` voisines (4 cotes) dont les vides [g0, q) se recouvrent."""
    from scipy.sparse import coo_matrix
    from scipy.sparse.csgraph import connected_components
    D, W = mask.shape
    node = -np.ones((D, W), dtype=np.int64)
    n = int(mask.sum())
    label = -np.ones((D, W), dtype=np.int64)
    if not n:
        return label, 0
    node[mask] = np.arange(n)
    rows, cols = [], []
    for dz, dx in ((0, 1), (1, 0)):
        a = (slice(0, D - dz), slice(0, W - dx)); b = (slice(dz, D), slice(dx, W))
        link = mask[a] & mask[b] & (np.maximum(g0[a], g0[b]) < np.minimum(q[a], q[b]))
        rows.append(node[a][link]); cols.append(node[b][link])
    r_, c_ = np.concatenate(rows), np.concatenate(cols)
    count, comp = connected_components(coo_matrix((np.ones(len(r_), dtype=np.int8), (r_, c_)), shape=(n, n)), directed=False)
    label[mask] = comp
    return label, int(count)


def side(dz, dx, D, W, H, label, cand, covered, g0, q, solid, opening):
    """Ce que voit une colonne de zone du cote (dz, dx) : 0 la meme zone, 1 ouvert, 2 ouverture, 3 mur, 4 un espace qui sert."""
    zz, xx = np.indices((D, W))
    nz, nx = zz + dz, xx + dx
    ok = (nz >= 0) & (nz < D) & (nx >= 0) & (nx < W)
    nzc, nxc = np.clip(nz, 0, D - 1), np.clip(nx, 0, W - 1)
    edge = ~ok
    same = ok & (label[nzc, nxc] == label) & (label >= 0)
    passable = np.ones((D, W), dtype=bool)
    has_opening = np.zeros((D, W), dtype=bool)
    for k in range(4):
        y = np.clip(g0 + k, 0, H - 1)
        within = (g0 + k) < np.minimum(q, H)
        s = solid[y, nzc, nxc]
        if k < 2:
            passable &= ~(s & within)
        has_opening |= opening[y, nzc, nxc] & within
    n_cov, n_g0, n_q, n_cand = covered[nzc, nxc], g0[nzc, nxc], q[nzc, nxc], cand[nzc, nxc]
    used = ok & n_cov & ~n_cand & (np.maximum(g0, n_g0) < np.minimum(q, n_q))
    out = np.zeros((D, W), dtype=np.int8)
    is_c = label >= 0
    out[is_c] = 3
    out[is_c & has_opening] = 2
    out[is_c & passable] = 1
    out[is_c & used] = 4
    out[is_c & edge] = 1
    out[is_c & same] = 0
    return out
