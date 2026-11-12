# Sukrara — brochure technique : la cryptographie

*Pièce jointe à la déclaration de fourniture d'un moyen de cryptologie (ANSSI). Version décrite : 0.14.3
(novembre 2026). Chaque point renvoie au code du dépôt https://github.com/mauriciabad/sukrara (chemins relatifs à sa
racine). Les spécifications du protocole sont dans `contracts/protocol/PROTOCOL-v2.md` et
`contracts/protocol/PROTOCOL-identity.md`.*

## 1. Vue d'ensemble

Sukrara n'est pas un produit de sécurité : sa fonction est le suivi de la glycémie. La cryptographie y sert à cinq
choses :

1. **Partage chiffré de bout en bout** des données de santé entre le téléphone de la personne et ses proches
   (protocole Nostr : NIP-44 v2, NIP-59, signatures BIP-340), sur des relais publics ou par Bluetooth.
2. **Sauvegarde de la clé d'identité** (code de récupération NIP-49, Android).
3. **Protection des secrets locaux** (Android Keystore, trousseau macOS), fournie par le système.
4. **Liaison avec le capteur de glucose** (Android) : protocoles imposés par chaque fabricant de capteur.
5. **Connexions réseau** (TLS, fourni par le système) et **vérification des mises à jour**.

L'utilisateur ne choisit ni les algorithmes, ni les tailles de clé, ni les modes : tout est fixé dans le code. Il
peut seulement activer ou non le partage, choisir les relais (transport) et, pour le code de récupération, un mot de
passe.

Aucun algorithme n'est conçu par le projet. Le projet appelle des bibliothèques reconnues ou les services du système,
sauf deux exceptions écrites dans le code de Sukrara à partir de primitives du système : la dérivation HKDF de la clé
d'invitation (Android) et les protocoles des capteurs Dexcom G6 et Aidex X (section 5).

## 2. Bibliothèques cryptographiques intégrées

| Bibliothèque | Version | Où | Ce qu'elle apporte à Sukrara |
|---|---|---|---|
| **rust-nostr** `nostr-sdk` (liaisons Kotlin `org.nostrdevkit:nostr-sdk` et Swift `nostr-sdk-swift`) | 0.45.1 (Android : `apps/android/gradle/libs.versions.toml` ; Mac : `apps/macos/Package.swift`) | Android (module `:share`), Mac (cible `SukraraShare`) | génération des clés secp256k1, signatures BIP-340, NIP-44 v2, NIP-59, NIP-49, encodage bech32 |
| — dont crates Rust incluses dans la bibliothèque native | `secp256k1` 0.30.0 (libsecp256k1, `secp256k1-sys` 0.10.1), `chacha20` 0.9.1, `chacha20poly1305` 0.10.1, `poly1305` 0.8.0, `hmac` 0.13.0, `sha2` 0.11.0, `bitcoin_hashes`, `scrypt` 0.12.0, `salsa20` 0.11.0 | | primitives des NIP ci-dessus |
| — également présentes dans la bibliothèque, **non utilisées par Sukrara** | `aes` 0.8.4 (NIP-04), `rustls` 0.23.43 / `ring` 0.17.14 (client de relais de la bibliothèque) | | Sukrara ne fait pas appel à NIP-04 et ouvre ses connexions aux relais par le système (OkHttp sur Android, URLSession sur Mac) |
| **Sparkle** | 2.10.1 (`apps/macos/Package.swift`) | Mac, version GitHub uniquement (pas dans la version Mac App Store) | vérification de la signature EdDSA (Ed25519) des mises à jour |
| **Cœur de sécurité Libre 3 de Juggluco** (GPL-3.0, J. Korthals Altes) et **TinyCrypt** (Intel, BSD) | Juggluco au commit `a983788a17a35f8e76d9da5c125996b648b4f27a` (`apps/android/sensor/juggluco.lock`) | Android, bibliothèque native `libsukrara_libre3.so` (`apps/android/sensor/src/main/cpp`) | protocole de sécurité du capteur Libre 3 ; AES-128-CCM |

