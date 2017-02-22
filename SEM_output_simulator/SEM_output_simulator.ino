int vSyncPin = 8;
int hSyncPin = 9;
int lLED = 13;
int buttonPin = 2;
int signalPin = 3;
volatile boolean fChange = false; 

int pulseDownTime = 50; // Micros, can't be higher than 150

uint8_t lineBrightness = 0;
int freqIndex = 0;
#define NUM_MODES 2
int freqs[NUM_MODES][4] = {
//  {0, 150 - pulseDownTime, 266, 250},// no workie, crashes scanner
  {4, 1000 - pulseDownTime, 1000, 2},
  {39, 1000 - pulseDownTime, 2500, 1}
  };

void setup() {
  pinMode (vSyncPin, OUTPUT);
  pinMode (hSyncPin, OUTPUT);
  pinMode (lLED, OUTPUT);
  pinMode (buttonPin, INPUT_PULLUP);
  pinMode (signalPin, OUTPUT);
  
  digitalWrite(vSyncPin, HIGH);
  digitalWrite(hSyncPin, HIGH);
  digitalWrite(lLED, LOW);

  attachInterrupt(buttonPin, changeFreq, FALLING);
}

void loop() {
    static int frames = 0;
    int lineStart;
    int lineTime;
    int endLineTime = (freqs[freqIndex][0]*1000) +freqs[freqIndex][1];
    
    for (int i = 0; i < freqs[freqIndex][2]; i++) {
      lineStart = micros();
      analogWrite(signalPin, lineBrightness);
      lineBrightness++;
      pulsePin(hSyncPin, pulseDownTime);
      do {
        lineTime = micros()-lineStart;
        testPattern ((lineTime*100)/endLineTime, (i*100)/freqs[freqIndex][2]);
      } while (lineTime < endLineTime);
      
      //delay(freqs[freqIndex][0]);
      //delayMicroseconds(freqs[freqIndex][1]);
      if (fChange) {
        break;
      }
    }
    digitalWrite(LED_BUILTIN, HIGH);
    pulsePin(vSyncPin, 10*pulseDownTime);
    digitalWrite(LED_BUILTIN, LOW);

    if (++frames > freqs[freqIndex][3] || fChange) {
      freqIndex = (freqIndex + 1) % NUM_MODES;
      frames = 0;
      fChange = false;
      blinkLED(2);
      delay(2000);
    }
}

void blinkLED(int n) {
  
  for (int i= 0; i<n; i++) {
    // blink built-in LED
    digitalWrite(LED_BUILTIN, HIGH);
    delay(200);
    digitalWrite(LED_BUILTIN, LOW);
    if(i < n-1) {
      delay(200);
    }
  }
}

void pulsePin(int pin, int uS) { 
  digitalWrite(pin, LOW);
  delayMicroseconds(uS);
  digitalWrite(pin, HIGH);
}

void changeFreq() {
  if (digitalRead(buttonPin) == LOW) {
    fChange = true;
  }
}



void testPattern(int timePercent, int linesPercent) {

  if (getPixel(timePercent, linesPercent)) {
    analogWrite(DAC0, 255);
    analogWrite(DAC1, 255);
  } else {
    analogWrite(DAC0,((linesPercent*256)/100) % 256);
    analogWrite(DAC1, 256-(((linesPercent*256)/100) % 256));
  }
  
}

bool getPixel(int x, int y) {
  const int xMin = 10;
  const int xMax = 75;
  const int yMin = 35;
  const int yMax = 60;

  const int TEST_NUM_RANGES = 5;

  int range0[] = {35, 40, 6, 10, 25, 30, 45, 50, 75};
  int range1[] = {40, 45,10, 10, 15, 30, 35, 50, 55, 60, 65, 70, 75};
  int range2[] = {45, 50,10, 10, 25, 30, 40, 50, 55, 60, 65, 70, 75};
  int range3[] = {50, 55, 8, 20, 25, 30, 35, 50, 55, 70, 75};
  int range4[] = {55, 60, 8, 10, 25, 30, 45, 50, 55, 70, 75};
  int *ranges[TEST_NUM_RANGES] = { range0, range1, range2, range3, range4 };

  if (x < xMin || x > xMax || y < yMin || y > yMax) {
    return false;
  }
 
  bool white = false;
  
  for (int i = 0; i < TEST_NUM_RANGES; i++) {
    int *r = ranges[i];
    if (y >= r[0] && y < r[1]) {
      for (int j = 3; j < r[2] + 3; j++) {
        if (x < r[j]) {
          return white;
        }
        white = !white;
      }
      return white;
    }
  }
  return white;
}


