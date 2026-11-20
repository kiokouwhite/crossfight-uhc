#!/usr/bin/env python3
"""Repere puis range les repliques d'un fichier de voix (la compilation des repliques d'un personnage).

Usage :
  python3 tools/voix_repliques.py analyse <fichier> <dossier> [--langue ja] [--seuil 0.012] [--modele medium]
  python3 tools/voix_repliques.py range <fichier> <plan.json> [--repliques <dossier>/repliques.json] [--verifie] [--langue ja]

analyse : decoupe le fichier aux silences et dit ce qu'on entend dans chaque extrait (transcription automatique,
faster-whisper : pip install faster-whisper). Ecrit <dossier>/repliques.txt, une ligne par extrait : son numero,
son debut, sa fin, sa duree, son texte ; et <dossier>/repliques.json. Le dossier est a prendre sous prive/
(jamais versionne) : c'est le texte de voix officielles. --modele small va trois a quatre fois plus vite
(le modele medium met a peu pres la duree du fichier) : assez pour reperer les repliques, que --verifie controle ensuite.

range : decoupe les extraits choisis et les range dans le pack prive (resourcepack-prive/, jamais versionne).
plan.json : {"<cle de voix>": [extrait, ...]}. Un extrait est [debut, fin] en secondes ou, avec --repliques, le
numero d'un extrait de l'analyse (12), ou "12-13" pour aller du debut du 12 a la fin du 13 (une replique en deux
temps). Un seul extrait donne <cle>.ogg ; plusieurs, <cle>_1.ogg, <cle>_2.ogg... et le plugin en tire un au hasard.
Les anciens fichiers de ces cles sont remplaces.
--verifie retranscrit chaque extrait une fois decoupe, pour controler son etiquette.

Les cles que le plugin joue de lui-meme : <capacite> (a chaque usage reussi), <perso>_selection, <perso>_kill,
<perso>_mort, <capacite>_vide (jauge d'une capacite a bascule epuisee).
Ensuite : python3 tools/build_pack.py, puis recompiler le plugin.
"""
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RATE = 16000


def option(argv, name, default):
    return argv[argv.index(name) + 1] if name in argv else default


def decode(src):
    """Le fichier en mono 16 kHz, en nombres flottants (ffmpeg lit tous les formats)."""
    import numpy as np
    raw = subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-i", str(src), "-ac", "1", "-ar", str(RATE), "-f", "s16le", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0


def model(name="medium"):
    os.environ.setdefault("HF_HUB_DISABLE_XET", "1")
    try:
        from faster_whisper import WhisperModel
    except ImportError:
        sys.exit("faster-whisper manque : pip install faster-whisper")
    return WhisperModel(name, device="cpu", compute_type="int8", cpu_threads=os.cpu_count() or 4)


def clips_of(audio, threshold):
    """Les extraits : ce qui sonne, entre deux silences d'au moins 0,22 s (en secondes)."""
    import numpy as np
    win = int(0.02 * RATE)
    rms = np.sqrt(np.convolve(audio * audio, np.ones(win) / win, mode="same"))
    loud = rms > threshold
    out, start, quiet, step, gap = [], None, 0, 160, int(0.22 * RATE)
    for i in range(0, len(audio), step):
        if loud[i]:
            if start is None:
                start = i
            quiet = 0
        elif start is not None:
            quiet += step
            if quiet >= gap:
                out.append((start, i - quiet))
                start, quiet = None, 0
    if start is not None:
        out.append((start, len(audio)))
    return [(s / RATE, e / RATE) for s, e in out if e - s > 0.12 * RATE]


