// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LocalAppWidgetOptions
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import app.mauri.sukrara.core.model.LockBackground
import app.mauri.sukrara.core.model.LockWidgetLink
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.graph.A11y
import app.mauri.sukrara.graph.CardSurface
import app.mauri.sukrara.graph.GlucoseCard
import app.mauri.sukrara.graph.GraphPalette
import app.mauri.sukrara.graph.LockHole
import app.mauri.sukrara.graph.RoundedFont
import app.mauri.sukrara.graph.lockWidgetBoxes
import app.mauri.sukrara.graph.lockWidgetCardStyle
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * The lock-screen widget (#66, docs/17): a registered card surface (`lock-widget`) that System UI's module hosts in the
 * empty lower part of the lock screen, around the fingerprint icon. Not a home-screen widget: no launcher or hub offers
 * it (`hide_from_picker`, keyguard only), the module binds it itself and tells it where the lock icon is
 * ([LockWidgetLink] options). The card's pieces in three boxes ([lockWidgetBoxes]): the value left of the icon, the
 * slots right of it, the chart below, drawn only by the renderer in the home widgets' palette of its appearance, on a
 * transparent background (the module draws the card, frosted glass or dim behind it; the shadow is drawn here). A tap
 * does what a tap on a home widget does ([WidgetTap]): Add treatment shows over the lock screen without unlocking.
 */

/** The share of the RemoteViews bitmap limit the picture may use (one size: the module reports exactly one). */
private const val LOCK_SHARE_OF_LIMIT = 1.5f / 2f

/** Where the lock icon is, from the module's options; before they arrive (or without them) a centred one. */
fun lockHole(
    options: Bundle,
    widthDp: Float,
): LockHole {
    val centred = LockHole.centred(widthDp)
    return LockHole(
        options.getFloat(LockWidgetLink.HOLE_LEFT, centred.left),
        options.getFloat(LockWidgetLink.HOLE_RIGHT, centred.right),
        options.getFloat(LockWidgetLink.HOLE_BOTTOM, centred.bottom),
    )
}

/**
 * The lock-screen widget's picture, its one entry: the card for [frame] around the lock icon [hole], at [wDp] × [hDp]
 * dp, in the palette of its appearance with a transparent background, and the shadow when that's its background.
 */
fun renderLockWidget(
    context: Context,
    wDp: Float,
    hDp: Float,
    hole: LockHole,
    frame: WidgetFrame,
): Bitmap {
    RoundedFont.init(context)
    val metrics = context.resources.displayMetrics
    val density = metrics.density
    val maxPixels = metrics.widthPixels.toFloat() * metrics.heightPixels * LOCK_SHARE_OF_LIMIT
    val d = density * min(1f, sqrt(maxPixels / (wDp * density * hDp * density)))
    val bmp =
        Bitmap.createBitmap((wDp * d).roundToInt().coerceAtLeast(16), (hDp * d).roundToInt().coerceAtLeast(16), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val model = widgetModel(context, frame)
    val style = lockWidgetCardStyle(frame.config)
    val dark = frame.lock.appearance.dark(frame.dark)
    // transparent: the halo of the chart's end dot takes the background colour
    val palette = GraphPalette.of(dark).copy(bg = 0)
    val fontScale = context.resources.configuration.fontScale
    for ((part, box) in lockWidgetBoxes(bmp.width.toFloat(), bmp.height.toFloat(), hole, d, frame.lock.background.boxed)) {
        canvas.save()
        canvas.translate(box.left, box.top)
        canvas.clipRect(0f, 0f, box.width(), box.height())
        try {
            GlucoseCard.draw(
                canvas,
                box.width(),
                box.height(),
                model,
                style,
                palette,
                d,
                CardSurface.LOCK_WIDGET,
                part,
                fontScale = fontScale,
            )
        } finally {
            canvas.restore()
        }
    }
    return if (frame.lock.background == LockBackground.SHADOW) withShadow(bmp, d, dark) else bmp
}

/**
 * Text protection without a box: a soft shadow around everything drawn (the content's alpha, blurred), like the one
 * System UI puts under the lock screen's clock. Dark behind light content, a white glow behind dark content.
 */
private fun withShadow(
    src: Bitmap,
    d: Float,
    dark: Boolean,
): Bitmap {
    val offset = IntArray(2)
    val mask = src.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter(5 * d, BlurMaskFilter.Blur.NORMAL) }, offset)
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val cv = Canvas(out)
    val paint = Paint().apply { color = if (dark) 0x99000000.toInt() else 0xB3FFFFFF.toInt() }
    // twice: the blur spreads the alpha thin, one pass is too faint behind thin lines
    repeat(2) { cv.drawBitmap(mask, offset[0].toFloat(), offset[1].toFloat() + 0.5f * d, paint) }
    cv.drawBitmap(src, 0f, 0f, null)
    mask.recycle()
    src.recycle()
    return out
}

