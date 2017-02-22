int vSyncPin = 8;
int hSyncPin = 9;
int lLED = 13;
int buttonPin = 2;
int signalPin = 3;
volatile boolean prevButtonState = HIGH; 

int pulseDownTime = 50; // Micros, can't be higher than 150

uint8_t lineBrightness = 0;
int freqIndex = 1;
int freqs[3][3] = {
  {0, 150 - pulseDownTime, 533},
  {4, 1000 - pulseDownTime, 1000},
  {39, 1000 - pulseDownTime, 2500}};

void setup() {
  pinMode (vSyncPin, OUTPUT);
  pinMode (hSyncPin, OUTPUT);
  pinMode (lLED, OUTPUT);
  pinMode (buttonPin, INPUT_PULLUP);
  pinMode (signalPin, OUTPUT);
  
  digitalWrite(vSyncPin, HIGH);
  digitalWrite(hSyncPin, HIGH);
  digitalWrite(lLED, LOW);
}

void loop() {
    for (int i = 0; i < freqs[freqIndex][2]; i++) {
      analogWrite(signalPin, lineBrightness);
      lineBrightness++;
      pulsePin(hSyncPin, pulseDownTime);
      delay(freqs[freqIndex][0]);
      delayMicroseconds(freqs[freqIndex][1]);
      freqIndex = makeButtonUpdates();
    }
    digitalWrite(lLED, HIGH);
    pulsePin(vSyncPin, 3*pulseDownTime);
    digitalWrite(lLED, LOW);
}

void pulsePin(int pin, int uS) { // Causes a 1 microsecond delay in program
  digitalWrite(pin, LOW);
  delayMicroseconds(uS);
  digitalWrite(pin, HIGH);
}

int makeButtonUpdates() {
  boolean buttonState = digitalRead(buttonPin);
  int f;
  
  if (buttonState == LOW && prevButtonState == HIGH) {
    f = (freqIndex + 1) % 3;
  }
  prevButtonState = buttonState;
  return f;
}

