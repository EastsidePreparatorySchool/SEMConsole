// Arduino Due ADC->DMA->USB
// adapted from a sample by stimmer
// Input: Analog in A0, A1, A2, A3
// Output: Raw stream of uint16_t in range 0-4095 on Native USB Serial/ACM


// not sure whether this needs to be here, but whatever
#undef HID_ENABLED


// test mode



// USB communication headers

#define COMMAND_BYTES 16
byte headerConnect[COMMAND_BYTES]  = {'E','P','S','_','S','E','M','_','C','O','N','N','E','C','T','.'};
byte headerReady[COMMAND_BYTES]    = {'E','P','S','_','S','E','M','_','R','E','A','D','Y','.','.','.'};
byte headerFrame[COMMAND_BYTES]    = {'E','P','S','_','S','E','M','_','F','R','A','M','E','.','.','.'};
byte headerBytes[COMMAND_BYTES]    = {'E','P','S','_','S','E','M','_','B','Y','T','E','S','.','.','.'};
byte headerEndFrame[COMMAND_BYTES] = {'E','P','S','_','S','E','M','_','E','N','D','F','R','A','M','E'};

#define SENTINEL_BYTES 16
byte sentinelTrailer[SENTINEL_BYTES] = {0,1,2,3,4,5,6,7,8,9,0xA,0xB,0xC,0xD,0xE,0xF};

struct BytesParams {
  byte      headerBytes[16];
  uint32_t  checkSum;         // now includes everything from here on, to just before sentinel trailer
  uint16_t  line;
  uint16_t  bytes;
  uint16_t  timeLine;
};



//
// ADC memory structures
// - For SLOW2, we take an HD picture across all 4 channels.
// - FOR H6V7, we take a UHD-1 (4K) picture across 2 channels.
// - this works out to the same buffer size for both cases
//
// TODO: Make the channels for H6V7 selectable by setting digital input pins to low or high
//


// 2 channels in regular HD 1080p for Slow1:
#define MODE_SLOW1          0
#define SLOW1_NUM_CHANNELS  2
#define SLOW1_NUM_PIXELS    1776 
#define SLOW1_NUM_LINES     1000


// SEI, BEI1, BEI2, AEI all simultaneously in Slow2
#define MODE_SLOW2          1
#define SLOW2_NUM_CHANNELS  4
#define SLOW2_NUM_PIXELS    1111 
#define SLOW2_NUM_LINES     625


// For Photo H6V7, our highest resolution, we are using UHD-1 (4K). Pixel number is memory-bound, having trouble making the array bigger. 
// H6V7 really has 2500 lines, can make bigger or crop vertically by starting scan late (throwing 170 lines away)
// H6V7 takes 100s to scan, this scan transmits in about 20s, so we are fine.
// scan lines also don't take more than 4ms, so have to keep this under the theretical max of 4444, which leads to 4.2ms

#define MODE_H6V7           2
#define H6V7_NUM_CHANNELS   2
#define H6V7_NUM_PIXELS     3840
#define H6V7_NUM_LINES      2160

// one channel, fast:
// TODO : Choose good values for a workable resolution
#define MODE_FAST           3
#define FAST_NUM_CHANNELS   1
#define FAST_NUM_PIXELS     272 
#define FAST_NUM_LINES      153

#define NUM_MODES 4
// parameters
int g_numChannels[NUM_MODES] = {SLOW1_NUM_CHANNELS, SLOW2_NUM_CHANNELS, H6V7_NUM_CHANNELS, FAST_NUM_CHANNELS};
int g_numPixels[NUM_MODES] = {SLOW1_NUM_PIXELS, SLOW2_NUM_PIXELS, H6V7_NUM_PIXELS, FAST_NUM_PIXELS};
int g_numLines[NUM_MODES] = {SLOW1_NUM_LINES, SLOW2_NUM_LINES, H6V7_NUM_LINES, FAST_NUM_LINES};


int g_channelSelection1 = 0; // TODO: Make this programmable with digital inputs
int g_channelSelection2 = 2; // TODO: For now, make sure that SEI is on A0 and AEI on A1
int g_mode = MODE_SLOW2;


//
// Buffers are really independent of the resolution we are tracking, just make them big enough
//

#define NUM_BUFFERS   2 // fill one with DMA while main program reads and copies the other
#define BUFFER_LENGTH (H6V7_NUM_PIXELS * H6V7_NUM_CHANNELS)
#define BUFFER_BYTES  (BUFFER_LENGTH * sizeof(uint16_t))
#define NEXT_BUFFER(n)((n+1)%NUM_BUFFERS) // little macro to aid switch to next buffer

