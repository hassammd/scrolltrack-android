# ScrollTrack 📵

**Less scrolling. More living.**

ScrollTrack is an Android app that automatically counts the reels you watch on
Instagram, Facebook and TikTok, measures exact screen time per app, and helps
you stay in control with gentle daily limits, streaks and insights.

![version](https://img.shields.io/badge/version-2.7-blue)
![targetSdk](https://img.shields.io/badge/targetSdk-34-green)
![minSdk](https://img.shields.io/badge/minSdk-26-orange)
![license](https://img.shields.io/badge/license-MIT-lightgrey)

## ✨ Features

- 🎬 **Automatic reel counting** — Instagram, Facebook & TikTok reels counted as
  you scroll (Accessibility Service)
- ⏱️ **Exact per-app screen time** — via UsageStatsManager, synced every ~60s
- 🎯 **Daily screen-time goal** + **per-app limits** with gentle nudge
  notifications (once per day per limit)
- 🔥 **Streaks** — clean days grow your flame; milestone celebrations at
  7 / 30 / 100 days (a missed day never erases your progress)
- 📊 **Insights** — Day / Week / Month charts, hourly reels, app breakdown,
  "time reclaimed" vs last week, browse any past period
- ⏸️ **Take-a-break timer** — 5 / 15 / 30 minute mindful pauses
- 🌙 **Light & dark themes** (follows system)
- 🔒 **100% on-device** — no account, no server, no analytics SDK.
  Your data never leaves your phone.

## 🛠️ Tech stack

- Native Android — **Java + XML layouts** (no frameworks)
- Manual build pipeline (`build.sh`): `javac` → `d8` → `aapt2` → `apksigner`
  (no Gradle required)
- Local **SQLite** database (per-day stats + hourly reels buckets) and
  **SharedPreferences** for settings
- `minSdk 26` · `targetSdk 34` · Java 17

## 🚀 Build

Requirements: JDK 17 and the Android SDK
(`build-tools;34.0.0` and `platforms;android-34`).

```bash
./build.sh
# → build/scrolltrack-v2.7.apk  (debug-signed)
```

> **Signing note:** `debug.keystore` is intentionally **not** committed.
> Keep your own copy safe — installing an APK signed with a different key
> will not update the existing install, it will install as a separate app.

Ready-to-install APKs are published under
[GitHub Releases](../../releases) — no need to build.

## 🔐 Permissions — why each one is needed

| Permission | Why ScrollTrack needs it |
|---|---|
| Accessibility Service | Counts reels as you scroll — the core feature. No text is read. |
| Usage Access (`PACKAGE_USAGE_STATS`) | Measures exact foreground time per app. |
| Notifications | Daily-limit nudges and break reminders (one per limit per day). |
| Foreground Service | Keeps tracking alive reliably. |

## 📁 Project structure

```
├── AndroidManifest.xml
├── build.sh                 # full manual build pipeline
├── java/com/hatch/reeltracker/
│   ├── MainActivity.java        # Today / Insights / Limits / You tabs
│   ├── OnboardingActivity.java  # first-run onboarding
│   ├── StreakActivity.java      # milestone celebration screen
│   ├── BreakActivity.java       # break countdown timer
│   ├── ReelAccessibilityService.java  # reel detection + limit nudges
│   ├── StreakManager.java       # clean-day evaluation, milestones
│   ├── StatsDb.java             # SQLite: days + hourly buckets
│   ├── Prefs.java               # settings (SharedPreferences)
│   ├── BarsView.java / GoalRingView.java / HeroRingView.java / FlameView.java
│   └── DiagnosticsActivity.java # setup & troubleshooting screen
├── res/                     # layouts, drawables, light/dark themes
└── design/figma/            # Figma reference screens
```

## 🔒 Privacy

- All tracking data (reel counts, screen time, streaks) is stored in the app's
  private storage on your device.
- The app has **no INTERNET permission** — it physically cannot send data
  anywhere.
- Deleting the app deletes all its data.

## 📄 License

MIT — see [LICENSE](LICENSE).
