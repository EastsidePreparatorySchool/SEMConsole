int vSyncPin = 8;
int hSyncPin = 9;
int lLED = 13;
int buttonPin = 2;
int signalPin = 3;
volatile boolean fChange = false; 

int pulseDownTime = 50; // Micros, can't be higher than 150

uint8_t lineBrightness = 0;
int freqIndex = 0;
int freqs[3][4] = {
  {0, 150 - pulseDownTime, 266, 250},
  {4, 1000 - pulseDownTime, 1000, 2},
  {39, 1000 - pulseDownTime, 2500, 1}};

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
    for (int i = 0; i < freqs[freqIndex][2]; i++) {
      analogWrite(signalPin, lineBrightness);
      lineBrightness++;
      pulsePin(hSyncPin, pulseDownTime);
      delay(freqs[freqIndex][0]);
      delayMicroseconds(freqs[freqIndex][1]);
      //freqIndex = makeButtonUpdates();
      if (fChange) {
        break;
      }
    }
    digitalWrite(LED_BUILTIN, HIGH);
    pulsePin(vSyncPin, 10*pulseDownTime);
    digitalWrite(LED_BUILTIN, LOW);

    if (++frames > freqs[freqIndex][3] || fChange) {
      freqIndex = (freqIndex + 1) % 3;
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

