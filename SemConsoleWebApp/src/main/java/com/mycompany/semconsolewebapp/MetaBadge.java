/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.semconsolewebapp;

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

    private final int width = 200;
    private final int height = 110;
    private final String[] channelLabels = {"SEI", "BEI1", "BEI2", "AEI"};
    Rectangle badge;
    Text channel;
    Text kv;
    Text mag;
    Text wd;
    Text ops;
    VBox vbox;

    MetaBadge(int channel, int kv, int mag, int wd, String[] ops, double compression) {
        this.channel = new Text("Channel: " + (channel == -1 ? "unknown" : channelLabels[channel]));
        this.channel.setFill(Color.GOLD);
        this.kv = new Text("Accelerating Voltage: " + (kv == -1 ? "unknown" : kv + "kv"));
        this.kv.setFill(Color.GOLD);
        this.mag = new Text("Magnification: " + (mag == -1 ? "unknown" : mag + "x"));
        this.mag.setFill(Color.GOLD);
        this.wd = new Text("Working Depth: " + (wd == -1 ? "unknown" : wd + "mm"));
        this.wd.setFill(Color.GOLD);
        String opsText = "Operators:";
        for (String op : ops) {
            opsText += " " + op;
        }
        this.ops = new Text(opsText);
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
        bigBadge.setFill(Color.LIGHTBLUE);
        bigBadge.setOpacity(0.4);
        bigBadge.setArcWidth(10);
        bigBadge.setArcHeight(10);

        StackPane sp = new StackPane();
        sp.getChildren().addAll(this.badge, this.vbox);
        sp.setPadding(new Insets(10, 10, 10, 10));

        sp.setTranslateX(-2);
        sp.setTranslateY(-2);

        ScaleData sd = ScaleData.findScaleData(mag);
        sd.pixelX /= compression;
        sd.pixelY /= compression;
        sd.fitScale((int) ((width - 32)), (int) (height - 8));
        Text scale = new Text(sd.findScaleText());
        scale.setFill(Color.GOLD);
        scale.setTranslateX(3);
        scale.setTranslateY(0);

        Line l = new Line();
        l.setStartX(0);
        l.setStartY(0);
        l.setEndX(sd.pixelX);
        l.setEndY(0);
        l.setStroke(Color.GOLD);
        l.setTranslateX(42);
        l.setTranslateY(9);

        Line l2 = new Line();
        l2.setStartX(0);
        l2.setStartY(0);
        l2.setEndX(0);
        l2.setEndY(6);
        l2.setStroke(Color.GOLD);
        l2.setTranslateX(42);
        l2.setTranslateY(6);

        Line l3 = new Line();
        l3.setStartX(0);
        l3.setStartY(0);
        l3.setEndX(0);
        l3.setEndY(6);
        l3.setStroke(Color.GOLD);
        l3.setTranslateX(42 + sd.pixelX);
        l3.setTranslateY(6);

        Line l4 = new Line();
        l4.setStartX(0);
        l4.setStartY(0);
        l4.setEndX(0);
        l4.setEndY(sd.pixelY);
        l4.setStroke(Color.GOLD);
        l4.setTranslateX(8);
        l4.setTranslateY(20);

        Line l5 = new Line();
        l5.setStartX(0);
        l5.setStartY(0);
        l5.setEndX(6);
        l5.setEndY(0);
        l5.setStroke(Color.GOLD);
        l5.setTranslateX(5);
        l5.setTranslateY(20);

        Line l6 = new Line();
        l6.setStartX(0);
        l6.setStartY(0);
        l6.setEndX(6);
        l6.setEndY(0);
        l6.setStroke(Color.GOLD);
        l6.setTranslateX(5);
        l6.setTranslateY(20 + sd.pixelY);

        this.setAlignment(Pos.TOP_LEFT);
        this.getChildren().addAll(bigBadge, sp, l, l2, l3, l4, l5, l6, scale);
        this.setMinSize(width + 40, height + 40);
        this.setMaxSize(width + 40, height + 40);
    }

    MetaBadge(SEMImage si, int channel, String[] ops, double compression) {
        this(channel, si.kv, si.magnification, si.wd, ops, compression);
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

        void fitScale(int maxx, int maxy) {
            double factor;
            while (this.pixelY > maxy || this.pixelX > maxx) {
                factor = (Double.toString(this.scale).endsWith("5")) ? 5 : 2;

                this.scale /= factor;
                this.pixelX /= factor;
                this.pixelY /= factor;
            }
        }

        private static final ScaleData[] data = {
            new ScaleData(100, 479, 516, 0.1),
            new ScaleData(110, 500, 540, 0.1)
        };

        static ScaleData findScaleData(int mag) {
            for (ScaleData sd : data) {
                if (sd.mag == mag) {
                    return new ScaleData(sd);
                }
            }
            return new ScaleData(0, 10, 10, 0);
        }

        String findScaleText() {
            for (ScaleText t : texts) {
                if (t.scale == this.scale) {
                    return t.scaleText;
                }
            }

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
            new ScaleText(1, "1 mm"),
            new ScaleText(0.5, "0.5 mm"),
            new ScaleText(0.1, "0.1 mm"),
            new ScaleText(0.05, "50 \u00b5m"),
            new ScaleText(0.01, "10 \u00b5m"),
            new ScaleText(000.5, "5 \u00b5m"),
            new ScaleText(000.1, "1 \u00b5m")
        };
    }

}