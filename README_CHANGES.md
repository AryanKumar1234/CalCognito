# Video player changes (match Nosved Player)

Goal: make the vault video player look and behave like Nosved Player.
Project: CalculatorApp (package com.onion.calcognito). Not compiled or run yet, so fix any build errors.

## How to apply
1. Easiest: copy the files from `files/` into the project:
   - `MainActivity.kt`, `MpvEngine.kt`, `NosvedSeekBar.kt` -> `app/src/main/java/com/onion/calcognito/` (overwrite)
   - all `ic_pv_*.xml` -> `app/src/main/res/drawable/`
2. Or apply `player-changes.patch` from the project root with `git apply player-changes.patch`.
3. Build in Android Studio. If something fails, give the error to the AI together with this README.

## What changed (all inside buildPlayerView in MainActivity.kt unless noted)
1. **Two layouts, chosen by orientation** (function `applyChromeMetrics`, re-runs on rotation):
   - Landscape: dock (prev, -10, play, +10, next) on top; timeline row below it (elapsed, seek bar, total; tap total toggles "-remaining"); bottom row: lock capsule left, aspect + rotate capsule right.
   - Portrait: dock centred on screen; bottom has a time pill ("elapsed / total") and speed pill ("1.0x"), the seek bar, then one capsule: decoder, chapters, enhance, aspect, rotate.
2. **Top bar**: back button, title (16sp semibold), info line "720p • H.264 • 9.60 MB", HDR10/HLG badge. Decoder, subtitles, audio, more share one glass capsule (landscape). Portrait capsule has subtitles, audio, more only.
3. **Buttons**: `capsuleButton` now passes the clicked view. Aspect icon changes by mode (Fit, Stretch, Crop, 100%). New Enhance button, blue while any adjustment is non-zero. Chapters button is hidden when there are no chapters; Chapters is also in the More menu (`showChapters()`).
4. **Seek bar** (`NosvedSeekBar.kt`): Nosved's "gapped" slider. 6dp gap each side of a white pill thumb, played = accent, unplayed = white 25%. Track 6->10dp and thumb 18->24dp while dragging, with a spring easing (stiffness 500, damping 0.8). Buffer shading removed for local files.
5. **MpvEngine.kt**: added `videoCodecLabel()` and `hdrLabel()` (reads mpv `video-format`, `video-params/gamma`).
6. **Gesture overlays**: volume panel (left) and brightness panel (right): rounded 56dp panel, icon, 12x150dp vertical bar, percent. Volume bar turns red above 100%. Double-tap / two-finger tap shows a centre 88dp play/pause flash.
7. **New icons** in `res/drawable`: `ic_pv_enhance`, `ic_pv_aspect_fit/stretch/crop/original`, `ic_pv_volume`, `ic_pv_brightness`.

