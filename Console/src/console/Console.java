/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package console;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 *
 * @author gmein
 */
public class Console extends Application {

    SEMPort semport = new SEMPort();

    @Override
    public void start(Stage primaryStage) {
        Button btn = new Button();
        btn.setText("Connect to SEM");
        btn.setOnAction((event) -> {
            try {
                semport.initialize();
                //semport.test();
                System.out.println("SEM Port Initialized");
                for (int i = 0; i < 10000; i++) {
                    String s = semport.peekMessage();
                    if (s != null) {
                        if (s.equals("Finished")) {
                            break;
                        }
                    }
                }
                semport.shutdown();
            } catch (Exception e) {
                System.out.println(e.toString());
            }
        });

        StackPane root = new StackPane();
        root.getChildren().add(btn);

        Scene scene = new Scene(root, 300, 250);

        primaryStage.setTitle("Hello World!");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

}
