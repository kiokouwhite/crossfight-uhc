#!/usr/bin/env python3
"""Fabrique le guide du joueur (guide/) : les règles, les événements, les lieux, et les personnages.

Usage : python3 tools/build_guide.py

Le guide est la documentation pour qui joue ; docs/ (tools/build_docs.py) est la documentation technique, tirée du README.
Les pages générales sont écrites ici. Les pages des personnages sont tirées du code (leurs noms, leurs capacités, leurs
descriptions et leurs temps de recharge), et la table des objets de Lootbox du README : relancer le script après un
changement les met à jour. gitbook-docs.yaml, à la racine, déclare les deux espaces au site GitBook.
"""
import json
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "guide"
SRC = ROOT / "src" / "main" / "java" / "com" / "kiokouwhite" / "crossfight"
FORMATS = {"SOLO": "Personnage solitaire", "DUO": "Groupe duo", "TRIO": "Équipe de trois"}


def licences():
    """[(id, nom, format, [(nom du personnage, [(capacité, description, recharge ou None)])])], d'après le code."""
    out = []
    for folder in sorted((SRC / "character").iterdir()):
        if not folder.is_dir():
            continue
        head = None
        for f in folder.glob("*.java"):
            m = re.search(r'new Licence\("([a-z0-9_]+)",\s*"([^"]+)",\s*TeamSize\.(\w+)', f.read_text(encoding="utf-8"))
            if m:
                head = m.groups()
        if not head:
            continue
        people = []
        for f in sorted(folder.glob("*.java")):
            text = f.read_text(encoding="utf-8")
            who = re.search(r'super\("([a-z0-9_]+)",\s*"([^"]+)",\s*Voie\.\w+', text)
            if not who:
                continue
            skills = []
            for m in re.finditer(r'(?:super|new \w+)\("([a-z0-9_]+)",\s*"([^"]+)",\s*"((?:[^"\\]|\\.)*)"\s*,\s*Material\.\w+(?:,\s*(\d+))?', text):
                cooldown = int(m.group(4)) if m.group(4) and int(m.group(4)) > 0 else None
                skills.append((m.group(2), m.group(3).replace('\\"', '"'), cooldown))
            people.append((who.group(2), skills))
        out.append((head[0], head[1].title().replace("'S", "'s").replace("Rwby", "RWBY").replace("Unordinary", "unOrdinary").replace(" No ", " no "), FORMATS.get(head[2], head[2]), people))
    return out


def lootbox_table():
    """La table des objets de Lootbox, telle qu'elle est dans le README."""
    text = (ROOT / "README.md").read_text(encoding="utf-8")
    part = text[text.index("### Événement Lootbox"):]
    rows = [l for l in part.splitlines() if l.startswith("| ")][:6]
    return "\n".join(rows)


PAGES = {}

PAGES["README.md"] = """# Crossfight UHC

**Crossfight UHC** est un UHC sur Minecraft 1.21 où chaque joueur incarne un personnage tiré d'une licence : Fire Emblem, RWBY, Valorant, Shingeki no Kyojin, unOrdinary, Earth Delta, Armed Blue Gunvolt, Shoja's Memories, Bloody Snow, ou un personnage solitaire.

On y joue seul, à deux ou à trois, les trois formats se mêlent dans la même partie, et la dernière équipe en vie gagne.

Ce qui change d'un UHC ordinaire :

- **Un personnage, des capacités, un Ultime.** Ton arme et tes pouvoirs viennent du personnage que tu incarnes, pas de ton établi.
- **Trois vies.** Mourir n'est pas la fin, mais chaque vie compte.
- **Des lieux à conquérir.** Des lieux venus d'autres mondes sont posés sur la map. Chacun rend un service, que ton équipe débloque en accomplissant la quête d'Anna.
- **Des événements.** Des caisses qui tombent du ciel, un duel d'équipes dans l'arène, un boss.

## Par où commencer

1. [Rejoindre une partie](commencer.md)
2. [Le déroulement d'une partie](deroulement.md)
3. [Les lieux et les quêtes d'Anna](lieux.md)
4. [Les événements](evenements.md)
5. [Les personnages](personnages/README.md)
"""

