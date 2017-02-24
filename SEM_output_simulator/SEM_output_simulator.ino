int vSyncPin = 8;
int hSyncPin = 9;
int lLED = 13;
int buttonPin = 2;
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

#define NUM_MODES 1
struct Resolution freqs[2] = {
//    {160, 50, 3000, 266, 10},// no workie, crashes scanner
    {4500, 500, 1110, 8000, 20}
//  {25000, 4500, 4000, 1}  //
  };

void setup() {
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
    static long frames = 0;
    long lineStart;
    long lineTime;
    long endLineTime = freqs[freqIndex].usHSync;
    
    for (long i = 0; i < freqs[freqIndex].lines; i++) {
      lineStart = micros();
      pulsePin(hSyncPin, freqs[freqIndex].usHPulseTime);
      do {
        lineTime = micros()-lineStart;
        testPattern ((lineTime*100)/endLineTime, (i*100)/freqs[freqIndex].lines);
      } while (lineTime < endLineTime);
      
      if (fChange) {
        break;
      }
    }
    digitalWrite(LED_BUILTIN, HIGH);
    pulsePin(vSyncPin, freqs[freqIndex].usVPulseTime);
    digitalWrite(LED_BUILTIN, LOW);

    if (++frames > freqs[freqIndex].frames || fChange) {
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

void pulsePin(int pin, long uS) { 
  digitalWrite(pin, LOW);
  delayMicroseconds(uS);
  digitalWrite(pin, HIGH);
}

void changeFreq() {
  if (digitalRead(buttonPin) == LOW) {
    fChange = true;
  }
}



void testPattern(long timePercent, long linesPercent) {

  if (getPixel(timePercent, linesPercent)) {
    digitalWrite(signalPin, HIGH);
    digitalWrite(signalPin2, HIGH);
    analogWrite(DAC0, range-1);
    analogWrite(DAC1, range-1);
  } else {
    analogWrite(signalPin, ((linesPercent*range)/100)%range);
    analogWrite(signalPin2, ((linesPercent*2*range)/100)%range);
    analogWrite(DAC0,((linesPercent*range)/1000) % range);
    analogWrite(DAC1, ((linesPercent*range)/500) % range);
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


