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
public class MiniBadge extends StackPane {

    private final int width = 100;
    private final int height = 20;
    private final String[] channelLabels = {"SEI", "BEI1", "BEI2", "AEI"};
    private final Rectangle badge;
    private final Text channel;

    MiniBadge(int channel) {
        this.channel = new Text((channel < 0 || channel > 3) ? "unknown" : channelLabels[channel]);
        this.channel.setFill(Color.GOLD);

        this.badge = new Rectangle(width, height);
        this.badge.setFill(Color.BLUE);
        this.badge.setOpacity(0.6);
        this.badge.setArcWidth(10);
        this.badge.setArcHeight(10);

        super.getChildren().addAll(this.badge, this.channel);
        super.setPadding(new Insets(8, 8, 8, 8));

        super.setMaxSize(width, height);
    }



}
