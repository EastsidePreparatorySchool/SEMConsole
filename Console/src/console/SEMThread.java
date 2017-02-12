/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package console;

import javafx.scene.chart.XYChart;

/**
 *
 * @author gmein
 */
public class SEMThread extends Thread {

    SEMThread() {
    }

    SEMPort semport = new SEMPort();

    public void run() {
        try {
            // TODO: clean this up, make it a proper message loop, keep it alive after frame complete
            semport.initialize();
            //semport.test();
            System.out.println("SEM Port Initialized");
            while (true) {
                String s = semport.peekMessage();
                if (s != null) {
                    if (s.equals("Finished")) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e.toString());
        } finally {
            if (semport != null) {
                semport.shutdown();
            }
        }
    }

}