Les versions des crates Rust ont été lues dans les binaires de nostr-sdk 0.45.1 (Android `.aar` arm64 et `.jar`
JVM/macOS) : chemins de compilation `crates.io/<nom>-<version>`.

## 3. Partage chiffré de bout en bout (Android et Mac)

Spécification : `contracts/protocol/PROTOCOL-v2.md`, `contracts/protocol/PROTOCOL-identity.md`.
Code : interface `apps/android/core/protocol/src/main/kotlin/app/mauri/sukrara/protocol/NostrCrypto.kt`, mise en œuvre
`apps/android/share/src/main/kotlin/app/mauri/sukrara/share/crypto/SdkNostrCrypto.kt` ; Mac
`apps/macos/Sources/SukraraShare/Protocol/NostrCrypto.swift` et `NostrSDKCrypto.swift`. Les deux applications
passent les mêmes vecteurs de test (`contracts/protocol`).

### 3.1 Clés

Toutes les clés sont des clés **secp256k1 de 256 bits**, générées aléatoirement par la bibliothèque (générateur du
système).

| Clé | Détenue par | Rôle |
|---|---|---|
| Identité `I` | téléphone de la personne | signe la liste des appareils et des proches, les livraisons de clés, la sauvegarde ; scelle les invitations |
| Appareil `D` | un téléphone | signe les données publiées (glycémies, alertes, traitements, réglages) |
| Clé de lecture `G_n` (époque n) | la personne et les proches approuvés | destinataire NIP-44 de toutes les données ; une nouvelle époque à chaque retrait d'un proche ou changement de téléphone |
| Proche `F` | chaque appareil d'un proche (Android ou Mac) | reçoit les livraisons de clé ; signe ses demandes et ses saisies |
| Clé d'invitation | dérivée du secret de 16 octets d'un lien d'invitation | destinataire de l'invitation chiffrée |

### 3.2 Chiffrement : NIP-44 version 2

Chaque contenu publié (glycémie en direct, blocs horaires, alertes, traitements, réglages, liste des appareils) est
chiffré avec NIP-44 v2 de la clé de l'appareil vers la clé de lecture du groupe :

- accord de clé **ECDH secp256k1** (coordonnée x du secret partagé) ;
- **HKDF-SHA256** (RFC 5869) : extraction avec le sel `nip44-v2` → clé de conversation (256 bits) ; expansion avec
  un nonce aléatoire de 32 octets par message → clé ChaCha20 (256 bits), nonce ChaCha20 (96 bits), clé HMAC (256 bits) ;
- **ChaCha20** (RFC 8439) sur le texte complété (remplissage NIP-44) ;
- **HMAC-SHA256** sur nonce ‖ texte chiffré (chiffrer-puis-authentifier).

Protège : la **confidentialité** et l'**intégrité** des données de santé face aux relais, au réseau et à toute
personne qui n'a pas été approuvée.

### 3.3 Messages privés : NIP-59 (gift wrap)

Les messages entre la personne et un proche (invitation, demande d'accès, livraison de la clé de lecture, retrait,
code d'appairage…) sont emballés selon NIP-59 :

1. le message (« rumor », non signé) ;
2. le **sceau** (kind 13) : le message chiffré en NIP-44 v2 de l'expéditeur vers le destinataire, **signé** par
   l'expéditeur (authentifie l'expéditeur) ;
3. l'**enveloppe** (kind 1059) : le sceau chiffré en NIP-44 v2 avec une **clé jetable aléatoire**, signée par cette
   clé, adressée au destinataire ; horodatage aléatoirement antidaté (masque l'expéditeur et l'heure).

Protège : confidentialité, authenticité de l'expéditeur et discrétion des métadonnées.

### 3.4 Signatures : Schnorr BIP-340