## Not done
- Picture-in-picture (conflicts with the vault's auto-lock).
- 2x speed badge and seek ripples still use the old text pill.
- Nosved's overflow arrow for more than 2 bottom-corner buttons.
- Side-panel colours/sizes are approximate (#E6161B22 panel, #2A3340 border, #DDE3EC icon text).

## Prompt to give the free AI
"Here is my Android project and README_CHANGES.md. Apply the changes described (or the patch), fix any compile errors, and tell me which files you edited. Do not change anything else."

## Update 2 (from the screen recording of Nosved)
- Rotation glitch fix: `MainActivity.onConfigurationChanged` no longer rebuilds the calculator while the viewer is open (it waits until the viewer closes, `rebuildAfterViewer`). `MpvEngine` surfaceChanged now re-applies the aspect mode so mpv redraws at the new size, even when paused.
- Title info line now shows mpv's full codec name like Nosved: "1080p • H.264 / AVC / MPEG-4 AVC / MPEG-4 PART 10 • 2.72 MB" (`MpvEngine.videoCodecLabel`).
- Time labels got a small letter spacing.
- Still missing vs the recording: the big right-side tabbed "More" settings panel (Speed, Gestures, Layout, Appearance, Automation, Info), the side-panel Subtitle settings and Decoder engine dialogs, and the PiP button next to Lock.

## Update 3: theming + side sheets (first part of "pixel match the recording")
- NEW `PlayerTheme.kt`: Material 3 colour roles. Pixel/Android 12+ "dynamic" colours come from the wallpaper (system_accent1 / neutral1 / neutral2 tonal palettes, mapped like Compose's dynamicDarkColorScheme). Off, or older phones: Nosved's default BLUE dark palette. Toggle lives in the More menu ("Dynamic colors"), default On.
- NEW `PlayerSheets.kt`: `NvUi` helpers, `NvSheet.show` (Nosved's right-hand side sheet: 48% width landscape / 85% portrait, 24dp left corners, 50% black scrim, 300ms slide), and `NvSheet.decoder` = exact port of DecoderSideSheet.kt (Hardware / Hardware+ / Software cards, ACTIVE chip).
- `MainActivity.kt`: the HW pill now opens `NvSheet.decoder`; More menu has the Dynamic colors row.
- New icons `ic_pv_memory.xml`, `ic_pv_close.xml`.
- TODO (same pattern, port from Nosved source): PlayerSettingsSideSheet (6 tabs), SubtitleSettingsSideSheet (Tracks/Style/Sync), PlayerInfoSideSheet, Audio/Enhance/Chapters side sheets.

## Update 4: all the sheets (ported from Nosved's Compose source)
New files (copy to app/src/main/java/com/onion/calcognito/):
- `PlayerSettingsSheet.kt`: `PlayerPrefs` (saved settings, Nosved's names/defaults), `NvSwitch` (M3 switch), `NvSlider` (M3 gapped slider with stop dot), `NvSettings.show` = the 6-tab panel (Speed, Gestures, Layout, Appearance, Automation, Info) incl. big editable speed, presets grid, option dialogs. 50% width in landscape, bottom sheet in portrait.
- `PlayerSubtitleSheet.kt`: `NvSubtitles.show` = Tracks / Style / Sync (size, font, background, bold, ASS override, delay, vertical position).
- `PlayerMediaSheets.kt`: `NvAudio` (streams, disable audio, 2x booster, delay), `NvChapters`, `NvEnhance`.
- `ic_nv_*.xml`: 43 icons generated from the Material Design Icons set (close to, not identical with, Google's Material icons).
Changed:
- `MainActivity.kt`: More (...) button opens `NvSettings` (long-press = old quick menu). Subtitles/Audio/Chapters/Enhance buttons open the new sheets. Custom speed (`baseSpeed`, 0.25x-4x). Saved settings that the player applies (`applyPlayerPrefs`): double-tap action + seek length, press & hold on/off + speed, seek and prev/next buttons, backdrop dimming, remaining time, keep screen awake.
- `MpvEngine.kt`: `disableAudio`, `setSubFont`, `setSubBackground`, `setSubOverrideAss`.
Saved but NOT acting yet (no matching feature in the vault player): orientation mode, fullscreen scale default, system navigation buttons, control position, icon size, seekbar style, auto-play next, ambient mode, battery/clock overlay, stats overlay, save brightness, pause on obstruction, two/three-finger action choices, subtitle drag gestures.

## Update 5: vault changes
- Apps folder: the "+" button is gone inside it (hidden apps are added with its own "Hide app" tile); its 3-dot button and long-press menu are removed; files can't be moved into it (`showMoveDialog`).
- "+" menu has a new "Text" option (Folder / Hide app / Text / Upload): asks for a title, creates a .txt note in the current folder (encrypted if "encrypt by default" is on) and opens the existing text editor. `VaultManager.createTextNote` (new), `MainActivity.promptNewTextNote` (new), icon `ic_text_note.xml`.
- Files to copy: MainActivity.kt, VaultManager.kt (new this round) + ic_text_note.xml, on top of everything from updates 1-4.
