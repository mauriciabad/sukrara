# B — Garmin: the platform, the owner's watch and the face ecosystem

*Research for docs/42 · 2026-11-15 · knowledge only, no design.* Garmin limits are listed as **platform notes**, not
as design rules (owner: "we'll find a solution"). What a face *can use* gets equal weight. Unverified or third-party
claims are marked *(3rd-party)* or *(unverified)*.

## 1. The owner's watch: Descent Mk3i 43 mm

### Which watch
- **No 42 mm Descent Mk3 exists.** The series is Mk3 43 mm (stainless bezel, no air integration), **Mk3i 43 mm** and
  Mk3i 51 mm. It was announced 2023-11-14. → The owner's "42 mm" Mk3i is almost certainly the **Mk3i 43 mm** (part
  010-02753-10 carbon-grey DLC titanium with a black band, or -13 bronze PVD titanium with a French-grey band). The
  51 mm is much bigger (1.4″, 454 px).
  [Garmin newsroom](https://www.garmin.com/en-US/newsroom/press-release/outdoor/garmins-new-descent-mk3-series-dive-computers-have-200-meter-dive-ratings-bright-amoled-displays-and-options-with-a-built-in-flashlight/),
  [DIVE Magazine](https://divemagazine.com/scuba-diving-equipment/a-closer-look-at-the-garmin-descent-mk3-and-mk3i),
  [Garmin product page 852217](https://www.garmin.com/en-US/p/852217/)
- **Connect IQ device id `descentmk343mm`**, shared by the Mk3 43 mm and the Mk3i 43 mm. The 51 mm is `descentmk351mm`.
  The highest Connect IQ API level is **5.1.0 (System 8)** in SDK 9.3 device files. This decides which APIs below
  apply. [Compatible devices](https://developer.garmin.com/connect-iq/compatible-devices/),
  [flocsy CSV, updated 2026-11](https://github.com/flocsy/garmin-dev-tools/tree/main/csv)
- The **epix Pro (Gen 2) 42 mm** has the same 390×390 AMOLED panel, so it is a "42 mm" sibling that shares the
  layout. [Compatible devices](https://developer.garmin.com/connect-iq/compatible-devices/)

### Display
- **1.2″ (30.4 mm) round AMOLED, 390×390 px, 325 ppi, about 12.8 px/mm, 16-bit colour (65,536).** This is the same
  panel generation as the epix 2. A square inscribed in the circle is about 276×276 px. → Any size decision converts
  directly: 1 mm ≈ 13 px.
  [Retailer spec table](https://www.americandivingsupply.com/Garmin-Descent-Mk3i-Watch-43mm-p/010-02753-1x.htm),
  [simulator.json (SDK copy)](https://github.com/wonderfulest/watch-face-designer/blob/main/src/assets/devices/descentmk343mm/simulator.json),
  [Real Devices of CIQ (65,536 colours on AMOLED)](https://forums.garmin.com/developer/connect-iq/b/news-announcements/posts/the-real-devices-of-connect-iq-part-1)
- **Brightness in nits: not published by Garmin** (not found). Reviewers call it bright underwater and in sun.
  One owner says the dimmed AOD "is impossible to see" in bright sun *(3rd-party)*.
  [Garmin forum](https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/descent-mk3-series/352611/descent-mk3i-display-type),
  [ScubaLab](https://www.scubadiving.com/garmin-descent-mk3-scubalab)
- **Garmin's own manuals say burn-in is expected:** "Image persistence, or pixel 'burn-in,' is normal behavior for
  AMOLED devices". The display turns off after the selected timeout to limit it.
  [fēnix 8 manual, AMOLED](https://www8.garmin.com/manuals/webhelp/GUID-EECCAC99-90D6-4AB1-9A3A-EC433D3365E2/EN-US/GUID-8ECEF6B4-5257-44E9-BB09-778836BBF700.html)

### Case, lens, bezel: what surrounds the pixels
- **Size and materials:** 43×43×14.15 mm (some sources say 14.3), 49.3 g bare and 68.3 g with the silicone band.
  Sapphire lens. The case is fibre-reinforced polymer with a titanium rear cover. The bezel is DLC-coated or
  bronze-PVD titanium, and four exposed screws sit at about 1:30, 4:30, 7:30 and 10:30. The band is a 20 mm QuickFit.
  The watch is rated to 200 m.
  [Gadgets & Wearables specs](https://gadgetsandwearables.com/technical-specs/garmin-descent-mk3i-43-mm/),
  [DIVE Magazine](https://divemagazine.com/scuba-diving-equipment/a-closer-look-at-the-garmin-descent-mk3-and-mk3i)
- **A fixed, printed 24-hour ring surrounds the screen.** Its numerals 24 (top), 04, 06 (3 o'clock), 08, 10, 14,
  16, 18 (9 o'clock), 20 and 22 sit 15° apart. "GARMIN" replaces 12 at 6 o'clock, and "NO-FLY TIME" (red) "- ALT TZ"
  (cyan) replaces 02. The ring lies outside the 390 px display circle, so it is physical and always visible, even
  when the screen is black. Every pixel near the screen edge therefore reads against these numerals. The 43 and 51 mm
  SDK images show the same ring.
  [descentmk343mm.png (SDK image)](https://github.com/wonderfulest/watch-face-designer/blob/main/src/assets/devices/descentmk343mm/descentmk343mm.png),
  [descentmk351mm.png](https://github.com/wonderfulest/watch-face-designer/blob/main/src/assets/devices/descentmk351mm/descentmk351mm.png)
- **Garmin's default Descent face uses that ring** as a dial for two pointers: the alternate time-zone pointer and
  the no-fly time remaining, both reading "the 24-hour time markings on the watch".
  [Mk3 manual, Default Watch Face](https://www8.garmin.com/manuals-apac/webhelp/descentmk3series/EN-SG/GUID-E9854CDA-A767-443F-867E-0D1B31503B78-8369.html)

### Buttons, touch, gestures
- **Five metal buttons, three on the left and two on the right.**
  - **LIGHT** (top-left): wake or backlight; press twice for the flashlight (the 51 mm only has one); hold for the
    controls menu.
  - **UP·MENU** (mid-left): glance loop; hold for the menu.
  - **DOWN** (bottom-left): glance loop.
  - **START·STOP** (top-right): activity list, start a dive.
  - **BACK·LAP** (bottom-right).

  → From the face, every button belongs to the system. A Connect IQ face receives no button events.
  [Mk3 manual PDF](https://www8.garmin.com/manuals/webhelp/GUID-9183E86B-2399-4CFC-AB50-EAFC6D6ED326/EN-US/Descent_Mk3_Series_OM_EN-US.pdf),
  [Device Overview](https://www8.garmin.com/manuals-apac/webhelp/descentmk3series/EN-SG/GUID-95C64C5C-DB90-49F6-88BB-17501C67D331-6813.html)
- **SDK key hit-boxes on the 638×830 simulator image:** START at (528,211), UP/MENU at (23,350), DOWN at (47,493),
  BACK at (524,487). The display sits at x 131, y 211, 390×390. Touch gestures: swipe up/down = next/previous page,
  swipe right = back, tap = select, plus hold and release. The user can turn touch off or lock the device from the
  controls menu.
  [simulator.json](https://github.com/wonderfulest/watch-face-designer/blob/main/src/assets/devices/descentmk343mm/simulator.json),
  [Mk3 manual, Controls](https://www8.garmin.com/manuals/webhelp/GUID-9183E86B-2399-4CFC-AB50-EAFC6D6ED326/EN-US/GUID-700E76C4-F7E2-4984-8199-D59D6A31DFB9.html)
- **Display settings are separate for each context:** In-Dive, During Activity, General Use, During Sleep and Night
  Vision. Options: Always On Display ("keeps watch face data visible with reduced brightness and background"),
  Brightness, Red Shift, Wrist Gesture, Timeout, Alerts (screen on for alerts), and **Large Fonts**. In-dive the
  screen is always on *(3rd-party: it can't be turned off)*.
  [Mk3 manual, Screen Settings (US)](https://www8.garmin.com/manuals/webhelp/GUID-9183E86B-2399-4CFC-AB50-EAFC6D6ED326/EN-US/GUID-C9A5E731-8826-4AD0-8087-F6E2EAC625D6.html),
  [APAC version](https://www8.garmin.com/manuals-apac/webhelp/descentmk3series/EN-SG/GUID-82EF25E6-C1AF-4487-8D86-DFFAB766F0AC-6966.html)
- **Battery, 43 mm (Garmin):**
  - Smartwatch: **10 days with Wrist Gesture, 4 days with Always On.**
  - Battery-saver watch mode: 12 days, and AOD isn't possible in it.
  - Dive: 30 h, or 15 h with air integration.
  - The 51 mm gets 25 and 10 days.

  → AOD costs about 60 % of standby life on this watch. Owners report better real-world figures *(3rd-party)*.
  [Mk3 manual, Battery](https://www8.garmin.com/manuals/webhelp/GUID-9183E86B-2399-4CFC-AB50-EAFC6D6ED326/EN-US/GUID-1D06C8ED-1134-4AB1-8270-F816F3E9608B.html)

### Descent-specific display modes and features
- **Red Shift** turns the whole interface into shades of red, green or orange for night vision. It can be a
  controls-menu toggle, and it can switch on automatically at sleep time or after sunset. The SDK device file has
  `redshiftSupport: true`. A Connect IQ face **cannot detect it**: `DeviceSettings.isNightModeEnabled` is not
  supported on the Descent Mk3. So a face's colours are likely re-tinted by the system *(inference)*.
  [Mk3 manual](https://www8.garmin.com/manuals/webhelp/GUID-9183E86B-2399-4CFC-AB50-EAFC6D6ED326/EN-US/GUID-C9A5E731-8826-4AD0-8087-F6E2EAC625D6.html),
  [DeviceSettings](https://developer.garmin.com/connect-iq/api-docs/Toybox/System/DeviceSettings.html),
  [DIVEIN review (via search)](https://www.divein.com/review/garmin-descent-mk3-mk3i/)
- **Lume** is a native watch-face option, "a low brightness mode for the watch face while in dark environments". It
  uses the **ambient light sensor** to switch to a turquoise or green "luminous" glow, like mechanical-watch lume,
  at any time of day *(reviews and forum)*. It is a Descent feature that fēnix owners ask for. No Connect IQ API
  exposes ambient light or Lume *(not found in DeviceSettings, Sensor or Activity docs)*.
  [Mk3 manual, Watch Face Settings](https://www8.garmin.com/manuals/webhelp/GUID-9183E86B-2399-4CFC-AB50-EAFC6D6ED326/EN-US/GUID-A4EA51C3-D0B2-48E3-807A-768C6F07D7BA.html),
  [fēnix 7 forum "lume from the Descent"](https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/fenix-7-series/370235/is-the-lume-feature-from-the-descent-coming-to-any-other-model/1770178)
- **Large Fonts** is the system's Enhanced Readability Mode. A face **can detect it**: `isEnhancedReadabilityModeEnabled`
  (API 4.2.3) lists the Descent Mk3. `fontScale` is not supported on the Mk3.
  [DeviceSettings](https://developer.garmin.com/connect-iq/api-docs/Toybox/System/DeviceSettings.html)
- **Native face options:** digital or analog; Dial, Hands, Layout, Seconds style, Data, Data Color, Accent Color, Lume,
  Alt. time zones. Up to four configurations of each native face.
  [Watch Face Settings](https://www8.garmin.com/manuals/webhelp/GUID-9183E86B-2399-4CFC-AB50-EAFC6D6ED326/EN-US/GUID-A4EA51C3-D0B2-48E3-807A-768C6F07D7BA.html)
- **Dive data lives on the native face and the Surface Interval glance:** SI time, no-fly hours, then (START/DOWN)
  OTU and CNS %, N2/He tissue load (green 0–79 %, yellow 80–99 %, red ≥100 %), and the time no-fly ends. No-fly is
  0 / 12 / 18 / 24 / 48 h depending on the dive. **None of this is available to Connect IQ** (no API and no
  complication). Developers have declined to estimate it for safety.
  [SI glance](https://www8.garmin.com/manuals/webhelp/GUID-9183E86B-2399-4CFC-AB50-EAFC6D6ED326/EN-US/GUID-5B6754B2-AC50-4686-AD61-8BCC57AE2D55.html),
  [No-Fly Time](https://www8.garmin.com/manuals-apac/webhelp/descentmk3series/EN-SG/GUID-FD2F3418-577B-4AD7-A39E-A3E06AC6EB32-3775.html),
  [forum: dive data for custom faces](https://forums.garmin.com/developer/connect-iq/f/discussion/244569/dive-data-surf-interval-and-no-fly-time-to-be-available-for-custom-watch-faces),
  [forum: no-fly on Mk1 faces](https://forums.garmin.com/developer/connect-iq/f/discussion/172339/how-to-include-the-no-flight-time-info-in-a-custom-watch-face-for-mk1)
- **Sleep mode and battery saver can replace the Connect IQ face** with a system sleep face (time, date, alarms,
  DND) on schedule, unless Watch Face = "Don't change". Bugs where the face doesn't come back are reported on
  fēnix/epix *(3rd-party)*. Firmware 27.16 (about 2026-09) fixed "watch face would sometimes erroneously switch to
  battery saver mode" on the Mk3.
  [Sleep-mode thread](https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/epix-2/296845/sleep-mode-keeps-turning-back-on/1869470),
  [epix manual, Sleep Mode](https://www8.garmin.com/manuals/webhelp/GUID-E5C62F3F-DCE3-4197-8CA5-E419B2A55D12/EN-US/GUID-10B3BD3D-5B72-4C4F-9B2D-93432AF67E43.html),
  [Mk3 27.16 notes](https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/descent-mk3-series/442243/descent-mk3-software-version-27-16-rollout)

### Mockup resources
- **Simulator image:** `descentmk343mm.png`, 638×830 px, bronze bezel with a French-grey band. The display is at
  x 131, y 211, 390×390, centre (326, 406). The 51 mm image is 847×1089 with the display at (192, 309), 454×454.
  These are third-party copies of the SDK device files. The originals install with the Connect IQ SDK Manager under
  `~/Library/Application Support/Garmin/ConnectIQ/Devices/descentmk343mm/`.
  [43 mm image](https://github.com/wonderfulest/watch-face-designer/blob/main/src/assets/devices/descentmk343mm/descentmk343mm.png),
  [51 mm image](https://github.com/wonderfulest/watch-face-designer/blob/main/src/assets/devices/descentmk351mm/descentmk351mm.png),
  [path per forum](https://forums.garmin.com/developer/connect-iq/f/discussion/258441/maximum-memory-for-data-field-per-device)
- **Official product imagery:** Garmin product page 852217 (gallery) and the newsroom release. Garmin publishes no
  dimension drawing that I could find. Specs give 43×43×14.15 mm and a 30.4 mm display.
  [product page](https://www.garmin.com/en-US/p/852217/),
  [newsroom](https://www.garmin.com/en-US/newsroom/press-release/outdoor/garmins-new-descent-mk3-series-dive-computers-have-200-meter-dive-ratings-bright-amoled-displays-and-options-with-a-built-in-flashlight/)

## 2. What a Connect IQ face can use (Descent Mk3, API ≤ 5.1)

### Data on the watch
- **Modules available to faces** (with permission): ActivityMonitor, SensorHistory, Weather, UserProfile,
  Complications, Background, Notifications (5.1.0), BluetoothLowEnergy (3.1.0), WatchUi.
  **Not available:** Attention (vibration and tones), Communications (foreground), Sensor and Position. The App Types
  table also marks `Toybox.Activity` unavailable, though faces commonly read `Activity.getActivityInfo()` (see §6).
  [App Types](https://developer.garmin.com/connect-iq/articles/connect-iq-basics/App_Types.html)
- **Battery:** `Stats.battery` gives the percentage. **`Stats.batteryInDays`** (API 3.3.0) gives the battery time
  remaining the owner asked for, and `charging` is also available.
  [System.Stats](https://developer.garmin.com/connect-iq/api-docs/Toybox/System/Stats.html)
- **ActivityMonitor.Info:** steps and goal, floors (and goal, metres climbed), active or intensity minutes per day and
  week, move-bar level, respiration rate (3.3.0), time to recovery (3.3.0), **stressScore** (5.0.0, 30 s rolling),
  calories and distance.
  [ActivityMonitor.Info](https://developer.garmin.com/connect-iq/api-docs/Toybox/ActivityMonitor/Info.html)
- **SensorHistory:** heart rate, elevation, pressure, temperature, SpO2 (3.2.0), **body battery** and **stress**
  (3.3.0) histories. The simulator models HR at 60 s × 360 samples (6 h) and elevation/temperature/pressure at
  120 s × 180 (6 h) *(simulator values)*. → Sparklines of the last hours are possible from on-watch data.
  [SensorHistory](https://developer.garmin.com/connect-iq/api-docs/Toybox/SensorHistory.html),
  [simulator.json](https://github.com/wonderfulest/watch-face-designer/blob/main/src/assets/devices/descentmk343mm/simulator.json)
- **Activity.Info:** current HR, altitude (barometric or GPS), ambient and sea-level pressure, **currentHeading**
  (compass) and SpO2. There are no depth or dive fields.
  [Activity.Info](https://developer.garmin.com/connect-iq/api-docs/Toybox/Activity/Info.html)
- **Weather** (3.2.0): cached current conditions, hourly and daily forecast. **Sunrise and sunset** (3.3.0). The data
  comes from the phone through Garmin Connect, so it disappears when the watch is disconnected for long *(Segment34
  FAQ)*.
  [Weather](https://developer.garmin.com/connect-iq/api-docs/Toybox/Weather.html),
  [Segment34 FAQ](https://github.com/ludw/Segment34mkII/blob/main/FAQ.md)
- **UserProfile:** the user's sleep and wake time and the resting HR (7-day average). `upcomingSleepTime` needs 6.0.0,
  so not on the Mk3. **Background sleep and wake events** fire at those times. There is no API for "watch is in sleep
  mode": `isSleepMode` is deprecated.
  [UserProfile.Profile](https://developer.garmin.com/connect-iq/api-docs/Toybox/UserProfile/Profile.html),
  [Background](https://developer.garmin.com/connect-iq/api-docs/Toybox/Background.html)
- **DeviceSettings:** notificationCount, phoneConnected, alarmCount, is24Hour, firstDayOfWeek, isTouchScreen,
  requiresBurnInProtection, isEnhancedReadabilityModeEnabled, and doNotDisturb (where supported).
  **`System.getDisplayMode()`** (5.0.0) returns HIGH, LOW or OFF, with an `onDisplayModeChanged` callback.
  [DeviceSettings](https://developer.garmin.com/connect-iq/api-docs/Toybox/System/DeviceSettings.html),
  [System](https://developer.garmin.com/connect-iq/api-docs/Toybox/System.html)
- **Background events a face can register:** temporal (≥5 min), phone-app message (3.2.0, a push from a phone app),
  sleep and wake, steps, goal reached, activity completed (3.0.10).
  [Background](https://developer.garmin.com/connect-iq/api-docs/Toybox/Background.html)

### Complications
- **System complications (API 4.2.0+), 42 types (enum 1–42):**
  - Battery %, steps, calories, floors, intensity minutes.
  - Date ("28 Mar"), weekday + day ("Mon 28").
  - Current weather and 1/2/3-day forecast, current temperature, high/low.
  - Calendar events (next event time), sunrise, sunset (seconds since midnight).
  - Altitude, sea-level pressure, notification count, heart rate.
  - Weekly run and bike distance, recovery time (minutes), stress, body battery, VO2max run and bike, training
    status.
  - Race predictors 5K/10K/HM/M (time and pace), pulse ox, respiration rate, solar input.
  - Wheelchair pushes (4.2.3), last golf round (5.0.0), **sleep score (6.0.2, so not on the Mk3)**.

  Each complication gives a value, short label, unit and ranges.
  There is no HRV, training readiness or battery-in-days complication. HRV and readiness aren't exposed to Connect IQ
  at all *(not found in the API)*.
  [Complications module](https://developer.garmin.com/connect-iq/api-docs/Toybox/Complications.html)
- **Third-party complications:** any device app can publish **up to 4** complications (id 0–255) with value, short
  and long labels, units, an SVG icon and **ranges (≥3 values)**. Visibility is public (all apps and Face It),
  protected (your apps and Face It) or private. Faces subscribe and get a callback on change. If the publisher is
  uninstalled, the face gets an exception. → This is how CGM+ GB feeds faces today, and a glucose complication with
  ranges can be drawn as a gauge by any face, including a user's own **Face It** photo face.
  [Complications core topic](https://developer.garmin.com/connect-iq/articles/core-topics/Complications.html),
  [Face It and CIQ complications](https://forums.garmin.com/developer/connect-iq/f/discussion/352175/face-it-and-ciq-complications)
- **Ecosystem precedent:** Segment34 MkII (a popular open-source face) added "experimental support for CGM data from
  the CGM Connect Widget" through a complication in 2026-02. Mainstream faces now take glucose as just another slot.
  [Segment34 MkII changelog](https://github.com/ludw/Segment34mkII)

### Drawing
- **Colour and blending:** 16-bit colour, anti-aliasing (3.2.0), ARGB alpha in fills and strokes, bitmap textures and
  blend modes (4.0.0), affine transforms (4.2.0), tinted and filtered `drawBitmap2` (4.2.1). All are confirmed for the
  Descent Mk3. **Text along the circle:** `drawRadialText` and `drawAngledText` (4.2.1) use system vector fonts.
  [Dc](https://developer.garmin.com/connect-iq/api-docs/Toybox/Graphics/Dc.html),
  [Graphics](https://developer.garmin.com/connect-iq/api-docs/Toybox/Graphics.html)
- **System vector (TTF) fonts on the Mk3:** BionicBold (numbers only), RobotoCondensed Bold and Regular, Swiss721
  Bold and Regular, plus CJK, Thai and Arabic faces. `getVectorFont` takes any pixel size. Custom bitmap fonts are
  also possible.
  **Built-in bitmap font heights (43 mm):** xtiny 19, tiny 25, small 27, medium 32, large 35 px (Roboto);
  numberMild 42, numberMedium 51, numberHot 69, numberThaiHot 80 px (Bionic semi-bold).
  [simulator.json fonts](https://github.com/wonderfulest/watch-face-designer/blob/main/src/assets/devices/descentmk343mm/simulator.json),
  [flocsy ttf CSV](https://github.com/flocsy/garmin-dev-tools/blob/main/csv/device2ttf-fonts.csv)
- **Motion:** timers, animations and `WatchUi.AnimationLayer` (3.1.0) run **only in high-power mode**, the roughly
  10 s after a wrist raise.
  [Low/high-power modes](https://forums.garmin.com/developer/connect-iq/b/news-announcements/posts/changes-to-watch-face-low--and-high-power-modes),
  [App Types](https://developer.garmin.com/connect-iq/articles/connect-iq-basics/App_Types.html)

### Input on the face
- **Touch-and-hold only:** `WatchFaceDelegate.onPress` (4.2.0) is a touch-and-hold with coordinates, and it lists the
  Descent Mk3. The common pattern is hold on a complication → `Complications.exitTo(id)` launches the publishing app
  (e.g. a glucose app), which receives `:launchedFromComplication`. Segment34 exposes five configurable "press to
  open" areas.
  **Not on the Mk3:** `onTap` and the native face editor (5.1.0) list fēnix 8, Venu 4 and newer, not the Descent.
  [WatchFaceDelegate](https://developer.garmin.com/connect-iq/api-docs/Toybox/WatchUi/WatchFaceDelegate.html),
  [Complications core topic](https://developer.garmin.com/connect-iq/articles/core-topics/Complications.html),
  [Segment34 FAQ](https://github.com/ludw/Segment34mkII/blob/main/FAQ.md)

### Settings
- **Three channels:**
  1. **Phone:** settings XML or properties, edited in the Connect IQ Store mobile app or Garmin Express. This works
     only for store-published apps. The web store stopped managing apps in 2025-11.
  2. **On-device "Customize":** hold MENU on the face → Watch Face → Customize, a Menu2 menu returned by
     `getSettingsView()`. The face restarts after a change.
  3. **The native watch-face editor** (Styles, Data, Data Color, Accent Color, up to 4 saved configurations). This is
     fēnix 8 and newer only, **not the Descent Mk3**.

  [Watch Faces UX](https://developer.garmin.com/connect-iq/articles/user-experience-guidelines/Watch_Faces.html),
  [System 8 beta](https://forums.garmin.com/developer/connect-iq/b/news-announcements/posts/system-8-beta-now-available),
  [on-device settings thread](https://forums.garmin.com/developer/connect-iq/f/discussion/242673/ciq-3-2-watchface-setting-menu-getsettingsview-directly-to-menu2),
  [web store shutdown](https://the5krunner.com/2025/11/22/garmin-shuts-down-connect-iq-web-store-mobile-app-mandatory/)

### Getting the user's attention
- **A face cannot vibrate or play a tone:** Attention isn't available to faces or background code, and
  `requestApplicationWake` "will be ignored by watch face apps".
  **What exists:**
  - **`Toybox.Notifications.showNotification`** (5.1.0, title, subtitle, body, icon, action buttons) is available to
    faces with permission and lists the Descent Mk3. Whether it vibrates follows the system and is undocumented. A
    bug report says it doesn't render on a real Venu 3S.
  - **Phone notifications mirrored by Garmin Connect** vibrate per the watch's own notification settings. Dexcom's
    Garmin integration relies on this.

  [Background](https://developer.garmin.com/connect-iq/api-docs/Toybox/Background.html),
  [Notifications](https://developer.garmin.com/connect-iq/api-docs/Toybox/Notifications.html),
  [Venu 3S bug](https://forums.garmin.com/developer/connect-iq/i/bug-reports/notifications-shownotification-renders-in-venu-3s-simulator-but-not-on-physical-venu-3s),
  [streak_saver (Notifications tiers)](https://github.com/ricksy/streak_saver),
  [Healthline on Dexcom + Garmin](https://www.healthline.com/diabetesmine/garmin-and-dexcom-diabetes-data)

## 3. Platform notes (limits as facts, not design rules)

### Power modes and timing
- **Low power, the default:** `onUpdate` runs once a minute (top of the minute). There are no timers or animations.
- **High power:** after a wrist gesture the face updates every second for "typically 10 seconds". Users report about
  7 s for Connect IQ faces against 15 s for native faces on a fēnix 8 *(3rd-party)*. → Seconds and animation exist
  only in that window.
- **`onPartialUpdate` (every-second partial redraw) is MIP-only:** the Descent Mk3 is not in its device list.
- [Power modes post](https://forums.garmin.com/developer/connect-iq/b/news-announcements/posts/changes-to-watch-face-low--and-high-power-modes),
  [WatchFace](https://developer.garmin.com/connect-iq/api-docs/Toybox/WatchUi/WatchFace.html),
  [fēnix 8 "15 seconds" thread](https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/fenix-8-series/414860/comon-garmin-15-seconds)
- **Without AOD the screen is black between glances.** When the user enables AOD, faces are expected to support it,
  because users can disable the wake gesture.
  [Watch Faces UX](https://developer.garmin.com/connect-iq/articles/user-experience-guidelines/Watch_Faces.html)

### AOD / burn-in rules (how Garmin enforces them)
- **Rule history:**
  - **v1 (Venu era):** no pixel on for more than 4 minutes (old FAQ: 3 min) and at most 10 % of pixels lit.
  - **v2:** at most 10 % of pixels.
  - **API 5.0.0 (System 7) onward:** "the screen will stay on if the display is using less than **10 % of the total
    luminance**". The Descent Mk3 is a 5.1 device, so the luminance rule should apply *(inference)*.
  - "No pixel over 3 minutes" applied to Venu/Venu Sq only per Garmin (2022).

  [Entry Points UX](https://developer.garmin.com/connect-iq/articles/user-experience-guidelines/Entry_Points.html),
  [Welcome to System 7](https://forums.garmin.com/developer/connect-iq/b/news-announcements/posts/welcome-to-system-7),
  [Real Devices pt 1](https://forums.garmin.com/developer/connect-iq/b/news-announcements/posts/the-real-devices-of-connect-iq-part-1)
- **Enforcement is automatic:** a face that exceeds the limit **goes black in AOD**. This is a power limit, separate
  from burn-in. Garmin doesn't review it at store submission *(developers; no review step found)*. The simulator
  warns at >10 % luminance (Settings › Display Mode › Always On) and runs a 24 h burn-in heat map (File › View Screen
  Heat Map).
  [Always-on rules thread](https://forums.garmin.com/developer/connect-iq/f/discussion/322419/always-on-display-sleep-mode-behaviour-and-rules-on-newer-amoled-watches),
  [AOD example thread](https://forums.garmin.com/developer/connect-iq/f/discussion/398404/is-there-an-always-on-display-basic-example-anywhere)
- **Garmin's own AOD advice:** "Avoid using much white or blue. Consider using light gray", "Use fonts with thin line
  weights", "moving elements up to four pixels in any direction every minute".
  For scale: 10 % luminance on a 390 px circle (about 119,500 px) ≈ 12,000 px at full white, or every pixel at about
  10 % *(arithmetic; Garmin's luminance formula is unpublished)*.
  [Watch Faces UX](https://developer.garmin.com/connect-iq/articles/user-experience-guidelines/Watch_Faces.html)
- **Native activities are reported to pixel-shift whole screens**, and the system also dims. Garmin staff say "the
  display will eventually timeout and show a black screen… to avoid burn". Faces without a low-power mode show black.
  [pixel-shift thread](https://forums.garmin.com/developer/connect-iq/f/discussion/387519/do-all-official-garmin-watch-faces-in-the-connect-iq-pixel-shift),
  [fēnix 8 AOD black screen](https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/fenix-8-series/385282/fenix-8-51mm-amoled---11-64-black-screen-when-aod-is-always-on)

### Memory and resources (descentmk343mm)
- **Watch face 128 KB** (131,072 B; usable is about 4 KB less), **background 64 KB**, glance 64 KB, a **3 MB
  graphics resource pool**, and 10 MB app storage. Images compile to 8-bit dithered by default unless set otherwise.
  [flocsy memory CSV](https://github.com/flocsy/garmin-dev-tools/blob/main/csv/device2memory-watchFace.csv),
  [simulator.json](https://github.com/wonderfulest/watch-face-designer/blob/main/src/assets/devices/descentmk343mm/simulator.json),
  [colour-format thread](https://forums.garmin.com/developer/connect-iq/f/discussion/363187/color-palette-vs-bitsperpixel-vs-pixelformat)

### Phone data freshness
- **Faces reach the network only from a background service:** at most one temporal run per 5 min, about 30 s each,
  and exit data of about 8 KB. Only the *active* face gets background runs. This is the 5-min cadence in docs/08.
  [Background](https://developer.garmin.com/connect-iq/api-docs/Toybox/Background.html),
  [forum](https://forums.garmin.com/developer/connect-iq/f/discussion/323935/how-to-run-background-process-every-1-minute)
- **Faster paths exist:**
  - A phone app can **push** a Connect IQ message that wakes the face's background (`registerForPhoneAppMessageEvent`,
    3.2.0). An AAPS fork reports "a few seconds" latency. Gotchas: queued messages fill watch storage if not drained,
    and registrations go stale *(3rd-party)*.
  - A separate Connect IQ app can publish a complication the face subscribes to.
  - BLE is available to faces (3.1.0).

  [AAPS push face](https://github.com/Pwb0186/aaps-garmin-push-watchface),
  [App Types](https://developer.garmin.com/connect-iq/articles/connect-iq-basics/App_Types.html)

### Not available to Connect IQ faces on this watch
- Dive data (SI, no-fly, tissue, CNS/OTU), ambient light / Lume, Red Shift state, HRV status, training readiness,
  sleep-mode state, seconds in AOD, vibration and tone, GPS, live sensors, the native editor and `onTap`.
  Native faces are reported to use a low-power co-processor that Connect IQ faces lack *(3rd-party)*.
  [battery forum](https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/fenix-7-series/343768/does-watchface-from-connectiq-use-more-battery-than-native-built-in)

## 4. Garmin's design language

- **Garmin's UX guide:**
  - Devices have **two system fonts**: a text font (Roboto) for labels and a number font (Bionic) for numerals,
    "tested for readability… should be used whenever possible". Custom typefaces are for accent and brand.
  - On AMOLED, **light-on-dark**; headers are "a gradient that fades into black"; subtle background imagery is fine.
    "Dark-on-light… will look out of place on an AMOLED display".
  - "Identify a theme color… use that in your iconography and headers".
  - Edge computers switch themes by time of day.

  [Visual design and product personalities](https://developer.garmin.com/connect-iq/articles/user-experience-guidelines/Incorporating_the_Visual_Design_and_Product_Personalities.html)
- **Hardware tone:** "every pixel uses power" on AMOLED, so prefer gradients fading to black over bright solid fills.
  Specify a point size per resolution.
  [Real Devices pt 1](https://forums.garmin.com/developer/connect-iq/b/news-announcements/posts/the-real-devices-of-connect-iq-part-1),
  [Watch Faces UX](https://developer.garmin.com/connect-iq/articles/user-experience-guidelines/Watch_Faces.html)
- **Round screens:** the curvature hides parts of rectangular areas, and Connect IQ reports which edges are obscured
  for data fields. There is no published safe-area spec for faces.
  [Data Fields UX](https://developer.garmin.com/connect-iq/articles/user-experience-guidelines/Data_Fields.html)
- **Current native faces (fēnix 8 / AMOLED generation):** data-rich, gradients, stacked numbers. In AOD the time turns
  into a **thin outline**. Owners criticised them as "too flashy", with "terrible hierarchy", typography "in no way
  optimised for readability", time "impossible to read in AOD". DC Rainmaker found the default face grew on him and
  called it "Garmin's most customizable watch face to date". Garmin re-added plain analog faces after complaints.
  Firmware tweaked AOD brightness twice.
  [fēnix 8 "What happened to the UI?"](https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/fenix-8-series/382039/what-happened-to-the-ui/1823106),
  [more faces petition](https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/fenix-8-series/383169/feature-request-more-watchfaces-sign-under-campain/1857424),
  [DC Rainmaker fēnix 8](https://www.dcrainmaker.com/2024/08/garmin-fenix-8-in-depth-review.html)
- **Descent identity:** the 24 h ring with no-fly and alt-TZ pointers, Lume's turquoise glow, Red Shift, "Big
  Numbers" dive mode, and dive-coded tissue colours (green/yellow/red).
  [the5krunner Mk3](https://the5krunner.com/2023/11/13/garmin-descent-mk3-2/), manual links above.
- **Face It** (Garmin, 5M+ downloads): photo or preset background → layout → drag-placed complications → colours,
  built in the phone app and not editable on the watch. Since the 6.4-era update it can show **public Connect IQ
  complications**, though one developer saw values only refresh at face start *(3rd-party)*.
  [Wareable Face It guide](https://www.wareable.com/garmin/how-to-set-garmin-photo-watch-face-connect-iq),
  [forum](https://forums.garmin.com/developer/connect-iq/f/discussion/352175/face-it-and-ciq-complications)

## 5. The Connect IQ face ecosystem

- **Charts (end of 2026-10):**
  - **Free:** Face It (4.6★, 38k reviews, 5M+); **GLANCE** ("very large fonts", 4.9★/71k, 1M+); fēnix 7 Pro Analog
    clone (4.8★/40k, 1M+); Data Lover (live HR chart, 1M+); Simply Large (big digits, battery-saving, 4.3★); Black
    Grid (dense grid with graphs, paywall after 7-day trial); Seasons (art changes with the year); Crystal (4.4★, 1M+);
    Simple 2; Easy G1 (60+ stats).
  - **Paid:** Goals (progress bars, night mode), Rad-Lad (character that levels up with activity), Black Hawk Elite and
    Vanguard (100+ data options, tides), Rondo (8 fields, 4 typefaces, 31 themes, all editable on the watch),
    GreenBlack fēnix 8 clones.

  → Patterns users expect: **user-chosen data slots, many colour themes, giant time, goal meters, native-face
  look-alikes, playful or seasonal art, trials and pro tiers.**
  [the5krunner top faces](https://the5krunner.com/connect-iq/watch-faces/)
- **Crystal:** 1M+ downloads and Garmin's 2019 Best New Watch Face. It had 3 data fields, 3 indicators, 2 side meters
  and a dozen colour schemes. Its 2026 "Reborn" shows what users kept asking for: Body Battery, full-colour AOD (the
  hour font becomes an outline and meters thin out), per-element AOD toggles and brightness, 30+ complications,
  on-watch settings ("a million times easier"), custom hex themes, and coloured or uniform icons.
  [Crystal Reborn](https://the5krunner.com/2026/03/20/garmin-crystal-watchface-reborn/)
- **Segment34 MkII (open source):**
  - **Its FAQ maps recurring confusion:**
    - Settings exist only in the phone app.
    - "Why are seconds disappearing?" (AMOLED can't show them in AOD).
    - Blank fields when the watch lacks a metric.
    - Weather vanishing when Bluetooth drops.
  - **Its requests list:** adjustable font size, goal markers, icon-style notifications, a second custom theme.
  - **Other details:** separate AOD-only fields, a web theme designer that outputs a colour string, and an OpenWeather
    API key that caused "quite a lot of negative reviews".

  [Segment34 MkII](https://github.com/ludw/Segment34mkII),
  [FAQ](https://github.com/ludw/Segment34mkII/blob/main/FAQ.md)
- **Battery complaints:**
  - Native faces use about 10–13 %/day.
  - Third-party faces used 25–33 %/day on a vívoactive 4 after firmware 5.10, and about 15 %/day on an epix 2.
  - Well-made AMOLED faces with AOD (Segment34, BigEasy) measured about 8–10 %/day on a fēnix 8 47 mm.
  - Every-second HR polling is a known drain *(all 3rd-party)*.

  [fēnix 8 consumption thread](https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/fenix-8-series/409574/watchface-battery-consumption-collecting-data-amoled-47mm),
  [VA4 bug report](https://forums.garmin.com/developer/connect-iq/i/bug-reports/battery-drain-issues-on-va4-and-4s-using-ciq-watch-faces-since-sw-5-10-release?CommentId=fa574d5b-5eaa-4732-b0b8-db21bfcaac7b),
  [epix 2 thread](https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/epix-2/312741/epix-2---battery-drain-depending-on-watch-face)
- **Other recurring complaints:**
  - AOD black-outs on third-party faces.
  - Dim AOD unreadable in sun.
  - Outline digits.
  - Data overflowing in FR/IT/DE translations (fēnix 8 "Portal").
  - Paywalls.
  - Settings that don't sync (bug report).
  - Faces reverting to default after sleep mode.

  [settings sync bug](https://forums.garmin.com/developer/connect-iq/i/bug-reports/watch-face-settings-not-updated-synced-correctly-in-the-connect-iq-mobile-apps-after-performing-settings-change),
  threads above.
- **AOD strategies developers use** (static at the limit vs "jumping" time, outline fonts, off-centre offsets, hands
  sweeping a full circle hourly). Developers worry users will blame them for burn-in if offered a "static" option.
  [jumping vs static](https://forums.garmin.com/developer/connect-iq/f/discussion/372772/always-on-display---implementations-jumping-vs-static)

## 6. Contradictions and open questions

- **Burn-in rule versions:** pixel time limit of 3 vs 4 min, and pixels vs luminance. Which rule the Descent Mk3
  firmware enforces is not officially stated (API 5.1 suggests luminance).
- **High-power duration:** Garmin says "typically 10 s"; fēnix 8 users measured about 7 s for Connect IQ faces vs 15 s
  native. It is unmeasured on the Descent, and it is unclear whether the user's Timeout setting changes it.
- **`Toybox.Activity`:** the App Types table marks it unavailable to faces, yet faces widely read
  `Activity.getActivityInfo()` (HR, altitude, heading). Verify on device.
- **Notifications vibration:** `showNotification` is available to faces on the Mk3, but whether it vibrates, and
  whether it works on the real Descent (a Venu 3S bug says no render), is unknown.
- **Red Shift:** whether it re-tints Connect IQ faces is inferred from "turns the screen" wording, not tested. Its
  colours are red-only in older manuals and red/green/orange in newer ones.
- **Brightness (nits), ambient-light auto-brightness and the exact AOD dimming level** are unpublished.
- **Bezel ring:** the SDK images show the bronze and carbon variants. The owner's colourway is unknown, and the Mk3
  (stainless) 43 mm ring was not checked.
- **Complication count:** the official enum has 42 types. A 2023 forum post said "39 native". The newer ones were
  added later.
- **Weather and sunrise:** Garmin docs have a copy error (`getSunset` says "Get sunrise time").
- **Not researched (web-search budget ran out):** Reddit (blocked to the crawler) and individual Store review pages.
  Ecosystem sentiment comes from Garmin forums, the5krunner and GitHub.

## Sources read

1. Garmin newsroom, Descent Mk3 launch (2023-11-14) — https://www.garmin.com/en-US/newsroom/press-release/outdoor/garmins-new-descent-mk3-series-dive-computers-have-200-meter-dive-ratings-bright-amoled-displays-and-options-with-a-built-in-flashlight/
2. Garmin product page Descent Mk3i 43 mm — https://www.garmin.com/en-US/p/852217/
3. Descent Mk3 manual PDF — https://www8.garmin.com/manuals/webhelp/GUID-9183E86B-2399-4CFC-AB50-EAFC6D6ED326/EN-US/Descent_Mk3_Series_OM_EN-US.pdf
4. Mk3 manual: Battery — https://www8.garmin.com/manuals/webhelp/GUID-9183E86B-2399-4CFC-AB50-EAFC6D6ED326/EN-US/GUID-1D06C8ED-1134-4AB1-8270-F816F3E9608B.html
5. Mk3 manual: Screen Settings (US) — https://www8.garmin.com/manuals/webhelp/GUID-9183E86B-2399-4CFC-AB50-EAFC6D6ED326/EN-US/GUID-C9A5E731-8826-4AD0-8087-F6E2EAC625D6.html
6. Mk3 manual: Screen Settings (APAC) — https://www8.garmin.com/manuals-apac/webhelp/descentmk3series/EN-SG/GUID-82EF25E6-C1AF-4487-8D86-DFFAB766F0AC-6966.html
7. Mk3 manual: Controls — https://www8.garmin.com/manuals/webhelp/GUID-9183E86B-2399-4CFC-AB50-EAFC6D6ED326/EN-US/GUID-700E76C4-F7E2-4984-8199-D59D6A31DFB9.html
8. Mk3 manual: Default Watch Face — https://www8.garmin.com/manuals-apac/webhelp/descentmk3series/EN-SG/GUID-E9854CDA-A767-443F-867E-0D1B31503B78-8369.html
9. Mk3 manual: Watch Face Settings — https://www8.garmin.com/manuals/webhelp/GUID-9183E86B-2399-4CFC-AB50-EAFC6D6ED326/EN-US/GUID-A4EA51C3-D0B2-48E3-807A-768C6F07D7BA.html
10. Mk3 manual: Surface Interval glance — https://www8.garmin.com/manuals/webhelp/GUID-9183E86B-2399-4CFC-AB50-EAFC6D6ED326/EN-US/GUID-5B6754B2-AC50-4686-AD61-8BCC57AE2D55.html
11. Mk3 manual: No-Fly Time — https://www8.garmin.com/manuals-apac/webhelp/descentmk3series/EN-SG/GUID-FD2F3418-577B-4AD7-A39E-A3E06AC6EB32-3775.html
12. ManualsLib Mk3i p.95 — https://www.manualslib.com/manual/3927116/Garmin-Descent-Mk3i.html?page=95
13. fēnix 8 manual: AMOLED display — https://www8.garmin.com/manuals/webhelp/GUID-EECCAC99-90D6-4AB1-9A3A-EC433D3365E2/EN-US/GUID-8ECEF6B4-5257-44E9-BB09-778836BBF700.html
14. epix manual: Sleep Mode — https://www8.garmin.com/manuals/webhelp/GUID-E5C62F3F-DCE3-4197-8CA5-E419B2A55D12/EN-US/GUID-10B3BD3D-5B72-4C4F-9B2D-93432AF67E43.html
15. DIVE Magazine, Mk3 vs Mk3i — https://divemagazine.com/scuba-diving-equipment/a-closer-look-at-the-garmin-descent-mk3-and-mk3i
16. the5krunner, Descent Mk3 — https://the5krunner.com/2023/11/13/garmin-descent-mk3-2/
17. Gadgets & Wearables specs — https://gadgetsandwearables.com/technical-specs/garmin-descent-mk3i-43-mm/
18. American Diving Supply specs — https://www.americandivingsupply.com/Garmin-Descent-Mk3i-Watch-43mm-p/010-02753-1x.htm
19. Garmin forum: Mk3i display type — https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/descent-mk3-series/352611/descent-mk3i-display-type
20. Garmin forum: Mk3 27.16 — https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/descent-mk3-series/442243/descent-mk3-software-version-27-16-rollout
21. Garmin forum: Lume on other models — https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/fenix-7-series/370235/is-the-lume-feature-from-the-descent-coming-to-any-other-model/1770178
22. CIQ Compatible Devices — https://developer.garmin.com/connect-iq/compatible-devices/
23. SDK simulator.json + images (3rd-party copy) — https://github.com/wonderfulest/watch-face-designer/tree/main/src/assets/devices/descentmk343mm
24. flocsy garmin-dev-tools CSVs — https://github.com/flocsy/garmin-dev-tools/tree/main/csv
25. Toybox.Complications — https://developer.garmin.com/connect-iq/api-docs/Toybox/Complications.html
26. Complications core topic — https://developer.garmin.com/connect-iq/articles/core-topics/Complications.html
27. WatchFaceDelegate — https://developer.garmin.com/connect-iq/api-docs/Toybox/WatchUi/WatchFaceDelegate.html
28. WatchFace — https://developer.garmin.com/connect-iq/api-docs/Toybox/WatchUi/WatchFace.html
29. App Types — https://developer.garmin.com/connect-iq/articles/connect-iq-basics/App_Types.html
30. Background — https://developer.garmin.com/connect-iq/api-docs/Toybox/Background.html
31. Notifications — https://developer.garmin.com/connect-iq/api-docs/Toybox/Notifications.html
32. DeviceSettings — https://developer.garmin.com/connect-iq/api-docs/Toybox/System/DeviceSettings.html
33. System — https://developer.garmin.com/connect-iq/api-docs/Toybox/System.html
34. System.Stats — https://developer.garmin.com/connect-iq/api-docs/Toybox/System/Stats.html
35. Graphics.Dc — https://developer.garmin.com/connect-iq/api-docs/Toybox/Graphics/Dc.html
36. Graphics — https://developer.garmin.com/connect-iq/api-docs/Toybox/Graphics.html
37. ActivityMonitor.Info — https://developer.garmin.com/connect-iq/api-docs/Toybox/ActivityMonitor/Info.html
38. SensorHistory — https://developer.garmin.com/connect-iq/api-docs/Toybox/SensorHistory.html
39. Weather — https://developer.garmin.com/connect-iq/api-docs/Toybox/Weather.html
40. Activity.Info — https://developer.garmin.com/connect-iq/api-docs/Toybox/Activity/Info.html
41. UserProfile.Profile — https://developer.garmin.com/connect-iq/api-docs/Toybox/UserProfile/Profile.html
42. UX: Watch Faces — https://developer.garmin.com/connect-iq/articles/user-experience-guidelines/Watch_Faces.html
43. UX: Entry Points — https://developer.garmin.com/connect-iq/articles/user-experience-guidelines/Entry_Points.html
44. UX: Visual design & product personalities — https://developer.garmin.com/connect-iq/articles/user-experience-guidelines/Incorporating_the_Visual_Design_and_Product_Personalities.html
45. UX: Data Fields — https://developer.garmin.com/connect-iq/articles/user-experience-guidelines/Data_Fields.html
46. CIQ news: Low/high-power modes — https://forums.garmin.com/developer/connect-iq/b/news-announcements/posts/changes-to-watch-face-low--and-high-power-modes
47. CIQ news: Welcome to System 7 — https://forums.garmin.com/developer/connect-iq/b/news-announcements/posts/welcome-to-system-7
48. CIQ news: System 8 beta — https://forums.garmin.com/developer/connect-iq/b/news-announcements/posts/system-8-beta-now-available
49. CIQ news: Real Devices of CIQ pt 1 (2022-02-28) — https://forums.garmin.com/developer/connect-iq/b/news-announcements/posts/the-real-devices-of-connect-iq-part-1
50. Forum: AOD rules on newer AMOLED — https://forums.garmin.com/developer/connect-iq/f/discussion/322419/always-on-display-sleep-mode-behaviour-and-rules-on-newer-amoled-watches
51. Forum: AOD basic example — https://forums.garmin.com/developer/connect-iq/f/discussion/398404/is-there-an-always-on-display-basic-example-anywhere
52. Forum: AOD jumping vs static — https://forums.garmin.com/developer/connect-iq/f/discussion/372772/always-on-display---implementations-jumping-vs-static
53. Forum: Venu 3 AOD — https://forums.garmin.com/developer/connect-iq/f/discussion/354108/always-on-display-on-venu-3
54. Forum: official faces pixel shift — https://forums.garmin.com/developer/connect-iq/f/discussion/387519/do-all-official-garmin-watch-faces-in-the-connect-iq-pixel-shift
55. Forum: fēnix 8 AOD black screen — https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/fenix-8-series/385282/fenix-8-51mm-amoled---11-64-black-screen-when-aod-is-always-on
56. Forum: fēnix 8 "15 seconds" — https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/fenix-8-series/414860/comon-garmin-15-seconds
57. Forum: fēnix 8 "What happened to the UI?" — https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/fenix-8-series/382039/what-happened-to-the-ui/1823106
58. Forum: fēnix 8 more watch faces — https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/fenix-8-series/383169/feature-request-more-watchfaces-sign-under-campain/1857424
59. Forum: fēnix 8 face battery data — https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/fenix-8-series/409574/watchface-battery-consumption-collecting-data-amoled-47mm
60. Forum: dive data for custom faces — https://forums.garmin.com/developer/connect-iq/f/discussion/244569/dive-data-surf-interval-and-no-fly-time-to-be-available-for-custom-watch-faces
61. Forum: background every 1 minute — https://forums.garmin.com/developer/connect-iq/f/discussion/323935/how-to-run-background-process-every-1-minute
62. Forum: on-device settings Menu2 — https://forums.garmin.com/developer/connect-iq/f/discussion/242673/ciq-3-2-watchface-setting-menu-getsettingsview-directly-to-menu2
63. Forum: Face It and CIQ complications — https://forums.garmin.com/developer/connect-iq/f/discussion/352175/face-it-and-ciq-complications
64. Forum bug: showNotification Venu 3S — https://forums.garmin.com/developer/connect-iq/i/bug-reports/notifications-shownotification-renders-in-venu-3s-simulator-but-not-on-physical-venu-3s
65. AAPS push watch face (GitHub) — https://github.com/Pwb0186/aaps-garmin-push-watchface
66. Segment34 MkII + FAQ (GitHub) — https://github.com/ludw/Segment34mkII
67. streak_saver (GitHub) — https://github.com/ricksy/streak_saver
68. the5krunner top watch faces (2026-10) — https://the5krunner.com/connect-iq/watch-faces/
69. the5krunner Crystal Reborn (2026-03) — https://the5krunner.com/2026/03/20/garmin-crystal-watchface-reborn/
70. the5krunner CIQ web store shutdown — https://the5krunner.com/2025/11/22/garmin-shuts-down-connect-iq-web-store-mobile-app-mandatory/
71. DC Rainmaker fēnix 8 review — https://www.dcrainmaker.com/2024/08/garmin-fenix-8-in-depth-review.html
72. Wareable Face It guide — https://www.wareable.com/garmin/how-to-set-garmin-photo-watch-face-connect-iq
73. ScubaLab Mk3/Mk3i (search snippet only) — https://www.scubadiving.com/garmin-descent-mk3-scubalab
74. Healthline DiabetesMine, Dexcom + Garmin (search snippet only) — https://www.healthline.com/diabetesmine/garmin-and-dexcom-diabetes-data
75. DIVEIN Mk3 review (403; search snippet only) — https://www.divein.com/review/garmin-descent-mk3-mk3i/
