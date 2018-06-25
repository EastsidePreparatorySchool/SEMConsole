/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.semconsolewebapp;

import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;

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
    MetaBadge mb;
    
    SEMImageView(SEMImage si) {
        this.si = si;
    }
    
    
}
