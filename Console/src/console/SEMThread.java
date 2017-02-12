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
        } catch (Exception e) {
            System.out.println(e.toString());
        } finally {
            if (semport != null) {
                semport.shutdown();
            }
        }
    }

}
