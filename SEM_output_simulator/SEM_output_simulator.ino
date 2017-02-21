int vSyncPin = 8;
int hSyncPin = 9;
int lLED = 13;
int buttonPin = 2;
int signalPin = 3;
volatile boolean prevButtonState = LOW; 

int pulseDownTime = 50; // Micros, can't be higher than 150

uint8_t lineBrightness = 0;
int freqIndex;
int nextFreqIndex = 0;
int freqs[3][3] = {
  {0, 150 - pulseDownTime, 533},
  {4, 1000 - pulseDownTime, 1000},
  {39, 1000 - pulseDownTime, 2500}};

void setup() {
  pinMode (vSyncPin, OUTPUT);
  pinMode (hSyncPin, OUTPUT);
  pinMode (lLED, OUTPUT);
  pinMode (buttonPin, INPUT);
  pinMode (signalPin, OUTPUT);
  
  digitalWrite(vSyncPin, HIGH);
  digitalWrite(hSyncPin, HIGH);
  digitalWrite(lLED, LOW);
}

void loop() {
    freqIndex = nextFreqIndex;
    pulseBothPins(vSyncPin, lLED);
    
    for (int i = 0; i < freqs[freqIndex][2]; i++) {
      analogWrite(signalPin, lineBrightness);
      lineBrightness++;
      pulsePin(hSyncPin, pulseDownTime);
      delay(freqs[freqIndex][0]);
      delayMicroseconds(freqs[freqIndex][1]);
      makeButtonUpdates();
    }
    pulseThreePins(hSyncPin, vSyncPin, lLED);
}

void pulsePin(int pin, int uS) { // Causes a 1 microsecond delay in program
  digitalWrite(pin, LOW);
  delayMicroseconds(uS);
  digitalWrite(pin, HIGH);
}

void pulseBothPins(int pin1, int pin2) {
  digitalWrite(pin1, LOW);
  digitalWrite(pin2, HIGH);
  delayMicroseconds(100);
  digitalWrite(pin1, HIGH);
  digitalWrite(pin2, LOW);
}

void pulseThreePins(int pin1, int pin2, int pin3) {
  digitalWrite(pin1, LOW);
  digitalWrite(pin2, LOW);
  digitalWrite(pin3, HIGH);
  delayMicroseconds(100);
  digitalWrite(pin1, HIGH);
  digitalWrite(pin2, HIGH);
  digitalWrite(pin3, LOW);
}

void makeButtonUpdates() {
  boolean buttonState = digitalRead(buttonPin);
  
  if (buttonState == HIGH && prevButtonState == LOW) {
    nextFreqIndex += 1;
    if (nextFreqIndex >= 3) {
      nextFreqIndex = 0;
    }
  }
  prevButtonState = buttonState;
}

