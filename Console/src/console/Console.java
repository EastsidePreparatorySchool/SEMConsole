/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package console;

import java.util.ArrayList;
import java.util.concurrent.LinkedTransferQueue;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

/**
 *
 * @author gmein
 */
public class Console extends Application {

    private static SEMThread semThread;
    private VBox root;
    private HBox top;
    private ImageView view;
    private LinkedTransferQueue<SEMImage> ltq;
    private ArrayList<SEMImage> currentImages;
    private int currentImage = 0;
    private int currentChannel = 0;
    private Button btn;
    private Text txt;

    @Override
    public void start(Stage primaryStage) {

        // main transfer mechanism with other thread
        this.ltq = new LinkedTransferQueue<SEMImage>();

        primaryStage.setTitle("SEM Console");
        this.root = new VBox();
        this.root.setPrefSize(960, 540);

        // top line - controls
        top = new HBox();

        // button for connection
        this.btn = new Button();
        btn.setText("Connect and scan");
        btn.setOnAction((event) -> startSEMThread());

        txt = new Text("Not connected");
        HBox h = new HBox();
        h.getChildren().add(txt);
        h.setPadding(new Insets(6, 12, 6, 12));

        top.setPadding(new Insets(15, 12, 15, 12));
        top.getChildren().addAll(btn, h);
        root.getChildren().addAll(top);

        Scene scene = new Scene(root, 960 + 10, 540 + 80);
        primaryStage.setScene(scene);

        primaryStage.show();
    }

    private void displayImage(WritableImage img) {
        if (img == null) {
            if (this.view != null) {
                this.root.getChildren().remove(this.view);
            }
            return;
        }

        int width = (int) img.getWidth();
        int height = (int) img.getHeight();

        // if old view is here, remove from display
        if (this.view != null) {
            this.root.getChildren().remove(this.view);
        }

        this.view = new ImageView(img);
        this.root.getChildren().add(this.view);
        this.view.setFitWidth(960);
        this.view.setPreserveRatio(true);
        this.view.setSmooth(true);
        this.view.setCache(true);
        this.view.setOnMouseClicked((e) -> {
            this.displayNextImage();
        });

    }

    private void displayNextImage() {
        // todo: this will crash if a channel is not occupied with an image

        if (this.currentImages == null || this.currentImages.size() == 0) {
            displayImage(null);
            return;
        }

        if (this.currentImage >= this.currentImages.size()) {
            this.currentImage = 0;
            this.currentChannel = 0;
        }

        SEMImage si = this.currentImages.get(this.currentImage);
        if (this.currentChannel >= si.channels) {
            this.currentChannel = 0;
            this.currentImage++;
        }

        if (this.currentImage >= this.currentImages.size()) {
            this.currentImage = 0;
            this.currentChannel = 0;
        }

        displayImage(this.currentImages.get(this.currentImage).images[this.currentChannel]);
        txt.setText(channelText(this.currentChannel));
        this.currentChannel++;
    }

    private void updateDisplay() {
        if (!this.ltq.isEmpty()) {
            this.currentImages = new ArrayList<>();
            ltq.drainTo(this.currentImages);
            System.out.println("Console: Received " + this.currentImages.size() + " images.");
            this.currentImage = 0;
            this.currentChannel = 0;

            displayNextImage();
            this.btn.setText("Scan again");
            this.btn.arm();
        }
    }

    public static void main(String[] args) {
        launch(args);
        if (semThread != null) {
            semThread.interrupt();
            try {
                semThread.join();
            } catch (InterruptedException ie) {
            }
        }
    }

    private void startSEMThread() {
        // remove currently displayed image
        txt.setText("Scanning ...");
        btn.disarm();
        this.currentImages = null;
        displayNextImage();

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

    String channelText(int channel) {
        String result = "";
        switch (channel) {
            case 0:
                result = "A0 (Secondary electron image)";
                break;
            case 1:
                result = "A1 (Absorbed current image)";
                break;
            case 2:
                result = "A2 (Backscatter image 1)";
                break;
            case 3:
                result = "A3 (Backscatter image 2)";
                break;
        }
        return result;
    }
}
