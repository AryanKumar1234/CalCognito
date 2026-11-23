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
