package com.onion.calcognito

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/** Saved player settings (same names and defaults as Nosved Player's PlaybackSettings). */
object PlayerPrefs {
    private fun sp(c: Context) = c.getSharedPreferences("player_settings", Context.MODE_PRIVATE)
    fun str(c: Context, k: String, d: String): String = sp(c).getString(k, d) ?: d
    fun bool(c: Context, k: String, d: Boolean): Boolean = sp(c).getBoolean(k, d)
    fun float(c: Context, k: String, d: Float): Float = sp(c).getFloat(k, d)
    fun long(c: Context, k: String, d: Long): Long = sp(c).getLong(k, d)
    fun put(c: Context, k: String, v: Any) {
        val e = sp(c).edit()
        when (v) {
            is String -> e.putString(k, v)
            is Boolean -> e.putBoolean(k, v)
            is Float -> e.putFloat(k, v)
            is Long -> e.putLong(k, v)
        }
        e.apply()
    }

    // Keys
    const val DOUBLE_TAP = "double_tap_action"            // both | play_pause | fast_forward | rewind | none
    const val SEEK_MS = "double_tap_seek_ms"
    const val TWO_FINGER = "two_finger_action"            // play_pause | fast_play | mute | screenshot | pinch_zoom | none
    const val THREE_FINGER = "three_finger_action"
    const val LONG_PRESS = "long_press_enabled"
    const val HOLD_SPEED = "tap_hold_speed"
    const val LONG_SPEED = "long_press_speed"
    const val ORIENTATION = "orientation_mode"            // system | landscape | portrait | auto
    const val ASPECT = "aspect_mode"                      // fit | stretch | crop | original
    const val SOFT_BUTTONS = "soft_button_mode"           // auto_hide | show | hide
    const val BOTTOM_LAYOUT = "bottom_layout"
    const val UP_NEXT = "show_up_next"
    const val ICON_SIZE = "control_icon_size"             // small | medium | large
    const val SEEK_STYLE = "seekbar_style"                // standard | thick | slim | wavy
    const val AUTO_PLAY = "auto_play_next"
    const val SEEK_BUTTONS = "show_seek_buttons"
    const val NEXT_PREV = "show_next_prev"
    const val AMBIENT = "ambient_mode"
    const val REMAINING = "show_remaining_time"
    const val BATTERY = "show_battery_clock"
    const val STATS = "show_stats"
    const val GRADIENTS = "show_control_gradients"
    const val SAVE_BRIGHT = "save_brightness"
    const val PAUSE_OBSTRUCT = "pause_when_obstructed"
    const val KEEP_AWAKE = "keep_awake_always"
}

/** Material 3 style switch (52 x 32 dp). */
class NvSwitch(ctx: Context, private val c: PlayerScheme, checked: Boolean, private val onChange: (Boolean) -> Unit) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pos = if (checked) 1f else 0f
    var isOn = checked
        private set

    init {
        setOnClickListener { toggle() }
    }

    fun toggle() {
        isOn = !isOn
        ValueAnimator.ofFloat(pos, if (isOn) 1f else 0f).apply {
            duration = 150
            addUpdateListener { pos = it.animatedValue as Float; invalidate() }
            start()
        }
        onChange(isOn)
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(Math.round(52 * d), Math.round(32 * d))

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val track = RectF(0f, 0f, w, h)
        // Unchecked: dark track with an outline. Checked: primary container.
        paint.style = Paint.Style.FILL
        paint.color = blend(c.surfaceVariant, c.primaryContainer, pos)
        canvas.drawRoundRect(track, h / 2, h / 2, paint)
        if (pos < 1f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2 * d
            paint.color = Color.argb(((1f - pos) * 255).toInt(), Color.red(c.outline), Color.green(c.outline), Color.blue(c.outline))
            canvas.drawRoundRect(RectF(d, d, w - d, h - d), h / 2 - d, h / 2 - d, paint)
        }
        val r = (8f + 4f * pos) * d
        val cx = (16f + 20f * pos) * d
        paint.style = Paint.Style.FILL
        paint.color = blend(c.outline, c.primary, pos)
        canvas.drawCircle(cx, h / 2, r, paint)
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )
}

