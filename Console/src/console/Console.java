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
import javafx.scene.layout.AnchorPane;

/**
 *
 * @author gmein
 */
public class Console extends Application {

    private static SEMThread semThread;
    private AnchorPane root;
    private ImageView view;
    private LinkedTransferQueue<SEMImage> ltq;

    @Override
    public void start(Stage primaryStage) {

        // main transfer mechanism with other thread
        this.ltq = new LinkedTransferQueue<SEMImage>();

        primaryStage.setTitle("SEM Console");
        this.root = new AnchorPane();

        // button for connection
        Button btn = new Button();
        btn.setText("Connect to SEM");
        btn.setOnAction((event) -> {
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
        AnchorPane.setTopAnchor(btn, 0.0);

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("Connect");
        primaryStage.setScene(scene);

        primaryStage.show();
    }

    private void displayImage(WritableImage img) {
        int width = (int) img.getWidth();
        int height = (int) img.getHeight();
        this.root.setPrefSize(width, height + 50.0);

        // if old view is here, remove from display
        if (this.view != null) {
            this.root.getChildren().remove(this.view);
        }

        this.view = new ImageView(img);
        this.root.getChildren().add(this.view);
        AnchorPane.setTopAnchor(this.view, 50.0);
        AnchorPane.setRightAnchor(this.view, (double) width);
    }

    private void updateDisplay() {
        if (!this.ltq.isEmpty()) {
            ArrayList<SEMImage> list = new ArrayList<>();
            ltq.drainTo(list);
            System.out.println("Console: Received " + list.size() + " images.");
            displayImage(list.get(0).images[0]);
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
