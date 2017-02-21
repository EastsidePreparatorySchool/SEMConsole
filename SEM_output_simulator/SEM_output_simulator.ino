int vSyncPin = 8;
int hSyncPin = 9;
int redColorPin = 2;
int greenColorPin = 3;
int blueColorPin = 4;
int buttonPin = 10;
int lLED = 13;

boolean makingPhoto;
int lines = 100;
int millisPerLine = 40 - 1;
double pulseDownTime = 100;
int microsPerLine = 1000 - pulseDownTime;

void setup() {
  // put your setup code here, to run once:
  pinMode (buttonPin, INPUT);
  pinMode (vSyncPin, OUTPUT);
  pinMode (hSyncPin, OUTPUT);
  pinMode (lLED, OUTPUT);
  pinMode (redColorPin, OUTPUT);
  pinMode (greenColorPin, OUTPUT);
  pinMode (blueColorPin, OUTPUT);
  
  digitalWrite(vSyncPin, HIGH);
  digitalWrite(hSyncPin, HIGH);
  digitalWrite(lLED, LOW);
}

void loop() {
    pulseBothPins(vSyncPin, lLED);
    
    for (int i = 0; i < lines; i++) {
      pulsePin(hSyncPin, pulseDownTime);
      delay(millisPerLine);
      delayMicroseconds(microsPerLine);
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
  delayMicroseconds(300);
  digitalWrite(pin1, HIGH);
  digitalWrite(pin2, LOW);
}

void pulseThreePins(int pin1, int pin2, int pin3) {
  digitalWrite(pin1, LOW);
  digitalWrite(pin2, LOW);
  digitalWrite(pin3, HIGH);
  delayMicroseconds(300);
  digitalWrite(pin1, HIGH);
  digitalWrite(pin2, HIGH);
  digitalWrite(pin3, LOW);
}

unsigned int rng() {
  static unsigned int y = 0;
  y += micros(); // seeded with changing number
  y ^= y << 2; y ^= y >> 7; y ^= y << 7;
  return (y/255);
}

