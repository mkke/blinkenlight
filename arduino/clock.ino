#include <util/atomic.h>

/* see http://www.instructables.com/id/Make-an-accurate-Arduino-clock-using-only-one-wire/

We are using a PWM output (which has a base freq of 490Hz) to create a signal
and route that to the interrupt input.
*/

volatile int clockInterruptCount = 0;
volatile int tickInterruptCount = 0;
volatile uint64_t clockInterruptTime = 0; // <-- this is the time changed in the interrupt handler
volatile uint64_t clockInterruptTicks = 0; // <-- this is the time changed in the interrupt handler

void clockInterruptHandler() {
  tickInterruptCount++;
  if (tickInterruptCount >= 20) {
    clockInterruptTicks++;
    tickInterruptCount = 0;
  }
  clockInterruptCount++;
  if (clockInterruptCount >= ticksPerSecond) {
    clockInterruptTime++;
    clockInterruptCount = 0;
  }
}

void setupClockInterrupt() {
  pinMode(PIN_CLOCK_INTERRUPT_PWM, OUTPUT);
  pinMode(PIN_CLOCK_INTERRUPT_INT, INPUT);
  attachInterrupt(INT_CLOCK, clockInterruptHandler, CHANGE);
  analogWrite(PIN_CLOCK_INTERRUPT_PWM, 127);
}

uint64_t lastTicks = 0;
void loopClock() {
  // time is updated in interrupt, so might change while we derive values;
  // so make a copy
  ATOMIC_BLOCK( ATOMIC_RESTORESTATE ){
    time = clockInterruptTime;
    ticks = clockInterruptTicks;
  }
  
  if (ticks != lastTicks) {
    lastTicks = ticks;
    loopLEDs();
  }
}

