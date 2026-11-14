# SmartMedicineDispenser Firmware (6-Compartment Stepper + Servo + Firebase)

NodeMCU ESP8266 firmware for the **6-compartment circular-tray medicine dispenser** using a **NEMA 17 stepper (A4988)**, **SG90 bottom drop gate servo**, **IR pill-drop counter**, **DS3231 RTC**, **16×2 I2C LCD**, and **Firebase HTTPS synchronization**.

## Files

- `SmartMedicineDispenser.ino` — Main loop, `D3` button (acknowledge / sync / long-press reset), and `115200` baud Serial CLI (`HELP`, `STATUS`, `DISPENSE <1..6> [qty]`, `STEP`, `HOME`, `SYNC`, `SETCOMP`, `RESET`, `SET`).
- `config.h` — Pin mapping (`D5`/`D6` A4988 STEP/DIR, `D7` gate servo, `D4` IR sensor, `D1`/`D2` I2C, `D3` button, `D0` buzzer), Wi-Fi & Firebase Cloud Functions URLs, `OWNER_USER_ID`, `DEVICE_ID`, `DEVICE_SECRET`.
- `rtc_manager.h` — DS3231 initialization, periodic I2C health checks, and timestamp validation.
- `lcd_manager.h` — Flicker-free `16×2` I2C LCD views (`[Wi-Fi]`/`[Local]`, `Moving Stepper..`, `IR Drop: X / Y`, reminders, `RTC ERROR!`).
- `dispenser_control.h` — Shortest-path NEMA 17 tray positioning (`0°`–`300°`), multi-tablet gate servo pulses, IR-verified drop counting with retry, and BC547 buzzer alarm.
- `reminder_scheduler.h` — 6-compartment + 12-schedule EEPROM persistence, IR-verified inventory subtraction, and pending report queue.
- `firebase_client.h` — Non-blocking Wi-Fi + `WiFiClientSecure` HTTPS client for `deviceSync` and `reportDispense` Cloud Functions.

## Build Command

```powershell
arduino-cli compile --warnings all --fqbn esp8266:esp8266:nodemcuv2 .
```

See the [project root README](../README.md) for wiring, Firebase setup, and Android Studio instructions.
