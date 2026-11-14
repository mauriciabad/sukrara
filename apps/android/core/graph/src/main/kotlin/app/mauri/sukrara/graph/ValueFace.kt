// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import app.mauri.sukrara.graph.stats.Bands
import kotlin.math.max
import kotlin.math.min

/**
 * The value's place on a card: the number + trend arrow and under it the late time and / or the status pill (a fresh
 * reading shows no time at all: the PoC's look, #181); or, instead of a value, the warm-up, "Sensor ended" with its "Replace sensor" pill (once an ended sensor's readings
 * stop; until then the value with the "Ended · Replace sensor" pill), or "No reading since" with the "no readings"
 * icon. One per [GlucoseCard.draw] call.
 */
internal class ValueFace(
    private val model: CardModel,
    private val fr: Freshness,
    private val bands: Bands,
    private val pal: GraphPalette,
    private val d: Float,
    private val sensor: SensorBadge.Look?,
    private val rec: CardLayout,
    private val motion: CardMotion?,
) {
    private val w = model.words
    private val nowS = model.nowS

    /** What the pills sit on ([CardStyle.ground]; null = the palette's surface). */
    private val ground = rec.style.ground

    companion object {
        /** The largest a warming-up sensor's words are drawn (dp): the size of "Sensor ended", never a value's. */
        const val WARMUP_TEXT_DP = 20f

        /** With a pill the number keeps at least this share of its size without it (contracts/graph "The value with a pill"). */
        const val PILL_KEEP = 0.85f

        /** A row under the number only while the number's box keeps this height (dp; as before #36). */
        const val MIN_ROW_NUMBER_DP = 26f

        /** A pill row's gap under the number's baseline, in pill heights (digits have no descent). */
        const val ROW_GAP = 0.15f
    }

    /** Draw into [box] with the status pill [look] (null = none) and the value in [ink] (null = its range colour). */
    fun draw(
        cv: Canvas,
        box: RectF,
        look: StatusBadge.Look?,
        ink: Int?,
    ) {
        val small =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink ?: pal.inkSoft
                textSize = max(11 * d, box.height() * 0.17f)
            }
        val face = GlucoseCard.face(model.latest, nowS, sensor, rec.surface, rec.style.unit, w.locale)
        when (face.state) {
            GlucoseLook.State.WARMING_UP -> drawWarmup(cv, box, checkNotNull(sensor), small)

            // an ended sensor says so as soon as it has ended, instead of a value or "No reading since" (not an error)
            GlucoseLook.State.ENDED -> drawEnded(cv, box)

            GlucoseLook.State.NO_READINGS, GlucoseLook.State.NONE -> drawNone(cv, box, face, small)

            // a sensor past its end whose readings keep arriving: the value, and its pill when no alert's takes the place
            GlucoseLook.State.VALUE -> drawNumber(cv, box, face, look ?: StatusBadge.sensor(face, w), ink, small)
        }
    }

    /** "Sensor ended" in the secondary colour and under it the "Replace sensor" pill (icon-only beside it when short). */
    private fun drawEnded(
        cv: Canvas,
        box: RectF,
    ) {
        val pill = StatusBadge.replaceSensor(w)
        val badgeH = (box.height() * 0.24f).coerceIn(14 * d, 24 * d)
        val row = box.height() - badgeH * 1.25f >= 20 * d
        val textBox = if (row) RectF(box.left, box.top, box.right, box.bottom - badgeH * 1.25f) else box
        val p =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pal.inkSoft
                typeface = Typeface.create(Typeface.DEFAULT, 600, false)
                textSize = min(textBox.height() * 0.5f, 20 * d)
            }
        val iconRoom = if (row) 0f else badgeH * 1.3f

        // the checkered flag before the words: the status bar's "sensor ended" glyph
        fun flagW() = p.textSize * 1.25f
        val tw = p.measureText(w.sensorEnded) + flagW()
        if (tw > box.width() - iconRoom) p.textSize *= ((box.width() - iconRoom) / tw).coerceAtLeast(0.45f)
        val base = textBox.centerY() + p.textSize * 0.36f
        val fs = p.textSize * 1.2f
        SensorBadge.flag(cv, box.left - fs * 0.2f, base - p.textSize * 0.72f - (fs - p.textSize * 0.72f) / 2, fs, pal.inkSoft)
        val textW = flagW() + p.measureText(w.sensorEnded)
        cv.drawText(w.sensorEnded, box.left + flagW(), base, p)
        rec.add("value.ended", RectF(box.left, textBox.top, box.left + textW, textBox.bottom))
        if (row) {
            val top = textBox.bottom + badgeH * 0.25f
            val pw = StatusBadge.draw(cv, box.left, top, badgeH, box.width(), pill, pal.dark, ground)
            rec.add("value.pill", RectF(box.left, top, box.left + pw, top + badgeH), "[row]")
        } else {
            val x = box.left + textW + badgeH * 0.3f
            if (x + badgeH <= box.right) {
                StatusBadge.draw(cv, x, box.centerY() - badgeH / 2, badgeH, badgeH, pill, pal.dark, ground)
                rec.add("value.pill", RectF(x, box.centerY() - badgeH / 2, x + badgeH, box.centerY() + badgeH / 2), "[icon]")
            } else {
                rec.add("value.pill", info = "(no room)")
            }
        }
    }

    /**
     * A new sensor warming up, labelled so it can never pass for a value (#27): the disc filling and "Warming up · 42m"
     * ("Almost ready" past the warm-up's end) at the size of "Sensor ended" (at most [WARMUP_TEXT_DP], never the
     * number's), and under it "Ready at 19:45" / "Waiting for the first reading" when there is room. A box too narrow
     * for one line puts the time left on its own line under the words, at the same size.
     */
    private fun drawWarmup(
        cv: Canvas,
        box: RectF,
        look: SensorBadge.Look,
        small: Paint,
    ) {
        val s = look.warmup ?: return
        val waiting = look.waiting(nowS)
        val title = if (waiting) w.almostReady else "${w.warmingUp} · $s"
        val line = look.warmupLine(nowS, w)
        val p =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pal.inkSoft
                typeface = Typeface.create(Typeface.DEFAULT, 600, false)
            }
        val sub = Paint(small).apply { textSize = min(small.textSize, 15 * d) }
        val subH = sub.textSize * 1.3f
        val withSub = box.height() - subH >= 20 * d
        val textBox = if (withSub) RectF(box.left, box.top, box.right, box.bottom - subH) else box
        p.textSize = min(textBox.height() * 0.5f, WARMUP_TEXT_DP * d)

        // the disc before the words, as the flag before "Sensor ended"
        fun iconW() = p.textSize * 1.3f
        val one = iconW() + p.measureText(title)
        val lines =
            if (one <= box.width() || waiting || box.width() / one >= 0.6f) {
                if (one > box.width()) p.textSize *= (box.width() / one).coerceAtLeast(0.45f)
                listOf(title)
            } else {
                // two lines: the words, then the time left under them (never alone at a bigger size)
                p.textSize = min(textBox.height() / 2.6f, WARMUP_TEXT_DP * d)
                val widest = iconW() + max(p.measureText(w.warmingUp), p.measureText(s))
                if (widest > box.width()) p.textSize *= (box.width() / widest).coerceAtLeast(0.45f)
                listOf(w.warmingUp, s)
            }
        val lh = p.textSize * 1.25f
        if (withSub && sub.measureText(line) > box.width()) sub.textSize *= box.width() / sub.measureText(line)
        // the words and the line under them as one group, centred in the box
        val groupH = lines.size * lh + if (withSub) sub.textSize * 1.3f else 0f
        val top = box.centerY() - groupH / 2
        val base = top + lh / 2 + p.textSize * 0.36f
        val ds = p.textSize * 1.05f
        SensorBadge.disc(cv, box.left, base - p.textSize * 0.36f - ds / 2, ds, look, pal)
        val x = box.left + iconW()
        lines.forEachIndexed { i, t -> cv.drawText(t, x, base + i * lh, p) }
        val textW = lines.maxOf { p.measureText(it) }
        rec.add("value.warmup", RectF(box.left, base - p.textSize, x + textW, base), s)
        rec.add("value.warmup.text", info = title)
        if (withSub) {
            cv.drawText(line, box.left, top + lines.size * lh + sub.textSize * 1.05f, sub)
            if (!waiting) rec.add("time.text", info = line)
        }
    }

    /** No reading yet / none for 20 min: the "no readings" icon (only with a last reading) and the words. */
    private fun drawNone(
        cv: Canvas,
        box: RectF,
        face: GlucoseLook,
        small: Paint,
    ) {
        val r = model.latest
        val big =
            Paint(small).apply {
                textSize = min(box.height() * 0.26f, 20 * d)
                typeface =
                    Typeface.create(Typeface.DEFAULT, 600, false)
            }
        val line1 = if (r == null) w.noReadingsYet else w.noReadingSince
        val line2 = r?.let { Freshness.timeText(it.t, nowS) }

        fun iconW() = if (r != null) big.textSize * (2.1f + 0.35f) else 0f
        val widest = listOfNotNull(line1, line2).maxOf { big.measureText(it) }
        if (widest + iconW() > box.width() - 8 * d && widest > 0) big.textSize *= max(0.4f, (box.width() - 8 * d) / (widest + iconW()))
        val y = box.centerY() - if (line2 == null) -big.textSize / 3 else big.textSize * 0.15f
        val icon = if (r != null) big.textSize * 2.1f else 0f
        if (r != null) Glyphs.drawSymbol(cv, Glyphs.noReadings, box.left, y - big.textSize * 0.95f, icon, pal.inkSoft)
        val x = box.left + if (r != null) icon + big.textSize * 0.35f else 0f
        cv.drawText(line1, x, y, big)
        if (line2 != null) {
            cv.drawText(line2, x, y + big.textSize * 1.25f, big)
            rec.add("time.text", info = line2)
        }
        rec.add(
            "value.none",
            RectF(
                box.left,
                y - big.textSize,
                box.right,
                y + (
                    if (line2 !=
                        null
                    ) {
                        big.textSize * 1.25f
                    } else {
                        0f
                    }
                ),
            ),
            face.state.name,
        )
    }

    /**
     * The number + arrow (or the "no trend" ring), and the pill and / or the late time. A pill costs the number little
     * (contracts/graph README "The value with a pill", the owner 2026-11-14: "it's fine to decrease it a little"): the
     * number keeps [PILL_KEEP] of its size without the pill or more, in this order: a row right under the number's
     * baseline when the number keeps its whole size (the digits have no descent: the gap is [ROW_GAP] of the pill), else
     * beside the arrow when its words fit there, else the row under a number made smaller down to [PILL_KEEP], else
     * beside with its icon (the number down to [PILL_KEEP] if needed), else under a number as large as the row allows
     * (while its box keeps [MIN_ROW_NUMBER_DP]), else it is left out.
     */
    private fun drawNumber(
        cv: Canvas,
        box: RectF,
        face: GlucoseLook,
        look: StatusBadge.Look?,
        ink: Int?,
        small: Paint,
    ) {
        val r = checkNotNull(model.latest)
        // an old value is always grey: no surface ink or animation colour can make it look current
        val color = if (fr.strike) pal.inkSoft else ink ?: motion?.color ?: pal.at(bands, r.sgv)
        rec.add(
            "value.color",
            info =
                when {
                    fr.strike -> "grey"
                    r.sgv >= bands.extremelyHigh -> "EXTREMELY_HIGH"
                    else -> bands.of(r.sgv).name
                },
            exact = true,
        )
        val badgeH = (box.height() * 0.24f).coerceIn(14 * d, 24 * d)
        // the range in words under an out-of-range value, never by colour alone (#26): in the pill's row, after it;
        // not where the place around the card already says it ([CardStyle.rangeSaid]: the notification's title)
        val range = if (rec.style.rangeSaid) null else RangeState.shown(face, r.sgv, bands, look)
        val rangeText = range?.let(w::range)
        val pill = look != null || range != null
        val timeRow = if (fr.showTime) small.textSize * 1.25f else 0f
        val num =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                typeface = RoundedFont.typeface(700)
                isStrikeThruText = fr.strike
            }
        val s = checkNotNull(face.text)

        // the number's size in a box [h] tall, fitted to the width too: number + arrow stay left of the slots
        fun sized(h: Float): Float {
            num.textSize = h * 1.18f
            repeat(3) {
                val n = num.measureText(s) + num.textSize * ValueGlyphs.MARK_BOX * 1.1f
                if (n > box.width()) num.textSize *= box.width() / n * 0.995f
            }
            return num.textSize
        }
        // as without a pill (the late time on its own row)
        val full = sized(box.height() - timeRow)
        // a row right under the baseline: the number as large as without the pill, smaller only to fit the row
        val rowSize = min(full, (box.height() - badgeH * (1 + ROW_GAP)) / ValueGlyphs.CAP)
        // what is left beside the number + arrow at [size] for the pill's icon at least
        val iconH = min((box.height() - timeRow) * 0.5f, 24 * d)

        // beside: the pill's icon at least, or the range word alone
        val besideNeed = if (look != null) iconH else rangeText?.let { RangeTag.width(it, min(iconH, 20 * d)) } ?: Float.MAX_VALUE

        fun besideRoom(size: Float) = box.width() - (num.apply { textSize = size }.measureText(s) + size * ValueGlyphs.MARK_BOX * 1.2f)
        // beside with words: the pill's short words (a range word alone is words)
        val wordsNeed = look?.let { StatusBadge.width(it, iconH, pal.dark, short = true) } ?: besideNeed
        val rowFits = rowSize >= MIN_ROW_NUMBER_DP * 1.18f * d
        val mode =
            when {
                !pill -> PillMode.NONE

                // the number as without the pill: under it
                rowFits && rowSize >= full * 0.999f -> PillMode.ROW

                // the number as without the pill: beside it, with words
                besideRoom(full) >= wordsNeed -> PillMode.BESIDE

                // the number a little smaller (never under PILL_KEEP): under it
                rowFits && rowSize >= full * PILL_KEEP * 0.999f -> PillMode.ROW

                besideRoom(full) >= besideNeed -> PillMode.BESIDE

                besideRoom(full * PILL_KEEP) >= besideNeed -> PillMode.BESIDE_SMALLER

                // the last resort: under a number as large as the row allows
                rowFits -> PillMode.ROW

                else -> PillMode.NONE
            }
        val badgeRow = mode == PillMode.ROW
        num.textSize =
            when (mode) {
                PillMode.ROW -> {
                    rowSize
                }

                // as large as leaves the pill its room (text widths scale with the size)
                PillMode.BESIDE_SMALLER -> {
                    val perSize = num.apply { textSize = full }.measureText(s) / full + ValueGlyphs.MARK_BOX * 1.2f
                    ((box.width() - besideNeed) / perSize).coerceIn(full * PILL_KEEP, full)
                }

                else -> {
                    full
                }
            }
        val capH = num.textSize * ValueGlyphs.CAP
        // the number's box: the whole box but the time's own row; in a row the number and the pill as one group, centred
        val numBox =
            when (mode) {
                PillMode.ROW -> {
                    val top = box.top + (box.height() - (capH + badgeH * (1 + ROW_GAP))) / 2
                    RectF(box.left, top, box.right, top + capH)
                }

                else -> {
                    RectF(box.left, box.top, box.right, box.bottom - timeRow)
                }
            }
        val iconBadge = look != null && !badgeRow && besideRoom(num.textSize) >= iconH
        val cy = numBox.centerY()
        val baseY = cy + capH / 2
        val x0 = box.left - num.textSize * 0.04f
        val prev = motion?.prev
        if (prev != null && prev != s &&
            motion.roll < 1f
        ) {
            rollNumber(cv, s, prev, motion, x0, baseY, capH, num)
        } else {
            cv.drawText(s, x0, baseY, num)
        }
        rec.add(
            "value.number",
            RectF(box.left, cy - capH / 2, box.left + num.measureText(s), cy + capH / 2),
            if (fr.strike) "$s struck" else s,
        )
        val x = box.left + num.measureText(s) + num.textSize * ValueGlyphs.MARK_GAP
        val arrowSize = num.textSize * ValueGlyphs.MARK_BOX
        val markBox = RectF(x, cy - arrowSize / 2, x + arrowSize, cy + arrowSize / 2)
        val rate = r.rate
        val band = r.band
        if (face.trend == TrendMark.ARROW && rate != null && band != null) {
            cv.save()
            cv.translate(x + arrowSize / 2, cy)
            cv.rotate(motion?.arrowDeg ?: TrendAngle.deg(rate))
            ValueGlyphs.arrow(
                cv,
                arrowSize * ValueGlyphs.ARROW_IN_BOX,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                },
                TrendAngle.isDouble(band),
            )
            cv.restore()
            rec.add("value.trend", markBox, if (TrendAngle.isDouble(band)) "arrow double" else "arrow single")
        } else {
            // no trend (no rate, or struck through): the hollow ring in the arrow's place
            cv.drawCircle(
                x + arrowSize / 2,
                cy,
                TrendMark.ringRadius(arrowSize),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    style = Paint.Style.STROKE
                    strokeWidth = TrendMark.ringStroke(arrowSize)
                },
            )
            rec.add("value.trend", markBox, "ring")
        }
        val time = if (fr.showTime) Freshness.timeText(r.t, nowS) else null
        if (badgeRow) {
            val top = baseY + badgeH * ROW_GAP
            var rx = box.left
            if (look != null) {
                // the pill keeps room for the range word: its short words or its icon rather than no word
                val keep = rangeText?.let { RangeTag.width(it, badgeH) + badgeH * 0.35f } ?: 0f
                val maxW = if (box.width() - keep >= badgeH) box.width() - keep else box.width()
                val bw = StatusBadge.draw(cv, box.left, top, badgeH, maxW, look, pal.dark, ground)
                rec.add("value.status", RectF(box.left, top, box.left + bw, top + badgeH), "${look.kind} [row]")
                rx += bw + badgeH * 0.35f
            }
            if (range != null && rangeText != null) {
                if (rx + RangeTag.width(rangeText, badgeH) <= box.right) {
                    val b = RangeTag.draw(cv, rx, top, badgeH, rangeText, color, pal, ink, ground)
                    rec.add("value.range", b, "${range.key} [row]")
                    rx = b.right + badgeH * 0.35f
                } else {
                    rec.add("value.range", info = "${range.key} [row] (no room)")
                }
            }
            if (time != null) {
                // the late time shares the row
                val t = Paint(small).apply { textSize = badgeH * 0.56f }
                if (rx + t.measureText(time) <= box.right) {
                    cv.drawText(time, rx, top + badgeH / 2 + t.textSize * 0.36f, t)
                    rec.add("value.time", RectF(rx, top, rx + t.measureText(time), top + badgeH), "[badge row]")
                    rec.add("time.text", info = time)
                } else {
                    rec.add("value.time", info = "[badge row] (no room)")
                }
            }
            return
        }
        // no row: the pill (its words where they fit, else its icon), then the range word, beside the arrow
        val ih = iconH
        var ix = x + arrowSize * 1.15f
        if (iconBadge && look != null) {
            // the range word keeps its room when the pill can still say something shorter
            val keep = rangeText?.let { RangeTag.width(it, min(ih, 20 * d)) + ih * 0.3f } ?: 0f
            val room = (box.right - ix).let { if (it - keep >= ih) it - keep else it }
            val bw = StatusBadge.draw(cv, ix, cy - ih / 2, ih, room, look, pal.dark, ground)
            rec.add("value.status", RectF(ix, cy - ih / 2, ix + bw, cy + ih / 2), "${look.kind} [beside]")
            ix += bw + ih * 0.3f
        } else if (look != null) {
            rec.add("value.status", info = "${look.kind} (no room)")
        }
        if (range != null && rangeText != null) {
            val th = min(ih, 20 * d)
            if (ix + RangeTag.width(rangeText, th) <= box.right) {
                rec.add("value.range", RangeTag.draw(cv, ix, cy - th / 2, th, rangeText, color, pal, ink, ground), "${range.key} [beside]")
            } else {
                rec.add("value.range", info = "${range.key} (no room)")
            }
        }
        if (time != null) {
            cv.drawText(time, box.left, baseY + small.textSize * 1.25f, small)
            rec.add("value.time", RectF(box.left, baseY, box.left + small.measureText(time), baseY + small.textSize * 1.25f), "[own row]")
            rec.add("time.text", info = time)
        }
    }

    /** Where the pill goes ([drawNumber]). */
    private enum class PillMode { NONE, ROW, BESIDE, BESIDE_SMALLER }

    /** Each digit that changed slides out and the new one in (up for a rising value), clipped to the number's line. */
    private fun rollNumber(
        cv: Canvas,
        s: String,
        prev: String,
        m: CardMotion,
        x0: Float,
        baseY: Float,
        capH: Float,
        num: Paint,
    ) {
        val lineH = capH * 1.6f
        val dir = if (m.up) 1f else -1f
        val e = m.roll.coerceIn(0f, 1f)
        cv.save()
        cv.clipRect(x0 - capH, baseY - capH * 1.3f, x0 + num.measureText(s) + capH, baseY + capH * 0.3f)
        val old = Paint(num)
        for (i in s.indices) {
            val x = x0 + num.measureText(s, 0, i)
            val o = prev.getOrNull(prev.length - (s.length - i))
            if (o == s[i]) {
                cv.drawText(s[i].toString(), x, baseY, num)
                continue
            }
            cv.drawText(s[i].toString(), x, baseY + (1 - e) * lineH * dir, Paint(num).apply { alpha = (255 * e).toInt() })
            if (o != null) {
                old.alpha = (255 * (1 - e)).toInt()
                cv.drawText(o.toString(), x, baseY - e * lineH * dir, old)
            }
        }
        cv.restore()
    }
}
