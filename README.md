# Smart Medicine Dispenser (Android Compose + Firebase + NodeMCU ESP8266)

Complete 3-tier **6-Compartment Automatic Medicine Dispenser** combining:
1. **Native Android App (`AndroidApp/`)**: Kotlin + Jetpack Compose + Material 3 + Firebase Auth, Cloud Firestore & Cloud Messaging.
2. **Firebase Backend (`FirebaseBackend/`)**: Single-owner Firestore Security Rules + Cloud Functions (`deviceSync`, `reportDispense`) with atomic IR-verified inventory transactions and FCM push notifications.
3. **NodeMCU ESP8266 Hardware Firmware (`SmartMedicineDispenser/`)**: NEMA 17 stepper (`A4988`) rotating a 6-compartment circular tray over a single **SG90 servo drop gate** and **IR pill-drop sensor**, with DS3231 RTC, 16×2 I2C LCD, and BC547 buzzer.

> **Architecture Choice:** Single-owner pairing — one authenticated Android owner account per physical dispenser (`users/{userId}/devices/{deviceId}` guarded by `deviceRegistry/{deviceId}` and `x-device-secret`).

---

## Repository Structure

```text
Smart medicine Dispenser/
├── README.md
├── AndroidApp/                        # Kotlin + Jetpack Compose + Material 3 Android Studio project
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle.properties
│   └── app/
│       ├── build.gradle.kts
│       └── src/main/
│           ├── AndroidManifest.xml
│           └── java/com/smartmed/dispenser/
│               ├── MainActivity.kt
│               ├── model/DispenserModels.kt
│               ├── data/FirebaseRepository.kt
│               ├── ui/DispenserViewModel.kt
│               ├── ui/DispenserScreens.kt
│               └── fcm/DispenserMessagingService.kt
├── FirebaseBackend/                   # Firestore Security Rules + Cloud Functions
│   ├── firebase.json
│   ├── firestore.rules
│   └── functions/
│       ├── package.json
│       └── index.js
└── SmartMedicineDispenser/            # NodeMCU ESP8266 Arduino C++ firmware
    ├── SmartMedicineDispenser.ino
    ├── config.h
    ├── rtc_manager.h
    ├── lcd_manager.h
    ├── dispenser_control.h
    ├── reminder_scheduler.h
    ├── firebase_client.h
    └── README.md
```

---

## Hardware Pin Allocation (NodeMCU ESP8266 ESP-12E)

| Component | Signal / Pin | NodeMCU Pin | ESP8266 GPIO | Notes |
| --- | --- | --- | :---: | --- |
| DS3231 RTC & 16×2 LCD | SDA / SCL | `D2` / `D1` | GPIO 4 / 5 | Shared 3.3V-safe I2C bus (`0x68` RTC, `0x27` LCD) |
| A4988 Stepper Driver | `STEP` | `D5` | GPIO 14 | Drives NEMA 17 circular tray (`60°` per slot) |
| A4988 Stepper Driver | `DIR` | `D6` | GPIO 12 | Shortest-path clockwise/counter-clockwise rotation |
| SG90 Drop-Gate Servo | Signal | `D7` | GPIO 13 | Single bottom drop gate (`0°` closed, `90°` open) |
| IR Obstacle Sensor | OUT | `D4` | GPIO 2 | Active `LOW` (`INPUT_PULLUP`), counts tablet drops |
| Acknowledge / Sync Button | Signal | `D3` | GPIO 0 | Short press: ack/sync; 2s hold: reset daily flags |
| Active Buzzer Driver | Control | `D0` | GPIO 16 | Via 1kΩ resistor to BC547 NPN base |

### Electrical Safety Notes
1. Power the **NEMA 17 motor** via the A4988 `VMOT` (e.g., 12V supply with A4988 current limit properly tuned and a 100µF decoupling capacitor across `VMOT`/`GND`), power the **SG90 servo** from a regulated **5V** rail (not NodeMCU `3V3`), and tie all grounds together cleanly.
2. Keep `D3` (GPIO 0) and `D4` (GPIO 2 / IR sensor) HIGH/unobstructed during power-up so ESP8266 boots normally.

