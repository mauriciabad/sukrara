**Reachable:** Reddit **no**: Claude in Chrome refuses reddit.com itself ("This site is not allowed due to safety restrictions"; on the tab the owner opened: "This site is blocked"), so it is a tool block, not a captcha. Read instead: ~15 Garmin Connect IQ store pages (≈220 reviews), Google Play reviews filtered to **Watch** (GlucoDataHandler, Juggluco), 12 Diabetes UK forum threads, one HN thread, FUDiabetes, and snippets of the BrisT1D study transcripts (young adults given smartwatches). Not reachable: apps.apple.com (no read permission), TuDiabetes (closed), DuckDuckGo/Bing (bot checks, not solved), and the BrisT1D PDFs (not text-readable, so search snippets only).

# User voices on glucose watch faces (supplement to docs/42)

*2026-11-16. Quotes are under 25 words, with no usernames. "★" is the review's star rating and "▲" the store's helpful count. Store dates are when the review was written. **Many** means 5 or more independent voices, **several** 2–4, **one** a single person.*

## 1. Glucose on the watch: what people want next to it

**The point of it is not taking the phone out.** This is the most common praise on every Garmin page (**many**).
- "keeps me from pulling out my phone constantly" (CGM+ Datafield, ★5 ▲4, Aug 2026, [store](https://apps.garmin.com/apps/43c8e77b-a447-44b3-b35f-c414bfb3adc0)).
- "Now I don't have to stop running to check my blood sugar anymore" (xDrip+/Spike/Nightscout Watch, ★5, Jan 2024, [store](https://apps.garmin.com/apps/8fab3746-f56f-4b41-b3c7-5f4aaeaed6c9)).
- A pump user: "so great to look at my wrist and not go searching for my pump" (NightscoutWatch1, ★5, May 2020, [store](https://apps.garmin.com/apps/2d68fb47-922a-4da2-b221-db9823730dbb)).
- Why it matters: the face replaces a phone check, so it has to answer the whole question (value, direction, freshness) in the same glance.

**It has to be there the moment the wrist comes up, in every screen state** (**several**).
- "Every time I raise my hand, I'll see this data (unlike Apple watch)" (CGM+ Datafield, ★5, Jul 2026).
- "shows my blood glucose as soon as the watch activates" (CGM Connect Watchface, ★5 ▲4, Jun 2026, [store](https://apps.garmin.com/apps/c6282b78-8d3d-48b8-a75e-45d34684bfbb)).
- The complaint side (Polish, ★3, Feb 2026, Discreet CGM, [store](https://apps.garmin.com/apps/e6474b93-e91b-493c-bd9b-eb2c5b57419d)): in the dark, the dimmed face shows only the time, not the CGM value.
- "please implement the 'screen always on' functionality! Very important for me" (DiaGlow, ★4, Sep 2026, [store](https://apps.garmin.com/apps/ce38244a-879a-4f53-a55c-2731486d0480)).
- Why it matters: glucose belongs in the low-power and always-on form too, not only in the awake face.

**What people ask to have next to the value:**
- **Delta** (**several**):
  - "would be nice to add 5 minutes delta… (and 15 and 30 minutes)" (GGlance face, ★5 ▲2, Oct 2026, [store](https://apps.garmin.com/apps/247ec5e5-5880-4c8a-94e0-9fa90b4bea94)).
  - "how much my glucose level has changed since last reading" (CGM Connect, Jul 2024).
  - A face that shows the delta *per minute* drew a ★2 "Incorrect BG delta", because the user expected the change from one reading to the next (AndroidAPS Glucose Watch, Nov 2023, [store](https://apps.garmin.com/apps/3d163641-8b13-456e-84c3-470ecd781fb1)). → Say the unit, or use the convention people expect.
- **Minutes since the reading** (**several**):
  - "you need to add the minutes of the last sugar reading" (GGlance, ★4, Sep 2026). It was already shown under the graph and the developer agreed it was too hard to find.
  - A cryptic version confuses: "What the number followed by : between the trend arrow and glucose reading mean?" (CGM Connect digital & analogue, Sep 2026, [store](https://apps.garmin.com/apps/301dc1f8-847a-4edf-98dd-871536866e1a)).
- **Insulin on board** instead of fitness fields (**several**):
  - "Would like to see the active insulin… more than my steps" (xDrip+ Watch, Jan 2025).
  - IOB requests also on NightscoutWatch1 (2021) and CGM Gauge (2026).
  - Whether IOB counts basal insulin is disputed. The developer: "most useful for me, but I'm not sure if everybody agrees" (AAPS Watch, Nov 2023).
- **Watch battery %** (**many**: xDrip+ Watch ×2, NightscoutWatch1 ×2, Glucose Sugar, GGlance colours). One developer shows it only when it falls below a set level (NightscoutWatch1, 2019).
- **Heart rate and steps**: wanted by about as many as want them gone.
- **Seconds**: wanted by some ("need day to day", xDrip+ Watch, ★4, Nov 2025), refused by others ("turn off the seconds", No Glasses Required, Nov 2024). One user wants them only by day: "in night I don't need them" (NightscoutWatch1, Jan 2020).
- **3-hour average** ("lots of useful extra info", CGM+ GB, ★5, Feb 2026).

**The graph splits people** (**several** on each side).
- For: "Finally can have Dexcom graph on my watch face" (CGM Connect, ★5 ▲3, Mar 2026).
- For: "Love the gesture to show the graph" (No Glasses Required, Dec 2024).
- For: "prefere graph on primary display" (same face, ★5 ▲2, Feb 2026).
- Against: "option of not displaying the graph, and instead a larger display of current reading and trend arrow" (GGlance, ★4, Oct 2026).
- Against: "Don't use the graph and get 8 dot points on the face" (CGM Connect, Dec 2024).
- Against: the graph crossing the numbers, and a graph line that "looks very similar to the reading score-out" xDrip shows for missed readings (NightscoutWatch1, 2019–2020).
- A graph that "always starts blank with no history" annoys (Blood Glucose watchface, Oct 2025, [store](https://apps.garmin.com/apps/cc6c4fe3-4610-43e6-abf4-b75f638c81af)).
- Why it matters: offer a graph-free variant, backfill history, and make sure a graph line can't be mistaken for a "stale" mark.

**How big the value should be versus the time: no consensus.**
- Bigger value:
  - "great that it has the blood sugar number as big as possible" (Discreet CGM, ★5, Jan 2025).
  - "For me personally the BG is more important than the time itself" (CGM Gauge, ★3, Jun 2026, [store](https://apps.garmin.com/apps/9a9d309f-37ee-454b-8a95-8170023e804e)).
- Bigger clock:
  - Drop a quadrant "and the clock / date part increased in size" (xDrip+ Watch, ★4, Oct 2023).
  - "clock a bit bigger and the date a bit smaller" (No Glasses Required, Nov 2024).
  - "Option to swap time and BG positions" (DiaGlow, Oct 2026).
- Why it matters: the hierarchy itself is personal. Let people choose which one dominates.

**Small grey secondary text fails** (**several**).
- "The day and date is hard to read since it's grey… for older eyes, the complications are too small" (GGlance, ★3, Oct 2026).
- "Battery and HR are not legible in very dark grey and small font" (CGM Gauge, ★4, Aug 2026).
- "so that I can see the digits even without glasses" (AAPS Watch, Apr 2024).
- One app is named "No Glasses Required CGM". A BrisT1D participant who is long-sighted raised the size of the watch face.

**Practical issues that recur:**
- mmol/L versus mg/dL settings (**many**, at least 8 reviews across 4 faces): ranges could only be entered in mg/dL, or the setting was ignored.
- Dexcom's double arrows missing (GGlance ★4, Nov 2026; Discreet CGM added them in 1.22).
- Settings people "could not figure out", with one wish: "a sample of what you're changing" (CGM Connect, ★4, May 2025).

**Non-numeric encodings work for some people** (**one** to **several**).
- "Very easy to read at a glance, without needing to necessarily look at the BG number" (CGM Gauge, a dial face, ★4, Aug 2026). The same reviewer wants the dial's indicator "slightly larger or different shape".
- A wish for a "dynamic gauge scale" (CGM Gauge, 2024).

**Emotion counts.**
- "The little emoticon at the bottom warms my heart every time I see it" (xDrip+ Watch, ★5, Jan 2024).
- A parent wanted a background-image option "for my daughter to have a nice background" (CGM Connect, Aug 2025).
- "Not the prettiest watch face, but it is super handy" (CGM Connect, ★4, Aug 2025).

## 2. Discretion and other people

**The wrist is where people check in meetings, concerts and the cinema** (**several**, all UK forum or study).
- "allows discreet monitoring of my levels during meetings" (Diabetes UK, [Apple Watch](https://forum.diabetes.org.uk/threads/apple-watch.121137/), Jan 2026). After moving to a pump app that doesn't feed the watch, another member: "I really miss having my readings on my Apple Watch".
- A musician who doesn't take the phone out in concerts. The reply, from someone using a Garmin with xDrip: "better to look at your watch rather than your phone in meetings, but I still don't want people to get the wrong idea", since clock-watching is only "marginally better" a look. Checking the watch takes "one shuffle of my sleeve and one tap", against a phone's PIN or Face ID ([Dexcom One on Fitbit](https://forum.diabetes.org.uk/threads/dexcom-one-on-fitbit.106371/), May 2023).
- BrisT1D participant P03 (Google search snippet): "that bit of privacy where I don't need to start stabbing my finger in the middle of a meeting".
- "perfect watchface for quick unobtrusive monitoring of your blood sugar at work or play" (GGlance, ★5 ▲5, Sep 2026).
- "displaying my blood glucose in a discreet and visually appealing way" and "Discreet and functional" (the same reviewer of CGM Gauge, ★5, 2024 and 2025).
- Why it matters: discretion is about the **gesture** (a glance reads as checking the time) as much as the graphics. Other people may still read a long look at the watch as boredom.

**The watch isn't automatically discreet in the dark.**
- In a cinema thread ([Diabetes UK](https://forum.diabetes.org.uk/threads/should-cgm-users-be-exspected-to-leave-the-cinema-each-time-they-want-to-check-their-levels-on-there-phone.118617/), Jul 2025), the anxious original poster concluded "I need to save up for a smart watch".
- A regular answered: "I would not use a smart watch in the cinema either, the screen is also bright". Lighting up a phone is "like switching a lamp on".
- Others suggested vibrate-only alarms at the levels that need action, and a focus mode that lets through only glucose alerts.
- The poster admits needing "a few reassurance checks" in unfamiliar places, but none at home or at work.
- Why it matters: in a dark room a face needs a glance that gives off almost no light, and anxiety drives the number of checks.

**Name and stigma.**
- Owners of the face called "Discreet CGM" ask for the number "as big as possible" and for a different colour from green.
- "Still tickles me that these add-ons to watch faces are called 'complications'" ([G7 direct to watch](https://forum.diabetes.org.uk/threads/g7-direct-to-watch.121995/), Apr 2026).
- A BrisT1D participant (P18) mentions "the stigma of a high blood sugar" (snippet only).
- **Not found:** no first-hand stories of strangers asking "what's that number?", hiding it on dates, or colour drawing looks. That evidence lived mostly on Reddit and is still a gap.

**Children and teens.** The voices found are almost all **parents** (**many**).
- A parent's own watch: "saves me from pulling out my phone to check my daughter's readings" (GGlance, ▲2).
- "Such a relief for a T1D mom" (CGM+ GB, ▲2).
- "recently diagnosed daughter's blood sugar while I am at work" (CGM Connect).
- "track my little boy's bloods when we're out and about" (NightscoutWatch1).
- Parents of 6-year-olds want the *child's* watch to work without a phone "at school/asleep" ([FUDiabetes](https://forum.fudiabetes.org/t/blood-glucose-readings-on-my-apple-watch-with-miaomiao/8977), 2020 and 2023).
- A wrist alert failed for a child: "vibrations on the watch are very short, and the kid did not fell it" (xDrip+ Watch, ★4, Sep 2026).
- Why it matters: a face may be read by a follower as well as the wearer, and alert strength matters on small wrists. No teen speaking for themselves about discretion was found.

## 3. Stale or wrong data, lag, trust

**Data drops are the most common complaint on every store page** (**many**; most 1–2★ reviews).
- "watch constantly loses the sugar data… the number simply grey" (Discreet CGM, ★1, Aug 2026, German).
- "sometimes I have --- (no data) even after 30 minutes" (GlucoDataHandler on a watch, ★4 ▲4, Oct 2024, [Play](https://play.google.com/store/apps/details?id=de.michelinside.glucodatahandler)).
- "quite often will completely stop updating for hours" (CGM Gauge, ★1, Apr 2024).
- "delays… Usually only 7 minutes but sometimes up to 13" (xDrip+ Watch, ★4, Jan 2024).
- "now updates most of the time after 10 minutes" (GGlance, ★1, Oct 2026).
- Breakage upstream:
  - "too many requests" and "failed to log into Dexcom" (CGM Connect, ★3 ▲5, May 2026).
  - Dexcom's server outage of 31 Mar 2025.
  - LibreLinkUp's 12-character password rule (CGM+ GB, Oct 2026).
  - "As of June 8, it no longer works" (CGM+ Datafield, ★1 ▲4).
- Users also call Dexcom's own Garmin apps "broken" or slow: "dexcoms apps are broken on garmin" (CGM+ GB, May 2026, [store](https://apps.garmin.com/apps/9521d9b2-8dea-4d05-b311-f62b884844c2)); "garmin official app display delay and not runs sometimes" (same, Feb 2026).
- Why it matters: being stale is the normal case, not an edge case. A face meets it weekly.

**How stale data is shown decides whether the face stays useful** (**several**).
- "when there is no BT connection the watch face is usless… a big BT symbol accross the watch" (Blood Glucose watchface, ★4, Jan 2026). → A stale mark must never cover the time.
- What users value: the Fitbit Glance face "does indicate though when the last reading was received" (Diabetes UK, May 2023).
- GGlance's v1.4 changelog added "No phone connection" under the graph "so an old reading explains itself".
- A graph line mistaken for xDrip's strike-out (see §1) is the opposite failure.
- Developers repeat the platform limit: background updates no more than every 5 minutes on Garmin. One aligns its polling to Dexcom's schedule, so data arrives within ~15 s of being published (CGM+ GB reply, Feb 2026).

**Independence from the phone builds trust** (**several**).
- "I can turn off my phone or leave it in a locker, my watch will give me my glucose level independently" (Juggluco on a watch, ★5, Apr 2025, [Play](https://play.google.com/store/apps/details?id=tk.glucodata)).
- With G7 linked to phone and watch, "it is unusual for all 3 to lose connectivity at the same time" (Diabetes UK, Apr 2026).
- A cyclist feeds Nightscout, Dexcom and xDrip+ into the bike unit "which gives you a better chance to keep connected" ([Diabetes UK](https://forum.diabetes.org.uk/threads/bike-computers-and-support-lack-thereof-for-ble-cgm-profile.115492/), Feb 2025).

**Night and the trend.**
- Diabetes UK's moderators keep reposting a note that brief overnight lows are likely compression lows ([CGM limitations](https://forum.diabetes.org.uk/boards/threads/cgm-limitations-and-precautions.108188/page-2), 2024–26).
- A Juggluco user's 1-minute graph shows compression lows and "inaccurate low alarms when a sensor is new" (Oct 2026).
- "when your BG is 5.2, is it falling (need to preempt a hypo) or rising (don't need to do anything)?" (May 2026).
- BrisT1D P03 acts on the trend from the watch: shaky "and 4.5… pointing down, I'll go and get myself a snack" (snippet).

## 4. Alerts on the wrist

**Watch-only vibration so a partner can sleep** (**several**).
- "I want the low blood sugar alarms to notify my iWatch (as the vibration will wake me) but not to sound an alarm on my iPhone" ([Diabetes UK](https://forum.diabetes.org.uk/threads/libre-2-alarms-on-smartwatch-only.102105/), Sep 2022; the alarm was waking her husband).
- Dexcom on Apple Watch "can be configured to also buzz your wrist… if you go low (eg at 70) before it starts making noise for a very low alert (eg 55)… not to wake up a partner" ([HN](https://news.ycombinator.com/item?id=31614694), Jun 2022, 257-point thread).
- A deaf user who can't hear night alarms needs a vibrating watch ([Diabetes UK](https://forum.diabetes.org.uk/threads/cannot-hear-cgm-alarm.106851/), Jun 2023). A reply: "I prefer a more subtle alarm"; he uses Juggluco on a Pixel Watch, where both devices vibrate.
- Why it matters: escalation goes from wrist to sound, and the wrist is the quiet first step.

**Too much, or not enough** (**several**).
- Too much:
  - G7 direct-to-watch: "a bit intrusive with alarms on my wrist. Instead I have G7 as a complication" (Diabetes UK, Apr 2026).
  - A watch that "started making VERY LOUD noises when my blood sugar goes low", until an "always alert for very low" setting was found (GlucoDataHandler, ★4, May 2025).
- Not enough:
  - The child who didn't feel short vibrations (§2).
  - "Would be awesome if there was a built in alarm" (CGM+ GB, ★4, Jul 2026). The developer replied: "Alarms cannot be triggered reliably on a third party app".
  - "is there a way to push low alerts to the watch?" (xDrip+ Watch, Jan 2026).
  - "stop alarms, and change modus" from the watch (AAPS Watch, Jul 2024).
- What works: "The vibration of my watch for notifications is particularly useful as is the graph on the home screen" (Sugar Sense, ★5, Nov 2026, [store](https://apps.garmin.com/apps/19c4658f-6af3-41e8-b0bb-317c5de73920)).

**A good face prevents alarms.**
- "so easy to just look at the watch and have a toffee or bolus before blood glucose gets low / high enough for the alarms to go off" — walking the dogs, walking football, the cinema (G7 thread, Apr 2026).
- BrisT1D snippet: when the Dexcom phone is on vibrate the watch vibrates, and "it's much harder to ignore than" the phone.
- Why it matters: a glanceable trend can stand in for an early alert, and fewer alarms means less alarm fatigue.

## 5. Contexts

- **Running, trail running, cycling, physical work** (**many**):
  - Trail running "monitor on the fly" (xDrip+ Watch, Nov 2025).
  - "I often work remotely in the bush… quick monitoring (and snacking)". The same reviewer runs a dashboard of time, glucose, 3 h chart, heart rate, battery, steps and temperature: "sounds like a lot but it's nicely laid out" (GGlance).
  - A bike computer "saves faffing around with gloves/sleeves" (Diabetes UK, 2023).
  - A cyclist wants "10h+" battery and a transflective screen over "10 zillion colours" (Diabetes UK, Dec 2024).
  - A contemporary dance class "involving a lot of rolling around the floor" (FUDiabetes, 2019).
- **Driving** (**several**):
  - A bus driver, twice: "I need to ensure my glucose does not go low while driving" (CGM Connect, ★5, May and Sep 2025).
  - "Super easy to read when driving a car" (CGM Gauge, ★5, Oct 2026).
  - "tracking while driving" (xDrip+ Watch, Jul 2025).
  - A workaround: glucose written to a calendar so CarPlay pops it up "complete with trend arrow" ([Diabetes UK](https://forum.diabetes.org.uk/threads/smart-watches.107438/), Jul 2023).
- **Night, always-on, brightness**: the dim screen hides glucose (Discreet CGM). Always-on is "very important" (DiaGlow). The watch screen is "also bright" in a cinema. Seconds only by day (all above).
- **Battery** (**several**):
  - "drains battery like there's no tomorrow… 90% to 88%" in 3 h (CGM+ GB, ★3, May 2026, Epix Pro).
  - Under a day on a Fenix 5X; the developer's fix was to turn off seconds (NightscoutWatch1, 2019).
  - The Sugar Sense developer quotes ~0.3 %/h on a Fenix 7 at one wake every 5 min. In Do Not Disturb it fetches every other reading.
- **Alcohol**: in the BrisT1D FG1 snippet, a participant wishes for "a little thing on my watch… 'eat something before you go to sleep'". This is the context-aware prompt the owner is describing.
- **Water, swimming, diving:** nothing found.

## What this adds to docs/42 (for its authors)

1. Glucose has to be in the always-on, low-power form, not only in the awake face (§1.1, §6 always-on).
2. Freshness has to be visible and unmistakable, and must never cover the time. Users ask for minutes-ago and a "no phone" reason (§3, §1.3).
3. The size balance between value and time, and having a graph or not, should be the wearer's choice (§9 customisation).
4. Grey, small secondary fields fail for older eyes; mmol/L and double arrows are expected (§5, §7).
5. Wrist vibration is the first, partner-safe step of an alert. It must be strong enough for a child, and must not turn into sound unexpectedly (§1.6).
6. A glanceable trend that lets people act before an alarm is valued (§1.6).
7. Discretion means a look that reads as checking the time and a screen dark enough for a cinema. The bystander reaction itself is still unevidenced (Open questions).

## Pages read

- Garmin Connect IQ, read through the store's own review list. A JS fetch was denied, so I opened the list's public URL in the tab:
  - [GGlance – CGM WatchFace](https://apps.garmin.com/apps/247ec5e5-5880-4c8a-94e0-9fa90b4bea94)
  - [Discreet CGM](https://apps.garmin.com/apps/e6474b93-e91b-493c-bd9b-eb2c5b57419d)
  - [Sugar Sense – CGM Glucose](https://apps.garmin.com/apps/19c4658f-6af3-41e8-b0bb-317c5de73920)
  - [xDrip+/Spike/Nightscout Watch](https://apps.garmin.com/apps/8fab3746-f56f-4b41-b3c7-5f4aaeaed6c9)
  - [CGM+ GB Glance](https://apps.garmin.com/apps/9521d9b2-8dea-4d05-b311-f62b884844c2)
  - [CGM+ Datafield](https://apps.garmin.com/apps/43c8e77b-a447-44b3-b35f-c414bfb3adc0)
  - [CGM Connect Watchface BETA](https://apps.garmin.com/apps/c6282b78-8d3d-48b8-a75e-45d34684bfbb)
  - [CGM Connect Watchface digital & analogue](https://apps.garmin.com/apps/301dc1f8-847a-4edf-98dd-871536866e1a)
  - [NightscoutWatch1](https://apps.garmin.com/apps/2d68fb47-922a-4da2-b221-db9823730dbb)
  - [Blood Glucose watchface](https://apps.garmin.com/apps/cc6c4fe3-4610-43e6-abf4-b75f638c81af)
  - [No Glasses Required CGM](https://apps.garmin.com/apps/bca65d5a-94af-4f31-b519-692ce60d1d25)
  - [AndroidAPS Glucose Watch](https://apps.garmin.com/apps/3d163641-8b13-456e-84c3-470ecd781fb1)
  - [Glucose Sugar](https://apps.garmin.com/apps/4c88e8a7-9bfc-4320-b224-aa0decf03ae5)
  - [CGM Gauge Watchface](https://apps.garmin.com/apps/9a9d309f-37ee-454b-8a95-8170023e804e)
  - [DiaGlow](https://apps.garmin.com/apps/ce38244a-879a-4f53-a55c-2731486d0480)
  - Store search results for "dexcom", "glucose", "nightscout", "CGM Connect". Dexcom's own Garmin apps weren't listed in this store region.
- Google Play, reviews filtered to Watch:
  - [GlucoDataHandler](https://play.google.com/store/apps/details?id=de.michelinside.glucodatahandler)
  - [Juggluco](https://play.google.com/store/apps/details?id=tk.glucodata)
  - Dexcom G7 has no Watch filter.
- Diabetes UK forum:
  - [Apple Watch](https://forum.diabetes.org.uk/threads/apple-watch.121137/)
  - [Dexcom one on Fitbit](https://forum.diabetes.org.uk/threads/dexcom-one-on-fitbit.106371/)
  - [Libre 2 alarms on smartwatch only?](https://forum.diabetes.org.uk/threads/libre-2-alarms-on-smartwatch-only.102105/)
  - [Cannot hear CGM alarm](https://forum.diabetes.org.uk/threads/cannot-hear-cgm-alarm.106851/)
  - [G7 direct to watch](https://forum.diabetes.org.uk/threads/g7-direct-to-watch.121995/)
  - [Smart watches](https://forum.diabetes.org.uk/threads/smart-watches.107438/)
  - [Should CGM users be expected to leave the cinema…](https://forum.diabetes.org.uk/threads/should-cgm-users-be-exspected-to-leave-the-cinema-each-time-they-want-to-check-their-levels-on-there-phone.118617/)
  - [Bike computers and BLE CGM profile](https://forum.diabetes.org.uk/threads/bike-computers-and-support-lack-thereof-for-ble-cgm-profile.115492/)
  - [CGM limitations and precautions p.2](https://forum.diabetes.org.uk/boards/threads/cgm-limitations-and-precautions.108188/page-2)
- Hacker News: [Continuous glucose monitoring on the Apple Watch](https://news.ycombinator.com/item?id=31614694) (2022, 93 comments).
- FUDiabetes: [Blood Glucose Readings On My Apple Watch With Miaomiao](https://forum.fudiabetes.org/t/blood-glucose-readings-on-my-apple-watch-with-miaomiao/8977).
- BrisT1D study transcripts, Google search snippets only:
  - [FG1](https://data.bris.ac.uk/datasets/33z5jc8fa6tob21ptrugzqog08/transcripts/2_august_focus_groups/2_FG_FG1.pdf), [FG3](https://data.bris.ac.uk/datasets/33z5jc8fa6tob21ptrugzqog08/transcripts/2_august_focus_groups/2_FG_FG3.pdf)
  - Interviews P03, P11 and P18 (`…/transcripts/0_Int_P03`, `6_Int_P11`, `0_Int_P18`).
  - The full transcripts are open and worth reading as PDFs.
- Low yield: Garmin Forums [Dexcom on watch face (FR955)](https://forums.garmin.com/sports-fitness/running-multisport/f/forerunner-955-series/353996/dexcom-on-watch-face) (setup only). TuDiabetes (closed).
