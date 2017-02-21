// 
// SEM EPS for Arduino Due
// Input: Analog in A0, A1, A2, A3, hsync and vsync at D1 and D2
// Output: USB frames to PC
//


// set to 1 to test, empty to run normal
#define TEST 1
// set to number of seconds to stay in mode
#define TEST_MODE_LENGTH 20



// test mode
#if TEST == 1
  #define TEST(a,b,c) test(a,b,c,TEST_MODE_LENGTH)
#else
  #define TEST(a,b,c) 
#endif

#define TESTSIGNAL TEST(false, false, 0);



// USB communication headers

#define COMMAND_BYTES 16
byte headerConnect[COMMAND_BYTES]  = {'E','P','S','_','S','E','M','_','C','O','N','N','E','C','T','.'};
byte headerReady[COMMAND_BYTES]    = {'E','P','S','_','S','E','M','_','R','E','A','D','Y','.','.','.'};
byte headerFrame[COMMAND_BYTES]    = {'E','P','S','_','S','E','M','_','F','R','A','M','E','.','.','.'};
byte headerBytes[COMMAND_BYTES]    = {'E','P','S','_','S','E','M','_','B','Y','T','E','S','.','.','.'};
byte headerEndFrame[COMMAND_BYTES] = {'E','P','S','_','S','E','M','_','E','N','D','F','R','A','M','E'};
byte headerReset[COMMAND_BYTES]    = {'E','P','S','_','S','E','M','_','R','E','S','E','T','.','.','.'};
byte headerIdle[COMMAND_BYTES]     = {'E','P','S','_','S','E','M','_','I','D','L','E','.','.','.','.'};

#define SENTINEL_BYTES 16
byte sentinelTrailer[SENTINEL_BYTES] = {0,1,2,3,4,5,6,7,8,9,0xA,0xB,0xC,0xD,0xE,0xF};

struct BytesParams {
  byte      headerBytes[16];
  uint32_t  checkSum;         // now includes everything from here on, to just before sentinel trailer
  uint16_t  line;
  uint16_t  bytes;
};

struct BytesParams *g_pbp = NULL;


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

#define NUM_MODES 3

struct Resolution g_allRes[NUM_MODES] = {
  {   100,   200,  250, 1,  533 }, // RAPID2
  {  4900,  5100, 2000, 4, 1000 }, // SLOW1
  { 39000, 41000, 4200, 2, 2500 }  // H6V7
};
/*
struct Resolution g_allRes[NUM_MODES] = {
  {   130,   160,  284, 1,  533 }, // RAPID2
  {  4900,  5100, 2200, 4, 1000 }, // SLOW1
  { 39000, 41000, 4770, 2, 2500 }  // H6V7
};
*/


#define HSYNC_PIN   1
#define VSYNC_PIN   2

#define PHASE_IDLE                0
#define PHASE_READY_TO_MEASURE    1
#define PHASE_MEASURING           2
#define PHASE_READY_FOR_SCAN      3
#define PHASE_SCANNING            4



int g_channelSelection1 = 0; // TODO: Make this programmable with digital inputs
int g_channelSelection2 = 2; // TODO: For now, make sure that SEI is on A0 and AEI on A1

//
// Buffers are really independent of the resolution we are tracking, just make them big enough
//

#define NUM_BUFFERS   2 // fill one with DMA while main program reads and copies the other
#define BUFFER_LENGTH (5000 * 8 + 64)
#define BUFFER_BYTES  (BUFFER_LENGTH * sizeof(uint16_t))
#define NEXT_BUFFER(n)((n+1)%NUM_BUFFERS) // little macro to aid switch to next buffer

volatile int currentBuffer; 
volatile int nextBuffer;
uint16_t *padcBuffer[NUM_BUFFERS];   
volatile unsigned long g_adcLineTimeStart;
volatile unsigned long g_adcLineTime;
int g_lineBytes;
volatile int g_phase = PHASE_IDLE;
volatile int g_measuredLineTime;



