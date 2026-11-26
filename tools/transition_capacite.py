#!/usr/bin/env python3
"""Les transitions des videos de capacites, a fond transparent : la transition seule, et une carte par categorie.

Usage : python3 tools/transition_capacite.py <personnage> <dossier> [--categories arme,capacite,ultime]
                                             [--verifier transition|arme|capacite|ultime 0.3,0.8]

Deux sortes de videos, separees pour etre posees independamment au montage :
  - la transition (TRANSITION secondes) : le signe de la voie du personnage surgit en grand au centre en tournant
    (plein a sa couleur, cerne de bleu nuit) dans une onde et une gerbe d'eclats ; des traits en biais a sa couleur
    balaient l'ecran de gauche a droite, le couvrent entierement de COUVERT[0] a COUVERT[1] secondes, puis repartent
    par la droite et laissent voir ce qu'il y a dessous ;
  - une carte par categorie (CARTE secondes) : opaque des la premiere image (le fond des overlays, le signe en
    filigrane, le cadre du pack), le mot de la categorie (ARME, CAPACITE, ULTIME) arrive de la gauche dans un grand
    cartouche a la couleur du personnage, entre deux ornements du pack qui arrivent en sens contraire, avec des
    trainees ; il derive doucement, puis la carte se decoupe en tranches qui filent de part et d'autre.
Pour les enchainer : poser la carte sur une piste sous la transition, COUVERT[0] secondes apres son debut (l'ecran est
alors couvert) ; les traits repartent en la devoilant.

Ecrit dans <dossier> : <personnage>_transition.(webm|mov), <personnage>_carte_<categorie>.(webm|mov) (WebM VP9 et MOV
ProRes 4444, tous deux a transparence), et <personnage>_enchainement_<categorie>_apercu.mp4 (transition et carte
enchainees sur l'apercu de l'overlay d'une capacite de cette categorie, s'il existe : tools/overlay_capacite.py).
Les images et les polices viennent du pack prive : les videos restent hors du depot (sous prive/).
"""
import math
import os
import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))

W, H = 1920, 1080
IPS = 60
TRANSITION, CARTE = 1.3, 2.5
COUVERT = (0.8, 0.86)          # l'ecran est entierement couvert par les traits entre ces deux instants de la transition
K_MOT = 9                      # un pixel du pack = 9 pixels dans le grand cartouche
TRANCHES, TRAITS = 8, 7
CATEGORIES = {"arme": "ARME", "capacite": "CAPACITÉ", "ultime": "ULTIME", "passif": "PASSIF"}


def option(argv, name, default):
    return argv[argv.index(name) + 1] if name in argv else default


def mele(c1, c2, t):
    return tuple(round(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))


def categorie_de(a):
    if a.get("weapon"):
        return "arme"
    return {"Ultime": "ultime", "Passif": "passif"}.get(a["type"], "capacite")


def page(css, corps):
    return ("<!doctype html><html><head><meta charset=\"utf-8\"><style>\n"
            f"html, body {{ margin: 0; width: {W}px; height: {H}px; overflow: hidden; background: transparent; }}\n"
            + "\n".join(css) + "</style></head><body>" + corps + "</body></html>")


