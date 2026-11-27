#!/usr/bin/env python3
"""La fiche d'un personnage, pour la fin de sa video : son arme, ses capacites et son ultime sur une seule image, dans le
style des overlays (tools/overlay_capacite.py).

Usage : python3 tools/recap_perso.py <personnage> <dossier>

Ecrit <dossier>/<personnage>_fiche.png (1920 x 1080).

  - Le fond, l'illustration a droite, le logo, le signe de la voie et le nom a la verticale a gauche : comme l'overlay.
  - En haut, deux cartouches : « FICHE » a la couleur du personnage, puis son nom.
  - Au milieu, une carte par element, deux par rangee : l'arme et ses variantes (tools/variantes.txt), les actifs et
    l'ultime, puis les passifs. Sur le bord haut de chaque carte, son type (Arme, Actif, Passif, Ultime) et son nom ;
    dedans, son icone (pour une arme en 3D du pack prive, son rendu : RENDUS) et sa description.
  - La description : tools/descriptions.txt, la ligne <id>.recap si elle existe (un texte qui tient sur la carte), sinon
    <id>, sinon le guide.

Comme les overlays : illustration, signe de la voie, icones, cadres et polices sont prives (jamais versionnes) ; l'image
va dans un dossier sous prive/.
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import overlay_capacite as oc  # noqa: E402

W, H = oc.W, oc.H
K = 3                                 # un pixel du pack = 3 (boites des cartes)
ZONE = (250, 1560)                    # les cartes, de gauche a droite (le nom vertical a gauche, l'illustration a droite)
HAUT, BAS = 150, 1062                 # sous les cartouches du haut, jusqu'au bas de l'image
ECART = 18                            # entre deux cartes
# Les armes en 3D du pack prive, rendues comme l'animation de l'Embleme de Feu (tools/build_embleme_anim.py) : le
# prefixe de leurs textures.
RENDUS = {"marth_falchion": "t", "marth_sainte_falchion": "sainte_t"}
TYPES = {"Actif": "ACTIF", "Passif": "PASSIF", "Ultime": "ULTIME"}


def elements(ch):
    """L'arme et ses variantes, les actifs, l'ultime, puis les passifs (sans les ultimes en duo ni les Synergies)."""
    abil = [a for a in ch["abilities"] if a.get("type") in TYPES]
    armes = [a for a in abil if a.get("weapon")]
    ids = {a["id"] for a in armes}
    variantes = [a for a in abil if a not in armes and any(a["id"] != b and a.get("icon") == b.get("icon") and a.get("weapon") for b in armes)]
    reste = [a for a in abil if a not in armes and a not in variantes]
    ordre = {"Actif": 0, "Ultime": 1, "Passif": 2}
    reste.sort(key=lambda a: ordre[a["type"]])
    return armes + variantes + reste


def rendu_arme(prefixe, taille):
    """L'arme 3D, penchee comme son icone, rognee et mise a la taille."""
    import numpy as np
    import build_embleme_anim as ea
    model = ea.modele()
    f, echelle = ea.placer(model)
    px = ea.rendre(model, prefixe, f, echelle)
    im = Image.fromarray((np.clip(px, 0, 1) * 255).astype(np.uint8), "RGBA")
    im = im.crop(im.getbbox())
    k = taille / max(im.width, im.height)
    return im.resize((round(im.width * k), round(im.height * k)), Image.LANCZOS)


