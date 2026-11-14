#ifndef DISPENSER_CONTROL_H
#define DISPENSER_CONTROL_H

#include <Arduino.h>
#include <Servo.h>
#include "config.h"

/**
 * Controls the NEMA 17 circular-tray stepper (via A4988 STEP/DIR),
 * single SG90 bottom drop-gate servo, IR tablet-drop counter, and BC547 buzzer
 * using non-blocking state machines.
 */
class DispenserControl {
 public:
  enum DispenseState : uint8_t {
    STATE_IDLE = 0,
    STATE_STEPPING,
    STATE_TRAY_SETTLE,
    STATE_GATE_OPENING,
    STATE_GATE_CLOSING,
    STATE_INTER_TABLET_WAIT,
    STATE_DONE
  };

  void begin() {
    pinMode(PIN_BUZZER, OUTPUT);
    digitalWrite(PIN_BUZZER, LOW);

    pinMode(PIN_STEPPER_STEP, OUTPUT);
    pinMode(PIN_STEPPER_DIR, OUTPUT);
    digitalWrite(PIN_STEPPER_STEP, LOW);
    digitalWrite(PIN_STEPPER_DIR, LOW);

    pinMode(PIN_IR_SENSOR, INPUT_PULLUP);
    resetIrDebounce();

    // Always close the bottom drop gate before any tray motion.
    _gateServo.attach(PIN_SERVO_GATE, SERVO_MIN_PULSE_US, SERVO_MAX_PULSE_US);
    _gateServo.write(GATE_CLOSED_ANGLE);
    delay(450);
    _gateServo.detach();

    _state = STATE_IDLE;
    _activeComp = -1;
    _currentStepPos = 0;
    _alarmActive = false;
    _beepActive = false;
  }

  void update() {
    updateIrSensor();
    updateMachine();
    updateBuzzer();
  }

  bool startDispense(uint8_t compIdx, uint8_t tabletsPerDose) {
    if (compIdx >= NUM_COMPARTMENTS || tabletsPerDose == 0 || _state != STATE_IDLE) {
      return false;
    }

    _activeComp = static_cast<int8_t>(compIdx);
    _scheduledQty = tabletsPerDose;
    _detectedQty = 0;
    _attemptInSlot = 0;
    _dropsInAttempt = 0;
    _attemptDropTimestamp = 0;
    _lastIrTriggerMs = 0;
    resetIrDebounce();

    int32_t targetStep =
        (static_cast<int32_t>(compIdx) * static_cast<int32_t>(STEPS_PER_REVOLUTION)) /
        NUM_COMPARTMENTS;
    int32_t diff = targetStep - _currentStepPos;
    int32_t halfRev = static_cast<int32_t>(STEPS_PER_REVOLUTION) / 2;
    if (diff > halfRev) {
      diff -= STEPS_PER_REVOLUTION;
    } else if (diff < -halfRev) {
      diff += STEPS_PER_REVOLUTION;
    }

    _stepDirPositive = (diff >= 0);
    _stepsRemaining = (diff >= 0) ? diff : -diff;
    digitalWrite(PIN_STEPPER_DIR, _stepDirPositive ? HIGH : LOW);

    _lastStepMs = millis();
    _stateTimestamp = _lastStepMs;

    if (_stepsRemaining > 0) {
      _state = STATE_STEPPING;
      Serial.print(F("[STEPPER] Moving "));
      Serial.print(_stepsRemaining);
      Serial.print(F(" steps to compartment "));
      Serial.println(compIdx + 1);
    } else {
      _state = STATE_TRAY_SETTLE;
      Serial.print(F("[STEPPER] Already aligned on compartment "));
      Serial.println(compIdx + 1);
    }
    return true;
  }

  bool isBusy() const {
    return _state != STATE_IDLE;
  }

  bool isStepping() const {
    return _state == STATE_STEPPING || _state == STATE_TRAY_SETTLE;
  }

  int8_t activeComp() const {
    return _activeComp;
  }

  uint8_t scheduledQty() const {
    return _scheduledQty;
  }

  uint8_t detectedQty() const {
    return _detectedQty;
  }

  bool isIrBeamBlocked() const {
    return _irStableBlocked;
  }

  bool consumeDispenseResult(uint8_t& compIdx, uint8_t& scheduledQty, uint8_t& detectedQty) {
    if (_state != STATE_DONE || _activeComp < 0) {
      return false;
    }

    compIdx = static_cast<uint8_t>(_activeComp);
    scheduledQty = _scheduledQty;
    detectedQty = _detectedQty;

    _activeComp = -1;
    _scheduledQty = 0;
    _detectedQty = 0;
    _state = STATE_IDLE;
    return true;
  }

