const int buttonPin = 26;     // the number of the pushbutton pin
const int loopLEDPin = 24;      // the number of the LED pin
const int interruptLEDPin = 22;      // the number of the LED pin

void setup() {
  pinMode(loopLEDPin, OUTPUT);
  pinMode(interruptLEDPin, OUTPUT);
  
  pinMode(buttonPin, INPUT);
}

void loop() {
  // put your main code here, to run repeatedly:
  digitalWrite(interruptLEDPin, HIGH);
  delay(150);
  digitalWrite(interruptLEDPin, LOW);
  delay(150);
}
