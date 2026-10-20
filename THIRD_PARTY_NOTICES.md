# Third-party notices

gokapi-android and its desktop app are Copyright (c) 2026 Uzuki-P and are
distributed under the MIT License in [LICENSE](LICENSE). Both apps bundle the
fonts below, which keep their own licenses.

## Google Sans Flex

`app/src/main/res/font/google_sans_flex.ttf`, subset to Latin with the `wght`
and `ROND` axes. The desktop app packages the same file.

- Source: <https://github.com/googlefonts/googlesans-flex>
- Copyright: 2015 The Google Sans Flex Authors
- License: SIL Open Font License 1.1. The full text is in
  [app/src/main/assets/licenses/google_sans_flex_OFL.txt](app/src/main/assets/licenses/google_sans_flex_OFL.txt),
  which both apps ship with the font.

## Material Design Icons

`app/src/main/res/font/mci.ttf` and the glyph map in
`app/src/main/java/com/uzukip/gokapi/ui/components/MciGlyphs.kt`. The desktop
app packages the same font.

- Source: <https://pictogrammers.com/library/mdi/>
- Copyright: Pictogrammers
- License: Pictogrammers Free License, <https://pictogrammers.com/docs/general/license/>

## Libraries

The apps link open-source libraries resolved by Gradle, including AndroidX,
Jetpack Compose, Compose Multiplatform, Kotlin and kotlinx, OkHttp,
MaterialKolor, the UnifiedPush connector, Coil, and JNA. Each keeps its own
license, listed in its published POM. The desktop build scripts adapt the
packaging from the author's lyrics-float and metrolist-desktop projects.
