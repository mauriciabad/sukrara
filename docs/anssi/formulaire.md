# Sukrara — réponses pour la déclaration ANSSI

*Réponses prêtes à coller dans le formulaire « Déclaration relative à un moyen de cryptologie » (ANSSI,
demarche.numerique.gouv.fr), pour la **fourniture** de Sukrara en France. Les numéros suivent la notice du
formulaire en ligne (2.1 à 2.4) ; entre crochets, la section correspondante de l'annexe I de l'arrêté du 29 janvier
2015 (B.1 à B.3, C). La partie « déclarant » (identité, adresse, contact) est remplie par le titulaire et n'est pas
ici. Pièces jointes : [brochure commerciale](brochure-commerciale.md) et [brochure technique](brochure-technique.md),
à exporter en PDF.*

*À vérifier avant l'envoi : la version (elle change à chaque publication) et les points de la section
[Incertitudes](#incertitudes).*

## 2.1 Informations générales [B.1]

| Champ | Réponse |
|---|---|
| Marque | Sukrara |
| Dénomination du moyen | Sukrara |
| Désignation générique (format « MARQUE - DÉNOMINATION ») | Sukrara - Sukrara (applications Android et macOS) |
| Version | 0.14.3 (fichier `VERSION` du dépôt, novembre 2026). La version change à chaque publication (0.x : préversions) ; la cryptographie décrite est la même dans toutes les versions 0.12 et suivantes. |
| Référence commerciale | `app.mauri.sukrara` (identifiant d'application Android et identifiant de bundle macOS) |
| Date de mise sur le marché | 11/11/2026 : version 0.12.0, première publication des applications Sukrara actuelles (GitHub, préversion). Google Play et Mac App Store : en préparation, pas encore publiés. Voir [Incertitudes](#incertitudes) pour les préversions antérieures. |
| Fabricant / dénomination d'origine | Sans objet : le déclarant est le développeur du logiciel. |

## 2.2 Description fonctionnelle [B.2]

**Catégorie [B.2.1]** : Logiciel.

**Description générale du moyen [B.2.2]** :

> Sukrara est une application gratuite et libre (GPL-3.0) de suivi de la glycémie pour les personnes diabétiques et
> leurs proches, pour Android (16 et plus) et macOS (15 et plus). Sur le téléphone de la personne, elle lit un capteur
> de glucose en continu (FreeStyle Libre 3 par NFC et Bluetooth), affiche la glycémie et sa tendance, sonne des
> alertes (glycémie basse, haute, variation rapide, absence de données) et permet de noter l'insuline et les glucides.
> Elle partage ces données avec les proches que la personne approuve (sur leur téléphone Android ou dans la barre des
> menus de leur Mac), chiffrées de bout en bout, par des relais publics Nostr ou par Bluetooth à proximité, sans
> compte ni serveur du développeur. Elle peut aussi envoyer les données, à la demande de la personne, à LibreView,
> Tidepool ou Nightscout (HTTPS). La cryptographie n'est pas la fonction du produit : elle protège la confidentialité
> et l'authenticité des données de santé partagées, la clé d'identité de la personne et la liaison avec le capteur.
> Les algorithmes et paramètres sont fixés dans le code et ne sont pas modifiables par l'utilisateur. Distribution
> gratuite : Google Play, Mac App Store, GitHub Releases, Homebrew. Ce n'est pas un dispositif médical.

**Catégorie de la fonction principale [B.2.3]** : Autres. Précision :

> Santé : suivi de la glycémie et alertes pour personnes diabétiques, partage avec les proches.

## 2.3 Description technique [B.3]

**Description des fonctionnalités cryptographiques [B.3.1]** :

> 1. Partage chiffré de bout en bout entre le téléphone de la personne et les appareils de ses proches (protocole
>    Nostr) : chiffrement NIP-44 v2 (ECDH secp256k1, HKDF-SHA256, ChaCha20, HMAC-SHA256) de chaque donnée vers une clé
>    de lecture de groupe, renouvelée à chaque retrait d'un proche ; messages privés (invitation, demande d'accès,
>    livraison de clé) en NIP-59 (sceau signé puis enveloppe chiffrée par une clé jetable) ; chaque événement signé en
>    Schnorr BIP-340 et vérifié par le destinataire ; authentification auprès des relais (NIP-42) et preuve d'identité
>    sur la liaison Bluetooth par la même signature ; clé d'invitation dérivée en HKDF-SHA256 ; codes de vérification à
>    4 et 6 chiffres par SHA-256. Bibliothèque : rust-nostr nostr-sdk 0.45.1 (libsecp256k1, crates RustCrypto).
> 2. Code de récupération de la clé d'identité (Android) : NIP-49, scrypt (N = 2^18) puis XChaCha20-Poly1305.
> 3. Secrets stockés sur l'appareil : Android Keystore (AES-256-GCM, clé non exportable, chiffrement réalisé par le
>    système) ; trousseau macOS (système).
> 4. Liaison avec le capteur de glucose (Android), protocoles imposés par les fabricants : Libre 3 (accord de clé sur
>    courbe P-256, défi-réponse par un chiffrement du fabricant en tables « boîte blanche », données AES-128-CCM,
>    HMAC-SHA1 de l'autorisation conservée ; cœur libre de Juggluco et TinyCrypt) ; Dexcom G6 (défi AES-128-ECB) et
>    Aidex X (MD5, AES-128-CFB), non testés ; appairage Bluetooth du système pour Dexcom, Accu-Chek SmartGuide et Aidex.
> 5. Connexions : TLS 1.2 / 1.3 du système (Android Conscrypt via OkHttp, macOS URLSession) vers les relais, LibreView,
>    Tidepool, Nightscout et GitHub.
> 6. Mises à jour (versions GitHub) : SHA-256 et certificat de signature de l'APK (Android) ; signature EdDSA Ed25519
>    Sparkle 2.10.1 (Mac).
> Aucun algorithme, mode ou taille de clé n'est réglable par l'utilisateur ; aucune interface cryptographique n'est
> offerte à d'autres logiciels.

**Catégories de fonctions cryptographiques [B.3.2]** — cocher les quatre :

- [x] Authentification : signatures BIP-340 des événements, AUTH NIP-42, preuve d'identité Bluetooth, défis des
  capteurs.
- [x] Intégrité : HMAC-SHA256 (NIP-44), AES-GCM, Poly1305, AES-CCM, SHA-256 des mises à jour.
- [x] Confidentialité : ChaCha20 (NIP-44), XChaCha20 (NIP-49), AES-256-GCM (Keystore), AES-128-CCM / CFB (capteurs),
  TLS.
- [x] Signature : Schnorr BIP-340 (création et vérification), Ed25519 (vérification des mises à jour Mac).

**Protocoles sécurisés [B.3.3]** :

- [x] SSL/TLS : TLS 1.2 et 1.3 fournis par le système (Android, macOS), suites par défaut du système.
- [ ] IPsec, [ ] SSH, [ ] VoIP (SIP/RTP) : non.
- [x] Autres. Précision :

> Nostr : NIP-01 (événements signés Schnorr BIP-340), NIP-44 v2 (chiffrement), NIP-59 (gift wrap), NIP-42
> (authentification aux relais), NIP-49 (clé chiffrée par mot de passe). Protocoles Bluetooth Low Energy des capteurs de
> glucose (Abbott Libre 3, Dexcom G6, Aidex X) et appairage Bluetooth du système.

## 2.4 Algorithmes [B.3.4]

Partie A : cryptographie mise en œuvre par Sukrara ou par les bibliothèques qu'il intègre.

| Algorithme | Mode | Taille de clé associée | Fonction |
|---|---|---|---|
| ECDH secp256k1 | accord de clé (NIP-44 v2) | 256 bits | Confidentialité : clé de conversation entre l'émetteur et la clé de lecture du groupe ou le destinataire |
| HKDF-SHA256 (RFC 5869) | extraction / expansion | 256 bits | Dérivation des clés de message NIP-44 ; dérivation de la clé d'invitation |
| ChaCha20 (RFC 8439) | flot, nonce 96 bits | 256 bits | Confidentialité des données de santé partagées et des messages privés (NIP-44 v2, NIP-59) |
| HMAC-SHA256 | chiffrer-puis-authentifier | 256 bits | Intégrité et authenticité des messages NIP-44 |
| Schnorr BIP-340 sur secp256k1 (avec SHA-256) | signature | 256 bits | Signature et authentification de chaque événement, des sceaux NIP-59, de l'AUTH NIP-42 et de la preuve Bluetooth |
| scrypt | dérivation depuis un mot de passe, N = 2^18, r = 8, p = 1 | clé dérivée 256 bits | Code de récupération NIP-49 (Android) |
| XChaCha20-Poly1305 | AEAD, nonce 192 bits | 256 bits | Chiffrement de la clé d'identité dans le code de récupération NIP-49 (Android) |
| SHA-256 | hachage | sans objet | Identifiants des événements, étiquettes opaques, codes de vérification à 4 et 6 chiffres, contrôle des mises à jour Android |
| Ed25519 (EdDSA) | vérification de signature | 256 bits | Authenticité des mises à jour Mac (Sparkle, version GitHub) |
| ECC P-256 (protocole Libre 3) | accord de clé à clés éphémères | 256 bits | Appairage avec le capteur Libre 3 (Android) |
| Chiffrement de défi Libre 3 (algorithme par bloc du fabricant, en tables « boîte blanche ») | défi-réponse authentifié | non déterminée ; transporte une clé de session de 128 bits | Authentification mutuelle avec le capteur Libre 3 et transport de la clé de session (Android) |
| AES-128 | CCM, nonce 13 octets, étiquette 32 bits | 128 bits | Confidentialité et intégrité des données du capteur Libre 3 (Android) |
| HMAC-SHA1 | MAC | 128 bits | Intégrité de l'autorisation Libre 3 conservée sur le téléphone (Android) |
| AES-128 | ECB (bloc unique, défi-réponse) | 128 bits | Authentification auprès d'un émetteur Dexcom G6 / ONE (Android, non testé) |
| AES-128 | CFB 128 | 128 bits | Confidentialité des commandes et de la clé de session Aidex X (Android, non testé) |
| MD5 | hachage | sans objet | Dérivation de la clé et de l'IV Aidex X depuis le numéro de série (protocole du fabricant) |
| SHA-1 | hachage | sans objet | Secret d'API Nightscout / Juggluco envoyé sous forme de SHA-1 (convention de ces logiciels) ; identifiants de documents |

Partie B : cryptographie fournie par le système d'exploitation, que Sukrara utilise sans la mettre en œuvre.

| Algorithme | Mode | Taille de clé associée | Fonction |
|---|---|---|---|
| AES-256 (Android Keystore) | GCM, IV 96 bits, étiquette 128 bits | 256 bits | Chiffrement des secrets stockés sur le téléphone (clés, identifiants de services, appairage du capteur) |
| TLS 1.2 / 1.3 (Android Conscrypt, macOS) | suites par défaut du système | selon la suite négociée | Connexions aux relais Nostr, LibreView, Tidepool, Nightscout, GitHub |
| Appairage Bluetooth LE (Android) | AES-CCM ; ECDH P-256 en LE Secure Connections | 128 bits | Liaison chiffrée avec les capteurs Dexcom, Accu-Chek SmartGuide et Aidex |
| Trousseau macOS | celui du système | celle du système | Clé du proche et secret Juggluco sur Mac |

## Classement « grand public » (catégorie 3 de l'annexe 2 du décret 2007-663) [C]

*Ce n'est pas un avis juridique : les critères du texte et la situation de Sukrara face à chacun.*

**Le texte.** Décret n° 2007-663 du 2 mai 2007, annexe 2 (« Opérations de transfert vers un État membre de la
Communauté européenne ou d'exportation soumises à déclaration »), point B, **catégorie 3** : « Moyens de cryptologie
relevant des catégories 1 ou 2 de la présente annexe et pour lesquels toutes les conditions ci-après sont
remplies » :

- a) ils « sont couramment à la disposition du public en étant vendus directement sur stock, sans restriction, à des
  points de vente au détail, que cette vente soit effectuée en magasin, par correspondance, par transaction
  électronique ou par téléphone » ;
- b) « la fonctionnalité cryptographique ne peut pas être modifiée facilement par l'utilisateur » ;
- c) ils « sont conçus pour être installés par l'utilisateur sans assistance ultérieure importante de la part du
  fournisseur ».

