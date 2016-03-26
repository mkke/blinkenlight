char line[256] = {0};
int lineLen = 0;
void loopSerial() {
  // don't use readBytesUntil, beause it blocks and disturbs LED updates
  while (Serial.available() > 0) {
    int v = Serial.read();
    if (v == '\n') {
      if (lineLen > 0) {
        line[lineLen] = '\0';
        processCommand((char*) &line); 
      }
      lineLen = 0;
    } else if (lineLen < 255) {
      line[lineLen] = v;
      lineLen++;
    }
  }
}

void processCommand(char* line) {
  // line length is guaranteed to be > 0
  switch (line[0]) {
  case 'V':
    Serial.println("V Blinkenlight 1.0");
    break;
  case 'S': {
    if (line[1] != '?') {
      int newSpeed;
      int count = sscanf((const char*)&line[1], "%d", &newSpeed);
      if (count >= 1 && newSpeed >= MIN_SPEED && newSpeed <= MAX_SPEED) {
        setSpeed(newSpeed);
      }
    }
    char speedinfo_s[60] = "";
    sprintf(speedinfo_s, "S%d", speed);
    Serial.println(speedinfo_s);
    }
    break;
  case 'C': {
    if (line[1] != '?') {
      int newColor;
      int count = sscanf((const char*)&line[1], "%d", &newColor);
      if (count >= 1 && newColor >= MIN_COLOR && newColor <= MAX_COLOR) {
        setColor(newColor);
      }
    }
    char colorinfo_s[60] = "";
    sprintf(colorinfo_s, "C%d", color);
    Serial.println(colorinfo_s);
    }
    break;
  case 'B': {
    if (line[1] != '?') {
      int newBrightness;
      int count = sscanf((const char*)&line[1], "%d", &newBrightness);
      if (count >= 1 && newBrightness >= 1 && newBrightness <= 255) {
        setBrightness(newBrightness);
      }
    }
    char info_s[60] = "";
    sprintf(info_s, "B%d", brightness);
    Serial.println(info_s);
    }
    break;
  case 'I': // idle
    Serial.println("Idle");
    break;
  default:
    Serial.println("* Error");
  }
  lastCommandTime = time;
}

