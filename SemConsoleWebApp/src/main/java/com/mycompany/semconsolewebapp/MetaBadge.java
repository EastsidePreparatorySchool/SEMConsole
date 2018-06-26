/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.semconsolewebapp;

import java.util.ArrayList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

/**
 *
 * @author gmein
 */
public class MetaBadge extends StackPane {

    private final int width = 230;
    private final int height = 110;
    private final String[] channelLabels = {"SEI", "BEI1", "BEI2", "AEI"};
    Rectangle badge;
    Text channel;
    Text kv;
    Text mag;
    Text wd;
    Text ops;
    VBox vbox;

    MetaBadge(int channel, int kv, int mag, int wd, String ops, double dpi) {
        this.channel = new Text("Channel: " + (channel == -1 ? "unknown" : channelLabels[channel]));
        this.channel.setFill(Color.GOLD);
        this.kv = new Text("Accelerating Voltage: " + (kv == -1 ? "unknown" : kv + "kv"));
        this.kv.setFill(Color.GOLD);
        this.mag = new Text("Magnification: Nom: " + (mag == -1 ? "unknown" : mag) + "x");
        this.mag.setFill(Color.GOLD);
        this.wd = new Text("Working Depth: " + (wd == -1 ? "unknown" : wd + "mm"));
        this.wd.setFill(Color.GOLD);

        this.ops = new Text(ops);
        this.ops.setFill(Color.GOLD);

        this.vbox = new VBox();
        this.vbox.getChildren().addAll(this.channel, this.kv, this.mag, this.wd, this.ops);
        this.vbox.setAlignment(Pos.CENTER_LEFT);
        this.vbox.setPadding(new Insets(10, 15, 10, 15));
        this.vbox.setMinSize(width, height);
        this.vbox.setMaxSize(width, height);

        this.badge = new Rectangle(width, height);
        this.badge.setFill(Color.BLUE);
        this.badge.setOpacity(0.6);
        this.badge.setArcWidth(10);
        this.badge.setArcHeight(10);

        Rectangle bigBadge = new Rectangle(width + 20, height + 20);
        bigBadge.setFill(Color.GOLD);
        bigBadge.setOpacity(0.4);
        bigBadge.setArcWidth(10);
        bigBadge.setArcHeight(10);

        StackPane sp = new StackPane();
        sp.getChildren().addAll(this.badge, this.vbox);
        sp.setPadding(new Insets(8, 8, 8, 8));

        sp.setTranslateX(-2);
        sp.setTranslateY(-2);

        ScaleData sd = ScaleData.findScaleData(mag);
        sd.fitScale((int) ((width - 32)), (int) (height - 8));
        Text scale = new Text(sd.findScaleText());
        scale.setFill(Color.BLACK);
        scale.setTranslateX(3);
        scale.setTranslateY(0);
        
        if (Double.toString(sd.scale).endsWith("5")) {
            int i = 1;
        }
        double amag = sd.pixelX; // length of scale bar in pixels
        amag /= dpi; // gives us length of scale bar in inches
        amag *= 25.4; // gives us length of scale bar in mm
        amag /= sd.scale; // gives us apparent magnification
        this.mag.setText(this.mag.getText()+", App: " +  (Math.round(amag * 1000)/1000)+"x");


        Line l = new Line();
        l.setStartX(0);
        l.setStartY(0);
        l.setEndX(sd.pixelX);
        l.setEndY(0);
        l.setStroke(Color.BLACK);
        l.setTranslateX(42);
        l.setTranslateY(9);

        Line l2 = new Line();
        l2.setStartX(0);
        l2.setStartY(0);
        l2.setEndX(0);
        l2.setEndY(6);
        l2.setStroke(Color.BLACK);
        l2.setTranslateX(42);
        l2.setTranslateY(6);

        Line l3 = new Line();
        l3.setStartX(0);
        l3.setStartY(0);
        l3.setEndX(0);
        l3.setEndY(6);
        l3.setStroke(Color.BLACK);
        l3.setTranslateX(42 + sd.pixelX);
        l3.setTranslateY(6);

        Line l4 = new Line();
        l4.setStartX(0);
        l4.setStartY(0);
        l4.setEndX(0);
        l4.setEndY(sd.pixelY);
        l4.setStroke(Color.BLACK);
        l4.setTranslateX(8);
        l4.setTranslateY(20);

        Line l5 = new Line();
        l5.setStartX(0);
        l5.setStartY(0);
        l5.setEndX(6);
        l5.setEndY(0);
        l5.setStroke(Color.BLACK);
        l5.setTranslateX(5);
        l5.setTranslateY(20);

        Line l6 = new Line();
        l6.setStartX(0);
        l6.setStartY(0);
        l6.setEndX(6);
        l6.setEndY(0);
        l6.setStroke(Color.BLACK);
        l6.setTranslateX(5);
        l6.setTranslateY(20 + sd.pixelY);

        this.setAlignment(Pos.TOP_LEFT);
        super.getChildren().addAll(bigBadge, sp, l, l2, l3, l4, l5, l6, scale);
        super.setMinSize(width + 40, height + 40);
        super.setMaxSize(width + 40, height + 40);
    }

