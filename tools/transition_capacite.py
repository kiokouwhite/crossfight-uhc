#!/usr/bin/env python3
"""La transition qui annonce une capacite, entre deux videos de jeu, a fond transparent.

Usage : python3 tools/transition_capacite.py <personnage> <capacite> <dossier> [--categorie "..."] [--verifier 0.3,1.5]

<capacite> comme pour tools/overlay_capacite.py (marth_contre, contre ou son numero). La categorie vient du guide : ARME
pour la capacite de l'arme, ULTIME, PASSIF, sinon CAPACITE ; --categorie en donne une autre.

Deroule (DUREE secondes) :
  - le signe de la voie du personnage surgit en grand au centre, en tournant, dans un eclair et une gerbe d'eclats ;
  - des traits en biais a la couleur du personnage balaient l'ecran de gauche a droite et le couvrent ;
  - derriere eux, la carte de titre : le fond des overlays (mur a losanges, portail des cartes), le signe en filigrane,
    le cadre du pack ; la categorie arrive de la gauche dans un cartouche a la couleur du personnage, le nom de la
    capacite de la droite dans un grand cartouche bleu nuit, tous deux avec des trainees ; ils derivent doucement ;
  - la carte se decoupe en tranches qui filent de part et d'autre, et laisse voir la video suivante.

Ecrit dans <dossier> : <personnage>_<capacite>_transition.webm (VP9 a transparence), .mov (ProRes 4444 a transparence,
pour les logiciels de montage) et _transition_apercu.mp4 (posee sur l'apercu de l'overlay de la capacite, s'il existe).
Les images et les polices viennent du pack prive : les videos restent hors du depot (sous prive/).
"""
import math
import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))

W, H = 1920, 1080
DUREE, IPS = 3.4, 60
K_CAT, K_NOM = 5, 8            # un pixel du pack = 5 (cartouche de la categorie) ou 8 (cartouche du nom) pixels
TRANCHES = 8                   # la carte se decoupe en autant de tranches a la sortie
TRAITS = 7                     # les traits en biais du balayage


def option(argv, name, default):
    return argv[argv.index(name) + 1] if name in argv else default


def categorie_de(a):
    if a.get("weapon"):
        return "ARME"
    return {"Ultime": "ULTIME", "Passif": "PASSIF"}.get(a["type"], "CAPACITÉ")


