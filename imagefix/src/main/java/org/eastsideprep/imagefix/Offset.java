/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.eastsideprep.imagefix;

/**
 *
 * @author gmein
 */
public class Offset {

    int line;
    int offset;

    Offset(int line, int offset) {
        this.line = line;
        this.offset = offset;
    }
    
    @Override
    public String toString() {
        return ""+this.line+","+this.offset;
    }
}