  void nudgeStepper(int32_t steps) {
    if (_state != STATE_IDLE || steps == 0) {
      return;
    }
    bool dirPos = (steps > 0);
    int32_t count = dirPos ? steps : -steps;
    digitalWrite(PIN_STEPPER_DIR, dirPos ? HIGH : LOW);
    for (int32_t i = 0; i < count; ++i) {
      digitalWrite(PIN_STEPPER_STEP, HIGH);
      delayMicroseconds(STEP_PULSE_US);
      digitalWrite(PIN_STEPPER_STEP, LOW);
      delay(STEP_INTERVAL_MS);
    }
  }

  void zeroStepperHome() {
    _currentStepPos = 0;
    Serial.println(F("[STEPPER] Current position set as Compartment 1 home (step 0)."));
  }

  void startAlarm() {
    _beepActive = false;
    _alarmActive = true;
    _buzzerOutputHigh = true;
    _buzzerTimestamp = millis();
    digitalWrite(PIN_BUZZER, HIGH);
  }

  void stopAlarm() {
    _alarmActive = false;
    _beepActive = false;
    _buzzerOutputHigh = false;
    digitalWrite(PIN_BUZZER, LOW);
  }

  bool isAlarmActive() const {
    return _alarmActive;
  }

  void beep(uint16_t durationMs = 80) {
    if (_alarmActive || durationMs == 0) {
      return;
    }
    _beepActive = true;
    _beepDurationMs = durationMs;
    _beepTimestamp = millis();
    _buzzerOutputHigh = true;
    digitalWrite(PIN_BUZZER, HIGH);
  }

 private:
  Servo _gateServo;
  DispenseState _state = STATE_IDLE;
  int8_t _activeComp = -1;

  int32_t _currentStepPos = 0;
  int32_t _stepsRemaining = 0;
  bool _stepDirPositive = true;
  unsigned long _lastStepMs = 0;

  uint8_t _scheduledQty = 0;
  uint8_t _detectedQty = 0;
  uint8_t _attemptInSlot = 0;
  uint8_t _dropsInAttempt = 0;
  unsigned long _attemptDropTimestamp = 0;
  unsigned long _stateTimestamp = 0;

  bool _irLastRaw = false;
  bool _irStableBlocked = false;
  unsigned long _irLastChangeMs = 0;
  unsigned long _lastIrTriggerMs = 0;

  bool _alarmActive = false;
  bool _buzzerOutputHigh = false;
  unsigned long _buzzerTimestamp = 0;

  bool _beepActive = false;
  uint16_t _beepDurationMs = 0;
  unsigned long _beepTimestamp = 0;

  void resetIrDebounce() {
    bool blocked = (digitalRead(PIN_IR_SENSOR) == IR_ACTIVE_STATE);
    _irLastRaw = blocked;
    _irStableBlocked = blocked;
    _irLastChangeMs = millis();
  }

  void updateIrSensor() {
    bool rawBlocked = (digitalRead(PIN_IR_SENSOR) == IR_ACTIVE_STATE);
    unsigned long nowMs = millis();

    if (rawBlocked != _irLastRaw) {
      _irLastRaw = rawBlocked;
      _irLastChangeMs = nowMs;
    }

    if ((nowMs - _irLastChangeMs) >= IR_DEBOUNCE_MS && rawBlocked != _irStableBlocked) {
      _irStableBlocked = rawBlocked;

      if (_irStableBlocked &&
          (_state == STATE_GATE_OPENING || _state == STATE_GATE_CLOSING)) {
        if (_lastIrTriggerMs == 0 || (nowMs - _lastIrTriggerMs) >= IR_RETRIGGER_HOLDOFF_MS) {
          ++_dropsInAttempt;
          ++_detectedQty;
          _lastIrTriggerMs = nowMs;
          if (_attemptDropTimestamp == 0) {
            _attemptDropTimestamp = nowMs;
          }
          Serial.print(F("[IR] Tablet detected! Verified count = "));
          Serial.print(_detectedQty);
          Serial.print('/');
          Serial.println(_scheduledQty);
        }
      }
    }
  }

