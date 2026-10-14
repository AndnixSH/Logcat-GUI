# Logcat GUI — Architecture

A standalone desktop Logcat viewer that reproduces Android Studio's Logcat tool window
(toolbar, tabs and splits, device picker, filter language, formatting, settings) without the
IntelliJ Platform.

## 1. Goals and non-goals

**Goals**

- **The AS Logcat experience.** That means AS's actions, settings, formats, and colors, and
  the IDE's look: the Islands Dark theme, the new-UI icons, and the Inter and JetBrains Mono
  fonts.
- **Interoperability with AS.** The `.logcat` export format matches AS's, so files open in
  either app.
- **Small and single-purpose.** No project or IDE is needed, only `adb`.
- **Apache 2.0**, with clean attribution to AOSP and JetBrains.

**Non-goals**

- Not a byte-for-byte port of the AS plugin. §2 explains why.
- No project integration. That rules out `package:mine` in the IDE sense, links from stack
  frames to source, and debugger attach.

## 2. Why re-implement instead of copying the AS module

The AS Logcat module (`tools/adt/idea/logcat` in AOSP) is an IntelliJ plugin. Its core
pieces are tied to the platform:

| AS dependency | What it provides | Why it can't come along cheaply |
|---|---|---|
| IntelliJ `EditorEx` | The log text surface | The editor drags in most of the IntelliJ Platform |
| Grammar-Kit lexer/parser + PSI | The filter language, completion, highlighting | Needs the platform's PSI and language infrastructure |
| `adblib` / `ddmlib` + device provisioner | Devices, streams, process names | Wired through AS services |
| Project system | `package:mine` | No project exists standalone |

So the code is not copied. **The design, data, and look are.** §4 maps each AS class to its
counterpart here. §12 records where the colors, icons, and values were taken from in the
installed IDE.

## 3. Layers

```
┌────────────────────────────────────────────────────────────────────────────────┐
│ Main.kt  Window + dark title bar + dialogs        AppServices: adb, settings,  │
│                                                   DeviceService, notifications │
├────────────────────────────────────────────────────────────────────────────────┤
│ ui/window   LogcatWindow: island, header (title · tabs · + · ⋮), PanelTree      │
│             AppController (PanelHost): import/export, splits, dialogs           │
│             TabsState: tabs → PanelNode tree (LeafNode | SplitNode)             │
├────────────────────────────────────────────────────────────────────────────────┤
│ ui/panel    LogcatPanel: DeviceComboBox · FilterField · ? / OverflowToolbar /   │
│             LogcatView / status line         LogcatPanelState (one per panel)   │
├────────────────────────────────────────────────────────────────────────────────┤
│ ui/components  ActionButton, OverflowToolbar, IdePopup/IdeMenu, IdeDropdown,    │
│                controls        ui/theme  IdeColors, LogColors, TagPalette, ...  │
├───────────────────┬──────────────────┬──────────────────┬──────────────────────┤
│ adb/              │ filter/          │ files/ retrace/  │ settings/            │
│ DeviceService     │ FilterParser     │ LogcatFiles      │ AppSettings          │
│ LogcatStream      │ LogcatFilter     │ ProguardRetracer │ FormattingOptions    │
│ ProcessNameRes.   │ FilterCompletion │                  │ SettingsStore (JSON) │
│ Screen{Capture,   ├──────────────────┴──────────────────┴──────────────────────┤
│  Recorder}, Adb   │ model/ (LogLevel, LogcatMessage)   parser/ (LogcatLongParser)│
└───────────────────┴──────────────────────────────────────────────────────────────┘
```

Dependencies only point downward. Everything under `ui/` reads state on the main thread.

## 4. Android Studio → Logcat GUI

AS class names are approximate and change between AS releases.