/** Material 3 slider with a gapped thumb: primary track, secondary-container remainder, stop dot at the end. */
class NvSlider(
    ctx: Context, private val c: PlayerScheme,
    var min: Float, var max: Float, private val step: Float, value: Float,
    private val onChange: (Float) -> Unit
) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    var value: Float = value
        set(v) { field = v.coerceIn(min, max); invalidate() }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), Math.round(44 * d))

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val cy = height / 2f
        val trackH = 16 * d; val gap = 6 * d; val thumbW = 4 * d; val thumbH = 44 * d
        val frac = if (max > min) (value - min) / (max - min) else 0f
        val tx = (w - thumbW) * frac + thumbW / 2
        val big = trackH / 2; val small = 2 * d
        fun bar(l: Float, r: Float, lr: Float, rr: Float, color: Int) {
            if (r - l <= 0f) return
            path.reset()
            path.addRoundRect(RectF(l, cy - trackH / 2, r, cy + trackH / 2), floatArrayOf(lr, lr, rr, rr, rr, rr, lr, lr), Path.Direction.CW)
            paint.style = Paint.Style.FILL; paint.color = color
            canvas.drawPath(path, paint)
        }
        bar(0f, tx - thumbW / 2 - gap, big, small, c.primary)
        bar(tx + thumbW / 2 + gap, w, small, big, c.secondaryContainer)
        paint.color = c.onSecondaryContainer
        canvas.drawCircle(w - 8 * d, cy, 2 * d, paint)
        paint.color = c.primary
        canvas.drawRoundRect(RectF(tx - thumbW / 2, cy - thumbH / 2, tx + thumbW / 2, cy + thumbH / 2), thumbW / 2, thumbW / 2, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { parent?.requestDisallowInterceptTouchEvent(false); return true }
        }
        val thumbW = 4 * d
        val f = ((e.x - thumbW / 2) / (width - thumbW)).coerceIn(0f, 1f)
        val raw = min + (max - min) * f
        val snapped = (Math.round(raw / step) * step).coerceIn(min, max)
        val v = Math.round(snapped * 100f) / 100f
        if (v != value) { value = v; onChange(v) }
        return true
    }
}

private class MaxHeightScroll(ctx: Context, private val maxPx: Int) : ScrollView(ctx) {
    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxPx, MeasureSpec.AT_MOST))
    }
}

/** Nosved's six-tab player settings sheet (Speed, Gestures, Layout, Appearance, Automation, Info). */
object NvSettings {

