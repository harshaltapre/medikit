#ifndef CONFIG_H
#define CONFIG_H

#include <Arduino.h>

// Fallback GPIO definitions if board variant omits Dx macros
#ifndef D0
#define D0 16
#endif
#ifndef D1
#define D1 5
#endif
#ifndef D2
#define D2 4
#endif
#ifndef D3
#define D3 0
#endif
#ifndef D4
#define D4 2
#endif
#ifndef D5
#define D5 14
#endif
#ifndef D6
#define D6 12
#endif
#ifndef D7
#define D7 13
#endif

// -----------------------------------------------------------------------------
// Hardware Pin Allocation (NodeMCU ESP8266 ESP-12E)
// -----------------------------------------------------------------------------
constexpr uint8_t PIN_I2C_SDA = D2;          // GPIO 4  -> DS3231 & LCD1602 SDA
constexpr uint8_t PIN_I2C_SCL = D1;          // GPIO 5  -> DS3231 & LCD1602 SCL

constexpr uint8_t PIN_STEPPER_STEP = D5;     // GPIO 14 -> A4988 STEP
constexpr uint8_t PIN_STEPPER_DIR = D6;      // GPIO 12 -> A4988 DIR
constexpr uint8_t PIN_SERVO_GATE = D7;       // GPIO 13 -> Single SG90 bottom drop-gate servo
constexpr uint8_t PIN_IR_SENSOR = D4;        // GPIO 2  -> IR obstacle drop sensor (active LOW)

constexpr uint8_t PIN_BTN_ACK = D3;          // GPIO 0  -> Acknowledge / Force Sync button
constexpr uint8_t PIN_BUZZER = D0;           // GPIO 16 -> BC547 NPN base (via 1k resistor)

// -----------------------------------------------------------------------------
// 6-Compartment Tray & Multi-Schedule Limits
// -----------------------------------------------------------------------------
constexpr uint8_t NUM_COMPARTMENTS = 6;      // compartment1..compartment6 (60 deg apart)
constexpr uint8_t MAX_SCHEDULES = 12;        // Up to 12 enabled daily schedules

// -----------------------------------------------------------------------------
// NEMA 17 (A4988), Bottom Gate Servo & IR Drop-Sensor Calibration
// -----------------------------------------------------------------------------
constexpr uint16_t STEPS_PER_REVOLUTION = 200; // Full-step NEMA 17 (200). Multiply for microsteps.
constexpr unsigned int STEP_PULSE_US = 1200;   // Step HIGH/LOW pulse width
constexpr unsigned long STEP_INTERVAL_MS = 3UL;// Delay between steps (non-blocking)
constexpr unsigned long STEPPER_SETTLE_MS = 200UL;

constexpr uint8_t MAX_ATTEMPTS_PER_TABLET = 2; // Retry gate pulse if IR misses tablet drop
constexpr uint8_t GATE_CLOSED_ANGLE = 0;       // Single bottom gate closed angle
constexpr uint8_t GATE_OPEN_ANGLE = 90;        // Single bottom gate open angle
constexpr unsigned long GATE_OPEN_DURATION_MS = 1200UL;
constexpr unsigned long GATE_EARLY_CLOSE_DELAY_MS = 120UL;
constexpr unsigned long SERVO_SETTLE_DURATION_MS = 450UL;
constexpr unsigned long INTER_TABLET_PAUSE_MS = 400UL;
constexpr int SERVO_MIN_PULSE_US = 500;
constexpr int SERVO_MAX_PULSE_US = 2400;

constexpr uint8_t IR_ACTIVE_STATE = LOW;
constexpr unsigned long IR_DEBOUNCE_MS = 10UL;
constexpr unsigned long IR_RETRIGGER_HOLDOFF_MS = 150UL;

// -----------------------------------------------------------------------------
// Scheduling, Buzzer & Button Timing
// -----------------------------------------------------------------------------
constexpr unsigned long SERIAL_BAUD_RATE = 115200UL;
constexpr uint16_t DISPENSE_WINDOW_MINUTES = 5;
constexpr unsigned long REMINDER_TIMEOUT_MS = 300000UL;

constexpr unsigned long BUZZER_ON_MS = 300UL;
constexpr unsigned long BUZZER_OFF_MS = 700UL;

constexpr unsigned long BTN_BOOT_GUARD_MS = 2000UL;
constexpr unsigned long BTN_DEBOUNCE_MS = 50UL;
constexpr unsigned long BTN_LONG_PRESS_MS = 2000UL;

// -----------------------------------------------------------------------------
// I2C & LCD Configuration
// -----------------------------------------------------------------------------
constexpr uint8_t DS3231_I2C_ADDR = 0x68;
constexpr uint8_t LCD_I2C_ADDR = 0x27;
constexpr uint8_t LCD_COLS = 16;
constexpr uint8_t LCD_ROWS = 2;
constexpr unsigned long RTC_POLL_INTERVAL_MS = 500UL;

// -----------------------------------------------------------------------------
// Wi-Fi & Firebase Cloud Functions Configuration (Single-Owner Paired Device)
// -----------------------------------------------------------------------------
constexpr char WIFI_STA_SSID[] = "YOUR_WIFI_SSID";
constexpr char WIFI_STA_PASS[] = "YOUR_WIFI_PASSWORD";

constexpr char FIREBASE_SYNC_URL[] =
    "https://YOUR_REGION-YOUR_PROJECT_ID.cloudfunctions.net/deviceSync";
constexpr char FIREBASE_REPORT_URL[] =
    "https://YOUR_REGION-YOUR_PROJECT_ID.cloudfunctions.net/reportDispense";

// Must match the paired device & user's permanent API key in Firebase Realtime DB
constexpr char OWNER_USER_ID[] = "CHANGE_ME_OWNER_UID";
constexpr char USER_API_KEY[] = "CHANGE_ME_USER_API_KEY";
constexpr char DEVICE_ID[] = "dispenser-01";
constexpr char DEVICE_SECRET[] = "CHANGE_ME_DEVICE_SECRET";

constexpr unsigned long FIREBASE_SYNC_INTERVAL_MS = 30000UL;
constexpr uint16_t HTTPS_TIMEOUT_MS = 6000;

// -----------------------------------------------------------------------------
// EEPROM & Default Inventory Settings
// -----------------------------------------------------------------------------
constexpr size_t EEPROM_SIZE = 768;
constexpr uint16_t EEPROM_MAGIC = 0x4D47;  // "MG" (v3 Stepper+Firebase 6-slot)
constexpr uint8_t DEFAULT_LOW_STOCK_LIMIT = 2;

#endif  // CONFIG_H
