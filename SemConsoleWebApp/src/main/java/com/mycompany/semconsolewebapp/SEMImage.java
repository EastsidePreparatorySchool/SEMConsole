/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.semconsolewebapp;

import java.nio.IntBuffer;
import java.util.ArrayList;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;

/**
 *
 * @author gmein
 */
public class SEMImage {

    public int channels;
    public int width;
    public int height;
    public WritableImage[] images;
    public int[] capturedChannels;
    public int kv;
    public int magnification;
    public int wd;
    public String operators;

    private PixelReader[] readers;
    private PixelWriter[] writers;
    private PixelFormat pf;
    private WritablePixelFormat<IntBuffer> format;

    private int[] lineBuffer;
    ArrayList<int[]> aRawLineBuffers;
    public int rangeMin[];
    public int rangeMax[];
    public int rangeMaxLine[];
    public int maxLine = 0;
    public String imageNames[];
    public LineBuffer lb;
    public int lineCounter;

    // for construction from stereo pairs only
    SEMImage left = null;
    SEMImage right = null;
    String fileName = null;

    Image thumbnail = null;

    SEMImage(int channels, int[] capturedChannels, int width, int height, int kv, int mag, int wd, String operators) {
        this.format = WritablePixelFormat.getIntArgbInstance();
        this.channels = channels;
        this.width = width;
        this.height = height;
        this.kv = kv;
        this.magnification = mag;
        this.wd = wd;
        this.operators = operators;

        this.lineBuffer = new int[width];
        this.aRawLineBuffers = new ArrayList<>(3000);
        this.capturedChannels = new int[channels];
        this.rangeMin = new int[channels];
        this.rangeMax = new int[channels];
        this.rangeMaxLine = new int[channels];

        images = new WritableImage[channels];
        readers = new PixelReader[channels];
        writers = new PixelWriter[channels];
        imageNames = new String[channels];

        System.arraycopy(capturedChannels, 0, this.capturedChannels, 0, channels);

        //
        // make line buffer pool
        //
        this.lb = LineBuffer.grabLineBuffer(width, height, channels);
        this.lineCounter = 0;
    }

    SEMImage(String fileName) {
        // img_<count>_channel-<capturedchannel>.png>
        this.format = null;
        this.lb = null;
        this.lineCounter = 0;
        this.lineBuffer = null;
        this.aRawLineBuffers = null;
        this.rangeMin = null;
        this.rangeMax = null;
        this.rangeMaxLine = null;
        this.writers = null;
        this.readers = null;

        this.width = -1;
        this.height = -1;
        this.kv = -1;
        this.magnification = -1;
        this.wd = -1;
        this.operators = null;
        this.channels = 1;
        this.capturedChannels = new int[]{-1};

        Session.parseFileName(fileName, this);

        this.images = new WritableImage[1];
        this.imageNames = new String[]{fileName};
    }

    SEMImage(SEMImage left, SEMImage right) {
        this.left = left;
        this.right = right;
        this.format = WritablePixelFormat.getIntArgbInstance();
        this.channels = Math.min(left.channels, right.channels);
        this.width = Math.min(left.width, right.width);
        this.height = Math.min(left.height, right.height);
        this.kv = left.kv;
        this.magnification = left.magnification;
        this.wd = left.wd;
        this.operators = left.operators;

        this.capturedChannels = new int[channels];
        System.arraycopy(left.capturedChannels, 0, this.capturedChannels, 0, channels);

        images = new WritableImage[channels];
        writers = new PixelWriter[channels];
        imageNames = new String[channels];

        // allocate images
        for (int i = 0; i < channels; i++) {
            images[i] = new WritableImage(width, height);
            writers[i] = images[i].getPixelWriter();
            readers[i] = images[i].getPixelReader();
        }
    }

    void dehydrate() {
        for (int i = 0; i < channels; i++) {
            if (imageNames[i] != null) {
                images[i] = null;
            }
        }
    }

    void rehydrate() {
        for (int i = 0; i < channels; i++) {
            if (imageNames[i] != null) {
                // todo: reload image from file;
            }
        }
    }