PAGES["commencer.md"] = """# Rejoindre une partie

## Le pack de ressources

À la connexion, le serveur propose un pack : accepte-le. Il apporte les sons, les voix, les armes des personnages et le panneau de quête. Sans lui, tu peux jouer, mais sans bruitages ni voix. `/cf pack` le redemande si tu l'as refusé.

## Le lobby

Avant le lancement, tout le monde attend sur une plateforme de verre dans le ciel. Tu ne peux pas en tomber.

## Choisir son équipe

Tu as dans ta barre une bannière **« Choisir son équipe »** : un clic droit ouvre le menu.

- **Jouer en solitaire**, **créer un duo** ou **créer une équipe de trois**.
- **Rejoindre** une équipe déjà formée et incomplète, en cliquant dessus.
- **Quitter mon équipe** pour changer d'avis.
- **Regarder la partie**, si tu préfères être spectateur.

La partie ne se lance que quand chacun a choisi une équipe ou de regarder. Une équipe incomplète peut jouer.

Si l'organisateur a choisi les **équipes aléatoires**, tu n'as rien à faire : tout le monde est mélangé au lancement.

## Choisir son personnage

Au lancement, chaque équipe reçoit une **licence tirée au sort**, parmi celles de son format : les solitaires parmi les personnages solitaires, les duos parmi les groupes duo, les trios parmi les équipes de trois. Tu choisis ensuite ton personnage dans cette licence. Tu as **60 secondes** ; passé ce délai, un personnage t'est attribué.

Ensuite, ton équipe est téléportée sur la map, avec l'arme et les capacités de chacun.
"""

PAGES["deroulement.md"] = """# Le déroulement d'une partie

Les durées ci-dessous sont les réglages par défaut : l'organisateur peut les changer.

| Moment | Ce qui se passe |
|---|---|
| Départ | Les équipes sont dispersées sur la map. Tu es invincible 30 secondes. |
| 0 à 30 min | Le PvP est coupé : c'est le moment de s'équiper, de miner et de conquérir des lieux. |
| 20 min | Événement Lootbox : des caisses tombent sur la map. |
| 30 min | Le PvP s'active. Événement Regna Ferox : un duel d'équipes dans l'arène. |
| 60 min | La bordure commence à se refermer, de 1000 à 100 blocs en 20 minutes. Événement Omori : un boss dans l'arène. |
| Fin | La dernière équipe en vie gagne. |

## Les vies

Tu as **3 vies**. Quand tu meurs, tu en perds une et tu réapparais ailleurs sur la map. À zéro, tu es éliminé. Si tu attends la mort de ton équipe, `/vivre` te fait réapparaître.

## Les capacités et l'Ultime

Ton personnage a une arme, des capacités (des objets dans ta barre, à utiliser au clic droit) et un **Ultime**. L'Ultime se charge en infligeant et en recevant des dégâts : une barre de vie entière de dégâts donnés ou reçus le charge d'un quart.

Le détail de chaque personnage est dans [Les personnages](personnages/README.md).

## Ce qui change par rapport à Minecraft

- **Pas d'épée à fabriquer.** Les épées viennent de ton personnage. Les arcs, arbalètes, tridents et masses ne se fabriquent pas non plus.
- **Les haches fabriquées sont des outils** : elles ne blessent pas les joueurs.
- **La Force est réduite** : chaque niveau donne un demi-cœur de dégâts en plus, au lieu d'un cœur et demi.
- **Cinq fois plus de minerais** sous terre.

## Les scénarios

L'organisateur peut activer des scénarios de confort ; ceux qui le sont sont rappelés au lancement, et `/cf scenario` en donne la liste.

| Scénario | Ce qu'il fait |
|---|---|
| CutClean | Le fer, l'or et le cuivre tombent en lingots, les animaux en viande cuite |
| Hasty Boys | Les outils fabriqués ont Efficacité III et Solidité I |
| Timber | Casser une bûche fait tomber tout l'arbre |
| Fast Smelting | Les fours cuisent 4 fois plus vite |
| Vanilla+ | Le gravier donne toujours du silex, tous les feuillages donnent des pommes |
| Cat Eyes | Vision nocturne permanente |
"""

