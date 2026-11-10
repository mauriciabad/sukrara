// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.pen

import android.nfc.Tag
import android.util.Log
import android.view.View
import app.mauri.sukrara.core.model.PenFill
import app.mauri.sukrara.pen.opennov.NotAPenException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * *Next time, just tap your pen* (after rapid insulin typed by hand while a pen is read): at most once a day and three
 * times in all ([shown] so far, the last on epoch [day]).
 */
@Serializable
data class PenTapHint(
    val shown: Int = 0,
    val day: Long = -1,
) {
    fun due(today: Long): Boolean = shown < MAX && day != today

    fun shownOn(today: Long): PenTapHint = PenTapHint(shown + 1, today)

    companion object {
        const val MAX = 3
    }
}

/**
 * A pen held to the phone while Add treatment is open: the same read as a tap from any app (in the module's scope, so
 * closing the dialog never cancels a read or a write half way; the same haptics and the *Pen reads* notification),
 * then what the dialog shows: the newest injection the read logged, or the card's words.
 */
internal class PenDialogRead(
    private val module: PensModule,
) {
    suspend fun read(
        tag: Tag,
        view: View,
        reading: () -> Unit,
    ): PenFill? =
        module.scope
            .async {
                module.ready()
                if (!module.availableNow()) return@async null
                val text = PenCardText(view.context)
                var selected = false
                var last = 0f
                val res =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            PenTag.read(
                                tag,
                                onSelected = {
                                    selected = true
                                    module.pens.startedReading()
                                    view.post {
                                        reading()
                                        PenHaptics.play(view, PenHaptics.Moment.Tap)
                                    }
                                },
                                progress = { p ->
                                    module.pens.progressed(p)
                                    if (p > last) view.post { PenHaptics.play(view, PenHaptics.Moment.Segment) }
                                    last = p
                                },
                                stopBefore = { s -> runBlocking { module.pens.stopBefore(s) } },
                            )
                        }
                    }
                val read = res.getOrNull()
                val failed = { PenFill.Said(text.card(PenReadState(PenReadState.Stage.Failed)).title, error = true) }
                when (PenTag.outcome(selected, res.exceptionOrNull() ?: if (read == null) NotAPenException() else null)) {
                    PenTag.Outcome.NotPen -> {
                        return@async null
                    }

                    PenTag.Outcome.Failed -> {
                        module.pens.failed()
                        view.post { PenHaptics.play(view, PenHaptics.Moment.Failure) }
                        return@async failed()
                    }

                    PenTag.Outcome.Pen -> {
                        Unit
                    }
                }
                val st =
                    runCatching { module.pens.onRead(read!!) }
                        .onFailure { Log.e(TAG, "pen read not applied", it) }
                        .getOrElse {
                            module.pens.failed()
                            view.post { PenHaptics.play(view, PenHaptics.Moment.Failure) }
                            return@async failed()
                        } ?: return@async null
                runCatching { module.notices.read(st, module.notifyAfterRead(), module.pens.canUndo(st.undo)) }
                val newest = st.entries.maxByOrNull { it.t }
                view.post { PenHaptics.play(view, if (newest != null) PenHaptics.Moment.Success else PenHaptics.Moment.Nothing) }
                if (newest == null) {
                    PenFill.Said(text.card(st).title, error = false)
                } else {
                    PenFill.Logged(newest.k, newest.a, newest.pr ?: 0.0, newest.t, also = st.entries.size - 1)
                }
            }.await()

    private companion object {
        const val TAG = "SukraraPen"
    }
}
