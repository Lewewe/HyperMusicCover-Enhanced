package com.os4.musiccover

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Remap compact native islands while retaining HyperOS expansion, launch and touch handling. */
object IslandIphoneGestures {
    private const val CONTENT = "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
    private const val WINDOW = "miui.systemui.dynamicisland.window.DynamicIslandWindowView"
    private const val TOUCH = "miui.systemui.dynamicisland.touch.domain.interactor.DynamicIslandTouchInteractor"
    private const val BASE = "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private const val UPDATER = "miui.systemui.dynamicisland.anim.ui.animator.IslandPropertyUpdater"
    private const val CAMERA_GAP_DP = 6f
    private const val MEDIA_CARD =
        "com.android.systemui.statusbar.notification.mediaisland.PlayerIslandConstraintLayout"
    private const val ANIM = "com.android.systemui.statusbar.notification.DynamicIslandWindowAnimController"
    private const val PIN = "com.android.systemui.statusbar.notification.utils.DynamicIslandMiniWindowHelper"
    /** Long enough for SystemUI's open-app callback, short enough not to style a later launch. */
    private const val LAUNCH_WINDOW_MS = 1500L
    private const val GHOST_MS = 380L
    private val loaders = java.util.Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    private val main = Handler(Looper.getMainLooper())
    private val expanding = ThreadLocal<Any>()
    private val releaseClicks = WeakHashMap<Any, Boolean>()
    @Volatile private var active = false
    @Volatile private var top = false
    @Volatile private var radiusDp = IslandGesturePolicy.DEFAULT_RADIUS_DP
    private var loaderWatch = false
    private val hostLoaders = java.util.Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())

    /** A compact tap's island bounds, consumed by SystemUI's default open-app animation. */
    private class Launch(val pkg: String, view: View, val bounds: Rect, val at: Long) {
        val view = WeakReference(view)
    }
    @Volatile private var launch: Launch? = null

    @JvmStatic fun enabled(): Boolean = active
    @JvmStatic fun topEnabled(): Boolean = top
    /** Read on every expansion, so a change applies from the next one without a restart. */
    @JvmStatic fun configureTop(on: Boolean) {
        top = on
        main.post { resizeFullWidthCards() }
        replaceAllText()
    }
    @JvmStatic fun radiusDp(): Int = radiusDp
    @JvmStatic fun configureRadius(dp: Int) {
        radiusDp = IslandGesturePolicy.radius(dp)
        replaceAllText()
    }
    @JvmStatic fun configure(on: Boolean) {
        active = on
        if (!on) main.post { releaseClicks.clear() }
    }

    @JvmStatic @Synchronized fun install(loader: ClassLoader) {
        installLaunchAnimation(loader)
        installMediaCardWidth(loader)
        tryInstall(loader)
        if (loaders.isNotEmpty() || loaderWatch) return
        loaderWatch = true
        runCatching {
            Xp.hookAllConstructors(Class.forName("dalvik.system.BaseDexClassLoader")) { chain ->
                val result = chain.proceed()
                if (loaders.isEmpty()) (chain.thisObject as? ClassLoader)?.let(::tryInstall)
                result
            }
        }.onFailure { Xp.w("Island gestures loader unavailable: $it") }
    }

    @Synchronized private fun tryInstall(loader: ClassLoader) {
        if (loader in loaders) return
        val content = runCatching { Xp.findClass(CONTENT, loader) }.getOrNull() ?: return
        val window = runCatching { Xp.findClass(WINDOW, loader) }.getOrNull() ?: return
        val touch = runCatching { Xp.findClass(TOUCH, loader) }.getOrNull() ?: return
        // Resolve the native entry points before installing anything on an unknown OEM build.
        // All required entry points or none: a partial set could swallow taps or leave a stale
        // release-click consumer without the touch hook that clears it.
        if (!declares(content, "handleOpenApp") || !declares(content, "onIslandClick") ||
            !declares(window, "onLongPress") || !declares(window, "dispatchTouchEvent") ||
            !declares(touch, "performLongClick")) {
            Xp.w("Island gestures unsupported on this SystemUI plugin build")
            return
        }
        loaders.add(loader)
        runCatching {
            Xp.hookAll(content, "onIslandClick") { chain ->
                val owner = chain.thisObject ?: return@hookAll chain.proceed()
                if (!active || expanding.get() === owner) return@hookAll chain.proceed()
                if (releaseClicks.remove(owner) == true) return@hookAll null
                if (!compact(owner)) return@hookAll chain.proceed()
                val data = call(owner, "getCurrentIslandData") ?: return@hookAll chain.proceed()
                val extras = call(data, "getExtras") as? Bundle ?: return@hookAll chain.proceed()
                val notification = runCatching {
                    extras.getParcelable("miui.sbn", StatusBarNotification::class.java)
                }.getOrNull()
                val pending = runCatching {
                    extras.getParcelable("miui.pending.intent", PendingIntent::class.java)
                }.getOrNull()
                if (notification?.notification?.contentIntent == null && pending == null)
                    return@hookAll chain.proceed()
                if (!clickAllowed(owner)) return@hookAll null
                rememberLaunch(owner, extras.getString("miui.pkg.name"))
                // Captured before the launch: HyperOS hides the island as soon as the app opens.
                val ghost = launch?.let { snapshot(owner as View, it.bounds) }
                // OEM launch handles keyguard dismissal, PendingIntent and its app animation.
                try {
                    Xp.callMethod(owner, "handleOpenApp", notification)
                    ghost?.let { dissolve(owner as View, it) }
                    null
                }
                catch (t: Throwable) {
                    launch = null
                    Xp.w("Island gestures app launch failed: $t")
                    chain.proceed()
                }
            }
            Xp.hookAll(touch, "performLongClick") { chain ->
                val interactor = chain.thisObject ?: return@hookAll chain.proceed()
                if (!active || field(interactor, "downInExpanded") == true)
                    return@hookAll chain.proceed()
                val nativeWindow = field(interactor, "windowView") ?: return@hookAll chain.proceed()
                val owner = when {
                    field(interactor, "downInShowOnceIsland") == true ->
                        call(call(field(interactor, "showOnceIslandHandler"), "get"), "getCurrent")
                    field(interactor, "downInBigIsland") == true -> call(nativeWindow, "getCurrentBigIslandState")
                    field(interactor, "downInSmallIsland") == true -> call(nativeWindow, "getCurrentSmallIslandState")
                    else -> null
                } ?: return@hookAll chain.proceed()
                // Do not inherit the OEM region/share restriction on its long-press action.
                if (!canExpand(owner)) return@hookAll chain.proceed()
                val oldLongClick = field(interactor, "longClickReceived") as? Boolean
                    ?: return@hookAll chain.proceed()
                // Native gesture bookkeeping must see the hold, or its release becomes a tap.
                if (!setFlag(interactor, "longClickReceived", true)) return@hookAll chain.proceed()
                val coordinator = call(owner, "getDynamicIslandEventCoordinator")
                if (coordinator != null) runCatching { Xp.callMethod(coordinator, "resetPress",
                    field(interactor, "downInBigIsland") == true,
                    field(interactor, "downInSmallIsland") == true, false) }
                if (expand(owner)) true else {
                    setFlag(interactor, "longClickReceived", oldLongClick)
                    chain.proceed()
                }
            }
            Xp.hookAll(window, "onLongPress") { chain ->
                if (!active) return@hookAll chain.proceed()
                val owner = chain.args.getOrNull(1) ?: chain.args.firstOrNull()
                    ?: return@hookAll chain.proceed()
                if (!canExpand(owner)) return@hookAll chain.proceed()
                if (expand(owner)) null else chain.proceed()
            }
            // Optional: builds without direct-intent routing already expand on onIslandClick.
            if (declares(content, "interceptClickForDirectIntent"))
                Xp.hookAll(content, "interceptClickForDirectIntent") { chain ->
                    if (active && expanding.get() === chain.thisObject) false else chain.proceed()
                }
            Xp.hookAll(window, "dispatchTouchEvent") { chain ->
                if (releaseClicks.isEmpty()) return@hookAll chain.proceed()
                val event = chain.args.firstOrNull() as? MotionEvent
                if (event?.actionMasked == MotionEvent.ACTION_DOWN) releaseClicks.clear()
                val result = chain.proceed()
                if (releaseClicks.isNotEmpty() && (event?.actionMasked == MotionEvent.ACTION_UP ||
                        event?.actionMasked == MotionEvent.ACTION_CANCEL)) {
                    val owners = releaseClicks.keys.toList()
                    // Native click delivery may be posted; consume only the hold's release.
                    main.postDelayed({ owners.forEach { releaseClicks.remove(it) } }, 150L)
                }
                result
            }
            installExpandedTop(loader)
            Xp.log("Island gestures installed")
        }.onFailure { Xp.w("Island gestures hooks unavailable: $it") }
    }

    /**
     * HyperOS opens a compact island's app with ActivityOptions.makeBasic(): the island vanishes
     * and the app simply appears. For our tap only, grow the app from the island instead. Pin
     * mode, keyguard, element transitions and every native expanded-island launch are untouched.
     */
    private fun installLaunchAnimation(loader: ClassLoader) {
        if (loader in hostLoaders) return
        val anim = runCatching { Xp.findClass(ANIM, loader) }.getOrNull() ?: return
        if (!declares(anim, "openAppUseDefaultAnim")) return
        hostLoaders.add(loader)
        runCatching {
            Xp.hookAll(anim, "openAppUseDefaultAnim") { chain ->
                val pkg = chain.args.getOrNull(0) as? String
                val intent = chain.args.getOrNull(1) as? PendingIntent
                val source = takeLaunch(pkg)
                if (!active || source == null || intent == null || !intent.isActivity)
                    return@hookAll chain.proceed()
                val view = source.view.get()
                if (view == null || !view.isAttachedToWindow) return@hookAll chain.proceed()
                // Native swaps pinned apps to a launcher intent; keep that path entirely native.
                val pinned = runCatching {
                    Xp.findClass(PIN, loader).getMethod("isPinMode", String::class.java,
                        PendingIntent::class.java).invoke(null, pkg, intent) as Boolean
                }.getOrNull()
                if (pinned != false) return@hookAll chain.proceed()
                val options = runCatching {
                    val b = source.bounds
                    ActivityOptions.makeScaleUpAnimation(view, b.left, b.top, b.width(), b.height()).also {
                        // The same launch policy as native makeBasic() in this method.
                        Xp.callMethod(it, "setPendingIntentLaunchFlags", Intent.FLAG_ACTIVITY_NEW_TASK)
                        it.setPendingIntentBackgroundActivityStartMode(
                            @Suppress("DEPRECATION") ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    }
                }.getOrElse {
                    Xp.w("Island launch animation unavailable: $it")
                    return@hookAll chain.proceed()
                }
                runCatching { Xp.setObjectField(chain.thisObject!!, "mPendingIntent", intent) }
                try {
                    intent.send(null, 0, null, null, null, null, options.toBundle())
                } catch (e: PendingIntent.CanceledException) {
                    Xp.w("Island launch PendingIntent canceled: $e")
                }
                null
            }
            Xp.log("Island launch animation installed")
        }.onFailure { Xp.w("Island launch animation hook unavailable: $it") }
    }

    /** The expanded card at the camera: the pill's top and height, the lens and the radius. */
    private class TopLayout(val pillTop: Int, val pillHeight: Int, val lens: Rect, val radius: Float,
        val gap: Float)

    private fun topLayout(view: View): TopLayout? {
        if (!top) return null
        val config = view.resources.configuration
        if (config.orientation != android.content.res.Configuration.ORIENTATION_PORTRAIT ||
            config.smallestScreenWidthDp >= 600) return null
        val pillTop = field(view, "islandViewMarginTop") as? Int ?: return null
        val pillHeight = field(view, "islandViewHeight") as? Int ?: return null
        if (pillTop <= 0 || pillHeight <= 0) return null
        val density = view.resources.displayMetrics.density
        val lens = cutout(view.rootView ?: view,
            Rect(0, pillTop, maxOf(view.width, 1), pillTop + pillHeight))
        return TopLayout(pillTop, pillHeight, lens, radiusDp * density, CAMERA_GAP_DP * density)
    }

    /** Only when HyperOS would place the card lower, as getExpandedViewY() below decides. */
    private fun liftedLayout(view: View): TopLayout? {
        val layout = topLayout(view) ?: return null
        val native = field(view, "expandedViewY") as? Int ?: return null
        return layout.takeIf { it.pillTop < native }
    }

    /**
     * Every expanded placement - card, touch region, open/close and swipe animations, blur -
     * reads getExpandedViewY(), so answering with the pill's own top moves the card up to the
     * camera consistently; the compact window height reads the field and is left alone. As on
     * iPhone the card keeps its size: only text under the lens moves down, and text in a bottom
     * corner moves up when a rounder corner would cut it. Tablets and landscape keep HyperOS
     * placement.
     */
    private fun installExpandedTop(loader: ClassLoader) {
        val base = runCatching { Xp.findClass(BASE, loader) }.getOrNull() ?: return
        val updater = runCatching { Xp.findClass(UPDATER, loader) }.getOrNull()
        if (!declares(base, "getExpandedViewY")) return
        runCatching {
            Xp.hookAll(base, "getExpandedViewY") { chain ->
                val y = chain.proceed()
                if (y !is Int) return@hookAll y
                val layout = topLayout(chain.thisObject as? View ?: return@hookAll y)
                    ?: return@hookAll y
                minOf(y, layout.pillTop)
            }
            // Called when an island's expanded content is set or resized, not every frame.
            if (declares(base, "updateExpandedSize")) Xp.hookAll(base, "updateExpandedSize") { chain ->
                val owner = chain.thisObject as? View
                val args = chain.args.toTypedArray()
                if (owner != null) runCatching {
                    val native = evenMargins(owner)
                    // Media cards ask for HyperOS's maximum, read before this call.
                    if (native != null && args.getOrNull(0) == native)
                        args[0] = field(owner, "expandedViewMaxWidth")
                }.onFailure { Xp.w("Island expanded width failed: $it") }
                val result = chain.proceed(args)
                val content = call(chain.args.getOrNull(2), "getView") as? View
                if (owner != null && content != null) watchContent(owner, content)
                result
            }
            // On dark screens HyperOS rims the expanded island: its background is drawn a stroke
            // width beyond the card and outlined in faint white. iPhone's island has no rim, so
            // the card at the camera draws its background exactly under the content.
            if (declares(base, "updateDarkLightMode")) Xp.hookAll(base, "updateDarkLightMode") { chain ->
                val result = chain.proceed()
                runCatching {
                    val owner = chain.thisObject as? View ?: return@runCatching
                    val background = call(owner, "getBackgroundView") as? View ?: return@runCatching
                    val expanded = chain.args.getOrNull(0)?.javaClass?.simpleName == "Expanded"
                    setRim(background, liftedLayout(owner)?.takeIf { expanded }?.radius)
                }.onFailure { Xp.w("Island rim update failed: $it") }
                result
            }
            if (updater != null && declares(updater, "containerClipRadius")) {
                Xp.hookAll(updater, "containerClipRadius") { chain ->
                    val r = chain.proceed()
                    val clipTop = chain.args.getOrNull(0) as? Float ?: return@hookAll r
                    val clipBottom = chain.args.getOrNull(1) as? Float ?: return@hookAll r
                    val view = field(chain.thisObject ?: return@hookAll r, "view") as? View
                        ?: return@hookAll r
                    val layout = liftedLayout(view) ?: return@hookAll r
                    val height = call(view, "getExpandedViewHeight") as? Int ?: return@hookAll r
                    IslandGesturePolicy.clipRadius(clipBottom - clipTop, layout.pillHeight.toFloat(),
                        height.toFloat(), layout.radius)
                }
            }
        }.onFailure { Xp.w("Island expanded top unavailable: $it") }
    }

    /**
     * iPhone's expanded island keeps the same gap above and at both sides. The card already
     * starts at the pill's top; HyperOS derives the card's width, its centring margin, the content
     * and fake-view sizes and the open animation from one maximum width, so that maximum is set
     * to the screen less that gap on each side before HyperOS sizes the card, and given back when
     * the setting is off.
     */
    /** Returns HyperOS's own maximum while the card is widened, null otherwise. */
    private fun evenMargins(owner: View): Int? {
        val layout = liftedLayout(owner)
        val current = field(owner, "expandedViewMaxWidth") as? Int ?: return null
        if (layout == null) {
            nativeMaxWidth.remove(owner)?.let { Xp.setObjectField(owner, "expandedViewMaxWidth", it) }
            return null
        }
        val screen = owner.width.takeIf { it > 0 } ?: owner.resources.displayMetrics.widthPixels
        val width = screen - 2 * layout.pillTop
        if (width <= 0) return null
        if (current == width) return nativeMaxWidth[owner]
        // HyperOS may recompute its maximum on a size change; that fresh value is the native one.
        nativeMaxWidth[owner] = current
        Xp.setObjectField(owner, "expandedViewMaxWidth", width)
        return current
    }

    /**
     * Re-run HyperOS's own sizing for full-width cards so the setting applies at once rather
     * than at the island's next update; narrower cards never take the maximum and are left alone.
     */
    private fun resizeFullWidthCards() {
        val owners = (watched.values.mapNotNull { it.get() } + nativeMaxWidth.keys).toSet()
        for (owner in owners) runCatching {
            val native = nativeMaxWidth[owner] ?: field(owner, "expandedViewMaxWidth") as? Int ?: return@runCatching
            val wide = field(owner, "expandedViewMaxWidth") as? Int ?: return@runCatching
            val width = field(owner, "expandedViewWidth") as? Int ?: return@runCatching
            if (width != native && width != wide) return@runCatching
            val height = field(owner, "expandedViewHeight") as? Int ?: return@runCatching
            val data = call(owner, "getCurrentIslandData") ?: return@runCatching
            Xp.callMethod(owner, "updateExpandedSize", native, height, data)
        }
    }

    /** HyperOS's own stroke width of each island background this module flattened. */
    private val nativeRim = WeakHashMap<View, Int>()

    /**
     * [radius] null gives the background back to HyperOS. Otherwise it is drawn exactly under the
     * card: no stroke width outside it, no outline, and the card's own corners, since this view
     * sits behind the card's clip and would show its smaller native corners outside the curve.
     */
    private fun setRim(background: View, radius: Float?) {
        if (radius == null) {
            // The drawable is chosen and stroked afresh on every update; only the width returns.
            nativeRim.remove(background)?.let { Xp.callMethod(background, "setStokeWidth", it) }
            return
        }
        val width = call(background, "getStokeWidth") as? Int ?: return
        if (background !in nativeRim) nativeRim[background] = width
        Xp.callMethod(background, "setStokeWidth", 0)
        (call(background, "getDrawable") as? android.graphics.drawable.GradientDrawable)
            ?.mutate()?.let {
                val shape = it as android.graphics.drawable.GradientDrawable
                shape.setStroke(0, 0)
                shape.cornerRadius = radius
            }
        background.invalidate()
    }

    /** HyperOS's own maximum expanded width of each island this module widened. */
    private val nativeMaxWidth = WeakHashMap<View, Int>()

    /** Content views followed, with their island, and the shift this module gave each text. */
    private val watched = WeakHashMap<View, WeakReference<View>>()
    private val shifted = WeakHashMap<View, Float>()

    /** Re-place text whenever the expanded content lays out; nothing runs between layouts. */
    private fun watchContent(owner: View, content: View) {
        val known = watched[content]?.get()
        watched[content] = WeakReference(owner)
        if (known == null) content.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val island = watched[v]?.get() ?: return@addOnLayoutChangeListener
            runCatching { placeText(island, v) }.onFailure { Xp.w("Island text placement failed: $it") }
        }
        content.post { runCatching { placeText(owner, content) } }
    }

    /** A setting changed: re-place text in every followed island without waiting for layout. */
    private fun replaceAllText() = main.post {
        for ((content, owner) in watched.entries.toList()) {
            val island = owner.get() ?: continue
            runCatching { placeText(island, content) }
        }
    }

    private fun placeText(owner: View, content: View) {
        val group = content as? android.view.ViewGroup ?: return
        val texts = ArrayList<android.widget.TextView>()
        collectText(group, texts, 0)
        val layout = liftedLayout(owner)
        if (layout == null) {
            texts.forEach { unshift(it) }
            return
        }
        val width = content.width.toFloat()
        val height = content.height.toFloat()
        // Card coordinates: content sits at the pill's top in the island window.
        val lensLeft = layout.lens.left - (owner.width - content.width) / 2f
        val lensRight = lensLeft + layout.lens.width()
        val lensBottom = layout.lens.bottom - layout.pillTop.toFloat()
        val boxes = texts.map { it to textBox(it, content) }
        // Text whose column runs under the lens moves down together, as title and artist do. The
        // view's full width counts, so a long or scrolling title is placed the same as a short one.
        val under = boxes.filter { (text, b) ->
            val left = viewLeft(text, content)
            left < lensRight && left + text.width > lensLeft && b.centerY() < height / 2f
        }
        val down = under.maxOfOrNull { (_, b) ->
            IslandGesturePolicy.cameraPadding(lensBottom, b.top, layout.gap)
        } ?: 0f
        for ((text, box) in boxes) {
            val shift = if (under.any { it.first === text }) down else -IslandGesturePolicy.bottomCornerLift(
                layout.radius, width, height, box.left, box.right, box.bottom)
            setShift(text, shift)
        }
    }

    private fun collectText(group: android.view.ViewGroup, out: MutableList<android.widget.TextView>, depth: Int) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child.visibility != View.VISIBLE || child.width <= 0) continue
            if (child is android.widget.TextView && child !is android.widget.Button) out += child
            else if (child is android.view.ViewGroup && depth < 3) collectText(child, out, depth + 1)
        }
    }

    private fun viewLeft(view: View, content: View): Float {
        var x = 0f
        var v: View = view
        while (v !== content) {
            x += v.left
            v = v.parent as? View ?: break
        }
        return x
    }

    /** The drawn glyphs, in content coordinates and without this module's own shift. */
    private fun textBox(text: android.widget.TextView, content: View): RectF {
        var x = 0f
        var y = 0f
        var v: View = text
        while (v !== content) {
            x += v.left
            y += v.top
            v = v.parent as? View ?: break
        }
        val line = text.layout
        val metrics = text.paint.fontMetrics
        val baseline = text.baseline.toFloat()
        val left = if (line != null && line.lineCount > 0) text.totalPaddingLeft + line.getLineLeft(0)
            else text.paddingLeft.toFloat()
        val right = if (line != null && line.lineCount > 0) text.totalPaddingLeft + line.getLineRight(0)
            else (text.width - text.paddingRight).toFloat()
        val bottom = if (line != null && line.lineCount > 0)
            text.totalPaddingTop + line.getLineBottom(line.lineCount - 1).toFloat()
            else baseline + metrics.descent
        // Single-line and scrolling layouts can report lines far wider than the view.
        val inner = text.totalPaddingLeft.toFloat()
        val outer = (text.width - text.totalPaddingRight).toFloat()
        return RectF(x + left.coerceIn(inner, outer), y + baseline + metrics.ascent,
            x + right.coerceIn(inner, outer), y + bottom)
    }

    private fun setShift(text: View, shift: Float) {
        val old = shifted[text] ?: 0f
        if (old == shift) return
        text.translationY = text.translationY - old + shift
        if (shift == 0f) shifted.remove(text) else shifted[text] = shift
    }

    private fun unshift(text: View) = setShift(text, 0f)

    /** Media cards whose fixed width this module replaced. */
    private val widenedCards = WeakHashMap<View, Boolean>()

    /**
     * SystemUI's music card measures itself at a width fixed from resources and ignores the size
     * the island gives it. While the card is at the camera it takes the island's exact width, so
     * its artwork background fills the evenly inset card; otherwise HyperOS's own size returns.
     */
    private fun installMediaCardWidth(loader: ClassLoader) {
        val card = runCatching { Xp.findClass(MEDIA_CARD, loader) }.getOrNull() ?: return
        if (!declares(card, "onMeasure") || !declares(card, "calSizeByDensity")) return
        runCatching {
            Xp.hookAll(card, "onMeasure") { chain ->
                val view = chain.thisObject as? View ?: return@hookAll chain.proceed()
                val spec = chain.args.getOrNull(0) as? Int ?: return@hookAll chain.proceed()
                val size = View.MeasureSpec.getSize(spec)
                val exact = View.MeasureSpec.getMode(spec) == View.MeasureSpec.EXACTLY
                val config = view.resources.configuration
                val widen = top && exact && size > 0 &&
                    config.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT &&
                    config.smallestScreenWidthDp < 600 &&
                    size > (field(view, "calWidth") as? Int ?: Int.MAX_VALUE).let {
                        if (view in widenedCards) 0 else it
                    }
                when {
                    widen -> {
                        Xp.setObjectField(view, "calWidth", size)
                        widenedCards[view] = true
                    }
                    view in widenedCards && !top -> {
                        widenedCards.remove(view)
                        runCatching { Xp.callMethod(view, "calSizeByDensity") }
                    }
                }
                chain.proceed()
            }
        }.onFailure { Xp.w("Island media card width unavailable: $it") }
    }

    private fun rememberLaunch(owner: Any, pkg: String?) {
        launch = null
        val view = owner as? View ?: return
        if (pkg.isNullOrEmpty()) return
        val bounds = islandBounds(view) ?: return
        launch = Launch(pkg, view, bounds, SystemClock.uptimeMillis())
    }

    private class Ghost(val bitmap: Bitmap, val bounds: Rect)

    /** The pill as drawn now, in window coordinates; null for anything a canvas cannot copy. */
    private fun snapshot(owner: View, bounds: Rect): Ghost? = runCatching {
        if (bounds.isEmpty || !owner.isAttachedToWindow) return null
        val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            // The live pill is clipped by its hardware outline, which a canvas copy does not get.
            val radius = bounds.height() / 2f
            clipPath(Path().apply { addRoundRect(0f, 0f, bounds.width().toFloat(),
                bounds.height().toFloat(), radius, radius, Path.Direction.CW) })
            translate(-bounds.left.toFloat(), -bounds.top.toFloat())
            owner.draw(this)
        }
        val at = IntArray(2).also(owner::getLocationInWindow)
        Ghost(bitmap, Rect(bounds).apply { offset(at[0], at[1]) })
    }.getOrNull()

    /**
     * HyperOS drops the island the moment the app opens. Like its own swipe-to-hide, a copy of
     * the pill in its window's overlay draws in from both ends onto the camera cutout, its content
     * fading first, while the app grows from the same rectangle.
     */
    private fun dissolve(owner: View, ghost: Ghost) {
        val root = owner.rootView ?: return ghost.bitmap.recycle()
        val drawable = CameraShrink(ghost.bitmap, ghost.bounds, cutout(root, ghost.bounds))
        runCatching { root.overlay.add(drawable) }.onFailure { return ghost.bitmap.recycle() }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = GHOST_MS
            interpolator = PathInterpolator(0.3f, 0f, 0.1f, 1f)
            addUpdateListener { drawable.progress = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    runCatching { root.overlay.remove(drawable) }
                    ghost.bitmap.recycle()
                }
            })
            start()
        }
    }

    /**
     * The camera lens as a circle in window coordinates. The cutout's bounding rect runs from the
     * screen edge down past the lens, so only its width and horizontal centre are used; the pill
     * is drawn around the lens, so its vertical centre is the lens's.
     */
    private fun cutout(root: View, pill: Rect): Rect {
        val at = IntArray(2).also(root::getLocationOnScreen)
        val hole = runCatching { root.rootWindowInsets?.displayCutout?.boundingRectTop }.getOrNull()
            ?.takeIf { !it.isEmpty }?.let { Rect(it).apply { offset(-at[0], -at[1]) } }
            ?.takeIf { it.left < pill.right && it.right > pill.left }
        val cx = hole?.exactCenterX() ?: pill.exactCenterX()
        val cy = pill.exactCenterY()
        val r = minOf(hole?.width() ?: (pill.height() * 0.6f).toInt(), pill.height()) / 2f
        return Rect((cx - r).toInt(), (cy - r).toInt(), (cx + r).toInt(), (cy + r).toInt())
    }

    private class CameraShrink(
        private val bitmap: Bitmap,
        private val from: Rect,
        private val to: Rect,
    ) : android.graphics.drawable.Drawable() {
        private val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
        }
        private val image = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        private val clip = RectF()
        private val path = Path()

        init { bounds = Rect(from).apply { union(to) } }

        var progress = 0f
            set(value) { field = value; invalidateSelf() }

        override fun draw(canvas: Canvas) {
            val t = progress
            clip.set(lerp(from.left, to.left, t), lerp(from.top, to.top, t),
                lerp(from.right, to.right, t), lerp(from.bottom, to.bottom, t))
            val radius = clip.height() / 2f
            // The last of it melts into the lens rather than popping off.
            fill.alpha = (255 * ((1f - t) / 0.2f).coerceIn(0f, 1f)).toInt()
            canvas.drawRoundRect(clip, radius, radius, fill)
            // Content leaves within the first part of the shrink, as in HyperOS swipe-to-hide.
            val content = (1f - t / 0.45f).coerceIn(0f, 1f)
            if (content <= 0f) return
            path.rewind()
            path.addRoundRect(clip, radius, radius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(path)
            image.alpha = (255 * content).toInt()
            canvas.drawBitmap(bitmap, from.left.toFloat(), from.top.toFloat(), image)
            canvas.restore()
        }

        private fun lerp(a: Int, b: Int, t: Float) = a + (b - a) * t
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(filter: android.graphics.ColorFilter?) = Unit
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun takeLaunch(pkg: String?): Launch? {
        val current = launch ?: return null
        launch = null
        val fresh = SystemClock.uptimeMillis() - current.at <= LAUNCH_WINDOW_MS
        return if (fresh && current.pkg == pkg) current else null
    }

    /** The drawn compact pill, relative to the content view; null when HyperOS hides it. */
    private fun islandBounds(owner: View): Rect? {
        (call(owner, "getRoundedRect") as? RectF)?.takeIf { it.width() > 1f && it.height() > 1f }?.let {
            return Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
        }
        val background = call(owner, "getBackgroundView") as? View ?: return null
        if (background.width <= 1 || background.height <= 1) return null
        val at = IntArray(2).also(owner::getLocationOnScreen)
        val bg = IntArray(2).also(background::getLocationOnScreen)
        val left = bg[0] - at[0]
        val top = bg[1] - at[1]
        return Rect(left, top, left + background.width, top + background.height)
    }

    private fun canExpand(owner: Any): Boolean {
        val data = call(owner, "getCurrentIslandData")
        return compact(owner) && clickAllowed(owner) && data != null && call(data, "getView") != null
    }

    private fun expand(owner: Any): Boolean {
        expanding.set(owner)
        return try {
            releaseClicks[owner] = true
            Xp.callMethod(owner, "onIslandClick")
            (owner as? View)?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            true
        } catch (t: Throwable) {
            releaseClicks.remove(owner)
            Xp.w("Island gestures expansion failed: $t")
            false
        } finally { expanding.remove() }
    }

    private fun declares(type: Class<*>, name: String): Boolean =
        runCatching { type.declaredMethods.any { it.name == name } }.getOrDefault(false)

    private fun setFlag(owner: Any, name: String, value: Boolean): Boolean =
        runCatching { Xp.setBooleanField(owner, name, value) }.isSuccess

    private fun field(owner: Any, name: String): Any? =
        runCatching { Xp.getObjectField(owner, name) }.getOrNull()

    private fun compact(owner: Any): Boolean = IslandGesturePolicy.remap(active,
        call(owner, "getState")?.javaClass?.simpleName)

    private fun clickAllowed(owner: Any): Boolean {
        val state = call(owner, "getState") ?: return false
        val allowed = runCatching { Xp.callMethod(owner, "canClick", state) as? Boolean }.getOrNull() == true
        val pinning = call(call(owner, "getWindowState"), "getScreenPinning")
        return allowed && call(pinning, "getValue") != true
    }

    private fun call(owner: Any?, method: String): Any? = owner?.let {
        runCatching { Xp.callMethod(it, method) }.getOrNull()
    }
}
