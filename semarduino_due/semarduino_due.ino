// 
// SEM EPS for Arduino Due
// Input: Analog in A0, A1, A2, A3, hsync and vsync at D1 and D2
// Output: USB frames to PC
//




// test mode

#define FRAMES_PER_TEST 4



// USB communication headers

#define COMMAND_BYTES 16
byte headerConnect[COMMAND_BYTES]  = {'E','P','S','_','S','E','M','_','C','O','N','N','E','C','T','.'};
byte headerReady[COMMAND_BYTES]    = {'E','P','S','_','S','E','M','_','R','E','A','D','Y','.','.','.'};
byte headerFrame[COMMAND_BYTES]    = {'E','P','S','_','S','E','M','_','F','R','A','M','E','.','.','.'};
byte headerBytes[COMMAND_BYTES]    = {'E','P','S','_','S','E','M','_','B','Y','T','E','S','.','.','.'};
byte headerEndFrame[COMMAND_BYTES] = {'E','P','S','_','S','E','M','_','E','N','D','F','R','A','M','E'};
byte headerReset[COMMAND_BYTES]    = {'E','P','S','_','S','E','M','_','R','E','S','E','T','.','.','.'};

#define SENTINEL_BYTES 16
byte sentinelTrailer[SENTINEL_BYTES] = {0,1,2,3,4,5,6,7,8,9,0xA,0xB,0xC,0xD,0xE,0xF};

struct BytesParams {
  byte      headerBytes[16];
  uint32_t  checkSum;         // now includes everything from here on, to just before sentinel trailer
  uint16_t  line;
  uint16_t  bytes;
};

struct BytesParams *g_pbp;


//
// resolution recognition (autosync) structures and array
//

struct Resolution {
  int minLineTime;  // window of scan length (in microseconds) for recognizing this resolution
  int maxLineTime;
  int numPixels;    // number of pixels we will set up to scan for line
  int numChannels;  // number of channels for every pixel
  int numLines;     // documented number of lines (in actuality, we will obey vsync)
};

struct Resolution *g_pCurrentRes;


// resolutions are stored in this array in ascending order of horizontal scan times
// min time, max time, pixels, channels, spec lines

struct Resolution g_allRes[3] = {
  {   145,   155,  284, 1,  533 }, // RAPID2
  {  4900,  5100, 2200, 4, 1000 }, // SLOW1
  { 39000, 41000, 4770, 2, 2500 }  // H6V7
};

#define NUM_MODES 3

int g_channelSelection1 = 0; // TODO: Make this programmable with digital inputs
int g_channelSelection2 = 2; // TODO: For now, make sure that SEI is on A0 and AEI on A1
int g_mode = 0;


//
// Buffers are really independent of the resolution we are tracking, just make them big enough
//

#define NUM_BUFFERS   2 // fill one with DMA while main program reads and copies the other
#define BUFFER_LENGTH (5000 * 2 + 64)
#define BUFFER_BYTES  (BUFFER_LENGTH * sizeof(uint16_t))
#define NEXT_BUFFER(n)((n+1)%NUM_BUFFERS) // little macro to aid switch to next buffer

