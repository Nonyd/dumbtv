# Phase 0 — Inventory and recommendations

Device: **Fire TV Stick 4K Max (2nd gen), AFTKRT / "karat"**, Fire OS 8.1.8.5 (RS8185.3891N), Android 11 (SDK 30).
311 packages, 0 disabled. Current Home: `com.amazon.tv.launcher/.ui.HomeActivity_vNext`.

References (used only as a cross-check, every row below was verified on this device with `dumpsys package`):
- `humblemuzzu/dumbtv` — `data/fireos/*.txt` and `catalog/devices/fireos-amazon-aftkrt.yaml` (tested on this exact model **and** build)
- `Gathin23/cleanyourtv` — `examples/amazon-fire-tv-stick-4k-2023/debloat.txt` (same board, same build)

Names from your prompt that **do not exist** on this device: `com.amazon.client.metrics` (the real one is `com.amazon.client.metrics.api`),
`com.amazon.device.sale.service` (the sale provider lives inside `com.amazon.media.recommendations`),
`com.amazon.tv.alexanotification` (the real one is `com.amazon.tv.alexanotifications`).
Also absent: `com.amazon.katoch`, `com.amazon.tv.fw.metrics`, `com.amazon.tv.democontent.provider`, `com.amazon.device.rdmapplication`,
`com.amazon.vizzini.ftvcds`, `com.amazon.wha.mediabrowserservice`, `com.android.nfc`.

Legend: **DISABLE** = recommended for Phase 2 · **ASK** = your choice · **KEEP** = leave enabled · **SKIP-RULE3** = touches something your hard rule 3 protects · **SKIP-UNKNOWN** = references disagree / unclear · **PROTECTED** = Fire OS refuses to disable it without root

## A. Ads, sponsored content, shopping

| Package | What it does (from dumpsys + references) | Category | Rec |
|---|---|---|---|
| com.amazon.kso.blackbird | Launcher ad bidding (`aps.GetBidService`, ad WebView) | ads | DISABLE |
| com.amazon.sneakpeek | Autoplaying "Sneak Peek" sponsored previews | ads | DISABLE |
| com.amazon.hybridadidservice | Advertising ID service | ads | DISABLE |
| com.amazon.privacypassservice | Ad-token (privacy pass) service | ads | DISABLE |
| com.amazon.media.recommendations | Sponsored/recommended rows, promo notifications, "SaleProvider" | ads | DISABLE (own batch — see note 1) |
| com.amazon.shoptv.client | Amazon Shopping on TV | ads | DISABLE |
| com.amazon.shoptv.firetv.client | "Amazon Live Shopping" tile | ads | DISABLE |
| com.amazon.systemnotices | Amazon promo notices | ads | DISABLE |
| com.amazon.tv.website_launcher | Opens promo web pages | ads | DISABLE |
| com.amazon.tv.easyupgrade | "Upgrade your Fire TV" promos / device-migration | ads | DISABLE |
| com.amazon.tv.earlyaccess | Early-access program stub | ads | DISABLE |
| com.amazon.uxnotification | UX promo popups | ads | DISABLE |
| com.amazon.tv.ftvambient | "Ambient Experience" (the plain screensaver is separate) | ads | DISABLE |
| com.amazon.tv.releasenotes | "What's new" popups | ads | DISABLE |
| com.amazon.ftvads.deeplinking | Ad deep links | ads | PROTECTED |

## B. ACR / viewing tracking

| Package | What it does | Category | Rec |
|---|---|---|---|
| com.amazon.tv.acr | Automatic Content Recognition (stub on this build) | ACR-tracking | DISABLE |
| com.amazon.hedwig | Fire TV Channels + watch-time/engagement tracking | ACR-tracking | DISABLE |
| com.amazon.tv.developer.dataservice | Shares viewing data for "content personalization" with 3rd-party apps | ACR-tracking | DISABLE |
| com.amazon.ftvspatial | Presence / indoor-location sensing (`SensorDataReceiver`, `IlsProximityReceiver`) | ACR-tracking | DISABLE |

## C. Telemetry, logging, experiments