PAGES["lieux.md"] = """# Les lieux et les quêtes d'Anna

Des lieux venus d'autres mondes sont posés sur la map, entourés d'une faille lumineuse. Chacun rend un **service** à ton équipe, à condition de l'avoir mérité.

## Comment ça marche

1. **Entre dans un lieu** : son nom s'affiche au milieu de l'écran, et un panneau de quête apparaît au-dessus du tableau de bord.
2. **Trouve Anna.** La marchande se tient près du centre du lieu, en surbrillance : tu la vois à travers les murs. De près, une bulle au-dessus d'elle dit ce qu'elle attend.
3. **Clic droit sur Anna** : la quête commence pour toute ton équipe. Le progrès est commun.
4. **Accomplis la quête** : le service du lieu est débloqué pour ton équipe **jusqu'à la fin de la partie**. La bulle d'Anna explique alors ce que tu as gagné.

Chaque équipe fait sa propre quête. Une autre équipe ne peut pas te la voler, mais rien ne l'empêche de venir t'attaquer pendant que tu es occupé.

**Les places sont comptées.** Chaque lieu ne garde le privilège que de deux équipes. Quand une équipe termine une quête, tout le monde l'apprend, avec le nombre de places qui restent. Si le lieu est complet et qu'une autre équipe s'y lance, celles qui le tiennent sont averties : c'est le moment de venir le défendre. Si elle termine quand même, elle choisit laquelle des équipes perd son privilège. L'équipe écartée peut refaire la quête pour reprendre sa place.

`/cf map` te donne une carte de la map, avec les lieux et leur service.

## Les lieux

| Lieu | Quête | Ce qu'il faut faire | Ce que tu débloques |
|---|---|---|---|
| Garreg Mach | La partie de pêche | Pêcher dix poissons à l'étang avec la canne d'Anna | Enchantement : reparle à Anna, clique l'objet à enchanter, puis choisis le niveau : I (1 niveau), II (2 niveaux) ou III (5 niveaux ; pas sur le diamant) |
| Shiganshina | La chasse au Titan | Abattre un Titan en cent coups, en esquivant ses cinq attaques | Forge : on y forge en musique chez Anna, pour du fer, de l'or et des diamants |
| Pearl | Les perles englouties | Retrouver cinq perles au cœur de la ville | Une potion offerte par Anna, toutes les 10 minutes |
| Central Town | Les quatre piliers | Rallumer quatre piliers en résolvant l'énigme de chacun | Marchand : Anna vend quatre objets de lootbox contre des diamants, et change d'étalage toutes les dix minutes |
| Arcadia | La récolte | Rattraper quinze pommes au casse-briques | Roulette : toutes les dix minutes, Anna te fait gagner de 3 à 10 pommes dorées |
| Terrace | L'ascension | Monter au sommet en passant par tous les drapeaux | De l'expérience tant que tu restes dans le lieu |
| Beacon | L'initiation | Retrouver quatre reliques | À chaque événement Lootbox, une caisse tombe ici |
| Forest of Hope | Les pièces du vaisseau | Rapporter trois pièces à Anna, une par une | La chasse aux pièces cachées du lieu |
| Quartier général | (pas de quête) | Payer Anna : 5 diamants, 10 pommes dorées ou 20 niveaux | La carte montre tous les joueurs ; on ouvre leur fiche et on les traque |

## Le détail des quêtes

**Le Titan (Shiganshina).** Il tombe en cent coups, comptés sur la barre en haut de l'écran. Chaque attaque s'annonce deux secondes avant, par son nom, dans une seconde barre sous la sienne : à toi de réagir. Il en a cinq, tirées au hasard : la *Charge titanesque* (sors du couloir rouge avant qu'il ne fonce : 2 cœurs), le *Lancer de pierres* (sors des cercles orange, trois fois de suite : 2 cœurs), le *Regard terrifiant* (détourne les yeux à temps, sinon tu es pétrifié 10 secondes), le *Cri strident* (éloigne-toi de 20 blocs, sinon nausée et lenteur 10 secondes) et la *Course du Titan* (fuis pendant 10 secondes : 1 cœur chaque fois qu'il te rattrape). Ses coups ignorent l'armure, et il casse les blocs sur son passage : ne compte pas sur un mur pour t'abriter.

**L'événement des lieux.** Toutes les vingt minutes, une cloche sonne : un lieu au hasard est renforcé, un autre affaibli, jusqu'à la cloche suivante. Un lieu renforcé donne bien plus (chez Anna à Pearl, toutes ses potions, une de chaque ; à la forge de Shiganshina, des matériaux doublés et de la netherite pour un A en difficile ; des enchantements gratuits à Garreg Mach...), un lieu affaibli donne moins ou coûte le double. Regarde l'annonce avant de choisir ta route.

**La forge (Shiganshina).** Le Titan vaincu, reparle à Anna : ici, on forge en musique. Elle te propose neuf airs, trois faciles, trois moyens et trois difficiles. Un grand tableau se dresse devant toi : des notes tombent sur quatre couloirs ; frappe avec les touches 1, 2, 3 et 4 de ton clavier quand elles touchent la ligne du bas. Ta note dépend de ta précision : C à partir de 55 %, B à 75 %, A à 90 %.

| Difficulté | C | B | A |
|---|---|---|---|
| Facile | 5 fer | 5 fer, 5 or | 5 fer, 10 or, 1 diamant |
| Moyen | 10 fer, 10 or | 10 fer, 20 or, 1 diamant | 10 fer, 20 or, 2 diamants |
| Difficile | 10 or, 1 diamant | 10 or, 2 diamants | 20 or, 3 diamants |

Les récompenses s'additionnent : chaque note que tu atteins te donne la sienne. Un A te donne donc celles du C, du B et du A : au difficile, 40 or et 6 diamants. Chaque récompense ne se reçoit qu'une fois par musique : si tu la rejoues et fais mieux, tu reçois celles des notes que tu n'avais pas encore. C'est le lieu de ceux qui veulent s'équiper sans descendre à la mine.

**La pêche (Garreg Mach).** Anna te donne une canne, et le bord de l'étang se met à scintiller pour ton équipe. Clic droit pour lancer la ligne dans l'eau. Quand « ÇA MORD ! » s'affiche, fais clic droit le plus vite possible. Une barre apparaît en haut de l'écran : la résistance du poisson. Ensuite, un cercle se resserre sur une cible au milieu de l'écran : clic droit pour l'arrêter. Plus il est près du centre (Bien, Très bien, Parfait), plus la barre descend. Tu ne peux pas rater : un mauvais clic fatigue seulement le poisson moins vite. Quand la barre est vide, il est pris ; il en faut dix.

**Le Titan (Shiganshina).** Il se tourne lentement vers le joueur le plus proche et écrase ceux qui restent à ses pieds. Frappe-le de dos, ou d'en haut : de face, tes coups ne comptent pas. À plusieurs, l'un attire son regard pendant que l'autre frappe.

**Le casse-briques (Arcadia).** Parle à Anna : un mur de briques se dresse devant toi. Ta raquette suit ton viseur, un clic lance la balle. Certaines briques portent une pomme et dessinent un motif : casse-les, et rattrape chaque pomme qui tombe avec la raquette. Quand toutes les briques à pomme sont cassées, un nouveau mur arrive, avec un autre motif. Toutes les pommes de ton équipe s'additionnent, et à quinze le lieu est à vous. Si la balle t'échappe, elle revient sur la raquette : tu ne perds rien.

**Les objets à trouver (Pearl, Beacon).** Ils brillent, entourés de jaune, et tintent quand tu approches : plus le son est fort, plus tu es près. Il suffit de passer dessus.

**Les énigmes des piliers (Central Town).** Quatre piliers éteints apparaissent autour d'Anna, et chacun cache une énigme différente. Un clic droit sur un pilier dresse un tableau devant toi : tu y joues en visant et en cliquant, et tu le fermes en t'accroupissant. Résous l'énigme, et le pilier se rallume.

- **La tablette.** À gauche la figure à obtenir, à droite une grille, en bas trois pièces dorées. Clique une pièce pour l'attraper, amène-la sur la grille, clique pour la poser ; la touche F la tourne. Là où deux pièces se recouvrent, les cases s'annulent : sers-t'en pour creuser la figure.
- **Le circuit.** Une source orange, deux lampes rouges, et des tuiles de fils. Clique une tuile pour la faire pivoter : le courant doit courir de la source jusqu'aux deux lampes.
- **L'établi.** Un objet t'est montré : refais sa recette sur la table de craft, avec les ingrédients proposés (attention aux intrus). Prends un ingrédient, puis clique les cases où le poser ; clique une case garnie pour la vider.
- **Le puzzle.** Une image découpée en neuf pièces mélangées : prends-les une à une et remets-les dans le cadre, d'après le modèle.

**Les pièces du vaisseau (Forest of Hope).** Tu n'en portes qu'une à la fois, et tu es ralenti tant que tu la portes. Si tu meurs, la pièce retourne où elle était.

**La montée aux drapeaux (Terrace).** Cinq drapeaux brillent le long de la montée, visibles à travers les murs ; le dernier est tout en haut. Passe par les quatre premiers, dans l'ordre que tu veux, puis atteins celui du sommet : il ne compte que si tu as passé tous les autres. Un seul membre de l'équipe suffit.

**Le quartier général.** Au centre de la map, pas de quête : Anna le loue à ton équipe contre 5 diamants, 10 pommes dorées ou 20 niveaux. Une seule équipe le tient à la fois, et une autre peut vous le reprendre en payant. Tant que tu es dedans, ta carte montre tous les autres joueurs en rouge. Fais un clic droit avec la carte, amène la croix sur un joueur et clique : sa fiche s'ouvre, avec son équipement, son inventaire, ses vies et ses kills. Tu peux y prendre une boussole qui pointe vers lui. Attention : un joueur traqué en est averti.

## Les potions d'Anna (Pearl)

La quête finie, un clic droit sur Anna ouvre sa boutique. Tout est gratuit, mais tu ne prends qu'**une potion toutes les 10 minutes** :

| Potion | Usage |
|---|---|
| Force | À boire |
| Vitesse | À boire |
| Poison | À lancer sur un adversaire |
| Faiblesse | À lancer sur un adversaire |
| Lenteur | À lancer sur un adversaire |
"""