volatile int currentBuffer; 
volatile int nextBuffer;
uint16_t adcBuffer[NUM_BUFFERS][BUFFER_LENGTH];   
uint16_t writeBuffer[BUFFER_LENGTH];
volatile unsigned long g_adcLineTimeStart;
volatile unsigned long g_adcLineTime;






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

  // visual signal that we are alive
  blinkBuiltInLED(1);
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

  g_pCurrentRes = &g_allRes[g_mode];
  initializeADC();
  int bytes = (g_pCurrentRes->numPixels * g_pCurrentRes->numChannels * sizeof(uint16_t));
  int lineBufferSize = setupLineBuffers();
  

  int frames = FRAMES_PER_TEST;
  int frame;
  for (frame = 0; frame < frames; frame++) {
    int numLines = g_pCurrentRes->numLines;

    sendFrameHeader();
    
    int frameTime = millis();
    int line = 0;
    int lineTime = 0;
    char o,k;
  
    startADC();
    for (long i = 0; i < numLines; i++) {
      // give us a test signal on pin 2 TODO: remove this
      analogWrite(2, (frame*256/frames) % 256);
      analogWrite(DAC0, (frame*256/frames) % 256);
      analogWrite(DAC1, 256-((frame*256/frames) % 256));
  
      while (NEXT_BUFFER(currentBuffer) == nextBuffer) {                  // while current and next are one apart
        delayMicroseconds(10);                                            // wait for buffer to be full
      }
  
      // put the line somewhere safe from adc, just past the params header:
      memcpy(&g_pbp[1], adcBuffer[currentBuffer], bytes);
      lineTime = max(lineTime, g_adcLineTime);
      currentBuffer = NEXT_BUFFER(currentBuffer);                         // set next buffer for waiting
  
      // restart conversions TODO: This needs to be HSync triggered
  
      startADC();
  
      // compute checkSum
      long checkSum = 0;
      uint16_t *pWord = (uint16_t *)&g_pbp[1];
      for (int i=0; i<bytes/2; i++) {
        checkSum += *pWord++;
      }
      
      g_pbp->checkSum = checkSum + line + bytes;
      g_pbp->line = line;
      g_pbp->bytes = bytes;
  
      // send the line until it gets through
      int errorCount;
      for (errorCount = 0; errorCount < 10; errorCount++) {
        SerialUSB.write((uint8_t *)g_pbp, sizeof(struct BytesParams) +  bytes + sizeof(sentinelTrailer));
        
        // wait for response
        int wait = micros();
        int acceptable = wait + 100000;
        while (SerialUSB.available() == 0 && wait<acceptable) {
          wait = micros();
        }
        
        if (wait >= acceptable)
          goto reset;
        
        // read two bytes of response, either "OK" or "NG"
        o = SerialUSB.read();   
        k = SerialUSB.read();
        
        if (o == 'O' && k == 'K')
          break;
          
        if (o == 'N' && k == 'G') 
          continue;
      };
      if (errorCount == 10)
        goto reset;
             
      line++;
    }
    frameTime = millis() - frameTime;
    sendEndFrame (lineTime, frameTime);
    
  }

  reset:
  SerialUSB.write(headerReset, 16);   
  
  // send more frames, or 
  // continue loop function by waiting for new connection

  // test: switch to other setting
  g_mode = (g_mode + 1) % NUM_MODES;
}



int setupLineBuffers() {
  int bytes = (g_pCurrentRes->numPixels * g_pCurrentRes->numChannels * sizeof(uint16_t));
  int bufferSize = sizeof(struct BytesParams) +  bytes + sizeof(sentinelTrailer);
  g_pbp = (struct BytesParams *) malloc (bufferSize); 
  memcpy(&g_pbp->headerBytes, headerBytes, sizeof(headerBytes));
  memcpy(((byte *)&g_pbp[1]) + bytes, sentinelTrailer, sizeof (sentinelTrailer)); 
  g_pbp->bytes = bytes;
  return bufferSize;
}


void sendFrameHeader() {
  SerialUSB.write(headerFrame, 16);                     // send FRAME header
  SerialUSB_write_uint16_t(g_pCurrentRes->numChannels); // number of channels                       
  SerialUSB_write_uint16_t(g_pCurrentRes->numPixels);   // width in pixels
  SerialUSB_write_uint16_t(g_pCurrentRes->numLines);    // height in lines
  SerialUSB_write_uint16_t(0);                          // stand-in for line scan time
  
  switch (g_pCurrentRes->numChannels) {
    case 4:
      SerialUSB_write_uint16_t(0);
      SerialUSB_write_uint16_t(1);
      SerialUSB_write_uint16_t(2);
      SerialUSB_write_uint16_t(3);
      break;

    case 2:
      // list just the specific 2 channels we are capturing
      SerialUSB_write_uint16_t(g_channelSelection1);
      SerialUSB_write_uint16_t(g_channelSelection2);
      SerialUSB_write_uint16_t(255);
      SerialUSB_write_uint16_t(255);
      break;

    case 1:
      // list just the specific 1 channel we are capturing in FAST
      SerialUSB_write_uint16_t(g_channelSelection1);
      SerialUSB_write_uint16_t(255);
      SerialUSB_write_uint16_t(255);
      SerialUSB_write_uint16_t(255);
      break;
  }
}