def icone(a, taille):
    """L'icone d'un element : le rendu de son arme 3D, sinon son icone du pack (en gros pixels), sinon rien."""
    if a["id"] in RENDUS:
        try:
            return rendu_arme(RENDUS[a["id"]], taille)
        except Exception:
            pass
    src = ROOT / "resourcepack-prive" / "assets" / "crossfight" / "textures" / "item" / "icone" / (a["id"] + ".png")
    if src.exists():
        ic = Image.open(src).convert("RGBA")
        return oc.gros(ic, max(1, taille // ic.height))
    return None


def cartouches(gauche, droite, k, bs, pp, rgb):
    """Deux cartouches du HUD cote a cote : le premier a la couleur du personnage. Rend l'image (avec sa lueur) et le
    decalage de la lueur."""
    from build_menu_franuka import wide
    banniere = bs.ui_sprite("Title banners", "BannerMedium_01B")
    milieu = pp.interieur(bs, "BannerMedium_01B", k)
    t1, m1 = oc.ligne_hud(gauche, k, bs, pp)
    t2, m2 = oc.ligne_hud(droite, k, bs, pp)
    p1 = oc.gros(oc.recolore(wide(banniere, max(48, math.ceil((t1.width / k + 36) / 16) * 16)), oc.FOND_CARTOUCHE, rgb), k)
    p2 = oc.gros(wide(banniere, max(48, math.ceil((t2.width / k + 36) / 16) * 16)), k)
    chevauche = 6 * k
    out = Image.new("RGBA", (p1.width + p2.width - chevauche + 32, max(p1.height, p2.height) + 32), (0, 0, 0, 0))
    for plaque, texte, tm, x in ((p1, t1, m1, 0), (p2, t2, m2, p1.width - chevauche)):
        el = oc.lueur(plaque, bs.NAVY_DARK, 8)
        el.alpha_composite(plaque, (16, 16))
        el.alpha_composite(texte, (round(16 + plaque.width / 2 - texte.width / 2), round(16 + milieu - tm)))
        out.alpha_composite(el, (x, 0))
    return out


def fiche(cid):
    import build_selection as bs
    import presentation_perso as pp
    from build_menu_franuka import nine
    ch = oc.capacites(cid)
    desc = oc.descriptions()
    name, color = {i: (n, c) for i, n, c in bs.roster()}[cid]
    rgb = bs.hexrgb(color)

    # Le fond et le portail, comme l'overlay.
    img = bs.backdrop((W, H)).convert("RGBA")
    sheet, _ = bs.portal(W // 8, H // 8, (W / 8 * 0.86, H / 8 * 0.42), card=False)
    portail = ImageChops.multiply(sheet.crop((0, 0, W // 8, H // 8)), Image.new("RGBA", (W // 8, H // 8), rgb + (255,)))
    portail = portail.resize((W, H), Image.NEAREST)
    portail.putalpha(110)
    img.alpha_composite(portail)

    # L'illustration a droite ; ce qui deborde a gauche de la zone des cartes s'efface.
    art_src = next(iter(sorted((ROOT / "resourcepack-prive" / "selection" / "art").glob(cid + ".*"))), None)
    voie, cadrage = bs.framings().get(cid, (None, None))
    if art_src:
        art = bs.load(art_src)
        vx, vy, zoom = cadrage or bs.auto_framing(art)
        s = zoom * 1.5 * H / art.height
        a_img = art.resize((round(art.width * s), round(art.height * s)), Image.LANCZOS)
        couche = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        couche.alpha_composite(a_img, (round(1795 - vx * a_img.width), round(205 - vy * a_img.height)))
        masque = Image.new("L", (W, H), 255)
        bord = ZONE[1] + 40
        masque.paste(Image.new("L", (bord - oc.ESTOMPE, H), 0), (0, 0))
        masque.paste(Image.linear_gradient("L").rotate(90).resize((oc.ESTOMPE, H)), (bord - oc.ESTOMPE, 0))
        couche.putalpha(ImageChops.multiply(couche.getchannel("A"), masque))
        img.alpha_composite(couche)

    # A gauche : le signe de la voie au-dessus du nom a la verticale, centres en hauteur ; le logo en haut.
    signe = None
    if voie:
        src = next(iter(sorted((ROOT / "resourcepack-prive" / "selection" / "voie").glob(voie + ".*"))), None)
        if src:
            signe = pp.signe_voie(src).resize((256, 256), Image.LANCZOS)
    nom = oc.texte_pixel(name.upper(), bs.UI_TITLE, 17, bs.CREAM, rgb, oc.K_NOM).rotate(90, expand=True)
    haut_groupe = nom.height + ((132 + 44) if signe else 0)
    gy, colonne = round(H / 2 - haut_groupe / 2), 126
    if signe:
        petit = signe.resize((132, 132), Image.LANCZOS)
        img.alpha_composite(oc.lueur(petit, rgb, 14), (colonne - petit.width // 2 - 28, gy - 28))
        img.alpha_composite(petit, (colonne - petit.width // 2, gy))
        gy += petit.height + 44
    img.alpha_composite(oc.lueur(nom, bs.NAVY_DARK, 10), (colonne - nom.width // 2 - 20, gy - 20))
    img.alpha_composite(nom, (colonne - nom.width // 2, gy))
    logo = oc.logo_png(200)
    if logo:
        img.alpha_composite(logo, (24, 18))

    # En haut, centres sur la zone des cartes : « FICHE » et le nom.
    tete = cartouches("FICHE", name.upper(), oc.K_UI, bs, pp, rgb)
    img.alpha_composite(tete, (round((ZONE[0] + ZONE[1]) / 2 - tete.width / 2), 10))

    # Les cartes, deux par rangee.
    liste = elements(ch)
    rangees = math.ceil(len(liste) / 2)
    cw = (ZONE[1] - ZONE[0] - ECART) // 2
    chh = (BAS - HAUT - (rangees - 1) * ECART) // rangees
    boite_px = bs.ui_sprite("Background boxes", "BGbox_01B")
    bm = next(y for y in range(boite_px.height) if boite_px.getpixel((24, y))[3])
    for n, a in enumerate(liste):
        x0 = ZONE[0] + (n % 2) * (cw + ECART)
        y0 = HAUT + (n // 2) * (chh + ECART)
        boite = oc.gros(oc.recolore(nine(boite_px, cw // K + 2 * bm, chh // K + 2 * bm), oc.FOND_BOITE, bs.NAVY), K)
        img.alpha_composite(oc.lueur(boite, bs.NAVY_DARK, 8), (x0 - bm * K - 16, y0 - bm * K - 16))
        img.alpha_composite(boite, (x0 - bm * K, y0 - bm * K))
        # L'icone (ou l'arme), a gauche, avec une lueur a la couleur du personnage ; la Sainte-Falchion, bleue.
        place = min(150, chh - 70)
        ic = icone(a, place)
        gauche = 34 + place
        if ic:
            teinte = (110, 210, 255) if a["id"] == "marth_sainte_falchion" else rgb
            el = oc.lueur(ic, teinte, 10)
            el.alpha_composite(ic, (20, 20))
            img.alpha_composite(el, (round(x0 + 24 + place / 2 - ic.width / 2) - 20, round(y0 + 30 + (chh - 30) / 2 - ic.height / 2) - 20))
        # La description, a droite de l'icone, en gros pixels x3 si elle tient, sinon x2.
        texte = desc.get(a["id"] + ".recap") or desc.get(a["id"]) or a.get("description", "")
        larg, haut_t = cw - gauche - 30, chh - 58
        for k in (3, 2):
            d = oc.paragraphe(texte, bs.UI_TEXT, 12, larg, 11, bs.CREAM, bs.NAVY_DARK, k)
            if d.height <= haut_t:
                break
        img.alpha_composite(d, (round(x0 + gauche + 6 + larg / 2 - d.width / 2), round(y0 + 34 + (chh - 34) / 2 - d.height / 2)))
        # Sur le bord haut : le type et le nom.
        typ = "ARME" if a.get("weapon") or a["id"] in RENDUS else TYPES[a["type"]]
        nom_el = desc.get(a["id"] + ".nom", a["name"]).upper()
        bandeau = cartouches(typ, nom_el, 2, bs, pp, rgb)
        img.alpha_composite(bandeau, (x0 + 6, y0 - 34))
    return img.convert("RGB")


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 1
    cid, out = sys.argv[1], Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    chemin = out / f"{cid}_fiche.png"
    fiche(cid).save(chemin, optimize=True)
    print(chemin)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
