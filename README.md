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