def analyse(argv):
    src, work = Path(argv[2]), Path(argv[3])
    work.mkdir(parents=True, exist_ok=True)
    audio = decode(src)
    clips = clips_of(audio, float(option(argv, "--seuil", "0.012")))
    # Une seule passe sur tout le fichier, avec l'heure de chaque mot : il va a l'extrait qui le contient.
    segments, _ = model(option(argv, "--modele", "medium")).transcribe(audio, language=option(argv, "--langue", "ja"), word_timestamps=True, vad_filter=True,
                                     vad_parameters=dict(min_silence_duration_ms=200), beam_size=5, condition_on_previous_text=False)
    texts = [""] * len(clips)
    for seg in segments:
        for w in seg.words or []:
            mid = (w.start + w.end) / 2
            best = min(range(len(clips)), key=lambda k: 0 if clips[k][0] - 0.15 <= mid <= clips[k][1] + 0.15 else min(abs(mid - clips[k][0]), abs(mid - clips[k][1])))
            if clips[best][0] - 0.4 <= mid <= clips[best][1] + 0.4:
                texts[best] += w.word
    rows = [[round(s, 2), round(e, 2), t.strip()] for (s, e), t in zip(clips, texts)]
    (work / "repliques.json").write_text(json.dumps(rows, ensure_ascii=False), encoding="utf-8")
    lines = [f"{k:4d} {s:7.2f} {e:7.2f} {e - s:5.2f}  {t}" for k, (s, e, t) in enumerate(rows)]
    (work / "repliques.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{len(rows)} extraits dans {work / 'repliques.txt'}")
    return 0


def store(argv):
    src, plan = Path(argv[2]), json.loads(Path(argv[3]).read_text(encoding="utf-8"))
    voice = ROOT / "resourcepack-prive" / "assets" / "crossfight" / "sounds" / "voice"
    voice.mkdir(parents=True, exist_ok=True)
    check = model() if "--verifie" in argv else None
    rows = json.loads(Path(option(argv, "--repliques", "")).read_text(encoding="utf-8")) if "--repliques" in argv else None

    def span(part):
        """Les bornes d'un extrait du plan : deux secondes, un numero de l'analyse, ou "de-a"."""
        if isinstance(part, int):
            return rows[part][0], rows[part][1]
        if isinstance(part, str):
            first, last = (int(v) for v in part.split("-"))
            return rows[first][0], rows[last][1]
        return part[0], part[1]

    with tempfile.TemporaryDirectory() as tmp:
        for key, parts in plan.items():
            for old in voice.glob(f"{key}*.ogg"):          # les anciens fichiers de cette cle, pas ceux d'une cle plus longue
                if re.fullmatch(re.escape(key) + r"(_\d+)?\.ogg", old.name):
                    old.unlink()
            for n, part in enumerate(parts, 1):
                a, b = span(part)
                name = key if len(parts) == 1 else f"{key}_{n}"
                wav = Path(tmp) / f"{name}.wav"
                # Un souffle avant et apres, et un fondu de chaque cote : pas de clic a la coupe.
                subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-ss", f"{max(0, a - 0.05):.3f}", "-to", f"{b + 0.10:.3f}", "-i", str(src),
                                "-af", "afade=t=in:d=0.02,areverse,afade=t=in:d=0.05,areverse", str(wav)], check=True)
                subprocess.run([sys.executable, str(ROOT / "tools" / "add_audio.py"), "voice", str(wav), name, "--prive"], check=True, capture_output=True)
                heard = ""
                if check:
                    import numpy as np
                    pad = np.zeros(int(0.3 * RATE), dtype=np.float32)
                    segments, _ = check.transcribe(np.concatenate([pad, decode(wav), pad]), language=option(argv, "--langue", "ja"), beam_size=5, condition_on_previous_text=False)
                    heard = " ".join(s.text.strip() for s in segments)
                print(f"{name:26s} {b - a:5.2f} s  {heard}", flush=True)
    return 0


if __name__ == "__main__":
    if len(sys.argv) >= 4 and sys.argv[1] == "analyse":
        sys.exit(analyse(sys.argv))
    if len(sys.argv) >= 4 and sys.argv[1] == "range":
        sys.exit(store(sys.argv))
    print(__doc__)
    sys.exit(1)
