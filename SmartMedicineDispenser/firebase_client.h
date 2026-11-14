#ifndef FIREBASE_CLIENT_H
#define FIREBASE_CLIENT_H

#include <Arduino.h>
#include <ESP8266WiFi.h>
#include <ESP8266HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>
#include "config.h"
#include "rtc_manager.h"
#include "dispenser_control.h"
#include "reminder_scheduler.h"

/**
 * Synchronizes compartment inventory, medicine schedules, device online
 * heartbeat, and IR-verified dispense reports with Firebase Cloud Functions
 * over HTTPS without stalling active stepper/servo dispensing cycles.
 */
class FirebaseClient {
 public:
  void begin(RtcManager& rtc, DispenserControl& dispenser, ReminderScheduler& scheduler) {
    _rtc = &rtc;
    _dispenser = &dispenser;
    _scheduler = &scheduler;

    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(true);

    if (isWifiConfigured()) {
      WiFi.begin(WIFI_STA_SSID, WIFI_STA_PASS);
      Serial.print(F("[WIFI] Connecting to "));
      Serial.println(WIFI_STA_SSID);
    } else {
      Serial.println(F("[WIFI] Wi-Fi SSID not configured in config.h; running locally."));
    }
  }

  void requestImmediateSync() {
    _forceSync = true;
  }

  bool isConnected() const {
    return WiFi.status() == WL_CONNECTED;
  }

  void update() {
    if (_scheduler == nullptr || _dispenser == nullptr) {
      return;
    }

    bool connected = isConnected();
    _scheduler->setWifiConnected(connected);

    if (connected && !_wasConnected) {
      _wasConnected = true;
      _forceSync = true;
      Serial.print(F("[WIFI] Connected! IP: "));
      Serial.println(WiFi.localIP());
    } else if (!connected && _wasConnected) {
      _wasConnected = false;
      Serial.println(F("[WIFI] Disconnected; continuing offline RTC scheduling."));
    }

    // Never block stepper/servo/IR timing during active dispensing.
    if (!connected || !isFirebaseConfigured() || _dispenser->isBusy()) {
      return;
    }

    unsigned long nowMs = millis();

    ReminderScheduler::PendingDispenseReport report{};
    if (_scheduler->peekPendingReport(report)) {
      if (nowMs - _lastReportMs >= 5000UL) {
        _lastReportMs = nowMs;
        if (postDispenseReport(report)) {
          _scheduler->popPendingReport();
        }
      }
      return;
    }

    if (_forceSync || (nowMs - _lastSyncMs >= FIREBASE_SYNC_INTERVAL_MS)) {
      _forceSync = false;
      _lastSyncMs = nowMs;
      syncWithCloud();
    }
  }

 private:
  RtcManager* _rtc = nullptr;
  DispenserControl* _dispenser = nullptr;
  ReminderScheduler* _scheduler = nullptr;

  bool _wasConnected = false;
  bool _forceSync = false;
  unsigned long _lastSyncMs = 0;
  unsigned long _lastReportMs = 0;

  static bool isWifiConfigured() {
    return strlen(WIFI_STA_SSID) > 0 && strcmp(WIFI_STA_SSID, "YOUR_WIFI_SSID") != 0;
  }

  static bool hasUserApiKey() {
    return strlen(USER_API_KEY) > 0 && strcmp(USER_API_KEY, "CHANGE_ME_USER_API_KEY") != 0;
  }

  static bool hasDeviceSecret() {
    return strlen(DEVICE_SECRET) > 0 && strcmp(DEVICE_SECRET, "CHANGE_ME_DEVICE_SECRET") != 0;
  }

  static bool isFirebaseConfigured() {
    bool hasAuth = hasUserApiKey() ||
                   (hasDeviceSecret() && strcmp(OWNER_USER_ID, "CHANGE_ME_OWNER_UID") != 0);
    return strstr(FIREBASE_SYNC_URL, "YOUR_PROJECT_ID") == nullptr && hasAuth;
  }

  static void addAuthHeaders(HTTPClient& https) {
    if (hasUserApiKey()) {
      https.addHeader(F("x-api-key"), USER_API_KEY);
    }
    if (hasDeviceSecret()) {
      https.addHeader(F("x-device-secret"), DEVICE_SECRET);
    }
  }

