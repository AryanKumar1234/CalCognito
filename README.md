# Calculator (Android)

A calculator app for Android, written in Kotlin. It has a basic and scientific mode, calculation history, and a hidden PIN-protected vault for private files.

## Features

- Basic and scientific calculator (sin, cos, tan, ln, log, √, x^y, n!, π, e, Deg/Rad)
- Calculation history, copy and paste by long-press
- Light and dark theme that follows the system
- Hidden vault for photos, videos, and files, with optional AES-256 encryption
- Works offline; the app has no internet permission

## How the vault works

1. Type a PIN of at least 4 digits, then hold `=` for 4 seconds to set it.
2. To enter the vault type the PIN and tap on `=`.
3. To change the pin, type the existing PIN and hold `=` for 4 seconds, now enter a new PIN tape on `=`.
4. A wrong PIN does nothing.
