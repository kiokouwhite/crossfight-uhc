#!/usr/bin/env python3
"""L'overlay des capacites en couches, pour le montage : une base fixe, et pour chaque capacite ses textes, qui entrent
et sortent en s'etirant comme une image qui saute ; et un volet qui couvre la fenetre de la video pendant la coupe.

Usage : python3 tools/overlay_anime.py <personnage> <dossier> [--capacites contre,ult] [--textes-seuls] [--verifier 0.2,0.5]

--textes-seuls : seulement la base et les textes des capacites choisies (ni le volet ni l'apercu).

Ecrit dans <dossier> (WebM VP9 et MOV ProRes 4444, a transparence) :
  <personnage>_overlay_base.png              tout ce qui ne change pas : le fond, l'illustration, le cadre, le nom
                                             vertical, le logo, les boites vides ; la fenetre de la video transparente
  <personnage>_<capacite>_textes.png         les cartouches du haut (type, nom), l'icone, la description, seuls
  <personnage>_<capacite>_textes_entree.*    leur arrivee (ENTREE secondes) : chaque element se devoile de gauche a
                                             droite en trois tranches decalees, une trainee lumineuse sur le bord
  <personnage>_<capacite>_textes_sortie.*    leur depart (SORTIE secondes), dans le meme sens
  <personnage>_volet.*                       des traits a la couleur du personnage qui couvrent la fenetre seule
                                             (entierement de VOLET_COUVERT[0] a [1]) puis repartent
  <personnage>_changement_apercu.mp4         l'enchainement d'une capacite a l'autre, pour voir

Au montage, de bas en haut : la video, le volet, la base, les textes. Pour passer d'une capacite a la suivante a
l'instant T de la coupe : le volet et la sortie des anciens textes commencent a T - VOLET_COUVERT[0] ; l'entree des
nouveaux textes commence a T + 0,2 s, puis leur image fixe tient jusqu'au changement suivant.
"""
import os
import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))

W, H, IPS = 1920, 1080, 60
ENTREE, SORTIE, VOLET = 0.8, 0.6, 0.9
VOLET_COUVERT = (0.4, 0.5)
RETARDS_ENTREE = {"type": 0.0, "nom": 0.07, "icone": 0.14, "description": 0.2}
RETARDS_SORTIE = {"type": 0.0, "nom": 0.05, "icone": 0.1, "description": 0.14}
TRANCHES = ((0, 66), (33, 33), (66, 0))       # les trois tranches de chaque element : haut et bas coupes, en %
DECALAGES = (0.0, 0.05, 0.025)


def option(argv, name, default):
    return argv[argv.index(name) + 1] if name in argv else default


def css_commun(color):
    return f"""
html, body {{ margin: 0; width: {W}px; height: {H}px; overflow: hidden; background: transparent; }}
.el {{ position: absolute; }}
.el img {{ position: absolute; left: 0; top: 0; }}
.trainee {{ position: absolute; left: 0; top: 22%; width: 100%; height: 56%;
  background: linear-gradient(90deg, transparent, {color} 60%, rgb(255,240,214) 96%, transparent); }}
""" + "".join(f"""
@keyframes entre{i} {{ 0% {{ clip-path: inset({h}% 100% {b}% 0); transform: translateX(-70px); }}
  70% {{ clip-path: inset({h}% 0 {b}% 0); transform: translateX(6px); }} 100% {{ clip-path: inset({h}% 0 {b}% 0); transform: none; }} }}
@keyframes sort{i} {{ 0% {{ clip-path: inset({h}% 0 {b}% 0); transform: none; }}
  100% {{ clip-path: inset({h}% 0 {b}% 100%); transform: translateX(70px); }} }}""" for i, (h, b) in enumerate(TRANCHES)) + """
@keyframes trainee { 0% { clip-path: inset(0 100% 0 0); opacity: 1; } 70% { clip-path: inset(0 0 0 60%); opacity: .9; }
  100% { clip-path: inset(0 0 0 100%); opacity: 0; } }
"""


