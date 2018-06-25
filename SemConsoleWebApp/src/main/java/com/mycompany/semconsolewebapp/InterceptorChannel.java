/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.semconsolewebapp;

import dk.thibaut.serial.SerialChannel;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 *
 * @author gmein
 */
public class InterceptorChannel implements SerialChannel {

    SerialChannel sc;

    InterceptorChannel(SerialChannel s) {
        sc = s;
    }

    @Override
    public void flush(boolean bln, boolean bln1) throws IOException {
        sc.flush(bln, bln1);
    }

    @Override
    public int read(ByteBuffer bb) throws IOException {
        return sc.read(bb);
    }

    @Override
    public boolean isOpen() {
        return sc.isOpen();
    }

    @Override
    public void close() throws IOException {
        sc.close();
    }

    @Override
    public int write(ByteBuffer bb) throws IOException {
        return sc.write(bb);
    }

}
