#!/usr/bin/env python3
"""La fiche d'un personnage, pour la fin de sa video : son arme, ses capacites et son ultime sur une seule image, dans le
style des overlays (tools/overlay_capacite.py).

Usage : python3 tools/recap_perso.py <personnage> <dossier>

Ecrit <dossier>/<personnage>_fiche.png (1920 x 1080).

  - Le fond, l'illustration a droite, le logo, le signe de la voie et le nom a la verticale a gauche : comme l'overlay.
  - En haut, deux cartouches : « FICHE » a la couleur du personnage, puis son nom.
  - Au milieu, trois bandes bien separees, chacune sous son titre (un cartouche a la couleur du personnage et un trait) :
    ARME (l'arme et ses variantes, tools/variantes.txt), CAPACITES (les actifs puis les passifs, chacun avec son type
    sur sa carte) et ULTIME (sur toute la largeur). Une carte par element, cote a cote dans sa bande : son nom sur son
    bord haut, son icone (pour une arme en 3D du pack prive, son rendu : RENDUS) et sa description ; une carte etroite
    a son icone en haut et le texte dessous.
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
HAUT, BAS = 122, 1066                 # sous les cartouches du haut, jusqu'au bas de l'image
ECART = 18                            # entre deux cartes d'une bande
TITRE = 52                            # la hauteur du titre d'une bande (cartouche et trait), avec l'espace sous lui
ENTRE = 14                            # entre deux bandes
# La part de la hauteur de chaque bande (les capacites, cote a cote sur des cartes etroites, ont besoin de plus).
POIDS = {"ARME": 1.0, "CAPACITÉS": 1.35, "ULTIME": 0.62}
# Les armes en 3D du pack prive, rendues comme l'animation de l'Embleme de Feu (tools/build_embleme_anim.py) : le
# prefixe de leurs textures.
RENDUS = {"marth_falchion": "t", "marth_sainte_falchion": "sainte_t"}
TYPES = {"Actif": "ACTIF", "Passif": "PASSIF", "Ultime": "ULTIME"}


def sections(ch):
    """Les trois bandes : [(titre, elements)] : l'arme et ses variantes, les capacites (actifs puis passifs), l'ultime
    (sans les ultimes en duo ni les Synergies). Une bande vide n'est pas montree."""
    abil = [a for a in ch["abilities"] if a.get("type") in TYPES]
    armes = [a for a in abil if a.get("weapon")]
    variantes = [a for a in abil if a not in armes and any(a["id"] != b["id"] and a.get("icon") == b.get("icon") for b in armes)]
    reste = [a for a in abil if a not in armes and a not in variantes]
    capacites = [a for a in reste if a["type"] == "Actif"] + [a for a in reste if a["type"] == "Passif"]
    ultime = [a for a in reste if a["type"] == "Ultime"]
    return [(t, l) for t, l in (("ARME", armes + variantes), ("CAPACITÉS", capacites), ("ULTIME", ultime)) if l]


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
    """Un ou deux cartouches du HUD cote a cote : le premier (s'il y en a deux) a la couleur du personnage. Rend l'image,
    avec sa lueur (16 pixels autour)."""
    from build_menu_franuka import wide
    banniere = bs.ui_sprite("Title banners", "BannerMedium_01B")
    milieu = pp.interieur(bs, "BannerMedium_01B", k)
    pieces = []
    for n, mot in enumerate(m for m in (gauche, droite) if m):
        t, m = oc.ligne_hud(mot, k, bs, pp)
        plaque = wide(banniere, max(48, math.ceil((t.width / k + 36) / 16) * 16))
        if n == 0 and gauche and droite:
            plaque = oc.recolore(plaque, oc.FOND_CARTOUCHE, rgb)
        pieces.append((oc.gros(plaque, k), t, m))
    chevauche = 6 * k
    largeur = sum(p.width for p, _, _ in pieces) - chevauche * (len(pieces) - 1)
    out = Image.new("RGBA", (largeur + 32, max(p.height for p, _, _ in pieces) + 32), (0, 0, 0, 0))
    x = 0
    for plaque, texte, tm in pieces:
        el = oc.lueur(plaque, bs.NAVY_DARK, 8)
        el.alpha_composite(plaque, (16, 16))
        el.alpha_composite(texte, (round(16 + plaque.width / 2 - texte.width / 2), round(16 + milieu - tm)))
        out.alpha_composite(el, (x, 0))
        x += plaque.width - chevauche
    return out


