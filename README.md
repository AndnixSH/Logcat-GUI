# Logcat GUI

A standalone desktop Logcat viewer with the look and workflow of Android Studio's Logcat tool
window. You don't need Android Studio, an IDE, or a project. You only need `adb`.

The colors, icons, fonts, and settings come from Android Studio itself:

- **UI theme:** the IntelliJ "Islands Dark" theme.
- **Log colors:** the "Android Logcat" color scheme.
- **Tag colors:** Android Studio's tag palette and color-assignment algorithm.
- **Icons:** the new-UI icons.
- **Fonts:** Inter for the UI and JetBrains Mono for the log.

Built with Kotlin and Compose Multiplatform (Desktop). Licensed under Apache 2.0.

## Features

- **Toolbar.** Android Studio's Logcat actions, in the same order:

  | Action | Notes |
  |---|---|
  | Clear | Also clears the device buffer (`logcat -c`) |
  | Pause / Resume | Messages that arrive while paused are buffered |
  | Restart | Reloads the log; for an imported file, rereads it |
  | Scroll to the End | |
  | Soft-Wrap | |
  | Import / Export | |
  | Format | Standard / Compact / Modify Views |
  | Split Panels | |
  | Load ProGuard Mappings | |
  | Take Screenshot | |
  | Record Screen | |

  It leaves out "Up/Down the Stack Trace". Actions that don't fit move into a `⋮` overflow menu.
- **Tabs and splits.**
  - Add tabs with `+`.
  - Rename a tab by double-clicking it.
  - Close a tab with its `×` or a middle-click; right-click for more.
  - Split any panel right or down, and drag the divider to resize.
- **Device picker.** This works like Android Studio's:
  - Names come from the device properties, e.g. `samsung SM-X710 (serial)  Android 16, API 36.1`.
  - Devices that disconnect stay in the list as `[OFFLINE]`.
  - Each panel follows its own device and resumes after a reconnect without duplicating lines.
- **Filter box.**
  - Android Studio's query language, with `-`, `|`, `&`, `( )`, `=:` (exact match), `~:` (regex), `age:`, `is:crash` and `is:stacktrace`.
  - **Ctrl+Space** completion for keys, tags, packages, processes and levels.
  - Filter history with favorites, a **Cc** button for case-sensitive matching, and a **?** button for a syntax cheat sheet.
- **Views.** Standard and Compact views with every "Modify Views" option. Process names are resolved from pids.
- **Import / Export.**
  - Exports Android Studio's `.logcat` format, so files open in either app.
  - Imports `.logcat` files, plain `adb logcat -v threadtime` text (also with `-v uid`), bugreport text and Firebase Test Lab logs.
- **ProGuard / R8.** Load a `mapping.txt` and stack traces are de-obfuscated with R8's retrace, the same way Android Studio does it.
- **Screenshot and screen recording.**
  - Screenshots can be copied to the clipboard or saved as PNG.
  - Recordings have a bit-rate setting, an optional show-taps overlay, and save as MP4.
- **Settings** (⋮ → Settings…, or **Ctrl+Alt+S**). These are Android Studio's Logcat settings:

  | Page | Settings |
  |---|---|
  | Logcat | Cycle buffer size (KB), default filter for new windows, most-recently-used filter as default, history in autocomplete, ignore tags, ignore apps, font size override |
  | Logcat Format | Each column of the Standard and Compact views, the default view, and restore defaults, with a live preview |
  | Color Scheme | The indicator and message colors for each level |

  Settings are saved in `%APPDATA%\LogcatGUI\settings.json` on Windows, `~/Library/Application Support/LogcatGUI` on macOS, and `~/.config/logcat-gui` on Linux.
- **About** (⋮ → About Logcat GUI). Shows the version, runtime, adb path and version, the settings file location, and the acknowledgements.

## Platforms

| OS | x64 | arm64 | Installer |
|---|---|---|---|
| Windows 10/11 | ✓ | — | MSI |
| macOS 12+ | ✓ (Intel) | ✓ (Apple silicon) | DMG |
| Linux | ✓ | ✓ | DEB, RPM |

The app is the same everywhere. Platform-specific behavior:

- **macOS:**
  - ⌘, opens Settings.
  - The app menu's About and Settings items work.
  - The title bar is dark.
  - adb is also found in Homebrew's folders and the default SDK location, because apps
    started from Finder don't see your shell's `PATH`.
- **Linux:** file pickers use the GTK dialog, and "Open" / "Show in folder" fall back to
  `xdg-open`.
- **Windows:** the title bar takes the IDE's dark colors.

## Requirements

- JDK 21 (the Gradle toolchain is set to 21)
- `adb`, found in this order:
  1. `ADB_PATH`
  2. `ANDROID_HOME/platform-tools` or `ANDROID_SDK_ROOT/platform-tools`
  3. `PATH`
  4. the default SDK location (`%LOCALAPPDATA%\Android\Sdk`, `~/Library/Android/sdk`,
     `~/Android/Sdk`)
  5. package-manager locations: Homebrew's `/opt/homebrew/bin` and `/usr/local/bin`, and
     on Linux `/usr/bin`, `/usr/lib/android-sdk`, and `/opt/android-sdk`

## Run

```bash
./gradlew run
```

On Windows use `gradlew.bat run`.

## Test

```bash
./gradlew test
```

