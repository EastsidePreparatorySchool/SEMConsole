package org.eastsideprep.imagefix;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.scene.control.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Scanner;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javax.imageio.ImageIO;
import javafx.scene.image.*;
import javafx.scene.layout.HBox;

/**
 * JavaFX App
 */
public class App extends Application {

    ArrayList<Offset> offsets = new ArrayList<>();
    Rectangle2D viewportRect;
    double originX;
    double originY;

    @Override
    public void start(Stage stage) {
        var javaVersion = SystemInfo.javaVersion();
        var javafxVersion = SystemInfo.javafxVersion();

        InputStream is = App.class.getResourceAsStream("/offsets.csv");
        Scanner sc = new Scanner(is);
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (!line.equals("")) {
                String[] numbers = line.split(",");
                offsets.add(new Offset(Integer.parseInt(numbers[0]), Integer.parseInt(numbers[1])));
            }
        }

        // load src image
        Image img1 = loadImage();
        // make writable copy
        WritableImage img2 = SwingFXUtils.toFXImage(SwingFXUtils.fromFXImage(img1, null), null);
        // apply the offsets that we read from the resource
        applyOffsets(img2, offsets);

        // make to picture frames
        ImageView iv1 = new ImageView(img1);
        ImageView iv2 = new ImageView(img2);

        // configure the visible region
        viewportRect = new Rectangle2D(1750, 150, 100, 100);
        iv1.setViewport(viewportRect);
        iv1.setFitHeight(500);
        iv1.setPreserveRatio(true);
        iv1.setSmooth(false);
        iv2.setViewport(viewportRect);
        iv2.setFitWidth(500);
        iv2.setPreserveRatio(true);
        iv2.setSmooth(false);

        // control box
        Label l = new Label("Line:");
        TextField tf = new TextField();

        Button bRight = new Button("->");
        bRight.setOnAction((e) -> {
            adjustOffset(img2, offsets, Integer.parseInt(tf.getText()), 1);
        });
        bRight.setPrefWidth(200);

        Button bLeft = new Button("<-");
        bLeft.setOnAction((e) -> {
            adjustOffset(img2, offsets, Integer.parseInt(tf.getText()), -1);
        });
        bLeft.setPrefWidth(200);

        Button bFix = new Button("Fixify");
        bFix.setOnAction((e) -> {
            calculateAndApplyOffsets(img2);
        });
        bFix.setPrefWidth(200);

        Button bSave = new Button("Save");
        bSave.setOnAction((e) -> saveToFile(img2, offsets));
        bSave.setPrefWidth(200);

        VBox controls = new VBox();
        controls.getChildren().addAll(l, tf, bRight, bLeft, bSave, bFix);

        // wire up the mouse click event to populate the line number
        iv1.setOnMouseClicked((e) -> {
            //System.out.println(e);
            double y = e.getY();
            y = y / 500 * viewportRect.getHeight() + viewportRect.getMinY();
            tf.setText("" + (int) y);
        });

        iv1.setOnMousePressed((e) -> {
            originX = e.getX() / 500 * viewportRect.getHeight();
            originY = e.getY() / 500 * viewportRect.getHeight();
        });

        iv1.setOnMouseDragged((e) -> {
            viewportRect = new Rectangle2D(
                    viewportRect.getMinX() + originX - e.getX() / 500 * viewportRect.getHeight(),
                    viewportRect.getMinY() + originY - e.getY() / 500 * viewportRect.getHeight(),
                    viewportRect.getWidth(),
                    viewportRect.getHeight());
            iv1.setViewport(viewportRect);
            iv2.setViewport(viewportRect);
            originX = e.getX() / 500 * viewportRect.getHeight();
            originY = e.getY() / 500 * viewportRect.getHeight();
        });

        HBox hb = new HBox();
        hb.getChildren().addAll(iv1, controls, iv2);

