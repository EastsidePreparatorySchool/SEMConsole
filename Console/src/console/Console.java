/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package console;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import javafx.application.Application;
import javafx.scene.layout.VBox;

/**
 *
 * @author gmein
 */
public class Console extends Application {

    private static SEMThread semThread;

    @Override
    public void start(Stage primaryStage) {

        primaryStage.setTitle("SEM Console");
        //defining the axes
        Button btn = new Button();

        btn.setText("Connect to SEM");
        btn.setOnAction((event) -> {
            (semThread = new SEMThread()).start();
        }
        );

        VBox root = new VBox();
        root.getChildren().addAll(btn);

        Scene scene = new Scene(root, 800, 600);

        primaryStage.setTitle("Connect");
        primaryStage.setScene(scene);

        primaryStage.show();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args)  {
        launch(args);
        semThread.interrupt();
        try {
            semThread.join();
        } catch (InterruptedException ie) {
        }
    }

}
