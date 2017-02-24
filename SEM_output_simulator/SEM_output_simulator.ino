int vSyncPin = 8;
int hSyncPin = 9;
int lLED = 13;
int buttonPin = 45;
int signalPin = 3;
int signalPin2 = 4;
const int range = 4096; // DAC range for AnagogWrite
volatile boolean fChange = false; 

int freqIndex = 0;
struct Resolution {
  long usHSync;
  long usHPulseTime;
  long lines;
  long usVPulseTime;
  long frames;
};

#define NUM_MODES 3
struct Resolution freqs[NUM_MODES] = {
  {  160,   50,  266, 3000, 20},// no workie, crashes scanner
  { 5790,  500,  865, 8000,  5},
  {33326, 4500, 3000, 4500,  1}  
  };


long lineTime;
long lineStart;
long endLineTime;
long currentLine;
long frames = 0;


void setup() {
  frames = 0;
  
  analogWriteResolution(12);
  pinMode (vSyncPin, OUTPUT);
  pinMode (hSyncPin, OUTPUT);
  pinMode (lLED, OUTPUT);
  pinMode (buttonPin, INPUT_PULLUP);
  pinMode (signalPin, OUTPUT);
  pinMode (signalPin2, OUTPUT);
  
  digitalWrite(vSyncPin, HIGH);
  digitalWrite(hSyncPin, HIGH);
  digitalWrite(lLED, LOW);

  attachInterrupt(buttonPin, changeFreq, FALLING);
}

void loop() {
    
    endLineTime = freqs[freqIndex].usHSync;
    
    for (currentLine = 0; currentLine < freqs[freqIndex].lines; currentLine++) {
      lineStart = micros();
      pulsePin(hSyncPin, freqs[freqIndex].usHPulseTime);
      do {
        lineTime = micros()-lineStart;
        testPattern ();
      } while (lineTime < endLineTime);
      
      if (fChange) {
        break;
      }
    }
    digitalWrite(LED_BUILTIN, HIGH);
    pulsePin(vSyncPin, freqs[freqIndex].usVPulseTime);
    digitalWrite(LED_BUILTIN, LOW);

    ++frames;
    if (frames >= freqs[freqIndex].frames || fChange) {
      freqIndex = (freqIndex + 1) % NUM_MODES;
      frames = 0;
      blinkLED(2);
      delay(1000);
      fChange = false;
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

void pulsePin(int pin, long uS) { 
  digitalWrite(pin, LOW);
  long timeStart = micros();
  long timeEnd = timeStart + uS;
  while (micros() < timeEnd) {
    lineTime = micros()-lineStart;
    testPattern();
  }
  digitalWrite(pin, HIGH);
}

void changeFreq() {
  if (digitalRead(buttonPin) == LOW) {
    fChange = true;
  }
}



void testPattern() {
  long timePercent;
  long linesPercent;
  
  timePercent = (lineTime*100)/endLineTime;
  linesPercent = (currentLine*100)/freqs[freqIndex].lines;

  if (getPixel(timePercent, linesPercent)) {
    // "SEM" text
    digitalWrite(signalPin, HIGH);
    digitalWrite(signalPin2, HIGH);
    analogWrite(DAC0, range-1);
    analogWrite(DAC1, range-1);
  } else {
    if (linesPercent > 65 && linesPercent < 70) {
      // channel marker
      digitalWrite(signalPin, timePercent > 10 && timePercent < 20 ? HIGH: LOW);
      digitalWrite(signalPin2, timePercent > 28 && timePercent < 38? HIGH: LOW);
      analogWrite(DAC0, timePercent > 48 && timePercent < 58? range-1: 0);
      analogWrite(DAC1, timePercent > 65 && timePercent < 75? range-1: 0);
    } else {
      // pattern unique to channel
      analogWrite(signalPin, ((linesPercent*range)/100)%range);
      analogWrite(signalPin2, ((linesPercent*2*range)/100)%range);
      analogWrite(DAC0,((linesPercent*(range-1))/100));
      analogWrite(DAC1, (((100-linesPercent)*(range-1))/100));
    }
  }
  
}

bool getPixel(long x, long y) {
  const long xMin = 10;
  const long xMax = 75;
  const long yMin = 35;
  const long yMax = 60;

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


