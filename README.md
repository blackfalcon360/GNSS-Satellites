# GNSS Satellites 🛰

Shows every satellite your phone's GNSS chip can see — GPS 🇺🇸, GLONASS 🇷🇺, Galileo 🇪🇺, BeiDou 🇨🇳, QZSS 🇯🇵, NavIC 🇮🇳 and SBAS (WAAS, EGNOS, MSAS, GAGAN, SDCM, BDSBAS). Portrait only.

- **Sky** tab: sky plot (azimuth / elevation). Filled circle = used for your position, empty = seen only; bigger circle = stronger signal; each satellite carries its country's flag. **🧭 compass button**: the plot turns with the phone (hold it flat) so satellites appear where they really are around you.
- **List** tab: every satellite with flag, system, number (G12, C23, I5…), signal bar in dB-Hz, elevation/azimuth, signal band (L1, L5, E5a, B1C, S-band…) and whether it is used.
- **Info** tab: your fix (latitude, longitude, altitude, accuracy, speed, GPS time, time to first fix, GNSS hardware year/model on Android 9+) and a short description of each satellite system.
- Chips at the top show used/seen counts for each system; tap one to hide or show it.
- Works offline. Needs the Location permission and GPS switched on. Android 7.0 (API 24) or newer.

Brought to you By: Black Falcon 🦅

## Build
Push to GitHub -> Actions -> "Build APK" -> download `GnssSatellites-APK`.
Local: `gradle assembleDebug` (JDK 17, Gradle 8.7+).
