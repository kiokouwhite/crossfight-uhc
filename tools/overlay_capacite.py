#!/usr/bin/env python3
"""L'overlay d'une capacite, pour presenter une video de jeu : une fenetre 16:9 transparente ou poser la video, dans le
style du HUD de Crossfight.

Usage : python3 tools/overlay_capacite.py <personnage> <capacite> <dossier> [--description "..."] [--apercu image]
        python3 tools/overlay_capacite.py <personnage>          (la liste de ses capacites)

<capacite> : l'identifiant du guide (marth_contre), sans le prefixe du personnage (contre), ou son numero dans la liste.

Ecrit <dossier>/<personnage>_<capacite>.png (1920x1080, la fenetre FENETRE transparente : poser la video dessous, a
cette place) et <dossier>/<personnage>_<capacite>_apercu.png (la meme, une image de jeu dans la fenetre).

  - Le fond : le mur a losanges de l'ecran de selection, le portail des cartes a la couleur du personnage par-dessus.
  - Le cadre du pack d'interface (celui des cartes) autour de la fenetre.
  - En haut, deux cartouches du HUD : le type de la capacite (Actif, Passif, Ultime) a la couleur du personnage, puis
    son nom.
  - A gauche, le logo, puis le signe de la voie du personnage et son nom a la verticale, centres en hauteur ; a droite,
    son illustration, cadree de pres sur son visage.
  - En bas, l'icone de la capacite et sa description, dans des boites du pack (police de texte du HUD). Le
    guide (tools/guide_capacites.json) est ecrit sans accents : la description vient de tools/descriptions.txt quand la
    capacite y est (accentuee), sinon du guide ; --description donne un autre texte.

Nom et couleur : resourcepack/selection.txt ; voie et cadrage : resourcepack/selection_cartes.txt ; capacites :
tools/guide_capacites.json ; illustration, signe de la voie, icones, cadres et polices : prives (jamais versionnes).
"""
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))

W, H = 1920, 1080
FENETRE = (252, 120, 1344, 756)    # la video : x, y, largeur, hauteur (16:9)
K_CADRE, K_UI, K_BOITE, K_NOM = 4, 4, 3, 8   # un pixel du pack = 4 (cadre, cartouches), 3 (boites du bas) ou 8 (nom)
FOND_BOITE, FOND_CARTOUCHE = (163, 167, 194), (76, 104, 133)   # le fond clair des boites et des cartouches du pack


def option(argv, name, default):
    return argv[argv.index(name) + 1] if name in argv else default


def capacites(cid):
    for lic in json.loads((ROOT / "tools" / "guide_capacites.json").read_text(encoding="utf-8")):
        for ch in lic.get("characters", []):
            if ch["id"] == cid:
                return ch
    sys.exit("Personnage inconnu du guide : " + cid)


def descriptions():
    """Les descriptions accentuees (tools/descriptions.txt : "<id>\t<texte>" par ligne)."""
    f = ROOT / "tools" / "descriptions.txt"
    out = {}
    if f.exists():
        for line in f.read_text(encoding="utf-8").splitlines():
            if "\t" in line and not line.startswith("#"):
                k, v = line.split("\t", 1)
                out[k.strip()] = v.strip()
    return out


def gros(img, k):
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


