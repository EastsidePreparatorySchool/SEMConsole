/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
 */
package usb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import org.usb4java.BufferUtils;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;
import org.usb4java.Transfer;
import org.usb4java.TransferCallback;
import org.usb4java.*;

public class USB{

    private static TransferCallback receivedEvent;
    private static TransferCallback sentEvent;

    private static final short VENDOR_ID = 0x2341; // The vendor ID of the Arduino Due native USB port.
    private static final short PRODUCT_ID = 0x003E; // The vendor ID of the Arduino Due native USB port.
    private static final byte INTERFACE = 1; //The SEMSCAN interface number of the Arduino Due native USB port.
    private static final byte IN_ENDPOINT = (byte) 0x83; // The SEMSCAN input endpoint of the Arduino Due native USB port.
    private static final byte OUT_ENDPOINT = (byte) 2; //The SEMSCAN output endpoint of the Arduino Due native USB port.
    private static final int TIMEOUT = 5000; // The communication timeout in milliseconds.
    static volatile boolean exit = false; // Flag set during the asynchronous transfers to indicate the program is finished.


    /*
    This is the event handling thread. libusb doesn't start threads by its
    own so it is our own responsibility to give libusb time to handle the
    events in our own thread.
     */
    static class EventHandlingThread extends Thread {

        private volatile boolean abort; //If thread should abort.

        public void abort() {
            this.abort = true;
        }

        @Override
        public void run() {
            while (!this.abort) {
                try {
                    // Let libusb handle pending events. This blocks until events
                    // have been handled, a hotplug callback has been deregistered
                    // or the specified time of 0.5 seconds (Specified in
                    // Microseconds) has passed.
                    int result = LibUsb.handleEventsTimeout(null, 500000);
                    if (result != LibUsb.SUCCESS) {
                        throw new LibUsbException("Unable to handle events", result);
                    }
                } catch (Exception e) {
                    return;
                }
            }
        }
    }

    /**
     * Asynchronously writes some data to the device.
     *
     * @param handle The device handle.
     * @param data The data to send to the device.
     * @param callback The callback to execute when data has been transfered.
     */
    public static void write(DeviceHandle handle, byte[] data, TransferCallback callback) {
        ByteBuffer buffer = BufferUtils.allocateByteBuffer(data.length);
        buffer.put(data);
        Transfer transfer = LibUsb.allocTransfer();
        LibUsb.fillBulkTransfer(transfer, handle, OUT_ENDPOINT, buffer,
                callback, null, TIMEOUT);
        System.out.println("Sending " + data.length + " bytes to device");
        int result = LibUsb.submitTransfer(transfer);
        if (result != LibUsb.SUCCESS) {
            throw new LibUsbException("Unable to submit transfer", result);
        }
    }

    /**
     * Asynchronously reads some data from the device.
     *
     * @param handle The device handle.
     * @param size The number of bytes to read from the device.
     * @param callback The callback to execute when data has been received.
     */
    public static void read(DeviceHandle handle, int size, TransferCallback callback) {
        ByteBuffer buffer = BufferUtils.allocateByteBuffer(size).order(ByteOrder.LITTLE_ENDIAN);
        Transfer transfer = LibUsb.allocTransfer();
        LibUsb.fillBulkTransfer(transfer, handle, IN_ENDPOINT, buffer, callback, null, TIMEOUT);
        System.out.println("Reading " + size + " bytes from device");
        // IntBuffer transferred = IntBuffer.allocate(1);
        // int result = LibUsb.bulkTransfer(handle, IN_ENDPOINT, buffer, transferred, TIMEOUT);
        int result = LibUsb.submitTransfer(transfer);
        if (result != LibUsb.SUCCESS) {
            throw new LibUsbException("Unable to submit transfer", result);
        }
    }

