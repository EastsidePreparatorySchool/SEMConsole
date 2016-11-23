package semserial;

import dk.thibaut.serial.SerialChannel;
import dk.thibaut.serial.SerialPort;
import dk.thibaut.serial.enums.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;

public class SEMSerial {

    public static void main(String[] args) throws IOException {

        // Get a list of available ports names (COM2, COM4, ...)
        List<String> portsNames = SerialPort.getAvailablePortsNames();
        System.out.println(portsNames.toString());

        // Get a new instance of SerialPort by opening a port.
        SerialPort port = SerialPort.open("COM11");

        // Configure the connection
        port.setTimeout(8000);
        port.setConfig(BaudRate.B115200, Parity.NONE, StopBits.ONE, DataBits.D8);

        // You have the choice, you can either use the Java NIO channels
        // or classic Input/Ouput streams to read and write data.
        SerialChannel channel = port.getChannel();
        InputStream istream = port.getInputStream(); // Read some data using a stream

        byte[] byteBuffer = new byte[4096];
        // Will timeout after 100ms, returning 0 if no bytes were available.
        int n = istream.read(byteBuffer);

        // Read some data using a ByteBuffer.
        ByteBuffer buffer = ByteBuffer.allocate(4*2048*2500);
        int c = channel.read(buffer);
        System.out.println ("Bytes read: " + c);

        /*for (int i = 0; i < c; i++) {
            System.out.print(Integer.toHexString(buffer.get(i)) + " ");
        }
        System.out.println();
        System.out.println(buffer.asCharBuffer());*/

        port.close();
    }
}