/**
 * What the lock-screen widget says to TalkBack: the card's one sentence ([A11y.card]), with a follower's wearer's name
 * first as on the home widgets, then *Add treatment* when that is what a tap does.
 */
fun describeLock(
    context: Context,
    frame: WidgetFrame,
): String {
    val model = widgetModel(context, frame)
    val style = lockWidgetCardStyle(frame.config)
    val sentence = A11y.card(model, style, CardSurface.LOCK_DESCRIPTION)
    val name =
        frame.glucose.link
            ?.wearerName
            ?.takeIf { frame.glucose.role == Role.Follower && it.isNotBlank() }
    val said = if (name != null) context.getString(R.string.widget_desc_follower, name, sentence) else sentence
    return if (widgetTap(context, frame) is WidgetTap.QuickTreatment) context.getString(R.string.widget_lock_desc_tap, said) else said
}

/** The lock-screen widget as a Glance widget: composes the updater's frame at the size and hole the module gives. */
class LockWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val host = context.widgetsHost
        host.awaitReady()
        val updater = host.widgets.updater
        val first = updater.current()
        provideContent {
            val frame by updater.frame.collectAsState()
            LockContent(frame ?: first)
        }
    }

    /** System UI's module let go of the widget (turned off, or a leftover id): forget the id. */
    override suspend fun onDelete(
        context: Context,
        glanceId: GlanceId,
    ) {
        val host = context.widgetsHost
        host.awaitReady()
        host.widgets.settings.forget(intArrayOf(GlanceAppWidgetManager(context).getAppWidgetId(glanceId)))
    }
}

@Composable
private fun LockContent(frame: WidgetFrame) {
    val context = LocalContext.current
    val size = LocalSize.current
    val hole = lockHole(LocalAppWidgetOptions.current, size.width.value)
    val bitmap = remember(frame, size, hole) { renderLockWidget(context, size.width.value, size.height.value, hole, frame) }
    val description = remember(frame) { describeLock(context, frame) }
    val tap = remember(frame) { WidgetTap.intent(context, widgetTap(context, frame)) }
    Image(
        ImageProvider(bitmap),
        contentDescription = description,
        modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(tap)),
        contentScale = ContentScale.FillBounds,
    )
}

/**
 * The lock-screen widget's provider ([LockWidgetLink.RECEIVER]): exported for the system's widget broadcasts. It learns
 * the ids System UI's module hosts from them (the system doesn't list those to the app): the bind's update, and the
 * options the module sends on every start of System UI and whenever the app asks.
 */
class LockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LockWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        hosted(context, appWidgetManager, appWidgetIds.toList())
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        hosted(context, appWidgetManager, listOf(appWidgetId))
    }

    /** Remembers the [ids] the system has bound to this provider (the receiver is exported: a stray id is ignored). */
    private fun hosted(
        context: Context,
        manager: AppWidgetManager,
        ids: List<Int>,
    ) {
        val provider = ComponentName(context, LockWidgetReceiver::class.java)
        val ours = ids.filter { id -> runCatching { manager.getAppWidgetInfo(id)?.provider == provider }.getOrDefault(false) }
        val host = context.widgetsHost
        host.widgets.lockHosted(ours, host::awaitReady)
    }
}