Chaque événement Nostr porte un identifiant **SHA-256** de son contenu sérialisé et une **signature Schnorr BIP-340**
sur secp256k1 (NIP-01). Les proches n'acceptent une donnée que si la signature est valide et que l'auteur figure dans
la liste signée par l'identité (`PROTOCOL-v2.md` › *Acceptance rules*). Les mêmes signatures servent à :

- l'**authentification auprès des relais** (NIP-42, événement kind 22242 signé par la clé destinataire) ;
- la **preuve d'identité sur la liaison Bluetooth** : le proche signe un défi (événement kind 21080) avec sa clé `F`
  (`apps/android/core/protocol/src/main/kotlin/app/mauri/sukrara/protocol/ble/BleMessages.kt`,
  `apps/macos/Sources/SukraraShare/Nearby/BleProtocol.swift`).

Protège : **authentification** et **intégrité** (signature numérique).

### 3.5 Dérivation de la clé d'invitation : HKDF-SHA256

Un lien d'invitation contient 8 octets d'empreinte de l'identité et un secret aléatoire de 16 octets `s`. La clé
destinataire de l'invitation est `scalar(s, "sukrara/invite/v3", "wrap-recipient")` : HKDF-SHA256 (RFC 5869),
répété avec un compteur jusqu'à obtenir un scalaire secp256k1 valide (`PROTOCOL-identity.md` › *Derivation*).

- Android : `apps/android/core/protocol/src/main/kotlin/app/mauri/sukrara/protocol/Derive.kt` (code de Sukrara, sur
  le HMAC-SHA256 de la JCA du système).
- Mac : `apps/macos/Sources/SukraraShare/Protocol/Derive.swift` (HKDF de CryptoKit, système).

### 3.6 Codes de vérification et identifiants (SHA-256)

SHA-256 (JCA sur Android, CryptoKit sur Mac) sert aussi, sans secret propre :

- étiquettes opaques des événements `H(x) = hex(sha256(x))[:32]` et nom d'époque `gk` (`Wire.kt`, `Wire.swift`) ;
- code de correspondance à 4 chiffres d'une invitation : `sha256(F + t) mod 10000` ;
- **appairage Bluetooth à proximité** : engagement `SHA-256("cgm2:pair-c:" + nonce)` puis code à 6 chiffres
  `SHA-256(I, F, t, na, nb) mod 10⁶` que les deux personnes comparent (comparaison numérique, comme le *LE Secure
  Connections* du Bluetooth) (`NearbyPairing.kt`, `NearbyPairing.swift`) ;
- preuve du téléphone sur la liaison Bluetooth `m = H("cgm2:ble:" + sid + ":" + nonce)`.

### 3.7 Transport Bluetooth entre la personne et ses proches

La liaison Bluetooth Low Energy de partage (Android serveur GATT, Android ou Mac client) **n'utilise pas
d'appairage Bluetooth** ni de chiffrement de la couche liaison : elle transporte les mêmes événements Nostr signés et
chiffrés NIP-44 qu'Internet (`docs/07-bluetooth-followers.md`). La confiance vient uniquement de ces événements.

### 3.8 Code de récupération : NIP-49 (Android)

La personne peut sauvegarder sa clé d'identité :

- dans son **gestionnaire de mots de passe** (Android Credential Manager) : la clé `nsec` en clair, protégée par le
  gestionnaire choisi (hors de Sukrara) ;
- ou en **code de récupération** NIP-49 `ncryptsec` : **scrypt** (N = 2¹⁸, r = 8, p = 1, sel de 16 octets) dérive une
  clé de 256 bits du mot de passe, qui chiffre la clé d'identité en **XChaCha20-Poly1305** (nonce de 192 bits)
  (`apps/android/core/protocol/src/main/kotlin/app/mauri/sukrara/protocol/owner/RecoveryCode.kt`).

L'application Mac (rôle de proche uniquement) ne crée ni ne lit de code de récupération.

## 4. Secrets stockés sur l'appareil (cryptographie du système)

