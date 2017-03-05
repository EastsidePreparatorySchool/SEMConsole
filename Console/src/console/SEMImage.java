/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package console;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import javafx.scene.image.PixelFormat;
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
    public final int[] capturedChannels;

    private PixelWriter[] writers;
    private PixelFormat pf;
    private WritablePixelFormat<IntBuffer> format;

    private final boolean firstLine;
    private int[] rawBuffer;
    private int[] grayRawBuffer;
    ArrayList<int[]> alineBuffers;
    public int rangeMin[];
    public int rangeMax[];
    public int rangeMaxLine[];
    public int maxLine = 0;
    BufferedImage grayImages[];
    WritableRaster[] rasters;

    private static final int floorValue = 8; // TODO: ADC has to be properly calibrated

    SEMImage(int channels, int[] capturedChannels, int width, int height) {
        this.format = WritablePixelFormat.getIntArgbInstance();
        this.channels = channels;
        this.width = width;
        this.height = height;
        this.rawBuffer = new int[width];
        this.grayRawBuffer = new int[width];
        this.alineBuffers = new ArrayList<>(3000);
        this.capturedChannels = new int[channels];
        this.rangeMin = new int[channels];
        this.rangeMax = new int[channels];
        this.rangeMaxLine = new int[channels];

        images = new WritableImage[channels];
        writers = new PixelWriter[channels];
        grayImages = new BufferedImage[channels];
        rasters = new WritableRaster[channels];

        System.arraycopy(capturedChannels, 0, this.capturedChannels, 0, channels);

        firstLine = true;
    }

    void fileDataLine(int line, int[] data, int count) {
        if (line < 0) {
            return;
        }

        int[] copy = Arrays.copyOf(data, count + 1);
        copy[count] = line;
        this.alineBuffers.add(copy);
        if (line > maxLine) {
            maxLine = line;
        }

    }

    void parseAllLines() {
        int size = alineBuffers.size();
        if (size > 0) {
            int[] lastLine = alineBuffers.get(size - 1);

            if (maxLine + 1 > height) {
                this.height = maxLine + 1;
            }
            // allocate images
            for (int i = 0; i < channels; i++) {
                images[i] = new WritableImage(width, height);
                writers[i] = images[i].getPixelWriter();
                rangeMin[i] = 4095;
                rangeMax[i] = 0;

                grayImages[i] = new BufferedImage(width, height, BufferedImage.TYPE_USHORT_GRAY);
                rasters[i] = (WritableRaster) grayImages[i].getData();

            }

            // compute ranges from first 75% of image. ignore duplicates as best we can
            int prevLine = -1;
            for (int i = 0; i < (size * 3 / 4); i++) {
                int[] data = alineBuffers.get(i);

                if (i == 0) {
                    Console.print("{" + Integer.toHexString(data[0]) + "}");
                }

                int line = data[data.length - 1];
                if (line != prevLine) {
                    rangeLine(line, data, data.length - 1); // don't range that last int, which is the line number
                }
                prevLine = line;
            }

            // parse all lines, correcting data values for ranges
            prevLine = -1;
            for (int i = 0; i < size; i++) {
                int[] lineData = alineBuffers.get(i);
                int line = (lineData[lineData.length - 1]) + (height - maxLine + 1); // correct for vsync jitter by aligning at bottom
                while (++prevLine <= line) {
                    this.parseRawLine(prevLine, lineData, lineData.length - 1);
                }
                prevLine = line;
            }
            
            for (int i = 0; i < channels; i++) {
                grayImages[i].setData(rasters[i]);
            }

        }
    }

    // keep track of min and max for a line, for all channels
    void rangeLine(int line, int[] data, int count) {
        int intensity;

        for (int channel = 0; channel < this.channels; channel++) {
            for (int i = channel; i < count; i += this.channels) {
                intensity = getValue(data[i]);
                recordRange(channel, line, intensity);
            }
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
    void parseRawLine(int line, int[] data, int count) {
        int pixel;
        int capturedChannel;
        int writeChannel;
        int intensity;

        for (int channel = 0; channel < this.channels; channel++) {
            // copy one line of pixels for a specific channel out of data into rawBuffer
            pixel = 0;
            capturedChannel = translateChannel(getEncodedChannel(data[channel]));
            for (int i = channel; i < count; i += this.channels) {
                intensity = getValue(data[i]);
                intensity = autoContrast(intensity, rangeMin[channel], rangeMax[channel]);

                // make a pseudo-gray-scale, full alpha pixel for display
                rawBuffer[pixel] = grayScale(capturedChannel, intensity);

                // and also a true grayscale image for saving to disk as PNG 16bit grayscale later
                grayRawBuffer[pixel] = intensity << 4;
                
                pixel++;
            }

            // find the right image to write into
            writeChannel = 0;
            for (int i = 0; i < this.channels; i++) {
                if (this.capturedChannels[i] == capturedChannel) {
                    writeChannel = i;
                    break;
                }
            }
            // write rawBuffer into images[writeChannel]
            try {
                writers[writeChannel].setPixels(0, line, this.width, 1, this.format, rawBuffer, 0, this.width);
                rasters[writeChannel].setPixels(0, line, width, 1, grayRawBuffer);
            } catch (Exception e) {
                System.out.println("Write failed: " + line);
                System.out.println(e.getStackTrace());
            }
        }
    }

    // get the encoded channel number from a word in the data stream
    int getEncodedChannel(int word) {
        return (word >> 12);

    }

    // get the raw value of the ADC reading, and adjust it to fit into a byte
    int getValue(int word) {
        word = ((word & 0xFFF)); // - SEMImage.floorValue;
        if (word > 4095) {
            word = 4095;
        }
        if (word < 0) {
            word = 0;
        }
        return word;
    }

    void recordRange(int channel, int line, int intensity) {
        if (intensity < rangeMin[channel]) {
            rangeMin[channel] = intensity;
        }

        if (intensity > rangeMax[channel]) {
            rangeMax[channel] = intensity;
            rangeMaxLine[channel] = line;
        }
    }

    int grayScale(int realChannel, int intensity) {
        // todo: real gain calibration
        if (intensity > 4095) {
            intensity = 4095;
        } else if (intensity < 0) {
            intensity = 0;
        }

//        if (realChannel == 0) {
        //special treatment
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
//    }
//
//    
//        else {
//            intensity *= 4;
//        final int shiftFactor = 0;
//        return 0xFF000000 // full alpha
//                + ((realChannel == 2 ? (intensity >> 4) : ((intensity & 0xF) << shiftFactor)) << 16) // red
//                + ((realChannel == 1 ? (intensity >> 4) : ((intensity & 0xF) << shiftFactor)) << 8) // green
//                + ((realChannel == 3 ? (intensity >> 4) : ((intensity & 0xF) << shiftFactor)));      // blue
//    }
}

// maps encoded Arduino ADC channel tags into Ax input pin numbers (7 -> A0, 6-> A1 etc.)
int translateChannel(int word) {
        return 7 - word;
    }

    void cleanUp() {
        for (int i = 0; i < channels; i++) {
            writers[i] = null;
        }

        format = null;
        pf = null;

        rawBuffer = null;
        grayRawBuffer = null;
        alineBuffers = null;

    }
}