#define MAX_ERRORS  50
#define USB_TIMEOUT 50









// code to blink the built-in LED n times 
const int builtInLEDPin = 13; 

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


//
// ERROR HALT
// 

void halt(int blinks) {
  while (true) {
    blinkBuiltInLED(blinks);
    delay(1000);
  }
}

//
// software reset
//

void reset() {
  freeLineBuffers(); // safe to do
  
  while(SerialUSB.available()) {
    SerialUSB.read();
  }
  SerialUSB.write(headerReset, 16);
  delay(100);

  setup();
}


void setup() {
    // start USB
  SerialUSB.begin(0); 

  // set up built-in blink LED, custom led, pushButton
  pinMode (builtInLEDPin, OUTPUT);
  // visual signal that we are alive
  blinkBuiltInLED(1);
  
  int n;
  byte buffer[16];

  // initialize buffer pointers to NULL
  g_pbp = NULL;
  for (int i = 0; i < NUM_BUFFERS; i++) {
    padcBuffer[i] = NULL;
  }

 
  // wait for USB connect command from host
  do {
    do {
      // wait for any request
      do {
        delayMicroseconds(20); 
        n = SerialUSB.available();
      } while (n == 0);


      // read the request
      SerialUSB.readBytes(buffer, 16);

      // read any extra bytes
      while (SerialUSB.available()) {
        SerialUSB.read();   
      }
    } while (n != COMMAND_BYTES);
   } while (memcmp(buffer, headerConnect, COMMAND_BYTES) != 0);
  
  // acknowledge connection request
  SerialUSB.write(headerReady, 16);
  SerialUSB.flush();
  blinkBuiltInLED(2);  
  g_pCurrentRes = NULL;
  g_phase = PHASE_IDLE;

  TEST(true, true, 1); // start test signal simulation in mode 1
}



void computeCheckSum(int line, int bytes) {
  // compute checkSum
  long checkSum = 0;
  uint16_t *pWord = (uint16_t *)&g_pbp[1];
  for (int i=0; i<bytes/2; i++) {
    checkSum += *pWord++;
  }
  
  g_pbp->checkSum = checkSum + line + bytes;
  g_pbp->line = line;
  g_pbp->bytes = bytes;
}


bool sendLine(int bytes) {
  // send the line until it gets through
  int errorCount;
  char o,k;
  
  for (errorCount = 0; errorCount < 50; errorCount++) {
    SerialUSB.write((uint8_t *)g_pbp, sizeof(struct BytesParams) +  bytes + sizeof(sentinelTrailer));
    
    // wait for response
    long wait = micros();
    long acceptable = wait + (USB_TIMEOUT);
    while (SerialUSB.available() == 0 && wait<acceptable) {
      TESTSIGNAL;
      delayMicroseconds(10);
      wait = micros();
    }
    
    if (wait >= acceptable)
      continue;
    
    // process two bytes of response, either "OK" or "NG"
    o = SerialUSB.read();   
    k = SerialUSB.read();
    
    if (o == 'O' && k == 'K') // ok, on to next lines
      break;
      
    if (o == 'N' && k == 'G') // no good, retry sending the line
      continue;

    if (o == 'A' && k == 'B') // ABORT frame, things are messed up, reset
      return false;
    
  }
  
  if (errorCount >= MAX_ERRORS) {
    return false; // too many errors, reset
  }
    
 return true;
}



int setupLineBuffers() {
  // compute raw byte number for one scan line
  int bytes = (g_pCurrentRes->numPixels * g_pCurrentRes->numChannels * sizeof(uint16_t));

  for (int i = 0; i < NUM_BUFFERS; i++) {
    padcBuffer[i] = (uint16_t *)malloc(bytes);
    if (padcBuffer[i] == NULL) {
      halt(3);
    }
  }
  
  // compute buffer size for whole USB command, allocate, and fill with known info
  int bufferSize = sizeof(struct BytesParams) +  bytes + sizeof(sentinelTrailer);
  g_pbp = (struct BytesParams *) malloc (bufferSize); 
  if (g_pbp == NULL) {
    halt(4);
  }

  memcpy(&g_pbp->headerBytes, headerBytes, sizeof(headerBytes));
  memcpy(((byte *)&g_pbp[1]) + bytes, sentinelTrailer, sizeof (sentinelTrailer)); 
  g_pbp->bytes = bytes;

  // return the size for USB send
  return bufferSize;
}




