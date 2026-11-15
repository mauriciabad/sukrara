# C — What horology and conceptual watch design teach about displaying time and data

Research input for docs/42. It covers knowledge, constraints and options only: no design and no preferred style. Every finding links its source.

Markers:
- **[3P]**: a third-party, enthusiast or retailer claim that wasn't checked against a primary source.
- **[snippet]**: seen only in a search-result extract; the page wasn't read in full.
- **[INVENTED]**: an encoding reasoned out in this report, not found in any product.

Ratings of precision, glance cost and discretion are this report's judgement unless a source is cited.

Catalogue legend:
- **Glance**: L = one look (under about 1 s). M = a second fixation, counting or a learned key. H = decoding, arithmetic or a button press.
- **Disc.** (discretion): P = public (any onlooker can read it). C = coded (it looks like ordinary watch furniture, and you need a private key to read it). H = hidden until the wearer asks.

---

## 1. Principles of dial craft (finding → why it matters for a face → source)

### 1.1 Hierarchy and roles
- **Pointers must be unmistakable from each other in the worst light.** ISO 6425 requires that "the minute hand shall be clearly distinguishable from the hour hand" at 25 cm in the dark. It also requires an "indication that the watch is running", and, for battery watches, a battery end-of-life indication. → Any face with two pointers, two rings or two similar numbers needs a difference that survives darkness and dimming, not only a colour difference. Freshness ("is this live?") is a requirement with a precedent in a standard. [ISO 6425:1996 §6.2 sample](https://cdn.standards.iteh.ai/samples/12774/166555c91df4457294ef397a7a10d6a4/ISO-6425-1996.pdf). Sources conflict on how long the glow must last: [divewatch.com](https://divewatch.com/reference/iso-6425/) says 60 min, [HELM](https://helmwatches.com/iso-6425.html) says 180 min [3P].
- **The longest radius goes to the datum that needs the most precision.** The B-Uhr Type B puts minutes 5–55 on the outer ring and the hours on an inner ring, because navigators cared about minutes ([Quill & Pad](https://quillandpad.com/2020/07/11/a-crash-course-in-b-uhren-and-flieger-pilots-watches-covering-both-historic-and-modern-examples-a-pilots-watch-photofest/)). The regulator gives the central hand to the minutes and moves the hours to a subdial, "where imprecision does not matter much" ([Ethos](https://www.ethoswatches.com/the-watch-guide/regulator-display-time-watches/) [3P]). → Rank the data by the precision it needs, not by convention. That ranking decides which datum gets the perimeter.
- **A hand's reach ties it to its scale.** The minute hand should reach the minute track and the hour hand the indices; wrong lengths "read as wrong before you can say why" ([The Horologue](https://thehorologue.com/article/the-dial)).
  - Rules of thumb from a forum: hour hand ≈0.5–0.65 r, minute hand ≈0.75–0.95 r, seconds hand ≈0.9–0.975 r, where r is the visible dial radius ([Watch Repair Talk](https://www.watchrepairtalk.com/topic/26439-dial-hand-length-ratio-rule-of-thumb/) [3P]).
  - Max Bill's baton hands reach exactly to the minute track ([Fratello](https://www.fratellowatches.com/tbt-iconic-bauhaus-watch-junghans-max-bill/)).
  - → When a data pointer shares a dial with the time, its length can say which scale it reads.
- **One unmistakable orientation anchor.** Examples:
  - The B-Uhr has a triangle flanked by two dots at 12, "to help pilots orient the dial under stress".
  - Rolex's California dial uses Roman numerals on the top half and Arabic numerals on the bottom half. Rolex called it "error-proof" for divers ([Monochrome](https://monochrome-watches.com/dial-styles-history-and-evolution-of-a-watchs-crucial-part-abcs-of-time/)).
  - Grand Seiko uses a double-width index at 12 ([Grand Seiko](https://www.grand-seiko.com/us-en/worldofgrandseiko/design/grandseikostyle)).
  - Movado's Museum dial has a single dot at 12, "the sun at high noon" ([Wikipedia](https://en.wikipedia.org/wiki/Nathan_George_Horwitt)).
  - → A round face without numerals depends on one landmark, and the wrist rotates. Mixing glyph systems can also encode which half of the dial you're looking at.
- **Text is a ranked list of claims, and restraint reads as quality.** "The great dials say remarkably little" ([The Horologue](https://thehorologue.com/article/the-dial)). MIL-PRF-46374G forbids maker marks on the dial and fixes the layout: black dial, white numerals, 1–12 inside the hour marks, and 13–24 in a smaller concentric ring ([MIL-PRF-46374G](https://hagensieker.com/wp-content/uploads/2019/10/MIL-PRF-46374G.pdf)). → Every glyph competes with the data. The military spec shows that a second scale can sit inside the first as a smaller concentric ring.
- **Extra hands cost legibility, and distinct tips win it back.** A fourth central hand makes the time harder to read, so pointer-date hands get a crescent, triangle or coloured tip ([WatchTime](https://www.watchtime.com/brands/automatic-watches/watch-date-displays-explained)). Subdials "add business", and triple-date subdials make you "stop and look for longer" than necessary ([Fratello](https://www.fratellowatches.com/different-date-displays-watches/)).

### 1.2 Type, contrast, light
- **One typeface and few colours.** Dietrich Lubs "only ever used one font, Akzidenz-Grotesk," on all his Braun dials. Dial graphics "should be clear and not use a lot of fonts", and the palette was "black and white and a bit of metal" ([Dezeen](https://www.dezeen.com/2017/09/18/braun-philosophy-about-reducing-bling-bling-watch-clock-designer-dietrich-lubs-interview/)). Other examples:
  - Railroad standards banned Roman numerals in favour of large black Arabic numerals ([Monochrome](https://monochrome-watches.com/dial-styles-history-and-evolution-of-a-watchs-crucial-part-abcs-of-time/)).
  - The Nomos Tangente's long, narrow numerals "clearly and evenly" segment the empty dial ([Gear Patrol](https://www.gearpatrol.com/watches/a519830/time-and-type/)).
  - Max Bill drew his own numerals for Junghans ([Fratello](https://www.fratellowatches.com/tbt-iconic-bauhaus-watch-junghans-max-bill/)).
  - → A numeral's shape serves legibility and identity at once. A few numerals can structure the whole dial.
- **Contrast can come from light.** Taro Tanaka's "Grammar of Design" (1962–67) uses flat, multi-faceted, mirror-polished hands and indices that catch light ([Worn & Wound](https://wornandwound.com/art-time-taro-tanaka-seikos-grammar-design/)). → An AMOLED screen gives off its own light and reflects none, so contrast comes from luminance against black. That is the analogue of lume, not of polish.
- **The lume's hue changes its brightness at night.** Green C3 (~515 nm) looks brightest in the dark because it is close to the peak sensitivity of night vision. Blue BGW9 (~485 nm) looks slightly dimmer ([Aquastar](https://aquastar.ch/journal/superluminova-lume-explained/), [WatchTime](https://www.watchtime.com/brands/sports-watch/history-of-watch-lume-radium-tritium-super-luminova)). → On a dimmed night face, two hues at equal energy don't look equally bright.
- **One accent colour, one role, borrowed meaning.** Every Braun alarm clock had a yellow seconds hand ([Cooper Hewitt](https://www.cooperhewitt.org/2018/02/24/ahead-of-the-times/)). Hilfiker's red seconds hand (1953) is shaped like the railway dispatcher's signal baton ([Wikipedia](https://en.wikipedia.org/wiki/Swiss_railway_clock)). → An accent copied from a real-world signal arrives with that signal's meaning.
- **Hand angles carry emotion.** People rate 10:10 as smile-like, and it modestly raised pleasure and purchase intent; 8:20 reads as sad ([Karim et al. 2017](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC5572348/)). The samples were small and the effect modest ([RealClearScience](https://www.realclearscience.com/articles/2022/05/09/why_are_watches_usually_set_to_1010_in_advertisements_830409.html)). → The angle a data pointer rests at may be read as a mood.

### 1.3 Precision is a design variable
- **Coarse precision can be a deliberate choice.** MeisterSinger watches have one hand and 144 strokes, each worth 5 minutes. The brand says they read "almost to the nearest minute" ([MeisterSinger](https://meistersinger.com/en/the-brand/single-hand-watches/)). Japanese customers told the brand "it doesn't really matter much whether it's 13:29 or 13:31" ([Monochrome](https://monochrome-watches.com/the-intriguing-philosophy-of-time-that-carved-meistersinger-out-its-niche/)).
- **Layered precision lets the glance stop early:**
  - QlockTwo's words change every 5 min, and four corner dots add 0–4 min ([New Atlas](https://newatlas.com/qlocktwo-typographic-clock/12620/)).
  - The Pebble "Fuzzy Text" faces use natural language with 5-minute precision ([Rebble](https://apps.repebble.com/fuzzy-text-plus_52de476d6094ff6bf0000046)).
  - TTMMORE rounds so that minutes 3 and 8 jump forward and minutes 2 and 7 "lean back" ([TTMM](https://ttmm.is/portfolio/tic-ttmmore/)).
  - The Semper Opera five-minute clock (1841) shows the hour in Roman numerals and the minutes in 5-minute Arabic steps. It was readable from every seat without disturbing the performance ([Quill & Pad](https://quillandpad.com/2021/04/11/dresden-semper-opera-five-minute-clock-the-inspiration-for-a-lange-sohnes-large-date-celebrates-180th-anniversary/)).
  - → The face offers precision instead of imposing it. Rounding needs a rule for which way it goes.
- **Reading speed depends on the question:**
  - For absolute time, digital displays read 3.5–4× faster with 10× fewer errors, according to a 1971 review ([Applied Ergonomics](https://pubmed.ncbi.nlm.nih.gov/15676699/)).
  - A 2023 study found digits faster and more accurate even for people who prefer clock faces. Errors clustered at hand angles of 30° and 60° ([Springer, HCII 2023](https://link.springer.com/chapter/10.1007/978-3-031-35681-0_27)).
  - Analogue displays were faster for time-difference tasks. The authors note a confound: the digital format required subtraction ([MDPI 2024](https://www.mdpi.com/2076-328X/14/9/847)).
  - → "What is the value?" favours digits. "How far from a threshold?" or "how long until?" favours position.

### 1.4 Motion carries meaning
- **Sweep, tick and pause say different things:**
  - Spring Drive's glide is said to express "the natural flow of time" ([Grand Seiko](https://www.grand-seiko.com/us-en/collections/movement/springdrive)).
  - Deadbeat ("dead") seconds were built so observatories could read the exact second ([watch-movements.eu](https://watch-movements.eu/blog/en/2022/06/04/a-dead-beat-seconds-movement-140-years-old/)).
  - Hilfiker's stop-to-go hand laps in 58.5 s, then waits about 1.5 s for the master clock's minute pulse, to "bring calm in the last moment" before trains leave ([Galaxus](https://www.galaxus.at/en/page/come-to-think-of-it-why-does-the-second-hand-of-the-swiss-railway-clock-pause-at-12-21628), [Wikipedia](https://en.wikipedia.org/wiki/Swiss_railway_clock)).
  - → The style of movement means flowing, measured, or synchronised with something outside the watch.
- **A change of rhythm can be an alarm.** On Citizen Eco-Drive and Seiko Kinetic watches, the seconds hand jumps in 2-second steps when charge is low. On Citizen, an irregular 1-1-2 pattern means the watch stopped, restarted, and now shows the wrong time ([Citizen FAQ](https://www.citizenwatch-global.com/support/faq/gfaq/malfunction), [Seiko 3M22 manual](https://www.seikowatches.com/us-en/-/media/Files/Common/Seiko/instructions/English/3/3M22/3M22.pdf)). → An existing element's rhythm can carry a second datum and a warning without lighting more pixels.
- **A transition can mark the moment of update.** The 24 clocks of ClockClock 24 spin in choreographed patterns and line up into digits once a minute ([Humans since 1982](https://www.humanssince1982.com/en-us/blogs/concepts/the-clock-clock-2008-2010)). Apple's Flux numerals "snap into new forms when the minute changes" ([Apple](https://support.apple.com/guide/watch/faces-and-features-apde9218b440/watchos)).

### 1.5 The glance budget (evidence)
- **Glances are short.** In Pizza et al. (CHI 2016), about half of all smartwatch uses were time checks, averaging about 2 s; notifications averaged about 7 s ([cited in Blascheck et al. 2018](https://www.microsoft.com/en-us/research/wp-content/uploads/2018/08/GlanceableVis-InfoVis2018.pdf)). Across 307 users, more than half of all sessions were "peeks" of 5 s or less ([Visuri et al., CHI 2017](https://www.kostakos.org/papers/chi17.pdf)).
- **Concentric arcs are slow to compare.** Comparing two values on a watch took under 220 ms with a donut chart and under 300 ms with a bar chart, but up to about 1.8 s with radial bars, i.e. concentric arcs ([Blascheck et al. 2018](https://www.microsoft.com/en-us/research/wp-content/uploads/2018/08/GlanceableVis-InfoVis2018.pdf)). → Concentric ring gauges are costly when values must be compared. The study tested comparison, not reading a single value.
- **Some features are seen pre-attentively.** Hue, size, orientation, length, luminance, flicker, curvature, and the direction and velocity of motion are detected in under 200–250 ms, before focused attention. The effect is asymmetric: a tilted line among vertical ones pops out, but not the reverse ([Healey, NCSU](https://www.csc2.ncsu.edu/faculty/healey/PP/)). → Use these channels for what must reach peripheral vision, and avoid them for what should stay discreet.
- **Calm technology** moves information between the periphery and the centre of attention: "the periphery is informing without overburdening", and "the individual... must be in charge" of the moves ([Weiser & Brown 1995](https://calmtech.com/papers/designing-calm-technology)). Apple's Alan Dye described early Watch work as "how do we impart a great deal of data on such a small screen at-a-glance?" ([9to5Mac](https://9to5mac.com/2020/10/21/apple-watch-ui/)).
- **Learning curves:**
  - Tokyoflash RPM: "surprisingly easy to tell time after a couple tries" ([SlashGear](https://www.slashgear.com/tokyoflash-kisai-rpm-watch-review-a-minimalist-led-watch-26370567/)).
  - Optical Illusion: "I don't want to solve a puzzle... I just want to know the time" ([Gear Diary](https://geardiary.com/2014/05/07/tokyoflash-kisai-optical-illusion-watch-secret/)).
  - Ochs und Junior's date dots: "after a short time, you can recognize the date intuitively". The brand claims they read better from a distance than digits ([Ochs und Junior](https://www.ochsundjunior.swiss/watches/moon-phase/)), and an owner agrees ([Fifth Wrist](https://www.fifthwrist.com/ochs-und-junior-moonphase-review/) [3P]).
  - Nooka Zot: checking the time was "a pain" at first and fast with practice ([Wristwatch Review](https://www.wristwatchreview.com/review-nooka-zot-2/) [snippet]).

### 1.6 Displays that hide, reveal or react
- **Mechanical and electronic watches already hide and reveal:**
  - Hermès L'heure masquée parks the hour hand under the minute hand and shows it only while the crown pusher is held ([SJX](https://watchesbysjx.com/2014/03/baselworld-2014-introducing-the-hermes-dressage-lheure-masquee-a-dual-time-zone-with-a-secret-hour-hand-with-specs-and-price.html)).
  - Le Temps Suspendu: the pusher sends the hands to an unreadable "11:62" V and hides the retrograde date. The mechanism keeps running, and pressing again shows the true time ([Revolution](https://revolutionwatch.com/a-closer-look-hermes-arceau-le-temps-suspendu/)).
  - L'heure impatiente counts down the last 60 minutes before a set time on a fan-shaped scale, then chimes ([Monochrome](https://monochrome-watches.com/hands-hermes-lheure-impatiente-poetical-alarm-complication/)).
  - The 1972 Pulsar stayed dark until pressed: a short press showed hours and minutes, and holding longer showed seconds. The LEDs drew too much power to stay on ([Penn State](https://pabook.libraries.psu.edu/literary-cultural-heritage-map-pa/feature-articles/pulsar-revolution-display-technology)).
  - Tokyoflash Kisai Online has an accelerometer "cryptic mode" that shows the time when you swing the watch towards you ([Digital Trends](https://www.digitaltrends.com/cool-tech/hands-on-with-tokyoflash-kisai-online/)).
  - Apple's Metropolitan numerals "rotate to become pills when your wrist is down", and Contour "gradually changes to highlight the current hour" ([Apple](https://support.apple.com/guide/watch/faces-and-features-apde9218b440/watchos)).
- → Hiding buys discretion and calm, but loses the zero-effort glance (a complaint about button-activated Tokyoflash models [snippet]).

### 1.7 Instruments that show a value with a direction and a "good zone"
- **Zones drawn on the scale.** FAA airspeed indicators use a standard colour code: a white arc (flap range), a green arc (normal range), a yellow arc (caution, smooth air only) and a red line (never exceed). Glass cockpits keep the same zones on tape displays ([Wikipedia, citing the FAA's Pilot's Handbook](https://en.wikipedia.org/wiki/Airspeed_indicator)). → This is a safety-tested vocabulary in which the zones live on the scale and the pointer stays neutral.
- **A finer scale where decisions happen.** The Blancpain X Fathoms has a 0–90 m main scale plus a geared-up hand for 0–15 m that covers ¾ of a turn, with the 3–6 m decompression band luminous in orange. It also has a maximum-depth memory hand and a 5-minute retrograde decompression counter ([Monochrome](https://monochrome-watches.com/blancpain-x-fathoms/), [aBlogtoWatch](https://www.ablogtowatch.com/blancpain-x-fathoms-watch-hands-on/) [snippet]). → Magnify the scale where decisions are made, and remember the extreme value.
- **A trend vocabulary that includes inflection.** On Casio Pro Trek barometers ([Casio 5601](https://support.casio.com/global/en/wat/manual/5601_en/VPCVSYjtsljskq.html), [Casio 5470](https://support.casio.com/global/en/wat/manual/5470_en/YLGNSYieuxevdo.html)):
  - A graph shows readings at 2-hour intervals over 20–72 hours, with the latest reading as a square at the right.
  - Four arrows mean: sudden drop, sudden rise, rise turning to fall, and fall turning to rise.
  - Some models add a differential pointer that shows the change since the last plotted reading.
  - → Slope is not the only trend information worth showing.
- **A printed scale can do the arithmetic.** Pulsometer, asthmometer and tachymeter scales are non-linear scales that convert a timed count straight into a rate (BPM = 60·N/t) ([Monochrome](https://monochrome-watches.com/understanding-bezels-and-all-the-different-scales/), [Doplr](https://monochrome-watches.com/doplr-pulse-watch-unique-timepiece-created-specifically-doctors-doctors/)).
- **One pointer can read several scales in turn.** On the Urwerk UR-100, the minute pointer reads the minutes during its hour, then passes under scales for distance travelled through Earth's rotation and orbit ([SJX](https://watchesbysjx.com/2019/09/urwerk-ur100-spacetime.html)).

### 1.8 Where Garmin's platform collides with horology
- **Always-on AMOLED limits:**
  - The face updates once a minute, and each update may light at most 10% of the pixels ([Garmin forum](https://forums.garmin.com/developer/connect-iq/b/news-announcements/posts/changes-to-watch-face-low--and-high-power-modes)).
  - Some devices add burn-in limits: no pixel may stay lit for more than about 3–4 minutes. Faces cannot show seconds continuously ([Garmin forum thread](https://forums.garmin.com/developer/connect-iq/f/discussion/385685/how-does-garmin-prevent-burn-ins-with-their-native-watchfaces)).
  - After a wrist gesture, high-power mode lasts about 10 seconds.
- → Every seconds encoding below works only during the wake window. In always-on mode, ISO's running indicator cannot be a seconds element.

---

## 2. Catalogue per datum

### 2.1 Hours and minutes
| Encoding | Example (source) | How read | Precision | Glance | Pitfalls | Disc. |
|---|---|---|---|---|---|---|
| Two concentric hands | universal; [MIL-PRF](https://hagensieker.com/wp-content/uploads/2019/10/MIL-PRF-46374G.pdf) | angles | ~1 min with a track | L | hands occlude data; errors cluster at 30°/60° | P |
| Single hand, 5-min strokes | [MeisterSinger](https://meistersinger.com/en/the-brand/single-hand-watches/) | one angle on a fine track | 2–5 min | L | needs a long hand and a fine track | P |
| 24-h single hand, noon at top | [MeisterSinger 24H](https://meistersinger.com/en/product/edition-24h/); [Solar Dial](https://support.apple.com/guide/watch/faces-and-features-apde9218b440/watchos) | position of the "sun" | ~15 min | L–M | half the angular resolution; unfamiliar | P/C |
| Regulator: minutes central, hours on a subdial | [Chronoswiss](https://monochrome-watches.com/the-evolution-of-the-regulator-watch-by-chronoswiss/) | big minute hand + small hour | 1 min | M, then L | the hour is hard to find | P |
| Minutes outer, hours inner | [B-Uhr B](https://quillandpad.com/2020/07/11/a-crash-course-in-b-uhren-and-flieger-pilots-watches-covering-both-historic-and-modern-examples-a-pilots-watch-photofest/) | two rings | 1 min | M | hour demoted | P |
| Jumping hour + retrograde minute arc | Chanel Monsieur ([Chrono24](https://www.chrono24.com/magazine/retrograde-watches-p_11853/)); VCA Pont des Amoureux, where two figures walk an arc and meet at 12 ([VCA](https://www.vancleefarpels.com/en/the-maison/articles/the-story-of-Poetic-Complication.html)) | digit + arc position | 1 min | L | the snap-back draws the eye | P |
| Wandering hour: the numeral travels a minute arc | [AP Starwheel](https://www.acollectedman.com/blogs/journal/story-of-the-audemars-piguet-star-wheel), Urwerk | which numeral, and where on the arc | 1 min | M | uses only part of the dial | C |
| Mechanical digits (discs) | Lange Zeitwerk, IWC Pallweber ([1916](https://www.the1916company.com/blog/your-ultimate-guide-to-unusual-unconventional-and-downright-surreal-ways-to-display-the-time.html)), [Semper clock](https://quillandpad.com/2021/04/11/dresden-semper-opera-five-minute-clock-the-inspiration-for-a-lange-sohnes-large-date-celebrates-180th-anniversary/) | read | 1 or 5 min | L | big area | P |
| Segment / dot-matrix digits | Pulsar, 27 dots per digit ([Penn State](https://pabook.libraries.psu.edu/literary-cultural-heritage-map-pa/feature-articles/pulsar-revolution-display-technology)); 34-segment [Segment34](https://github.com/ludw/Segment34mkII) | read | 1 min | L | AMOLED pixel budget | P |
| Words + corner dots | [QlockTwo](https://newatlas.com/qlocktwo-typographic-clock/12620/), [Fuzzy Text](https://github.com/hallettj/Fuzzy-Text-International) | read the phrase, count the dots | 5 min (1 min with dots) | L–M | bound to one language; public | P |
| LED rings / column sums | Tokyoflash [RPM](https://www.slashgear.com/tokyoflash-kisai-rpm-watch-review-a-minimalist-led-watch-26370567/) (hours inner, 5-min steps outer, plus dots), [Keisan](https://www.slashgear.com/tokyoflash-kisai-keisan-numerically-challenged-should-not-apply-2031109/) (add the columns) | count / add | 1 min | M–H | puzzle fatigue | C |
| 12 hour dots + growing minute bar | [Nooka Zot](https://www.wristwatchreview.com/review-nooka-zot-2/) | count, length | ~1 min | M → L with practice | counting | C |
| Minute dots that group into squares | [TTMM 9TTMM](https://ttmm.is/portfolio/tic-9ttmm/): every ten minutes the dots form a full square, and three squares make a rectangle | count the groups | 1 min | M | counting | C |
| Discs with no hands | [Ressence ROCS](https://monochrome-watches.com/review-ressence-type-3-oil-filled-watch-goes-beyond-hands/); [Ziiiro](https://www.ziiiro.com/howto_gravity/) gradient swirls; Mr Jones Last Laugh, time read on a skull's teeth ([archive](https://eu.mrjoneswatches.com/pages/design-archive)) | marker on a moving disc | 1 min (Ressence); "about quarter to" (Ziiiro, [review](https://www.wristwatchreview.com/review-ziiiro-gravity/)) | L–M | approximate; legibility claims mostly from brands and retailers [3P] | C |
| Liquid meniscus in a tube | [HYT](https://monochrome-watches.com/hyt-h2o-review-price/) | where the coloured liquid ends | ~5–10 min | M | slow retrograde refill | C |
| Linear / roller | Urwerk UR-111C; Patek Cobra prototype ([1916](https://www.the1916company.com/blog/your-ultimate-guide-to-unusual-unconventional-and-downright-surreal-ways-to-display-the-time.html)) | position on a line | ~1 min | M | wastes a round screen | C |
| Nested octagons | Hublot Sang Bleu ([1916](https://www.the1916company.com/blog/your-ultimate-guide-to-unusual-unconventional-and-downright-surreal-ways-to-display-the-time.html)) | three nested polygons | ~1 min | M–H ("takes some effort") | | C |
| Clock of clocks | [ClockClock 24](https://www.humanssince1982.com/en-us/blogs/concepts/the-clock-clock-2008-2010) | digits formed once a minute | 1 min | L when formed | unreadable mid-transition; studio claims copyright on the grid idea | P |
| Position only, one dot | [Museum dial](https://en.wikipedia.org/wiki/Nathan_George_Horwitt) | angle against the single landmark | ~5 min | L | relies on its anchor | P |
| Morphing / highlighted numerals | Metropolitan, Contour, Typograph ([Apple](https://support.apple.com/guide/watch/faces-and-features-apde9218b440/watchos), [arun.is](https://arun.is/blog/apple-metropolitan-face/)) | emphasised numeral | as hands | L | animation and power cost | P |
| Hands that carry a message | Mr Jones The Accurate, whose hands read "remember" / "you will die" ([archive](https://eu.mrjoneswatches.com/pages/design-archive)) | the hands say a sentence | as hands | L | the message dominates | P |
| Hidden on demand | [Le Temps Suspendu](https://revolutionwatch.com/a-closer-look-hermes-arceau-le-temps-suspendu/), [L'heure masquée](https://watchesbysjx.com/2014/03/baselworld-2014-introducing-the-hermes-dressage-lheure-masquee-a-dual-time-zone-with-a-secret-hour-hand-with-specs-and-price.html) | press to reveal | – | H | no passive glance | H |

### 2.2 Seconds
| Encoding | Example (source) | How read | Precision | Glance | Pitfalls | Disc. |
|---|---|---|---|---|---|---|
| Continuous sweep | [Spring Drive glide](https://www.grand-seiko.com/us-en/collections/movement/springdrive) | angle; motion means "alive" | below 1 s | L | constant motion pulls peripheral attention; not possible in AMOLED always-on | P |
| Deadbeat 1 Hz tick | [seconde morte](https://watch-movements.eu/blog/en/2022/06/04/a-dead-beat-seconds-movement-140-years-old/) | discrete jumps | 1 s | L | reads as "quartz" to collectors [3P] | P |
| Stop-to-go (58.5 s lap + pause) | [Hilfiker](https://en.wikipedia.org/wiki/Swiss_railway_clock) | a lap, then a wait at 12 | ~1 s, not exact | L | the pause means "synchronised", not seconds | P |
| Small-seconds subdial | railroad dial at 6 ([Monochrome](https://monochrome-watches.com/dial-styles-history-and-evolution-of-a-watchs-crucial-part-abcs-of-time/)) | small hand | 1 s | M | tiny | P |
| Retrograde seconds arc | Moser Pioneer, 30-s arc ([1916](https://www.the1916company.com/blog/your-ultimate-guide-to-unusual-unconventional-and-downright-surreal-ways-to-display-the-time.html)) | position on the arc | 1 s | L | the snap-back is an attention event | P |
| Jumping seconds digits | F.P. Journe Vagabondage III ([1916](https://www.the1916company.com/blog/your-ultimate-guide-to-unusual-unconventional-and-downright-surreal-ways-to-display-the-time.html)) | read digits | 1 s | L | constant digit change | P |
| Rotating disc under a fixed reference | [Ressence](https://monochrome-watches.com/review-ressence-type-3-oil-filled-watch-goes-beyond-hands/) sub-discs | angle of a mark on the disc | ~1 s | M | the whole surface moves | C |
| Perimeter count-up | [Modular Ultra](https://support.apple.com/guide/watch/faces-and-features-apde9218b440/watchos): "seconds counting up to a minute on the outer edges" | how much of the edge is marked | ~1 s | L | ring drawn on round screen = many pixels | P |
| Colour fills the face from the bottom | [Flux](https://support.apple.com/guide/watch/faces-and-features-apde9218b440/watchos) | fill height | ~5 s | L (approximate) | large lit area | C |
| Gradient follows the seconds hand | [Palette](https://support.apple.com/guide/watch/faces-and-features-apde9218b440/watchos) | where the gradient sits | ~5 s | L | low precision | C |
| Character animation | Mickey's feet "tap out each second" ([Apple](https://support.apple.com/guide/watch/faces-and-features-apde9218b440/watchos)) | rhythm | 1 s | L for liveness | whimsy | P |
| A line drawn every 5 s | [TTMM EXDIGITTMM](https://ttmm.is/portfolio/tic-exdigittmm/) | count the lines | 5 s | M | | C |
| Luminous tip as running indicator | [ISO 6425](https://cdn.standards.iteh.ai/samples/12774/166555c91df4457294ef397a7a10d6a4/ISO-6425-1996.pdf) | motion seen in the dark | shows liveness only | L | | P |
| Change of rhythm as a warning | 2-s steps on [Citizen](https://www.citizenwatch-global.com/support/faq/gfaq/malfunction) / [Seiko](https://www.seikowatches.com/us-en/-/media/Files/Common/Seiko/instructions/English/3/3M22/3M22.pdf) | cadence | threshold only | L if you know the code | invisible to others | C |
| Press for seconds | [Pulsar](https://pabook.libraries.psu.edu/literary-cultural-heritage-map-pa/feature-articles/pulsar-revolution-display-technology) | hold the button longer | 1 s | H | | H |

### 2.3 Day of week
| Encoding | Example (source) | How read | Precision | Glance | Pitfalls | Disc. |
|---|---|---|---|---|---|---|
| Window, abbreviation or full word | Rolex Day-Date, Seiko; full words "look nicer", abbreviations are the author's least favourite ([Fratello](https://www.fratellowatches.com/different-date-displays-watches/)) | read | exact | L | language; space | P |
| One glyph per day | Seiko kanji wheel: 月 moon (Mon), 火 fire, 水 water, 木 wood, 金 metal, 土 earth, 日 sun ([Blacksmithmods](https://www.blacksmithmods.fi/those-tiny-symbols-on-your-seiko-a-quick-look-at-weekday-kanji/)) | recognise the glyph | exact | L once learned | | C for non-readers |
| Planet-god figures | Strasbourg clock: chariots from Apollo (Sun) to Saturn ([Wikipedia](https://en.wikipedia.org/wiki/Strasbourg_Cathedral)) | which figure is in front | exact | M | needs the myth key | C |
| Retrograde day hand | snail cam that snaps back to Monday ([watch-movements.eu](https://watch-movements.eu/blog/en/2023/06/03/retrograde-time-displays/)) | position on a 7-step arc | exact | L | | P |
| Subdial pointer | triple calendars ([Fratello](https://www.fratellowatches.com/different-date-displays-watches/)) | small hand | exact | M | "stop and look for longer" | P |
| Colour of one day | Snoopy's "Sunday Surprise" cycles background colours on Sundays ([Apple](https://support.apple.com/guide/watch/faces-and-features-apde9218b440/watchos)) | colour | weekend or not | L | | C |

### 2.4 Day of month
| Encoding | Example (source) | How read | Precision | Glance | Pitfalls | Disc. |
|---|---|---|---|---|---|---|
| Window at 3, 6 or 4:30 | [Fratello](https://www.fratellowatches.com/different-date-displays-watches/) | read | exact | L | small; the cyclops is "a Band-Aid"; the window cuts an index | P |
| Outsize date (two discs) | Lange 1, "five times larger" ([Quill & Pad](https://quillandpad.com/2021/04/11/dresden-semper-opera-five-minute-clock-the-inspiration-for-a-lange-sohnes-large-date-celebrates-180th-anniversary/) [snippet]) | read | exact | L | space | P |
| Central pointer date with a distinct tip | Oris ([WatchTime](https://www.watchtime.com/brands/automatic-watches/watch-date-displays-explained)) | hand on a 1–31 track | exact | M | a 4th central hand | P |
| Coloured apertures frame the numeral | Nomos double date ([WatchTime](https://www.watchtime.com/brands/automatic-watches/watch-date-displays-explained)) | marked numeral on a ring | exact | L | | P |
| Retrograde date arc | Hermès Le Temps Suspendu, arc at 5 ([Revolution](https://revolutionwatch.com/a-closer-look-hermes-arceau-le-temps-suspendu/)) | arc position | exact | L | | P |
| Dot spiral, 30+1 holes | [Ochs und Junior](https://www.ochsundjunior.swiss/watches/moon-phase/): the 10-minute indices double as 5-day landmarks; it is a spiral because 31 and 1 share an angle | position of the coloured dot | exact | L after learning | needs the key | C |
| Day-of-year ring | Prague orloj: 365 lines and an arrow ([Wikipedia](https://en.wikipedia.org/wiki/Prague_astronomical_clock)) | position | ~1 day | M | | C |

### 2.5 Month
| Encoding | Example (source) | How read | Precision | Glance | Pitfalls | Disc. |
|---|---|---|---|---|---|---|
| Window or subdial | perpetual and triple calendars ([Fratello](https://www.fratellowatches.com/different-date-displays-watches/)) | read | exact | M | clutter | P |
| Disc hole points to the hour that equals the month | [Ochs und Junior perpetual](https://www.ochsundjunior.swiss/watches/perpetual-calendar/): the outer hole points to 1 for January; a coloured dot among four holes marks the leap-year cycle ([Revolution](https://revolutionwatch.com/perpetual-calendar/)) | angle | exact | M | key | C |
| Pictures (zodiac, seasonal labour) | Prague calendar dial: 12 medallions with rural scenes ([Wikipedia](https://en.wikipedia.org/wiki/Prague_astronomical_clock)) | recognise the picture | exact | M | | C |
| Hour dots reused as months | Nooka date mode ([Wristwatch Review](https://www.wristwatchreview.com/review-nooka-zot-2/)) | count | exact | M | | C |
| Day length as season | Solar Dial: the day length visibly changes with the seasons ([note.com](https://note.com/gajyumaru_san/n/n65d3e88335e1?hl=en) [3P]) | arc length | weeks | L | depends on location | C |
| Sun/moon/earth geometry | Ochs Luna Sole ([Monochrome](https://monochrome-watches.com/introducing-ochs-und-junior-luna-sole-moon-phase-ludwig-oechslin-astronomical-watch-specs-price/) [snippet]) | geometry | days | M | | C |

### 2.6 Battery / time remaining
| Encoding | Example (source) | How read | Precision | Glance | Pitfalls | Disc. |
|---|---|---|---|---|---|---|
| Arc between "AUF" (full) and "AB" (empty) | Lange patent 1879, Lange 1 ([swisswatches](https://swisswatches-magazine.com/a-lange-sohne-1815-auf-ab-375-mm/), [Wikipedia](https://en.wikipedia.org/wiki/Power_reserve_indicator)) | pointer in a bounded arc | continuous | L | words are public | P |
| Subdial gauge | Lange 1815 Up/Down at 8, Datograph at 6 | small hand | coarse | M | | P |
| Days-left number | Segment34's "battery days remaining" field ([Garmin store](https://apps.garmin.com/apps/0e4b2466-1b18-4aff-8b48-c4452e4edc20)) | read | 1 day | L | | P |
| Rhythm change at low charge | Citizen / Seiko 2-s steps | cadence | threshold | L if known | | C |
| Jump-size classes on demand | Seiko Kinetic: the hand jumps 5, 10, 20 or 30 s, each a reserve band ([Seiko](https://www.seikowatches.com/us-en/-/media/Files/Common/Seiko/instructions/English/3/3M22/3M22.pdf)) | press, see the jump size | 4 non-linear classes | H | | H |
| End-of-life indication legible in the dark | [ISO 6425](https://cdn.standards.iteh.ai/samples/12774/166555c91df4457294ef397a7a10d6a4/ISO-6425-1996.pdf) | binary | binary | L | | P |
| Fan countdown in the last hour | [L'heure impatiente](https://monochrome-watches.com/hands-hermes-lheure-impatiente-poetical-alarm-complication/) | the hand descends to 0 | 1 min | L | appears only near the end | P |
| Countdown / elapsed bezel | [Monochrome](https://monochrome-watches.com/understanding-bezels-and-all-the-different-scales/) | align, then read | 1 min | M | set by hand | P |

### 2.7 A quantity with direction and a good zone (the class glucose belongs to)
| Encoding | Example (source) | How read | Precision | Glance | Pitfalls | Disc. |
|---|---|---|---|---|---|---|
| Zones printed on the scale, neutral pointer | [FAA airspeed arcs](https://en.wikipedia.org/wiki/Airspeed_indicator) | which band the pointer is in | continuous | L for the zone | colour-only zones fail colour-blind eyes; zone limits must be personal | P |
| Fine scale and accent band for the critical range | [X Fathoms](https://monochrome-watches.com/blancpain-x-fathoms/) 0–15 m hand, 3–6 m in orange | second pointer | high where it matters | L–M | two pointers to tell apart | P/C |
| Memory of the extreme | X Fathoms max-depth hand | a marker stays until reset | – | L | needs a reset rule | C |
| History graph + newest-reading square | [Casio](https://support.casio.com/global/en/wat/manual/5470_en/YLGNSYieuxevdo.html) | slope | coarse | M | small; needs axes | P |
| Four trend arrows, including turning points | [Casio](https://support.casio.com/global/en/wat/manual/5601_en/VPCVSYjtsljskq.html) | arrow shape | 4 classes | L | flashing draws attention | P |
| Differential pointer (change since the last reading) | Casio PRG-600 ([manual](https://www.manualslib.com/manual/3098196/Casio-Pro-Trek-Prg-600.html) [snippet]) | signed offset | coarse | M | | C |
| Non-linear scale that does the arithmetic | [pulsometer / asthmometer](https://monochrome-watches.com/doplr-pulse-watch-unique-timepiece-created-specifically-doctors-doctors/) | read at the hand | coarse | M | | C |
| Same pointer, extra scales | [Urwerk UR-100](https://watchesbysjx.com/2019/09/urwerk-ur100-spacetime.html) | time-sliced | – | M | | C |
| Motion intensity | Weiser's Dangling String: "a madly whirling string" for a busy network, "a small twitch every few seconds" for a quiet one ([Weiser & Brown](https://calmtech.com/papers/designing-calm-technology)) | peripheral motion | coarse | L, peripheral | motion is pre-attentive, so not discreet | C |
| Liquid level | [HYT](https://monochrome-watches.com/hyt-h2o-review-price/) | level | coarse | L | | C |
| Shape or colour morph | Unity: "the shapes change as you move" ([Apple](https://support.apple.com/guide/watch/faces-and-features-apde9218b440/watchos)) | gestalt | coarse | L | ambiguous | C |

---

## 3. Invented encodings [INVENTED]
All of these are reasoned from the sources above. None is tested.

| # | Encoding [INVENTED] | Reasoned from | How read | Precision | Glance | Pitfalls | Disc. |
|---|---|---|---|---|---|---|---|
| I1 | **A gap travels the minute track**: the seconds are one missing tick | perimeter dot; pre-attentive asymmetry | where the gap is | 1 s | M | an absence pops out less than a presence (Healey); many lit pixels | C |
| I2 | **Stop-to-go synced to the sensor**: the seconds element finishes its lap and waits at 12 until the next reading arrives | Hilfiker | the wait means "fresh value due"; a long wait means stale | – | L | drifts from true seconds; the wait must be bounded | C |
| I3 | **The running indicator becomes a freshness indicator**: the element that proves "alive" (ISO 6425) stops or dims when the reading is old | ISO 6425 | motion or brightness | binary or graded | L | in always-on, motion is per minute only | C |
| I4 | **Weekday as the accented index**: indices 1–7 mean Mon–Sun | Ochs month disc | which index is marked | exact | L after learning | clashes with the hour reading | C |
| I5 | **Date as one dot on the minute track** (day d at tick 2d) | Ochs dot spiral | position | exact | L after learning | 31 and 1 collide unless spiralled | C |
| I6 | **Month as a ring around hour index n** | Ochs perpetual | which index is ringed | exact | M | | C |
| I7 | **Battery as an eroding chapter ring**: the drawn minute track shortens with the days left | Auf/Ab; Segment34 field | track length | days | L | reads as decoration, or as a bug | C |
| I8 | **Battery as the seconds element's cadence**: 1-s steps when healthy, 2-s steps when low | Citizen / Seiko | rhythm | threshold | L if known | wake window only | C |
| I9 | **Pseudo-GMT glucose hand**: a 4th hand pointing at mmol/L on the existing 1–12 numerals, with minute ticks as 0.2 steps; mg/dL needs a scale key | GMT hand, pointer date, MIL dual ring | angle | ~0.2 mmol/L | L for the wearer | values above 12 wrap; the wearer may confuse it with the time (safety); onlookers read "a GMT" | C |
| I10 | **Good zone as a sector-dial band**: the target range printed as a 1930s-style sector ring; the pointer is in or out of it | FAA arcs, sector dial | inside or outside | zone | L | zone limits must follow the user's settings | C |
| I11 | **Trend as the tilt of the 12 o'clock anchor** (a B-Uhr triangle rotated 0/±45/±90°) | B-Uhr anchor; orientation is pre-attentive | tilt | 5–7 classes | L | the anchor stops anchoring; wrist rotation adds ambiguity | C |
| I12 | **Trend as the shape of the hand tip** (arrowhead, flat, chevron) | pointer-date tips | shape | 3–5 classes | M | small at 1.2" | C |
| I13 | **Level and slope as a liquid line**: height is the value, tilt is the trend | HYT, Flux | level + tilt | coarse | L | large lit area (10% pixel cap) | C |
| I14 | **12-hour history in the index lengths**: each hour index's length is that hour's mean | radial sparkline | profile | coarse | M | radial comparisons are slow (Blascheck); clashes with time reading | C |
| I15 | **Range as daylight**: the glucose "sun" is in its "day" arc when in range | Solar Dial | sun position | zone | L | collides with real day and night | C |
| I16 | **Overnight extreme memory**: a tiny marker stays at the night's lowest value until morning | X Fathoms max depth | marker | value | L | reset semantics | C |
| I17 | **Magnified low band**: a finer scale only between the low thresholds | X Fathoms 0–15 m | second pointer | high where it matters | M | two pointers | C |
| I18 | **Inflection arrows** (rising-but-slowing, falling-but-turning) | Casio four arrows | arrow shape | classes | L | needs a defined derivative | P/C |
| I19 | **Fuzzy glucose words** ("about 6, steady") | Fuzzy Time, MeisterSinger | read | coarse | L | words are public | P |
| I20 | **Masked value**: the number sits under another element and shows on wrist-raise or tap | L'heure masquée; Garmin ~10 s high power | reveal | exact | H | loses the passive glance; it hides exactly when it matters unless an alert overrides it | H |

---

## 4. Discretion: how a watch can be readable only by its wearer
1. **Unlabelled positions read with a private key.** Examples: Ochs dots, Ziiiro swirls, Museum dot, Nooka bar. Onlookers see ornament ([Ochs und Junior](https://www.ochsundjunior.swiss/watches/moon-phase/), [Ziiiro](https://www.ziiiro.com/howto_gravity/)).
2. **Disguise as a familiar complication.** GMT hand, power reserve, sector ring, moon phase. Hermès shows that an ordinary-looking dial can hold a hidden layer ([SJX](https://watchesbysjx.com/2014/03/baselworld-2014-introducing-the-hermes-dressage-lheure-masquee-a-dual-time-zone-with-a-secret-hour-hand-with-specs-and-price.html)). The risk is that the wearer misreads it too.
3. **Hidden until asked**: Le Temps Suspendu, Pulsar, Tokyoflash cryptic mode. This trades the passive glance away.
4. **Behaviour codes**: a rhythm change (Citizen/Seiko), a jump size (Kinetic). Only someone who knows the code notices.
5. **Glyph systems the onlooker doesn't share**: kanji weekdays, planetary gods, mixed Roman/Arabic.
6. **Coarseness**: QlockTwo and MeisterSinger reveal less detail to a glancing stranger, and also to the wearer.
7. **Anti-discretion to avoid**: anything pre-attentive (hue jumps, flicker, motion) gets noticed by others in the periphery too ([Healey](https://www.csc2.ncsu.edu/faculty/healey/PP/)). FAA-style red and green zones are a public code.

---

## 5. Contradictions and open questions
- **ISO 6425 vs Garmin AMOLED.** ISO wants a visible running indicator in the dark. Garmin's always-on mode forbids continuous seconds and caps the lit pixels at 10%. Liveness must be shown at minute level or through data freshness.
- **Digits vs position.** Digits win for the value and position wins for distance to a threshold ([Springer](https://link.springer.com/chapter/10.1007/978-3-031-35681-0_27), [MDPI](https://www.mdpi.com/2076-328X/14/9/847)). Glucose needs both, so the evidence pulls in two directions.
- **Rings are beloved but slow.** Concentric arcs are slow for comparison ([Blascheck](https://www.microsoft.com/en-us/research/wp-content/uploads/2018/08/GlanceableVis-InfoVis2018.pdf)). There is no data on reading a single value or zone from one perimeter arc.
- **Discretion vs safety.** Coded or disguised encodings lengthen learning, and they risk the wearer misreading glucose as time (I9). Reviewers dislike decoding effort ([Gear Diary](https://geardiary.com/2014/05/07/tokyoflash-kisai-optical-illusion-watch-secret/)).
- **Weak evidence.** The 10:10 effect rests on small samples. The legibility claims for Ressence and Ochs come from the brands and their owners. Hand-length ratios are forum lore. No source here measures legibility of the alternative displays (word, disc, liquid) against hands.
- **Disputed facts:**
  - The year of Hilfiker's red hand is given as 1947, 1953 or 1955 (Wikipedia: 1953).
  - ISO 6425's glow duration is given as 60 or 180 minutes.
  - Lubs's "10:08" preference was seen only in a snippet.
- **Gaps:**
  - Hodinkee could not be fetched (its domain blocks this tool).
  - No source covered Facer or WatchMaker creative faces, or Garmin's own built-in faces.
  - No study was found on reading a trend arrow at a glance, or on how a round dial's rotation on the wrist affects reading it.

---

## 6. Sources read
Read in full or in large part (fetched): [The Horologue: the dial](https://thehorologue.com/article/the-dial) · [Monochrome: dial styles ABCs](https://monochrome-watches.com/dial-styles-history-and-evolution-of-a-watchs-crucial-part-abcs-of-time/) · [Monochrome: bezels and scales](https://monochrome-watches.com/understanding-bezels-and-all-the-different-scales/) · [1916 Company: unusual time displays](https://www.the1916company.com/blog/your-ultimate-guide-to-unusual-unconventional-and-downright-surreal-ways-to-display-the-time.html) · [Fratello: date displays](https://www.fratellowatches.com/different-date-displays-watches/) · [Dezeen: Dietrich Lubs interview](https://www.dezeen.com/2017/09/18/braun-philosophy-about-reducing-bling-bling-watch-clock-designer-dietrich-lubs-interview/) · [Apple: watch faces and features](https://support.apple.com/guide/watch/faces-and-features-apde9218b440/watchos) · [Wikipedia: Swiss railway clock](https://en.wikipedia.org/wiki/Swiss_railway_clock) · [Garmin: watch face low and high power modes](https://forums.garmin.com/developer/connect-iq/b/news-announcements/posts/changes-to-watch-face-low--and-high-power-modes) · [Blascheck et al. 2018: Glanceable Visualization](https://www.microsoft.com/en-us/research/wp-content/uploads/2018/08/GlanceableVis-InfoVis2018.pdf)

Seen through search-result extracts (primary pages where possible):
- **Standards, research and instruments:** [ISO 6425:1996 sample](https://cdn.standards.iteh.ai/samples/12774/166555c91df4457294ef397a7a10d6a4/ISO-6425-1996.pdf) · [divewatch.com: ISO 6425](https://divewatch.com/reference/iso-6425/) · [HELM: ISO 6425](https://helmwatches.com/iso-6425.html) · [MIL-PRF-46374G](https://hagensieker.com/wp-content/uploads/2019/10/MIL-PRF-46374G.pdf) · [Karim et al. 2017: 10 past 10](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC5572348/) · [RealClearScience on 10:10](https://www.realclearscience.com/articles/2022/05/09/why_are_watches_usually_set_to_1010_in_advertisements_830409.html) · [Digital versus conventional clocks, a review (1971)](https://pubmed.ncbi.nlm.nih.gov/15676699/) · [On Time Reading Performance (2023)](https://link.springer.com/chapter/10.1007/978-3-031-35681-0_27) · [Time display format and cognitive performance (MDPI 2024)](https://www.mdpi.com/2076-328X/14/9/847) · [Visuri et al. CHI 2017](https://www.kostakos.org/papers/chi17.pdf) · [Healey: Perception in Visualization](https://www.csc2.ncsu.edu/faculty/healey/PP/) · [Weiser & Brown: Designing Calm Technology](https://calmtech.com/papers/designing-calm-technology) · [Wikipedia: Airspeed indicator](https://en.wikipedia.org/wiki/Airspeed_indicator) · [Casio module 5601: pressure change indicators](https://support.casio.com/global/en/wat/manual/5601_en/VPCVSYjtsljskq.html) · [Casio module 5470: pressure over time](https://support.casio.com/global/en/wat/manual/5470_en/YLGNSYieuxevdo.html) · [Casio PRG-600 manual](https://www.manualslib.com/manual/3098196/Casio-Pro-Trek-Prg-600.html) · [Garmin forum: burn-in on native faces](https://forums.garmin.com/developer/connect-iq/f/discussion/385685/how-does-garmin-prevent-burn-ins-with-their-native-watchfaces)
- **Watchmaking history and design:** [Galaxus: why the railway clock pauses](https://www.galaxus.at/en/page/come-to-think-of-it-why-does-the-second-hand-of-the-swiss-railway-clock-pause-at-12-21628) · [Quill & Pad: B-Uhren](https://quillandpad.com/2020/07/11/a-crash-course-in-b-uhren-and-flieger-pilots-watches-covering-both-historic-and-modern-examples-a-pilots-watch-photofest/) · [Fratello: Junghans Max Bill](https://www.fratellowatches.com/tbt-iconic-bauhaus-watch-junghans-max-bill/) · [Cooper Hewitt: Ahead of the Times](https://www.cooperhewitt.org/2018/02/24/ahead-of-the-times/) · [Grand Seiko style](https://www.grand-seiko.com/us-en/worldofgrandseiko/design/grandseikostyle) · [Worn & Wound: Grammar of Design](https://wornandwound.com/art-time-taro-tanaka-seikos-grammar-design/) · [Grand Seiko Spring Drive](https://www.grand-seiko.com/us-en/collections/movement/springdrive) · [Watch Repair Talk: hand ratios](https://www.watchrepairtalk.com/topic/26439-dial-hand-length-ratio-rule-of-thumb/) · [Gear Patrol: Time & Type](https://www.gearpatrol.com/watches/a519830/time-and-type/) · [Aquastar: lume colours](https://aquastar.ch/journal/superluminova-lume-explained/) · [WatchTime: lume history](https://www.watchtime.com/brands/sports-watch/history-of-watch-lume-radium-tritium-super-luminova) · [watch-movements.eu: dead-beat seconds](https://watch-movements.eu/blog/en/2022/06/04/a-dead-beat-seconds-movement-140-years-old/) · [watch-movements.eu: retrograde displays](https://watch-movements.eu/blog/en/2023/06/03/retrograde-time-displays/) · [Chrono24: retrograde watches](https://www.chrono24.com/magazine/retrograde-watches-p_11853/) · [Wikipedia: Nathan George Horwitt](https://en.wikipedia.org/wiki/Nathan_George_Horwitt) · [Ethos: regulator](https://www.ethoswatches.com/the-watch-guide/regulator-display-time-watches/) · [Monochrome: Chronoswiss regulator](https://monochrome-watches.com/the-evolution-of-the-regulator-watch-by-chronoswiss/) · [Quill & Pad: Semper Opera five-minute clock](https://quillandpad.com/2021/04/11/dresden-semper-opera-five-minute-clock-the-inspiration-for-a-lange-sohnes-large-date-celebrates-180th-anniversary/) · [swisswatches: Lange 1815 Auf/Ab](https://swisswatches-magazine.com/a-lange-sohne-1815-auf-ab-375-mm/) · [Wikipedia: Power reserve indicator](https://en.wikipedia.org/wiki/Power_reserve_indicator) · [WatchTime: date displays](https://www.watchtime.com/brands/automatic-watches/watch-date-displays-explained) · [Wikipedia: Strasbourg Cathedral](https://en.wikipedia.org/wiki/Strasbourg_Cathedral) · [Wikipedia: Prague astronomical clock](https://en.wikipedia.org/wiki/Prague_astronomical_clock) · [Blacksmithmods: Seiko weekday kanji](https://www.blacksmithmods.fi/those-tiny-symbols-on-your-seiko-a-quick-look-at-weekday-kanji/) · [Citizen FAQ](https://www.citizenwatch-global.com/support/faq/gfaq/malfunction) · [Seiko Kinetic 3M22 manual](https://www.seikowatches.com/us-en/-/media/Files/Common/Seiko/instructions/English/3/3M22/3M22.pdf)
- **Complications and independent makers:** [MeisterSinger: single-hand watches](https://meistersinger.com/en/the-brand/single-hand-watches/) · [MeisterSinger Edition 24H](https://meistersinger.com/en/product/edition-24h/) · [Monochrome: MeisterSinger philosophy](https://monochrome-watches.com/the-intriguing-philosophy-of-time-that-carved-meistersinger-out-its-niche/) · [A Collected Man: AP Star Wheel](https://www.acollectedman.com/blogs/journal/story-of-the-audemars-piguet-star-wheel) · [Monochrome: Doplr pulse watch](https://monochrome-watches.com/doplr-pulse-watch-unique-timepiece-created-specifically-doctors-doctors/) · [Ochs und Junior: moon phase](https://www.ochsundjunior.swiss/watches/moon-phase/) · [Ochs und Junior: perpetual calendar](https://www.ochsundjunior.swiss/watches/perpetual-calendar/) · [Revolution: perpetual calendar basics](https://revolutionwatch.com/perpetual-calendar/) · [Fifth Wrist: Ochs und Junior review](https://www.fifthwrist.com/ochs-und-junior-moonphase-review/) · [Monochrome: Ochs und Junior Luna Sole](https://monochrome-watches.com/introducing-ochs-und-junior-luna-sole-moon-phase-ludwig-oechslin-astronomical-watch-specs-price/) · [Monochrome: Ressence Type 3](https://monochrome-watches.com/review-ressence-type-3-oil-filled-watch-goes-beyond-hands/) · [Monochrome: HYT H2O](https://monochrome-watches.com/hyt-h2o-review-price/) · [Revolution: Le Temps Suspendu](https://revolutionwatch.com/a-closer-look-hermes-arceau-le-temps-suspendu/) · [SJX: L'heure masquée](https://watchesbysjx.com/2014/03/baselworld-2014-introducing-the-hermes-dressage-lheure-masquee-a-dual-time-zone-with-a-secret-hour-hand-with-specs-and-price.html) · [Monochrome: L'heure impatiente](https://monochrome-watches.com/hands-hermes-lheure-impatiente-poetical-alarm-complication/) · [Van Cleef & Arpels: Poetic Complications](https://www.vancleefarpels.com/en/the-maison/articles/the-story-of-Poetic-Complication.html) · [De Bethune DB28 Digitale](https://www.debethune.ch/collections/db28/digitale-db28dns/) · [SJX: Urwerk UR-100](https://watchesbysjx.com/2019/09/urwerk-ur100-spacetime.html) · [Monochrome: X Fathoms](https://monochrome-watches.com/blancpain-x-fathoms/) · [aBlogtoWatch: X Fathoms](https://www.ablogtowatch.com/blancpain-x-fathoms-watch-hands-on/)
- **Conceptual and digital:** [New Atlas: QlockTwo](https://newatlas.com/qlocktwo-typographic-clock/12620/) · [Mr Jones: design archive](https://eu.mrjoneswatches.com/pages/design-archive) · [Ziiiro: how to read Gravity](https://www.ziiiro.com/howto_gravity/) · [Wristwatch Review: Ziiiro Gravity](https://www.wristwatchreview.com/review-ziiiro-gravity/) · [SlashGear: Kisai RPM](https://www.slashgear.com/tokyoflash-kisai-rpm-watch-review-a-minimalist-led-watch-26370567/) · [SlashGear: Kisai Keisan](https://www.slashgear.com/tokyoflash-kisai-keisan-numerically-challenged-should-not-apply-2031109/) · [Gear Diary: Optical Illusion](https://geardiary.com/2014/05/07/tokyoflash-kisai-optical-illusion-watch-secret/) · [Digital Trends: Kisai Online](https://www.digitaltrends.com/cool-tech/hands-on-with-tokyoflash-kisai-online/) · [Penn State: Pulsar](https://pabook.libraries.psu.edu/literary-cultural-heritage-map-pa/feature-articles/pulsar-revolution-display-technology) · [Humans since 1982: ClockClock](https://www.humanssince1982.com/en-us/blogs/concepts/the-clock-clock-2008-2010) · [Wristwatch Review: Nooka Zot](https://www.wristwatchreview.com/review-nooka-zot-2/) · [9to5Mac: Alan Dye on Apple Watch UI](https://9to5mac.com/2020/10/21/apple-watch-ui/) · [arun.is: Metropolitan](https://arun.is/blog/apple-metropolitan-face/) · [note.com: Solar Dial](https://note.com/gajyumaru_san/n/n65d3e88335e1?hl=en) · [TTMM: TIC-TTMMORE](https://ttmm.is/portfolio/tic-ttmmore/) · [TTMM: TIC-EXDIGITTMM](https://ttmm.is/portfolio/tic-exdigittmm/) · [TTMM: TIC-9TTMM](https://ttmm.is/portfolio/tic-9ttmm/) · [Rebble: Fuzzy Text Plus](https://apps.repebble.com/fuzzy-text-plus_52de476d6094ff6bf0000046) · [Fuzzy Text International](https://github.com/hallettj/Fuzzy-Text-International) · [Segment34 MkII](https://github.com/ludw/Segment34mkII) · [Garmin store: Segment 34](https://apps.garmin.com/apps/0e4b2466-1b18-4aff-8b48-c4452e4edc20)
