// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.systemui

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import app.mauri.sukrara.core.model.LockBackground
import app.mauri.sukrara.core.model.LockLook
import app.mauri.sukrara.core.model.LockWidgetLink
import app.mauri.sukrara.graph.GraphPalette
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.math.hypot

/**
 * The lock-screen widget inside System UI (#66, docs/17): the app's widget ([LockWidgetLink.RECEIVER], a card surface
 * drawn by the app) in the empty lower part of the lock screen, around the fingerprint icon, above the shortcuts.
 *
 * Android 17's lock screen on Pixel is one Compose scene container (SceneWindowRootView), with no layout to add a view
 * to. So System UI itself hosts the widget: an [AppWidgetHost] in System UI (which holds BIND_APPWIDGET for its own
 * hub, so no prompt), its view added as the window root's *first* child, under every lock-screen layer: the lock icon,
 * the shortcuts and the notifications keep drawing on top of it. The app redraws it like its home widgets; a tap runs
 * the widget's action (Add treatment shows over the lock screen without unlocking).
 *  - **Place**: from System UI's own geometry ([LockGeometry]): the fingerprint sensor's bounds (AuthController), else
 *    the lock icon's default spot; the shortcuts' dimens. The widget learns the icon's spot from its options.
 *  - **Visibility** follows the lock-screen scene on every frame the window draws ([sceneAlpha]): shown on the idle
 *    lock screen, fading with the shade, the bouncer and the unlock, hidden while the screen is off (AOD) and while
 *    the app has it off.
 *  - **Touches** stay the lock screen's (swipe up, long press, the fingerprint): the widget only gets copies of taps
 *    that start on it, outside the fingerprint's column ([LockTouches]).
 *  - **Background** ([LockBackground]): card, frosted glass and dim are drawn here, the shadow by the widget.
 * Every entry point catches everything: a failure here is logged and the lock screen stays as System UI draws it.
 */
