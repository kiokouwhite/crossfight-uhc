#!/usr/bin/env python3
"""La video de presentation d'un personnage : il monte du bas de l'ecran, et son nom s'ecrit en bas.

Usage : python3 tools/presentation_perso.py <personnage> <dossier> [--duree 6] [--graine N] [--surnom "..."] [--bande]

Le personnage, son nom et sa couleur viennent de resourcepack/selection.txt ; sa voie et le cadrage de son visage, de
resourcepack/selection_cartes.txt (comme sa carte de selection) ; son surnom, de tools/surnoms.txt ; son illustration et
le signe de sa voie, de resourcepack-prive/selection/{art,voie}/ (fournis, jamais versionnes) ; le cartouche du nom et
ses polices, du pack d'interface (resourcepack-prive/ui-franuka, achete). La video reste donc dans le dossier donne, a
prendre sous prive/.

Toute la scene est penchee (PENTE degres) : un plateau plus grand que l'ecran, tourne autour de son centre.
  - Le fond : le portail anime des cartes de selection (build_selection.portal, en gros pixels, a la couleur du
    personnage) sous le fond algorithmique "Concorde stellaire" (tools/fond_concorde.js, p5.js ; sa philosophie :
    tools/fond_concorde.md) : des milliers de particules montent en colonne avec le personnage, une onde de choc part a
    son arrivee et remet leurs phases a zero, puis elles s'accordent (modele de Kuramoto) en une pulsation commune et se
    rangent sur trois orbites d'un halo, ponctue du signe de sa voie qui tourne sur lui-meme.
  - Le personnage, cadre sur son visage (plus grand que sur sa carte : CARTE), surgit du bas de l'ecran en silhouette
    blanche et s'arrete net ; la colonne de particules monte encore un instant, puis l'explosion (EXPLOSION secondes :
    eclair, secousse, onde de choc) lui rend ses couleurs, et il respire doucement.
  - En bas, le cartouche du HUD se deplie et le nom y tombe lettre par lettre (police de titre du HUD), puis le surnom
    apparait dans un cartouche plus petit dessous (meme police) ; les deux flottent (ombre, lueur, leger balancement).
    --bande : la bande de nom des cartes traverse l'ecran sous les cartouches ; la scene et la video s'appellent alors
    <personnage>_bande.
  - Le logo Crossfight UHC est en haut a gauche, droit (hors de la scene penchee).

Ecrit <dossier>/<personnage>.html (la scene, a ouvrir dans un navigateur), <dossier>/<personnage>.mp4 (1920x1080,
60 images par seconde ; rendu par tools/rendu_video.js : Playwright, Chromium et ffmpeg) et <dossier>/fond_concorde.html
(la visionneuse du fond, pour explorer les graines et les reglages ; la graine vient du personnage, --graine pour en
essayer une autre). p5.js est pris une fois sur le registre npm dans build/vendor/.
"""
import math
import shutil
import subprocess
import sys
import zlib
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
P5 = ROOT / "build" / "vendor" / "p5-1.7.0.min.js"
SURNOMS = ROOT / "tools" / "surnoms.txt"

W, H = 1920, 1080          # l'ecran
PENTE = -6                 # la scene penchee, en degres (negatif : elle monte vers la droite)
PW, PH = 2200, 1400        # le plateau tourne, plus grand que l'ecran pour en couvrir les coins
OX, OY = (W - PW) // 2, (H - PH) // 2
PIXEL = 8                  # un pixel de dessin du portail = 8 pixels du plateau
VISAGE = (0.5, 0.28)       # ou se place le visage du personnage, en fraction de l'ecran
CARTE = 1.5                # l'illustration, 1,5 fois plus grande que sur sa carte (a hauteur de carte = hauteur d'ecran)
NOM_Y = 0.79               # le milieu du cartouche du nom, en fraction de la hauteur de l'ecran
EXPLOSION = 1.7            # l'explosion qui rend ses couleurs au personnage (il s'est pose a 1,2 s)
K_NOM, K_SURNOM = 8, 4     # un pixel du HUD = 8 (nom) ou 4 (surnom) pixels a l'ecran


