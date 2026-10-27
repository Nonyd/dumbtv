# Living-room Fire TV Stick: what was changed, and how to change it back

This folder is the record of everything done to the **Fire TV Stick 4K Max** in the living room
(model AFTKRT, Fire OS 8.1.8.5, IP address `192.168.100.3`), done in October 2026.

In short:
- **56 built-in Amazon apps and services were switched off** (not deleted): the ones that show ads,
  track what you watch, send usage data, or that you said you never use.
- **The home screen was replaced** with a clean, Apple TV-style one: your 9 apps as big rounded cards,
  a blurred photo in the background, the clock top right, and Settings/Bluetooth buttons at the bottom.
- **Your film, "You are for Me"**, plays across the top of the home screen with its song, fading in
  every time you come home and stopping the moment an app opens.
- **After 15 minutes of not touching the remote**, the home screen turns into a slideshow of the newest
  films on each streaming service (your photos when there's no internet).
- **Sleep time:** the screen turns off after 30 minutes without the remote, and the Stick sleeps after 45
  (it was 5 and 21). The undo puts the old times back.

Nothing was uninstalled from Amazon, nothing touches your Amazon account, and **everything can be
undone** with one command (see "Undo everything" below).

---

## What was switched off, and why

The full list, with one line per item, is in [disable-list.txt](disable-list.txt). The research
behind each choice is in [phase0-inventory.md](phase0-inventory.md). In plain terms:

| Group | What it did | Examples |
|---|---|---|
| Viewing tracking | Recognised what was on screen, logged what you watched, shared it with other apps, sensed whether people were in the room | Automatic Content Recognition, watch-time tracking, presence sensing |
| Ads and shopping | Sponsored rows and previews, ad bidding, an advertising ID, shopping tiles, "upgrade your Fire TV" pop-ups | Sneak Peek, Amazon Live Shopping, promo notices |
| Telemetry | Usage, performance and log uploads, remote "experiments" | metrics, logger, crash/perf collectors |
| Placeholders and tutorials | Empty stub apps and remote tutorials | dummy alarm clock/calendar/gallery |
| Amazon apps you don't use | Luna (games), Amazon Photos, Amazon Music, Amazon Kids, the Live tab | |
| Alexa extras | Alexa alarms/notifications pop-ups, Alexa music, skills, calling, smart-home dashboard | **Voice search on the remote still works** |
| Wi-Fi password upload | Uploaded your saved Wi-Fi passwords to Amazon | Wi-Fi itself works normally |

Left alone on purpose: Wi-Fi, Bluetooth, the remote, Settings, the keyboard, updates, your Amazon
sign-in, the Appstore, Prime Video, casting, and Amazon's own home screen (it is still there, just
hidden behind the new one, because Fire OS's Settings menu lives inside it).

**One side effect to know about:** switching off "Ambient Experience" (an Amazon promo screen) also
stops Fire OS from running *any* screensaver, Amazon's included. That is why the photo slideshow is
built into the home screen instead. If you leave the Stick idle inside another app, it simply goes
to sleep after 5 minutes.

---

## Undo everything

The restore script turns every app back on and brings back Amazon's normal home screen. About a minute.

1. Turn on the TV and the Stick. Make sure this computer is on the same Wi-Fi.
2. Open this folder (`Documents\firestick`) in File Explorer, click in the address bar, type
   `powershell`, press Enter.
3. Type this and press Enter:

   ```
   .\restore.ps1
   ```

   (If it says "running scripts is disabled", use `powershell -ExecutionPolicy Bypass -File .\restore.ps1`.)
4. Wait for `Failed: 0`. The TV jumps back to the normal Amazon home screen.

On a Mac or Linux computer, open a terminal here and run `./restore.sh` instead.

### Undo only the new home screen

Keeps the ad and tracking cleanup, brings back Amazon's home screen and the old screen and sleep times:

```
.\restore.ps1 -LauncherOnly
```

(`./restore.sh --launcher-only` on Mac/Linux.) The new home screen app stays installed but unused;
remove it completely with `adb uninstall tv.dumbtv.home`.

### Handy extras
- **See what it would do first:** add `-DryRun` (Windows) or `--dry-run` (Mac/Linux).
- **The Stick's IP changed?** Check it on the TV under Settings → My Fire TV → About → Network, then
  add it: `.\restore.ps1 -Device 192.168.1.50`.
- **"Not reachable"?** On the TV, check Settings → My Fire TV → Developer Options → ADB Debugging is
  On. If the TV asks "Allow USB debugging?", tick Always allow and press OK. Or unplug the Stick for
  10 seconds and try again.
- **Black screen with no home screen?** Run the restore script, or type
  `adb shell pm enable com.amazon.tv.launcher` and then `adb reboot`.
- **Turn one app back on:** on the TV, Settings → Applications → Manage Installed Applications →
  the app → Enable. Or `adb shell pm enable <name from disable-list.txt>`.

Both restore scripts were tested for real (not just dry runs) on this Stick on 27 October 2026.

---

## Changing the home screen

All the home screen's settings are in one small text file, [home.conf](home.conf), in this folder.
Edit it in Notepad, then send it to the Stick from PowerShell in this folder:

```
adb push home.conf /sdcard/Android/media/tv.dumbtv.home/home.conf
```

Then press **Home** on the remote. Changes appear right away.

Many settings can also be changed on the TV: press **☰ (Menu)** on any app card and choose **Options**.

### Your film in the top shelf

`film.mp4` in the home screen's media folder plays across the top, looping, in place of the movie
pictures. Its sound:
- fades in over about 2 seconds to 40 % every time the home screen appears;
- stops at once when an app opens, when the slideshow starts, or when the screen sleeps;
- stays silent if other music is already playing, and goes quiet while you use voice search (or
  anything else that needs the speakers), coming back afterwards.

Change it in `home.conf`: `film.volume=40` (0 to 100), `film.title=You are for Me` (the caption).

- **Replace the film:** `adb push film.mp4 /sdcard/Android/media/tv.dumbtv.home/film.mp4`, then press Home.
- **Remove it** (back to the movie pictures): `adb shell rm /sdcard/Android/media/tv.dumbtv.home/film.mp4`,
  then press Home.

The film is only on this PC (`brag-output/`) and the Stick; it is never put on GitHub.

### The top shelf (movie pictures) and switching back to the grid

The home screen now uses the **top shelf** look: a big picture across the top, your apps in one
row, and the background taking a dark shade of the highlighted app's colour. With a TMDB key in
place, the picture shows what's popular on the highlighted app: in Nigeria for Netflix and Prime
Video, and in the United States for Disney+, Max, Tubi and Pluto TV (they have no Nigerian
catalogue); "Trending this week" for YouTube, Spotify and Plex. The caption always says which.
Without the key, or without internet, it shows your wallpaper photos.

**Adding or changing the TMDB key** (from themoviedb.org → Settings → API, the short "API Key"):
from PowerShell in this folder run

```
.\set-tmdb-key.ps1
```

paste the key at the hidden prompt, and wait for "On the Stick: OK". Then press Home.

Keep `tmdb.key` out of anything you share; it is deliberately not in `home.conf`.

**Back to the 3 × 3 grid look:** `adb push home-grid.conf /sdcard/Android/media/tv.dumbtv.home/home.conf`,
then press Home. To return to the top shelf, push `home.conf` the same way.

The top shelf only gets movie pictures from the **net build** of the home screen, which is allowed to
contact TMDB (and nothing else). The offline build has no internet permission at all; it shows
the top shelf with your photos.

### Add, remove or reorder app tiles

The `apps.only=` line lists exactly which apps appear, in order, separated by commas:

```
apps.only=com.netflix.ninja,com.amazon.firetv.youtube,com.amazon.firebat,com.disney.disneyplus,com.spotify.tv.android,com.plexapp.android,com.hbo.hbonow,com.tubitv.ott,tv.pluto.android
```

| App | Name to use |
|---|---|
| Netflix | `com.netflix.ninja` |
| YouTube | `com.amazon.firetv.youtube` |
| Prime Video | `com.amazon.firebat` |
| Disney+ | `com.disney.disneyplus` |
| Spotify | `com.spotify.tv.android` |
| Plex | `com.plexapp.android` |
| Max | `com.hbo.hbonow` |
| Tubi | `com.tubitv.ott` |
| Pluto TV | `tv.pluto.android` |

- **Reorder:** change the order of the names. Or, on the TV, hold **OK** on a card → **Move**, use
  ◀ ▶ ▲ ▼, press OK.
- **Remove:** delete its name from the line. Or on the TV: hold OK → **Hide**.
- **Add a new app:** install it from the Appstore, then find its name with
  `adb shell pm list packages` (for example `adb shell pm list packages | findstr -i sling`),
  and add it to the line. Or on the TV: ☰ → Options → the list of apps → tick it.

With 9 apps the cards are sized so everything fits on one screen. More apps make the cards smaller.

### Background photo: dim, blur and how often it changes

| Line | Choices | Now |
|---|---|---|
| `dim=` | 0, 20, 40, 60, 80 (percent darker) | 60 |
| `blur=` | off, soft, strong | strong |
| `rotate=` | off, visit (every return home), hourly, daily, daytime | hourly |
| `idle=` | off, 1, 2, 3, 4, 10, 15, 20 (minutes before the slideshow) | 15 |
| `saver=` | 15, 30, 60, 120 (seconds per slideshow picture) | 30 |

With a TMDB key the slideshow shows the newest films (the last 6 months) on Netflix, Prime Video,
Disney+, Max, Tubi and Pluto TV, taking turns, each captioned "New on …". Without one it shows your photos.

Keep `idle=` shorter than the screen timeout. `launcher-install.sh` sets that to 30 minutes and the
sleep time to 45, and saves the old values in `backup/sleep-before.txt` for the undo. To change the
sleep time yourself: Settings › Display & Sounds › Screensaver / Sleep on the Stick, or rerun the
install with `SCREEN_OFF_MS=… SLEEP_MS=… ./launcher-install.sh`.

### Add or remove wallpaper photos

The photos live on the Stick in `/sdcard/Android/media/tv.dumbtv.home/wallpapers/`. They are used
both for the blurred background and for the slideshow. JPEG, PNG or WebP; any size.

- **Add one:** `adb push "C:\path\to\photo.jpg" /sdcard/Android/media/tv.dumbtv.home/wallpapers/`
- **See what's there:** `adb shell ls /sdcard/Android/media/tv.dumbtv.home/wallpapers/`
- **Remove one:** `adb shell rm /sdcard/Android/media/tv.dumbtv.home/wallpapers/photo.jpg`

The 18 current photos are copies of openly licensed pictures from Wikimedia Commons, kept in
[wallpapers/commons](wallpapers/commons). Their photographers and licenses are listed in
[wallpapers/commons/CREDITS.txt](wallpapers/commons/CREDITS.txt); several licenses ask that the
photographer is credited if you share the pictures.

---

## Rebuilding the home screen after editing its code

The home screen is a customised version of the open-source **dumbtv home**. Its code is in
[dumbtv](dumbtv), on the `appletv` branch of your fork, https://github.com/Nonyd/dumbtv. The build
tools are already installed on this computer (Java 17 and the Android SDK in `C:\Android\Sdk`).

1. Edit the code in `dumbtv\launcher\src\tv\dumbtv\home\`.
2. Build it, from PowerShell:

   ```
   cd dumbtv\launcher
   .\build.ps1 -Net
   ```

   It ends with `built build\dumbtv-home.apk (net)` and `4 uses-permission entries`: internet (for
   TMDB) plus three that only apply to old Fire OS 6. Leave out `-Net` for the offline build:
   `3 uses-permission entries`, no internet at all, photos in the top shelf.
3. Install it on the Stick, from Git Bash in this folder:

   ```
   bash launcher-install.sh
   ```

   This installs the new version, sends `home.conf`, and makes sure the Home button still opens it.
4. Save your work to GitHub:

   ```
   cd dumbtv
   git add -A
   git commit -m "what you changed"
   git push
   ```

**Keep `dumbtv\launcher\release.keystore` safe.** It is the app's signing key. Android only accepts
an update signed with the same key; without it you would have to uninstall and reinstall.

---

## Things to know

- **Pressing Home** shows a split second of black before the new home screen. That is how the new
  home screen takes over the button on Fire OS.
- **Prime Video's playback controls may stay on screen** instead of hiding by themselves. Prime Video
  does this whenever an accessibility service is on, and the new home screen uses one, a very
  limited one, to catch the Home button. It cannot read the screen or see your key presses.
- **After a restart** a small "remote" message from Fire OS may appear for a few seconds; it goes away
  by itself or with OK.
- **Ads in other apps** (Pluto TV, Tubi, YouTube) are those apps' own and are not affected.

---

## Last resort: factory reset

Only if the restore script didn't help. It erases the **Stick** (apps, logins, settings), not your
Amazon account. You sign in again and reinstall apps; about 20–30 minutes.

- **With the remote:** hold **Back** and the **right side of the ring** together for about 10 seconds,
  then choose OK.
- **From the menus:** Settings → My Fire TV → Reset to Factory Defaults.

---

## What's in this folder

| File | What it is |
|---|---|
| `restore.ps1`, `restore.sh` | Undo everything (or only the home screen) |
| `disable-list.txt` | The 56 switched-off packages, one reason per line |
| `home.conf` | The home screen's settings |
| `launcher-install.sh` | (Re)installs the home screen and hands it the Home button |
| `phase2-batch.sh` | The tool that switched the packages off in small, checked batches |
| `dumbtv\` | The home screen's source code (your GitHub fork) |
| `wallpapers\commons\` | The 18 wallpaper photos and their credits |
| `backup\` | The Stick's state before and after: `packages-before.txt`, `packages-after.txt`, launcher and screensaver settings |
| `log\` | Every command that was run, with screenshots in `log\screens\` |
| `phase0-inventory.md`, `phase3-*.md` | Research notes |
| `android-sdk-license.txt` | The Android SDK license you accepted for the build tools |