PAGES["evenements.md"] = """# Les événements

Les minutes sont celles des réglages par défaut.

## Lootbox (20 min)

Des caisses apparaissent au hasard sur la map. Leurs coordonnées sont annoncées à tous, et une colonne de lumière les signale. Chaque caisse contient 2 à 8 diamants, 6 à 12 lingots d'or et un objet de son rang. Elle disparaît une fois vidée, ou au bout de 10 minutes.

Le rang est tiré au sort : rang 1 (45 %), rang 2 (35 %), rang 3 (19 %), rang 4 (1 %). Les objets ont un nombre d'utilisations compté, affiché sur l'objet.

%LOOTBOX%

Si une équipe a fini la quête de Beacon, une caisse tombe à chaque fois dans ce lieu.

## Regna Ferox (30 min)

Chaque joueur en vie reçoit une proposition de **duel d'équipes** dans le chat, avec deux boutons : Participer ou Refuser. Une équipe est inscrite quand tous ses membres en vie ont accepté. Les deux premières équipes inscrites sont téléportées dans l'arène de Ferox ; s'il n'y en a pas deux au bout d'une minute, l'arène reste fermée.

Dans l'arène, on ne meurt pas : un coup fatal te met à terre et tu regardes la fin du combat.

- **L'équipe gagnante** reçoit, pour chaque joueur, le contenu d'une Lootbox de rang 4.
- **L'équipe battue** perd une vie par joueur.

Tout le monde revient ensuite là où il était. On s'y bat même si le PvP n'est pas encore actif sur la map.

## Omori (60 min)

Les herses de l'arène se lèvent et une **porte blanche** apparaît dans la fosse ; ses coordonnées sont annoncées. Un clic droit dessus t'emmène dans l'Espace Blanc, où attend le boss **Omori**.

- Omori n'a pas de barre de vie : il faut lui porter **251 coups**, en cinq phases. Chaque coup compte pour un.
- Il est **intouchable pendant ses attaques**. Frappe-le pendant ses cinq secondes de repos, et pendant la seconde où il annonce l'attaque suivante.
- Le nom de chaque attaque s'affiche une seconde avant qu'elle parte : c'est le moment de t'écarter.
- À la dernière phase, le sol devient noir et ta vue se brouille.

Quand il tombe, une **porte noire** apparaît : elle te fait sortir. Une fois dehors, chaque joueur présent à sa mort fait un choix :

- **Se pardonner** : une vie de plus.
- **Ne pas se pardonner** : une vie de moins, mais Force III jusqu'à la fin de la partie.

Mourir dans l'Espace Blanc est une mort ordinaire.
"""

