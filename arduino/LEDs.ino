/* This is a slightly changed copy of the FastLED 'Noise' example.
 *
 * This file's license is
 * 
 * The MIT License (MIT)
 * 
 * Copyright (c) 2013 FastLED
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

#include<FastLED.h>

const uint8_t kMatrixWidth  = 12;
const uint8_t kMatrixHeight = 1;
const bool    kMatrixSerpentineLayout = true;


// This example combines two features of FastLED to produce a remarkable range of
// effects from a relatively small amount of code.  This example combines FastLED's 
// color palette lookup functions with FastLED's Perlin/simplex noise generator, and
// the combination is extremely powerful.
//
// You might want to look at the "ColorPalette" and "Noise" examples separately
// if this example code seems daunting.
//
// 
// The basic setup here is that for each frame, we generate a new array of 
// 'noise' data, and then map it onto the LED matrix through a color palette.
//
// Periodically, the color palette is changed, and new noise-generation parameters
// are chosen at the same time.  In this example, specific noise-generation
// values have been selected to match the given color palettes; some are faster, 
// or slower, or larger, or smaller than others, but there's no reason these 
// parameters can't be freely mixed-and-matched.
//
// In addition, this example includes some fast automatic 'data smoothing' at 
// lower noise speeds to help produce smoother animations in those cases.
//
// The FastLED built-in color palettes (Forest, Clouds, Lava, Ocean, Party) are
// used, as well as some 'hand-defined' ones, and some proceedurally generated
// palettes.


#define NUM_LEDS (kMatrixWidth * kMatrixHeight)
#define MAX_DIMENSION ((kMatrixWidth>kMatrixHeight) ? kMatrixWidth : kMatrixHeight)

// The leds
CRGB leds[kMatrixWidth * kMatrixHeight];

// The 16 bit version of our coordinates
static uint16_t x;
static uint16_t y;
static uint16_t z;

// We're using the x/y dimensions to map to the x/y pixels on the matrix.  We'll
// use the z-axis for "time".  speed determines how fast time moves forward.  Try
// 1 for a very slow moving effect, or 60 for something that ends up looking like
// water.
//uint16_t speed = 20; // speed is set dynamically once we've started up

// Scale determines how far apart the pixels in our noise matrix are.  Try
// changing these values around to see how it affects the motion of the display.  The
// higher the value of scale, the more "zoomed out" the noise iwll be.  A value
// of 1 will be so zoomed in, you'll mostly see solid colors.
uint16_t scale = 30; // scale is set dynamically once we've started up

// This is the array that we keep our computed noise values in
uint8_t noise[MAX_DIMENSION][MAX_DIMENSION];

CRGBPalette16 currentPalette;
CRGBPalette16 targetPalette;
uint8_t       colorLoop = 1;

void setupLEDs() {
  FastLED.addLeds<LPD8806,A4,A5,BRG>(leds, NUM_LEDS).setCorrection(TypicalLEDStrip);
  fill_solid(leds, NUM_LEDS, CRGB(0,0,0));
  FastLED.setBrightness(brightness);
  FastLED.show();

  // Initialize our coordinates to some random values
  x = random16();
  y = random16();
  z = random16();

  ChangePaletteAndSettings();
  currentPalette = targetPalette;
}



// Fill the x/y array of 8-bit noise values using the inoise8 function.
void fillnoise8() {
  // If we're runing at a low "speed", some 8-bit artifacts become visible
  // from frame-to-frame.  In order to reduce this, we can do some fast data-smoothing.
  // The amount of data smoothing we're doing depends on "speed".
  uint8_t dataSmoothing = 0;
  if( speed < 50) {
    dataSmoothing = 200 - (speed * 4);
  }
  
  for(int i = 0; i < MAX_DIMENSION; i++) {
    int ioffset = scale * i;
    for(int j = 0; j < MAX_DIMENSION; j++) {
      int joffset = scale * j;
      
      uint8_t data = inoise8(x + ioffset,y + joffset,z);

      // The range of the inoise8 function is roughly 16-238.
      // These two operations expand those values out to roughly 0..255
      // You can comment them out if you want the raw noise data.
      data = qsub8(data,16);
      data = qadd8(data,scale8(data,39));

      if( dataSmoothing ) {
        uint8_t olddata = noise[i][j];
        uint8_t newdata = scale8( olddata, dataSmoothing) + scale8( data, 256 - dataSmoothing);
        data = newdata;
      }
      
      noise[i][j] = data;
    }
  }
  
  z += speed;
  
  // apply slow drift to X and Y, just for visual variation.
  x += speed / 8;
  y -= speed / 16;
}

void mapNoiseToLEDsUsingPalette()
{
  static uint8_t ihue=0;

  int enabledLedCount = constrain(kMatrixWidth * abs(amplitude) / 255, 0, kMatrixWidth); 
  int minI;
  int maxI;

  if (amplitude >= 0) {
    minI = 0;
    maxI = enabledLedCount - 1;
  } else {
    minI = kMatrixWidth - enabledLedCount;
    maxI = kMatrixWidth - 1;
  }
  
  for(int i = 0; i < kMatrixWidth; i++) {
    for(int j = 0; j < kMatrixHeight; j++) {
      if (i < minI || i > maxI) {
        leds[XY(i,j)] = CRGB(0,0,0);
        continue;
      }
      
      // We use the value at the (i,j) coordinate in the noise
      // array for our brightness, and the flipped value from (j,i)
      // for our pixel's index into the color palette.

      uint8_t index = noise[j][i];
      uint8_t bri =   noise[i][j];

      // if this palette is a 'loop', add a slowly-changing base value
      if( colorLoop) { 
        index += ihue;
      }

      // brighten up, as the color palette itself often contains the 
      // light/dark dynamic range desired
      if( bri > 127 ) {
        bri = 255;
      } else {
        bri = dim8_raw( bri * 2);
      }

      CRGB color = ColorFromPalette( currentPalette, index, bri);
      leds[XY(i,j)] = color;
    }
  }
  
  ihue+=1;
}

int shutdownBrightness = 0;
void loopLEDs() {
  if (time - lastCommandTime > 30) {
    if (shutdownBrightness < 255) {
      shutdownBrightness++;
      FastLED.setBrightness(constrain(brightness - shutdownBrightness, 0, 255));
    }
  } else if (shutdownBrightness > 0) {
    shutdownBrightness--;
    FastLED.setBrightness(constrain(brightness - shutdownBrightness, 0, 255));
  }
  
  // Periodically choose a new palette, speed, and scale
  ChangePaletteAndSettings();

  // generate noise data
  fillnoise8();
  
  // convert the noise data to colors in the LED array
  // using the current palette
  mapNoiseToLEDsUsingPalette();

  LEDS.show();
}

int wrap_hue(int h) {
  if (h < 0) {
    return h + 256;
  } else if (h > 255) {
    return h - 256;
  } else {
    return h;
  }
}

#define HUE_SHIFT 20

int color_to_hue(int c, int shift) {
  // we want a color wheel blue -> green -> red, which maps to HSB 165 -> 0 -> 208 in the FastLED rainbow HSB palette
  // so we have to map 256 values to (165 + 48) = 213, 
  // and leave additional space for the shift: 213 - 80 = 133; factor = 255 / 133 = 1.91
  int hue = shift * HUE_SHIFT + 165 - 2 * HUE_SHIFT - color / 1.91;
  return wrap_hue(hue);
}

int lastColor = -1;
void ChangePaletteAndSettings() {
  if (color != lastColor) {
    lastColor = color;
  
    if (color >= 0 && color <= 255)  { 
      int center_hsv = color_to_hue(color, 0);
      int below_hsv = color_to_hue(color, -1);
      int above_hsv = color_to_hue(color, +1);
      int min_hsv = color_to_hue(color, -2);
      int max_hsv = color_to_hue(color, +2);
      
      targetPalette = CRGBPalette16( 
                      CHSV(center_hsv, 255, 224), CHSV(center_hsv, 255, 255), CHSV(above_hsv, 255, 255), CHSV(center_hsv, 255, 255),
                      CHSV(center_hsv, 255, 255), CHSV(above_hsv, 255, 255), CHSV(max_hsv, 255, 224), CHSV(above_hsv, 255, 255),
                      CHSV(center_hsv, 255, 255), CHSV(below_hsv, 255, 255), CHSV(min_hsv, 255, 255), CHSV(below_hsv, 255, 255),
                      CHSV(below_hsv, 255, 224), CHSV(min_hsv, 255, 255), CHSV(min_hsv, 255, 255), CHSV(below_hsv, 255, 255)); 
    } else {
      targetPalette = PartyColors_p; 
    }
  }

  nblendPaletteTowardPalette(currentPalette, targetPalette, 16);
}

//
// Mark's xy coordinate mapping code.  See the XYMatrix for more information on it.
//
uint16_t XY( uint8_t x, uint8_t y)
{
  uint16_t i;
  if( kMatrixSerpentineLayout == false) {
    i = (y * kMatrixWidth) + x;
  }
  if( kMatrixSerpentineLayout == true) {
    if( y & 0x01) {
      // Odd rows run backwards
      uint8_t reverseX = (kMatrixWidth - 1) - x;
      i = (y * kMatrixWidth) + reverseX;
    } else {
      // Even rows run forwards
      i = (y * kMatrixWidth) + x;
    }
  }
  return i;
}

