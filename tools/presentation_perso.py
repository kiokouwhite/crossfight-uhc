#!/usr/bin/env python3
"""La video de presentation d'un personnage : il monte du bas de l'ecran, et son nom s'ecrit en bas.

Usage : python3 tools/presentation_perso.py <personnage> <dossier> [--duree 6]

Le personnage, son nom et sa couleur viennent de resourcepack/selection.txt ; sa licence et sa voie, du guide
(tools/guide_capacites.json) ; son illustration, de resourcepack-prive/selection/art/<personnage>.* (fournie, jamais
versionnee : la video reste dans le dossier donne, a prendre sous prive/). Son fond blanc est retire comme pour les
cartes de l'ecran de selection.

Ecrit <dossier>/<personnage>.html (la scene animee, a ouvrir dans un navigateur) et <dossier>/<personnage>.mp4
(1920x1080, 60 images par seconde ; rendu par tools/rendu_video.js : Playwright, Chromium et ffmpeg).

Deroule : le fond s'allume a la couleur du personnage ; une colonne de lumiere monte et le personnage surgit du bas de
l'ecran, s'arrete net (eclair, secousse) puis respire doucement ; une barre traverse le bas de l'ecran, la licence
s'ecrit au-dessus, le nom tombe lettre par lettre (blanc, decale de son ombre a la couleur du personnage, comme le
logo), et la voie s'ecrit dessous. Le logo Crossfight UHC est en haut a gauche.
"""
import json
import random
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))


