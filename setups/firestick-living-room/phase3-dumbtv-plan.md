# Phase 3 — dumbtv home fork: findings and plan

Status: **plan only.** Projectivy was undone (launcher-only restore) and uninstalled; the Stick is back to the
post-Phase-2 state (Amazon Home, no accessibility services, 56 packages disabled). dumbtv is cloned to
`./dumbtv` (commit `225bb00`, launcher 1.5, MIT license). Nothing has been built or installed.

## 1. What it's built with
- **Plain Java + Android framework, no libraries, no Gradle.** ~3,000 lines in `launcher/src/tv/dumbtv/home/`
  (15 classes). UI is built in code (no layout XML): `Home.java` (screen, rows, clock, wallpaper),
  `Tile.java` (one card drawn in a single `onDraw`), `Config.java` (`home.conf` + wallpapers), `Guard.java`
  (HOME takeover), `Options.java` (on-TV settings).
- `launcher/build.sh` calls the SDK tools directly: `aapt2` → `javac --release 8` → `d8` → `zip` → `zipalign`
  → `apksigner`. Compiles against **android-25** (one APK for Fire OS 6/7/8), targetSdk 28.
- APK permissions on Fire OS 8: **none** (its three permissions are capped at SDK 27 for Fire OS 6). No INTERNET.

