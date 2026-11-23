package com.onion.calcognito

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import java.io.File
import java.util.Locale

/**
 * The vault player's engine: libmpv (the same engine as Nosved Player / mpv-android) behind a small, thread-safe API.
 *
 * libmpv is a process-wide singleton, so only one [MpvEngine] can be alive at a time; creating a new one releases
 * the previous one first. All listener callbacks arrive on the main thread. Everything here is a port of the engine
 * side of Nosved Player's `MPVPlayerEngine` (decoder modes, aspect modes, enhance, subtitle / audio controls,
 * chapters, track list), trimmed to what the vault player needs.
 */
class MpvEngine(context: Context) : MPVLib.EventObserver {

    data class Track(
        val id: Int, val type: String, val title: String, val lang: String,
        val codec: String, val selected: Boolean, val external: Boolean
    )

    data class Chapter(val title: String, val timeMs: Long)

    enum class Aspect(val label: String) { FIT("Fit"), STRETCH("Stretch"), CROP("Crop"), ORIGINAL("100%") }

    enum class Decoder(val value: String, val label: String) {
        HW("mediacodec", "HW"), HW_PLUS("mediacodec-copy", "HW+"), SW("no", "SW")
    }

    interface Listener {
        fun onPlayingChanged(playing: Boolean) {}
        fun onDuration(ms: Long) {}
        fun onFileLoaded() {}
        fun onFirstFrame() {}
        fun onEnded() {}
        fun onError(message: String) {}
        fun onTracksChanged() {}
        fun onVideoSize(width: Int, height: Int) {}
    }

    private val appCtx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var released = false
    private var inited = false
    private var surfaceReady = false
    private var pendingPath: String? = null
    private var pendingStartMs = 0L
    private var pendingSubs: List<File> = emptyList()
    private var fileLoaded = false

    var listener: Listener? = null

    // Cached from mpv's property events, so the UI can poll them without calling into native code.
    @Volatile var positionMs = 0L; private set
    @Volatile var durationMs = 0L; private set
    @Volatile var bufferedMs = 0L; private set
    @Volatile var paused = true; private set
    @Volatile var eof = false; private set
    @Volatile var videoWidth = 0; private set
    @Volatile var videoHeight = 0; private set
    @Volatile var hasVideo = true; private set
    val isPlaying: Boolean get() = !paused && !eof && fileLoaded

    var decoder = Decoder.HW; private set
    var aspect = Aspect.FIT; private set

    /** The picture. Add it to the layout; mpv draws straight onto its surface, so no aspect-ratio wrapper is needed. */
    val surfaceView: SurfaceView = SurfaceView(context).apply {
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) {
                synchronized(MpvEngine) {
                    if (released || !inited) return
                    try {
                        MPVLib.attachSurface(h.surface)
                        MPVLib.setOptionString("force-window", "yes")
                        MPVLib.setPropertyString("vo", "gpu")
                        surfaceReady = true
                        loadPending()
                    } catch (e: Throwable) {
                        Log.e(TAG, "attach surface", e)
                    }
                }
            }

            override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {
                synchronized(MpvEngine) {
                    if (released || !inited) return
                    try { MPVLib.setPropertyString("android-surface-size", "${width}x$height") } catch (_: Throwable) {}
                }
            }

