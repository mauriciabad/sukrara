# 37 — The treatment list: design

*2026-11-14 · design note · issue #207 · Material 3 guidelines, Google Photos' help, the docs and manuals of
AndroidAPS, xDrip+, Juggluco, LibreLink, mySugr, Dexcom G7, Gluroo, Tidepool, user forums · synthetic data only*

## The person

Home's *Last 24 hours* is checked in a hurry: "did I already bolus for this?", "how much did I eat at lunch?", "why
did I go high after that?". The answer needs the kind at a glance, the amount and time, and the glucose then. The
owner (#207) found the list unprofessional: a bold trash icon on every row, a cloud on every row although everything
is backed up, no link to the chart's visual language, *· from the pen* wrapping the line, no way to see more, and
no glucose context.

## Decisions

| # | Decision | Why |
|---|---|---|
| 1 | **No trash button.** *(Round 1; changed in round 2 below: no long-press menu, one swipe direction.)* Delete by swiping the row; TalkBack: a *Delete* custom action; *Undo* on the snackbar (no confirmation dialog). | M3: swipe reveals actions on list items [gestures](https://m3.material.io/foundations/interaction/gestures); a swipe must have a TalkBack/Switch Access alternative [Android a11y principles](https://developer.android.com/guide/topics/ui/accessibility/principles), done with `customActions` [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics); undo beats confirm for a reversible action [snackbar](https://m3.material.io/components/snackbar/guidelines), [dialogs](https://m3.material.io/components/dialogs/guidelines). |
| 2 | Swipe feedback: the row slides over a surface-tone strip with the delete icon; once letting go would delete, the strip turns error-container, the icon grows, and a `GESTURE_THRESHOLD_ACTIVATE` haptic fires (`…_DEACTIVATE` going back). A delete the log refuses slides back after 1.5 s. Reduced motion: colours and size snap. | The motion says what will happen before it happens; a fast accidental swipe is the known risk ([Cronometer forum](https://forums.cronometer.com/discussion/comment/18330)) and *Undo* stays available. |
| 3 | **LibreView mark only when it says something**: none once the entry is in LibreView; ☁↑ while it waits or updates; ☁̸ in the error colour when the upload stopped; a ☁✓ that pops in for **3 s** when an entry gets there while the list is on screen. Each mark still opens the LibreView page. | Google Photos: no icon = backed up, an icon only while uploading / not backed up, "recently backed up only show Cloud check for 3 seconds" ([Google Photos help](https://support.google.com/photos/answer/6193313?hl=en&co=GENIE.Platform%3DAndroid)); Gmail likewise marks only *Queued* mail ([Mailmeteor](https://mailmeteor.com/blog/gmail-queued)). A writer's *Sending…* / *Not sent* + *Retry* already worked that way and stays. |
| 4 | **Kind badge**: the chart's marker (▼ rapid, ▽ long, ● carbs, ◆ note, `TreatmentMarks.marker`) in the kind's tonal ink on a 36 dp circle of its tonal tint, the colours of the card's tinted pills. Shared composable in `:core:graph` (`TreatmentKindMark` / `TreatmentKindBadge`); the Add treatment dialog's labels use the same one. | One visual language from chart to dialog to list; M3: "a leading icon should provide a quick visual cue that relates to the item's label" ([lists](https://m3.material.io/components/lists/guidelines)); mySugr and LibreLink also give each entry kind its own symbol ([mySugr manual](https://assets.mysugr.com/app_logbook/ios/3.147.1/manual/us/en/logbook-ios-User_Manual-en.pdf)). |
| 5 | **Source as an icon**: a 16 dp pen icon after the amount (TalkBack *from the pen*) instead of *· from the pen*. | Keeps the headline on one line; the words stay for TalkBack. |
| 6 | Two-line rows: *4.5u rapid insulin* / *14:05 · 12m · Pasta* (the Treatments page drops the *12m*: the day is its header). A note's headline is its text. | M3 lists: headline + 1–3 lines of supporting text; the old single line wrapped. |
| 7 | **Glucose then** at the row's end: the reading nearest the entry's time **within ±5 min** (the earlier on a tie), as the card writes it (`Freshness.valueText`: LO / HI, the unit) with the card's arrow for that reading's trend (`ValueGlyphs.trendIcon`; the ring without one). None when no reading is that close. TalkBack: *glucose then 142 milligrams per decilitre, rising*. | 5 min = one reading interval of a 5-min sensor, and the age from which the card strikes a value. The trend is the reading's own `Reading.trend` (THE trend from `TrendReadings`, docs/34); nothing new works one out. LibreLink's logbook, mySugr's entries and Dexcom's *Event Insights* put glucose beside the entry ([LibreLink IFU](https://freestyleserver.com/Payloads/IFU/2023/q3/ART48221-001_rev-A.pdf), [Dexcom](https://www.dexcom.com/en-us/all-access/dexcom-cgm-explained/new-g7-app-features)). The upload mark sits before it so the values line up. |
| 8 | **See all** in the section header → the **Treatments page**: the last 31 days grouped by day (sticky *Today* / *Yesterday* / *Mon, Nov 10* headers), the same rows, edit, pen sheet, delete and *Undo*, *Add treatment*, a footer saying older entries are in the export / LibreView / Tidepool. The section also shows when only older entries exist (*Nothing logged in the last 24 hours* + *See all*). | Users want the history ([diabetes.co.uk thread](https://www.diabetes.co.uk/forum/threads/recommendations-please-for-blood-sugar-insulin-logbook-apps.208161/)); AAPS and Dexcom have a history screen. 31 days = what home already holds for the chart (entries and readings), so every row can have its glucose. No M3 component for "See all": a text button at the header's end is the common pattern. |
| 9 | Motion: rows leave and come back with the list resizing (`animateContentSize` / `animateItem`), the check pops in with a spring, all through `Motion` specs (snap with *Remove animations*); confirm haptic on delete. | CLAUDE.md "Make it juicy", honouring reduced motion. |

## Kept as it was

The delete and edit paths themselves: `HomeViewModel.delete` (tombstone through the treatment log, LibreView removal
and its snackbar wording, alert quiets recomputed from the log), `undoDelete`, the treatment dialog's edit, the pen
sheet, *Sending…* / *Not sent* / *Retry*. Only how they are reached changed.

## Not done

- Day totals (units, grams) in the page's headers: useful, not asked; later.
- Glucose colour by range on the rows: the card's range colours would compete with the kind colours; the number stays
  neutral.
- Asking before a sheet with unsaved changes is swiped away (M3 full-screen dialogs ask; the dialog never did either).
- A "recently deleted" list: *Undo* for 10 s and the sheet's *Delete* cover it; no one asked to recover later.

## Round 2: the owner's feedback (2026-11-14)

The owner tried round 1 on the phone: a tap opened a dialog for typed entries and a sheet for pen entries; notes
couldn't be added to pen entries; the swipe's *Undo* went unnoticed; the long-press menu looked unprofessional and had
no icons. Research (search extracts of m3.material.io, Google help pages, NN/g):

| Question | Options | Chosen | Why |
|---|---|---|---|
| What opens on a tap | dialog · bottom sheet · full-screen page | **The app's bottom sheet** (`SukraraSheet`, `:core:designsystem`) for every entry: a typed entry's edit form, a pen entry's doses, and *Add treatment* from home. Outside the app (notification, widget, tile, lock screen) the floating dialog stays. The quiet sheet, the pen question and Doses read sheets moved onto the same component. | M3: a modal bottom sheet is "an alternative to inline menus or simple dialogs" on phones, dialogs are "purposefully interruptive" and for decisions ([sheets](https://m3.material.io/components/bottom-sheets/guidelines), [dialogs](https://m3.material.io/components/dialogs/guidelines)); Calendar creates events in a bottom sheet ([9to5](https://9to5google.com/2019/05/16/google-calendar-bottom-sheet/)), Photos shows details in a sheet ([9to5](https://9to5google.com/2025/08/25/google-photos-viewer-redesign-android/)). A full page (Tasks) is heavier than a 1–2 field form needs. Over another app or the lock screen a sheet would look like system UI and the countdown layout is the dialog's. Confirmations (forget a pen, remove a follower) stay dialogs. |
| Preventing a wrong delete | confirmation dialog · undo snackbar · swipe reveals a button · bin | **Undo snackbar, made noticeable**: it names the entry (*Deleted: 4.5u rapid insulin*), stays **10 s** (was 4 s: Compose's *Short*), longer with TalkBack / *Time to take action*; the same on home and the Treatments page and after the sheet's *Delete*. **One swipe direction** (towards the start) past 40 %, with a threshold haptic. | NN/g: confirm only what can't be undone, otherwise "go to great lengths to provide undo"; routine confirmations get clicked through ([NN/g](https://www.nngroup.com/articles/confirmation-dialog/)); Gmail and Keep swipe with *Undo* ([MUO](https://www.makeuseof.com/tag/customize-gmail-swipe-gestures/)). M2: snackbars last 4–10 s, one with an action doesn't time out under TalkBack ([M2 snackbars](https://m2.material.io/components/snackbars)); M3 Flutter stopped auto-dismissing action snackbars ([Flutter](https://docs.flutter.dev/release/breaking-changes/snackbar-with-action-behavior-update)). The owner missed the 4 s generic *Treatment deleted*: too short and it didn't say which row went. A two-step swipe (iOS) has no Google precedent; a bin adds screens nobody asked for. |
| Reusable | — | `SwipeToDelete` and `showUndo` in `:core:designsystem`. Other deletable rows in the app: followers (*Remove* can't be undone: needs a new invite) and pens (*Forget*) keep their confirmation dialogs (M3/NN/g: confirm the irreversible); graph lines are chips with ×; no other list deletes rows. Every undo snackbar (deletes, *Tip closed*, pen corrections) uses `showUndo`. | |
| The long-press menu | menu with icons · ⋮ button per row · actions in the sheet | **Removed**; the sheet holds every action, each with an icon (*Delete* with the bin, *Cancel* with ×, *Save* / *Done* with a check). Long press does nothing. | M3: "long pressing a list item selects it" ([gestures](https://m3.material.io/foundations/interaction/gestures)); M1 / Holo: not for context menus ([M1](https://www.mdui.org/en/design/1/patterns/gestures.html)); Contacts, Messages and Gmail use it for selection. A ⋮ per row adds a control to every row for what one tap already opens. |
| Notes on pen entries | — | **The pen sheet's note is a field** where the phone may change the entry, labelled like the Add treatment form's (the note marker, *Pizza, exercise, sick…* while empty; read only: the marker and the text); *Save* stores it through the treatment log (a newer version; the pen keys kept, contracts/treatments SPEC §1 already allows it, no wire change); *Entry changed* with *Undo*. | Any entry carries `n`; only the UI was missing. A later read of the same session keeps the note (`PenEngine` copies the stored entry). |

**LibreView and Tidepool after an edit** (checked in code and tests): an edit or delete of any entry, typed or from
the pen, goes to both. LibreView: the old record is retracted and the new version sent (`LibreViewUploaderTest`
*edits retract the old version*); it carries `units` = injected units and a note's text only for a note entry (its
format has no note on insulin or carbs), never `pr`. Tidepool: the datum is sent again under the same origin id
(`TidepoolUploaderTest` *edits go up again*), with the entry's note in `notes` (*a note added to an insulin entry goes
up in its datum*); a note entry is a Tidepool note, edited and removed with it. Pen entries go up once their session
closes (`UploadHold`).

Pictures (synthetic data): [`docs/img/treatments`](img/treatments).
