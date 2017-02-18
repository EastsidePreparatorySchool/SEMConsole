/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package console;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedTransferQueue;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javax.imageio.ImageIO;

/**
 *
 * @author gmein
 */
public class Console extends Application {

    private static SEMThread semThread;
    private Pane root;
    private HBox top;
    private ImageView[] aViews;
    private LinkedTransferQueue<SEMImage> ltq;
    private ArrayList<SEMImage> currentImages;
    private Button btn;
    private Text txt;
    private Scene scene;
    private Stage stage;
    private BorderPane bp;

    static private ConsolePane cp;
    static private boolean printOff = false;

    @Override
    public void start(Stage primaryStage) {

        // main transfer mechanism with other thread
        this.ltq = new LinkedTransferQueue<>();
        this.stage = primaryStage;

        primaryStage.setTitle("SEM Console");
        this.bp = new BorderPane();
        this.root = this.bp;

        // top line - controls
        top = new HBox();
        //top.setMinHeight(30);

        // button for connection
        this.btn = new Button("Connect and scan");
        btn.setOnAction((event) -> startSEMThread());

        Button btn2 = new Button("Full screen");
        btn2.setOnAction((ActionEvent event) -> {

            primaryStage.setFullScreenExitHint("Press any key to exit full screen mode");
            primaryStage.setFullScreenExitKeyCombination(null);
            primaryStage.setFullScreen(true);

        }
        );
        Button btn3 = new Button("Save as ...");
        btn3.setOnAction((event) -> saveFile());

        txt = new Text("Not connected");
        HBox h = new HBox();

        h.getChildren().add(txt);
        h.setPadding(new Insets(6, 12, 6, 12));

        top.setPadding(new Insets(15, 12, 15, 12));
        top.getChildren().addAll(btn, h, btn2, btn3);
        bp.setTop(top);
        cp = new ConsolePane();

        cp.setPrefWidth(740);
        BorderPane.setAlignment(cp, Pos.CENTER);

        BorderPane.setMargin(cp, new Insets(10, 8, 10, 8));
        bp.setBottom(cp);

        // img
        this.aViews = new ImageView[4];
        for (int i = 0; i < 4; i++) {
            this.aViews[i] = new ImageView();
            this.aViews[i].setSmooth(true);
            this.aViews[i].setCache(true);
            this.aViews[i].setFitHeight(360);
            this.aViews[i].setFitWidth(480);
        }

        HBox hb = new HBox();
        VBox vbLeft = new VBox();
        vbLeft.setPadding(new Insets(10, 8, 10, 8));
        VBox vbRight = new VBox();
        vbRight.setPadding(new Insets(10, 8, 10, 8));
        vbLeft.getChildren().addAll(this.aViews[0], this.aViews[1]);
        vbRight.getChildren().addAll(this.aViews[2], this.aViews[3]);
        hb.getChildren().addAll(vbLeft, vbRight);

        bp.setCenter(hb);
        BorderPane.setAlignment(hb, Pos.CENTER);
        BorderPane.setMargin(hb, new Insets(10, 8, 10, 8));

        this.scene = new Scene(bp);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void updateDisplay() {
        if (this.ltq.isEmpty()) {
            return;
        }

        this.currentImages = new ArrayList<>();
        ltq.drainTo(this.currentImages);
        Console.println("[Console: Received " + this.currentImages.size() + " image"
                + (this.currentImages.size() == 1 ? "" : "s") + "]");

        // get last set of images
        SEMImage si = this.currentImages.get(this.currentImages.size() - 1);

        // set absent channel images to empty
        List cc = new ArrayList();
        Collections.addAll(cc, si.capturedChannels);
        for (int i = 0; i < 4; i++) {
            if (!cc.contains(i)) {
                this.aViews[i].setImage(null);
            }
        }

        // put the images in place
        for (int i = 0; i < si.channels; i++) {
            this.aViews[si.capturedChannels[i]].setImage(si.images[i]);

        }

    }

    private void startSEMThread() {
        // remove currently displayed image
        // stop any existing SEM thread
        if (semThread != null) {
            semThread.interrupt();
            try {
                semThread.join();
            } catch (InterruptedException ie) {
            }
        }

        // and create a new one
        semThread = new SEMThread(this.ltq, () -> {
            updateDisplay();
        });
        semThread.start();

    }

    String channelText(int width, int height, int channel) {
        String result = "" + width + "x" + height + " channel: ";
        switch (channel) {
            case 0:
                result += "A0 (secondary electron image)";
                break;
            case 1:
                result += "A1 (absorbed current image)";
                break;
            case 2:
                result += "A2 (backscatter image 1)";
                break;
            case 3:
                result += "A3 (backscatter image 2)";
                break;
        }

        return result;
    }

    public void saveFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Image");
        System.out.println(this.aViews[0].getId());
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try {
                if (!file.getName().toLowerCase().endsWith("png")) {
                    // TODO: create new file object with added extension
                }
                ImageIO.write(SwingFXUtils.fromFXImage(this.aViews[0].getImage(), null), "png", file);
                Console.println("Image written to " + file.getName());
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }
    }

    // console util functions
    public static void println(String s) {
        if (!printOff) {
            cp.println(s);
        }
    }

    public static void print(String s) {
        if (!printOff) {
            cp.print(s);
        }
    }

    public static void println() {
        if (!printOff) {
            cp.println();
        }
    }

    public static void printOff() {
        printOff = true;
    }

    public static void printOn() {
        printOff = false;
    }

    // main fx launcher
    public static void main(String[] args) {
        try {
            launch(args);
        } finally {
            if (semThread != null) {
                semThread.interrupt();
                try {
                    semThread.join();
                } catch (InterruptedException ie) {
                }
            }
        }
    }
}
