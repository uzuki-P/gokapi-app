# Material color and expressive motion

Gokapi uses Compose Material 3 Expressive shapes, typography, and motion.
Settings selects a default teal seed, wallpaper colors on Android 12 and later,
or a custom seed. Older devices use the selected seed as the wallpaper fallback.
All screens use MaterialTheme tokens so appearance changes do not require
per-screen palettes. This adapts Spendr ADR-0006 to Gokapi's persisted settings.