void sendEndFrame(int lineTime, int frameTime) {
    SerialUSB.write(headerEndFrame, 16);     // send EFRAME (end frame), line time, frame time
    SerialUSB_write_uint16_t((uint16_t)lineTime);
    SerialUSB_write_uint16_t((uint16_t)frameTime);
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
//

void initializeADC() {
  // convert from Ax input pin numbers to ADC channel numbers
  int channel1 = 7-g_channelSelection1;
  int channel2 = 7-g_channelSelection2;

  pmc_enable_periph_clk(ID_ADC);
  adc_init(ADC, SystemCoreClock, ADC_FREQ_MAX, ADC_STARTUP_FAST);
  analogReadResolution(8);
  adcConfigureGain();

  // prescale : ADC clock is mck/((prescale+1)*2).  mck is 84MHZ. 
  // prescale : 0x00 -> 40 Mhz

  ADC->ADC_MR &=0xFFFF0000;     // mode register "prescale" zeroed out. 
  ADC->ADC_MR |=0x80000000;     // high bit indicates to use sequence numbers
  ADC->ADC_EMR |= (1<<24);      // turn on channel numbers
  ADC->ADC_CHDR = 0xFFFFFFFF;   // disable all channels   

  switch (g_pCurrentRes->numChannels) {
    case 4:
    // set 4 channels 
    ADC->ADC_CHER = 0xF0;         // enable ch 7, 6, 5, 4 -> pins a0, a1, a2, a3
    ADC->ADC_SEQR1 = 0x45670000;  // produce these channel readings for every completion
    break;
    
    case 2:
    // set 2 channels  
    ADC->ADC_CHER = (1 << channel1) | (1 << channel2);
    ADC->ADC_SEQR1 = (channel1 << (channel1 *4)) | (channel2 << (channel2*4));
    break;

    case 1:
    ADC->ADC_CHER = (1 << channel1); // todo: does this work for channels other than A0?
    break;
  }

  NVIC_EnableIRQ(ADC_IRQn);

  ADC->ADC_IDR = ~(1 << 27);              // disable other interrupts
  ADC->ADC_IER = 1 << 27;                 // enable the DMA one
  ADC->ADC_RPR = (uint32_t)adcBuffer[0];  // set up DMA buffer
  ADC->ADC_RCR = g_pCurrentRes->numPixels * g_pCurrentRes->numChannels;           // and number of words
  ADC->ADC_RNPR = (uint32_t)adcBuffer[1]; // next DMA buffer
  ADC->ADC_RNCR = g_pCurrentRes->numPixels * g_pCurrentRes->numChannels;  

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
    ADC->ADC_RNCR = g_pCurrentRes->numPixels * g_pCurrentRes->numChannels;
    }
}

void stopADC() {
    ADC->ADC_MR &=0xFFFFFF00;                         // disable free run mode
    g_adcLineTime = micros() - g_adcLineTimeStart;    // record microseconds
}

void startADC() {
    switch (g_pCurrentRes->numChannels) {
      case 4:
        ADC->ADC_MR |=0x000000F0;     // a0-a3 free running
        break;
        
      case 2:
        ADC->ADC_MR |= (1<<(7-g_channelSelection1)) | (1<<(7-g_channelSelection2));     // two channels free running
        break;
        
      case 1:
        ADC->ADC_MR |= (1<<(7-g_channelSelection1));     // one channel free running
        break;
    }
  g_adcLineTimeStart = micros();
 
}


#define HSYNC_PIN   1
#define VSYNC_PIN   2

#define PHASE_IDLE                0
#define PHASE_READY_TO_MEASRURE  1
#define PHASE_MEASURING           2
#define PHASE_READY_FOR_SCAN      3
#define PHASE_SCANNING            4

volatile int g_phase = PHASE_IDLE;
volatile int g_measuredLineTime;

void setupInterrupts() {
  pinMode(VSYNC_PIN, INPUT);
  pinMode(HSYNC_PIN, INPUT);
  attachInterrupt(VSYNC_PIN, vsyncHandler, FALLING);  // catch falling edge of vsync to get ready for measuring
  attachInterrupt(HSYNC_PIN, hsyncHandler, RISING);   // catch rising edge of hsync to start ADC
}

void vsyncHandler() {
  switch (g_phase) {
    case PHASE_IDLE:
    case PHASE_SCANNING:
      // time to end the frame and send the image
      g_phase = PHASE_READY_TO_MEASRURE;
      break;

    case PHASE_READY_TO_MEASRURE:
    case PHASE_MEASURING:
    case PHASE_READY_FOR_SCAN:
      // we should never get here, but hey, just stop everything
      g_phase = PHASE_IDLE;
      break;
  }
}

void hsyncHandler() {
  switch (g_phase) {
    case PHASE_IDLE:
      // not doing anything right now
      break;
    
    case PHASE_READY_TO_MEASRURE:
      // start stopwatch, switch phase
      g_measuredLineTime = micros();
      g_phase = PHASE_MEASURING;
      break;

    case PHASE_MEASURING:
      // take scan time, get ready to start scanning (initiated by main program, need to set up ADC first)
      g_measuredLineTime = micros() - g_measuredLineTime;
      g_phase = PHASE_READY_FOR_SCAN;
      break;
      
    case PHASE_SCANNING:
      // start ADC (completion handled by other interrupt)
      startADC();
      break;
  }
}


//
// takes line scan time in us, returns pointer to resolution info, or null if not recognized
//

struct Resolution *getResolution(int lineTime) {
  int i;

  for (i=0; i<(sizeof(g_allRes)/sizeof(struct Resolution)); i++) {
    if (lineTime > g_allRes[i].minLineTime && lineTime < g_allRes[i].maxLineTime) {
      return &g_allRes[i];
    }
  }
  return NULL;
}




