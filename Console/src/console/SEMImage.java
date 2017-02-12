/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package console;

import java.nio.IntBuffer;
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
    private final PixelWriter[] writers;
    private final int[] capturedChannels;
    private PixelFormat pf;
    private final WritablePixelFormat<IntBuffer> format;

    private final boolean firstLine;
    private final int[] rawBuffer;
    private static final int floorValue = 200; // TODO: ADC has to be properly calibrated

    SEMImage(int channels, int[] capturedChannels, int width, int height) {
        this.format = WritablePixelFormat.getIntArgbInstance();
        this.channels = channels;
        this.width = width;
        this.height = height;
        this.rawBuffer = new int[width];
        this.capturedChannels = new int[channels];

        images = new WritableImage[channels];
        writers = new PixelWriter[channels];
        for (int i = 0; i < channels; i++) {
            images[i] = new WritableImage(width, height);
            writers[i] = images[i].getPixelWriter();
        }
        
        System.arraycopy(capturedChannels, 0, this.capturedChannels, 0, channels);

        firstLine = true;
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
            // copy one line of pixel for a specific channel out of data into rawBuffer
            pixel = 0;
            capturedChannel = translateChannel(data[channel]);
            for (int i = channel; i < count; i += this.channels) {
                intensity = getValue(data[i]);
                // make a gray-scale, full alpha pixel
                rawBuffer[pixel++] = intensity = 0xFF000000 + (intensity<<16) + (intensity << 8) + intensity;
            }

            // find the right image to write into
            writeChannel = 0;
            for (int i = 0; i < this.channels; i++) {
                if (this.capturedChannels[i] == capturedChannel) {
                    writeChannel = i;
                    break;
                }
            }
            // write rawBuffer into images[capturedChannel]
            writers[writeChannel].setPixels(0, line, this.width, 1, this.format, rawBuffer, 0, this.width);
        }
    }

    // get the encoded channel number form a word in the data stream
    static int getEncodedChannel(int word) {
        return (word >> 12);

    }

    // get the raw value of the ADC reading, and adjust it to fit into a byte
    static int getValue(int word) {
        word = (word & 0xFFF) - SEMImage.floorValue; //TODO: better claibration and adjustment
        if (word > 255) {
            word = 255;
        }
        
        return word;
    }

    // maps encoded Arduino ADC channel tags into Ax input pin numbers (7 -> A0, 6-> A1 etc.)
    static int translateChannel(int word) {
        return 7 - getEncodedChannel(word);
    }
}

