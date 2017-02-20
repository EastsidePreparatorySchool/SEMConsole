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

    public enum Phase {
        WAITING_TO_CONNECT,
        WAITING_FOR_FRAME,
        WAITING_FOR_BYTES_OR_EFRAME,
        FINISHED,
    }

    private Phase phase = Phase.WAITING_TO_CONNECT;
    private LinkedTransferQueue<SEMImage> ltq;
    private Runnable update;

    SEMThread(LinkedTransferQueue<SEMImage> q, Runnable r) {
        this.ltq = q;
        this.update = r;
    }

    SEMPort semport = new SEMPort();

    public void run() {
        try {
            // TODO: clean this up, make it a proper message loop, keep it alive after frame complete
            this.phase = Phase.WAITING_TO_CONNECT;
            semport.initialize();
            Console.println("SEM Port Initialized");

            // main loop waiting for FRAME or BYTES or EFRAME messages
            this.phase = Phase.WAITING_FOR_FRAME;
            while (!this.isInterrupted() && this.phase != Phase.FINISHED) {
                this.phase = semport.processMessage(this.ltq, this.update, this.phase);
                Thread.yield();
            }

        } catch (InterruptedException ie) {
        } catch (Exception e) {
            Console.println(e.toString());
        } finally {
            if (semport != null) {
                semport.shutdown();
            }
        }
    }

}