            override fun surfaceDestroyed(h: SurfaceHolder) {
                // Must be synchronous: the system invalidates the surface as soon as this returns.
                synchronized(MpvEngine) {
                    surfaceReady = false
                    if (released || !inited) return
                    try {
                        MPVLib.setPropertyString("vo", "null")
                        MPVLib.setOptionString("force-window", "no")
                        MPVLib.detachSurface()
                    } catch (e: Throwable) {
                        Log.e(TAG, "detach surface", e)
                    }
                }
            }
        })
    }

    /** Starts libmpv. Returns false (and reports through [Listener.onError]) if the native library can't load. */
    fun init(): Boolean {
        synchronized(MpvEngine) {
            current?.release()
            try {
                MPVLib.create(appCtx)
                val cfg = File(appCtx.filesDir, "mpv").apply { mkdirs() }
                try {
                    val font = File(cfg, "subfont.ttf")
                    if (!font.exists()) appCtx.assets.open("subfont.ttf").use { i -> font.outputStream().use { o -> i.copyTo(o) } }
                } catch (_: Throwable) {
                }
                fun opt(k: String, v: String) { try { MPVLib.setOptionString(k, v) } catch (_: Throwable) {} }
                opt("config", "no")
                opt("config-dir", cfg.path)
                opt("gpu-shader-cache-dir", appCtx.cacheDir.path)
                opt("icc-cache-dir", appCtx.cacheDir.path)
                opt("vo", "gpu")
                opt("gpu-context", "android")
                opt("hwdec", Decoder.HW.value)
                opt("profile", "fast")
                opt("force-window", "no")
                opt("idle", "yes")
                opt("keep-open", "yes")           // stay on the last frame; we show the controls ourselves
                opt("volume-max", "200")          // lets the volume swipe boost up to 200 %
                opt("sub-visibility", "yes")
                opt("sub-auto", "no")             // sidecar subtitles are added by the player (they live in the vault)
                opt("sub-fonts-dir", cfg.path)
                opt("sub-ass-override", "scale")
                opt("ytdl", "no")
                opt("cache", "yes")
                opt("demuxer-max-bytes", "32MiB")
                opt("demuxer-max-back-bytes", "16MiB")
                MPVLib.init()
                MPVLib.addObserver(this)
                MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
                MPVLib.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
                MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
                MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
                MPVLib.observeProperty("demuxer-cache-time", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
                MPVLib.observeProperty("dwidth", MPVLib.MpvFormat.MPV_FORMAT_INT64)
                MPVLib.observeProperty("dheight", MPVLib.MpvFormat.MPV_FORMAT_INT64)
                MPVLib.observeProperty("track-list", MPVLib.MpvFormat.MPV_FORMAT_NONE)
                MPVLib.observeProperty("chapter-list", MPVLib.MpvFormat.MPV_FORMAT_NONE)
                inited = true
                current = this
                return true
            } catch (e: Throwable) {
                Log.e(TAG, "mpv init failed", e)
                released = true
                val msg = "The video engine couldn't start (${e.javaClass.simpleName})"
                main.post { listener?.onError(msg) }   // [post] would skip it: the engine is already marked released
                return false
            }
        }
    }

    /** Loads [media] (plus [subs]) and starts playing at [startMs] once the picture surface exists. */
    fun start(media: File, startMs: Long, subs: List<File>) {
        pendingPath = media.absolutePath
        pendingStartMs = startMs
        pendingSubs = subs
        synchronized(MpvEngine) { if (surfaceReady) loadPending() }
    }

    private fun loadPending() {
        val path = pendingPath ?: return
        pendingPath = null
        try {
            MPVLib.setPropertyString("start", if (pendingStartMs > 0L) String.format(Locale.US, "%.3f", pendingStartMs / 1000.0) else "0")
            MPVLib.setPropertyBoolean("pause", false)
            MPVLib.command("loadfile", path)
        } catch (e: Throwable) {
            Log.e(TAG, "loadfile", e)
            post { listener?.onError("Couldn't open the file") }
        }
    }

    private inline fun <T> safe(default: T, block: () -> T): T =
        try { if (released || !inited) default else block() } catch (_: Throwable) { default }

    private fun post(r: () -> Unit) { if (!released) main.post { if (!released) r() } }

    // ---------------------------------------------------------------- playback

    fun play() = safe(Unit) { eof = false; MPVLib.setPropertyBoolean("pause", false) }
    fun pause() = safe(Unit) { MPVLib.setPropertyBoolean("pause", true) }

    fun toggle() {
        if (eof) { seekTo(0L, exact = true); play() }
        else if (paused) play() else pause()
    }

    fun seekTo(ms: Long, exact: Boolean = true) = safe(Unit) {
        val s = (ms.coerceAtLeast(0L) / 1000.0)
        MPVLib.command("seek", String.format(Locale.US, "%.3f", s), if (exact) "absolute+exact" else "absolute")
        positionMs = ms.coerceAtLeast(0L)
        eof = false
    }

    fun setSpeed(speed: Float) = safe(Unit) { MPVLib.setPropertyDouble("speed", speed.toDouble()) }
    fun setLoop(on: Boolean) = safe(Unit) { MPVLib.setPropertyString("loop-file", if (on) "inf" else "no") }
    fun setMute(on: Boolean) = safe(Unit) { MPVLib.setPropertyBoolean("mute", on) }
    fun isMuted(): Boolean = safe(false) { MPVLib.getPropertyBoolean("mute") ?: false }

    /** [boost] 0..1 = 100 %..200 % volume inside mpv (the system volume is moved separately). */
    fun setBoost(boost: Float) = safe(Unit) { MPVLib.setPropertyDouble("volume", 100.0 + 100.0 * boost.coerceIn(0f, 1f)) }

    fun stepFrame(forward: Boolean) = safe(Unit) {
        MPVLib.command(if (forward) "frame-step" else "frame-back-step")
    }

    // ---------------------------------------------------------------- tracks, chapters

    fun tracks(): List<Track> = safe(emptyList()) {
        val n = MPVLib.getPropertyInt("track-list/count") ?: 0
        val out = ArrayList<Track>(n)
        for (i in 0 until n) {
            val type = MPVLib.getPropertyString("track-list/$i/type") ?: continue
            out.add(
                Track(
                    id = MPVLib.getPropertyInt("track-list/$i/id") ?: continue,
                    type = type,
                    title = MPVLib.getPropertyString("track-list/$i/title") ?: "",
                    lang = MPVLib.getPropertyString("track-list/$i/lang") ?: "",
                    codec = MPVLib.getPropertyString("track-list/$i/codec") ?: "",
                    selected = MPVLib.getPropertyBoolean("track-list/$i/selected") ?: false,
                    external = MPVLib.getPropertyBoolean("track-list/$i/external") ?: false
                )
            )
        }
        out
    }

    fun selectAudio(id: Int) = safe(Unit) { MPVLib.setPropertyString("aid", id.toString()) }

    /** [id] 0 turns subtitles off. */
    fun selectSubtitle(id: Int) = safe(Unit) {
        if (id <= 0) {
            MPVLib.setPropertyString("sid", "no")
            MPVLib.setPropertyString("sub-visibility", "no")
        } else {
            MPVLib.setPropertyString("sid", id.toString())
            MPVLib.setPropertyString("sub-visibility", "yes")
        }
    }

    fun addSubtitle(file: File) = safe(Unit) { MPVLib.command("sub-add", file.absolutePath, "auto") }

    /** Short codec name for the title bar ("H.264", "HEVC", "AV1"...), mapped the way Nosved Player maps mpv's video-format. */
    fun videoCodecLabel(): String? = safe(null) {
        val raw = (MPVLib.getPropertyString("video-codec")?.takeIf { it.isNotBlank() }
            ?: MPVLib.getPropertyString("video-format")?.takeIf { it.isNotBlank() }) ?: return@safe null
        val f = MPVLib.getPropertyString("video-format")?.lowercase(Locale.ROOT) ?: raw.lowercase(Locale.ROOT)
        when (f) {
            "h264", "avc", "avc1" -> "H.264"
            "hevc", "h265", "hev1", "hvc1" -> "HEVC"
            "vp9", "vp09" -> "VP9"
            "vp8" -> "VP8"
            "av1", "av01" -> "AV1"
            "mpeg4", "mp4v" -> "MPEG-4"
            "vc1" -> "VC-1"
            else -> f.uppercase(Locale.ROOT)
        }
    }

    /** "HDR10" for PQ, "HLG" for hybrid log-gamma, null for ordinary video. */
    fun hdrLabel(): String? = safe(null) {
        when (MPVLib.getPropertyString("video-params/gamma")?.lowercase(Locale.ROOT)) {
            "pq" -> "HDR10"
            "hlg" -> "HLG"
            else -> null
        }
    }

    fun chapters(): List<Chapter> = safe(emptyList()) {
        val n = MPVLib.getPropertyInt("chapter-list/count") ?: 0
        val out = ArrayList<Chapter>(n)
        for (i in 0 until n) {
            val title = MPVLib.getPropertyString("chapter-list/$i/title")?.takeIf { it.isNotBlank() } ?: "Chapter ${i + 1}"
            val t = (MPVLib.getPropertyString("chapter-list/$i/time")?.toDoubleOrNull() ?: 0.0)
            out.add(Chapter(title, (t * 1000).toLong()))
        }
        out
    }

    fun currentChapter(): Int = safe(-1) { MPVLib.getPropertyInt("chapter") ?: -1 }

    // ---------------------------------------------------------------- subtitle / audio tuning

    fun subDelayMs(): Long = safe(0L) { ((MPVLib.getPropertyDouble("sub-delay") ?: 0.0) * 1000).toLong() }
    fun setSubDelayMs(ms: Long) = safe(Unit) { MPVLib.setPropertyDouble("sub-delay", ms / 1000.0) }
    fun audioDelayMs(): Long = safe(0L) { ((MPVLib.getPropertyDouble("audio-delay") ?: 0.0) * 1000).toLong() }
    fun setAudioDelayMs(ms: Long) = safe(Unit) { MPVLib.setPropertyDouble("audio-delay", ms / 1000.0) }
    fun subScale(): Float = safe(1f) { (MPVLib.getPropertyDouble("sub-scale") ?: 1.0).toFloat() }
    fun setSubScale(v: Float) = safe(Unit) { MPVLib.setPropertyDouble("sub-scale", v.toDouble()) }
    /** 0 = top, 100 = bottom (mpv's default is 100). */
    fun subPos(): Int = safe(100) { MPVLib.getPropertyInt("sub-pos") ?: 100 }
    fun setSubPos(v: Int) = safe(Unit) { MPVLib.setPropertyInt("sub-pos", v.coerceIn(0, 100)) }
    fun setSubBold(on: Boolean) = safe(Unit) { MPVLib.setPropertyString("sub-bold", if (on) "yes" else "no") }

    // ---------------------------------------------------------------- picture

    fun setDecoder(mode: Decoder) {
        decoder = mode
        safe(Unit) { MPVLib.setPropertyString("hwdec", mode.value) }
    }

    fun hwdecCurrent(): String = safe("") { MPVLib.getPropertyString("hwdec-current") ?: "" }

    fun setAspect(mode: Aspect) {
        aspect = mode
        safe(Unit) {
            when (mode) {
                Aspect.FIT -> { MPVLib.setPropertyString("keepaspect", "yes"); MPVLib.setPropertyDouble("panscan", 0.0); MPVLib.setPropertyString("video-unscaled", "no") }
                Aspect.STRETCH -> { MPVLib.setPropertyString("keepaspect", "no"); MPVLib.setPropertyDouble("panscan", 0.0); MPVLib.setPropertyString("video-unscaled", "no") }
                Aspect.CROP -> { MPVLib.setPropertyString("keepaspect", "yes"); MPVLib.setPropertyDouble("panscan", 1.0); MPVLib.setPropertyString("video-unscaled", "no") }
                Aspect.ORIGINAL -> { MPVLib.setPropertyString("keepaspect", "yes"); MPVLib.setPropertyDouble("panscan", 0.0); MPVLib.setPropertyString("video-unscaled", "yes") }
            }
        }
    }

    /** Each value is mpv's -100..100. */
    fun setEnhance(brightness: Int, contrast: Int, saturation: Int, gamma: Int, hue: Int) = safe(Unit) {
        MPVLib.setPropertyInt("brightness", brightness)
        MPVLib.setPropertyInt("contrast", contrast)
        MPVLib.setPropertyInt("saturation", saturation)
        MPVLib.setPropertyInt("gamma", gamma)
        MPVLib.setPropertyInt("hue", hue)
    }

    // ---------------------------------------------------------------- media information

    /** Label / value pairs for the information sheet. */
    fun info(): List<Pair<String, String>> = safe(emptyList()) {
        val out = ArrayList<Pair<String, String>>()
        fun s(p: String) = MPVLib.getPropertyString(p)?.takeIf { it.isNotBlank() }
        fun add(label: String, v: String?) { if (v != null) out.add(label to v) }
        add("Container", s("file-format"))
        add("Duration", s("duration")?.toDoubleOrNull()?.let { fmtClock((it * 1000).toLong()) })
        val w = MPVLib.getPropertyInt("video-params/w")
        val h = MPVLib.getPropertyInt("video-params/h")
        if (w != null && h != null && w > 0) out.add("Resolution" to "$w × $h")
        add("Video codec", s("video-codec"))
        add("Frame rate", s("container-fps")?.toDoubleOrNull()?.let { String.format(Locale.US, "%.3f fps", it) })
        add("Pixel format", s("video-params/pixelformat"))
        add("Colour", listOfNotNull(s("video-params/primaries"), s("video-params/gamma")).joinToString(" / ").ifBlank { null })
        add("Decoder", s("hwdec-current")?.let { if (it == "no") "Software" else "Hardware ($it)" })
        add("Audio codec", s("audio-codec-name") ?: s("audio-codec"))
        val ch = s("audio-params/hr-channels")
        val rate = s("audio-params/samplerate")
        add("Audio", listOfNotNull(ch, rate?.let { "$it Hz" }).joinToString(" · ").ifBlank { null })
        add("Video bitrate", s("video-bitrate")?.toDoubleOrNull()?.let { String.format(Locale.US, "%.0f kb/s", it / 1000.0) })
        add("Audio bitrate", s("audio-bitrate")?.toDoubleOrNull()?.let { String.format(Locale.US, "%.0f kb/s", it / 1000.0) })
        add("Dropped frames", s("frame-drop-count"))
        out
    }

    // ---------------------------------------------------------------- events

    override fun eventProperty(property: String) {
        when (property) {
            "track-list" -> post { listener?.onTracksChanged() }
        }
    }

    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "dwidth" -> { videoWidth = value.toInt(); notifySize() }
            "dheight" -> { videoHeight = value.toInt(); notifySize() }
        }
    }

    private fun notifySize() {
        val w = videoWidth
        val h = videoHeight
        hasVideo = w > 0 && h > 0
        post { listener?.onVideoSize(w, h) }
    }

    override fun eventProperty(property: String, value: Boolean) {
        when (property) {
            "pause" -> { paused = value; post { listener?.onPlayingChanged(isPlaying) } }
            "eof-reached" -> {
                eof = value
                if (value) post { listener?.onEnded(); listener?.onPlayingChanged(false) }
            }
        }
    }

    override fun eventProperty(property: String, value: String) {}

    override fun eventProperty(property: String, value: Double) {
        when (property) {
            "time-pos" -> positionMs = (value * 1000).toLong()
            "duration" -> { val d = (value * 1000).toLong(); if (d != durationMs) { durationMs = d; post { listener?.onDuration(d) } } }
            "demuxer-cache-time" -> bufferedMs = (value * 1000).toLong()
        }
    }

    override fun eventProperty(property: String, value: MPVNode) {}

    override fun event(eventId: Int, data: MPVNode) {
        when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                fileLoaded = true
                eof = false
                val subs = pendingSubs
                if (subs.isNotEmpty()) safe(Unit) { for (f in subs) MPVLib.command("sub-add", f.absolutePath, "auto") }
                post { listener?.onFileLoaded(); listener?.onPlayingChanged(isPlaying) }
            }
            MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> post { listener?.onFirstFrame() }
            MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                val reason = data["reason"]?.asString()
                if (reason == "error") {
                    val msg = data["file_error"]?.asString() ?: "playback error"
                    post { listener?.onError(msg) }
                }
            }
        }
    }

    fun release() {
        synchronized(MpvEngine) {
            if (released) return
            released = true
            listener = null
            main.removeCallbacksAndMessages(null)
            if (inited) {
                try { MPVLib.removeObserver(this) } catch (_: Throwable) {}
                try {
                    MPVLib.setPropertyString("vo", "null")
                    MPVLib.detachSurface()
                } catch (_: Throwable) {}
                try { MPVLib.destroy() } catch (_: Throwable) {}
            }
            inited = false
            if (current === this) current = null
        }
    }

    companion object {
        private const val TAG = "MpvEngine"
        @Volatile private var current: MpvEngine? = null

        fun fmtClock(ms: Long): String {
            val t = (ms / 1000).toInt().coerceAtLeast(0)
            val h = t / 3600
            val m = (t % 3600) / 60
            val s = t % 60
            return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
        }
    }
}