internal class LockWidgetHost(
    private val appPackage: String,
    private val log: (String) -> Unit,
) {
    /** Written on the main thread, read by the notification-stack hook ([reservesShelf]) on System UI's own threads. */
    @Volatile private var enabled = false
    private var look = LockLook()

    /** The tint in use, to redo the background only when the dark theme really flips. */
    private var drawnDark: Boolean? = null

    private var root: WeakReference<ViewGroup>? = null
    private var host: AppWidgetHost? = null

    @Volatile private var view: AppWidgetHostView? = null
    private var scrim: View? = null
    private var blur: Drawable? = null
    private var scenes: Any? = null
    private var auth: Any? = null
    private var lastAlpha = -1f
    private var recheck = false

    /** Where the lock icon (the fingerprint sensor) is, from the last [place]. */
    private var lockRect = Rect()

    /** A new one on every System UI start and every ask from the app: the provider hears of the widget again. */
    private var stamp = SystemClock.elapsedRealtime()
    private var sentOptions: Options? = null
    private val touches = LockTouches()

    // --- from the hooks (main thread) ---------------------------------------------------------------------------

    /** The scene interactor (Lockscreen / Shade / Bouncer / Gone …), captured when Dagger creates it. */
    fun onScenes(o: Any) {
        scenes = o
    }

    /** AuthController: knows the under-display fingerprint sensor's bounds, where the lock icon sits. */
    fun onAuth(o: Any) {
        auth = o
    }

    /** The notification shade window's root view was attached: the lock screen lives in it. */
    fun onRootAttached(r: ViewGroup) =
        safe("attach") {
            if (root?.get() === r) return@safe
            root = WeakReference(r)
            r.viewTreeObserver.addOnPreDrawListener(
                ViewTreeObserver.OnPreDrawListener {
                    safe("predraw") { follow() }
                    true
                },
            )
            r.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> safe("layout") { place() } }
            if (enabled) ensureView(r)
            log("lock screen widget: root attached")
        }

    /** Every touch on the window, before System UI handles it (it always gets it too). */
    fun onTouch(ev: MotionEvent) = safe("touch") { touch(ev) }

    /** The app's state: the widget on or off and its look. */
    fun update(
        on: Boolean,
        newLook: LockLook,
    ) = safe("state") {
        val restyle = newLook != look
        look = newLook
        val r = root?.get()
        if (on != enabled) {
            enabled = on
            if (r != null) {
                if (on) {
                    ensureView(r)
                } else {
                    turnOff()
                }
            }
        } else if (restyle && view != null) {
            applyBackground()
        }
    }

    /** The app asked who's there (its process started): tell the provider about the widget again. */
    fun announce() =
        safe("announce") {
            stamp = SystemClock.elapsedRealtime()
            place()
        }

    /** While on, the notification shelf must fit above the lock icon (the widget's value sits beside it). */
    val reservesShelf: Boolean get() = enabled && view != null

    // --- the hosted widget --------------------------------------------------------------------------------------

    private fun ensureView(r: ViewGroup) {
        if (view?.parent === r) return place()
        val ctx = r.context
        val mgr = AppWidgetManager.getInstance(ctx)
        val provider = ComponentName(appPackage, LockWidgetLink.RECEIVER)
        val h = host ?: GuardedHost(ctx) { removed -> if (removed == view?.appWidgetId) dropView() }.also { host = it }
        h.startListening()
        // one widget id for good: reuse it across System UI restarts, drop leftovers (an older provider's too)
        val ids = h.appWidgetIds.toList()
        var id = ids.firstOrNull { mgr.getAppWidgetInfo(it)?.provider == provider }
        ids.filter { it != id }.forEach { h.deleteAppWidgetId(it) }
        if (id == null) {
            val n = h.allocateAppWidgetId()
            if (!mgr.bindAppWidgetIdIfAllowed(n, provider)) {
                h.deleteAppWidgetId(n)
                return log("lock screen widget: bind refused")
            }
            id = n
            log("lock screen widget: bound")
        }
        val info = mgr.getAppWidgetInfo(id) ?: return log("lock screen widget: no provider info")
        val v = h.createView(ctx, id, info)
        (v as? GuardedView)?.onConfig = { safe("config") { if (drawnDark != dark(v.context)) applyBackground() } }
        // the widget owns its content: no host padding or background
        v.setPadding(0, 0, 0, 0)
        // the window's bottom: under every lock-screen layer (the fingerprint icon, the shortcuts and the indication
        // text are in the lower Compose layers, so a card, blur or dim must go below them)
        r.addView(v, 0, FrameLayout.LayoutParams(0, 0))
        view = v
        sentOptions = null
        lastAlpha = -1f
        applyBackground()
        log("lock screen widget: view added")
    }

    /** Off in the app: the view goes and System UI lets go of the widget (the app stops drawing it). */
    private fun turnOff() {
        dropView()
        host?.let { h -> h.appWidgetIds.forEach { h.deleteAppWidgetId(it) } }
        log("lock screen widget: off")
    }

    private fun dropView() {
        view?.let { (it.parent as? ViewGroup)?.removeView(it) }
        view = null
        scrim?.let { (it.parent as? ViewGroup)?.removeView(it) }
        scrim = null
        blur = null
    }

    private fun dark(c: Context) =
        look.appearance.dark(c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)

    // --- contrast behind the widget (Material's text protection) ------------------------------------------------

    private fun applyBackground() {
        val v = view ?: return
        val r = root?.get() ?: return
        val res = r.resources
        val d = res.displayMetrics.density

        @SuppressLint("DiscouragedApi")
        val radius =
            res.getIdentifier("notification_corner_radius", "dimen", SystemUi.PACKAGE).takeIf { it != 0 }?.let(res::getDimension)
                ?: (28 * d)
        val dark = dark(v.context)
        drawnDark = dark
        blur = if (look.background == LockBackground.FROSTED) blurDrawable(r, radius, dark) else null
        v.background =
            when (look.background) {
                LockBackground.FROSTED -> blur ?: card(v.context, radius, dark)
                LockBackground.CARD -> card(v.context, radius, dark)
                else -> null
            }
        scrim?.let { r.removeView(it) }
        scrim =
            if (look.background == LockBackground.DIM) {
                ScrimView(r.context, dark).also { s -> r.addView(s, r.indexOfChild(v), FrameLayout.LayoutParams(0, 0)) }
            } else {
                null
            }
        lastAlpha = -1f
        place()
        follow()
    }

    /** Like a notification, a little see-through: the dark surface colour, or the home widgets' light background. */
    private fun card(
        c: Context,
        radius: Float,
        dark: Boolean,
    ) = GradientDrawable().apply {
        cornerRadius = radius
        setColor(ColorUtils.setAlphaComponent(if (dark) c.getColor(android.R.color.system_surface_container_high_dark) else LIGHT, 0xE0))
    }

    /**
     * Frosted glass: the wallpaper behind the widget blurred and tinted, with System UI's own BackgroundBlurDrawable
     * (ViewRootImpl, hidden API; the shade uses it). Dark: black 35 %; light: the home widgets' background 60 %. Null
     * when cross-window blur is off (battery saver, some devices): the card instead.
     */
    private fun blurDrawable(
        r: View,
        radius: Float,
        dark: Boolean,
    ): Drawable? =
        runCatching {
            if (r.context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled != true) return null
            val vri = View::class.java.getMethod("getViewRootImpl").invoke(r) ?: return null
            val dr = vri.javaClass.getMethod("createBackgroundBlurDrawable").invoke(vri) as Drawable
            val d = r.resources.displayMetrics.density
            dr.javaClass.getMethod("setBlurRadius", Int::class.javaPrimitiveType).invoke(dr, (24 * d).toInt())
            val tint = if (dark) 0x59000000 else ColorUtils.setAlphaComponent(LIGHT, 0x99)
            dr.javaClass.getMethod("setColor", Int::class.javaPrimitiveType).invoke(dr, tint)
            dr.javaClass.getMethod("setCornerRadius", Float::class.javaPrimitiveType).invoke(dr, radius)
            dr
        }.onFailure { log("lock screen widget: no blur: ${it.javaClass.simpleName}") }.getOrNull()

    /**
     * Dim: Material's gradient scrim (dark end 60 %, its middle 3/10 towards the dark end, fading to 0) over the bottom
     * of the screen, from a little above the widget to the bottom edge, full width; white on the light look.
     */
    @SuppressLint("ViewConstructor")
    private class ScrimView(
        c: Context,
        private val dark: Boolean,
    ) : View(c) {
        private val paint = Paint()

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldW: Int,
            oldH: Int,
        ) {
            val rgb = if (dark) 0 else 0xFFFFFF
            paint.shader =
                LinearGradient(
                    0f,
                    0f,
                    0f,
                    h.toFloat(),
                    intArrayOf(rgb, 0x4D000000 or rgb, 0x99000000.toInt() or rgb),
                    floatArrayOf(0f, 0.35f, 1f),
                    Shader.TileMode.CLAMP,
                )
        }

        override fun onDraw(cv: Canvas) {
            cv.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }

    /** Size and position from the lock icon and the shortcuts; tells the widget where the lock icon is. */
    private fun place() {
        val r = root?.get() ?: return
        val v = view ?: return
        if (r.width == 0 || r.height == 0) return
        val res = r.resources
        val d = res.displayMetrics.density

        @SuppressLint("DiscouragedApi")
        fun dimen(
            name: String,
            fallbackDp: Float,
        ): Int =
            res.getIdentifier(name, "dimen", SystemUi.PACKAGE).takeIf { it != 0 }?.let(res::getDimensionPixelSize)
                ?: (fallbackDp * d).toInt()
        val lock = lockIcon(r, ::dimen)
        if (lock != lockRect) log("lock screen widget: lock icon at $lock")
        lockRect = lock
        val shortcutsTop = r.height - dimen("keyguard_affordance_vertical_offset", 32f) - dimen("keyguard_affordance_fixed_height", 48f)
        val p = LockGeometry.place(r.width, lock.left, lock.top, lock.right, lock.bottom, shortcutsTop, d) ?: return
        val lp = v.layoutParams as FrameLayout.LayoutParams
        if (lp.width != p.width || lp.height != p.height || lp.leftMargin != p.left || lp.topMargin != p.top) {
            lp.width = p.width
            lp.height = p.height
            lp.leftMargin = p.left
            lp.topMargin = p.top
            v.layoutParams = lp
        }
        scrim?.let { s ->
            val slp = s.layoutParams as FrameLayout.LayoutParams
            val sTop = (p.top - 64 * d).toInt()
            if (slp.width != r.width || slp.height != r.height - sTop || slp.topMargin != sTop) {
                slp.width = r.width
                slp.height = r.height - sTop
                slp.leftMargin = 0
                slp.topMargin = sTop
                s.layoutParams = slp
            }
        }
        // updateAppWidgetSize reports each size minus the default widget padding, but the view has none (the widget
        // draws edge to edge): add it back, or the widget draws for a smaller box and is stretched (docs/17: a test
        // square came out 9 % taller). It sends options only when the size changed.
        val pad = AppWidgetHostView.getDefaultPaddingForWidget(r.context, v.appWidgetInfo?.provider, null)
        val options = Options(p.holeLeft, p.holeRight, p.holeBottom, stamp)
        v.updateAppWidgetSize(options.bundle(), listOf(SizeF((p.width + pad.left + pad.right) / d, (p.height + pad.top + pad.bottom) / d)))
        // the lock icon's spot and the stamp also when the size stayed: the provider learns the id from these
        if (options != sentOptions) {
            AppWidgetManager.getInstance(r.context).updateAppWidgetOptions(v.appWidgetId, options.bundle())
            sentOptions = options
        }
    }

    /** What the widget's options say: the lock icon's spot (dp in the widget) and the stamp. */
    private data class Options(
        val holeLeft: Float,
        val holeRight: Float,
        val holeBottom: Float,
        val stamp: Long,
    ) {
        fun bundle() =
            Bundle().apply {
                putFloat(LockWidgetLink.HOLE_LEFT, holeLeft)
                putFloat(LockWidgetLink.HOLE_RIGHT, holeRight)
                putFloat(LockWidgetLink.HOLE_BOTTOM, holeBottom)
                putLong(LockWidgetLink.STAMP, stamp)
            }
    }

    private fun lockIcon(
        r: ViewGroup,
        dimen: (String, Float) -> Int,
    ): Rect {
        val udfps = runCatching { authBounds?.get(auth) as? Rect }.getOrNull()
        if (udfps != null && !udfps.isEmpty) return Rect(udfps)
        // no under-display sensor: System UI's default lock icon, centred above lock_icon_margin_bottom
        val size = (64 * r.resources.displayMetrics.density).toInt()
        val bottom = r.height - dimen("lock_icon_margin_bottom", 74f)
        return Rect(r.width / 2 - size / 2, bottom - size, r.width / 2 + size / 2, bottom)
    }

    // --- visibility ---------------------------------------------------------------------------------------------

    private fun follow() {
        val v = view ?: return
        val a = if (enabled && interactive(v.context)) lockscreenAlpha() else 0f
        // mid-transition: look again next frame, so the widget never stays half faded when System UI's last frame of
        // the transition came before the scene settled
        if (a > 0f && a < 1f && !recheck) {
            recheck = true
            v.postOnAnimation {
                recheck = false
                safe("follow") { follow() }
            }
        }
        if (a == lastAlpha) return
        lastAlpha = a
        for (x in listOfNotNull(v, scrim)) {
            x.alpha = a
            x.visibility = if (a > 0f) View.VISIBLE else View.INVISIBLE
        }
        // the blur is composited by the window, not with the view: fade it too
        blur?.alpha = (a * 255).toInt()
    }

    private fun interactive(c: Context) = c.getSystemService(PowerManager::class.java)?.isInteractive == true

    /** 1 on the idle lock screen, 0 elsewhere, in between while a transition to or from it runs. */
    private fun lockscreenAlpha(): Float {
        val s = scenes ?: return 0f
        val r = reflect(s) ?: return 0f
        val ts = r.transitionState.invoke(s) ?: return 0f
        val lock = r.lockscreen
        if (r.idle.isInstance(ts)) {
            val overlays = r.idleOverlays.invoke(ts) as? Set<*>
            return sceneAlpha(idle = true, onLock = r.idleScene.invoke(ts) == lock, overlay = !overlays.isNullOrEmpty())
        }
        if (!r.transition.isInstance(ts)) return 0f
        return sceneAlpha(
            idle = false,
            from = r.from.get(ts) == lock,
            to = r.to.get(ts) == lock,
            progress = r.progress.invoke(ts) as Float,
        )
    }

    /** Reflection handles for System UI's scene classes, resolved once. */
    private class Scenes(
        val transitionState: Method,
        val lockscreen: Any,
        val idle: Class<*>,
        val idleScene: Method,
        val idleOverlays: Method,
        val transition: Class<*>,
        val progress: Method,
        val from: Field,
        val to: Field,
    )

    private var scenesReflect: Scenes? = null
    private var reflectFailed = false
    private var authBounds: Field? = null

    private fun reflect(s: Any): Scenes? {
        scenesReflect?.let { return it }
        if (reflectFailed) return null
        return runCatching {
            val cl = checkNotNull(s.javaClass.classLoader)
            val state = "com.android.compose.animation.scene.content.state.TransitionState"
            val idle = cl.loadClass("$state\$Idle")
            val transition = cl.loadClass("$state\$Transition")
            Scenes(
                s.javaClass.getMethod("getTransitionState"),
                checkNotNull(cl.loadClass("com.android.systemui.scene.shared.model.Scenes").getField("Lockscreen").get(null)),
                idle,
                idle.getMethod("getCurrentScene"),
                idle.getMethod("getCurrentOverlays"),
                transition,
                transition.getMethod("getProgress"),
                transition.getField("fromContent"),
                transition.getField("toContent"),
            )
        }.onFailure {
            reflectFailed = true
            log("lock screen widget: scene reflection failed: ${it.javaClass.simpleName}")
        }.getOrNull()
            ?.also { scenesReflect = it }
    }

    /** AuthController's under-display fingerprint bounds field (System UI's class loader). */
    fun resolveAuth(cl: ClassLoader) {
        authBounds =
            runCatching {
                cl
                    .loadClass(
                        "com.android.systemui.biometrics.AuthController",
                    ).getDeclaredField("mUdfpsBounds")
                    .apply { isAccessible = true }
            }.onFailure { log("lock screen widget: no AuthController.mUdfpsBounds") }.getOrNull()
    }

    // --- touches ------------------------------------------------------------------------------------------------

    private fun touch(ev: MotionEvent) {
        val v = view ?: return
        val d = v.resources.displayMetrics.density
        val starts =
            v.visibility == View.VISIBLE &&
                v.alpha > 0.9f &&
                LockTouches.hits(
                    ev.x,
                    ev.y,
                    v.left,
                    v.top,
                    v.right,
                    v.bottom,
                    lockRect.left,
                    lockRect.right,
                    LockTouches.LOCK_MARGIN_DP * d,
                )
        val slop = ViewConfiguration.get(v.context).scaledTouchSlop.toFloat()
        when (
            touches.on(
                ev.actionMasked,
                ev.x,
                ev.y,
                ev.eventTime - ev.downTime,
                starts,
                slop,
                ViewConfiguration.getLongPressTimeout().toLong(),
            )
        ) {
            LockTouches.Do.DELIVER -> deliver(v, ev, null)
            LockTouches.Do.CANCEL -> deliver(v, ev, MotionEvent.ACTION_CANCEL)
            LockTouches.Do.NOTHING -> Unit
        }
    }

    private fun deliver(
        v: View,
        ev: MotionEvent,
        action: Int?,
    ) {
        val e = MotionEvent.obtain(ev)
        if (action != null) e.action = action
        e.offsetLocation(-v.left.toFloat(), -v.top.toFloat())
        (v as? GuardedView)?.allow = true
        try {
            v.dispatchTouchEvent(e)
        } finally {
            (v as? GuardedView)?.allow = false
            e.recycle()
        }
    }

    /**
     * The window would also hand the widget every touch the Compose lock screen above it doesn't consume (taps on empty
     * space, a finger on the fingerprint sensor: docs/17's first-build bug). It only takes the filtered copies.
     */
    @SuppressLint("ViewConstructor")
    private class GuardedView(
        c: Context,
    ) : AppWidgetHostView(c) {
        var allow = false

        /** The dark theme may have flipped: an automatic appearance redoes its tint. */
        var onConfig: (() -> Unit)? = null

        override fun dispatchTouchEvent(ev: MotionEvent) = allow && super.dispatchTouchEvent(ev)

        override fun onConfigurationChanged(newConfig: Configuration?) {
            super.onConfigurationChanged(newConfig)
            onConfig?.invoke()
        }
    }

    /** Our host in System UI's uid; [onRemoved] = the system removed a widget (Sukrara uninstalled, the id deleted). */
    private class GuardedHost(
        c: Context,
        private val onRemoved: (Int) -> Unit,
    ) : AppWidgetHost(c, HOST_ID) {
        override fun onCreateView(
            c: Context,
            id: Int,
            info: AppWidgetProviderInfo?,
        ): AppWidgetHostView = GuardedView(c)

        override fun onAppWidgetRemoved(appWidgetId: Int) {
            super.onAppWidgetRemoved(appWidgetId)
            onRemoved(appWidgetId)
        }
    }

    private inline fun safe(
        what: String,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (t: Throwable) {
            log("lock screen widget: $what failed: $t")
        }
    }

    companion object {
        /** Our host id inside System UI's uid (its hub uses its own): "SUK" in ASCII, as in the PoC. */
        const val HOST_ID = 0x53554B

        /** The home widgets' light background (the light look's card and tint). */
        val LIGHT: Int = GraphPalette.of(false).bg
    }
}

