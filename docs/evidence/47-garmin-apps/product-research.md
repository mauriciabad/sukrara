# Garmin app, glance, data field, complications, wrist logging and alerts: person-first research

*2026-11-22, for #199. Builds on `docs/42-watch-faces.md` §1 and
`docs/evidence/42-watch-faces/user-voices.md`, and doesn't repeat them
(time checks, stigma, alarm fatigue, night facts, the partner-safe wrist buzz, stale data as the normal case).
Platform internals are left out except where they change what the person sees.*

**Reach:** Reddit is blocked (WebSearch refuses reddit.com; Chrome was refused in the earlier round). Read instead:
about 1,200 Connect IQ store reviews across 21 glucose apps (the store's public review list), the store listings and
changelogs, Garmin developer and user forums, GitHub repos, PRs and issues (AAPS, Loop, Trio, xDrip+, Juggluco, GDH),
docs, Diabetes UK, Singletrack and diabetes.co.uk threads, clinical papers. Quotes are under 25 words, no usernames;
★ = rating, then the month written.

---

## 1. The person's life (new surfaces only)

### When people open a glance / app / field instead of the face
- **Their face can't show glucose.** Garmin's built-in faces don't run Connect IQ, so they can't show any app's
  complication ([CGM Connect dev reply](https://apps.garmin.com/apps/286fcd0e-8697-4d22-a557-6203ae3cca18),
  [CIQ forum](https://forums.garmin.com/developer/connect-iq/f/discussion/336401/complications-on-native-watch-faces)),
  and Fenix 8 owners say third-party faces are sluggish and drain battery
  ([forum](https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/fenix-8-series/430758/fenix-8-solar---watch-face-portal-so-useless/2036484)).
  For them the glance is the glucose screen: "just hitting one button on my Fenix 6X. I moved it to the top"
  ([Dexcom widget](https://apps.garmin.com/apps/027db46d-fbc8-4362-a65f-05fa6c0f113f) ★5, Jun 2023).
  → design: treat the glance as a main surface for everyone not on Aura. It has to answer the face's whole question
  (value, direction, age) without being opened.
- **They want the graph, or a fresh reading now.** "Fresh on every look: opening the glance or app fetches a new
  reading" ([GGlance app](https://apps.garmin.com/apps/bddd4e35-05ea-4bd9-8312-4021ef5a68c7)); CGM+ refreshes when you
  press START ([CGM+ GB reply](https://apps.garmin.com/apps/9521d9b2-8dea-4d05-b311-f62b884844c2)); CGM Connect
  refreshes every 30 s while it's open ([widget](https://apps.garmin.com/apps/034a3784-b27f-4f2a-a109-67413a5f4f83)).
  → design: opening the glance or app asks the phone for the newest reading and shows that it's checking, so a stale
  value never sits there unexplained.
- **The app ends up used as the face.** "This is always on as my watch face" ([xDrip+ Watch App](https://apps.garmin.com/apps/fc892009-996a-4907-b277-0c5bfa6fdab2) ★5, Apr 2020);
  "I do wish there was time displayed because I tend to always use app versions" (CGM Connect widget ★5, May 2024);
  Kerfstok's home screen is a face: time, date, battery, glucose, HR ([store](https://apps.garmin.com/apps/b6348ccc-86d8-4780-8013-d9e19fed5260)).
  → design: the app's main screen always shows the time.
- **During an activity the face isn't on screen**, so the data field is the only glucose: "This is my most important
  metric when running" ([CGM Connect DF](https://apps.garmin.com/apps/286fcd0e-8697-4d22-a557-6203ae3cca18) ★5, May 2026).
- **Gloves and dirty hands**: a dental assistant who won't touch her phone with gloves on (Dexcom widget ★1, May 2023);
  cyclists who switched watches or head units just for this ("I Changed from wahoo to Garmin (edge 840) just because of
  this feature", CGM Connect DF ★5, Apr 2024). → design: every view is reachable with buttons alone, no touch needed.

### Contexts
- **Running and cycling**: the phone sits in a belt or saddle bag. Riders check "50 times each ride" on a receiver
  when the field fails ([Dexcom DF](https://apps.garmin.com/apps/9040cc1d-13de-4d48-a859-6c2a0cedec3e) ★1, Apr 2026);
  in races some carry no phone at all and use a Dexcom receiver on the bike
  ([nduranz](https://nduranz.com/blogs/blogs/cgm-training-racing-t1d)); "for cycling race i don't take the phone"
  (CGM Connect DF ★5, Mar 2024). CGMs are banned in UCI races
  ([TrainingPeaks](https://www.trainingpeaks.com/coach-blog/continuous-blood-glucose-monitor-athletes/)).
  → design: the field says plainly when the phone isn't there ("No phone", last value and its age), and never pretends.
- **Triathlon and long efforts**: an athlete sets race alerts at low 130 / high 190 mg/dL as early cues, not clinical
  lines ([nduranz](https://nduranz.com/blogs/blogs/cgm-training-racing-t1d)); a 30–80 g/h carb intake is normal
  ([EASD/ISPAD](https://pmc.ncbi.nlm.nih.gov/articles/PMC7702152/)). Losing CGM mid-race makes the rest
  "essentially guesswork" ([diaTribe, Muchow](https://diatribe.org/see-your-dexcom-g6-data-your-garmin-watch)).
  → design: an optional low line for activities (docs/42 already has exercise at 100 mg/dL), set apart from the
  everyday alert.
- **Swimming**: Bluetooth stops in water; a Dexcom watch app back-fills up to 6 h, a phone up to 24 h
  ([Dexcom FAQ](https://www.dexcom.com/en-us/faqs/can-i-swim-and-bathe-dexcom-g7)). Only a watch talking straight to
  the sensor works in a lake (Libre 3 Kerfstok: "especially handy when swimming in a lake",
  [store](https://apps.garmin.com/apps/44d1b2ed-bb3d-4b14-a6cf-e52544207f6c)). → design: after a swim the graph fills
  the gap when the phone sends history, and doesn't show a cliff or a break that reads as a drop.
- **Diving (the owner's Descent)**: "Connect IQ features are not available while diving" on every Descent
  ([Mk3 manual](https://www8.garmin.com/manuals/webhelp/GUID-9183E86B-2399-4CFC-AB50-EAFC6D6ED326/EN-US/GUID-C3289B5E-1A70-4BB2-A7F0-9B16CF60D75D.html));
  readings come back 5–10 min after surfacing ([DiabetesWise](https://www.diabeteswise.org/en/exercise/resources-for-activity/scuba-diving-and-t1d)).
  → design: diving is a surface-interval job: the glance or app shows the 60/30/0-min pre-dive sequence (docs/42 §1.4),
  not a dive-mode field.
- **Skiing and hiking**: cold drains phones; advice is to check before every lift, keep devices inside layers
  ([CCS Medical](https://ccsmed.com/education/staying-on-track-managing-diabetes-while-hitting-the-slopes-and-other-winter-sports/),
  [Beyond Type 1](https://beyondtype1.org/riding-on-insulin-tips-for-skiing-and-snowboarding-with-t1d/)); "No internet
  in the woods Garmin" (Dexcom DF ★1, May 2026). → design: Sukrara's phone-to-watch link needs no internet; say so
  where people choose the app ("works with no signal").
- **Driving and work**: bus drivers, class, an office where the phone stays on the desk ("lets me leave my phone on my
  desk", Dexcom widget ★2, Jun 2023). → design: a two-second check, no taps.

### What they log from the wrist, and when
- **Pre-bolus at a meal out**: dosing 15–20 min before eating cuts the post-meal rise by about 30 %
  ([Slattery 2018](https://onlinelibrary.wiley.com/doi/full/10.1111/dme.13525)), but many people prefer to dose at the
  meal ([Clinical Diabetes 2021](https://diabetesjournals.org/clinical/article-pdf/39/4/347/627358/diaclincd200117.pdf)).
  AAPS's watch wizard and a Garmin control widget take a later carb time and buzz "time to eat"
  ([AAPS PR #5406](https://github.com/nightscout/AndroidAPS/pull/5406),
  [Pwb0186 widget](https://github.com/Pwb0186/aaps-garmin-control-widget)). → design: logging rapid insulin can set
  *eat in 15 min*; the watch buzzes when it's time. It's discreet at the table and takes timing off their mind.
- **Carbs during sport**: cycling fields log a gel or bottle with one tap on a preset ("Gel" 30 g, "Bottle" 60 g) and
  undo with a quick second tap ("accidental bumps on the handlebars",
  [Carb Master](https://apps.garmin.com/apps/2f0249cf-7506-46b2-ae8d-01b9682556bb)); running fields only remind and
  log the *planned* carbs: "FuelSteps is a reminder, not a log" ([FuelSteps](https://bruynr.github.io/fuelsteps-site/)).
  Kerfstok lets you go back to log a number without stopping the activity ([Kerfstok](https://www.juggluco.nl/Kerfstok/)).
  → design: carbs mid-activity are 2 presses from the field or the activity's menu, using the person's own presets,
  with an undo.
- **Rescue carbs during a low**: Gluroo's watch logs "intervention snacks" separately
  ([Gluroo 2.3.26](https://gluroo.com/blog/releases/gluroo-2-3-26-apple-watch-app-dexcom-g7-on-your-phone-ai-assistants-alexa-more/)).
  → design: the low alert itself offers *Log carbs* (15 g by default) and quiets lows, as a phone entry would.
- **Long-acting at bedtime**: smart-pen data show a 22 % chance of missing at least one basal dose in any 14 days
  ([Ekberg 2024](https://pmc.ncbi.nlm.nih.gov/articles/PMC10899845/)); without a record people "skip the dose or
  potentially administer a second dose" ([Warshaw 2020](https://doi.org/10.1177/0145721720930183)); a repeated basal
  dose "can stack insulin… for hours" ([Pillo](https://pillo.care/blog/cant-remember-if-i-took-my-insulin)).
  → design: the long-insulin entry opens with *Last: 18u · yesterday 22:05*. Answering "did I take it?" is half the
  feature.

### What makes watch logging go wrong
- **Accidental triggers**: a complication tap opened bolus screens directly and skipped the phone's switch
  ([AAPS PR #5229](https://github.com/nightscout/AndroidAPS/pull/5229)); handlebar bumps (Carb Master). Good fixes:
  insulin needs START held 2 s while a ring fills and the watch ticks; only START counts, touch and swipes do nothing,
  and a notification over the screen cancels the hold ([Pwb0186](https://github.com/Pwb0186/aaps-garmin-control-widget));
  a full crown turn on an unlocked watch that's on the wrist ([LoopFollow PR #785](https://github.com/loopandlearn/LoopFollow/pull/785)).
  → design: carbs save with one press; insulin needs a hold on a physical button that can't be done by brushing. A
  complication opens the app, never a confirm step.
- **Double logging**: Loop counted watch carbs twice after a sync race ([Loop PR #2556](https://github.com/LoopKit/Loop/pull/2556));
  its docs say "If the carb entry is present on the watch display, do not enter it again"
  ([LoopDocs](https://loopkit.github.io/loopdocs/operation/features/watch/)). A gentle fix: "Logged twice? 4 u
  NovoRapid · Undo", which never blocks and never deletes on its own ([GlucOS #387](https://github.com/DMayoBiologic/GlucOS/pull/387)),
  and "You logged a similar meal 3 min ago · View" ([#458](https://github.com/DMayoBiologic/GlucOS/pull/458)).
  → design: the log screen shows the last entry of that kind with its time; a near-duplicate gets the non-blocking
  *Logged twice?* undo.
- **A stale or zeroed amount**: after a reconnect the watch reset 3 U to 0 U, and old entries were sent minutes later
  ([Trio PR #1751](https://github.com/nightscout/Trio/pull/1751): fixed with a 2-min expiry).
  → design: an entry not sent within a few minutes is dropped with a message, never sent late.
- **Wrong kind or unit**: pump makers' patents name entering "38" as units when meaning grams, and 44 for 4 u
  ([US10994077](https://image-ppubs.uspto.gov/dirsearch-public/print/downloadPdf/10994077),
  [US11484642](https://image-ppubs.uspto.gov/dirsearch-public/print/downloadPdf/11484642)).
  → design: the kind's marker and unit are glued to the digits (*▼ 4.5u*, *● 30g*). Each kind starts from its own
  usual amount, and anything above the person's own usual range asks again.
- **No hold-to-repeat on Garmin keys** (holding is reserved), so every step is a press
  ([Pwb0186](https://github.com/Pwb0186/aaps-garmin-control-widget)). → design: sensible steps (0.5u, 5g; the
  person's last amounts and presets first) so most entries take ≤ 4 presses.

### How a low should feel: sport vs night
- **In motion, vibration is felt less**: identifying a tactile cue dropped from about 97 % standing to 85 % walking
  and took longer; stronger cues held up better
  ([Kuber et al.](https://www.researchgate.net/profile/Ravi-Kuber/publication/262293702_Tactile_notifications_for_ambulatory_users/links/55428dd60cf234bdb21a1679/Tactile-notifications-for-ambulatory-users.pdf)).
  Edge head units can't vibrate at all; the advice is a red screen and a repeating message
  ([CIQ forum](https://forums.garmin.com/developer/connect-iq/f/discussion/236865/alarm-from-a-datafield-on-bike-computer)).
  Data fields can raise Garmin's own full-screen activity alert, like Carb Master's overlay with a beep and vibration
  ([Carb Master](https://apps.garmin.com/apps/2f0249cf-7506-46b2-ae8d-01b9682556bb)).
  → design: in an activity a low takes over the data page in the activity-alert style (big value, arrow, *Eat
  carbs*), with the strongest pattern, repeated until acknowledged; on an Edge, the tone and colour carry it.
- **At night it should wake only the wearer** and stop in one move. "Alarm einfach an der Uhr abschalten… Meine Frau
  dankt dir" (xDrip+ Widget ★5, May 2021, [store](https://apps.garmin.com/apps/73115e04-dc9f-4765-ad88-e7ae283ce995));
  AAPS repeats the watch vibration every 30 s for 5 min, and *Silence* / *Mute 5 min* act on both devices
  ([AAPS docs](https://github.com/openaps/AndroidAPSdocs/blob/master/docs/EN/RemoteFeatures/RemoteControlWearOS.md)).
  Keeping the alert screen lit cost Juggluco 55 % of the watch battery against GDH's 1.6 %
  ([Juggluco PR #63](https://github.com/L3-N0X/Juggluco/pull/63)). → design: the night pattern is pulsed and
  escalating on a dim screen. One press snoozes the watch and the phone; the screen lights only while it rings.
  Unacknowledged, it hands over to the phone's sound.

---

## 2. What others do, judged for the person

**Garmin**
- **Dexcom (made by Garmin: the listing's support address is Garmin's; the data field listing is US-only)**
  ([widget](https://apps.garmin.com/apps/027db46d-fbc8-4362-a65f-05fa6c0f113f) ★1.9 from 325 reviews,
  [data field](https://apps.garmin.com/apps/9040cc1d-13de-4d48-a859-6c2a0cedec3e) ★1.7 from 157). Glance: value,
  arrow, 3-h history; field: value and arrow; a complication only for Face It, because it's "protected"
  ([CIQ forum](https://forums.garmin.com/developer/connect-iq/f/discussion/355833/has-anyone-else-been-able-to-get-dexcom-data-via-the-complications-api));
  no alerts, by design ([DiabetesMine](https://www.healthline.com/diabetesmine/garmin-and-dexcom-diabetes-data));
  needs internet and a Share follower. When it fails it shows "NO DATA" over a dotted graph, or the "IQ" crash logo
  ([forum](https://forums.garmin.com/apps-software/mobile-apps-web/f/connect-iq-store-ios/329932/dexcom-g7-not-working-with-existing-gc-dexcom-data-field-and-glance-app)).
  No native integration exists on newer watches either: it's the same Connect IQ apps; Garmin is a named partner of
  Dexcom's FDA-cleared partner APIs, a possible future path
  ([MobiHealthNews](https://www.mobihealthnews.com/news/dexcom-gets-fda-nod-its-new-api-integration)).
  *Bad*: unreliable, no reason for a failure, a sign-in loop on the watch, no age, no alerts. *Good*: never passes off
  an old value as current. → design: the anti-model. Ratings track reliability and honest states, not features.
- **CGM+ GB (watchface.io)**: glance/app, a separate data field, and complications for watchface.io's faces
  (value / data / graph, 1-h average). Stale: a "-" before the value; expired: "-1" on faces, struck through in the
  app ([changelog](https://apps.garmin.com/apps/9521d9b2-8dea-4d05-b311-f62b884844c2)). It times its fetch to land
  about 15 s after Dexcom publishes. No alerts: "Alarms cannot be triggered reliably on a third party app" (dev).
  About 10 min to set up ([FAQ](https://watchface.io/docs/cgm/faq)). Tapping the field on a face opens the app
  ([datafields](https://watchface.io/docs/datafields)).
  *Bad*: "-1" is a number where no number should be; setup. *Good*: well-timed fetches, tap-through.
  → design: publish the CGM+ GB labels so those faces work, but an expired reading publishes *nothing* or `---`,
  never a number.
- **CGM Connect (bruder_jakob)** ([widget](https://apps.garmin.com/apps/034a3784-b27f-4f2a-a109-67413a5f4f83) ★4.6/317,
  [data field](https://apps.garmin.com/apps/286fcd0e-8697-4d22-a557-6203ae3cca18) ★4.5/168): the most-loved one. Glance
  with a coloured graph and four unlabelled numbers; the field updates every 15 s on newer watches, with adjustable
  font size and no label. "old" replaces the value after 12 min (face, glance) or 23 min (field); a red ring grows over
  the last 24 h of a sensor; tap-and-hold the value toggles the graph or opens the widget; a "CGM" complication you
  switch on in settings ([support](https://cgmconnect.exparrot.de/support/)). *Bad*: codes like "P-430", "E-104";
  each app configured separately; it once drew the age in front of the value ("1:68"), and a cyclist read 168 while at
  53 (field ★1, Mar 2024; the developer: "absolutely should never ever be allowed to happen").
  → design: the age never touches the value or shares its digits' style; the sensor countdown ring is worth borrowing;
  errors are words.
- **GGlance** ([field](https://apps.garmin.com/apps/7c75ab12-67c8-47ad-8fb9-e6253576c752),
  [app](https://apps.garmin.com/apps/bddd4e35-05ea-4bd9-8312-4021ef5a68c7)): a big value, arrow, colour (white in
  range, yellow/orange high, red low), graph 1–12 h or off "for a bigger number"; the only field with a low alert: it vibrates
  and turns red on a *fresh* reading below low, and warns it can be missed when "the field [is] not on your active
  screen". It greys after 15 min and says when the last reading came; a lighter version on old watches; a TIR page
  (24 h below / in / above). *Good*: the fresh-only alert, the honest disclaimer, the graph switch.
  → design: the low alert must not depend on which data page is showing; only a fresh reading triggers it.
- **Sugar Sense** ([glance](https://apps.garmin.com/apps/19c4658f-6af3-41e8-b0bb-317c5de73920),
  [field](https://apps.garmin.com/apps/dab2d770-df9a-4a25-900f-11ca19b16486),
  [the5krunner](https://the5krunner.com/2026/09/02/sugar-sense-libre-dexcom-and-nightscout-glucose-on-garmin/)):
  glance row = value, arrow, age, sparkline, delta where it fits; the field grows with the slot (value + arrow → age,
  delta → graph), greys after 15 min, records to the FIT file; pairing by code or QR, no credentials on the watch.
  Polls half as often in Do Not Disturb. No alerts. *Bad*: the paywall found only after setup (two ★1 reviews).
  → design: pairing from the phone, one step; the content adapts to the slot.
- **Kerfstok (Juggluco)** ([store](https://apps.garmin.com/apps/b6348ccc-86d8-4780-8013-d9e19fed5260),
  [screens](https://www.juggluco.nl/Kerfstok/)): a watch app with a face-like screen; *Input* = your labels → a digit
  dial, previous values, shortcuts with arithmetic (bread × 0.45), memory slots; **touching the middle saves, with no
  confirm step**; *View* = an editable, deletable list; *Sport* records any Garmin sport with glucose every minute; the
  Libre 3 variant talks to the sensor, with low / high / signal-loss alarms that vibrate until touched. Only one watch
  can enter numbers. *Bad*: crashes ("Sometimes it works for a day, sometimes for an hour", ★1 Mar 2026), one-touch
  insulin, its own activity recorder missing auto-pause (★4 Nov 2026). *Good*: logging inside an activity, previous
  values. → design: live inside Garmin's own activities (a field plus the app) instead of replacing the recorder.
- **AAPS** ([Glucose Watch](https://apps.garmin.com/apps/3d163641-8b13-456e-84c3-470ecd781fb1),
  [data field](https://apps.garmin.com/apps/75e1bb2c-0559-42f3-adf2-109da36da186),
  [widget](https://apps.garmin.com/apps/26d7fffa-190a-439d-a5d5-1d1d0014ce1d)): the field shows value, delta, age,
  IOB, and a 2-h graph when the slot is big enough; it sends heart rate to AAPS. The glance logs carbs and pump
  disconnect/reconnect; a reviewer: "Would it also be possible to dispense insulin? That would be awesome" (★5,
  Apr 2024). The best Garmin logging reference is a fork,
  [Pwb0186/aaps-garmin-control-widget](https://github.com/Pwb0186/aaps-garmin-control-widget): carbs 5-g steps →
  meal type → carb time ±60 min → *time to eat* buzz; insulin in pump steps; the phone's own confirmation lines shown
  unchanged; carbs = one START, insulin = hold START 2 s; confirmations expire after 30 s; when unsure:
  "Unknown - do NOT repeat. Check AAPS and pump.", never resent on its own.
  → design: copy its truth rules (expiry, unknown ≠ failed, no resend) and its key-only confirm.
- **xDrip+ family (swissalpine)** ([data field](https://apps.garmin.com/apps/5a3e2cda-12f0-4afd-88ed-000e67a68d84),
  [CGM Datafield 2](https://apps.garmin.com/apps/910c3df4-8b46-45d8-9200-1eba69bc10c4),
  [widget](https://apps.garmin.com/apps/73115e04-dc9f-4765-ad88-e7ae283ce995),
  [xDrip+ Sport](https://apps.garmin.com/apps/aaf6533b-bca1-4365-9131-9f882f6f148c),
  [xDrip+ Watch App](https://apps.garmin.com/apps/fc892009-996a-4907-b277-0c5bfa6fdab2)): value, delta, minutes
  since, AAPS loop status, IOB/COB, FIT recording; Datafield 2 fetches every minute on newer watches (the old one could
  wait up to 10 min); the watch app's own low alert "reraises every five minutes and stopps if the direction of the
  values turns"; a button or swipe snoozes xDrip+'s phone alarm. Works with no internet. *Good*: offline, snooze from
  the wrist. → design: an alert that repeats while it's still falling and stops once it turns is a good fit for
  sport.
- **Garmin-native-feeling extras**: Face It shows any app's published complications
  ([forum](https://forums.garmin.com/developer/connect-iq/f/discussion/406507/watchfaces-for-all-the-available-complications));
  Glance Ultra shows a third-party complication "as is" in Garmin's stock font
  ([showcase](https://forums.garmin.com/developer/connect-iq/f/showcase/279043/watchface-glance/2032535));
  Crystal-Tesla only opens the publisher on a long press. → design: the complication's text value has to read right
  in someone else's font with no colour: `112→`, `---` when stale.

**Apple Watch (logging)**
- **Loop** ([LoopDocs](https://loopkit.github.io/loopdocs/operation/features/watch/)): carbs ±5 g, absorption as
  lollipop / taco / pizza, time ±15 min → the bolus screen (±0.5 U) → turn the crown until two triangles merge, a tick
  of haptic and a white check. Carbs entered offline wait on the watch; a list of recent carbs guards against
  re-entry. *Good*: the confirm is a gesture nobody does by accident, and you feel it complete.
  → design: Garmin's equivalent is the hold-with-ring on START; end with a haptic and a check.
- **Trio** ([PR #1648](https://github.com/nightscout/Trio/pull/1648), [#1751](https://github.com/nightscout/Trio/pull/1751)):
  when the phone's safety check objects, the watch shows the reason and "To bolus anyway, dial crown"; "iPhone not
  reachable. Nothing was sent."; "No answer from iPhone. Check Trio on your iPhone before trying again."
  → design: delivery messages say *what was and wasn't done*, in those words.
- **Dexcom G7**: view and alerts only (no event logging found on the watch), alerts mirrored from the phone; urgent
  low can't be silenced ([Dexcom](https://www.dexcom.com/en-gb/blog/dexcom-g7-direct-to-apple-watch)).
  **mySugr**: the watch shows CGM values, logging stays on the phone
  ([Accu-Chek](https://www.accu-chek.com.au/training/cgm/mysugr-app-overview)). **Sugarmate**: puts the reading in a
  calendar event so the watch updates instantly, logging on the phone
  ([Tandem](https://www.tandemdiabetes.com/products/software-apps/sugarmate)). **Gluroo**: logs food, insulin,
  rescue snacks and fingersticks from the watch, with FOB/IOB complications
  ([release](https://gluroo.com/blog/releases/gluroo-2-3-26-apple-watch-app-dexcom-g7-on-your-phone-ai-assistants-alexa-more/)).
  → design: few apps log from the wrist at all; doing it well, with no pump behind it, is a real gap.

**Wear OS**
- **AAPS Wear** ([docs](https://github.com/openaps/AndroidAPSdocs/blob/master/docs/EN/RemoteFeatures/RemoteControlWearOS.md)):
  carbs with **+10 / +5** buttons; check mark → the phone computes → second check mark; tiles "are a shortcut, not a
  status display"; a carbs tile takes a negative amount to correct; every complication's tap action can be set to
  *None*. → design: quick buttons sized to real amounts, and a correction path that isn't delete-and-redo.
- **xDrip+ Wear** ([WatchGuide](https://github.com/jamorham/xDrip-plus/blob/master/Documentation/WatchGuide.md)):
  keyboard entry saved on the watch and synced at the next connection; spoken entry gets an approval screen; snoozing
  from the number picker snoozes both devices. But "Sending Snooze" sometimes never reached the phone
  ([#3501](https://github.com/NightscoutFoundation/xDrip/discussions/3501)). → design: a snooze from the wrist shows
  as confirmed only once the phone has it.
- **Juggluco Wear** ([help](https://www.juggluco.nl/Jugglucohelp/wearosinfo.html)): amounts on the watch with your own
  labels; switching where amounts are entered can lose them. **GDH**: complications, tiles, a sensor-age complication,
  watch alarms, "no alarm while charging" ([releases](https://github.com/pachi81/GlucoDataHandler/releases)); no
  logging. → design: when the watch can't alert (charging, off the wrist, out of range), the phone must, and the
  phone's watch page says so.

---

## 3. What real people say (new, beyond user-voices.md)

- **Glance**: wanted to show glucose "while just scrolling through the widgets without actually selecting it"
  (xDrip+ Widget ★5, Mar 2021); "The BG number is not on the top level widget screen before stepping in" (Dexcom
  ★1, Dec 2023); "I just wish I could have the choice to show the graph or not" on the glance (GGlance ★5, Sep 2026);
  "remove widget's black background… closer to systems's widgets" (CGM Connect ★4, Nov 2025); readings near 100 "blend
  in with the white graph lines… dots coloured" by range (Dexcom ★4, Sep 2023); match the phone's 300 mg/dL graph top
  (Dexcom ★4, Mar 2022); four numbers around the graph: "I am guessing they are lowest and highest… % in target range
  and average" (CGM Connect ★5, Nov 2024); "font size of the max/average glucose/time in range… too small" (CGM
  Connect ★5, Aug 2026). → design: value + arrow + age at the glance's top level; the graph optional; system-like look;
  range colour on the line; every small number labelled; TIR digits as big as the space allows.
- **Data field during activity**: "Avoid this data field and go with CGM Connect" (Dexcom DF ★1, Jun 2025); "needs the
  time counter since the data last refreshed… 4 min old or 20 minutes" (Dexcom DF ★1, Jun 2025); "I like the lack of
  a label and the adjustable font-size… not showing the age if it is quite current" (CGM Connect DF ★5, Jul 2024);
  "enlarging the arrow" (CGM Connect DF ★5, Jun 2024; again Nov 2023); "removing the word 'CGM'… larger fonts" (CGM
  DF 2 ★5, Sep 2026); "chart even during activity… more useful than common trend arrow" (Blood Glucose DF ★5,
  Sep 2025, [store](https://apps.garmin.com/apps/f32eb9e4-ecd5-4ea0-a1c8-8dc166df08a4)); "the last 2-4 hours… more
  readable… during an activity" (GGlance ★5, Jul 2026); "follow the system theme" on Edge day/night (GGlance DF ★5,
  Nov 2026); "doesnt have either Dexcom G6 or G7 data… looking at my run afterwards" (Dexcom ★2, Jan 2026).
- **Alerts**: "It don't give warnings also like apple watch" (Dexcom ★1, Dec 2025); "No way you can leave phone in
  the house… and receive alerts" (Dexcom ★2, Aug 2025); "Large font… middle of the night without glasses. Alert of Low
  & High" (Dexcom ★2, Feb 2022); "works well except for the vibrate function. It's a feature I need badly" (GGlance DF
  ★4, Sep 2026); "my watch vibrates and wakes me up" at a night low (CGM Connect ★5, Oct 2025); "not scare the world
  with a phone alarm" (CGM Connect ★5, Apr 2025). The counter-view: "Alerts aren't usefull… at this moment… you can see
  what's going on anyway" (xDrip+ widget developer, Feb 2022). A rider can snooze lows for 30 min and highs for 1 h from
  the watch while riding ([Singletrack](https://singletrackworld.com/forum/bike-forum/calling-diabetics-freestyle-libre-2-garmin-head-unit/)).
  → design: snooze lengths differ by kind; alerts are wrist-first but never wrist-only.
- **Logging**: few voices exist on Garmin (people barely know it's possible); the ones found want more ("dispense
  insulin… without having to use your cell phone would be brilliant", AAPS widget ★5, Apr 2024). The main evidence is
  in GitHub PRs (§1). → design: discovery matters: show *Log* where people already look (the glance, the field, a long
  press on the complication, the low alert).
- **Complications in other faces**: "I can't get it to recognize the app as a complication to add it to a better face"
  (Dexcom ★2, Mar 2025); "add it to my watch face data fields that link to their glances" (Dexcom ★4, Sep 2023);
  "select the current glucose value as a data field on my main watch interface… like heartbeat, steps" (CGM Connect
  ★5, Sep 2024; not possible on stock faces); a third-party face's developer couldn't show a 3–6 h graph "because they
  are dependent on what data they receive" (CGM Connect ★5, Sep 2024). → design: publish value, trend and graph data
  openly (not protected), so any face can draw them.
- **Setup and reliability**: "Spent hours setting up" (Sugar Sense ★1, Nov 2026); "each 'instance' of the
  app/data field… needs login credentials" (CGM Connect DF ★5, Feb 2026); "Fails to stay connected, process of fixing
  this is inconsistent and hard to remember" (Dexcom DF ★1, Sep 2026). → design: one pairing for all surfaces; the fix
  for a broken link is shown, not remembered.

---

## 4. Data field specifics

- **Layouts people run** (no usage data exists): guides recommend 3–4 fields per screen on watches; 6 is "too small
  to read at running speed"; the most-checked number goes top or centre
  ([RunnersPicks](https://www.runnerspicks.com/blog/best-running-watch-data-fields-2026/)). Riders put glucose on an
  existing dense Edge page next to power, HR and speed ("all necessary data on one single screen", xDrip+ DF ★5,
  Jun 2020). Carb Master supports 1-, 2-, 3- and 4-field layouts
  ([store](https://apps.garmin.com/apps/2f0249cf-7506-46b2-ae8d-01b9682556bb)).
  → design: three sizes. **Small slot** = value + arrow (+ age only when old). **Half** = + delta, + a 2-h sparkline
  with the range band. **Full** = big value, arrow, age, 3-h chart with band and treatment marks.
- **What they want in it** (by number of voices): a big value (many); a bigger arrow (several); age, but only when it
  matters (several); delta in the unit they expect, per reading (several, user-voices); a short graph (several); range
  colour that also works on 1-bit Instinct screens (Sugar Sense keeps the grey for stale there); a low alert (several);
  IOB/COB for loopers (AAPS, xDrip+). → design: the value grows to the largest font the slot allows (Sugar Sense's
  latest change, "the way Garmin's own single-field layout does").
- **After the activity**: glucose in the FIT file with min/max/average and lap averages is now standard (CGM+
  Datafield [changelog](https://apps.garmin.com/apps/43c8e77b-a447-44b3-b35f-c414bfb3adc0), Sugar Sense, CGM Connect,
  xDrip+); its absence draws complaints (Dexcom). → design: record it; this is how people learn what a run does to
  them.
- **The start of an activity**: on older watches the first reading can take up to 5 min
  ([GGlance DF](https://apps.garmin.com/apps/7c75ab12-67c8-47ad-8fb9-e6253576c752)); one field pre-fills the graph with
  the last 30–60 min when the activity starts ([Blood Glucose DF](https://apps.garmin.com/apps/f32eb9e4-ecd5-4ea0-a1c8-8dc166df08a4)).
  → design: the field opens with the last known value and its history, never blank.
- **Failure modes during activity**: the "IQ" crash logo (Dexcom); "IQ..." a few minutes into a training-target run
  (CGM Connect DF ★5, Jun 2025); a blank field after *resume later* (firmware bug,
  [swissalpine #2](https://github.com/swissalpine/GARMIN-xDrip-Spike-Nightscout-Watchface/issues/2)); some Edges
  switch Wi-Fi on when not recording and then skip the phone's Bluetooth ([CGM DF 2 notes](https://apps.garmin.com/apps/910c3df4-8b46-45d8-9200-1eba69bc10c4)).
  → design: keep the field's memory small enough for workouts, training targets and resumed activities, and test
  those.
- **Alerts from a field** are Garmin's own activity alert, which the person has to switch on under the activity
  profile's alerts (Carb Master's setup). → design: Sukrara's setup flow walks them through it, or the field shows
  *Alerts off for this activity · how to turn on*.

---

## 5. States people hit

| State | What others do | → design |
|---|---|---|
| **No data yet / never paired** | "ERROR: Missing Credentials" (CGM+); "no request yet" + a countdown (CGM Connect); sign-in screens on the watch (Dexcom) | A pairing state that says *Open Sukrara on your phone* (+ a QR / code if needed); no credentials on the watch |
| **Old data** | Grey after 15 min + when it came (GGlance, Sugar Sense); "-" prefix, then "-1" (CGM+); "old" instead of the value after 12/23 min (CGM Connect); "NO DATA" without the last value (Dexcom) | Sukrara's own steps (late → struck → *No reading since 10:09*) on every surface; a complication publishes `---` once expired |
| **Phone away / Garmin Connect asleep** | Last value stays, no reason (CGM+ FAQ); "BLE error", "P-2", "E-104" (CGM Connect) | Say which hop broke (*No phone* / *Garmin Connect isn't answering*), as docs/43 does on the face |
| **Sensor warm-up** | Data stopped after a sensor change with no hint (Dexcom ★2, Nov 2021); xDrip+ Watch shows "data while sensor starts up" | *Warming up · 23 min left*, never a number that passes for glucose (docs/42) |
| **Sensor ending / ended** | A red ring that grows over the last 24 h (CGM Connect); a sensor-age complication (GDH) | Days left in the app and the full field; a ring or *Sensor ends in 6h* in the glance on the last day |
| **No sensor on purpose** | "not a way to disable it for those times in which I am not using a C[GM]" (CGM Connect ★5, Jun 2025) | A calm *No sensor* state that doesn't alert or look broken |
| **During an activity** | Field alert only if that page is showing (GGlance); no alert at all (Dexcom, Sugar Sense, CGM+) | The alert doesn't depend on the visible page; on Edge, tone + colour |
| **Logging while the phone is unreachable** | Carbs wait on the watch and sync later (Loop, xDrip+ Wear); "Nothing was sent" (Trio); "Unknown - do NOT repeat" (Pwb0186); Sukrara's phone writer: *Sending…* → *Not sent* + *Retry* | Same vocabulary as the phone: *Sending… → On the phone*; offline: *Saved on the watch, sends when the phone is back* (carbs) / insulin asks first; unknown = *Check your phone before logging again* |
| **Snooze not received** | "Sending Snooze" lost (xDrip+ #3501) | *Snoozed on watch and phone* only after the phone confirms; otherwise *Phone didn't get it* |
| **Watch charging / off the wrist** | "no alarm while charging" (GDH) | The phone alerts; its watch page shows *Watch can't alert now* |
| **Diving (Descent)** | Connect IQ is off in dive mode (Garmin) | A surface-interval view: last readings + trend, no promise during the dive |

---

## Top 10 design implications (ranked by how much they matter to the person)

1. **An old value never looks current, on any surface**, the complications in other people's faces included: Sukrara's
   staleness steps everywhere, `---` once expired (never "-1"), and the age never placed against the digits (the
   "1:68" read as 168 during a real 53 low).
2. **A low reaches the wrist when the phone is in a bag**, with two personalities: in an activity it takes over the
   data page (largest value, arrow, *Eat carbs*, the strongest pattern, repeated until it turns or is acknowledged;
   tone and colour on Edge); at night it's pulsed and escalating on a dim screen. One press snoozes watch and phone,
   only fresh readings trigger it, the screen lights only while ringing, and the phone takes over when the watch can't
   (charging, off the wrist, unanswered).
3. **Insulin can't be logged by accident**: carbs save with one press; rapid and long need a hold on START with a
   filling ring and haptic ticks; touch, swipes and complication taps never confirm, and a notification over the
   screen cancels the hold.
4. **Double doses are prevented by memory, not friction**: each kind's screen opens with its last entry (*Long: 18u ·
   yesterday 22:05*); a near-duplicate gets a non-blocking *Logged twice? · Undo*. This answers "did I take my
   long-acting?" from the wrist.
5. **The watch tells the truth about delivery**: *Sending… → On the phone*; *Not sent, nothing saved*; *Unknown,
   check your phone before logging again*; nothing is resent on its own; an entry older than a couple of minutes is
   dropped, never sent late; the quiet line (*Lows quiet for 15 min*) appears once the phone has it, as on the phone.
6. **Fast, unambiguous entry**: the kind's marker and unit glued to the digits (*▼ 4.5u*, *● 30g*); each kind starts
   at the person's usual amount, with their presets (gel 30g) and last values first; 0.5u / 5g steps; ≤ 4 presses for
   most entries; anything above their usual range asks again. Rapid insulin can set *eat in 15 min* and the watch
   buzzes when it's time (pre-bolus at a restaurant).
7. **A data field that lives in Garmin's own activities**: the value in the largest font the slot allows, no label, a
   bigger arrow, age only when old, delta per reading, a 2–3 h chart with the range band and treatment marks on bigger
   slots, Edge day/night, pre-filled at the start, recorded to the FIT file; logging carbs reachable without stopping
   the activity.
8. **One pairing, no credentials, plain words**: the glance, field, complications and app pair once from the phone;
   every failure names the broken hop and the fix (*Garmin Connect isn't answering · open it on the phone*), never a
   code.
9. **The glance is a main surface**: value, arrow and age at the top level without opening it, a fresh fetch on open,
   the graph optional, *Log* one press away; the app's main screen shows the time (people keep it on as a face); every
   TIR / average number labelled and large.
10. **Complications any face can use**: publish value, trend and history openly (not protected) for Face It and under
    the CGM+ GB labels, with a plain-text form that reads right in a stock font (`112→`), stale handling built into
    the published value, and a tap or long press opening Sukrara (the log one step further, never a confirm).

<!-- Sources not reachable: reddit.com (blocked for this tool), apps.apple.com review text (one Gluroo watch review only). -->
