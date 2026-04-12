# DergPlayerAndroid

A full-screen retro Terminal User Interface (TUI) media player for Android.

## Features

- **Media Playback**: Powered by Android Media3 (ExoPlayer).
- **YouTube Integration**: Uses YouTube Data API and NewPipe Extractor for media discovery.
- **Permanent TUI Design**: Unique terminal aesthetic with monospaced typography, retro color schemes, and ASCII-style UI elements.
- **Dynamic Themes**: Multiple color schemes including Matrix Green, Amber, Cyberpunk, and Dynamic (based on artwork).
- **Lightweight UI**: Built entirely with Jetpack Compose using box-drawing characters for layout.

## Technical Stack

- **UI Framework**: Jetpack Compose
- **Typography**: JetBrains Mono with font ligatures (via Google Fonts)
- **Language**: Kotlin
- **Media**: Android Media3 (ExoPlayer)
- **Networking**: OkHttp, Retrofit
- **Image Loading**: Coil (used for metadata and dynamic coloring)
- **Extractors**: NewPipe Extractor

## TUI Design Principles

The application follows a unique retro Terminal User Interface (TUI) design. For more details on the design principles, see [TUI_DESIGN.md](TUI_DESIGN.md).

## Setup

1. Open the project in Android Studio.
2. Sync the project with Gradle files.
3. Build and run the `app` module.

## Dependencies

The project uses several libraries, including:
- `androidx.media3`
- `com.google.apis:google-api-services-youtube`
- `com.github.TeamNewPipe:NewPipeExtractor`
- `io.coil-kt:coil-compose`
- `com.squareup.retrofit2:retrofit`

## License

This project is licensed under the GNU General Public License v3.0 - see the 
[LICENSE](LICENSE.md) file for details.
