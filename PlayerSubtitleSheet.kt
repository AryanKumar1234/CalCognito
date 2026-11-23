package com.onion.calcognito

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/** Nosved's Subtitle Settings side sheet: Tracks / Style / Sync. */
object NvSubtitles {
    private const val K_SIZE = "sub_size_scale"
    private const val K_FONT = "sub_font"
    private const val K_BG = "sub_background"
    private const val K_BOLD = "sub_bold"
    private const val K_ASS = "sub_override_ass"
    private const val K_DRAG = "sub_drag_gestures"

    /** Re-applies the saved look to mpv (call when a video opens). */
    fun applySaved(activity: Activity, eng: MpvEngine) {
        eng.setSubScale(PlayerPrefs.float(activity, K_SIZE, 1f))
        eng.setSubFont(PlayerPrefs.str(activity, K_FONT, "default"))
        eng.setSubBackground(PlayerPrefs.long(activity, K_BG, 0L).toInt())
        eng.setSubBold(PlayerPrefs.bool(activity, K_BOLD, false))
        eng.setSubOverrideAss(PlayerPrefs.bool(activity, K_ASS, false))
    }

    fun show(
        activity: Activity, eng: MpvEngine, trackName: (MpvEngine.Track, Int) -> String,
        onImport: () -> Unit, onClosed: () -> Unit
    ): Dialog = NvSheet.show(activity, NvSheet.Side, onClosed) { ui, close ->
        val c = ui.c
        val tracks = eng.tracks().filter { it.type == "sub" }
        val active = tracks.firstOrNull { it.selected }
        var tab = 0

        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(18), ui.dp(20), 0)
        }
        column.addView(ui.header(R.drawable.ic_nv_sub, "Subtitle Settings",
            active?.let { trackName(it, tracks.indexOf(it)) } ?: "No Subtitles Active", close))

        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, ui.dp(20)) }
        val scroll = ScrollView(activity).apply { isVerticalScrollBarEnabled = false; addView(body) }
        lateinit var render: () -> Unit

        fun gap(v: View, top: Int = 16) { body.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(top) }) }

        fun sectionHeader(t: String) = ui.sectionHeader(t)

        fun card(content: View): View = FrameLayout(activity).apply {
            background = ui.shape(ui.alpha(c.surfaceVariant, 0.3f), 16f)
            addView(content, FrameLayout.LayoutParams(-1, -2))
        }

        fun chip(label: String, selected: Boolean, onClick: () -> Unit): View = FrameLayout(activity).apply {
            background = ui.shape(if (selected) ui.alpha(c.primary, 0.2f) else ui.alpha(c.surfaceVariant, 0.4f), 12f,
                if (selected) c.primary else ui.alpha(c.outlineVariant, 0.4f), 1.5f)
            foreground = ui.ripple(null, 12f)
            isClickable = true
            setOnClickListener { onClick() }
            addView(ui.text(label, 13f, if (selected) c.primary else c.onSurface, 700), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }

        fun chipRow(labels: List<String>, selected: Int, onPick: (Int) -> Unit): View = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            labels.forEachIndexed { i, l ->
                addView(chip(l, i == selected) { onPick(i); render() },
                    LinearLayout.LayoutParams(0, ui.dp(44), 1f).apply { if (i > 0) marginStart = ui.dp(8) })
            }
        }

        fun round(res: Int, onClick: () -> Unit): View = FrameLayout(activity).apply {
            background = ui.ripple(ui.shape(c.secondaryContainer, 18f), 18f)
            isClickable = true
            setOnClickListener { onClick() }
            addView(ui.icon(res, 18, c.onSecondaryContainer), FrameLayout.LayoutParams(ui.dp(18), ui.dp(18), Gravity.CENTER))
        }

        fun stepper(value: Float, min: Float, max: Float, step: Float, onChange: (Float) -> Unit): View {
            lateinit var slider: NvSlider
            fun nudge(d: Int) {
                val v = Math.round((Math.round((slider.value + d * step) / step) * step).coerceIn(min, max) * 100f) / 100f
                slider.value = v; onChange(v)
            }
            slider = NvSlider(activity, c, min, max, step, value, onChange)
            return LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(round(R.drawable.ic_nv_minus) { nudge(-1) }, LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)))
                addView(slider, LinearLayout.LayoutParams(0, ui.dp(44), 1f).apply { marginStart = ui.dp(6); marginEnd = ui.dp(6) })
                addView(round(R.drawable.ic_nv_plus) { nudge(1) }, LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)))
            }
        }

        fun titledCard(icon: Int, title: String, valueView: View, content: View): View = card(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(ui.icon(icon, 18, c.primary))
                addView(ui.text(title, 14f, c.onSurface, 700), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(10) })
                addView(valueView)
            })
            addView(content, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
        })

        fun valueChip(t: String): TextView = ui.text(t, 12f, c.onPrimaryContainer, 700).apply {
            background = ui.shape(c.primaryContainer, 8f)
            setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3))
        }

        fun toggleCard(title: String, desc: String, key: String, def: Boolean, after: (Boolean) -> Unit): View {
            val on = PlayerPrefs.bool(activity, key, def)
            lateinit var sw: NvSwitch
            sw = NvSwitch(activity, c, on) { v -> PlayerPrefs.put(activity, key, v); after(v) }
            return card(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12))
                isClickable = true
                setOnClickListener { sw.toggle() }
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(ui.text(title, 14f, c.onSurface, 600))
                    addView(ui.text(desc, 12f, c.onSurfaceVariant), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(1) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(sw)
            })
        }

        // ------------------------------------------------------------ tabs
        fun tracksTab() {
            gap(card(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
                background = ui.shape(ui.alpha(c.primaryContainer, 0.5f), 16f, ui.alpha(c.primary, 0.3f), 1f)
                foreground = ui.ripple(null, 16f)
                isClickable = true
                setOnClickListener { onImport() }
                addView(FrameLayout(activity).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c.primary) }
                    addView(ui.icon(R.drawable.ic_nv_import, 20, c.onPrimary), FrameLayout.LayoutParams(ui.dp(20), ui.dp(20), Gravity.CENTER))
                }, LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)))
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(ui.text("Import External Subtitle", 14f, c.onSurface, 700))
                    addView(ui.text("Supports .srt, .ass, .ssa, .vtt formats", 12f, c.onSurfaceVariant))
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(12) })
                addView(ui.icon(R.drawable.ic_nv_chevron, 24, c.primary))
            }), 0)
            gap(sectionHeader("Available Subtitle Tracks"))
            fun trackCard(title: String, selected: Boolean, tag: String?, external: Boolean, onClick: () -> Unit): View {
                val ring = if (selected) c.primary else c.onSurfaceVariant
                val radio = FrameLayout(activity).apply {
                    addView(View(activity).apply {
                        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(ui.dp(2), ring); setColor(Color.TRANSPARENT) }
                    }, FrameLayout.LayoutParams(ui.dp(20), ui.dp(20), Gravity.CENTER))
                    if (selected) addView(View(activity).apply {
                        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c.primary) }
                    }, FrameLayout.LayoutParams(ui.dp(10), ui.dp(10), Gravity.CENTER))
                }
                return LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12))
                    background = ui.shape(if (selected) ui.alpha(c.primaryContainer, 0.5f) else ui.alpha(c.surfaceContainer, 0.6f), 14f,
                        if (selected) c.primary else ui.alpha(c.outlineVariant, 0.3f), if (selected) 1.5f else 1f)
                    foreground = ui.ripple(null, 14f)
                    isClickable = true
                    setOnClickListener { onClick() }
                    addView(radio, LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)))
                    addView(ui.text(title, 14f, if (selected) c.onSurface else c.onSurfaceVariant, if (selected) 700 else 500).apply {
                        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                    }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(10) })
                    if (tag != null) addView(ui.text(tag, 10f, if (external) c.onTertiaryContainer else c.onSecondaryContainer, 700).apply {
                        background = ui.shape(if (external) c.tertiaryContainer else c.secondaryContainer, 6f)
                        setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3))
                    }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = ui.dp(8) })
                }
            }
            gap(trackCard("None (Disabled)", active == null, null, false) { eng.selectSubtitle(0); close() }, 12)
            tracks.forEachIndexed { i, t ->
                gap(trackCard(trackName(t, i), t.selected, if (t.external) "External" else "Embedded", t.external) {
                    eng.selectSubtitle(t.id); close()
                }, 10)
            }
        }

        fun styleTab() {
            val scale = PlayerPrefs.float(activity, K_SIZE, 1f)
            val font = PlayerPrefs.str(activity, K_FONT, "default")
            val bg = PlayerPrefs.long(activity, K_BG, 0L).toInt()
            val bold = PlayerPrefs.bool(activity, K_BOLD, false)

            // Live preview
            val sample = ui.text("Sample Subtitle Text Preview", 16f * scale, Color.WHITE, if (bold) 700 else 400).apply {
                gravity = Gravity.CENTER
                typeface = when (font) {
                    "mono" -> android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                    "serif" -> android.graphics.Typeface.create(android.graphics.Typeface.SERIF, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                    else -> typeface
                }
                setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(4))
                background = when (bg) {
                    1 -> ui.shape(Color.argb(153, 0, 0, 0), 4f)
                    2 -> ui.shape(Color.BLACK, 4f)
                    else -> null
                }
            }
            val preview = FrameLayout(activity).apply {
                background = ui.shape(Color.argb(200, 0, 0, 0), 16f, ui.alpha(c.outlineVariant, 0.4f), 1f)
                addView(sample, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
                addView(ui.text("PREVIEW", 9f, ui.alpha(Color.WHITE, 0.5f), 700),
                    FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, ui.dp(8), ui.dp(10), 0) })
            }
            gap(preview, 0)
            body.getChildAt(body.childCount - 1).layoutParams.height = ui.dp(96)

            gap(titledCard(R.drawable.ic_nv_textsize, "Text Size Scale", valueChip(String.format(Locale.US, "%.1fx", scale)),
                stepper(scale, 0.5f, 2.0f, 0.1f) { v ->
                    PlayerPrefs.put(activity, K_SIZE, v); eng.setSubScale(v); sample.textSize = 16f * v
                }))
            gap(sectionHeader("Font Family"))
            val fonts = listOf("default", "mono", "sans", "serif")
            gap(chipRow(listOf("Default", "Mono", "Sans", "Serif"), fonts.indexOf(font).coerceAtLeast(0)) { i ->
                PlayerPrefs.put(activity, K_FONT, fonts[i]); eng.setSubFont(fonts[i])
            }, 8)
            gap(sectionHeader("Background Style"))
            gap(chipRow(listOf("None", "Glass", "Solid Black"), bg) { i ->
                PlayerPrefs.put(activity, K_BG, i.toLong()); eng.setSubBackground(i)
            }, 8)
            gap(sectionHeader("Typography & Style Overrides"))
            gap(toggleCard("Bold Subtitles", "Thicker lines for enhanced legibility", K_BOLD, false) { eng.setSubBold(it); render() }, 8)
            gap(toggleCard("Override Embedded ASS/SSA", "Apply app style on styled SSA/ASS captions", K_ASS, false) { eng.setSubOverrideAss(it) }, 10)
        }

        fun syncTab() {
            val delay = eng.subDelayMs()
            val delayText = ui.text(when { delay > 0 -> "+$delay ms"; delay < 0 -> "$delay ms"; else -> "0 ms (In Sync)" }, 12f, c.onSurfaceVariant)
            fun shift(by: Long) { eng.setSubDelayMs(if (by == 0L) 0L else eng.subDelayMs() + by); render() }
            fun btn(t: String, by: Long): View = FrameLayout(activity).apply {
                background = ui.shape(ui.alpha(c.surfaceVariant, 0.5f), 12f)
                foreground = ui.ripple(null, 12f)
                isClickable = true
                setOnClickListener { shift(by) }
                addView(ui.text(t, 13f, c.onSurface, 600), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            }
            val reset = FrameLayout(activity).apply {
                foreground = ui.ripple(null, 12f)
                isClickable = true
                setOnClickListener { shift(0L) }
                addView(ui.icon(R.drawable.ic_nv_history, 20, c.primary), FrameLayout.LayoutParams(ui.dp(20), ui.dp(20), Gravity.CENTER))
            }
            val buttons = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                val items = listOf(btn("-100ms", -100L), btn("-50ms", -50L), reset, btn("+50ms", 50L), btn("+100ms", 100L))
                items.forEachIndexed { i, v -> addView(v, LinearLayout.LayoutParams(0, ui.dp(44), 1f).apply { if (i > 0) marginStart = ui.dp(6) }) }
            }
            gap(titledCard(R.drawable.ic_nv_delaysync, "Subtitle Delay Sync", delayText, buttons), 0)
            val pos = 100 - eng.subPos()
            val posChip = valueChip("$pos%")
            gap(titledCard(R.drawable.ic_nv_vertalign, "Vertical Alignment Height", posChip,
                stepper(pos.toFloat(), 0f, 50f, 1f) { v -> eng.setSubPos(100 - v.toInt()); posChip.text = "${v.toInt()}%" }))
            gap(sectionHeader("Gesture Controls"))
            gap(toggleCard("Swipe & Drag Gestures", "Drag subtitles on video screen to reposition freely", K_DRAG, false) {}, 8)
        }

        // ------------------------------------------------------------ segmented control
        val segs = listOf("Tracks" to R.drawable.ic_nv_list, "Style" to R.drawable.ic_nv_style, "Sync" to R.drawable.ic_nv_sync)
        val segViews = ArrayList<LinearLayout>()
        val segBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ui.shape(Color.TRANSPARENT, 20f, c.outline, 1f)
        }
        fun paintSegs() {
            val r = ui.dpf(20f)
            segViews.forEachIndexed { i, v ->
                val sel = i == tab
                v.background = if (sel) GradientDrawable().apply {
                    setColor(c.secondaryContainer)
                    cornerRadii = floatArrayOf(
                        if (i == 0) r else 0f, if (i == 0) r else 0f, if (i == 2) r else 0f, if (i == 2) r else 0f,
                        if (i == 2) r else 0f, if (i == 2) r else 0f, if (i == 0) r else 0f, if (i == 0) r else 0f)
                } else null
                (v.getChildAt(0) as ImageView).setColorFilter(if (sel) c.onSecondaryContainer else c.onSurface)
                (v.getChildAt(1) as TextView).setTextColor(if (sel) c.onSecondaryContainer else c.onSurface)
            }
        }
        render = {
            body.removeAllViews()
            when (tab) { 0 -> tracksTab(); 1 -> styleTab(); else -> syncTab() }
            paintSegs()
        }
        segs.forEachIndexed { i, (label, icon) ->
            val v = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                isClickable = true
                setOnClickListener { tab = i; render(); scroll.scrollTo(0, 0) }
                addView(ui.icon(icon, 16, c.onSurface))
                addView(ui.text(label, 12f, c.onSurface, 500), LinearLayout.LayoutParams(-2, -2).apply { marginStart = ui.dp(8) })
            }
            segViews.add(v)
            segBar.addView(v, LinearLayout.LayoutParams(0, ui.dp(40), 1f))
            if (i < 2) segBar.addView(View(activity).apply { setBackgroundColor(c.outline) }, LinearLayout.LayoutParams(ui.dp(1), -1))
        }
        column.addView(segBar, LinearLayout.LayoutParams(-1, ui.dp(40)).apply { topMargin = ui.dp(16) })
        column.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = ui.dp(16) })
        render()
        column
    }
}
