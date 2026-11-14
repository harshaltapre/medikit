#ifndef REMINDER_SCHEDULER_H
#define REMINDER_SCHEDULER_H

#include <Arduino.h>
#include <EEPROM.h>
#include <stddef.h>
#include "config.h"
#include "rtc_manager.h"
#include "dispenser_control.h"
#include "lcd_manager.h"

/**
 * Coordinates 6-compartment medicine schedules, IR-verified tablet counting,
 * EEPROM persistence, and queued dispense reports for Firebase synchronization.
 */
class ReminderScheduler {
 public:
  struct CompartmentData {
    char medicineName[24];
    uint16_t remainingQuantity;
    uint16_t lowStockLimit;
  };

  struct ScheduleEntry {
    char scheduleId[24];
    uint8_t compartmentId;   // 1..NUM_COMPARTMENTS
    uint8_t hour;            // 0..23
    uint8_t minute;          // 0..59
    uint8_t tabletsPerDose;  // 1..10
    uint8_t enabled;         // 0 or 1
  };

  struct PendingDispenseReport {
    uint8_t compartmentId;   // 1..NUM_COMPARTMENTS
    uint8_t scheduledQuantity;
    uint8_t detectedQuantity;
    char timestampIso[28];
    char status[16];         // "completed", "partial", "failed"
  };

  static constexpr uint8_t MAX_PENDING_REPORTS = 8;

  void begin(RtcManager& rtc, DispenserControl& dispenser, LcdManager& lcd) {
    _rtc = &rtc;
    _dispenser = &dispenser;
    _lcd = &lcd;

    EEPROM.begin(EEPROM_SIZE);
    loadState();

    if (_rtc->isHealthy()) {
      syncDateWithRtc(_rtc->now());
    }
  }

  void setWifiConnected(bool connected) {
    _wifiConnected = connected;
  }

  void update() {
    if (_rtc == nullptr || _dispenser == nullptr || _lcd == nullptr) {
      return;
    }

    uint8_t compIdx = 0;
    uint8_t scheduledQty = 0;
    uint8_t detectedQty = 0;
    if (_dispenser->consumeDispenseResult(compIdx, scheduledQty, detectedQty)) {
      handleDispenseCompleted(compIdx, scheduledQty, detectedQty);
    }

    if (_reminderActive && (millis() - _reminderStartMs >= REMINDER_TIMEOUT_MS)) {
      _dispenser->stopAlarm();
      _reminderActive = false;
      _activeReminderComp = -1;
      Serial.println(F("[REMINDER] Auto-silenced after timeout."));
    }

    if (!_rtc->isHealthy()) {
      _lcd->showRtcError();
      return;
    }

    DateTime now = _rtc->now();
    syncDateWithRtc(now);

    if (!_dispenser->isBusy()) {
      for (uint8_t i = 0; i < _state.scheduleCount && i < MAX_SCHEDULES; ++i) {
        const ScheduleEntry& s = _state.schedules[i];
        if (!s.enabled || s.compartmentId < 1 || s.compartmentId > NUM_COMPARTMENTS) {
          continue;
        }
        if (isScheduleDispensed(i)) {
          continue;
        }
        if (isWithinScheduleWindow(now, s.hour, s.minute)) {
          startDispenseCycle(static_cast<uint8_t>(s.compartmentId - 1),
                             s.tabletsPerDose,
                             static_cast<int8_t>(i),
                             true);
          break;
        }
      }
    }

    if (_dispenser->isStepping() && _dispenser->activeComp() >= 0) {
      _lcd->showTrayStepping(static_cast<uint8_t>(_dispenser->activeComp()));
    } else if (_dispenser->isBusy() && _dispenser->activeComp() >= 0) {
      _lcd->showDispensing(static_cast<uint8_t>(_dispenser->activeComp()),
                           _dispenser->detectedQty(),
                           _dispenser->scheduledQty());
    } else if (_reminderActive && _activeReminderComp >= 0) {
      uint8_t idx = static_cast<uint8_t>(_activeReminderComp);
      _lcd->showReminder(idx,
                         _state.compartments[idx].medicineName,
                         _lastDetectedQty,
                         _lastScheduledQty,
                         static_cast<uint8_t>(_state.compartments[idx].remainingQuantity));
    } else {
      int8_t nextSchedIdx = findNextSchedule(now);
      int8_t nextCompIdx = -1;
      uint8_t nextH = 0, nextM = 0, nextTab = 0;
      if (nextSchedIdx >= 0) {
        const ScheduleEntry& ns = _state.schedules[nextSchedIdx];
        nextCompIdx = static_cast<int8_t>(ns.compartmentId - 1);
        nextH = ns.hour;
        nextM = ns.minute;
        nextTab = ns.tabletsPerDose;
      }
      _lcd->showClockAndStatus(now, _wifiConnected, nextCompIdx, nextH, nextM, nextTab,
                               hasAnyLowStock());
    }
  }

