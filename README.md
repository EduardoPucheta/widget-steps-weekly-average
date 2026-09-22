# widget-steps-weekly-average

An Android home-screen widget showing your **average steps per day for the current
week**, read from Health Connect.

```
┌─────────────────────────────┐
│ Weekly average              │
│ 8.4k  +12%                  │
│ per day, 3 days so far      │
│ 21–27 Sep                   │
└─────────────────────────────┘
```

## What it does

* Reads daily step totals from Health Connect (read-only, step counts only).
* Averages them over the current week, following the device locale's week start.
* Shows the change against last week's average.
* Refreshes every 30 minutes via WorkManager; nothing leaves the device.

## Layout

| Module | What lives there |
| --- | --- |
| `core` | Pure Kotlin/JVM. Week windows, the averaging rules, the trend. No Android imports, so it runs in a plain JVM test. |
| `app` | The Android side: Health Connect access, the Glance widget, the permission screen. |

The split is deliberate — all the arithmetic that can be wrong is in `core`, where it
is tested directly without an emulator.

## The decision worth knowing about

"Average steps per day this week" is ambiguous the moment the week is unfinished, and
the readings differ a lot. On a Wednesday with 12,000 steps across Monday and
Wednesday (Tuesday untracked):

| `AverageBasis` | Divisor | Result | Reads as |
| --- | --- | --- | --- |
| `CALENDAR_DAYS` | 7 | 1,714 | Comparable across weeks, but looks like a collapse mid-week |
| `ELAPSED_DAYS` **(default)** | 3 | 4,000 | "Steps per day so far this week" — untracked days count as zero |
| `DAYS_WITH_DATA` | 2 | 6,000 | "On days I tracked" — not comparable across weeks |

The widget states its divisor on screen (`per day, 3 days so far`) rather than showing
a bare number whose meaning shifts through the week.

A related distinction runs through the whole data path: **a day with no reported data
is absent from the list, not present as a zero.** "Untracked" and "did not move" are
different facts, and collapsing them would silently drag the average down. The basis
then decides whether an untracked day counts against you.

## Building

Needs JDK 17 and the Android SDK (compileSdk 36). Point `local.properties` at your
SDK (`sdk.dir=/path/to/android-sdk`), then:

```bash
./gradlew :core:test            # the averaging rules
./gradlew :app:testDebugUnitTest # the widget's state machine
./gradlew :app:assembleDebug     # APK
./gradlew :app:lintDebug         # lint (currently clean)
```

## Running it on a device

1. Install Health Connect from the Play Store if the device does not already have it.
2. Install the app, open it, and tap **Allow step access** — Health Connect shows its
   own permission dialog, not the standard Android one.
3. Long-press the home screen → Widgets → **Weekly step average**.

Without a step provider writing into Health Connect (Fitbit, Google Fit, Samsung
Health, or the phone's own sensor) the widget correctly shows "No steps recorded this
week" rather than a zero.

## Not done yet

* No instrumented tests — they need a device or emulator with Health Connect installed.
* The release build is unsigned; add a signing config before distributing.
* `AverageBasis` is fixed to `ELAPSED_DAYS` in code. Making it a user setting means
  adding a preferences screen and passing the choice into `StepsWidgetStateLoader`.
