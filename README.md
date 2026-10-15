A Google-Calculator-style Android app written in Kotlin with no third-party dependencies. It is a full basic + scientific calculator, and it also contains a hidden, PIN-protected file vault that is opened from the calculator keypad.
Features
Calculator
Basic arithmetic plus a scientific panel (expand-arrow toggle): sin, cos, tan, ln, log, √, x^y, x², n!, π, e, and Deg/Rad mode
High-precision evaluation (BigDecimal) with thousands separators
Implicit multiplication shown as typed (5√2, not 5×√(2))
Pixel-style context-aware percent
Calculation history (history icon, top left) and an overflow menu (deg/rad, clear history)
Long-press the result to copy, long-press the expression to paste
Material You warm colors, light/dark follows the system, adaptive launcher icon
Secure Vault
Hide images, videos, and other files inside the app's private storage; originals are removed from the gallery and file manager
Optional per-file encryption: AES-256-GCM, with a random per-file key wrapped by an Android Keystore key, processed in 64 KB chunks
Folders, filter (all / images / videos / files), sort (A-Z / newest / largest), and restore back to public storage
Encrypted thumbnails, temp files cleared on exit, and screen-capture protection flags