To check the UI without a device, render the main UI states (popups, menus, dialogs, a split
window) to `build/ui-snapshots/*.png`:

```bash
./gradlew uiSnapshots
```

## Package a native installer

Installers are built with `jpackage`, which only targets the OS and CPU it runs on. To get a
macOS arm64 DMG, build on an Apple-silicon Mac; to get a Linux arm64 DEB, build on an arm64
Linux machine.

| On | Command | Output in `build/compose/binaries/main/` |
|---|---|---|
| Windows | `gradlew.bat packageMsi` | `msi/LogcatGUI-1.0.0.msi` |
| macOS | `./gradlew packageDmg` | `dmg/LogcatGUI-1.0.0.dmg` |
| Linux | `./gradlew packageDeb packageRpm` | `deb/`, `rpm/` (needs `fakeroot` and `rpm`) |
| any | `./gradlew createDistributable` | `app/`, a runnable app folder with a bundled Java runtime |

Every installer bundles a trimmed Java runtime, so users don't need Java.

**CI builds are portable.** [.github/workflows/build.yml](.github/workflows/build.yml) tests
on five GitHub runners (Windows x64, macOS arm64, macOS x64, Linux x64, Linux arm64) and
uploads one portable archive per platform: a `.zip` for Windows and macOS, a `.tar.gz` for
Linux. Unpack it and run `LogcatGUI.exe`, `LogcatGUI.app`, or `bin/LogcatGUI`; nothing is
installed. Pushing a `v*` tag (e.g. `git tag v1.0.0 && git push --tags`) publishes the
archives as a GitHub release. The installer tasks above still work locally.

**Download warnings.** The executables are not code-signed, so browsers and Windows
SmartScreen may warn about or block the downloads. That is a reputation check on unsigned,
rarely downloaded files, and only code signing removes it.

**macOS signing.** The DMG is not signed or notarized. Gatekeeper blocks it the first time;
right-click the app and choose **Open**, or run `xattr -dr com.apple.quarantine "/Applications/Logcat GUI.app"`.
For signed builds, add a Developer ID to the `macOS { signing { … } }` block in
`build.gradle.kts`.

**Icons.** `packaging/icons/app.{ico,icns,png}` are generated from `icons/app-icon.svg`.
After changing the SVG, regenerate them:

```bash
./gradlew generateIcons
```

## Filter cheat sheet

| Query | Shows |
|---|---|
| `timeout` | messages containing "timeout" |
| `tag:OkHttp` | tag contains "OkHttp" (also `package:`, `process:`, `message:`, `line:`) |
| `tag=:OkHttp` | tag is exactly "OkHttp" |
| `tag~:^Net.*` | tag matches a regular expression |
| `level:warn` | Warn and above |
| `age:5m` | logged in the last 5 minutes (`s`, `m`, `h`, `d`) |
| `is:crash` | Java and native crashes (`is:stacktrace`: messages with stack frames) |
| `-tag:Choreographer` | everything except that tag |
| `(tag:A \| tag:B) level:error` | errors from either tag |

A space or `&` means AND, `|` means OR, and `-` or `!` negates a term. Matching ignores case
unless **Cc** is on. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) §8 has the full grammar.

## Keyboard

| Keys | Action |
|---|---|
| Ctrl+Space | Filter completion |
| Enter | Apply the filter and save it to history |
| Up / Down, Enter, Esc | Navigate, choose, and close popups |
| Ctrl+Alt+S (⌘, on macOS) | Settings |
| Double-click a tab | Rename it |
| Middle-click a tab | Close it |

## Project layout

```
src/main/kotlin/dev/logcatgui/
├── Main.kt, AppServices.kt  window, services, notifications, build info
├── model/                   LogLevel, LogcatMessage / LogcatHeader
├── parser/                  LogcatLongParser  (adb logcat -v long -v epoch)
├── filter/                  FilterParser, LogcatFilter, FilterCompletion
├── adb/                     Adb, Device, DeviceService, LogcatStream, ProcessNameResolver,
│                            ScreenCapture, ScreenRecorder
├── files/                   LogcatFiles  (.logcat JSON, threadtime/bugreport import)
├── retrace/                 ProguardRetracer  (R8)
├── settings/                AppSettings, FormattingOptions, SettingsStore
├── platform/                OS detection, file dialogs, clipboard, open/reveal, dark title bar (JNA)
└── ui/
    ├── theme/               IdeColors (Islands Dark), LogColors + TagPalette, fonts, icons
    ├── components/          ActionButton, OverflowToolbar, IdePopup/IdeMenu, controls
    ├── panel/               LogcatPanelState, LogcatPanel, DeviceComboBox, FilterField,
    │                        LogcatView, MessageFormatter
    ├── window/              AppController, TabsState (tabs + split tree), LogcatWindow
    └── dialogs/             Settings, About, Screenshot, Screen Recorder
```

[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) covers the design, how each Android Studio
concept maps to this code, the theme sources, and the license rules for porting code from AOSP.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE). The bundled fonts use the SIL
Open Font License 1.1 ([licenses/OFL-1.1.txt](licenses/OFL-1.1.txt)).

The design is modeled on Android Studio's Logcat (AOSP, Apache 2.0). This project isn't
affiliated with Google or JetBrains. "Android" and "Android Studio" are trademarks of
Google LLC.