        var scene = new Scene(hb);
        stage.setScene(scene);
        stage.show();
    }

    int errorFunction(String errorType, int[] line1, int[] line2) {

        if (errorType.equals("LSE")) {//least squared error
            int errorSum = 0;
            for (int i = 0; i < line1.length; i++) {
                errorSum += (line1[i] - line2[i]) * * 2;
            }
            return errorSum
        }
        return -1;//all errors are non negative 
    }

    void calculateAndApplyOffsets(WritableImage img) {
        int maxOffset = 10;
        int errorType = "LSE";

        int width = (int) img.getWidth();
        int height = (int) img.getHeight();

        PixelReader reader = img.getPixelReader();
        PixelWriter writer = img.getPixelWriter();
        WritablePixelFormat<IntBuffer> format
                = WritablePixelFormat.getIntArgbInstance();

        int[] line1 = new int[width];
        int[] line2 = new int[width];
        int[] errors = new int[maxOffset * 2 + 1];

        for (int line = 0; line < height - 1; line++) {
            //shift -10 to 10 pixels in each direction, keep the one with smallest error

            for (int o = -maxOffset; o <= maxOffset; o++) {
                //yucky, could optimize later
                if (o > 0) {
                    reader.getPixels(0, line, width, 1, format, line1, 0, 0);
                    writer.setPixels(o, line, width - o, 1, format, line1, 0, 0);
                } else {
                    reader.getPixels(-o, line, width + o, 1, format, line1, 0, 0);
                    writer.setPixels(0, line, width + o, 1, format, line1, 0, 0);
                }

                reader.getPixels(0, line, width, 1, format, line2, 0, 0);
                errors[o + maxOffset] = errorFunction(errorType, line1, line2)//offset of -max should go to 0, offset of max should go to end of array
            }

            int bestOffset = -maxOffset;
            for (int i = 0; i < errors.length; i++) {
                if (errors[i] < errors[bestOffset + maxOffset]) {
                    bestOffset = i - maxOffset;
                }
            }
            if (bestOffset > 0) {
                reader.getPixels(0, line, width, 1, format, line1, 0, 0);
                writer.setPixels(bestOffset, line, width - bestOffset, 1, format, line1, 0, 0);
            } else {
                reader.getPixels(-bestOffset, line, width + bestOffset, 1, format, line1, 0, 0);
                writer.setPixels(0, line, width + bestOffset, 1, format, line1, 0, 0);
            }
        }
    }

    void applyOffsets(WritableImage img, ArrayList<Offset> offsets) {
        int width = (int) img.getWidth();
        int height = (int) img.getHeight();

        PixelReader reader = img.getPixelReader();
        PixelWriter writer = img.getPixelWriter();
        WritablePixelFormat<IntBuffer> format
                = WritablePixelFormat.getIntArgbInstance();

        int[] lineBuffer = new int[width];

        for (Offset offset : offsets) {
            if (offset.offset > 0) {
                reader.getPixels(0, offset.line, width, 1, format, lineBuffer, 0, 0);
                writer.setPixels(offset.offset, offset.line, width - offset.offset, 1, format, lineBuffer, 0, 0);
            } else {
                reader.getPixels(-offset.offset, offset.line, width + offset.offset, 1, format, lineBuffer, 0, 0);
                writer.setPixels(0, offset.line, width + offset.offset, 1, format, lineBuffer, 0, 0);
            }
        }
    }

    void applyIncrementalOffsetToLine(WritableImage img, int line, int o) {
        int width = (int) img.getWidth();
        int height = (int) img.getHeight();
        PixelReader reader = img.getPixelReader();
        PixelWriter writer = img.getPixelWriter();
        WritablePixelFormat<IntBuffer> format
                = WritablePixelFormat.getIntArgbInstance();
        int[] lineBuffer = new int[width];

        if (o > 0) {
            reader.getPixels(0, line, width, 1, format, lineBuffer, 0, 0);
            writer.setPixels(o, line, width - o, 1, format, lineBuffer, 0, 0);
        } else {
            reader.getPixels(-o, line, width + o, 1, format, lineBuffer, 0, 0);
            writer.setPixels(0, line, width + o, 1, format, lineBuffer, 0, 0);
        }
    }

    void adjustOffset(WritableImage img, ArrayList<Offset> offsets, int line, int offset) {
        // look for that line in the list of offsets
        for (Offset o : offsets) {
            // if we find it, adjust it
            if (o.line == line) {
                o.offset += offset;
                //System.out.println("found and adjusted");
                applyIncrementalOffsetToLine(img, line, offset);
                return;
            }
        }
        // didn't find it? Add it.
        Offset o = new Offset(line, offset);
        offsets.add(o);
        //System.out.println("added");
        applyIncrementalOffsetToLine(img, line, offset);
    }

    public static void main(String[] args) {
        launch();
    }

    Image loadImage() {
        return new Image("/test.png");
    }

    public static void saveToFile(Image image, ArrayList<Offset> offsets) {
        String fileName = System.getProperty("user.home") + "\\desktop\\test2.png";
        System.out.println(fileName);
        File outputFile = new File(fileName);
        BufferedImage bImage = SwingFXUtils.fromFXImage(image, null);
        try {
            ImageIO.write(bImage, "png", outputFile);
        } catch (IOException e) {
            System.out.println("Saving image had exception");
        }

        fileName = System.getProperty("user.home") + "\\desktop\\offsets.csv";
        System.out.println(fileName);
        outputFile = new File(fileName);
        try {
            PrintStream ps = new PrintStream(outputFile);
            for (Offset o : offsets) {
                ps.println(o);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    int decode(int pixel) {
        int highsix = (pixel & 0xFC) >> 2;
        int lowsix = (pixel & 0x03) << 4;
        lowsix += ((pixel & 0x300) >> 8) << 2;
        lowsix += (pixel & 0x30000) >> 16;
        return (highsix << 6) + lowsix;
    }

    int encode(int intensity) {
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

}
