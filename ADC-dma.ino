#undef HID_ENABLED

// Arduino Due ADC->DMA->USB
// adapted from a samplestimmer
// Input: Analog in A0, A1, A2, A3
// Output: Raw stream of uint16_t in range 0-4095 on Native USB Serial/ACM


#define NUM_CHANNELS  4
#define NUM_PIXELS    2048

#define NUM_BUFFERS   4
#define BUFFER_LENGTH (NUM_PIXELS * NUM_CHANNELS)
#define BUFFER_BYTES  (BUFFER_LENGTH * sizeof(uint16_t))

volatile int bufn, obufn;
uint16_t buf[NUM_BUFFERS][BUFFER_LENGTH];

void ADC_Handler() {
  // move DMA pointers to next buffer
  int f = ADC->ADC_ISR;                   // read interrupt register
  if (f & (1 << 27)) {                    // if this was a completed DMA
    bufn = (bufn + 1) % NUM_BUFFERS;      // get the next buffer (and let the main program know)
    ADC->ADC_RNPR = (uint32_t)buf[bufn];  // put it in place
    ADC->ADC_RNCR = BUFFER_LENGTH;
  }
}

void setup() {
  Serial.begin(9600);
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

void loop() {
  int t = millis();
  for (long i = 0; i < 2500; i++) {
    while (obufn == bufn);                                      // wait for buffer to be full
    //SerialUSB.write((uint8_t *)buf[obufn], BUFFER_BYTES);     // send it, length in bytes
    obufn = (obufn + 1) % NUM_BUFFERS ;                         // set next buffer for waiting
  }
  t = millis() - t;
  Serial.print   ("2,500 lines x [4 channels x ");
  Serial.print   (BUFFER_LENGTH/NUM_CHANNELS);
  Serial.print   (" pixels] read in ");
  Serial.print   (t);
  Serial.print   ("ms, pixel rate (4 channels): ");
  Serial.print   (2500.0*BUFFER_LENGTH/NUM_CHANNELS/t);
  Serial.println ("khz");
}

