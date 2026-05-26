# Music.axs

Music player that mimics default Samsung Music app... except with fixed/added things that I wanted in there and removed things that I didn't.

<div align=center>

<p align=center>
  <a href="https://apt.izzysoft.de/packages/dev.pgaxis.musicaxs"><img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroidButtonGreyBorder_nofont.png" height="80" alt="Get it at IzzyOnDroid"></a>
</p>

<p align=center>
  <a href="https://apt.izzysoft.de/packages/dev.pgaxis.musicaxs"><img src="https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/dev.pgaxis.musicaxs&label=IzzyOnDroid"/></a>
  <a href="https://apt.izzysoft.de/packages/dev.pgaxis.musicaxs"><img src="https://img.shields.io/badge/dynamic/json?url=https://dlstats.izzyondroid.org/iod-stats-collector/stats/basic/yearly/rolling.json&query=$.['dev.pgaxis.musicaxs']&label=IzzyOnDroid%20yearly%20downloads"/></a>
</p>

<p align=center>
  <a href="https://shields.rbtlog.dev/dev.pgaxis.musicaxs"><img src="https://shields.rbtlog.dev/simple/dev.pgaxis.musicaxs"/></a>
</p>

</div>

## Features

- Album art extraction for even embedded album art
- Playlist creation, import and export
- **Favourite** tag for songs and playlists
- Dynamic playlists: **Recently Added**, **Recently Played**, **Most Played**
- Metadata editing
- **Repeat**, **Repeat once** and **Don't repeat** modes
- Shuffle mode

## Building

### 1. Requirements

- Android Studio
- Android SDK 26+
- JDK 17+

### 2. Steps

1. Clone this repository
2. Open in Android Studio
3. Sync Gradle
4. Build → Generate Signed APK (or run directly on a device)

## Dependencies

- [Media3](https://github.com/androidx/media) - media playback
- [Coil](https://github.com/coil-kt/coil) — image loading
- [Gson](https://github.com/google/gson) — JSON serialization
- [AXS](https://github.com/PGAxis/AxsFileFormat) - Settings handling
- [Jetpack Compose](https://developer.android.com/jetpack/compose) — UI
- [Reorderable](https://github.com/Calvin-LL/Reorderable) - song queue reordering
- [Jaudiotagger](https://github.com/Kaned1as/jaudiotagger) - metadata editing

## License

GNU GPL v3 License — see [LICENSE](LICENSE) for details.

## Contact

- Issues: [Issue page](https://github.com/PGAxis/MusicPlayer/issues)
- Email: pgaxis.dev@gmail.com
