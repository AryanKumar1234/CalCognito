package com.onion.calcognito

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build

/**
 * Colours for the player sheets. Same roles as Material 3's ColorScheme, as used by Nosved Player.
 *
 * Dynamic ("Material You") colours: on Android 12+ they come from the wallpaper through the system tonal palettes,
 * mapped the way Material 3's dynamicDarkColorScheme maps them. Off, or on older phones, the palette is Nosved's
 * factory default (BLUE, dark).
 */
class PlayerScheme(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceVariant: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val outlineVariant: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val background: Int,
    val surface: Int,
    val error: Int = Color.parseColor("#FFB4AB"),
    val errorContainer: Int = Color.parseColor("#93000A"),
    val onErrorContainer: Int = Color.parseColor("#FFDAD6")
)

object PlayerTheme {
    private const val PREFS = "player_theme"
    private const val KEY_DYNAMIC = "dynamic_color"

    fun isDynamic(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DYNAMIC, true)

    fun setDynamic(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DYNAMIC, on).apply()
    }

    val dynamicSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Nosved's factory default dark palette ("Nosved Blue"). */
    private fun blue() = PlayerScheme(
        primary = Color.parseColor("#AEC6FF"),
        onPrimary = Color.parseColor("#002E6E"),
        primaryContainer = Color.parseColor("#004398"),
        onPrimaryContainer = Color.parseColor("#AEC6FF"),
        surfaceContainer = Color.parseColor("#1E2537"),
        surfaceContainerHigh = Color.parseColor("#222C3F"),
        surfaceVariant = Color.parseColor("#1E2537"),
        onSurface = Color.parseColor("#E2E5F0"),
        onSurfaceVariant = Color.parseColor("#8D97B2"),
        outline = Color.parseColor("#3A4660"),
        outlineVariant = Color.parseColor("#263044"),
        secondaryContainer = Color.parseColor("#2D3691"),
        onSecondaryContainer = Color.parseColor("#C5CAE9"),
        tertiaryContainer = Color.parseColor("#00522C"),
        onTertiaryContainer = Color.parseColor("#B9F6CA"),
        background = Color.parseColor("#0E1117"),
        surface = Color.parseColor("#161B27")
    )

    private fun mix(a: Int, b: Int, t: Float): Int {
        val u = 1f - t
        return Color.argb(
            (Color.alpha(a) * u + Color.alpha(b) * t).toInt(),
            (Color.red(a) * u + Color.red(b) * t).toInt(),
            (Color.green(a) * u + Color.green(b) * t).toInt(),
            (Color.blue(a) * u + Color.blue(b) * t).toInt()
        )
    }

    @SuppressLint("NewApi")
    private fun dynamic(ctx: Context): PlayerScheme {
        fun c(id: Int) = ctx.getColor(id)
        val n1_900 = c(android.R.color.system_neutral1_900)   // tone 10
        val n1_800 = c(android.R.color.system_neutral1_800)   // tone 20
        return PlayerScheme(
            primary = c(android.R.color.system_accent1_200),
            onPrimary = c(android.R.color.system_accent1_800),
            primaryContainer = c(android.R.color.system_accent1_700),
            onPrimaryContainer = c(android.R.color.system_accent1_100),
            surfaceContainer = mix(n1_900, n1_800, 0.2f),       // tone 12
            surfaceContainerHigh = mix(n1_900, n1_800, 0.7f),   // tone 17
            surfaceVariant = c(android.R.color.system_neutral2_700),
            onSurface = c(android.R.color.system_neutral1_100),
            onSurfaceVariant = c(android.R.color.system_neutral2_200),
            outline = c(android.R.color.system_neutral2_400),
            outlineVariant = c(android.R.color.system_neutral2_700),
            secondaryContainer = c(android.R.color.system_accent2_700),
            onSecondaryContainer = c(android.R.color.system_accent2_100),
            tertiaryContainer = c(android.R.color.system_accent3_700),
            onTertiaryContainer = c(android.R.color.system_accent3_100),
            background = n1_900,
            surface = n1_900
        )
    }

    fun scheme(ctx: Context): PlayerScheme =
        if (dynamicSupported && isDynamic(ctx)) {
            try { dynamic(ctx) } catch (_: Throwable) { blue() }
        } else blue()
}
