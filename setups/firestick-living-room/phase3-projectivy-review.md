# Phase 3 — Projectivy Launcher pre-install review

Nothing installed. APK downloaded to an isolated scratch folder and inspected as data only.

## File
- `ProjectivyLauncher-4.72-c97-xda-release.apk`, 11,432,750 bytes, from the developer's own GitHub release
  (https://github.com/spocky/miproja1/releases/tag/4.72)
- SHA-256 `a312935837b7cf323880455c322f4c98060e3e37e8c6187963fb2c4764534aae`: **matches** the value GitHub publishes for the asset
- Package `com.spocky.projengmenu`, version 4.72 (code 97), minSdk 23, targetSdk 37, requires `android.software.leanback`

## Requested permissions (21)
| Permission | What it allows | Note |
|---|---|---|
| INTERNET, ACCESS_NETWORK_STATE | Network access | Wallpaper providers, channel artwork, license check |
| READ_EXTERNAL_STORAGE (≤ SDK 32), READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED | Read your photos/videos | Custom wallpapers |
| RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE, WAKE_LOCK | Start at boot, stay running | Needed to win the Home race |
| REQUEST_INSTALL_PACKAGES | Can *prompt* to install APKs | Icon packs / plugins; Android still shows a confirmation |
| REQUEST_DELETE_PACKAGES | Can *prompt* to uninstall apps | "Uninstall" from the launcher; Android still confirms |
| com.android.launcher.permission.INSTALL_SHORTCUT | Apps can add shortcuts | Standard for launchers |
| READ_TV_LISTINGS, providers.tv READ/WRITE_EPG_DATA | Read/write TV channel data | Channel rows |
| com.android.vending.BILLING | Google Play purchases | Premium; no Play billing on Fire TV |
| com.google.android.finsky…INSTALL_REFERRER | Play install-referrer | Play Store attribution |
| **com.google.android.gms.permission.AD_ID** | **Google advertising ID** | From the bundled Firebase/Google measurement SDK |
| **ACCESS_ADSERVICES_AD_ID, ACCESS_ADSERVICES_ATTRIBUTION** | **Android Privacy Sandbox ad ID / attribution** | Same SDK; no Privacy Sandbox on Fire OS 8 (Android 11) |
| com.spocky.projengmenu.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION | Internal | Signature-level, app-private |

Not requested: SYSTEM_ALERT_WINDOW, WRITE_SECURE_SETTINGS, device admin, camera, microphone, location, contacts.

## Bundled SDKs
Firebase Analytics, Crashlytics, Performance, Remote Config, A/B Testing, Installations, Sessions, Google datatransport.
The manifest sets `firebase_analytics_collection_enabled=false`, `firebase_crashlytics_collection_enabled=false` and
`firebase_performance_collection_enabled=false`: those three are **off by default** (the app can turn them on at runtime,
usually from a consent option). Remote Config / Installations have no such switch and will contact Google servers.
No ad-serving SDK (AdMob, Facebook, AppsFlyer, etc.) was found.

## Accessibility service
`com.spocky.projengmenu/.services.ProjectivyAccessibilityService` (config `res/hI.xml`):
- `canRetrieveWindowContent=true`: **can read the content of whatever is on screen, in any app**
- `canRequestFilterKeyEvents=true`: **can see every remote-control key press**
- The developer states it's only used for Home override and remote shortcuts; it's closed source, so that can't be verified.
  For comparison, dumbtv's Guard only watches window changes.
- Side effect on any Fire TV: while an accessibility service is on, Prime Video keeps playback controls on screen (miproja1 #380).

## Your apps on this Stick
| Order | App | Package | Launch activity |
|---|---|---|---|
| 1 | Netflix | com.netflix.ninja | .MainActivity |
| 2 | YouTube | com.amazon.firetv.youtube | dev.cobalt.app.MainActivity (not YouTube TV: `com.amazon.firetv.youtube.tv`) |
| 3 | Prime Video | com.amazon.firebat | com.amazon.pyrocore.IgnitionActivity |
| 4 | Disney+ | com.disney.disneyplus | …main.MainActivity |
| 5 | Spotify | com.spotify.tv.android | .SpotifyTVActivity |
| 6 | Plex | com.plexapp.android | tv.plex.app.MainActivity |
| 7 | Max | com.hbo.hbonow | com.wbd.beam.BeamActivity |
| 8 | Tubi | com.tubitv.ott | com.tubitv.activities.FireTVMainActivity (regular launcher entry, not leanback) |
| 9 | Pluto TV | tv.pluto.android | …SplashMediatorActivity |
| — | Settings | via `android.settings.SETTINGS` | **resolves to `com.amazon.tv.launcher/.ui.SettingsActivity`** |

Because Settings is hosted by Amazon's launcher, `com.amazon.tv.launcher` must stay **enabled** under Projectivy.
