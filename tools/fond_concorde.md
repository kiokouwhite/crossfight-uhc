# Concorde stellaire

*Le fond algorithmique des vidéos de présentation des personnages (`tools/fond_concorde.js`).*

Des milliers de lumières naissent désaccordées et finissent par battre ensemble. Chaque particule est un oscillateur :
elle porte une phase qui tourne à sa propre fréquence, à peine différente de celle des autres, et son éclat suit cette
phase. Avant que le personnage n'arrive, le champ scintille comme une nuée de lucioles, chacune sur son propre rythme.
Le sujet de l'œuvre n'est pas une image mais un passage : le passage du désordre à l'accord, calculé pas à pas,
jamais dessiné à l'avance.

La montée est une colonne d'air. Un courant ascendant, gaussien autour de l'axe de l'écran, aspire le chœur des
particules depuis sous le bord inférieur ; un champ d'angles tiré d'un bruit de Perlin qui dérive lentement leur
imprime des remous, de sorte qu'aucune trajectoire n'est droite et qu'aucune ne ressemble à une autre. Des braises plus
lointaines, plus petites et plus lentes, montent en parallaxe derrière elles. Chaque constante (la largeur de la
colonne, la viscosité qui ramène chaque vitesse vers le courant, l'échelle du bruit) a été réglée et revérifiée image
par image, comme seul le ferait un artisan du calcul qui sait qu'une valeur à peine fausse transforme la grâce en bruit.

L'impact est une onde de choc qui se propage à vitesse finie. Quand son front atteint une particule, il la projette
vers l'extérieur et remet sa phase à zéro : la lumière se rallume là où le choc passe, et la synchronisation part du
centre en anneaux. Le couplage du modèle de Kuramoto prend alors le relais : chaque oscillateur est attiré vers la
phase moyenne du chœur, avec une force qui culmine juste après le choc puis se stabilise au-dessus du seuil critique.
Les ondulations se resserrent d'elles-mêmes en une seule pulsation commune. Ce n'est pas une animation de pulsation,
c'est un accord qui émerge d'un calcul conçu avec un soin extrême, où le seuil, la dispersion des fréquences et la
durée du pic ont été choisis pour que l'accord arrive exactement quand le regard se pose.

Puis vient le halo. Le chœur se range sur trois orbites elliptiques dont les rayons sont dans les rapports d'intervalles
purs (quinte, quarte, unisson) et dont les vitesses suivent la troisième loi de Kepler : plus l'orbite est intérieure,
plus elle tourne vite, et l'orbite médiane tourne à contre-sens. Des nœuds régulièrement répartis sur l'orbite
extérieure ralentissent la course des particules qui les traversent ; la densité s'y accumule et ils s'allument comme
des éclats. Les forces sont un ressort amorti vers l'orbite et un alignement sur la vitesse orbitale : l'ordre n'est
pas imposé, il est atteint, et chaque graine le compose autrement.

Le rendu est additif. Les traînées s'accumulent sur une couche noire qui s'efface d'une fraction à chaque pas, de sorte
que la lumière garde la mémoire du mouvement ; la couleur part de la teinte du personnage et blanchit avec l'éclat et
la vitesse ; la lueur du fond respire au rythme de la pulsation commune. La simulation avance par pas fixes et tire tout
son hasard d'une graine : la même graine donne la même image au même instant, à la vidéo comme dans la visionneuse.
C'est la marque d'un algorithme mené jusqu'à son terme : une maîtrise patiente du calcul, au service d'un seul instant
où tout s'accorde.