| Android Studio (AOSP `logcat/`) | Logcat GUI |
|---|---|
| `LogcatMessage`, `LogcatHeader`, `LogLevel` | `model/` (same fields, so the `.logcat` JSON matches) |
| `LogcatMainPanel` + `EditorEx` | `LogcatPanel` + `LogcatView` (virtualized `LazyColumn`) |
| Left toolbar actions (`ClearLogcatAction`, `PauseLogcatAction`, `RestartOrReloadLogcatAction`, `LogcatScrollToTheEndToolbarAction`, `LogcatToggleUseSoftWrapsToolbarAction`, `ImportLogcatAction`, `SaveLogcatAction`, `LogcatFormatAction`, split actions, `SetProguardMappingAction`, `ScreenshotAction`, `ScreenRecorderAction`) | `toolbarItems()` in `LogcatPanel.kt`, laid out by `OverflowToolbar` |
| Splitting tabs (`SplittingTabsToolWindowFactory`) | `TabsState` + `PanelNode` + `SplitPane` |
| `DeviceComboBox` + device provisioner | `DeviceComboBox` + `DeviceService` (`adb devices -l` + `getprop`) |
| `ProcessNameMonitor` | `ProcessNameResolver` (`ps -A -o PID,NAME`) |
| `LogcatFilterParser` (Grammar-Kit) and completion | `FilterParser` (recursive descent) and `FilterCompletion` |
| `MessageFormatter`, `FormattingOptions`, `AndroidLogcatFormattingOptions` presets | `MessageFormatter`, `FormattingOptions.STANDARD` / `COMPACT` (same values) |
| `LogcatFormatDialog` ("Modify Views") | Settings → Logcat Format |
| `AndroidLogcatSettings` + `LogcatApplicationSettingsConfigurable` | `LogcatSettings` + Settings → Logcat |
| `LogcatColorSettingsPage` + `LogcatColorSchemeDarcula.xml` | `LogcatColorScheme` + Settings → Color Scheme |
| `LogcatColors.getTagColor` + `ColorPaletteManager` | `TagPalette` (same palette, same index math) |
| `MessageBacklog` (cycle buffer in bytes) | the buffer in `LogcatPanelState` (sized in KB from settings) |
| `LogcatFileIo` (Gson JSON) | `LogcatFiles` (same JSON layout; plus threadtime, bugreport and Firebase text import) |
| `ProguardMessageRewriter` (R8 `RetraceCommand`) | `ProguardRetracer` (same R8 API) |

## 5. Data flow

1. **Startup.** `AppServices` locates adb (`ADB_PATH` → `ANDROID_HOME` / `ANDROID_SDK_ROOT`
   → `PATH` → default SDK), loads settings, and starts **`DeviceService`**.
2. **Devices.** One poller for the whole app.
   - It runs `adb devices -l` every 2 s and reads each new online device's name and
     version once, in a single `getprop` call. The API level comes from
     `ro.build.version.sdk_full` (e.g. `36.1`), falling back to `ro.build.version.sdk`.
   - Devices that disappear stay in the list as `DISCONNECTED`, shown as `[OFFLINE]`, as in AS.
   - Every poll increments `polls`, which panels use as a heartbeat.
3. **Panels.** Each panel owns its source, either a device or an imported file.
   - A device panel with no device chosen yet picks the first online one.
   - On a heartbeat, if its device is online and no stream is running, it restarts
     `LogcatStream` with `-T <last timestamp + 1 ms>`, so a reconnect doesn't replay the
     device buffer.
4. **Stream.** `LogcatStream` runs `adb -s <serial> logcat -v long -v epoch` in a
   `callbackFlow` and parses the output with `LogcatLongParser`.
   - `ProcessNameResolver` fills in process names. The app id is the process name before any
     `:` suffix.
   - Cancelling the flow destroys the process.
   - `Adb` tracks long-running processes and kills them on exit, so no `adb logcat` is
     left orphaned.
5. **Ingest** (main thread). While paused, messages go to a pending queue, which is bounded
   by the buffer size and flushed on Resume. Otherwise each message is appended to the
   buffer, added to `visible` if it passes the filter and ignore lists, and the buffer is
   trimmed (§9).
6. **Filter.** The text field debounces input for 150 ms, then compiles the query with the
   **Cc** setting. On success it rebuilds `visible`; on a syntax error the previous filter
   stays active and the error shows in the status line. Pressing Enter or leaving the field
   commits the filter to history and to the most-recently-used setting.