    void knitStereoImage() {
        int[] leftBuffer = new int[this.width];
        int[] rightBuffer = new int[this.width];
        for (int i = 0; i < this.channels; i++) {
            PixelReader prLeft = left.images[i].getPixelReader();
            PixelReader prRight = right.images[i].getPixelReader();
            PixelWriter pw = this.images[i].getPixelWriter();

            for (int line = 0; line < this.height; line++) {
                prLeft.getPixels(0, line, this.width, 1, this.format, leftBuffer, 0, 0);
                prRight.getPixels(0, line, this.width, 1, this.format, rightBuffer, 0, 0);

                for (int pixel = 0; pixel < this.width; pixel++) {
                    leftBuffer[pixel] = combinePixels(leftBuffer[pixel], rightBuffer[pixel]);
                }

                pw.setPixels(0, line, this.width, 1, this.format, leftBuffer, 0, 0);
            }
        }
    }

    int[] getNextDataLine() {
        if (lineCounter >= height) {
            return null;
        }
        return this.lb.data[lineCounter++];

    }

    void fileDataLine(int line, int[] data, int count) {
        if (line < 0) {
            System.out.println("Invalid line " + line);
            return;
        }

        data[count] = line;

        this.aRawLineBuffers.add(data);
        if (line > maxLine) {
            maxLine = line;
        }

    }

    //todo: is this what we want?
    int autoContrast(int value, int min, int max) {
        //return value;
        int newValue = (int) (((double) value - (double) min) * (double) 4095 / ((double) max - (double) min));
        if (newValue > 4095) {
            newValue = 4095;
        } else if (newValue < 0) {
            newValue = 0;
        }
        return newValue;
    }

    //
    // extract separate image lines from one line of data
    //
    void parseRawLineToWriters(int line, int[] data, int count, SEMImage siOld) {
        int pixel;
        int capturedChannel;
        int writeChannel;
        int intensity;

        if (line >= height) { // since we adjusted downward for jitter, some lines might be too high in number now.
            return;
        }

        for (int channel = 0; channel < this.channels; channel++) {
            // copy one line of pixels for a specific channel out of data into rawBuffer. record min and max.
            pixel = 0;
            capturedChannel = translateChannel(getEncodedChannel(data[channel]));
            for (int i = channel; i < count; i += this.channels) {
                intensity = getValue(data[i]);
                if (intensity < rangeMin[capturedChannel]) {
                    rangeMin[capturedChannel] = intensity;
                }
                if (intensity > rangeMax[capturedChannel]) {
                    rangeMax[capturedChannel] = intensity;
                }

                lineBuffer[pixel++] = intensity;
            }

            // find the right image to write into
            writeChannel = 0;
            for (int i = 0; i < this.channels; i++) {
                if (this.capturedChannels[i] == capturedChannel) {
                    writeChannel = i;
                    break;
                }
            }

            try {
                // if we are in cumulative mode, find the right reader, combine buffers
                if (siOld != null && Console.dNewWeight < 1.0 && width == siOld.width && height == siOld.height) {
                    // combine pixels by weight (dNewWeight)
                    // todo: Allocating line2 should not be necessary, I should be able to reuse the oldSi.lineBuffer, but it just gives dark images. Try again.
                    int[] line2 = new int[lineBuffer.length];
                    siOld.readers[writeChannel].getPixels(0, line, siOld.width, 1, siOld.format, line2, 0, siOld.width);
                    for (int i = 0; i < lineBuffer.length; i++) {
                        lineBuffer[i] = (int) (lineBuffer[i] * Console.dNewWeight + intensityFromARGB(line2[i]) * (1 - Console.dNewWeight));

                    }
                }
                // convert into ARGB
                for (int i = 0; i < lineBuffer.length; i++) {
                    lineBuffer[i] = ARGBFromIntensity(lineBuffer[i]);
                }
                // write rawBuffer into images[writeChannel]
                writers[writeChannel].setPixels(0, line, this.width, 1, this.format, lineBuffer, 0, this.width);
            } catch (Exception e) {
                System.out.println("pixel Write failed: " + line + ", " + height);
                System.out.println(e.getStackTrace());

            }
        }
    }

