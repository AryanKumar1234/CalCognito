# Calculator (Android)

A calculator app for Android, written in Kotlin. It has a basic and scientific mode, calculation history, and a hidden PIN-protected vault for private files having the option for SHA-256 encryption with built-in media player.

## Features

- Basic and scientific calculator (sin, cos, tan, ln, log, √, x^y, n!, π, e, Deg/Rad)
- Calculation history, copy and paste by long-press
- Light and dark theme that follows the system
- Hidden vault for photos, videos, and files, with optional AES-256 encryption
- Works offline; the app has no internet permission

## How the vault works

1. Type a PIN of at least 4 digits, then hold `=` for 3 seconds to set it.
2. To enter the vault type the PIN and tap on `=`.
3. To change the PIN, type the existing PIN and hold `=` for 3 seconds, now enter a new PIN tap on `=`.
4. A wrong PIN does nothing.
5. Forgot your PIN? Type `0000` and hold `=` for 3 seconds. The calculator will prompt "Press = again to reset PIN (erases existing files)" — tap `=` again to confirm. This wipes the PIN and every file in the vault, then you set a new PIN as in step 1.
6. Unhide restores a file to the folder it was originally added from, not a generic folder.

## Video player

The vault's video player is drawn to match Nosved Player and runs on libmpv (`app/libs/mpvlib.aar`), so it opens the formats libmpv supports rather than only the ones the phone's codecs handle.

- Glass chrome over the video: back button, title, an info line (`720p • H.264 • 9.60 MB`) and an `HDR10`/`HLG` badge, with decoder, subtitles, audio and more in one glass capsule.
- Two layouts, chosen by orientation and re-applied when the phone is rotated: landscape puts the transport dock (previous chapter, −10 s, play/pause, +10 s, next chapter) above the timeline, with lock and aspect/rotate capsules at the bottom corners; portrait centres the dock and puts a time pill (`elapsed / total`), a speed pill (`1.0x`) and a single capsule (decoder, chapters, enhance, aspect, rotate) at the bottom.
- The seek bar is Nosved's gapped slider: a white pill thumb with a 6 dp gap either side, played track in accent blue, unplayed in translucent white. Tap or drag to seek; the track grows 6 → 10 dp and the thumb 18 → 24 dp while dragging. Tapping the total time toggles `-remaining`.
- Gestures: drag the left half for the volume panel, the right half for brightness (rounded panel, icon, 12 × 150 dp bar, percentage; the volume bar turns red above 100%). Double-tap or a two-finger tap flashes a large play/pause icon in the centre.
- Aspect button cycles Fit, Stretch, Crop and 100% with its icon following the mode, an Enhance button that lights blue while any picture adjustment is non-zero, a Chapters button that only appears when the file has chapters, plus subtitle/audio track pickers, playback speed, loop, mute, frame step, stream info and open-with in the overflow menu.
- The vault's own 56 dp title bar is hidden while a video is playing and comes back when the player closes.

### Settings, sheets and theme

- The overflow (⋯) button opens the tabbed settings sheet: **Speed** (editable value plus presets), **Gestures**, **Layout**, **Appearance**, **Automation** and **Info**. Long-pressing it still opens the old quick menu. Settings the player can honour are applied as you change them — playback speed, double-tap action and seek length, press-and-hold speed, showing the seek / previous / next buttons, backdrop gradients, remaining time and keeping the screen awake.
- Subtitles, Audio, Chapters and Enhance open their own side sheets, ported from Nosved Player:
  - **Subtitles** — tracks, plus Size, Font, Background, Bold, ASS override, Delay and Vertical position. The saved look is re-applied to mpv every time a video opens.
  - **Audio** — streams, disable audio, a 2x volume booster, audio delay.
  - **Chapters** — the chapter list with the current chapter marked.
  - **Enhance** — brightness, contrast, saturation, gamma and hue sliders with Reset.
  - **Decoder** (the pill in the top bar) — Hardware, Hardware+ and Software cards with an ACTIVE chip.
- The player uses Material 3 colour roles. On Android 12 and newer it takes its palette from the wallpaper (the same tonal-palette mapping Compose's `dynamicDarkColorScheme` uses); elsewhere, or when **Dynamic colors** is switched off in the menu, it uses Nosved's blue dark palette.

## The vault's "+" menu

The `+` button offers **Folder**, **Hide app**, **Text** and **Upload**. **Text** asks for a title, creates a `.txt` note in the current folder (encrypted when "encrypt by default" is on) and opens the text editor.

Inside the **Apps** folder the `+` button is hidden, its three-dot and long-press menus are removed, and files cannot be moved into it: hidden apps are added with the folder's own **Hide app** tile. The same folder has an **Engine settings** row with the hidden-app engine's (BlackBox) own switches: Hide root, Gaming mode, Allow screenshots, Use VPN network and Background service; a change applies the next time a hidden app starts.

## Downloads and source

- **mpv build (3.1)** — published as `CalCognito v3.1` (tag `v3.1`), APK asset `app-release.apk`:
  <https://github.com/AryanKumar1234/CalCognito/releases/tag/v3.1>
- **ExoPlayer build (2.0, small)** — published as `CalCognito 2.0 - ExoPlayer (small)` (tag `v2.0-exo`), built from `CalculatorApp-v20.zip`.

Source code:

- `sourcecode.zip` — the whole project as readable files (Kotlin sources, resources, Gradle files, the CI workflow). It contains everything except the 71 MB `app/libs/mpvlib.aar` binary, which GitHub cannot show anyway.
- `CalculatorApp.zip` — the exact tree CI unpacks and builds, including `app/libs/mpvlib.aar`. The Gradle build has no checkout of its own, so CI unzips this file and runs Gradle on the result; this zip is the source of truth for the build.
- `CalculatorApp-v20.zip` — the same app on ExoPlayer (the small 2.0 build).
- `app/libs/README.md` — which libmpv AAR is vendored (and which release must not be used).
