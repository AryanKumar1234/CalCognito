package com.onion.calcognito

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Small view helpers that read their colours from a [PlayerScheme], so every sheet follows the same theme. */
class NvUi(val activity: Activity, val c: PlayerScheme) {
    private val density = activity.resources.displayMetrics.density
    fun dp(v: Int): Int = Math.round(v * density)
    fun dpf(v: Float): Float = v * density

    /** [color] with its alpha replaced by [a] (0..1), like Compose's Color.copy(alpha = a). */
    fun alpha(color: Int, a: Float): Int =
        Color.argb((a * 255f).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    fun face(weight: Int): Typeface =
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, weight, false)
        else if (weight >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

    fun shape(fill: Int, radiusDp: Float, strokeColor: Int = 0, strokeDp: Float = 0f): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dpf(radiusDp)
            setColor(fill)
            if (strokeDp > 0f) setStroke(Math.round(dpf(strokeDp)), strokeColor)
        }

    fun ripple(base: android.graphics.drawable.Drawable?, radiusDp: Float): RippleDrawable =
        RippleDrawable(
            ColorStateList.valueOf(alpha(c.onSurface, 0.12f)), base,
            GradientDrawable().apply { cornerRadius = dpf(radiusDp); setColor(Color.WHITE) }
        )

    fun text(s: String, sp: Float, color: Int, weight: Int = 400): TextView = TextView(activity).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        typeface = face(weight)
        includeFontPadding = false
    }

    fun icon(res: Int, sizeDp: Int, tint: Int): ImageView = ImageView(activity).apply {
        setImageResource(res)
        setColorFilter(tint)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    /** Nosved's sheet header: tinted icon tile, title, subtitle, and a round close button on the right. */
    fun header(iconRes: Int, title: String, subtitle: String, close: () -> Unit): View {
        val tile = FrameLayout(activity).apply {
            background = shape(c.primaryContainer, 12f)
            addView(icon(iconRes, 22, c.onPrimaryContainer), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        }
        val titles = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, 16f, c.onSurface, 700))
            addView(text(subtitle, 12f, c.onSurfaceVariant, 400), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        }
        val closeBtn = FrameLayout(activity).apply {
            background = ripple(shape(alpha(c.surfaceVariant, 0.5f), 24f), 24f)
            isClickable = true
            setOnClickListener { close() }
            addView(icon(R.drawable.ic_pv_close, 24, c.onSurface), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(tile, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(titles, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
            addView(closeBtn, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
    }

    fun sectionHeader(title: String): TextView = text(title, 14f, c.primary, 700)
}

/** Nosved's right-hand side sheet: dimmed video behind it, 24 dp rounded corners on the left, slides in from the right. */
object NvSheet {

    /** How a sheet looks. [Side] is the Decoder / Subtitle look; [Settings] is the tabbed player-settings look. */
    class Style(
        val widthLand: Float, val widthPort: Float, val bottomInPortrait: Boolean,
        val scrimAlpha: Float, val bg: (PlayerScheme, Boolean) -> Int, val border: (PlayerScheme) -> Int
    )

    val Side = Style(0.48f, 0.85f, false, 0.50f,
        { c, _ -> Color.argb(245, Color.red(c.surfaceContainerHigh), Color.green(c.surfaceContainerHigh), Color.blue(c.surfaceContainerHigh)) },
        { c -> Color.argb(64, Color.red(c.outlineVariant), Color.green(c.outlineVariant), Color.blue(c.outlineVariant)) })

    val Settings = Style(0.50f, 1.0f, true, 0.45f,
        { c, land -> Color.argb(if (land) 235 else 242, Color.red(c.background), Color.green(c.background), Color.blue(c.background)) },
        { c -> Color.argb(31, Color.red(c.onSurface), Color.green(c.onSurface), Color.blue(c.onSurface)) })

    fun show(
        activity: Activity, style: Style = Side, onClosed: () -> Unit = {},
        build: (ui: NvUi, close: () -> Unit) -> View
    ): Dialog {
        val ui = NvUi(activity, PlayerTheme.scheme(activity))
        val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
        val land = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val dm = activity.resources.displayMetrics
        val bottom = !land && style.bottomInPortrait
        val widthPx = (dm.widthPixels * (if (land) style.widthLand else style.widthPort)).toInt()
        val slideDistance = if (bottom) dm.heightPixels.toFloat() else widthPx.toFloat()

        val root = FrameLayout(activity)
        val scrim = View(activity).apply {
            setBackgroundColor(Color.argb((style.scrimAlpha * 255).toInt(), 0, 0, 0)); alpha = 0f
        }
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        val r = ui.dpf(24f)
        val sheet = FrameLayout(activity).apply {
            background = GradientDrawable().apply {
                cornerRadii = if (bottom) floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) else floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
                setColor(style.bg(ui.c, land))
                setStroke(ui.dp(1), style.border(ui.c))
            }
            isClickable = true   // taps inside the sheet must not reach the scrim
            if (bottom) translationY = slideDistance else translationX = slideDistance
        }

        var closing = false
        fun close() {
            if (closing) return
            closing = true
            scrim.animate().alpha(0f).setDuration(250).start()
            val anim = sheet.animate().setDuration(300).setInterpolator(PathInterpolator(0.4f, 0f, 1f, 1f))
            (if (bottom) anim.translationY(slideDistance) else anim.translationX(slideDistance))
                .withEndAction { try { dialog.dismiss() } catch (_: Throwable) {} }
                .start()
        }

        sheet.addView(build(ui, ::close), FrameLayout.LayoutParams(-1, if (bottom) -2 else -1))
        val lp = if (bottom) FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        else FrameLayout.LayoutParams(widthPx, -1, Gravity.END)
        root.addView(sheet, lp)
        scrim.setOnClickListener { close() }

        dialog.setContentView(root)
        dialog.setCancelable(false)
        dialog.setOnKeyListener { _, key, ev ->
            if (key == KeyEvent.KEYCODE_BACK && ev.action == KeyEvent.ACTION_UP) { close(); true } else false
        }
        dialog.setOnDismissListener { onClosed() }
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            if (Build.VERSION.SDK_INT >= 28) {
                attributes = attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            WindowCompat.setDecorFitsSystemWindows(this, false)
            WindowInsetsControllerCompat(this, decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        dialog.show()
        root.post {
            scrim.animate().alpha(1f).setDuration(250).start()
            val a = sheet.animate().setDuration(300).setInterpolator(PathInterpolator(0f, 0f, 0.2f, 1f))
            (if (bottom) a.translationY(0f) else a.translationX(0f)).start()
        }
        return dialog
    }

    // ------------------------------------------------------------------ Decoder Engine (port of DecoderSideSheet.kt)

    fun decoder(activity: Activity, current: MpvEngine.Decoder, onSelect: (MpvEngine.Decoder) -> Unit): Dialog =
        show(activity) { ui, close ->
            val c = ui.c
            val column = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ui.dp(20), ui.dp(18), ui.dp(20), 0)
            }
            column.addView(ui.header(R.drawable.ic_pv_memory, "Decoder Engine", "Active: ${current.label}", close))
            column.addView(ui.sectionHeader("Hardware & Software Decoding"),
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(16) })

            val list = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, ui.dp(20))
            }
            for (mode in MpvEngine.Decoder.values()) {
                val selected = mode == current
                val title = when (mode) { MpvEngine.Decoder.HW -> "Hardware"; MpvEngine.Decoder.HW_PLUS -> "Hardware+"; else -> "Software" }
                val desc = when (mode) {
                    MpvEngine.Decoder.HW -> "Hardware decoder using native MediaCodec GPU acceleration"
                    MpvEngine.Decoder.HW_PLUS -> "Advanced Hardware+ decoder with direct video surface rendering"
                    else -> "Software CPU decoding fallback for legacy codec compatibility"
                }
                list.addView(decoderCard(ui, selected, mode.label, title, desc) { onSelect(mode); close() },
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
            }
            val scroll = ScrollView(activity).apply { isVerticalScrollBarEnabled = false; addView(list) }
            column.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            column
        }

    private fun decoderCard(ui: NvUi, selected: Boolean, tag: String, title: String, desc: String, onClick: () -> Unit): View {
        val c = ui.c
        val fill = if (selected) ui.alpha(c.primaryContainer, 0.5f) else ui.alpha(c.surfaceContainer, 0.6f)
        val stroke = if (selected) c.primary else ui.alpha(c.outlineVariant, 0.3f)

        // Radio button: a 20 dp ring in a 24 dp box (Compose's RadioButton without a click handler), with a 10 dp dot when selected.
        val ringColor = if (selected) c.primary else c.onSurfaceVariant
        val radio = FrameLayout(ui.activity).apply {
            addView(View(ui.activity).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(ui.dp(2), ringColor); setColor(Color.TRANSPARENT) }
            }, FrameLayout.LayoutParams(ui.dp(20), ui.dp(20), Gravity.CENTER))
            if (selected) addView(View(ui.activity).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c.primary) }
            }, FrameLayout.LayoutParams(ui.dp(10), ui.dp(10), Gravity.CENTER))
        }

        val tagChip = ui.text(tag, 11f, if (selected) c.onPrimary else c.onSurfaceVariant, 900).apply {
            background = ui.shape(if (selected) c.primary else c.surfaceVariant, 6f)
            setPadding(ui.dp(7), ui.dp(2), ui.dp(7), ui.dp(2))
        }
        val titleView = ui.text(title, 14f, if (selected) c.onSurface else c.onSurfaceVariant, if (selected) 700 else 600)
        val titleRow = LinearLayout(ui.activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(tagChip)
            addView(titleView, LinearLayout.LayoutParams(-2, -2).apply { marginStart = ui.dp(8) })
        }
        val textCol = LinearLayout(ui.activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleRow)
            addView(ui.text(desc, 12f, c.onSurfaceVariant), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(2) })
        }
        val left = LinearLayout(ui.activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            addView(radio, LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)))
            addView(textCol, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(12) })
        }
        val row = LinearLayout(ui.activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
            addView(left, LinearLayout.LayoutParams(0, -2, 1f))
            if (selected) {
                val active = ui.text("ACTIVE", 10f, c.onPrimary, 700).apply {
                    background = ui.shape(c.primary, 6f)
                    setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3))
                }
                addView(active, LinearLayout.LayoutParams(-2, -2).apply { marginStart = ui.dp(8) })
            }
        }
        return FrameLayout(ui.activity).apply {
            background = ui.shape(fill, 16f, stroke, if (selected) 1.5f else 1f)
            foreground = ui.ripple(null, 16f)
            isClickable = true
            setOnClickListener { onClick() }
            addView(row, FrameLayout.LayoutParams(-1, -2))
        }
    }
}
