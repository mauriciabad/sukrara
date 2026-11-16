# E — What a person with type 1 diabetes needs from the wrist

Research input for docs/42. It collects knowledge, numbers, evidence and catalogues. It is not a design and does not recommend a style. Each bullet gives the finding, then why it matters for a face (after →), then the source.

**Method and limits.** I read about 60 sources: consensus statements and papers, official docs, app source code, GitHub issues, store pages and forum threads.
- Reddit couldn't be fetched (both the domain and `site:` searches returned nothing). User voice therefore comes from GitHub issues, Garmin forums, Connect IQ and App Store listings, and qualitative studies.
- PMC began showing a CAPTCHA and the web-search budget ran out late in the work. A few numbers were taken from secondary summaries and are marked **[secondary]**. Vendor claims are marked **[vendor]**.

---

## 1. Clinical knowledge that shapes what to show

### 1.1 Ranges (the international consensus)
- **Adult T1D/T2D targets:**
  - Time in range (TIR) 70–180 mg/dL (3.9–10.0 mmol/L) above 70%, about 16 h 48 min a day.
  - Below 70 mg/dL under 4% (under 1 h). Below 54 mg/dL under 1% (under 15 min).
  - Above 180 mg/dL under 25%. Above 250 mg/dL under 5%.
  - → These are the standard bands any range colouring or zone mapping inherits. [Battelino 2019, Table 3](https://pmc.ncbi.nlm.nih.gov/articles/PMC6973648/)
- **Named levels:**
  - Very Low (level 2 hypo): below 54 mg/dL (3.0).
  - Low (level 1): 54–69 mg/dL.
  - High (level 1 hyper): 181–250 mg/dL.
  - Very High (level 2): above 250 mg/dL (13.9).
  - → There are five clinically named states, not three. [Battelino 2019](https://pmc.ncbi.nlm.nih.gov/articles/PMC6973648/)
- **Other populations:**
  - Older or high-risk adults: TIR above 50% and time below 70 under 1%.
  - Pregnancy with T1D: a tighter 63–140 mg/dL (3.5–7.8) range.
  - → The same number can mean "fine" or "act" depending on who wears the watch, so thresholds must be user-set. [Battelino 2019](https://pmc.ncbi.nlm.nih.gov/articles/PMC6973648/)
- **Size of a meaningful change:**
  - Each 5% rise in TIR is clinically meaningful.
  - 10% of TIR corresponds to about 0.5–0.8% of HbA1c.
  - → A day-level TIR field has meaningful resolution in 5% steps. [Battelino 2019](https://pmc.ncbi.nlm.nih.gov/articles/PMC6973648/)
- **Units:** guidelines print mg/dL and mmol/L side by side, and the factor is about 18.
  - The owner's countries and their apps differ, and every source above gives both units.
  - → The unit is a per-user setting. Decimals (5.6) compete for width with integers (100).

### 1.2 Trend arrows: what they mean, and that they differ per sensor
- **Arrows look back, not ahead.**
  - They summarise the last ~15 min.
  - They imply the change over the next 15–30 min.
  - They can still point down after glucose has levelled off, so position statements say to read the arrow together with the last ~10 min of the curve.
  - → An arrow alone can mislead; a short recent history disambiguates it. [Brazilian Diabetes Society position statement 2021](https://pmc.ncbi.nlm.nih.gov/articles/PMC7780381/), [Medscape on Aleppo/Laffel 2017](https://www.medscape.com/viewarticle/888896)
- **The same glyph means different speeds on different sensors** (predicted change over 30 min):

  | Sensor | Arrows | Speed per arrow |
  |---|---|---|
  | Dexcom | 7 | ↑↑ >+90 mg/dL, ↑ +60–90, ↗ +30–60, → <±30 (≈ >3, 2–3, 1–2, <1 mg/dL/min) |
  | Libre | 5 | ↑ >+60, ↗ +30–60, → <±30 (no double arrow) |
  | Medtronic 640G | ↑…↑↑↑ | a single ↑ is only +30–60 |

  → A face that draws its own arrows decides which vocabulary it uses: 5 or 7 states, and how speed maps to glyphs. [Brazilian statement, Table 1](https://pmc.ncbi.nlm.nih.gov/articles/PMC7780381/)
  - Libre thresholds of >2, 1–2 and <1 mg/dL/min appear in a patent citing Kudva 2018 **[secondary]**. [Kudva et al. 2018, J Endocr Soc](https://pubmed.ncbi.nlm.nih.gov/30474069/)
- **Context changes how an arrow should be read:**
  - Arrow-based dose changes are not advised within ~2 h of a bolus, because insulin is still acting.
  - They are not advised on hybrid or closed-loop systems.
  - With predictive low-glucose suspend, only up arrows apply.
  - Kudva assumes more than 4 h after a meal means no active bolus.
  - Exercise, stress, menstruation, illness and steroids all alter needs.
  - → "Rising" right after a meal bolus is expected, and "rising" 4 h later may need action. Time since bolus, IOB or COB is the context that decides between them. [Brazilian statement](https://pmc.ncbi.nlm.nih.gov/articles/PMC7780381/), [Endocrine Society on Kudva](https://www.endocrine.org/news-and-advocacy/news-room/2018/streamlined-method-helps-people-with-diabetes-act-on-cgm-data)
- **Noisy 1-minute data:**
  - A user saw the AAPS watch arrow disagree with the phone's arrow, because the watch ignored smoothing of noisy Libre 1-minute data.
  - → Every surface has to compute the arrow the same way, or users notice. [AAPS #3414](https://github.com/nightscout/AndroidAPS/issues/3414)

### 1.3 Lag, accuracy, and the sensor life cycle
- **Physiological lag from blood to interstitial fluid:**
  - About 5–6 min in healthy adults and about 7–8 min in fasting T1D (Basu tracer studies).
  - Total blood-to-CGM lag reported at 5–25 min, longer after meals.
  - Mean 12.5 min in one T1D "push-pull" analysis. One study blames most delay on fibrous encapsulation around the sensor, up to about 20 min.
  - → During fast changes (exercise, rescue carbs) the number on the wrist trails reality. Rate and age context matter most then. [Basu 2013](https://www.researchgate.net/publication/256452201_Time_Lag_of_Glucose_From_Intravascular_to_Interstitial_Compartment_in_Type_1_Diabetes), [Sinha 2017](https://sites.bu.edu/bionicpancreas/files/2017/05/Sinha-CGM-lag-analysis-JDST-2017.pdf), [Diabetes 2019 encapsulation](https://diabetesjournals.org/diabetes/article/68/10/1892/35372/Fibrotic-Encapsulation-Is-the-Dominant-Source-of-Continuous-Glucose-Monitor-Delays)
- **Where accuracy is worst:**
  - CGM is least accurate at low glucose.
  - It is especially poor in the first 12 h after insertion.
  - It is prone to compression artefacts during sleep.
  - → Lows, the first night and the night itself are when the displayed value is least trustworthy. [Smartwatch nocturnal hypo study 2025](https://pmc.ncbi.nlm.nih.gov/articles/PMC11851596/)
- **Compression lows:**
  - Lying on the sensor caused sudden false drops of more than 25 mg/dL from the median of four sensors (video-verified sleeping position).
  - They look like a sharp drop followed by a quick rebound.
  - AID algorithms treat them as real, cut insulin, and push glucose up.
  - → A sudden night drop with a flat trend before it is a known false-alarm pattern. [Mensh 2013](https://pubmed.ncbi.nlm.nih.gov/23911167), [Kulzer 2024 review](https://journals.sagepub.com/doi/full/10.1177/19322968241267823), [UMich guidance](https://teamdynamix.umich.edu/TDClient/210/DepressionCenter/KB/Article/10689/CGM-Is-Reading-Low-Values-at-Night-Causes-and-Solutions)
- **Life-cycle states the face has to represent** **[secondary]**:

  | Sensor | Warm-up | Wear | End |
  |---|---|---|---|
  | Dexcom G7 | 30 min | 10 days | plus a 12 h grace period |
  | Libre 3 | 60 min | 14 days | hard stop |
  | Libre 3 Plus | 60 min | 15 days | hard stop |

  - → Warm-up (no value), first-day inaccuracy, end-of-life countdown and "no sensor" are all real states. Users ask for sensor-end reminders so they carry a spare: [GlucoDataHandler #295](https://github.com/pachi81/GlucoDataHandler/issues/295) and [xDrip #1467](https://github.com/NightscoutFoundation/xDrip/issues/1467). [Gluroo comparison](https://gluroo.com/blog/life-with-diabetes/dexcom-g7-vs-freestyle-libre-3/)
- **How official apps age a reading:**
  - Dexcom G7 keeps the last reading on screen for 10.5 min, then shows "—".
  - It raises Signal Loss after 20 min. [Dexcom FAQ (search excerpt)](https://www.dexcom.com/en-SG/faqs/why-am-i-getting-signal-loss?ipc=US)
  - Up to 24 h of missed readings backfill later. [Dexcom G7 FAQ](https://www.dexcom.com/en-sa/faqs/g7/why-am-i-getting-signal-loss)
  - Libre raises its Signal Loss alarm after 20 min without communication. [Abbott support](https://www.support.freestyle.abbott/hc/en-us/articles/33956342832273-What-would-cause-my-smartphone-to-set-off-the-Signal-Loss-Alarm-with-the-Libre-app)
  - → These are the platform conventions that users already know.
- **Stale data has caused regulatory recalls:**
  - Dexcom G7 iOS app 2.7.0 could freeze on a stale glucose value: a Class 2 recall in May 2025.
  - G7 apps, the Watch app included (≤2.8), did not warn when the transmitter failed, leaving users unaware of missed data and alarms. This was the most serious recall class.
  - → A face that looks live when it isn't is a recognised safety failure. [FDA recall record, 2.7.0](https://www.accessdata.fda.gov/scripts/cdrh/cfdocs/cfRES/res.cfm?id=213398), [FDA early alert, G7/ONE apps (search excerpt; page later returned 404)](https://fda.gov/medical-devices/medical-device-recalls-and-early-alerts/continuous-glucose-monitor-apps-correction-dexcom-inc-issues-correction-g7-apps-and-one-apps-due)
- **Water:**
  - The G7 sensor is rated to 2.4 m for 24 h. Libre 3 is rated to 1 m for 30 min **[secondary]**.
  - Bluetooth doesn't pass through water, so swim screens show stale data.
  - → Shower and swim are routine "no new data" periods. [MDS comparison](https://learn.mdsdiabetes.com/wiki/dexcom-g7-vs-freestyle-libre-3), [Sugar Sense setup](https://sugarsense.io/garmin-watch-glucose-setup)

### 1.4 Exercise
- **Riddell 2017 consensus:**
  - Start aerobic exercise when glucose is 7–10 mmol/L (126–180 mg/dL).
  - Blood ketones of 1.5 mmol/L or more contraindicate exercise.
  - Aerobic exercise lowers glucose. Very high intensity or anaerobic exercise can raise it through stress hormones and lactate.
  - → The same activity type can push glucose either way, so "exercising" alone doesn't predict the direction. [Riddell 2017 full text](https://www.digibete.org/wp-content/uploads/2018/01/Exercise-management-in-type-1-diabetes-a-consensus-Mike-Riddell-Lancet-DE-2017.pdf), [Riddell lab summary](https://mriddell.lab.yorku.ca/2017/02/nutrition-and-insulin-management-guidelines-for-exercise-in-type-1-diabetes/)
- **EASD/ISPAD 2020 CGM-and-exercise statement:**
  - Set the low alert at the highest available threshold, 5.6 mmol/L (100 mg/dL), when exercise starts.
  - Target 4.4–10.0 mmol/L for the first 90 min afterwards. People at higher risk raise the lower limit to 5.0–5.6.
  - After evening or long, hard exercise, set the night low alert at 4.4 mmol/L (80 mg/dL) or higher, and avoid a correction bolus at bedtime.
  - Carb advice is stratified by trend arrow before, during, after exercise and at night.
  - → Exercise moves the danger line upward and extends into the following night, so the meaning of "low" is time-dependent. [Moser 2020, Diabetologia](https://link.springer.com/article/10.1007/s00125-020-05263-9) (numbers from search excerpts of the full text **[secondary]**)
- **ISPAD 2022 on ketones and exercise:**
  - With blood ketones at 1.5 mmol/L or more, exercise is contraindicated.
  - At 0.6–1.4, postpone exercise and give half a correction.
  - [ISPAD 2022 exercise](https://pmc.ncbi.nlm.nih.gov/articles/PMC10107219/)
- **What users ask for during sport:**
  - "When doing sports, riding a bike etc. you … cannot read the exact tiny number values, it is good enough to see 'green'."
  - → Users ask for an at-a-glance range state during motion. [AAPS #2271](https://github.com/nightscout/AndroidAPS/issues/2271)
- **Competition stress:** an athlete reported a rise from 100 to 300 mg/dL during a 50 m race, an anecdote combining effort and stress. [PMC10012106](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC10012106/)

### 1.5 Night, hypo unawareness, and detection from the watch
- **How common night hypos are:**
  - In the DCCT, 55% of severe hypos happened during sleep and 43% between midnight and 8 AM.
  - Kulzer 2024 cites DCCT for 51% of severe nocturnal events going undetected during sleep.
  - "Dead in bed" accounts for about 5–6% of deaths under 40 in T1D.
  - [Kulzer 2024 / DCCT](https://journals.sagepub.com/doi/full/10.1177/19322968241267823), [Sovik via NCT02714972](https://cdn.clinicaltrials.gov/large-docs/72/NCT02714972/Prot_000.pdf)
- **Sleep blunts the waking response to a low:**
  - At an induced 2.2 mmol/L, 1 of 16 T1D patients woke, against 10 of 16 controls.
  - → Night alerts have to work against a blunted arousal response. [Schultes 2007, PLoS Med](https://journals.plos.org/plosmedicine/article?id=10.1371%2Fjournal.pmed.0040069)
- **Impaired awareness of hypoglycaemia:**
  - Prevalence is about 25% (Geddes 2008). A pooled meta-analysis gives 27.2%. Recent CGM-era cohorts show 17.5–21%.
  - It is measured by the Gold score (4 or more out of 7).
  - → For a quarter of wearers the watch may be the only signal of a low. [PMC11178233](https://pmc.ncbi.nlm.nih.gov/articles/PMC11178233/), [PMC12434435](https://pmc.ncbi.nlm.nih.gov/articles/PMC12434435)
- **Detecting night lows from smartwatch signals alone** (Garmin heart rate, stress/HRV, activity):

  | Night period | Level | AUROC | Sensitivity | PPV | Specificity |
  |---|---|---|---|---|---|
  | Early sleep | 1 | 0.78 | 0.78 | 0.25 | 0.64 |
  | Early sleep | 2 | 0.83 | 0.89 | 0.13 | 0.64 |
  | Late sleep | — | 0.34–0.35 | — | — | — |

  - Late sleep is no better than chance.
  - → Heart rate is weak context and cannot replace CGM. [PMC11851596](https://pmc.ncbi.nlm.nih.gov/articles/PMC11851596/)
- **Night complaints from users:**
  - An ambient face was "so bright that I can't even sleep". [xDrip #1137](https://github.com/NightscoutFoundation/xDrip/issues/1137)
  - A Pebble face vibrated every 5–10 min at night about phone battery at 30%, ignoring silent mode. [xDrip #881](https://github.com/NightscoutFoundation/xDrip/issues/881)
  - → Night brightness and non-glucose nags are real complaints.

### 1.6 Driving
- **UK rules:**
  - Glucose must be 5 mmol/L or more before driving ("five to drive"). At 4–5, eat carbs first.
  - Check before every trip and at least every 2 h.
  - On a hypo: stop, take the keys out, leave the driver's seat, treat, and don't drive until 45 min after glucose is back at 5 or above.
  - CGM is acceptable if it is approved for non-adjunctive use. Keep a finger-prick meter as backup.
  - There is no high limit.
  - [Diabetes UK](https://www.diabetes.org.uk/living-with-diabetes/life-with-diabetes/driving)
- **DVLA additions:**
  - Group 2 (lorry and bus) drivers may use CGM since November 2025.
  - "Trust your symptoms – do not rely solely on CGM."
  - A driver who becomes dependent on alarms to recognise lows must stop driving and tell the DVLA.
  - The 2019 version of the guidance added that the CGM must not be actively used while driving.
  - [gov.uk fitness to drive](https://gov.uk/guidance/diabetes-mellitus-assessing-fitness-to-drive), [INS186](https://assets.publishing.service.gov.uk/media/690b4eb688a98da87e292310/ins186_a_guide_for_drivers_with_insulin_treated_diabetes.pdf), [INF294 2019 copy](https://www.birchwoodmedicalcentre.co.uk/_common/getdocument?id=173207)
- **ADA 2024:**
  - Raise glucose before driving if it is below 90 mg/dL. Treat below 70.
  - CGM helps catch a falling trend before it impairs driving.
  - 25% of surveyed people wrongly believed driving below 70 was safe.
  - → The driving threshold (90–100 mg/dL) differs from the clinical low threshold (70). [ADA 2024](https://diabetesjournals.org/care/article/47/11/1889/157262/Diabetes-and-Driving-A-Statement-of-the-American), [Endocrinology Advisor summary](https://www.endocrinologyadvisor.com/pri/diabetes-and-driving-a-statement-of-the-american-diabetes-association/)

### 1.7 Alcohol, illness, menstruation, stress, travel
- **Alcohol:**
  - Moderate evening alcohol led to hypos after breakfast the next morning: 5 of 6 participants had symptomatic hypos between 10:00 and 12:00, at 1.9–2.9 mmol/L. [Turner 2001](https://diabetesonthenet.com/journal-diabetes-nursing/effects-of-alcohol-on-blood-glucose-levels-in-people-with-type-1-diabetes-a-systematic-review/)
  - Reviews describe the risk window as up to 10–16 h. **[secondary]** [JDN pump article](https://diabetesonthenet.com/wp-content/uploads/jdn15-8_292294-297-1.pdf)
  - A hypo is easily mistaken for being drunk. [Browne 2014](https://pmc.ncbi.nlm.nih.gov/articles/PMC4120421/)
  - → After a night out, the risk peaks the next morning, not during the evening.
- **Illness (ISPAD 2022 sick-day rules):**
  - Keep blood ketones below 0.6 mmol/L. At 0.6 or above, switch closed loops to manual.
  - 1.5–2.9 is high; 3.0 or above meets the DKA criterion.
  - Check glucose and ketones every 1–2 h **[secondary]**.
  - → Sick days are a distinct mode that adds ketones as a data point. Continuous ketone sensors are emerging. [ISPAD sick-day chapter](https://www.digibete.org/wp-content/uploads/2023/05/ISPAD-Ch_13_SDR.pdf), [CWD summary](https://childrenwithdiabetes.com/cwd-news/sick-day-guidelines-2023/), [CKM recommendations 2025](https://www.sciencedirect.com/science/article/abs/pii/S2213858725003316)
- **Menstrual cycle:**
  - TIR is lower in the mid- and late-luteal phase, and time above range is higher late-luteal.
  - Time below range is higher around menstruation.
  - Insulin needs rise in the luteal phase.
  - The effect is modest on AID: TIR 80.7% vs 82.7%.
  - [J Clin Endocrinol Metab 2022](https://pubmed.ncbi.nlm.nih.gov/35869507/), [Systematic review 2023](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC9962060/), [780G study](https://pubmed.ncbi.nlm.nih.gov/41877361/)
- **Stress:**
  - Evidence is mixed.
  - Self-reported stress went with more time above range and less time below range, and morning cortisol correlated with glucose variability.
  - Short lab stressors showed no effect in the fasting state.
  - [figshare 2024](https://figshare.com/articles/online_resource/Supplemental_Document_Impact_of_psychological_stress_and_physical_activity_on_CGM_metrics_and_non-glucose_signals_in_Type_1_Diabetes/25612011), [SAT-641](https://pmc.ncbi.nlm.nih.gov/articles/PMC7207325/), [NCT00442884](https://clinicaltrials.gov/study/NCT00442884)
- **Travel:**
  - Adjust basal insulin by about 4% per time zone; it matters from 5 zones.
  - Reset pump and CGM clocks on arrival.
  - In the paper's worked example, the traveller changes their watch to local time only after the basal dose.
  - → The watch's own clock and time zone are part of insulin timing. [PMC7962589](https://pmc.ncbi.nlm.nih.gov/articles/PMC7962589/)

### 1.8 Diving (the owner's watch is a dive computer)
- **DAN/UHMS (2005 workshop):**
  - Glucose must be 150 mg/dL (8.3 mmol/L) or more and stable or rising.
  - At least three checks: 60 min, 30 min and immediately before the dive. Some sources say 10 min before.
  - Delay the dive below 150 or above 300 mg/dL.
  - Low signal to the buddy: an "L" made with thumb and index finger.
  - On a low: surface with the buddy, establish positive buoyancy, take glucose, and leave the water.
  - Limits: 30 m, 60 min, no decompression stops, no overhead environments.
  - Check after every dive and frequently for 12–15 h.
  - [DAN guidelines](https://dan.org/health-medicine/health-resource/health-safety-guidelines/guidelines-for-diabetes-and-recreational-diving/), [Scuba Diving summary](https://www.scubadiving.com/training/basic-skills/diving-diabetes), [Healio](https://www.healio.com/news/endocrinology/20141203/recreational-diving-and-diabetes)
- **Other national protocols:**
  - SPUMS: 8 mmol/L or more on three readings; cancel diving for the day above 16. [SPUMS extract](https://prodivecairns.com/pdf/Suggested-assesment-for-divers-with-Diabetes-EXTRACT-SPUMS-diving-medical-Guideline.pdf)
  - UK: one guideline column reads "9.0 mmol/L & stable or rising" **[unverified which body]**. [ADS 2016 appendix](https://www.diabetessociety.com.au/documents/ADS_Diving_Diabetes_2016_Appendices_Final.pdf)
  - There is no international consensus. [Review 2022](https://www.researchgate.net/publication/362256659_Diving_with_Diabetes_-_A_Review_of_Recommendations)
  - → Protocols are about trend and sequence ("three rising checks"), not a single number. This suits a countdown or sequence view on the surface.
- **CGM while diving:** a DAN Europe pilot found no difference in 5-min CGM values before, during and after 26 dives. [Alert Diver 2017](https://alertdiver.eu/en_US/articles/diving-with-diabetes-guidelines-and-latest-research/)
- **Hard constraints:**
  - Sensors are rated to 1–2.4 m, far shallower than dives.
  - Bluetooth doesn't work underwater.
  - On the Descent Mk3, "Connect IQ features are not available while diving".
  - → Any glucose role for the face is on the surface: before, between and after dives. [Descent Mk3 manual](https://www8.garmin.com/manuals/webhelp/GUID-9183E86B-2399-4CFC-AB50-EAFC6D6ED326/EN-US/GUID-C3289B5E-1A70-4BB2-A7F0-9B16CF60D75D.html)

---

## 2. Daily contexts and what can be detected

| Context | What the wearer needs at a glance (evidence) | Detectable / predictable signal |
|---|---|---|
| Meeting, date, meal out, cinema | Glance without pulling out the phone; Dexcom markets exactly these moments ([Dexcom](https://www.dexcom.com/en-us/all-access/dexcom-cgm-explained/direct-to-apple-watch)); "smartwatch facilitated discrete access" ([Sehgal 2024](https://onlinelibrary.wiley.com/doi/10.1111/dme.15168)) | Recent meal bolus or carbs; post-meal rise expected within ~2 h of a bolus ([Brazilian statement](https://pmc.ncbi.nlm.nih.gov/articles/PMC7780381/)) |
| Night | Lows matter more and are less trustworthy (compression); arousal is blunted; brightness complaints | Time of day; sleep state; HR as weak context ([PMC11851596](https://pmc.ncbi.nlm.nih.gov/articles/PMC11851596/)); evening exercise or alcohol raise next-night / next-morning risk |
| Sport | Colour or range readable in motion ([AAPS #2271](https://github.com/nightscout/AndroidAPS/issues/2271)); higher low line (5.6 mmol/L) | Activity / HR on the watch itself; Garmin data fields inside activities |
| Driving | 5 mmol/L (UK) / 90 mg/dL (ADA) line; trend before departure; no interaction while driving | Possibly speed or vehicle; not established in sources |
| Phone far, locked or dead | Whether the value is current, and which link broke (urchin shows separate icons for rig→cloud, cloud→phone, phone→watch) ([urchin](https://github.com/longlandm/urchin-cgm)) | Age of the last reading; connection state; direct sensor-to-watch is possible (G7 Direct to Watch, Libre 3 Kerfstok) |
| Shower, swim, dive | Data will be stale; diving needs a pre-dive trend sequence | Water lock / activity type; known gaps |
| Travel | Clock and time-zone change interacts with insulin timing | Time-zone change event |
| Sick day | Ketones, frequent checks | Not detectable; user-declared |
| After insulin or carbs | Rising after a bolus / falling after rescue carbs is expected | IOB/COB, time since bolus |
| Predicted low | Earlier warning cut time below range by more than 40% and severe lows by more than 60% in adolescents ([Drug Topics](https://www.drugtopics.com/view/real-time-cgm-predictive-alarm-improves-glycemic-control-in-adolescents-with-t1d)) | Dexcom "Urgent Low Soon" fires when 55 mg/dL is predicted within 20 min ([Dexcom](https://www.dexcom.com/en-us/faqs/how-does-the-urgent-low-soon-alert-work)); loop "eventual BG" |
| Muted or snoozed | Users lose track of mute vs quiet hours ([GDH #259](https://github.com/pachi81/GlucoDataHandler/issues/259)) | Alert state, a known quantity |
| Watch on or off wrist | Users want the watch to alert when worn and the phone when not ([GDH #230](https://github.com/pachi81/GlucoDataHandler/issues/230)) | Off-wrist detection |
| Temporary target or automation active | "See if automations have been triggered or how long a temp target remains" ([AAPS #4619](https://github.com/nightscout/AndroidAPS/issues/4619)) | Loop state |

---

## 3. Stigma and discretion
- **How common perceived stigma is:**
  - 76% of people with T1D report that diabetes carries stigma: 74% of adults and 83% of parents.
  - The figure is 52% for T2D. n = 5,422 (dQ&A panel).
  - → Discretion is the norm, not a niche. [Liu 2017, Clinical Diabetes](https://diabetesjournals.org/clinical/article/35/1/27/35423/Stigma-in-People-With-Type-1-or-Type-2-Diabetes), [diaTribe](https://diatribe.org/issues/67/learning-curve)
- **How adults with T1D hide it** (27 adults, qualitative):
  - Injecting and testing in toilet cubicles.
  - Avoiding swimming so a pump isn't seen.
  - Being taken for a drug user when injecting.
  - A hypo mistaken for drunkenness.
  - Not disclosing at work (fear for careers) or to partners ("I used to hide it from any boyfriends").
  - Being blamed for eating sweets.
  - → Visible diabetes signals carry real social costs, and food-related signals invite judgement. [Browne 2014, BMJ Open](https://pmc.ncbi.nlm.nih.gov/articles/PMC4120421/)
- **Device visibility as a barrier** (n = 1,503 adults):
  - 47% cite the hassle of wearing devices.
  - About 35% dislike having devices on their body **[secondary]**.
  - Young adults have the lowest device use. [Tanenbaum 2017 via HealthExec](https://healthexec.com/topics/healthcare-management/healthcare-economics/barriers-utilizing-diabetes-devices)
  - "Wary Wearers" (11%) are younger, more distressed and have higher HbA1c. [Tanenbaum 2018](https://doi.org/10.1177/1932296818793756)
- **Teens:**
  - They are self-conscious about CGM visibility at first and try to "hide it in a way".
  - Over time some reframe the device as a chance to educate others. [PHCFM 2023](https://phcfm.org/index.php/phcfm/article/view/3879/6164)
  - Visibility of supplies and of hypo symptoms is linked to stigma and to avoiding activities. [PMC5935805](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC5935805/)
- **Alarms in public:**
  - Loud alarms cause embarrassment in public and are intrusive at night for others. [PMC11223994 (T2D)](https://pmc.ncbi.nlm.nih.gov/articles/PMC11223994/)
  - People switch alarms off at social events and at work.
  - Some perceive alarms as "signalling personal failure". [Keeping safe (IAH)](https://www.researchgate.net/publication/318613732_Keeping_safe_Continuous_glucose_monitoring_CGM_in_persons_with_Type_1_diabetes_and_impaired_awareness_of_hypoglycaemia_a_qualitative_study)
  - A systematic review found distress from alarm frequency and embarrassment from visibility. [Natale 2023](https://onlinelibrary.wiley.com/doi/10.1111/1753-0407.13454)
  - Vibration is described as noticeable in noise and less embarrassing than sound. [Alarm-fatigue review 2025](https://pmc.ncbi.nlm.nih.gov/articles/PMC12731774/)
  - → How an alert or high value looks to the wearer carries emotional weight ("failure"), as well as how it looks to others.
- **Discreet or abstract ways of showing glucose, already in the wild:**
  - Nightscout Clock has animated faces where glucose drives colour or state: a unicorn mane, a race car that stops when data is stale, a dragon whose fire goes out, a smiley "mood". [nightscout-clock](https://github.com/ktomy/nightscout-clock)
  - SugarPixel has an emoji mode and a night mode dimmed "to just a few small lights". [Diabetech](https://www.diabetech.info/p/sugarpixel-the-cgm-display-with-customizable-alerts-how-it-works)
  - A student wristband shows a colour instead of a number. [UW-Madison](https://bmedesign.engr.wisc.edu/public/projects/f25/when_they_glow-you_know/file/view/54e70151-de59-44d7-9586-436a619241ab/GlucoseAlerting_Final_Report.pdf)
  - GlucoDataHandler offers a "Glucose (very big and colored)" complication, which AAPS users asked to copy. [AAPS #5155](https://github.com/nightscout/AndroidAPS/issues/5155)
  - Third-party Apple Watch apps market "discreet monitoring". [GlucoWatch](https://apps.apple.com/mx/app/glucowatch/id1609222678)
- **Evidence gap:** I found no study of how others perceive a glucose number, a red colour or a graph on a watch face. Stigma research predates wrist displays.

---

## 4. Existing CGM watch UIs, judged

| Product | Shows | Stale / no data | Alarms on wrist | Praise / complaints |
|---|---|---|---|---|
| **Dexcom Connect IQ (made by Garmin)** | Widget: value, arrow, 3 h history; activity data field; Face It faces ([Healthline](https://www.healthline.com/diabetesmine/garmin-and-dexcom-diabetes-data)) | "NO DATA", a broken-cloud icon | None; "secondary passive monitoring", phone must be in range with data ([BusinessWire](https://www.businesswire.com/news/home/20211013005069/en)) | Store rating **1.9**, 10K+ installs ([store](https://apps.garmin.com/en-US/apps/027db46d-fbc8-4362-a65f-05fa6c0f113f)); G7 needed a "share and follow yourself" workaround; on an Edge the field "turns itself off" about half the time; reboots ([Garmin forum](https://forums.garmin.com/apps-software/mobile-apps-web/f/connect-iq-store-ios/329932/dexcom-g7-not-working-with-existing-gc-dexcom-data-field-and-glance-app/1724728)) |
| **Dexcom G7 Apple Watch** | Value, arrow, graph 1/3/6 h; complications; Direct to Watch without the phone ([Dexcom FAQ](https://www.dexcom.com/en-us/faqs/what-g7-features-can-i-view-on-my-apple-watch-when-in-direct-to-watch-mode)) | Complications show "- - -"; refresh is budgeted, about every 15 min **[third-party]** ([GlucoseGlance](https://github.com/dgsmith/GlucoseGlance)) | Mirrored alerts; Focus modes can silence them | Dismissing on the watch ≠ dismissing on the phone, so alerts repeat; requests for more complication sizes ([G6 App Store](https://apps.apple.com/us/app/dexcom-g6/id1209262925?mt=8)); the watch can't Share/Follow ([G7 listing](https://apps.apple.com/us/app/dexcom-g7/id1569432518)) |
| **Abbott Libre app for Watch** | Value plus trend graph; large-centre, small-round and corner complications; Apple only, US first ([Diabetech](https://www.diabetech.info/p/freestyle-libre-app-gets-apple-watch-app-ai-food-assistant)) | — | Mirrored; "dismiss alarms on your watch and phone with one tap" ([Abbott](https://www.freestyle.abbott/en-us/products/freestyle-libre-app.html)) | No Wear OS or Garmin app |
| **xDrip+ Wear OS** | XDrip / Large / BigChart faces: value, delta, delta time, status line (basal %, IOB, COB), heart rate, steps, uploader / bridge battery, treatments on graph ([WatchGuide](https://github.com/NightscoutFoundation/xDrip/blob/master/Documentation/WatchGuide.md)) | Value **struck through** once stale (`ageLevel`, [source](https://github.com/NightscoutFoundation/xDrip/blob/master/wear/src/main/java/com/eveningoutpost/dexdrip/BaseWatchFace.java)) | Vibrate-only profile, smart alerting, snooze syncs to the phone | Complications showing "null null" (53-comment issue [#303](https://github.com/NightscoutFoundation/xDrip/issues/303)); the faces stopped working when Wear OS 5 dropped the old runtime ([#3581](https://github.com/NightscoutFoundation/xDrip/discussions/3581)); Mi Band shows no data-loss indication, "dangerous" ([#1317](https://github.com/NightscoutFoundation/xDrip/issues/1317)) |
| **AAPS Wear OS** | AAPSv2: loop age, value, minutes since reading, 5-min delta, 15-min average delta, phone battery, basal, BGI, COB, IOB ([docs](https://androidaps.readthedocs.io/en/latest/WearOS/WearOsSmartwatch.html)); bolus, carbs, temp target and wizard from the watch ([docs](https://androidaps.readthedocs.io/en/latest/RemoteFeatures/RemoteControlWearOS.html)) | Value struck through after 12 min; loop indicator turns red after 14 min ([BaseWatchFace.kt](https://github.com/nightscout/AndroidAPS/blob/master/wear/src/main/kotlin/app/aaps/wear/watchfaces/utils/BaseWatchFace.kt)) | Watch vibrates every 30 s for 5 min; Silence / Mute 5 min | Requests: TIR "green" indicator (#2271), temp-target complication with remaining time (#4619), watch-battery key (#3758); outdated IOB on a dual face (#4858) |
| **AAPS custom watch-face designer** | Keys: sgv, direction, delta, avg_delta, timestamp, iob, cob, basalRate, bgi, reservoir, uploader / rig battery, tempTarget, loop, patient_name, day / week / month, analog hands; dynData drives colour, image, offset or rotation from sgvLevel (−1 / 0 / 1), timestamp 0–60 min, delta and so on ([reference](https://androidaps.readthedocs.io/en/latest/ExchangeSiteCustomWatchfaces/CustomWatchfaceReference.html)) | `invalid*` keys, "-- minute ago" | — | Shows the vocabulary DIY users want to design with |
| **Nightscout Pebble (2014) / urchin** | Origin: a father put his son's Dexcom G4 data on a Pebble during kindergarten ([Nightscout](https://en.wikipedia.org/wiki/Nightscout)); urchin is graph-first with a configurable status bar (battery, raw, basal, IOB) | A gap in the graph = not current; per-hop icons after 10 min (phone, cloud) and 20 min (rig) ([urchin](https://github.com/longlandm/urchin-cgm)) | Pebble vibration | "Unopinionated, ridiculously configurable" |
| **Nightscout Clock / SugarPixel** | Value, arrow, delta, graph; abstract animated faces; emoji mode | Age bars: 1–5 min green, 6–19 yellow; at 20 min the value turns grey and the arrow becomes an X ([repo](https://github.com/ktomy/nightscout-clock)) | Loud plus vibration puck; **randomised tones** against alarm fatigue ([Diabetech](https://www.diabetech.info/p/sugarpixel-the-cgm-display-with-customizable-alerts-how-it-works)) | Vibration puck "more pleasant than an alarm" ([forum](https://forum.diabetes.org.uk/threads/sugar-pixel.116804/)) |
| **Juggluco / Kerfstok (Garmin)** | Face with time, date, battery, HR, glucose, trend; **Libre 3 Kerfstok connects to the sensor directly** ([changelog](https://www.juggluco.nl/Juggluco/changes.html)) | Keeps only a short history on the watch | Low, high and **signal-loss** alarms; vibrates until touched | Best offline Garmin option **[third-party]** ([Diabetotech](https://www.diabetotech.com/cgm-to-smartwatch)) |
| **GlucoDataHandler** | Wear OS complications incl. "very big and colored"; Garmin via the xDrip web-service format ([repo](https://github.com/pachi81/GlucoDataHandler)) | "Obsolete" alarm | Alarms on the watch | Requests: a single small vibration on every new value "if you are entering lows at high speed" ([#226](https://github.com/pachi81/GlucoDataHandler/issues/226)); DND behaviour ([#194](https://github.com/pachi81/GlucoDataHandler/issues/194)) |
| **xDrip+/Spike/Nightscout Watch (Garmin)** | Time, date, value, graph, trend, reading age, steps, HR, notification / alarm counts; IOB, TBR, COB with AAPS; low-power AMOLED variant ([repo](https://github.com/swissalpine/GARMIN-xDrip-Spike-Nightscout-Watchface)) | — | "Watchfaces cannot issue alarms"; the SDK polls only every 5 min | Store rating **4.7**, 10K+ installs ([store](https://apps.garmin.com/en-US/apps/8fab3746-f56f-4b41-b3c7-5f4aaeaed6c9)), against 1.9 for the official app |
| **CGM+ (watchface.io)** | "CGM Time" age field; arrows → ⬈ ↑ ↑↑ | Stale warning and cutoff settings; "---" | None | Readings arrive every 5–8 min and can be up to 10 min behind, because Garmin's background budget is 5 min ([FAQ](https://watchface.io/docs/cgm/faq)) |
| **Sugar Sense / GGlance (Garmin)** | Zone colours (default 3.9 / 10.0); 5-state arrow; 1–24 h graph; GGlance has a 12 h TIR screen | **Greys after 15 min, disappears at 2 h** ([Sugar Sense](https://sugarsense.io/garmin-watch-glucose-setup)) | "Never vibrates or sounds"; alerts stay on the phone | GGlance users call it "unobtrusive" ([GGlance](https://gglance.app/)) **[vendor]** |
| **Loop (Apple Watch)** | Loop status, value, arrow, **eventual glucose**; graph with prediction (2–12 h); active insulin and carbs; carbs and bolus entry, where a bolus needs the crown to "merge two triangles" ([LoopDocs](https://loopkit.github.io/loopdocs/operation/features/watch/)) | — | — | Complication refresh is limited by Apple |
| **Trio** | Apple: loop status, IOB, COB; Garmin face and field are display-only and update every loop cycle ([TrioDocs](https://triodocs.org/configuration/settings/devices/smart-watch/)) | Proposed "Grey Out After" setting (5–30 min); IOB/COB shown as "--" when stale ([PR #1723](https://github.com/nightscout/Trio/pull/1723)) | — | — |
| **Gluroo** | Wear OS: Big BG, chart, COB, IOB complications ([Gluroo](https://gluroo.com/watchface/)); Apple: a "Contact" complication workaround for real-time updates | Wear OS complications go stale because the platform controls refresh ([support](https://gluroo.com/support/my-wear-os-watch-face-is-showing-an-old-cgm-reading-of-my-blood-sugar/)) | — | — |

**Data users ask for most.** Evidence comes from feature requests, field lists in the DIY faces, and what official faces lack:
- **Age or staleness of the reading.** Every DIY face has it; its absence is called "dangerous" (xDrip #1317); FDA recalls involved it.
- **Delta and average delta.** Present in AAPS, xDrip, Nightscout and the Garmin faces; missing from the official Dexcom and Garmin app.
- **IOB and COB, loop status and age.** Present in AAPS, Loop, Trio, Gluroo and the Garmin DIY faces; a sync bug for them is a top complaint.
- **Graph or recent history, with treatments.**
- **Eventual or predicted glucose** (Loop).
- **Range colour or "green" state** (AAPS #2271, GDH big coloured complication).
- **Sensor-end countdown** (GDH #295, xDrip #1467).
- **Temporary target and its remaining time** (AAPS #4619).
- **Phone or uploader battery.** Wanted, but disliked when it alarms (xDrip #881).
- **Watch battery** (AAPS #3758).
- **Heart rate and steps** (xDrip, Garmin faces).
- A smartwatch systematic review found users liked "seeing the last values" and disliked battery drain and not being able to delete entries from the watch. [JMIR mHealth 2024](https://mhealth.jmir.org/2024/1/e54826/PDF)

---

## 5. Alarm fatigue and wrist haptics
- **Definition:** alarm fatigue is when so many alarms arrive that the person becomes less responsive to them, which constrains the safe use of diabetes devices. [Shivers 2013](https://doi.org/10.1177/193229681300700324)
- **Utah, 95 adults on Dexcom (Lin 2020):**
  - 9% had the low alarm off and 13% the high alarm off.
  - A low threshold of 73 mg/dL or more went with less time below 70: 2.3% vs 4.7%.
  - Lower high thresholds produced 30.9 vs 14.2 high alarms, and more alarms correlated with *longer* recovery from highs.
  - ROC-optimal thresholds: low 75, high 170.
  - → More alarms is not more safety. [PMC6977942](https://pmc.ncbi.nlm.nih.gov/articles/PMC6977942/)
- **Review of 2025 (paediatric):**
  - Alarm-fatigue prevalence ranges from 0 to 62%; discontinuation from 12 to 62%.
  - 50% of former CGM users cite too many alarms.
  - 73% of parents are woken by devices, and CGM alarms cause 38% of those awakenings.
  - Parents responded to 37% of alarms; patients to 29%.
  - [PMC12731774](https://pmc.ncbi.nlm.nih.gov/articles/PMC12731774/)
- **Teens and young adults (CITY trial):**
  - They tolerate a low alert of about 70 and a high alert of 240–300.
  - Young adults name alarm fatigue as a reason for quitting.
  - [Messer 2023](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC12016964/), [CITY protocol](https://cdn.clinicaltrials.gov/large-docs/94/NCT03263494/Prot_SAP_001.pdf)
- **Official non-negotiables:**
  - Dexcom's Urgent Low at 55 mg/dL and its failure alerts (transmitter, sensor, app stopped) cannot be silenced. The G6 also has a vibrate-only option.
  - Unacknowledged urgent alerts escalate to sound even in quiet mode.
  - → Some states are designed to break discretion on purpose. [G6 App Store](https://apps.apple.com/us/app/dexcom-g6/id1209262925?mt=8), [Dexcom G7 alerts FAQ](https://www.dexcom.com/en-us/faqs/are-there-any-changes-to-alerts-for-dexcom-g7-compared-to-dexcom-g6)
- **What wrist apps do:**
  - Watch alarms: AAPS repeats every 30 s for 5 min; xDrip uses a vibrate-only profile; Juggluco vibrates until touched.
  - No watch alarms: Sugar Sense and the Garmin Dexcom app. Garmin faces technically cannot alarm.
  - → The ecosystem is split on whether the wrist alerts at all.
- **Why users want wrist haptics:**
  - A single tick per reading while falling fast. [GDH #226](https://github.com/pachi81/GlucoDataHandler/issues/226)
  - Watch-first alerts when the watch is worn. [GDH #230](https://github.com/pachi81/GlucoDataHandler/issues/230)
  - Quiet hours that survive mute and unmute. [GDH #259](https://github.com/pachi81/GlucoDataHandler/issues/259)
  - Some alarms that override DND while others don't. [GDH #194](https://github.com/pachi81/GlucoDataHandler/issues/194)
- **Vibration as a waking signal** (purpose-built devices, not watches; summaries from a vendor page **[vendor]**):
  - About 97% of 20 healthy adults woke from deep sleep, median 52 s (Attali 2020).
  - Pulsed vibration woke 100% and continuous 92% (Ashley 2007).
  - 80–84% of hard-of-hearing adults woke, against 58% for a tone (Bruck & Thomas 2009).
  - People can also dismiss a watch alarm without truly waking.
  - [Dawn summary](https://dawnbands.com/pages/do-vibration-alarms-work)
  - Awake users recognised wrist-vibration patterns 89–99% of the time. [GoodVibes, arXiv](https://arxiv.org/pdf/2406.01738)
  - → Combine this with T1D's blunted arousal: 1 of 16 woke at 2.2 mmol/L ([Schultes](https://journals.plos.org/plosmedicine/article?id=10.1371%2Fjournal.pmed.0040069)).
- **Other ways to reduce fatigue:**
  - Predictive alerts (20 min ahead) cut both lows and alarm burden. [Drug Topics](https://www.drugtopics.com/view/real-time-cgm-predictive-alarm-improves-glycemic-control-in-adolescents-with-t1d)
  - Requiring 3 consecutive readings reduced how often a tighter-range alarm would fire (Scheinker 2025, via the review). [PMC12731774](https://pmc.ncbi.nlm.nih.gov/articles/PMC12731774/)
  - SugarPixel randomises its tones to resist habituation. [Diabetech](https://www.diabetech.info/p/sugarpixel-the-cgm-display-with-customizable-alerts-how-it-works)
  - Healthline notes that compression lows wake people for nothing. [Healthline](https://www.healthline.com/health/diabetes/dexcom-g7-cgm-review)

---

## 6. Contradictions and open questions
- **Colour:**
  - For: users ask for "just green" in sport, and DIY faces colour by zone.
  - Against: stigma research shows visible signals cost socially, and wearers read alerts as "personal failure".
  - No study tests how others perceive a coloured number on a watch.
- **Arrow vocabulary:** Dexcom uses 7 states, Libre 5, Medtronic up to 3 arrows per direction. The same glyph means different speeds, and noisy 1-minute Libre data needs smoothing to keep surfaces consistent.
- **When "stale" begins:** the products disagree.

  | Product | Behaviour | At |
  |---|---|---|
  | Dexcom | Dashes | 10.5 min |
  | AAPS / xDrip | Strikethrough | 12 min |
  | AAPS | Loop indicator red | 14 min |
  | Sugar Sense | Grey | 15 min |
  | Urchin | Phone and cloud icons | 10 min |
  | Urchin | Rig icon | 20 min |
  | Dexcom, Libre | Signal-loss alarm | 20 min |
  | Nightscout Clock | Grey value, arrow becomes X | 20 min |
  | Sugar Sense | Value hidden | 2 h |

  On Garmin, a 5-minute background budget means readings can legitimately be 5–10 min old.
- **Should the wrist alert at all?**
  - No: Garmin's Dexcom app and Sugar Sense.
  - Yes: Juggluco, AAPS, xDrip, GDH.
  - The DVLA treats dependence on alarms to recognise lows as a reason to stop driving.
- **Driving:** the 2019 DVLA wording said the CGM must not be actively used while driving, while ADA notes CGM helps catch falling trends. Whether a glance at the wrist counts as "active use" is not addressed by any source.
- **Diving:**
  - Protocols predate CGM and rely on finger-sticks.
  - CGM is rated far shallower than dives, and Bluetooth fails underwater.
  - Descent dive mode blocks Connect IQ.
  - rt-CGM diving evidence is a 26-dive pilot.
  - The UK threshold (9.0 vs 8.3 mmol/L) is unverified.
- **Heart-rate-based hypo detection:** promising in early sleep (AUROC up to 0.83) but PPV of 0.13–0.25 and failure in late sleep. It is only context, not an alarm source.
- **Gaps in the evidence base:**
  - Stigma studies are pre-smartwatch and mostly qualitative.
  - Reddit, the main place people discuss watch faces, could not be accessed here.
  - Tanenbaum's percentages, the Moser numbers, the Kudva arrow thresholds and the vibration-wake studies are taken from secondary summaries.

---

## Sources read
1. Battelino et al. 2019, Clinical Targets for CGM Data Interpretation — https://pmc.ncbi.nlm.nih.gov/articles/PMC6973648/
2. Brazilian Diabetes Society, Using trend arrows in CGM systems (2021) — https://pmc.ncbi.nlm.nih.gov/articles/PMC7780381/
3. Medscape on Aleppo/Laffel 2017 trend-arrow guidance — https://www.medscape.com/viewarticle/888896
4. Kudva et al. 2018, Approach to using trend arrows in FreeStyle Libre — https://pubmed.ncbi.nlm.nih.gov/30474069/
5. Endocrine Society press release on Kudva 2018 — https://www.endocrine.org/news-and-advocacy/news-room/2018/streamlined-method-helps-people-with-diabetes-act-on-cgm-data
6. Riddell et al. 2017, Exercise management in T1D (full-text PDF) — https://www.digibete.org/wp-content/uploads/2018/01/Exercise-management-in-type-1-diabetes-a-consensus-Mike-Riddell-Lancet-DE-2017.pdf
7. Moser et al. 2020, EASD/ISPAD CGM and exercise — https://link.springer.com/article/10.1007/s00125-020-05263-9
8. ISPAD 2022 Exercise guideline — https://pmc.ncbi.nlm.nih.gov/articles/PMC10107219/
9. ISPAD 2022 Sick-day chapter — https://www.digibete.org/wp-content/uploads/2023/05/ISPAD-Ch_13_SDR.pdf
10. Children with Diabetes, Sick-day guidelines 2023 — https://childrenwithdiabetes.com/cwd-news/sick-day-guidelines-2023/
11. Basu 2013, Time lag of glucose in T1D — https://www.researchgate.net/publication/256452201
12. Sinha 2017, Time delay in three CGMs — https://sites.bu.edu/bionicpancreas/files/2017/05/Sinha-CGM-lag-analysis-JDST-2017.pdf
13. Fibrotic encapsulation and CGM delays (Diabetes 2019) — https://diabetesjournals.org/diabetes/article/68/10/1892/35372/
14. Mensh 2013, CGM performance and sleeping position — https://pubmed.ncbi.nlm.nih.gov/23911167
15. Kulzer et al. 2024, Nocturnal hypoglycemia in the CGM era — https://journals.sagepub.com/doi/full/10.1177/19322968241267823
16. UMich, CGM reading low at night — https://teamdynamix.umich.edu/TDClient/210/DepressionCenter/KB/Article/10689/
17. Smartwatch-based nocturnal hypo detection (2025) — https://pmc.ncbi.nlm.nih.gov/articles/PMC11851596/
18. Schultes 2007, Defective awakening response — https://journals.plos.org/plosmedicine/article?id=10.1371%2Fjournal.pmed.0040069
19. IAH prevalence cross-sectional study — https://pmc.ncbi.nlm.nih.gov/articles/PMC11178233/
20. Predictors of IAH and severe hypo — https://pmc.ncbi.nlm.nih.gov/articles/PMC12434435
21. NCT02714972 protocol (dead-in-bed figure) — https://cdn.clinicaltrials.gov/large-docs/72/NCT02714972/Prot_000.pdf
22. Diabetes UK, Driving — https://www.diabetes.org.uk/living-with-diabetes/life-with-diabetes/driving
23. gov.uk, Diabetes: assessing fitness to drive — https://gov.uk/guidance/diabetes-mellitus-assessing-fitness-to-drive
24. DVLA INS186 — https://assets.publishing.service.gov.uk/media/690b4eb688a98da87e292310/ins186_a_guide_for_drivers_with_insulin_treated_diabetes.pdf
25. ADA 2024, Diabetes and Driving — https://diabetesjournals.org/care/article/47/11/1889/157262/
26. Turner 2001 via the JDN alcohol systematic review — https://diabetesonthenet.com/journal-diabetes-nursing/effects-of-alcohol-on-blood-glucose-levels-in-people-with-type-1-diabetes-a-systematic-review/
27. Menstrual cycle AGP study (JCEM 2022) — https://pubmed.ncbi.nlm.nih.gov/35869507/
28. Menstrual cycle systematic review 2023 — https://www.ncbi.nlm.nih.gov/pmc/articles/PMC9962060/
29. Basal insulin and travel across time zones — https://pmc.ncbi.nlm.nih.gov/articles/PMC7962589/
30. Stress and CGM metrics (figshare) — https://figshare.com/articles/online_resource/.../25612011
31. DAN, Guidelines for diabetes and recreational diving — https://dan.org/health-medicine/health-resource/health-safety-guidelines/guidelines-for-diabetes-and-recreational-diving/
32. Alert Diver Europe, Diving with diabetes — https://alertdiver.eu/en_US/articles/diving-with-diabetes-guidelines-and-latest-research/
33. SPUMS extract (Pro Dive Cairns) — https://prodivecairns.com/pdf/Suggested-assesment-for-divers-with-Diabetes-EXTRACT-SPUMS-diving-medical-Guideline.pdf
34. Australian Diabetes Society, diving appendix — https://www.diabetessociety.com.au/documents/ADS_Diving_Diabetes_2016_Appendices_Final.pdf
35. Garmin Descent Mk3 manual, Connect IQ — https://www8.garmin.com/manuals/webhelp/GUID-9183E86B-2399-4CFC-AB50-EAFC6D6ED326/EN-US/GUID-C3289B5E-1A70-4BB2-A7F0-9B16CF60D75D.html
36. Liu 2017, Stigma in T1D/T2D — https://diabetesjournals.org/clinical/article/35/1/27/35423/
37. Browne 2014, "I'm not a druggie" — https://pmc.ncbi.nlm.nih.gov/articles/PMC4120421/
38. Tanenbaum 2017 summary (HealthExec) — https://healthexec.com/topics/healthcare-management/healthcare-economics/barriers-utilizing-diabetes-devices
39. Adolescents and CGM (PHCFM 2023) — https://phcfm.org/index.php/phcfm/article/view/3879/6164
40. Stigma in adolescents and young adults — https://www.ncbi.nlm.nih.gov/pmc/articles/PMC5935805/
41. "Keeping safe", CGM alarms with IAH — https://www.researchgate.net/publication/318613732
42. CGM alarms embarrassment, T2D Medicaid — https://pmc.ncbi.nlm.nih.gov/articles/PMC11223994/
43. Sehgal 2024, DIY-CGM qualitative — https://onlinelibrary.wiley.com/doi/10.1111/dme.15168
44. JMIR mHealth 2024, Smartwatches in diabetes care — https://mhealth.jmir.org/2024/1/e54826/PDF
45. Lin 2020, CGM alarm settings — https://pmc.ncbi.nlm.nih.gov/articles/PMC6977942/
46. Alarm-fatigue review 2025 — https://pmc.ncbi.nlm.nih.gov/articles/PMC12731774/
47. Messer 2023, CITY trial CGM features — https://www.ncbi.nlm.nih.gov/pmc/articles/PMC12016964/
48. Shivers 2013, "Turn it off!" — https://doi.org/10.1177/193229681300700324
49. Dexcom Connect IQ store page — https://apps.garmin.com/en-US/apps/027db46d-fbc8-4362-a65f-05fa6c0f113f
50. Garmin forum, Dexcom G7 data field — https://forums.garmin.com/apps-software/mobile-apps-web/f/connect-iq-store-ios/329932/
51. Healthline, Garmin and Dexcom — https://www.healthline.com/diabetesmine/garmin-and-dexcom-diabetes-data
52. Dexcom, Direct to Watch FAQ — https://www.dexcom.com/en-us/faqs/what-g7-features-can-i-view-on-my-apple-watch-when-in-direct-to-watch-mode
53. Dexcom, Urgent Low Soon FAQ — https://www.dexcom.com/en-us/faqs/how-does-the-urgent-low-soon-alert-work
54. Dexcom G7, Signal loss FAQ — https://www.dexcom.com/en-sa/faqs/g7/why-am-i-getting-signal-loss
55. Abbott Libre apps — https://www.freestyle.abbott/en-us/products/freestyle-libre-app.html
56. Diabetech, Libre Apple Watch app — https://www.diabetech.info/p/freestyle-libre-app-gets-apple-watch-app-ai-food-assistant
57. xDrip+ WatchGuide — https://github.com/NightscoutFoundation/xDrip/blob/master/Documentation/WatchGuide.md
58. xDrip+ and AAPS GitHub issues: #303, #881, #1137, #1317, #1467, #3581 (xDrip); #2271, #3414, #4619, #5155 (AAPS)
59. AAPS Wear OS docs and Custom Watchface Reference — https://androidaps.readthedocs.io/en/latest/ExchangeSiteCustomWatchfaces/CustomWatchfaceReference.html
60. AAPS BaseWatchFace.kt (staleness logic) — https://github.com/nightscout/AndroidAPS/blob/master/wear/src/main/kotlin/app/aaps/wear/watchfaces/utils/BaseWatchFace.kt
61. urchin-cgm — https://github.com/longlandm/urchin-cgm
62. Nightscout wearable docs — https://nightscout.readthedocs.io/en/latest/nightscout/wearable.html
63. Nightscout Clock — https://github.com/ktomy/nightscout-clock
64. Diabetech, SugarPixel — https://www.diabetech.info/p/sugarpixel-the-cgm-display-with-customizable-alerts-how-it-works
65. Juggluco changelog and help — https://www.juggluco.nl/Juggluco/changes.html
66. GlucoDataHandler repo and issues #194, #226, #230, #259, #295 — https://github.com/pachi81/GlucoDataHandler
67. swissalpine xDrip/Spike/Nightscout Garmin face and store page — https://github.com/swissalpine/GARMIN-xDrip-Spike-Nightscout-Watchface
68. CGM+ FAQ — https://watchface.io/docs/cgm/faq
69. Sugar Sense Garmin setup and the5krunner article — https://sugarsense.io/garmin-watch-glucose-setup
70. GGlance — https://gglance.app/
71. LoopDocs, Apple Watch — https://loopkit.github.io/loopdocs/operation/features/watch/
72. TrioDocs, Smart Watch, and Trio PR #1723 — https://triodocs.org/configuration/settings/devices/smart-watch/
73. Gluroo watch face and stale-reading support page — https://gluroo.com/watchface/
74. GlucoseGlance (Apple complication refresh) — https://github.com/dgsmith/GlucoseGlance
75. Dexcom G6 App Store listing — https://apps.apple.com/us/app/dexcom-g6/id1209262925?mt=8
76. Drug Topics, predictive alarm in adolescents — https://www.drugtopics.com/view/real-time-cgm-predictive-alarm-improves-glycemic-control-in-adolescents-with-t1d
77. Dawn summary of vibration-wake studies [vendor] — https://dawnbands.com/pages/do-vibration-alarms-work
78. GoodVibes, wrist vibration recognition — https://arxiv.org/pdf/2406.01738
