# MIQAT (مِيقات)

Native Android app (Kotlin + Jetpack Compose) with exactly three features:

1. **Prayer Times** – nearby mosques (OpenStreetMap), map, distances, each mosque's published timetable.
2. **Qibla** – rotating compass using phone sensors, bearing in degrees, works offline.
3. **Calendar** – Gregorian + Hijri month view, Islamic occasions, Ramadan countdown, calendar-authority setting.

Permanent dark theme (black / #202020 cards / white text and icons). No ads, accounts, notifications or background tracking.

## Build the APK with GitHub (no Android Studio needed)

1. Create a new **empty repository** on github.com (e.g. `miqat`).
2. Upload **everything in this folder** (including the hidden `.github` folder) to the repository, on the `main` branch.
   - Easiest: install GitHub Desktop, or use `git`:
     `git init && git add . && git commit -m "MIQAT" && git branch -M main && git remote add origin <your repo url> && git push -u origin main`
   - The website's "Upload files" button does not always include hidden folders; if you use it, make sure `.github/workflows/build.yml` ends up in the repo.
3. Open the repo's **Actions** tab → *Build MIQAT APK*. It starts automatically on push (or press **Run workflow**). It takes about 5–10 minutes.
4. When it finishes, open the run and download the artifacts at the bottom:
   - `MIQAT-debug-apk` → `app-debug.apk`
   - `MIQAT-release-apk` → `app-release.apk`
   (Each download is a zip containing the APK.)

### Release signing
Without any setup, the release APK is signed with the debug key so it installs fine. For a proper permanent signing key:
```
keytool -genkeypair -v -keystore miqat.jks -alias miqat -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 miqat.jks      # copy the output
```
Add these repository secrets (Settings → Secrets and variables → Actions): `MIQAT_KEYSTORE_BASE64`, `MIQAT_KEYSTORE_PASSWORD`, `MIQAT_KEY_ALIAS`, `MIQAT_KEY_PASSWORD`. Keep `miqat.jks` safe; you need the same key for every future update.

## Install on a phone
1. Copy the `.apk` to the phone (or download it there).
2. Open it. If asked, allow "Install unknown apps" for your browser/file manager.
3. Open **MIQAT** and allow location when asked.

## Build locally (optional)
Open the folder in Android Studio (Ladybug or newer, JDK 17) and run, or with Gradle 8.9: `gradle assembleDebug assembleRelease`.

## Project layout
`app/src/main/java/com/miqat/app/`
- `core/` – location tracker (foreground only), preferences, distance maths
- `data/` – Room cache, Overpass mosque search
- `timetable/` – timetable model, pluggable `OfficialTimetableSource` (Mawaqit implemented), offline calculated fallback
- `qibla/` – bearing maths (pure, unit-tested) and compass sensor
- `hijri/` – Hijri conversion (Umm al-Qura via `java.time`, tabular civil), occasions, Ramadan estimate
- `ui/` – Compose screens and theme

## How prayer times are sourced (important)
- There is no single free API for every mosque's own timetable. MIQAT first tries **Mawaqit** (mawaqit.net), where mosques publish their own times; the mosque is matched by map position and name.
- If no official timetable is found, the card says **"Official timetable unavailable"** and shows **calculated** times clearly labelled as *not the mosque's timetable*. Nothing is ever presented as official unless it came from a source.
- To add another source (a specific mosque network, a website parser, etc.), implement `OfficialTimetableSource` and add it to the list in `AppViewModel`.
- Iqamah (congregation) times and Jumu'ah are shown only when the source provides them.
- The Mawaqit search endpoint is a public one used without an API key; if it changes or restricts access, the app falls back gracefully (unavailable + calculated) and will not crash.
- Timetables are fetched only when you open a mosque card, cached for 6 h, and mosque searches are cached for 24 h.

## Known limitations
- Calendar: no free feed of official moon-sighting announcements exists, so every Ramadan/Eid date is labelled **estimated**. "Local moon-sighting" starts from Umm al-Qura and relies on your ±1 day adjustment.
- Calculated times use the Muslim World League method in the phone's time zone. With a manually chosen location in another time zone, calculated times are shown in the phone's zone.
- Manual location is entered as latitude/longitude.
- Qibla needs the phone held flat. The compass uses the magnetic declination correction so the arrow points to true Qibla.

## Test checklist (real device)
GPS permission (allow / deny / deny twice) · mosque list and distances vs. a map app · mosque with and without Mawaqit data · flight-mode behaviour (saved mosques marked as possibly outdated) · Qibla vs. a known reference / another compass app, and calibration notice · phone without compass sensor message · Hijri date vs. a trusted source for the chosen method · Ramadan countdown · change time zone and reopen · kill and restart the app (saved location, settings, cache).

Unit tests (`MathTests`) cover Qibla bearing, angle wrapping and the tabular Hijri conversion; they run in the workflow after the APK build.
