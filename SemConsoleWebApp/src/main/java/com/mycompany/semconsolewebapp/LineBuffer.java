/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.semconsolewebapp;

import java.util.ArrayList;

/**
 *
 * @author gmein
 */
public class LineBuffer {

    int width;
    int height;
    public int[][] data;

    static ArrayList<LineBuffer> buffers = new ArrayList<>();

    LineBuffer(int width, int height, int channels) {
        this.width = width;
        this.height = height;
        this.data = new int[height][channels* width + 1];
    }

    static LineBuffer grabLineBuffer(int width, int height, int channels) {
        LineBuffer lb;
        if (height > 300) {
            return new LineBuffer(width, height, channels);
        }

        synchronized (buffers) {
            if (buffers.isEmpty()) {
                System.out.println("New Line buffer");
                return new LineBuffer(width, height, channels);
            }
            lb = buffers.remove(0);
        }
        return lb;
    }

    static void returnLineBuffer(LineBuffer lb) {
        if (lb.height <= 300) {
            synchronized (buffers) {
                buffers.add(0, lb);
            }
        }
    }

}