    fun show(
        activity: Activity,
        currentSpeed: () -> Float,
        onSpeed: (Float) -> Unit,
        mediaInfo: () -> List<Pair<String, String>>,
        onClosed: () -> Unit
    ): Dialog = NvSheet.show(activity, NvSheet.Settings, onClosed) { ui, close ->
        val c = ui.c
        val land = activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val dm = activity.resources.displayMetrics
        var tab = 0
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(24), 0, ui.dp(24), ui.dp(24))
        }
        val scroll: ScrollView = if (land) ScrollView(activity) else MaxHeightScroll(activity, (dm.heightPixels * 0.9f).toInt() - ui.dp(90))
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(body)

        lateinit var render: () -> Unit
        fun rerender() { val y = scroll.scrollY; render(); scroll.post { scroll.scrollTo(0, y) } }

        // ---------------------------------------------------------------- building blocks
        fun label(t: String) = ui.text(t, 12f, c.primary, 700).apply { setPadding(ui.dp(4), 0, 0, 0) }

        fun tile(res: Int): View = FrameLayout(activity).apply {
            background = ui.shape(c.secondaryContainer, 8f)
            addView(ui.icon(res, 18, c.onSecondaryContainer), FrameLayout.LayoutParams(ui.dp(18), ui.dp(18), Gravity.CENTER))
        }

        fun textCol(title: String, sub: String): View = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.text(title, 14f, c.onSurface, 600))
            addView(ui.text(sub, 12f, c.onSurfaceVariant), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(1) })
        }

        fun row(icon: Int, title: String, sub: String, onClick: () -> Unit): View = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12))
            foreground = ui.ripple(null, 0f)
            isClickable = true
            setOnClickListener { onClick() }
            addView(tile(icon), LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)))
            addView(textCol(title, sub), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(12) })
            addView(ui.icon(R.drawable.ic_nv_chevron, 16, c.onSurfaceVariant), LinearLayout.LayoutParams(ui.dp(16), ui.dp(16)).apply { marginStart = ui.dp(12) })
        }

        fun toggle(icon: Int, title: String, sub: String, key: String, def: Boolean, after: () -> Unit = {}): View {
            val on = PlayerPrefs.bool(activity, key, def)
            lateinit var sw: NvSwitch
            sw = NvSwitch(activity, c, on) { v -> PlayerPrefs.put(activity, key, v); after() }
            return LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12))
                foreground = ui.ripple(null, 0f)
                isClickable = true
                setOnClickListener { sw.toggle() }
                addView(tile(icon), LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)))
                addView(textCol(title, sub), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(12) })
                addView(sw, LinearLayout.LayoutParams(-2, -2).apply { marginStart = ui.dp(12) })
            }
        }

        fun divider(): View = View(activity).apply { setBackgroundColor(c.outlineVariant) }

        fun card(vararg items: View): View = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.shape(ui.alpha(c.primary, 0.08f), 16f)
            items.forEachIndexed { i, v ->
                if (i > 0) addView(divider(), LinearLayout.LayoutParams(-1, ui.dp(1)).apply { marginStart = ui.dp(52) })
                addView(v, LinearLayout.LayoutParams(-1, -2))
            }
        }

        fun round(res: Int, tint: Int, bg: Int, size: Int = 36, iconDp: Int = 18, onClick: () -> Unit): View = FrameLayout(activity).apply {
            background = ui.ripple(ui.shape(bg, size / 2f), size / 2f)
            isClickable = true
            setOnClickListener { onClick() }
            addView(ui.icon(res, iconDp, tint), FrameLayout.LayoutParams(ui.dp(iconDp), ui.dp(iconDp), Gravity.CENTER))
        }

        fun fmtX(v: Float) = String.format(Locale.US, "%.2fx", v)

        /** [-] slider [+] with 0.05 steps, as Nosved's StepSlider. */
        fun stepSlider(value: Float, min: Float, max: Float, onChange: (Float) -> Unit): View {
            val step = 0.05f
            lateinit var slider: NvSlider
            fun nudge(dir: Int) {
                val n = (Math.round((slider.value + dir * step) / step) * step).coerceIn(min, max)
                val v = Math.round(n * 100f) / 100f
                slider.value = v; onChange(v)
            }
            slider = NvSlider(activity, c, min, max, step, value, onChange)
            return LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(round(R.drawable.ic_nv_minus, c.onSecondaryContainer, c.secondaryContainer) { nudge(-1) }, LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)))
                addView(slider, LinearLayout.LayoutParams(0, ui.dp(44), 1f).apply { marginStart = ui.dp(6); marginEnd = ui.dp(6) })
                addView(round(R.drawable.ic_nv_plus, c.onSecondaryContainer, c.secondaryContainer) { nudge(1) }, LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)))
            }
        }

        fun inlineSlider(labelText: String, key: String, def: Float): View {
            val v0 = PlayerPrefs.float(activity, key, def)
            val valueText = ui.text(fmtX(v0), 12f, c.primary, 700)
            val slider = stepSlider(v0, 1.5f, 4.0f) { v -> PlayerPrefs.put(activity, key, v); valueText.text = fmtX(v) }
            val reset = round(R.drawable.ic_nv_refresh, c.onTertiaryContainer, c.tertiaryContainer, 22, 12) {
                PlayerPrefs.put(activity, key, def); rerender()
            }
            return LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8))
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(ui.text(labelText, 12f, c.onSurfaceVariant), LinearLayout.LayoutParams(0, -2, 1f))
                    addView(valueText)
                    addView(reset, LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)).apply { marginStart = ui.dp(6) })
                })
                addView(slider, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(4) })
            }
        }

        // ---------------------------------------------------------------- option dialog (PremiumSelectionDialog)
        fun pick(
            title: String, keys: List<String>, labels: List<String>, descs: List<String?>?,
            selected: String, reset: (() -> Unit)?, onPick: (String) -> Unit
        ) {
            val dlg = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = ui.shape(c.surface, 24f, ui.alpha(c.outlineVariant, 0.5f), 1f)
                setPadding(0, ui.dp(16), 0, ui.dp(16))
            }
            val titleRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.dp(24), ui.dp(8), ui.dp(24), ui.dp(8))
                addView(ui.text(title, 16f, c.onSurface, 700), LinearLayout.LayoutParams(0, -2, 1f))
                if (reset != null) addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6))
                    foreground = ui.ripple(null, 20f)
                    isClickable = true
                    setOnClickListener { reset(); dlg.dismiss(); rerender() }
                    addView(ui.icon(R.drawable.ic_nv_refresh, 16, c.primary))
                    addView(ui.text("Reset", 14f, c.primary, 500), LinearLayout.LayoutParams(-2, -2).apply { marginStart = ui.dp(6) })
                })
            }
            card.addView(titleRow)
            card.addView(View(activity).apply { setBackgroundColor(ui.alpha(c.outlineVariant, 0.6f)) },
                LinearLayout.LayoutParams(-1, ui.dp(1)).apply { topMargin = ui.dp(8) })
            val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8)) }
            keys.forEachIndexed { i, k ->
                val sel = k == selected
                val ring = if (sel) c.primary else ui.alpha(c.onSurfaceVariant, 0.6f)
                val radio = FrameLayout(activity).apply {
                    addView(View(activity).apply {
                        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(ui.dp(2), ring); setColor(Color.TRANSPARENT) }
                    }, FrameLayout.LayoutParams(ui.dp(20), ui.dp(20), Gravity.CENTER))
                    if (sel) addView(View(activity).apply {
                        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c.primary) }
                    }, FrameLayout.LayoutParams(ui.dp(10), ui.dp(10), Gravity.CENTER))
                }
                val texts = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(ui.text(labels[i], 16f, if (sel) c.primary else c.onSurface, if (sel) 600 else 400))
                    val dsc = descs?.getOrNull(i)
                    if (dsc != null) addView(ui.text(dsc, 12f, c.onSurfaceVariant), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(2) })
                }
                list.addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
                    background = ui.shape(if (sel) ui.alpha(c.primaryContainer, 0.15f) else Color.TRANSPARENT, 16f,
                        if (sel) ui.alpha(c.primary, 0.5f) else Color.TRANSPARENT, 1f)
                    foreground = ui.ripple(null, 16f)
                    isClickable = true
                    setOnClickListener { onPick(k); dlg.dismiss(); rerender() }
                    addView(radio, LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)))
                    addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(16) })
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(4); bottomMargin = ui.dp(4) })
            }
            val sc = MaxHeightScroll(activity, ui.dp(350)).apply { isVerticalScrollBarEnabled = false; addView(list) }
            card.addView(sc, LinearLayout.LayoutParams(-1, -2))
            card.addView(View(activity).apply { setBackgroundColor(ui.alpha(c.outlineVariant, 0.6f)) }, LinearLayout.LayoutParams(-1, ui.dp(1)))
            card.addView(LinearLayout(activity).apply {
                gravity = Gravity.END
                setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0)
                addView(ui.text("Cancel", 14f, c.primary, 500).apply {
                    setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
                    isClickable = true
                    setOnClickListener { dlg.dismiss() }
                })
            })
            val frame = FrameLayout(activity)
            val w = (dm.widthPixels * 0.92f).toInt().coerceAtMost(ui.dp(560))
            frame.addView(card, FrameLayout.LayoutParams(w, -2, Gravity.CENTER))
            frame.setOnClickListener { dlg.dismiss() }
            card.isClickable = true
            dlg.setContentView(frame)
            dlg.window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0.6f)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
            dlg.show()
        }

        // ---------------------------------------------------------------- option tables
        val doubleTap = listOf("both", "play_pause", "fast_forward", "rewind", "none")
        val doubleTapShort = listOf("Seek on double-tap", "Play / Pause", "Fast Forward Only", "Rewind Only", "No Action")
        val doubleTapLabel = listOf("Seek Left/Right", "Play / Pause", "Fast Forward Only", "Rewind Only", "Disable Double Tap")
        val doubleTapDesc = listOf("Double tap left to rewind, right to forward", "Double tap anywhere to toggle playback",
            "Double tap to jump forward", "Double tap to jump backward", "No gesture on double tap")
        val fingers = listOf("play_pause", "fast_play", "mute", "screenshot", "pinch_zoom", "none")
        val fingerLabel = listOf("Play / Pause", "Fast Play (2x)", "Mute / Unmute", "Take Screenshot", "Pinch to Zoom", "No Action")
        fun fingerDesc(n: String) = listOf("$n-finger tap to play or pause", "$n-finger tap to toggle fast play speed",
            "$n-finger tap to mute or unmute audio", "$n-finger tap to capture video frame",
            "Pinch gesture to zoom and pan the video", "Disable $n-finger gesture actions")
        val orient = listOf("system", "landscape", "portrait", "auto")
        val orientShort = listOf("Follow System Setting", "Force Landscape Mode", "Force Portrait Mode", "Auto-Rotate based on Sensor")
        val orientLabel = listOf("System Default", "Landscape Only", "Portrait Only", "Sensor Auto-Rotate")
        val aspects = listOf("fit", "stretch", "crop", "original")
        val aspectShort = listOf("Fit Screen (Letterbox)", "Stretch to Fill", "Crop and Zoom", "100% Original")
        val aspectLabel = listOf("Fit Screen", "Stretch", "Crop", "100% Original")
        val aspectDesc = listOf("Fit to screen (Letterbox)", "Stretch to fill screen", "Crop and zoom to fill", "Original video resolution")
        val soft = listOf("auto_hide", "show", "hide")
        val softShort = listOf("Auto-hide with controls", "Always show navigation buttons", "Always hide (Immersive mode)")
        val softLabel = listOf("Auto-hide with controls", "Always Show Navigation Bar", "Always hide (Immersive mode)")
        val sizes = listOf("small", "medium", "large")
        val sizeShort = listOf("Small (Compact)", "Medium (Default)", "Large (Spacious)")
        val sizeDesc = listOf("Smaller controls and tighter spacing to maximize viewing area", "Standard balanced controls and spacing",
            "Larger buttons and icons for easier touch accessibility")
        val styles = listOf("standard", "thick", "slim", "wavy")
        val styleShort = listOf("Standard", "Thick", "Slim", "Wavy")
        val styleLabel = listOf("Standard", "Thick", "Slim", "Wavy (Curve)")
        val durations = listOf(5000L, 10000L, 15000L, 30000L, 60000L)

        fun short(key: String, def: String, keys: List<String>, names: List<String>): String =
            names[keys.indexOf(PlayerPrefs.str(activity, key, def)).coerceAtLeast(0)]

        // ---------------------------------------------------------------- tabs
        fun speedTab(): View {
            val col = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            var editing = false
            val speedHolder = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            lateinit var drawSpeed: () -> Unit
            fun setSpeed(v: Float) { onSpeed(v); drawSpeed() }
            val slider = stepSlider(currentSpeed(), 0.25f, 4.0f) { v -> onSpeed(v); if (!editing) drawSpeed() }
            val presetViews = ArrayList<Pair<Float, View>>()
            drawSpeed = {
                speedHolder.removeAllViews()
                val cur = currentSpeed()
                if (!editing) {
                    val big = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        setPadding(ui.dp(24), ui.dp(12), ui.dp(24), ui.dp(12))
                        foreground = ui.ripple(null, 16f)
                        isClickable = true
                        setOnClickListener { editing = true; drawSpeed() }
                        addView(ui.text(fmtX(cur), 48f, c.primary, 900))
                        addView(ui.icon(R.drawable.ic_nv_edit, 24, ui.alpha(c.primary, 0.6f)), LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)).apply { marginStart = ui.dp(8) })
                    }
                    speedHolder.addView(big)
                    speedHolder.addView(ui.text("Tap speed value to type custom rate", 11f, ui.alpha(c.onSurfaceVariant, 0.55f)))
                } else {
                    val et = EditText(activity).apply {
                        setText(String.format(Locale.US, "%.2f", cur))
                        setTextColor(c.onSurface)
                        textSize = 20f
                        typeface = ui.face(700)
                        gravity = Gravity.CENTER
                        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                        imeOptions = EditorInfo.IME_ACTION_DONE
                        setSingleLine(true)
                        background = ui.shape(Color.TRANSPARENT, 4f, c.primary, 2f)
                        selectAll()
                    }
                    val hint = ui.text("Enter a speed between 0.25x and 4.0x", 12f, ui.alpha(c.onSurfaceVariant, 0.5f))
                    fun parsed(): Float? = et.text.toString().toFloatOrNull()?.takeIf { it in 0.25f..4.0f }
                    fun commit() { parsed()?.let { setSpeed(it); editing = false; drawSpeed() } }
                    et.setOnEditorActionListener { _, a, _ -> if (a == EditorInfo.IME_ACTION_DONE) { commit(); true } else false }
                    et.addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {
                            hint.setTextColor(if (parsed() != null || s.isNullOrEmpty()) ui.alpha(c.onSurfaceVariant, 0.5f) else c.error)
                        }
                        override fun afterTextChanged(s: android.text.Editable?) {}
                    })
                    val rowv = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        addView(et, LinearLayout.LayoutParams(ui.dp(120), ui.dp(56)))
                        addView(round(R.drawable.ic_nv_check, c.onPrimaryContainer, c.primaryContainer, 40, 20) { commit() }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)).apply { marginStart = ui.dp(12) })
                        addView(round(R.drawable.ic_pv_close, c.onErrorContainer, c.errorContainer, 40, 20) { editing = false; drawSpeed() }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)).apply { marginStart = ui.dp(6) })
                    }
                    speedHolder.addView(rowv)
                    speedHolder.addView(hint, LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(8) })
                }
                (slider as ViewGroup).getChildAt(1).let { (it as NvSlider).value = cur }
                presetViews.forEach { (s, v) ->
                    val sel = Math.abs(cur - s) < 0.01f
                    v.background = ui.shape(if (sel) ui.alpha(c.primary, 0.2f) else ui.alpha(c.surfaceVariant, 0.4f), 12f,
                        if (sel) c.primary else ui.alpha(c.outlineVariant, 0.4f), 1.5f)
                    ((v as FrameLayout).getChildAt(0) as TextView).setTextColor(if (sel) c.primary else c.onSurface)
                }
            }
            col.addView(label("Playback Speed"))
            col.addView(speedHolder, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(20) })
            col.addView(label("Speed Slider"), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(20) })
            col.addView(slider, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(4) })
            col.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(ui.dp(4), 0, ui.dp(4), 0)
                listOf("0.25x", "1x", "2x", "3x", "4x").forEachIndexed { i, t ->
                    if (i > 0) addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
                    addView(ui.text(t, 11f, ui.alpha(c.onSurfaceVariant, 0.55f)))
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(4) })
            col.addView(label("Presets"), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(20) })
            val grid = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).chunked(3).forEachIndexed { ri, items ->
                grid.addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    items.forEachIndexed { ci, s ->
                        val b = FrameLayout(activity).apply {
                            foreground = ui.ripple(null, 12f)
                            isClickable = true
                            setOnClickListener { setSpeed(s) }
                            addView(ui.text(fmtX(s), 14f, c.onSurface, 700), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
                        }
                        presetViews.add(s to b)
                        addView(b, LinearLayout.LayoutParams(0, ui.dp(44), 1f).apply { if (ci > 0) marginStart = ui.dp(8) })
                    }
                }, LinearLayout.LayoutParams(-1, -2).apply { if (ri > 0) topMargin = ui.dp(8) })
            }
            col.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
            drawSpeed()
            return col
        }

        fun gesturesTab(): List<View> {
            val rows = ArrayList<View>()
            rows.add(row(R.drawable.ic_nv_touch, "Double Tap Action", short(PlayerPrefs.DOUBLE_TAP, "both", doubleTap, doubleTapShort)) {
                pick("Double Tap Action", doubleTap, doubleTapLabel, doubleTapDesc, PlayerPrefs.str(activity, PlayerPrefs.DOUBLE_TAP, "both"), null) { PlayerPrefs.put(activity, PlayerPrefs.DOUBLE_TAP, it) }
            })
            val sec = PlayerPrefs.long(activity, PlayerPrefs.SEEK_MS, 10000L) / 1000
            rows.add(row(R.drawable.ic_nv_timer, "Double Tap Seek Duration", "$sec seconds") {
                pick("Seek Duration", durations.map { it.toString() }, durations.map { "${it / 1000} seconds" }, null,
                    (sec * 1000).toString(), null) { PlayerPrefs.put(activity, PlayerPrefs.SEEK_MS, it.toLong()) }
            })
            rows.add(row(R.drawable.ic_nv_touch, "Two Finger Tap Action", short(PlayerPrefs.TWO_FINGER, "play_pause", fingers, fingerLabel)) {
                pick("Two Finger Tap Action", fingers, fingerLabel, fingerDesc("Double"), PlayerPrefs.str(activity, PlayerPrefs.TWO_FINGER, "play_pause"), null) { PlayerPrefs.put(activity, PlayerPrefs.TWO_FINGER, it) }
            })
            rows.add(row(R.drawable.ic_nv_touch, "Three Finger Tap Action", short(PlayerPrefs.THREE_FINGER, "fast_play", fingers, fingerLabel)) {
                pick("Three Finger Tap Action", fingers, fingerLabel, fingerDesc("Triple"), PlayerPrefs.str(activity, PlayerPrefs.THREE_FINGER, "fast_play"), null) { PlayerPrefs.put(activity, PlayerPrefs.THREE_FINGER, it) }
            })
            rows.add(toggle(R.drawable.ic_nv_speed, "Press & Hold Acceleration", "Hold screen to temporarily speed up video", PlayerPrefs.LONG_PRESS, true) { rerender() })
            if (PlayerPrefs.bool(activity, PlayerPrefs.LONG_PRESS, true)) {
                rows.add(inlineSlider("Tap & Hold Speed Override", PlayerPrefs.HOLD_SPEED, 2.0f))
                rows.add(inlineSlider("Long Press Default Speed", PlayerPrefs.LONG_SPEED, 2.0f))
            }
            return listOf(label("Press & Tap Controls"), card(*rows.toTypedArray()))
        }

        fun layoutTab(): List<View> {
            val a = card(
                row(R.drawable.ic_nv_screenrot, "Screen Orientation", short(PlayerPrefs.ORIENTATION, "system", orient, orientShort)) {
                    pick("Screen Orientation", orient, orientLabel, null, PlayerPrefs.str(activity, PlayerPrefs.ORIENTATION, "system"), null) { PlayerPrefs.put(activity, PlayerPrefs.ORIENTATION, it) }
                },
                row(R.drawable.ic_nv_aspectratio, "Fullscreen Scale Mode", short(PlayerPrefs.ASPECT, "fit", aspects, aspectShort)) {
                    pick("Fullscreen Scaling", aspects, aspectLabel, aspectDesc, PlayerPrefs.str(activity, PlayerPrefs.ASPECT, "fit"), null) { PlayerPrefs.put(activity, PlayerPrefs.ASPECT, it) }
                },
                row(R.drawable.ic_nv_fullscreen, "System Navigation Buttons", short(PlayerPrefs.SOFT_BUTTONS, "auto_hide", soft, softShort)) {
                    pick("System Buttons Mode", soft, softLabel, null, PlayerPrefs.str(activity, PlayerPrefs.SOFT_BUTTONS, "auto_hide"), null) { PlayerPrefs.put(activity, PlayerPrefs.SOFT_BUTTONS, it) }
                },
                row(R.drawable.ic_nv_playcircle, "Playback Control Position", if (PlayerPrefs.bool(activity, PlayerPrefs.BOTTOM_LAYOUT, false)) "Below Seekbar" else "Center") {
                    val cur = if (PlayerPrefs.bool(activity, PlayerPrefs.BOTTOM_LAYOUT, false)) "true" else "false"
                    pick("Playback Control Position", listOf("false", "true"), listOf("Center", "Below Seekbar"), null, cur, null) { PlayerPrefs.put(activity, PlayerPrefs.BOTTOM_LAYOUT, it == "true") }
                },
                toggle(R.drawable.ic_nv_queuenext, "Enable Show Up Next", "Tap the video title during playback to show the queue", PlayerPrefs.UP_NEXT, true)
            )
            val b = card(
                row(R.drawable.ic_nv_photosize, "Controls Icon Size", short(PlayerPrefs.ICON_SIZE, "medium", sizes, sizeShort)) {
                    pick("Controls Icon Size", sizes, sizeShort, sizeDesc, PlayerPrefs.str(activity, PlayerPrefs.ICON_SIZE, "medium"),
                        { PlayerPrefs.put(activity, PlayerPrefs.ICON_SIZE, "medium") }) { PlayerPrefs.put(activity, PlayerPrefs.ICON_SIZE, it) }
                },
                row(R.drawable.ic_nv_waves, "Seekbar Style", short(PlayerPrefs.SEEK_STYLE, "standard", styles, styleShort)) {
                    pick("Seekbar Style", styles, styleLabel, null, PlayerPrefs.str(activity, PlayerPrefs.SEEK_STYLE, "standard"),
                        { PlayerPrefs.put(activity, PlayerPrefs.SEEK_STYLE, "standard") }) { PlayerPrefs.put(activity, PlayerPrefs.SEEK_STYLE, it) }
                },
                toggle(R.drawable.ic_nv_skipnext, "Auto-play Next Video", "Automatically load and play next video in folder", PlayerPrefs.AUTO_PLAY, false),
                toggle(R.drawable.ic_nv_fastforward, "Show Seek Buttons", "Show fast forward and rewind seek buttons in player controls", PlayerPrefs.SEEK_BUTTONS, true),
                toggle(R.drawable.ic_nv_skipnext, "Show Skip Prev/Next Buttons", "Show previous/next chapter skip buttons in player controls", PlayerPrefs.NEXT_PREV, true)
            )
            return listOf(label("Layout & Orientation"), a, label("Player Controls Customization"), b)
        }

        fun appearanceTab(): List<View> = listOf(label("Player Appearance"), card(
            toggle(R.drawable.ic_nv_bulb, "Ambient Mode", "Makes the player background glow. May increase battery usage and affect performance.", PlayerPrefs.AMBIENT, false),
            toggle(R.drawable.ic_nv_hourglass, "Show Remaining Time", "Display remaining time instead of total duration", PlayerPrefs.REMAINING, false),
            toggle(R.drawable.ic_nv_battery, "Show Battery & Clock Overlay", "Display battery status and device clock on control bar", PlayerPrefs.BATTERY, false),
            toggle(R.drawable.ic_nv_analytics, "Statistics & Diagnostics Overlay", "Display real-time stream codecs, decoders, bitrate, and playback health", PlayerPrefs.STATS, false),
            toggle(R.drawable.ic_nv_gradient, "Controls Backdrop Dimming", "Show top and bottom black fade behind player controls", PlayerPrefs.GRADIENTS, true)
        ))

        fun automationTab(): List<View> = listOf(label("Automation Behavior"), card(
            toggle(R.drawable.ic_nv_lightmode, "Save Brightness Level", "Save and restore custom brightness across playback sessions instead of matching device brightness", PlayerPrefs.SAVE_BRIGHT, false),
            toggle(R.drawable.ic_nv_pausecircle, "Pause on Obstruction", "Pause video playback automatically if screen is covered", PlayerPrefs.PAUSE_OBSTRUCT, true),
            toggle(R.drawable.ic_nv_sunny, "Keep Screen Awake Always", "Prevent screen from turning off when playback is paused or active", PlayerPrefs.KEEP_AWAKE, false)
        ))

        fun infoTab(): List<View> {
            val rows = mediaInfo()
            val out = ArrayList<View>()
            out.add(label("Media Information"))
            if (rows.isEmpty()) {
                out.add(ui.text("No media information available", 14f, c.onSurfaceVariant).apply {
                    gravity = Gravity.CENTER; setPadding(0, ui.dp(32), 0, ui.dp(32))
                })
            } else {
                val items = rows.map { (k, v) ->
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12))
                        addView(ui.text(k, 12f, c.onSurfaceVariant), LinearLayout.LayoutParams(ui.dp(110), -2))
                        addView(ui.text(v, 13f, c.onSurface, 500), LinearLayout.LayoutParams(0, -2, 1f))
                    } as View
                }
                out.add(card(*items.toTypedArray()))
            }
            return out
        }

        // ---------------------------------------------------------------- tab bar + render
        val tabIcons = listOf(R.drawable.ic_nv_speed, R.drawable.ic_nv_touch, R.drawable.ic_nv_tune,
            R.drawable.ic_nv_layers, R.drawable.ic_nv_automode, R.drawable.ic_nv_info)
        val tabViews = ArrayList<FrameLayout>()
        fun paintTabs() {
            tabViews.forEachIndexed { i, v ->
                val sel = i == tab
                v.background = ui.shape(if (sel) c.primary else Color.TRANSPARENT, 8f)
                (v.getChildAt(0) as android.widget.ImageView).setColorFilter(if (sel) c.onPrimary else c.onSurfaceVariant)
            }
        }
        render = {
            body.removeAllViews()
            val views: List<View> = when (tab) {
                0 -> listOf(speedTab())
                1 -> gesturesTab()
                2 -> layoutTab()
                3 -> appearanceTab()
                4 -> automationTab()
                else -> infoTab()
            }
            views.forEachIndexed { i, v ->
                body.addView(v, LinearLayout.LayoutParams(-1, -2).apply {
                    if (i > 0) topMargin = ui.dp(20)
                })
            }
            paintTabs()
        }
        if (!land) {
            root.addView(FrameLayout(activity).apply {
                addView(View(activity).apply { background = ui.shape(ui.alpha(c.onSurfaceVariant, 0.4f), 2f) },
                    FrameLayout.LayoutParams(ui.dp(40), ui.dp(4), Gravity.CENTER_HORIZONTAL).apply { topMargin = ui.dp(10) })
            }, LinearLayout.LayoutParams(-1, ui.dp(16)))
        }
        val tabBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ui.shape(ui.alpha(c.surfaceVariant, 0.25f), 12f)
            setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
        }
        tabIcons.forEachIndexed { i, res ->
            val t = FrameLayout(activity).apply {
                isClickable = true
                setOnClickListener { tab = i; render(); scroll.scrollTo(0, 0) }
                addView(ui.icon(res, 20, c.onSurfaceVariant), FrameLayout.LayoutParams(ui.dp(20), ui.dp(20), Gravity.CENTER))
            }
            tabViews.add(t)
            tabBar.addView(t, LinearLayout.LayoutParams(0, ui.dp(38), 1f))
        }
        root.addView(tabBar, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16))
        })
        root.addView(scroll, if (land) LinearLayout.LayoutParams(-1, 0, 1f) else LinearLayout.LayoutParams(-1, -2))
        render()
        root
    }
}