def option(argv, name, default):
    return argv[argv.index(name) + 1] if name in argv else default


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 1
    cid, out = sys.argv[1], Path(sys.argv[2]).resolve()
    duree = float(option(sys.argv, "--duree", "6"))
    out.mkdir(parents=True, exist_ok=True)
    import build_selection as bs
    names = {i: (n, c) for i, n, c in bs.entries()} if hasattr(bs, "entries") else {}
    if not names:
        for line in (ROOT / "resourcepack" / "selection.txt").read_text(encoding="utf-8").splitlines():
            parts = line.split()
            if len(parts) >= 3 and not line.startswith("#"):
                names[parts[0]] = (" ".join(parts[1:-1]), parts[-1])
    name, color = names[cid]
    licence, voie = "", ""
    for lic in json.loads((ROOT / "tools" / "guide_capacites.json").read_text(encoding="utf-8")):
        for ch in lic.get("characters", []):
            if ch["id"] == cid:
                licence, voie = lic["name"], ch.get("voie", "")
    art = next(iter(sorted((ROOT / "resourcepack-prive" / "selection" / "art").glob(cid + ".*"))), None)
    if art is None:
        sys.exit("Pas d'illustration pour " + cid)
    img = bs.load(art)
    img = img.crop(img.getbbox())
    img.save(out / f"{cid}.png")
    w, h = img.size
    logo = (ROOT / "tools" / "logo" / "crossfight_uhc.svg").read_text(encoding="utf-8")
    (out / "logo.svg").write_text(logo, encoding="utf-8")

    # Les grains de lumiere qui montent : places au hasard, mais toujours les memes.
    rnd = random.Random(cid)
    grains = "\n".join(
        f'<i class="grain" style="left:{rnd.uniform(2, 98):.1f}%;width:{s:.1f}px;height:{s:.1f}px;'
        f'animation-duration:{rnd.uniform(4, 9):.2f}s;animation-delay:-{rnd.uniform(0, 9):.2f}s;opacity:{rnd.uniform(.3, .9):.2f}"></i>'
        for s in (rnd.uniform(2, 7) for _ in range(46)))
    lettres = "".join(f'<span style="animation-delay:{1.55 + i * 0.07:.2f}s">{c if c != " " else "&nbsp;"}</span>' for i, c in enumerate(name.upper()))
    # Le personnage occupe la hauteur de l'ecran, son bas sous le nom.
    haut = 900
    large = round(w * haut / h)
    D = duree
    html = f"""<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: Titre; src: url("file:///usr/share/fonts/truetype/crosextra/Caladea-Bold.ttf"); }}
html, body {{ margin: 0; width: 1920px; height: 1080px; overflow: hidden; background: #0b0d18; }}
#scene {{ position: absolute; inset: 0; overflow: hidden; animation: secousse {D}s linear both; }}
.fond {{ position: absolute; inset: 0; background: radial-gradient(ellipse 60% 70% at 50% 58%, {color}cc 0%, {color}44 35%, #0b0d18 75%);
  animation: fond 0.9s ease-out both; }}
@keyframes fond {{ from {{ opacity: 0; transform: scale(1.15); }} to {{ opacity: 1; transform: none; }} }}
.rayons {{ position: absolute; left: 50%; top: 62%; width: 3000px; height: 3000px; margin: -1500px 0 0 -1500px; opacity: .16;
  background: repeating-conic-gradient(from 0deg, #fff 0deg 3deg, transparent 3deg 18deg);
  -webkit-mask: radial-gradient(circle, #000 0%, transparent 45%); animation: tourne 40s linear infinite, rayons 1.2s ease-out both; }}
@keyframes tourne {{ to {{ transform: rotate(360deg); }} }}
@keyframes rayons {{ from {{ opacity: 0; }} to {{ opacity: .16; }} }}
.grain {{ position: absolute; bottom: -20px; border-radius: 50%; background: #fff; box-shadow: 0 0 10px 3px {color};
  animation-name: grain; animation-timing-function: linear; animation-iteration-count: infinite; }}
@keyframes grain {{ from {{ transform: translateY(0); }} to {{ transform: translateY(-1150px); }} }}
.colonne {{ position: absolute; left: 50%; bottom: 0; width: 520px; height: 1080px; margin-left: -260px;
  background: linear-gradient(to top, #ffffffcc, {color}88 40%, transparent 85%); filter: blur(30px);
  transform-origin: 50% 100%; animation: colonne {D}s linear both; }}
@keyframes colonne {{ 0%, {0.2 / D * 100:.2f}% {{ opacity: 0; transform: scaleY(0); }} {0.9 / D * 100:.2f}% {{ opacity: 1; transform: scaleY(1); }}
  {1.4 / D * 100:.2f}% {{ opacity: .9; transform: scaleX(1.25); }} {2.4 / D * 100:.2f}%, 100% {{ opacity: .22; transform: scaleX(.8); }} }}
.monte {{ position: absolute; left: 50%; bottom: 60px; width: {large}px; height: {haut}px; margin-left: -{large // 2}px;
  animation: monte {D}s linear both; }}
@keyframes monte {{
  0%, {0.3 / D * 100:.2f}% {{ transform: translateY(1150px); filter: blur(10px) brightness(1.6); animation-timing-function: cubic-bezier(.15,.85,.25,1); }}
  {1.2 / D * 100:.2f}% {{ transform: translateY(-34px); filter: blur(0) brightness(1.6); }}
  {1.38 / D * 100:.2f}% {{ transform: translateY(8px); filter: brightness(1.3); }}
  {1.55 / D * 100:.2f}%, 100% {{ transform: none; filter: none; }} }}
.perso {{ width: 100%; height: 100%; display: block; transform-origin: 50% 70%; animation: respire {D}s ease-in-out both; }}
@keyframes respire {{ 0%, {1.5 / D * 100:.2f}% {{ transform: none; }} 100% {{ transform: scale(1.045) translateY(-10px); }} }}
.voile {{ position: absolute; left: 0; right: 0; bottom: 0; height: 380px; background: linear-gradient(to top, #0b0d18 15%, #0b0d18cc 45%, transparent); }}
.bas {{ position: absolute; left: 0; right: 0; bottom: 70px; text-align: center; color: #fff; font-family: Titre, serif; }}
.licence {{ font-size: 34px; letter-spacing: 14px; color: #e8ecff; text-shadow: 3px 3px 0 {color}; animation: licence {D}s ease-out both; }}
@keyframes licence {{ 0%, {1.75 / D * 100:.2f}% {{ opacity: 0; letter-spacing: 2px; }} {2.3 / D * 100:.2f}%, 100% {{ opacity: 1; letter-spacing: 14px; }} }}
.barre {{ height: 4px; margin: 14px auto 6px; width: 1300px; background: linear-gradient(90deg, transparent, #fff 15%, #fff 85%, transparent);
  box-shadow: 0 5px 0 {color}aa, 0 0 18px #fff; transform-origin: 0 50%; animation: barre {D}s linear both; }}
@keyframes barre {{ 0%, {1.3 / D * 100:.2f}% {{ transform: scaleX(0); animation-timing-function: cubic-bezier(.6,0,1,1); }} {1.48 / D * 100:.2f}% {{ transform: scale(1, 3); }} {1.7 / D * 100:.2f}%, 100% {{ transform: none; }} }}
.nom {{ font-size: 150px; line-height: 1; letter-spacing: 10px; white-space: nowrap; }}
.nom span {{ display: inline-block; text-shadow: 10px 10px 0 {color}d0; opacity: 0; animation: lettre 0.45s cubic-bezier(.2,.9,.3,1.3) both; }}
@keyframes lettre {{ from {{ opacity: 0; transform: translateY(-120px) scale(1.4); filter: blur(10px); }} to {{ opacity: 1; transform: none; filter: none; }} }}
.voie {{ margin-top: 6px; font-size: 32px; font-style: italic; letter-spacing: 4px; color: #cfd6ff; animation: voie {D}s ease-out both; }}
@keyframes voie {{ 0%, {2.35 / D * 100:.2f}% {{ opacity: 0; transform: translateY(14px); }} {2.8 / D * 100:.2f}%, 100% {{ opacity: 1; transform: none; }} }}
.logo {{ position: absolute; left: 56px; top: 44px; width: 250px; animation: logo {D}s ease-out both; }}
@keyframes logo {{ 0%, {0.5 / D * 100:.2f}% {{ opacity: 0; transform: translateX(-30px); }} {1.1 / D * 100:.2f}%, 100% {{ opacity: .9; transform: none; }} }}
.flash {{ position: absolute; inset: 0; background: radial-gradient(ellipse 55% 65% at 50% 60%, #fff 0%, {color}aa 40%, transparent 75%); mix-blend-mode: screen; opacity: 0; animation: flash {D}s linear both; }}
@keyframes flash {{ 0%, {1.19 / D * 100:.2f}% {{ opacity: 0; }} {1.2 / D * 100:.2f}% {{ opacity: .85; }} {1.45 / D * 100:.2f}%, 100% {{ opacity: 0; }} }}
@keyframes secousse {{ 0%, {1.19 / D * 100:.2f}% {{ transform: none; }} {1.21 / D * 100:.2f}% {{ transform: translate(0, 14px); }} {1.25 / D * 100:.2f}% {{ transform: translate(-8px, -9px); }}
  {1.3 / D * 100:.2f}% {{ transform: translate(5px, 5px); }} {1.36 / D * 100:.2f}% {{ transform: translate(-2px, -2px); }} {1.42 / D * 100:.2f}%, 100% {{ transform: none; }} }}
</style></head><body><div id="scene">
<div class="fond"></div><div class="rayons"></div>{grains}
<div class="colonne"></div>
<div class="monte"><img class="perso" src="{cid}.png"></div>
<div class="voile"></div>
<div class="bas"><div class="licence">{licence}</div><div class="barre"></div><div class="nom">{lettres}</div><div class="voie">{voie}</div></div>
<img class="logo" src="logo.svg">
<div class="flash"></div>
</div></body></html>"""
    page = out / f"{cid}.html"
    page.write_text(html, encoding="utf-8")
    subprocess.run(["node", str(ROOT / "tools" / "rendu_video.js"), str(page), str(out / f"{cid}.mp4"), str(D)], check=True, cwd="/tmp")
    print(out / f"{cid}.mp4")


if __name__ == "__main__":
    sys.exit(main())