L'ANSSI appelle ce classement « grand public » (point 3 de l'annexe II) : le fournisseur le demande dans la
déclaration (case de la section C du formulaire, arrêté du 29 janvier 2015, annexe I), l'ANSSI le valide, et il
facilite ensuite l'exportation. Sources : [Légifrance, annexe 2 du décret 2007-663](https://www.legifrance.gouv.fr/codes/section_lc/JORFTEXT000000646995/LEGISCTA000006117886/),
[décret 2007-663](https://www.legifrance.gouv.fr/loda/id/JORFTEXT000000646995),
[arrêté du 29 janvier 2015](https://www.legifrance.gouv.fr/loda/id/JORFTEXT000030255024/).

**Catégorie 1 (prérequis).** Sukrara n'assure pas que de l'authentification ou de l'intégrité (il chiffre des
données) et emploie un algorithme symétrique de plus de 56 bits (ChaCha20, 256 bits) et un algorithme asymétrique sur
un groupe de plus de 112 bits (courbe secp256k1, 256 bits). Il relève donc de la catégorie 1 de l'annexe 2. Aucune
dispense de l'annexe 1 du décret ne semble s'appliquer (la catégorie 13, « aucun algorithme au-delà des seuils »,
non plus).

**Sukrara face aux trois conditions.**

| Condition | Situation de Sukrara | Appréciation |
|---|---|---|
| a) à la disposition du public, sans restriction, par transaction électronique | Téléchargement libre par tous sur Google Play, le Mac App Store, GitHub et Homebrew, sans compte ni restriction. Mais il est **gratuit** : il n'est pas « vendu ». | Remplie sur la disponibilité ; le texte dit « vendus », l'ANSSI juge si la distribution gratuite dans les boutiques d'applications compte. |
| b) fonctionnalité cryptographique non modifiable facilement par l'utilisateur | Algorithmes, modes, tailles de clé et protocoles fixés dans le code ; aucun réglage ; binaires signés (Google Play, Apple, clé de publication). L'utilisateur choisit seulement d'activer le partage, les relais et un mot de passe de récupération. Mais le **code source est public (GPL-3.0)** : quiconque sait compiler peut produire une version modifiée. | Remplie pour le produit distribué ; le point à faire trancher par l'ANSSI est l'effet du code ouvert. |
| c) installable par l'utilisateur sans assistance importante du fournisseur | Installation standard depuis la boutique ou par glisser-déposer (Mac) ; aucune intervention du développeur. | Remplie. |