  void openGateAttempt() {
    _dropsInAttempt = 0;
    _attemptDropTimestamp = 0;
    ++_attemptInSlot;

    _gateServo.attach(PIN_SERVO_GATE, SERVO_MIN_PULSE_US, SERVO_MAX_PULSE_US);
    _gateServo.write(GATE_OPEN_ANGLE);
    _stateTimestamp = millis();
    _state = STATE_GATE_OPENING;

    Serial.print(F("[GATE] Opening drop gate (attempt "));
    Serial.print(_attemptInSlot);
    Serial.print(F(", detected "));
    Serial.print(_detectedQty);
    Serial.print('/');
    Serial.print(_scheduledQty);
    Serial.println(')');
  }

  void updateMachine() {
    if (_activeComp < 0 || _activeComp >= NUM_COMPARTMENTS) {
      return;
    }

    unsigned long nowMs = millis();

    switch (_state) {
      case STATE_IDLE:
      case STATE_DONE:
        break;

      case STATE_STEPPING:
        if (_stepsRemaining <= 0) {
          _stateTimestamp = nowMs;
          _state = STATE_TRAY_SETTLE;
          break;
        }
        if (nowMs - _lastStepMs >= STEP_INTERVAL_MS) {
          _lastStepMs = nowMs;
          digitalWrite(PIN_STEPPER_STEP, HIGH);
          delayMicroseconds(STEP_PULSE_US);
          digitalWrite(PIN_STEPPER_STEP, LOW);

          --_stepsRemaining;
          if (_stepDirPositive) {
            _currentStepPos = (_currentStepPos + 1) % STEPS_PER_REVOLUTION;
          } else {
            _currentStepPos = (_currentStepPos - 1 + STEPS_PER_REVOLUTION) % STEPS_PER_REVOLUTION;
          }

          if (_stepsRemaining == 0) {
            _stateTimestamp = nowMs;
            _state = STATE_TRAY_SETTLE;
          }
        }
        break;

      case STATE_TRAY_SETTLE:
        if (nowMs - _stateTimestamp >= STEPPER_SETTLE_MS) {
          openGateAttempt();
        }
        break;

      case STATE_GATE_OPENING: {
        bool earlyClose = (_dropsInAttempt > 0) &&
                          (nowMs - _attemptDropTimestamp >= GATE_EARLY_CLOSE_DELAY_MS);
        bool timedOut = (nowMs - _stateTimestamp >= GATE_OPEN_DURATION_MS);

        if (earlyClose || timedOut) {
          _gateServo.write(GATE_CLOSED_ANGLE);
          _stateTimestamp = nowMs;
          _state = STATE_GATE_CLOSING;
        }
        break;
      }

      case STATE_GATE_CLOSING:
        if (nowMs - _stateTimestamp >= SERVO_SETTLE_DURATION_MS) {
          _gateServo.detach();

          if (_detectedQty >= _scheduledQty) {
            _state = STATE_DONE;
            Serial.print(F("[DISPENSER] Complete! Verified "));
            Serial.print(_detectedQty);
            Serial.print('/');
            Serial.println(_scheduledQty);
          } else if (_dropsInAttempt > 0) {
            _attemptInSlot = 0;
            _stateTimestamp = nowMs;
            _state = STATE_INTER_TABLET_WAIT;
          } else if (_attemptInSlot < MAX_ATTEMPTS_PER_TABLET) {
            _stateTimestamp = nowMs;
            _state = STATE_INTER_TABLET_WAIT;
          } else {
            _state = STATE_DONE;
            Serial.print(F("[DISPENSER] Finished with partial/missed drop: "));
            Serial.print(_detectedQty);
            Serial.print('/');
            Serial.println(_scheduledQty);
          }
        }
        break;

      case STATE_INTER_TABLET_WAIT:
        if (nowMs - _stateTimestamp >= INTER_TABLET_PAUSE_MS) {
          openGateAttempt();
        }
        break;
    }
  }

  void updateBuzzer() {
    unsigned long nowMs = millis();

    if (_alarmActive) {
      unsigned long interval = _buzzerOutputHigh ? BUZZER_ON_MS : BUZZER_OFF_MS;
      if (nowMs - _buzzerTimestamp >= interval) {
        _buzzerOutputHigh = !_buzzerOutputHigh;
        _buzzerTimestamp = nowMs;
        digitalWrite(PIN_BUZZER, _buzzerOutputHigh ? HIGH : LOW);
      }
      return;
    }

    if (_beepActive && (nowMs - _beepTimestamp >= _beepDurationMs)) {
      _beepActive = false;
      _buzzerOutputHigh = false;
      digitalWrite(PIN_BUZZER, LOW);
    }
  }
};

#endif  // DISPENSER_CONTROL_H