## 2. How its HOME redirect works (`Guard.java`)
1. `Guard` is an accessibility service that only receives **window-state-changed** events.
   `canRetrieveWindowContent="false"`, no key filtering: it can't read screens or see key presses
   (Projectivy's could do both).
2. When the window that comes up is Amazon's `HomeActivity_vNext`, Guard shows a full-screen solid overlay
   (in your background colour) so Amazon's home never paints, then launches dumbtv home.
3. The launch goes through an **AlarmManager PendingIntent** because Android delays app launches for 5 s after
   HOME; alarm launches are exempt. That only works instantly with **`min_futurity=0`** in
   `settings global alarm_manager_constants`, which `install.sh` sets (and `--undo` removes).
4. Android 11 only lets an app with a visible window start an activity; the overlay *is* that window,
   so the launch fires on the overlay's first draw. Result per the README: ~0.3 s of black, then dumbtv home.
5. Amazon's *other* launcher screens (its Settings grid) are left alone, so **Settings keeps working**
   through the stock launcher, which stays enabled.

> ⚠ Unlike Projectivy, `min_futurity=0` is **required** here, not optional: without it the overlay lifts after
> 1.5 s and Amazon's home shows until the 5 s delay ends. Side effect: app alarms on the Stick may fire up to 5 s
> sooner than the system minimum. Reverted by the undo.

## 3. Can we build it on this PC?
Not yet. Missing today:

| Need | Status | Install (needs your OK) | Size, approx. |
|---|---|---|---|
| JDK 17 | missing | `winget install Microsoft.OpenJDK.17` | ~180 MB |
| Android cmdline-tools | missing | zip from developer.android.com → `C:\Android\Sdk\cmdline-tools\latest` | ~150 MB |
| `platforms;android-25`, `build-tools;34.0.0` | missing | `sdkmanager …`, **requires accepting the Android SDK license** (you accept it) | ~120 MB |
| `zip` | missing in Git Bash | not needed: use `jar` from the JDK | — |
| Python | `C:\Python314` is broken (no stdlib) | not needed | — |

`build.sh` won't run as-is on Windows: it looks for `aapt2`/`d8` without `.exe`/`.bat` and needs `zip`.
Plan: add a small **`build.ps1`** (same steps, Windows tool names, `jar` instead of `zip`) and keep `build.sh` untouched.
Signing: a new `release.keystore` in the fork. **Keep it**; updates must be signed with the same key.

## 4. Restyle to the Apple TV look: what changes

| Goal | Today | Change | Where | Effort |
|---|---|---|---|---|
| Large rounded **16:9 cards** | Banners are already 16:9 (Android TV banners are 320×180); 10 dp corners; small/medium/large | Add an `xlarge` size (≈ 400×225 dp), 18–20 dp radius; apps without a banner get a 16:9 card with icon + name on a frosted tile | `Tile.java`, `Config.TILES`, `Home.java` sizing | S |
| **Lift-and-glow** on focus | Scale 1.08 + accent ring | Scale ~1.12 + translateY −8 dp + soft shadow/glow under the card (pre-blurred glow bitmap or `setShadowLayer`), 180 ms ease-out; drop the ring by default | `Tile.java` | M |
| **Dark blurred wallpaper** | Image + 0–80 % dim; no blur | Android 11 has no `RenderEffect`, so blur once at decode time: downscale to 1/8, stack-blur, upscale; plus the existing dim | `Home.decode()` | S–M |
| **Clock + date top-right** | Clock/date in the header, left side | Move the header block right-aligned; thinner font weight | `Home.java` header | S |
| **No sidebar clutter** | No sidebar; "Inputs" row (empty on a Stick) | `inputs=off`, hide row titles, single centred app row/grid | config + small layout tweak | S |
| **Only your 9 apps, in order** | `apps.order` + `apps.hide` per app | Add `apps.only=` (show only these, in this order) so new installs don't appear | `Config.java`, `Home.java` | S |

Your apps: `com.netflix.ninja, com.amazon.firetv.youtube, com.amazon.firebat, com.disney.disneyplus,
com.spotify.tv.android, com.plexapp.android, com.hbo.hbonow, com.tubitv.ott, tv.pluto.android`.
(Tubi has no TV banner, only a phone launcher entry, so it gets the icon card. dumbtv's catalog handles both.)

## 5. Additions: feasibility, in your order

| # | Feature | Feasible? | Needs INTERNET? | Notes |
|---|---|---|---|---|
| 1 | Rotating wallpapers from your photos | **Already built in.** | No | Push photos to `/sdcard/Android/media/tv.dumbtv.home/wallpapers/` (adb can write there on Fire OS 8), set `background=image`, `rotate=visit/hourly/daily/daytime`. Blur/dim from §4 applies. Optional: cross-fade between images (S) |
| 2 | Quick-inputs row: Settings, Bluetooth, HDMI | **Yes, easy.** Verified intents on your Stick: Settings `android.settings.SETTINGS` → Amazon launcher's SettingsActivity; Bluetooth `android.settings.CONTROLLERS_SETTINGS` → Controllers & Bluetooth Devices; HDMI-CEC `com.amazon.device.settings.action.HDMI_CEC_DEVICE_CONTROL` → Display & Sounds. (A Stick has no HDMI *inputs*; it **is** the HDMI source, so "HDMI" = CEC/display settings.) | No | New `Catalog.SHORTCUTS` row of small icon cards (S–M). Wi-Fi (`android.settings.WIFI_SETTINGS`) also resolves if you want it |
| 3 | Small weather strip | Yes, but **needs INTERNET** | **Yes** | Open-Meteo (no API key) every 30–60 min, cached; or your own endpoint. No-internet alternative: your PC/server `adb push`es a `weather.json` into the media folder on a schedule (clunky, needs ADB always on) |
| 4 | Screensaver with live numbers from your endpoint | Yes, **needs INTERNET**; one unknown | **Yes** | Android `DreamService` in the same APK. Fire OS uses the standard dream system (`screensaver_components` = Amazon's today), so ours is selected with `settings put secure screensaver_components …` and reverted the same way. Unknown: whether Fire OS resets that setting on its own; test on the Stick. LAN `http://` needs a cleartext exception limited to your host. Android needs INTERNET even for LAN-only |

**INTERNET policy:** default build stays permission-free. Features 3–4 go behind a build switch (`-Net`) that adds
`INTERNET` + a network-security config allowing **only** the hosts you name (weather API + your endpoint). Without
the switch, the weather strip and live screensaver are compiled out.

## 6. Proposed plan (each step stops for your OK where marked)
1. **Tooling (asks first):** install JDK 17 via winget; download Android cmdline-tools; you accept the SDK
   license; `sdkmanager "platforms;android-25" "build-tools;34.0.0"`.
2. **Fork:** work on a local branch `appletv` in `./dumbtv`. Optional: a GitHub fork on your account (asks first).
3. **build.ps1**, then build **unmodified** dumbtv 1.5 to prove the toolchain (compare permissions = 0).
4. **Restyle** (§4) + `apps.only` + quick-inputs row (feature 2). Build, check permissions = 0.
5. **Install (asks first)** with dumbtv's own steps: `adb install`, deviceidle whitelist, `min_futurity=0`,
   enable Guard keeping other services, verify it's bound; push `home.conf` with your 9 apps and look settings.
   Screenshot, Home-press timing test, Settings/Bluetooth/HDMI shortcuts test.
6. **Restore scripts:** generalise the launcher-undo to cover `tv.dumbtv.home` (Guard service, deviceidle,
   `min_futurity` removal only if we set it), dry-run, then real-run once.
7. **Wallpapers (feature 1):** you push photos; set rotation.
8. **Net features (3, 4):** only if you approve the INTERNET build. Weather first, then the screensaver (Phase 4).