  bool triggerManualDispense(uint8_t compIdx, uint8_t tabletsPerDose, bool enqueueFirebaseReport) {
    if (_dispenser == nullptr || compIdx >= NUM_COMPARTMENTS || tabletsPerDose == 0) {
      return false;
    }
    if (_dispenser->isBusy()) {
      Serial.println(F("[SCHEDULER] Busy: dispense already in progress."));
      return false;
    }
    return startDispenseCycle(compIdx, tabletsPerDose, -1, enqueueFirebaseReport);
  }

  bool acknowledgeReminder() {
    if (_dispenser == nullptr || _lcd == nullptr) {
      return false;
    }

    if (_reminderActive) {
      _dispenser->stopAlarm();
      _reminderActive = false;
      _activeReminderComp = -1;
      _dispenser->beep(60);
      _lcd->showToast("Dose Taken! OK", "Reminder Cleared", 2000UL);
      Serial.println(F("[REMINDER] Acknowledged by user."));
      return true;
    }

    _dispenser->beep(40);
    _lcd->showToast(_wifiConnected ? "Wi-Fi Connected" : "Offline Mode",
                    "Syncing Cloud...", 1600UL);
    return false;
  }

  void resetTodayHistory() {
    if (_rtc != nullptr && _rtc->isHealthy()) {
      DateTime now = _rtc->now();
      _state.year = now.year();
      _state.month = now.month();
      _state.day = now.day();
    }
    _state.dispensedMask = 0;
    saveState();

    if (_dispenser != nullptr && _reminderActive) {
      _dispenser->stopAlarm();
    }
    _reminderActive = false;
    _activeReminderComp = -1;

    if (_lcd != nullptr) {
      _lcd->showToast("History Reset!", "Ready For Doses", 2000UL);
    }
    Serial.println(F("[EEPROM] Today's dispensed flags cleared."));
  }

  void applyCloudSync(const CompartmentData syncedComps[NUM_COMPARTMENTS],
                      const ScheduleEntry syncedScheds[MAX_SCHEDULES],
                      uint8_t syncedSchedCount) {
    if (syncedSchedCount > MAX_SCHEDULES) {
      syncedSchedCount = MAX_SCHEDULES;
    }

    bool changed = false;
    for (uint8_t i = 0; i < NUM_COMPARTMENTS; ++i) {
      if (strncmp(_state.compartments[i].medicineName, syncedComps[i].medicineName,
                  sizeof(CompartmentData::medicineName)) != 0 ||
          _state.compartments[i].remainingQuantity != syncedComps[i].remainingQuantity ||
          _state.compartments[i].lowStockLimit != syncedComps[i].lowStockLimit) {
        _state.compartments[i] = syncedComps[i];
        changed = true;
      }
    }

    uint16_t newMask = 0;
    for (uint8_t i = 0; i < syncedSchedCount; ++i) {
      for (uint8_t oldIdx = 0; oldIdx < _state.scheduleCount; ++oldIdx) {
        if (strcmp(_state.schedules[oldIdx].scheduleId, syncedScheds[i].scheduleId) == 0 &&
            _state.schedules[oldIdx].hour == syncedScheds[i].hour &&
            _state.schedules[oldIdx].minute == syncedScheds[i].minute &&
            isScheduleDispensed(oldIdx)) {
          newMask |= static_cast<uint16_t>(1U << i);
          break;
        }
      }
    }

    if (_state.scheduleCount != syncedSchedCount || _state.dispensedMask != newMask) {
      changed = true;
    } else {
      for (uint8_t i = 0; i < syncedSchedCount; ++i) {
        if (memcmp(&_state.schedules[i], &syncedScheds[i], sizeof(ScheduleEntry)) != 0) {
          changed = true;
          break;
        }
      }
    }

    if (changed) {
      _state.scheduleCount = syncedSchedCount;
      _state.dispensedMask = newMask;
      for (uint8_t i = 0; i < syncedSchedCount; ++i) {
        _state.schedules[i] = syncedScheds[i];
      }
      saveState();
      Serial.println(F("[SYNC] Updated local compartments & schedules from Firebase."));
    }
  }

