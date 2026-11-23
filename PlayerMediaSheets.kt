package com.onion.calcognito

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Shared bits for the Audio, Chapters and Enhance side sheets. */
private fun radio(ui: NvUi, selected: Boolean): View {
    val c = ui.c
    val ring = if (selected) c.primary else c.onSurfaceVariant
    return FrameLayout(ui.activity).apply {
        addView(View(ui.activity).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(ui.dp(2), ring); setColor(Color.TRANSPARENT) }
        }, FrameLayout.LayoutParams(ui.dp(20), ui.dp(20), Gravity.CENTER))
        if (selected) addView(View(ui.activity).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c.primary) }
        }, FrameLayout.LayoutParams(ui.dp(10), ui.dp(10), Gravity.CENTER))
    }
}

private fun optionCard(ui: NvUi, title: String, subtitle: String?, selected: Boolean, iconRes: Int?, onClick: () -> Unit): View {
    val c = ui.c
    val act = ui.activity
    return LinearLayout(act).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12))
        background = ui.shape(if (selected) ui.alpha(c.primaryContainer, 0.5f) else ui.alpha(c.surfaceContainer, 0.6f), 14f,
            if (selected) c.primary else ui.alpha(c.outlineVariant, 0.3f), if (selected) 1.5f else 1f)
        foreground = ui.ripple(null, 14f)
        isClickable = true
        setOnClickListener { onClick() }
        addView(radio(ui, selected), LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)))
        if (iconRes != null) addView(ui.icon(iconRes, 18, if (selected) c.primary else c.onSurfaceVariant),
            LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)).apply { marginStart = ui.dp(10) })
        addView(LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.text(title, 14f, if (selected) c.onSurface else c.onSurfaceVariant, if (selected) 700 else 500).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            if (subtitle != null) addView(ui.text(subtitle, 12f, c.onSurfaceVariant), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(1) })
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(10) })
        if (selected) addView(ui.text("ACTIVE", 10f, c.onPrimary, 700).apply {
            background = ui.shape(c.primary, 6f)
            setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3))
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = ui.dp(8) })
    }
}

private fun sheetBody(act: Activity, ui: NvUi, close: () -> Unit, icon: Int, title: String, sub: String, fill: (LinearLayout) -> Unit): View {
    val column = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(20), ui.dp(18), ui.dp(20), 0)
    }
    column.addView(ui.header(icon, title, sub, close))
    val list = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, ui.dp(20)) }
    fill(list)
    column.addView(ScrollView(act).apply { isVerticalScrollBarEnabled = false; addView(list) },
        LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = ui.dp(8) })
    return column
}

private fun LinearLayout.addGap(ui: NvUi, v: View, top: Int) {
    addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(top) })
}

object NvAudio {
    private const val K_BOOST = "volume_booster"

