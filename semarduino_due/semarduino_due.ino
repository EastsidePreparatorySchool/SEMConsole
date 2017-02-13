// Arduino Due ADC->DMA->USB
// adapted from a sample by stimmer
// Input: Analog in A0, A1, A2, A3
// Output: Raw stream of uint16_t in range 0-4095 on Native USB Serial/ACM


// not sure whether this needs to be here, but whatever
#undef HID_ENABLED


// test mode

  static boolean fSLOW1 = false;


// USB communication headers

#define COMMAND_BYTES 16
byte headerConnect[COMMAND_BYTES]  = {'E','P','S','_','S','E','M','_','C','O','N','N','E','C','T','.'};
byte headerReady[COMMAND_BYTES]    = {'E','P','S','_','S','E','M','_','R','E','A','D','Y','.','.','.'};
byte headerFrame[COMMAND_BYTES]    = {'E','P','S','_','S','E','M','_','F','R','A','M','E','.','.','.'};
byte headerBytes[COMMAND_BYTES]    = {'E','P','S','_','S','E','M','_','B','Y','T','E','S','.','.','.'};
byte headerEndFrame[COMMAND_BYTES] = {'E','P','S','_','S','E','M','_','E','N','D','F','R','A','M','E'};

#define SENTINEL_BYTES 16
byte sentinelTrailer[SENTINEL_BYTES] = {0,1,2,3,4,5,6,7,8,9,0xA,0xB,0xC,0xD,0xE,0xF};



//
// ADC memory structures
// we support only two modes of capture: SLOW1 and H6V7.
// - For SLOW1, we take an HD picture across all 4 channels.
// - FOR H6V7, we take a UHD-1 (4K) picture across 2 channels.
// - this works out to the same buffer size for both cases
//
// TODO: Make the channels for H6V7 selectable by setting digital input pins to low or high
//


// SEI, BEI1, BEI2, AEI in regular HD 1080p - good enough for Slow 1:
#define SLOW1_NUM_CHANNELS  4
#define SLOW1_NUM_PIXELS    1920 
#define SLOW1_NUM_LINES     1080


// For Photo H6V7, our highest resolution, we are using UHD-1 (4K). Pixel number is memory-bound, having trouble making the array bigger. 
// H6V7 really has 2500 lines, can make bigger or crop vertically by starting scan late (throwing 170 lines away)
// H6V7 takes 100s to scan, this scan transmits in about 20s, so we are fine.
#define H6V7_NUM_CHANNELS  2
#define H6V7_NUM_PIXELS    3840
#define H6V7_NUM_LINES     2160


//
// Buffers are really independent of the resolution we are tracking
//

#define NUM_BUFFERS   2 // fill one with DMA while main program reads and copies the other
#define BUFFER_LENGTH (SLOW1_NUM_PIXELS * SLOW1_NUM_CHANNELS)
#define BUFFER_BYTES  (BUFFER_LENGTH * sizeof(uint16_t))
#define NEXT_BUFFER(n)((n+1)%NUM_BUFFERS) // little macro to aid switch to next buffer

volatile int currentBuffer; 
volatile int nextBuffer;
uint16_t adcBuffer[NUM_BUFFERS][BUFFER_LENGTH];   
uint16_t writeBuffer[BUFFER_LENGTH];
volatile unsigned long timeLineStart;
volatile unsigned long timeLine;

const int buttonPin = 26;     // the number of the pushbutton pin
const int builtInLEDPin = 13; // how to blink the built-in LED
const int customLEDPin = 22;  // the number of the custom LED pin
int buttonState = 0;


int channelSelection1 = 0; // TODO: Make this programmanble with digital inputs
int channelSelection2 = 1; // TODO: For now, make sure that SEI is on A0 and AEI on A1


//
// set up analog-to-digital conversions
// argument: fSlow = true if SLOW1 scan (2 channels)
//          
//