void freeLineBuffers() {
  for (int i = NUM_BUFFERS-1; i >= 0; i--) {
    if (padcBuffer != NULL) {
      free(padcBuffer[i]);
      padcBuffer[i] = NULL;
    }
  }

  if (g_pbp != NULL) {
    free (g_pbp);
    g_pbp = NULL;
  }
}





void sendFrameHeader() {
  SerialUSB.write(headerFrame, 16);                     // send FRAME header
  SerialUSB_write_uint16_t(g_pCurrentRes->numChannels); // number of channels                       
  SerialUSB_write_uint16_t(g_pCurrentRes->numPixels);   // width in pixels
  SerialUSB_write_uint16_t(g_pCurrentRes->numLines);    // height in lines
  SerialUSB_write_uint16_t(g_measuredLineTime);         // measured line scan time that determined resolution
  
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
  ADC->ADC_RPR = (uint32_t)padcBuffer[0]; // set up DMA buffer
  ADC->ADC_RCR = g_pCurrentRes->numPixels * g_pCurrentRes->numChannels;           // and number of words
  ADC->ADC_RNPR = (uint32_t)padcBuffer[1]; // next DMA buffer
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
    ADC->ADC_RNPR = (uint32_t)padcBuffer[nextBuffer]; // put it in place
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


void setupInterrupts() {
  pinMode(VSYNC_PIN, INPUT);
  pinMode(HSYNC_PIN, INPUT);
  attachInterrupt(VSYNC_PIN, vsyncHandler, FALLING);  // catch falling edge of vsync to get ready for measuring
  attachInterrupt(HSYNC_PIN, hsyncHandler, RISING);   // catch rising edge of hsync to start ADC
}

void vsyncHandler() {
  switch (g_phase) {
    case PHASE_IDLE:
      g_phase = PHASE_READY_TO_MEASURE;
      break;
      
    case PHASE_SCANNING:
      // time to end the frame and send the image
      g_phase = PHASE_IDLE;
      break;

    case PHASE_READY_TO_MEASURE:
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
    
    case PHASE_READY_TO_MEASURE:
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
      // start ADC (completion handled by ADC interrupt)
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



void loop () {
  static int numLines = 0;
  static int timeFrame = 0;
  static bool fFrameInProgress = false;
  static struct Resolution *lastRes = NULL;

  int timeLineScan = 0;
  char o,k;

  // 
  // let hsync measure the time
  //
  
  while (g_phase == PHASE_READY_TO_MEASURE) {
    TESTSIGNAL;
    delayMicroseconds (10);
  }

  while (g_phase == PHASE_MEASURING) {
    TESTSIGNAL;
    delayMicroseconds (10);
  }

  //
  // time has been measured by hsync, let's do our calculations, set up the scan buffers and start scanning!
  //
  if (g_phase == PHASE_READY_FOR_SCAN) {
    g_pCurrentRes = getResolution(g_measuredLineTime);
    if (g_pCurrentRes != NULL) {
      if (g_pCurrentRes != lastRes) {
        adjustToNewRes();
        lastRes = g_pCurrentRes;
      }
      
      fFrameInProgress = true;
      sendFrameHeader();
      numLines = 0;
      timeFrame = millis();
      g_phase = PHASE_SCANNING;
    } else
      g_phase = PHASE_IDLE;
  }
  TESTSIGNAL;

  //
  // main line scanning
  // vsync will get us out of this
  //
  while (g_phase == PHASE_SCANNING) {
    TESTSIGNAL;
    // wait for scan completion, get line out of the way of the DMA controller
    scanAndCopyOneLine();

    // keep track of maximum scan time (we report this so we can dial it in and get max possible pixel res)
    timeLineScan = max(timeLineScan, g_adcLineTime);

    // compute check sum and fill in line and bytes
    computeCheckSum(numLines, g_lineBytes);

    // try to send the line, if things go wrong too often, reset.
    if (!sendLine(g_lineBytes)) {
      reset();
      return;
    }
              
    numLines++;
  }

  //
  // aftermath
  // send frame, check for abort
  //
  while (g_phase == PHASE_IDLE) {
    TESTSIGNAL;
    if (fFrameInProgress) {
      timeFrame = millis() - timeFrame;
      sendEndFrame (timeLineScan, timeFrame);
      fFrameInProgress = false;
    } else {
      sendIdle();
    }
    g_phase = PHASE_READY_TO_MEASURE;
    //delayMicroseconds (100); // TODO: Good sleep value? need to sleep at all?
    
    // check for abort
    o = 0;
    while (SerialUSB.available()) {
      TESTSIGNAL;
      k = SerialUSB.read();
      if (o == 'A' && k == 'B') {
        reset();
        return;
      } 
      o = k;
    }
  }

  
}

void adjustToNewRes() {
    // free previous buffers (safe to do)
    freeLineBuffers();

    //
    // calculate some basic frame parameters and allocate buffer, init adc
    //
    g_lineBytes = (g_pCurrentRes->numPixels * g_pCurrentRes->numChannels * sizeof(uint16_t));
    setupLineBuffers();
    initializeADC();
}



void scanAndCopyOneLine() {
  while (NEXT_BUFFER(currentBuffer) == nextBuffer) {                  // while current and next are one apart
    TESTSIGNAL;
    delayMicroseconds(10);                                            // wait for buffer to be full
  }

  // put the line somewhere safe from adc, just past the params header:
  memcpy(&g_pbp[1], padcBuffer[currentBuffer], g_lineBytes);
  currentBuffer = NEXT_BUFFER(currentBuffer);                         // set next buffer for waiting
}


void sendIdle() {
    SerialUSB.write(headerIdle, 16);
}


void test(bool fResetMode, bool fResetFrame, int mode, int modeSeconds) {
  static int timeModeStart = 0;
  static int timeMillisStart = 0;
  static int timeMicrosStart = 0;
  static struct Resolution *pRes = NULL;
  static int curMode;
  static int line;
  static int lineTime;
  static int timeModeLength;

  if (fResetMode) {
    blinkBuiltInLED(3);
    timeModeStart = millis();
    timeModeLength = modeSeconds;
    pRes = &g_allRes[mode];
    curMode = mode;
    line = 0;
    lineTime = (pRes->minLineTime + pRes->maxLineTime) / 2;
  }

  if (fResetFrame) {
    // new frame, possibly new res
    timeMillisStart = millis();
    timeMicrosStart = micros();
    line = 0;
    // simulate vsync event
    vsyncHandler();
    
    return;
  } 

  int timeMicros = micros()-timeMicrosStart;  // micros in line
  int timeMillis = millis()-timeMillisStart;  // millis in frame
  int timeMode = millis()-timeModeStart;      // time in current resolution mode

  if (line == pRes->numLines) {
    // frame done
    // todo: select new res mode here from time to time
    
    // end frame, trigger vsync
    if (timeMode > (timeModeLength*1000)) {
      curMode = (curMode+1)%NUM_MODES;
      test(true, true, curMode, timeModeLength);
    } else {
      test(false, true, curMode, timeModeLength);
    }
  }

  if (timeMicros > lineTime) {
    // line is up, trigger hsync
    timeMicrosStart = micros();
    hsyncHandler();
    line++;
    return;
  }

  // if we make it to here, we are inside a line

  analogWrite(2, (line*1024/pRes->numLines) % 256);
  analogWrite(DAC0, (line*256/pRes->numLines) % 256);
  analogWrite(DAC1, 256-((line*256/pRes->numLines) % 256));
}


