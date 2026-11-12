# Sukrara — brochure commerciale

*Pièce jointe à la déclaration de fourniture d'un moyen de cryptologie (ANSSI). Version décrite : 0.14.3
(novembre 2026). Le texte suit la présentation publique du produit (README du dépôt, fiches Google Play et Mac App
Store).*

## Le produit

**Sukrara** (« ton sucre, maintenant ») est une application gratuite et libre pour les personnes atteintes de diabète
et leurs proches. Elle affiche la glycémie en direct, sonne une alerte quand elle est trop basse, trop haute, change
vite ou n'arrive plus, et la partage avec les personnes qui s'occupent du patient, **chiffrée de bout en bout**. Il
n'y a aucun compte à créer et aucun serveur à héberger.

Le nom vient du catalan : *sucre* (sucre) + *ara* (maintenant).

Sukrara **n'est pas un dispositif médical** : il ne diagnostique, ne traite, ne guérit ni ne prévient aucune maladie,
et ne doit pas servir à prendre des décisions de traitement.

## Ce qu'il fait pour la personne

**Sur le téléphone Android de la personne qui porte le capteur**

- Lit directement un capteur de glucose **FreeStyle Libre 3 / 3 Plus** (démarrage par NFC, puis Bluetooth), ou les
  valeurs de l'application Juggluco. La prise en charge de certains capteurs Dexcom, Accu-Chek SmartGuide et Aidex est
  expérimentale et non testée.
- Affiche la valeur, sa tendance et une courbe sur 31 jours : écran d'accueil, widgets, notification permanente,
  écran de verrouillage.
- Alertes de glycémie très basse, basse, haute, très haute, de variation rapide et d'absence de données, avec seuils
  et sons réglables ; une hypoglycémie sévère sans réponse peut prévenir les proches.
- Saisie rapide de l'insuline, des glucides et de notes ; lecture des doses d'un stylo à insuline compatible (NFC).
- Rapport de glycémie (temps dans la cible, moyenne, GMI, variabilité) et export CSV.
- Envoi, si la personne l'active, vers LibreView, Tidepool, Nightscout, Health Connect, d'autres applications du
  téléphone (xDrip+, AAPS…) et des montres Garmin.

**Pour les proches (téléphone Android ou Mac)**

- Suivre la glycémie d'une personne après son invitation (lien, QR code ou appairage Bluetooth à proximité avec un
  code à 6 chiffres à comparer). La personne approuve chaque proche et peut le retirer.
- Sur Mac, Sukrara vit dans la **barre des menus** : la valeur dans la couleur de sa plage, un panneau avec la courbe,
  des widgets de bureau et les mêmes alertes que sur le téléphone.
- Les données voyagent **chiffrées de bout en bout** du téléphone de la personne jusqu'à l'appareil du proche, par
  Internet (relais publics Nostr) ou directement par Bluetooth à proximité, sans Internet. Personne d'autre, ni le
  développeur ni les relais, ne peut les lire.

**Vie privée**

- Tout reste sur le téléphone tant que la personne n'active pas une connexion.
- Ni publicité, ni statistiques d'usage, ni pistage. Le développeur ne reçoit aucune donnée.
- Politique de confidentialité : https://sukrara.mauri.app/privacy

## Plateformes

| Application | Configuration requise | Rôle |
|---|---|---|
| Sukrara pour Android | Android 16 ou plus récent, téléphone 64 bits (arm64) ; NFC et Bluetooth pour lire un capteur | lire le capteur, alerter, partager ; ou suivre un proche |
| Sukrara pour Mac | Mac à puce Apple, macOS 15 ou plus récent | suivre un proche depuis la barre des menus |

Langues : anglais et espagnol.

## Canaux de distribution

| Canal | Application | Mises à jour |
|---|---|---|
| **Google Play** (en préparation) | Android | par Google Play |
| **Mac App Store** (en préparation) | Mac | par le Mac App Store |
| **GitHub Releases** : https://github.com/mauriciabad/sukrara/releases | Android (APK) et Mac (dmg signé Developer ID et notarisé par Apple) | l'application se met à jour d'elle-même (paquet vérifié avant installation) |
| **Homebrew** : `brew install --cask mauriciabad/tap/sukrara` | Mac | comme la version GitHub |

Les versions 0.x sont des préversions (« prereleases » sur GitHub).

## Prix et licence

- **Gratuit** sur tous les canaux, sans achat intégré ni abonnement.
- **Logiciel libre** sous licence GPL-3.0-or-later. Code source complet : https://github.com/mauriciabad/sukrara
- Éditeur : Maurici Abad, développeur indépendant (personne physique).
- Site web : https://sukrara.mauri.app

## Marques

FreeStyle Libre et LibreView sont des marques d'Abbott. Sukrara n'est affilié ni à Abbott ni à aucun fabricant de
capteurs, ni à Juggluco. Les autres noms de produits appartiennent à leurs propriétaires.
