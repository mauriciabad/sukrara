// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.pen

import app.mauri.sukrara.core.model.PenAnswer
import app.mauri.sukrara.core.model.PenEntryInfo
import app.mauri.sukrara.core.model.PenInfo
import app.mauri.sukrara.core.model.PenNearEntry
import app.mauri.sukrara.core.model.PenQuestionInfo
import app.mauri.sukrara.core.model.PenReadUndo
import app.mauri.sukrara.core.model.PenSessionInfo
import app.mauri.sukrara.core.model.PenTaps
import app.mauri.sukrara.core.model.PensPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Settings › Insulin pens over made-up pens (the screens' goldens): every action does nothing. */
class FakePensPort(
    pens: List<PenInfo> = emptyList(),
    val sessionsOf: List<PenSessionInfo> = emptyList(),
    questions: List<PenQuestionInfo> = emptyList(),
    var tapsNow: PenTaps = PenTaps(blocked = false, nfcOff = false, notifyBlocked = false),
    val entries: Map<String, PenEntryInfo> = emptyMap(),
) : PensPort {
    override val pens = MutableStateFlow(pens)
    override val questions = MutableStateFlow(questions)

    override fun sessions(serial: String): Flow<List<PenSessionInfo>> = pens.map { sessionsOf.filter { it.serial == serial } }

    override suspend fun answer(
        question: PenQuestionInfo,
        answer: PenAnswer,
    ) = false

    override suspend fun entry(id: String) = entries[id]

    override suspend fun rename(
        serial: String,
        name: String,
    ) = Unit

    override suspend fun setKind(
        serial: String,
        kind: String,
    ) = Unit

    override suspend fun opened(serial: String) = Unit

    override suspend fun forget(serial: String) = Unit

    override suspend fun logDoses(
        serial: String,
        doses: Map<String, Boolean>,
    ) = false

    override suspend fun sessionEntry(
        serial: String,
        t: Long,
    ): PenNearEntry? = null

    override suspend fun joinEntry(
        serial: String,
        doses: Map<String, Boolean>,
        id: String,
    ) = false

    override suspend fun match(
        serial: String,
        t: Long,
        units: Double,
    ): PenNearEntry? = null

    override suspend fun correct(
        shown: PenEntryInfo,
        primed: List<Boolean>,
    ): PenEntryInfo? = null

    override suspend fun restore(was: PenEntryInfo) = false

    override suspend fun undoRead(serial: String) = PenReadUndo.Gone

    override suspend fun checkPassed(
        serial: String,
        readAt: Long,
    ) = false

    override suspend fun checkFailed(serial: String) = Unit

    override suspend fun checkAgain(serial: String) = Unit

    override fun taps() = tapsNow

    override fun openTagSettings() = true

    override fun openNfcSettings() = Unit

    override fun openNotifySettings() = Unit

    override fun openJugglucoInfo() = Unit
}
