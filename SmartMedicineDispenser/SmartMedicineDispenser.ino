#include <Arduino.h>
#include "config.h"
#include "rtc_manager.h"
#include "lcd_manager.h"
#include "dispenser_control.h"
#include "reminder_scheduler.h"
#include "firebase_client.h"

namespace {

struct DebouncedButton {
  uint8_t pin;
  bool lastRaw;
  bool stableState;
  unsigned long lastChangeMs;
  unsigned long pressedSinceMs;
  bool longPressHandled;

  void init(uint8_t inputPin) {
    pin = inputPin;
    pinMode(pin, INPUT_PULLUP);
    resetSample();
  }

  void resetSample() {
    bool current = (digitalRead(pin) == HIGH);
    lastRaw = current;
    stableState = current;
    lastChangeMs = millis();
    pressedSinceMs = current ? 0 : millis();
    longPressHandled = false;
  }

  void poll(bool& fellEdge, bool& roseEdge) {
    fellEdge = false;
    roseEdge = false;

    bool raw = (digitalRead(pin) == HIGH);
    unsigned long nowMs = millis();

    if (raw != lastRaw) {
      lastRaw = raw;
      lastChangeMs = nowMs;
    }

    if ((nowMs - lastChangeMs) >= BTN_DEBOUNCE_MS && raw != stableState) {
      stableState = raw;
      if (!stableState) {
        pressedSinceMs = nowMs;
        longPressHandled = false;
        fellEdge = true;
      } else {
        roseEdge = true;
      }
    }
  }

