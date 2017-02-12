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
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;

/**
 *
 * @author gmein
 */
public class Console extends Application {

    private static SEMThread semThread;
    private VBox root;
    private ImageView view;
    private LinkedTransferQueue<SEMImage> ltq;
    private ArrayList<SEMImage> currentImages;
    private int currentImage = 0;
    private int currentChannel = 0;

    @Override
    public void start(Stage primaryStage) {

        // main transfer mechanism with other thread
        this.ltq = new LinkedTransferQueue<SEMImage>();

        primaryStage.setTitle("SEM Console");
        this.root = new VBox();

        // button for connection
        Button btn = new Button();
        btn.setText("Connect to SEM");
        btn.setOnAction((event) -> {
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
            (semThread = new SEMThread(
                    this.ltq,
                    () -> {
                        updateDisplay();
                    })).start();

        }
        );
        root.getChildren().addAll(btn);
        //AnchorPane.setTopAnchor(btn, 0.0);

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("Connect");
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
        this.root.setPrefSize(width, height + 50.0);

        // if old view is here, remove from display
        if (this.view != null) {
            this.root.getChildren().remove(this.view);
        }

        this.view = new ImageView(img);
        this.root.getChildren().add(this.view);
        //AnchorPane.setTopAnchor(this.view, 50.0);
        //AnchorPane.setRightAnchor(this.view, (double) width);

        this.view.setOnMouseClicked((e) -> {
            this.displayNextImage();
        });

    }

    private void displayNextImage() {
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
    
    
  
}