| Package | What it does | Category | Rec |
|---|---|---|---|
| com.amazon.client.metrics.api | Metrics API | telemetry | DISABLE |
| com.amazon.device.telemetry.emitter | Telemetry emitter (no components of its own) | telemetry | DISABLE |
| com.amazon.ceviche | Analytics pipeline (`TrackEventsContentProvider`) | telemetry | DISABLE |
| com.amazon.dp.logger | Device logger | telemetry | DISABLE |
| com.amazon.perfc / com.amazon.perfcollection | Performance data collection | telemetry | DISABLE |
| com.amazon.neo.minerva / .logcat | Metrics + logcat upload | telemetry | DISABLE |
| com.amazon.wirelessmetrics.service | Wireless *metrics* reporting only (not the Wi-Fi stack) | telemetry | DISABLE |
| com.amazon.livedeviceservice | Device "shadow" state reporting | telemetry | DISABLE |
| com.amazon.ale | Experiments framework | telemetry | DISABLE |
| com.amazon.device_remote_config_sync_service | Remote config / experiment sync | telemetry | DISABLE |
| com.android.traceur | System tracing tool | telemetry | DISABLE |
| com.amazon.device.metrics, .crashmanager, .logmanager, com.amazon.diode, com.amazon.tv.csapp | Core metrics / crash / help app | telemetry | PROTECTED |

## D. Dead stubs and tutorials

| Package | What it does | Category | Rec |
|---|---|---|---|
| com.amazon.dummy.alarmclock / .calendar / .contacts / .gallery / .music | Empty placeholder apps | bloat | DISABLE |
| com.amazon.tmm.tutorial | Remote/headphone tutorial screens | bloat | DISABLE |
| com.amazon.minitv.android.app | miniTV stub | bloat | DISABLE |

## E. Alexa / Amazon extras (only if you don't use them)

Voice search on the remote stays in all cases: `vizzini`, `aca`, `aria`, `alexa.datastore.app`, `alexadirectivebrokerservice`, `tcomm`, `ivona.*` are **KEEP**.

| Package | What it does | Rec |
|---|---|---|
| com.amazon.tv.alexaalerts | Alexa alarms/timers/reminder overlays | DISABLE (you asked for this) |
| com.amazon.tv.alexanotifications | Alexa notification overlays | DISABLE (you asked for this) |
| com.amazon.alexa.externalmediaplayer.fireos | Alexa music playback | ASK |
| com.amazon.alexamediaplayer.runtime.ftv | Alexa media player | ASK |
| com.amazon.aiondec | Alexa skill connections | ASK |
| com.amazon.firetv.androidskillconnectioncapability | Alexa skill / Alexa+ events | ASK |
| com.amazon.spotify.mediabrowserservice | Lets *Alexa* control Spotify (the Spotify app itself is unaffected) | ASK |
| com.amazon.rtcsessioncontroller | Alexa calling / Drop In | ASK |
| com.amazon.smarthomemapviewapp | Smart-home dashboard (cameras, lights) | ASK |
| com.amazon.tv.mattercohost | Matter smart-home controller (Matter *casting* stays) | ASK |
| com.amazon.ods.kindleconnect | "Show on Fire TV" from Echo/Fire tablet | ASK |

## F. Pre-installed Amazon apps

| Package | App | Rec |
|---|---|---|
| com.amazon.gamehub | Amazon Luna / Game Hub | ASK |
| com.amazon.bueller.music | Amazon Music | ASK |
| com.amazon.bueller.photos | Amazon Photos | ASK |
| com.amazon.cloud9 | Silk browser | ASK |
| com.amazon.tahoe | Amazon Kids (FreeTime) profiles | ASK |

## G. Skipped — rule 3 (Wi-Fi / remote / audio / OTA / account / Settings)