def transition_html(color, rgb, bs, avec_signe):
    """L'embleme qui surgit, puis les traits qui couvrent l'ecran et repartent."""
    D = TRANSITION
    pc = lambda t: f"{t / D * 100:.3f}%"
    css = [f"""
.embleme {{ position: absolute; left: {W // 2 - 380}px; top: {H // 2 - 380}px; width: 760px; height: 760px; animation: embleme {D}s linear both; }}
.embleme img {{ width: 100%; height: 100%; filter: drop-shadow(0 0 6px rgb(255,240,214)) drop-shadow(0 0 40px {color}); }}
@keyframes embleme {{
  0% {{ opacity: 0; transform: scale(2.6) rotate(-150deg); filter: blur(16px); animation-timing-function: cubic-bezier(.2,.8,.3,1); }}
  {pc(0.28)} {{ opacity: 1; transform: scale(.9) rotate(10deg); filter: blur(0); }}
  {pc(0.36)} {{ transform: scale(1) rotate(0deg); animation-timing-function: ease-in; }}
  {pc(COUVERT[0] - 0.04)} {{ opacity: 1; transform: scale(1.3) rotate(50deg); }}
  {pc(COUVERT[0] - 0.02)}, 100% {{ opacity: 0; transform: scale(1.3) rotate(50deg); }} }}
.onde {{ position: absolute; left: {W // 2 - 110}px; top: {H // 2 - 110}px; width: 196px; height: 196px; border-radius: 50%;
  border: 12px solid rgb(255,240,214); box-shadow: 0 0 30px {color}, inset 0 0 30px {color}; animation: onde {D}s linear both; }}
@keyframes onde {{ 0%, {pc(0.16)} {{ opacity: 0; transform: scale(.3); }} {pc(0.18)} {{ opacity: 1; transform: scale(.5); animation-timing-function: cubic-bezier(.1,.7,.3,1); }}
  {pc(0.6)}, 100% {{ opacity: 0; transform: scale(7); }} }}
.eclat {{ position: absolute; left: {W // 2 - 9}px; top: {H // 2 - 9}px; width: 18px; height: 18px; background: rgb(255,240,214);
  box-shadow: 0 0 12px {color}; opacity: 0; }}
.trait {{ position: absolute; left: 0; width: 2600px; }}
"""]
    corps = '<div class="onde"></div>'
    for i in range(16):
        ang = 2 * math.pi * i / 16 + (0.2 if i % 2 else 0)
        d = 380 + 160 * (i % 3)
        dx, dy = round(math.cos(ang) * d), round(math.sin(ang) * d)
        css.append(f"""@keyframes eclat{i} {{ 0%, {pc(0.17)} {{ opacity: 0; transform: translate(0, 0) rotate(45deg) scale(1); }}
  {pc(0.19)} {{ opacity: 1; animation-timing-function: cubic-bezier(.1,.7,.3,1); }}
  {pc(0.62)}, 100% {{ opacity: 0; transform: translate({dx}px, {dy}px) rotate(45deg) scale(.4); }} }}""")
        corps += f'<i class="eclat" style="animation: eclat{i} {D}s linear both"></i>'
    if avec_signe:
        corps += '<div class="embleme"><img src="embleme.png"></div>'
    # Les traits : chacun entre par la gauche et couvre sa bande au plus tard a COUVERT[0], repart a partir de COUVERT[1].
    couleurs = [rgb, mele(rgb, bs.NAVY_DARK, 0.45), mele(rgb, (255, 255, 255), 0.35)]
    bande = H / TRAITS
    for i in range(TRAITS):
        t0 = COUVERT[0] - 0.24 - (TRAITS - 1 - i) * 0.03
        t1 = COUVERT[1] + i * 0.03
        c = couleurs[(i * 2) % 3]
        css.append(f"""@keyframes trait{i} {{ 0%, {pc(t0)} {{ transform: translateX(-2900px) skewX(-28deg); animation-timing-function: cubic-bezier(.5,0,.3,1); }}
  {pc(t0 + 0.24)}, {pc(t1)} {{ transform: translateX(-340px) skewX(-28deg); animation-timing-function: cubic-bezier(.6,0,.8,.4); }}
  {pc(min(D, t1 + 0.24))}, 100% {{ transform: translateX(2300px) skewX(-28deg); }} }}""")
        corps += (f'<div class="trait" style="top:{round(i * bande - 6)}px;height:{round(bande + 12)}px;'
                  f'background:linear-gradient(90deg, rgb{c} 0 96%, rgb(255,240,214) 96% 100%);animation: trait{i} {D}s linear both"></div>')
    return page(css, corps)