    /**
     * Main method.
     *
     * @param args Command-line arguments (Ignored)
     * @throws Exception When something goes wrong.
     */
    public static void main(String[] args) throws Exception {
        // Initialize the libusb context

        int result = LibUsb.init(null);
        if (result != LibUsb.SUCCESS) {
            throw new LibUsbException("Unable to initialize libusb", result);
        }

        LibUsb.setDebug(null, 3);

        // Open test device
        Device device = findDevice(VENDOR_ID, PRODUCT_ID);
        if (device == null) {
            System.out.println("Device not found.");
            System.exit(0);
        }
        DeviceHandle handle = new DeviceHandle();
        result = LibUsb.open(device, handle);
        if (result != LibUsb.SUCCESS) {
            System.out.println("Device cannot be opened." + result);
            System.exit(0);
        }

        // Start event handling thread
        EventHandlingThread thread = new EventHandlingThread();
        thread.start();

        try {
            // Claim the SEMSCAN interface
            result = LibUsb.claimInterface(handle, INTERFACE);
            if (result != LibUsb.SUCCESS) {
                throw new LibUsbException("Unable to claim interface", result);
            }

            USB.sentEvent = (transfer) -> {
                if (transfer.status() == LibUsb.TRANSFER_COMPLETED) {
                    System.out.println(transfer.actualLength() + " bytes sent");
                    System.out.print(transfer.buffer().get(0));
                    System.out.print(" ");
                    System.out.print(transfer.buffer().get(1));
                    System.out.print(" ");
                    System.out.print(transfer.buffer().get(2));
                    System.out.print(" ");
                    System.out.print(transfer.buffer().get(3));
                    System.out.print(" ");
                    LibUsb.freeTransfer(transfer);
                    System.out.println("Asynchronous write finished");
                    read(handle, 16, USB.receivedEvent);
                } else {
                    exit = true;
                    System.out.println("Asynchronous write did not complete");
                }
            };

            USB.receivedEvent = (transfer) -> {
                if (transfer.status() == LibUsb.TRANSFER_COMPLETED) {
                    System.out.println(transfer.actualLength() + " bytes received");
                    System.out.print(transfer.buffer().get(0));
                    System.out.print(" ");
                    System.out.print(transfer.buffer().get(1));
                    System.out.print(" ");
                    System.out.print(transfer.buffer().get(2));
                    System.out.print(" ");
                    System.out.print(transfer.buffer().get(3));
                    System.out.print(" ");
                    LibUsb.freeTransfer(transfer);
                    System.out.println("Asynchronous read finished");
                    write(handle, "SEMSCAN.getNextLine\000".getBytes(), USB.sentEvent);
                } else {
                    exit = true;
                    System.out.println("Asynchronous read did not complete.");
                }
            };
            //
            // write a test phrase to open connection
            //
            write(handle, "EPS_SEM_CONNECT.".getBytes(), sentEvent);
//            read(handle, 1, USBTest.receivedEvent);

            // Fake application loop
            while (!exit) {
                Thread.yield();
            }

        } finally {
            thread.abort();
            thread.join();
        }

        // Release the ADB interface
        result = LibUsb.releaseInterface(handle, INTERFACE);
        if (result != LibUsb.SUCCESS) {
            throw new LibUsbException("Unable to release interface", result);
        }

        // Close the device
        LibUsb.close(handle);

        // Stop event handling thread
        thread.abort();
        thread.join();

        // Deinitialize the libusb context
        LibUsb.exit(null);

        System.out.println("Program finished");
    }

