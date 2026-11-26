#!/usr/bin/env python3
"""L'animation du logo : CROSSFIGHT s'ecrit seul, puis les deux barres le transpercent (la verticale, puis l'horizontale),
et UHC sort de la barre horizontale.

Usage : python3 tools/logo_anime.py <dossier de sortie>

Ecrit <dossier>/crossfight_uhc_anime.svg : un SVG anime (CSS), fond transparent, a ouvrir dans un navigateur ou a
poser sur une page web. Ensuite, pour la video : node tools/logo_video.js <dossier> (MP4, GIF, WebM transparent).
Le dessin vient de tools/logo/crossfight_uhc.svg, sans rien y changer : seules les pieces y sont reperees et animees.
"""
import re
import sys
from pathlib import Path

SRC = Path(__file__).resolve().parent / "logo" / "crossfight_uhc.svg"
DUREE = 4.2  # secondes, sans la boucle

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
    letters = "\n".join(f'<g class="lettre" style="animation-delay:{0.12 + i * 0.07:.2f}s">{p}</g>' for i, p in enumerate(white_cf))
    css = f"""{style}
    svg {{ overflow: visible; }}
    .tout {{ animation: tremble {d}s linear both; transform-box: view-box; transform-origin: 50% 50%; }}
    .lettre {{ opacity: 0; transform-box: fill-box; transform-origin: 50% 100%; animation: lettre 0.55s cubic-bezier(.2,.9,.3,1.2) both; }}
    @keyframes lettre {{ 0% {{ opacity: 0; transform: translateY(26px) scale(.9); filter: blur(6px); }} 100% {{ opacity: 1; transform: none; filter: none; }} }}
    .ombre-rouge {{ animation: ombre-rouge 0.6s ease-out 0.75s both; }}
    @keyframes ombre-rouge {{ 0% {{ opacity: 0; transform: translate(-8px, -8px); }} 100% {{ opacity: .7; transform: none; }} }}
    .barre-v {{ transform-box: fill-box; transform-origin: 50% 0%; animation: barre-v {d}s linear both; }}
    @keyframes barre-v {{ 0%, {k(1.35)} {{ transform: scaleY(0); }} {k(1.52)} {{ transform: scaleY(1.03); }} {k(1.6)}, 100% {{ transform: scaleY(1); }} }}
    .reflet-v {{ transform-box: fill-box; transform-origin: 50% 0%; animation: reflet-v {d}s linear both; }}
    @keyframes reflet-v {{ 0%, {k(1.42)} {{ transform: scaleY(0); }} {k(1.62)}, 100% {{ transform: scaleY(1); }} }}
    .barre-h {{ transform-box: fill-box; transform-origin: 0% 50%; animation: barre-h {d}s linear both; }}
    @keyframes barre-h {{ 0%, {k(1.78)} {{ transform: scaleX(0); }} {k(1.95)} {{ transform: scaleX(1.02); }} {k(2.03)}, 100% {{ transform: scaleX(1); }} }}
    .trait-h {{ transform-box: fill-box; transform-origin: 0% 50%; animation: trait-h {d}s linear both; }}
    @keyframes trait-h {{ 0%, {k(1.85)} {{ transform: scaleX(0); }} {k(2.05)}, 100% {{ transform: scaleX(1); }} }}
    @keyframes tremble {{
      0%, {k(1.50)} {{ transform: none; }} {k(1.53)} {{ transform: translate(0, 9px); }} {k(1.57)} {{ transform: translate(0, -5px); }} {k(1.62)} {{ transform: translate(0, 2px); }} {k(1.67)} {{ transform: none; }}
      {k(1.93)} {{ transform: none; }} {k(1.96)} {{ transform: translate(10px, 0); }} {k(2.0)} {{ transform: translate(-6px, 0); }} {k(2.05)} {{ transform: translate(2px, 0); }} {k(2.1)}, 100% {{ transform: none; }} }}
    .eclair {{ transform-box: fill-box; transform-origin: 50% 50%; opacity: 0; }}
    .eclair-1 {{ animation: eclair-1 {d}s linear both; }}
    .eclair-2 {{ animation: eclair-2 {d}s linear both; }}
    @keyframes eclair-1 {{ 0%, {k(1.49)} {{ opacity: 0; transform: scale(.2); }} {k(1.52)} {{ opacity: 1; transform: scale(1); }} {k(1.8)}, 100% {{ opacity: 0; transform: scale(2.6); }} }}
    @keyframes eclair-2 {{ 0%, {k(1.92)} {{ opacity: 0; transform: scale(.2); }} {k(1.95)} {{ opacity: 1; transform: scale(1.2); }} {k(2.25)}, 100% {{ opacity: 0; transform: scale(3); }} }}
    .uhc {{ animation: uhc {d}s cubic-bezier(.2,.9,.3,1.1) both; }}
    @keyframes uhc {{ 0%, {k(2.12)} {{ transform: translateY(-190px); }} {k(2.62)}, 100% {{ transform: none; }} }}
    .ombre-bleue {{ animation: ombre-bleue {d}s ease-out both; }}
    @keyframes ombre-bleue {{ 0%, {k(2.55)} {{ opacity: 0; transform: translate(-8px, -8px); }} {k(2.95)}, 100% {{ opacity: .7; transform: none; }} }}
    .reflet {{ animation: reflet {d}s ease-in-out both; }}
    @keyframes reflet {{ 0%, {k(3.0)} {{ transform: translateX(-300px); }} {k(3.75)}, 100% {{ transform: translateX(1500px); }} }}
"""
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1189.91 678.52">
  <defs>
    <style>{css}</style>
    <clipPath id="sous-la-barre"><rect x="-50" y="356" width="1300" height="400"/></clipPath>
    <radialGradient id="lueur"><stop offset="0" stop-color="#ffffff" stop-opacity="1"/><stop offset=".35" stop-color="#cfe0ff" stop-opacity=".7"/><stop offset="1" stop-color="#8fa8ff" stop-opacity="0"/></radialGradient>
    <linearGradient id="brillance" x1="0" x2="1" y1="0" y2="0"><stop offset="0" stop-color="#fff" stop-opacity="0"/><stop offset=".5" stop-color="#fff" stop-opacity=".55"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>
    <mask id="lettres"><g fill="#fff">{"".join(white_cf)}{"".join(white_uhc)}</g></mask>
  </defs>
  <g class="tout">
    <g class="cls-1 ombre-rouge">{"".join(red)}</g>
    <g clip-path="url(#sous-la-barre)"><g class="uhc"><g class="ombre-bleue">{"".join(blue)}</g></g></g>
    <g class="trait-h">{redline[0]}</g>
    <g class="cls-1 reflet-v">{vblue[0]}</g>
    <g>{letters}</g>
    <g clip-path="url(#sous-la-barre)"><g class="uhc">{"".join(white_uhc)}</g></g>
    <g mask="url(#lettres)"><g transform="skewX(-20)"><rect class="reflet" x="-200" y="0" width="160" height="700" fill="url(#brillance)"/></g></g>
    <g class="barre-h">{hbar}</g>
    <g class="barre-v">{vbar}</g>
    <circle class="eclair eclair-1" cx="331" cy="270" r="80" fill="url(#lueur)"/>
    <circle class="eclair eclair-2" cx="331" cy="354" r="90" fill="url(#lueur)"/>
  </g>
</svg>
'''
    (out / "crossfight_uhc_anime.svg").write_text(svg, encoding="utf-8")
    print(out / "crossfight_uhc_anime.svg", "duree", d)

if __name__ == "__main__":
    main()