def p5():
    """p5.js (bibliotheque libre, LGPL), pris une fois sur le registre npm et garde dans build/vendor/."""
    if not P5.exists():
        paquet = P5.parent / "p5-1.7.0"
        shutil.rmtree(paquet, ignore_errors=True)
        paquet.mkdir(parents=True)
        subprocess.run(["npm", "pack", "p5@1.7.0", "--silent"], check=True, cwd=paquet, stdout=subprocess.DEVNULL)
        subprocess.run(["tar", "-xzf", "p5-1.7.0.tgz", "package/lib/p5.min.js"], check=True, cwd=paquet)
        shutil.copy(paquet / "package" / "lib" / "p5.min.js", P5)
        shutil.rmtree(paquet)
    return P5


def option(argv, name, default):
    return argv[argv.index(name) + 1] if name in argv else default


def surnom_de(cid):
    """Le surnom du personnage (tools/surnoms.txt : "<id> <surnom>" par ligne, # pour un commentaire)."""
    if SURNOMS.exists():
        for line in SURNOMS.read_text(encoding="utf-8").splitlines():
            parts = line.split(None, 1)
            if len(parts) == 2 and parts[0] == cid and not line.startswith("#"):
                return parts[1].strip()
    return ""


def plateau(fx, fy):
    """Un point de l'ecran (en fractions) dans le plateau, avant sa rotation."""
    return PW / 2 + (fx - 0.5) * W, PH / 2 + (fy - 0.5) * H


