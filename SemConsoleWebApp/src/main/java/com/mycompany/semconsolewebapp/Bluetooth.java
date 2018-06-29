/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.semconsolewebapp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;

public class Bluetooth {

   

    public static void getDevices(ArrayList<String> operators, List<DeviceRegistration> registered) {
        /* Create Vector variable */
        final Object inquiryCompletedEvent = new Object();

        try {
            /* Create an object of DiscoveryListener */
            DiscoveryListener listener = new DiscoveryListener() {

                @Override
                public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {
                    try {
                        /* Get devices paired with system or in range(Without Pair) */
                        String devName = btDevice.getFriendlyName(false) + " (" + btDevice.getBluetoothAddress() + ")";
                        System.out.println("Device discovered: " + devName);
                        for (DeviceRegistration dr : registered) {
                            if (dr.deviceName.equals(devName)) {
                                synchronized (operators) {
                                    operators.add(dr.operatorInitials);
                                }
                            }
                        }
                    } catch (IOException ex) {
                        System.err.println("Bluetooth remote device error");
                    }
                }

                @Override
                public void inquiryCompleted(int discType) {
                    /* Notify thread when inquiry completed */
                    synchronized (inquiryCompletedEvent) {
                        inquiryCompletedEvent.notifyAll();
                    }
                }

                /* To find service on bluetooth */
                @Override
                public void serviceSearchCompleted(int transID, int respCode) {
                }

                /* To find service on bluetooth */
                @Override
                public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {
                }
            };

            synchronized (inquiryCompletedEvent) {
                /* Start device discovery */
                boolean started = LocalDevice.getLocalDevice().getDiscoveryAgent().startInquiry(DiscoveryAgent.GIAC, listener);
                if (started) {
                    System.out.println("wait for device inquiry to complete...");
                    inquiryCompletedEvent.wait(60000);
                }
            }
        } catch (Exception e) {
            System.err.println("Bluetooth exception, operator detection aborted");
            e.printStackTrace(System.err);
        }
        /* Return list of devices */
        System.out.println("Discovery finished.");
    }
}