  void setCompartmentLocal(uint8_t compIdx,
                           const char* medName,
                           uint16_t remainingQty,
                           uint16_t lowLimit) {
    if (compIdx >= NUM_COMPARTMENTS) {
      return;
    }
    if (medName == nullptr) {
      _state.compartments[compIdx].medicineName[0] = '\0';
    } else if (medName != _state.compartments[compIdx].medicineName) {
      snprintf(_state.compartments[compIdx].medicineName,
               sizeof(CompartmentData::medicineName),
               "%s", medName);
    }
    _state.compartments[compIdx].remainingQuantity = remainingQty;
    _state.compartments[compIdx].lowStockLimit = lowLimit;
    saveState();
  }

  bool peekPendingReport(PendingDispenseReport& out) const {
    if (_reportCount == 0) {
      return false;
    }
    out = _reportQueue[_reportHead];
    return true;
  }

  void popPendingReport() {
    if (_reportCount == 0) {
      return;
    }
    _reportHead = (_reportHead + 1) % MAX_PENDING_REPORTS;
    --_reportCount;
  }

  uint8_t pendingReportCount() const {
    return _reportCount;
  }

  const CompartmentData& compartment(uint8_t idx) const {
    return _state.compartments[idx < NUM_COMPARTMENTS ? idx : 0];
  }

  uint8_t scheduleCount() const {
    return _state.scheduleCount;
  }

  const ScheduleEntry& schedule(uint8_t idx) const {
    return _state.schedules[idx < MAX_SCHEDULES ? idx : 0];
  }

  bool isScheduleDispensed(uint8_t schedIdx) const {
    if (schedIdx >= MAX_SCHEDULES) {
      return false;
    }
    return (_state.dispensedMask & (1U << schedIdx)) != 0;
  }

  bool isReminderActive() const {
    return _reminderActive;
  }

  const char* dispensingStatusString() const {
    if (_rtc != nullptr && !_rtc->isHealthy()) {
      return "rtc_error";
    }
    if (_dispenser != nullptr && _dispenser->isBusy()) {
      return "dispensing";
    }
    if (_reminderActive) {
      return "reminder_active";
    }
    return "idle";
  }

 private:
  struct PersistedState {
    uint16_t magic;
    uint16_t year;
    uint8_t month;
    uint8_t day;
    uint16_t dispensedMask;
    uint8_t scheduleCount;
    CompartmentData compartments[NUM_COMPARTMENTS];
    ScheduleEntry schedules[MAX_SCHEDULES];
    uint8_t checksum;
  };

  RtcManager* _rtc = nullptr;
  DispenserControl* _dispenser = nullptr;
  LcdManager* _lcd = nullptr;

  PersistedState _state{};
  bool _wifiConnected = false;

  bool _reminderActive = false;
  int8_t _activeReminderComp = -1;
  int8_t _activeScheduleIdx = -1;
  bool _enqueueReport = true;
  uint8_t _lastScheduledQty = 0;
  uint8_t _lastDetectedQty = 0;
  unsigned long _reminderStartMs = 0;

  PendingDispenseReport _reportQueue[MAX_PENDING_REPORTS]{};
  uint8_t _reportHead = 0;
  uint8_t _reportTail = 0;
  uint8_t _reportCount = 0;

