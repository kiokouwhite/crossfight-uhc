#!/usr/bin/env python3
"""La video de presentation d'un personnage : il monte du bas de l'ecran, et son nom s'ecrit en bas.

Usage : python3 tools/presentation_perso.py <personnage> <dossier> [--duree 6] [--graine N]

Le personnage, son nom et sa couleur viennent de resourcepack/selection.txt ; sa licence et sa voie, du guide
(tools/guide_capacites.json) ; son illustration, de resourcepack-prive/selection/art/<personnage>.* (fournie, jamais
versionnee : la video reste dans le dossier donne, a prendre sous prive/). Son fond blanc est retire comme pour les
cartes de l'ecran de selection.

Ecrit <dossier>/<personnage>.html (la scene animee, a ouvrir dans un navigateur) et <dossier>/<personnage>.mp4
(1920x1080, 60 images par seconde ; rendu par tools/rendu_video.js : Playwright, Chromium et ffmpeg).

Le fond est algorithmique (tools/fond_concorde.js, p5.js ; sa philosophie : tools/fond_concorde.md) : des milliers de
particules montent en colonne avec le personnage, une onde de choc part a son arrivee et remet leurs phases a zero, puis
elles s'accordent (modele de Kuramoto) en une pulsation commune et se rangent sur trois orbites d'un halo aux eclats
dores. La graine vient du personnage (--graine pour en essayer une autre) ; <dossier>/fond_concorde.html est la
visionneuse pour explorer les graines et les reglages (le personnage a ses couleurs par defaut). p5.js est telecharge
une fois dans build/vendor/.

Deroule : le fond s'allume a la couleur du personnage ; le personnage surgit du bas de l'ecran avec la colonne de
particules, s'arrete net (eclair, secousse) puis respire doucement ; une barre traverse le bas de l'ecran, la licence
s'ecrit au-dessus, le nom tombe lettre par lettre (blanc, decale de son ombre a la couleur du personnage, comme le
logo), et la voie s'ecrit dessous. Le logo Crossfight UHC est en haut a gauche.
"""
import json
import shutil
import subprocess
import zlib
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
P5 = ROOT / "build" / "vendor" / "p5-1.7.0.min.js"


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


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 1
    cid, out = sys.argv[1], Path(sys.argv[2]).resolve()
    duree = float(option(sys.argv, "--duree", "6"))
    graine = int(option(sys.argv, "--graine", str(zlib.crc32(cid.encode()) % 999999 + 1)))
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

    lettres = "".join(f'<span style="animation-delay:{1.55 + i * 0.07:.2f}s">{c if c != " " else "&nbsp;"}</span>' for i, c in enumerate(name.upper()))
    # Le personnage occupe la hauteur de l'ecran, son bas sous le nom.
    haut = 900
    large = round(w * haut / h)
    D = duree
    html = f"""<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: Titre; src: url("file:///usr/share/fonts/truetype/crosextra/Caladea-Bold.ttf"); }}
html, body {{ margin: 0; width: 1920px; height: 1080px; overflow: hidden; background: #0b0d18; }}
#scene {{ position: absolute; inset: 0; overflow: hidden; animation: secousse {D}s linear both; }}
#fond, #fond canvas {{ position: absolute; inset: 0; }}
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
@keyframes flash {{ 0%, {1.19 / D * 100:.2f}% {{ opacity: 0; }} {1.2 / D * 100:.2f}% {{ opacity: .5; }} {1.45 / D * 100:.2f}%, 100% {{ opacity: 0; }} }}
@keyframes secousse {{ 0%, {1.19 / D * 100:.2f}% {{ transform: none; }} {1.21 / D * 100:.2f}% {{ transform: translate(0, 14px); }} {1.25 / D * 100:.2f}% {{ transform: translate(-8px, -9px); }}
  {1.3 / D * 100:.2f}% {{ transform: translate(5px, 5px); }} {1.36 / D * 100:.2f}% {{ transform: translate(-2px, -2px); }} {1.42 / D * 100:.2f}%, 100% {{ transform: none; }} }}
</style></head><body><div id="scene">
<div id="fond"></div>
<div class="monte"><img class="perso" src="{cid}.png"></div>
<div class="voile"></div>
<div class="bas"><div class="licence">{licence}</div><div class="barre"></div><div class="nom">{lettres}</div><div class="voie">{voie}</div></div>
<img class="logo" src="logo.svg">
<div class="flash"></div>
</div>
<script src="p5.min.js"></script><script src="fond_concorde.js"></script>
<script>
window.pret = false;
new p5(p => {{
  p.setup = () => {{
    p.pixelDensity(1);
    p.createCanvas(1920, 1080).parent('fond');
    p.noLoop();
    const c = new Concorde(p, {{ graine: {graine}, couleur: '{color}' }});
    window.avancer = t => {{ c.avancer(t); c.dessiner(); }};
    window.avancer(0);
    window.pret = true;
  }};
}});
</script></body></html>"""
    page = out / f"{cid}.html"
    page.write_text(html, encoding="utf-8")
    shutil.copy(p5(), out / "p5.min.js")
    algo = (ROOT / "tools" / "fond_concorde.js").read_text(encoding="utf-8")
    (out / "fond_concorde.js").write_text(algo, encoding="utf-8")
    # La visionneuse, autonome (l'algorithme inclus), aux couleurs et a la graine du personnage.
    vue = (ROOT / "tools" / "fond_concorde.html").read_text(encoding="utf-8")
    vue = vue.replace('<script src="fond_concorde.js"></script>', "<script>\n" + algo + "\n</script>")
    vue = vue.replace("seed: 12345,", f"seed: {graine},").replace('value="12345"', f'value="{graine}"')
    vue = vue.replace("'#4a4fc4'", f"'{color}'").replace('"#4a4fc4"', f'"{color}"').replace(">#4a4fc4<", f">{color}<")
    (out / "fond_concorde.html").write_text(vue, encoding="utf-8")
    subprocess.run(["node", str(ROOT / "tools" / "rendu_video.js"), str(page), str(out / f"{cid}.mp4"), str(D)], check=True, cwd="/tmp")
    print(out / f"{cid}.mp4")


if __name__ == "__main__":
    sys.exit(main())
