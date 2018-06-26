/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.semconsolewebapp;

import javafx.geometry.Orientation;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Slider;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 *
 * @author gmein
 */
//
// concept:
// A SEMImageView is an AnchorPane with a GridPane for the images, a sliders on the right side for contrast and brightness, 
// and a metabadge in the bottom right corner. The GridPane holds up to 4 StackPanes with an ImageView and a 
// channel-only or full metabadge
//
public class SEMImageView extends AnchorPane {

    SEMImage si;
    ImageView[] aiv;
    StackPane[] asp;
    MetaBadge mb;

    SEMImageView(SEMImage si, boolean isPhoto) {
        this.si = si;

        for (int i = 0; i < si.channels; i++) {
            aiv[i] = new ImageView();
            this.aiv[i] = new ImageView();
            this.aiv[i].setSmooth(false);
            this.aiv[i].setCache(true);

            setSizeNormal(this.aiv[i], i);

            // make the 3 secondary views appear in a slightly different color effect
            if (i != 0) {
                ColorAdjust colorAdjust = new ColorAdjust();
                colorAdjust.setHue(new double[]{0, -1, -0.2, 0.3}[i]);
//                colorAdjust.setBrightness(0.1);
//                colorAdjust.setSaturation(0.2);
//                colorAdjust.setContrast(0.1);
                this.aiv[i].setEffect(colorAdjust);
            }

            // add to their own StackPane so we can slap a MetaBadge on top
            this.asp[i] = new StackPane();
            asp[i].getChildren().add(this.aiv[i]);

            MiniBadge mb = new MiniBadge(this.si.capturedChannels[i]);
            asp[i].getChildren().add(mb);

        }

        // add 4 StackPanes to the grid pane, anchor gridpane at top left
        GridPane gp = new GridPane();
        gp.addRow(0, asp[0], asp[1]);
        gp.addRow(1, asp[2], asp[3]);
        gp.setHgap(4.0);
        gp.setVgap(4.0);

        AnchorPane.setTopAnchor(gp, 4.0);
        AnchorPane.setBottomAnchor(gp, 4.0);
        super.getChildren().add(gp);

        // weight, contrast and brightness controls
        Slider weight = new Slider();
        weight.valueProperty().addListener((f) -> {
            Console.dNewWeight = weight.getValue();
        });
        weight.setPrefHeight(80);
        weight.setMin(0.05);
        weight.setMax(1.0);
        weight.setValue(1.0);
        weight.setShowTickMarks(true);
        weight.setShowTickLabels(true);
        weight.setMajorTickUnit(0.1);
        weight.setOrientation(Orientation.VERTICAL);

        Slider contrast = new Slider();
        weight.valueProperty().addListener((f) -> {
            si.dContrast = contrast.getValue();
            if (!isPhoto) {
                si.dContrast = contrast.getValue();
            }
        });
        contrast.setPrefHeight(80);
        contrast.setMin(0.0);
        contrast.setMax(1.0);
        contrast.setValue(isPhoto ? si.dContrast : Console.dContrast);
        contrast.setShowTickMarks(true);
        contrast.setShowTickLabels(false);
        contrast.setMajorTickUnit(0.1);
        contrast.setOrientation(Orientation.VERTICAL);

        Slider brightness = new Slider();
        weight.valueProperty().addListener((f) -> {
            Console.dNewWeight = weight.getValue();

        });
        brightness.setPrefHeight(80);
        brightness.setMin(0.05);
        brightness.setMax(1.0);
        brightness.setValue(isPhoto ? si.dBrightness : Console.dBrightness);
        brightness.setShowTickMarks(true);
        brightness.setShowTickLabels(true);
        brightness.setMajorTickUnit(0.1);
        brightness.setOrientation(Orientation.VERTICAL);

        // put the controls together just so
        CheckBox auto = new CheckBox("Auto");
        auto.setSelected(true);
        auto.setOnAction((e) -> {
            Console.autoContrast = auto.isSelected();
        });
        HBox cb = new HBox();
        cb.getChildren().addAll(contrast, brightness);
        VBox cba = new VBox();
        cba.getChildren().addAll(cb, auto);
        HBox controls = new HBox();
        controls.getChildren().addAll(weight, cba);
    }

    private void setSizeNormal(ImageView iv, int channel) {
        switch (this.si.channels) {
            case 2:
                if (channel < 2) {
                    iv.fitHeightProperty().bind(this.widthProperty().subtract(300).multiply(3).divide(8));
                    iv.fitWidthProperty().bind(this.widthProperty().subtract(300).divide(2));
                } else {
                    iv.fitHeightProperty().unbind();
                    iv.fitWidthProperty().unbind();
                    iv.setFitWidth(0);
                    iv.setFitHeight(0);
                }
                break;
            case 1:
                if (channel < 1) {
                    iv.fitHeightProperty().bind(this.heightProperty().subtract(260));
                    iv.fitWidthProperty().bind(this.heightProperty().subtract(260).multiply(4).divide(3));
                } else {
                    iv.fitHeightProperty().unbind();
                    iv.fitWidthProperty().unbind();
                    iv.setFitWidth(0);
                    iv.setFitHeight(0);
                }
                break;
            default:
                iv.fitHeightProperty().bind(this.heightProperty().subtract(260).divide(2));
                iv.fitWidthProperty().bind(this.heightProperty().subtract(300).divide(2).multiply(4).divide(3));
                break;
        }
    }

}
