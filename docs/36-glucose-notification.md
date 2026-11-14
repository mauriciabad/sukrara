# 36 — One glucose notification: can it also ring the alerts?

*2026-11-14 · investigation report · follow-up of #195 · Android source (frameworks/base `NotificationAttentionHelper`,
`NotificationManagerService`, SystemUI `HeadsUpCoordinator`, DeskClock), the Android docs, the sources of xDrip+,
Juggluco and GlucoDataHandler · prototype on an Android 17 emulator (API 37.0)
([`investigations/195-one-notification`](../investigations/195-one-notification)) · no real health data*

## Goal

The owner, 2026-11-14, about the Pixel 10 Pro (Android 17, rooted, release `5e669eb5`):

> "I'm very concerned about the 2 notifications issue. They basically show the same content duplicated. The
> permanent notification has several issues: it doesn't show the trend, the Open Sukrara button is unnecessary, the
> second line shows just "mg/dL" which is nonsense, and most important: it is not showing any graph at all... (unlike
> the emulator). In my phone I have root and I don't have the glucose status bar pill, and the notification looks very
> different, which is against the project rules to keep things consistent. Are you sure a permanent notification (or
> live activity, whatever it is) can't be triggered to show multiple times during its existence? In Google Maps, I
> would expect it pops up every time I need indications, so there must be a way."

#195 kept two notifications on Android on purpose (SPEC §1 "Showing an alert", FEATURES *What stays on screen*). It
argued that one notification has one channel, that an ongoing notification isn't bridged to a watch, that it can't be
the swipe that answers a very low, and that Android's guidance lists alerts as an inappropriate use of Live Updates.
This report checks each of those claims against the platform, and asks whether one notification can carry the alerts
safely.

## Short answer

- **Yes, one notification can ring as often as needed.** The foreground-service notification is posted again with
  the **same id on the alert kind's channel**, without `setOnlyAlertOnce`. It plays the channel's sound at the
  channel's volume, vibrates and shows heads-up, every time. It passes Do Not Disturb by the channel's own override.
  It opens full screen over the lock screen whenever it comes from the silent channel. It **stays a promoted Live
  Update**, chip included. Afterwards it goes back to the silent channel, and the swipe still reaches the app.
  Measured on an Android 17 emulator (§4), and it is what the AOSP source says (§3).
  "One notification has one channel" is true at any given moment, but the channel can change with every post.
- **What one notification can't do.** It can't reach a **watch**: Wear OS doesn't bridge ongoing notifications, and
  a foreground-service notification is always ongoing. Only Wear OS 7 may bridge a Live Update, and only if the
  manufacturer configures it. Separately, Android's Live Update guide names "alerts" as an inappropriate use; that is
  guidance and is not enforced. Three more points are rules to follow rather than blockers:
  - Each post after a ring must stay on the sounding channel while the sound plays; switching to the silent channel
    stops the sound.
  - A swipe during a ring must be posted back on the silent channel, or it rings again.
  - The full-screen intent fires on the switch from silent to alert, not on every re-ring.
- **The phone (part 1).** The phone was **not reachable over adb** during this session: adb listed no device and
  macOS showed no Pixel on USB in four checks spread over the run. So the diagnosis below comes from the code at
  `5e669eb5`, and the notification was reproduced on the emulator; the read-only checks to confirm it are listed.
  What it shows:
  - **There is no graph** because the phone shows the **Live Update** layout (*Live Update chip* on by default and
    granted by Android 17). Promotion forbids custom views, and the graph only exists in the chip-off custom card.
  - **The "mg/dL" line** is MetricStyle's metric **label**, which Sukrara sets to the unit.
  - **The trend is missing** because the big value has none: only a tiny text arrow in the title and the small icon.
  - ***Open Sukrara*** is added to every value state.
  - **The status-bar pill is off by default** (*Value in the status bar*). It also needs the LSPosed module scoped to
    System UI and a System UI restart.
- **Recommended**, pending the owner's decisions (end of §5):
  - **One notification on the phone.** The glucose notification is the alert while one is up, and rings on that
    kind's own channel.
  - **The card with the graph is the default layout everywhere.** It is the renderer's card, so it is the same on every
    Android version. The Live Update chip becomes an opt-in, and is redundant when the root value is in the bar.
  - **A separate copy of each alert only for people who choose *Alerts on my watch*.**