    fun show(activity: Activity, eng: MpvEngine, trackName: (MpvEngine.Track, Int) -> String, onClosed: () -> Unit): Dialog =
        NvSheet.show(activity, NvSheet.Side, onClosed) { ui, close ->
            val c = ui.c
            val tracks = eng.tracks().filter { it.type == "audio" }
            val active = tracks.firstOrNull { it.selected }
            sheetBody(activity, ui, close, R.drawable.ic_pv_volume, "Audio Settings",
                active?.let { trackName(it, tracks.indexOf(it)) } ?: "No Audio Active") { list ->
                list.addGap(ui, ui.sectionHeader("Audio Streams"), 16)
                if (tracks.isEmpty()) {
                    list.addGap(ui, ui.text("No audio tracks detected in media", 14f, c.onSurfaceVariant).apply {
                        gravity = Gravity.CENTER
                        background = ui.shape(ui.alpha(c.surfaceVariant, 0.3f), 16f)
                        setPadding(ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(24))
                    }, 12)
                } else {
                    list.addGap(ui, optionCard(ui, "Disable Audio", "Mute and turn off all audio streams", false, null) { eng.disableAudio(); close() }, 12)
                    tracks.forEachIndexed { i, t ->
                        list.addGap(ui, optionCard(ui, trackName(t, i), null, t.selected, R.drawable.ic_pv_volume) { eng.selectAudio(t.id); close() }, 10)
                    }
                }
                list.addGap(ui, ui.sectionHeader("Volume & Enhancement"), 20)
                val on = PlayerPrefs.bool(activity, K_BOOST, false)
                lateinit var sw: NvSwitch
                sw = NvSwitch(activity, c, on) { v -> PlayerPrefs.put(activity, K_BOOST, v); eng.setBoost(if (v) 1f else 0f) }
                list.addGap(ui, LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12))
                    background = ui.shape(ui.alpha(c.surfaceVariant, 0.3f), 16f)
                    isClickable = true
                    setOnClickListener { sw.toggle() }
                    addView(ui.icon(R.drawable.ic_pv_volume, 20, c.primary))
                    addView(LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(ui.text("2x Volume Booster", 14f, c.onSurface, 700))
                        addView(ui.text("Amplify low-volume media audio up to 200% via software pre-gain", 12f, c.onSurfaceVariant),
                            LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(1) })
                    }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(12); marginEnd = ui.dp(8) })
                    addView(sw)
                }, 12)

                // Audio delay: kept from the original player.
                list.addGap(ui, ui.sectionHeader("Audio Delay Sync"), 20)
                val delayText = ui.text("${eng.audioDelayMs()} ms", 12f, c.onPrimaryContainer, 700).apply {
                    background = ui.shape(c.primaryContainer, 8f)
                    setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3))
                }
                fun nudge(by: Long) { eng.setAudioDelayMs(if (by == 0L) 0L else eng.audioDelayMs() + by); delayText.text = "${eng.audioDelayMs()} ms" }
                fun btn(label: String, by: Long): View = FrameLayout(activity).apply {
                    background = ui.shape(ui.alpha(c.surfaceVariant, 0.5f), 12f)
                    foreground = ui.ripple(null, 12f)
                    isClickable = true
                    setOnClickListener { nudge(by) }
                    addView(ui.text(label, 13f, c.onSurface, 600), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
                }
                list.addGap(ui, LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    background = ui.shape(ui.alpha(c.surfaceVariant, 0.3f), 16f)
                    setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
                    addView(LinearLayout(activity).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(ui.text("Audio delay", 14f, c.onSurface, 700), LinearLayout.LayoutParams(0, -2, 1f))
                        addView(delayText)
                    })
                    addView(LinearLayout(activity).apply {
                        listOf(btn("-100ms", -100L), btn("-50ms", -50L), btn("Reset", 0L), btn("+50ms", 50L), btn("+100ms", 100L))
                            .forEachIndexed { i, v -> addView(v, LinearLayout.LayoutParams(0, ui.dp(44), 1f).apply { if (i > 0) marginStart = ui.dp(6) }) }
                    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
                }, 12)
            }
        }
}

