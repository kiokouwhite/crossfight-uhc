#!/usr/bin/env python3
"""L'animation du logo : CROSSFIGHT s'ecrit seul, puis les deux barres le transpercent (la verticale, puis l'horizontale),
et UHC sort de la barre horizontale. Le logo tient un instant, puis disparait (a partir de FIN) : il se gonfle, un
eclair part du croisement des barres, les barres se retirent d'un coup, les lettres de CROSSFIGHT s'envolent une a une
en se brouillant, UHC remonte dans la barre, et une derniere etincelle brille au croisement.

Usage : python3 tools/logo_anime.py <dossier de sortie>

Ecrit <dossier>/crossfight_uhc_anime.svg : un SVG anime (CSS), fond transparent, a ouvrir dans un navigateur ou a
poser sur une page web. Ensuite, pour la video : node tools/logo_video.js <dossier> (MP4, GIF, WebM transparent).
Le dessin vient de tools/logo/crossfight_uhc.svg, sans rien y changer : seules les pieces y sont reperees et animees.
"""
import re
import sys
from pathlib import Path

SRC = Path(__file__).resolve().parent / "logo" / "crossfight_uhc.svg"
# Les instants (secondes) : les lettres de CROSSFIGHT (LETTRE0 + i * LETTRE_PAS), les impacts de la barre verticale (V)
# et de l'horizontale (H), l'arrivee de UHC (U), le reflet, puis la disparition (FIN). tools/logo_son.py s'y cale.
LETTRE0, LETTRE_PAS = 0.1, 0.065
V, H, U = 1.36, 1.80, 2.16
REFLET = (2.95, 3.65)
FIN = 5.2    # le debut de la disparition (secondes)
DUREE = 6.25  # secondes : l'arrivee, la tenue, la disparition