def lettres(text, police, taille, k, bs):
    """Le texte dans une police en pixels du HUD, une image par lettre (creme, son ombre un pixel plus bas, comme sur
    les cartes), agrandie k fois en gros pixels nets. Rend [(x, image)], la largeur et la hauteur du tout."""
    font = ImageFont.truetype(str(police), taille)

    def dessine(s):
        im = Image.new("RGBA", (int(font.getlength(s)) + 8, taille * 2), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        d.fontmode = "1"
        d.text((2, 3), s, font=font, fill=bs.NAVY_DARK + (255,))
        d.text((2, 2), s, font=font, fill=bs.CREAM + (255,))
        return im

    box = dessine(text).getbbox()
    out = []
    for i, c in enumerate(text):
        im = dessine(c)
        b = im.getbbox()
        if c.isspace() or not b:
            continue
        piece = im.crop((b[0], box[1], b[2], box[3]))
        x = round(font.getlength(text[:i])) + b[0] - box[0]
        out.append((x * k, piece.resize((piece.width * k, piece.height * k), Image.NEAREST)))
    return out, (box[2] - box[0]) * k, (box[3] - box[1]) * k


def cartouche(bs, nom, largeur, marge, k):
    """Un cartouche du HUD (une banniere de trois tuiles) allonge pour tenir largeur pixels de texte, agrandi k fois."""
    from build_menu_franuka import wide
    banner = bs.ui_sprite("Title banners", nom)
    w = max(48, math.ceil((largeur / k + 2 * marge) / 16) * 16)
    img = wide(banner, w)
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


def signe_voie(src):
    """Le signe de la voie, sans son disque sombre : blanc, sa transparence suit la clarte du dessin."""
    img = Image.open(src).convert("RGBA").resize((256, 256), Image.LANCZOS)
    lum = img.convert("L").point(lambda v: max(0, min(255, round((v - 60) * 255 / 175))))
    alpha = Image.eval(img.getchannel("A"), lambda a: a)
    from PIL import ImageChops
    out = Image.new("RGBA", img.size, (255, 255, 255, 0))
    out.putalpha(ImageChops.multiply(lum, alpha))
    return out


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 1
    cid, out = sys.argv[1], Path(sys.argv[2]).resolve()
    D = float(option(sys.argv, "--duree", "6"))
    graine = int(option(sys.argv, "--graine", str(zlib.crc32(cid.encode()) % 999999 + 1)))
    surnom = option(sys.argv, "--surnom", surnom_de(cid))
    flottant = "--bande" not in sys.argv
    sortie = cid + ("" if flottant else "_bande")
    out.mkdir(parents=True, exist_ok=True)
    import build_selection as bs
    names = {i: (n, c) for i, n, c in bs.roster()}
    name, color = names[cid]
    rgb = bs.hexrgb(color)
    art_src = next(iter(sorted((ROOT / "resourcepack-prive" / "selection" / "art").glob(cid + ".*"))), None)
    if art_src is None:
        sys.exit("Pas d'illustration pour " + cid)
    art = bs.load(art_src)
    voie, cadrage = bs.framings().get(cid, (None, None))
    fx, fy, zoom = cadrage or bs.auto_framing(art)

    # Le personnage : recadre sur son dessin, le visage a VISAGE, CARTE fois plus grand que sur sa carte.
    box = art.getbbox()
    s = zoom * CARTE * H / art.height
    vx, vy = fx * art.width - box[0], fy * art.height - box[1]
    perso = art.crop(box)
    perso = perso.resize((round(perso.width * s), round(perso.height * s)), Image.LANCZOS)
    perso.save(out / f"{cid}.png")
    # Sa silhouette blanche, avant l'explosion.
    blanc = Image.new("RGBA", perso.size, (255, 255, 255, 255))
    blanc.putalpha(perso.getchannel("A"))
    blanc.save(out / f"{cid}_blanc.png")
    cx, cy = plateau(*VISAGE)
    p_left, p_top = round(cx - vx * s), round(cy - vy * s)

    # Le fond des cartes : le portail, a la taille du plateau, son coeur derriere le buste, a la couleur du personnage.
    sheet, meta = bs.portal(PW // PIXEL, PH // PIXEL, (PW / PIXEL / 2, (cy + 0.14 * H) / PIXEL), card=False)
    teinte = Image.new("RGBA", sheet.size, rgb + (255,))
    from PIL import ImageChops
    ImageChops.multiply(sheet, teinte).save(out / "portail.png")
    images = sheet.height // (PH // PIXEL)

    # Le signe de la voie, a la place des etoiles du halo.
    signe = None
    if voie:
        src = next(iter(sorted((ROOT / "resourcepack-prive" / "selection" / "voie").glob(voie + ".*"))), None)
        if src:
            signe_voie(src).save(out / "signe.png")
            signe = "signe.png"

    # Le nom et le surnom, dans les cartouches et les polices du HUD.
    nom_lettres, nom_w, nom_h = lettres(name.upper(), bs.UI_TITLE, 17, K_NOM, bs)
    plaque = cartouche(bs, "BannerMedium_01B", nom_w, 20, K_NOM)
    plaque.save(out / "plaque_nom.png")
    for n, (x, im) in enumerate(nom_lettres):
        im.save(out / f"nom_{n}.png")
    ny = plateau(0.5, NOM_Y)[1]
    plaque_html = (f'<img class="plaque" src="plaque_nom.png" style="left:{round(PW / 2 - plaque.width / 2)}px;'
                   f'top:{round(ny - plaque.height / 2)}px">')
    x0, y0 = PW / 2 - nom_w / 2, ny - nom_h / 2 - K_NOM
    plaque_html += "".join(
        f'<img class="lettre" src="nom_{n}.png" style="left:{round(x0 + x)}px;top:{round(y0)}px;animation-delay:{EXPLOSION + 0.3 + n * 0.08:.2f}s">'
        for n, (x, im) in enumerate(nom_lettres))
    bande_y, bande_h = ny - 0.42 * plaque.height, round(0.84 * plaque.height)
    if surnom:
        sur_lettres, sur_w, sur_h = lettres(surnom, bs.UI_TITLE, 17, K_SURNOM, bs)
        petite = cartouche(bs, "BannerMedium_01B", sur_w, 16, K_SURNOM)
        petite.save(out / "plaque_surnom.png")
        sy = ny + plaque.height / 2 + petite.height * 0.12
        mot = Image.new("RGBA", (sur_w + 4 * K_SURNOM, sur_h), (0, 0, 0, 0))
        for x, im in sur_lettres:
            mot.alpha_composite(im, (x, 0))
        mot.save(out / "surnom.png")
        plaque_html += (f'<div class="surnom" style="left:{round(PW / 2 - petite.width / 2)}px;top:{round(sy - petite.height / 2)}px;'
                        f'width:{petite.width}px;height:{petite.height}px"><img src="plaque_surnom.png">'
                        f'<img src="surnom.png" style="left:{round(petite.width / 2 - sur_w / 2)}px;top:{round(petite.height / 2 - sur_h / 2 - K_SURNOM)}px"></div>')

    # Le voile sombre du bas : sous les cartouches flottants, il ne descend que sous le nom pour laisser voir le buste.
    voile = (ny - 60, 320, 560) if flottant else (bande_y - 260, 260, 420)
    logo = (ROOT / "tools" / "logo" / "crossfight_uhc.svg").read_text(encoding="utf-8")
    (out / "logo.svg").write_text(logo, encoding="utf-8")
    pc = lambda t: f"{t / D * 100:.2f}%"
    navy, sombre = "rgb(38,41,74)", "rgb(24,26,48)"
    html = f"""<!doctype html><html><head><meta charset="utf-8"><style>
html, body {{ margin: 0; width: {W}px; height: {H}px; overflow: hidden; background: #0b0d18; }}
#scene {{ position: absolute; inset: 0; overflow: hidden; animation: secousse {D}s linear both; }}
#plateau {{ position: absolute; left: {OX}px; top: {OY}px; width: {PW}px; height: {PH}px; transform: rotate({PENTE}deg); }}
#fond, #fond canvas {{ position: absolute; inset: 0; }}
.monte {{ position: absolute; left: {p_left}px; top: {p_top}px; width: {perso.width}px; height: {perso.height}px; animation: monte {D}s linear both; }}
@keyframes monte {{
  0%, {pc(0.3)} {{ transform: translateY({PH}px); filter: blur(10px); animation-timing-function: cubic-bezier(.15,.85,.25,1); }}
  {pc(1.2)} {{ transform: translateY(-34px); filter: blur(0); }}
  {pc(1.38)} {{ transform: translateY(8px); filter: none; }}
  {pc(1.55)}, 100% {{ transform: none; filter: none; }} }}
.respire {{ position: absolute; inset: 0; transform-origin: {round(vx * s)}px {round(vy * s)}px; animation: respire {D}s ease-in-out both; }}
@keyframes respire {{ 0%, {pc(1.2)} {{ transform: none; }} {pc(EXPLOSION - 0.02)} {{ transform: scale(1.012); }}
  {pc(EXPLOSION + 0.08)} {{ transform: scale(1.03); }} {pc(EXPLOSION + 0.35)} {{ transform: none; animation-timing-function: ease-in-out; }} 100% {{ transform: scale(1.04); }} }}
.perso, .blanc {{ position: absolute; inset: 0; width: 100%; height: 100%; display: block; }}
.perso {{ animation: couleurs {D}s linear both; }}
@keyframes couleurs {{ 0%, {pc(EXPLOSION)} {{ filter: brightness(2.2); }} {pc(EXPLOSION + 0.45)}, 100% {{ filter: brightness(1); }} }}
.blanc {{ filter: drop-shadow(0 0 14px #fff) drop-shadow(0 0 40px {color}); animation: blanc {D}s linear both; }}
@keyframes blanc {{ 0%, {pc(EXPLOSION)} {{ opacity: 1; }} {pc(EXPLOSION + 0.28)}, 100% {{ opacity: 0; }} }}
.voile {{ position: absolute; left: 0; right: 0; top: {round(voile[0])}px; height: {PH}px;
  background: linear-gradient(to bottom, transparent, #0b0d18aa {voile[1]}px, #0b0d18 {voile[2]}px); }}
.bande {{ position: absolute; left: 0; width: {PW}px; top: {round(bande_y)}px; height: {bande_h}px; transform-origin: 0 50%;
  background: linear-gradient(to bottom, {color} 0 {2 * K_NOM}px, {sombre} {2 * K_NOM}px {3 * K_NOM}px, {navy} {3 * K_NOM}px 64%,
    rgb(84,90,140) 64% calc(64% + {K_NOM}px), {sombre} calc(64% + {K_NOM}px));
  box-shadow: 0 0 40px {color}66; animation: bande {D}s linear both; }}
@keyframes bande {{ 0%, {pc(EXPLOSION + 0.02)} {{ transform: scaleX(0); animation-timing-function: cubic-bezier(.6,0,1,1); }} {pc(EXPLOSION + 0.25)}, 100% {{ transform: none; }} }}
.plaque {{ position: absolute; animation: plaque {D}s linear both; }}
@keyframes plaque {{ 0%, {pc(EXPLOSION + 0.1)} {{ transform: scaleX(0); opacity: 0; }} {pc(EXPLOSION + 0.22)} {{ transform: scaleX(1.12); opacity: 1; }} {pc(EXPLOSION + 0.32)}, 100% {{ transform: none; opacity: 1; }} }}
.lettre {{ position: absolute; opacity: 0; animation: lettre 0.42s cubic-bezier(.2,.9,.3,1.3) both; }}
@keyframes lettre {{ from {{ opacity: 0; transform: translateY(-110px) scale(1.5); filter: blur(8px); }} to {{ opacity: 1; transform: none; filter: none; }} }}
.surnom {{ position: absolute; animation: surnom {D}s ease-out both; }}
.surnom img {{ position: absolute; left: 0; top: 0; }}
@keyframes surnom {{ 0%, {pc(EXPLOSION + 0.85)} {{ opacity: 0; transform: translateY(-30px) scaleX(.4); }} {pc(EXPLOSION + 1.2)} {{ opacity: 1; transform: scaleX(1.06); }} {pc(EXPLOSION + 1.3)}, 100% {{ opacity: 1; transform: none; }} }}
.flotte {{ position: absolute; inset: 0; animation: flotte 1.7s ease-in-out infinite alternate; }}
@keyframes flotte {{ from {{ transform: translateY(-6px); }} to {{ transform: translateY(6px); }} }}
.flotte .plaque, .flotte .surnom img:first-child {{ filter: drop-shadow(0 {2 * K_NOM}px 0 rgba(8,9,18,.45)) drop-shadow(0 0 26px {color}aa); }}
.logo {{ position: absolute; left: 56px; top: 44px; width: 250px; animation: logo {D}s ease-out both; }}
@keyframes logo {{ 0%, {pc(0.5)} {{ opacity: 0; transform: translateX(-30px); }} {pc(1.1)}, 100% {{ opacity: .95; transform: none; }} }}
.flash {{ position: absolute; inset: 0; background: radial-gradient(ellipse 55% 65% at 50% 55%, #fff 0%, {color}aa 40%, transparent 75%); mix-blend-mode: screen; opacity: 0; animation: flash {D}s linear both; }}
@keyframes flash {{ 0%, {pc(EXPLOSION - 0.01)} {{ opacity: 0; }} {pc(EXPLOSION)} {{ opacity: .6; }} {pc(EXPLOSION + 0.3)}, 100% {{ opacity: 0; }} }}
@keyframes secousse {{ 0%, {pc(1.19)} {{ transform: none; }} {pc(1.21)} {{ transform: translate(0, 7px); }} {pc(1.26)} {{ transform: none; }}
  {pc(EXPLOSION - 0.01)} {{ transform: none; }} {pc(EXPLOSION + 0.01)} {{ transform: translate(0, 16px); }} {pc(EXPLOSION + 0.05)} {{ transform: translate(-10px, -10px); }}
  {pc(EXPLOSION + 0.1)} {{ transform: translate(6px, 6px); }} {pc(EXPLOSION + 0.16)} {{ transform: translate(-3px, -2px); }} {pc(EXPLOSION + 0.22)}, 100% {{ transform: none; }} }}
</style></head><body><div id="scene"><div id="plateau">
<div id="fond"></div>
<div class="monte"><div class="respire"><img class="perso" src="{cid}.png"><img class="blanc" src="{cid}_blanc.png"></div></div>
<div class="voile"></div>
{'<div class="flotte">' + plaque_html + '</div>' if flottant else '<div class="bande"></div>' + plaque_html}
</div>
<img class="logo" src="logo.svg">
<div class="flash"></div>
</div>
<script src="p5.min.js"></script><script src="fond_concorde.js"></script>
<script>
window.pret = false;
// Les images sont chargees a la main : le chargeur de p5 passe par fetch, que Chromium refuse sur file://.
const charge = src => new Promise((ok, ko) => {{ const i = new Image(); i.onload = () => ok(i); i.onerror = ko; i.src = src; }});
Promise.all([charge('portail.png'), {f"charge('{signe}')" if signe else "null"}]).then(([portail, signe]) => new p5(p => {{
  p.setup = () => {{
    p.pixelDensity(1);
    p.createCanvas({PW}, {PH}).parent('fond');
    p.noLoop();
    const c = new Concorde(p, {{
      graine: {graine}, couleur: '{color}', particules: 3400, eclats: 8, lueur: 0.5, impact: {EXPLOSION},
      centre: [0.5, {(cy + 0.08 * H) / PH:.3f}], demiAxes: [0.37, 0.33], choc: [0.5, {(cy + 0.12 * H) / PH:.3f}],
      portail: {{ image: portail, images: {images}, pas: 0.1, opacite: 0.9 }}, signe, tailleSigne: 130 }});
    window.avancer = t => {{ c.avancer(t); c.dessiner(); }};
    window.avancer(0);
    window.pret = true;
  }};
}}));
</script></body></html>"""
    page = out / f"{sortie}.html"
    page.write_text(html, encoding="utf-8")
    shutil.copy(p5(), out / "p5.min.js")
    algo = (ROOT / "tools" / "fond_concorde.js").read_text(encoding="utf-8")
    (out / "fond_concorde.js").write_text(algo, encoding="utf-8")
    # La visionneuse du fond, autonome (l'algorithme inclus), aux couleurs et a la graine du personnage.
    vue = (ROOT / "tools" / "fond_concorde.html").read_text(encoding="utf-8")
    vue = vue.replace('<script src="fond_concorde.js"></script>', "<script>\n" + algo + "\n</script>")
    vue = vue.replace("seed: 12345,", f"seed: {graine},").replace('value="12345"', f'value="{graine}"')
    vue = vue.replace("'#4a4fc4'", f"'{color}'").replace('"#4a4fc4"', f'"{color}"').replace(">#4a4fc4<", f">{color}<")
    (out / "fond_concorde.html").write_text(vue, encoding="utf-8")
    subprocess.run(["node", str(ROOT / "tools" / "rendu_video.js"), str(page), str(out / f"{sortie}.mp4"), str(D)], check=True, cwd="/tmp")
    print(out / f"{sortie}.mp4")


if __name__ == "__main__":
    sys.exit(main())