volatile int currentBuffer; 
volatile int nextBuffer;
uint16_t adcBuffer[NUM_BUFFERS][BUFFER_LENGTH];   
uint16_t writeBuffer[BUFFER_LENGTH];
volatile unsigned long timeLineStart;
volatile unsigned long timeLine;

const int builtInLEDPin = 13; // how to blink the built-in LED






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

  // test: pwm write to pin 2 to have some sort of signal
  pinMode(2,OUTPUT);
  analogWrite(2,0);

  // visual signal that we are alive
  blinkBuiltInLED(1);
  
  // setup ADC and buffers
  initializeADC(); 
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


  //
  // test:
  // transmit NUM_LINES ADC runs (lines)
  //

  int numLines = g_numLines[g_mode];
  
  SerialUSB.write(headerFrame, 16);                                         // send FRAME header
  SerialUSB_write_uint16_t(g_numChannels[g_mode]);  // number of channels                       
  SerialUSB_write_uint16_t(g_numPixels[g_mode]);      // width in pixels
  SerialUSB_write_uint16_t(numLines);                                       // height in lines
  if (g_mode == MODE_SLOW2) {
    // list all the channels we are capturing in SLOW2 (0-3)
    SerialUSB_write_uint16_t(0);
    SerialUSB_write_uint16_t(1);
    SerialUSB_write_uint16_t(2);
    SerialUSB_write_uint16_t(3);
  } else if (g_mode == MODE_SLOW1) {
    // list just the specific 2 channels we are capturing in SLOW1
    SerialUSB_write_uint16_t(g_channelSelection1);
    SerialUSB_write_uint16_t(g_channelSelection2);
    SerialUSB_write_uint16_t(255);
    SerialUSB_write_uint16_t(255);
  } else if (g_mode == MODE_H6V7) {
    // list just the specific 2 channels we are capturing in H6V7
    SerialUSB_write_uint16_t(g_channelSelection1);
    SerialUSB_write_uint16_t(g_channelSelection2);
    SerialUSB_write_uint16_t(255);
    SerialUSB_write_uint16_t(255);
  } else if (g_mode == MODE_FAST) {
    // list just the specific 1 channel we are capturing in FAST
    SerialUSB_write_uint16_t(g_channelSelection1);
    SerialUSB_write_uint16_t(255);
    SerialUSB_write_uint16_t(255);
    SerialUSB_write_uint16_t(255);
  }

  // set up bytes structure
  
  int bytes = (g_numPixels[g_mode] * g_numChannels[g_mode] * sizeof(uint16_t));
  struct BytesParams *pbp = (struct BytesParams *) malloc (sizeof(struct BytesParams) +  bytes + sizeof(sentinelTrailer)); 
  memcpy(pbp, headerBytes, sizeof(headerBytes));
  //memcpy(((byte *)&pbp[1]) + bytes, sentinelTrailer, sizeof (sentinelTrailer)); 
  
  int t = millis();
  int line = 0;
  int thisTime;
  char o,k;

  startADC();
  for (long i = 0; i < numLines; i++) {
    // give us a test signal on pin 2 TODO: remove this
    analogWrite(2, (i/2) % 256);

    while (NEXT_BUFFER(currentBuffer) == nextBuffer) {                  // while current and next are one apart
      delayMicroseconds(10);                                            // wait for buffer to be full
    }

    // put the line somewhere safe from adc, just past the params header:
    memcpy(&pbp[1], adcBuffer[currentBuffer], bytes);
    thisTime = timeLine;
    startADC();

    // compute checkSum
    long checkSum = 0;
    uint16_t *pWord = (uint16_t *)&pbp[1];
    for (int i=0; i<bytes/2; i++) {
      checkSum += *pWord++;
    }
    
    pbp->checkSum = checkSum + line + bytes + (uint16_t)thisTime;
    pbp->line = line;
    pbp->bytes = bytes;
    pbp->timeLine = (uint16_t) timeLine;

    // send the line until it gets through
    do {
      
      SerialUSB.write(headerBytes, 16);                                   // send BYTES header, line number, and length info
      SerialUSB_write_uint32_t(pbp->checkSum);                                 // write the long checkSum
      SerialUSB_write_uint16_t(line);                         
      SerialUSB_write_uint16_t(bytes); 
      SerialUSB_write_uint16_t((uint16_t)(timeLine));                        
      
      SerialUSB.write((uint8_t *)(&pbp[1]), bytes);               // send data, length in bytes
  
      SerialUSB.write((uint8_t *)sentinelTrailer, SENTINEL_BYTES);        // send trailer so client can recover from transmission errors

//      SerialUSB.write((uint8_t *)pbp, sizeof(struct BytesParams) +  bytes + sizeof(sentinelTrailer));
      
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

  // test: switch to other setting
  g_mode = (g_mode + 1) % NUM_MODES;

  // reinit
  initializeADC();  
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




//
// set up analog-to-digital conversions
// argument: fSlow = true if SLOW2 scan (2 channels)
//          
//

void initializeADC() {
  // convert from Ax input pin numbers to ADC channel numbers
  int channel1 = 7-g_channelSelection1;
  int channel2 = 7-g_channelSelection2;

  pmc_enable_periph_clk(ID_ADC);
  adc_init(ADC, SystemCoreClock, ADC_FREQ_MAX, ADC_STARTUP_FAST);
  analogReadResolution(12);
  adcConfigureGain();

  //ADC->ADC_CR |=1; //reset the adc
  //ADC->ADC_MR= 0x9038ff00;      //this setting is used by arduino. 

  // prescale :  ADC clock is mck/((prescale+1)*2).  mck is 84MHZ. 
  // prescale : 0x00 -> 40 Mhz

  ADC->ADC_MR &=0xFFFF0000;     // mode register "prescale" zeroed out. 
  if (g_mode != MODE_FAST) {
    ADC->ADC_MR |=0x80000000;   // high bit indicates to use sequence numbers
    ADC->ADC_EMR |= (1<<24);    // turn on channel numbers
  }
  ADC->ADC_CHDR = 0xFFFFFFFF;   // disable all channels   

  if (g_mode == MODE_SLOW2) {
    // set 4 channels for SLOW2. TODO: Which channels in case we have more than 4 connected
    ADC->ADC_CHER = 0xF0;         // enable ch 7, 6, 5, 4 -> pins a0, a1, a2, a3
    ADC->ADC_SEQR1 = 0x45670000;  // produce these channel readings for every completion
  } else if (g_mode == MODE_H6V7){
    // set 2 channels for H6V7. 
    ADC->ADC_CHER = (1 << channel1) | (1 << channel2);
    ADC->ADC_SEQR1 = (channel1 << (channel1 *4)) | (channel2 << (channel2*4));
  } else if (g_mode == MODE_SLOW1) {
    ADC->ADC_CHER = (1 << channel1) | (1 << channel2);
    ADC->ADC_SEQR1 = (channel1 << (channel1 *4)) | (channel2 << (channel2*4));
  } else if (g_mode == MODE_FAST) {
    ADC->ADC_CHER = (1 << channel1);
    // TODO: this works only for A0 right now
  }

  NVIC_EnableIRQ(ADC_IRQn);

  ADC->ADC_IDR = ~(1 << 27);              // disable other interrupts
  ADC->ADC_IER = 1 << 27;                 // enable the DMA one
  ADC->ADC_RPR = (uint32_t)adcBuffer[0];  // set up DMA buffer
  ADC->ADC_RCR = g_numPixels[g_mode] * g_numChannels[g_mode];           // and length
  ADC->ADC_RNPR = (uint32_t)adcBuffer[1]; // next DMA buffer
  ADC->ADC_RNCR = g_numPixels[g_mode] * g_numChannels[g_mode];          // and length

  currentBuffer = 0;
  nextBuffer = 1; 

  ADC->ADC_PTCR = 1;
  ADC->ADC_CR = 2;

}

void ADC_Handler() {
  // move DMA pointers to next buffer

  int flags = ADC->ADC_ISR;                           // read interrupt register
  if (flags & (1 << 27)) {                            // if this was a completed DMA
    stopADC();
    nextBuffer = NEXT_BUFFER(nextBuffer);             // get the next buffer (and let the main program know)
    ADC->ADC_RNPR = (uint32_t)adcBuffer[nextBuffer];  // put it in place
    ADC->ADC_RNCR = g_numPixels[g_mode] * g_numChannels[g_mode];
    }
}

void stopADC() {
    ADC->ADC_MR &=0xFFFFFF00;                         // disable free run mode
    timeLine = micros() - timeLineStart;              // record microseconds
}

void startADC() {
    switch (g_mode) {
      case MODE_SLOW1:
        ADC->ADC_MR |= (1<<(7-g_channelSelection1)) | (1<<(7-g_channelSelection2));     // two channels free running
        break;
      case MODE_SLOW2:
        ADC->ADC_MR |=0x000000F0;     // a0-a3 free running
        break;
      case MODE_H6V7:
        ADC->ADC_MR |= (1<<(7-g_channelSelection1)) | (1<<(7-g_channelSelection2));     // two channels free running
        break;
      case MODE_FAST:
        ADC->ADC_MR |= (1<<(7-g_channelSelection1));     // one channel free running
        break;
    }
  timeLineStart = micros();
 
}