7. **Render.** `LogcatView` follows the tail while it is at the bottom. Scrolling up or
   clicking a line pauses following, as AS does.

## 6. Threading

| Work | Where |
|---|---|
| `adb` processes, parsing, `getprop` / `ps`, file import/export, mapping load | `Dispatchers.IO` |
| All Compose state: panels, tabs, devices, settings, notifications | main (Swing EDT) |
| Settings file writes | a single background writer thread, in order |

Panels use their own `SupervisorJob` scope, so a failing panel can't affect the others.
`dispose()` cancels the scope, which kills that panel's stream.

## 7. Wire format

The app reads `adb logcat -v long -v epoch`:

```
[ 1760342096.123  1234: 1250 I/ActivityManager ]
message line 1
message line 2

```

A multi-line body stays one message. A message ends at a blank line or at the next header,
and any pending message is flushed at EOF.

## 8. Filter language

```
query    := orExpr
orExpr   := andExpr ( '|' andExpr )*
andExpr  := factor ( ['&'] factor )*      -- juxtaposition or & = AND
factor   := '(' orExpr ')' | term
term     := ['-' | '!'] [ key op ] value
op       := ':'  (contains) | '=:' (exact) | '~:' (regex)
```

| Key | Matches |
|---|---|
| *(none)*, `message:` / `msg:` | message |
| `tag:` | tag |
| `package:` / `app:` | app id (`package:mine` matches any package-like name) |
| `process:` | process name |
| `line:` | tag or message |
| `level:` | at or above a level (`V D I W E A` or a name) |
| `age:` | within a duration (`30s`, `5m`, `2h`, `1d`) |
| `is:crash`, `is:stacktrace` | crash reports; messages with Java stack frames |
| `pid:`, `tid:` | exact id |

Text matching is case-insensitive unless **Cc** is on. An unknown `key:` falls back to a
message search on the whole term. **Completion** (`FilterCompletion`) offers keys, then
values: recently seen tags, packages and processes, levels, `is:` kinds, and ages. With
"Add filters from history to autocomplete" on, it also offers history entries.

## 9. Buffering and performance

- **Cycle buffer.** It is sized in KB, as AS's is. Each message counts as its text plus a
  fixed header estimate. When the buffer is full, it is trimmed to 90% in one batch, so
  `visible` is shifted only occasionally. `visible` is an in-order subsequence of the buffer,
  so evicted visible messages are always a prefix of it.
- **Imported files** are never trimmed.
- **Pause** queues incoming messages under the same limit.
- **Rendering.** `LazyColumn` composes only the rows on screen. Formatted header and body
  strings are remembered per message.

## 10. Rendering notes

- **Header and body are separate `Text`s.** The header has a minimum width measured from
  JetBrains Mono, so columns align, and soft-wrapped or multi-line messages stay in the
  message column.
- **Soft-wrap off.** As in the AS editor, lines keep their length and the view scrolls
  horizontally. The content width comes from the widest visible line.
- **Soft-wrap on, panel too narrow.** If the panel can't fit the header plus about 40
  message characters, whole lines wrap instead.
- **The level badge is padded with non-breaking spaces.** Skia leaves trailing breakable
  whitespace out of a line's measured width.
- **Tabs are expanded to four spaces.** Skia has no tab stops and draws `\t` as a missing
  glyph.
- **`LineHeightStyle.Trim.None`** gives uniform line spacing.
- **Popups render in the window's own layer.** That is what makes the offscreen snapshots
  (§13) possible.

## 11. Files, settings, devices

- **`.logcat` export.** The JSON is AS's Gson layout, with 2-space indent, `metadata.device`
  as `{"physicalDevice"|"emulatorDevice": {…}}`, and timestamps as `{seconds, nanos}`.
  `LogcatFilesTest` checks both directions against AS's structure.
- **Settings file.** `settings.json` lives in the per-user config directory. Unknown keys
  are ignored and missing ones take defaults, so older and newer versions can share it.
  AS's default filter `package:mine` is replaced with an empty default, because there is no
  project.