  bool syncWithCloud() {
    WiFiClientSecure client;
    client.setInsecure();

    HTTPClient https;
    https.setTimeout(HTTPS_TIMEOUT_MS);

    String url;
    url.reserve(256);
    url += FIREBASE_SYNC_URL;
    url += F("?ownerId=");
    url += OWNER_USER_ID;
    url += F("&deviceId=");
    url += DEVICE_ID;
    url += F("&status=");
    url += _scheduler->dispensingStatusString();

    if (!https.begin(client, url)) {
      Serial.println(F("[FIREBASE] Failed to initialize sync HTTPS request."));
      return false;
    }

    addAuthHeaders(https);
    int httpCode = https.GET();
    if (httpCode != HTTP_CODE_OK) {
      Serial.print(F("[FIREBASE] Sync HTTP error: "));
      Serial.println(httpCode);
      https.end();
      return false;
    }

    String payload = https.getString();
    https.end();

    JsonDocument doc;
    DeserializationError err = deserializeJson(doc, payload);
    if (err) {
      Serial.print(F("[FIREBASE] JSON parse error: "));
      Serial.println(err.c_str());
      return false;
    }

    ReminderScheduler::CompartmentData comps[NUM_COMPARTMENTS]{};
    for (uint8_t i = 0; i < NUM_COMPARTMENTS; ++i) {
      comps[i] = _scheduler->compartment(i);
    }

    JsonArray compArr = doc["compartments"].as<JsonArray>();
    for (JsonObject c : compArr) {
      uint8_t id = c["id"] | 0;
      if (id >= 1 && id <= NUM_COMPARTMENTS) {
        uint8_t idx = id - 1;
        const char* name = c["medicineName"] | comps[idx].medicineName;
        snprintf(comps[idx].medicineName, sizeof(comps[idx].medicineName), "%s", name);
        comps[idx].remainingQuantity = c["remainingQuantity"] | comps[idx].remainingQuantity;
        comps[idx].lowStockLimit = c["lowStockLimit"] | comps[idx].lowStockLimit;
      }
    }

    ReminderScheduler::ScheduleEntry scheds[MAX_SCHEDULES]{};
    uint8_t schedCount = 0;
    JsonArray schedArr = doc["schedules"].as<JsonArray>();
    for (JsonObject s : schedArr) {
      if (schedCount >= MAX_SCHEDULES) {
        break;
      }
      uint8_t compId = s["compartmentId"] | 0;
      const char* timeStr = s["time"] | "";
      unsigned int hh = 0, mm = 0;
      if (compId < 1 || compId > NUM_COMPARTMENTS || sscanf(timeStr, "%u:%u", &hh, &mm) != 2 ||
          hh >= 24 || mm >= 60) {
        continue;
      }

      ReminderScheduler::ScheduleEntry& dst = scheds[schedCount++];
      const char* sid = s["id"] | "";
      snprintf(dst.scheduleId, sizeof(dst.scheduleId), "%s", sid);
      dst.compartmentId = compId;
      dst.hour = static_cast<uint8_t>(hh);
      dst.minute = static_cast<uint8_t>(mm);
      dst.tabletsPerDose = s["tabletsPerDose"] | 1;
      if (dst.tabletsPerDose == 0) {
        dst.tabletsPerDose = 1;
      }
      dst.enabled = (s["enabled"] | true) ? 1 : 0;
    }

    _scheduler->applyCloudSync(comps, scheds, schedCount);
    return true;
  }

  bool postDispenseReport(const ReminderScheduler::PendingDispenseReport& report) {
    WiFiClientSecure client;
    client.setInsecure();

    HTTPClient https;
    https.setTimeout(HTTPS_TIMEOUT_MS);

    if (!https.begin(client, FIREBASE_REPORT_URL)) {
      Serial.println(F("[FIREBASE] Failed to initialize report HTTPS request."));
      return false;
    }

    https.addHeader(F("Content-Type"), F("application/json"));
    addAuthHeaders(https);

    JsonDocument doc;
    doc["ownerId"] = OWNER_USER_ID;
    doc["deviceId"] = DEVICE_ID;
    doc["compartmentId"] = report.compartmentId;
    doc["scheduledQuantity"] = report.scheduledQuantity;
    doc["detectedQuantity"] = report.detectedQuantity;
    doc["timestamp"] = report.timestampIso;
    doc["status"] = report.status;

    String body;
    serializeJson(doc, body);

    int httpCode = https.POST(body);
    String resp = https.getString();
    https.end();

    if (httpCode >= 200 && httpCode < 300) {
      Serial.print(F("[FIREBASE] Reported verified dispense ("));
      Serial.print(report.detectedQuantity);
      Serial.print('/');
      Serial.print(report.scheduledQuantity);
      Serial.println(F(") successfully."));
      return true;
    }

    Serial.print(F("[FIREBASE] Report HTTP error "));
    Serial.print(httpCode);
    Serial.print(F(": "));
    Serial.println(resp);
    return false;
  }
};

#endif  // FIREBASE_CLIENT_H
