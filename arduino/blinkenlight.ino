/*
 * Blinkenlight - 2016 by <ms@mallorn.de>
 * 
 * for Arduino Nano 5V
 * 
 * Hardware config:
 * - LED I2C: A4 (SDA) green, A5 (SCL) red
 * - Timer interrupt: connect 3 - 9
 */

#include <errno.h>
#include <util/atomic.h>
#include <FastLED.h>
#include <EEPROM.h>

#define PIN_CLOCK_INTERRUPT_PWM 9
#define PIN_CLOCK_INTERRUPT_INT 3

#define INT_CLOCK 1
 
#define ticksPerSecond 979  // 979 for most PWM pins

#define EEPROM_BRIGHTNESS    0

#define MIN_SPEED 1
#define MAX_SPEED 255

#define MIN_COLOR 0
#define MAX_COLOR 256

/* clock values, see tab 'clock' */
uint64_t time = 0; // <-- this is the time copied in loop() from clockInterruptTime, so code can rely on it not to change within a loop
uint64_t ticks = 0;
uint64_t lastCommandTime = 0;

uint16_t speed = 1;
int color = 0;
int brightness = 255;

void setBrightness(int newBrightness) {
  if (brightness != newBrightness) {
    brightness = newBrightness;
    EEPROM.write(EEPROM_BRIGHTNESS, newBrightness);
  }
  FastLED.setBrightness(brightness);
}

void setSpeed(uint16_t newSpeed) {
  speed = newSpeed;
}

void setColor(int newColor) {
  color = newColor;
}

void setup() {
  Serial.begin(57600);
  Serial.println("* Blinkenlight 1.0 READY");

  brightness = EEPROM.read(EEPROM_BRIGHTNESS);

  setupClockInterrupt();
  setupLEDs();
}

void loop() {
  loopClock();
  loopSerial();
}