/** The lock-screen widget's place in System UI's window (px), from System UI's geometry. Pure: tested. */
internal object LockGeometry {
    /** The sides, like the notifications'. */
    const val SIDE_DP = 16f

    /** Between the widget and the shortcuts. */
    const val ABOVE_SHORTCUTS_DP = 8f

    /** Between the lock icon and the value or the slots. */
    const val GAP_DP = 6f

    /** Less room than this between the icon's top and the shortcuts: no widget. */
    const val MIN_HEIGHT_DP = 80f

    /** The widget's box (px) and the lock icon in it (dp from its top-left). */
    data class Place(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val holeLeft: Float,
        val holeRight: Float,
        val holeBottom: Float,
    )

    /**
     * From the top of the lock icon ([lockLeft] … [lockBottom], px) down to [ABOVE_SHORTCUTS_DP] above the shortcuts
     * ([shortcutsTop]), [SIDE_DP] from each side of a [windowW] window; null without room for it.
     */
    fun place(
        windowW: Int,
        lockLeft: Int,
        lockTop: Int,
        lockRight: Int,
        lockBottom: Int,
        shortcutsTop: Int,
        d: Float,
    ): Place? {
        val side = (SIDE_DP * d).toInt()
        val bottom = shortcutsTop - (ABOVE_SHORTCUTS_DP * d).toInt()
        if (bottom - lockTop < MIN_HEIGHT_DP * d) return null
        val gap = GAP_DP * d
        return Place(
            left = side,
            top = lockTop,
            width = windowW - 2 * side,
            height = bottom - lockTop,
            holeLeft = (lockLeft - side - gap) / d,
            holeRight = (lockRight - side + gap) / d,
            holeBottom = (lockBottom - lockTop) / d,
        )
    }
}