    public static Device findDevice(short vendorId, short productId) {
        // Read the USB device list
        System.out.println("Looing for: " + vendorId + ", " + productId);
        DeviceList list = new DeviceList();
        int result = LibUsb.getDeviceList(null, list);
        if (result < 0) {
            throw new LibUsbException("Unable to get device list", result);
        }

        try {
            // Iterate over all devices and scan for the right one
            for (Device device : list) {
                DeviceDescriptor descriptor = new DeviceDescriptor();
                result = LibUsb.getDeviceDescriptor(device, descriptor);
                if (result != LibUsb.SUCCESS) {
                    throw new LibUsbException("Unable to read device descriptor", result);
                }

                System.out.print("Vendor:  " + descriptor.idVendor());
                System.out.println(", Product: " + descriptor.idProduct());
                if (descriptor.idVendor() == 0x2341 && descriptor.idProduct() == 0x3e) {

                    ConfigDescriptor cd = new ConfigDescriptor();
                    result = LibUsb.getConfigDescriptor(device, (byte) 0, cd);
                    if (result != LibUsb.SUCCESS) {
                        throw new LibUsbException("Unable to read device Config descriptor", result);
                    }

                    //System.out.println("Descriptor: " + descriptor.dump());
                    System.out.println("Config for interface 0: " + cd.dump());
                }

                if (descriptor.idVendor() == vendorId && descriptor.idProduct() == productId) {
                    return device;
                }
            }
        } finally {
            // Ensure the allocated device list is freed
            LibUsb.freeDeviceList(list, true);
        }

        // Device not found
        return null;
    }
}
/*
#include <iostream>
#include <libusb.h>
using namespace std;

void printdev(libusb_device *dev); //prototype of the function

int main() {
	libusb_device **devs; //pointer to pointer of device, used to retrieve a list of devices
	libusb_context *ctx = NULL; //a libusb session
	int r; //for return values
	ssize_t cnt; //holding number of devices in list
	r = libusb_init(&ctx); //initialize a library session
	if(r < 0) {
		cout<<"Init Error "<<r<<endl; //there was an error
				return 1;
	}
	libusb_set_debug(ctx, 3); //set verbosity level to 3, as suggested in the documentation
	cnt = libusb_get_device_list(ctx, &devs); //get the list of devices
	if(cnt < 0) {
		cout<<"Get Device Error"<<endl; //there was an error
	}
	cout<<cnt<<" Devices in list."<<endl; //print total number of usb devices
		ssize_t i; //for iterating through the list
	for(i = 0; i < cnt; i++) {
				printdev(devs[i]); //print specs of this device
		}
		libusb_free_device_list(devs, 1); //free the list, unref the devices in it
		libusb_exit(ctx); //close the session
		return 0;
}

void printdev(libusb_device *dev) {
	libusb_device_descriptor desc;
	int r = libusb_get_device_descriptor(dev, &desc);
	if (r < 0) {
		cout<<"failed to get device descriptor"<<endl;
		return;
	}
	cout<<"Number of possible configurations: "<<(int)desc.bNumConfigurations<<"  ";
	cout<<"Device Class: "<<(int)desc.bDeviceClass<<"  ";
	cout<<"VendorID: "<<desc.idVendor<<"  ";
	cout<<"ProductID: "<<desc.idProduct<<endl;
	libusb_config_descriptor *config;
	libusb_get_config_descriptor(dev, 0, &config);
	cout<<"Interfaces: "<<(int)config->bNumInterfaces<<" ||| ";
	const libusb_interface *inter;
	const libusb_interface_descriptor *interdesc;
	const libusb_endpoint_descriptor *epdesc;
	for(int i=0; i<(int)config->bNumInterfaces; i++) {
		inter = &config->interface[i];
		cout<<"Number of alternate settings: "<<inter->num_altsetting<<" | ";
		for(int j=0; j<inter->num_altsetting; j++) {
			interdesc = &inter->altsetting[j];
			cout<<"Interface Number: "<<(int)interdesc->bInterfaceNumber<<" | ";
			cout<<"Number of endpoints: "<<(int)interdesc->bNumEndpoints<<" | ";
			for(int k=0; k<(int)interdesc->bNumEndpoints; k++) {
				epdesc = &interdesc->endpoint[k];
				cout<<"Descriptor Type: "<<(int)epdesc->bDescriptorType<<" | ";
				cout<<"EP Address: "<<(int)epdesc->bEndpointAddress<<" | ";
			}
		}
	}
	cout<<endl<<endl<<endl;
	libusb_free_config_descriptor(config);
}
 */