def elements_html(elements, prefixe, mode, depart):
    """Les elements, chacun en trois tranches qui se devoilent (mode "entre") ou s'effacent ("sort") a partir de depart."""
    duree = 0.42 if mode == "entre" else 0.36
    retards = RETARDS_ENTREE if mode == "entre" else RETARDS_SORTIE
    out = ""
    for cle, img, x, y in elements:
        d0 = depart + retards.get(cle, 0)
        out += f'<div class="el" style="left:{x}px;top:{y}px;width:{img.width}px;height:{img.height}px">'
        for i, dec in enumerate(DECALAGES):
            out += (f'<img src="{prefixe}{cle}.png" style="animation: {mode}{i} {duree}s cubic-bezier(.2,.8,.3,1) '
                    f'{d0 + dec:.3f}s both">')
        out += f'<i class="trainee" style="animation: trainee {duree + 0.05:.2f}s linear {d0:.3f}s both"></i></div>'
    return out


def volet_html(rgb, bs, depart):
    """Les traits qui couvrent la fenetre de la video seule, a partir de depart."""
    import overlay_capacite as oc
    fx0, fy0, fw, fh = oc.FENETRE
    mele = lambda c1, c2, t: tuple(round(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))
    couleurs = [rgb, mele(rgb, bs.NAVY_DARK, 0.45), mele(rgb, (255, 255, 255), 0.35)]
    n, bande = 6, fh / 6
    css, corps = [], f'<div style="position:absolute;left:{fx0}px;top:{fy0}px;width:{fw}px;height:{fh}px;overflow:hidden">'
    for i in range(n):
        t0 = depart + VOLET_COUVERT[0] - 0.22 - (n - 1 - i) * 0.03
        t1 = depart + VOLET_COUVERT[1] + i * 0.03
        tot = t1 + 0.22 - t0
        p = lambda t: f"{(t - t0) / tot * 100:.2f}%"
        css.append(f"""@keyframes volet{i} {{ 0% {{ transform: translateX(-2300px) skewX(-28deg); animation-timing-function: cubic-bezier(.5,0,.3,1); }}
  {p(t0 + 0.22)}, {p(t1)} {{ transform: translateX(-260px) skewX(-28deg); animation-timing-function: cubic-bezier(.6,0,.8,.4); }}
  100% {{ transform: translateX({fw + 400}px) skewX(-28deg); }} }}""")
        c = couleurs[(i * 2) % 3]
        corps += (f'<div style="position:absolute;left:0;top:{round(i * bande - 6)}px;width:2000px;height:{round(bande + 12)}px;'
                  f'background:linear-gradient(90deg, rgb{c} 0 96%, rgb(255,240,214) 96% 100%);'
                  f'animation: volet{i} {tot:.3f}s linear {t0:.3f}s both"></div>')
    return css, corps + "</div>"


def rendre(page, duree, dossier, sortie=None, verifier=""):
    """Rend une page : en images transparentes encodees en WebM et MOV (sortie), ou en MP4 (sortie en .mp4)."""
    if verifier:
        env = dict(os.environ, IMAGES=verifier)
        subprocess.run(["node", str(ROOT / "tools" / "rendu_video.js"), str(page), str(page) + ".verif", str(duree), str(IPS)],
                       check=True, cwd="/tmp", env=env)
        return
    if str(sortie).endswith(".mp4"):
        subprocess.run(["node", str(ROOT / "tools" / "rendu_video.js"), str(page), str(sortie), str(duree), str(IPS)], check=True, cwd="/tmp")
        return
    images = dossier / "images"
    shutil.rmtree(images, ignore_errors=True)
    env = dict(os.environ, ALPHA="1", IMAGES_DANS=str(images))
    subprocess.run(["node", str(ROOT / "tools" / "rendu_video.js"), str(page), str(page) + ".x", str(duree), str(IPS)],
                   check=True, cwd="/tmp", env=env)
    ff = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-framerate", str(IPS), "-i", str(images / "%04d.png")]
    subprocess.run(ff + ["-c:v", "libvpx-vp9", "-pix_fmt", "yuva420p", "-b:v", "0", "-crf", "24", "-row-mt", "1", f"{sortie}.webm"], check=True)
    subprocess.run(ff + ["-c:v", "prores_ks", "-profile:v", "4444", "-pix_fmt", "yuva444p10le", "-qscale:v", "9", "-vendor", "apl0",
                         f"{sortie}.mov"], check=True)
    shutil.rmtree(images)