PAGES["commandes.md"] = """# Les commandes du joueur

| Commande | Ce qu'elle fait |
|---|---|
| `/cf team create solo` (ou `duo`, `trio`) | Créer son équipe dans le lobby |
| `/cf team join <numéro>` | Rejoindre une équipe |
| `/cf team leave` | Quitter son équipe |
| `/cf team list` | Voir les équipes |
| `/cf perso` | Ouvrir le choix du personnage |
| `/cf licences` | Lister les licences et leurs personnages |
| `/cf map` | Recevoir la carte de la map, avec les lieux |
| `/cf scenario` | Voir les scénarios actifs |
| `/cf ferox oui` (ou `non`) | Répondre à la proposition de Regna Ferox |
| `/vivre` | Réapparaître quand on attend la mort de son équipe |
| `/cf pack` | Redemander le pack de ressources |

La bannière « Choisir son équipe » du lobby fait la même chose que les commandes d'équipe, sans rien taper.
"""


DATA = ROOT / "tools" / "guide_capacites.json"
VIDEOS = OUT / "videos"
FORMAT_PAGES = {"TRIO": ("equipe-de-trois", "Équipe de trois"), "DUO": ("groupe-duo", "Groupe duo"), "SOLO": ("personnage-solitaire", "Personnage solitaire")}
FORMAT_TEXT = {
    "TRIO": "Les équipes de trois reçoivent une de ces licences ; chaque joueur y choisit un personnage différent.",
    "DUO": "Les équipes de deux reçoivent une de ces licences ; chaque joueur y choisit un personnage différent.",
    "SOLO": "Les joueurs seuls choisissent un de ces personnages, qui n'appartiennent à aucune licence.",
}
VOIES = {"Destruction": "Voie de la Destruction", "Harmonie": "Voie de l'Harmonie", "Chasse": "Voie de la Chasse", "Nihilite": "Voie de la Nihilité", "Nihilité": "Voie de la Nihilité"}


def slug(text):
    import unicodedata
    text = unicodedata.normalize("NFD", text).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", "-", text).strip("-")


def licence_title(name):
    return name.title().replace("'S", "'s").replace("Rwby", "RWBY").replace("Unordinary", "unOrdinary").replace(" No ", " no ")


def duration(seconds):
    m, s = divmod(seconds, 60)
    if m and s:
        return f"{m} minute{'s' if m > 1 else ''} {s}"
    if m:
        return f"{m} minute{'s' if m > 1 else ''}"
    return f"{s} secondes"


