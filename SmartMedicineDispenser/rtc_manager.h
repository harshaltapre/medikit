#ifndef RTC_MANAGER_H
#define RTC_MANAGER_H

#include <Arduino.h>
#include <Wire.h>
#include <RTClib.h>
#include "config.h"

/**
 * Manages the DS3231 Real-Time Clock over I2C with periodic health checks.
 */
class RtcManager {
 public:
  bool begin() {
    Wire.begin(PIN_I2C_SDA, PIN_I2C_SCL);

    if (!_rtc.begin()) {
      _healthy = false;
      Serial.println(F("[RTC] ERROR: DS3231 not found on I2C bus."));
      return false;
    }

    if (_rtc.lostPower()) {
      Serial.println(F("[RTC] Warning: RTC lost power, setting to sketch compile time."));
      _rtc.adjust(DateTime(F(__DATE__), F(__TIME__)));
    }

    _lastPollMs = millis() - RTC_POLL_INTERVAL_MS;
    pollNow();
    return _healthy;
  }

  void update() {
    unsigned long nowMs = millis();
    if (nowMs - _lastPollMs < RTC_POLL_INTERVAL_MS) {
      return;
    }
    _lastPollMs = nowMs;
    pollNow();
  }

  bool isHealthy() const {
    return _healthy;
  }

  DateTime now() const {
    return _now;
  }

  bool setDateTime(const DateTime& dt) {
    if (!isValidDateTime(dt)) {
      return false;
    }

    if (!isBusPresent()) {
      _healthy = false;
      return false;
    }

    _rtc.adjust(dt);
    _now = dt;
    _healthy = true;
    _lastPollMs = millis();
    return true;
  }

  static bool isValidDateTime(const DateTime& dt) {
    return dt.year() >= 2024 && dt.year() <= 2099 &&
           dt.month() >= 1 && dt.month() <= 12 &&
           dt.day() >= 1 && dt.day() <= 31 &&
           dt.hour() < 24 && dt.minute() < 60 && dt.second() < 60;
  }

 private:
  RTC_DS3231 _rtc;
  DateTime _now{2024, 1, 1, 0, 0, 0};
  bool _healthy = false;
  unsigned long _lastPollMs = 0;

  bool isBusPresent() {
    Wire.beginTransmission(DS3231_I2C_ADDR);
    return Wire.endTransmission() == 0;
  }

  void pollNow() {
    if (!isBusPresent()) {
      if (_healthy) {
        Serial.println(F("[RTC] ERROR: Lost I2C communication with DS3231."));
      }
      _healthy = false;
      return;
    }

    DateTime candidate = _rtc.now();
    if (!isValidDateTime(candidate)) {
      if (_healthy) {
        Serial.println(F("[RTC] ERROR: Invalid timestamp read from DS3231."));
      }
      _healthy = false;
      return;
    }

    if (!_healthy) {
      Serial.println(F("[RTC] DS3231 communication healthy."));
    }
    _now = candidate;
    _healthy = true;
  }
};

#endif  // RTC_MANAGER_H
