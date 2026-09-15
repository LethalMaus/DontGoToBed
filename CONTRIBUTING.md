# Build and contribute

## Prerequisites

Use JDK 21, the checked-in Gradle wrapper, Android SDK 36, and Xcode with the
required iOS SDK on macOS. Android minimum is 24; iOS minimum is 15.0.
Set `sdk.dir` in ignored `local.properties` as needed. Never commit credentials,
signing files, device logs or local SDK paths.

## Audio

The native players require these MP3 files before running:
`menu`, `search1`, `search2`, `search3`, `search4`, `panic`, `jump`, `hit`, `place`,
`arrow`, `damage_taken`, `drink`, `turn` (all with `.mp3` extensions).
Supply licensed copies/replacements in `.jc/`, or extract the publisher's private
handoff into the checkout to create `.jc/release-private/audio/`. Then run:

```sh
python3 scripts/prepare-local-audio.py
```

The helper prefers the private handoff folder when present. Audio is copied to
ignored app resources. The original Pixabay and Liborio Conti tracks retain their
licences; they are not covered by the code licence. Missing files are a setup error,
not a supported silent mode. Audio credits are available in the game's settings.

## Android

```sh
./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest
```

Install the APK from `composeApp/build/outputs/apk/debug/`. Purchasing is optional;
gameplay runs without RevenueCat configuration. Never embed secret API keys.
Production purchase configuration and upload signing belong in ignored local files
or environment variables. `-PstoreRelease=true` additionally enforces release prerequisites.

## iPhone and iPad

Open `iosApp/iosApp.xcodeproj`. Create ignored `iosApp/Configuration/Local.xcconfig`
with `TEAM_ID=YOUR_APPLE_TEAM_ID` for device signing. Choose your device and the
`Playtest` scheme, then Run. The `iosApp` scheme also uses optimized Release without
a debugger; `Debug` enables LLDB. A changed Kotlin Release framework can take several
minutes to compile. Select the intended Xcode with `DEVELOPER_DIR` for terminal builds.
The build helper uses `JAVA_HOME`, then ignored `.gradle/config.properties`, then JDK 21.

Both platform identifiers are `dev.jamescullimore.dontgotobed`. Fork publishers must
replace identifiers, family artwork/identity, store configuration and support details.

## Validation

Run the Android unit tests above for shared gameplay changes. On macOS also compile
and test iOS; `./gradlew :composeApp:iosSimulatorArm64Test` runs the native test suite.
Two previously recorded native inventory assertions and a native test linker issue
remain unverified; an Android pass is not proof of native test success.

For multiplayer changes test both host directions, terrain edits, turns, reconnects,
backgrounding and player death using the same build on all devices. The scripts
`check-multiplayer.py`, `check-accessibility-android.py`, `measure-idle-rendering.py`
and `record-ios-performance.py` provide optional diagnostics; see their `--help`.
The WebSocket checker requires Python's `websockets` package. Keep outputs in `.jc/`.

## Contributions and release status

Keep changes focused, describe player-visible behaviour and report checks performed.
No gameplay features should be claimed complete based only on installation/startup.
Extended archer play after the skeleton flash fix, physical cross-play, purchase
validation and child-audience release review remain release checks. See [ROADMAP.md](ROADMAP.md).

The public tree intentionally excludes website production and publisher operations.
The private handoff is not a source dependency except for licensed publisher audio.
