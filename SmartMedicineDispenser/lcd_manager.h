#ifndef LCD_MANAGER_H
#define LCD_MANAGER_H

#include <Arduino.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <RTClib.h>
#include "config.h"

/**
 * Manages the 16x2 I2C LCD with flicker-free line caching and non-blocking toasts.
 */
class LcdManager {
 public:
  void begin() {
    for (uint8_t r = 0; r < LCD_ROWS; ++r) {
      _rowCache[r][0] = '\0';
    }

    Wire.begin(PIN_I2C_SDA, PIN_I2C_SCL);
    Wire.beginTransmission(LCD_I2C_ADDR);
    if (Wire.endTransmission() != 0) {
      Serial.print(F("[LCD] Warning: No I2C ACK at address 0x"));
      Serial.println(LCD_I2C_ADDR, HEX);
    }

    _lcd.init();
    Wire.begin(PIN_I2C_SDA, PIN_I2C_SCL);
    _lcd.backlight();
    _lcd.clear();
  }

  void update() {
    if (_toastActive && (millis() - _toastStartMs >= _toastDurationMs)) {
      _toastActive = false;
    }
  }

  bool isToastActive() const {
    return _toastActive;
  }

  void clearToast() {
    _toastActive = false;
  }

  void showStartupBanner() {
    printLine(0, "Smart Medicine");
    printLine(1, "6-Slot Firebase");
  }

  void showClockAndStatus(const DateTime& now,
                          bool wifiConnected,
                          int8_t nextCompIdx,
                          uint8_t nextHour,
                          uint8_t nextMinute,
                          uint8_t nextTablets,
                          bool anyLowStock) {
    if (_toastActive) {
      return;
    }

    char line0[32];
    char line1[32];

    snprintf(line0, sizeof(line0), "%02u:%02u:%02u %s",
             now.hour(), now.minute(), now.second(),
             wifiConnected ? "[Wi-Fi]" : "[Local]");

    if (nextCompIdx >= 0 && nextCompIdx < NUM_COMPARTMENTS) {
      snprintf(line1, sizeof(line1), "Next C%u %02u:%02u x%u",
               static_cast<unsigned>(nextCompIdx + 1),
               nextHour, nextMinute, nextTablets);
    } else if (anyLowStock) {
      snprintf(line1, sizeof(line1), "Low Stock Alert!");
    } else {
      snprintf(line1, sizeof(line1), "All Doses Done!");
    }

    printLine(0, line0);
    printLine(1, line1);
  }

  void showTrayStepping(uint8_t compIdx) {
    if (compIdx >= NUM_COMPARTMENTS) {
      return;
    }
    _toastActive = false;

    char line1[32];
    snprintf(line1, sizeof(line1), "To Compartment %u",
             static_cast<unsigned>(compIdx + 1));

    printLine(0, "Moving Stepper..");
    printLine(1, line1);
  }

  void showDispensing(uint8_t compIdx, uint8_t detected, uint8_t scheduled) {
    if (compIdx >= NUM_COMPARTMENTS) {
      return;
    }
    _toastActive = false;

    char line0[32];
    char line1[32];
    snprintf(line0, sizeof(line0), "Dispensing C%u...",
             static_cast<unsigned>(compIdx + 1));
    snprintf(line1, sizeof(line1), "IR Drop: %u / %u", detected, scheduled);

    printLine(0, line0);
    printLine(1, line1);
  }

  void showReminder(uint8_t compIdx,
                    const char* medName,
                    uint8_t detected,
                    uint8_t scheduled,
                    uint8_t remaining) {
    if (compIdx >= NUM_COMPARTMENTS) {
      return;
    }
    _toastActive = false;

    char line0[32];
    char line1[32];
    if (medName != nullptr && medName[0] != '\0') {
      snprintf(line0, sizeof(line0), "C%u %s",
               static_cast<unsigned>(compIdx + 1), medName);
    } else {
      snprintf(line0, sizeof(line0), "Compartment %u",
               static_cast<unsigned>(compIdx + 1));
    }
    snprintf(line1, sizeof(line1), "Take %u/%u Left:%u", detected, scheduled, remaining);

    printLine(0, line0);
    printLine(1, line1);
  }

  void showRtcError() {
    if (_toastActive) {
      return;
    }
    printLine(0, "RTC ERROR!");
    printLine(1, "Check I2C Wiring");
  }

  void showToast(const char* line0, const char* line1, unsigned long durationMs = 2000UL) {
    _toastActive = true;
    _toastStartMs = millis();
    _toastDurationMs = durationMs;

    printLine(0, line0 != nullptr ? line0 : "");
    printLine(1, line1 != nullptr ? line1 : "");
  }

 private:
  LiquidCrystal_I2C _lcd{LCD_I2C_ADDR, LCD_COLS, LCD_ROWS};
  char _rowCache[LCD_ROWS][LCD_COLS + 1]{};
  bool _toastActive = false;
  unsigned long _toastStartMs = 0;
  unsigned long _toastDurationMs = 0;

  void printLine(uint8_t row, const char* text) {
    if (row >= LCD_ROWS) {
      return;
    }

    char formatted[LCD_COLS + 1];
    size_t len = (text != nullptr) ? strlen(text) : 0;
    for (uint8_t i = 0; i < LCD_COLS; ++i) {
      formatted[i] = (i < len) ? text[i] : ' ';
    }
    formatted[LCD_COLS] = '\0';

    if (strncmp(formatted, _rowCache[row], LCD_COLS) == 0) {
      return;
    }

    memcpy(_rowCache[row], formatted, LCD_COLS + 1);
    _lcd.setCursor(0, row);
    _lcd.print(formatted);
  }
};

#endif  // LCD_MANAGER_H