def texte_pixel(text, police, taille, couleur, ombre, k):
    """Un texte d'une ligne dans une police en pixels du HUD, son ombre un pixel plus bas, agrandi k fois."""
    font = ImageFont.truetype(str(police), taille)
    im = Image.new("RGBA", (int(font.getlength(text)) + 8, taille * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    d.text((2, 3), text, font=font, fill=ombre + (255,))
    d.text((2, 2), text, font=font, fill=couleur + (255,))
    return gros(im.crop(im.getbbox()), k)


def paragraphe(text, police, taille, largeur, interligne, couleur, ombre, k):
    """Un paragraphe coupe en lignes de largeur pixels d'image au plus, centrees, agrandi k fois."""
    font = ImageFont.truetype(str(police), taille)
    lignes, ligne = [], ""
    for mot in text.split():
        essai = (ligne + " " + mot).strip()
        if font.getlength(essai) * k <= largeur or not ligne:
            ligne = essai
        else:
            lignes.append(ligne)
            ligne = mot
    lignes.append(ligne)
    lw = max(int(font.getlength(l)) for l in lignes) + 4
    im = Image.new("RGBA", (lw, interligne * len(lignes) + 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    for i, l in enumerate(lignes):
        x = (lw - font.getlength(l)) / 2
        d.text((x, i * interligne + 1), l, font=font, fill=ombre + (255,))
        d.text((x, i * interligne), l, font=font, fill=couleur + (255,))
    return gros(im.crop(im.getbbox()), k)


def recolore(img, de, vers):
    """Les pixels de la couleur de (le fond d'un cartouche) passent a la couleur vers."""
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            if px[x, y][:3] == de and px[x, y][3]:
                px[x, y] = vers + (px[x, y][3],)
    return out


def lueur(img, couleur, rayon):
    """Une lueur floutee de la couleur donnee, de la forme de l'image (a poser dessous)."""
    halo = Image.new("RGBA", (img.width + 4 * rayon, img.height + 4 * rayon), couleur + (0,))
    masque = Image.new("L", halo.size, 0)
    masque.paste(img.getchannel("A"), (2 * rayon, 2 * rayon))
    halo.putalpha(masque.filter(ImageFilter.GaussianBlur(rayon)))
    return halo


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    cid = sys.argv[1]
    ch = capacites(cid)
    if len(sys.argv) < 4:
        for n, a in enumerate(ch["abilities"]):
            print(f"{n}  {a['id']:28} {a['type']:8} {a['name']}")
        return 0
    cle, out = sys.argv[2], Path(sys.argv[3]).resolve()
    a = next((a for n, a in enumerate(ch["abilities"]) if cle in (a["id"], a["id"].removeprefix(cid + "_"), str(n))), None)
    if a is None:
        sys.exit(f"Capacite inconnue : {cle} (python3 tools/overlay_capacite.py {cid} pour la liste)")
    description = option(sys.argv, "--description", descriptions().get(a["id"], a["description"]))
    out.mkdir(parents=True, exist_ok=True)

    import build_selection as bs
    import presentation_perso as pp
    from build_menu_franuka import nine, wide
    name, color = {i: (n, c) for i, n, c in bs.roster()}[cid]
    rgb = bs.hexrgb(color)
    fx0, fy0, fw, fh = FENETRE

    # Le fond : le mur a losanges, le portail des cartes a la couleur du personnage par-dessus, son coeur a droite.
    img = bs.backdrop((W, H)).convert("RGBA")
    sheet, _ = bs.portal(W // 8, H // 8, (W / 8 * 0.86, H / 8 * 0.42), card=False)
    portail = ImageChops.multiply(sheet.crop((0, 0, W // 8, H // 8)), Image.new("RGBA", (W // 8, H // 8), rgb + (255,)))
    portail = portail.resize((W, H), Image.NEAREST)
    portail.putalpha(110)
    img.alpha_composite(portail)

    # L'illustration, a droite : le visage dans la marge, le buste devant le bord du cadre.
    art_src = next(iter(sorted((ROOT / "resourcepack-prive" / "selection" / "art").glob(cid + ".*"))), None)
    voie, cadrage = bs.framings().get(cid, (None, None))
    art_layer = None
    if art_src:
        art = bs.load(art_src)
        vx, vy, zoom = cadrage or bs.auto_framing(art)
        s = zoom * 1.5 * H / art.height
        a_img = art.resize((round(art.width * s), round(art.height * s)), Image.LANCZOS)
        art_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        art_layer.alpha_composite(a_img, (round(1795 - vx * a_img.width), round(330 - vy * a_img.height)))

    # Le cadre du pack autour de la fenetre (son interieur, a l'echelle, tombe pile sur la fenetre).
    lw, lh = fw // K_CADRE, fh // K_CADRE
    essai = bs.ui_frame(lw, lh)
    rangee = [essai.getpixel((x, lh // 2))[3] for x in range(lw)]
    marge = next(x for x, al in enumerate(rangee) if al)
    dedans = next(x for x in range(marge, lw) if not rangee[x])
    cadre = gros(bs.ui_frame(lw + 2 * dedans, lh + 2 * dedans), K_CADRE)
    cx0, cy0 = fx0 - dedans * K_CADRE, fy0 - dedans * K_CADRE
    bord = (dedans - marge) * K_CADRE                     # l'epaisseur visible du cadre
    img.alpha_composite(lueur(cadre, rgb, 18), (cx0 - 36, cy0 - 36))
    if art_layer:
        img.alpha_composite(art_layer)
    img.alpha_composite(cadre, (cx0, cy0))

    # A gauche : le signe de la voie au-dessus du nom a la verticale, le tout centre en hauteur.
    signe = None
    if voie:
        src = next(iter(sorted((ROOT / "resourcepack-prive" / "selection" / "voie").glob(voie + ".*"))), None)
        if src:
            signe = pp.signe_voie(src).resize((132, 132), Image.LANCZOS)
    nom = texte_pixel(name.upper(), bs.UI_TITLE, 17, bs.CREAM, rgb, K_NOM).rotate(90, expand=True)
    ecart = 44
    haut_groupe = nom.height + ((signe.height + ecart) if signe else 0)
    gy, colonne = round(H / 2 - haut_groupe / 2), 126
    if signe:
        img.alpha_composite(lueur(signe, rgb, 14), (colonne - signe.width // 2 - 28, gy - 28))
        img.alpha_composite(signe, (colonne - signe.width // 2, gy))
        gy += signe.height + ecart
    img.alpha_composite(lueur(nom, bs.NAVY_DARK, 10), (colonne - nom.width // 2 - 20, gy - 20))
    img.alpha_composite(nom, (colonne - nom.width // 2, gy))

    # En haut, au-dessus du cadre : le type (a la couleur du personnage), puis le nom.
    banniere = bs.ui_sprite("Title banners", "BannerMedium_01B")
    milieu = pp.interieur(bs, "BannerMedium_01B", K_UI)
    typ, typ_m = ligne_hud(a["type"].upper(), K_UI, bs, pp)
    cap, cap_m = ligne_hud(a["name"].upper(), K_UI, bs, pp)
    t1 = gros(recolore(wide(banniere, max(48, math.ceil((typ.width / K_UI + 36) / 16) * 16)), FOND_CARTOUCHE, rgb), K_UI)
    t2 = gros(wide(banniere, max(48, math.ceil((cap.width / K_UI + 36) / 16) * 16)), K_UI)
    chevauche = 6 * K_UI
    total = t1.width + t2.width - chevauche
    x = round(fx0 + fw / 2 - total / 2)
    # Le bas visible des cartouches (rangee 24 de la banniere) mord un peu sur le cadre, sans toucher la fenetre.
    ty = fy0 - bord + 14 - 24 * K_UI
    centre_y = ty + milieu
    for plaque, texte, tm, px in ((t1, typ, typ_m, x), (t2, cap, cap_m, x + t1.width - chevauche)):
        img.alpha_composite(lueur(plaque, bs.NAVY_DARK, 8), (px - 16, ty - 16))
        img.alpha_composite(plaque, (px, ty))
        img.alpha_composite(texte, (round(px + plaque.width / 2 - texte.width / 2), round(centre_y - tm)))

    # En bas, sous le cadre : l'icone de la capacite, puis sa description, dans des boites du pack au fond bleu nuit.
    boite_px = bs.ui_sprite("Background boxes", "BGbox_01B")
    bm = next(y for y in range(boite_px.height) if boite_px.getpixel((24, y))[3])   # la marge transparente des boites
    haut_vis = fy0 + fh + bord + 14                                               # le haut visible des boites
    bh = (H - 10 - haut_vis) // K_BOITE + 2 * bm
    by = haut_vis - bm * K_BOITE
    cy_b = by + bh * K_BOITE / 2
    icone_src = ROOT / "resourcepack-prive" / "assets" / "crossfight" / "textures" / "item" / "icone" / (a["id"] + ".png")
    dx = fx0
    if icone_src.exists():
        # Une boite carree, l'icone en gros pixels au milieu (la plus grande taille entiere qui y tient).
        boite_i = gros(recolore(nine(boite_px, bh, bh), FOND_BOITE, bs.NAVY), K_BOITE)
        ic = Image.open(icone_src).convert("RGBA")
        k = max(1, (bh - 2 * bm - 10) * K_BOITE // ic.height)
        ic = gros(ic, k)
        img.alpha_composite(lueur(boite_i, bs.NAVY_DARK, 8), (fx0 - bm * K_BOITE - 16, by - 16))
        img.alpha_composite(boite_i, (fx0 - bm * K_BOITE, by))
        img.alpha_composite(lueur(ic, rgb, 10), (round(fx0 - bm * K_BOITE + boite_i.width / 2 - ic.width / 2) - 20, round(cy_b - ic.height / 2) - 20))
        img.alpha_composite(ic, (round(fx0 - bm * K_BOITE + boite_i.width / 2 - ic.width / 2), round(cy_b - ic.height / 2)))
        dx = fx0 + boite_i.width - 2 * bm * K_BOITE + 16
    boite_d = gros(recolore(nine(boite_px, (fx0 + fw - dx) // K_BOITE + 2 * bm, bh), FOND_BOITE, bs.NAVY), K_BOITE)
    img.alpha_composite(lueur(boite_d, bs.NAVY_DARK, 8), (dx - bm * K_BOITE - 16, by - 16))
    img.alpha_composite(boite_d, (dx - bm * K_BOITE, by))
    # La description : en gros pixels x3 si elle tient, sinon x2.
    interieur_l = boite_d.width - 2 * (bm + 8) * K_BOITE
    interieur_h = boite_d.height - 2 * (bm + 6) * K_BOITE
    for k in (K_BOITE, 2):
        desc = paragraphe(description, bs.UI_TEXT, 12, interieur_l, 11, bs.CREAM, bs.NAVY_DARK, k)
        if desc.height <= interieur_h:
            break
    img.alpha_composite(desc, (round(dx - bm * K_BOITE + boite_d.width / 2 - desc.width / 2), round(cy_b - desc.height / 2)))

    # Le logo (un SVG), rendu par Chromium, en haut a gauche.
    logo = logo_png(200)
    if logo:
        img.alpha_composite(logo, (24, 18))

    # La fenetre de la video : transparente.
    trou = Image.new("RGBA", (fw, fh), (0, 0, 0, 0))
    img.paste(trou, (fx0, fy0))
    base = f"{cid}_{a['id'].removeprefix(cid + '_')}"
    img.save(out / f"{base}.png", optimize=True)

    # L'apercu : une image de jeu dans la fenetre.
    apercu_src = option(sys.argv, "--apercu", str(ROOT / "prive" / "skins" / f"{cid}_en_jeu.png"))
    fond = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    if Path(apercu_src).exists():
        jeu = Image.open(apercu_src).convert("RGBA")
        k = max(fw / jeu.width, fh / jeu.height)
        jeu = jeu.resize((math.ceil(jeu.width * k), math.ceil(jeu.height * k)), Image.LANCZOS)
        fond.alpha_composite(jeu.crop(((jeu.width - fw) // 2, (jeu.height - fh) // 2, (jeu.width - fw) // 2 + fw, (jeu.height - fh) // 2 + fh)), (fx0, fy0))
    fond.alpha_composite(img)
    fond.convert("RGB").save(out / f"{base}_apercu.png", optimize=True)
    print(out / f"{base}.png")
    print(f"fenetre de la video : x={fx0} y={fy0}, {fw}x{fh}")


def ligne_hud(text, k, bs, pp):
    """Une ligne dans la police de titre du HUD (creme, son ombre dessous), et le milieu du corps des capitales."""
    morceaux, w, h, m = pp.lettres(text, bs.UI_TITLE, 17, k, bs)
    im = Image.new("RGBA", (w + 2 * k, h), (0, 0, 0, 0))
    for x, piece in morceaux:
        im.alpha_composite(piece, (x, 0))
    return im, m


def logo_png(largeur):
    """Le logo Crossfight UHC (tools/logo/crossfight_uhc.svg) en PNG transparent, rendu par Chromium."""
    import subprocess
    import tempfile
    svg = ROOT / "tools" / "logo" / "crossfight_uhc.svg"
    with tempfile.TemporaryDirectory() as tmp:
        png, page = Path(tmp) / "logo.png", Path(tmp) / "logo.html"
        page.write_text(f'<style>html,body{{margin:0;background:transparent}}</style><img id="l" src="file://{svg}" style="width:{largeur}px">')
        script = f"""
const {{ chromium }} = require('playwright');
(async () => {{
  const b = await chromium.launch();
  const p = await b.newPage({{ viewport: {{ width: {largeur + 20}, height: {largeur + 20} }} }});
  await p.goto('file://{page}');
  await p.waitForTimeout(200);
  await (await p.$('#l')).screenshot({{ path: '{png}', omitBackground: true }});
  await b.close();
}})();"""
        r = subprocess.run(["node", "-e", script], cwd="/tmp", capture_output=True, text=True)
        if r.returncode or not png.exists():
            print("logo non rendu :", r.stderr[-300:])
            return None
        return Image.open(png).convert("RGBA")


if __name__ == "__main__":
    sys.exit(main())