def carte_html(mot_png, mot, orne, avec_signe):
    """La carte d'une categorie : opaque des le debut, le mot arrive, derive, puis la carte part en tranches."""
    D = CARTE
    pc = lambda t: f"{t / D * 100:.3f}%"
    my = round(H / 2 - mot.height / 2)
    css = [f"""
.carte {{ position: absolute; inset: 0; }}
.carte > img {{ position: absolute; }}
.filigrane {{ left: {W // 2 - 520}px; top: {H // 2 - 520}px; width: 1040px; height: 1040px; opacity: .12; animation: filigrane {D}s linear both; }}
@keyframes filigrane {{ from {{ transform: rotate(-12deg) scale(1.08); }} to {{ transform: rotate(10deg) scale(1); }} }}
.cadre {{ left: 48px; top: 48px; opacity: .6; }}
.mot {{ left: {round(W / 2 - mot.width / 2)}px; top: {my}px; animation: mot {D}s linear both; }}
.orne {{ left: {round(W / 2 - orne.width / 2)}px; }}
.haut {{ top: {my - orne.height + 40}px; animation: orne_g {D}s linear both; }}
.bas {{ top: {my + mot.height - 40}px; animation: orne_d {D}s linear both; }}
.ombre1 {{ opacity: .45; animation-delay: .045s !important; }}
.ombre2 {{ opacity: .22; animation-delay: .09s !important; }}
@keyframes mot {{ 0%, {pc(0.15)} {{ transform: translateX(-2300px); animation-timing-function: cubic-bezier(.15,.85,.3,1.15); }}
  {pc(0.42)} {{ transform: translateX(30px); }} {pc(0.52)} {{ transform: translateX(0); animation-timing-function: linear; }} 100% {{ transform: translateX(-18px); }} }}
@keyframes orne_g {{ 0%, {pc(0.22)} {{ transform: translateX(2300px); animation-timing-function: cubic-bezier(.15,.85,.3,1.1); }}
  {pc(0.5)} {{ transform: translateX(-20px); }} {pc(0.6)} {{ transform: translateX(0); animation-timing-function: linear; }} 100% {{ transform: translateX(14px); }} }}
@keyframes orne_d {{ 0%, {pc(0.26)} {{ transform: translateX(2300px); animation-timing-function: cubic-bezier(.15,.85,.3,1.1); }}
  {pc(0.54)} {{ transform: translateX(-20px); }} {pc(0.64)} {{ transform: translateX(0); animation-timing-function: linear; }} 100% {{ transform: translateX(14px); }} }}
"""]
    interieur = ('<img src="fond.png" style="left:0;top:0">'
                 + ('<img class="filigrane" src="signe.png">' if avec_signe else '')
                 + '<img class="cadre" src="cadre.png">'
                 + '<img class="orne haut ombre1" src="orne.png"><img class="orne haut" src="orne.png">'
                 + '<img class="orne bas ombre1" src="orne.png"><img class="orne bas" src="orne.png">'
                 + f'<img class="mot ombre2" src="{mot_png}"><img class="mot ombre1" src="{mot_png}"><img class="mot" src="{mot_png}">')
    corps = ""
    for i in range(TRANCHES):
        h0, h1 = i * H / TRANCHES, (i + 1) * H / TRANCHES
        t0 = D - 0.55 + i * 0.028
        sens = -1 if i % 2 else 1
        css.append(f"""@keyframes tranche{i} {{ 0%, {pc(t0)} {{ transform: none; animation-timing-function: cubic-bezier(.55,0,.9,.5); }}
  {pc(min(D, t0 + 0.3))}, 100% {{ transform: translateX({sens * 2200}px); }} }}""")
        corps += f'<div class="carte" style="clip-path: inset({h0:.1f}px 0 {H - h1:.1f}px 0);animation: tranche{i} {D}s linear both">{interieur}</div>'
    return page(css, corps)


def rendre(html_path, duree, images, verifier=""):
    env = dict(os.environ, ALPHA="1", IMAGES_DANS=str(images))
    if verifier:
        env = dict(os.environ, IMAGES=verifier)
    shutil.rmtree(images, ignore_errors=True)
    subprocess.run(["node", str(ROOT / "tools" / "rendu_video.js"), str(html_path), str(images) + ".verif", str(duree), str(IPS)],
                   check=True, cwd="/tmp", env=env)


