/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.semconsolewebapp;

import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;

/**
 *
 * @author gmein
 */
//
// concept:
// A SEMImageView is an AnchorPane with a GridPane for the images, a VBox on the right side for data view (osc etc), 
// and a metabadge in the bottom right corner. The GridPane holds up to 4 StackPanes with an ImageView and a 
// channel-only or full metabadge
//
public class SEMImageView extends AnchorPane {

    SEMImage si;
    ImageView[] aiv;
    StackPane[] asp;
    MetaBadge mb;

    SEMImageView(SEMImage si) {
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
        }
        
        // add 4 StackPaness to the grid pane
        GridPane gp = new GridPane();
        gp.addRow(0, asp[0], asp[1]);
        gp.addRow(1, asp[2], asp[3]);
        gp.setHgap(4.0);
        gp.setVgap(4.0);
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