  static uint8_t computeChecksum(const PersistedState& s) {
    const uint8_t* bytes = reinterpret_cast<const uint8_t*>(&s);
    uint8_t sum = 0x5A;
    for (size_t i = 0; i < offsetof(PersistedState, checksum); ++i) {
      sum ^= bytes[i];
    }
    return sum;
  }

  static bool isValidState(const PersistedState& s) {
    return s.magic == EEPROM_MAGIC &&
           s.checksum == computeChecksum(s) &&
           s.scheduleCount <= MAX_SCHEDULES &&
           s.year >= 2024 && s.year <= 2099 &&
           s.month >= 1 && s.month <= 12 &&
           s.day >= 1 && s.day <= 31;
  }

  void initDefaultState() {
    DateTime seed = (_rtc != nullptr && _rtc->isHealthy())
                        ? _rtc->now()
                        : DateTime(2026, 11, 14, 0, 0, 0);

    memset(&_state, 0, sizeof(_state));
    _state.magic = EEPROM_MAGIC;
    _state.year = seed.year();
    _state.month = seed.month();
    _state.day = seed.day();
    _state.dispensedMask = 0;

    for (uint8_t i = 0; i < NUM_COMPARTMENTS; ++i) {
      snprintf(_state.compartments[i].medicineName,
               sizeof(CompartmentData::medicineName),
               "Medicine %u", static_cast<unsigned>(i + 1));
      _state.compartments[i].remainingQuantity = (i == 0) ? 20 : 10;
      _state.compartments[i].lowStockLimit = DEFAULT_LOW_STOCK_LIMIT;
    }

    _state.scheduleCount = 1;
    snprintf(_state.schedules[0].scheduleId, sizeof(ScheduleEntry::scheduleId), "default-1");
    _state.schedules[0].compartmentId = 1;
    _state.schedules[0].hour = 8;
    _state.schedules[0].minute = 0;
    _state.schedules[0].tabletsPerDose = 2;
    _state.schedules[0].enabled = 1;

    saveState();
  }

  void loadState() {
    PersistedState loaded{};
    EEPROM.get(0, loaded);

    if (isValidState(loaded)) {
      _state = loaded;
      Serial.print(F("[EEPROM] Loaded 6 compartments & "));
      Serial.print(_state.scheduleCount);
      Serial.println(F(" schedules."));
    } else {
      Serial.println(F("[EEPROM] Initializing default 6-compartment & schedule state."));
      initDefaultState();
    }
  }

  void saveState() {
    _state.magic = EEPROM_MAGIC;
    _state.checksum = computeChecksum(_state);
    EEPROM.put(0, _state);
    EEPROM.commit();
  }

  void syncDateWithRtc(const DateTime& now) {
    if (_state.year != now.year() ||
        _state.month != now.month() ||
        _state.day != now.day()) {
      _state.year = now.year();
      _state.month = now.month();
      _state.day = now.day();
      _state.dispensedMask = 0;
      saveState();
      Serial.println(F("[SCHEDULER] New day detected; daily dispensed flags reset."));
    }
  }

  static uint16_t toDayMinutes(uint8_t hour, uint8_t minute) {
    return static_cast<uint16_t>(hour) * 60U + minute;
  }

  static bool isWithinScheduleWindow(const DateTime& now, uint8_t hour, uint8_t minute) {
    uint16_t nowMin = toDayMinutes(now.hour(), now.minute());
    uint16_t schedMin = toDayMinutes(hour, minute);
    return nowMin >= schedMin && (nowMin - schedMin) < DISPENSE_WINDOW_MINUTES;
  }

  int8_t findNextSchedule(const DateTime& now) const {
    uint16_t nowMin = toDayMinutes(now.hour(), now.minute());
    int8_t bestIdx = -1;
    uint16_t bestMin = 24U * 60U + 1U;

    for (uint8_t i = 0; i < _state.scheduleCount && i < MAX_SCHEDULES; ++i) {
      const ScheduleEntry& s = _state.schedules[i];
      if (!s.enabled || s.compartmentId < 1 || s.compartmentId > NUM_COMPARTMENTS) {
        continue;
      }
      if (isScheduleDispensed(i)) {
        continue;
      }
      uint16_t schedMin = toDayMinutes(s.hour, s.minute);
      if (schedMin >= nowMin || (nowMin - schedMin) < DISPENSE_WINDOW_MINUTES) {
        if (schedMin < bestMin) {
          bestMin = schedMin;
          bestIdx = static_cast<int8_t>(i);
        }
      }
    }
    return bestIdx;
  }

