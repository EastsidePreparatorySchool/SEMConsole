/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package console;

import java.util.concurrent.LinkedTransferQueue;

/**
 *
 * @author gmein
 */
public class SEMThread extends Thread {

    LinkedTransferQueue<SEMImage> ltq;
    Runnable update;

    SEMThread(LinkedTransferQueue<SEMImage> q, Runnable r) {
        this.ltq = q;
        this.update = r;
    }

    SEMPort semport = new SEMPort();

    public void run() {
        try {
            // TODO: clean this up, make it a proper message loop, keep it alive after frame complete
            semport.initialize();
            //semport.test();
            Console.println("SEM Port Initialized");
            while (true) {
                String s = semport.peekMessage(this.ltq, this.update);
                if (s != null) {
                    if (s.equals("Finished")) {
                        break;
                    }
                }
                if (this.isInterrupted()) {
                    break;
                }
            }
        } catch (Exception e) {
            Console.println(e.toString());
        } finally {
            if (semport != null) {
                semport.shutdown();
            }
        }
    }

}
