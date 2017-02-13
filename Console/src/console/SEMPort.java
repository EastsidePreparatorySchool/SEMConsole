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
import java.util.concurrent.LinkedTransferQueue;
import javafx.application.Platform;

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
    int lastBytes = 0;

    int[] rawMultiChannelBuffer;
    SEMImage si;

    SEMPort() {
    }

    public void initialize() throws Exception {

        ArrayList<String> portNames = null;

        // Get a list of available ports names (COM2, COM4, ...)
        try {
            // copy this so it does not change underneath us while iterating
            portNames = new ArrayList<>(SerialPort.getAvailablePortsNames());
        } catch (Exception e) {
            Console.println("SEMPort: No ports on this system");
            throw e;
        }
        Console.println("Ports: " + portNames.toString());

        for (String name : portNames) {
            // Get a new instance of SerialPort by opening a port.
            try {
                Console.println("Opening port: " + name);
                this.port = SerialPort.open(name);
            } catch (IOException e) {
                Console.println(e.toString());
                continue;
            } catch (NullPointerException n) {
                Console.println(n.toString());
                continue;
            }
            try {
                // Configure the connection
                port.setTimeout(100);
                //port.setConfig(BaudRate.B115200, Parity.NONE, StopBits.ONE, DataBits.D8);

                channel = port.getChannel();
                //ostream = port.getOutputStream();
                //istream = port.getInputStream();
                buffer = ByteBuffer.allocateDirect(1000000);
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
                Console.println(e.toString());

            }
            if (port != null) {
                port.close();
            }
            Console.println("Next port");
        }
        Console.println("SEMPort: SEM port not found or no answer.");
        throw new Exception("SEMPort: SEM port not found or no answer.");
    }

    void shutdown() {
        try {
            //ostream.close();
            //istream.close();
            if (this.port != null) {
                port.close();
            }
            name = "";
        } catch (Exception e) {
        }
    }

    String peekMessage(LinkedTransferQueue<SEMImage> ltq, Runnable updateDisplayLambda) {
        String result = null;
        int checkSum = 0;
        int checkSumRead = 0;
        int lines = 100;

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
                        Console.print("\nStart of frame: ");
                        dotCounter = 0;
                        numErrors = 0;
                        numOKs = 0;
                        lastBytes = 0;

                        // read channel count, width, height
                        buffer.rewind();
                        buffer.limit(14);
                        n = channel.read(buffer);
                        //System.out.print("[read " + n + "bytes]");
                        if (n != 14) {
                            return null;
                        }
                        buffer.flip();

                        // read line number (unsigned short)
                        int channelCount = Short.toUnsignedInt(buffer.getShort());
                        int width = Short.toUnsignedInt(buffer.getShort());
                        int height = Short.toUnsignedInt(buffer.getShort());
                        Console.println("channels: " + channelCount + ", width: " + width + ", height: " + height);
                        Console.print("Captured channels: ");
                        int[] capturedChannels = new int[4];
                        for (int i = 0; i < 4; i++) {
                            capturedChannels[i] = Short.toUnsignedInt(buffer.getShort());
                            if (i < channelCount) {
                                Console.print(capturedChannels[i] + " ");
                            }
                        }
                        Console.println();
                        rawMultiChannelBuffer = new int[channelCount * width];
                        this.si = new SEMImage(channelCount, capturedChannels, width, height);
                        break;

                    case "EPS_SEM_BYTES...":
                        Console.printOff();
                        // read whole line if length known
                        if (lastBytes != 0) {
                            buffer.rewind();
                            buffer.limit(lastBytes);
                            n = channel.read(buffer);
                            //System.out.print("[prefetch read " + n + "bytes]");
                            if (n != lastBytes) {
                                channel.write(ByteBuffer.wrap("NG".getBytes(StandardCharsets.UTF_8)));
                                numErrors++;
                                Console.print("-");
                                dotCounter++;

                                return null;
                            }
                            buffer.flip();
                        }

                        // read line number (unsigned short)
                        while (buffer.remaining() < 2) {
                            buffer.position(0);
                            buffer.limit(2);
                            n = channel.read(buffer);
                            buffer.flip();
                            //System.out.print("[read " + n + "bytes]");
                        }
                        int line = Short.toUnsignedInt(buffer.getShort());
                        if (dotCounter % lines == 0) {

                            Console.print("Line: ");
                            Console.print(line + ", ");
                        }

                        // read byte count (unsigned short)
                        while (buffer.remaining() < 2) {
                            buffer.position(0);
                            buffer.limit(2);
                            n = channel.read(buffer);
                            buffer.flip();
                            //System.out.print("[read " + n + "bytes]");
                        }
                        int bytes = Short.toUnsignedInt(buffer.getShort());
                        if (dotCounter % lines == 0) {
                            Console.print("bytes: ");
                            Console.print(bytes + ", ");
                        }

                        // read scan time (unsigned short)
                        while (buffer.remaining() < 2) {
                            buffer.position(0);
                            buffer.limit(2);
                            n = channel.read(buffer);
                            buffer.position(0);
                        }
                        int time = Short.toUnsignedInt(buffer.getShort());
                        if (line == 0 && dotCounter == 0) {
                            Console.printOn();
                            Console.print("Line scan time: ");
                            Console.print("" + ((long) time) * 100);
                            Console.println(" microseconds");
                            Console.printOff();
                        }

                        if (dotCounter % lines == 0) {
                        }

                        // todo: add checksum for this header
                        // read line bytes
                        checkSum = 0;
                        //System.out.print("[buffer remaining " + buffer.remaining() + " bytes]");

                        if (buffer.remaining() == 0) {
                            buffer.position(0);
                            buffer.limit(bytes);
                            n = channel.read(buffer);
                            //System.out.print("[read " + n + "bytes]");
                            buffer.position(0);
                        }
                        int word;
                        System.out.println ("Bytes: " + bytes);
                        for (int i = 0; i < bytes / 2; i++) {
                            word = Short.toUnsignedInt(buffer.getShort());
                            rawMultiChannelBuffer[i] = word;
                            checkSum += word;
                            if (dotCounter % lines == 0 && i < 4) {
                                Console.print("A" + (7 - (word >> 12)) + ":" + (word & 0xFFF) + " ");
                            }
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
//                            System.out.println();
//                            System.out.print("Line: " + line + ", wrong check sum: reported: ");
//                            System.out.println(Integer.toHexString(checkSumRead) + ", actual: " + Integer.toHexString(checkSum));
                            Console.printOn();
                            Console.print("-");
                            Console.printOff();
                            channel.write(ByteBuffer.wrap("NG".getBytes(StandardCharsets.UTF_8)));
                            numErrors++;
                        } else {
                            if (dotCounter % lines == 0) {
                                Console.printOn();
                                Console.print(".");
                                Console.printOff();
                            }
                            channel.write(ByteBuffer.wrap("OK".getBytes(StandardCharsets.UTF_8)));
                            numOKs++;
                            this.si.parseRawLine(line, this.rawMultiChannelBuffer, bytes / 2);
                        }

                        // read trailer
                        if (buffer.remaining() == 0) {
                            buffer.position(0);
                            buffer.limit(16);
                            n = channel.read(buffer);
                            //System.out.println("[read " + n + " bytes]");
                        }

                        dotCounter++;
                        if (dotCounter % lines == 0) {
                            //System.out.println();
                        }

                        lastBytes = bytes + 26;

                        Console.printOn();
                        break;

                    case "EPS_SEM_ENDFRAME":
                        Console.println();
                        Console.print("End of frame. Send time: ");
                        while (buffer.remaining() < 2) {
                            buffer.position(0);
                            buffer.limit(2);
                            n = channel.read(buffer);
                            buffer.position(0);
                            //System.out.print("[read " + n + "bytes]");
                        }
                        Console.print(Short.toUnsignedInt(buffer.getShort()) + "ms, OKs: ");
                        Console.println(numOKs + ", errors: " + numErrors);
                        ltq.add(this.si);
                        this.si = null;
                        Platform.runLater(updateDisplayLambda);

                        result = "Finished";
                        break;

                    default:
//                        System.out.println();
//                        System.out.print("Other: ");
//                        for (int i = 0; i < 16; i++) {
//                            System.out.print(Integer.toHexString(Byte.toUnsignedInt(ab[i])) + " ");
//                        }
//                        System.out.println();
                        channel.write(ByteBuffer.wrap("NG".getBytes(StandardCharsets.UTF_8)));
                        numErrors++;

                        if (findSentinel()) {
                            dotCounter = 0;
                            return null;
                        }
                        Console.println("Unable to recover.");
                        return "Finished";
                }
                buffer.position(0);

            }
        } catch (Exception e) {
            Console.println(e.toString());
            for (StackTraceElement s : e.getStackTrace()) {
                Console.println(s.toString());
            }
        }

        return result;
    }

    boolean findSentinel() {
        int n;
        byte[] ab = new byte[16];
        int skipped = 16;

        //System.out.print("Attempting recovery ...");
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
                            //System.out.println("... found sentinel. Lost bytes*: " + skipped);
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
                                    //System.out.println(" found sentinel. Lost bytes: " + (skipped + i));
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

}