  bool isPressed() const {
    return !stableState;
  }
};

RtcManager rtcManager;
LcdManager lcdManager;
DispenserControl dispenser;
ReminderScheduler scheduler;
FirebaseClient firebaseClient;

DebouncedButton btnAck;
bool buttonsArmed = false;

char serialLine[96];
size_t serialLen = 0;

void printHelp() {
  Serial.println(F("\n--- 6-Slot Smart Medicine Dispenser Commands ---"));
  Serial.println(F("  HELP                          Show available commands"));
  Serial.println(F("  STATUS                        Print RTC, Wi-Fi, compartments & schedules"));
  Serial.println(F("  DISPENSE <1..6> [qty]         Rotate stepper, dispense & count IR drops"));
  Serial.println(F("  STEP <+/-steps>               Nudge NEMA 17 stepper for tray alignment"));
  Serial.println(F("  HOME                          Set current tray position as Compartment 1"));
  Serial.println(F("  SYNC                          Sync schedules & stock from Firebase now"));
  Serial.println(F("  SETCOMP <1..6> <qty> [lim]    Set local compartment stock & low limit"));
  Serial.println(F("  RESET                         Clear today's dispensed schedule flags"));
  Serial.println(F("  SET YYYY-MM-DD HH:MM:SS       Set DS3231 RTC date & time"));
  Serial.println(F("------------------------------------------------\n"));
}

void printStatus() {
  Serial.println(F("\n=== 6-Slot Dispenser Status ==="));
  Serial.print(F("RTC Health  : "));
  Serial.println(rtcManager.isHealthy() ? F("OK") : F("ERROR"));

  if (rtcManager.isHealthy()) {
    DateTime dt = rtcManager.now();
    char buf[32];
    snprintf(buf, sizeof(buf), "%04u-%02u-%02u %02u:%02u:%02u",
             dt.year(), dt.month(), dt.day(),
             dt.hour(), dt.minute(), dt.second());
    Serial.print(F("RTC Time    : "));
    Serial.println(buf);
  }

  Serial.print(F("Wi-Fi       : "));
  if (firebaseClient.isConnected()) {
    Serial.print(F("Connected ("));
    Serial.print(WiFi.localIP());
    Serial.println(')');
  } else {
    Serial.println(F("Disconnected"));
  }

  Serial.print(F("IR Sensor   : "));
  Serial.println(dispenser.isIrBeamBlocked() ? F("BLOCKED") : F("Clear"));
  Serial.print(F("State       : "));
  Serial.println(scheduler.dispensingStatusString());
  Serial.print(F("Sync Queue  : "));
  Serial.print(scheduler.pendingReportCount());
  Serial.println(F(" pending report(s)"));

  Serial.println(F("Compartments:"));
  for (uint8_t i = 0; i < NUM_COMPARTMENTS; ++i) {
    const ReminderScheduler::CompartmentData& c = scheduler.compartment(i);
    char line[96];
    snprintf(line, sizeof(line), "  [C%u] %-20s  Remaining=%3u  LowLimit=%2u",
             static_cast<unsigned>(i + 1),
             c.medicineName,
             c.remainingQuantity,
             c.lowStockLimit);
    Serial.println(line);
  }

  Serial.println(F("Schedules   :"));
  uint8_t count = scheduler.scheduleCount();
  if (count == 0) {
    Serial.println(F("  (none)"));
  }
  for (uint8_t i = 0; i < count && i < MAX_SCHEDULES; ++i) {
    const ReminderScheduler::ScheduleEntry& s = scheduler.schedule(i);
    char line[96];
    snprintf(line, sizeof(line),
             "  (%2u) C%u  %02u:%02u  qty=%u  %-8s  %-9s  id=%s",
             static_cast<unsigned>(i + 1),
             s.compartmentId,
             s.hour,
             s.minute,
             s.tabletsPerDose,
             s.enabled ? "ENABLED" : "DISABLED",
             scheduler.isScheduleDispensed(i) ? "DISPENSED" : "PENDING",
             s.scheduleId);
    Serial.println(line);
  }
  Serial.println(F("===============================\n"));
}

void handleSetTimeCommand(const char* args) {
  unsigned int y = 0, m = 0, d = 0, hh = 0, mm = 0, ss = 0;
  if (sscanf(args, "%u-%u-%u %u:%u:%u", &y, &m, &d, &hh, &mm, &ss) != 6) {
    Serial.println(F("[CMD] Invalid format. Use: SET YYYY-MM-DD HH:MM:SS"));
    return;
  }

  DateTime candidate(static_cast<uint16_t>(y),
                     static_cast<uint8_t>(m),
                     static_cast<uint8_t>(d),
                     static_cast<uint8_t>(hh),
                     static_cast<uint8_t>(mm),
                     static_cast<uint8_t>(ss));

  if (!RtcManager::isValidDateTime(candidate)) {
    Serial.println(F("[CMD] Date/time out of valid range (year 2024..2099)."));
    return;
  }

  if (rtcManager.setDateTime(candidate)) {
    Serial.println(F("[CMD] DS3231 RTC updated successfully."));
    lcdManager.showToast("RTC Time Set!", "Clock Updated", 2000UL);
  } else {
    Serial.println(F("[CMD] Failed to update DS3231 RTC (check I2C wiring)."));
  }
}

void handleDispenseCommand(const char* args) {
  int slot = 0;
  int qty = 1;
  int parsed = sscanf(args, "%d %d", &slot, &qty);
  if (parsed < 1 || slot < 1 || slot > NUM_COMPARTMENTS || qty < 1 || qty > 10) {
    Serial.println(F("[CMD] Usage: DISPENSE <1..6> [qty 1..10]"));
    return;
  }
  scheduler.triggerManualDispense(static_cast<uint8_t>(slot - 1),
                                  static_cast<uint8_t>(qty),
                                  true);
}

void handleSetCompCommand(const char* args) {
  int slot = 0, qty = -1, lim = -1;
  int parsed = sscanf(args, "%d %d %d", &slot, &qty, &lim);
  if (parsed < 2 || slot < 1 || slot > NUM_COMPARTMENTS || qty < 0 || qty > 999) {
    Serial.println(F("[CMD] Usage: SETCOMP <1..6> <qty> [lowLimit]"));
    return;
  }
  uint8_t idx = static_cast<uint8_t>(slot - 1);
  const ReminderScheduler::CompartmentData& cur = scheduler.compartment(idx);
  uint16_t newLim = (parsed >= 3 && lim >= 0) ? static_cast<uint16_t>(lim) : cur.lowStockLimit;
  scheduler.setCompartmentLocal(idx, cur.medicineName, static_cast<uint16_t>(qty), newLim);
  printStatus();
}

void processSerialCommand(char* cmd) {
  while (*cmd == ' ' || *cmd == '\t') {
    ++cmd;
  }
  size_t len = strlen(cmd);
  while (len > 0 && (cmd[len - 1] == ' ' || cmd[len - 1] == '\t' ||
                     cmd[len - 1] == '\r' || cmd[len - 1] == '\n')) {
    cmd[--len] = '\0';
  }
  if (len == 0) {
    return;
  }

  if (strcasecmp(cmd, "HELP") == 0) {
    printHelp();
  } else if (strcasecmp(cmd, "STATUS") == 0) {
    printStatus();
  } else if (strcasecmp(cmd, "RESET") == 0) {
    scheduler.resetTodayHistory();
  } else if (strcasecmp(cmd, "HOME") == 0) {
    dispenser.zeroStepperHome();
  } else if (strcasecmp(cmd, "SYNC") == 0) {
    firebaseClient.requestImmediateSync();
    Serial.println(F("[CMD] Requested immediate Firebase sync."));
  } else if (strncasecmp(cmd, "DISPENSE", 8) == 0 && (cmd[8] == ' ' || cmd[8] == '\t')) {
    handleDispenseCommand(cmd + 9);
  } else if (strncasecmp(cmd, "STEP", 4) == 0 && (cmd[4] == ' ' || cmd[4] == '\t')) {
    long steps = atol(cmd + 5);
    dispenser.nudgeStepper(static_cast<int32_t>(steps));
  } else if (strncasecmp(cmd, "SETCOMP", 7) == 0 && (cmd[7] == ' ' || cmd[7] == '\t')) {
    handleSetCompCommand(cmd + 8);
  } else if (strncasecmp(cmd, "SET", 3) == 0 && (cmd[3] == ' ' || cmd[3] == '\t')) {
    handleSetTimeCommand(cmd + 4);
  } else {
    Serial.print(F("[CMD] Unknown command: "));
    Serial.println(cmd);
    printHelp();
  }
}

void pollSerial() {
  while (Serial.available() > 0) {
    char c = static_cast<char>(Serial.read());
    if (c == '\r' || c == '\n') {
      if (serialLen > 0) {
        serialLine[serialLen] = '\0';
        processSerialCommand(serialLine);
        serialLen = 0;
      }
    } else if (serialLen < sizeof(serialLine) - 1) {
      serialLine[serialLen++] = c;
    }
  }
}

void pollButtons() {
  if (!buttonsArmed) {
    if (millis() >= BTN_BOOT_GUARD_MS) {
      btnAck.resetSample();
      buttonsArmed = true;
    }
    return;
  }

  bool ackPressed = false;
  bool ackReleased = false;
  btnAck.poll(ackPressed, ackReleased);

  if (btnAck.isPressed() && !btnAck.longPressHandled &&
      (millis() - btnAck.pressedSinceMs >= BTN_LONG_PRESS_MS)) {
    btnAck.longPressHandled = true;
    scheduler.resetTodayHistory();
    dispenser.beep(150);
    return;
  }

  if (ackReleased && !btnAck.longPressHandled) {
    if (!scheduler.acknowledgeReminder()) {
      firebaseClient.requestImmediateSync();
    }
  }
}

}  // namespace

void setup() {
  Serial.begin(SERIAL_BAUD_RATE);
  delay(200);
  Serial.println(F("\nSmart Medicine 6-Slot Firebase Dispenser Starting..."));

  lcdManager.begin();
  lcdManager.showStartupBanner();

  rtcManager.begin();
  dispenser.begin();
  scheduler.begin(rtcManager, dispenser, lcdManager);
  firebaseClient.begin(rtcManager, dispenser, scheduler);

  btnAck.init(PIN_BTN_ACK);

  delay(1000);
  printHelp();
  printStatus();
}

void loop() {
  rtcManager.update();
  lcdManager.update();
  dispenser.update();
  scheduler.update();
  firebaseClient.update();
  pollButtons();
  pollSerial();
}
