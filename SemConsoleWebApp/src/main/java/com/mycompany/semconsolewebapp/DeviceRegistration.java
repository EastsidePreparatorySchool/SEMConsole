/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.semconsolewebapp;

/**
 *
 * @author gmein
 */
 public class DeviceRegistration {

        String deviceName;
        String operatorName;
        String operatorInitials;
        
        DeviceRegistration(String dn, String on, String oi) {
            this.deviceName = dn;
            this.operatorInitials = oi;
            this.operatorName = on;
        }
    }