void initializeADC(boolean fSLOW1, int channel1, int channel2) {
  adcConfigureGain();
  
  pmc_enable_periph_clk(ID_ADC);
  analogReadResolution(12);

  ADC->ADC_CR |=1; //reset the adc
  ADC->ADC_MR= 0x9038ff00;      //this setting is used by arduino. 

  // prescale :  ADC clock is mck/((prescale+1)*2).  mck is 84MHZ. 
  // prescale : 0x00 -> 40 Mhz

  ADC->ADC_MR &=0xFFFF0000;     // mode register "prescale" zeroed out. 
  ADC->ADC_MR |=0x80000000;     // set the prescale to 0x00, high bit indicates to use sequence numbers
  ADC->ADC_EMR |= (1<<24);      // turn on channel numbers
  ADC->ADC_CHDR = 0xFFFFFFFF;   // disable all channels   

  if (fSLOW1) {
    // set 4 channels for SLOW1. TODO: Which channels in case we have more than 4 connected
    ADC->ADC_CHER = 0xF0;         // enable ch 7, 6, 5, 4 -> pins a0, a1, a2, a3
    ADC->ADC_SEQR1 = 0x45670000;  // produce these channel readings for every completion
  } else {
    // set 2 channels for H6V7. TODO: THIS DOES NOT SCAN A1 correctly (SLOW1 does). READ ARTICLE ABOUT SEQ and TAGGING AGAIN
    ADC->ADC_CHER = 0x30;         // enable ch 7, 6 -> pins a0, a1
    ADC->ADC_SEQR1 = ((7-channel1) << 16) + ((7-channel2)<<20); // produce these channel readings for every completion
    //ADC->ADC_SEQR1 = 0x00670000;  // old code for channels A0 and A1
  }

  NVIC_EnableIRQ(ADC_IRQn);

  ADC->ADC_IDR = ~(1 << 27);              // disable other interrupts
  ADC->ADC_IER = 1 << 27;                 // enable the DM one
  ADC->ADC_RPR = (uint32_t)adcBuffer[0];  // set up DMA buffer
  ADC->ADC_RCR = BUFFER_LENGTH;           // and length
  ADC->ADC_RNPR = (uint32_t)adcBuffer[1]; // next DMA buffer
  ADC->ADC_RNCR = BUFFER_LENGTH;          // and length

  currentBuffer = 0;
  nextBuffer = 1; 

  ADC->ADC_PTCR = 1;
  ADC->ADC_CR = 2;
  ADC->ADC_MR |=0x000000F0;     // a0-a3 free running

  timeLineStart = micros();
}




void ADC_Handler() {
  // move DMA pointers to next buffer

  int flags = ADC->ADC_ISR;                           // read interrupt register
  if (flags & (1 << 27)) {                            // if this was a completed DMA
    ADC->ADC_MR &=0xFFFFFF00;                         // disable free run mode

    timeLine = micros() - timeLineStart;              // record microseconds
    timeLineStart = micros();                         // reset timer
    nextBuffer = NEXT_BUFFER(nextBuffer);             // get the next buffer (and let the main program know)
    ADC->ADC_RNPR = (uint32_t)adcBuffer[nextBuffer];  // put it in place
    ADC->ADC_RNCR = BUFFER_LENGTH;
    ADC->ADC_MR |=0x000000F0;     // a0-a3 free running

  }
}





// code to blink the built-in LED n times 

void blinkBuiltInLED(int n) {
  for (int i= 0; i<n; i++) {
    // blink built-in LED
    digitalWrite(builtInLEDPin, HIGH);
    delay(200);
    digitalWrite(builtInLEDPin, LOW);
    if(i < n-1) {
      delay(200);
    }
  }
}


void setup() {
  // start USB
  SerialUSB.begin(0); 

  // set up built-in blink LED, custom led, pushButton
  pinMode (builtInLEDPin, OUTPUT);
  pinMode(customLEDPin, OUTPUT);
  pinMode(buttonPin, INPUT);
  pinMode(2,OUTPUT);

  analogWrite(2,0);

  // visual signal that we are alive
  blinkBuiltInLED(1);
  
  // setup ADC and buffers
  initializeADC(fSLOW1, channelSelection1, channelSelection2); // start with mode SLOW1 (channels are ignored, all 4 are sent)
}



