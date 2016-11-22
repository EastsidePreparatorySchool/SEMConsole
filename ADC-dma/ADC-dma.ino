#undef HID_ENABLED

// Arduino Due ADC->DMA->USB
// adapted from a samplestimmer
// Input: Analog in A0, A1, A2, A3
// Output: Raw stream of uint16_t in range 0-4095 on Native USB Serial/ACM


#define NUM_CHANNELS  4
#define NUM_PIXELS    32

#define NUM_BUFFERS   4
#define BUFFER_LENGTH (NUM_PIXELS * NUM_CHANNELS)
#define BUFFER_BYTES  (BUFFER_LENGTH * sizeof(uint16_t))

#define READINGS_TO_DISPLAY 4

volatile int bufn, obufn;
uint16_t buf[NUM_BUFFERS][BUFFER_LENGTH];

const int buttonPin = 26;     // the number of the pushbutton pin
const int ledPin = 22;      // the number of the LED pin

int buttonState = 0;

void ADC_Handler() {
  // move DMA pointers to next buffer
  int f = ADC->ADC_ISR;                   // read interrupt register
  if (f & (1 << 27)) {                    // if this was a completed DMA
    bufn = (bufn + 1) % NUM_BUFFERS;      // get the next buffer (and let the main program know)
    ADC->ADC_RNPR = (uint32_t)buf[bufn];  // put it in place
    ADC->ADC_RNCR = BUFFER_LENGTH;
  }
}
void initializeBuffers() {
  SerialUSB.begin(200000);
  //SerialUSB.begin(0); // start USB
  //while (!SerialUSB); // wait for it to be ready

  pmc_enable_periph_clk(ID_ADC);
  ADC->ADC_CR |=1; //reset the adc
  ADC->ADC_MR= 0x9038ff00;      //this setting is used by arduino. 
  // prescale :  ADC clock is mck/((prescale+1)*2).  mck is 84MHZ. 
  // prescale : 0x00 -> 40 Mhz
  ADC->ADC_MR &=0xFFFF0000;     // mode register "prescale" zeroed out. 
  ADC->ADC_MR |=0x800000F0;     // set the prescale to 0x00, and a0-a3 free running, high bit indicates to use sequence numbers
  ADC->ADC_EMR |= (1<<24);      // turn on channel numbers
  ADC->ADC_CHDR = 0xFFFFFFFF;   // disable all channels   
  ADC->ADC_CHER = 0xF0;         // ch 7, 6, 5, 4 -> pins a0, a1, a2, a3
  ADC->ADC_SEQR1 = 0x45670000;  // produce these channel readings for every completion

  NVIC_EnableIRQ(ADC_IRQn);
  ADC->ADC_IDR = ~(1 << 27);        // disable other interrupts
  ADC->ADC_IER = 1 << 27;           // enable the DM one
  ADC->ADC_RPR = (uint32_t)buf[0];  // set up DMA buffer
  ADC->ADC_RCR = BUFFER_LENGTH;     // and length
  ADC->ADC_RNPR = (uint32_t)buf[1]; // next DMA buffer
  ADC->ADC_RNCR = BUFFER_LENGTH;    // and length
  bufn = obufn = 1;
  ADC->ADC_PTCR = 1;
  ADC->ADC_CR = 2;
}

void setup() {
  initializeBuffers();
  analogReadResolution(12);
  // initialize the LED pin as an output:
  pinMode(ledPin, OUTPUT);
  // initialize the pushbutton pin as an input:
  pinMode(buttonPin, INPUT);
}

void loop() {
  // read the state of the pushbutton value:
  buttonState = digitalRead(buttonPin);
  if (buttonState == LOW) {
    digitalWrite(ledPin, LOW);
    return;
  }
  digitalWrite(ledPin, HIGH);
  SerialUSB.write("Hi");
  int t = millis();
  for (long i = 0; i < 64; i++) {
    while (obufn == bufn);                                      // wait for buffer to be full
    SerialUSB.write((uint8_t *)buf[obufn], BUFFER_BYTES);     // send it, length in bytes
    obufn = (obufn + 1) % NUM_BUFFERS;                          // set next buffer for waiting
  }
  t = millis() - t;
  digitalWrite(ledPin, LOW);
/*
  SerialUSB.print   ("2,500 lines x [4 channels x ");
  SerialUSB.print   (BUFFER_LENGTH/NUM_CHANNELS);
  SerialUSB.print   (" pixels] read in ");
  SerialUSB.print   (t);
  SerialUSB.print   ("ms, pixel rate (4 channels): ");
  SerialUSB.print   (2500.0*BUFFER_LENGTH/NUM_CHANNELS/t);
  SerialUSB.println ("khz");
  SerialUSB.println();

  */

  /*SerialUSB.print ("Buffer ");
  SerialUSB.print (obufn);
  SerialUSB.print (": ");
 
  
  for (int k = 0; k < READINGS_TO_DISPLAY; k++) {
    char numbuf[10];
    sprintf(numbuf, "Ch %d, value %04d, ", (buf[obufn][k]>>12), (buf[obufn][k] &0xFFF));
    SerialUSB.print (numbuf);
    //SerialUSB.print (", ");
  }
  SerialUSB.println();*/

}

