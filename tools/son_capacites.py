#!/usr/bin/env python3
"""Le son de chaque capacite d'un personnage, pour le montage : son bruitage et sa replique, mixes comme le jeu les
joue (les deux en meme temps, au meme niveau).

Usage : python3 -I tools/son_capacites.py <personnage> <dossier>

Lit les sons dans le pack complet (prive/CrossfightPackComplet.zip, apres tools/build_pack.py) et ecrit, pour chaque
capacite qui a un bruitage ou une replique, <dossier>/<personnage>_<n>_<capacite>.wav (48 kHz, stereo, 24 bits) :
  - le bruitage : "ability.<capacite>" (le premier des sons tires au hasard), "sfx.ultimate" pour le Burst ;
  - la replique : "voice.<capacite>", "voice.<personnage>_burst" pour le Burst (toutes ses variantes : _1, _2...).
Un limiteur garde le mix sous -1 dBFS. Les sons de Minecraft que le plugin ajoute (neige, totem...) n'y sont pas.
"""
import json
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "prive" / "CrossfightPackComplet.zip"


def capacites(cid):
    for lic in json.loads((ROOT / "tools" / "guide_capacites.json").read_text(encoding="utf-8")):
        for ch in lic.get("characters", []):
            if ch["id"] == cid:
                return ch["abilities"]
    sys.exit(f"personnage inconnu : {cid}")


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 1
    cid, out = sys.argv[1], Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    pack = zipfile.ZipFile(PACK)
    events = json.loads(pack.read("assets/crossfight/sounds.json"))

    def fichier(nom, tmp):
        """Un son du pack ("crossfight:sfx/dark/1"), extrait dans tmp."""
        ns, chemin = nom.split(":", 1) if ":" in nom else ("minecraft", nom)
        dest = Path(tmp) / (chemin.replace("/", "_") + ".ogg")
        dest.write_bytes(pack.read(f"assets/{ns}/sounds/{chemin}.ogg"))
        return dest

    def sons(event):
        return [s if isinstance(s, str) else s["name"] for s in events.get(event, {}).get("sounds", [])]

    with tempfile.TemporaryDirectory() as tmp:
        for n, a in enumerate(capacites(cid), 1):
            burst = a["id"] == "solo_burst"
            bruitage = sons("sfx.ultimate" if burst else "ability." + a["id"])[:1]
            voix = sons(f"voice.{cid}_burst" if burst else "voice." + a["id"])
            cle = "burst" if burst else a["id"].removeprefix(cid + "_")
            if not bruitage and not voix:
                print(f"{a['name']} : ni bruitage ni replique, rien")
                continue
            for i, v in enumerate(voix or [None], 1):
                entrees = [fichier(s, tmp) for s in bruitage + ([v] if v else [])]
                nom = out / f"{cid}_{n}_{cle}{f'_{i}' if len(voix) > 1 else ''}.wav"
                cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y"]
                for e in entrees:
                    cmd += ["-i", str(e)]
                mix = "".join(f"[{k}:a]aresample=48000,aformat=channel_layouts=stereo[a{k}];" for k in range(len(entrees)))
                mix += "".join(f"[a{k}]" for k in range(len(entrees)))
                mix += f"amix=inputs={len(entrees)}:duration=longest:normalize=0,alimiter=limit=0.89:level=false[m]"
                subprocess.run(cmd + ["-filter_complex", mix, "-map", "[m]", "-c:a", "pcm_s24le", str(nom)], check=True)
                duree = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(nom)],
                                             capture_output=True, text=True).stdout)
                print(f"{nom.name:34} {duree:5.2f} s  bruitage : {bruitage[0] if bruitage else '-'}  replique : {v or '-'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