/**
 * How much the widget shows for System UI's scene state: 1 on the idle lock screen with no overlay (the shade, the
 * bouncer…), 0 on any other idle scene; in a transition, 1 − [progress] when it leaves the lock screen ([from]) and
 * [progress] when it comes to it ([to]). Pure: tested.
 */
internal fun sceneAlpha(
    idle: Boolean,
    onLock: Boolean = false,
    overlay: Boolean = false,
    from: Boolean = false,
    to: Boolean = false,
    progress: Float = 0f,
): Float {
    if (idle) return if (onLock && !overlay) 1f else 0f
    val p = progress.coerceIn(0f, 1f)
    return ((if (from) 1 - p else 0f) + (if (to) p else 0f)).coerceIn(0f, 1f)
}

/**
 * Which touches the widget gets: copies of taps that start on it, never in the fingerprint's column; a drag (the unlock
 * swipe) or a press held like a long press (a finger resting on the sensor, the lock screen's long press) is cancelled
 * before it can click. Pure: tested.
 */
internal class LockTouches {
    enum class Do { NOTHING, DELIVER, CANCEL }

    private var tracking = false
    private var downX = 0f
    private var downY = 0f

    /**
     * [action] = the masked action; [heldMs] = time since the press started; [startsOnWidget] = a down on the widget,
     * outside the fingerprint's column ([hits]).
     */
    fun on(
        action: Int,
        x: Float,
        y: Float,
        heldMs: Long,
        startsOnWidget: Boolean,
        slop: Float,
        longPressMs: Long,
    ): Do =
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                tracking = startsOnWidget
                downX = x
                downY = y
                if (tracking) Do.DELIVER else Do.NOTHING
            }

            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                when {
                    !tracking -> {
                        Do.NOTHING
                    }

                    hypot(x - downX, y - downY) > slop || heldMs > longPressMs -> {
                        tracking = false
                        Do.CANCEL
                    }

                    else -> {
                        if (action == MotionEvent.ACTION_UP) tracking = false
                        Do.DELIVER
                    }
                }
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                if (tracking) {
                    tracking = false
                    Do.CANCEL
                } else {
                    Do.NOTHING
                }
            }

            else -> {
                Do.NOTHING
            }
        }

    companion object {
        /** Extra width on each side of the lock icon where touches are never the widget's. */
        const val LOCK_MARGIN_DP = 24f

        /**
         * On the widget ([left] … [bottom], window px), but not in the lock icon's column ([lockLeft] … [lockRight] ±
         * [margin]): a finger on the under-display sensor lands anywhere around the icon, top to bottom of the widget.
         */
        fun hits(
            x: Float,
            y: Float,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            lockLeft: Int,
            lockRight: Int,
            margin: Float,
        ): Boolean {
            if (x < left || x >= right || y < top || y >= bottom) return false
            return x < lockLeft - margin || x > lockRight + margin
        }
    }
}