def page(css, corps):
    return f'<!doctype html><html><head><meta charset="utf-8"><style>{css}</style></head><body>{corps}</body></html>'


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 1
    import build_selection as bs
    import overlay_capacite as oc
    cid, out = sys.argv[1], Path(sys.argv[2]).resolve()
    ch = oc.capacites(cid)
    choix = option(sys.argv, "--capacites", "")
    caps = [oc.capacite(ch, cid, c) for c in choix.split(",")] if choix else ch["abilities"]
    travail = out / f"{cid}_overlay_couches"
    travail.mkdir(parents=True, exist_ok=True)
    color = {i: c for i, n, c in bs.roster()}[cid]
    rgb = bs.hexrgb(color)
    textes = oc.descriptions()
    verifier = option(sys.argv, "--verifier", "")

    # La base, et les elements de chaque capacite.
    couches = {}
    for a in caps:
        cle = a["id"].removeprefix(cid + "_")
        base, elements = oc.composer(cid, a, textes.get(a["id"], a["description"]))
        couches[cle] = elements
        for nom, img, x, y in elements:
            img.save(travail / f"{cle}_{nom}.png")
        seul = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        for nom, img, x, y in elements:
            seul.alpha_composite(img, (x, y))
        if not verifier:
            seul.save(out / f"{cid}_{cle}_textes.png", optimize=True)
    base.save(travail / "base.png")
    if not verifier:
        base.save(out / f"{cid}_overlay_base.png", optimize=True)

    css = css_commun(color)
    seuls = "--textes-seuls" in sys.argv
    # L'apercu d'un changement : de la deuxieme capacite a la derniere, la coupe a T.
    a1, a2 = caps[1 % len(caps)], caps[-1]
    k1, k2 = a1["id"].removeprefix(cid + "_"), a2["id"].removeprefix(cid + "_")
    jeu = ROOT / "prive" / "skins" / f"{cid}_en_jeu.png"
    fx0, fy0, fw, fh = oc.FENETRE
    if jeu.exists():
        clip = oc.dans_fenetre(Image.open(jeu))
        clip.save(travail / "clip_a.png")
        ImageOps.mirror(clip).save(travail / "clip_b.png")
    T, D = 1.6, 4.2
    v_css, v_corps = volet_html(rgb, bs, T - VOLET_COUVERT[0])
    apercu = page(css + "\n".join(v_css) + f"""
.clip {{ position: absolute; left: {fx0}px; top: {fy0}px; }}
@keyframes clip_b {{ 0%, {T / D * 100:.2f}% {{ opacity: 0; }} {(T + 0.02) / D * 100:.2f}%, 100% {{ opacity: 1; }} }}""",
        '<div style="position:absolute;inset:0;background:#000"></div>'
        + '<img class="clip" src="clip_a.png"><img class="clip" src="clip_b.png" style="animation: clip_b ' + str(D) + 's linear both">'
        + v_corps + '<img src="base.png" style="position:absolute;left:0;top:0">'
        + elements_html(couches[k1], f"{k1}_", "sort", T - VOLET_COUVERT[0])
        + elements_html(couches[k2], f"{k2}_", "entre", T + 0.2))
    (travail / "apercu.html").write_text(apercu, encoding="utf-8")
    if verifier:
        rendre(travail / "apercu.html", D, travail, verifier=verifier)
        return 0
    if not seuls:
        rendre(travail / "apercu.html", D, travail, out / f"{cid}_changement_apercu.mp4")
        # Le volet.
        v_css, v_corps = volet_html(rgb, bs, 0)
        (travail / "volet.html").write_text(page(css + "\n".join(v_css), v_corps), encoding="utf-8")
        rendre(travail / "volet.html", VOLET, travail, out / f"{cid}_volet")
    # L'entree et la sortie des textes de chaque capacite.
    for cle, elements in couches.items():
        for mode, duree, nom in (("entre", ENTREE, "entree"), ("sort", SORTIE, "sortie")):
            f = travail / f"{cle}_{nom}.html"
            f.write_text(page(css, elements_html(elements, f"{cle}_", mode, 0)), encoding="utf-8")
            rendre(f, duree, travail, out / f"{cid}_{cle}_textes_{nom}")
    for f in sorted(out.glob(f"{cid}_*textes*")) + sorted(out.glob(f"{cid}_volet.*")) + [out / f"{cid}_overlay_base.png", out / f"{cid}_changement_apercu.mp4"]:
        if not f.exists():
            continue
        print(f.name, f"{f.stat().st_size / 1e6:.1f} Mo")


if __name__ == "__main__":
    sys.exit(main())
