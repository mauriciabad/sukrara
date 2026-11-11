// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import app.mauri.sukrara.core.model.NavTargets
import app.mauri.sukrara.core.model.PenFill
import app.mauri.sukrara.core.model.PenNearEntry
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * The quick Add treatment dialog over whatever is on screen: a tap on the glucose notification, a home-screen widget
 * or the Quick Settings tile opens this instead of the app. Translucent, no task of its own in Recents; when this
 * phone can't add entries (no role yet, a follower without "Can add entries") it opens the app instead.
 *
 * Over the lock screen an entry needs no unlock, but the dialog closes by itself, unsaved, after *Time to save*
 * ([LockCountdown]) so a stray touch in a pocket can't log anything; the screen turning off closes it at once.
 * *Unlock* pauses the countdown for the unlock prompt (success: an ordinary dialog, every input kept). What the
 * countdown closes on is kept as a draft ([TreatmentDraft]) and offered on the next unlock and the next time this opens.
 *
 * After Save the dialog goes and a snackbar at the bottom says what was saved, with *Undo* ([SavedSnackbar]); the
 * window then covers only the snackbar and lets touches elsewhere through, so the app behind stays usable.
 *
 * With [NavTargets.EXTRA_TREATMENT_ID] (a row of home's list) it edits that stored entry instead: its kind's amount,
 * its time and its note, Save changes it (a newer version, synced) and *Undo* brings the old one back.
 *
 * On the wearer's phone with pens (reading On, unlocked) the dialog holds the NFC reader: a pen held to the phone is
 * read and logged at once like a tap anywhere ([DialogPen]); its newest injection fills Rapid / Long-acting (counting
 * up) and *When*, under the fields *Logged from your pen* with *Undo*. *Save* never logs that dose again; a field
 * changed afterwards is a typed dose again (and asks *Already logged by your pen?*).
 */
class QuickTreatmentActivity : ComponentActivity() {
    private val module by lazy { treatmentsModule }
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private var countdown by mutableStateOf<LockCountdown?>(null)
    private var now by mutableLongStateOf(0L)
    private var warnings by mutableIntStateOf(0)

    /** The one *Closes in 10 seconds* was due (TalkBack hears it once, politely). */
    private var announced by mutableStateOf(false)
    private var unlocking = false
    private var form = TreatmentFormState()

    /** The countdown left before a configuration change: a recreated activity never gets more time. */
    private var carriedMs: Long? = null
    private var saved = false

    /** The stored entry this dialog edits (home's list), once read; null = adding (or still reading it). */
    private var editing by mutableStateOf<Treatment?>(null)
    private val editId by lazy { intent.getStringExtra(NavTargets.EXTRA_TREATMENT_ID) }

    /** The form holds the kept draft (when it was kept), with *Discard*; null = an ordinary form. */
    private var draftAt by mutableStateOf<Instant?>(null)

    /** Look for a kept draft once the form exists (a fresh dialog only). */
    private var offerDraft = false

    /** After Save: the dialog leaves, then [confirmation] shows the snackbar. */
    private var leaving by mutableStateOf(false)
    private var confirmation by mutableStateOf<Confirmation?>(null)

    /** What the snackbar will say once the dialog has left. */
    private var pending: Confirmation? = null

    private val screen =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    // put away: discard, no toast (a late broadcast after the screen came back on is ignored)
                    Intent.ACTION_SCREEN_OFF -> if ((countdown != null || leaving) && !interactive()) finish()

                    // unlocked on the lock screen itself (fingerprint) while the dialog was up
                    Intent.ACTION_USER_PRESENT -> endLock()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!module.canAdd()) {
            openApp()
            return
        }
        registerReceiver(
            screen,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            RECEIVER_NOT_EXPORTED,
        )
        enableEdgeToEdge()
        val focusCarbs = QuickTreatmentIntents.focusCarbs(intent) || module.lowNow()
        val focusLong = !focusCarbs && QuickTreatmentIntents.focusLong(intent)
        // *Add treatment* after a pen read: at that injection's time (within the current minute it is simply now)
        val startAt = QuickTreatmentIntents.at(intent)?.takeIf { Duration.between(it, module.clock.now()) >= Duration.ofMinutes(1) }
        carriedMs = savedInstanceState?.getLong(KEY_LEFT, -1L)?.takeIf { it >= 0 }
        // a question open before a rotation is asked again (Save was already pressed for it)
        duplicate = PenDuplicate.restore(savedInstanceState)
        saved = duplicate != null
        // a field filled by a pen read stays the pen's after a rotation (its Undo stays with the read card)
        penLine = PenFills.restore(savedInstanceState)
        draftAt = savedInstanceState?.getLong(KEY_DRAFT, -1L)?.takeIf { it >= 0 }?.let(Instant::ofEpochMilli)
        setContent {
            val edited = editing
            // editing: nothing until the entry is read (a moment), then its own form
            if (editId != null && edited == null) return@setContent
            form = if (edited != null) rememberEditFormState(edited) else rememberTreatmentFormState(startAt)
            SukraraTheme {
                val c = confirmation
                if (c != null) {
                    ConfirmationLayer(c)
                } else {
                    FormLayer(focusCarbs, focusLong)
                }
            }
        }
        editId?.let { id ->
            lifecycleScope.launch {
                val t = module.editable(id)
                if (t == null) {
                    // deleted meanwhile, or no longer this phone's to change
                    Toast.makeText(this@QuickTreatmentActivity, R.string.treatment_edit_gone, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    editing = t
                }
            }
        }
        // the kept draft fills a fresh form (a recreated one keeps what is typed); never an edit's
        offerDraft = savedInstanceState == null && editId == null
    }

    @Composable
    private fun FormLayer(
        focusCarbs: Boolean,
        focusLong: Boolean,
    ) {
        val cd = countdown
        val quiet by module.hints.quiet.collectAsState(QuietRules.NONE)
        val reduced = LocalReducedMotion.current
        LaunchedEffect(cd) { if (cd != null) runCountdown(cd) }
        PenFillEffects()
        LaunchedEffect(Unit) {
            if (!offerDraft) return@LaunchedEffect
            offerDraft = false
            val draft = module.draft() ?: return@LaunchedEffect
            if (form.hasInput || leaving) return@LaunchedEffect
            form.fill(draft.form())
            draftAt = draft.keptAt
        }
        // after Save: the scrim fades and the card leaves, then the window shrinks to the snackbar
        val scrim by animateFloatAsState(if (leaving) 0f else SCRIM_ALPHA, tween(if (reduced) 0 else LEAVE_MS), label = "scrim")
        LaunchedEffect(leaving) {
            if (!leaving) return@LaunchedEffect
            delay(if (reduced) 0L else LEAVE_MS.toLong())
            pending?.let(::confirm)
        }
        // M3 dialog scrim; a tap on it dismisses, like outside a dialog
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrim))
                // a tap outside dismisses (TalkBack users have Cancel and back; no unlabeled node)
                .pointerInput(Unit) { detectTapGestures { finish() } }
                .safeDrawingPadding()
                .imePadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                !leaving,
                enter = fadeIn(snap()),
                exit = fadeOut(tween(if (reduced) 0 else LEAVE_MS)) + scaleOut(tween(if (reduced) 0 else LEAVE_MS), targetScale = 0.92f),
            ) {
                TreatmentDialogContent(
                    form = form,
                    focusCarbs = focusCarbs,
                    zone = module.clock.zone,
                    now = module.clock::now,
                    onSave = ::save,
                    onCancel = ::finish,
                    // over the lock screen Unlock takes Open Sukrara's place; opened from the app (an edit too), the
                    // app is already behind it
                    onOpenApp = if (cd == null && editId == null && !QuickTreatmentIntents.fromApp(intent)) ::openApp else null,
                    lock = cd?.let { lockInfo(it) },
                    quiet = quiet,
                    draft = draftAt?.let { DraftLine(it, ::discardDraft) },
                    focusLong = focusLong,
                    penLine =
                        penLine?.let { line ->
                            { PenFillLine(line, showHint = form.rapid.isBlank() && form.long.isBlank(), onUndo = ::undoPen) }
                        },
                    // the card swallows taps so they don't reach the scrim
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures { } },
                )
            }
            duplicate?.let { d ->
                PenDuplicateDialog(d.pen, onCancel = { cancelDuplicate() }, onLog = { store(d.entries, d.at) })
            }
        }
    }

    /**
     * The snackbar after Save. It goes by itself after [SNACK_MS] (raised to Android's *Time to take action*, longer
     * with TalkBack), [UNDONE_MS] after an Undo; back closes it at once.
     */
    @Composable
    private fun ConfirmationLayer(c: Confirmation) {
        LaunchedEffect(c) {
            val ms = if (c.undo == null) UNDONE_MS else SNACK_MS
            val a11y = getSystemService(AccessibilityManager::class.java)
            val flags = AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS
            // raised for TalkBack and *Time to take action*, never shorter than its own time
            delay(maxOf(ms, a11y.getRecommendedTimeoutMillis(ms, flags)).toLong())
            finish()
        }
        val reduced = LocalReducedMotion.current
        val shown = remember { MutableTransitionState(reduced) }.apply { targetState = true }
        Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(
                shown,
                enter = slideInVertically(tween(if (reduced) 0 else SNACK_IN_MS)) { it } + fadeIn(tween(if (reduced) 0 else LEAVE_MS)),
            ) { SavedSnackbar(c, onUndo = ::undo, modifier = Modifier.widthIn(max = 560.dp)) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (unlocking || isFinishing) return
        if (keyguard.isKeyguardLocked && countdown == null) {
            startLock()
        } else if (!keyguard.isKeyguardLocked) {
            endLock()
        }
        startPenReader()
    }

    override fun onPause() {
        stopPenReader()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        countdown?.let { outState.putLong(KEY_LEFT, it.remaining(SystemClock.elapsedRealtime())) }
        duplicate?.save(outState)
        (penLine as? PenLine.Filled)?.let { PenFills.save(it, outState) }
        draftAt?.let { outState.putLong(KEY_DRAFT, it.toEpochMilli()) }
    }

    override fun onStop() {
        super.onStop()
        // the screen went off in lock mode (doesn't wait for the broadcast); after Save there's nothing left to do
        if ((countdown != null || leaving) && !interactive()) finish()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screen) }
        super.onDestroy()
    }

    /**
     * A typed insulin dose a pen already logged: the entries and time waiting for *Log anyway*. While it is asked the
     * lock-screen countdown stands still (the dialog must not close under the question); *Cancel* resumes it.
     */
    private var duplicate by mutableStateOf<PenDuplicate?>(null)

    /** Whether the lock-screen countdown stands still for *Already logged by your pen?* (tests). */
    internal val countdownPaused: Boolean get() = countdown?.paused == true

    private fun save(
        typed: List<NewEntry>,
        at: java.time.Instant?,
    ) {
        if (saved) return
        editing?.let { t ->
            saved = true
            storeEdit(t, typed.single(), at)
            return
        }
        // the dose a pen read filled in is in the log already: Save never logs it again
        val entries = PenFills.withoutFilled(typed, penLine, form)
        if (entries.isEmpty()) {
            finish()
            return
        }
        saved = true
        lifecycleScope.launch {
            val d = module.penDuplicate(entries, at)
            if (d != null) {
                duplicate = PenDuplicate(entries, d.second, at)
                countdown?.pause(SystemClock.elapsedRealtime())
            } else {
                store(entries, at)
            }
        }
    }

    /** *Cancel* on *Already logged by your pen?*: back to the form, nothing saved. */
    private fun cancelDuplicate() {
        duplicate = null
        countdown?.resume(SystemClock.elapsedRealtime())
        saved = false
    }

    private fun store(
        entries: List<NewEntry>,
        at: java.time.Instant?,
    ) {
        duplicate = null
        val outbox =
            module.sync.writer.value
                ?.throughOutbox == true
        val saved = module.save(entries, at)
        if (saved == null) {
            finish()
            return
        }
        if (draftAt != null) module.dropDraft()
        val text =
            getString(if (outbox) R.string.treatment_sent_snack else R.string.treatment_saved_snack, EntryWords.of(resources, entries))
        // rapid insulin typed by hand while a pen is read: once a day, three times, the quicker way
        val hint = tapHint && entries.any { it.kind == TreatmentKind.RapidInsulin }
        if (hint) module.pen()?.tapHintShown()
        leave(Confirmation(if (hint) getString(R.string.treatment_saved_tap_hint, text) else text, saved))
    }

    /** Save on an edit: [t] becomes [entry] at [at]; the snackbar's *Undo* brings [t] back. */
    private fun storeEdit(
        t: Treatment,
        entry: NewEntry,
        at: Instant?,
    ) {
        val saved = module.edit(t, entry, at)
        if (saved == null) {
            finish()
            return
        }
        leave(Confirmation(getString(R.string.treatment_changed_snack, EntryWords.one(resources, entry)), saved))
    }

    /** The dialog leaves (the countdown ends with it, the pen reader and the keyboard go), then [c] shows. */
    private fun leave(c: Confirmation) {
        endLock()
        stopPenReader()
        pending = c
        WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        leaving = true
    }

    /**
     * The snackbar shows: the window shrinks to it at the bottom and lets every touch outside it through
     * (`FLAG_NOT_TOUCH_MODAL`), so whatever is behind can be used at once.
     */
    private fun confirm(c: Confirmation) {
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM)
        confirmation = c
    }

    /** *Undo*: what this Save stored is taken back (a newer deleted version of each); the snackbar says so and goes. */
    private fun undo() {
        val c = confirmation ?: return
        c.undo?.undo()
        confirmation = Confirmation(getString(R.string.treatment_undone), null)
    }

    /** *Discard* on the kept draft: the form empties and the draft (and its notification) is dropped. */
    private fun discardDraft() {
        form.fill(TreatmentFormState())
        draftAt = null
        module.dropDraft()
    }

    /** What the dialog shows of the pen (null = pens can't be read into it now). */
    private var penLine by mutableStateOf<PenLine?>(null)

    /** A read that logged a dose, waiting to be counted into its field. */
    private var pendingFill by mutableStateOf<PenFill.Logged?>(null)
    private var penReader: DialogPen? = null
    private var tapHint = false

    /** Holds the NFC reader while the dialog is open, unlocked, and pens can be read (Android reads tags unlocked only). */
    private fun startPenReader() {
        if (editId != null || leaving) return
        val pen = module.pen() ?: return
        lifecycleScope.launch {
            val ok = runCatching { pen.readable() }.getOrDefault(false)
            tapHint = ok && runCatching { pen.tapHintDue() }.getOrDefault(false)
            val nfc = NfcAdapter.getDefaultAdapter(this@QuickTreatmentActivity)
            if (!ok || nfc == null || keyguard.isKeyguardLocked || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
            if (penLine == null) penLine = PenLine.Ready
            penReader = pen
            runCatching {
                nfc.enableReaderMode(this@QuickTreatmentActivity, { tag -> onTag(pen, tag) }, READER_FLAGS, null)
            }
        }
    }

    private fun stopPenReader() {
        if (penReader == null) return
        penReader = null
        runCatching { NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this) }
    }

    /** A tag while the reader is held (a binder thread): a pen is read and logged, anything else is left alone. */
    private fun onTag(
        pen: DialogPen,
        tag: Tag,
    ) {
        if (!pen.mightBePen(tag)) return
        val view = window.decorView
        lifecycleScope.launch {
            val fill = runCatching { pen.read(tag, view) { penLine = PenLine.Reading } }.getOrNull()
            if (fill == null) {
                if (penLine == PenLine.Reading) penLine = PenLine.Ready
            } else {
                penRead(fill)
            }
        }
    }

    /** What a pen read brought into the dialog (also the tests' way in: Robolectric and the emulator have no NFC). */
    internal fun penRead(fill: PenFill) {
        when (fill) {
            is PenFill.Logged -> pendingFill = fill
            is PenFill.Said -> penLine = PenLine.Said(fill.text, fill.error)
        }
    }

    /**
     * A logged dose counts into its field in the pen dial's 0.5u steps (at once with reduced motion), *When* moves to
     * its time, then the line says it is logged; a field changed afterwards is typed again; the read's words go after 3 s.
     */
    @Composable
    private fun PenFillEffects() {
        val reduced = LocalReducedMotion.current
        LaunchedEffect(pendingFill) {
            val f = pendingFill ?: return@LaunchedEffect
            val set: (String) -> Unit = if (f.kind == "long") ({ form.long = it }) else ({ form.rapid = it })
            if (!reduced) {
                val steps = (f.units * 2).toInt().coerceIn(1, MAX_COUNT_STEPS)
                for (k in 1 until steps) {
                    set(PenFills.number(Math.round(f.units * k / steps * 2) / 2.0))
                    delay(COUNT_MS / steps)
                }
            }
            val text = PenFills.number(f.units)
            set(text)
            form.at = Instant.ofEpochSecond(f.t).takeIf { Duration.between(it, module.clock.now()) >= Duration.ofMinutes(1) }
            form.ago = null
            penLine = PenLine.Filled(f, text)
            pendingFill = null
        }
        LaunchedEffect(form.rapid, form.long) {
            val l = penLine as? PenLine.Filled ?: return@LaunchedEffect
            if (pendingFill == null && PenFills.fieldText(l, form) != l.text) penLine = PenLine.Ready
        }
        val line = penLine
        LaunchedEffect(line) {
            if (line is PenLine.Said) {
                delay(SAID_MS)
                if (penLine === line) penLine = if (penReader != null) PenLine.Ready else null
            }
        }
    }

    /** *Undo* under the fields: the read back (refused, and said, once something changed since). */
    private fun undoPen() {
        val l = penLine as? PenLine.Filled ?: return
        val pen = module.pen() ?: return
        lifecycleScope.launch {
            if (runCatching { pen.undo() }.getOrDefault(false)) {
                if (PenFills.fieldText(l, form) == l.text) PenFills.setField(l.fill.kind, form, "")
                if (form.at?.epochSecond == l.fill.t) form.at = null
                window.decorView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                penLine = PenLine.Said(getString(R.string.treatment_pen_undone))
            } else {
                window.decorView.performHapticFeedback(HapticFeedbackConstants.REJECT)
                penLine = PenLine.Said(getString(R.string.treatment_pen_undo_failed), error = true)
            }
        }
    }

    /**
     * The countdown as the dialog shows it: the digits and TalkBack's 5 s steps recompose only when they change; the
     * ring reads the time in its draw (every frame, stepped once a second with reduced motion).
     */
    @Composable
    private fun lockInfo(cd: LockCountdown): LockInfo {
        val seconds by remember(cd) { derivedStateOf { cd.wholeSeconds(now) } }
        val spoken by remember(cd) { derivedStateOf { cd.spokenSeconds(now) } }
        val reduced = LocalReducedMotion.current
        return LockInfo(seconds, ::unlock, spoken, { cd.fraction(now, stepped = reduced) }, warnings, announced)
    }

    private suspend fun runCountdown(cd: LockCountdown) {
        cd.firstFrame(SystemClock.elapsedRealtime())
        var warned = cd.warning(SystemClock.elapsedRealtime())
        while (countdown === cd && !cd.stopped) {
            now = SystemClock.elapsedRealtime()
            if (!warned && cd.warning(now)) {
                warned = true
                // the last 5 s: the ring pulses once; one haptic tick only when something was typed (in a pocket, none)
                warnings++
                if (form.hasInput) window.decorView.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
            }
            if (!announced && cd.announceDue(now)) announced = true
            if (cd.expired(now)) {
                // nothing is saved (no haptic: from a pocket it means nothing). Entries that could be saved are kept as a
                // draft, offered after the unlock; anything else typed only gets the toast
                val draft = TreatmentDraft.of(form, module.clock.now())?.takeIf { form.canSave && editId == null }
                if (draft != null) {
                    module.keepDraft(draft, EntryWords.of(resources, form.entries.orEmpty()))
                    Toast.makeText(this, R.string.treatment_lock_draft_kept, Toast.LENGTH_LONG).show()
                } else if (form.hasInput) {
                    Toast.makeText(this, R.string.treatment_lock_not_saved, Toast.LENGTH_SHORT).show()
                }
                finish()
                return
            }
            // every frame: the ring empties smoothly (its draw reads [now]; the dialog recomposes only for the digits)
            withFrameMillis { }
        }
    }

    private fun startLock() {
        val seconds = module.lockSeconds.value.toInt()
        val a11y = getSystemService(AccessibilityManager::class.java)
        val recommended =
            a11y.getRecommendedTimeoutMillis(
                seconds * 1000,
                AccessibilityManager.FLAG_CONTENT_CONTROLS or AccessibilityManager.FLAG_CONTENT_TEXT,
            )
        val start = SystemClock.elapsedRealtime()
        now = start
        val full = LockCountdown.duration(seconds, recommended.toLong(), a11y.isTouchExplorationEnabled)
        val left = carriedMs?.coerceAtMost(full) ?: full
        carriedMs = null
        announced = false
        countdown =
            LockCountdown(full, start - (full - left)).also {
                if (left < full) it.firstFrame(start - (full - left))
                if (duplicate != null) it.pause(start)
            }
    }

    private fun endLock() {
        countdown?.stop()
        countdown = null
        // unlocked: a pen can be read into the dialog now
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && penReader == null && !leaving) startPenReader()
    }

    private fun unlock() {
        val cd = countdown ?: return
        cd.pause(SystemClock.elapsedRealtime())
        unlocking = true
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    unlocking = false
                    endLock()
                }

                override fun onDismissCancelled() = resumeAfterPrompt()

                override fun onDismissError() = resumeAfterPrompt()
            },
        )
    }

    private fun resumeAfterPrompt() {
        unlocking = false
        if (duplicate == null) countdown?.resume(SystemClock.elapsedRealtime())
        if (!keyguard.isKeyguardLocked) endLock()
    }

    private fun interactive() = getSystemService(PowerManager::class.java).isInteractive

    /** Opens the app (after the unlock prompt when locked) and closes the dialog. */
    private fun openApp() {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launch == null) {
            finish()
            return
        }
        if (keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        startActivity(launch)
                        finish()
                    }
                },
            )
        } else {
            startActivity(launch)
            finish()
        }
    }

    private companion object {
        const val KEY_LEFT = "lock_left_ms"
        const val KEY_DRAFT = "draft_kept_ms"
        const val SCRIM_ALPHA = 0.32f

        /** The dialog's way out after Save, before the snackbar slides in. */
        const val LEAVE_MS = 180
        const val SNACK_IN_MS = 250

        /** How long the snackbar offers Undo (a short snackbar's 4 s), and how long *Undone* stays. */
        const val SNACK_MS = 4_000
        const val UNDONE_MS = 1_500
        const val READER_FLAGS =
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        const val MAX_COUNT_STEPS = 20
        const val COUNT_MS = 350L
        const val SAID_MS = 3000L
    }
}

/**
 * *Already logged by your pen?*: a pen entry of insulin is this close to the typed dose. *Cancel* goes back to the form;
 * *Log anyway* saves what was typed (a second injection is the person's call).
 */
@Composable
internal fun PenDuplicateDialog(
    pen: PenNearEntry,
    onCancel: () -> Unit,
    onLog: () -> Unit,
) {
    val amount = (if (pen.units % 1.0 == 0.0) "${pen.units.toLong()}" else String.format(java.util.Locale.ROOT, "%.1f", pen.units)) + "u"
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.treatment_pen_duplicate_title)) },
        text = {
            Text(
                stringResource(
                    R.string.treatment_pen_duplicate_text,
                    amount,
                    app.mauri.sukrara.graph.ClockText
                        .time(pen.t),
                ),
            )
        },
        confirmButton = { TextButton(onClick = onLog) { Text(stringResource(R.string.treatment_pen_duplicate_log)) } },
        dismissButton = { Button(onClick = onCancel) { Text(stringResource(R.string.treatment_cancel)) } },
    )
}