def titre_bande(texte, largeur, bs, pp, rgb):
    """Le titre d'une bande : un cartouche a la couleur du personnage, puis un trait creme qui va jusqu'au bout."""
    from build_menu_franuka import wide
    from PIL import ImageDraw
    k = 2
    banniere = bs.ui_sprite("Title banners", "BannerMedium_01B")
    milieu = pp.interieur(bs, "BannerMedium_01B", k)
    t, m = oc.ligne_hud(texte, k, bs, pp)
    plaque = oc.gros(oc.recolore(wide(banniere, max(48, math.ceil((t.width / k + 40) / 16) * 16)), oc.FOND_CARTOUCHE, rgb), k)
    out = Image.new("RGBA", (largeur + 32, plaque.height + 32), (0, 0, 0, 0))
    trait = Image.new("RGBA", out.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(trait)
    y = 16 + milieu
    d.rectangle([16 + plaque.width - 8, y - 2, 16 + largeur, y + 1], fill=bs.CREAM + (200,))
    d.rectangle([16 + plaque.width - 8, y + 2, 16 + largeur, y + 3], fill=rgb + (200,))
    out.alpha_composite(oc.lueur(trait.crop((16, 0, out.width - 16, out.height)), rgb, 6), (4, -12))
    out.alpha_composite(trait)
    el = oc.lueur(plaque, bs.NAVY_DARK, 8)
    el.alpha_composite(plaque, (16, 16))
    el.alpha_composite(t, (round(16 + plaque.width / 2 - t.width / 2), round(16 + milieu - m)))
    out.alpha_composite(el, (0, 0))
    return out


def mise_en_page(cw, chh):
    """Ou vont l'icone et le texte d'une carte : etroite (moins de 520 pixels), l'icone en haut et le texte dessous ;
    sinon l'icone a gauche. Rend (etroite, taille de l'icone, largeur et hauteur du texte, haut du texte)."""
    haut_in = 30                                      # sous le cartouche du nom
    if cw < 520:
        place = min(136, round((chh - haut_in) * 0.42))
        return True, place, cw - 40, chh - haut_in - place - 22, haut_in + place + 12
    place = min(150, chh - 60)
    return False, place, cw - 64 - place, chh - haut_in - 16, haut_in


def texte_de(a, desc):
    return desc.get(a["id"] + ".recap") or desc.get(a["id"]) or a.get("description", "")


def taille_texte(a, cw, chh, desc, bs):
    """Les gros pixels du texte de cette carte : x3 s'il tient, sinon x2."""
    _, _, larg, haut_t, _ = mise_en_page(cw, chh)
    for k in (3, 2):
        if oc.paragraphe(texte_de(a, desc), bs.UI_TEXT, 12, larg, 11, bs.CREAM, bs.NAVY_DARK, k).height <= haut_t:
            return k
    return 2


def carte(img, a, x0, y0, cw, chh, avec_type, desc, bs, pp, rgb, k_texte=None):
    """Une carte : sa boite, son nom sur le bord haut, son icone et sa description (en gros pixels k_texte : toutes
    les cartes d'une bande ont la meme taille de texte). avec_type : son type (Actif, Passif) dans le coin haut droit."""
    from build_menu_franuka import nine
    boite_px = bs.ui_sprite("Background boxes", "BGbox_01B")
    bm = next(y for y in range(boite_px.height) if boite_px.getpixel((24, y))[3])
    boite = oc.gros(oc.recolore(nine(boite_px, cw // K + 2 * bm, chh // K + 2 * bm), oc.FOND_BOITE, bs.NAVY), K)
    img.alpha_composite(oc.lueur(boite, bs.NAVY_DARK, 8), (x0 - bm * K - 16, y0 - bm * K - 16))
    img.alpha_composite(boite, (x0 - bm * K, y0 - bm * K))
    etroite, place, larg, haut_t, ty = mise_en_page(cw, chh)
    haut_in = 30
    teinte = (110, 210, 255) if a["id"] == "marth_sainte_falchion" else rgb
    ic = icone(a, place)
    if ic:
        el = oc.lueur(ic, teinte, 10)
        el.alpha_composite(ic, (20, 20))
        if etroite:
            img.alpha_composite(el, (round(x0 + cw / 2 - ic.width / 2) - 20, round(y0 + haut_in + 4 + place / 2 - ic.height / 2) - 20))
        else:
            img.alpha_composite(el, (round(x0 + 24 + place / 2 - ic.width / 2) - 20, round(y0 + haut_in + (chh - haut_in) / 2 - ic.height / 2) - 20))
    k = k_texte or taille_texte(a, cw, chh, desc, bs)
    d = oc.paragraphe(texte_de(a, desc), bs.UI_TEXT, 12, larg, 11, bs.CREAM, bs.NAVY_DARK, k)
    cx = x0 + cw / 2 if etroite else x0 + 40 + place + larg / 2
    img.alpha_composite(d, (round(cx - d.width / 2), round(y0 + ty + haut_t / 2 - d.height / 2)))
    if avec_type:
        # Le type, dans le coin haut droit de la carte, a la couleur du personnage.
        clair = tuple(min(255, round(c + (255 - c) * 0.45)) for c in rgb)
        t = oc.texte_pixel(TYPES[a["type"]], bs.UI_TITLE, 17, clair, bs.NAVY_DARK, 2)
        img.alpha_composite(t, (x0 + cw - t.width - 30, y0 + haut_in + 4))
    nom_el = desc.get(a["id"] + ".nom", a["name"]).upper()
    img.alpha_composite(cartouches("", nom_el, 2, bs, pp, rgb), (x0 + 6, y0 - 34))


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

    # Les trois bandes, chacune sous son titre, leurs cartes cote a cote.
    bandes = sections(ch)
    libre = BAS - HAUT - len(bandes) * TITRE - (len(bandes) - 1) * ENTRE
    total = sum(POIDS.get(t, 1.0) for t, _ in bandes)
    y = HAUT
    for titre, liste in bandes:
        img.alpha_composite(titre_bande(titre, ZONE[1] - ZONE[0], bs, pp, rgb), (ZONE[0] - 16, y - 16))
        y += TITRE
        bh = round(libre * POIDS.get(titre, 1.0) / total)
        cw = (ZONE[1] - ZONE[0] - (len(liste) - 1) * ECART) // len(liste)
        # Une meme taille de texte pour toute la bande : la plus grande qui tient sur chacune de ses cartes.
        k_bande = min(taille_texte(a, cw, bh - 18, desc, bs) for a in liste)
        for n, a in enumerate(liste):
            # Le type sur la carte, seulement chez les capacites (Actif ou Passif) : ailleurs, la bande le dit.
            carte(img, a, ZONE[0] + n * (cw + ECART), y + 18, cw, bh - 18, titre == "CAPACITÉS", desc, bs, pp, rgb, k_bande)
        y += bh + ENTRE
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