**Android** — `apps/android/core/data/src/main/kotlin/app/mauri/sukrara/core/data/secure/KeystoreSecretBox.kt` :
chaque secret (clés d'identité et de proche, identifiants LibreView, Tidepool et Nightscout, secrets des serveurs
locaux, appairage du capteur, secret Juggluco) est chiffré en **AES-256-GCM** (IV de 96 bits, étiquette de 128 bits,
nom du secret en données authentifiées) avec une clé **non exportable générée et conservée dans l'Android
Keystore** (TEE ou StrongBox selon le téléphone), une clé par usage. Sukrara appelle l'API ; le chiffrement est
effectué par le système. Les fichiers de l'application sont en outre dans le stockage chiffré du système (chiffrement
par fichier d'Android). La base de données des glycémies n'est pas chiffrée par l'application (choix documenté dans
`docs/31-db-encryption.md`).

**Mac** — `apps/macos/Sources/SukraraCore/Stores/KeychainStore.swift` : l'état du suivi (la clé du proche et les clés de
lecture reçues) et le secret Juggluco sont des éléments du **trousseau macOS** (accessibles après le premier déverrouillage, jamais synchronisés). Le chiffrement
est celui de macOS ; Sukrara n'en ajoute pas.

## 5. Liaison avec le capteur de glucose (Android)

Ces protocoles sont **imposés par les fabricants des capteurs** ; Sukrara les met en œuvre uniquement pour lire la
glycémie de la personne (interopérabilité). Code : `apps/android/sensor`.

