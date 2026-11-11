---
title: Privacy policy · Sukrara
---

# Privacy policy

*Sukrara for Android (the Google Play build and the GitHub build), Sukrara for Mac (the App Store version and the
GitHub version) and this website. Last changed 2026-11-11; every change is in [the project's history](https://github.com/mauriciabad/sukrara/commits/main/docs/privacy.md).*

**In short:** Sukrara has no account, no server of its own, no analytics, no ads and no crash reporting. Your glucose,
treatments and settings stay on your phone. Something leaves the phone only when you turn on a feature that sends it,
and only to where that feature says: the people you share with (end-to-end encrypted, so no one in between can read
it), or a service you sign in to yourself (LibreView, Tidepool, your Nightscout site). The developer never receives
any of it.

Sukrara is made by Maurici Abad (Spain) as free software
([source code](https://github.com/mauriciabad/sukrara), GPL-3.0-or-later). Questions or requests:
**support-googleplay@a.mauri.app**, or [an issue on GitHub](https://github.com/mauriciabad/sukrara/issues).

## What stays on your phone

- **Glucose readings** from your sensor (or from the person you follow): kept 90 days.
- **Treatments** you add (rapid or long insulin units, carbs in grams, a short note) and doses read from an insulin
  pen (with the pen's serial number): kept until you delete them or uninstall.
- **Your settings**, your profile name and generated picture (shown only to the people you share with), and the
  sensor's pairing.
- **Keys and sign-ins**: the sharing keys, the tokens for LibreView, Tidepool and Nightscout when you use them, and
  your LibreView e-mail and password once you sign in to LibreView (so you sign in once for starting sensors and the
  LibreView upload; *Sign out* deletes them). These are encrypted with a key in the phone's Android Keystore. The e-mail
  of a LibreView or Tidepool account is kept in Sukrara's own storage so the settings can show which account is signed
  in.
- **A diagnostic log** that never leaves the phone unless you copy it yourself (*Copy report*).

Only Sukrara can read its storage. Android backup and device-to-device transfer are turned off for Sukrara, so none of
it goes to a Google backup. Uninstalling Sukrara, or *Clear storage* in Android's settings for Sukrara, deletes all of
it.

## What leaves your phone, and only when you turn it on

| Feature (where you turn it on) | What is sent | To |
|---|---|---|
| **Sharing with people over the internet** (setup's *Your followers* step or Settings › Sharing; *Over the internet* can be turned off there) | Readings, alerts, treatments and notes, the settings followers mirror, your name and picture: all end-to-end encrypted (NIP-44) to the people you allowed. A backup of your sharing (your followers with their names and pictures, your open invites, the keys) is end-to-end encrypted to your own key, so only your phone (or the one you restore it on) can read it. Your phone publishes them from the moment sharing is set up, so a follower can catch up. | Public Nostr relays (by default nos.lol, relay.primal.net, nostr.mom, relay.snort.social), over encrypted connections. Relays can't read the content. They do see your IP address, Sukrara's version, random public keys (one of them stays the same for as long as you share), the time, size and kind of each message (a live reading, an alert, stored data, a key or invite message) and when it expires, and when someone follows the same stream. |
| **Sharing over Bluetooth** (Settings › Sharing › *Over Bluetooth nearby*, on once you have a follower; *Pair nearby*) | The same encrypted messages the relays get, phone to phone: any device in Bluetooth range that asks receives them, and only your followers can read them. While *Pair nearby* is open (up to 10 minutes), devices in range can also read your name, your picture, a one-time invite code and your relays; you both confirm a 6-digit code before anything is shared. | Devices nearby (your followers' phones and Macs read it) |
| **Invite link or QR code** (Settings › Sharing) | A one-time link, valid 24 hours. Its secret is after the `#`, which browsers never send to this website. Whoever has the link can see your name, picture and relays and ask to follow; you decide with *Allow*. | Whoever you give it to |
| **LibreView sign-in** (setup, a new sensor, Settings › Sensor or Share with apps › LibreView: one account for the app) | Your LibreView e-mail and password, to get your account's receiver ID (for starting Libre 3 sensors). They are kept encrypted on the phone until you sign out, so you sign in once. | Abbott (api.libreview.io) |
| **LibreView upload** (Share with apps › LibreView) | Your LibreView e-mail and password when it signs in (with the account above); then glucose readings with their times (and so your time zone) and trend, insulin, carbs and notes, sensor starts, a random device ID for this install, the current sensor's number (set as the account's active sensor), and the name and date of birth your LibreView account already has. | Abbott (LibreView; its settings from fsll3.freestyleserver.com) |
| **Tidepool upload** (Share with apps › Tidepool) | Your Tidepool e-mail and password at sign-in; then glucose readings with their times and trend, insulin, carbs, notes, your time zone, the sensor's model, Sukrara's version and a random device name. A follower can turn this on for the person they follow: then that person's readings go to the Tidepool account the follower signs in to. *Import history* from Tidepool signs in the same way and only downloads. | Tidepool (api.tidepool.org) |
| **Nightscout upload** (Share with apps › Nightscout) | Glucose readings, insulin, carbs, notes, sensor changes (with the sensor's number), the phone's battery level and whether it is charging, and a random device name. Only to an `https://` address. | Your own Nightscout site |
| **Health Connect** (Share with apps › Health Connect) | Your glucose readings (write only; Sukrara reads nothing from Health Connect). They stay in Health Connect if you turn this off; delete them there. | Health Connect, on your phone |
| **Apps on this phone** (Share with apps: broadcasts, xDrip+ server, Nightscout API) | Each new reading (and the sensor's number) to the apps you pick, one by one. The xDrip+ server lets any app on this phone read the last 24 hours of glucose while it is on; the Nightscout API (readings and treatments) needs its secret, and *Also on this Wi-Fi* serves it unencrypted to devices on your home network. With Juggluco as the source, Sukrara reads it on the phone itself. | Apps on this phone; devices on your Wi-Fi if you allow it |
| **Sensor end in your calendar** (Settings › Sensor end) | An event when the sensor ends, with its number. In Sukrara's own calendar on the phone by default; if you pick one of your accounts' calendars, that account syncs it. | Your calendar |
| **Backup** (Settings › Backup) | Your sharing key, saved as a password in the password manager you choose, or shown as a recovery code protected by your own password. | Your password manager |
| **Export data** (Settings › Export data) | A CSV file of readings and treatments, saved or shared where you choose. | Where you choose |
| **Report a problem**, *Send feedback* | Opens GitHub in your browser; *Send feedback* about an untested sensor fills in your phone model, Android and Sukrara versions and the sensor type. Nothing is sent unless you submit it there. | GitHub, if you submit it |

All of these are off until you turn them on, except sharing itself: once you set up sharing the relays are used, and
Bluetooth too once someone follows you, because that is how sharing works (Settings › Sharing › *Over the internet*
and *Over Bluetooth nearby* turn them off). Every connection Sukrara makes is encrypted (`https` or `wss`), except the
opt-in ones on your own phone or home network named above.

Sukrara never asks for your location, contacts, microphone or files. The camera is used only to scan an invite QR code,
on the phone. Bluetooth finds the sensor and nearby phones, never your location.

**Only in the GitHub build** (not the Google Play build): Sukrara asks GitHub for the list of Sukrara releases once a
day (Settings › About › Updates, on by default) and when About opens, and downloads an update from GitHub when you
install it; GitHub sees your IP address, nothing else is sent.

## The people who follow you

They receive what you share: readings, alerts, sensor start and end, treatments with notes, the settings they mirror,
your name and picture. Their phone keeps readings 90 days (a Mac, 31 days). If you allow them to add entries, they can
send you treatments and notes. Removing someone stops what they get from then on; what they already received stays
on their device.

## Sukrara for Mac

The Mac app follows a wearer: it connects to the same relays and to the wearer's phone over Bluetooth, and sends your
follow request (with a name, by default the computer's name, and a picture), the encrypted messages that keep the
follow working (requests for new keys and for missed readings) and, if allowed, the entries you add, all end-to-end
encrypted.
*Juggluco on this network*, when you turn it on, reads glucose from Juggluco on your home network: without an address
it looks for it on the home network's private addresses, and it sends a hash of Juggluco's API secret if you set one
(plain http, home network only). The version from GitHub (the dmg or Homebrew) reads the list of updates from this
website (`sukrara.mauri.app/appcast.xml`) once a day and when you choose *Check for Updates…*, and downloads an update
from GitHub; the App Store version does neither (the App Store updates it). It keeps readings 31 days and your keys
in the Mac's Keychain, on that Mac only; nothing goes to iCloud. The camera reads an invite QR code only while the
scanner is open: nothing is recorded or sent.

## How long things last, and how to delete them

- **On your phone or Mac:** readings 90 days (Mac 31), treatments until you delete them. Delete a treatment and it is
  also deleted for your followers and in LibreView, Tidepool and Nightscout. Uninstall to delete everything, keys
  included. A follower's *Stop following* deletes what they received from you on that device.
- **On the relays:** every message carries an expiry, after which relays delete it: live readings and alerts after 1
  hour, readings history after 7 days, treatments, settings and the follower list after 30 days, the encrypted backup
  of your sharing after 90 days. Sukrara doesn't send separate deletion requests; relays run by others decide how
  they honour expiry.
- **LibreView, Tidepool, Nightscout:** what was uploaded stays in your account there after you sign out; delete it in
  that service.
- **Password manager, calendar, Health Connect, exported files:** they are yours; delete them there.

The developer holds none of your data, so there is nothing to delete on our side. Write to the address above if you
have a question.

## Children

Sukrara collects nothing from anyone, children included. A parent who sets Sukrara up on a child's phone decides which
of the features above are on.

## This website

sukrara.mauri.app is a static site on GitHub Pages, without analytics or cookies; GitHub serves it and sees your IP
address. The invite page reads the invite only in your browser. The sound packs page loads its font from Google
Fonts.

## Not a medical device

Sukrara is not a medical device and does not diagnose, treat, cure or prevent any medical condition. FreeStyle Libre
and LibreView are trademarks of Abbott; Sukrara is not affiliated with or endorsed by Abbott.