  bool hasAnyLowStock() const {
    for (uint8_t i = 0; i < NUM_COMPARTMENTS; ++i) {
      if (_state.compartments[i].remainingQuantity <= _state.compartments[i].lowStockLimit) {
        return true;
      }
    }
    return false;
  }

  bool startDispenseCycle(uint8_t compIdx,
                          uint8_t tabletsPerDose,
                          int8_t scheduleIdx,
                          bool enqueueReport) {
    if (!_dispenser->startDispense(compIdx, tabletsPerDose)) {
      return false;
    }

    _activeScheduleIdx = scheduleIdx;
    _enqueueReport = enqueueReport;
    _activeReminderComp = static_cast<int8_t>(compIdx);
    _lastScheduledQty = tabletsPerDose;
    _lastDetectedQty = 0;
    _reminderActive = true;
    _reminderStartMs = millis();

    _dispenser->startAlarm();
    _lcd->showTrayStepping(compIdx);
    return true;
  }

  void handleDispenseCompleted(uint8_t compIdx, uint8_t scheduledQty, uint8_t detectedQty) {
    if (compIdx >= NUM_COMPARTMENTS) {
      return;
    }

    _lastScheduledQty = scheduledQty;
    _lastDetectedQty = detectedQty;

    // Crucial rule: decrease inventory ONLY by the IR-verified detected quantity.
    uint16_t current = _state.compartments[compIdx].remainingQuantity;
    uint16_t decrement = (detectedQty <= current) ? detectedQty : current;
    _state.compartments[compIdx].remainingQuantity = current - decrement;

    if (_activeScheduleIdx >= 0 && _activeScheduleIdx < MAX_SCHEDULES) {
      _state.dispensedMask |= static_cast<uint16_t>(1U << _activeScheduleIdx);
    }
    saveState();

    Serial.print(F("[INVENTORY] Compartment "));
    Serial.print(compIdx + 1);
    Serial.print(F(" verified "));
    Serial.print(detectedQty);
    Serial.print('/');
    Serial.print(scheduledQty);
    Serial.print(F(". Remaining: "));
    Serial.println(_state.compartments[compIdx].remainingQuantity);

    if (_enqueueReport) {
      enqueuePendingReport(static_cast<uint8_t>(compIdx + 1), scheduledQty, detectedQty);
    }

    _activeScheduleIdx = -1;
    _enqueueReport = true;
  }

  void enqueuePendingReport(uint8_t compartmentId,
                            uint8_t scheduledQty,
                            uint8_t detectedQty) {
    if (_reportCount >= MAX_PENDING_REPORTS) {
      popPendingReport();
    }

    PendingDispenseReport& r = _reportQueue[_reportTail];
    r.compartmentId = compartmentId;
    r.scheduledQuantity = scheduledQty;
    r.detectedQuantity = detectedQty;

    if (_rtc != nullptr && _rtc->isHealthy()) {
      DateTime now = _rtc->now();
      snprintf(r.timestampIso, sizeof(r.timestampIso),
               "%04u-%02u-%02uT%02u:%02u:%02uZ",
               now.year(), now.month(), now.day(),
               now.hour(), now.minute(), now.second());
    } else {
      snprintf(r.timestampIso, sizeof(r.timestampIso), "1970-01-01T00:00:00Z");
    }

    if (detectedQty == 0) {
      snprintf(r.status, sizeof(r.status), "failed");
    } else if (detectedQty < scheduledQty) {
      snprintf(r.status, sizeof(r.status), "partial");
    } else {
      snprintf(r.status, sizeof(r.status), "completed");
    }

    _reportTail = (_reportTail + 1) % MAX_PENDING_REPORTS;
    ++_reportCount;
  }
};

#endif  // REMINDER_SCHEDULER_H
