# widget-steps-weekly-average

An Android home-screen widget showing your **average steps per day over the last 7
complete days**, measured against a daily goal. Data comes from Health Connect.

```
        ╭───────────╮
      ╭─┘           └─╮
     │    7-day avg    │       ring fills toward the goal;
     │      14.1k      │       past 100% a second lap is
     │      of 10k     │       drawn over a faded first one
      ╰─┐           ┌─╯
        ╰───────────╯
```

## What it does

* Reads daily step totals from Health Connect (read-only, step counts only).
* Averages the **last 7 complete days, ending yesterday**.
* Draws that average as a ring filling toward your daily goal.
* Goal is editable in the app and saved on device; defaults to 10,000.
* Refreshes every 30 minutes via WorkManager; nothing leaves the device.

## Layout

| Module | What lives there |
| --- | --- |
| `core` | Pure Kotlin/JVM. Rolling windows, the averaging rules, goal progress. No Android imports, so it runs in a plain JVM test. |
| `app` | The Android side: Health Connect access, the Glance widget, goal storage, the setup screen. |

The split is deliberate — all the arithmetic that can be wrong is in `core`, where it
is tested directly without an emulator.

## The two decisions worth knowing about

### Why today is excluded

The window is the last 7 **complete** days, ending yesterday. Today is left out
entirely.

Including a day in progress means it contributes a partial step count while still
occupying a whole slot in the divisor. The average would sag every morning and creep
back up by evening — the same number meaning different things at 8am and 8pm. Ending
at yesterday gives a figure that is stable all day and moves once, at midnight.

The cost: a big walk today does not show up until tomorrow. That is the deliberate
trade — this widget answers "how am I doing lately", not "how am I doing right now".

### Rolling window, not a calendar week

A Mon–Sun average resets every Monday, so on a Monday morning it reports one day of
data as if it were a week. A rolling window always covers the same amount of time, so
two readings a day apart are actually comparable.

### Why the ring has two laps

A progress indicator clamped at full stops carrying information the moment the goal
is beaten: 101% and 300% draw the same picture. Once the average passes the goal the
first lap fades and a second one is drawn over it, so how far past is still legible.
It saturates at 200%, beyond which the distinction costs more clutter than it is
worth.

Type is sized as a share of the widget's shorter side rather than in fixed `sp`, so
the face fills the circle at any size the user drags it to. The ratios are bounded by
what the widest realistic string — five glyphs, like `14.1k` or `9,999` — can occupy
on the inner circle's centre line; Glance cannot shrink text to fit, so anything
wider is clipped rather than scaled.

The ring is rasterised with Canvas and shown as an `Image`, because Glance has no arc
primitive — its `CircularProgressIndicator` is indeterminate only. The bitmap is
capped at 512px square: a widget's RemoteViews payload is limited to roughly 1.5 MB
and a bitmap costs 4 bytes a pixel, so an uncapped one gets the widget dropped by the
launcher.

### How missing days count

A day the provider never reported is **absent from the list, not stored as a zero**.
"Untracked" and "did not move" are different facts, and collapsing them would silently
drag the average down. `AverageBasis` then decides how an untracked day counts:

| `AverageBasis` | Divisor | Reads as |
| --- | --- | --- |
| `ALL_DAYS` **(default)** | 7 | "Steps per day" — an untracked day counts against you |
| `DAYS_WITH_DATA` | days reported | "On days I tracked" — not comparable between windows |

## Building

Needs JDK 17 and the Android SDK (compileSdk 36). Point `local.properties` at your
SDK (`sdk.dir=/path/to/android-sdk`), then:

```bash
./gradlew :core:test             # averaging, windows, goal progress
./gradlew :app:testDebugUnitTest # the widget's state machine
./gradlew :app:assembleDebug     # APK
./gradlew :app:lintDebug         # lint (currently clean)
```

## Running it on a device

1. Install Health Connect from the Play Store if the device does not already have it
   (Android 14+ has it built in).
2. Install the app, open it, set your goal, and tap **Allow step access** — Health
   Connect shows its own permission dialog, not the standard Android one.
3. Long-press the home screen → Widgets → **7-day step average**. It wants about
   3×2 cells.

Without a step provider writing into Health Connect (Fitbit, Google Fit, Samsung
Health, or the phone's own sensor) the widget correctly shows "No steps recorded in
the last 7 days" rather than a zero.

## Releasing

Builds are published as GitHub Releases and picked up on the phone by
[Obtainium](https://github.com/ImranR98/Obtainium), which watches the repo and
offers the update when a new tag appears.

### One-time setup

**1. Create a signing key.** Do this on your own machine and keep the file — Android
identifies an app by package name *plus* signing key, so losing it means you can
never update this app again, only publish a new one under a different package name.

macOS or Linux:

```bash
keytool -genkeypair -v \
  -keystore release.jks -alias weekly-steps \
  -keyalg RSA -keysize 4096 -validity 10000
```

Windows PowerShell — same command, without the line continuations:

```powershell
keytool -genkeypair -v -keystore release.jks -alias weekly-steps -keyalg RSA -keysize 4096 -validity 10000
```

Keep the file outside this repository, or at least out of a folder that syncs
somewhere. `.gitignore` covers `*.jks`, but the safest keystore is one that was
never in the working tree.

**2. Add four repository secrets** (Settings → Secrets and variables → Actions).

For `KEYSTORE_BASE64`, the file has to become a single line of text:

```bash
base64 -w0 release.jks                       # Linux
base64 release.jks | tr -d '\n'              # macOS
```

```powershell
# Windows PowerShell — straight onto the clipboard, ready to paste
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\release.jks")) | Set-Clipboard
```

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | the single line produced above |
| `KEYSTORE_PASSWORD` | the keystore password from step 1 |
| `KEY_ALIAS` | `weekly-steps` |
| `KEY_PASSWORD` | the key password (the same one, unless you set it separately) |

**3. Install Obtainium** on the phone, *Add App*, paste this repository's URL. It
checks on its own and notifies you; installing is one tap. Android will not let any
app install another silently — only the Play Store can do that.

### Cutting a release

```bash
git tag v1.0.0 && git push origin v1.0.0
```

The workflow runs the tests and lint first, builds a signed APK, refuses to continue
if it turns out debug-signed, and publishes it. `versionName` comes from the tag;
`versionCode` from the commit count, so it cannot go backwards and strand an update.

### The switch from debug builds

The APKs installed by hand before this were signed with Android's universal debug
key. A release-signed build is a different identity, so Android will refuse it as an
update: **uninstall the app once** before installing the first release. The only
thing lost is the stored goal.

### Minification

`isMinifyEnabled` is off for release. R8 removes what it cannot see being used, and
the reflective entry points here — the Glance receiver, the WorkManager worker — are
what it tends to get wrong. `proguard-rules.pro` is meant to cover them, but no
minified build has been run on a device, and a broken widget arriving through an
automatic update is worse than a larger download. Worth turning on once a release
build has been installed and checked.

## Not done yet

* No instrumented tests — they need a device or emulator with Health Connect installed.
* The release build is unsigned; add a signing config before distributing.
* The window length is fixed at 7 days in code (`StepsWindow.DEFAULT_DAYS`), and
  `AverageBasis` at `ALL_DAYS`. Both are constructor parameters, so exposing them as
  settings is a UI change rather than a logic one.