    MetaBadge(SEMImage si, int channel, double dpi) {
        this(channel, si.kv, si.magnification, si.wd, si.operators, dpi);
    }

    private static class ScaleData {

        int mag;
        double pixelX;
        double pixelY;
        double scale;

        ScaleData(int m, double x, double y, double s) {
            mag = m;
            pixelX = x;
            pixelY = y;
            scale = s;
        }

        ScaleData(ScaleData sd) {
            mag = sd.mag;
            pixelX = sd.pixelX;
            pixelY = sd.pixelY;
            scale = sd.scale;
        }

        ScaleData(ScaleData sd, int mag) {
            this.mag = mag;
            pixelX = sd.pixelX * mag / sd.mag;
            pixelY = sd.pixelY * mag / sd.mag;
            scale = sd.scale;
        }

        void fitScale(int maxx, int maxy) {
            double factor;
            while (this.pixelY > maxy || this.pixelX > maxx) {
                if (Double.toString(this.scale).endsWith("5")) {
                    factor = 2.5;
                } else {
                    factor = 2;
                }

                this.scale /= factor;
                this.pixelX /= factor;
                this.pixelY /= factor;
            }
        }

        private static final ScaleData[] data = {
            new ScaleData(100, 479, 516, 0.1),
            new ScaleData(110, 500, 540, 0.1),
            new ScaleData(900, 445, 317, 0.01)
        };

        static ScaleData findScaleData(int mag) {
            int i;
            for (i = 0; i < data.length; i++) {
                if (data[i].mag >= mag) {
                    break;
                }
            }

            if (i < data.length) {
                return new ScaleData(data[i], mag);
            }

            return new ScaleData(0, 10, 10, 0);
        }

        String findScaleText() {
            for (ScaleText t : texts) {
                if (t.scale == this.scale) {
                    return t.scaleText;
                }
            }

            System.out.println("ST: " + this.scale);
            return "unknown";
        }

        private static class ScaleText {

            double scale;
            String scaleText;

            ScaleText(double s, String t) {
                scale = s;
                scaleText = t;
            }
        }

        private static final ScaleText[] texts = {
            new ScaleText(0, "inv"),
            new ScaleText(2, "2mm"),
            new ScaleText(1, "1mm"),
            new ScaleText(0.5, "0.5mm"),
            new ScaleText(0.2, "0.2mm"),
            new ScaleText(0.1, "0.1mm"),
            new ScaleText(0.05, "50\u00b5m"),
            new ScaleText(0.02, "20\u00b5m"),
            new ScaleText(0.01, "10\u00b5m"),
            new ScaleText(0.005, "5\u00b5m"),
            new ScaleText(0.002, "2\u00b5m"),
            new ScaleText(0.001, "1\u00b5m")
        };
    }

}