def mele(c1, c2, t):
    return tuple(round(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        return 1
    import build_selection as bs
    import overlay_capacite as oc
    import presentation_perso as pp
    from build_menu_franuka import wide

    cid, cle, out = sys.argv[1], sys.argv[2], Path(sys.argv[3]).resolve()
    ch = oc.capacites(cid)
    a = next((a for n, a in enumerate(ch["abilities"]) if cle in (a["id"], a["id"].removeprefix(cid + "_"), str(n))), None)
    if a is None:
        sys.exit(f"Capacite inconnue : {cle} (python3 tools/overlay_capacite.py {cid} pour la liste)")
    categorie = option(sys.argv, "--categorie", categorie_de(a))
    base = f"{cid}_{a['id'].removeprefix(cid + '_')}"
    tr = out / f"{base}_transition"
    tr.mkdir(parents=True, exist_ok=True)
    name, color = {i: (n, c) for i, n, c in bs.roster()}[cid]
    rgb = bs.hexrgb(color)

    # La carte de titre : le fond des overlays, le cadre du pack.
    fond = bs.backdrop((W, H)).convert("RGBA")
    sheet, _ = bs.portal(W // 8, H // 8, (W / 16, H / 16), card=False)
    portail = ImageChops.multiply(sheet.crop((0, 0, W // 8, H // 8)), Image.new("RGBA", (W // 8, H // 8), rgb + (255,)))
    portail = portail.resize((W, H), Image.NEAREST)
    portail.putalpha(120)
    fond.alpha_composite(portail)
    fond.convert("RGB").save(tr / "fond.png")
    lw, lh = (W - 96) // 4, (H - 96) // 4
    oc.gros(bs.ui_frame(lw, lh), 4).save(tr / "cadre.png")

    # Le signe de la voie (l'embleme du debut et le filigrane de la carte).
    voie, _ = bs.framings().get(cid, (None, None))
    signe_src = next(iter(sorted((ROOT / "resourcepack-prive" / "selection" / "voie").glob((voie or "_") + ".*"))), None)
    sombre = mele(rgb, bs.NAVY_DARK, 0.45)
    clair = mele(rgb, (255, 255, 255), 0.35)
    if signe_src:
        signe = pp.signe_voie(signe_src).resize((760, 760), Image.LANCZOS)
        signe.save(tr / "signe.png")
        # L'embleme : le signe plein, a la couleur du personnage (ses parties claires plus claires), cerne de bleu nuit.
        al = signe.getchannel("A")
        plein = al.point(lambda v: 255 if v > 50 else round(v * 5.1))
        haut = al.point(lambda v: 255 if v > 150 else 0)
        teinte = Image.composite(Image.new("RGBA", signe.size, clair + (255,)), Image.new("RGBA", signe.size, rgb + (255,)), haut)
        teinte.putalpha(plein)
        emb = Image.new("RGBA", signe.size, bs.NAVY_DARK + (0,))
        emb.putalpha(plein.filter(ImageFilter.MaxFilter(15)))
        emb.alpha_composite(teinte)
        emb.save(tr / "embleme.png")

    # Les deux cartouches, le texte deja pose dedans, et leur lueur sombre.
    banniere = bs.ui_sprite("Title banners", "BannerMedium_01B")

    def cartouche(texte, k, largeur, teinte):
        t, tm = oc.ligne_hud(texte, k, bs, pp)
        lb = max(48, math.ceil(max(largeur, t.width + 36 * k) / k / 16) * 16)
        b = wide(banniere, lb)
        if teinte:
            b = oc.recolore(b, oc.FOND_CARTOUCHE, rgb)
        b = oc.gros(b, k)
        milieu = pp.interieur(bs, "BannerMedium_01B", k)
        b.alpha_composite(t, (round(b.width / 2 - t.width / 2), round(milieu - tm)))
        halo = oc.lueur(b, bs.NAVY_DARK, 14)
        halo.alpha_composite(b, (28, 28))
        return halo

    cat = cartouche(categorie.upper(), K_CAT, 1400, True)
    nom = cartouche(a["name"].upper(), K_NOM, 1560, False)
    cat.save(tr / "categorie.png")
    nom.save(tr / "nom.png")

    # Les places : le nom un peu au-dessus du milieu, la categorie posee sur lui.
    ny = round(H / 2 + 30 - nom.height / 2)
    cy = round(ny + 28 + 5 * K_NOM - (24 * K_CAT) - 28 + 2 * K_CAT)
    pc = lambda t: f"{t / DUREE * 100:.3f}%"
    couleurs = [rgb, sombre, clair, rgb, sombre, rgb, clair]

    css = [f"""
html, body {{ margin: 0; width: {W}px; height: {H}px; overflow: hidden; background: transparent; }}
.embleme {{ position: absolute; left: {W // 2 - 380}px; top: {H // 2 - 380}px; width: 760px; height: 760px; animation: embleme {DUREE}s linear both; }}
.embleme img {{ width: 100%; height: 100%; filter: drop-shadow(0 0 6px rgb(255,240,214)) drop-shadow(0 0 40px {color}); }}
@keyframes embleme {{
  0% {{ opacity: 0; transform: scale(2.6) rotate(-150deg); filter: blur(16px); animation-timing-function: cubic-bezier(.2,.8,.3,1); }}
  {pc(0.28)} {{ opacity: 1; transform: scale(.9) rotate(10deg); filter: blur(0); }}
  {pc(0.36)} {{ transform: scale(1) rotate(0deg); animation-timing-function: ease-in; }}
  {pc(0.74)} {{ opacity: 1; transform: scale(1.3) rotate(50deg); }}
  {pc(0.76)}, 100% {{ opacity: 0; transform: scale(1.3) rotate(50deg); }} }}
.onde {{ position: absolute; left: {W // 2 - 110}px; top: {H // 2 - 110}px; width: 196px; height: 196px; border-radius: 50%;
  border: 12px solid rgb(255,240,214); box-shadow: 0 0 30px {color}, inset 0 0 30px {color}; animation: onde {DUREE}s linear both; }}
@keyframes onde {{ 0%, {pc(0.16)} {{ opacity: 0; transform: scale(.3); }} {pc(0.18)} {{ opacity: 1; transform: scale(.5); animation-timing-function: cubic-bezier(.1,.7,.3,1); }}
  {pc(0.6)}, 100% {{ opacity: 0; transform: scale(7); }} }}
.eclat {{ position: absolute; left: {W // 2 - 9}px; top: {H // 2 - 9}px; width: 18px; height: 18px; background: rgb(255,240,214);
  box-shadow: 0 0 12px {color}; opacity: 0; }}
.trait {{ position: absolute; left: 0; width: 2600px; }}
.carte {{ position: absolute; inset: 0; }}
.carte > img {{ position: absolute; }}
.filigrane {{ left: {W // 2 - 520}px; top: {H // 2 - 520}px; width: 1040px; height: 1040px; opacity: .12; animation: filigrane {DUREE}s linear both; }}
@keyframes filigrane {{ from {{ transform: rotate(-12deg) scale(1.08); }} to {{ transform: rotate(10deg) scale(1); }} }}
.cadre {{ left: 48px; top: 48px; opacity: .6; }}
.cat, .nom {{ will-change: transform; }}
.cat {{ left: {round(W / 2 - cat.width / 2)}px; top: {cy}px; animation: cat {DUREE}s linear both; }}
.nom {{ left: {round(W / 2 - nom.width / 2)}px; top: {ny}px; animation: nom {DUREE}s linear both; }}
.ombre1 {{ opacity: .45; animation-delay: .045s !important; }}
.ombre2 {{ opacity: .22; animation-delay: .09s !important; }}
@keyframes cat {{ 0%, {pc(0.95)} {{ transform: translateX(-2300px); animation-timing-function: cubic-bezier(.15,.85,.3,1.15); }}
  {pc(1.22)} {{ transform: translateX(26px); }} {pc(1.32)} {{ transform: translateX(0); animation-timing-function: linear; }} 100% {{ transform: translateX(-16px); }} }}
@keyframes nom {{ 0%, {pc(1.04)} {{ transform: translateX(2300px); animation-timing-function: cubic-bezier(.15,.85,.3,1.15); }}
  {pc(1.31)} {{ transform: translateX(-30px); }} {pc(1.41)} {{ transform: translateX(0); animation-timing-function: linear; }} 100% {{ transform: translateX(16px); }} }}
"""]
    # La gerbe d'eclats : des losanges qui partent du centre.
    eclats = []
    for i in range(16):
        ang = 2 * math.pi * i / 16 + (0.2 if i % 2 else 0)
        d = 380 + 160 * (i % 3)
        dx, dy = round(math.cos(ang) * d), round(math.sin(ang) * d)
        css.append(f"""@keyframes eclat{i} {{ 0%, {pc(0.17)} {{ opacity: 0; transform: translate(0, 0) rotate(45deg) scale(1); }}
  {pc(0.19)} {{ opacity: 1; animation-timing-function: cubic-bezier(.1,.7,.3,1); }}
  {pc(0.62)}, 100% {{ opacity: 0; transform: translate({dx}px, {dy}px) rotate(45deg) scale(.4); }} }}""")
        eclats.append(f'<i class="eclat" style="animation: eclat{i} {DUREE}s linear both"></i>')
    # Les traits en biais : ils entrent par la gauche, couvrent l'ecran, puis sortent par la droite.
    traits = []
    bande = H / TRAITS
    for i in range(TRAITS):
        t0, t1 = 0.40 + i * 0.035, 0.80 + i * 0.03
        c = couleurs[i % len(couleurs)]
        css.append(f"""@keyframes trait{i} {{ 0%, {pc(t0)} {{ transform: translateX(-2900px) skewX(-28deg); animation-timing-function: cubic-bezier(.5,0,.3,1); }}
  {pc(t0 + 0.24)}, {pc(t1)} {{ transform: translateX(-340px) skewX(-28deg); animation-timing-function: cubic-bezier(.6,0,.8,.4); }}
  {pc(t1 + 0.26)}, 100% {{ transform: translateX(2300px) skewX(-28deg); }} }}""")
        traits.append(f'<div class="trait" style="top:{round(i * bande - 6)}px;height:{round(bande + 12)}px;'
                      f'background:linear-gradient(90deg, rgb{c} 0 96%, rgb(255,240,214) 96% 100%);animation: trait{i} {DUREE}s linear both"></div>')
    # La carte, en tranches : chacune la montre entiere a travers sa bande, puis file d'un cote ou de l'autre.
    interieur = ('<img src="fond.png" style="left:0;top:0">'
                 + ('<img class="filigrane" src="signe.png">' if signe_src else '')
                 + '<img class="cadre" src="cadre.png">'
                 + '<img class="cat ombre2" src="categorie.png"><img class="cat ombre1" src="categorie.png"><img class="cat" src="categorie.png">'
                 + '<img class="nom ombre2" src="nom.png"><img class="nom ombre1" src="nom.png"><img class="nom" src="nom.png">')
    tranches = []
    for i in range(TRANCHES):
        h0, h1 = i * H / TRANCHES, (i + 1) * H / TRANCHES
        t0 = 2.75 + i * 0.028
        sens = -1 if i % 2 else 1
        css.append(f"""@keyframes tranche{i} {{ 0%, {pc(0.745)} {{ opacity: 0; transform: none; }} {pc(0.75)}, {pc(t0)} {{ opacity: 1; transform: none; animation-timing-function: cubic-bezier(.55,0,.9,.5); }}
  {pc(t0 + 0.3)}, 100% {{ opacity: 1; transform: translateX({sens * 2200}px); }} }}""")
        tranches.append(f'<div class="carte" style="clip-path: inset({h0:.1f}px 0 {H - h1:.1f}px 0);animation: tranche{i} {DUREE}s linear both">{interieur}</div>')

    html = ("<!doctype html><html><head><meta charset=\"utf-8\"><style>" + "\n".join(css) + "</style></head><body>"
            + "".join(tranches)
            + '<div class="onde"></div>' + "".join(eclats)
            + (f'<div class="embleme"><img src="embleme.png"></div>' if signe_src else '')
            + "".join(traits) + "</body></html>")
    page = tr / "transition.html"
    page.write_text(html, encoding="utf-8")

    verifier = option(sys.argv, "--verifier", "")
    if verifier:
        # Quelques instants seulement (en secondes), en PNG sur fond gris, pour regarder vite.
        env = dict(__import__("os").environ, IMAGES=verifier)
        subprocess.run(["node", str(ROOT / "tools" / "rendu_video.js"), str(page), str(tr / "verif"), str(DUREE), str(IPS)],
                       check=True, cwd="/tmp", env=env)
        return 0

    # Les images, a fond transparent, puis les videos.
    images = tr / "images"
    shutil.rmtree(images, ignore_errors=True)
    env = dict(__import__("os").environ, ALPHA="1", IMAGES_DANS=str(images))
    subprocess.run(["node", str(ROOT / "tools" / "rendu_video.js"), str(page), str(tr / "x.mp4"), str(DUREE), str(IPS)],
                   check=True, cwd="/tmp", env=env)
    seq = str(images / "%04d.png")
    ff = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-framerate", str(IPS), "-i", seq]
    webm, mov, apercu = out / f"{base}_transition.webm", out / f"{base}_transition.mov", out / f"{base}_transition_apercu.mp4"
    subprocess.run(ff + ["-c:v", "libvpx-vp9", "-pix_fmt", "yuva420p", "-b:v", "0", "-crf", "24", "-row-mt", "1", str(webm)], check=True)
    subprocess.run(ff + ["-c:v", "prores_ks", "-profile:v", "4444", "-pix_fmt", "yuva444p10le", "-qscale:v", "9",
                         "-vendor", "apl0", str(mov)], check=True)
    sous = out / f"{base}_apercu.png"
    if sous.exists():
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-loop", "1", "-framerate", str(IPS), "-i", str(sous),
                        "-framerate", str(IPS), "-i", seq, "-filter_complex", "[0][1]overlay=shortest=1,format=yuv420p",
                        "-c:v", "libx264", "-crf", "18", str(apercu)], check=True)
    shutil.rmtree(images)
    for f in (webm, mov, apercu):
        if f.exists():
            print(f, f"{f.stat().st_size / 1e6:.1f} Mo")


if __name__ == "__main__":
    sys.exit(main())