def encoder(images, sortie):
    seq = str(images / "%04d.png")
    ff = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-framerate", str(IPS), "-i", seq]
    subprocess.run(ff + ["-c:v", "libvpx-vp9", "-pix_fmt", "yuva420p", "-b:v", "0", "-crf", "24", "-row-mt", "1",
                         str(sortie) + ".webm"], check=True)
    subprocess.run(ff + ["-c:v", "prores_ks", "-profile:v", "4444", "-pix_fmt", "yuva444p10le", "-qscale:v", "9",
                         "-vendor", "apl0", str(sortie) + ".mov"], check=True)


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 1
    import build_selection as bs
    import overlay_capacite as oc
    import presentation_perso as pp
    from build_menu_franuka import wide

    cid, out = sys.argv[1], Path(sys.argv[2]).resolve()
    cats = option(sys.argv, "--categories", "arme,capacite,ultime").split(",")
    ch = oc.capacites(cid)
    travail = out / f"{cid}_transitions"
    travail.mkdir(parents=True, exist_ok=True)
    name, color = {i: (n, c) for i, n, c in bs.roster()}[cid]
    rgb = bs.hexrgb(color)

    # Les images communes : le fond des overlays, le cadre du pack, le signe, l'embleme, les ornements.
    fond = bs.backdrop((W, H)).convert("RGBA")
    sheet, _ = bs.portal(W // 8, H // 8, (W / 16, H / 16), card=False)
    portail = ImageChops.multiply(sheet.crop((0, 0, W // 8, H // 8)), Image.new("RGBA", (W // 8, H // 8), rgb + (255,)))
    portail = portail.resize((W, H), Image.NEAREST)
    portail.putalpha(120)
    fond.alpha_composite(portail)
    fond.convert("RGB").save(travail / "fond.png")
    oc.gros(bs.ui_frame((W - 96) // 4, (H - 96) // 4), 4).save(travail / "cadre.png")
    voie, _ = bs.framings().get(cid, (None, None))
    signe_src = next(iter(sorted((ROOT / "resourcepack-prive" / "selection" / "voie").glob((voie or "_") + ".*"))), None)
    if signe_src:
        signe = pp.signe_voie(signe_src).resize((760, 760), Image.LANCZOS)
        signe.save(travail / "signe.png")
        al = signe.getchannel("A")
        plein = al.point(lambda v: 255 if v > 50 else round(v * 5.1))
        haut = al.point(lambda v: 255 if v > 150 else 0)
        teinte = Image.composite(Image.new("RGBA", signe.size, mele(rgb, (255, 255, 255), 0.35) + (255,)),
                                 Image.new("RGBA", signe.size, rgb + (255,)), haut)
        teinte.putalpha(plein)
        emb = Image.new("RGBA", signe.size, bs.NAVY_DARK + (0,))
        emb.putalpha(plein.filter(ImageFilter.MaxFilter(15)))
        emb.alpha_composite(teinte)
        emb.save(travail / "embleme.png")
    orne = oc.gros(wide(bs.ui_sprite("Dividers", "Divider_06"), 192), 6)
    orne_l = oc.lueur(orne, bs.NAVY_DARK, 10)
    orne_l.alpha_composite(orne, (20, 20))
    orne_l.save(travail / "orne.png")
    banniere = oc.recolore(wide(bs.ui_sprite("Title banners", "BannerMedium_01B"), 160), oc.FOND_CARTOUCHE, rgb)
    milieu = pp.interieur(bs, "BannerMedium_01B", K_MOT)

    def mot_image(texte):
        t, tm = oc.ligne_hud(texte, K_MOT, bs, pp)
        b = oc.gros(banniere, K_MOT)
        b.alpha_composite(t, (round(b.width / 2 - t.width / 2), round(milieu - tm)))
        halo = oc.lueur(b, bs.NAVY_DARK, 16)
        halo.alpha_composite(b, (32, 32))
        return halo

    quoi, instants = "", ""
    if "--verifier" in sys.argv:
        i = sys.argv.index("--verifier")
        quoi, instants = sys.argv[i + 1], sys.argv[i + 2]

    # La transition seule.
    (travail / "transition.html").write_text(transition_html(color, rgb, bs, signe_src is not None), encoding="utf-8")
    if quoi == "transition":
        rendre(travail / "transition.html", TRANSITION, travail / "transition", instants)
        return 0
    if not quoi:
        rendre(travail / "transition.html", TRANSITION, travail / "transition")
        encoder(travail / "transition", out / f"{cid}_transition")

    # Une carte par categorie, puis l'enchainement sur l'apercu de l'overlay.
    for cat in cats:
        mot = mot_image(CATEGORIES[cat])
        mot.save(travail / f"mot_{cat}.png")
        (travail / f"carte_{cat}.html").write_text(carte_html(f"mot_{cat}.png", mot, orne_l, signe_src is not None), encoding="utf-8")
        if quoi:
            if quoi == cat:
                rendre(travail / f"carte_{cat}.html", CARTE, travail / f"carte_{cat}", instants)
            continue
        rendre(travail / f"carte_{cat}.html", CARTE, travail / f"carte_{cat}")
        encoder(travail / f"carte_{cat}", out / f"{cid}_carte_{cat}")
        a = next((a for a in ch["abilities"] if categorie_de(a) == cat), None)
        sous = out / f"{cid}_{a['id'].removeprefix(cid + '_')}_apercu.png" if a else None
        if sous and sous.exists():
            total = COUVERT[0] + CARTE + 0.6
            subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                            "-loop", "1", "-framerate", str(IPS), "-i", str(sous),
                            "-framerate", str(IPS), "-i", str(travail / f"carte_{cat}" / "%04d.png"),
                            "-framerate", str(IPS), "-i", str(travail / "transition" / "%04d.png"),
                            "-filter_complex", f"[1]setpts=PTS+{COUVERT[0]}/TB[c];[0][c]overlay=eof_action=pass[a];"
                                               "[a][2]overlay=eof_action=pass,format=yuv420p",
                            "-t", f"{total:.2f}", "-c:v", "libx264", "-crf", "18", str(out / f"{cid}_enchainement_{cat}_apercu.mp4")],
                           check=True)
        shutil.rmtree(travail / f"carte_{cat}")
    if not quoi:
        shutil.rmtree(travail / "transition", ignore_errors=True)
        for f in sorted(out.glob(f"{cid}_transition.*")) + sorted(out.glob(f"{cid}_carte_*")) + sorted(out.glob(f"{cid}_enchainement_*")):
            print(f, f"{f.stat().st_size / 1e6:.1f} Mo")


if __name__ == "__main__":
    sys.exit(main())
