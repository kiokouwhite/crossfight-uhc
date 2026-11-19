#!/usr/bin/env python3
"""Filme les capacites pour le guide, dans le vrai client (serveur et client de test deja lances).

Usage :  python3 tools/tournage.py <capacites.json> <dossier de sortie> [perso ...]

Le client "Camera" (Xvfb :99, son dans la sortie PulseAudio "rec") passe en troisieme personne : un mannequin
incarne le personnage et lance chaque capacite vers la camera (/cf tournage). Chaque capacite donne
<dossier>/<perso>/<capacite>.mp4 (quelques secondes, avec le son). Les capacites d'arme et les passifs sans
objet sont filmes pendant que le mannequin frappe la camera.
"""
import json
import os
import subprocess
import sys
import time

SERVER = "/home/user/testserver"
ENV = dict(os.environ, DISPLAY=":99")
SKIP = {"uno_aura", "valorant_shop", "snk_odm", "rwby_duo"}   # objets communs a une licence, pas propres au personnage


def console(cmd):
    with open(os.path.join(SERVER, "console.in"), "w") as f:
        f.write(cmd + "\n")
    time.sleep(0.3)


def chat(text):
    """Tape une commande dans le chat du client (t, texte, Entree)."""
    subprocess.run(["xdotool", "key", "--clearmodifiers", "t"], env=ENV)
    time.sleep(0.6)
    subprocess.run(["xdotool", "type", "--delay", "25", text], env=ENV)
    time.sleep(0.3)
    subprocess.run(["xdotool", "key", "Return"], env=ENV)
    time.sleep(0.8)


def record(path, seconds, action):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = path + ".raw.mkv"
    rec = subprocess.Popen(["ffmpeg", "-y", "-loglevel", "error", "-f", "x11grab", "-video_size", "1280x720", "-framerate", "20",
                            "-i", ":99", "-f", "pulse", "-i", "rec.monitor", "-t", str(seconds),
                            "-c:v", "libx264", "-preset", "ultrafast", "-crf", "20", "-c:a", "pcm_s16le", raw])
    time.sleep(0.8)
    action()
    rec.wait()
    # Version publiee : 854x480, H.264 + AAC, petite.
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", raw, "-vf", "scale=854:480", "-c:v", "libx264", "-preset", "slow",
                    "-crf", "30", "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-c:a", "aac", "-b:a", "64k", path])
    os.remove(raw)


def main():
    data = json.load(open(sys.argv[1], encoding="utf-8"))
    out = sys.argv[2]
    only = set(sys.argv[3:])
    for licence in data:
        for c in licence["characters"]:
            if only and c["id"] not in only:
                continue
            chat("/cf change " + c["id"])
            time.sleep(4)
            for a in c["abilities"]:
                if a["id"] in SKIP:
                    continue
                path = os.path.join(out, c["id"], a["id"] + ".mp4")
                if os.path.exists(path):
                    continue
                console("cf tournage Camera reset")
                time.sleep(1.0)
                if a["item"] and not a["weapon"]:
                    seconds = 9 if a["type"] == "Ultime" else 7
                    record(path, seconds, lambda a=a: console("cf tournage Camera " + a["id"]))
                else:
                    def hits():
                        for _ in range(4):
                            console("cf tournage Camera frappe")
                            time.sleep(1.4)
                    record(path, 7, hits)
                print(c["id"], a["id"], "ok", flush=True)
                time.sleep(2.5)


if __name__ == "__main__":
    main()