| Package | Why skipped |
|---|---|
| com.amazon.wifilocker | Uploads saved Wi-Fi passwords to Amazon. Disabling doesn't break Wi-Fi (both projects do it), but it's Wi-Fi-related, so it's **your call** |
| com.amazon.connectivitydiag | Wi-Fi/network diagnostics screens |
| com.amazon.dummy.settings | Hosts Android's Wi-Fi "network request" dialog |
| com.amazon.tv.ffsprovisioneeclient | Frustration-free setup (Wi-Fi provisioning) |
| com.amazon.whisperjoin.middleware.v2.np | Setup/pairing middleware |
| com.amazon.storm.lightning.tutorial | Includes the PIN pairing screen for the Fire TV phone-remote app |
| com.amazon.whad / com.amazon.whasettings / com.amazon.audiohome | Multi-room audio / home-theater audio routing |
| com.amazon.tv.forcedotaupdater.v2 | OTA update UI |
| com.amazon.device.software.ota / .override | OTA core (also protected) |
| com.amazon.imp | Amazon account/auth. **Known to cause a black home screen on this exact model** |
| com.amazon.tv.consents, com.amazon.tv.turnstile, com.amazon.adep, com.amazon.platform.fdra | Consent / PIN sign-in / security |

## H. Skipped — unknown or references disagree

| Package | Why |
|---|---|
| com.amazon.tigris | dumbtv calls it telemetry, cleanyourtv says it's part of the Appstore (it has a `HomeActivity`) |
| com.amazon.providers.contentsupport | Has a text-to-speech engine receiver (could affect VoiceView/accessibility) |
| com.amazon.logan | Accessibility (screen magnifier, text banner). dumbtv disables it, cleanyourtv keeps it |
| com.amazon.tv.livetv / com.amazon.franktvinput | "Live" tab + guide. You have Sling, fubo and Pluto installed, which feed it |
| com.amazon.ftv.screensaver | Sponsored screensaver. Phase 4 only, once a replacement is in place |

## Never touched (hard list)
`com.amazon.tv.launcher` (until Phase 3), `com.amazon.firehomestarter`, `com.amazon.tv.settings.*`, `com.amazon.tv.ime`, `com.amazon.vizzini`
(re-enables itself and shows a "Unable to detect your remote" overlay), `com.amazon.device.messaging` (disabling it hung a Fire TV at boot;
Prime Video needs it), `com.amazon.venezia` (Appstore), `com.amazon.webview.chromium`, `com.amazon.fireinputdevices`, `com.amazon.autopairservice`,
`com.amazon.device.bluetoothpa/blepa`, `com.android.systemui`, `com.android.bluetooth`, `com.amazon.firebat`/`com.amazon.tv.support` (Prime Video),
casting (`cast.sink`, `dialservice`, `whisperplay`, `whisperlink`, `tv.matter`).

## Notes
1. On a second AFTKRT on this same build, disabling `imp` **together with** `media.recommendations`, `shoptv.*`, `sneakpeek`, `systemnotices`, `earlyaccess`
   and `tv.acr` made Home paint nothing (black screen, remote still worked). Re-enabling fixed it with no reboot. `imp` is the likely cause and we never touch it,
   but I'll still put `media.recommendations` in a batch of its own and check the screen after it.
2. What disabling **can't** remove: the hero banner and the "Sponsored" row come from the launcher's own cloud feed. Phase 3 (another launcher) or a
   DNS blocker handles those. Also turn off, in the menus: Settings → Preferences → Privacy Settings (Device Usage Data, Collect App Usage Data,
   Interest-based Ads) and Settings → Preferences → Featured Content (autoplay). Those settings sync to your Amazon account, so I won't change them over ADB.
3. Leave animation scales at 1.0 on this build. At 0.5 the side menu stops opening Settings.
4. Protected packages: neither project found a no-root way to disable these. We leave them alone.

## Phase 2 batch plan (draft, max 5 each, Home checked after every batch)
1. tv.acr, hedwig, tv.developer.dataservice, ftvspatial, hybridadidservice
2. kso.blackbird, sneakpeek, privacypassservice, systemnotices, tv.website_launcher
3. **media.recommendations** (alone)
4. shoptv.client, shoptv.firetv.client, tv.easyupgrade, tv.earlyaccess, uxnotification
5. tv.ftvambient, tv.releasenotes, client.metrics.api, device.telemetry.emitter, ceviche
6. dp.logger, perfc, perfcollection, neo.minerva, neo.minerva.logcat
7. wirelessmetrics.service, livedeviceservice, ale, device_remote_config_sync_service, android.traceur
8. dummy.alarmclock, dummy.calendar, dummy.contacts, dummy.gallery, dummy.music
9. tmm.tutorial, minitv.android.app, tv.alexaalerts, tv.alexanotifications
10+. Whatever you approve from sections E, F, G
