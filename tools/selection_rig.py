#!/usr/bin/env python3
"""Les illustrations animees de l'ecran de selection : un personnage decoupe en calques, qui bougent chacun a
leur facon (tools/build_selection.py en fait une carte animee).

Un decoupage (RIGS) donne, en pixels de l'illustration d'origine, le contour de chaque calque et son pivot. Les
calques sont separes, et ce que chacun cachait est comble (les couleurs voisines s'etendent dans le trou) : quand un
calque bouge, on ne voit pas de vide derriere lui. frames() rend ensuite les images d'une boucle.

Marth : la tete et les cheveux penchent un peu d'un cote puis de l'autre autour du cou, le bras pose sur la tete
suit le mouvement autour de l'epaule, le buste respire.
"""
import math

from PIL import Image, ImageChops, ImageDraw, ImageFilter

RIGS = {
    "marth": {
        # Le bras leve, de la main posee sur la tete jusqu'a l'epaule (il passe devant la tete).
        "arm": {"poly": [(612, 305), (645, 268), (700, 268), (748, 315), (765, 375), (835, 425), (905, 515), (938, 600),
                         (915, 668), (830, 672), (782, 610), (758, 545), (718, 472), (688, 412), (640, 395), (608, 352)],
                "pivot": (862, 625)},
        # La tete et les cheveux, jusqu'a la base du cou.
        "head": {"poly": [(410, 425), (425, 385), (468, 335), (520, 305), (590, 288), (690, 298), (745, 350), (756, 450), (742, 540),
                          (704, 600), (648, 628), (590, 620), (540, 606), (500, 600), (455, 590), (430, 545), (420, 480)],
                 "pivot": (642, 612)},
        "base_y": 1150,          # le bas du buste : il ne bouge pas quand le personnage respire
        "head_deg": 1.8, "arm_follow": 0.95, "breath": 0.006,
    },
}


def soft_mask(size, poly, blur=2.0):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).polygon(poly, fill=255)
    return m.filter(ImageFilter.GaussianBlur(blur))


def fill_holes(layer, hole, rounds=7):
    """Etend les couleurs du calque dans la zone `hole` (un masque) : a chaque tour, les pixels vides prennent la
    moyenne floue de leurs voisins deja peints, de plus en plus loin. Un comblement simple, suffisant derriere un
    calque qui ne bouge que de quelques pixels."""
    out = layer.copy()
    for k in range(rounds):
        r = 3 * 2 ** k
        a = out.getchannel("A")
        pre = Image.merge("RGB", [ImageChops.multiply(c, a) for c in out.split()[:3]]).filter(ImageFilter.GaussianBlur(r))
        ab = a.filter(ImageFilter.GaussianBlur(r))
        # couleur = flou(couleur x alpha) / flou(alpha)
        px = [c.load() for c in pre.split()]
        al, ho, o = ab.load(), hole.load(), out.load()
        for y in range(out.height):
            for x in range(out.width):
                if ho[x, y] and o[x, y][3] < 250 and al[x, y] > 6:
                    k_ = 255.0 / al[x, y]
                    o[x, y] = (min(255, round(px[0][x, y] * k_)), min(255, round(px[1][x, y] * k_)), min(255, round(px[2][x, y] * k_)), 255)
    return out


def layers(art, rig):
    """(buste, tete, bras) : chaque calque sur sa propre image, les trous combles."""
    size = art.size
    arm_m, head_m = soft_mask(size, rig["arm"]["poly"]), soft_mask(size, rig["head"]["poly"])
    alpha = art.getchannel("A")
    inv = lambda m: m.point(lambda v: 255 - v)
    def cut(mask):
        out = art.copy()
        out.putalpha(ImageChops.multiply(alpha, mask))
        return out
    arm = cut(arm_m)
    head = cut(ImageChops.multiply(head_m, inv(arm_m)))
    body = cut(ImageChops.multiply(inv(head_m), inv(arm_m)))
    # Sous la main : des cheveux ; sous la tete et le bras : le buste, la ou l'illustration est peinte.
    hard = lambda m: m.point(lambda v: 255 if v > 40 else 0)
    head = fill_holes(head, ImageChops.multiply(hard(head_m), hard(arm_m)))
    painted = alpha.point(lambda v: 255 if v > 200 else 0)
    body = fill_holes(body, ImageChops.multiply(ImageChops.lighter(hard(head_m), hard(arm_m)), painted))
    return body, head, arm


def frames(art, name, count):
    """Les `count` images de la boucle, a la taille de l'illustration."""
    rig = RIGS[name]
    body, head, arm = layers(art, rig)
    w, h = art.size
    hx, hy = rig["head"]["pivot"]
    ax, ay = rig["arm"]["pivot"]
    out = []
    for n in range(count):
        t = 2 * math.pi * n / count
        deg = rig["head_deg"] * math.sin(t)
        stretch = 1 + rig["breath"] * (0.5 - 0.5 * math.cos(2 * t))
        yb = rig["base_y"]
        lift = lambda y: (yb - y) * (stretch - 1)                     # de combien un point monte quand il respire
        frame = body.transform((w, h), Image.AFFINE, (1, 0, 0, 0, 1 / stretch, yb * (1 - 1 / stretch)), resample=Image.BICUBIC)
        frame.alpha_composite(head.rotate(deg, center=(hx, hy), resample=Image.BICUBIC, translate=(0, -lift(hy))))
        frame.alpha_composite(arm.rotate(deg * rig["arm_follow"], center=(ax, ay), resample=Image.BICUBIC, translate=(0, -lift(ay))))
        out.append(frame)
    return out