## 1. The owner's phone

**Not reached.** `adb devices` listed only this session's emulator at the start of the session and three more times
during it, and `system_profiler SPUSBDataType` showed no Pixel. No phone command was run. What follows comes from the
code at `5e669eb5` (`LiveNotification`, `LiveNotificationContent`) and its reproduction on the API 37 emulator with the
same notification. The read-only commands at the end of this section confirm it on the phone.

| What the owner sees | Cause (code at `5e669eb5`) |
|---|---|
| No graph; "looks very different from the emulator" | Settings › Status bar › *Live Update chip* is on by default, and Android 17 grants promotion (`canPostPromotedNotifications`), so `Layout.LIVE_UPDATE` applies. A promoted notification may not have custom views or be colorized ([Live Updates](https://developer.android.com/develop/ui/views/notifications/live-update); `Notification.hasPromotableCharacteristics`, [android17-release](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/core/java/android/app/Notification.java)). The renderer's card (surface `notification`: header row + chart) is drawn only in `Layout.CARD`, which is used when the chip is off or refused. The emulator pictures (Roborazzi `NotificationCardShotsTest`, API 36.0 images, which have no promotion) show that card. So **two different notifications** come out of one surface, depending on the Android version and a setting: the Live Update is the `notification-text` glyph surface, not the card. That is the "One graph everywhere" violation |
| Second line "mg/dL" | `metricStyle()` adds `Metric(FixedText(value), label = unit)`. With a single metric, Android 17 lays MetricStyle out compactly: the title on the first line, the **label** on the second, and the value large at the end (reproduced: title `138 mg/dL ↗`, line `mg/dL`, a big green `138`). The slot metrics (`GlucoseCard.slotTexts`) were missing on the phone, so it had one metric. That happens when the Notification place shows no slots (`notificationCardStyle`), which was not confirmed on the phone |
| No trend | The big metric value is `look.text` alone. The only arrows are the small icon and a text `↗` at title size, with no colour, which the eye misses next to the large value |
| *Open Sukrara* button | `LiveNotificationContent.value()` adds `OPEN_APP` to every value. A promoted card draws actions full width, so it becomes a large button that repeats the tap (which already opens Add treatment, or the app) |
| No status-bar pill (root) | *Value in the status bar* is **off by default** (FEATURES "Root status bar"). Only someone who turns it on is asked for root. If it is on: KernelSU never prompts, so Sukrara must be allowed in its Superuser list (*Allow root for Sukrara*). The clock placements need the LSPosed module to be **enabled with System UI in its scope**, plus a **System UI restart** after every install whose `HookCode.VERSION` changed (home's *Restart System UI* card). The `slot:` placements show with root alone |

Read-only checks for the phone, for the next session that has it connected:

```sh
P="adb -s 62240DLCH0005W"
$P shell dumpsys notification --noredact | grep -B2 -A45 'pkg=app.mauri.sukrara.*id=1 '   # channel, flags (PROMOTED_ONGOING), style, actions
$P shell appops get app.mauri.sukrara | grep -i promot                              # Live Updates allowed for Sukrara?
# the settings (chip, Value in the status bar, placement): read on screen in Settings › Status bar (release build)
$P logcat -d | grep -E "Sukrara\.(notify|systemui|root)|LSPosed|Xposed" | tail -100       # module loaded? hook errors?
$P shell su -c 'ls /data/adb/lspd/; cat /data/adb/lspd/log/modules_*.log | tail -50'   # only if su is allowed for the shell
$P shell dumpsys statusbar | grep -i sukrara                                         # our slots (root icons) present?
```

## 2. What others do

| App | One notification or two | How it gets attention |
|---|---|---|
| Google Maps navigation | One Live Update (ETA, next turn, progress, chip) ([Android Authority](https://www.androidauthority.com/google-maps-testing-live-updates-3593335/)) | Its **voice** is audio the app plays (Navigation SDK `setAudioGuidanceSettings`: mode, volume, vibration; [Navigator.AudioGuidance](https://developers.google.com/maps/documentation/navigation/android-sdk/reference/com/google/android/libraries/navigation/Navigator.AudioGuidance)), not the notification's sound. The SDK's foreground notification is shared under one id ([background usage](https://developers.google.com/maps/documentation/navigation/android-sdk/background-location-usage)). Re-alerting by mistake on every update is a known bug: "pops into view" and vibrates every ~3 s ([react-native-navigation-sdk #696](https://github.com/googlemaps/react-native-navigation-sdk/issues/696)), which shows that an update re-alerts |
| AOSP Clock: timers | Two ids on one channel: running timers `Int.MAX_VALUE-2` (`notify`), expired `Int.MAX_VALUE-3` (`startForeground`, full-screen intent) ([NotificationModel.kt](https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/main/src/com/android/deskclock/data/NotificationModel.kt), [TimerNotificationBuilder.kt](https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/main/src/com/android/deskclock/data/TimerNotificationBuilder.kt)) | The app's own klaxon (`TimerKlaxon.start()`, [TimerModel.kt](https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/main/src/com/android/deskclock/data/TimerModel.kt)) |
| AOSP Clock: alarms | Firing alarm = the foreground notification `Int.MAX_VALUE-7` on `alarmNotification` (HIGH), `CATEGORY_ALARM`, full screen, **no sound of its own** ([AlarmNotifications.kt](https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/main/src/com/android/deskclock/alarms/AlarmNotifications.kt)) | The app's klaxon |
| xDrip+ | Two: ongoing id 8811 (`setOnlyAlertOnce`, optional promoted chip on API 36) and the BG alert ([Notifications.java](https://github.com/NightscoutFoundation/xDrip/blob/master/app/src/main/java/com/eveningoutpost/dexdrip/utilitymodels/Notifications.java)) | The app's MediaPlayer; the alert notification is re-posted on every repeat ([AlertPlayer.java](https://github.com/NightscoutFoundation/xDrip/blob/master/app/src/main/java/com/eveningoutpost/dexdrip/utilitymodels/AlertPlayer.java)). Since its alert channel went silent, alarms no longer reach the Pixel Watch ([xDrip #4749](https://github.com/NightscoutFoundation/xDrip/issues/4749)) |
| Juggluco | Two by default (81431 ongoing, 81432 alarms); a legacy `alertseparate=false` puts alarms on the ongoing id, and JugglucoNG notes upstream forces separate at start ([Notify.java](https://github.com/j-kaltes/Juggluco/blob/primary/Common/src/main/java/tk/glucodata/Notify.java), [JugglucoNG #675](https://github.com/ctqvva/JugglucoNG/pull/675)) | The app plays a looping `Ringtone` itself (channels have no sound: outside Android's per-channel choices) |
| GlucoDataHandler | Two: alarms 801–807 on an alarm channel (`setOnlyAlertOnce(false)`, `CATEGORY_ALARM`), permanent notifications separate ([AlarmNotificationBase.kt](https://github.com/pachi81/GlucoDataHandler/blob/master/common/src/main/java/de/michelinside/glucodatahandler/common/notification/AlarmNotificationBase.kt)) | `RingtoneManager` with `USAGE_ALARM`; switches the DND filter itself |
| AAPS | Alert notification + an `AlarmSoundService` that plays the sound ([AAPS #692](https://github.com/nightscout/AndroidAPS/issues/692)) | The app's service |
| Dexcom G7, Libre 3 | Layout not public. Libre plays alarms itself with an in-app "Override Do Not Disturb" ([Abbott safety info](https://www.freestyle.abbott/en-us/safety-information.html)) | — |

**Takeaway.** Nobody found here rings from the ongoing notification by default. Those that come close (Juggluco,
the Clock) **play the sound themselves**, which puts it outside Android's per-channel choices, Do Not Disturb and the
watch. Maps "pops up" with its voice, not its notification. Ringing the ongoing notification on the alert's own
channel, as below, keeps every per-kind channel choice and DND override, which the others give up.

## 3. Android facts (with source)

- **An update without `FLAG_ONLY_ALERT_ONCE` alerts again.** In `buzzBeepBlinkLocked`,
  `shouldMuteNotificationLocked` returns `MUTE_REASON_SILENT_UPDATE` only for `record.isUpdate && (flags &
  FLAG_ONLY_ALERT_ONCE)`
  ([NotificationAttentionHelper.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/notification/NotificationAttentionHelper.java)).
  In SystemUI, `HeadsUpCoordinator.shouldHunAgain` is `!entry.hasInterrupted() || (flags & FLAG_ONLY_ALERT_ONCE) == 0`,
  so the heads-up shows again
  ([HeadsUpCoordinator.kt](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/packages/SystemUI/src/com/android/systemui/statusbar/notification/collection/coordinator/HeadsUpCoordinator.kt)).
- **A sound still playing stops** on an update whose channel has no sound (`if (wasBeep && !hasValidSound)
  clearSoundLocked()`, same file). A muted update on a channel that has a sound does not stop it.
- **The channel is resolved on every post.** `enqueueNotificationInternal` looks up the post's `channelId` and does
  not compare it with the old record's channel. `postNotification` keeps the foreground-service flag on an update
  ([NotificationManagerService.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/services/core/java/com/android/server/notification/NotificationManagerService.java)).
  `NotificationRecord.updateNotificationChannel` recomputes the importance
  ([NotificationRecord.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/notification/NotificationRecord.java)).
- **Live Update rules.**
  - The notification must request promotion, be ongoing, have a title, use the standard, BigText, Call, Progress or
    Metric style, have no custom views, not be colorized and not be a group summary.
  - Its channel must be above `IMPORTANCE_MIN`.
  - Only the system sets `FLAG_PROMOTED_ONGOING`, and it clears it when the person revokes promotion.
  - The attention helper has no rule for promoted notifications, so promotion doesn't stop a sound.
  - The guide says, under **"Inappropriate uses: Ads, promotions, chat messages, alerts, upcoming calendar events…"**,
    and also *"Don't repost Live Updates that the user dismissed"*
    ([Live Updates](https://developer.android.com/develop/ui/views/notifications/live-update)).
  - That is guidance; nothing in the source enforces it.
- **The foreground notification can be dismissed** on Android 14+ (except CallStyle, media and a few system cases),
  but not on the lock screen or by *Clear all*
  ([Android 14 changes](https://developer.android.com/about/versions/14/behavior-changes-all#non-dismissable-notifications)).
  A full-screen intent or `CATEGORY_ALARM` doesn't change that.
- **Full-screen intent.** Android 14+ allows it only for calling and alarm apps (`canUseFullScreenIntent`). When
  denied, Android removes the intent and the notification becomes a sticky heads-up (`FLAG_FSI_REQUESTED_BUT_DENIED`)
  ([Android 14](https://developer.android.com/about/versions/14/behavior-changes-14)). SystemUI starts it from
  `onEntryAdded`. In the prototype it also fired on an update that made a silent notification an alerting one, but not
  on an alert-to-alert re-ring (§4).
- **Do Not Disturb.** `setBypassDnd` works only with DND access and only on a channel the person hasn't edited
  ([PreferencesHelper.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/notification/PreferencesHelper.java)).
  This is unchanged: Sukrara's `AlertChannels.setDndBypass` already handles it.
- **Watch.**
  - Wear OS bridging skips local-only, **ongoing** (`FLAG_ONGOING_EVENT`) and `FLAG_NO_CLEAR` notifications
    ([bridging](https://developer.android.com/training/wearables/notifications/bridger)).
  - Wear OS 7 may bridge Live Updates, depending on the manufacturer's configuration, and doesn't support MetricStyle
    ([Live Updates on Wear](https://developer.android.com/training/wearables/notifications/live-updates)).
  - No official word on whether an update of a bridged notification buzzes the watch again.
- **Cooldown (Android 15+)** exempts `CATEGORY_ALARM` (`MUTE_REASON_COOLDOWN` in the attention helper). Every Sukrara
  ring already posts as `CATEGORY_ALARM`.
- **MetricStyle (API 37).** `Metric(value, label[, semanticStyle])`, up to 3 metrics. `FixedText(value, unit)` takes a
  unit of its own. Semantic colours apply only when promoted
  ([MetricStyle](https://developer.android.com/develop/ui/views/notifications/metric-style),
  [FixedText](https://developer.android.com/reference/android/app/Notification.Metric.FixedText)).

## 4. The prototype

`dev.spike.onenotif` (README in the investigation folder) runs a `specialUse` foreground service with three channels:

- `live`: default importance, no sound or vibration; Sukrara's `glucose.live`.
- `alarm`: HIGH, the alarm ringtone on `USAGE_ALARM`, vibration, DND bypass granted.
- `notif`: HIGH, the notification sound.

Every post is `setRequestPromotedOngoing(true)` + `setShortCriticalText`. A ring is the same id on `alarm`, without
`setOnlyAlertOnce`, `CATEGORY_ALARM`, *Mute…* / *Log carbs*, and a full-screen intent. Emulator: API 37.0
(`CE2A.260420.051.A3`). Each step was observed through `logcat` (`RingtonePlayer`, heads-up), `dumpsys notification
--noredact` (channel, flags, `mSoundNotificationKey`, `mIntercept`), `dumpsys power` and screenshots. The screenshots
were kept out of the repo and deleted.

| Step | Observed |
|---|---|
| `start` (silent, promoted) | `ch=live`, flags `ONGOING_EVENT|ONLY_ALERT_ONCE|NO_CLEAR|FOREGROUND_SERVICE|PROMOTED_ONGOING`; the chip `↑ 138`; no sound |
| `ring` (same id → `alarm`) | `RingtonePlayer: play …alarm_alert`, `mSoundNotificationKey` and `mVibrateNotificationKey` = this notification, heads-up with *Mute…* / *Log carbs*; flags keep `FOREGROUND_SERVICE|PROMOTED_ONGOING` (**still a Live Update**, chip `↑ 62` while ringing); `NO_CLEAR` dropped |
| `update` on `alarm` with `setOnlyAlertOnce` (after the sound, and 1 s into it) | silent; **the sound playing kept playing** |
| `ring` again on `alarm` (alarm → alarm), screen on | sound and heads-up again, on every re-ring tried |
| `quiet` (→ `live`) 2 s into a ring | `RingtonePlayer: stop` — **switching to the silent channel stops the sound** |
| Screen off, locked, `ring` from `live` | sound, and the **full-screen activity started** (`AlarmActivity fsi=true`, screen woke) |
| Screen off, `ring` again while already on `alarm` | sound, **no** full-screen intent, screen stayed asleep |
| Screen off, `quiet` then `ring` 1 s later | sound **and** the full-screen intent again |
| DND *Priority* (alarms allowed, the channel's bypass on) | `mIntercept=false`, sound and heads-up. DND *Total silence*: intercepted, like any notification |
| Swipe in the shade while ringing | the delete intent arrives (the swipe = an answer); the re-post **on `alarm` rang again** (a new record is not an update, so `onlyAlertOnce` doesn't apply) → a re-post after a swipe must be on the silent channel |
| `move` (startForeground on the alert's id, cancel the glucose id) | works, but the alert then becomes a foreground notification: `NO_CLEAR` is added a moment later, so a watch wouldn't get it either |
| API 37 MetricStyle as in `5e669eb5` (one metric, label = unit, *Open Sukrara*) | the owner's card: title line, a line saying only `mg/dL`, a big green `138` with no arrow, one full-width *Open Sukrara* |
| MetricStyle `FixedText("138 ↗", "mg/dL")`, label *In range*, + 2 slot metrics | `In range (mg/dL)` over a big green `138 ↗`, then `74%`, `4.5u`; ringing: `Low (mg/dL)` over a red `62 ↗`. The chip stays `↑ 140` (`setShortCriticalText`), so it never shows two arrows |

Not covered: a real watch (no Wear emulator paired), the Pixel's own System UI (it may differ from the emulator's;
the 0.14.x promoted card did), and an alarm sound longer than its file.

## 5. Proposal

### 5.1 The glucose notification is the card, on every Android

"One graph everywhere" asks for the renderer's card wherever the value shows, and the owner's first complaint is the
missing graph. A Live Update can't hold the graph (§3), and none of the promotable styles draws one (docs/19). The
ProgressStyle band timeline is at most 10 segments and 4 points, so it isn't the card. So:

- **Default: the card** (`Layout.CARD`, surface `notification`): the renderer's header row (value, arrow, late time,
  pill, slots) and its chart, the same on Android 16 and 17, emulator and phone. The status bar keeps the value
  without the chip: the *number icon* (default on), or the root value with the module.
- **Live Update chip: opt-in** (Settings › Status bar, *Live Update chip*, off by default). It is for people without
  root who want the value pill and the lock-screen Live Update more than the chart. Its summary says so: *"Shows the
  value as a chip; Android then hides the chart"*. While the root value is in the bar, the row says the bar already
  shows it.
- **While the chip is chosen, the Live Update says the same as the card's header.** MetricStyle on Android 17 is
  `FixedText("138 ↗", unit)` (the face's value and THE text arrow, the unit inside the value), with the range in
  words as its label (*In range*, *Low*, *High*, the late time when late; never the bare unit), then the slots. On
  Android 16 it is BigText, as today. The chip stays the arrow icon + `setShortCriticalText`.
- ***Open Sukrara* goes.** The tap already opens Add treatment (Carbs during a low), or the app. The only actions are
  ones the tap doesn't do: *Replace sensor*, *Set up sensor*, and the alert's actions while one is up (5.2).

### 5.2 One notification that rings

While an alert is up, the glucose notification is the alert. There is no second notification on the phone.

- **Silent (no alert).** Today's content on `glucose.live`.
- **A ring** (a rule fires: first alert, repeat, a worse kind; or *Send a test alert*). The same id is posted on the
  kind's own channel (`alert.<kind>` / `alert_alarm.<kind>`, with the person's sound, vibration, volume and DND
  choices), without `setOnlyAlertOnce`, as `CATEGORY_ALARM` (or `REMINDER`, per the existing `AlertCategory` rule).
  - The headline goes into the card's pill and the title: *Low glucose · Eat carbs*.
  - The alert's actions are added: *Mute…*, *Remind less*, *Log carbs*.
  - Alarm-level kinds add the full-screen intent.
  - `UnlockedAlertSound`, `SoundGap`, `AlertRingOrder` and the one-sound-per-second rule stay as they are.
- **During the ring window** (the existing 10 s; the sound's length if longer), every update stays on the alert
  channel with `setOnlyAlertOnce`, so the sound keeps playing (§4). A *Mute* or a *clear* in that window switches to
  the silent channel, which stops the sound at once (§4); that is what Mute should do.
- **After the window**, the notification goes back to `glucose.live`, still showing the alert: headline, pill and
  actions, the live value. So the next ring is a switch from silent to alert again, which brings back the heads-up
  and the full-screen intent (§4).
- **Two groups at once** (low + falling fast): the ring that is due takes the channel. The other is a line in the
  text (*also falling fast*) and rings through the same notification when its own turn comes.
- **Swipe** = the answer. Today's delete intent reports it, `ack` (#39, a very low answered) and *seen*. The re-post
  is **always on the silent channel**, without the alert part, until the next ring (§4: a re-post on the alert channel
  rings again).
- **The service is dead when an alert is due** (the alert alarm ticks it). The runtime posts the same id with
  `nm.notify`. It is a plain notification then, and `startForeground` with the same id adopts it when the service is
  back. The fallback chain stays: if the merged post throws, the plain alert (today's `showPlain`, its own id), then
  the *Monitoring problems* notice.
- **Watch: *Alerts on my watch*** (Settings › Alerts). Off by default; on by default when a bonded Bluetooth device
  is a wrist watch. When on, each **ring** is also posted as today's separate alert, so the watch is bridged. Its
  value updates follow #195's thresholds, so the watch doesn't buzz every minute. This is the one case with two
  notifications on the phone, and it is the owner's choice (decision 3). Not yet known: whether a copy that is silent
  on the phone still buzzes the watch. It needs a real watch. If it doesn't, the copy rings and the merged
  notification takes that ring silently.
- **Followers (Android).** The same, fed by the protocol's `fire` / `show` / `clear` / `answered` (no protocol
  change). The follower's glucose notification rings with the follower's own sound choices.
- **Mac.** Unchanged. It has no ongoing notification: the menu bar shows the value, and an alert is already the only
  notification. Its body stays the card's face (#195).

**How #195 maps onto it**

| #195 behaviour | In one notification |
|---|---|
| The value is now's, re-posted at 5 mg/dL / arrow / freshness steps | The glucose notification shows every reading anyway (`PostGate`). `AlertLiveValue` is kept only for the watch copy |
| Never in the 10 s after a ring | Posts in the window stay on the alert channel, muted (the sound keeps playing); none goes to the silent channel early |
| Silent show between alerts (`show`) | The alert part reappears on the silent channel |
| Seen = away until the next ring | Swipe or tap removes the alert part (the glucose card stays); the next ring brings it back |
| No data clears the level and rate alerts | The card's *No readings since 14:05* is the no-data alert's content; it rings on `alert.no_data` |
| One slot per group | One notification; the second group is a line, its ring takes the channel |

### States and exits

| State | What shows | Ways out (also with TalkBack, without a notification) |
|---|---|---|
| Glucose, no alert | The card (or the Live Update if chosen) | Settings › Status bar (layout, chip, number icon, lock screen) |
| Ringing (≤ 10 s, or the sound's length) | Heads-up / full screen; the card with the headline and *Mute…*, *Remind less*, *Log carbs* | Mute / Mute all (sound stops at once), *Log carbs*, swipe (answer), the reading back in range (clear), the app's pill and Active now |
| Alert up, silent | The card with the headline and actions | The same actions; swipe → seen; next ring |
| Seen | The card without the alert part; the pill still says it | The pill and Active now in the app; the next ring |
| Watch copy (opt-in) | The bridged alert on the watch, and its copy in the phone's alert group | *Alerts on my watch* off; dismissing it on the watch = seen |
| Chip chosen, refused by Android | The card, and the *Allow Live Updates* row | As today |

### Risks

- **The Pixel's System UI may differ from the emulator's.** Promotion while on a HIGH channel, the full-screen intent
  on a silent-to-alert update, and heads-up on a promoted re-ring must be checked on the phone with a debug build before
  shipping. The owner's phone was not reachable this session.
- **Google's guidance against alerts on Live Updates.** With the card as the default nothing is promoted, so it
  doesn't apply. With the chip chosen it does, but nothing in the source enforces it. A future Android release could
  enforce it, by demoting or silencing promoted notifications. The scenario tests plus a QA check on each new Android
  release keep watch on it.
- **A missed ring would be a regression.** The alert scenarios (`notifications.txt`) gain the presentation steps
  ("rang on channel X", "back to silent"), and an instrumented test reads back the record's channel and
  `mSoundNotificationKey` (as `AlertLiveNotificationTest` does today).
- **The channel choices people already made** are kept: the alert channels and their ids don't change.

**Decisions for the owner**

1. Merge alerts into the glucose notification on Android: yes / no.
2. The default layout: the card with the graph (chip opt-in), or the Live Update chip (no graph).
3. Do you use a watch for alerts (Wear OS / Pixel Watch)? This decides whether *Alerts on my watch* is needed now.
4. Remove *Open Sukrara* from the glucose notification: yes / no.

## In Sukrara

Nothing is brought in yet; no app code changed. The build plan, pending the owner's decisions, is one round on both
apps where it applies:

| Step | Where |
|---|---|
| Live Update content: `FixedText(value + arrow, unit)`, the range or late time as the label, no *Open Sukrara*; the card as the default, *Live Update chip* default off with the summary above | `:monitor` `LiveNotification`, `LiveNotificationContent`, `contracts/settings/settings.json` (`statusbar.chip` default), FEATURES *Live notification*, surfaces `notification-text` |
| The merged alert presentation: `AlertNotifier` posts through the glucose notification (channel per ring, the ring window, back to silent, swipe re-post silent, the service-dead path); `showPlain` and the problem notice stay the fallbacks | `:monitor` `alerts/`, `LiveNotification`, `PostGate` (a ring bypasses the 1 s gate) |
| The card's header carries the alert headline (the pill) on every card surface | `:core:graph` + `SukraraGraph` (`GlucoseCard`), `contracts/graph`, both apps' goldens |
| *Alerts on my watch* | `contracts/settings/settings.json`, `:monitor`, FEATURES |
| SPEC §1 "Showing an alert" and FEATURES *What stays on screen* rewritten; scenario steps for the presentation | `contracts/alerts/SPEC.md`, `scenarios/notifications.txt`, `AlertScenarioTest`, Mac `AlertsTests` (no-op steps) |
| Checks on the owner's phone (debug build, synthetic alerts): ring, re-ring, full screen when locked, DND, swipe, promotion kept | QA.md |

**Left out**

| Left out | Why |
|---|---|
| Playing the alert sound from the app (Juggluco, the Clock) | It bypasses the person's per-kind channel choices and Android's DND rules, and doesn't reach the watch |
| Moving the foreground service to the alert's id (`move`) | It still isn't bridged to a watch, it's one more state to keep right, and it gains nothing over ringing the glucose notification |
| ProgressStyle or BigPicture "graph" in the Live Update | Not the card: 10 segments / 4 points, or not promotable (docs/19) |
| `FLAG_INSISTENT` (a looping sound) | It isn't today's behaviour; the repeat rules already re-ring |
