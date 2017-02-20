/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package console;

import java.util.concurrent.LinkedTransferQueue;
import javafx.application.Platform;

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
        ABORTED
    }

    private Phase phase = Phase.WAITING_TO_CONNECT;
    private LinkedTransferQueue<SEMImage> ltq;
    private Runnable update;
    private Runnable restart;

    SEMThread(LinkedTransferQueue<SEMImage> q, Runnable update, Runnable restart) {
        this.ltq = q;
        this.update = update;
        this.restart = restart;
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
            while (!this.isInterrupted() && this.phase != Phase.FINISHED && this.phase != Phase.ABORTED) {
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
        if (this.phase == Phase.ABORTED) {
            Platform.runLater(this.restart);
        }
    }

}