def main():
    out = Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)
    t = SRC.read_text(encoding="utf-8")
    tags = [(m.start(), m.group(0)) for m in re.finditer(r"<(path|line)\b[^>]*/>", t)]
    def between(a, b): return [s for p, s in tags if a <= p < b]
    pos = lambda s: t.index(s)
    g_red = pos('<g class="cls-1">'); g_blue = t.index('<g class="cls-1">', g_red + 1); line = pos('<line'); g_vblue = t.index('<g class="cls-1">', line)
    calque2 = pos('id="Calque_2"'); uhc_white = t.index("<g>", t.index("<g>", t.index("<g>", calque2) + 3) + 3)
    red = between(g_red, g_blue)            # ombre rouge de CROSSFIGHT (10 lettres)
    blue = between(g_blue, line)            # ombre bleue de UHC (3 lettres)
    redline = between(line, g_vblue)        # le trait rouge sous la barre horizontale
    vblue = between(g_vblue, calque2)       # le reflet bleu de la barre verticale
    white_cf = between(calque2, uhc_white)  # CROSSFIGHT blanc
    rest = between(uhc_white, len(t))
    white_uhc, hbar, vbar = rest[:3], rest[3], rest[4]
    assert len(red) == 10 and len(blue) == 3 and len(white_cf) == 10 and len(redline) == 1 and len(vblue) == 1, (len(red), len(blue), len(white_cf))

    style = re.search(r"<style>(.*?)</style>", t, re.S).group(1)
    d = DUREE
    def k(sec): return f"{sec / d * 100:.2f}%"
    # Les instants des deux impacts, et de l'arrivee de UHC.
    F = FIN
    def lettre(i):
        return (f'<g class="lettre" style="animation: lettre 0.5s cubic-bezier(.2,.9,.3,1.25) {LETTRE0 + i * LETTRE_PAS:.3f}s both, '
                f'sortie 0.32s cubic-bezier(.4,0,.7,.7) {F + 0.15 + i * 0.026:.3f}s forwards">{white_cf[i]}</g>')
    def bloc(classe, idx):
        # Une partie de CROSSFIGHT avec son ombre : repoussee d'un cote a l'impact de la barre verticale.
        return (f'<g class="{classe}"><g class="cls-1"><g class="ombre-rouge">{"".join(red[i] for i in idx)}</g></g>'
                + "".join(lettre(i) for i in idx) + "</g>")
    # Les etincelles : des traits qui partent du point d'impact.
    def gerbe(classe, cx, cy, n, graine):
        import random
        rnd = random.Random(graine)
        lignes = []
        for i in range(n):
            a = 2 * 3.14159 * i / n + rnd.uniform(-0.25, 0.25)
            r1, r2 = rnd.uniform(18, 40), rnd.uniform(70, 150)
            import math
            lignes.append(f'<line x1="{cx + math.cos(a) * r1:.1f}" y1="{cy + math.sin(a) * r1:.1f}" x2="{cx + math.cos(a) * r2:.1f}" y2="{cy + math.sin(a) * r2:.1f}" '
                          f'stroke="{"#ffffff" if i % 3 else "#ffd0d4"}" stroke-width="{rnd.uniform(2.5, 5):.1f}" stroke-linecap="round"/>')
        return f'<g class="{classe}" style="transform-origin:{cx}px {cy}px">{"".join(lignes)}</g>'
    css = f"""{style}
    svg {{ overflow: visible; }}
    .tout {{ transform-box: view-box; transform-origin: 28% 52%; animation: tout {d}s linear both; }}
    @keyframes tout {{
      0%, {k(V - 0.01)} {{ transform: none; }}
      {k(V)} {{ transform: translate(0, 26px) scale(1.08); }} {k(V + .03)} {{ transform: translate(-12px, -18px) scale(1.05); }}
      {k(V + .06)} {{ transform: translate(9px, 12px) scale(1.03); }} {k(V + .10)} {{ transform: translate(-6px, -7px) scale(1.015); }}
      {k(V + .15)} {{ transform: translate(3px, 3px); }} {k(V + .22)} {{ transform: none; }}
      {k(H - 0.01)} {{ transform: none; }}
      {k(H)} {{ transform: translate(30px, 6px) scale(1.09); }} {k(H + .03)} {{ transform: translate(-20px, -8px) scale(1.05); }}
      {k(H + .06)} {{ transform: translate(13px, 5px) scale(1.03); }} {k(H + .10)} {{ transform: translate(-7px, -3px) scale(1.015); }}
      {k(H + .15)} {{ transform: translate(3px, 1px); }} {k(H + .22)} {{ transform: none; }}
      {k(U - 0.01)} {{ transform: none; }} {k(U)} {{ transform: translate(0, 10px) scale(1.02); }} {k(U + .05)} {{ transform: translate(0, -4px); }} {k(U + .1)} {{ transform: none; }}
      {k(F)} {{ transform: none; animation-timing-function: cubic-bezier(.3,0,.7,1); }} {k(F + .12)} {{ transform: scale(1.04); }}
      {k(F + .14)} {{ transform: translate(-14px, 10px) scale(1.06); }} {k(F + .18)} {{ transform: translate(10px, -8px) scale(1.04); }}
      {k(F + .23)} {{ transform: translate(-5px, 4px) scale(1.02); }} {k(F + .3)}, 100% {{ transform: none; }} }}
    .lettre {{ opacity: 0; transform-box: fill-box; transform-origin: 50% 100%; }}
    @keyframes sortie {{ 0% {{ opacity: 1; transform: none; filter: none; }} 100% {{ opacity: 0; transform: translateY(-60px) scale(1.25, .6); filter: blur(10px); }} }}
    @keyframes lettre {{ 0% {{ opacity: 0; transform: translateY(30px) scale(.85); filter: blur(7px); }} 100% {{ opacity: 1; transform: none; filter: none; }} }}
    .ombre-rouge {{ animation: ombre-rouge {d}s linear both; }}
    @keyframes ombre-rouge {{
      0%, {k(.75)} {{ opacity: 0; transform: translate(-10px, -10px); }} {k(1.2)} {{ opacity: 1; transform: none; }}
      {k(V - .01)} {{ transform: none; }} {k(V)} {{ transform: translate(22px, -14px); }} {k(V + .05)} {{ transform: translate(-10px, 8px); }} {k(V + .12)} {{ transform: none; }}
      {k(H - .01)} {{ transform: none; }} {k(H)} {{ transform: translate(-20px, 18px); }} {k(H + .05)} {{ transform: translate(9px, -7px); }} {k(H + .12)}, {k(F + .14)} {{ opacity: 1; transform: none; }}
      {k(F + .45)}, 100% {{ opacity: 0; transform: translate(-16px, -26px); }} }}
    /* La barre verticale ecarte CROSSFIGHT : ce qui est a gauche part a gauche, le reste a droite, et le O se tord. */
    .pousse-g {{ animation: pousse-g {d}s cubic-bezier(.2,.8,.3,1) both; }}
    @keyframes pousse-g {{ 0%, {k(V - .01)} {{ transform: none; }} {k(V + .02)} {{ transform: translate(-46px, 4px) rotate(-2deg); }} {k(V + .2)} {{ transform: translate(6px, 0); }} {k(V + .32)}, 100% {{ transform: none; }} }}
    .pousse-d {{ animation: pousse-d {d}s cubic-bezier(.2,.8,.3,1) both; }}
    @keyframes pousse-d {{ 0%, {k(V - .01)} {{ transform: none; }} {k(V + .02)} {{ transform: translate(46px, 4px) rotate(1.5deg); }} {k(V + .2)} {{ transform: translate(-6px, 0); }} {k(V + .32)}, 100% {{ transform: none; }} }}
    .pousse-o {{ transform-box: fill-box; transform-origin: 50% 50%; animation: pousse-o {d}s linear both; }}
    @keyframes pousse-o {{ 0%, {k(V - .01)} {{ transform: none; }} {k(V)} {{ transform: scale(1.35, .7); }} {k(V + .06)} {{ transform: scale(.85, 1.2); }} {k(V + .14)} {{ transform: scale(1.06, .95); }} {k(V + .24)}, 100% {{ transform: none; }} }}
    /* La barre horizontale souleve tout CROSSFIGHT. */
    .pousse-haut {{ animation: pousse-haut {d}s cubic-bezier(.2,.8,.3,1) both; }}
    @keyframes pousse-haut {{ 0%, {k(H - .01)} {{ transform: none; }} {k(H + .02)} {{ transform: translate(0, -40px); }} {k(H + .2)} {{ transform: translate(0, 5px); }} {k(H + .32)}, 100% {{ transform: none; }} }}
    /* Les barres : elles arrivent en un eclair, s'epaississent sous le choc, puis s'affinent. */
    .barre-v {{ transform-box: fill-box; transform-origin: 50% 0%; animation: barre-v {d}s linear both; }}
    @keyframes barre-v {{ 0%, {k(V - .07)} {{ transform: scale(1, 0); animation-timing-function: cubic-bezier(.6,0,1,1); }} {k(V)} {{ transform: scale(4, 1); }} {k(V + .1)} {{ transform: scale(1.6, 1); }} {k(V + .25)}, {k(F + .12)} {{ transform: none; }} {k(F + .15)} {{ transform: scale(2.6, 1); animation-timing-function: cubic-bezier(.6,0,1,.6); }} {k(F + .3)}, 100% {{ transform: scale(.4, 0); }} }}
    .barre-h {{ transform-box: fill-box; transform-origin: 0% 50%; animation: barre-h {d}s linear both; }}
    @keyframes barre-h {{ 0%, {k(H - .07)} {{ transform: scale(0, 1); animation-timing-function: cubic-bezier(.6,0,1,1); }} {k(H)} {{ transform: scale(1, 4.5); }} {k(H + .1)} {{ transform: scale(1, 1.6); }} {k(H + .25)}, {k(F + .14)} {{ transform: none; }} {k(F + .17)} {{ transform: scale(1, 3); animation-timing-function: cubic-bezier(.6,0,1,.6); }} {k(F + .33)}, 100% {{ transform: scale(0, .4); }} }}
    .reflet-v {{ transform-box: fill-box; transform-origin: 50% 0%; animation: reflet-v {d}s linear both; }}
    @keyframes reflet-v {{ 0%, {k(V - .03)} {{ transform: scaleY(0); }} {k(V + .04)}, {k(F + .12)} {{ transform: scaleY(1); }} {k(F + .28)}, 100% {{ transform: scaleY(0); }} }}
    .trait-h {{ transform-box: fill-box; transform-origin: 0% 50%; animation: trait-h {d}s linear both; }}
    @keyframes trait-h {{ 0%, {k(H - .03)} {{ transform: scaleX(0); }} {k(H + .04)}, {k(F + .14)} {{ transform: scaleX(1); }} {k(F + .3)}, 100% {{ transform: scaleX(0); }} }}
    /* Le sillage lumineux des barres. */
    .sillage {{ filter: blur(10px); opacity: 0; }}
    .sillage-v {{ transform-box: fill-box; transform-origin: 50% 0%; animation: sillage-v {d}s linear both; }}
    @keyframes sillage-v {{ 0%, {k(V - .07)} {{ opacity: 0; transform: scale(5, 0); }} {k(V)} {{ opacity: 1; transform: scale(9, 1); }} {k(V + .35)}, 100% {{ opacity: 0; transform: scale(2, 1); }} }}
    .sillage-h {{ transform-box: fill-box; transform-origin: 0% 50%; animation: sillage-h {d}s linear both; }}
    @keyframes sillage-h {{ 0%, {k(H - .07)} {{ opacity: 0; transform: scale(0, 5); }} {k(H)} {{ opacity: 1; transform: scale(1, 10); }} {k(H + .35)}, 100% {{ opacity: 0; transform: scale(1, 2); }} }}
    /* Le flash de tout l'ecran, l'eclair au point d'impact, l'onde de choc et les etincelles. */
    .flash {{ opacity: 0; animation: flash {d}s linear both; }}
    @keyframes flash {{ 0%, {k(V - .01)} {{ opacity: 0; }} {k(V)} {{ opacity: .75; }} {k(V + .12)} {{ opacity: 0; }} {k(H - .01)} {{ opacity: 0; }} {k(H)} {{ opacity: .85; }} {k(H + .14)} {{ opacity: 0; }} {k(F + .13)} {{ opacity: 0; }} {k(F + .14)} {{ opacity: .6; }} {k(F + .3)}, 100% {{ opacity: 0; }} }}
    .eclair {{ transform-box: fill-box; transform-origin: 50% 50%; opacity: 0; }}
    .eclair-1 {{ animation: eclair-1 {d}s linear both; }}
    .eclair-2 {{ animation: eclair-2 {d}s linear both; }}
    @keyframes eclair-1 {{ 0%, {k(V - .01)} {{ opacity: 0; transform: scale(.3); }} {k(V)} {{ opacity: 1; transform: scale(1.6); }} {k(V + .3)}, 100% {{ opacity: 0; transform: scale(3.4); }} }}
    @keyframes eclair-2 {{ 0%, {k(H - .01)} {{ opacity: 0; transform: scale(.3); }} {k(H)} {{ opacity: 1; transform: scale(1.8); }} {k(H + .32)}, 100% {{ opacity: 0; transform: scale(3.8); }} }}
    .onde {{ transform-box: fill-box; transform-origin: 50% 50%; opacity: 0; fill: none; }}
    .onde-1 {{ animation: onde-1 {d}s cubic-bezier(.1,.7,.3,1) both; }}
    .onde-2 {{ animation: onde-2 {d}s cubic-bezier(.1,.7,.3,1) both; }}
    @keyframes onde-1 {{ 0%, {k(V - .01)} {{ opacity: 0; transform: scale(.2); stroke-width: 14px; }} {k(V)} {{ opacity: 1; }} {k(V + .45)}, 100% {{ opacity: 0; transform: scale(9); stroke-width: 1px; }} }}
    @keyframes onde-2 {{ 0%, {k(H - .01)} {{ opacity: 0; transform: scale(.2); stroke-width: 14px; }} {k(H)} {{ opacity: 1; }} {k(H + .45)}, 100% {{ opacity: 0; transform: scale(11); stroke-width: 1px; }} }}
    .eclair-3 {{ animation: eclair-3 {d}s linear both; }}
    @keyframes eclair-3 {{ 0%, {k(F + .13)} {{ opacity: 0; transform: scale(.3); }} {k(F + .14)} {{ opacity: 1; transform: scale(2); }} {k(F + .5)}, 100% {{ opacity: 0; transform: scale(4.2); }} }}
    .onde-3 {{ animation: onde-3 {d}s cubic-bezier(.1,.7,.3,1) both; }}
    @keyframes onde-3 {{ 0%, {k(F + .13)} {{ opacity: 0; transform: scale(.2); stroke-width: 16px; }} {k(F + .14)} {{ opacity: 1; }} {k(F + .6)}, 100% {{ opacity: 0; transform: scale(13); stroke-width: 1px; }} }}
    .gerbe-3 {{ opacity: 0; animation: gerbe-3 {d}s cubic-bezier(.1,.8,.3,1) both; }}
    @keyframes gerbe-3 {{ 0%, {k(F + .13)} {{ opacity: 0; transform: scale(.3); }} {k(F + .14)} {{ opacity: 1; transform: scale(.6); }} {k(F + .5)}, 100% {{ opacity: 0; transform: scale(3.4); }} }}
    .etoile {{ transform-box: fill-box; transform-origin: 50% 50%; opacity: 0; animation: etoile {d}s linear both; }}
    @keyframes etoile {{ 0%, {k(F + .6)} {{ opacity: 0; transform: scale(0) rotate(0deg); }} {k(F + .72)} {{ opacity: 1; transform: scale(1.4) rotate(45deg); animation-timing-function: ease-in; }}
      {k(F + 1.0)}, 100% {{ opacity: 0; transform: scale(0) rotate(90deg); }} }}
    .gerbe-1 {{ opacity: 0; animation: gerbe-1 {d}s cubic-bezier(.1,.8,.3,1) both; }}
    .gerbe-2 {{ opacity: 0; animation: gerbe-2 {d}s cubic-bezier(.1,.8,.3,1) both; }}
    @keyframes gerbe-1 {{ 0%, {k(V - .01)} {{ opacity: 0; transform: scale(.3); }} {k(V)} {{ opacity: 1; transform: scale(.5); }} {k(V + .3)}, 100% {{ opacity: 0; transform: scale(2.6); }} }}
    @keyframes gerbe-2 {{ 0%, {k(H - .01)} {{ opacity: 0; transform: scale(.3); }} {k(H)} {{ opacity: 1; transform: scale(.5); }} {k(H + .3)}, 100% {{ opacity: 0; transform: scale(2.9); }} }}
    /* UHC tombe de la barre horizontale et s'ecrase en place. */
    .uhc {{ animation: uhc {d}s linear both; }}
    @keyframes uhc {{ 0%, {k(U - .2)} {{ transform: translateY(-200px); animation-timing-function: cubic-bezier(.5,0,1,.6); }} {k(U)} {{ transform: translateY(16px) scaleY(.92); }} {k(U + .07)} {{ transform: translateY(-6px); }} {k(U + .13)}, {k(F + .2)} {{ transform: none; animation-timing-function: cubic-bezier(.4,-0.4,.9,.5); }} {k(F + .42)}, 100% {{ transform: translateY(-220px); }} }}
    .ombre-bleue {{ animation: ombre-bleue {d}s ease-out both; }}
    @keyframes ombre-bleue {{ 0%, {k(U)} {{ opacity: 0; transform: translate(-12px, -12px); }} {k(U + .3)}, {k(F + .18)} {{ opacity: .7; transform: none; }} {k(F + .38)}, 100% {{ opacity: 0; transform: translate(-12px, -12px); }} }}
    .reflet {{ animation: reflet {d}s ease-in-out both; }}
    @keyframes reflet {{ 0%, {k(REFLET[0])} {{ transform: translateX(-300px); }} {k(REFLET[1])}, 100% {{ transform: translateX(1500px); }} }}
"""
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1189.91 678.52" data-duree="{d}">
  <defs>
    <style>{css}</style>
    <clipPath id="sous-la-barre"><rect x="-50" y="356" width="1300" height="400"/></clipPath>
    <radialGradient id="lueur"><stop offset="0" stop-color="#ffffff" stop-opacity="1"/><stop offset=".3" stop-color="#e6eeff" stop-opacity=".85"/><stop offset="1" stop-color="#8fa8ff" stop-opacity="0"/></radialGradient>
    <linearGradient id="brillance" x1="0" x2="1" y1="0" y2="0"><stop offset="0" stop-color="#fff" stop-opacity="0"/><stop offset=".5" stop-color="#fff" stop-opacity=".55"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>
    <mask id="lettres"><g fill="#fff">{"".join(white_cf)}{"".join(white_uhc)}</g></mask>
  </defs>
  <g class="tout">
    <g clip-path="url(#sous-la-barre)"><g class="uhc"><g class="ombre-bleue">{"".join(blue)}</g></g></g>
    <g class="trait-h">{redline[0]}</g>
    <g class="cls-1 reflet-v">{vblue[0]}</g>
    <g class="pousse-haut">
      {bloc("pousse-g", [0, 1])}
      <g class="pousse-o">{bloc("", [2])}</g>
      {bloc("pousse-d", list(range(3, 10)))}
    </g>
    <g clip-path="url(#sous-la-barre)"><g class="uhc">{"".join(white_uhc)}</g></g>
    <g mask="url(#lettres)"><g transform="skewX(-20)"><rect class="reflet" x="-200" y="0" width="160" height="700" fill="url(#brillance)"/></g></g>
    <g class="sillage sillage-h" fill="#dfe8ff">{hbar}</g>
    <g class="sillage sillage-v" fill="#dfe8ff">{vbar}</g>
    <g class="barre-h">{hbar}</g>
    <g class="barre-v">{vbar}</g>
    <circle class="eclair eclair-1" cx="331" cy="270" r="80" fill="url(#lueur)"/>
    <circle class="eclair eclair-2" cx="331" cy="354" r="90" fill="url(#lueur)"/>
    <circle class="onde onde-1" cx="331" cy="270" r="40" stroke="#ffffff"/>
    <circle class="onde onde-2" cx="331" cy="354" r="40" stroke="#ffffff"/>
    {gerbe("gerbe-1", 331, 270, 14, 7)}
    {gerbe("gerbe-2", 331, 354, 16, 11)}
    <circle class="eclair eclair-3" cx="331" cy="330" r="90" fill="url(#lueur)"/>
    <circle class="onde onde-3" cx="331" cy="330" r="40" stroke="#ffffff"/>
    {gerbe("gerbe-3", 331, 330, 20, 23)}
    <path class="etoile" d="M331 270 L340 321 L391 330 L340 339 L331 390 L322 339 L271 330 L322 321 Z" fill="#ffffff"/>
  </g>
  <rect class="flash" x="-2000" y="-2000" width="5200" height="4700" fill="#ffffff"/>
</svg>
'''
    (out / "crossfight_uhc_anime.svg").write_text(svg, encoding="utf-8")
    print(out / "crossfight_uhc_anime.svg", "duree", d)

if __name__ == "__main__":
    main()
