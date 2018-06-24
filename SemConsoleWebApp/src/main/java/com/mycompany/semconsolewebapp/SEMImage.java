/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.semconsolewebapp;

import static java.lang.Double.NaN;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

/**
 *
 * @author gmein
 */
public class SEMImage {

    public int channels;
    public int width;
    public int height;
    public WritableImage[] images;
    public WritableImage[] displayImages;

    public int[] capturedChannels;
    public int kv;
    public int magnification;
    public int wd;
    public String operators;

    PixelReader[] readers;
    PixelWriter[] writers;
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
    public int frameTime;

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
        displayImages = new WritableImage[channels];

        System.arraycopy(capturedChannels, 0, this.capturedChannels, 0, channels);

        //
        // make line buffer pool
        //
        this.lb = LineBuffer.grabLineBuffer(width, height, channels);
        this.lineCounter = 0;
    }

    SEMImage(String fileName) {
        // img_<count>_channel-<capturedchannel>.png>
        this.format = WritablePixelFormat.getIntArgbInstance();;
        this.lb = null;
        this.lineCounter = 0;
        this.lineBuffer = null;
        this.aRawLineBuffers = null;
        this.rangeMin = new int[4];
        this.rangeMax = new int[4];

        this.width = -1;
        this.height = -1;
        this.kv = -1;
        this.magnification = -1;
        this.wd = -1;
        this.operators = null;
        this.channels = 1;
        this.capturedChannels = new int[]{0};

        Session.parseFileName(fileName, this);

        this.images = new WritableImage[1];
        this.displayImages = new WritableImage[1];

        this.imageNames = new String[]{fileName};
        this.readers = new PixelReader[1];
        this.readers[0] = null;
        this.writers = new PixelWriter[1];
        this.writers[0] = null;

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
    int autoContrastValue(int value, int min, int max) {
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
    // Auto contrast: expand contrast from the recorded ranges to max
    //
    void autoContrast() {
        // need to perform autoContrast for every pixel
        // todo: add brightness, add UI controls
        for (int c = 0; c < channels; c++) {

            // determine image ranges for each channel
            rangeMin[c] = 4096;
            rangeMax[c] = 0;

            for (int line = 0; line < height; line++) {
                int[] line2 = new int[width];
                try {
                    readers[c].getPixels(0, line, width, 1, format, line2, 0, width);
                    for (int i = 0; i < width; i++) {
                        int intensity = intensityFromARGB(line2[i]);
                        rangeMin[c] = Math.min(rangeMin[c], intensity);
                        rangeMax[c] = Math.max(rangeMax[c], intensity);
                    }
                } catch (Exception e) {
                    System.err.println("autoContrast: range read failed, " + e.getMessage());
                    e.printStackTrace(System.err);
                    return;
                }
            }
//            Console.println("autoContrast: before: min: " + rangeMin[c] + " , max: " + rangeMax[c]);
            // adjust all values
            int min = 4096;
            int max = 0;
            for (int line = 0; line < height; line++) {
                int[] line2 = new int[width];
                try {
                    readers[c].getPixels(0, line, width, 1, format, line2, 0, width);
                    for (int i = 0; i < width; i++) {
                        int intensity = intensityFromARGB(line2[i]);
                        intensity = autoContrastValue(intensity, rangeMin[c], rangeMax[c]);
                        min = Math.min(min, intensity);
                        max = Math.max(max, intensity);
                        line2[i] = ARGBFromIntensity(intensity);
                    }
                    // write rawBuffer into images[c]
                    writers[c].setPixels(0, line, this.width, 1, this.format, line2, 0, this.width);
                } catch (Exception e) {
                    System.err.println("autoContrast: read or write failed, " + e.getMessage());
                    e.printStackTrace(System.err);
                    return;
                }
            }
//            Console.println("autoContrast: after: min: " + min + " , max: " + max);

            // no other processed images to display right now
            displayImages[c] = null;
        }
    }

    //
    // FFT: Create an image of the frequency spectrum of the image
    //
    void fft() {

        for (int c = 0; c < channels; c++) {
            double[] sum = new double[FFT.nextPower2(width)];

            for (int line = 0; line < height; line++) {
                double[] data = new double[FFT.nextPower2(width)];
                int[] line2 = new int[width];
                readers[c].getPixels(0, line, width, 1, format, line2, 0, width);
                for (int i = 0; i < width; i++) {
                    data[i] = intensityFromARGB(line2[i]);
                }

                // transform
                data = FFT.forward(data);

                // add to other lines
                for (int i = 0; i < data.length; i++) {
                    sum[i] += Math.log10(data[i]);
                }
            }

            // compact
            while (sum.length > 128) {
                double[] sum2 = new double[sum.length / 2];
                for (int i = 0; i < sum.length; i++) {
                    sum2[i / 2] += sum[i];
                }
                sum = sum2;
            }

            // determine range
            double max = 0;
            for (int i = 0; i < sum.length; i++) {
                max = Math.max(sum[i], max);
            }

            // normalize
            for (int i = 0; i < sum.length; i++) {
                sum[i] = sum[i] / max * height;
            }
            displayImages[c] = diagram("FFT view", sum);
        }
    }

    //
    // Oscilloscope: Create an image of the values as a time series
    //
    void oscilloscope() {

        for (int c = 0; c < channels; c++) {
            double[] data = new double[height];

            for (int line = 0; line < height; line++) {
                int[] line2 = new int[width];
                readers[c].getPixels(0, line, width, 1, format, line2, 0, width);
                for (int i = 0; i < width; i++) {
                    data[line] += intensityFromARGB(line2[i]) / 4095.0 * height;
                }
                data[line] /= width;
            }
            displayImages[c] = diagram("Oscilloscope view", data);

        }
    }

    //
    // histrogram: create a histogram of the intensity values
    //
    void histogram() {

        for (int c = 0; c < channels; c++) {
            double[] data = new double[100];

            for (int line = 0; line < height; line++) {
                int[] line2 = new int[width];
                readers[c].getPixels(0, line, width, 1, format, line2, 0, width);
                for (int i = 0; i < width; i++) {
                    data[(int) (intensityFromARGB(line2[i]) / 4096.0 * 100)]++;
                }
            }

            for (int i = 0; i < data.length; i++) {
                data[i] /= width;
            }
            displayImages[c] = diagram("Histogram view", data);

        }
    }

    WritableImage diagram(String title, double[] data) {
        Group g = new Group();

        Line xAxis = new Line(0, height / 2, width, height / 2);
        xAxis.setStroke(Color.WHITE);
        xAxis.setStrokeWidth(width * 0.002);

        Line yAxis = new Line(width / 2, 0, width / 2, height);
        yAxis.setStrokeWidth(height * 0.002);
        yAxis.setStroke(Color.RED);

        Text label = new Text(title);
        label.setFill(Color.GOLDENROD);

//        g.setTranslateY(height);
        g.getChildren().addAll(xAxis, yAxis);

        for (int i = 0; i < data.length; i++) {
            if (data[i] > 0) {
                Rectangle r = new Rectangle(i * (double) width / data.length, height - data[i], width / data.length, data[i]);
                r.setFill(Color.GOLDENROD);
//            r.setStrokeWidth(1.0);
                g.getChildren().add(r);
            }
        }

        new Scene(g, width, height, Color.BLACK);

        return g.snapshot(null, null);
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
            // copy one line of pixels for a specific channel out of data into rawBuffer
            pixel = 0;
            capturedChannel = translateChannel(getEncodedChannel(data[channel]));
            for (int i = channel; i < count; i += this.channels) {
                intensity = getValue(data[i]);
//                if (intensity < rangeMin[capturedChannel]) {
//                    rangeMin[capturedChannel] = intensity;
//                }
//                if (intensity > rangeMax[capturedChannel]) {
//                    rangeMax[capturedChannel] = intensity;
//                }

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
                    int[] line2 = new int[width];
                    boolean dataPresent = false;
                    siOld.readers[writeChannel].getPixels(0, line, width, 1, format, line2, 0, width);
                    for (int i = 0; i < lineBuffer.length; i++) {
                        lineBuffer[i] = (int) (lineBuffer[i] * Console.dNewWeight + intensityFromARGB(line2[i]) * (1 - Console.dNewWeight));
                        if (line2[i] != 0) {
                            dataPresent = true;
                        }
                    }
                    if (!dataPresent) {
                        throw new Exception("no data in old image");
                    }
                }

                // convert into ARGB
                for (int i = 0; i < lineBuffer.length; i++) {
                    lineBuffer[i] = ARGBFromIntensity(lineBuffer[i]);
                }
                // write rawBuffer into images[writeChannel]
                writers[writeChannel].setPixels(0, line, this.width, 1, this.format, lineBuffer, 0, this.width);
            } catch (Exception e) {
                System.err.println("parseRaw: " + line + ", " + height);
                System.err.println(e.getMessage());
                System.err.println(e.getStackTrace());

            }
        }
    }

    private int lineNumberFromTime(int time) {
        return (time*height)/frameTime;
    }
    
    public void makeImagesForDisplay(SEMImage siOld) {
        if (aRawLineBuffers == null || aRawLineBuffers.isEmpty()) {
            return;
        }

        // already done?
        if (images[0] != null) {
            return;
        }

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
            
            // convert the scan start times into line numbers
            int line = lineNumberFromTime(lineData[lineData.length - 1]);
            if (line < 0) {
                line = 0;
            }
            
            if (line >= height) {
                line = height-1;
            }
            while (++prevLine <= line) {
                this.parseRawLineToWriters(prevLine, lineData, lineData.length - 1, siOld);
            }
            prevLine = line;
        }

        //cleanUp();
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
//        for (int i = 0; i < channels; i++) {
//            writers[i] = null;
//        }
//
//        format = null;
//        pf = null;
//
//        aRawLineBuffers = null;
//        LineBuffer.returnLineBuffer(this.lb);

    }
}