- **Screen tools.**
  - Screenshots use `exec-out screencap -p`.
  - Recordings run `screenrecord` with the chosen bit rate (and `show_touches` when asked).
    They are stopped with SIGINT (`pkill -INT`, falling back to `kill -2 $(pidof …)`) so
    the MP4 is finalized, then pulled and deleted from the device.
  - These commands were checked on a real Android 9 device.

## 12. Theme sources (re-syncing with a newer IDE)

Everything visual was read from the installed Android Studio, not approximated. To re-sync
with a newer version, extract the same files again.

| What | Source in the AS install | Used in |
|---|---|---|
| UI colors | `lib/intellij.platform.ide.impl.jar` → `themes/islands/ManyIslandsDark.theme.json` (palette + semantic tokens) | `IdeColors` (token names in comments) |
| Editor colors | `themes/expUI/expUI_darkScheme.xml` ("Dark", parent Darcula) | `IdeColors.Editor*` |
| Logcat level colors | `plugins/android/lib/android.jar` → `colorSchemes/LogcatColorSchemeDarcula.xml` | `LogcatColorScheme` defaults |
| Tag palette | `android.jar` → `palette/logcat-tags-palette.json`; index math from `ColorPaletteManager` | `TagPalette` |
| Icons | `lib/intellij.platform.ide.jar` → `expui/**_dark.svg` (resolved via `PlatformIconMappings.json`); `plugins/android/lib/artwork.jar` → `studio/icons/**_dark.svg` | `src/main/resources/icons` |
| Fonts | `jbr/lib/fonts/Inter-*.otf`, `JetBrainsMono-*.ttf` | `src/main/resources/fonts` |
| Settings, labels, presets | `android.jar` → `messages/LogcatBundle.properties`; `AndroidLogcatSettings`, `AndroidLogcatFormattingOptions` | `settings/`, dialogs |

The values were cross-checked by sampling pixels from AS screenshots. For example, tag
`Bundle` renders in `#D28546`, which `MessageFormatterTest` asserts.

## 13. Verification

- **Unit tests** (`./gradlew test`) cover:
  - the parser and filter language, including regex, exact match, `age:`, `is:`, and case
    sensitivity;
  - completion;
  - the `.logcat` round trip, AS import, and threadtime/uid/Firebase text parsing;
  - formatter column widths and presets;
  - tag colors;
  - settings persistence;
  - adb output parsing;
  - R8 retrace against a real mapping.
- **UI snapshots** (`./gradlew uiSnapshots`) render real composables offscreen with
  `ImageComposeScene`, using fake devices and sample logs. They simulate clicks and keys to
  open the device popup, the toolbar menus and overflow, completion, history, every
  settings page, About, the options menu, and a split window in both wrap modes. The output
  goes to `build/ui-snapshots/`. No device or visible window is needed.

## 14. Roadmap

| Item | Notes |
|---|---|
| Message context menu | AS's Copy, Ignore tag/app, Fold lines like this, Force-stop/Kill/Crash app |
| Find in log (Ctrl+F) | Plus highlighting of matches |
| "(Show original)" for retraced traces | AS shows a link to the obfuscated trace |
| Restore tabs, splits and filters on restart | AS restores Logcat panels per project |
| Filter syntax highlighting | AS colors keys and values (`LOGCAT_FILTER_*` colors) |
| `package:mine` | Let users pick their app ids, since there is no project to read them from |
| `adb track-devices` | Replace polling with the adb host protocol |

## 15. License rules for borrowing AS code

AOSP's Android Studio code and the IntelliJ Platform are Apache 2.0, the same license as this
project. Porting is allowed as long as you:

1. **Keep the original header.** That is `Copyright (C) <year> The Android Open Source
   Project`, or JetBrains' header for platform code.
2. **Mark the file as modified.** Add a line such as
   `Modified for Logcat GUI: removed IntelliJ Platform dependencies.`
3. **Keep NOTICE current.**
4. **Leave out branding.** No Android Studio name or logos in the product. The bundled icons
   are generic action and device icons, each with its original header.
