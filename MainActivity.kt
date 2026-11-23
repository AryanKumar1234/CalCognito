package com.onion.calcognito

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.RecoverableSecurityException
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.media.ExifInterface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.OverScroller
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {

    private class Palette(
        val bg: Int, val text: Int, val icon: Int,
        val numBg: Int, val numFg: Int,
        val opBg: Int, val opFg: Int,
        val acBg: Int, val acFg: Int,
        val eqBg: Int, val eqFg: Int,
        val sciBg: Int, val sciFg: Int,
        val result: Int, val cursor: Int,
        val clearFg: Int, val ripple: Int, val divider: Int,
        val histBg: Int, val dialogBg: Int
    )

    private fun c(hex: String) = Color.parseColor(hex)

    private val light = Palette(
        bg = c("#FFF8F6"), text = c("#231917"), icon = c("#53433F"),
        numBg = c("#F1DFDA"), numFg = c("#231917"),
        opBg = c("#E7BDB2"), opFg = c("#442A22"),
        acBg = c("#FFDAD6"), acFg = c("#410002"),
        eqBg = c("#8B5000"), eqFg = c("#FFFFFF"),
        sciBg = c("#F1DFDA"), sciFg = c("#442A22"),
        result = c("#6B5E2F"), cursor = c("#9A4026"),
        clearFg = c("#BA1A1A"), ripple = c("#33000000"), divider = c("#E7D6D1"), histBg = c("#F8EBE7"), dialogBg = c("#F6E6E1")
    )
    private val dark = Palette(
        bg = c("#1D100D"), text = c("#EEE0DD"), icon = c("#D7C3BD"),
        numBg = c("#43302E"), numFg = c("#EEE0DD"),
        opBg = c("#5D4038"), opFg = c("#FFDBD2"),
        acBg = c("#93000A"), acFg = c("#FFDAD6"),
        eqBg = c("#F8BD85"), eqFg = c("#4A2800"),
        sciBg = c("#40332D"), sciFg = c("#FFDBD2"),
        result = c("#DAC58E"), cursor = c("#FFB5A2"),
        clearFg = c("#FFB5A2"), ripple = c("#33FFFFFF"), divider = c("#3A2C27"), histBg = c("#271C18"), dialogBg = c("#322621")
    )
    private lateinit var pal: Palette

    /** Whether to derive the palette from the system's Material You / wallpaper colors (Android 12+). */
    private fun dynamicColorAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private fun useDynamicColor(): Boolean = dynamicColorAvailable() && prefs.getBoolean("dynamic_theme", true)

    private fun resolvePalette(night: Boolean): Palette {
        if (useDynamicColor()) buildDynamicPalette(night)?.let { return if (night) tintDark(it) else it }
        return if (night) dark else light
    }

    /**
     * Pixel Calculator's dark surface and number keys carry the wallpaper hue more strongly than the
     * plain neutral1 tones (e.g. #10131A background, #34343C keys on a blue wallpaper). Rebuild those two
     * from the more chromatic neutral2 ramp. Falls back to the unmodified palette if a tone is missing.
     */
    private fun tintDark(p: Palette): Palette {
        val n900 = sysColor(android.R.color.system_neutral2_900) ?: return p
        val n800 = sysColor(android.R.color.system_neutral2_800) ?: return p
        val n700 = sysColor(android.R.color.system_neutral2_700) ?: return p
        val bg = androidx.core.graphics.ColorUtils.blendARGB(Color.BLACK, n900, 0.65f)
        val keys = androidx.core.graphics.ColorUtils.blendARGB(n800, n700, 0.3f)
        return Palette(
            bg = bg, text = p.text, icon = p.icon,
            numBg = keys, numFg = p.numFg,
            opBg = p.opBg, opFg = p.opFg, acBg = p.acBg, acFg = p.acFg,
            eqBg = p.eqBg, eqFg = p.eqFg, sciBg = keys, sciFg = p.sciFg,
            result = p.result, cursor = p.cursor, clearFg = p.clearFg, ripple = p.ripple,
            divider = p.divider, histBg = p.histBg, dialogBg = p.dialogBg
        )
    }

    private fun sysColor(resId: Int): Int? = try { resources.getColor(resId, theme) } catch (_: Exception) { null }

    /**
     * Android 14+ exposes the real Material 3 color roles of the wallpaper scheme
     * (android.R.color.system_surface_dark, system_tertiary_dark, ...), the same ones Pixel
     * Calculator is themed with. They are looked up by name so older systems simply fall back
     * to the tonal-palette mapping below.
     */
    private fun buildRolePalette(night: Boolean): Palette? {
        val suffix = if (night) "dark" else "light"
        fun role(name: String): Int? {
            val id = resources.getIdentifier("system_${name}_$suffix", "color", "android")
            return if (id == 0) null else sysColor(id)
        }
        return try {
            val surface = role("surface") ?: return null
            val onSurface = role("on_surface") ?: return null
            val onSurfaceVar = role("on_surface_variant") ?: return null
            val keyBg = (if (night) sysColor(android.R.color.system_neutral1_800) else null) ?: role("surface_container_highest") ?: return null
            val primC = role("primary_container") ?: return null
            val onPrimC = role("on_primary_container") ?: return null
            val secC = role("secondary_container") ?: return null
            val onSecC = role("on_secondary_container") ?: return null
            val tert = role("tertiary") ?: return null
            val onTert = role("on_tertiary") ?: return null
            val primary = role("primary") ?: return null
            val low = role("surface_container") ?: keyBg
            val high = role("surface_container_high") ?: keyBg
            Palette(
                bg = surface, text = onSurface, icon = onSurfaceVar,
                numBg = keyBg, numFg = onSurface,
                opBg = secC, opFg = onSecC,
                // Pixel Calculator tints AC with the wallpaper's primary container
                acBg = primC, acFg = onPrimC,
                eqBg = tert, eqFg = onTert,
                sciBg = keyBg, sciFg = onSecC,
                result = primary, cursor = primary,
                clearFg = if (night) Color.parseColor("#FFB4AB") else Color.parseColor("#BA1A1A"),
                ripple = Color.parseColor(if (night) "#33FFFFFF" else "#33000000"),
                divider = role("outline_variant") ?: role("surface_variant") ?: keyBg,
                histBg = low, dialogBg = high
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Maps the framework's wallpaper-derived tonal palette (system_accent1/2, system_neutral1/2) onto our Palette shape. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun buildDynamicPalette(night: Boolean): Palette? {
        buildRolePalette(night)?.let { return it }
        fun cc(resId: Int): Int = sysColor(resId) ?: throw IllegalStateException("missing dynamic color")
        return try {
            if (!night) {
                Palette(
                    bg = cc(android.R.color.system_neutral1_10), text = cc(android.R.color.system_neutral1_900), icon = cc(android.R.color.system_neutral2_700),
                    numBg = cc(android.R.color.system_neutral1_100), numFg = cc(android.R.color.system_neutral1_900),
                    opBg = cc(android.R.color.system_accent2_100), opFg = cc(android.R.color.system_accent2_900),
                    acBg = cc(android.R.color.system_accent1_100), acFg = cc(android.R.color.system_accent1_900),
                    eqBg = cc(android.R.color.system_accent3_600), eqFg = Color.WHITE,
                    sciBg = cc(android.R.color.system_neutral1_100), sciFg = cc(android.R.color.system_accent1_800),
                    result = cc(android.R.color.system_accent1_600), cursor = cc(android.R.color.system_accent1_600),
                    clearFg = Color.parseColor("#BA1A1A"), ripple = Color.parseColor("#33000000"),
                    divider = cc(android.R.color.system_neutral2_200), histBg = cc(android.R.color.system_neutral1_50), dialogBg = cc(android.R.color.system_neutral1_50)
                )
            } else {
                Palette(
                    bg = androidx.core.graphics.ColorUtils.blendARGB(cc(android.R.color.system_neutral1_1000), cc(android.R.color.system_neutral1_900), 0.6f), text = cc(android.R.color.system_neutral1_100), icon = cc(android.R.color.system_neutral2_200),
                    numBg = cc(android.R.color.system_neutral1_800), numFg = cc(android.R.color.system_neutral1_100),
                    opBg = cc(android.R.color.system_accent2_700), opFg = cc(android.R.color.system_accent2_100),
                    acBg = cc(android.R.color.system_accent1_700), acFg = cc(android.R.color.system_accent1_100),
                    eqBg = cc(android.R.color.system_accent3_200), eqFg = cc(android.R.color.system_accent3_900),
                    sciBg = cc(android.R.color.system_neutral1_700), sciFg = cc(android.R.color.system_accent1_100),
                    result = cc(android.R.color.system_accent1_200), cursor = cc(android.R.color.system_accent1_200),
                    clearFg = Color.parseColor("#FFB5A2"), ripple = Color.parseColor("#33FFFFFF"),
                    divider = cc(android.R.color.system_neutral2_700), histBg = cc(android.R.color.system_neutral1_800), dialogBg = cc(android.R.color.system_neutral1_800)
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private class CursorSpan(
        private val widthPx: Int, private val barPx: Int, private val color: Int
    ) : ReplacementSpan() {
        var visible = true
        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int = widthPx
        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            if (!visible) return
            val p = Paint(paint)
            p.color = color
            p.style = Paint.Style.FILL
            val left = x + (widthPx - barPx) / 2f
            canvas.drawRect(left, top.toFloat(), left + barPx, bottom.toFloat(), p)
        }
    }

    private lateinit var topScroll: HorizontalScrollView
    private lateinit var topView: TextView
    private lateinit var bottomView: TextView
    private lateinit var bottomScroll: HorizontalScrollView
    private lateinit var histContainer: LinearLayout
    private lateinit var histScroll: ScrollView
    private lateinit var histHandle: LinearLayout
    private lateinit var histHost: FrameLayout
    private lateinit var histThumb: View
    private lateinit var displayHost: View
    private var contentW = 0
    private var uiScale = 1f
    private var bottomBaseH = 0
    private lateinit var histBgDrawable: GradientDrawable
    private lateinit var histBtnFill: GradientDrawable
    private var histHeightPx = 0
    private var histMaxCache = 0
    private var histTotal = 0
    private var histAnim: ValueAnimator? = null
    private var pendingReopen = false
    private var pinBottom = true
    private val argb = ArgbEvaluator()
    private val histHandleHeight by lazy { dp(44) }
    private var toolRowView: View? = null
    private val hideThumb = Runnable {
        if (::histThumb.isInitialized) histThumb.animate().alpha(0f).setDuration(250).start()
    }

    private enum class DragZone { NONE, EDGE, LIST }
    private var dragZone = DragZone.NONE
    private var histDragging = false
    private var dragDownX = 0f
    private var dragDownY = 0f
    private var dragStartH = 0
    private var velocity: VelocityTracker? = null
    private var historyBtn: ImageView? = null
    private lateinit var historyList: LinearLayout
    private lateinit var vaultPanel: LinearLayout
    private lateinit var vaultFabHost: FrameLayout
    private lateinit var vaultPager: ViewPager2
    /** The list inside each swipeable tab page (0 All, 1 Images, 2 Videos, 3 Files); null while that page doesn't exist. */
    private val vaultPages = arrayOfNulls<LinearLayout>(4)
    private lateinit var vaultTitle: TextView
    private lateinit var vaultChipsRow: LinearLayout
    private lateinit var vaultCloseBtn: TextView
    /** Top-right button of the vault: the "i" (About) normally, a three-dot menu while files are selected. */
    private lateinit var vaultInfoBtn: ImageView
    /** Search: a bar under the header that filters the whole vault (every folder) by name. */
    private var vaultQuery = ""
    private var vaultSearchOpen = false
    private lateinit var vaultSearchBox: LinearLayout
    private lateinit var vaultSearchField: EditText
    private val searchDebounce = Runnable { renderVaultFiles() }
    private var vaultInfoSelectMode = false
    private lateinit var fabShape: GradientDrawable
    private lateinit var fabIcon: ImageView
    private lateinit var fabScrim: View
    private lateinit var fabMenu: LinearLayout
    private lateinit var folderPill: View
    private var fabMenuOpen = false
    private val selected = LinkedHashSet<String>()
    /** Per tab page: file path -> refreshes that row's selected look. A file can be on several pages at once. */
    private val rowUpdaters = Array(4) { HashMap<String, () -> Unit>() }
    private var currentFolder: String? = null
    private var vaultFilter = 0
    private var vaultSort = 0
    private var expandBtn: ImageView? = null
    private lateinit var radKey: TextView
    private val sciLines = mutableListOf<View>()
    private val sciKeys = mutableMapOf<String, TextView>()
    private val keyTexts = mutableListOf<Pair<TextView, Float>>()
    private var backspaceIcon: ImageView? = null
    private var inverse = false

    private val cursorSpan by lazy { CursorSpan(dp(8), dp(2), pal.cursor) }
    private val handler = Handler(Looper.getMainLooper())
    private val blink = object : Runnable {
        override fun run() {
            cursorSpan.visible = !cursorSpan.visible
            topView.invalidate()
            handler.postDelayed(this, 530)
        }
    }

    private var expression = ""
    private var previousExpression = ""
    private var justEvaluated = false
    private var errorText: String? = null
    private var sciVisible = false

    // Expand / collapse animation of the scientific keys. 0 = collapsed, 1 = expanded.
    private var sciProgress = 0f
    private var sciAnim: ValueAnimator? = null
    private var sciStacked = true          // rows above the keypad (portrait); false = side panel (landscape)
    private var sciPanelView: View? = null // the side panel when !sciStacked
    private var sciPanelWeight = 1f
    private var sciPanelGap = 0
    private var keyMul = 1f                // multiplier on the basic keys' base glyph sizes at rest
    private var sciRestSp = 24f            // glyph size of the scientific keys when fully shown
    private var historyVisible = false
    private var vaultVisible = false

    private companion object {
        const val REQ_PICK = 1001
        const val REQ_PERM = 1002
        const val REQ_NOTIF = 1003
        /** Expand / collapse of the scientific keys. */
        const val SCI_ANIM_MS = 320L
        /** In the stacked (portrait) layout the glyphs shrink by this fraction when all 8 rows are showing. */
        const val SCI_GLYPH_SHRINK = 0.2f
        const val REQ_DELETE_BATCH = 9999
        const val REQ_DELETE_LEGACY = 9998
        const val REPO_URL = "https://github.com/AryanKumar1234/CalCognito"
        const val PROFILE_URL = "https://github.com/AryanKumar1234"
    }

    private var changeMode = false
    private var hideTickShown = false
    private var holdFired = false
    private var pinResetPending = false
    private var eqButton: View? = null
    private var eraseOverlay: View? = null

    private var leavingForTask = false

    /** Hidden apps already offered Google Play services setup since the app opened (asked once, not on every open). */
    private val gmsOfferedFor = HashSet<String>()

    private var externalViewing = false
    private var afterPermission: (() -> Unit)? = null
    private var viewerDialog: Dialog? = null
    /** Shows / hides the viewer's top bar (title + menu) with an animation; set while a viewer is open. */
    private var viewerChrome: ((Boolean) -> Unit)? = null
    /** Shows / hides the viewer's own title bar; the video player hides it in favour of its Nosved top bar. */
    private var viewerBar: ((Boolean) -> Unit)? = null
    /** True while a video player is on screen, so the viewer's title bar stays out of its way. */
    private var playerContentShown = false
    /** True while the viewer's three-dot menu is open, so the bar doesn't fade out from under it. */
    private var viewerMenuOpen = false
    private var aboutDialog: Dialog? = null
    /** An update the person agreed to install but first had to allow "Install unknown apps" for; resumed in onResume. */
    private var pendingUpdate: Updater.UpdateInfo? = null
    /** Went to Settings to allow "All files access" so the built-in file picker can open; resumed in onResume. */
    private var pendingFilePicker = false
    private var updateBusy = false
    /** Vault dialogs/sheets (selection sheet, confirmations, prompts) that must close whenever the vault locks. */
    private val vaultDialogs = ArrayList<Dialog>()

    private fun trackVaultDialog(d: Dialog) {
        vaultDialogs.add(d)
        d.setOnDismissListener { vaultDialogs.remove(d) }
    }

    private fun dismissVaultDialogs() {
        for (d in vaultDialogs.toList()) try { d.dismiss() } catch (_: Exception) {}
        vaultDialogs.clear()
    }
    private var lastDisplayWidth = 0
    private var lastDisplayHeight = 0
    private val thumbCache = ConcurrentHashMap<String, Bitmap>()
    private val thumbExecutor = Executors.newSingleThreadExecutor()
    /** Video frames take longer to read than stored thumbnails, so they get their own thread and never hold up the rest. */
    private val randomThumbExecutor = Executors.newSingleThreadExecutor()
    private val migrateExecutor = Executors.newSingleThreadExecutor()
    private val galleryExecutor = Executors.newFixedThreadPool(2)
    private val migrating = ConcurrentHashMap.newKeySet<String>()
    private var hideHidden = 0
    private var hideFailed = 0
    private var hideNotRemoved = 0
    private var hideCancelled = false

    // Background transfers (adding / unhiding files). The work itself lives in TransferManager and carries on when
    // this activity is hidden or closed; the activity only shows a progress card and handles the outcome.
    private var transferCard: PixelProgressDialog? = null
    private var transferCardJob: TransferManager.Job? = null
    private var cardClosing = false
    private val finishedJobs = ArrayList<TransferManager.Job>()
    private var resultBusy = false
    private var batchDeleteCount = 0
    private val legacyDeleteQueue = mutableListOf<Uri>()
    private var legacyCurrent: Uri? = null

    private var currentPrecision = 120
    private var isInfiniteResult = false
    // Extra digits of an irrational result are computed on this thread, never on the UI thread.
    private val calcExecutor = Executors.newSingleThreadExecutor()
    private var calcFuture: Future<*>? = null
    private var calcGen = 0
    private var digitsComputing = false
    // Cached split of cachedRawResult (rebuilt only when the result string changes, not per frame).
    private var fracSource: String? = null
    private var fracCache = ""
    private var intGroupedCache = ""
    private var activeEvalExpr = ""
    private var cachedRawResult = ""
    private var scrollOffsetPx = 0f
    private var fullCache = ""
    private var scrollStarted = false
    private var pendingFlingVelocity = 0f
    private lateinit var scroller: OverScroller
    private lateinit var gestureDetector: GestureDetector

    private val flingRunnable = object : Runnable {
        override fun run() {
            if (scroller.computeScrollOffset()) {
                scrollOffsetPx = scroller.currX.toFloat().coerceAtLeast(0f)
                renderBottomResult()
                bottomView.postOnAnimation(this)
            }
        }
    }

    private val operators = setOf('+', '−', '×', '÷')
    private val prefs by lazy { getSharedPreferences("calc_history", Context.MODE_PRIVATE) }

    private fun loadFont(file: String, fallback: Typeface): Typeface = try {
        Typeface.createFromAsset(assets, "fonts/$file")
    } catch (e: Exception) { fallback }

    private val displayFont: Typeface by lazy { loadFont("GoogleSansFlex-Display.ttf", Typeface.create("sans-serif-light", Typeface.NORMAL)) }
    private val keyFont: Typeface by lazy { loadFont("GoogleSansFlex-Key.ttf", Typeface.create("sans-serif-medium", Typeface.NORMAL)) }
    private val opFont: Typeface by lazy { loadFont("GoogleSansFlex-Op.ttf", Typeface.create("sans-serif-medium", Typeface.NORMAL)) }
    private val sciFont: Typeface by lazy { loadFont("GoogleSansFlex-Label.ttf", Typeface.create("sans-serif", Typeface.NORMAL)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        val night = isNightMode()
        setTheme(if (night) R.style.AppThemeDark else R.style.AppThemeLight)
        super.onCreate(savedInstanceState)
        pal = resolvePalette(night)
        Evaluator.degrees = prefs.getBoolean("deg", true)

        window.statusBarColor = pal.bg
        window.navigationBarColor = pal.bg
        window.setBackgroundDrawable(ColorDrawable(pal.bg))
        // Draw into the camera-cutout area in landscape instead of leaving a black bar there.
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30)
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (night) 0
            else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        VaultManager.clearTemp(applicationContext)
        // Leftover partial files are only safe to delete when no transfer is running in the background.
        if (!TransferManager.isBusy()) VaultManager.cleanStaleMigrations(applicationContext)
        scroller = OverScroller(this)
        setContentView(buildUi())
        registerBackCallback()
        setupResultScrollGesture()

        expression = savedInstanceState?.getString("expr") ?: ""
        previousExpression = savedInstanceState?.getString("prev") ?: ""
        justEvaluated = savedInstanceState?.getBoolean("done") ?: false
        refresh()

        // First launch: show the "How to use" guide (skipped if a password already exists).
        if (!prefs.getBoolean("guide_seen", false)) {
            if (VaultManager.isPinSet(this)) {
                prefs.edit().putBoolean("guide_seen", true).apply()
            } else {
                window.decorView.post { if (!isFinishing && !isDestroyed) showGuide() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setSecure(vaultVisible)
        handler.removeCallbacks(blink)
        handler.postDelayed(blink, 530)
        if (externalViewing) {
            externalViewing = false
            VaultManager.clearTemp(applicationContext)
        }
        // Pick up any transfer that is still running (or finished) while we were away.
        TransferManager.setListener(transferListener)
        // Back from the "All files access" screen: open the file picker if it was allowed.
        if (pendingFilePicker) {
            pendingFilePicker = false
            if (vaultVisible && !isFinishing && !isDestroyed) {
                if (canBrowseAllFiles()) showFilePicker()
                else Toast.makeText(this, "All files access is still off. Allow it to browse files, or use the system picker.", Toast.LENGTH_LONG).show()
            }
        }
        // Back from Android's "Install unknown apps" screen: carry on with the update.
        pendingUpdate?.let { info ->
            pendingUpdate = null
            if (HiddenApps.canInstall(this)) startUpdate(info)
            else Toast.makeText(this, "Installing updates is still switched off", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(blink)

        // Transfers keep going without us. The card and the result handling come back when the vault is opened again.
        TransferManager.clearListener(transferListener)
        dismissTransferCard()

        if (leavingForTask) {
            leavingForTask = false
            return
        }
        if (changeMode) {
            changeMode = false
            expression = ""
            justEvaluated = false
            refresh()
        }
        if (pinResetPending) {
            // Never leave the "erase everything?" prompt armed behind the user's back.
            pinResetPending = false
            refresh()
        }
        viewerDialog?.dismiss()
        aboutDialog?.dismiss()
        if (vaultVisible) {
            toggleVault()
            setSecure(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        thumbExecutor.shutdownNow()
        migrateExecutor.shutdownNow()
        galleryExecutor.shutdownNow()
        calcFuture?.cancel(true)
        calcExecutor.shutdownNow()
        TransferManager.clearListener(transferListener)
        dismissTransferCard()
        viewerDialog?.dismiss()
        guideDialog?.dismiss()
        VaultManager.clearTemp(applicationContext)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rebuildUi()
        // Re-lay out the guide for the new orientation / window size.
        if (guideDialog?.isShowing == true) showGuide(guideStep)
    }

    private fun rebuildUi() {
        sciAnim?.cancel()
        histAnim?.cancel()
        histHeightPx = 0
        pendingReopen = historyVisible
        historyVisible = false
        window.statusBarColor = pal.bg
        window.setBackgroundDrawable(ColorDrawable(pal.bg))
        sciLines.clear(); sciKeys.clear(); keyTexts.clear()
        expandBtn = null; backspaceIcon = null
        lastDisplayWidth = 0
        lastDisplayHeight = 0
        setContentView(buildUi())
        setupResultScrollGesture()
        if (sciVisible) updateExpandIcon()
        updateSciLabels()
        if (vaultVisible) { renderVaultFiles(); vaultPanel.visibility = View.VISIBLE; updateSelectionUi() }
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("expr", expression)
        outState.putString("prev", previousExpression)
        outState.putBoolean("done", justEvaluated)
    }

    // Screenshots and screen recording are allowed everywhere, including the vault.
    @Suppress("UNUSED_PARAMETER")
    private fun setSecure(on: Boolean) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun isNightMode(): Boolean = when (prefs.getInt("theme", 2)) {
        0 -> false
        1 -> true
        else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun winW(): Int =
        if (resources.configuration.screenWidthDp > 0) (resources.configuration.screenWidthDp * resources.displayMetrics.density).toInt()
        else resources.displayMetrics.widthPixels

    private fun winH(): Int =
        if (resources.configuration.screenHeightDp > 0) (resources.configuration.screenHeightDp * resources.displayMetrics.density).toInt()
        else resources.displayMetrics.heightPixels

    private fun isLand() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun rippleBg(radiusDp: Int = 0): RippleDrawable {
        val mask = GradientDrawable().apply { cornerRadius = dpf(radiusDp); setColor(Color.BLACK) }
        return RippleDrawable(ColorStateList.valueOf(pal.ripple), null, mask)
    }

    private fun showStyledDialog(
        title: String, body: View?, positive: String, negative: String?,
        onNegative: () -> Unit = {}, onPositive: () -> Unit
    ): Dialog {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dpf(28); setColor(pal.dialogBg) }
            setPadding(0, dp(24), 0, dp(16))
        }
        card.addView(TextView(this).apply {
            text = title
            setTextColor(pal.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setPadding(dp(24), 0, dp(24), dp(16))
        })
        if (body != null) {
            // Keep the buttons reachable in short windows (landscape, split-screen): the body scrolls
            // inside whatever height is left after the title and button row.
            val maxBodyH = (winH() - dp(210)).coerceAtLeast(dp(96))
            val scroller = object : ScrollView(this@MainActivity) {
                override fun onMeasure(widthSpec: Int, heightSpec: Int) {
                    super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(maxBodyH, View.MeasureSpec.AT_MOST))
                }
            }.apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                addView(body, FrameLayout.LayoutParams(-1, -2))
            }
            card.addView(scroller, LinearLayout.LayoutParams(-1, -2))
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(16), dp(16), dp(16), 0)
        }
        fun button(label: String, action: () -> Unit) = TextView(this).apply {
            text = label
            setTextColor(pal.cursor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            minWidth = dp(64)
            background = rippleBg(20)
            setOnClickListener { dialog.dismiss(); action() }
        }
        if (negative != null) buttons.addView(button(negative) { onNegative() })
        buttons.addView(button(positive, onPositive))
        card.addView(buttons, LinearLayout.LayoutParams(-1, -2))
        dialog.setContentView(card)
        val w = minOf(winW() - dp(66), dp(560))
        dialog.window?.setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT)
        trackVaultDialog(dialog)
        dialog.show()
        return dialog
    }

    private fun msgBody(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(pal.icon)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setLineSpacing(0f, 1.15f)
        setPadding(dp(24), 0, dp(24), 0)
    }

    private fun progressColors() = ProgressColors(
        card = pal.dialogBg, text = pal.text, subtext = pal.icon,
        active = pal.cursor,
        track = androidx.core.graphics.ColorUtils.setAlphaComponent(pal.cursor, 60),
        tickFill = pal.cursor, tickFg = pal.dialogBg,
        ripple = pal.ripple
    )

    /** Pixel-style list dialog: rows with an icon and label; tapping a row closes the dialog and runs it. */
    private fun showListDialog(title: String, rows: List<Triple<Int, String, () -> Unit>>, cancelLabel: String = "Cancel") {
        var dlg: Dialog? = null
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for ((icon, label, action) in rows) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52)
                setPadding(dp(24), 0, dp(24), 0)
                background = rippleBg()
                isClickable = true
                setOnClickListener { dlg?.dismiss(); action() }
            }
            row.addView(ImageView(this).apply {
                setImageResource(icon)
                setColorFilter(pal.icon)
            }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(20) })
            row.addView(TextView(this).apply {
                text = label
                setTextColor(pal.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -2, 1f))
            list.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        dlg = showStyledDialog(title, ScrollView(this).apply { addView(list) }, cancelLabel, null) {}
    }

    private fun radioRows(labels: List<String>, initial: Int, onPick: (Int) -> Unit): View {
        val group = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val radios = mutableListOf<RadioButton>()
        val tint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(pal.cursor, pal.text)
        )
        labels.forEachIndexed { i, label ->
            val rb = RadioButton(this).apply {
                isChecked = i == initial
                isClickable = false
                isFocusable = false
                buttonTintList = tint
            }
            radios.add(rb)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setPadding(dp(20), 0, dp(24), 0)
                background = rippleBg()
                setOnClickListener {
                    radios.forEachIndexed { j, r -> r.isChecked = j == i }
                    onPick(i)
                }
            }
            row.addView(rb, LinearLayout.LayoutParams(-2, -2))
            row.addView(TextView(this).apply {
                text = label
                setTextColor(pal.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(dp(12), 0, 0, 0)
            }, LinearLayout.LayoutParams(-2, -2))
            group.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        return group
    }

    private fun showVideoHelp() {
        val items = listOf(
            "Tap" to "Show or hide the controls and title bar (they also hide by themselves after 3 seconds of playing)",
            "Double tap left / right" to "Back / forward 10 seconds",
            "Double tap centre" to "Pause or resume",
            "Swipe up / down on right" to "Volume (keep going past 100% to boost up to 200%)",
            "Swipe up / down on left" to "Brightness",
            "Hold anywhere" to "Play at 2x while held",
            "Two-finger tap" to "Pause or resume",
            "Three-finger tap" to "Turn 2x speed on / off",
            "Audio" to "Switch audio track and fix audio delay",
            "Subtitles" to "Pick a track, turn them off, and change delay, size and position. A .srt, .vtt or .ass file with the same name in the same folder loads automatically",
            "Fit / Stretch / Crop / 100%" to "Change how the picture fills the screen",
            "Loop" to "Repeat this file",
            "HW / HW+ / SW" to "Hardware or software decoding. Try another one if a video stutters or shows green",
            "Enhance" to "Brightness, contrast, saturation, gamma and hue",
            "Chapters" to "Jump to a chapter (only shown when the video has them)",
            "Frame" to "Step one frame back or forward (pauses the video)",
            "Mute / Info" to "Mute the sound, or see codec, resolution and decoder details",
            "Rotate" to "Switch between portrait and landscape",
            "Lock" to "Ignore touches until you tap Unlock",
            "Speed" to "0.25x to 4x, pitch stays natural"
        )
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for ((gesture, action) in items) {
            list.addView(TextView(this).apply {
                text = gesture
                setTextColor(pal.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(dp(24), dp(10), dp(24), 0)
            })
            list.addView(TextView(this).apply {
                text = action
                setTextColor(pal.icon)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(dp(24), dp(2), dp(24), dp(10))
            })
        }
        val scroll = ScrollView(this).apply { addView(list) }
        showStyledDialog("Video controls", scroll, "Got it", null) {}
    }

    private fun showThemeDialog() {
        var choice = prefs.getInt("theme", 2)
        val group = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val radios = mutableListOf<RadioButton>()
        val tint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(pal.cursor, pal.text)
        )
        listOf("Light", "Dark", "System default").forEachIndexed { i, label ->
            val rb = RadioButton(this).apply {
                isChecked = i == choice
                isClickable = false
                isFocusable = false
                buttonTintList = tint
            }
            radios.add(rb)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setPadding(dp(20), 0, dp(24), 0)
                background = rippleBg()
                setOnClickListener {
                    choice = i
                    radios.forEachIndexed { j, r -> r.isChecked = j == i }
                }
            }
            row.addView(rb, LinearLayout.LayoutParams(-2, -2))
            row.addView(TextView(this).apply {
                text = label
                setTextColor(pal.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(dp(12), 0, 0, 0)
            }, LinearLayout.LayoutParams(-2, -2))
            group.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        var dynamicChoice = useDynamicColor()
        if (dynamicColorAvailable()) {
            val dynCheck = CheckBox(this).apply {
                text = "Dynamic Theming"
                isChecked = dynamicChoice
                setTextColor(pal.text)
                buttonTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(pal.cursor, pal.text)
                )
                setOnCheckedChangeListener { _, checked -> dynamicChoice = checked }
            }
            group.addView(dynCheck, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(20), dp(8), dp(24), dp(4)) })
        }
        showStyledDialog("Choose theme", group, "OK", "Cancel") {
            val themeChanged = choice != prefs.getInt("theme", 2)
            val dynamicChanged = dynamicColorAvailable() && dynamicChoice != prefs.getBoolean("dynamic_theme", true)
            if (themeChanged || dynamicChanged) {
                prefs.edit().putInt("theme", choice).putBoolean("dynamic_theme", dynamicChoice).apply()
                recreate()
            }
        }
    }

    private fun appVersion(): String = try {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.1"
    } catch (_: Exception) { "1.1" }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Int) = v * resources.displayMetrics.density

    private fun setupResultScrollGesture() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                scroller.forceFinished(true)
                bottomView.removeCallbacks(flingRunnable)
                scrollStarted = false
                pendingFlingVelocity = 0f
                return true
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (!isInfiniteResult || errorText != null) return false
                var d = distanceX
                if (!scrollStarted) {
                    // The first event contains the whole touch-slop distance; drop it so the digits don't jump.
                    scrollStarted = true
                    val slop = ViewConfiguration.get(this@MainActivity).scaledTouchSlop.toFloat()
                    d = if (d > 0f) maxOf(0f, d - slop) else minOf(0f, d + slop)
                }
                scrollOffsetPx = (scrollOffsetPx + d).coerceAtLeast(0f)
                renderBottomResult()
                return true
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (!isInfiniteResult || errorText != null) return false
                scroller.fling(scrollOffsetPx.toInt(), 0, (-velocityX).toInt(), 0, 0, Int.MAX_VALUE / 4, 0, 0)
                bottomView.postOnAnimation(flingRunnable)
                return true
            }
            override fun onLongPress(e: MotionEvent) { copyResult() }
        })
        bottomScroll.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            isInfiniteResult && errorText == null
        }
    }

    private fun buildUi(): View {
        sciPanelView = null
        sciStacked = true
        val root = FrameLayout(this).apply { setBackgroundColor(pal.bg) }
        val wDp = resources.configuration.screenWidthDp
        val hDp = resources.configuration.screenHeightDp
        // Always use the compact 4-column Pixel-style keypad (AC row first), stretched to the
        // full window width in every orientation / window size.
        val land = wDp > hDp
        // Old side-by-side landscape layout is for phones only; tablets keep the full-width 4-column keypad.
        val phoneLand = land && resources.configuration.smallestScreenWidthDp < 600
        contentW = if (phoneLand) minOf(winW(), dp(900)) else winW()
        uiScale = if (land) (hDp / 411f).coerceIn(1f, 1.4f) else (minOf(wDp, 600) / 411f).coerceIn(1f, 1.4f)
        bottomBaseH = dp(((if (land) 44 else 72) * uiScale).toInt())

        if (phoneLand) {
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(pal.bg)
                fitsSystemWindows = true
            }
            val topBar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
            }
            historyBtn = iconButton(R.drawable.ic_history, "History") { toggleHistory() }
            topBar.addView(historyBtn, LinearLayout.LayoutParams(dp(40), dp(40)))
            topBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            topBar.addView(iconButton(R.drawable.ic_more_vert, "More options") { showMenu(it) }, LinearLayout.LayoutParams(dp(40), dp(40)))
            addTopArea(column, topBar)

            val display = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.END
                setPadding(dp(24), dp(4), dp(24), 0)
            }
            topView = TextView(this).apply {
                gravity = Gravity.END or Gravity.BOTTOM
                maxLines = 1
                setSingleLine(true)
                typeface = keyFont
                setOnLongClickListener { pasteIntoExpression(); true }
            }
            topScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(topView, FrameLayout.LayoutParams(-2, -1))
            }
            bottomView = ResultTextView(this).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                maxLines = 1
                setSingleLine(true)
                typeface = keyFont
            }
            bottomScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(bottomView, FrameLayout.LayoutParams(-2, -1))
            }
            display.addView(topScroll, LinearLayout.LayoutParams(-1, 0, 1f))
            display.addView(bottomScroll, LinearLayout.LayoutParams(-1, bottomBaseH))
            display.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
                if (r - l != lastDisplayWidth) {
                    lastDisplayWidth = r - l
                    display.post { refresh() }
                }
            }

            val expBtn = iconButton(R.drawable.ic_unfold_more, "Scientific functions") { toggleScientific() }
            expandBtn = expBtn
            val displayBox = FrameLayout(this)
            displayBox.addView(display, FrameLayout.LayoutParams(-1, -1))
            displayBox.addView(expBtn, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.BOTTOM or Gravity.START).apply {
                setMargins(dp(14), 0, 0, dp(6))
            })
            toolRowView = null
            attachDisplayHost(displayBox)
            column.addView(displayBox, LinearLayout.LayoutParams(-1, 0, 1f))

            val keypad = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(8), 0, dp(8), dp(4))
            }
            val sciPanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }
            val sciGrid = listOf(
                listOf("Inv", if (Evaluator.degrees) "Rad" else "Deg", "!"),
                listOf("sin", "cos", "tan"),
                listOf("ln", "log", "e"),
                listOf("√", "π", "^")
            )
            for (row in sciGrid) {
                val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                for (label in row) {
                    line.addView(makeSciKey(label), LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
                }
                sciPanel.addView(line, LinearLayout.LayoutParams(-1, 0, 1f))
            }
            sciLines.add(sciPanel)
            keypad.addView(sciPanel, LinearLayout.LayoutParams(0, -1, 3f))
            sciStacked = false
            sciPanelView = sciPanel
            sciPanelWeight = 3f
            sciPanelGap = 0

            val mainPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val basicRows = listOf(
                listOf("7", "8", "9", "AC", "×"),
                listOf("4", "5", "6", "( )", "−"),
                listOf("1", "2", "3", "%", "+"),
                listOf("0", ".", "⌫", "÷", "=")
            )
            for (row in basicRows) {
                val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                for (label in row) {
                    line.addView(makeKey(label), LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
                }
                mainPanel.addView(line, LinearLayout.LayoutParams(-1, 0, 1f))
            }

            keyMul = 0.7f * uiScale
            sciRestSp = 20f * uiScale
            for ((tv, sz) in keyTexts) tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sz * keyMul)
            for ((_, tv) in sciKeys) tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sciRestSp)
            keypad.addView(mainPanel, LinearLayout.LayoutParams(0, -1, 5f))
            column.addView(keypad, LinearLayout.LayoutParams(-1, 0, 2.4f))
            root.addView(column, FrameLayout.LayoutParams(contentW, -1, Gravity.CENTER_HORIZONTAL))
        } else {
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(pal.bg)
                fitsSystemWindows = true
            }
            val topBar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(6), dp(3), 0)
            }
            historyBtn = iconButton(R.drawable.ic_history, "History") { toggleHistory() }
            topBar.addView(historyBtn, LinearLayout.LayoutParams(dp(48), dp(48)))
            topBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            topBar.addView(iconButton(R.drawable.ic_more_vert, "More options") { showMenu(it) }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addTopArea(column, topBar)

            val display = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.END
                setPadding(dp(24), dp(8), dp(24), 0)
            }
            topView = TextView(this).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                maxLines = 1
                setSingleLine(true)
                typeface = keyFont
                setOnLongClickListener { pasteIntoExpression(); true }
            }
            topScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(topView, FrameLayout.LayoutParams(-2, -1))
            }
            bottomView = ResultTextView(this).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                maxLines = 1
                setSingleLine(true)
                typeface = keyFont
            }
            bottomScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(bottomView, FrameLayout.LayoutParams(-2, -1))
            }
            display.addView(topScroll, LinearLayout.LayoutParams(-1, 0, 1f))
            display.addView(bottomScroll, LinearLayout.LayoutParams(-1, bottomBaseH))
            // Pixel keeps the expression in the upper-middle of the display with free space under the result line
            val spacer = View(this)
            display.addView(spacer, LinearLayout.LayoutParams(-1, 0, 0.4f))
            // Re-fit the text whenever the window is resized (split-screen, freeform, rotation, short landscape).
            display.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
                val w = r - l
                val h = b - t
                if (w != lastDisplayWidth || h != lastDisplayHeight) {
                    lastDisplayWidth = w
                    lastDisplayHeight = h
                    v.post {
                        if (land) {
                            val blp = bottomScroll.layoutParams
                            blp.height = minOf(bottomBaseH, (h * 0.3f).toInt())
                            bottomScroll.layoutParams = blp
                        }
                        val slp = spacer.layoutParams as LinearLayout.LayoutParams
                        slp.weight = if (h < dp(200)) 0f else 0.4f
                        spacer.layoutParams = slp
                    }
                }
            }
            // Re-fit the text once the lines have their *final* size. The adjustments above change the
            // line heights after the first layout pass, so fitting only at that moment used stale (small)
            // heights, which left the digits tiny after rotating until the next keypress.
            val refit = View.OnLayoutChangeListener { v, l, t, r, b, ol, ot, or2, ob ->
                if (r - l != or2 - ol || b - t != ob - ot) v.post { refresh() }
            }
            topScroll.addOnLayoutChangeListener(refit)
            bottomScroll.addOnLayoutChangeListener(refit)
            val expBtn = iconButton(R.drawable.ic_unfold_more, "Scientific functions") { toggleScientific() }
            expandBtn = expBtn
            if (land) {
                // Landscape: keep the expander inside the display area to save vertical space.
                val displayBox = FrameLayout(this)
                displayBox.addView(display, FrameLayout.LayoutParams(-1, -1))
                displayBox.addView(expBtn, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.BOTTOM or Gravity.START).apply {
                    setMargins(dp(14), 0, 0, dp(6))
                })
                toolRowView = null
                attachDisplayHost(displayBox)
                column.addView(displayBox, LinearLayout.LayoutParams(-1, 0, 1f))
            } else {
                attachDisplayHost(display)
                column.addView(display, LinearLayout.LayoutParams(-1, 0, 1f))
                val toolRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(4), dp(24), dp(12))
                }
                toolRow.addView(expBtn, LinearLayout.LayoutParams(dp(48), dp(48)))
                toolRowView = toolRow
                column.addView(toolRow, LinearLayout.LayoutParams(-1, -2))
            }

            // Pixel-style side margins on wide landscape windows so no key touches the screen edge
            val kpSide = if (land) dp(24) else dp(2)
            val cell = (contentW - 2 * kpSide) / 4
            val keypadH = minOf(cell * 5, (winH() * (if (land) 0.5f else 0.57f)).toInt())

            // Tablet landscape: the scientific keys open as a panel to the LEFT of the keypad (sideways),
            // instead of adding rows on top. Portrait keeps the stacked rows.
            val sideSci = land
            val keypad = LinearLayout(this).apply {
                orientation = if (sideSci) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                setPadding(kpSide, 0, kpSide, dp(8))
            }
            val mainPanel: LinearLayout
            val sciRows = listOf(
                listOf("√", "π", "^", "!"),
                listOf(if (Evaluator.degrees) "Rad" else "Deg", "sin", "cos", "tan"),
                listOf("Inv", "e", "ln", "log")
            )
            if (sideSci) {
                val sciPanel = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = View.GONE
                }
                for (row in sciRows) {
                    val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    for (label in row) {
                        line.addView(makeSciKey(label), LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
                    }
                    sciPanel.addView(line, LinearLayout.LayoutParams(-1, 0, 1f))
                }
                sciLines.add(sciPanel)
                keypad.addView(sciPanel, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = dp(8) })
                sciStacked = false
                sciPanelView = sciPanel
                sciPanelWeight = 1f
                sciPanelGap = dp(8)
                mainPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                keypad.addView(mainPanel, LinearLayout.LayoutParams(0, -1, 1f))
            } else {
                for (row in sciRows) {
                    val line = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        visibility = View.GONE
                    }
                    for (label in row) {
                        line.addView(makeSciKey(label), LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
                    }
                    sciLines.add(line)
                    keypad.addView(line, LinearLayout.LayoutParams(-1, 0, 1f))
                }
                mainPanel = keypad
            }

            val basicRows = listOf(
                listOf("AC", "( )", "%", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "−"),
                listOf("1", "2", "3", "+"),
                listOf("0", ".", "⌫", "=")
            )
            for (row in basicRows) {
                val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                for (label in row) {
                    line.addView(makeKey(label), LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
                }
                mainPanel.addView(line, LinearLayout.LayoutParams(-1, 0, 1f))
            }
            // Scale glyphs to the real key height so tall/wide windows and short landscape ones both fit.
            val rowDp = keypadH / resources.displayMetrics.density / 5f
            val keyScale = minOf(uiScale, rowDp / 80f).coerceAtLeast(0.6f)
            // Tablet landscape (like Google's calculator): slightly larger, bolder key labels.
            val tabBoost = if (land) 1.25f else 1f
            fun semiBold(tf: Typeface?): Typeface =
                if (Build.VERSION.SDK_INT >= 28) Typeface.create(tf ?: Typeface.DEFAULT, 600, false)
                else Typeface.create(tf ?: Typeface.DEFAULT, Typeface.BOLD)
            keyMul = keyScale * tabBoost
            sciRestSp = 24f * keyScale * tabBoost
            for ((tv, sz) in keyTexts) {
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sz * keyMul)
                if (land) tv.typeface = semiBold(tv.typeface)
            }
            for ((_, tv) in sciKeys) {
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sciRestSp)
                if (land) tv.typeface = semiBold(tv.typeface)
            }
            column.addView(keypad, LinearLayout.LayoutParams(-1, keypadH))
            root.addView(column, FrameLayout.LayoutParams(contentW, -1, Gravity.CENTER_HORIZONTAL))
        }

        val headerTop = if (land) dp(4) else dp(20)

        vaultPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(pal.bg)
            fitsSystemWindows = true
            isClickable = true
            isFocusable = true
        }
        val vHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), headerTop, dp(8), dp(8))
            gravity = Gravity.CENTER_VERTICAL
        }
        vaultTitle = TextView(this).apply {
            text = "CalCognito"
            setTextColor(pal.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), dp(8), dp(8))
            // Inside a folder the title is the way back; at the top it is a plain-looking link to the project page.
            setOnClickListener {
                if (selected.isEmpty()) {
                    if (currentFolder != null) {
                        currentFolder = null
                        renderVaultFiles()
                    } else {
                        openUrl(REPO_URL)
                    }
                }
            }
        }
        vHeader.addView(vaultTitle, LinearLayout.LayoutParams(0, -2, 1f))
        vaultCloseBtn = TextView(this).apply {
            text = "Close"
            setTextColor(pal.sciFg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = RippleDrawable(
                ColorStateList.valueOf(pal.ripple),
                GradientDrawable().apply { cornerRadius = dpf(100); setColor(pal.numBg) },
                GradientDrawable().apply { cornerRadius = dpf(100); setColor(Color.BLACK) }
            )
            setOnClickListener { if (selected.isNotEmpty()) clearSelection() else toggleVault() }
        }
        vHeader.addView(iconButton(R.drawable.ic_search, "Search") { toggleVaultSearch() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        vHeader.addView(vaultCloseBtn)
        vaultInfoSelectMode = selected.isNotEmpty()
        vaultInfoBtn = iconButton(
            if (vaultInfoSelectMode) R.drawable.ic_more_vert else R.drawable.ic_settings,
            if (vaultInfoSelectMode) "More options" else "Settings"
        ) { anchor -> if (selected.isNotEmpty()) showSelectionMenu(anchor) else showSettings() }
        vHeader.addView(vaultInfoBtn, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(4) })
        vaultPanel.addView(vHeader)
        vaultSearchField = EditText(this).apply {
            hint = "Search your vault"
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setTextColor(pal.text)
            setHintTextColor(pal.icon)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            background = null
            setPadding(dp(10), dp(10), dp(4), dp(10))
            setText(vaultQuery)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val q = s?.toString() ?: ""
                    if (q == vaultQuery) return
                    vaultQuery = q
                    removeCallbacks(searchDebounce)
                    postDelayed(searchDebounce, 180)
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            setOnEditorActionListener { v, _, _ ->
                (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(v.windowToken, 0)
                true
            }
        }
        vaultSearchBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(4), 0)
            background = GradientDrawable().apply { cornerRadius = dpf(100); setColor(pal.numBg) }
            visibility = if (vaultSearchOpen) View.VISIBLE else View.GONE
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_search)
                setColorFilter(pal.icon)
            }, LinearLayout.LayoutParams(dp(20), dp(20)))
            addView(vaultSearchField, LinearLayout.LayoutParams(0, -2, 1f))
            addView(iconButton(R.drawable.ic_close, "Close search") { closeVaultSearch() },
                LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        vaultPanel.addView(vaultSearchBox, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), 0, dp(16), dp(8)) })
        vaultChipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(4), dp(8), dp(8))
        }
        vaultPanel.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(vaultChipsRow)
        }, LinearLayout.LayoutParams(-1, -2))
        // The file list is a pager: swipe left / right to move between All, Images, Videos and Files.
        vaultPages.fill(null)
        vaultPager = ViewPager2(this).apply {
            adapter = VaultTabsAdapter()
            offscreenPageLimit = 1
            setCurrentItem(vaultFilter, false)
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) { onVaultTabSelected(position) }
            })
        }
        vaultPanel.addView(vaultPager, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(vaultPanel, FrameLayout.LayoutParams(-1, -1))

        fabMenuOpen = false
        fabShape = GradientDrawable().apply { cornerRadius = dpf(28); setColor(pal.acBg) }
        val fabMask = GradientDrawable().apply { cornerRadius = dpf(28); setColor(Color.BLACK) }
        fabIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_add)
            setColorFilter(pal.acFg)
        }
        val fab = FrameLayout(this).apply {
            background = RippleDrawable(ColorStateList.valueOf(pal.ripple), fabShape, fabMask)
            elevation = dpf(6)
            isClickable = true
            isFocusable = true
            contentDescription = "Add"
            setOnClickListener { setFabMenu(!fabMenuOpen) }
            addView(fabIcon, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
        }
        fabScrim = View(this).apply {
            setBackgroundColor((pal.bg and 0xFFFFFF) or (0xE6 shl 24))
            visibility = View.GONE
            isClickable = true
            setOnClickListener { setFabMenu(false) }
        }
        fun fabPill(icon: Int, label: String, onClick: () -> Unit): LinearLayout {
            val shape = GradientDrawable().apply { cornerRadius = dpf(28); setColor(pal.acBg) }
            val mask = GradientDrawable().apply { cornerRadius = dpf(28); setColor(Color.BLACK) }
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(24), 0, dp(28), 0)
                background = RippleDrawable(ColorStateList.valueOf(pal.ripple), shape, mask)
                elevation = dpf(4)
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(icon)
                    setColorFilter(pal.acFg)
                }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(12) })
                addView(TextView(this@MainActivity).apply {
                    text = label
                    setTextColor(pal.acFg)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                })
            }
        }
        val uploadPill = fabPill(R.drawable.ic_upload, "Upload") { setFabMenu(false); pickFileForVault() }
        folderPill = fabPill(R.drawable.ic_create_folder, "Folder") { setFabMenu(false); promptNewFolder() }
        val appPill = fabPill(R.drawable.ic_lock, "Hide app") { setFabMenu(false); startHideApp() }
        fabMenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            visibility = View.GONE
            addView(folderPill, LinearLayout.LayoutParams(-2, dp(56)).apply { topMargin = dp(12) })
            addView(appPill, LinearLayout.LayoutParams(-2, dp(56)).apply { topMargin = dp(12) })
            addView(uploadPill, LinearLayout.LayoutParams(-2, dp(56)).apply { topMargin = dp(12); bottomMargin = dp(8) })
        }
        vaultFabHost = FrameLayout(this).apply {
            visibility = View.GONE
            fitsSystemWindows = true
            addView(fabScrim, FrameLayout.LayoutParams(-1, -1))
            addView(fabMenu, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(16), dp(32) + dp(80) + dp(4))
            })
            addView(fab, FrameLayout.LayoutParams(dp(80), dp(80), Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(16), dp(32))
            })
        }
        root.addView(vaultFabHost, FrameLayout.LayoutParams(-1, -1))

        // Put the scientific keys in the right state for this layout (also after rotation / window resize).
        applySciProgress(if (sciVisible) 1f else 0f)

        return root
    }

    private fun addTopArea(column: LinearLayout, topBar: View) {

        val r = dpf(28)
        histBgDrawable = GradientDrawable().apply {
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
            setColor(pal.bg)
        }
        histContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = histBgDrawable
        }
        histContainer.addView(topBar, LinearLayout.LayoutParams(-1, -2))

        histScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        historyList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
        }
        histScroll.addView(historyList, FrameLayout.LayoutParams(-1, -2))

        histThumb = View(this).apply {
            background = GradientDrawable().apply { cornerRadius = dpf(4); setColor(pal.icon) }
            alpha = 0f
        }
        histHost = FrameLayout(this).apply { visibility = View.GONE }
        histHost.addView(histScroll, FrameLayout.LayoutParams(-1, -1))
        histHost.addView(histThumb, FrameLayout.LayoutParams(dp(4), dp(36), Gravity.TOP or Gravity.END).apply {
            rightMargin = dp(4)
        })
        histContainer.addView(histHost, LinearLayout.LayoutParams(-1, 0))
        column.addView(histContainer, LinearLayout.LayoutParams(-1, -2))

        histScroll.setOnScrollChangeListener { _, _, y, _, _ ->
            pinBottom = y >= (historyList.height - histScroll.height) - dp(4)
            updateHistThumb(true)
        }
        histScroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (pinBottom) {
                val bottom = (historyList.height - histScroll.height).coerceAtLeast(0)
                if (histScroll.scrollY != bottom) histScroll.scrollTo(0, bottom)
            }
            updateHistThumb(histDragging || histAnim?.isRunning == true)
        }

        histBtnFill = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(pal.opBg); alpha = 0 }
        val btnMask = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.BLACK) }
        historyBtn?.background = RippleDrawable(ColorStateList.valueOf(pal.ripple), histBtnFill, btnMask)

        histHandle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            alpha = 0f
            setPadding(dp(24), dp(10), dp(24), 0)
            isClickable = true
            setOnClickListener { toggleHistory() }
        }
        histHandle.addView(View(this).apply {
            background = GradientDrawable().apply { cornerRadius = dpf(4); setColor(pal.icon) }
        }, LinearLayout.LayoutParams(dp(36), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(8) })
        histHandle.addView(TextView(this).apply {
            text = "Current expression"
            setTextColor(pal.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        })
        column.addView(histHandle, LinearLayout.LayoutParams(-1, 0))
    }

    private fun attachDisplayHost(v: View) {
        displayHost = v
        v.addOnLayoutChangeListener { view, _, top, _, bottom, _, _, _, _ ->
            if (histHeightPx == 0 && histAnim?.isRunning != true) {
                histTotal = (bottom - top) + (if (toolRowView != null) dp(64) else 0)
                if (pendingReopen && histTotal > 0) {
                    pendingReopen = false
                    view.post {
                        histMaxCache = computeHistMax()
                        if (histMaxCache > 0) {
                            renderHistory()
                            historyVisible = true
                            setHistHeight(histMaxCache)
                        }
                    }
                }
            }
        }
    }

    private fun computeHistMax(): Int {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val minDisplay = if (land) dp(64) else dp((104 * uiScale).toInt())
        if (histTotal <= 0) return 0

        return (histTotal - histHandleHeight - minDisplay).coerceAtLeast(dp(80))
    }

    private fun setHistHeight(h: Int) {
        val hh = h.coerceIn(0, histMaxCache)
        histHeightPx = hh
        val frac = if (histMaxCache > 0) hh.toFloat() / histMaxCache else 0f
        val show = hh > 0
        histHost.visibility = if (show) View.VISIBLE else View.GONE
        histHandle.visibility = if (show) View.VISIBLE else View.GONE
        histHost.layoutParams.height = hh
        histHandle.layoutParams.height = (histHandleHeight * frac).toInt()
        histHost.requestLayout()
        histHandle.requestLayout()
        histHandle.alpha = frac
        val c = argb.evaluate(frac, pal.bg, pal.histBg) as Int
        histBgDrawable.setColor(c)
        window.statusBarColor = c
        histBtnFill.alpha = (255 * frac).toInt()
        toolRowView?.let { v ->
            val lp = v.layoutParams
            lp.height = if (hh == 0) LinearLayout.LayoutParams.WRAP_CONTENT else (dp(64) * (1f - frac)).toInt()
            v.layoutParams = lp
            v.alpha = 1f - frac
            val blp = bottomScroll.layoutParams
            blp.height = (bottomBaseH - dp((16 * uiScale).toInt()) * frac).toInt()
            bottomScroll.layoutParams = blp
        }
    }

    private fun animateHist(target: Int, velocityY: Float = 0f) {
        histAnim?.cancel()
        historyVisible = target > 0
        val from = histHeightPx
        if (from == target || histMaxCache <= 0) return
        val dist = Math.abs(target - from).toFloat()
        val a = ValueAnimator.ofInt(from, target)

        var dur = 200f + 180f * dist / histMaxCache
        if (Math.abs(velocityY) > dpf(1500)) dur *= 0.75f
        a.duration = dur.toLong()
        a.interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        a.addUpdateListener { setHistHeight(it.animatedValue as Int) }
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (!cancelled) refresh()
            }
        })
        a.start()
        histAnim = a
    }

    private fun openHistory() {
        histAnim?.cancel()
        histMaxCache = computeHistMax()
        if (histMaxCache <= 0) return
        if (histHeightPx == 0) renderHistory()
        animateHist(histMaxCache)
    }

    private fun updateHistThumb(show: Boolean) {
        if (!::histThumb.isInitialized) return
        val content = historyList.height
        val view = histScroll.height
        if (view <= 0 || content <= view) { histThumb.alpha = 0f; return }
        val range = content - view
        val thumbH = Math.max(dp(36), (view.toLong() * view / content).toInt())
        val lp = histThumb.layoutParams
        if (lp.height != thumbH) { lp.height = thumbH; histThumb.layoutParams = lp }
        histThumb.translationY = histScroll.scrollY.toFloat() / range * (view - thumbH)
        if (show) {
            histThumb.animate().cancel()
            histThumb.alpha = 1f
            histThumb.removeCallbacks(hideThumb)
            histThumb.postDelayed(hideThumb, 900)
        }
    }

    private fun viewRect(v: View): Rect {
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (vaultVisible || !::histHost.isInitialized || !::displayHost.isInitialized) return super.dispatchTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                histDragging = false
                dragZone = DragZone.NONE
                velocity?.recycle()
                velocity = null
                val x = ev.rawX.toInt()
                val y = ev.rawY.toInt()
                val inList = histHost.visibility == View.VISIBLE && viewRect(histHost).contains(x, y)
                val top = viewRect(histContainer).top
                val bottom = viewRect(displayHost).bottom
                dragZone = when {
                    inList -> DragZone.LIST
                    y in top..bottom -> DragZone.EDGE
                    else -> DragZone.NONE
                }
                if (dragZone != DragZone.NONE) {
                    dragDownX = ev.rawX
                    dragDownY = ev.rawY
                    velocity = VelocityTracker.obtain().also { it.addMovement(ev) }

                    if (dragZone == DragZone.EDGE && histAnim?.isRunning == true) {
                        histAnim?.cancel()
                        histDragging = true
                        dragStartH = histHeightPx
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragZone == DragZone.NONE) return super.dispatchTouchEvent(ev)
                velocity?.addMovement(ev)
                if (!histDragging) {
                    val dy = ev.rawY - dragDownY
                    val dx = ev.rawX - dragDownX
                    val slop = ViewConfiguration.get(this).scaledTouchSlop
                    val vertical = Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx) * 1.2f
                    val canStart = vertical && when (dragZone) {
                        DragZone.EDGE -> when {
                            histHeightPx <= 0 -> dy > 0
                            histHeightPx >= histMaxCache -> dy < 0
                            else -> true
                        }
                        DragZone.LIST -> dy < 0 && !histScroll.canScrollVertically(1)
                        else -> false
                    }
                    if (!canStart) return super.dispatchTouchEvent(ev)
                    histAnim?.cancel()
                    histMaxCache = computeHistMax()
                    if (histMaxCache <= 0) { dragZone = DragZone.NONE; return super.dispatchTouchEvent(ev) }
                    if (histHeightPx == 0) renderHistory()
                    histDragging = true
                    dragStartH = histHeightPx
                    dragDownY = ev.rawY
                    val cancel = MotionEvent.obtain(ev)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    return true
                }
                setHistHeight(dragStartH + (ev.rawY - dragDownY).toInt())
                historyVisible = histHeightPx > 0
                updateHistThumb(true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasDragging = histDragging
                var vy = 0f
                velocity?.let {
                    it.addMovement(ev)
                    it.computeCurrentVelocity(1000)
                    vy = it.yVelocity
                    it.recycle()
                }
                velocity = null
                histDragging = false
                dragZone = DragZone.NONE
                if (wasDragging) {
                    val flick = dpf(600)
                    val target = when {
                        vy > flick -> histMaxCache
                        vy < -flick -> 0
                        histHeightPx > histMaxCache / 2 -> histMaxCache
                        else -> 0
                    }
                    animateHist(target, vy)
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun iconButton(res: Int, desc: String, onClick: (View) -> Unit): ImageView {
        val mask = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.BLACK) }
        return ImageView(this).apply {
            setImageResource(res)
            setColorFilter(pal.icon)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            contentDescription = desc
            background = RippleDrawable(ColorStateList.valueOf(pal.ripple), null, mask)
            setOnClickListener { onClick(it) }
        }
    }

    private fun makeKey(label: String): View {
        val isOp = label in setOf("÷", "×", "−", "+", "%", "( )")
        val bg: Int
        val fg: Int
        when {
            label == "=" -> { bg = pal.eqBg; fg = pal.eqFg }
            label == "AC" -> { bg = pal.acBg; fg = pal.acFg }
            isOp -> { bg = pal.opBg; fg = pal.opFg }
            else -> { bg = pal.numBg; fg = pal.numFg }
        }
        val content: View = if (label == "⌫") {
            ImageView(this).apply {
                setImageResource(R.drawable.ic_backspace)
                setColorFilter(fg)
                layoutParams = FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER)
                backspaceIcon = this
            }
        } else {
            val bigOp = label in setOf("÷", "×", "−", "+", "=")
            val lightGlyph = label in setOf("÷", "×", "−", "+", "%", "( )")
            val size = when {
                label == "AC" -> 30f
                bigOp -> 40f
                else -> 34f
            }
            TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextColor(fg)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                typeface = if (label == "=") opFont else if (lightGlyph) displayFont else keyFont
                includeFontPadding = false
                layoutParams = FrameLayout.LayoutParams(-1, -1)
                keyTexts.add(this to size)
            }
        }
        val button = makeButton(content, bg) { onPress(label) }
        if (label == "=") {
            eqButton = button

            val holdRunnable = Runnable {
                holdFired = true
                button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onEqualsHeld()
            }
            button.setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        holdFired = false
                        handler.postDelayed(holdRunnable, 3000)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (ev.x < 0f || ev.y < 0f || ev.x > v.width || ev.y > v.height) handler.removeCallbacks(holdRunnable)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(holdRunnable)
                }
                false
            }

            button.setOnClickListener {
                if (holdFired) holdFired = false else onPress("=")
            }
        }
        if (label == "⌫") button.setOnLongClickListener { onPress("AC"); true }
        return button
    }

    private fun makeSciKey(label: String): View {
        val tv = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(pal.opFg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = sciFont
            includeFontPadding = false
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        sciKeys[label] = tv
        if (label == "Rad" || label == "Deg") radKey = tv
        return makeButton(tv, pal.opBg) { onSciPress(label) }
    }

    private fun makeButton(content: View, bg: Int, onClick: () -> Unit): FrameLayout {
        val radius = dpf(200)
        val shape = GradientDrawable().apply { cornerRadius = radius; setColor(bg) }
        val mask = GradientDrawable().apply { cornerRadius = radius; setColor(Color.BLACK) }
        return FrameLayout(this).apply {
            background = RippleDrawable(ColorStateList.valueOf(pal.ripple), shape, mask)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            addView(content)
        }
    }

    private fun showMenu(anchor: View) {
        var popup: PopupWindow? = null
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dpf(28); setColor(pal.numBg) }
            setPadding(0, dp(8), 0, dp(8))
            minimumWidth = dp(200)
        }
        fun item(icon: Int, label: String, action: () -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setPadding(dp(18), 0, dp(24), 0)
                background = rippleBg()
                setOnClickListener { popup?.dismiss(); action() }
            }
            row.addView(ImageView(this).apply {
                setImageResource(icon)
                setColorFilter(pal.icon)
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            row.addView(TextView(this).apply {
                text = label
                setTextColor(pal.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setPadding(dp(16), 0, 0, 0)
            }, LinearLayout.LayoutParams(-2, -2))
            card.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        item(R.drawable.ic_delete_history, "Clear history") { confirmClearHistory() }
        item(R.drawable.ic_palette, "Choose theme") { showThemeDialog() }
        item(R.drawable.ic_help, "How to use") { showGuide() }

        val p = PopupWindow(card, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        p.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        p.isOutsideTouchable = true
        popup = p
        card.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val w = card.measuredWidth
        card.pivotX = w.toFloat()
        card.pivotY = 0f
        card.scaleX = 0.85f
        card.scaleY = 0.85f
        card.alpha = 0f
        p.showAsDropDown(anchor, anchor.width - w - dp(8), dp(2))
        card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(160)
            .setInterpolator(PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start()
    }

    private fun toggleDegrees() {
        Evaluator.degrees = !Evaluator.degrees
        prefs.edit().putBoolean("deg", Evaluator.degrees).apply()
        radKey.text = if (Evaluator.degrees) "Rad" else "Deg"
        refresh()
    }

    private fun onSciPress(key: String) {
        if (key == "Rad" || key == "Deg") { toggleDegrees(); return }
        if (key == "Inv") { inverse = !inverse; updateSciLabels(); return }
        val inv = inverse
        val postfix = key == "^" || key == "!" || (key == "√" && inv)
        if (justEvaluated) {
            if (!postfix) expression = ""
            justEvaluated = false
        }
        when (key) {
            "√" -> expression += if (inv) "²" else "√"
            "π" -> expression += "π"
            "^" -> expression += "^"
            "!" -> expression += "!"
            "e" -> expression += "e"
            "sin" -> expression += if (inv) "arcsin(" else "sin("
            "cos" -> expression += if (inv) "arccos(" else "cos("
            "tan" -> expression += if (inv) "arctan(" else "tan("
            "ln" -> expression += if (inv) "e^(" else "ln("
            "log" -> expression += if (inv) withTimes("10^(") else "log("
        }
        if (inverse && key != "π" && key != "^" && key != "!" && key != "e") {
            inverse = false
            updateSciLabels()
        }
        refresh()
    }

    private fun withTimes(token: String): String =
        if (expression.isNotEmpty() && (expression.last().isDigit() || expression.last() in ")%²!πe.")) "×$token" else token

    private fun updateSciLabels() {
        sciKeys["sin"]?.text = if (inverse) "sin⁻¹" else "sin"
        sciKeys["cos"]?.text = if (inverse) "cos⁻¹" else "cos"
        sciKeys["tan"]?.text = if (inverse) "tan⁻¹" else "tan"
        sciKeys["ln"]?.text = if (inverse) "eˣ" else "ln"
        sciKeys["log"]?.text = if (inverse) "10ˣ" else "log"
        sciKeys["√"]?.text = if (inverse) "x²" else "√"
        val tabletLand = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            resources.configuration.smallestScreenWidthDp >= 600
        sciKeys["Inv"]?.typeface = when {
            inverse -> Typeface.DEFAULT_BOLD
            tabletLand && Build.VERSION.SDK_INT >= 28 -> Typeface.create(sciFont, 600, false)
            else -> sciFont
        }
    }

    private fun updateExpandIcon() {
        expandBtn?.setImageResource(if (sciVisible) R.drawable.ic_unfold_less else R.drawable.ic_unfold_more)
    }

    private fun toggleScientific() {
        sciVisible = !sciVisible
        updateExpandIcon()
        animateSci(if (sciVisible) 1f else 0f)
    }

    /**
     * Animates the scientific keys in or out (like Pixel Calculator): in portrait the extra rows grow from a flat
     * sliver while the whole keypad squeezes together and the glyphs shrink with the keys; in landscape and on
     * tablets the side panel slides open and the keypad makes room. Tapping again mid-way simply reverses it.
     */
    private fun animateSci(target: Float) {
        sciAnim?.cancel()
        val from = sciProgress
        if (from == target) {
            applySciProgress(target)
            return
        }
        // Reversing part-way takes proportionally less time, so the speed stays the same.
        val ms = (SCI_ANIM_MS * kotlin.math.abs(target - from)).toLong().coerceAtLeast(120L)
        sciAnim = ValueAnimator.ofFloat(from, target).apply {
            duration = ms
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { applySciProgress(it.animatedValue as Float) }
            start()
        }
    }

    /** Lays out the keypad for scientific-keys progress [p] (0 = collapsed, 1 = expanded). */
    private fun applySciProgress(p: Float) {
        sciProgress = p
        val shown = p > 0.001f
        if (sciStacked) {
            for (line in sciLines) {
                val lp = line.layoutParams as LinearLayout.LayoutParams
                lp.weight = p
                line.layoutParams = lp
                line.visibility = if (shown) View.VISIBLE else View.GONE
            }
            val k = 1f - SCI_GLYPH_SHRINK * p
            for ((tv, sz) in keyTexts) tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sz * keyMul * k)
            for ((_, tv) in sciKeys) tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, maxOf(1f, sciRestSp * k * p))
        } else {
            val panel = sciPanelView ?: return
            val lp = panel.layoutParams as LinearLayout.LayoutParams
            lp.weight = sciPanelWeight * p
            lp.rightMargin = (sciPanelGap * p).toInt()
            panel.layoutParams = lp
            panel.visibility = if (shown) View.VISIBLE else View.GONE
            for ((_, tv) in sciKeys) tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, maxOf(1f, sciRestSp * p))
        }
    }

    private fun toggleHistory() {
        if (historyVisible) animateHist(0) else openHistory()
    }

    private fun toggleVault() {
        vaultVisible = !vaultVisible
        if (vaultVisible) {
            vaultPageKey.fill(null)   // play the entrance animation again
            renderVaultFiles()
        } else {
            setFabMenu(false)
            resetVaultSearch()
            selected.clear()
            rowUpdaters.forEach { it.clear() }
            thumbCache.clear()
            currentFolder = null
            vaultFilter = 0
            vaultPager.setCurrentItem(0, false)
            viewerDialog?.dismiss()
            dismissVaultDialogs()
        }
        vaultPanel.visibility = if (vaultVisible) View.VISIBLE else View.GONE
        vaultFabHost.visibility = if (vaultVisible) View.VISIBLE else View.GONE
        setSecure(vaultVisible)
        if (vaultVisible) {
            // Show the card of a transfer that is still running, and report any that finished while the vault was closed.
            syncTransferCard()
            pumpTransferResults()
        } else {
            dismissTransferCard()
        }
    }

    private fun openUrl(url: String) {
        leavingForTask = true
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            leavingForTask = false
            Toast.makeText(this, "No app found to open this link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAbout() = showSettings()

    /** Settings (the gear in the vault header): defaults for the questions the app asks, then update check and credits. */
    private fun showSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, dp(24), 0)
        }
        fun section(title: String) {
            box.addView(TextView(this).apply {
                text = title.uppercase()
                setTextColor(pal.cursor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f
                setPadding(0, dp(14), 0, dp(4))
            })
        }
        fun settingCheck(label: String, hint: String?, value: Boolean, onChange: (Boolean) -> Unit) {
            box.addView(themedCheck(label, value).apply {
                setOnCheckedChangeListener { _, c -> onChange(c) }
            }, LinearLayout.LayoutParams(-1, -2))
            if (hint != null) box.addView(rowMeta(hint).apply { maxLines = 4; setPadding(dp(32), 0, 0, dp(6)) })
        }

        section("Adding files")
        settingCheck("Encrypt files by default", "The \"Encrypt the files\" box starts ticked.", AppSettings.encryptByDefault(this)) {
            AppSettings.setEncryptByDefault(this, it)
        }
        settingCheck("Ask before adding files", "Off: files are added straight away with the choice above (what \"Remember my choice\" does).",
            AppSettings.askBeforeAdd(this)) { AppSettings.setAskBeforeAdd(this, it) }

        section("Hiding apps")
        box.addView(rowMeta("Hide method").apply { setPadding(0, dp(4), 0, dp(4)) })
        val methodRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun drawMethods() {
            methodRow.removeAllViews()
            val cur = AppSettings.hideMethod(this)
            listOf("Ask" to AppSettings.HIDE_ASK, "Shizuku / Sui" to AppSettings.HIDE_SHIZUKU, "Parallel engine" to AppSettings.HIDE_ENGINE)
                .forEach { (label, value) ->
                    methodRow.addView(filterChip(label, cur == value) {
                        AppSettings.setHideMethod(this, value)
                        drawMethods()
                    }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
                }
        }
        drawMethods()
        box.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(methodRow)
        }, LinearLayout.LayoutParams(-1, -2))
        settingCheck("Set up Google services by default",
            "When a hidden app needs Google services, the \"Also set up\" box starts ticked. The Parallel engine doesn't fully support Google apps.",
            AppSettings.gmsWithHide(this)) { AppSettings.setGmsWithHide(this, it) }

        section("Videos")
        settingCheck("Continue videos where I left off", null, AppSettings.resumeVideos(this)) {
            AppSettings.setResumeVideos(this, it)
            if (!it) AppSettings.clearResumePositions(this)
        }
        box.addView(TextView(this).apply {
            text = "Reset video thumbnails"
            setTextColor(pal.cursor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, dp(10), 0, dp(10))
            setOnClickListener {
                VaultManager.clearVideoThumbs(applicationContext)
                thumbCache.clear()
                Toast.makeText(this@MainActivity, "Thumbnails will be made again", Toast.LENGTH_SHORT).show()
                renderVaultFiles()
            }
        })

        section("About")
        val sb = SpannableStringBuilder()
        fun link(text: String, url: String) {
            val start = sb.length
            sb.append(text)
            sb.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) { openUrl(url) }
                override fun updateDrawState(ds: TextPaint) {
                    ds.color = pal.result
                    ds.isUnderlineText = true
                }
            }, start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        sb.append("Developer: The AryanOnion · ")
        link("GitHub", PROFILE_URL)
        sb.append("\nCheck out Repo: ")
        link("CalCognito", REPO_URL)
        sb.append("\n\nVersion ${appVersion()}")
        sb.append(
            "\nHidden-app engine: " + when {
                EngineBridge.available -> "running"
                EngineBridge.compiledIn && EngineBridge.disabledByCrash(this) -> "switched off after it crashed the app"
                EngineBridge.compiledIn && EngineBridge.failure != null -> "in this build, but it failed to start"
                EngineBridge.compiledIn -> "in this build (starts when you first use hidden apps)"
                else -> "NOT in this build"
            }
        )
        sb.append("\nShizuku / Sui: " + ShizukuHider.statusLine())
        box.addView(TextView(this).apply {
            text = sb
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(pal.text)
            setLineSpacing(0f, 1.2f)
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = Color.TRANSPARENT
        }, LinearLayout.LayoutParams(-1, -2))

        val status = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(pal.icon)
            setLineSpacing(0f, 1.15f)
            visibility = View.GONE
        }
        val checkBtn = TextView(this).apply {
            text = "Check for updates"
            setTextColor(pal.cursor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = RippleDrawable(
                ColorStateList.valueOf(pal.ripple),
                GradientDrawable().apply {
                    cornerRadius = dpf(20)
                    setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(pal.cursor, 36))
                },
                null
            )
            setOnClickListener {
                if (updateBusy) return@setOnClickListener
                updateBusy = true
                isEnabled = false
                alpha = 0.5f
                status.visibility = View.VISIBLE
                status.text = "Checking for updates…"
                Updater.check(this@MainActivity, appVersion()) { result ->
                    updateBusy = false
                    isEnabled = true
                    alpha = 1f
                    if (isDestroyed || isFinishing) return@check
                    when (result) {
                        is Updater.CheckResult.Available -> {
                            status.text = "Version ${result.info.version} is available"
                            showUpdateDialog(result.info)
                        }
                        is Updater.CheckResult.UpToDate -> status.text = "You're on the latest version"
                        is Updater.CheckResult.Failed -> status.text = "Couldn't check: ${result.message}"
                    }
                }
            }
        }
        box.addView(checkBtn, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14) })
        box.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        aboutDialog?.dismiss()
        aboutDialog = showStyledDialog("Settings", box, "Close", null) {}
    }

    /** "Update available" dialog: shows the new version and its release notes; "Update" downloads and installs. */
    private fun showUpdateDialog(info: Updater.UpdateInfo) {
        val sb = StringBuilder("Version ${info.version} is available. You have ${appVersion()}.")
        if (info.size > 0) sb.append("\nDownload size: ${"%.1f".format(info.size / 1048576.0)} MB")
        if (info.notes.isNotBlank()) {
            val notes = info.notes.replace("\r", "").let { if (it.length > 700) it.take(700).trimEnd() + "…" else it }
            sb.append("\n\nWhat's new:\n").append(notes)
        }
        showStyledDialog("Update available", msgBody(sb.toString()), "Update", "Later") { startUpdate(info) }
    }

    private fun startUpdate(info: Updater.UpdateInfo) {
        if (!HiddenApps.canInstall(this)) {
            showStyledDialog(
                "Allow installing updates",
                msgBody("To install updates, Android needs your permission for this app to install apps. Tap Settings, switch on \"Allow from this source\", then come back. The update continues by itself."),
                "Settings", "Cancel"
            ) {
                pendingUpdate = info
                leavingForTask = true
                try {
                    HiddenApps.openInstallSettings(this)
                } catch (e: Exception) {
                    pendingUpdate = null
                    leavingForTask = false
                    Toast.makeText(this, "Couldn't open Android's settings", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        val cancelled = AtomicBoolean(false)
        val dlg = PixelProgressDialog(this, progressColors(), wavy = true)
        dlg.cancelable = true
        dlg.onCancel = { cancelled.set(true) }
        dlg.update(title = "Downloading update", sub = "Version ${info.version}", progress = -1f)
        dlg.show(0L)
        Updater.download(this, info, cancelled, onProgress = { dlg.update(progress = it) }) { file, error ->
            dlg.dismiss()
            if (isDestroyed || isFinishing) return@download
            if (file == null) {
                if (error != null) Toast.makeText(this, "Update failed: $error", Toast.LENGTH_LONG).show()
                return@download
            }
            val prep = PixelProgressDialog(this, progressColors(), wavy = true)
            prep.update(title = "Preparing update…", sub = "Version ${info.version}", progress = -1f)
            prep.show(0L)
            Updater.install(this, file, beforeConfirm = { prep.dismiss(); leavingForTask = true }) { ok, msg ->
                prep.dismiss()
                if (isDestroyed || isFinishing) return@install
                if (!ok && !msg.isNullOrBlank()) {
                    Toast.makeText(this, "Couldn't install the update: $msg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** 0000 is reserved for the Forgot-PIN reset, so it can never be a password. */
    private fun isValidPin(s: String) = s.length >= 4 && s.all { it.isDigit() } && s != "0000"

    private fun pinProblem(s: String) =
        if (s == "0000") "0000 is reserved for Forgot PIN. Choose another password."
        else "Type at least 4 digits"

    private var guideDialog: Dialog? = null
    private var guideStep = 0

    /**
     * Full-screen "How to use" guide with five swipeable slides. An animated mock of this calculator shows a hand
     * typing 1234, holding = for 3 seconds to set the password, typing 1234 again and opening the secure vault;
     * the last slide, in red, shows what to do when the PIN is forgotten (0000, hold =, press = to confirm).
     * Every colour comes from [pal], so it follows the dynamic (Material You) theme.
     * [startStep] lets a rotation reopen the guide on the slide that was showing.
     */
    private fun showGuide(startStep: Int = 0) {
        guideDialog?.dismiss()
        val land = winW() > winH()
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(pal.bg))
            w.statusBarColor = pal.bg
            w.navigationBarColor = pal.bg
        }
        @Suppress("DEPRECATION")
        dialog.window?.decorView?.systemUiVisibility = if (isNightMode()) 0
            else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val titles = arrayOf("Type a password", "Hold = for 3 seconds", "Enter it again", "Your vault opens", "Forgot PIN?")
        val bodies = arrayOf(
            "On the calculator, type a password of at least 4 digits, like 1234.",
            "Now press and hold the = key for 3 seconds. You'll see \"Password set\" when it's saved.",
            "Type your password again, then press =.",
            "That's it. Your secure vault opens, ready for your photos, videos and files.",
            "Type 0000 and hold = for 3 seconds, then press = to confirm. This erases all vault files and relaunches the app so you can set a new PIN."
        )

        val demo = GuideDemoView(
            this,
            GuideColors(
                bg = pal.bg, stage = pal.histBg, text = pal.text, subtle = pal.icon,
                numBg = pal.numBg, numFg = pal.numFg, opBg = pal.opBg, opFg = pal.opFg,
                acBg = pal.acBg, acFg = pal.acFg, eqBg = pal.eqBg, eqFg = pal.eqFg,
                primary = pal.cursor, outline = pal.divider,
                danger = pal.clearFg
            ),
            keyFont, displayFont, opFont
        )

        val title = TextView(this).apply {
            setTextColor(pal.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = keyFont
            maxLines = 2
        }
        val body = TextView(this).apply {
            setTextColor(pal.icon)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setLineSpacing(0f, 1.15f)
            minLines = 3
        }

        // Page dots (tap one to slide to that step; the last slide is red)
        val dotOff = androidx.core.graphics.ColorUtils.blendARGB(pal.bg, pal.icon, 0.35f)
        val dotViews = ArrayList<View>()
        val dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        for (i in titles.indices) {
            val dot = View(this)
            dotViews.add(dot)
            val holder = FrameLayout(this).apply {
                setOnClickListener { demo.goToStep(i) }
                addView(dot, FrameLayout.LayoutParams(dp(8), dp(8), Gravity.CENTER))
            }
            dotsRow.addView(holder, LinearLayout.LayoutParams(dp(24), dp(40)))
        }
        fun updateDots(active: Int) {
            for (i in dotViews.indices) {
                val on = i == active
                dotViews[i].background = GradientDrawable().apply {
                    cornerRadius = dpf(4)
                    setColor(if (on) (if (active == GuideDemoView.STEP_FORGOT) pal.clearFg else pal.cursor) else dotOff)
                }
                val lp = dotViews[i].layoutParams as FrameLayout.LayoutParams
                lp.width = if (on) dp(20) else dp(8)
                dotViews[i].layoutParams = lp
            }
        }

        fun markSeen() { prefs.edit().putBoolean("guide_seen", true).apply() }
        fun finishGuide() { markSeen(); dialog.dismiss() }

        var actionBtn: TextView? = null
        var fadeGen = 0
        var shown = -1
        fun showStep(step: Int) {
            if (step == shown) return
            val first = shown < 0
            shown = step
            guideStep = step
            updateDots(step)
            // "Next" moves on manually; only the last slide ends the guide with "Got it".
            actionBtn?.text = if (step == titles.size - 1) "Got it" else "Next"
            val titleColor = if (step == GuideDemoView.STEP_FORGOT) pal.clearFg else pal.text
            if (first) {
                title.text = titles[step]
                title.setTextColor(titleColor)
                body.text = bodies[step]
                return
            }
            // Rapid taps start several fades; only the newest one may swap the text.
            val gen = ++fadeGen
            body.animate().alpha(0f).setDuration(100).start()
            title.animate().alpha(0f).setDuration(100).withEndAction {
                if (gen != fadeGen) return@withEndAction
                title.text = titles[shown]
                title.setTextColor(if (shown == GuideDemoView.STEP_FORGOT) pal.clearFg else pal.text)
                body.text = bodies[shown]
                title.animate().alpha(1f).setDuration(180).start()
                body.animate().alpha(1f).setDuration(180).start()
            }.start()
        }
        demo.onStepChanged = { showStep(it) }

        val gotIt = TextView(this).apply {
            text = "Next"
            setTextColor(pal.eqFg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dp(28), 0, dp(28), 0)
            val shape = GradientDrawable().apply { cornerRadius = dpf(200); setColor(pal.eqBg) }
            val mask = GradientDrawable().apply { cornerRadius = dpf(200); setColor(Color.BLACK) }
            background = RippleDrawable(ColorStateList.valueOf(pal.ripple), shape, mask)
            setOnClickListener {
                if (guideStep >= titles.size - 1) finishGuide() else demo.goNext()
            }
        }
        actionBtn = gotIt
        val skip = TextView(this).apply {
            text = "Skip"
            setTextColor(pal.cursor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            background = rippleBg(20)
            setOnClickListener { finishGuide() }
        }
        // "CalCognito" (dynamic themed) at the top left; quietly links to the project page.
        fun brandView() = TextView(this).apply {
            text = "CalCognito"
            setTextColor(pal.cursor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = keyFont
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            background = rippleBg(20)
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AryanKumar1234/CalCognito")))
                } catch (_: Exception) {}
            }
        }
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(dotsRow, LinearLayout.LayoutParams(-2, -2))
            addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(gotIt, LinearLayout.LayoutParams(-2, dp(44)))
        }

        val content = LinearLayout(this)
        if (!land) {
            content.orientation = LinearLayout.VERTICAL
            content.setPadding(dp(16), dp(8), dp(16), dp(12))
            val topRow = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(brandView(), LinearLayout.LayoutParams(-2, dp(40)))
                addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
                addView(skip, LinearLayout.LayoutParams(-2, dp(40)))
            }
            content.addView(topRow, LinearLayout.LayoutParams(-1, dp(48)))
            content.addView(demo, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(20) })
            content.addView(title, LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(8); rightMargin = dp(8) })
            content.addView(body, LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(8); rightMargin = dp(8); topMargin = dp(8) })
            content.addView(bottomRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        } else {
            content.orientation = LinearLayout.HORIZONTAL
            content.setPadding(dp(16), dp(12), dp(16), dp(12))
            content.addView(demo, LinearLayout.LayoutParams(0, -1, 1f))
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val topRow = LinearLayout(this@MainActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(brandView(), LinearLayout.LayoutParams(-2, dp(40)))
                    addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
                    addView(skip, LinearLayout.LayoutParams(-2, dp(40)))
                }
                addView(topRow, LinearLayout.LayoutParams(-1, dp(40)))
                addView(View(this@MainActivity), LinearLayout.LayoutParams(-1, 0, 1f))
                addView(title, LinearLayout.LayoutParams(-1, -2))
                addView(body, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
                addView(View(this@MainActivity), LinearLayout.LayoutParams(-1, 0, 1f))
                addView(bottomRow, LinearLayout.LayoutParams(-1, -2))
            }
            content.addView(col, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(24) })
        }
        // fitsSystemWindows overwrites padding, so it lives on an outer wrapper. It is also what turns
        // horizontal drags anywhere on the page into slide swipes.
        val outer = SwipeHost(this, demo).apply {
            setBackgroundColor(pal.bg)
            fitsSystemWindows = true
            addView(content, FrameLayout.LayoutParams(-1, -1))
        }

        dialog.setContentView(outer)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.setOnCancelListener { markSeen() }
        dialog.setOnDismissListener {
            demo.stop()
            if (guideDialog === dialog) guideDialog = null
        }
        guideDialog = dialog
        if (startStep > 0) demo.seekToStep(startStep)
        dialog.show()
        demo.start()
    }

    private fun onEqualsHeld() {
        if (changeMode || justEvaluated) return
        // "0000" is the Forgot-PIN code, unless it really is the PIN: then holding = changes the password as usual.
        if (VaultManager.isPinSet(this) && expression == "0000" && !VaultManager.checkPin(this, expression)) {
            // The confirmation is shown in the calculator's result line (see renderBottomResult), like the
            // "Enter new password" prompt, instead of in a popup.
            pinResetPending = true
            expression = ""
            refresh()
            return
        }
        if (!VaultManager.isPinSet(this)) {
            if (isValidPin(expression)) {
                VaultManager.setPin(this, expression)
                expression = ""
                refresh()
                Toast.makeText(this, "Password set", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, if (expression == "0000") pinProblem(expression) else "Type at least 4 digits, then hold = for 3 seconds", Toast.LENGTH_LONG).show()
            }
        } else if (VaultManager.checkPin(this, expression)) {

            expression = ""
            changeMode = true
            refresh()
        }

    }

    /** Size in bytes of a picked file, or -1 if the provider doesn't say. */
    private fun getFileSize(uri: Uri): Long {
        try {
            if (uri.scheme == "content") {
                contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.SIZE)
                        if (idx != -1 && !c.isNull(idx)) return c.getLong(idx)
                    }
                }
            } else if (uri.scheme == "file") {
                uri.path?.let { return File(it).length() }
            }
        } catch (_: Exception) {
        }
        return -1L
    }

    private fun withStoragePermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            afterPermission = action
            leavingForTask = true
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_PERM)
        } else {
            action()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIF) {
            // Optional: transfers run either way, this only decides whether their notification can be shown.
            leavingForTask = false
            return
        }
        if (requestCode == REQ_PERM) {
            val action = afterPermission
            afterPermission = null
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                action?.invoke()
            } else {
                Toast.makeText(this, "Storage permission is needed to move files", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Upload: the built-in file picker where the phone lets us browse storage, Android's own picker otherwise. */
    private fun pickFileForVault() {
        when {
            // Android 11+: browsing needs "All files access".
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                if (canBrowseAllFiles()) showFilePicker() else askAllFilesAccess()
            // Android 10 has no permission that allows browsing: only the system picker works.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> pickWithSystemPicker()
            // Android 9 and older: the ordinary storage permission is enough.
            else -> withStoragePermission { showFilePicker() }
        }
    }

    private fun canBrowseAllFiles(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && android.os.Environment.isExternalStorageManager()

    private fun askAllFilesAccess() {
        showStyledDialog(
            "Allow access to your files",
            msgBody(
                "To browse your folders and pick several files at once, the vault needs \"All files access\". " +
                "It is only used to list folders and to move the files you choose into the vault, and you can switch it off again " +
                "in Settings at any time.\n\nPrefer not to? Android's own picker works too."
            ),
            "Open settings", "System picker", onNegative = { pickWithSystemPicker() }
        ) { openAllFilesSettings() }
    }

    private fun openAllFilesSettings() {
        pendingFilePicker = true
        leavingForTask = true
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (e2: Exception) {
                pendingFilePicker = false
                leavingForTask = false
                Toast.makeText(this, "Couldn't open the settings screen, using the system picker", Toast.LENGTH_SHORT).show()
                pickWithSystemPicker()
            }
        }
    }

    private fun showFilePicker() {
        val colors = PickerColors(
            bg = pal.bg, text = pal.text, sub = pal.icon,
            chipBg = pal.numBg, chipFg = pal.sciFg, chipOnBg = pal.eqBg, chipOnFg = pal.eqFg,
            fabBg = pal.acBg, fabFg = pal.acFg,
            tint = (pal.opBg and 0xFFFFFF) or (0x55 shl 24), checkBg = pal.eqBg, checkFg = pal.eqFg,
            folder = pal.result, divider = pal.divider, ripple = pal.ripple
        )
        val picker = FilePicker(
            this, colors, !isNightMode(),
            onPicked = { files -> confirmAdd(files.map { Uri.fromFile(it) }) },
            onSystemPicker = { pickWithSystemPicker() }
        )
        trackVaultDialog(picker.show())
    }

    private fun pickWithSystemPicker() = withStoragePermission {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        leavingForTask = true
        try {
            startActivityForResult(intent, REQ_PICK)
        } catch (e: Exception) {
            leavingForTask = false
            Toast.makeText(this, "No file picker available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun collectUris(data: Intent): List<Uri> {
        val list = mutableListOf<Uri>()
        val clip = data.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let { list.add(it) }
            }
        } else {
            data.data?.let { list.add(it) }
        }
        return list
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_PICK -> if (resultCode == RESULT_OK && data != null) confirmAdd(collectUris(data))
            REQ_DELETE_BATCH -> {
                if (resultCode != RESULT_OK) hideNotRemoved += batchDeleteCount
                finishHide()
            }
            REQ_DELETE_LEGACY -> {
                val uri = legacyCurrent
                legacyCurrent = null
                if (resultCode == RESULT_OK && uri != null) {
                    try {
                        if (contentResolver.delete(uri, null, null) <= 0) hideNotRemoved++
                    } catch (_: Exception) {
                        hideNotRemoved++
                    }
                } else {
                    hideNotRemoved++
                }
                processLegacyDeleteQueue()
            }
        }
    }

    private fun themedCheck(label: String, checked: Boolean): CheckBox = CheckBox(this).apply {
        text = label
        isChecked = checked
        setTextColor(pal.text)
        buttonTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(pal.cursor, pal.text)
        )
    }

    private fun warnRed(): Int = if (isNightMode()) Color.parseColor("#FF6B5E") else Color.parseColor("#D93025")

    private fun confirmAdd(uris: List<Uri>) {
        if (uris.isEmpty()) return
        // "Remember my choice" turned the question off earlier: go straight on with the saved choice (Settings changes it).
        if (!AppSettings.askBeforeAdd(this)) {
            hideSelectedFiles(uris, AppSettings.encryptByDefault(this))
            return
        }
        val check = themedCheck("Encrypt the files", AppSettings.encryptByDefault(this))
        val remember = themedCheck("Remember my choice", false)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(msgBody(
                "Files are moved into the vault and removed from your gallery and file manager.\n\n" +
                "Not encrypted: opens instantly, and other apps can't see it, but the file itself isn't scrambled.\n" +
                "Encrypted: fully protected, but a bit slower to add and open."
            ))
            addView(check, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(20), dp(12), dp(24), 0) })
            addView(remember, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(20), dp(2), dp(24), 0) })
        }
        showStyledDialog("Add ${uris.size} file(s) to vault", ScrollView(this).apply { addView(box) }, "Add", "Cancel") {
            if (remember.isChecked) {
                AppSettings.setEncryptByDefault(this, check.isChecked)
                AppSettings.setAskBeforeAdd(this, false)
                Toast.makeText(this, "Remembered. You can change it in Settings.", Toast.LENGTH_SHORT).show()
            }
            hideSelectedFiles(uris, check.isChecked)
        }
    }

    private fun hideSelectedFiles(uris: List<Uri>, encrypt: Boolean) {
        if (uris.isEmpty()) return
        // Sizes give the card real progress and decide whether it appears at once (100 MB+ or unknown) or only
        // if the add turns out to take a moment. The copy itself runs in the background (see TransferManager).
        val sizes = uris.map { getFileSize(it) }
        val job = TransferJobs.add(uris, sizes, currentFolder, encrypt)
        askNotificationPermissionOnce()
        TransferManager.enqueue(this, job)
    }

    // ---- Background transfers: progress card + results ----

    private val transferListener = object : TransferManager.Listener {
        override fun onJobUpdate(job: TransferManager.Job) {
            syncTransferCard()
        }

        override fun onJobFinished(job: TransferManager.Job) {
            if (job !in finishedJobs) finishedJobs.add(job)
            pumpTransferResults()
            syncTransferCard()
        }
    }

    /** Makes the card match the transfers: the oldest un-hidden one gets it, and only while the vault is open. */
    private fun syncTransferCard() {
        if (cardClosing) return
        val job = if (vaultVisible && !isDestroyed && !isFinishing) TransferManager.cardJob() else null
        if (job == null) {
            dismissTransferCard()
            return
        }
        if (transferCardJob !== job) {
            dismissTransferCard()
            openTransferCard(job)
        }
        updateTransferCard(job)
    }

    private fun openTransferCard(job: TransferManager.Job) {
        val card = PixelProgressDialog(this, progressColors(), wavy = true)
        card.showButtons = true
        card.onCancel = {
            TransferManager.cancel(job)
            updateTransferCard(job)
        }
        card.onHide = {
            // Back button or Hide: the card goes away, the transfer does not.
            job.userHidden = true
            if (transferCardJob === job) {
                transferCard = null
                transferCardJob = null
            }
            Toast.makeText(this, "Continuing in the background", Toast.LENGTH_SHORT).show()
        }
        transferCard = card
        transferCardJob = job
        // A transfer that has already been running for a while gets its card at once.
        val wait = job.createdAt + job.cardDelayMs - SystemClock.uptimeMillis()
        card.show(wait.coerceAtLeast(0L))
    }

    private fun updateTransferCard(job: TransferManager.Job) {
        val card = transferCard ?: return
        if (transferCardJob !== job) return
        if (job.cancelled) {
            card.setButtonsVisible(false)
            card.update(title = "Cancelling…", sub = "", progress = -1f)
            return
        }
        if (job.state == TransferManager.State.QUEUED) {
            card.update(title = job.title, sub = "Waiting…", progress = -1f)
            return
        }
        val pct = if (job.progress >= 0f) "${(job.progress * 100).toInt()}%" else ""
        val sub = listOf(job.label, pct).filter { it.isNotEmpty() }.joinToString(" · ")
        card.update(title = job.title, sub = sub, progress = job.progress)
    }

    private fun dismissTransferCard() {
        transferCard?.dismiss()
        transferCard = null
        transferCardJob = null
    }

    /** Takes over the card that was showing [job], so its result can play the tick animation. */
    private fun takeCardFor(job: TransferManager.Job): PixelProgressDialog? {
        if (transferCardJob !== job) return null
        val card = transferCard
        transferCard = null
        transferCardJob = null
        return card
    }

    /**
     * Handles finished transfers one at a time, and only while the vault is open: results mention the vault,
     * and Android's "delete the originals?" prompt needs a visible screen. Anything that finished while the app
     * was closed or locked waits here until the vault is opened again.
     */
    private fun pumpTransferResults() {
        if (resultBusy || !vaultVisible || isDestroyed || isFinishing) return
        val job = finishedJobs.firstOrNull() ?: return
        finishedJobs.removeAt(0)
        TransferManager.consume(job)
        resultBusy = true
        when (job.kind) {
            TransferManager.Kind.ADD -> handleAddFinished(job)
            TransferManager.Kind.UNHIDE -> handleUnhideFinished(job)
            TransferManager.Kind.ENCRYPT, TransferManager.Kind.DECRYPT -> handleCryptFinished(job)
        }
    }

    private fun handleAddFinished(job: TransferManager.Job) {
        val r = job.result as? AddResult
        hideHidden = r?.hidden ?: 0
        hideFailed = r?.failed ?: (if (job.failed) job.count else 0)
        hideNotRemoved = r?.notRemoved ?: 0
        hideCancelled = job.cancelled
        hideTickShown = false
        val needConfirm = r?.needConfirm ?: emptyList()
        renderVaultFiles()
        val proceed = {
            when {
                needConfirm.isEmpty() -> finishHide()
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> requestBatchDelete(needConfirm)
                else -> {
                    legacyDeleteQueue.addAll(needConfirm)
                    processLegacyDeleteQueue()
                }
            }
        }
        val card = takeCardFor(job)
        if (card != null && !job.cancelled && hideFailed == 0 && hideHidden > 0) {
            // Tick, then the card fades away on its own and we carry on.
            hideTickShown = card.wasShown
            card.update(title = "Added", sub = if (hideHidden == 1) "1 file" else "$hideHidden files", progress = 1f)
            cardClosing = true
            card.finishSuccess {
                cardClosing = false
                proceed()
                syncTransferCard()
            }
        } else {
            card?.dismiss()
            proceed()
            syncTransferCard()
        }
    }

    private fun handleUnhideFinished(job: TransferManager.Job) {
        val r = job.result as? UnhideResult
        val ok = r?.restored ?: 0
        val total = r?.total ?: job.count
        selected.clear()
        renderVaultFiles()
        val msg = when {
            job.cancelled && ok == 0 -> "Cancelled (nothing was restored)"
            job.cancelled -> "Cancelled · restored $ok of $total files"
            ok == 0 -> "Couldn't unhide (still safe in the vault)"
            ok == total && ok == 1 -> "Restored to ${r?.lastFolder ?: "storage"}"
            ok == total -> "Restored $ok files"
            else -> "Restored $ok of $total files (the rest are still safe in the vault)"
        }
        val report = { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        val card = takeCardFor(job)
        if (card != null && !job.cancelled && total > 0 && ok == total) {
            card.update(title = "Restored", sub = if (ok == 1) "1 file" else "$ok files", progress = 1f)
            cardClosing = true
            card.finishSuccess {
                cardClosing = false
                report()
                syncTransferCard()
            }
        } else {
            card?.dismiss()
            report()
            syncTransferCard()
        }
        resultBusy = false
        pumpTransferResults()
    }

    private fun handleCryptFinished(job: TransferManager.Job) {
        val encrypt = job.kind == TransferManager.Kind.ENCRYPT
        val r = job.result as? CryptResult
        val ok = r?.done ?: 0
        val total = r?.total ?: job.count
        val hook = viewerCryptHooks.remove(job)
        selected.clear()
        thumbCache.clear()
        val newFile = r?.firstNew
        if (hook != null && newFile != null) hook(newFile)
        renderVaultFiles()
        val verb = if (encrypt) "Encrypted" else "Decrypted"
        val msg = when {
            job.cancelled && ok == 0 -> "Cancelled (files left unchanged)"
            job.cancelled -> "Cancelled · $verb $ok of $total files"
            ok == 0 -> "Couldn't ${if (encrypt) "encrypt" else "decrypt"} (files left unchanged)"
            ok < total -> "$verb $ok of $total files"
            ok == 1 -> if (hook != null) verb else "$verb 1 file"
            else -> "$verb $ok files"
        }
        val report = { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
        val card = takeCardFor(job)
        if (card != null && !job.cancelled && total > 0 && ok == total) {
            card.update(title = verb, sub = if (ok == 1) "1 file" else "$ok files", progress = 1f)
            cardClosing = true
            card.finishSuccess {
                cardClosing = false
                report()
                syncTransferCard()
            }
        } else {
            card?.dismiss()
            report()
            syncTransferCard()
        }
        resultBusy = false
        pumpTransferResults()
    }

    /**
     * Android 13+: ask once, so the background transfer can show its (deliberately bland) progress notification.
     * Transfers run either way; this only decides whether that notification is visible.
     */
    private fun askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        if (prefs.getBoolean("notif_asked", false)) return
        prefs.edit().putBoolean("notif_asked", true).apply()
        leavingForTask = true   // the system prompt pauses us; don't lock the vault for it
        try {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        } catch (_: Exception) {
            leavingForTask = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestBatchDelete(uris: List<Uri>) {
        batchDeleteCount = uris.size
        try {
            val request = MediaStore.createDeleteRequest(contentResolver, uris)
            leavingForTask = true
            startIntentSenderForResult(request.intentSender, REQ_DELETE_BATCH, null, 0, 0, 0)
        } catch (e: Exception) {
            leavingForTask = false
            hideNotRemoved += uris.size
            finishHide()
        }
    }

    private fun processLegacyDeleteQueue() {
        while (legacyDeleteQueue.isNotEmpty()) {
            val uri = legacyDeleteQueue.removeAt(0)
            try {
                if (contentResolver.delete(uri, null, null) <= 0) hideNotRemoved++
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    legacyCurrent = uri
                    leavingForTask = true
                    try {
                        startIntentSenderForResult(e.userAction.actionIntent.intentSender, REQ_DELETE_LEGACY, null, 0, 0, 0)
                        return
                    } catch (_: Exception) {
                        leavingForTask = false
                        legacyCurrent = null
                        hideNotRemoved++
                    }
                } else {
                    hideNotRemoved++
                }
            } catch (_: Exception) {
                hideNotRemoved++
            }
        }
        finishHide()
    }

    private fun finishHide() {
        resultBusy = false
        renderVaultFiles()
        if (hideFailed == 0 && hideNotRemoved == 0) {
            if (hideCancelled) {
                val said = if (hideHidden > 0) "Cancelled · $hideHidden file(s) hidden in the vault" else "Cancelled"
                Toast.makeText(this, said, Toast.LENGTH_SHORT).show()
            } else if (hideHidden > 0 && !hideTickShown) {
                Toast.makeText(this, "$hideHidden file(s) hidden in the vault", Toast.LENGTH_SHORT).show()
            }
            pumpTransferResults()
            return
        }
        val msg = StringBuilder()
        if (hideCancelled) msg.append("Cancelled.\n\n")
        if (hideHidden > 0) msg.append("$hideHidden file(s) are now in the vault.\n\n")
        if (hideNotRemoved > 0) {
            msg.append("$hideNotRemoved original(s) could not be removed from their old location, so they are still visible there. ")
            msg.append("Delete them manually, or allow the deletion request when Android asks.\n\n")
        }
        if (hideFailed > 0) msg.append("$hideFailed file(s) could not be copied into the vault and were left untouched.")
        showStyledDialog("Hide result", msgBody(msg.toString().trim()), "OK", null) {}
        pumpTransferResults()
    }

    /** [siblings] is the list the file was opened from; images and videos in it can be swiped through in the viewer. */
    private fun openVaultFile(file: File, siblings: List<File> = listOf(file)) {
        val name = VaultManager.displayName(file)
        val mime = VaultManager.mimeFor(name)
        when {
            name.endsWith(HiddenApps.EXT, ignoreCase = true) -> showHiddenAppMenu(file)
            name.endsWith(".txt", ignoreCase = true) -> showTextEditor(file)
            mime.startsWith("image/") || mime.startsWith("video/") -> showGallery(siblings, file)
            mime.startsWith("audio/") -> showGallery(listOf(file), file)
            else -> openExternally(file, mime)
        }
    }

    /** The vault file currently shown in a viewer; it changes when the file is encrypted/decrypted from the viewer. */
    private class ViewerState(file: File) {
        /** Set by [showViewer] so the title follows [file] (swiping to another image, encrypting, ...). */
        var onFileChanged: (() -> Unit)? = null
        /** Set by the gallery: the shown file was replaced by an encrypted / decrypted copy. */
        var onReplaced: ((File) -> Unit)? = null
        var file: File = file
            set(value) {
                field = value
                onFileChanged?.invoke()
            }
    }

    private class MenuRow(val icon: Int, val label: String, val color: Int, val action: () -> Unit)

    private val chromeIn by lazy { PathInterpolator(0.05f, 0.7f, 0.1f, 1f) }
    private val chromeOut by lazy { PathInterpolator(0.3f, 0f, 0.8f, 0.15f) }

    /**
     * Fades [v] in (sliding from [slide] px away) or out (sliding back) instead of snapping. Calling it again
     * mid-animation reverses smoothly from wherever the view is, and calling it when nothing needs to change
     * does nothing, so it is safe to call from frequently-run code.
     */
    private fun animateShown(v: View, shown: Boolean, slide: Float) {
        // The view's tag remembers which way it is heading, so repeated calls don't keep restarting a fade.
        if (shown) {
            if (v.visibility == View.VISIBLE && (v.tag == "in" || (v.alpha == 1f && v.translationY == 0f))) return
            v.animate().cancel()
            if (v.visibility != View.VISIBLE) {
                v.alpha = 0f
                v.translationY = slide
                v.visibility = View.VISIBLE
            }
            v.tag = "in"
            v.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(chromeIn).setListener(null).start()
        } else {
            if (v.visibility != View.VISIBLE || v.tag == "out") return
            v.animate().cancel()
            v.tag = "out"
            v.animate().alpha(0f).translationY(slide).setDuration(200).setInterpolator(chromeOut)
                .setListener(object : AnimatorListenerAdapter() {
                    private var cancelled = false
                    override fun onAnimationCancel(animation: Animator) { cancelled = true }
                    override fun onAnimationEnd(animation: Animator) { if (!cancelled) v.visibility = View.GONE }
                }).start()
        }
    }

    private fun shortName(name: String): String = if (name.length > 18) name.take(18) + "..." else name

    /**
     * Full-screen viewer. Top bar: back arrow (left), file name (centre, up to 75% of the width, then scrolling),
     * three-dot menu (right). The menu is the same in every viewer, in this order:
     * Open with, Encrypt/Decrypt, Unhide, Share, Delete (red).
     * [beforeAction] runs before Open with / Encrypt / Decrypt / Unhide / Share (the editor uses it to save pending edits).
     */
    private fun showViewer(
        state: ViewerState,
        content: View,
        editor: Boolean = false,
        beforeAction: () -> Unit = {},
        onClose: () -> Unit = {}
    ) {
        viewerDialog?.dismiss()
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        val fg = if (editor) pal.text else Color.WHITE
        val rippleColor = if (editor) pal.ripple else Color.parseColor("#33FFFFFF")
        val root = FrameLayout(this).apply { setBackgroundColor(if (editor) pal.bg else Color.BLACK) }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(if (editor) pal.bg else Color.parseColor("#B3000000"))
            setPadding(dp(4), 0, dp(4), 0)
        }
        fun barButton(icon: Int, desc: String, onClick: (View) -> Unit): ImageView = ImageView(this).apply {
            setImageResource(icon)
            setColorFilter(fg)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = desc
            background = RippleDrawable(
                ColorStateList.valueOf(rippleColor), null,
                GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.BLACK) }
            )
            setOnClickListener { onClick(it) }
        }
        // The name gets up to 75% of the screen width; a longer one scrolls left in a loop (marquee) and comes
        // back in from the right. A marquee only runs while the view is selected.
        val screenW = resources.displayMetrics.widthPixels
        val titleMax = minOf((screenW * 0.75f).toInt(), screenW - dp(104))
        val title = TextView(this).apply {
            text = VaultManager.displayName(state.file)
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            maxWidth = titleMax
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(14))
            isSelected = true
            gravity = Gravity.CENTER
        }
        state.onFileChanged = { title.text = VaultManager.displayName(state.file) }
        bar.addView(barButton(R.drawable.ic_arrow_back, "Close") { dialog.dismiss() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        bar.addView(FrameLayout(this).apply {
            addView(title, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(barButton(R.drawable.ic_more_vert, "More options") { anchor ->
            val encrypted = VaultManager.isEncrypted(state.file)
            val rows = ArrayList<MenuRow>()
            rows.add(MenuRow(R.drawable.ic_open_in_new, "Open with", pal.text) {
                beforeAction()
                openExternally(state.file, VaultManager.mimeFor(VaultManager.displayName(state.file)), chooser = true, keepTemp = true)
            })
            rows.add(MenuRow(
                if (encrypted) R.drawable.ic_lock_open else R.drawable.ic_lock,
                if (encrypted) "Decrypt" else "Encrypt", pal.text
            ) {
                beforeAction()
                cryptViewerFile(state, !encrypted) { title.text = VaultManager.displayName(state.file) }
            })
            rows.add(MenuRow(R.drawable.ic_unhide, "Unhide", pal.text) {
                beforeAction()
                dialog.dismiss()
                unhideFile(state.file)
            })
            rows.add(MenuRow(R.drawable.ic_share, "Share", pal.text) {
                beforeAction()
                shareFiles(listOf(state.file), keepTemp = true)
            })
            rows.add(MenuRow(R.drawable.ic_delete, "Delete", if (isNightMode()) Color.parseColor("#FF6B5E") else Color.parseColor("#D93025")) {
                dialog.dismiss()
                confirmDelete(state.file)
            })
            showViewerMenu(anchor, rows)
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(bar, FrameLayout.LayoutParams(-1, dp(56), Gravity.TOP))
        // The video player hides this bar together with its own controls (and brings it back with them).
        val chromeSetter: (Boolean) -> Unit = { show -> if (!editor) animateShown(bar, show, -dpf(20)) }
        viewerChrome = chromeSetter
        viewerBar = { show -> if (!editor) bar.visibility = if (show) View.VISIBLE else View.GONE }
        if (playerContentShown) bar.visibility = View.GONE

        dialog.setContentView(root)
        if (editor) {
            // Keep the text clear of the keyboard (adjustResize is ignored by fullscreen windows).
            dialog.window?.let { w ->
                w.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                )
                if (Build.VERSION.SDK_INT >= 30) w.setDecorFitsSystemWindows(false)
            }
            root.setOnApplyWindowInsetsListener { v, ins ->
                val bottom = if (Build.VERSION.SDK_INT >= 30) {
                    maxOf(
                        ins.getInsets(android.view.WindowInsets.Type.ime()).bottom,
                        ins.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
                    )
                } else {
                    @Suppress("DEPRECATION") ins.systemWindowInsetBottom
                }
                v.setPadding(0, 0, 0, bottom)
                ins
            }
        }
        dialog.setOnDismissListener {
            if (viewerChrome === chromeSetter) viewerChrome = null
            viewerBar = null
            playerContentShown = false
            viewerMenuOpen = false
            onClose()
            if (viewerDialog === dialog) viewerDialog = null
            VaultManager.clearTemp(applicationContext)
        }
        viewerDialog = dialog
        dialog.show()
    }

    private fun showViewerMenu(anchor: View, rows: List<MenuRow>) {
        var popup: PopupWindow? = null
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dpf(28); setColor(pal.numBg) }
            setPadding(0, dp(8), 0, dp(8))
            minimumWidth = dp(200)
        }
        for (r in rows) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setPadding(dp(18), 0, dp(24), 0)
                background = rippleBg()
                setOnClickListener { popup?.dismiss(); r.action() }
            }
            row.addView(ImageView(this).apply {
                setImageResource(r.icon)
                setColorFilter(r.color)
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            row.addView(TextView(this).apply {
                text = r.label
                setTextColor(r.color)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setPadding(dp(16), 0, 0, 0)
            }, LinearLayout.LayoutParams(-2, -2))
            card.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        val p = PopupWindow(card, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        p.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        p.isOutsideTouchable = true
        popup = p
        viewerMenuOpen = true
        p.setOnDismissListener { viewerMenuOpen = false }
        card.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val w = card.measuredWidth
        card.pivotX = w.toFloat()
        card.pivotY = 0f
        card.scaleX = 0.85f
        card.scaleY = 0.85f
        card.alpha = 0f
        p.showAsDropDown(anchor, anchor.width - w - dp(8), dp(2))
        card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(160)
            .setInterpolator(PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start()
    }

    /** Encrypts/decrypts the file shown in a viewer, then points the viewer at the renamed file. */
    /** Viewer-specific follow-up for an encrypt/decrypt job (swap in the new file, refresh the title). */
    private val viewerCryptHooks = HashMap<TransferManager.Job, (File) -> Unit>()

    private fun cryptViewerFile(state: ViewerState, encrypt: Boolean, onDone: () -> Unit) {
        val job = startCrypt(listOf(state.file), encrypt)
        viewerCryptHooks[job] = { newFile ->
            state.onReplaced?.invoke(newFile)
            state.file = newFile
            onDone()
        }
    }

    private fun showTextEditor(file: File) {
        if (file.length() > 1_100_000L) {
            Toast.makeText(this, "Too large for the built-in editor", Toast.LENGTH_SHORT).show()
            openExternally(file, "text/plain")
            return
        }
        Thread {
            val bytes = VaultManager.readDecrypted(file)
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                if (bytes == null) {
                    Toast.makeText(this, "Unable to open this file", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                // Keep the file's own encoding so saving never alters bytes the user didn't touch.
                val cs = try {
                    Charsets.UTF_8.newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(bytes))
                    Charsets.UTF_8
                } catch (_: Exception) {
                    Charsets.ISO_8859_1
                }
                buildTextEditor(file, String(bytes, cs), cs)
            }
        }.start()
    }

    private fun buildTextEditor(file: File, initial: String, cs: java.nio.charset.Charset) {
        val state = ViewerState(file)
        var dirty = false
        val et = EditText(this).apply {
            setText(initial)
            setTextColor(pal.text)
            setHintTextColor(pal.icon)
            hint = "Tap to start typing"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setLineSpacing(0f, 1.15f)
            gravity = Gravity.TOP or Gravity.START
            background = null
            setPadding(dp(16), dp(12), dp(16), dp(24))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            isVerticalScrollBarEnabled = true
            isSaveEnabled = false
        }
        et.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { dirty = true }
        })
        fun save(announce: Boolean) {
            if (!dirty) return
            val ok = VaultManager.overwriteContent(applicationContext, state.file, et.text.toString().toByteArray(cs))
            if (ok) {
                dirty = false
                thumbCache.clear()
                if (announce) Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Couldn't save changes", Toast.LENGTH_SHORT).show()
            }
        }
        // Edits are saved automatically: on back arrow / system back / leaving the app, and before share or encrypt/decrypt.
        val holder = FrameLayout(this).apply {
            setPadding(0, dp(56), 0, 0)
            addView(et, FrameLayout.LayoutParams(-1, -1))
        }
        showViewer(state, holder, editor = true, beforeAction = { save(false) }, onClose = { save(true) })
    }

    // ------------------------------------------------------------------------------------------------
    // Gallery: swipe left / right between the images and videos of the list you opened a file from.
    // ------------------------------------------------------------------------------------------------

    private fun isGalleryMedia(file: File): Boolean {
        val mime = VaultManager.mimeFor(VaultManager.displayName(file))
        return mime.startsWith("image/") || mime.startsWith("video/")
    }

    private fun galleryAsync(block: () -> Unit) {
        try { galleryExecutor.execute { block() } } catch (_: Exception) {}
    }

    private class PlayerHandle(val view: View, val release: () -> Unit)

    private class GalleryHolder(val frame: FrameLayout) : RecyclerView.ViewHolder(frame) {
        var page: GalleryPage? = null
        var index = -1
    }

    private fun showGallery(siblings: List<File>, start: File) {
        val startMime = VaultManager.mimeFor(VaultManager.displayName(start))
        val items = ArrayList<File>()
        if (startMime.startsWith("audio/")) {
            items.add(start)
        } else {
            for (f in siblings) if (isGalleryMedia(f)) items.add(f)
            if (items.none { it.absolutePath == start.absolutePath }) items.add(start)
        }
        var index = items.indexOfFirst { it.absolutePath == start.absolutePath }.coerceAtLeast(0)
        val state = ViewerState(items[index])
        val galleryAdapter = GalleryAdapter(items)
        galleryAdapter.activeIndex = index

        val pager = ViewPager2(this)
        pager.adapter = galleryAdapter
        pager.offscreenPageLimit = 1
        pager.setPageTransformer(MarginPageTransformer(dp(16)))
        pager.setCurrentItem(index, false)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                index = position
                state.file = items[position]
                viewerChrome?.invoke(true)
            }
            override fun onPageScrollStateChanged(scrollState: Int) {
                // Videos only start (and the one you left only stops) once the swipe has settled.
                if (scrollState == ViewPager2.SCROLL_STATE_IDLE) galleryAdapter.activate(index)
            }
        })
        state.onReplaced = { newFile ->
            items[index] = newFile
            galleryAdapter.pageAt(index)?.file = newFile
        }
        showViewer(state, pager, onClose = {
            galleryAdapter.releaseAll()
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        })
    }

    private inner class GalleryAdapter(val items: MutableList<File>) : RecyclerView.Adapter<GalleryHolder>() {
        var activeIndex = -1
        private val holders = HashSet<GalleryHolder>()

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryHolder {
            val frame = FrameLayout(this@MainActivity).apply {
                layoutParams = RecyclerView.LayoutParams(-1, -1)
                setBackgroundColor(Color.BLACK)
            }
            return GalleryHolder(frame)
        }

        override fun onBindViewHolder(holder: GalleryHolder, position: Int) {
            holder.page?.release()
            val page = GalleryPage(holder.frame, items[position])
            holder.page = page
            holder.index = position
            holders.add(holder)
            page.bind()
            if (position == activeIndex) {
                holder.frame.post {
                    if (holder.page === page && holder.index == activeIndex) page.makeActive(true)
                }
            }
        }

        override fun onViewRecycled(holder: GalleryHolder) {
            holder.page?.release()
            holder.page = null
            holders.remove(holder)
        }

        fun activate(i: Int) {
            activeIndex = i
            for (h in holders.toList()) h.page?.makeActive(h.index == i)
        }

        fun pageAt(i: Int): GalleryPage? = holders.firstOrNull { it.index == i }?.page

        fun releaseAll() {
            for (h in holders.toList()) {
                h.page?.release()
                h.page = null
            }
            holders.clear()
        }
    }

    /**
     * One page of the gallery. Images load as soon as the page exists (so the next / previous one is ready before
     * you swipe). Videos show their thumbnail until the page is the settled, current one; only then does the
     * player start (encrypted videos are decrypted in the page, with a progress ring), and it is torn down again
     * when you swipe away.
     */
    private inner class GalleryPage(val frame: FrameLayout, var file: File) {
        private val isImage = VaultManager.mimeFor(VaultManager.displayName(file)).startsWith("image/")
        private var released = false
        private var active = false
        private var poster: Bitmap? = null
        private var player: PlayerHandle? = null
        private var decryptCancel: AtomicBoolean? = null
        private var tempMedia: File? = null

        fun bind() {
            if (isImage) bindImage() else showPoster()
        }

        private fun bindImage() {
            frame.removeAllViews()
            val f = file
            val dm = resources.displayMetrics
            val iv = ZoomImageView(this@MainActivity, dp(56))
            frame.addView(iv, FrameLayout.LayoutParams(-1, -1))
            // The grid thumbnail shows instantly; the sharp version replaces it as soon as it is decoded.
            val cached = thumbCache[f.absolutePath + f.lastModified()]
            if (cached != null) iv.setImageBitmap(cached)
            galleryAsync {
                val bm = try {
                    VaultManager.readDecrypted(f)?.let {
                        VaultManager.decodeBitmap(it, (dm.widthPixels * 1.5f).toInt(), (dm.heightPixels * 1.5f).toInt())
                    }
                } catch (_: Throwable) {
                    null
                }
                runOnUiThread {
                    if (released || isDestroyed || isFinishing) return@runOnUiThread
                    if (bm != null) iv.setImageBitmap(bm)
                    else if (cached == null) showMessage("Unable to open this image")
                }
            }
        }

        private fun showMessage(msg: String) {
            frame.removeAllViews()
            frame.addView(TextView(this@MainActivity).apply {
                text = msg
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(-1, -1))
        }

        /** Video page at rest: just its thumbnail. */
        private fun showPoster() {
            frame.removeAllViews()
            val iv = ImageView(this@MainActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setBackgroundColor(Color.BLACK)
            }
            frame.addView(iv, FrameLayout.LayoutParams(-1, -1))
            val have = poster
            if (have != null) {
                iv.setImageBitmap(have)
                return
            }
            val f = file
            val cached = thumbCache[f.absolutePath + "#frame30"] ?: thumbCache[f.absolutePath + f.lastModified()]
            if (cached != null) {
                poster = cached
                iv.setImageBitmap(cached)
                return
            }
            galleryAsync {
                val bm = try {
                    VaultManager.cachedVideoFrame(this@MainActivity, f) ?: VaultManager.readThumb(f)
                } catch (_: Throwable) { null }
                if (bm != null) runOnUiThread {
                    if (released) return@runOnUiThread
                    poster = bm
                    iv.setImageBitmap(bm)
                }
            }
        }

        fun makeActive(on: Boolean) {
            if (released || isImage || active == on) return
            active = on
            if (on) startPlayer() else stopPlayer()
        }

        private fun startPlayer() {
            val f = file
            // Plain files play straight from the vault; only encrypted ones need decrypting first.
            if (!VaultManager.isEncrypted(f)) {
                showPlayer(f)
                return
            }
            val cancel = AtomicBoolean(false)
            decryptCancel = cancel
            val ring = ProgressRingView(
                this@MainActivity, true, pal.result,
                androidx.core.graphics.ColorUtils.setAlphaComponent(pal.result, 60), pal.result, Color.BLACK
            )
            ring.setProgress(-1f)
            val label = TextView(this@MainActivity).apply {
                text = "Opening…"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
            }
            frame.removeAllViews()
            frame.addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(ring, LinearLayout.LayoutParams(dp(56), dp(56)))
                addView(label, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(12) })
            }, FrameLayout.LayoutParams(-1, -1))

            Thread {
                var last = 0L
                val tmp = VaultManager.prepareForOpen(applicationContext, f, unique = true, cancelled = { cancel.get() }) { p ->
                    val now = System.currentTimeMillis()
                    if (now - last >= 100L) {
                        last = now
                        runOnUiThread {
                            if (!cancel.get() && !released) {
                                ring.setProgress(p)
                                label.text = "Opening… ${(p * 100).toInt()}%"
                            }
                        }
                    }
                }
                runOnUiThread {
                    if (cancel.get() || released || !active || isDestroyed || isFinishing) {
                        tmp?.delete()
                        return@runOnUiThread
                    }
                    decryptCancel = null
                    if (tmp == null) {
                        showMessage("Decryption failed")
                        return@runOnUiThread
                    }
                    tempMedia = tmp
                    showPlayer(tmp)
                }
            }.start()
        }

        private fun showPlayer(media: File) {
            if (released || !active) return
            val handle = buildPlayerView(media, poster, file)
            player = handle
            frame.removeAllViews()
            frame.addView(handle.view, FrameLayout.LayoutParams(-1, -1))
        }

        private fun stopPlayer() {
            decryptCancel?.set(true)
            decryptCancel = null
            player?.release()
            player = null
            tempMedia?.delete()
            tempMedia = null
            if (!released) showPoster()
        }

        fun release() {
            if (released) return
            released = true
            active = false
            decryptCancel?.set(true)
            decryptCancel = null
            player?.release()
            player = null
            tempMedia?.delete()
            tempMedia = null
            frame.removeAllViews()
        }
    }

    /**
     * Subtitle files that sit next to a video in the vault and share its name ("Movie.mp4" + "Movie.en.srt").
     * They are decrypted into the temp folder (listed in [tempOut] so the player can delete them) and mpv loads them.
     */
    private fun sidecarSubtitles(vaultFile: File, tempOut: MutableList<File>): List<File> {
        val out = ArrayList<File>()
        try {
            val base = VaultManager.displayName(vaultFile).substringBeforeLast('.').lowercase(java.util.Locale.ROOT)
            val dir = vaultFile.parentFile ?: return out
            val siblings = dir.listFiles { f -> f.isFile && (f.name.endsWith(".vlt") || f.name.endsWith(".enc")) } ?: return out
            val tmpDir = File(cacheDir, "vault_tmp").apply { mkdirs() }
            for (f in siblings.sortedBy { it.name }) {
                val n = VaultManager.displayName(f)
                if (!n.lowercase(java.util.Locale.ROOT).startsWith(base)) continue
                val ext = n.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
                if (ext !in setOf("srt", "vtt", "ass", "ssa", "sub")) continue
                val bytes = VaultManager.readDecrypted(f) ?: continue
                val tmp = File(tmpDir, "sub_${System.nanoTime()}.$ext")
                tmp.writeBytes(bytes)
                tempOut.add(tmp)
                out.add(tmp)
            }
        } catch (_: Throwable) {
        }
        return out
    }

    /**
     * Builds the player for [media] (a plain file; encrypted ones are decrypted to a temp file first) on top of
     * libmpv ([MpvEngine]), with the in-player features of Nosved Player. [vaultFile] is the file in the vault
     * (used for "Open with" and for finding subtitle files). [posterBmp] covers the video until its first frame is
     * on screen.
     *
     * Controls: seek bar, play / pause, +-10 s, speed 0.25x-4x, audio track + audio delay, subtitles (tracks,
     * delay, size, position, files next to the video), aspect (fit / stretch / crop / 100 %), loop, decoder
     * (HW / HW+ / SW), enhance (brightness, contrast, saturation, gamma, hue), chapters, frame step, mute, media
     * information, rotate, screen lock, volume boost up to 200 %, brightness and volume swipes, double-tap skip,
     * two-finger tap to pause, three-finger tap for 2x, hold for 2x.
     */
    private fun buildPlayerView(media: File, posterBmp: Bitmap?, vaultFile: File): PlayerHandle {
        playerContentShown = true
        val ui = Handler(Looper.getMainLooper())
        val speeds = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 4f)
        val speedLabels = speeds.map { if (it == 1f) "1x (Normal)" else if (it == it.toInt().toFloat()) "${it.toInt()}x" else "${it}x" }
        var speedIdx = 3
        var dragging = false
        var holdBoost = false
        var fastToggle = false
        var gesture = 0
        var locked = false
        var released = false
        var errorShown = false
        var hasVideo = true
        var fileReady = false
        var boost = 0f // extra volume above the system maximum, 0..1 (= up to 200 %)
        var enBri = 0; var enCon = 0; var enSat = 0; var enGam = 0; var enHue = 0
        var onEnhanceChanged: () -> Unit = {}
        val tempFiles = ArrayList<File>()

        val eng = MpvEngine(this)
        eng.init()
        val surface = eng.surfaceView
        // Continue where the video was left (per vault file). Cleared again when it is watched to the end or the
        // position is within the first / last 3 seconds. Switched off in Settings.
        val resumeKey = vaultFile.absolutePath
        val resumeOn = AppSettings.resumeVideos(this)
        fun saveResume() {
            if (!resumeOn || released) return
            try {
                val d = eng.durationMs
                val pos = eng.positionMs
                val keep = d > 0L && pos >= 3_000L && pos <= d - 3_000L
                AppSettings.saveResumePosition(this, resumeKey, if (keep) pos else 0L)
            } catch (_: Throwable) {
            }
        }
        val posterView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setBackgroundColor(Color.BLACK)
            if (posterBmp != null) setImageBitmap(posterBmp)
            visibility = if (posterBmp != null) View.VISIBLE else View.GONE
        }
        val noteView = TextView(this).apply {
            text = "♪"
            setTextColor(Color.parseColor("#55FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 120f)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        val white = Color.WHITE

        /** Time codes read like Nosved Player's: 03:12, or 1:03:12 for long films. */
        fun fmt(ms: Long, forceHours: Boolean = false): String {
            val t = ms.coerceAtLeast(0L) / 1000
            val h = t / 3600
            val m = (t / 60) % 60
            val s = t % 60
            return if (h > 0 || forceHours) String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
            else String.format(java.util.Locale.US, "%02d:%02d", m, s)
        }
        fun durationMs(): Long = eng.durationMs

        // ---- Nosved Player's chrome: glass controls over the picture, transport dock along the bottom ----
        val glassBg = Color.parseColor("#AD121316")      // #121316 at 68 %
        val glassBorder = Color.parseColor("#24FFFFFF")  // white at 14 %
        val dockBg = Color.parseColor("#C7121316")       // #121316 at 78 %
        val dockBorder = Color.parseColor("#29FFFFFF")   // white at 16 %
        val accentBlue = Color.parseColor("#3B82F6")
        val playInk = Color.parseColor("#0F172A")
        val whiteE6 = Color.argb(230, 255, 255, 255)
        val mono = Typeface.MONOSPACE

        /** Buttons shrink a little while pressed and spring back, as in Nosved Player. */
        fun <T : View> pressable(v: T): T {
            v.setOnTouchListener { view, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.animate().scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(chromeIn).start()
                }
                false
            }
            return v
        }
        /** The glass pill every Nosved control sits on. */
        fun roundBg(bg: Int, border: Int): GradientDrawable = GradientDrawable().apply {
            cornerRadius = dpf(100)
            setColor(bg)
            setStroke(dp(1), border)
        }
        fun iconView(res: Int, sizeDp: Int, tint: Int): ImageView = ImageView(this).apply {
            setImageResource(res)
            setColorFilter(tint)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), Gravity.CENTER)
        }
        /** Circular glass button: the shape of every control in Nosved Player. */
        fun glassButton(sizeDp: Int, res: Int, iconDp: Int, onClick: (View) -> Unit): FrameLayout =
            pressable(FrameLayout(this).apply {
                background = RippleDrawable(
                    ColorStateList.valueOf(Color.parseColor("#33FFFFFF")),
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(glassBg)
                        setStroke(dp(1), glassBorder)
                    },
                    GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { v -> onClick(v) }
                addView(iconView(res, iconDp, Color.WHITE))
            })
        /** Icon button inside a glass capsule (it has no background of its own). */
        fun capsuleButton(sizeDp: Int, res: Int, iconDp: Int, onClick: (View) -> Unit): FrameLayout =
            pressable(FrameLayout(this).apply {
                background = RippleDrawable(
                    ColorStateList.valueOf(Color.parseColor("#33FFFFFF")),
                    null,
                    GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { v -> onClick(v) }
                addView(iconView(res, iconDp, whiteE6))
            }).apply { layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)) }

        val overlay = FrameLayout(this)
        val scrimTop = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#CC000000"), Color.TRANSPARENT)
            )
        }
        val scrimBottom = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.parseColor("#CC000000"), Color.TRANSPARENT)
            )
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
        }
        overlay.addView(scrimTop, FrameLayout.LayoutParams(-1, dp(120), Gravity.TOP))
        overlay.addView(scrimBottom, FrameLayout.LayoutParams(-1, dp(240), Gravity.BOTTOM))
        overlay.addView(topRow, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        overlay.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        val cur = TextView(this).apply {
            text = "00:00"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = mono
            includeFontPadding = false
        }
        val total = TextView(this).apply {
            text = "00:00"
            setTextColor(whiteE6)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = mono
            includeFontPadding = false
        }
        val seek = NosvedSeekBar(this).apply { accent = accentBlue }
        // Medium weight where the phone supports it (API 28+), regular otherwise: Nosved's labels are FontWeight.Medium.
        val monoMedium: Typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.MONOSPACE, 500, false) else Typeface.MONOSPACE
        cur.typeface = monoMedium
        total.typeface = monoMedium
        // Portrait timeline: a glass pill with "elapsed / total" on the left and the speed on the right.
        val timePill = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = monoMedium
            includeFontPadding = false
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = roundBg(glassBg, glassBorder)
            text = "00:00 / 00:00"
        }
        val speedPill = TextView(this).apply {
            setTextColor(whiteE6)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = monoMedium
            includeFontPadding = false
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = roundBg(glassBg, glassBorder)
            text = "1.0x"
        }
        var showRemaining = false
        /** Updates every time label: landscape "elapsed ... total" (tap total for -remaining), portrait pill. */
        fun setTimes(pos: Long) {
            val d = durationMs()
            val hours = d >= 3_600_000L
            cur.text = fmt(pos, hours)
            total.text = if (showRemaining && d > 0L) "-" + fmt((d - pos).coerceAtLeast(0L), hours) else fmt(d, hours)
            timePill.text = fmt(pos) + " / " + fmt(d)
        }
        total.setOnClickListener { showRemaining = !showRemaining; setTimes(if (seek.scrubbing) seek.positionMs else eng.positionMs) }
        val playIcon = ImageView(this).apply {
            setColorFilter(playInk)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER)
        }
        // White circle while paused, white squircle while playing: the button morphs between the two.
        val playBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpf(27)
            setColor(Color.WHITE)
        }
        val playBtn = pressable(FrameLayout(this).apply {
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#1F000000")),
                playBg,
                GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            )
            isClickable = true
            isFocusable = true
            addView(playIcon)
        }).apply { layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)) }

        // Everything laid over the picture fades in and out together.
        var chromeShown = true
        fun setChrome(show: Boolean) {
            chromeShown = show
            // Nosved's top bar replaces the viewer's own title bar while a video is open.
            viewerBar?.invoke(false)
            animateShown(overlay, show, 0f)
        }
        val hide = object : Runnable {
            override fun run() {
                if (released) return
                // Don't pull the controls away while a menu is open.
                if (viewerMenuOpen) { ui.postDelayed(this, 1000); return }
                if (eng.isPlaying && !dragging && hasVideo) setChrome(false)
            }
        }
        fun scheduleHide() {
            ui.removeCallbacks(hide)
            if (eng.isPlaying && hasVideo && !dragging) ui.postDelayed(hide, 3000)
        }
        fun showControls() {
            if (locked) return
            setChrome(true)
            scheduleHide()
        }

        val unlockBtn = pressable(TextView(this).apply {
            text = "Unlock"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            background = roundBg(glassBg, glassBorder)
            visibility = View.GONE
        })
        var iconPlaying: Boolean? = null
        var playRadius = dpf(27)
        fun updatePlayIcon() {
            val playing = eng.isPlaying
            if (iconPlaying == playing) return
            val first = iconPlaying == null
            iconPlaying = playing
            playIcon.setImageResource(if (playing) R.drawable.ic_pv_pause else R.drawable.ic_pv_play)
            if (!first) {
                // A small pop so play <-> pause doesn't just snap.
                playIcon.animate().cancel()
                playIcon.scaleX = 0.8f
                playIcon.scaleY = 0.8f
                playIcon.alpha = 0.5f
                playIcon.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).setInterpolator(chromeIn).start()
                // Circle -> squircle, the same morph Nosved Player's play button makes.
                val to = dpf(if (playing) 15 else 27)
                val from = playRadius
                ValueAnimator.ofFloat(0f, 1f).setDuration(260).apply {
                    addUpdateListener {
                        playRadius = from + (to - from) * it.animatedFraction
                        playBg.cornerRadius = playRadius
                        playBtn.invalidate()
                    }
                    start()
                }
            }
        }
        fun applySpeed() {
            val eff = if (holdBoost || fastToggle) 2f else speeds[speedIdx]
            eng.setSpeed(eff)
            speedPill.text = "${eff}x"
        }
        fun togglePlay() {
            if (!fileReady) return
            eng.toggle()   // the icon follows mpv's own pause event (within one 100 ms tick)
            showControls()
        }
        val pill = TextView(this).apply {
            setTextColor(white)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dpf(100)
                setColor(Color.parseColor("#99000000"))
            }
            visibility = View.GONE
        }
        val hidePill = Runnable { animateShown(pill, false, 0f) }
        fun showPill(msg: String, grav: Int, sticky: Boolean = false) {
            pill.text = msg
            val lp = pill.layoutParams as FrameLayout.LayoutParams
            lp.gravity = grav
            lp.topMargin = if ((grav and Gravity.VERTICAL_GRAVITY_MASK) == Gravity.TOP) dp(120) else dp(24)
            pill.layoutParams = lp
            animateShown(pill, true, 0f)
            ui.removeCallbacks(hidePill)
            if (!sticky) ui.postDelayed(hidePill, 800)
        }
        // ---- Nosved-style gesture panels: volume on the left, brightness on the right ----
        class SidePanel(val box: LinearLayout, val fill: View, val percent: TextView)
        fun sidePanel(res: Int, start: Boolean): SidePanel {
            val pct = TextView(this).apply {
                setTextColor(Color.parseColor("#DDE3EC"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = monoMedium
                gravity = Gravity.CENTER
            }
            val fillV = View(this).apply { background = GradientDrawable().apply { cornerRadius = dpf(6); setColor(accentBlue) } }
            val track = FrameLayout(this).apply {
                background = GradientDrawable().apply { cornerRadius = dpf(6); setColor(Color.argb(31, 255, 255, 255)) }
                addView(fillV, FrameLayout.LayoutParams(-1, 0, Gravity.BOTTOM))
            }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(14), 0, dp(14))
                background = GradientDrawable().apply {
                    cornerRadius = dpf(28)
                    setColor(Color.parseColor("#E6161B22"))
                    setStroke(dp(1), Color.parseColor("#2A3340"))
                }
                addView(iconView(res, 22, Color.parseColor("#DDE3EC")), LinearLayout.LayoutParams(dp(22), dp(22)))
                addView(track, LinearLayout.LayoutParams(dp(12), dp(150)).apply { topMargin = dp(12); bottomMargin = dp(12) })
                addView(pct, LinearLayout.LayoutParams(-2, -2))
                visibility = View.GONE
                isClickable = false
            }
            overlay.addView(box, FrameLayout.LayoutParams(dp(56), -2, Gravity.CENTER_VERTICAL or (if (start) Gravity.START else Gravity.END)).apply {
                if (start) marginStart = dp(24) else marginEnd = dp(24)
            })
            return SidePanel(box, fillV, pct)
        }
        val volPanel = sidePanel(R.drawable.ic_pv_volume, true)
        val briPanel = sidePanel(R.drawable.ic_pv_brightness, false)
        val hidePanels = Runnable { animateShown(volPanel.box, false, 0f); animateShown(briPanel.box, false, 0f) }
        fun showPanel(p: SidePanel, level: Float, max: Float) {
            val frac = (level / max).coerceIn(0f, 1f)
            (p.fill.layoutParams as FrameLayout.LayoutParams).height = Math.round(dp(150) * frac)
            p.fill.requestLayout()
            p.percent.text = "${Math.round(level * 100)}%"
            animateShown(p.box, true, 0f)
            ui.removeCallbacks(hidePanels)
        }
        // ---- centre play / pause flash on double tap ----
        val flash = FrameLayout(this).apply {
            background = GradientDrawable().apply { cornerRadius = dpf(28); setColor(Color.parseColor("#99000000")) }
            visibility = View.GONE
            isClickable = false
        }
        val flashIcon = iconView(R.drawable.ic_pv_play, 40, Color.WHITE)
        flash.addView(flashIcon, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
        overlay.addView(flash, FrameLayout.LayoutParams(dp(88), dp(88), Gravity.CENTER))
        val hideFlash = Runnable { animateShown(flash, false, 0f) }
        fun flashPlayState() {
            flashIcon.setImageResource(if (eng.paused) R.drawable.ic_pv_pause else R.drawable.ic_pv_play)
            animateShown(flash, true, 0f)
            ui.removeCallbacks(hideFlash)
            ui.postDelayed(hideFlash, 600)
        }
        fun skip(deltaMs: Long) {
            val d = durationMs()
            if (d <= 0L) return
            val t = (eng.positionMs + deltaMs).coerceIn(0L, d)
            eng.seekTo(t, exact = true)
            seek.positionMs = t
            setTimes(t)
            val label = (if (deltaMs < 0) "−${-deltaMs / 1000}s" else "+${deltaMs / 1000}s") + " (" + fmt(t) + ")"
            showPill(label, (if (deltaMs < 0) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL)
        }
        // ---- bottom-right capsule: no leftover chip row ----

        seek.onScrubStart = {
            dragging = true
            ui.removeCallbacks(hide)
        }
        seek.onSeek = { pos, finished ->
            if (durationMs() > 0L) eng.seekTo(pos, exact = finished)
            setTimes(pos)
            if (finished) {
                dragging = false
                showControls()
            }
        }
        // Keep the gallery's horizontal swipe from stealing a drag on the bar.
        seek.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        // ---- small builders for the settings dialogs ----
        fun hint(text: String): TextView = TextView(this).apply {
            this.text = text
            setTextColor(pal.icon)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(24), dp(10), dp(24), dp(4))
        }
        fun sectionLabel(text: String): TextView = TextView(this).apply {
            this.text = text
            setTextColor(pal.cursor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(24), dp(14), dp(24), dp(2))
        }
        /** Label + live value above a slider. [onChange] runs while dragging. */
        fun sliderRow(label: String, min: Int, max: Int, value: Int, show: (Int) -> String, onChange: (Int) -> Unit): View {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(2)) }
            val valueText = TextView(this).apply {
                text = show(value); setTextColor(pal.icon); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }
            col.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(TextView(this@MainActivity).apply {
                    text = label; setTextColor(pal.text); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(valueText)
            })
            col.addView(SeekBar(this).apply {
                this.max = max - min
                progress = value - min
                progressTintList = ColorStateList.valueOf(pal.cursor)
                thumbTintList = ColorStateList.valueOf(pal.cursor)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                        valueText.text = show(p + min)
                        if (fromUser) onChange(p + min)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar) {}
                    override fun onStopTrackingTouch(sb: SeekBar) {}
                })
                setOnTouchListener { v, ev ->
                    when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
            }, LinearLayout.LayoutParams(-1, dp(40)))
            return col
        }
        /** Label with  −  value  +  buttons, for delays. */
        fun stepperRow(label: String, start: Long, step: Long, onChange: (Long) -> Unit): View {
            var v = start
            val valueText = TextView(this).apply {
                setTextColor(pal.text); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); gravity = Gravity.CENTER
                minWidth = dp(76)
            }
            fun refresh() { valueText.text = (if (v > 0) "+" else "") + v + " ms" }
            refresh()
            fun btn(sign: String, d: Long) = TextView(this).apply {
                text = sign
                setTextColor(pal.cursor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                gravity = Gravity.CENTER
                background = rippleBg(20)
                setOnClickListener { v += d; refresh(); onChange(v) }
            }
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(24), dp(6), dp(16), dp(6))
                addView(TextView(this@MainActivity).apply {
                    text = label; setTextColor(pal.text); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(btn("−", -step), LinearLayout.LayoutParams(dp(40), dp(40)))
                addView(valueText)
                addView(btn("+", step), LinearLayout.LayoutParams(dp(40), dp(40)))
            }
        }

        // ---- track pickers ----
        fun trackLabel(t: MpvEngine.Track, n: Int): String {
            val lang = t.lang.takeIf { it.isNotBlank() && it != "und" }
                ?.let { java.util.Locale(it).displayLanguage.ifBlank { it } }
            val name = when {
                t.title.isNotBlank() -> t.title
                lang != null -> lang
                else -> "Track ${n + 1}"
            }
            val extra = ArrayList<String>()
            if (t.codec.isNotBlank()) extra.add(t.codec.uppercase(java.util.Locale.ROOT))
            if (t.external) extra.add("file")
            return if (extra.isEmpty()) name else "$name · ${extra.joinToString(" ")}"
        }
        fun showAudioSheet() {
            val tracks = eng.tracks().filter { it.type == "audio" }
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            if (tracks.isEmpty()) {
                body.addView(hint("No audio tracks in this file"))
            } else {
                val labels = tracks.mapIndexed { i, t -> trackLabel(t, i) }
                body.addView(radioRows(labels, tracks.indexOfFirst { it.selected }) { eng.selectAudio(tracks[it].id) })
            }
            body.addView(sectionLabel("Sync"))
            body.addView(stepperRow("Audio delay", eng.audioDelayMs(), 50L) { eng.setAudioDelayMs(it) })
            body.addView(hint("Use + if the sound comes before the picture, − if it comes after."))
            showStyledDialog("Audio", body, "Done", null) { showControls() }
        }
        fun showSubtitleSheet() {
            val tracks = eng.tracks().filter { it.type == "sub" }
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val labels = ArrayList<String>().apply { add("Off"); tracks.forEachIndexed { i, t -> add(trackLabel(t, i)) } }
            val sel = tracks.indexOfFirst { it.selected }.let { if (it < 0) 0 else it + 1 }
            body.addView(radioRows(labels, sel) { eng.selectSubtitle(if (it == 0) 0 else tracks[it - 1].id) })
            if (tracks.isEmpty()) body.addView(hint("No subtitles in this video. A .srt / .vtt / .ass file with the same name in the same folder loads automatically."))
            body.addView(sectionLabel("Sync and look"))
            body.addView(stepperRow("Subtitle delay", eng.subDelayMs(), 100L) { eng.setSubDelayMs(it) })
            body.addView(sliderRow("Size", 50, 250, Math.round(eng.subScale() * 100), { "$it%" }) { eng.setSubScale(it / 100f) })
            body.addView(sliderRow("Position", 0, 100, eng.subPos(), { if (it >= 100) "Bottom" else if (it <= 0) "Top" else "$it%" }) { eng.setSubPos(it) })
            showStyledDialog("Subtitles", body, "Done", null) { showControls() }
        }
        fun showEnhanceSheet() {
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            fun applyEnhance() { eng.setEnhance(enBri, enCon, enSat, enGam, enHue); onEnhanceChanged() }
            val sliders = listOf(
                Triple("Brightness", { enBri }, { v: Int -> enBri = v }),
                Triple("Contrast", { enCon }, { v: Int -> enCon = v }),
                Triple("Saturation", { enSat }, { v: Int -> enSat = v }),
                Triple("Gamma", { enGam }, { v: Int -> enGam = v }),
                Triple("Hue", { enHue }, { v: Int -> enHue = v })
            )
            for ((label, get, set) in sliders) {
                body.addView(sliderRow(label, -100, 100, get(), { if (it > 0) "+$it" else "$it" }) { set(it); applyEnhance() })
            }
            showStyledDialog("Enhance", body, "Done", "Reset", onNegative = {
                enBri = 0; enCon = 0; enSat = 0; enGam = 0; enHue = 0
                applyEnhance()
                showControls()
            }) { showControls() }
        }
        val decoderNames = listOf(
            "Hardware (HW) — fastest, saves battery",
            "Hardware+ (HW+) — hardware with copy-back, better compatibility",
            "Software (SW) — most compatible, uses more battery"
        )

        // ---- top-right controls: decoder pill, subtitles, audio, more ----
        val decoderPill = pressable(TextView(this).apply {
            text = eng.decoder.label
            setTextColor(Color.parseColor("#93C5FD"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                cornerRadius = dpf(100)
                setColor(Color.parseColor("#403B82F6"))
                setStroke(dp(1), Color.parseColor("#993B82F6"))
            }
            setOnClickListener {
                var pick = eng.decoder.ordinal
                showStyledDialog("Decoder", radioRows(decoderNames, pick) { pick = it }, "OK", "Cancel") {
                    val all = MpvEngine.Decoder.values()
                    eng.setDecoder(all[pick])
                    text = all[pick].label
                    showControls()
                }
            }
        })

        fun chooseSpeed() {
            var pick = speedIdx
            val body = radioRows(speedLabels, speedIdx) { pick = it }
            showStyledDialog("Playback speed", body, "OK", "Cancel") {
                speedIdx = pick
                applySpeed()
                showControls()
            }
        }
        var loopOn = false
        fun showInfoSheet() {
            val rows = eng.info()
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            if (rows.isEmpty()) body.addView(hint("Nothing to show yet"))
            for ((k, v) in rows) {
                body.addView(TextView(this).apply {
                    text = k; setTextColor(pal.icon); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(dp(24), dp(8), dp(24), 0)
                })
                body.addView(TextView(this).apply {
                    text = v; setTextColor(pal.text); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setPadding(dp(24), 0, dp(24), dp(4))
                })
            }
            showStyledDialog("Media information", body, "Close", null) { showControls() }
        }
        fun showChapters() {
            val list = eng.chapters()
            if (list.isEmpty()) {
                Toast.makeText(this, "No chapters in this video", Toast.LENGTH_SHORT).show()
            } else {
                var dlg: Dialog? = null
                val labels = list.map { fmt(it.timeMs) + "   " + it.title }
                val body = radioRows(labels, eng.currentChapter()) {
                    eng.seekTo(list[it].timeMs, exact = true)
                    dlg?.dismiss()
                    showControls()
                }
                dlg = showStyledDialog("Chapters", body, "Close", null) {}
            }
        }
        fun showMoreMenu(anchor: View) {
            val rows = ArrayList<MenuRow>()
            rows.add(MenuRow(R.drawable.ic_settings, "Playback speed", pal.text) { chooseSpeed() })
            rows.add(MenuRow(R.drawable.ic_palette, "Enhance picture", pal.text) { showEnhanceSheet() })
            // Landscape has no Chapters button (as in Nosved's default layout), so the list lives here too.
            if (eng.chapters().isNotEmpty()) rows.add(MenuRow(R.drawable.ic_pv_chapters, "Chapters", pal.text) { showChapters() })
            rows.add(MenuRow(R.drawable.ic_refresh, if (loopOn) "Loop: On" else "Loop: Off", pal.text) {
                loopOn = !loopOn
                eng.setLoop(loopOn)
                showControls()
            })
            rows.add(MenuRow(R.drawable.ic_pv_music, if (eng.isMuted()) "Unmute" else "Mute", pal.text) {
                eng.setMute(!eng.isMuted())
                showControls()
            })
            rows.add(MenuRow(R.drawable.ic_unfold_less, "Step frame back", pal.text) {
                eng.pause(); eng.stepFrame(false); showControls()
            })
            rows.add(MenuRow(R.drawable.ic_unfold_more, "Step frame forward", pal.text) {
                eng.pause(); eng.stepFrame(true); showControls()
            })
            rows.add(MenuRow(R.drawable.ic_info, "Media information", pal.text) { showInfoSheet() })
            rows.add(MenuRow(R.drawable.ic_help, "Player help", pal.text) { showVideoHelp() })
            rows.add(MenuRow(R.drawable.ic_open_in_new, "Open with another app", pal.text) {
                openExternally(vaultFile, VaultManager.mimeFor(VaultManager.displayName(vaultFile)), chooser = true, keepTemp = true)
            })
            showViewerMenu(anchor, rows)
        }

        val subBtn = capsuleButton(36, R.drawable.ic_pv_subtitles, 18) { showSubtitleSheet() }
        val audioBtn = capsuleButton(36, R.drawable.ic_pv_music, 18) { showAudioSheet() }
        val moreBtn = capsuleButton(36, R.drawable.ic_pv_more, 18) { showMoreMenu(it) }

        // ---- bottom glass capsules: lock / aspect on the left, chapters / rotate on the right ----
        fun aspectRes(a: MpvEngine.Aspect): Int = when (a) {
            MpvEngine.Aspect.FIT -> R.drawable.ic_pv_aspect_fit
            MpvEngine.Aspect.STRETCH -> R.drawable.ic_pv_aspect_stretch
            MpvEngine.Aspect.CROP -> R.drawable.ic_pv_aspect_crop
            MpvEngine.Aspect.ORIGINAL -> R.drawable.ic_pv_aspect_original
        }
        // The icon changes with the mode (Fit / Stretch / Crop / 100 %), as in Nosved Player.
        val aspectBtn = capsuleButton(36, aspectRes(eng.aspect), 18) { v ->
            val all = MpvEngine.Aspect.values()
            val next = all[(eng.aspect.ordinal + 1) % all.size]
            eng.setAspect(next)
            ((v as ViewGroup).getChildAt(0) as ImageView).setImageResource(aspectRes(next))
            showPill(next.label, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            showControls()
        }
        val enhanceBtn = capsuleButton(36, R.drawable.ic_pv_enhance, 18) { showEnhanceSheet() }
        // Blue while any picture adjustment is active, like Nosved's Enhance button.
        onEnhanceChanged = {
            val on = enBri != 0 || enCon != 0 || enSat != 0 || enGam != 0 || enHue != 0
            (enhanceBtn.getChildAt(0) as ImageView).setColorFilter(if (on) accentBlue else whiteE6)
        }
        val lockBtn = capsuleButton(36, R.drawable.ic_pv_lock, 18) {
            locked = true
            ui.removeCallbacks(hide)
            setChrome(false)
            animateShown(unlockBtn, true, 0f)
            ui.postDelayed({ animateShown(unlockBtn, false, 0f) }, 2500)
        }
        val chaptersBtn = capsuleButton(36, R.drawable.ic_pv_chapters, 18) { showChapters() }
        val rotateBtn = capsuleButton(36, R.drawable.ic_pv_rotate, 18) {
            val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            requestedOrientation = if (land) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            showControls()
        }
        unlockBtn.setOnClickListener {
            locked = false
            animateShown(unlockBtn, false, 0f)
            showControls()
        }

        // ---- top bar: back, title + metadata, decoder pill, subtitles, audio, more ----
        val titleText = TextView(this).apply {
            text = VaultManager.displayName(vaultFile)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)   // titleMedium
            // SemiBold (600) where supported, Bold on older phones.
            setTypeface(if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 600, false) else Typeface.DEFAULT_BOLD)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
        val metaText = TextView(this).apply {
            setTextColor(Color.parseColor("#ADFFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE
        }
        val hdrBadge = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            letterSpacing = 0.05f
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(2), dp(6), dp(2))
            visibility = View.GONE
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(titleText, LinearLayout.LayoutParams(-2, -2))
            addView(hdrBadge, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        }
        var videoW = 0
        var videoH = 0
        fun updateMeta() {
            if (videoW <= 0 || videoH <= 0) return
            val parts = ArrayList<String>()
            parts.add("${videoH}p")
            val codec = eng.videoCodecLabel()
            if (!codec.isNullOrBlank()) parts.add(codec)
            val hdr = eng.hdrLabel()
            if (hdr != null) parts.add(hdr)
            val bytes = media.length()
            if (bytes > 0L) {
                val units = arrayOf("B", "KB", "MB", "GB", "TB")
                val g = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
                parts.add(String.format(java.util.Locale.US, "%.2f %s", bytes / Math.pow(1024.0, g.toDouble()), units[g]))
            }
            metaText.text = parts.joinToString(" • ")
            metaText.visibility = View.VISIBLE
            hdrBadge.visibility = if (hdr != null) View.VISIBLE else View.GONE
            if (hdr != null) {
                val hlg = hdr == "HLG"
                hdrBadge.setTextColor(Color.parseColor(if (hlg) "#E0F2FE" else "#FEF3C7"))
                hdrBadge.text = hdr
                hdrBadge.background = GradientDrawable().apply {
                    cornerRadius = dpf(4)
                    setColor(Color.parseColor(if (hlg) "#D90C4A6E" else "#D978350F"))
                    setStroke(dp(1), Color.parseColor(if (hlg) "#CC38BDF8" else "#CCF59E0B"))
                }
                (titleText.layoutParams as LinearLayout.LayoutParams).apply { width = 0; weight = 1f }
                titleText.requestLayout()
            }
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleRow, LinearLayout.LayoutParams(-1, -2))
            addView(metaText)
            setOnClickListener { showInfoSheet() }
        }
        val backBtn = glassButton(38, R.drawable.ic_pv_back, 20) { viewerDialog?.dismiss() }
        val topCapsule = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundBg(glassBg, glassBorder)
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        topRow.addView(backBtn, LinearLayout.LayoutParams(dp(38), dp(38)))
        topRow.addView(titleCol, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10); marginEnd = dp(8) })
        topRow.addView(topCapsule, LinearLayout.LayoutParams(-2, -2))

        // ---- transport dock: previous, -10 s, play, +10 s, next ----
        fun chapterStep(dir: Int) {
            val list = eng.chapters()
            if (list.isEmpty()) { skip(dir * 10_000L); return }
            val i = (eng.currentChapter() + dir).coerceIn(0, list.size - 1)
            eng.seekTo(list[i].timeMs, exact = true)
            showPill(list[i].title, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            showControls()
        }
        fun transportButton(res: Int, sizeDp: Int, iconDp: Int, tint: Int, onClick: () -> Unit): FrameLayout =
            pressable(FrameLayout(this).apply {
                background = RippleDrawable(
                    ColorStateList.valueOf(Color.parseColor("#33FFFFFF")),
                    null,
                    GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
                addView(iconView(res, iconDp, tint))
            }).apply { layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)) }
        fun seekCircle(res: Int, onClick: () -> Unit): FrameLayout =
            pressable(FrameLayout(this).apply {
                background = RippleDrawable(
                    ColorStateList.valueOf(Color.parseColor("#33FFFFFF")),
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#14FFFFFF"))
                    },
                    GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
                addView(iconView(res, 21, Color.WHITE))
            }).apply { layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)) }

        val prevBtn = transportButton(R.drawable.ic_pv_prev, 38, 22, whiteE6) { chapterStep(-1) }
        val nextBtn = transportButton(R.drawable.ic_pv_next, 38, 22, whiteE6) { chapterStep(1) }
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundBg(dockBg, dockBorder)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        dock.addView(prevBtn)
        dock.addView(seekCircle(R.drawable.ic_pv_replay10) { skip(-10_000L); showControls() })
        dock.addView(playBtn)
        dock.addView(seekCircle(R.drawable.ic_pv_forward10) { skip(10_000L); showControls() })
        dock.addView(nextBtn)
        for (i in 1 until dock.childCount) {
            (dock.getChildAt(i).layoutParams as LinearLayout.LayoutParams).marginStart = dp(8)
        }
        playBtn.setOnClickListener { togglePlay() }
        fun updateChapterButtons() {
            val list = eng.chapters()
            val has = list.size > 1
            prevBtn.alpha = if (has) 1f else 0.38f
            nextBtn.alpha = if (has) 1f else 0.38f
            // Nosved hides the Chapters button when the file has none.
            chaptersBtn.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
        }
        updateChapterButtons()

        // ---- Nosved's two layouts ----
        // Landscape: transport dock, then the timeline row (elapsed, bar, total), then lock on the left and
        //            aspect / rotate on the right.
        // Portrait:  dock in the middle of the screen; at the bottom a time pill and a speed pill, the bar, and one
        //            capsule with decoder, chapters, enhance, aspect and rotate.
        fun newCapsule(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundBg(glassBg, glassBorder)
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        /** Puts [items] in a glass capsule: 2 dp apart, the decoder pill with its own 4 dp margin, as in Nosved. */
        fun fillCapsule(box: LinearLayout, items: List<View>) {
            items.forEachIndexed { i, v ->
                val lp = if (v === decoderPill) {
                    LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), dp(2), dp(4), dp(2)) }
                } else {
                    LinearLayout.LayoutParams(dp(36), dp(36))
                }
                if (i > 0) lp.marginStart += dp(2)
                box.addView(v, lp)
            }
        }
        val leftCapsule = newCapsule()
        val rightCapsule = newCapsule()
        val bottomCapsule = newCapsule()
        val timelineRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val pillRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6))
        }
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun detach(v: View) { (v.parent as? ViewGroup)?.removeView(v) }

        var metricsKey = ""
        fun applyChromeMetrics() {
            val cfg = resources.configuration
            val land = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
            val compact = land && cfg.screenHeightDp < 400
            val key = "$land:${cfg.screenWidthDp}:${cfg.screenHeightDp}"
            if (key == metricsKey) return
            metricsKey = key
            val hPad = if (land) (if (cfg.screenWidthDp >= 840) dp(48) else dp(24)) else dp(16)
            topRow.setPadding(hPad, if (land) (if (compact) dp(8) else dp(12)) else dp(8), hPad, dp(8))
            (titleCol.layoutParams as LinearLayout.LayoutParams).apply { marginEnd = dp(if (land) 8 else 4) }
            (topCapsule.layoutParams as LinearLayout.LayoutParams).apply { marginStart = dp(if (land) 18 else 10) }
            scrimBottom.layoutParams = FrameLayout.LayoutParams(-1, dp(if (land) 240 else 190), Gravity.BOTTOM)

            // Everything that moves between the two layouts is lifted out first.
            for (v in listOf<View>(dock, seek, cur, total, timePill, speedPill, timelineRow, pillRow, bottomRow,
                leftCapsule, rightCapsule, bottomCapsule, decoderPill, subBtn, audioBtn, moreBtn, chaptersBtn,
                enhanceBtn, aspectBtn, rotateBtn, lockBtn)) detach(v)
            controls.removeAllViews()
            topCapsule.removeAllViews()
            leftCapsule.removeAllViews()
            rightCapsule.removeAllViews()
            bottomCapsule.removeAllViews()
            timelineRow.removeAllViews()
            pillRow.removeAllViews()
            bottomRow.removeAllViews()

            if (land) {
                fillCapsule(topCapsule, listOf(decoderPill, subBtn, audioBtn, moreBtn))
                fillCapsule(leftCapsule, listOf(lockBtn))
                fillCapsule(rightCapsule, listOf(aspectBtn, rotateBtn))
                controls.gravity = Gravity.CENTER_HORIZONTAL
                controls.setPadding(hPad, 0, hPad, if (compact) dp(12) else dp(16))
                bottomRow.gravity = Gravity.CENTER_VERTICAL
                // dock (added below, first), 11 dp gap, timeline, 8 dp gap, capsules
                timelineRow.addView(cur)
                timelineRow.addView(seek, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(12); marginEnd = dp(12) })
                timelineRow.addView(total)
                controls.addView(timelineRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(if (compact) 9 else 11) })
                bottomRow.addView(leftCapsule, LinearLayout.LayoutParams(-2, -2))
                bottomRow.addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
                bottomRow.addView(rightCapsule, LinearLayout.LayoutParams(-2, -2))
                controls.addView(bottomRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(if (compact) 6 else 8) })
            } else {
                fillCapsule(topCapsule, listOf(subBtn, audioBtn, moreBtn))
                fillCapsule(bottomCapsule, listOf(decoderPill, chaptersBtn, enhanceBtn, aspectBtn, rotateBtn))
                controls.gravity = Gravity.START
                controls.setPadding(hPad, 0, hPad, dp(20))
                pillRow.addView(timePill, LinearLayout.LayoutParams(-2, -2))
                pillRow.addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
                pillRow.addView(speedPill, LinearLayout.LayoutParams(-2, -2))
                controls.addView(pillRow, LinearLayout.LayoutParams(-1, -2))
                controls.addView(seek, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(2) })
                bottomRow.gravity = Gravity.CENTER
                bottomRow.addView(bottomCapsule, LinearLayout.LayoutParams(-2, -2))
                controls.addView(bottomRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            }
            // The dock is its own child of the overlay in portrait (centred on the screen).
            detach(dock)
            if (land) controls.addView(dock, 0, LinearLayout.LayoutParams(-2, -2))
            else overlay.addView(dock, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        overlay.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyChromeMetrics() }
        applyChromeMetrics()

        val tick = object : Runnable {
            override fun run() {
                if (released) return
                val d = durationMs()
                if (d > 0L) {
                    if (seek.durationMs != d) {
                        seek.durationMs = d
                        setTimes(eng.positionMs)
                    }
                    if (!seek.scrubbing) {
                        seek.positionMs = eng.positionMs
                        setTimes(eng.positionMs)
                    }
                }
                updatePlayIcon()
                ui.postDelayed(this, if (chromeShown) 100L else 400L)
            }
        }

        eng.listener = object : MpvEngine.Listener {
            override fun onDuration(ms: Long) {
                if (ms > 0L) { seek.durationMs = ms; setTimes(eng.positionMs) }
            }
            override fun onFileLoaded() {
                fileReady = true
                applySpeed()
                // Audio-only files report no picture; give mpv a moment to say so, then show the note and keep the controls up.
                ui.postDelayed({
                    if (released) return@postDelayed
                    hasVideo = eng.hasVideo
                    noteView.visibility = if (hasVideo) View.GONE else View.VISIBLE
                    if (!hasVideo) { posterView.visibility = View.GONE; showControls() }
                }, 900)
                updateChapterButtons()
                updateMeta()
                updatePlayIcon()
            }
            override fun onFirstFrame() {
                if (posterView.visibility == View.VISIBLE) {
                    posterView.animate().alpha(0f).setDuration(180).withEndAction { posterView.visibility = View.GONE }.start()
                }
            }
            override fun onTracksChanged() {
                updateChapterButtons()
            }
            override fun onVideoSize(width: Int, height: Int) {
                if (fileReady && width > 0 && height > 0) {
                    hasVideo = true
                    videoW = width
                    videoH = height
                    noteView.visibility = View.GONE
                    updateMeta()
                }
            }
            override fun onPlayingChanged(playing: Boolean) {
                surface.keepScreenOn = playing
                if (!playing) saveResume()
                updatePlayIcon()
                if (playing) showControls()
            }
            override fun onEnded() {
                ui.removeCallbacks(hide)
                if (!locked) setChrome(true)
                updatePlayIcon()
            }
            override fun onError(message: String) {
                posterView.visibility = View.GONE
                if (errorShown || released || isDestroyed || isFinishing) return
                errorShown = true
                showStyledDialog(
                    "Can't play this file",
                    msgBody("The built-in player can't play this file ($message). Do you want to open it in another player app?"),
                    "Open with", "Close"
                ) {
                    openExternally(vaultFile, VaultManager.mimeFor(VaultManager.displayName(vaultFile)), chooser = true, keepTemp = true)
                }
            }
        }

        val subs = sidecarSubtitles(vaultFile, tempFiles)
        val startAt = if (resumeOn) AppSettings.resumePosition(this, resumeKey) else 0L
        eng.start(media, startAt, subs)
        val resumeSaver = object : Runnable {
            override fun run() {
                if (released) return
                if (eng.isPlaying) saveResume()
                ui.postDelayed(this, 4000)
            }
        }
        ui.postDelayed(resumeSaver, 4000)

        viewerBar?.invoke(false)
        val holder = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(surface, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
            addView(noteView, FrameLayout.LayoutParams(-1, -1))
            addView(posterView, FrameLayout.LayoutParams(-1, -1))
            addView(overlay, FrameLayout.LayoutParams(-1, -1))
            addView(pill, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            addView(unlockBtn, FrameLayout.LayoutParams(-2, dp(40), Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(16) })
        }

        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var startLevel = 0f

        fun curBrightness(): Float {
            val b = viewerDialog?.window?.attributes?.screenBrightness ?: -1f
            if (b >= 0f) return b
            return try {
                Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
            } catch (_: Exception) { 0.5f }
        }
        fun setBrightness(v: Float) {
            val w = viewerDialog?.window ?: return
            val lp = w.attributes
            lp.screenBrightness = v.coerceIn(0.01f, 1f)
            w.attributes = lp
        }

        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (locked) {
                    animateShown(unlockBtn, true, 0f)
                    ui.removeCallbacks(hide)
                    ui.postDelayed({ if (locked) animateShown(unlockBtn, false, 0f) }, 2500)
                    return true
                }
                // One tap opens the controls (and title bar) if they are hidden and hides them if they are
                // showing, playing or paused. Audio-only files keep them up.
                if (chromeShown && hasVideo) {
                    ui.removeCallbacks(hide)
                    setChrome(false)
                } else {
                    showControls()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (locked) return true
                val w = holder.width.toFloat()
                when {
                    e.x < w / 3f -> skip(-10_000L)
                    e.x > w * 2f / 3f -> skip(10_000L)
                    else -> {
                        togglePlay()
                        flashPlayState()
                    }
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (locked || !eng.isPlaying || gesture != 0) return
                holdBoost = true
                holder.parent?.requestDisallowInterceptTouchEvent(true)
                applySpeed()
                holder.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showPill("2x", Gravity.TOP or Gravity.CENTER_HORIZONTAL, true)
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (locked || holdBoost || e1 == null) return false
                val w = holder.width.toFloat()
                val h = holder.height.toFloat()
                if (gesture == 0) {
                    val dx = Math.abs(e2.x - e1.x)
                    val dy = Math.abs(e2.y - e1.y)
                    if (dy > slop && dy > dx) {
                        if (e1.x < w / 2f) {
                            gesture = 1
                            startLevel = curBrightness()
                        } else {
                            gesture = 2
                            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                            startLevel = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max + boost
                        }
                        ui.removeCallbacks(hide)
                        holder.parent?.requestDisallowInterceptTouchEvent(true)
                    } else if (dx > slop) {
                        gesture = 3
                        return false
                    } else {
                        return false
                    }
                }
                if (gesture == 1 || gesture == 2) {
                    val delta = (e1.y - e2.y) / (h * 0.7f)
                    if (gesture == 1) {
                        val level = (startLevel + delta).coerceIn(0f, 1f)
                        setBrightness(level)
                        showPanel(briPanel, level, 1f)
                    } else {
                        // 0-100 % moves the system volume; 100-200 % boosts above it (inside mpv).
                        val level = (startLevel + delta).coerceIn(0f, 2f)
                        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(level.coerceAtMost(1f) * max), 0)
                        boost = (level - 1f).coerceAtLeast(0f)
                        eng.setBoost(boost)
                        showPanel(volPanel, level, 2f)
                        volPanel.fill.background = GradientDrawable().apply {
                            cornerRadius = dpf(6)
                            setColor(if (level > 1f) Color.parseColor("#FFB4AB") else accentBlue)
                        }
                    }
                    return true
                }
                return false
            }
        })
        // Multi-finger taps (as in Nosved Player): two fingers = play / pause, three fingers = 2x on / off.
        var maxPointers = 0
        var downAt = 0L
        var downX = 0f
        var downY = 0f
        holder.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    maxPointers = 1; downAt = ev.eventTime; downX = ev.x; downY = ev.y
                }
                MotionEvent.ACTION_POINTER_DOWN -> maxPointers = Math.max(maxPointers, ev.pointerCount)
            }
            detector.onTouchEvent(ev)
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                if (ev.actionMasked == MotionEvent.ACTION_UP && !locked && maxPointers >= 2 &&
                    ev.eventTime - downAt < 300L && Math.abs(ev.x - downX) < slop * 3 && Math.abs(ev.y - downY) < slop * 3
                ) {
                    if (maxPointers == 2) {
                        togglePlay()
                        flashPlayState()
                    } else {
                        fastToggle = !fastToggle
                        applySpeed()
                        showPill(if (fastToggle) "2x" else speedLabels[speedIdx].substringBefore(' '), Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                    }
                }
                maxPointers = 0
                if (holdBoost) {
                    holdBoost = false
                    applySpeed()
                    ui.removeCallbacks(hidePill)
                    animateShown(pill, false, 0f)
                }
                if (gesture != 0) {
                    gesture = 0
                    ui.removeCallbacks(hidePill)
                    ui.postDelayed(hidePill, 600)
                    ui.removeCallbacks(hidePanels)
                    ui.postDelayed(hidePanels, 600)
                    // A volume / brightness swipe cancelled the pending hide; start the 3 seconds again.
                    if (chromeShown && !locked) scheduleHide()
                }
            }
            true
        }
        ui.post(tick)
        return PlayerHandle(holder) {
            saveResume()
            released = true
            playerContentShown = false
            viewerBar?.invoke(true)
            ui.removeCallbacksAndMessages(null)
            try { eng.release() } catch (_: Throwable) {}
            for (f in tempFiles) f.delete()
        }
    }

    /**
     * Hands a vault file to another app. Plain files are hard-linked (instant, any size); encrypted ones are
     * decrypted first. If that takes more than a moment, a wavy "Opening…" card with live progress appears
     * (back button cancels).
     */
    private fun openExternally(file: File, mime: String, chooser: Boolean = false, keepTemp: Boolean = false) {
        val cancelled = AtomicBoolean(false)
        val label = shortName(VaultManager.displayName(file))
        val dlg = PixelProgressDialog(this, progressColors(), wavy = true)
        dlg.cancelable = true
        dlg.onCancel = { cancelled.set(true) }
        dlg.update(title = "Opening…", sub = label, progress = -1f)
        dlg.show(350L)
        Thread {
            var last = 0L
            val tmp = VaultManager.prepareForOpen(applicationContext, file, unique = keepTemp, cancelled = { cancelled.get() }) { f ->
                val now = System.currentTimeMillis()
                if (now - last >= 100L) {
                    last = now
                    runOnUiThread { dlg.update(sub = "$label · ${(f * 100).toInt()}%", progress = f) }
                }
            }
            runOnUiThread {
                dlg.dismiss()
                if (cancelled.get()) {
                    tmp?.delete()
                    return@runOnUiThread
                }
                if (isDestroyed || isFinishing) return@runOnUiThread
                if (tmp == null) {
                    Toast.makeText(this, "Couldn't open this file", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", tmp)
                val viewMime = if (mime == "application/octet-stream") "*/*" else mime
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, viewMime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                leavingForTask = true
                externalViewing = true
                try {
                    startActivity(if (chooser) Intent.createChooser(viewIntent, "Open with") else viewIntent)
                } catch (e: Exception) {
                    leavingForTask = false
                    externalViewing = false
                    if (!keepTemp) VaultManager.clearTemp(applicationContext)
                    Toast.makeText(this, "No app can open this file. Use Unhide to restore it.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /** [keepTemp] is set when sharing from a viewer, so the temp copy a video is playing from isn't deleted. */
    private fun shareFiles(files: List<File>, keepTemp: Boolean = false) {
        if (files.isEmpty()) return
        val dlg = PixelProgressDialog(this, progressColors(), wavy = true)
        dlg.update(title = "Preparing…", sub = if (files.size == 1) shortName(VaultManager.displayName(files[0])) else "${files.size} files", progress = -1f)
        dlg.show(350L)
        Thread {
            if (!keepTemp) VaultManager.clearTemp(applicationContext)
            val temps = ArrayList<File>()
            for (f in files) VaultManager.decryptForShare(applicationContext, f)?.let { temps.add(it) }
            runOnUiThread {
                dlg.dismiss()
                if (isDestroyed || isFinishing) return@runOnUiThread
                if (temps.isEmpty()) {
                    Toast.makeText(this, "Couldn't prepare files for sharing", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                if (temps.size < files.size) {
                    Toast.makeText(this, "Some files couldn't be shared", Toast.LENGTH_SHORT).show()
                }
                val uris = ArrayList(temps.map { FileProvider.getUriForFile(this, "$packageName.fileprovider", it) })
                val mimes = temps.map { VaultManager.mimeFor(it.name) }.distinct()
                val type = when {
                    mimes.size == 1 -> mimes[0]
                    mimes.map { it.substringBefore('/') }.distinct().size == 1 -> mimes[0].substringBefore('/') + "/*"
                    else -> "*/*"
                }
                val send = if (uris.size == 1) {
                    Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
                } else {
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
                }
                send.type = type
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val clip = ClipData.newUri(contentResolver, "files", uris[0])
                for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
                send.clipData = clip
                leavingForTask = true
                try {
                    startActivity(Intent.createChooser(send, "Share"))
                } catch (e: Exception) {
                    leavingForTask = false
                    if (!keepTemp) VaultManager.clearTemp(applicationContext)
                    Toast.makeText(this, "No app available to share with", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun unhideFile(file: File) = withStoragePermission { startUnhide(listOf(file)) }

    /**
     * Restores files in the background. Files adding up to 100 MB or more get the same progress card as adding
     * (with Cancel / Hide); smaller ones just show a short toast and the result.
     */
    private fun startUnhide(files: List<File>) {
        val job = TransferJobs.unhide(files)
        selected.clear()
        if (job.cardDelayMs < 0L) Toast.makeText(this, "Unhiding…", Toast.LENGTH_SHORT).show()
        else askNotificationPermissionOnce()
        renderVaultFiles()
        TransferManager.enqueue(this, job)
    }

    private fun confirmDelete(file: File) {
        showStyledDialog(
            "Delete permanently?",
            msgBody("\"${VaultManager.displayName(file)}\" will be deleted from the vault and cannot be recovered."),
            "Delete", "Cancel"
        ) {
            VaultManager.deleteFile(this, file)
            renderVaultFiles()
            Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun categoryOf(name: String): Int {
        val mime = VaultManager.mimeFor(name)
        return when {
            mime.startsWith("image/") -> 1
            mime.startsWith("video/") -> 2
            else -> 3
        }
    }

    private fun filterChip(label: String, selected: Boolean, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(if (selected) pal.eqFg else pal.sciFg)
        typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setPadding(dp(14), dp(7), dp(14), dp(7))
        background = GradientDrawable().apply {
            cornerRadius = dpf(100)
            setColor(if (selected) pal.eqBg else pal.numBg)
        }
        setOnClickListener { onClick() }
    }

    private fun chipParams() = LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, 0, dp(8), 0) }

    private fun renderVaultChips() {
        vaultChipsRow.removeAllViews()
        val counts = IntArray(4)
        val here = vaultBaseFiles()
        counts[0] = here.size
        for (f in here) counts[categoryOf(VaultManager.displayName(f))]++
        listOf("All", "Images", "Videos", "Files").forEachIndexed { i, label ->
            vaultChipsRow.addView(filterChip(if (counts[i] > 0) "$label · ${counts[i]}" else label, vaultFilter == i) {
                vaultPager.setCurrentItem(i, true)
            }, chipParams())
        }
        val sortLabels = listOf("A–Z", "Newest", "Largest")
        vaultChipsRow.addView(filterChip("Sort: ${sortLabels[vaultSort]}", false) {
            vaultSort = (vaultSort + 1) % 3
            renderVaultFiles()
        }, chipParams())
        if (currentFolder == null) {
            vaultChipsRow.addView(filterChip("+ Folder", false) { promptNewFolder() }, chipParams())
        }
    }

    private fun promptText(title: String, initial: String, positive: String, hint: String = "Folder name", onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            this.hint = hint
            setSingleLine(true)
            setText(initial)
            setSelection(text.length)
            setTextColor(pal.text)
            setHintTextColor(pal.icon)
            backgroundTintList = ColorStateList.valueOf(pal.cursor)
        }
        val box = FrameLayout(this).apply {
            setPadding(dp(24), 0, dp(24), 0)
            addView(input)
        }
        val d = showStyledDialog(title, box, positive, "Cancel") { onOk(input.text.toString()) }
        d.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        input.requestFocus()
    }

    private fun promptNewFolder(onCreated: ((String) -> Unit)? = null) {
        promptText("New folder", "", "Create") { raw ->
            val name = VaultManager.cleanFolderName(raw)
            if (name == null) {
                Toast.makeText(this, "Enter a folder name", Toast.LENGTH_SHORT).show()
            } else if (VaultManager.folderExists(this, name)) {
                if (onCreated != null) onCreated(name)
                else Toast.makeText(this, "A folder with that name already exists", Toast.LENGTH_SHORT).show()
            } else if (VaultManager.createFolder(this, name)) {
                if (onCreated != null) {
                    onCreated(name)
                } else {
                    renderVaultFiles()
                    Toast.makeText(this, "Folder created", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Couldn't create folder", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFolderMenu(anchor: View, folder: String) {
        if (folder == VaultManager.APPS_FOLDER) {
            Toast.makeText(this, "The Apps folder holds your hidden apps and can't be renamed or deleted", Toast.LENGTH_SHORT).show()
            return
        }
        showListDialog(folder, listOf(
            Triple(R.drawable.ic_edit, "Rename", {
                promptText("Rename folder", folder, "Rename") { raw ->
                    val newName = VaultManager.cleanFolderName(raw)
                    if (newName == null) {
                        Toast.makeText(this, "Enter a folder name", Toast.LENGTH_SHORT).show()
                    } else if (newName != folder) {
                        if (VaultManager.renameFolder(this, folder, newName)) renderVaultFiles()
                        else Toast.makeText(this, "Couldn't rename (that name may already exist)", Toast.LENGTH_SHORT).show()
                    }
                }
            }),
            Triple(R.drawable.ic_delete, "Delete folder", { confirmDeleteFolder(folder) })
        ))
    }

    private fun confirmDeleteFolder(folder: String) {
        showStyledDialog(
            "Delete folder?",
            msgBody("\"$folder\" will be removed. Any files inside move back to the main vault, nothing is deleted."),
            "Delete folder", "Cancel"
        ) {
            VaultManager.deleteFolder(this, folder)
            renderVaultFiles()
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Hidden apps (see HiddenApps.kt for how this works and what Android does not allow)
    // ------------------------------------------------------------------------------------------------

    /** Explains why hidden apps can't run right now (no engine, switched off after a crash, or it failed to start). */
    private fun showEngineUnavailable() {
        if (EngineBridge.compiledIn && EngineBridge.disabledByCrash(this)) {
            showStyledDialog(
                "Hidden apps were switched off",
                msgBody(
                    VirtualSpace.unavailableReason +
                        "\n\nYou can try starting the engine again. If it crashes the app again it is switched off again."
                ).apply { setTextIsSelectable(true) },
                "Try again", "Close"
            ) {
                EngineBridge.resetGuard(this)
                startEngineHide()
            }
            return
        }
        showStyledDialog(
            if (EngineBridge.compiledIn) "Hidden-app engine didn't start" else "Hidden-app engine missing",
            msgBody(VirtualSpace.unavailableReason).apply { setTextIsSelectable(true) },
            "OK", null
        ) {}
    }

    private fun startEngineHide() {
        if (!EngineBridge.compiledIn) {
            showEngineUnavailable()
            return
        }
        if (EngineBridge.available) {
            loadAppsToHide()
            return
        }
        // The engine is not started when the app opens (it can crash some phones); start it now, off the main thread.
        val starting = PixelProgressDialog(this, progressColors(), wavy = true)
        starting.update(title = "Starting the engine…", sub = "", progress = -1f)
        starting.show(0L)
        Thread {
            val ok = VirtualSpace.ensureStarted(applicationContext)
            runOnUiThread {
                starting.dismiss()
                if (isDestroyed || isFinishing) return@runOnUiThread
                if (ok) loadAppsToHide() else showEngineUnavailable()
            }
        }.start()
    }

    private fun loadAppsToHide(viaShizuku: Boolean = false) {
        val dlg = PixelProgressDialog(this, progressColors(), wavy = true)
        dlg.update(title = "Loading apps…", sub = "", progress = -1f)
        dlg.show(300L)
        Thread {
            val apps = try {
                if (viaShizuku) {
                    ShizukuHider.candidates(applicationContext)
                } else {
                    HiddenApps.listCandidates(applicationContext).filter {
                        !VirtualSpace.contains(applicationContext, it.pkg) && !ShizukuHider.contains(applicationContext, it.pkg)
                    }
                }
            } catch (_: Throwable) { emptyList() }
            runOnUiThread {
                dlg.dismiss()
                if (isDestroyed || isFinishing) return@runOnUiThread
                if (apps.isEmpty()) {
                    Toast.makeText(this, "No apps found that can be hidden", Toast.LENGTH_SHORT).show()
                } else {
                    showAppPicker(apps, viaShizuku)
                }
            }
        }.start()
    }

    // ----- Hide app: Shizuku / Sui (hides the real app) or the parallel engine (hidden copy) -----

    private fun engineStatusLine(): String = when {
        !EngineBridge.compiledIn -> "Not in this build"
        EngineBridge.disabledByCrash(this) -> "Switched off after a crash"
        EngineBridge.available -> "Running"
        else -> "Starts when you choose it"
    }

    /** What the + > Hide app button does: ask how, then go on with that way. */
    private fun startHideApp() {
        when (AppSettings.hideMethod(this)) {
            AppSettings.HIDE_SHIZUKU -> { startHideViaShizuku(); return }
            AppSettings.HIDE_ENGINE -> { startEngineHide(); return }
        }
        var dlg: Dialog? = null
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val remember = themedCheck("Remember my choice", false)
        fun option(icon: Int, title: String, desc: String, status: String, method: String, warn: String? = null, onClick: () -> Unit) {
            val (row, _) = cardRow()
            row.addView(FrameLayout(this).apply {
                background = GradientDrawable().apply { cornerRadius = dpf(18); setColor(pal.eqBg) }
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(icon)
                    setColorFilter(pal.eqFg)
                }, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(60), dp(60)).apply { rightMargin = dp(14) })
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(rowTitle(title, 2).apply {
                if (warn != null) {
                    val sb = SpannableStringBuilder(title)
                    val start = sb.length
                    sb.append("  ").append(warn)
                    sb.setSpan(android.text.style.ForegroundColorSpan(warnRed()), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    text = sb
                }
            })
            col.addView(rowMeta(desc).apply { maxLines = 6 })
            col.addView(TextView(this).apply {
                text = status
                setTextColor(pal.result)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(6), 0, 0)
            })
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            row.setOnClickListener {
                if (remember.isChecked) AppSettings.setHideMethod(this@MainActivity, method)
                dlg?.dismiss()
                onClick()
            }
            pressScale(row)
            body.addView(row)
        }
        option(
            R.drawable.ic_hide_shizuku, "Shizuku / Sui",
            "Hides the real app on this phone: it disappears from the home screen, app drawer and search until you unhide " +
                "it here. Needs Shizuku or Sui to be running.",
            "Shizuku: " + ShizukuHider.statusLine(),
            AppSettings.HIDE_SHIZUKU
        ) { startHideViaShizuku() }
        option(
            R.drawable.ic_hide_engine, "Parallel engine",
            "Copies the app into a hidden space inside this app and runs it there. The original stays where it is, and " +
                "you can uninstall it. Experimental.",
            "Engine: " + engineStatusLine(),
            AppSettings.HIDE_ENGINE,
            "(Doesn't support Google Apps)"
        ) { startEngineHide() }
        body.addView(remember, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(20), dp(8), dp(24), 0) })
        dlg = showStyledDialog("How do you want to hide it?", body, "Cancel", null) {}
    }

    private fun startHideViaShizuku() {
        when (val st = ShizukuHider.status()) {
            ShizukuHider.Status.READY -> loadAppsToHide(viaShizuku = true)
            ShizukuHider.Status.NEEDS_PERMISSION -> {
                leavingForTask = true   // Shizuku's permission screen is another app; don't lock the vault meanwhile
                ShizukuHider.requestPermission { granted ->
                    leavingForTask = false
                    if (isDestroyed || isFinishing) return@requestPermission
                    if (granted) loadAppsToHide(viaShizuku = true) else showShizukuHelp(ShizukuHider.Status.DENIED)
                }
            }
            else -> showShizukuHelp(st)
        }
    }

    private fun showShizukuHelp(status: ShizukuHider.Status) {
        val installed = ShizukuHider.managerInstalled(this)
        val text = when (status) {
            ShizukuHider.Status.DENIED ->
                "Shizuku refused permission earlier. Open Shizuku, find \"${getString(R.string.app_name)}\" in the list of " +
                    "authorized apps and allow it."
            ShizukuHider.Status.TOO_OLD -> "Your Shizuku is too old. Update it to version 11 or later."
            else -> if (installed) {
                "Shizuku is installed but not running. Open it and start it (on Android 11 or later with wireless " +
                    "debugging, or with root). If you use Sui, make sure its Magisk module is enabled and restart the phone."
            } else {
                "Neither Shizuku nor Sui is running. Install Shizuku (or Sui, for rooted phones), start it, then try again."
            }
        }
        showStyledDialog(
            "Shizuku isn't ready", msgBody(text),
            if (installed) "Open Shizuku" else "Get Shizuku", "Close"
        ) {
            leavingForTask = true
            try {
                val intent = if (installed) packageManager.getLaunchIntentForPackage(ShizukuHider.MANAGER_PACKAGE)
                else Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
                startActivity(intent)
            } catch (_: Exception) {
                leavingForTask = false
                Toast.makeText(this, "Couldn't open it", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmShizukuHide(app: HiddenApps.Candidate) {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(appIconTile(app.icon, 72, 20), LinearLayout.LayoutParams(dp(72), dp(72)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(12)
        })
        body.addView(msgBody(
            "\"${app.label}\" will be disabled and hidden on this phone: it disappears from the home screen, app drawer, " +
                "search and the system app list, and stops running. You open or unhide it from the Apps folder in this vault.\n\n" +
                "• Shizuku (or Sui) has to be running when you hide and when you unhide.\n" +
                "• Its notifications and background work stop while it is hidden.\n" +
                "• If you uninstall this app, hidden apps stay hidden until you unhide them with Shizuku, so unhide them first."
        ))
        showStyledDialog("Hide ${app.label}?", body, "Hide app", "Cancel") { hideViaShizuku(app) }
    }

    private fun hideViaShizuku(app: HiddenApps.Candidate) {
        val dlg = PixelProgressDialog(this, progressColors(), wavy = true)
        dlg.update(title = "Hiding ${app.label}…", sub = "", progress = -1f)
        dlg.show(0L)
        Thread {
            val error = ShizukuHider.add(applicationContext, app.pkg, app.label, app.icon)
            runOnUiThread {
                dlg.dismiss()
                if (isDestroyed || isFinishing) return@runOnUiThread
                renderVaultFiles()
                if (error == null) {
                    showStyledDialog(
                        "${app.label} is hidden",
                        msgBody("It is gone from the home screen, app drawer and search. Find it in the Apps folder of this vault: tap it to unhide and open it."),
                        "OK", null
                    ) {}
                } else {
                    showStyledDialog(
                        "Couldn't hide ${app.label}",
                        msgBody(error).apply { setTextIsSelectable(true) }, "OK", null
                    ) {}
                }
            }
        }.start()
    }

    private fun runShizukuUnhide(app: VirtualSpace.VApp, open: Boolean) {
        when (val st = ShizukuHider.status()) {
            ShizukuHider.Status.READY -> doShizukuUnhide(app, open)
            ShizukuHider.Status.NEEDS_PERMISSION -> {
                leavingForTask = true
                ShizukuHider.requestPermission { granted ->
                    leavingForTask = false
                    if (isDestroyed || isFinishing) return@requestPermission
                    if (granted) doShizukuUnhide(app, open) else showShizukuHelp(ShizukuHider.Status.DENIED)
                }
            }
            else -> showShizukuHelp(st)
        }
    }

    private fun doShizukuUnhide(app: VirtualSpace.VApp, open: Boolean) {
        val dlg = PixelProgressDialog(this, progressColors(), wavy = true)
        dlg.update(title = "Unhiding ${app.label}…", sub = "", progress = -1f)
        dlg.show(0L)
        Thread {
            val error = ShizukuHider.unhide(applicationContext, app.pkg)
            val intent = if (error == null && open) ShizukuHider.launchIntent(applicationContext, app.pkg) else null
            runOnUiThread {
                dlg.dismiss()
                if (isDestroyed || isFinishing) return@runOnUiThread
                renderVaultFiles()
                when {
                    error != null -> showStyledDialog(
                        "Couldn't unhide ${app.label}", msgBody(error).apply { setTextIsSelectable(true) }, "OK", null
                    ) {}
                    !open -> Toast.makeText(this, "${app.label} is back on your home screen", Toast.LENGTH_SHORT).show()
                    intent != null -> try {
                        startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(this, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show()
                    }
                    else -> Toast.makeText(this, "${app.label} is unhidden but couldn't be opened yet", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showShizukuAppMenu(app: VirtualSpace.VApp) {
        showListDialog(app.label, listOf(
            Triple(R.drawable.ic_open_in_new, "Unhide and open", { runShizukuUnhide(app, true) }),
            Triple(R.drawable.ic_unhide, "Unhide (back on the home screen)", { runShizukuUnhide(app, false) }),
            Triple(R.drawable.ic_delete, "Forget (keep it hidden)", { confirmForgetShizuku(app) })
        ))
    }

    private fun confirmForgetShizuku(app: VirtualSpace.VApp) {
        showStyledDialog(
            "Forget ${app.label}?",
            msgBody(
                "${app.label} stays hidden on the phone, but it is no longer listed here. To get it back you would have to " +
                    "unhide it with Shizuku yourself, for example with the command \"pm unhide ${app.pkg}\"."
            ),
            "Forget", "Cancel"
        ) {
            ShizukuHider.forget(applicationContext, app.pkg)
            renderVaultFiles()
        }
    }

    // ----- Google Play services inside the hidden space (see VirtualSpace for what it does and its limits) -----

    private fun gmsCard(): View {
        val on = VirtualSpace.gmsEnabled(this)
        val hostHas = VirtualSpace.hostHasGms(this)
        val (row, _) = cardRow()
        row.addView(FrameLayout(this).apply {
            background = GradientDrawable().apply { cornerRadius = dpf(14); setColor(if (on) pal.eqBg else pal.numBg) }
            addView(ImageView(this@MainActivity).apply {
                setImageResource(if (on) R.drawable.ic_check else R.drawable.ic_add)
                setColorFilter(if (on) pal.eqFg else pal.icon)
            }, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { rightMargin = dp(14) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(rowTitle("Google Play services"))
        col.addView(rowMeta(when {
            on && !hostHas -> "microG is installed in the hidden space"
            on -> "Copied into the hidden space"
            !hostHas -> "Off · not on this phone, installs microG (open-source) instead"
            else -> "Off · for Google sign-in and notifications (experimental)"
        }))
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        run {
            val toggle = { if (on) confirmRemoveGms() else confirmEnableGms() }
            row.addView(TextView(this).apply {
                text = if (on) "Remove" else "Set up"
                setTextColor(pal.sciFg)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = GradientDrawable().apply { cornerRadius = dpf(100); setColor(pal.numBg) }
                setOnClickListener { toggle() }
            }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(4) })
            row.setOnClickListener { toggle() }
        }
        pressScale(row)
        return row
    }

    private fun confirmEnableGms() {
        if (VirtualSpace.usesMicroG(this)) {
            showStyledDialog(
                "Set up microG?",
                msgBody(
                    "This phone has no Google Play services, so apps like YouTube say \"Device not compatible\". " +
                        "microG is a free, open-source replacement. This downloads it from microg.org (about 100 MB) " +
                        "and installs it inside the hidden space only; nothing on your phone is changed and no root is needed.\n\n" +
                        "• Needs an internet connection. Downloads are checked against microG's published checksums.\n" +
                        "• Experimental. Most Google sign-in, notification and Maps features work, but apps that need " +
                        "Google's own device certification (Play Integrity, some banking and streaming apps) will refuse.\n" +
                        "• You sign in to Google inside the space separately from the phone.\n" +
                        "• You can remove it again here at any time."
                ),
                "Download & set up", "Cancel"
            ) { runEnableGms() }
            return
        }
        showStyledDialog(
            "Set up Google Play services?",
            msgBody(
                "Apps that use Google sign-in, push notifications, Maps or in-app purchases talk to Google Play services. " +
                    "Inside the hidden space they can't reach the phone's own copy properly, so this copies Google Play " +
                    "services (and the Play Store, if you have it) from your phone into the hidden space.\n\n" +
                    "• It is experimental. It may not work on every phone or version, and some apps will still not sign in.\n" +
                    "• It uses extra storage (a few hundred MB) and takes a minute or two.\n" +
                    "• You sign in to Google inside the space separately from your phone's account.\n" +
                    "• You can remove it again here at any time."
            ),
            "Set up", "Cancel"
        ) { runEnableGms() }
    }

    /** Sets up Play services in the space. With [openAfter], that hidden app is opened as soon as it is ready. */
    private fun runEnableGms(openAfter: VirtualSpace.VApp? = null) {
        if (!EngineBridge.compiledIn) {
            showEngineUnavailable()
            return
        }
        val dlg = PixelProgressDialog(this, progressColors(), wavy = true)
        dlg.update(title = "Setting up Google Play services…", sub = "This can take a minute", progress = -1f)
        dlg.show(0L)
        Thread {
            val result = VirtualSpace.enableGms(applicationContext) { name ->
                runOnUiThread { dlg.update(title = "Setting up Google Play services…", sub = name, progress = -1f) }
            }
            // A clone that is already running keeps what it saw when it started (no Play services), so close it
            // here; opening it again starts it fresh.
            if (openAfter != null && result.ok) VirtualSpace.restart(openAfter.pkg)
            runOnUiThread {
                dlg.dismiss()
                if (isDestroyed || isFinishing) return@runOnUiThread
                renderVaultFiles()
                if (openAfter != null && result.ok) {
                    Toast.makeText(this, "Google Play services is ready", Toast.LENGTH_SHORT).show()
                    launchVirtualApp(openAfter)
                    return@runOnUiThread
                }
                val title = if (result.ok) "Google Play services is ready" else "Couldn't set up Google Play services"
                val body = msgBody(result.message).apply { setTextIsSelectable(true) }
                if (openAfter != null) {
                    showStyledDialog(title, body, "Open anyway", "Close") { launchVirtualApp(openAfter) }
                } else {
                    showStyledDialog(title, body, "OK", null) {}
                }
            }
        }.start()
    }

    private fun confirmRemoveGms() {
        showStyledDialog(
            "Remove Google Play services?",
            msgBody(
                "The copy inside the hidden space is erased, including any Google account you signed in to there. " +
                    "Your phone's own Google services are not touched. Apps that need it may stop signing in."
            ),
            "Remove", "Cancel"
        ) {
            Thread {
                val ok = VirtualSpace.disableGms(applicationContext)
                runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    if (!ok) Toast.makeText(this, "Couldn't remove all of it", Toast.LENGTH_SHORT).show()
                    renderVaultFiles()
                }
            }.start()
        }
    }

    private fun appIconTile(icon: android.graphics.drawable.Drawable, sizeDp: Int, radiusDp: Int): View =
        FrameLayout(this).apply {
            background = GradientDrawable().apply { cornerRadius = dpf(radiusDp); setColor(pal.numBg) }
            clipToOutline = true
            addView(ImageView(this@MainActivity).apply {
                setImageDrawable(icon)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(sizeDp / 8), dp(sizeDp / 8), dp(sizeDp / 8), dp(sizeDp / 8))
            }, FrameLayout.LayoutParams(-1, -1))
        }

    private fun showAppPicker(apps: List<HiddenApps.Candidate>, viaShizuku: Boolean = false) {
        var dlg: Dialog? = null
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val search = EditText(this).apply {
            hint = "Search ${apps.size} apps"
            setSingleLine(true)
            setTextColor(pal.text)
            setHintTextColor(pal.icon)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply { cornerRadius = dpf(100); setColor(pal.numBg) }
        }
        body.addView(search, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(20), 0, dp(20), dp(8)) })
        // Sort chips: A–Z, Z–A, newest / oldest installed, largest. The choice is remembered.
        var sortMode = AppSettings.appPickerSort(this)
        var currentQuery = ""
        val sortLabels = listOf("A–Z", "Z–A", "Newest", "Oldest", "Largest")
        val sortRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), 0, dp(12), dp(8))
        }
        body.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(sortRow)
        }, LinearLayout.LayoutParams(-1, -2))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list, LinearLayout.LayoutParams(-1, -2))

        lateinit var fill: (String) -> Unit
        fun drawSortChips() {
            sortRow.removeAllViews()
            sortLabels.forEachIndexed { i, label ->
                sortRow.addView(filterChip(label, i == sortMode) {
                    sortMode = i
                    AppSettings.setAppPickerSort(this, i)
                    drawSortChips()
                    fill(currentQuery)
                }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
            }
        }
        fill = fill@{ query ->
            currentQuery = query
            list.removeAllViews()
            val q = query.trim().lowercase()
            val matching = if (q.isEmpty()) apps else apps.filter { it.label.lowercase().contains(q) }
            val shown = when (sortMode) {
                1 -> matching.sortedByDescending { it.label.lowercase() }
                2 -> matching.sortedByDescending { it.installedAt }
                3 -> matching.sortedBy { it.installedAt }
                4 -> matching.sortedByDescending { it.size }
                else -> matching.sortedBy { it.label.lowercase() }
            }
            if (shown.isEmpty()) {
                list.addView(TextView(this).apply {
                    text = "No apps match \"${query.trim()}\""
                    setTextColor(pal.icon)
                    gravity = Gravity.CENTER
                    setPadding(dp(24), dp(24), dp(24), dp(24))
                })
                return@fill
            }
            for (app in shown) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(20), dp(8), dp(20), dp(8))
                    background = rippleBg()
                    setOnClickListener { dlg?.dismiss(); confirmHideApp(app, viaShizuku) }
                }
                row.addView(appIconTile(app.icon, 48, 14), LinearLayout.LayoutParams(dp(48), dp(48)))
                row.addView(TextView(this).apply {
                    text = app.label
                    setTextColor(pal.text)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(dp(16), 0, 0, 0)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                list.addView(row, LinearLayout.LayoutParams(-1, -2))
            }
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { fill(s?.toString() ?: "") }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        drawSortChips()
        fill("")
        dlg = showStyledDialog("Choose an app to hide", body, "Cancel", null) {}
        dlg?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun confirmHideApp(app: HiddenApps.Candidate, viaShizuku: Boolean = false) {
        if (viaShizuku) {
            confirmShizukuHide(app)
            return
        }
        val msg = "\"${app.label}\" is cloned into a hidden space inside this app and runs there, like in Parallel Space. " +
            "It is not installed on your phone and nothing is stored in the vault.\n\n" +
            "• The clone starts fresh: you sign in again, and it has its own data.\n" +
            "• You open it from the Apps folder in the vault.\n" +
            "• Next you can choose to uninstall the original, so the app only exists in here."
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(appIconTile(app.icon, 72, 20), LinearLayout.LayoutParams(dp(72), dp(72)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(12)
        })
        body.addView(msgBody(msg))
        // Answering "does this app use Google Play services?" can mean reading the app's code (see
        // VirtualSpace.usesGms), so it is done off the UI thread and the checkbox appears when the answer is yes.
        var withGms = false
        val gmsOffer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(gmsOffer, LinearLayout.LayoutParams(-1, -2))
        showStyledDialog("Hide ${app.label}?", body, "Hide app", "Cancel") { hideApp(app, withGms) }
        if (!VirtualSpace.gmsEnabled(this)) {
            Thread {
                if (!VirtualSpace.usesGms(applicationContext, app.pkg)) return@Thread
                runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    withGms = AppSettings.gmsWithHide(this@MainActivity)
                    gmsOffer.addView(android.widget.CheckBox(this@MainActivity).apply {
                        text = if (VirtualSpace.usesMicroG(this@MainActivity)) "Also set up microG (experimental)" else "Also set up Google Play services (experimental)"
                        isChecked = withGms
                        setTextColor(pal.text)
                        setOnCheckedChangeListener { _, checked -> withGms = checked }
                    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
                    gmsOffer.addView(
                        rowMeta(if (VirtualSpace.usesMicroG(this@MainActivity)) "This app uses Google services (sign-in, notifications or Maps) and this phone has none. Inside the hidden space they only work if microG is installed there (downloads about 100 MB)." else "This app uses Google services (sign-in, notifications or Maps). Inside the hidden space they only work if Google Play services is copied in as well.")
                            .apply { maxLines = 5 },
                        LinearLayout.LayoutParams(-1, -2)
                    )
                }
            }.start()
        }
    }

    private fun hideApp(app: HiddenApps.Candidate, withGms: Boolean = false) {
        val dlg = PixelProgressDialog(this, progressColors(), wavy = true)
        dlg.update(title = "Moving into hidden space…", sub = shortName(app.label), progress = -1f)
        dlg.show(0L)
        Thread {
            val error = VirtualSpace.add(applicationContext, app.pkg)
            var gmsNote: String? = null
            if (error == null && withGms) {
                runOnUiThread { dlg.update(title = "Setting up Google Play services…", sub = "This can take a minute", progress = -1f) }
                val r = VirtualSpace.enableGms(applicationContext) { name ->
                    runOnUiThread { dlg.update(title = "Setting up Google Play services…", sub = name, progress = -1f) }
                }
                gmsNote = if (r.ok) null else r.message
            }
            runOnUiThread {
                dlg.dismiss()
                if (isDestroyed || isFinishing) return@runOnUiThread
                if (error != null) {
                    Toast.makeText(this, "Couldn't hide ${app.label}: $error", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                renderVaultFiles()
                showStyledDialog(
                    "${app.label} is hidden",
                    msgBody(
                        "Open it any time from the Apps folder. It starts signed out." +
                            (if (gmsNote != null) "\n\nGoogle Play services: $gmsNote" else "") +
                            "\n\nThe original is still on your home screen. Uninstall it so the app can only be reached from here?"
                    ),
                    "Uninstall original", "Keep it"
                ) {
                    leavingForTask = true
                    try {
                        HiddenApps.requestUninstall(this, app.pkg)
                    } catch (e: Exception) {
                        leavingForTask = false
                        Toast.makeText(this, "Couldn't open the uninstall screen", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    // ----- apps living in the hidden space (shown at the top of the Apps folder) -----

    private fun openVirtualApp(app: VirtualSpace.VApp) {
        if (EngineBridge.available) {
            launchOrOfferGms(app)
            return
        }
        if (!EngineBridge.compiledIn) {
            showEngineUnavailable()
            return
        }
        Thread {
            val ok = VirtualSpace.ensureStarted(applicationContext)
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                if (ok) launchOrOfferGms(app) else showEngineUnavailable()
            }
        }.start()
    }

    /**
     * Opens [app]. If it depends on Google Play services and they are not in the hidden space, offers to set them up
     * first (once per app per session): a clone of the Google app, Maps, YouTube... opened without them shows
     * "Google Play services isn't supported on this device". The engine must be running.
     */
    private fun launchOrOfferGms(app: VirtualSpace.VApp) {
        if (app.pkg in gmsOfferedFor) {
            launchVirtualApp(app)
            return
        }
        Thread {
            val needs = try {
                VirtualSpace.needsGmsSetup(applicationContext, app.pkg)
            } catch (_: Throwable) {
                false
            }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                if (!needs) {
                    launchVirtualApp(app)
                    return@runOnUiThread
                }
                gmsOfferedFor.add(app.pkg)
                showStyledDialog(
                    "${shortName(app.label)} needs Google Play services",
                    msgBody(
                        "Without Google Play services inside the hidden space, this app shows \"Google Play services " +
                            "isn't supported on this device\".\n\n" +
                            (if (VirtualSpace.usesMicroG(this)) "Set it up now? This phone has none, so it downloads microG (a free, open-source " +
                            "replacement, about 100 MB) into the hidden space and then opens the app. It is experimental, and " +
                            "you sign in to Google inside the space separately from your phone."
                        else "Set it up now? It copies Google Play services from your phone into the hidden space and " +
                            "then opens the app. It is experimental, and you sign in to Google inside the space " +
                            "separately from your phone.")
                    ),
                    "Set up and open", "Open anyway",
                    onNegative = { launchVirtualApp(app) }
                ) { runEnableGms(openAfter = app) }
            }
        }.start()
    }

    private fun launchVirtualApp(app: VirtualSpace.VApp) {
        if (!VirtualSpace.launch(this, app.pkg)) {
            Toast.makeText(this, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showVirtualAppMenu(app: VirtualSpace.VApp) {
        showListDialog(app.label, listOf(
            Triple(R.drawable.ic_play, "Open", { openVirtualApp(app) }),
            Triple(R.drawable.ic_delete, "Remove from hidden space", { confirmRemoveVirtual(app) })
        ))
    }

    private fun confirmRemoveVirtual(app: VirtualSpace.VApp) {
        showStyledDialog(
            "Remove ${app.label}?",
            msgBody("The hidden copy and everything inside it (logins, chats, files) is erased. An app you uninstalled earlier would have to be installed again from its store."),
            "Remove", "Cancel"
        ) {
            Thread {
                val ok = VirtualSpace.remove(applicationContext, app.pkg)
                runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    if (!ok) Toast.makeText(this, "Couldn't remove ${app.label}", Toast.LENGTH_SHORT).show()
                    renderVaultFiles()
                }
            }.start()
        }
    }

    private fun showHiddenAppMenu(file: File) {
        val pkg = HiddenApps.packageOf(this, file)
        val label = VaultManager.displayName(file).removeSuffix(HiddenApps.EXT)
        if (pkg == null) {
            showListDialog(label, listOf(Triple(R.drawable.ic_delete, "Delete from vault", { confirmDelete(file) })))
            return
        }
        val installed = HiddenApps.isInstalled(this, pkg)
        val rows = ArrayList<Triple<Int, String, () -> Unit>>()
        rows.add(Triple(R.drawable.ic_play, if (installed) "Open" else "Install & open", { openHiddenApp(file, pkg, label) }))
        if (installed) rows.add(Triple(R.drawable.ic_lock, "Hide again (uninstall)", { confirmHideAgain(pkg, label) }))
        rows.add(Triple(R.drawable.ic_delete, "Delete from vault", { confirmDelete(file) }))
        showListDialog(label, rows)
    }

    private fun confirmHideAgain(pkg: String, label: String) {
        showStyledDialog(
            "Hide $label again?",
            msgBody("Android will ask you to uninstall it. It disappears from your home screen, its data is lost, and the copy stays in the vault."),
            "Continue", "Cancel"
        ) {
            leavingForTask = true
            try {
                HiddenApps.requestUninstall(this, pkg)
            } catch (e: Exception) {
                leavingForTask = false
                Toast.makeText(this, "Couldn't open the uninstall screen", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openHiddenApp(file: File, pkg: String, label: String) {
        if (HiddenApps.isInstalled(this, pkg)) {
            if (!HiddenApps.launch(this, pkg)) Toast.makeText(this, "Couldn't open $label", Toast.LENGTH_SHORT).show()
            return
        }
        if (!HiddenApps.canInstall(this)) {
            showStyledDialog(
                "Allow installing apps",
                msgBody("To open hidden apps, Android needs your permission for this app to install apps. Tap Settings, switch on the permission, then come back and try again."),
                "Settings", "Cancel"
            ) {
                leavingForTask = true
                try {
                    HiddenApps.openInstallSettings(this)
                } catch (e: Exception) {
                    leavingForTask = false
                }
            }
            return
        }
        val dlg = PixelProgressDialog(this, progressColors(), wavy = true)
        dlg.update(title = "Preparing…", sub = shortName(label), progress = -1f)
        dlg.show(0L)
        HiddenApps.install(this, file, beforeConfirm = { dlg.dismiss(); leavingForTask = true }) { ok, msg ->
            dlg.dismiss()
            if (isDestroyed || isFinishing) return@install
            if (ok) {
                renderVaultFiles()
                if (!HiddenApps.launch(this, pkg)) Toast.makeText(this, "$label installed", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Couldn't install $label" + (if (msg.isNullOrBlank()) "" else ": $msg"), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showMoveDialog(files: List<File>) {
        val rows = mutableListOf<Triple<Int, String, () -> Unit>>()
        if (currentFolder != null) rows.add(Triple(R.drawable.ic_folder, "Main vault", { moveInto(files, null) }))
        for (f in VaultManager.listFolders(this)) {
            if (f != currentFolder) rows.add(Triple(R.drawable.ic_folder, f, { moveInto(files, f) }))
        }
        rows.add(Triple(R.drawable.ic_create_folder, "New folder…", { promptNewFolder { name -> moveInto(files, name) } }))
        showListDialog("Move to", rows)
    }

    private fun moveInto(files: List<File>, folder: String?) {
        var ok = 0
        for (f in files) if (VaultManager.moveFile(this, f, folder) != null) ok++
        selected.clear()
        renderVaultFiles()
        val where = folder ?: "main vault"
        Toast.makeText(
            this,
            when {
                ok == 0 -> "Couldn't move"
                ok < files.size -> "Moved $ok of ${files.size} to $where"
                ok == 1 -> "Moved to $where"
                else -> "Moved $ok files to $where"
            },
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updateRow(path: String) {
        for (m in rowUpdaters) m[path]?.invoke()
    }

    private fun toggleSelect(path: String) {
        if (!selected.add(path)) selected.remove(path)
        updateRow(path)
        updateSelectionUi()
    }

    private fun clearSelection() {
        val old = selected.toList()
        selected.clear()
        old.forEach { updateRow(it) }
        updateSelectionUi()
    }

    private fun selectedFiles(): List<File> = selected.map { File(it) }.filter { it.exists() }

    private fun setFabMenu(open: Boolean) {
        fabMenuOpen = open
        fabScrim.visibility = if (open) View.VISIBLE else View.GONE
        fabMenu.visibility = if (open) View.VISIBLE else View.GONE
        folderPill.visibility = if (currentFolder == null) View.VISIBLE else View.GONE
        fabShape.setColor(if (open) pal.cursor else pal.acBg)
        fabIcon.setImageResource(if (open) R.drawable.ic_close else R.drawable.ic_add)
        fabIcon.setColorFilter(if (open) pal.bg else pal.acFg)
        fabMenu.animate().cancel()
        fabScrim.animate().cancel()
        fabIcon.animate().cancel()
        if (open) {
            fabMenu.alpha = 0f
            fabScrim.alpha = 0f
            fabMenu.animate().alpha(1f).setDuration(150).start()
            fabScrim.animate().alpha(1f).setDuration(150).start()
            // The + turns into a x with a quarter turn and the options pop up one after another, nearest first.
            fabIcon.rotation = -90f
            fabIcon.animate().rotation(0f).setDuration(240).setInterpolator(chromeIn).start()
            val pills = (0 until fabMenu.childCount).map { fabMenu.getChildAt(it) }.filter { it.visibility == View.VISIBLE }.reversed()
            pills.forEachIndexed { i, v ->
                v.alpha = 0f
                v.translationY = dpf(20)
                v.scaleX = 0.92f
                v.scaleY = 0.92f
                v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setStartDelay(i * 45L).setDuration(240)
                    .setInterpolator(chromeIn).start()
            }
        } else {
            fabIcon.rotation = 0f
        }
    }

    private fun updateSelectionUi() {
        val n = selected.size
        if (n > 0 && fabMenuOpen) setFabMenu(false)
        vaultTitle.text = when {
            n > 0 -> "$n selected"
            currentFolder == null -> "CalCognito"
            else -> "‹  $currentFolder"
        }
        vaultCloseBtn.text = if (n > 0) "Cancel" else "Close"
        vaultFabHost.visibility = if (vaultVisible && n == 0) View.VISIBLE else View.GONE
        updateVaultInfoButton(n > 0)
    }

    /** Settings gear while nothing is selected, three dots (Select all / Invert selection) while something is. */
    private fun updateVaultInfoButton(selecting: Boolean) {
        if (!::vaultInfoBtn.isInitialized || vaultInfoSelectMode == selecting) return
        vaultInfoSelectMode = selecting
        val btn = vaultInfoBtn
        btn.animate().cancel()
        btn.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(90).setListener(null).withEndAction {
            // Read the state again: it may have flipped back while the icon was fading.
            val now = selected.isNotEmpty()
            btn.setImageResource(if (now) R.drawable.ic_more_vert else R.drawable.ic_settings)
            btn.contentDescription = if (now) "More options" else "Settings"
            btn.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start()
        }.start()
    }

    /** Paths of every file the current tab of the open folder shows (what Select all / Invert act on). */
    private fun visibleVaultPaths(): List<String> =
        filterSort(vaultBaseFiles(), vaultFilter).map { it.absolutePath }

    private fun selectAllVisible() {
        val added = visibleVaultPaths().filter { selected.add(it) }
        added.forEach { updateRow(it) }
        updateSelectionUi()
    }

    private fun invertSelection() {
        val all = visibleVaultPaths()
        for (path in all) if (!selected.remove(path)) selected.add(path)
        all.forEach { updateRow(it) }
        updateSelectionUi()
    }

    private fun showSelectionMenu(anchor: View) {
        showViewerMenu(anchor, listOf(
            MenuRow(R.drawable.ic_select_all, "Select all", pal.text) { selectAllVisible() },
            MenuRow(R.drawable.ic_invert_selection, "Invert selection", pal.text) { invertSelection() }
        ))
    }

    private fun onFileLongPress(file: File) {
        if (file.absolutePath !in selected) toggleSelect(file.absolutePath)
        showSelectionSheet()
    }

    private fun showSelectionSheet() {
        if (selectedFiles().isEmpty()) return
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val r = dpf(28)
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                setColor(pal.dialogBg)
            }
            setPadding(0, dp(20), 0, dp(16))
        }
        sheet.addView(TextView(this).apply {
            text = "Selected files"
            setTextColor(pal.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(dp(24), 0, dp(24), dp(16))
        })
        sheet.addView(View(this).apply { setBackgroundColor(pal.divider) }, LinearLayout.LayoutParams(-1, dp(1)))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sheet.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, -2))
        val current = selectedFiles()

        fun item(icon: Int, label: String, color: Int, action: () -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(24), 0, dp(24), 0)
                background = rippleBg()
                isClickable = true
                setOnClickListener { dialog.dismiss(); action() }
            }
            row.addView(ImageView(this).apply {
                setImageResource(icon)
                setColorFilter(color)
            }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(24) })
            row.addView(TextView(this).apply {
                text = label
                setTextColor(color)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            })
            list.addView(row, LinearLayout.LayoutParams(-1, dp(60)))
        }
        item(R.drawable.ic_open_in_new, "Open with", pal.text) {
            val files = selectedFiles()
            if (files.size == 1) openExternally(files[0], VaultManager.mimeFor(VaultManager.displayName(files[0])), chooser = true)
            else Toast.makeText(this, "Select a single file to use Open with", Toast.LENGTH_SHORT).show()
        }
        item(R.drawable.ic_unhide, "Unhide", pal.text) { unhideFiles(selectedFiles()) }
        if (current.any { !VaultManager.isEncrypted(it) }) {
            item(R.drawable.ic_lock, "Encrypt", pal.text) { cryptFiles(selectedFiles(), true) }
        }
        if (current.any { VaultManager.isEncrypted(it) }) {
            item(R.drawable.ic_lock_open, "Decrypt", pal.text) { cryptFiles(selectedFiles(), false) }
        }
        item(R.drawable.ic_open_with, "Move", pal.text) { showMoveDialog(selectedFiles()) }
        item(R.drawable.ic_share, "Share", pal.text) { shareFiles(selectedFiles()) }
        item(R.drawable.ic_delete, "Delete", pal.clearFg) { confirmDeleteSelected(selectedFiles()) }

        if (current.size == 1) {
            item(R.drawable.ic_edit, "Rename", pal.text) { renameSelected(current[0]) }
        }

        dialog.setContentView(sheet)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
        trackVaultDialog(dialog)
        dialog.show()
    }

    private fun confirmDeleteSelected(files: List<File>) {
        if (files.isEmpty()) return
        val n = files.size
        showStyledDialog(
            "Delete permanently?",
            msgBody(
                if (n == 1) "\"${VaultManager.displayName(files[0])}\" will be deleted from the vault and cannot be recovered."
                else "$n files will be deleted from the vault and cannot be recovered."
            ),
            "Delete", "Cancel"
        ) {
            files.forEach { VaultManager.deleteFile(this, it) }
            selected.clear()
            renderVaultFiles()
            Toast.makeText(this, if (n == 1) "Deleted" else "Deleted $n files", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renameSelected(file: File) {
        promptText("Rename", VaultManager.displayName(file), "Rename", "Name") { raw ->
            val name = raw.trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Enter a name", Toast.LENGTH_SHORT).show()
            } else if (name != VaultManager.displayName(file)) {
                if (VaultManager.renameFile(this, file, name) != null) {
                    selected.clear()
                    renderVaultFiles()
                    Toast.makeText(this, "Renamed", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Couldn't rename (that name may already exist)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun cryptFiles(files: List<File>, encrypt: Boolean) {
        val todo = files.filter { VaultManager.isEncrypted(it) != encrypt }
        if (todo.isEmpty()) {
            Toast.makeText(this, if (encrypt) "Already encrypted" else "Already decrypted", Toast.LENGTH_SHORT).show()
            return
        }
        startCrypt(todo, encrypt)
    }

    /**
     * Encrypts / decrypts in the background. Files adding up to 100 MB or more get the same progress card as
     * adding (with Cancel / Hide); smaller ones just show a short toast and the result.
     */
    private fun startCrypt(todo: List<File>, encrypt: Boolean): TransferManager.Job {
        val job = TransferJobs.crypt(todo, encrypt)
        selected.clear()
        if (job.cardDelayMs < 0L) Toast.makeText(this, if (encrypt) "Encrypting…" else "Decrypting…", Toast.LENGTH_SHORT).show()
        else askNotificationPermissionOnce()
        renderVaultFiles()
        TransferManager.enqueue(this, job)
        return job
    }

    private fun unhideFiles(files: List<File>) = withStoragePermission {
        if (files.isEmpty()) return@withStoragePermission
        startUnhide(files)
    }

    private fun upgradeInBackground(file: File, wantThumb: Boolean, applyThumb: (Bitmap) -> Unit) {
        val path = file.absolutePath
        if (!migrating.add(path)) return
        try {
            migrateExecutor.execute {
                try {
                    if (VaultManager.isLegacy(file) && VaultManager.migrateLegacy(file) && wantThumb) {
                        val bm = VaultManager.readThumb(file)
                        if (bm != null) {
                            thumbCache[path + file.lastModified()] = bm
                            runOnUiThread { applyThumb(bm) }
                        }
                    }
                } finally {
                    migrating.remove(path)
                }
            }
        } catch (_: Exception) {
            migrating.remove(path)
        }
    }

    /** Every file in the vault (all folders) whose name contains [q], ignoring case. */
    private fun vaultSearchFiles(q: String): List<File> {
        val ql = q.lowercase()
        val out = ArrayList<File>()
        val scopes: List<String?> = listOf<String?>(null) + VaultManager.listFolders(this)
        for (scope in scopes) {
            for (f in VaultManager.listFiles(this, scope)) {
                if (VaultManager.displayName(f).lowercase().contains(ql)) out.add(f)
            }
        }
        return out
    }

    /** The files the list is based on: the open folder, or matches from the whole vault while searching. */
    private fun vaultBaseFiles(): List<File> =
        if (vaultQuery.isNotBlank()) vaultSearchFiles(vaultQuery.trim()) else VaultManager.listFiles(this, currentFolder)

    private fun toggleVaultSearch() {
        if (vaultSearchOpen) closeVaultSearch() else openVaultSearch()
    }

    private fun openVaultSearch() {
        vaultSearchOpen = true
        animateShown(vaultSearchBox, true, -dpf(10))
        vaultSearchField.requestFocus()
        vaultSearchField.post {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showSoftInput(vaultSearchField, 0)
        }
    }

    private fun closeVaultSearch() {
        if (!vaultSearchOpen && vaultQuery.isEmpty()) return
        val hadQuery = vaultQuery.isNotEmpty()
        vaultSearchOpen = false
        vaultQuery = ""
        vaultSearchField.removeCallbacks(searchDebounce)
        vaultSearchField.setText("")
        vaultSearchField.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(vaultSearchField.windowToken, 0)
        animateShown(vaultSearchBox, false, -dpf(10))
        if (hadQuery) renderVaultFiles()
    }

    /** Used when the vault is closed: forget the search without drawing anything. */
    private fun resetVaultSearch() {
        vaultSearchOpen = false
        vaultQuery = ""
        if (::vaultSearchField.isInitialized) {
            vaultSearchField.removeCallbacks(searchDebounce)
            vaultSearchField.setText("")
            vaultSearchBox.animate().cancel()
            vaultSearchBox.visibility = View.GONE
        }
    }

    /** Applies the filter of tab [filter] (0 All, 1 Images, 2 Videos, 3 Files) and the current sort order. */
    private fun filterSort(all: List<File>, filter: Int): List<File> {
        val filtered = all.filter { filter == 0 || categoryOf(VaultManager.displayName(it)) == filter }
        return when (vaultSort) {
            1 -> filtered.sortedByDescending { it.lastModified() }
            2 -> filtered.sortedByDescending { it.length() }
            else -> filtered.sortedBy { VaultManager.displayName(it).lowercase() }
        }
    }

    /** Rebuilds the tab pages that exist (the visible one and its neighbours); the others build themselves when swiped to. */
    private fun renderVaultFiles() {
        renderVaultChips()
        val visible = filterSort(vaultBaseFiles(), vaultFilter)
        selected.retainAll(visible.map { it.absolutePath }.toSet())
        updateSelectionUi()
        for (i in vaultPages.indices) if (vaultPages[i] != null) renderVaultPage(i)
    }

    /** The pager's pages: one scrollable list per tab, created on demand and filled by [renderVaultPage]. */
    private inner class VaultTabsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = 4
        override fun getItemViewType(position: Int) = position
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val list = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, dp(136))
            }
            val scroll = ScrollView(this@MainActivity).apply {
                layoutParams = RecyclerView.LayoutParams(-1, -1)
                addView(list, FrameLayout.LayoutParams(-1, -2))
            }
            vaultPages[viewType] = list
            return object : RecyclerView.ViewHolder(scroll) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            renderVaultPage(position)
        }
        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            // An adapter left over from before a rotation rebuild must not clear the new pager's pages.
            if (vaultPager.adapter !== this) return
            vaultPages[holder.itemViewType] = null
            vaultPageKey[holder.itemViewType] = null
            rowUpdaters[holder.itemViewType].clear()
        }
    }

    /** Called when a swipe (or a chip tap) lands on another tab. */
    private fun onVaultTabSelected(position: Int) {
        if (position == vaultFilter) return
        vaultFilter = position
        if (!vaultVisible) return
        renderVaultChips()
        val keep = filterSort(VaultManager.listFiles(this, currentFolder), position).map { it.absolutePath }.toSet()
        val dropped = selected.filter { it !in keep }
        selected.removeAll(dropped.toSet())
        dropped.forEach { updateRow(it) }
        updateSelectionUi()
    }

    // ----- vault look: soft cards, section labels, a summary card, friendly empty states -----

    /** Which folder each tab page last showed, so the entrance animation only plays when the content is new. */
    private val vaultPageKey = arrayOfNulls<String>(4)

    /** Presses shrink a row slightly, like a physical button. */
    private fun pressScale(v: View) {
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(0.97f).scaleY(0.97f).setStartDelay(0).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(180).setInterpolator(chromeIn).start()
            }
            false
        }
    }

    private fun sectionLabel(label: String, trailing: String? = null): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(22), dp(16), dp(22), dp(6))
        addView(TextView(this@MainActivity).apply {
            text = label
            setTextColor(pal.icon)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setAllCaps(true)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (trailing != null) {
            addView(TextView(this@MainActivity).apply {
                text = trailing
                setTextColor(pal.icon)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            })
        }
    }

    /** One list row as a rounded, softly tinted card. Returns the card and its background (selection recolours it). */
    private fun cardRow(): Pair<LinearLayout, GradientDrawable> {
        val bg = GradientDrawable().apply { cornerRadius = dpf(20); setColor(pal.histBg) }
        val mask = GradientDrawable().apply { cornerRadius = dpf(20); setColor(Color.BLACK) }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(8), dp(10))
            background = bg
            foreground = RippleDrawable(ColorStateList.valueOf(pal.ripple), null, mask)
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(12), dp(4), dp(12), dp(4)) }
        }
        return row to bg
    }

    private fun rowTitle(text: String, lines: Int = 1): TextView = TextView(this).apply {
        this.text = text
        setTextColor(pal.text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        maxLines = lines
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun rowMeta(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(pal.icon)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dp(2), 0, 0)
    }

    /** Kinds of files the "Secure Vault" overview lists, like a file manager's storage screen. */
    private fun fileKindOf(name: String): String {
        val mime = VaultManager.mimeFor(name)
        val ext = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
        return when {
            mime.startsWith("image/") -> "Images"
            mime.startsWith("video/") -> "Videos"
            mime.startsWith("audio/") -> "Audio"
            ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "odp", "csv", "epub", "md", "json", "xml", "html") -> "Documents"
            ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz") -> "Archives"
            ext == "apk" -> "Installers"
            else -> "Other files"
        }
    }

    /** What the overview card shows when tapped: how many files of each type are in the vault, and how big. */
    private fun showVaultBreakdown(everything: List<File>, folderCount: Int, appCount: Int) {
        val order = listOf("Images", "Videos", "Audio", "Documents", "Archives", "Installers", "Other files")
        val colors = listOf(pal.eqBg, pal.cursor, pal.result, pal.opBg, pal.acBg, pal.sciFg, pal.icon)
        val counts = HashMap<String, Int>()
        val bytes = HashMap<String, Long>()
        var encrypted = 0
        for (f in everything) {
            val k = fileKindOf(VaultManager.displayName(f))
            counts[k] = (counts[k] ?: 0) + 1
            bytes[k] = (bytes[k] ?: 0L) + f.length()
            if (VaultManager.isEncrypted(f)) encrypted++
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, dp(24), 0)
        }
        fun row(label: String, value: String, color: Int?) {
            box.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(9), 0, dp(9))
                if (color != null) {
                    addView(View(this@MainActivity).apply {
                        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
                    }, LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(12) })
                }
                addView(TextView(this@MainActivity).apply {
                    text = label
                    setTextColor(pal.text)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(TextView(this@MainActivity).apply {
                    text = value
                    setTextColor(pal.icon)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                })
            }, LinearLayout.LayoutParams(-1, -2))
        }
        var any = false
        order.forEachIndexed { i, k ->
            val c = counts[k] ?: 0
            if (c == 0) return@forEachIndexed
            any = true
            row(k, "$c · " + android.text.format.Formatter.formatShortFileSize(this, bytes[k] ?: 0L), colors[i])
        }
        if (!any) row("No files yet", "", null)
        if (folderCount > 0 || appCount > 0 || encrypted > 0) {
            box.addView(View(this).apply { setBackgroundColor(pal.numBg) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(6); bottomMargin = dp(6) })
        }
        if (folderCount > 0) row("Folders", "$folderCount", null)
        if (appCount > 0) row("Hidden apps", "$appCount", null)
        if (encrypted > 0) row("Encrypted files", "$encrypted of ${everything.size}", null)
        showStyledDialog("Secure Vault", box, "Close", null) {}
    }

    /** The card at the top of the vault: "Secure Vault" and a one-line summary; tap it for the breakdown by file type. */
    private fun vaultSummaryCard(everything: List<File>, folderCount: Int, appCount: Int): View {
        var imageBytes = 0L; var videoBytes = 0L; var otherBytes = 0L
        for (f in everything) {
            val len = f.length()
            when (categoryOf(VaultManager.displayName(f))) {
                1 -> imageBytes += len
                2 -> videoBytes += len
                else -> otherBytes += len
            }
        }
        val total = everything.size
        val totalBytes = imageBytes + videoBytes + otherBytes
        val fg = pal.acFg
        val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(pal.opBg, pal.acBg)).apply { cornerRadius = dpf(24) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = bg
            foreground = RippleDrawable(ColorStateList.valueOf(pal.ripple), null, GradientDrawable().apply { cornerRadius = dpf(24); setColor(Color.BLACK) })
            isClickable = true
            isFocusable = true
            contentDescription = "Secure Vault, show what is inside"
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(12), dp(4), dp(12), dp(8)) }
            setOnClickListener { showVaultBreakdown(everything, folderCount, appCount) }
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor((fg and 0xFFFFFF) or (0x22 shl 24)) }
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_lock)
                setColorFilter(fg)
            }, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(12) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply {
            text = "Secure Vault"
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
        })
        val parts = ArrayList<String>()
        parts.add(if (total == 1) "1 item" else "$total items")
        parts.add(android.text.format.Formatter.formatShortFileSize(this, totalBytes))
        if (folderCount > 0) parts.add(if (folderCount == 1) "1 folder" else "$folderCount folders")
        if (appCount > 0) parts.add(if (appCount == 1) "1 hidden app" else "$appCount hidden apps")
        col.addView(TextView(this).apply {
            text = parts.joinToString(" · ")
            setTextColor(fg)
            alpha = 0.8f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        top.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(top, LinearLayout.LayoutParams(-1, -2))

        if (totalBytes > 0) {
            val bar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = GradientDrawable().apply { cornerRadius = dpf(4); setColor((fg and 0xFFFFFF) or (0x22 shl 24)) }
                clipToOutline = true
            }
            // Every non-empty part gets at least a sliver so it stays visible.
            val floor = totalBytes / 50
            fun seg(bytes: Long, color: Int) {
                if (bytes > 0) bar.addView(View(this).apply { setBackgroundColor(color) },
                    LinearLayout.LayoutParams(0, -1, maxOf(bytes, floor).toFloat()))
            }
            seg(imageBytes, pal.eqBg); seg(videoBytes, pal.cursor); seg(otherBytes, pal.result)
            card.addView(bar, LinearLayout.LayoutParams(-1, dp(5)).apply { topMargin = dp(10) })
        }
        return card
    }

    private fun vaultEmptyState(icon: Int, title: String, sub: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(32), dp(56), dp(32), dp(24))
        addView(FrameLayout(this@MainActivity).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(pal.numBg) }
            addView(ImageView(this@MainActivity).apply {
                setImageResource(icon)
                setColorFilter(pal.result)
            }, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(104), dp(104)))
        addView(TextView(this@MainActivity).apply {
            text = title
            setTextColor(pal.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(6))
        })
        addView(TextView(this@MainActivity).apply {
            text = sub
            setTextColor(pal.icon)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
        })
    }

    /** A launcher-style tile: a rounded icon with the app's name under it ([add] makes the dashed "Hide app" tile). */
    private fun appTile(label: String, icon: Bitmap?, add: Boolean, onClick: () -> Unit, onLong: (() -> Unit)?): View {
        val box = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dpf(20)
                if (add) { setColor(Color.TRANSPARENT); setStroke(dp(2), pal.icon) } else setColor(pal.histBg)
            }
            clipToOutline = true
            addView(ImageView(this@MainActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                when {
                    add -> { setImageResource(R.drawable.ic_add); setColorFilter(pal.icon); setPadding(dp(18), dp(18), dp(18), dp(18)) }
                    icon != null -> { setImageBitmap(icon); setPadding(dp(8), dp(8), dp(8), dp(8)) }
                    else -> { setImageResource(R.drawable.ic_lock); setColorFilter(pal.result); setPadding(dp(16), dp(16), dp(16), dp(16)) }
                }
            }, FrameLayout.LayoutParams(-1, -1))
        }
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
            isClickable = true
            setOnClickListener { onClick() }
            if (onLong != null) {
                setOnLongClickListener {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onLong()
                    true
                }
            }
            addView(box, LinearLayout.LayoutParams(dp(64), dp(64)))
            addView(TextView(this@MainActivity).apply {
                text = label
                setTextColor(if (add) pal.icon else pal.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            }, LinearLayout.LayoutParams(-1, -2))
        }
        pressScale(tile)
        return tile
    }

    /** The hidden apps as a launcher grid, ending with a "Hide app" tile. */
    private fun appGrid(
        apps: List<VirtualSpace.VApp>,
        withAdd: Boolean = true,
        onOpen: (VirtualSpace.VApp) -> Unit = { openVirtualApp(it) },
        onMenu: (VirtualSpace.VApp) -> Unit = { showVirtualAppMenu(it) }
    ): View {
        val cols = if (winW() > dp(600)) 6 else 4
        val tiles = ArrayList<View>()
        for (app in apps) tiles.add(appTile(app.label, app.icon, false, { onOpen(app) }, { onMenu(app) }))
        if (withAdd) tiles.add(appTile("Hide app", null, true, { startHideApp() }, null))
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        for (chunk in tiles.chunked(cols)) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (t in chunk) line.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
            repeat(cols - chunk.size) { line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            grid.addView(line, LinearLayout.LayoutParams(-1, -2))
        }
        return grid
    }

    private fun renderVaultPage(filter: Int) {
        val list = vaultPages[filter] ?: return
        val folder = currentFolder
        rowUpdaters[filter].clear()
        list.removeAllViews()

        val q = vaultQuery.trim()
        val searching = q.isNotEmpty()
        val ql = q.lowercase()
        val folders = when {
            searching -> VaultManager.listFolders(this).filter { it.lowercase().contains(ql) }
            folder == null -> VaultManager.listFolders(this)
            else -> emptyList()
        }
        val all = vaultBaseFiles()
        val files = filterSort(all, filter)
        val inApps = !searching && folder == VaultManager.APPS_FOLDER && filter == 0
        val virtualApps = when {
            inApps -> VirtualSpace.list(this)
            searching && filter == 0 -> VirtualSpace.list(this).filter { it.label.lowercase().contains(ql) }
            else -> emptyList()
        }
        val folderNames = if (searching) VaultManager.listFolders(this).toSet() else emptySet()

        // The entrance animation plays only when this page shows something new (first build, another folder, reopened).
        val pageKey = folder ?: "\u0000root"
        val animateIn = vaultPageKey[filter] != pageKey
        vaultPageKey[filter] = pageKey

        if (folder == null && !searching) {
            val everything = ArrayList<File>(all)
            for (f in folders) everything.addAll(VaultManager.listFiles(this, f))
            val appCount = VirtualSpace.list(this).size + ShizukuHider.list(this).size
            if (everything.isNotEmpty() || appCount > 0) list.addView(vaultSummaryCard(everything, folders.size, appCount))
        }

        if (searching && virtualApps.isNotEmpty()) {
            list.addView(sectionLabel("Hidden apps", virtualApps.size.toString()))
            list.addView(appGrid(virtualApps, withAdd = false))
        }

        if (inApps) {
            if (EngineBridge.compiledIn) list.addView(gmsCard())
            list.addView(sectionLabel("Hidden apps", virtualApps.size.toString()))
            list.addView(appGrid(virtualApps))
            if (virtualApps.isNotEmpty()) {
                list.addView(TextView(this).apply {
                    text = "Tap to open · hold for options"
                    setTextColor(pal.icon)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(2), 0, dp(8))
                })
            }
        }

        // Apps hidden on the phone itself (Shizuku / Sui): in the Apps folder, and in search results.
        val shizukuApps = if (inApps || (searching && filter == 0)) {
            ShizukuHider.list(this).filter { !searching || it.label.lowercase().contains(ql) }
        } else emptyList()
        if (shizukuApps.isNotEmpty()) {
            list.addView(sectionLabel("Hidden on this phone", shizukuApps.size.toString()))
            list.addView(appGrid(shizukuApps, withAdd = false,
                onOpen = { runShizukuUnhide(it, true) }, onMenu = { showShizukuAppMenu(it) }))
            list.addView(TextView(this).apply {
                text = "Tap to unhide and open · hold for options"
                setTextColor(pal.icon)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, dp(8))
            })
        }

        if (folders.isEmpty() && files.isEmpty() && !inApps && virtualApps.isEmpty() && shizukuApps.isEmpty()) {
            val none = when {
                searching -> "No results for \u201c$q\u201d"
                all.isNotEmpty() -> "No ${listOf("files", "images", "videos", "files")[filter]} here"
                folder == null -> "Your vault is empty"
                else -> "This folder is empty"
            }
            val hint = when {
                searching && all.isNotEmpty() -> "Try another tab"
                searching -> "Try a different word"
                all.isNotEmpty() -> "Try another tab"
                else -> "Tap + to add files, make a folder or hide an app"
            }
            val icon = when {
                searching -> R.drawable.ic_search
                all.isNotEmpty() -> listOf(R.drawable.ic_lock, R.drawable.ic_image, R.drawable.ic_play, R.drawable.ic_file)[filter]
                folder == null -> R.drawable.ic_lock
                else -> R.drawable.ic_folder
            }
            list.addView(vaultEmptyState(icon, none, hint))
            return
        }

        if (folders.isNotEmpty()) list.addView(sectionLabel("Folders", folders.size.toString()))
        for (f in folders) {
            val (row, _) = cardRow()
            row.setOnClickListener {
                selected.clear()
                currentFolder = f
                renderVaultFiles()
            }
            row.setOnLongClickListener { v -> showFolderMenu(v, f); true }
            val isApps = f == VaultManager.APPS_FOLDER
            row.addView(FrameLayout(this).apply {
                background = GradientDrawable().apply { cornerRadius = dpf(14); setColor(if (isApps) pal.eqBg else pal.opBg) }
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(if (isApps) R.drawable.ic_lock else R.drawable.ic_folder)
                    setColorFilter(if (isApps) pal.eqFg else pal.opFg)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(56), dp(56)).apply { rightMargin = dp(14) })
            val extra = if (isApps) VirtualSpace.list(this).size + ShizukuHider.list(this).size else 0
            val count = VaultManager.countFiles(this, f) + extra
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(rowTitle(f))
            col.addView(rowMeta(if (count == 1) "1 item" else "$count items"))
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(iconButton(R.drawable.ic_more_vert, "Folder options") { showFolderMenu(it, f) }, LinearLayout.LayoutParams(dp(44), dp(44)))
            pressScale(row)
            list.addView(row)
        }

        if (folders.isNotEmpty() && files.isNotEmpty()) list.addView(sectionLabel("Files", files.size.toString()))
        for (file in files) {
            val name = VaultManager.displayName(file)
            val mime = VaultManager.mimeFor(name)
            val isImage = mime.startsWith("image/")
            val isVideo = mime.startsWith("video/")
            val isHiddenApp = name.endsWith(HiddenApps.EXT, ignoreCase = true)
            val encrypted = VaultManager.isEncrypted(file)
            val path = file.absolutePath

            val (row, cardBg) = cardRow()
            row.setOnClickListener {
                if (selected.isNotEmpty()) toggleSelect(path) else openVaultFile(file, files)
            }
            row.setOnLongClickListener {
                row.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onFileLongPress(file)
                true
            }

            val thumb = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(-1, -1)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setColorFilter(pal.icon)
                setImageResource(
                    when {
                        isImage -> R.drawable.ic_image
                        isVideo || mime.startsWith("audio/") -> R.drawable.ic_play
                        else -> R.drawable.ic_file
                    }
                )
                tag = path
            }
            val selOverlay = FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dpf(14)
                    setColor((pal.eqBg and 0xFFFFFF) or (0xCC shl 24))
                }
                visibility = View.GONE
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_check)
                    setColorFilter(pal.eqFg)
                }, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
            }
            val thumbBox = FrameLayout(this).apply {
                background = GradientDrawable().apply { cornerRadius = dpf(14); setColor(pal.numBg) }
                clipToOutline = true
                isClickable = true
                setOnClickListener { toggleSelect(path) }
                setOnLongClickListener {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onFileLongPress(file)
                    true
                }
                addView(thumb)
                addView(selOverlay, FrameLayout.LayoutParams(-1, -1))
            }
            row.addView(thumbBox, LinearLayout.LayoutParams(dp(56), dp(56)).apply { rightMargin = dp(14) })
            if (encrypted) {
                thumb.setImageResource(R.drawable.ic_lock)
                thumb.setColorFilter(pal.result)
                thumb.setPadding(dp(14), dp(14), dp(14), dp(14))
            }

            val selTint = (pal.opBg and 0xFFFFFF) or (0xAA shl 24)
            val updateRow = {
                val on = path in selected
                selOverlay.visibility = if (on) View.VISIBLE else View.GONE
                cardBg.setColor(if (on) selTint else pal.histBg)
            }
            rowUpdaters[filter][path] = updateRow
            updateRow()

            val showThumb = { bm: Bitmap ->
                if (thumb.tag == path) {
                    thumb.clearColorFilter()
                    thumb.setPadding(0, 0, 0, 0)
                    thumb.scaleType = ImageView.ScaleType.CENTER_CROP
                    thumb.setImageBitmap(bm)
                    thumb.alpha = 0f
                    thumb.animate().alpha(1f).setDuration(200).start()
                }
            }
            // A video shows the frame at 30 s, taken once and kept on disk (VaultManager.cachedVideoFrame), so it is
            // the same every time. The stored thumbnail is only a stand-in until that frame is ready.
            var randomShown = false
            val applyThumb = { bm: Bitmap -> if (!randomShown) showThumb(bm) }
            if (isVideo && !encrypted) {
                val randomKey = "$path#frame30"
                val cachedRandom = thumbCache[randomKey]
                if (cachedRandom != null) {
                    randomShown = true
                    showThumb(cachedRandom)
                } else {
                    try {
                        randomThumbExecutor.execute {
                            val bm = thumbCache[randomKey] ?: VaultManager.cachedVideoFrame(this@MainActivity, file)
                            if (bm != null) {
                                thumbCache[randomKey] = bm
                                runOnUiThread {
                                    randomShown = true
                                    showThumb(bm)
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            if ((isImage || isVideo) && !encrypted) {
                val key = path + file.lastModified()
                val cached = thumbCache[key]
                if (cached != null) {
                    applyThumb(cached)
                } else {
                    try {
                        thumbExecutor.execute {
                            val bm = VaultManager.readThumb(file)
                            if (bm != null) {
                                thumbCache[key] = bm
                                runOnUiThread { applyThumb(bm) }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            if (encrypted) upgradeInBackground(file, false, applyThumb)
            if (isHiddenApp) {
                HiddenApps.iconFor(this, file)?.let { ic ->
                    thumb.clearColorFilter()
                    thumb.setPadding(dp(6), dp(6), dp(6), dp(6))
                    thumb.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    thumb.setImageBitmap(ic)
                }
            }

            val kind = when {
                isHiddenApp -> "Hidden app"
                isImage -> "Image"
                isVideo -> "Video"
                mime.startsWith("audio/") -> "Audio"
                else -> "File"
            }
            val inFolder = file.parentFile?.name?.takeIf { it in folderNames }
            val detail = if (isHiddenApp) {
                val pkg = HiddenApps.packageOfFast(this, file)
                if (pkg != null && HiddenApps.isInstalled(this, pkg)) "installed right now" else "tap to open"
            } else {
                android.text.format.Formatter.formatShortFileSize(this, file.length())
            }
            val nameCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            nameCol.addView(rowTitle(if (isHiddenApp) name.dropLast(HiddenApps.EXT.length) else name, 2))
            nameCol.addView(rowMeta("$kind · $detail" + (if (inFolder != null) " · in $inFolder" else "")))
            row.addView(nameCol, LinearLayout.LayoutParams(0, -2, 1f))
            if (encrypted) {
                row.addView(ImageView(this).apply {
                    setImageResource(R.drawable.ic_lock)
                    setColorFilter(pal.result)
                    alpha = 0.8f
                }, LinearLayout.LayoutParams(dp(16), dp(16)).apply { leftMargin = dp(8); rightMargin = dp(8) })
            }
            pressScale(row)
            list.addView(row)
        }

        if (animateIn) {
            for (i in 0 until list.childCount) {
                val v = list.getChildAt(i)
                v.alpha = 0f
                v.translationY = dpf(18)
                v.animate().alpha(1f).translationY(0f).setStartDelay(minOf(i, 12) * 24L).setDuration(280)
                    .setInterpolator(chromeIn).start()
            }
        }
    }

    private fun loadHistory(): List<Pair<String, String>> {
        val raw = prefs.getString("entries", "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split("\u0002").mapNotNull {
            val parts = it.split("\u0001")
            if (parts.size == 2) parts[0] to parts[1] else null
        }
    }

    private fun saveHistoryEntry(expr: String, result: String) {
        val entries = (listOf(expr to result) + loadHistory()).take(100)
        val raw = entries.joinToString("\u0002") { "${it.first}\u0001${it.second}" }
        prefs.edit().putString("entries", raw).apply()
    }

    private fun confirmClearHistory() {
        showStyledDialog("Clear history and memory?", null, "Clear", "Dismiss") { clearHistory() }
    }

    private fun clearHistory() {
        prefs.edit().remove("entries").apply()
        renderHistory()
        Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
    }

    private fun renderHistory() {
        historyList.removeAllViews()
        pinBottom = true
        val entries = loadHistory().reversed()
        if (entries.isEmpty()) {
            historyList.addView(TextView(this).apply {
                text = "No history yet"
                setTextColor(pal.icon)
                setPadding(dp(16), dp(24), dp(16), dp(24))
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, -2))
            return
        }
        for ((expr, result) in entries) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(12), dp(24), dp(12))
                isClickable = true
                background = RippleDrawable(ColorStateList.valueOf(pal.ripple), null, ColorDrawable(Color.BLACK))
                setOnClickListener {
                    expression = result
                    previousExpression = expr
                    justEvaluated = true
                    toggleHistory()
                    refresh()
                }
            }

            row.addView(TextView(this).apply {
                text = group(expr) + " ="
                setTextColor(pal.icon)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = displayFont
                gravity = Gravity.END
                setLineSpacing(0f, 1.1f)
            }, LinearLayout.LayoutParams(-1, -2))

            val resultView = TextView(this).apply {
                typeface = displayFont
                gravity = Gravity.END
            }
            val grouped = group(result)
            resultView.text = grouped
            style(resultView, 20, 34, pal.text, grouped)
            row.addView(resultView, LinearLayout.LayoutParams(-1, -2))
            historyList.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        histScroll.post { histScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun copyResult() {
        if (!justEvaluated && cachedRawResult.isEmpty()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val toCopy = (if (isInfiniteResult && cachedRawResult.isNotEmpty()) cachedRawResult else expression)
            .replace(",", "").replace('−', '-')
        cm.setPrimaryClip(ClipData.newPlainText("result", toCopy))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun pasteIntoExpression() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this).toString()
        val sanitized = text.filter { it.isDigit() || it in "+-×÷.%()^!√" }.replace('-', '−')
        if (sanitized.isEmpty()) return
        if (justEvaluated) { expression = ""; justEvaluated = false }
        expression += sanitized
        refresh()
    }

    /**
     * Confirmed "Forgot PIN": play the same red erase / relaunch animation as the guide, wiping the PIN and every
     * vault file as it starts, then relaunch the app as a fresh start.
     */
    private fun resetPinAndRelaunch() {
        if (eraseOverlay != null) return
        pinResetPending = false
        expression = ""
        justEvaluated = false
        // Nothing running in the background may touch (or report on) a vault that no longer exists.
        TransferManager.cancelAndForgetAll()
        finishedJobs.clear()
        viewerCryptHooks.clear()
        dismissTransferCard()
        VaultManager.resetVault(this)
        // The relaunched app is a fresh start, so it shows the How to use guide again.
        prefs.edit().putBoolean("guide_seen", false).apply()

        val decor = window.decorView as ViewGroup
        val loc = IntArray(2)
        val eq = eqButton
        val ox: Float
        val oy: Float
        if (eq != null && eq.isAttachedToWindow) {
            eq.getLocationInWindow(loc)
            ox = loc[0] + eq.width / 2f
            oy = loc[1] + eq.height / 2f
        } else { ox = decor.width / 2f; oy = decor.height * 0.8f }
        val overlay = EraseOverlayView(this, pal.clearFg, pal.bg, keyFont, ox, oy) {
            eraseOverlay = null
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
        }
        eraseOverlay = overlay
        decor.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay.post { overlay.start() }
    }

    private fun onPress(key: String) {
        errorText = null
        if (key != "=" && pinResetPending) pinResetPending = false
        when (key) {
            in "0".."9" -> {
                if (justEvaluated) { expression = ""; justEvaluated = false }
                expression += key
            }
            "." -> {
                if (justEvaluated) { expression = ""; justEvaluated = false }
                val lastNumber = expression.takeLastWhile { it.isDigit() || it == '.' }
                if (!lastNumber.contains('.')) expression += if (lastNumber.isEmpty()) "0." else "."
            }
            "+", "×", "÷", "−" -> {
                justEvaluated = false
                val op = key[0]
                if (expression.isEmpty() || expression.last() == '(') {
                    if (op == '−') expression += "−"
                } else if (expression.last() in operators) {
                    expression = if (expression.length > 1 && expression[expression.length - 2] != '(')
                        expression.dropLast(1) + op
                    else if (op == '−') expression else expression.dropLast(1)
                } else {
                    expression += op
                }
            }
            "%" -> {
                justEvaluated = false
                if (expression.isNotEmpty() && (expression.last().isDigit() || expression.last() in ")%²")) expression += "%"
            }
            "( )" -> {
                justEvaluated = false
                val open = expression.count { it == '(' }
                val close = expression.count { it == ')' }
                val last = expression.lastOrNull()
                val canClose = open > close && last != null && (last.isDigit() || last in ")%²πe!")
                expression += if (canClose) ")" else "("
            }
            "⌫" -> {
                if (justEvaluated) { justEvaluated = false; previousExpression = "" }
                else {
                    val hadOpen = expression.endsWith("(")
                    expression = expression.dropLast(1)
                    if (hadOpen) {
                        for (f in listOf("arcsin", "arccos", "arctan", "asin", "acos", "atan", "sin", "cos", "tan", "ln", "log")) {
                            if (expression.endsWith(f)) { expression = expression.dropLast(f.length); break }
                        }
                    }
                }
            }
            "AC" -> { expression = ""; previousExpression = ""; justEvaluated = false }
            "=" -> {
                if (pinResetPending) {
                    resetPinAndRelaunch()
                    return
                }
                if (changeMode) {
                    if (isValidPin(expression)) {
                        VaultManager.setPin(this, expression)
                        changeMode = false
                        expression = ""
                        justEvaluated = false
                        Toast.makeText(this, "Password changed", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, if (expression == "0000") pinProblem(expression) else "Password must be at least 4 digits", Toast.LENGTH_SHORT).show()
                    }
                    refresh()
                    return
                }
                if (VaultManager.isPinSet(this) && VaultManager.checkPin(this, expression)) {
                    expression = ""
                    justEvaluated = false
                    toggleVault()
                    refresh()
                    return
                }
                if (expression.isNotEmpty() && !justEvaluated) {
                    try {
                        val cleaned = closeParens(trimTrailingOps(expression))
                        val result = Evaluator.formatExpr(cleaned)
                        previousExpression = expression
                        expression = result
                        justEvaluated = true
                        saveHistoryEntry(previousExpression, expression)
                        if (histHeightPx > 0) renderHistory()
                    } catch (e: ArithmeticException) {
                        errorText = e.message ?: "Value too large"
                    } catch (e: Exception) {
                        errorText = "Format error"
                    }
                }
            }
        }
        refresh()
    }

    private fun trimTrailingOps(s: String): String {
        var t = s
        while (t.isNotEmpty() && (t.last() in operators || t.last() == '(')) t = t.dropLast(1)
        return t
    }

    private fun closeParens(s: String): String {
        val missing = s.count { it == '(' } - s.count { it == ')' }
        return if (missing > 0) s + ")".repeat(missing) else s
    }

    private val numberRegex = Regex("(\\d+)(\\.\\d*)?")
    private val plainNumberRegex = Regex("^−?[\\d.]+$")
    private val measurePaint = Paint()

    private fun group(s: String): String =
        numberRegex.replace(s) { m ->
            val int = m.groupValues[1].reversed().chunked(3).joinToString(",").reversed()
            int + m.groupValues[2]
        }

    private fun availWidthFor(view: TextView): Float {
        val w = when {
            view === topView -> topScroll.width
            view === bottomView -> bottomScroll.width
            else -> view.width
        }
        if (w > 0) return w.toFloat()
        return ((if (contentW > 0) contentW else winW()) - dp(48)).toFloat()
    }

    private fun style(view: TextView, minSp: Int, maxSp: Int, color: Int, sampleText: CharSequence) {
        view.setTextColor(color)
        val compact = histHeightPx > 0 && (view === topView || view === bottomView)
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        var cap = (maxSp * uiScale).toInt()
        if (compact) cap = minOf(cap, (40 * uiScale).toInt())
        if (land && view === topView) cap = minOf(cap, (resources.configuration.screenHeightDp * 0.11f).toInt())
        if (land && view === bottomView) cap = minOf(cap, (resources.configuration.screenHeightDp * 0.11f).toInt())
        cap = minOf(cap, 100)
        val maxSp = cap
        val minSp = minOf(minSp, maxSp)
        val availWidth = availWidthFor(view)
        val availHeight = when {
            view === topView -> topScroll.height
            view === bottomView -> bottomScroll.height
            else -> 0
        }
        val paint = measurePaint
        paint.set(view.paint)
        // Fit by width first; if the window is too short for the line, keep shrinking until it fits
        // vertically too (down to 10sp) so text is never clipped in small / landscape windows.
        val floorSp = if (availHeight > 0) minOf(10, minSp) else minSp
        var chosenSp = floorSp
        for (sp in maxSp downTo floorSp step 2) {
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp.toFloat(), resources.displayMetrics)
            val fm = paint.fontMetrics
            val heightOk = availHeight <= 0 || (fm.bottom - fm.top) <= availHeight
            val widthOk = paint.measureText(sampleText, 0, sampleText.length) <= availWidth
            if (heightOk && (widthOk || sp <= minSp)) {
                chosenSp = sp
                break
            }
        }
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, chosenSp.toFloat())
    }

    private fun refresh() {
        scroller.forceFinished(true)
        bottomView.removeCallbacks(flingRunnable)
        pendingFlingVelocity = 0f
        calcGen++
        calcFuture?.cancel(true)
        digitsComputing = false
        scrollOffsetPx = 0f
        bottomScroll.scrollTo(0, 0)
        currentPrecision = 120

        topView.gravity = if (justEvaluated) Gravity.END or Gravity.BOTTOM else Gravity.END or Gravity.CENTER_VERTICAL
        if (justEvaluated) {
            val topStr = group(previousExpression) + " ="
            style(topView, 16, 26, pal.icon, topStr)
            topView.text = topStr
            topScroll.post { topScroll.fullScroll(HorizontalScrollView.FOCUS_RIGHT) }

            val cleanedPrev = closeParens(trimTrailingOps(previousExpression))
            activeEvalExpr = cleanedPrev
            isInfiniteResult = cleanedPrev.isNotEmpty() && try { Evaluator.hasInfiniteDecimals(cleanedPrev) } catch (_: Exception) { false }
            cachedRawResult = if (isInfiniteResult) {
                try { Evaluator.evalBig(cleanedPrev, currentPrecision) }
                catch (e: Exception) {
                    errorText = e.message ?: "Value too large"
                    expression
                }
            } else {
                expression
            }
        } else {
            val groupedExpr = group(expression)
            style(topView, 28, 76, pal.text, groupedExpr)
            val sb = SpannableStringBuilder(groupedExpr)
            sb.append("\u200B")
            cursorSpan.visible = true
            sb.setSpan(cursorSpan, sb.length - 1, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            topView.text = sb
            topScroll.post { topScroll.fullScroll(HorizontalScrollView.FOCUS_RIGHT) }

            preparePreviewState()
        }

        renderBottomResult()
    }

    private fun preparePreviewState() {
        val e = trimTrailingOps(expression)
        if (e.isEmpty() || plainNumberRegex.matches(e)) {
            isInfiniteResult = false
            activeEvalExpr = ""
            cachedRawResult = ""
            return
        }
        try {
            val cleaned = closeParens(e)
            activeEvalExpr = cleaned
            isInfiniteResult = Evaluator.hasInfiniteDecimals(cleaned)
            cachedRawResult = if (isInfiniteResult) {
                Evaluator.evalBig(cleaned, currentPrecision)
            } else {
                Evaluator.formatExpr(cleaned)
            }
        } catch (ex: Exception) {
            isInfiniteResult = false
            cachedRawResult = ""
        }
    }

    private fun renderBottomResult() {
        val rv = bottomView as ResultTextView
        if (pinResetPending) {
            // "Forgot PIN" confirmation, shown in the result line in red (pressing anything but = cancels it).
            val msg = "Press = to erase & reset PIN"
            rv.unpin()
            rv.tabular(false)
            style(bottomView, 16, 26, pal.clearFg, msg)
            bottomView.text = msg
            return
        }
        if (changeMode) {
            val msg = "Enter new password, press ="
            rv.unpin()
            rv.tabular(false)
            style(bottomView, 16, 26, pal.result, msg)
            bottomView.text = msg
            return
        }
        val minSp = if (justEvaluated) 28 else 16
        val maxSp = if (justEvaluated) 64 else 26
        val mainColor = if (justEvaluated) pal.text else pal.eqBg

        if (errorText != null) {
            rv.unpin()
            rv.tabular(false)
            style(bottomView, minSp, maxSp, pal.clearFg, errorText!!)
            bottomView.text = errorText
            return
        }

        if (!isInfiniteResult || !cachedRawResult.contains('.')) {
            rv.unpin()
            rv.tabular(false)
            val grouped = group(cachedRawResult)
            style(bottomView, minSp, maxSp, mainColor, grouped)
            bottomView.text = grouped
            return
        }

        rv.tabular(true)
        bottomView.setTextColor(mainColor)
        bottomView.setTextSize(TypedValue.COMPLEX_UNIT_SP, minSp.toFloat())

        if (fracSource !== cachedRawResult) {
            fracSource = cachedRawResult
            val dot = cachedRawResult.indexOf('.')
            intGroupedCache = group(cachedRawResult.substring(0, dot)) + "."
            // The last digits of a rounded result may still change when more precision arrives; hide them.
            val all = cachedRawResult.substring(dot + 1)
            fracCache = if (all.length > 3) all.substring(0, all.length - 3) else ""
            fullCache = intGroupedCache + fracCache
        }
        val frac = fracCache
        val full = fullCache
        val availWidth = availWidthFor(bottomView)
        val charW = rv.charWidth()
        rv.prepareCounter(full.length)
        val visible = rv.visibleChars(availWidth)

        // Keep a few screens of digits computed ahead of what is on screen, in the background.
        val lookahead = maxOf(400, visible * 6)
        ensureDigits(visible + (scrollOffsetPx / charW).toInt() + lookahead, intGroupedCache.length, frac.length)

        // Everything fits: nothing to scroll.
        if (full.length <= visible) {
            rv.unpin()
            bottomView.text = full
            return
        }

        // Never scroll past the digits that exist yet; more arrive from the background worker.
        val maxOffset = ((full.length - visible - 1) * charW).coerceAtLeast(0f)
        if (scrollOffsetPx > maxOffset) {
            scrollOffsetPx = maxOffset
            if (!scroller.isFinished) {
                // A fling ran into the end of the digits computed so far: carry on when more arrive.
                pendingFlingVelocity = scroller.currVelocity
                scroller.forceFinished(true)
            }
        }

        rv.showWindow(full, cachedRawResult, scrollOffsetPx, availWidth, intGroupedCache.length,
            androidx.core.graphics.ColorUtils.blendARGB(pal.bg, pal.icon, 0.76f))
    }

    /** Computes more digits of the current irrational result off the UI thread (precision doubles each time). */
    private fun ensureDigits(neededFrac: Int, intLen: Int, haveFrac: Int) {
        if (!isInfiniteResult || digitsComputing || activeEvalExpr.isEmpty()) return
        if (haveFrac >= neededFrac || currentPrecision >= Evaluator.MAX_DIGITS) return
        val target = maxOf(neededFrac + intLen + 64, currentPrecision * 2).coerceAtMost(Evaluator.MAX_DIGITS)
        if (target <= currentPrecision) return
        digitsComputing = true
        val gen = calcGen
        val expr = activeEvalExpr
        calcFuture = calcExecutor.submit(Runnable {
            val res = try { Evaluator.evalBig(expr, target) } catch (_: Exception) { null }
            runOnUiThread {
                if (gen != calcGen || isDestroyed) return@runOnUiThread
                digitsComputing = false
                if (res != null && res.contains('.')) {
                    cachedRawResult = res
                    currentPrecision = target
                } else {
                    currentPrecision = Evaluator.MAX_DIGITS // give up quietly instead of retrying forever
                }
                renderBottomResult()
                val v = pendingFlingVelocity
                pendingFlingVelocity = 0f
                if (v > 300f && isInfiniteResult && errorText == null) {
                    scroller.fling(scrollOffsetPx.toInt(), 0, v.toInt(), 0, 0, Int.MAX_VALUE / 4, 0, 0)
                    bottomView.postOnAnimation(flingRunnable)
                }
            }
        })
    }

    /** Handles back inside the app. Returns true if it consumed the press. */
    private fun handleBack(): Boolean {
        when {
            historyVisible -> toggleHistory()
            vaultVisible && fabMenuOpen -> setFabMenu(false)
            vaultVisible && selected.isNotEmpty() -> clearSelection()
            vaultVisible && vaultSearchOpen -> closeVaultSearch()
            vaultVisible -> {
                if (currentFolder != null) {
                    currentFolder = null
                    renderVaultFiles()
                } else {
                    toggleVault()
                }
            }
            else -> return false
        }
        return true
    }

    // Android 13+ (and always when targeting API 36) delivers back through the predictive-back
    // dispatcher; onBackPressed() is only called on older versions.
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!handleBack()) super.onBackPressed()
    }

    @Suppress("DEPRECATION")
    private fun registerBackCallback() {
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                OnBackInvokedCallback { if (!handleBack()) super@MainActivity.onBackPressed() }
            )
        }
    }
}

/**
 * Result line. An ordinary TextView. While an irrational result is shown it holds a short plain-text window of
 * the digits and slides that window by exactly the scrolled distance, so scrolling is pixel-smooth and every
 * glyph is drawn by the normal TextView path. The slide is done with the view's own scroll position, which we
 * pin ourselves: TextView otherwise re-scrolls its text to follow the gravity whenever the text changes.
 * A small fixed "E-n" counter at the right edge shows how many digits have been scrolled into view.
 */
private class ResultTextView(ctx: Context) : TextView(ctx) {
    private var pinned = false
    private var pinX = 0
    private var winSource: String? = null
    private var winFrom = -1
    private var winTo = -1

    private var basePadRight = 0
    private var basePadLeft = 0
    private var ellipsisW = 0
    private var showEllipsis = false
    private var counterReserve = 0
    private var counterText = ""
    private var counterColor = Color.GRAY
    private val counterPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun charWidth(): Float = paint.measureText("0").coerceAtLeast(1f)

    private fun counterSize(): Float = paint.textSize

    private fun styleCounterPaint() {
        counterPaint.typeface = typeface
        counterPaint.textSize = counterSize()
        counterPaint.fontFeatureSettings = "tnum"
        counterPaint.color = counterColor
    }

    /** Room (px) kept free at the right edge for the counter, sized for a number with [totalChars] digits. */
    fun prepareCounter(totalChars: Int) {
        styleCounterPaint()
        val w = counterPaint.measureText("E-" + "9".repeat(totalChars.toString().length))
        counterReserve = Math.ceil((w + charWidth() * 0.4f).toDouble()).toInt()
    }

    private fun rightPad(): Int = if (pinned) basePadRight else paddingRight

    /** How many characters fit across the line (next to the counter). */
    fun visibleChars(availWidth: Float): Int =
        ((availWidth - paddingLeft - rightPad() - counterReserve - 2f) / charWidth()).toInt().coerceAtLeast(1)

    /** Equal-width digits, so the window slides without the text re-flowing. */
    fun tabular(on: Boolean) {
        val f: String? = if (on) "tnum" else null
        if (fontFeatureSettings != f) fontFeatureSettings = f
    }

    override fun scrollTo(x: Int, y: Int) {
        if (pinned) super.scrollTo(pinX, 0) else super.scrollTo(x, y)
    }

    // While pinned the text is wider than the screen; the view itself must stay exactly as wide as the viewport.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (pinned) {
            val vp = (parent as? View)?.width ?: 0
            if (vp > 0) {
                super.onMeasure(View.MeasureSpec.makeMeasureSpec(vp, View.MeasureSpec.EXACTLY), heightMeasureSpec)
                return
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!pinned || counterText.isEmpty()) return
        // The canvas is in scrolled (content) coordinates, so add scrollX to land at a fixed spot on screen.
        styleCounterPaint()
        val fm = counterPaint.fontMetrics
        val baseline = if (counterPaint.textSize == paint.textSize) getBaseline().toFloat()
            else (height - (fm.bottom - fm.top)) / 2f - fm.top
        val w = counterPaint.measureText(counterText)
        val x = scrollX + width - basePadRight - w
        canvas.drawText(counterText, x, baseline, counterPaint)
        if (showEllipsis) {
            val ep = Paint(paint)
            ep.color = currentTextColor
            ep.fontFeatureSettings = null
            canvas.drawText("\u2026", (scrollX + basePadLeft).toFloat(), getBaseline().toFloat(), ep)
        }
    }

    /** Back to ordinary text behaviour (right-aligned, normal padding). */
    fun unpin() {
        if (!pinned) return
        pinned = false
        winSource = null
        winFrom = -1
        winTo = -1
        counterText = ""
        showEllipsis = false
        setPadding(basePadLeft, paddingTop, basePadRight, paddingBottom)
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        scrollTo(0, 0)
        requestLayout()
    }

    /**
     * Shows [full] scrolled left by [offsetPx]. Offset 0 is the normal right-aligned start of the number. The
     * window holds just enough characters to cover the screen plus the one that is sliding in at the right edge.
     * [intLen] is the length of the integer part ("3."), used for the digit counter.
     */
    fun showWindow(full: String, source: String, offsetPx: Float, availWidth: Float, intLen: Int, countColor: Int) {
        if (full.isEmpty()) return
        val p = paint
        val cw = charWidth()
        if (!pinned) basePadLeft = paddingLeft
        ellipsisW = Math.ceil(p.measureText("\u2026").toDouble()).toInt() + Math.round(cw * 0.15f)
        val wantLeft = basePadLeft + ellipsisW
        if (paddingLeft != wantLeft) setPadding(wantLeft, paddingTop, paddingRight, paddingBottom)
        showEllipsis = offsetPx > 0f
        val v = visibleChars(availWidth)
        val k = (offsetPx / cw).toInt()
        var f = offsetPx / cw - k
        var end = v + k + 1
        if (end >= full.length) {
            end = full.length
            if (v + k + 1 > full.length) f = 1f
        }
        val from = (end - (v + 4)).coerceAtLeast(0)
        if (!pinned) {
            basePadRight = paddingRight
            pinned = true
            gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
            requestLayout()
        }
        // The clip edge of the text sits left of the counter.
        val wantRight = basePadRight + counterReserve
        if (paddingRight != wantRight) setPadding(paddingLeft, paddingTop, wantRight, paddingBottom)
        if (source !== winSource || from != winFrom || end != winTo) {
            winSource = source
            winFrom = from
            winTo = end
            text = full.substring(from, end)
        }
        val t = p.measureText(full, from, end)
        val lastW = p.measureText(full, end - 1, end)
        val slide = (1f - f) * lastW
        pinX = Math.round(paddingLeft + paddingRight + t - availWidth - slide)

        // Digits fully in view: the last character only counts once it has finished sliding in.
        val fullyShown = if (f >= 0.999f) end else end - 1
        counterColor = countColor
        counterText = "E-" + (fullyShown - intLen).coerceAtLeast(0)
        scrollTo(pinX, 0)
        invalidate()
    }
}

/** Image view with pinch-to-zoom, drag-to-pan and double-tap zoom. [topInsetPx] keeps the image clear of the top bar. */
private class ZoomImageView(ctx: Context, private val topInsetPx: Int) : ImageView(ctx) {
    private val m = Matrix()
    private val vals = FloatArray(9)
    private val rect = RectF()
    private var minScale = 1f
    private var maxScale = 1f

    private val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            zoomBy(d.scaleFactor, d.focusX, d.focusY)
            return true
        }
    })
    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            m.postTranslate(-dx, -dy)
            clampAndApply()
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val cur = currentScale()
            if (cur > minScale * 1.05f) zoomBy(minScale / cur, e.x, e.y) else zoomBy(2.5f, e.x, e.y)
            return true
        }
    })

    init { scaleType = ScaleType.MATRIX }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        fit()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        fit()
    }

    private fun currentScale(): Float {
        m.getValues(vals)
        return vals[Matrix.MSCALE_X]
    }

    private fun fit() {
        val d = drawable ?: return
        val aw = width.toFloat()
        val ah = (height - topInsetPx).toFloat()
        if (aw <= 0f || ah <= 0f || d.intrinsicWidth <= 0 || d.intrinsicHeight <= 0) return
        val s = minOf(aw / d.intrinsicWidth, ah / d.intrinsicHeight)
        m.reset()
        m.postScale(s, s)
        m.postTranslate((aw - d.intrinsicWidth * s) / 2f, topInsetPx + (ah - d.intrinsicHeight * s) / 2f)
        minScale = s
        maxScale = s * 6f
        imageMatrix = m
    }

    private fun zoomBy(factor: Float, fx: Float, fy: Float) {
        val cur = currentScale()
        if (cur <= 0f) return
        val target = (cur * factor).coerceIn(minScale, maxScale)
        val f = target / cur
        m.postScale(f, f, fx, fy)
        clampAndApply()
    }

    private fun clampAndApply() {
        val d = drawable ?: return
        rect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        m.mapRect(rect)
        val left = 0f
        val top = topInsetPx.toFloat()
        val right = width.toFloat()
        val bottom = height.toFloat()
        var dx = 0f
        var dy = 0f
        dx = if (rect.width() <= right - left) (left + right) / 2f - rect.centerX()
             else if (rect.left > left) left - rect.left
             else if (rect.right < right) right - rect.right else 0f
        dy = if (rect.height() <= bottom - top) (top + bottom) / 2f - rect.centerY()
             else if (rect.top > top) top - rect.top
             else if (rect.bottom < bottom) bottom - rect.bottom else 0f
        m.postTranslate(dx, dy)
        imageMatrix = m
    }

    private var lastX = 0f
    private var resync = false

    private fun atLeftEdge(): Boolean {
        val d = drawable ?: return true
        rect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        m.mapRect(rect)
        return rect.left >= -1f
    }

    private fun atRightEdge(): Boolean {
        val d = drawable ?: return true
        rect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        m.mapRect(rect)
        return rect.right <= width + 1f
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        if (!scaleDetector.isInProgress) gestures.onTouchEvent(ev)
        // Zoomed in or pinching: this view keeps the touch. But when a zoomed image is already at its left / right
        // edge and you keep dragging that way, the pager takes over and swipes to the previous / next file.
        var keep = ev.pointerCount > 1 || currentScale() > minScale * 1.01f
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = ev.x
                resync = false
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> resync = true
            MotionEvent.ACTION_MOVE -> if (ev.pointerCount == 1) {
                if (resync) {
                    resync = false
                } else if (keep) {
                    val dx = ev.x - lastX
                    if ((dx > 0f && atLeftEdge()) || (dx < 0f && atRightEdge())) keep = false
                }
                lastX = ev.x
            }
        }
        parent?.requestDisallowInterceptTouchEvent(keep)
        return true
    }
}