ACCENTS = {'abnegation': 'abnégation', 'achete': 'acheté', 'achetees': 'achetées', 'agilite': 'agilité', 'aleatoire': 'aléatoire', 'aleatoires': 'aléatoires', 'allie': 'allié', 'allies': 'alliés', 'ameliorees': 'améliorées', 'angelique': 'angélique', 'apres': 'après', 'araignee': 'araignée', 'araignees': 'araignées', 'arbalete': 'arbalète', 'arrete': 'arrête', 'arreter': 'arrêter', 'arriere': 'arrière', 'astrasphere': 'astrasphère', 'attitree': 'attitrée', 'augmentee': 'augmentée', 'aveugles': 'aveuglés', 'balaie': 'balaie', 'barriere': 'barrière', 'barrieres': 'barrières', 'bordee': 'bordée', 'brulante': 'brûlante', 'brulure': 'brûlure', 'capacite': 'capacité', 'capacites': 'capacités', 'carre': 'carré', 'categorie': 'catégorie', 'cecite': 'cécité', 'celeste': 'céleste', 'cerbere': 'cerbère', 'chaine': 'chaîne', 'chaines': 'chaînes', 'choisie': 'choisie', 'coeur': 'cœur', 'coeurs': 'cœurs', 'colere': 'colère', 'cone': 'cône', 'cones': 'cônes', 'consommees': 'consommées', 'coordonnees': 'coordonnées', 'cote': 'côté', 'coute': 'coûte', 'cratere': 'cratère', 'credits': 'crédits', 'cree': 'crée', 'debloquee': 'débloquée', 'dechainement': 'déchaînement', 'decharge': 'décharge', 'decolle': 'décolle', 'deferle': 'déferle', 'definitivement': 'définitivement', 'degat': 'dégât', 'degats': 'dégâts', 'demoniaque': 'démoniaque', 'deplace': 'déplace', 'derniere': 'dernière', 'derriere': 'derrière', 'desarmante': 'désarmante', 'desesperee': 'désespérée', 'detruite': 'détruite', 'differents': 'différents', 'dome': 'dôme', 'dore': 'doré', 'doree': 'dorée', 'dorees': 'dorées', 'doublees': 'doublées', 'eclatante': 'éclatante', 'eclate': 'éclate', 'effondrement': 'effondrement', 'egale': 'égale', 'ejecte': 'éjecte', 'ejectes': 'éjectés', 'electricite': 'électricité', 'electrique': 'électrique', 'electriques': 'électriques', 'electrise': 'électrisé', 'electrises': 'électrisés', 'electrocution': 'électrocution', 'electrofleche': 'électroflèche', 'elements': 'éléments', 'elimine': 'élimine', 'eliminee': 'éliminée', 'embleme': 'emblème', 'emeraude': 'émeraude', 'emet': 'émet', 'enchantes': 'enchantés', 'energie': 'énergie', 'enferme': 'enfermé', 'enflammee': 'enflammée', 'enorme': 'énorme', 'envolee': 'envolée', 'epee': 'épée', 'ephemeres': 'éphémères', 'epine': 'épine', 'epines': 'épines', 'eponge': 'éponge', 'equipe': 'équipe', 'equipees': 'équipées', 'equipement': 'équipement', 'etait': 'était', 'etend': 'étend', 'eternelle': 'éternelle', 'etincelante': 'étincelante', 'etouffement': 'étouffement', 'etre': 'être', 'evolue': 'évolue', 'execution': 'exécution', 'fige': 'fige', 'fleche': 'flèche', 'fleches': 'flèches', 'geante': 'géante', 'grise': 'grise', 'hades': 'hadès', 'immobilise': 'immobilisé', 'immobilises': 'immobilisés', 'immortalite': 'immortalité', 'immunise': 'immunisé', 'immunite': 'immunité', 'imperissable': 'impérissable', 'incassables': 'incassables', 'infliges': 'infligés', 'instantanement': 'instantanément', 'invoquees': 'invoquées', 'invulnerable': 'invulnérable', 'jetee': 'jetée', 'legue': 'lègue', 'lumiere': 'lumière', 'loyaute': 'loyauté', 'machoire': 'mâchoire', 'maitresse': 'maîtresse', 'malefique': 'maléfique', 'marquees': 'marquées', 'mecanique': 'mécanique', 'meme': 'même', 'memes': 'mêmes', 'merite': 'mérite', 'meritee': 'méritée', 'moitie': 'moitié', 'nausee': 'nausée', 'nuee': 'nuée', 'obscurite': 'obscurité', 'perdue': 'perdue', 'petales': 'pétales', 'portee': 'portée', 'possede': 'possède', 'projete': 'projeté', 'projetes': 'projetés', 'provoques': 'provoqués', 'rapiere': 'rapière', 'rapproche': 'rapproché', 'reapparaitre': 'réapparaître', 'recompense': 'récompense', 'recu': 'reçu', 'recupere': 'récupère', 'regagne': 'regagne', 'regenere': 'régénère', 'regenerent': 'régénèrent', 'regenerer': 'régénérer', 'relache': 'relâche', 'relachement': 'relâchement', 'renvoyes': 'renvoyés', 'resistance': 'résistance', 'ressuscite': 'ressuscite', 'retiree': 'retirée', 'revele': 'révèle', 'ruee': 'ruée', 'sante': 'santé', 'seule': 'seule', 'sphere': 'sphère', 'strategie': 'stratégie', 'superieure': 'supérieure', 'supplementaire': 'supplémentaire', 'supplementaires': 'supplémentaires', 'surmultipliee': 'surmultipliée', 'systeme': 'système', 'teleporte': 'téléporte', 'telesquive': 'télesquive', 'telesquives': 'télesquives', 'tempete': 'tempête', 'tenebres': 'ténèbres', 'trainee': 'traînée', 'traitre': 'traître', 'tres': 'très', 'trigonometrique': 'trigonométrique', 'troisieme': 'troisième', 'utilises': 'utilisés', 'verrouillee': 'verrouillée', 'violette': 'violette', 'voles': 'volés', 'vraie': 'vraie', 'ecran': 'écran', 'elle': 'elle', 'frappe': 'frappe', 'lancees': 'lancées', 'marques': 'marques', 'premiere': 'première', 'derniers': 'derniers', 'entierement': 'entièrement', 'deja': 'déjà', 'ete': 'été', 'periode': 'période', 'precedente': 'précédente', 'reussit': 'réussit', 'necessaire': 'nécessaire', 'esquive': 'esquive', 'interieur': 'intérieur', 'exterieur': 'extérieur', 'reduit': 'réduit', 'reduite': 'réduite', 'reduits': 'réduits', 'genere': 'génère', 'generee': 'générée', 'degage': 'dégage', 'debut': 'début', 'debloque': 'débloque', 'debloquees': 'débloquées', 'enchainement': 'enchaînement', 'eloigne': 'éloigne', 'eleve': 'élève', 'evite': 'évite', 'etat': 'état', 'etats': 'états', 'detruit': 'détruit', 'detruire': 'détruire', 'depense': 'dépense', 'depasse': 'dépasse', 'decoupe': 'découpe', 'decouvre': 'découvre', 'declenche': 'déclenche', 'declencher': 'déclencher', 'demarre': 'démarre', 'deplacement': 'déplacement', 'desactiver': 'désactiver', 'desactive': 'désactive', 'arrive': 'arrive', 'celle': 'celle', 'Livai': 'Livaï', 'Aida': 'Aïda'}


