// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.readaloud

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.Resources
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.graph.A11y
import app.mauri.sukrara.graph.CardSurface
import app.mauri.sukrara.graph.CardWords
import app.mauri.sukrara.graph.cardModelOf
import app.mauri.sukrara.graph.stats.Bands
import app.mauri.sukrara.monitor.R
import java.time.Instant
import java.util.Locale

/**
 * *Read glucose aloud* (#159): THE glucose sentence ([A11y.value], surface `read-aloud`: the value with its unit, the
 * trend, the range, the age, then the alert or mute the card's pill shows) said by the phone's text-to-speech voice in
 * Sukrara's language, on demand, without looking at the screen. Started from the app shortcut ([publish]: the
 * launcher's long-press menu, TalkBack's actions on the app icon, a shortcut pinned to the home screen); started again
 * while it speaks, it stops. It plays on the accessibility stream, like TalkBack's own voice.
 */
class ReadAloud(
    private val context: Context,
) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null

    /** Each start: a late callback of an earlier engine does nothing. */
    private var generation = 0

    /** Speaking (or about to): the next [toggle] stops. Main thread only. */
    var speaking = false
        private set

    /** Says [text] in [locale], or stops when it is speaking. Main thread. */
    fun toggle(
        text: String,
        locale: Locale,
    ) {
        if (speaking) return stop()
        stop()
        speaking = true
        val gen = ++generation
        tts = TextToSpeech(context.applicationContext) { status -> main.post { onInit(gen, status, text, locale) } }
    }

    private fun onInit(
        gen: Int,
        status: Int,
        text: String,
        locale: Locale,
    ) {
        val engine = tts?.takeIf { gen == generation } ?: return
        if (status != TextToSpeech.SUCCESS || engine.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) {
            Log.w(TAG, "no voice: status $status")
            stop()
            Toast.makeText(context, R.string.read_aloud_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        engine.language = locale
        engine.setAudioAttributes(
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit

                override fun onDone(id: String?) {
                    main.post { if (gen == generation) stop() }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    main.post { if (gen == generation) stop() }
                }
            },
        )
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE)
    }

    /** Stops speaking and lets the engine go. Main thread. */
    fun stop() {
        generation++
        speaking = false
        tts?.let {
            runCatching { it.stop() }
            runCatching { it.shutdown() }
        }
        tts = null
    }

    /**
     * The app shortcut *Read glucose aloud*, in the current language (again after a language change): in the
     * launcher's long-press menu, as an action on the app icon for TalkBack, and pinnable to the home screen.
     */
    fun publish() {
        val shortcuts = context.getSystemService(ShortcutManager::class.java) ?: return
        val info =
            ShortcutInfo
                .Builder(context, SHORTCUT_ID)
                .setShortLabel(context.getString(R.string.read_aloud_short))
                .setLongLabel(context.getString(R.string.read_aloud_long))
                .setIcon(Icon.createWithResource(context, R.drawable.monitor_ic_read_aloud_shortcut))
                .setIntent(Intent(context, ReadAloudActivity::class.java).setAction(ACTION))
                .build()
        runCatching { shortcuts.pushDynamicShortcut(info) }.onFailure { Log.w(TAG, "shortcut: ${it.javaClass.simpleName}") }
    }

    /** The shortcut was used: the launcher ranks it (and predicts it) by that. */
    fun used() {
        runCatching { context.getSystemService(ShortcutManager::class.java)?.reportShortcutUsed(SHORTCUT_ID) }
    }

    companion object {
        private const val TAG = "Sukrara.readaloud"
        private const val UTTERANCE = "glucose"
        const val SHORTCUT_ID = "read_glucose_aloud"
        const val ACTION = "app.mauri.sukrara.action.READ_GLUCOSE_ALOUD"

        /** What is said for [state] at [nowS], in [res]' language: THE sentence with the age and the pill's words. */
        fun sentence(
            state: GlucoseState,
            bands: Bands,
            unit: GlucoseUnit,
            nowS: Long,
            res: Resources,
        ): String {
            val model = cardModelOf(state, emptyList(), Instant.ofEpochSecond(nowS), CardWords.of(res))
            return A11y.value(model, bands, CardSurface.READ_ALOUD, unit)
        }
    }
}
