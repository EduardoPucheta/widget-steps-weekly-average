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
* Optional 09:00 reminder, only on the mornings you are **below** the goal.
* In-app screen with the same number, a bar chart of the seven days, and the exact
  daily figures.
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

## The in-app screen

The widget is a glance; the app is the detail. Opening it shows the same number the
widget does, then the seven days behind it, then the exact figures — and below all
of that, the settings.

### What is on the screen, top to bottom

1. **The headline.** The 7-day average in large type, and under it one line with
   the gap to your goal and the dates covered: `1,600 short of 10k · 15–21 Sep`, or
   `1,200 over 10k`, or `goal met — 10k` when you land on it exactly.
2. **The chart.** One bar per day, oldest on the left, with a dashed line at your
   goal labelled on the right.
3. **A caption**, only when at least one day has nothing recorded, saying what the
   grey slots mean.
4. **The day list.** Each date with its exact step count, or `no data`.
5. **Settings** — the goal, the morning reminder, and step access.

The headline, the bars and the list come from a **single read** of Health Connect,
so they can never disagree with each other or with the widget.

### Reading the chart

* **Bars** are steps per day. Taller is more.
* **The dashed line** is your goal. A bar that reaches past it was a day at or
  above goal; one that stops short was not. That is the whole comparison — there is
  no colour-coding for it, because position against the line already says it.
* **Grey full-height slots** are days with nothing recorded. See below; they are not
  low days.
* **The letters underneath** are the days of the week in your phone's language and
  region — `M T W T F S S` in English, `L M X J V S D` in Spain, `L M M J V S D` in
  Latin American Spanish. Single letters repeat (two T's, two S's, and in Latin
  America two M's), so the list underneath carries the full date for each day.
  Because the window rolls, the first bar is whichever day was seven days ago,
  **not** always Monday.

There are no numbers up the side. The top of the scale is set to whichever is
higher — your goal or your best day — plus a little headroom, so the goal line is
always on screen even in a week that never came near it. For exact values, read the
list underneath; that is what it is for.

A few deliberate omissions, so they are not mistaken for gaps:

* **No legend.** There is one series, and the heading already says what it is.
* **No value written on each bar.** Seven numbers crowded above seven bars is noise,
  and the list gives them properly.
* **The goal is the only label on the chart**, because a threshold nobody names is
  just a line. It is also the only dashed line — dashing is kept for the one thing
  that is a threshold.

### Why the headline can say "short" when every bar clears the goal

Because days with nothing recorded still count toward the average — as zero. Take a
week where you walked 12,000 steps Monday to Friday, and the phone recorded nothing
at the weekend:

| | |
| --- | --- |
| Every visible bar | 12,000 — above the 10k line |
| Total over the window | 60,000 |
| Divided by all **7** days | **8,571** — shown as `1,429 short of 10k` |
| Divided by only the 5 recorded days | 12,000 |

The app uses the first: an untracked day counts against you. That is the honest
reading of "steps per day", and it keeps two weeks with different tracking coverage
comparable. The grey slots on the chart are there precisely so this case is visible
rather than baffling. The alternative, dividing only by tracked days, exists in the
code as `AverageBasis.DAYS_WITH_DATA`, but it is not exposed as a setting.

### Days with nothing recorded

A day the provider never reported is drawn as a **neutral full-height slot**, not a
short bar and not an absent one. Both alternatives lie:

* A faded version of the series colour reads as "a small amount of data".
* Drawing nothing is what a **recorded zero** looks like — someone who genuinely did
  not move.

The slot is neutral so it cannot be confused with the series at all, and full height
so it cannot be read as a small value. A caption under the chart says so in words,
and the list shows `no data` rather than a number.

If **nothing at all** was recorded in the window, the headline says `Nothing
recorded` instead of showing an average. An average of zero would claim a week of
not moving; the truth is that there is no reading to average. That matches the
widget, which says `No steps in 7 days` for the same week, and the morning reminder,
which stays quiet rather than reporting a shortfall it cannot see.

### When it updates

Every time the app comes to the front, after you grant step access, and after you
save a new goal. Nothing refreshes while the app sits open, and nothing needs to:
the window ends yesterday, so the numbers only change at midnight. If you leave the
app open across midnight, reopen it to move the window forward.

### When there is nothing to chart

| You see | Because |
| --- | --- |
| `Reading your steps…` | First load, before Health Connect answers |
| `Allow step access below…` | Step permission not granted yet, or revoked |
| `Health Connect needs installing or updating…` | Installed but too old, or missing |
| `Health Connect isn't supported on this device…` | No Health Connect on this phone at all |
| `Couldn't read your steps just now…` | The read failed; reopening retries |

Each of these replaces the chart rather than drawing an empty one, so a problem is
never presented as a week of no steps.

### Accessibility

The chart has a spoken description that reads each day and its value, including
`no data`. The day list gives every figure as text, so nothing depends on seeing the
bars or telling colours apart.

### Colours

The chart does not reuse the widget ring's colours. A 6dp stroke and a filled bar
have different requirements: the ring's teal is too low in chroma to read as anything
but grey once it fills an area, and its dark-mode step is too light for a large
block. The chart's steps were taken from the same hue and checked against a lightness
band, a chroma floor and a contrast ratio for each surface — measured, not eyeballed.

| | Light | Dark |
| --- | --- | --- |
| Ring (stroke) | `#00696D` — fails as a fill: chroma 0.08 | `#4FD8DE` — fails as a fill: too light |
| Chart (bars) | `#0E9299` — passes | `#2BA8AE` — passes |

## The morning reminder

Off by default. Switched on in the app, it posts a notification at 09:00 **only when
the 7-day average is below the goal** — no message is the signal that you are on
track.

It also stays quiet when there is nothing honest to say: no data recorded, step
access not granted, Health Connect missing, or the read failed. Every one of those is
a named reason in `ReminderDecision`, and each is logged, because "it didn't notify
me" and "it wasn't supposed to" are otherwise indistinguishable.

### Why the timing is approximate

The alarm uses `setAndAllowWhileIdle`, so it fires *around* 09:00 rather than exactly
on it. An exact alarm needs `SCHEDULE_EXACT_ALARM`, which the user must grant by hand
from Android 12 and which Google restricts to alarm-clock apps — a large ask for a
nudge that is just as useful at 09:07. In deep doze it may land in the next
maintenance window.

Nothing is lost by the imprecision: the window ends yesterday, so the average does
not move during the day. The reminder says exactly what the widget says.

### How it survives

Alarms do not outlive a reboot, and an app update clears them too, so
`ReminderReceiver` listens for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` and re-arms.
The alarm is also re-armed *before* each run's work begins, so a failure costs one
quiet morning rather than the whole feature.

The receiver itself only schedules — reading Health Connect can outlast the ten
seconds a broadcast receiver is given, so the work runs in `ReminderWorker`.

## Releasing

Builds are published as GitHub Releases and picked up on the phone by
[Obtainium](https://github.com/ImranR98/Obtainium), which watches the repo and
offers the update when a new tag appears.

### One-time setup

**1. Create a signing key.** Do this on your own machine and keep the file — Android
identifies an app by package name *plus* signing key, so losing it means you can
never update this app again, only publish a new one under a different package name.

```bash
keytool -genkeypair -v \
  -keystore release.jks -alias weekly-steps \
  -keyalg RSA -keysize 4096 -validity 10000
```

**2. Add four repository secrets** (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` — the whole file, one line |
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