object NvChapters {
    fun show(activity: Activity, chapters: List<MpvEngine.Chapter>, current: Int, onSeek: (Long) -> Unit, onClosed: () -> Unit): Dialog =
        NvSheet.show(activity, NvSheet.Side, onClosed) { ui, close ->
            val c = ui.c
            fun time(ms: Long): String {
                val s = ms / 1000
                return if (s >= 3600) String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60) else String.format("%02d:%02d", s / 60, s % 60)
            }
            sheetBody(activity, ui, close, R.drawable.ic_pv_chapters, "Video Chapters",
                if (chapters.isEmpty()) "No chapters" else "${chapters.size} chapters") { list ->
                if (chapters.isNotEmpty() && current in chapters.indices) {
                    val ch = chapters[current]
                    list.addGap(ui, LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        background = ui.shape(ui.alpha(c.primaryContainer, 0.5f), 16f, ui.alpha(c.primary, 0.4f), 1f)
                        setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
                        addView(ui.text("CURRENTLY PLAYING", 10f, c.primary, 700))
                        addView(ui.text(ch.title, 16f, c.onSurface, 700), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(4) })
                        addView(ui.text(time(ch.timeMs), 12f, c.onSurfaceVariant), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(2) })
                    }, 16)
                }
                list.addGap(ui, ui.sectionHeader("All Chapters"), 20)
                if (chapters.isEmpty()) {
                    list.addGap(ui, ui.text("No chapters embedded in this video", 14f, c.onSurfaceVariant).apply {
                        gravity = Gravity.CENTER
                        background = ui.shape(ui.alpha(c.surfaceVariant, 0.3f), 16f)
                        setPadding(ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(24))
                    }, 12)
                }
                chapters.forEachIndexed { i, ch ->
                    val sel = i == current
                    list.addGap(ui, LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
                        background = ui.shape(if (sel) ui.alpha(c.primaryContainer, 0.4f) else ui.alpha(c.surfaceContainer, 0.6f), 14f,
                            if (sel) c.primary else ui.alpha(c.outlineVariant, 0.3f), if (sel) 1.5f else 1f)
                        foreground = ui.ripple(null, 14f)
                        isClickable = true
                        setOnClickListener { onSeek(ch.timeMs); close() }
                        addView(ui.text("${i + 1}", 13f, if (sel) c.onPrimary else c.onSecondaryContainer, 700).apply {
                            gravity = Gravity.CENTER
                            background = ui.shape(if (sel) c.primary else c.secondaryContainer, 10f)
                        }, LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)))
                        addView(LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(ui.text(ch.title, 14f, if (sel) c.onSurface else c.onSurfaceVariant, if (sel) 700 else 500).apply {
                                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                            })
                            addView(ui.text(time(ch.timeMs), 12f, c.onSurfaceVariant))
                        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(12) })
                    }, 10)
                }
            }
        }
}

object NvEnhance {
    /** [values] = brightness, contrast, saturation, gamma, hue, each -100..100. [onChange] is called after every edit. */
    fun show(activity: Activity, values: IntArray, onChange: () -> Unit, onClosed: () -> Unit): Dialog =
        NvSheet.show(activity, NvSheet.Side, onClosed) { ui, close ->
            val c = ui.c
            val names = listOf("Brightness", "Contrast", "Saturation", "Gamma", "Hue")
            sheetBody(activity, ui, close, R.drawable.ic_pv_enhance, "Enhance Picture", "Adjust the look of the video") { list ->
                val sliders = ArrayList<NvSlider>()
                val chips = ArrayList<TextView>()
                fun label(v: Int) = if (v > 0) "+$v" else "$v"
                names.forEachIndexed { i, name ->
                    val chip = ui.text(label(values[i]), 12f, c.onPrimaryContainer, 700).apply {
                        background = ui.shape(c.primaryContainer, 8f)
                        setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3))
                    }
                    chips.add(chip)
                    val slider = NvSlider(activity, c, -100f, 100f, 1f, values[i].toFloat()) { v ->
                        values[i] = v.toInt(); chip.text = label(values[i]); onChange()
                    }
                    sliders.add(slider)
                    list.addGap(ui, LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        background = ui.shape(ui.alpha(c.surfaceVariant, 0.3f), 16f)
                        setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(10))
                        addView(LinearLayout(activity).apply {
                            gravity = Gravity.CENTER_VERTICAL
                            addView(ui.text(name, 14f, c.onSurface, 700), LinearLayout.LayoutParams(0, -2, 1f))
                            addView(chip)
                        })
                        addView(slider, LinearLayout.LayoutParams(-1, ui.dp(44)).apply { topMargin = ui.dp(6) })
                    }, if (i == 0) 16 else 10)
                }
                list.addGap(ui, FrameLayout(activity).apply {
                    background = ui.shape(ui.alpha(c.surfaceVariant, 0.5f), 12f)
                    foreground = ui.ripple(null, 12f)
                    isClickable = true
                    setOnClickListener {
                        for (i in values.indices) { values[i] = 0; sliders[i].value = 0f; chips[i].text = "0" }
                        onChange()
                    }
                    addView(ui.text("Reset all", 14f, c.primary, 700), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
                }.also { it.minimumHeight = ui.dp(48) }, 16)
            }
        }
}
