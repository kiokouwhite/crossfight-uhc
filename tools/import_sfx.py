#!/usr/bin/env python3
"""Importe la bibliotheque de bruitages libres dans le pack de ressources.

Usage :  python3 tools/import_sfx.py <dossier des packs decompresses>

Les packs viennent d'OpenGameArt (voir resourcepack/CREDITS.md). Chacun est decompresse dans un
sous-dossier du dossier donne, nomme comme son archive (sans .zip, espaces remplaces par _).
Chaque son est converti en Ogg Vorbis mono 44,1 kHz, coupe a 3 secondes et mis au meme niveau.
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "resourcepack" / "assets" / "crossfight" / "sounds" / "sfx"

P80 = "80-CC0-RPG-SFX_0/"
P100 = "100-CC0-SFX_0/"
BANG = "25-CC0-bang-sfx/"
SCI = "sci-fi-sfx/"
WAT = "water-splash-slime-sfx/"
RPG = "rpg_sound_pack/RPG Sound Pack/"
FAN = "Fantasy_Sound_Library/Fantasy Sound Library/Wav/"
ELE = "Electric_Sound_Library/Wav/"

CATEGORIES = {
    "fire": [P80 + "spell_fire_01.ogg", P80 + "spell_fire_02.ogg", P80 + "spell_fire_05.ogg", P80 + "spell_fire_06.ogg"],
    "explosion": [P100 + "explosion.ogg", SCI + "explosion_02.ogg", BANG + "bang_02.ogg", BANG + "cannon_03.ogg"],
    "big_explosion": [SCI + "explosion_01.ogg", BANG + "cannon_02.ogg", BANG + "bang_06.ogg"],
    "water": [WAT + "splash_02.ogg", WAT + "splash_03.ogg", WAT + "splash_04.ogg", WAT + "splash_08.ogg"],
    "bubble": [WAT + "bubble_01.ogg", WAT + "bubble_02.ogg", WAT + "bubble_03.ogg"],
    "electric": [ELE + "UI_Electric/UI_Electric_00.wav", ELE + "UI_Electric/UI_Electric_01.wav", ELE + "UI_Electric/UI_Electric_02.wav",
                 ELE + "UI_Electric/UI_Electric_06.wav", ELE + "UI_Electric/UI_Electric_08.wav"],
    "electric_dash": [ELE + "Whoosh_Electric/Whoosh_Electric_00.wav", ELE + "Whoosh_Electric/Whoosh_Electric_01.wav",
                      ELE + "Whoosh_Electric/Whoosh_Electric_02.wav", ELE + "Whoosh_Electric/Whoosh_Electric_03.wav"],
    "whoosh": [RPG + "battle/swing.wav", RPG + "battle/swing2.wav", RPG + "battle/swing3.wav"],
    "slash": [P80 + "blade_01.ogg", P80 + "blade_02.ogg", P80 + "blade_03.ogg"],
    "weapon": [RPG + "battle/sword-unsheathe.wav", RPG + "battle/sword-unsheathe2.wav", RPG + "battle/sword-unsheathe3.wav",
               RPG + "battle/sword-unsheathe4.wav", RPG + "battle/sword-unsheathe5.wav"],
    "crystal": [P100 + "glass_01.ogg", P100 + "glass_02.ogg", P100 + "glass_03.ogg", P80 + "item_gem_04.ogg"],
    "teleport": [SCI + "teleport_01.ogg", SCI + "teleport_02.ogg"],
    "time": [P100 + "gong_01.ogg", P100 + "gong_02.ogg", P100 + "bell_01.ogg"],
    "magic": [FAN + "Spell_01.wav", FAN + "Spell_00.wav", RPG + "battle/magic1.wav", P80 + "spell_01.ogg", P80 + "spell_02.ogg"],
    "heal": [FAN + "Spell_02.wav", FAN + "Spell_04.wav"],
    "shield": [P100 + "metal_01.ogg", P100 + "metal_05.ogg", RPG + "inventory/metal-ringing.wav", RPG + "inventory/chainmail1.wav"],
    "gun": [BANG + "shot_01.ogg", BANG + "shot_02.ogg", BANG + "shot_03.ogg"],
    "laser": [SCI + "retro_laser_01.ogg", SCI + "shoot_01.ogg", SCI + "shoot_02.ogg"],
    "roar": [P80 + "creature_roar_02.ogg", P80 + "creature_roar_03.ogg", FAN + "Dragon_Growl_00.wav"],
    "dark": [RPG + "NPC/shade/shade1.wav", RPG + "NPC/shade/shade4.wav", RPG + "NPC/shade/shade6.wav"],
    "earth": [P80 + "stones_01.ogg", P80 + "stones_02.ogg", P80 + "stones_03.ogg"],
    "book": [P80 + "book_01.ogg", P80 + "book_02.ogg", P80 + "book_03.ogg", P80 + "book_04.ogg"],
    "chain": [P80 + "chain_01.ogg", P80 + "chain_02.ogg", P80 + "chain_03.ogg"],
    "plant": [P80 + "wood_04.ogg", P80 + "wood_05.ogg", P80 + "item_wood_01.ogg"],
    "trap": [FAN + "Trap_00.wav", FAN + "Trap_01.wav", FAN + "Trap_02.wav"],
    "ultimate": [BANG + "fw_01.ogg", BANG + "fw_02.ogg", BANG + "cannon_01.ogg"],
    "weird": [SCI + "weird_01.ogg", SCI + "weird_02.ogg", SCI + "weird_03.ogg"],
    "select": [FAN + "Menu_Select_00.wav"],
}

MAX_SECONDS = 3.0
TARGET_PEAK_DB = -3.0


def peak_db(src: Path) -> float:
    r = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", str(src), "-af", "volumedetect", "-f", "null", "-"],
                       capture_output=True, text=True)
    m = re.search(r"max_volume: (-?[\d.]+) dB", r.stderr)
    return float(m.group(1)) if m else 0.0


def duration(src: Path) -> float:
    r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(src)],
                       capture_output=True, text=True)
    try:
        return float(r.stdout.strip())
    except ValueError:
        return 0.0


def convert(src: Path, dst: Path) -> None:
    gain = TARGET_PEAK_DB - peak_db(src)
    length = min(duration(src), MAX_SECONDS)
    fade = max(0.0, length - 0.15)
    filters = f"volume={gain:.1f}dB,afade=t=out:st={fade:.2f}:d=0.15"
    dst.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(src), "-t", f"{MAX_SECONDS}",
                    "-ac", "1", "-ar", "44100", "-af", filters, "-map_metadata", "-1", "-fflags", "+bitexact",
                    "-flags:a", "+bitexact", "-c:a", "libvorbis", "-q:a", "4", str(dst)], check=True)


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 1
    packs = Path(sys.argv[1])
    missing = 0
    for cat, files in CATEGORIES.items():
        for n, rel in enumerate(files, 1):
            src = packs / rel
            if not src.exists():
                print("introuvable :", rel)
                missing += 1
                continue
            convert(src, OUT / cat / f"{n}.ogg")
    print(f"{len(CATEGORIES)} categories importees dans {OUT.relative_to(ROOT)}" + (f", {missing} fichier(s) manquant(s)" if missing else ""))
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