**Conclusion factuelle.** Le classement peut être **demandé** : les conditions b et c correspondent à Sukrara, et la
condition a correspond à la disponibilité publique sans restriction. Les deux points d'appréciation sont la gratuité
(« vendus ») et le code ouvert (« modifiée facilement »). La décision revient à l'ANSSI.

## Délai et calendrier

Décret 2007-663, art. 4 : le dossier est déposé « un mois au moins avant l'opération » ; art. 5 : sans réponse de
l'ANSSI dans le mois, le déclarant « peut procéder librement aux opérations faisant l'objet de la déclaration ». Les
préversions sur GitHub et Homebrew sont déjà téléchargeables depuis la France (depuis le 11/11/2026 pour la version
0.12.0) ; la disponibilité en France sur le Mac App Store et Google Play n'est pas encore ouverte.

## Incertitudes

Points non vérifiés ou qui demandent un choix du titulaire :

- **Date de mise sur le marché.** Avant les applications actuelles, le prototype de recherche a été publié sous le nom
  Sukrara, avec le même principe de partage Nostr chiffré, dans un autre dépôt GitHub
  (`mauriciabad/cgm-reading-mac-spike`, versions 0.10.1 du 16/10/2026 à 0.11.52 du 25/10/2026, d'après les
  étiquettes git). Vérifier sur la page des publications GitHub si ces préversions ont été publiées et laquelle
  déclarer comme première mise sur le marché.
- **Marque.** « Sukrara » n'est pas, à notre connaissance, une marque déposée ; le champ demande la marque de
  distribution, qui est ici le nom du produit.
- **Chiffrement de défi Libre 3.** Le cœur de Juggluco reconstitue le protocole du fabricant sous forme de tables
  (« boîte blanche ») sans nommer l'algorithme : son nom exact et la taille de sa clé ne sont pas déterminés. Le reste
  du protocole Libre 3 (P-256, AES-128-CCM, HMAC-SHA1) est lisible dans le code.
- **Version de nostr-sdk-swift.** La version 0.45.1 est fixée dans `Package.swift` ; les crates Rust listées ont été
  lues dans les binaires Android et JVM/macOS 0.45.1, pas dans le XCFramework Swift (même cœur rust-nostr 0.45.1).
- **Suites TLS.** Sukrara ne les choisit pas : ce sont celles d'Android et de macOS (TLS 1.2 minimum par défaut pour
  OkHttp 4.12 et pour macOS).
