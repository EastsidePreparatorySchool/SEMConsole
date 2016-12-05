package console;

import dk.thibaut.serial.SerialChannel;
import dk.thibaut.serial.SerialPort;
import dk.thibaut.serial.enums.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SEMPort {

    String name = "";
    SerialPort port;
    OutputStream ostream;
    InputStream istream;
    SerialChannel channel;
    ByteBuffer buffer;
    int dotCounter = 0;
    int numErrors = 0;
    int numOKs = 0;

    SEMPort() {
    }

    public void initialize() throws Exception {

        ArrayList<String> portNames = null;

        // Get a list of available ports names (COM2, COM4, ...)
        try {
            // copy this so it does not change underneath us while iterating
            portNames = new ArrayList<>(SerialPort.getAvailablePortsNames());
        } catch (Exception e) {
            System.err.println("SEMPort: No ports on this system");
            throw e;
        }
        System.out.println("Ports: " + portNames.toString());

        for (String name : portNames) {
            // Get a new instance of SerialPort by opening a port.
            try {
                System.out.println("Opening port: " + name);
                this.port = SerialPort.open(name);
            } catch (IOException e) {
                System.out.println(e.toString());
                continue;
            } catch (NullPointerException n) {
                System.out.println(n.toString());
                continue;
            }
            try {
                // Configure the connection
                port.setTimeout(100);
                //port.setConfig(BaudRate.B115200, Parity.NONE, StopBits.ONE, DataBits.D8);

                channel = port.getChannel();
                //ostream = port.getOutputStream();
                //istream = port.getInputStream();
                buffer = ByteBuffer.allocate(33000);
                buffer.order(ByteOrder.LITTLE_ENDIAN);

                ByteBuffer target = ByteBuffer.wrap("EPS_SEM_READY...".getBytes(StandardCharsets.UTF_8));
                ByteBuffer command = ByteBuffer.wrap("EPS_SEM_CONNECT.".getBytes(StandardCharsets.UTF_8));

                //for (int count = 0; count < 30; count++) {
                // ask the port whether our SEM is listening on the other side
                channel.write(command);

                int n = channel.read(buffer);
                //System.out.println("[read " + n + "bytes]");
                if (n != 0) {
                    buffer.position(0);
                    byte[] ab = new byte[16];
                    buffer.get(ab);
                    String result = new String(ab);
                    if (result.equals("EPS_SEM_READY...")) {
                        return;
                    }
                }

            } catch (Exception e) {
                System.out.println(e.toString());

            }
            if (port != null) {
                port.close();
            }
            System.out.println("Next port");
        }
        System.out.println("SEMPort: SEM port not found or no answer.");
        throw new Exception("SEMPort: SEM port not found or no answer.");
    }

    void shutdown() {
        try {
            //ostream.close();
            //istream.close();
            port.close();
            name = "";
        } catch (Exception e) {
        }
    }

    String peekMessage() {
        String result = null;
        int checkSum = 0;
        int checkSumRead = 0;

        try {
            buffer.position(0);
            buffer.limit(16); // look for commands

            int n = channel.read(buffer);
            //System.out.println("[read " + n + "bytes]");
            if (n != 0) {
                buffer.position(0);
                byte[] ab = new byte[16];
                buffer.get(ab);
                result = new String(ab);
                //System.out.println(result);

                switch (result) {
                    case "EPS_SEM_FRAME...":
                        System.out.println("Start of frame: ");
                        dotCounter = 0;
                        numErrors = 0;
                        numOKs = 0;
                        break;

                    case "EPS_SEM_BYTES...":
                        // read line number (unsigned short)
                        while (buffer.remaining() < 2) {
                            buffer.position(0);
                            buffer.limit(2);
                            n = channel.read(buffer);
                            buffer.position(0);
                            //System.out.print("[read " + n + "bytes]");
                        }
                        int line = Short.toUnsignedInt(buffer.getShort());
                        if (dotCounter == 0) {
                            System.out.print("Line: ");
                            System.out.print(line + ", ");
                        }

                        // read byte count (unsigned short)
                        while (buffer.remaining() < 2) {
                            buffer.position(0);
                            buffer.limit(2);
                            n = channel.read(buffer);
                            buffer.position(0);
                            //System.out.print("[read " + n + "bytes]");
                        }
                        int bytes = Short.toUnsignedInt(buffer.getShort());
                        if (dotCounter == 0) {
                            System.out.print("bytes: ");
                            System.out.println(bytes);
                        }

                        // read line bytes
                        checkSum = 0;
                        if (buffer.remaining() == 0) {
                            buffer.position(0);
                            buffer.limit(bytes);
                            n = channel.read(buffer);
                            buffer.position(0);
                            for (int i = 0; i < bytes / 2; i++) {
                                checkSum += Short.toUnsignedInt(buffer.getShort());
                            }
                            //System.out.println("[read " + n + " bytes]");
                        }

                        // read check sum
                        if (buffer.remaining() == 0) {
                            buffer.position(0);
                            buffer.limit(4);
                            n = channel.read(buffer);
                            buffer.position(0);
                            //System.out.println("[read " + n + " bytes]");
                        }
                        checkSumRead = buffer.getInt();
                        if (checkSum != checkSumRead) {
                            System.out.println();
                            System.out.print("Line: " + line + ", wrong check sum: reported: ");
                            System.out.println(Integer.toHexString(checkSumRead) + ", actual: " + Integer.toHexString(checkSum));
                            channel.write(ByteBuffer.wrap("NG".getBytes(StandardCharsets.UTF_8)));
                            numErrors++;
                        } else {
                            channel.write(ByteBuffer.wrap("OK".getBytes(StandardCharsets.UTF_8)));
                            numOKs++;
                        }

                        // read trailer
                        if (buffer.remaining() == 0) {
                            buffer.position(0);
                            buffer.limit(16);
                            n = channel.read(buffer);
                            //System.out.println("[read " + n + " bytes]");
                        }

                        System.out.print(".");
                        dotCounter++;
                        if (dotCounter % 100 == 0) {
                            System.out.println();
                        }
                        break;

                    case "EPS_SEM_EFRAME..":
                        System.out.println();
                        System.out.print("End of frame. Send time: ");
                        while (buffer.remaining() < 2) {
                            buffer.position(0);
                            buffer.limit(2);
                            n = channel.read(buffer);
                            buffer.position(0);
                            //System.out.print("[read " + n + "bytes]");
                        }
                        System.out.print(Short.toUnsignedInt(buffer.getShort()) + "ms, OKs: ");
                        System.out.println(numOKs + ", errors: " + numErrors);
                        result = "Finished";
                        break;

                    default:
                        System.out.println();
                        System.out.print("Other: ");
                        for (int i = 0; i < 16; i++) {
                            System.out.print(Integer.toHexString(Byte.toUnsignedInt(ab[i])) + " ");
                        }
                        System.out.println();
                        channel.write(ByteBuffer.wrap("NG".getBytes(StandardCharsets.UTF_8)));
                        numErrors++;

                        if (findSentinel()) {
                            dotCounter = 0;
                            return null;
                        }
                        System.out.println("Unable to recover.");
                        return "Finished";
                }
                buffer.position(0);

            }
        } catch (Exception e) {
            System.out.println(e.toString());
            for (StackTraceElement s : e.getStackTrace()) {
                System.out.println(s.toString());
            }
        }

        return result;
    }

    boolean findSentinel() {
        int n;
        byte[] ab = new byte[16];
        int skipped = 16;

        System.out.print("Attempting recovery ...");
        try {
            do {
                buffer.position(0);
                buffer.limit(16);
                n = channel.read(buffer);
                buffer.position(0);
                buffer.get(ab);

                // look for a sequence of 0, 1, 2, ...
                int i, j, k;
                for (i = 0; i < 16; i++) {
                    if (ab[i] == 0) {
                        for (j = 0; j < 16 - i; j++) {
                            if (ab[i + j] != j) {
                                break;
                            }
                        }
                        if (i == 0 && j == 16) {
                            System.out.println("... found sentinel. Lost bytes*: " + skipped);
                            return (true);
                        }

                        if (j == 16 - i) {
                            // if we get here, we have the beginnings of a sentinel
                            if (j < 16) {
                                buffer.position(0);
                                buffer.limit(16 - j); //
                                n = channel.read(buffer);
                                buffer.position(0);

                                // now we have the rest of the sentinel bytes, let's make sure they are correct
                                for (k = 0; k < 16 - j; k++) {
                                    if (buffer.get() != j + k) {
                                        break;
                                    }
                                }
                                if (k == 16 - j) {
                                    System.out.println(" found sentinel. Lost bytes: " + (skipped + i));
                                    return true;
                                }

                            }
                        }
                    }
                }
                skipped += 16;
            } while (n > 0);
        } catch (Exception e) {
        }
        return false;
    }
    
    
    
    // sample code from author of JSerial

    void test() throws IOException {

        // Get a list of available ports names (COM2, COM4, ...)
        List<String> portsNames = SerialPort.getAvailablePortsNames();

        System.out.println(portsNames.toString());

        // Get a new instance of SerialPort by opening a port.
        SerialPort port = SerialPort.open("COM13");

        // Configure the connection
        port.setTimeout(2000);

        port.setConfig(BaudRate.B115200, Parity.NONE, StopBits.ONE, DataBits.D8);

        // You have the choice, you can either use the Java NIO channels
        // or classic Input/Ouput streams to read and write data.
        SerialChannel channel = port.getChannel();

        InputStream istream = port.getInputStream(); // Read some data using a stream

        byte[] byteBuffer = new byte[4096];

        // Will timeout after 100ms, returning 0 if no bytes were available.
        int n = istream.read(byteBuffer);

        // Read some data using a ByteBuffer.
        ByteBuffer buffer = ByteBuffer.allocate(4 * 2048 * 2500);

        int c = channel.read(buffer);

        System.out.println("Bytes read: " + c);

        /*for (int i = 0; i < c; i++) {

            System.out.print(Integer.toHexString(buffer.get(i)) + " ");

        }

        System.out.println();

        System.out.println(buffer.asCharBuffer());*/
        port.close();

    }

}