| Capteur | Cryptographie | Qui la réalise |
|---|---|---|
| **FreeStyle Libre 3 / 3 Plus** (Abbott) | Appairage : accord de clé sur **courbe elliptique P-256** (clés éphémères, certificat du capteur de 140 octets, clés fixes de l'application) puis défi-réponse chiffré par un **chiffrement par bloc du fabricant mis en œuvre sous forme de tables (« boîte blanche »)**, qui transporte une clé de session de 128 bits. Données : **AES-128-CCM** (nonce de 13 octets, étiquette de 32 bits). L'enregistrement d'autorisation conservé pour les reconnexions (149 octets) porte un **HMAC-SHA1**. | Bibliothèque native `libsukrara_libre3.so`, compilée depuis le cœur portable de Juggluco (code reconstitué, sans OpenSSL) et TinyCrypt (`apps/android/sensor/src/main/cpp/CMakeLists.txt`, `jni.c`) ; aléa : `arc4random` du système. Interface : `sensor/libre3/Libre3Security.kt`, `Libre3Crypto.kt`. |
| **Dexcom G6 / ONE** (non testé) | Défi-réponse **AES-128-ECB** avec une clé dérivée de l'identifiant à 6 caractères de l'émetteur, puis appairage Bluetooth du système | Code de Sukrara (`sensor/dexcom/Dexcom.kt`, `G6Messages.kt`) sur l'AES de la JCA du système ; appairage par Android |
| **Dexcom G7 / ONE+ / Stelo** (non testé) | Aucune : Sukrara écoute en observateur un capteur déjà appairé par l'application Dexcom (lien chiffré par le Bluetooth du système) | Android |
| **Accu-Chek SmartGuide** (non testé) | Appairage Bluetooth du système avec le code PIN à 6 chiffres du capteur | Android |
| **Aidex X / LinX / GlucoRx** (non testé) | Clé et IV dérivés du numéro de série par **MD5** ; commandes et clé de session en **AES-128-CFB** (128 bits) | Code de Sukrara (`sensor/aidex/AidexX.kt`, porté de Juggluco) sur la JCA du système |

L'appairage Bluetooth du système (Dexcom, SmartGuide, Aidex) utilise le chiffrement de la couche liaison Bluetooth
Low Energy (AES-CCM 128 bits ; échange de clés ECDH P-256 en *LE Secure Connections*), entièrement géré par Android.

Le capteur Libre 3 peut aussi être lu par l'intermédiaire de l'application **Juggluco** sur le même téléphone
(serveur web local `127.0.0.1`, HTTP en clair limité à l'appareil) ; l'en-tête `api-secret` est le **SHA-1** du secret
(convention Nightscout, `sensor/juggluco/JugglucoSecret.kt`).

## 6. Connexions réseau

| Connexion | Application | Protection | Fournie par |
|---|---|---|---|
| Relais Nostr (`wss://`) | Android, Mac | **TLS** (1.2 / 1.3) ; contenu déjà chiffré de bout en bout (section 3) | Android : OkHttp 4.12.0 sur la pile TLS du système (Conscrypt) ; Mac : URLSession |
| LibreView, Tidepool, Nightscout (envoi et import) | Android | **TLS** (1.2 / 1.3), HTTPS uniquement (y compris sur le réseau domestique) | Android (OkHttp sur Conscrypt) |
| GitHub (vérification et téléchargement des mises à jour) | Android (version GitHub), Mac (version GitHub) | **TLS** | système |
| Flux de mises à jour Sparkle | Mac (version GitHub) | **TLS** | macOS |

Le trafic en clair est refusé par la configuration réseau Android (`apps/android/app/src/main/res/xml/network_security_config.xml`),
sauf vers le téléphone lui-même (127.0.0.1). Sukrara n'impose pas de suites TLS : il utilise les réglages par défaut
du système. Les authentifications auprès des services se font par leurs propres moyens à travers TLS (mot de passe
LibreView ou Tidepool, jeton ou secret d'API Nightscout ; pour Nightscout, le secret d'API est envoyé sous forme de
SHA-1 selon la convention Nightscout).

**Serveurs locaux sans chiffrement** (Android, désactivés par défaut) :

- serveur compatible xDrip+ pour les montres Garmin : `127.0.0.1:17580`, HTTP en clair, joignable uniquement depuis
  le téléphone ;
- API Nightscout locale : `127.0.0.1:17581`, HTTP en clair, protégée par un secret d'API (comparé sous forme de SHA-1
  ou de jeton, en temps constant) ; une option supplémentaire l'ouvre sur l'adresse Wi-Fi du téléphone, toujours en
  **HTTP en clair**, avec un secret distinct et un avertissement à l'utilisateur
  (`apps/android/integrations/src/main/kotlin/app/mauri/sukrara/integrations/localserver/`).

**Mac — Juggluco sur le réseau domestique** (désactivé par défaut) : HTTP en clair vers une adresse privée du réseau
local, avec le SHA-1 du secret d'API facultatif (`apps/macos/Sources/SukraraShare/Lan/`).

## 7. Vérification des mises à jour

- **Android, version GitHub** (`apps/android/update`) : avant l'installation, le **SHA-256** de l'APK téléchargé doit
  égaler celui que GitHub publie, et l'empreinte **SHA-256 du certificat de signature** doit être celle de Sukrara ;
  Android vérifie ensuite la signature de l'APK. La version Google Play n'a pas ce module (Play fait les mises à jour).
- **Mac, version GitHub** : Sparkle vérifie la signature **EdDSA (Ed25519)** de chaque mise à jour avec la clé
  publique incluse dans l'application (`apps/macos/Bundle/sparkle.plist`) avant d'ouvrir l'image disque (en cas de
  changement de clé : la signature Developer ID Apple de la même équipe). La version Mac App Store n'inclut pas
  Sparkle.
- **Diffusions vers d'autres applications** (Android) : une glycémie n'est envoyée qu'à une application dont
  l'empreinte SHA-256 du certificat de signature est celle enregistrée (vérification par le système).

## 8. Ce que Sukrara ne fait pas

- Pas de chiffrement de fichiers, de disques, de communications de l'utilisateur ou de messagerie générale : seules
  les données de santé de Sukrara et ses propres clés sont chiffrées.
- Aucun algorithme, mode ou taille de clé réglable par l'utilisateur ; aucune API cryptographique offerte à d'autres
  applications.
- Pas de NIP-04 (AES-CBC), pas de VPN, IPsec ou SSH.
