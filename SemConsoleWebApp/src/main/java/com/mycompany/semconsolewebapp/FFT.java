/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.semconsolewebapp;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

/**
 *
 * @author gmein
 */
public class FFT {

    public static double[] forward(double[] data) {
        FastFourierTransformer f = new FastFourierTransformer(DftNormalization.STANDARD);
        Complex[] fftC = f.transform(data, TransformType.FORWARD);

        for (int i = 0; i < data.length; i++) {

            data[i] = fftC[i].abs();

            if (Double.isNaN(data[i])) {
                System.out.println("C " + fftC[i].getReal() + " + i" + fftC[i].getImaginary());
                System.out.println("D " + data[i]);
                break;
            }
        }

        return data;
    }

    public static int nextPower2(int length) {
        if (length == 0) {
            return 0;
        } else {
            return 1 << (int) Math.ceil(Math.log(length) / Math.log(2));
        }
    }

}
