# Isolated dev flavor

The main app uses `com.uzukip.gokapi` and the dev flavor uses
`com.uzukip.gokapi.dev`. Separate Android packages isolate credentials, cached
files, preferences, notification registrations, and installer records. Both
flavors share code but install side by side. The dev launcher has a DEV badge.
This follows Spendr ADR-0009.