---

## Verified Inventory Logic

When a schedule triggers (e.g., Compartment 1 has **20** tablets and scheduled dose is **2** tablets):
1. ESP8266 reads schedules synced from Firebase `deviceSync` (and stored locally in EEPROM).
2. NEMA 17 stepper moves Compartment 1 (`0°`, `60°`, `120°`, `180°`, `240°`, `300°`) above the drop hole.
3. SG90 gate pulses up to `MAX_ATTEMPTS_PER_TABLET` per tablet while the IR sensor counts physical tablet drops (`detectedQuantity`).
4. ESP8266 subtracts **only `detectedQuantity`** locally and sends `{ compartmentId, scheduledQuantity, detectedQuantity, timestamp, status }` to Firebase `reportDispense`.
5. The Cloud Function atomically decrements `remainingQuantity` by `detectedQuantity`, writes `dispensingHistory`, and sends FCM push notifications for completed, partial, failed, or low-stock events.

---

## Setup Guide

### 1. Firebase Backend (`FirebaseBackend/`)
1. Create a Firebase project in the Firebase Console and enable **Authentication** (Google Sign-In + Email/Password), **Cloud Firestore**, **Realtime Database**, and **Cloud Messaging**.
2. Install dependencies & deploy rules + functions from `FirebaseBackend/`:
   ```powershell
   cd "d:\Student Projects\Smart medicine Dispenser\FirebaseBackend\functions"
   npm install
   cd ..
   firebase deploy --only firestore:rules,database,functions
   ```
3. Copy your deployed `deviceSync` and `reportDispense` HTTPS URLs.

### 2. Android App (`AndroidApp/`) — "Personal Drug Cabinet" UI & Persistent API Key
1. Open `AndroidApp/` in **Android Studio**.
2. Register Android app package `com.smartmed.dispenser` (with your SHA-1 fingerprint for Google Sign-In) in Firebase Console and place `google-services.json` inside `AndroidApp/app/`.
3. Sync Gradle and run the app:
   - **Welcome / Google Auth Screen:** Tap **G** / **Get started** to sign in with Google (or **Sign in** for Email/Password).
   - **Persistent API Key (Realtime Database):** On first sign-in, a unique key (`SMD-...`) is generated and stored in Firebase Realtime Database under `/users/{uid}/apiKey` and `/apiKeys/{apiKey}`. Signing out and signing back in with the same account always restores the exact same API key.
   - **Home Dashboard (`Hi, <Name> 👋`):** Vertical-oval calendar date strip, `To take` capsule/tablet cards with gold checkmark swipe/tap action, and curved center `+` navigation bar.
   - **Capsules Configurator (`+`):** Form selector (`Tablets` / `Capsules` / `Drops`), compartment slot `1..6`, notification bell toggle, vertical-oval weekday pills (`Mon`–`Sun`), and color swatches.
   - **Settings (`⚙`):** Copy your permanent API key, manage 6-compartment stock/refills, edit schedules, and inspect IR-verified dispensing history.

### 3. NodeMCU ESP8266 Firmware (`SmartMedicineDispenser/`)
1. Install **RTClib**, **LiquidCrystal I2C**, and **ArduinoJson** (v7+) in Arduino IDE (`esp8266:esp8266` board **NodeMCU 1.0 (ESP-12E Module)**).
2. Update `WIFI_STA_SSID`, `WIFI_STA_PASS`, `FIREBASE_SYNC_URL`, `FIREBASE_REPORT_URL`, `OWNER_USER_ID`, `USER_API_KEY` (copied from the Android app), `DEVICE_ID`, and `DEVICE_SECRET` in [`SmartMedicineDispenser/config.h`](SmartMedicineDispenser/config.h).
3. Flash the sketch and open Serial Monitor (`115200` baud) to calibrate tray zero (`STEP` / `HOME`) and test (`STATUS`, `DISPENSE 1 2`, `SYNC`).