    public void makeImagesForDisplay(SEMImage siOld) {
        if (aRawLineBuffers == null || aRawLineBuffers.isEmpty()) {
            return;
        }

        // already done?
        if (images[0] != null) {
            return;
        }

        // compute min and max for contrast
        int size = aRawLineBuffers.size();

        // allocate images
        for (int i = 0; i < channels; i++) {
            images[i] = new WritableImage(width, height);
            writers[i] = images[i].getPixelWriter();
            readers[i] = images[i].getPixelReader();
        }

        // parse all lines, 
        int prevLine = -1;
        for (int i = 0; i < size; i++) {
            int[] lineData = aRawLineBuffers.get(i);
            int line = (lineData[lineData.length - 1]) + (height - (maxLine + 1)); // correct for vsync jitter by aligning at bottom
            if (line < 0) {
                line = 0;
            }
            while (++prevLine <= line) {
                this.parseRawLineToWriters(prevLine, lineData, lineData.length - 1, siOld);
            }
            prevLine = line;
        }

        // need to perform autoContrast for every pixel
        // todo: add brightness, add UI controls
        for (int c = 0; c < channels; c++) {
            int channel = capturedChannels[c];
            rangeMin[c] = 0;
            rangeMax[c] = 4095;

            for (int line = 0; line < height; line++) {
                int[] line2 = new int[width];
                readers[channel].getPixels(0, line, width, 1, format, line2, 0, width);
                for (int i = 0; i < width; i++) {
                    int intensity = intensityFromARGB(line2[i]);
                    intensity = autoContrast(intensity, rangeMin[c], rangeMax[c]);
                    line2[i] = ARGBFromIntensity(intensity);
                }
                // write rawBuffer into images[c]
                writers[channel].setPixels(0, line, this.width, 1, this.format, line2, 0, this.width);
            }
        }

        cleanUp();
    }

    // get the encoded channel number from a word in the data stream
    int getEncodedChannel(int word
    ) {
        return (word >> 12);

    }

    // get the raw value of the ADC reading, and adjust it to fit into a byte
    int getValue(int word
    ) {
        word = ((word & 0xFFF));
        if (word > 4095) {
            word = 4095;
        }
        if (word < 0) {
            word = 0;
        }
        return word;
    }

    // converts intensity into a kind of gray scale, 6 bits are distributed evenly, 2+2+2 divided up between the colors
    // that way, we can save and later parse all the data with no losses
    int ARGBFromIntensity(int intensity
    ) {
        // todo: real gain calibration
        if (intensity > 4095) {
            intensity = 4095;
        } else if (intensity < 0) {
            intensity = 0;
        }

        int highSix = ((intensity >> 6) & 0x3F) << 2;
        int lowSix = intensity & 0x3F;
        int r = lowSix & 0x3;
        lowSix >>= 2;
        int g = lowSix & 0x3;
        lowSix >>= 2;
        int b = lowSix & 0x3;

        return 0xFF000000 // full alpha
                + ((highSix + r) << 16) // red
                + ((highSix + g) << 8) // green
                + (highSix + b);         // blue
    }

    int intensityFromARGB(int argb
    ) {
        return ((argb & 0xFF) << 4)
                + ((argb & 0x300) >> 6)
                + ((argb & 0x30000) >> 16);
    }

    // makes a red/blu stereo pixel out of two source pixels
    // decodes intensities, put 8bit + 8bit back together again
    // presumes that source pixels were computed by grayScale() (see above)
    int combinePixels(int left, int right
    ) {
        int intensityLeft = left & 0xFF; // blue contained the high 8 bits of intensity
        int intensityRight = right & 0xFF;

        return (0xFF000000 // full alpha
                + (intensityLeft << 16) // left becomes red
                + (intensityRight)); // right becomes blue
    }

// maps encoded Arduino ADC channel tags into Ax input pin numbers (7 -> A0, 6-> A1 etc.)
    int translateChannel(int word
    ) {
        return 7 - word;
    }

    void cleanUp() {
        for (int i = 0; i < channels; i++) {
            writers[i] = null;
        }

        format = null;
        pf = null;

        aRawLineBuffers = null;
        LineBuffer.returnLineBuffer(this.lb);

    }
}