void loop() {
  int n;
  byte buffer[16];
  // wait for USB connect command from host
  do {
    do {
      // wait for any request
      do {
        delay(20); 
        n = SerialUSB.available();
      } while (n == 0);


      // read the request
      for (int i = 0; i<n; i++) {
         buffer[i] = SerialUSB.read();
       }
    } while (n != COMMAND_BYTES);
   } while (memcmp(buffer, headerConnect, COMMAND_BYTES) != 0);
  
  // acknowledge connection request
  SerialUSB.write(headerReady, 16);
  SerialUSB.flush();
  blinkBuiltInLED(2);  


  
  // read the state of the pushbutton value
  // stop transmitting if pushed
  buttonState = digitalRead(buttonPin);
  if (buttonState == LOW) {
    blinkBuiltInLED(3);
    return;
  }


  //
  // test:
  // transmit NUM_LINES ADC runs (lines)
  //

  int numLines = fSLOW1? SLOW1_NUM_LINES:H6V7_NUM_LINES;

  SerialUSB.write(headerFrame, 16);                                         // send FRAME header
  //SerialUSB.flush();
  SerialUSB_write_uint16_t(fSLOW1 ? SLOW1_NUM_CHANNELS:H6V7_NUM_CHANNELS);  // number of channels                       
  SerialUSB_write_uint16_t(fSLOW1 ? SLOW1_NUM_PIXELS:H6V7_NUM_PIXELS);      // width in pixels
  SerialUSB_write_uint16_t(numLines);                                       // height in lines
  if (fSLOW1) {
    // list all the channels we are capturing in SLOW1 (0-3)
    SerialUSB_write_uint16_t(0);
    SerialUSB_write_uint16_t(1);
    SerialUSB_write_uint16_t(2);
    SerialUSB_write_uint16_t(3);
  } else {
    // list just the specific 2 channels we are capturing in H6V7
    SerialUSB_write_uint16_t(channelSelection1);
    SerialUSB_write_uint16_t(channelSelection2);
    SerialUSB_write_uint16_t(255);
    SerialUSB_write_uint16_t(255);

  }
  

  
  int t = millis();
  int line = 0;
  char o,k;
  
  for (long i = 0; i < numLines; i++) {
    // give us a test signal on pin 2
    analogWrite(2, i % 256);

    
    while (NEXT_BUFFER(currentBuffer) == nextBuffer) {                  // while current and next are one apart
      delayMicroseconds(50);                                            // wait for buffer to be full
    }

    // put the line somewhere safe from adc
    memcpy(writeBuffer, adcBuffer[currentBuffer], BUFFER_BYTES);


    // compute checkSum
    long checkSum = 0;
    uint16_t *pWord = writeBuffer;
    for (int i=0; i<BUFFER_LENGTH; i++) {
      checkSum += *pWord++;
    }

    // send the line until it gets through
    do {
      SerialUSB.write(headerBytes, 16);                                   // send BYTES header, line number, and length info
      SerialUSB_write_uint16_t(line);                         
      SerialUSB_write_uint16_t(BUFFER_BYTES); 
      SerialUSB_write_uint16_t((uint16_t)(timeLine/10));                        
      
      SerialUSB.write((uint8_t *)writeBuffer, BUFFER_BYTES);              // send data, length in bytes
  
      SerialUSB_write_uint32_t(checkSum);                                 // write the long checkSum
      SerialUSB.write((uint8_t *)sentinelTrailer, SENTINEL_BYTES);        // send trailer so client can recover from transmission errors
  
      // wait for response
      while (SerialUSB.available() == 0);
      
      // read two bytes of response, either "OK" or "NG"
      o = SerialUSB.read();   
      k = SerialUSB.read();
    } while(o != 'O' || k != 'K');
    

    currentBuffer = NEXT_BUFFER(currentBuffer);                         // set next buffer for waiting
    line++;
  }
  t = millis() - t;
  
  SerialUSB.write(headerEndFrame, 16);                                  // send EFRAME (end frame) and time
  SerialUSB_write_uint16_t((uint16_t)t);
  //SerialUSB.flush();
  
  // continue loop function by waiting for new connection

  // test: reinit ADC to other setting
  fSLOW1 = !fSLOW1;
  initializeADC(fSLOW1, channelSelection1, channelSelection2);  // for H6V7 mode, channels A0 and A1
}



void SerialUSB_write_uint16_t(uint16_t word) {
    SerialUSB.write((byte)(word & 255));
    SerialUSB.write((byte)(word >> 8));
}

void SerialUSB_write_uint32_t(uint32_t word) {
    SerialUSB.write((byte)(word & 255));                           
    SerialUSB.write((byte)((word >> 8) & 255));
    SerialUSB.write((byte)((word >> 16) & 255));
    SerialUSB.write((byte)(word >> 24));
}


void adcConfigureGain() {
  adc_enable_anch(ADC); 
  
  adc_set_channel_input_gain(ADC, (adc_channel_num_t)(g_APinDescription[0].ulADCChannelNumber), ADC_GAINVALUE_0);
  adc_set_channel_input_gain(ADC, (adc_channel_num_t)(g_APinDescription[1].ulADCChannelNumber), ADC_GAINVALUE_0);
  adc_set_channel_input_gain(ADC, (adc_channel_num_t)(g_APinDescription[2].ulADCChannelNumber), ADC_GAINVALUE_0);
  adc_set_channel_input_gain(ADC, (adc_channel_num_t)(g_APinDescription[3].ulADCChannelNumber), ADC_GAINVALUE_0);
  
  adc_disable_channel_input_offset(ADC, (adc_channel_num_t)(g_APinDescription[0].ulADCChannelNumber));
  adc_disable_channel_input_offset(ADC, (adc_channel_num_t)(g_APinDescription[1].ulADCChannelNumber));
  adc_disable_channel_input_offset(ADC, (adc_channel_num_t)(g_APinDescription[2].ulADCChannelNumber));
  adc_disable_channel_input_offset(ADC, (adc_channel_num_t)(g_APinDescription[3].ulADCChannelNumber));
}

/* web code for gain and input
 *  
adc_disable_anch(ADC); 
adc_enable_anch(ADC);
adc_set_channel_input_gain(ADC, ADC_CHANNEL_CAM1, ADC_GAINVALUE_2);
adc_set_channel_input_gain(ADC, ADC_CHANNEL_CAM1, ADC_GAINVALUE_0);

adc_enable_anch(ADC);
adc_enable_channel_input_offset(ADC, ADC_CHANNEL_CAM1);
adc_disable_channel_input_offset(ADC, ADC_CHANNEL_CAM1);



 */



