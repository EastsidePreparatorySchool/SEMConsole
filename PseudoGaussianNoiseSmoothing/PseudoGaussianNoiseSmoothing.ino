
#define ECHO_PIN    11    // Echo Pin
#define TRIG_PIN    12    // Trigger Pin
#define LED_PIN     13    // Onboard LED
#define SPEAKER_PIN 7     // speaker out
#define NUM_VALUES  50    // size of averaging array
#define AVG_DECAY   50
#define TRIGGER     2

long values[NUM_VALUES];
double pseudoGaussian[7];
int coefficients[7] = {1, 3, 6, 7, 6, 3, 1};
boolean canSkipPoint = true;


void setup() {
  Serial.begin (9600);
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  pinMode(LED_PIN, OUTPUT); 

  for (int i=0; i<7; i++) {
    pseudoGaussian[i] = 0;
  }
  tone(SPEAKER_PIN, 2000,500);
  delay(500);
  noTone(SPEAKER_PIN);
}

void loop() {
  long duration;
  double distance;

  // measure
  duration = measureUltrasonic();
  distance = duration / 58.0;

  // Move each item in array back one
  for (int i = 1; i < 7; i++) {
    pseudoGaussian[i-1] = pseudoGaussian[i];
  }

  // Average last 5 points
  double average = 0;
  for (int i = 1; i < 6; i++) {
    average += pseudoGaussian[i];
  }
  average /= 5.0;

  if (abs(distance - average) > 0.1) {
    if (canSkipPoint) {
      pseudoGaussian[6] = average;
      canSkipPoint = false;
    } else {
      pseudoGaussian[6] = distance;
    }
  } else {
    pseudoGaussian[6] = distance;
    canSkipPoint = true;
  }

  // Smooth it
  double sum = 0;
  for (int i = 0; i < 7; i++) {
    sum += pseudoGaussian[i] * coefficients[i];
  }
  
  //Serial.print(sum / 27.0);
  Serial.print((sum / 27.0) * 58.0 + 16);
  Serial.print(",");
  Serial.print(pseudoGaussian[6] * 58.0 + 8);
  Serial.print(",");
  Serial.print(duration);
  Serial.println();


  // delay 20ms before next reading.
  delay(20);
}

long measureUltrasonic() {
  long duration;
  
  // send trigger signal
  digitalWrite(TRIG_PIN, LOW); 
  delayMicroseconds(2); 
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10); 
  digitalWrite(TRIG_PIN, LOW);
  
  // get echo pulse
  duration = pulseIn(ECHO_PIN, HIGH);
  
  // clip the signal
  if (duration > 2000) {
    duration = 2000;
  }

  return duration;
}