def accent(text):
    """Les textes du plugin sont sans accents (polices du jeu) : on les rend au guide."""
    def fix(m):
        w = m.group(0)
        r = ACCENTS.get(w.lower())
        if not r:
            return w
        return r[0].upper() + r[1:] if w[0].isupper() else r
    text = re.sub(r"[A-Za-z]+", fix, text)
    # "a" preposition : devant une distance, un article ou "moins", c'est "à".
    return re.sub(r"\b([Aa]) (?=(moins|tous|toutes|tout|l'|la |le |les |\d|son |sa |ses |chaque|cote|côté|travers|leur|partir|nouveau|usage|sa |portée|distance|peine|elle|lui|eux|vie|mort|chaque|droite|gauche|terre|hauteur|volonté|jamais|nouveau)\b)", lambda m: ("À" if m.group(1) == "A" else "à") + " ", text)


def ability_block(a, depth, video_root):
    """Une capacité à la manière de l'ancien GitBook : titre, (type) description, temps de chargement, vidéo."""
    lines = [f"{'#' * depth} {accent(a['name'])}", ""]
    kind = "" if a["type"] == "Ultime" else f"({a['type']}) "
    lines += [kind + accent(a["description"]), ""]
    if a["cooldown"]:
        lines += [f"Temps de chargement : {duration(a['cooldown'])}", ""]
    clip = VIDEOS / a["character"] / f"{a['id']}.mp4"
    if clip.exists():
        rel = "../" * video_root + f"videos/{a['character']}/{a['id']}.mp4"
        raw = f"https://github.com/kiokouwhite/crossfight-uhc/raw/main/guide/videos/{a['character']}/{a['id']}.mp4"
        lines += [f'{{% file src="{rel}" %}}', f"{accent(a['name'])} en jeu", "{% endfile %}", "", f"[▶ Voir la vidéo]({raw})", ""]
    return lines


def characters_pages(summary):
    """Une page par format, par licence et par personnage, comme l'ancien GitBook."""
    data = json.loads(DATA.read_text(encoding="utf-8"))
    base = OUT / "personnages"
    index = ["# Les personnages", "",
             "Chaque équipe reçoit une licence tirée au sort parmi celles de son format, puis chacun y choisit son personnage.", "",
             "Chaque personnage suit une voie (Destruction, Harmonie, Chasse ou Nihilité), a une arme, des pouvoirs et un ultime. "
             "Les pouvoirs actifs s'utilisent au clic droit, objet en main ; l'ultime se charge en donnant et en recevant des dégâts. "
             "Sous chaque pouvoir, une courte vidéo le montre en jeu.", ""]
    count = 0
    for fmt in ("TRIO", "DUO", "SOLO"):
        fslug, ftitle = FORMAT_PAGES[fmt]
        licences = [l for l in data if l["team"] == fmt]
        if not licences:
            continue
        summary.append(f"  * [{ftitle}](personnages/{fslug}.md)")
        index += [f"## [{ftitle}]({fslug}.md)", ""]
        fpage = [f"# {ftitle}", "", FORMAT_TEXT[fmt], ""]
        for l in licences:
            lname = licence_title(l["name"])
            lslug = slug(lname)
            people = l["characters"]
            # Ce que tous les personnages de la licence ont en commun (épée d'aura, boutique...) va sur la page de la licence.
            shared = [a for a in people[0]["abilities"] if all(any(b["id"] == a["id"] for b in c["abilities"]) for c in people)] if len(people) > 1 else []
            shared_ids = {a["id"] for a in shared}
            if fmt == "SOLO":
                lpath, depth = f"{fslug}", 1
            else:
                summary.append(f"    * [{lname}](personnages/{fslug}/{lslug}.md)")
                lpath, depth = f"{fslug}/{lslug}", 2
                lpage = [f"# {lname}", ""]
                if l.get("particularite"):
                    lpage += ["## Particularité", "", accent(l["particularite"]), ""]
                if shared:
                    lpage[len(lpage):] = ["## Commun à la licence", ""]
                    for a in shared:
                        lpage += ability_block(dict(a, character=people[0]["id"]), 3, 2)
                lpage += ["## Personnages", ""] + [f"* [{c['name']}]({lslug}/{slug(c['name'])}.md) : {accent(c["voie"])}" for c in people] + [""]
                (base / fslug).mkdir(parents=True, exist_ok=True)
                (base / fslug / f"{lslug}.md").write_text("\n".join(lpage), encoding="utf-8")
                fpage.append(f"* [{lname}]({fslug}/{lslug}.md) : " + ", ".join(c["name"] for c in people))
                index.append(f"* [{lname}]({fslug}/{lslug}.md) : " + ", ".join(c["name"] for c in people))
            for c in people:
                count += 1
                cslug = slug(c["name"])
                own = [dict(a, character=c["id"]) for a in c["abilities"] if a["id"] not in shared_ids]
                weapon = [a for a in own if a["weapon"]]
                ult = [a for a in own if a["type"] == "Ultime"]
                powers = [a for a in own if not a["weapon"] and a["type"] != "Ultime"]
                page = [f"# {accent(c['name'])}", "", accent(c["voie"]), ""]
                if weapon or c.get("weapon"):
                    page += ["## Arme", "", f"### {accent(c.get('weapon') or 'Arme')}", ""]
                    for a in weapon:
                        block = ability_block(a, 4, depth + 1)
                        page += block[2:] if accent(a["name"]) == accent(c.get("weapon") or "") else block
                if powers:
                    page += ["## Pouvoirs", ""]
                    for a in powers:
                        page += ability_block(a, 3, depth + 1)
                if ult:
                    page += ["## Ultime", ""]
                    for a in ult:
                        page += ability_block(a, 3, depth + 1)
                folder = base / lpath if fmt != "SOLO" else base / fslug
                folder.mkdir(parents=True, exist_ok=True)
                (folder / f"{cslug}.md").write_text("\n".join(page), encoding="utf-8")
                rel = f"{lpath}/{cslug}.md"
                summary.append(("      " if fmt != "SOLO" else "    ") + f"* [{c['name']}](personnages/{rel})")
                if fmt == "SOLO":
                    fpage.append(f"* [{c['name']}]({fslug}/{cslug}.md) : {accent(c["voie"])}")
                    index.append(f"* [{c['name']}]({fslug}/{cslug}.md)")
        index.append("")
        (base / f"{fslug}.md").write_text("\n".join(fpage) + "\n", encoding="utf-8")
    (base / "README.md").write_text("\n".join(index) + "\n", encoding="utf-8")
    return count


def main():
    videos = None
    if VIDEOS.exists():          # les vidéos ne se refont pas : on les garde de côté pendant qu'on regénère
        videos = OUT.parent / ".guide_videos_tmp"
        shutil.move(str(VIDEOS), str(videos))
    if OUT.exists():
        shutil.rmtree(OUT)
    (OUT / "personnages").mkdir(parents=True)
    if videos:
        shutil.move(str(videos), str(VIDEOS))
    for name, text in PAGES.items():
        (OUT / name).write_text(text.replace("%LOOTBOX%", lootbox_table()), encoding="utf-8")
    summary = ["# Sommaire", "", "* [Crossfight UHC](README.md)", "* [Rejoindre une partie](commencer.md)", "* [Le déroulement d'une partie](deroulement.md)",
               "* [Les lieux et les quêtes d'Anna](lieux.md)", "* [Les événements](evenements.md)", "* [Les personnages](personnages/README.md)"]
    count = characters_pages(summary)
    summary.append("* [Les commandes du joueur](commandes.md)")
    (OUT / "SUMMARY.md").write_text("\n".join(summary) + "\n", encoding="utf-8")
    clips = len(list(VIDEOS.rglob("*.mp4"))) if VIDEOS.exists() else 0
    print(f"guide/ : {count} personnages, {clips} vidéos")


if __name__ == "__main__":
    main()
