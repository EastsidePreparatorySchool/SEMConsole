package console;

import dk.thibaut.serial.SerialChannel;
import dk.thibaut.serial.SerialPort;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.LinkedTransferQueue;
import javafx.application.Platform;

public class SEMPort {

    public enum SEMError {
        ERROR_WRONG_PHASE,
        ERROR_BYTE_COUNT,
        ERROR_VALUE,
        ERROR_CHECK_SUM,
        ERROR_UNKNOWN_COMMAND,
        ERROR_OTHER

    }

    public class SEMException extends Exception {

        SEMError error;

        SEMException(SEMError error
        ) {
            this.error = error;
        }
    }

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
    int proposedBytes;
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

                ByteBuffer command = ByteBuffer.wrap("EPS_SEM_CONNECT.".getBytes(StandardCharsets.UTF_8));

                for (int i = 0; i < 20; i++) {
                    // ask the port whether our SEM is listening on the other side
                    channel.write(command);

                    int n = channel.read(buffer);
                    if (n != 0) {
                        buffer.position(0);
                        byte[] ab = new byte[16];
                        buffer.get(ab);
                        String result = new String(ab);
                        if (result.equals("EPS_SEM_READY...")) {
                            return;
                        } else {
                            Console.println("Wrong answer: 0x" + Integer.toHexString(ab[0]) + " 0x" + Integer.toHexString(ab[0]));
                        }
                    } else {
                        Console.println("No answer.");
                    }
                    Thread.sleep(50);
                }
            } catch (Exception e) {
                Console.println(e.toString());

            }
            if (port != null) {
                port.close();
                port = null;
            }
            Console.println("Next port");
        }
        Console.println("SEMPort: SEM port not found or no answer.");
        throw new Exception("SEMPort: SEM port not found or no answer.");
    }

    void drain() {
        try {
            // drain channel
            int n = channel.read(buffer);
        } catch (IOException ex) {
        }
    }

    void shutdown() {
        try {
            if (this.port != null) {
                drain();
                port.close();
                this.port = null;
            }
            name = "";
        } catch (Exception e) {
        }
    }

    SEMThread.Phase processMessage(LinkedTransferQueue<SEMImage> ltq, Runnable updateDisplayLambda, SEMThread.Phase phase) {
        SEMThread.Phase result = phase;
        String message;
        byte[] ab;
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
                ab = new byte[16];
                buffer.get(ab);
                message = new String(ab);
                //System.out.println(result);

                switch (message) {
                    case "EPS_SEM_RESET...":
                        Console.printOn();
                        Console.println("Reset");
                        return SEMThread.Phase.FINISHED;

                    case "EPS_SEM_FRAME...":
                        if (phase != SEMThread.Phase.WAITING_FOR_FRAME) {
                            throw new SEMException(SEMError.ERROR_WRONG_PHASE);
                        }
                        Console.printOn();
                        Console.print("Start of frame: ");
                        dotCounter = 0;
                        numErrors = 0;
                        numOKs = 0;
                        lastBytes = 0;

                        // read channel count, width, height
                        buffer.rewind();
                        buffer.limit(16);
                        n = channel.read(buffer);
                        //System.out.print("[read " + n + "bytes]");
                        if (n != 16) {
                            throw new SEMException(SEMError.ERROR_BYTE_COUNT);
                        }
                        buffer.flip();

                        // read line number (unsigned short)
                        int channelCount = Short.toUnsignedInt(buffer.getShort());
                        int width = Short.toUnsignedInt(buffer.getShort());
                        int height = Short.toUnsignedInt(buffer.getShort());
                        int time = Short.toUnsignedInt(buffer.getShort());

                        Console.print("width: " + width + ", height: " + height);
                        Console.print(", line scan time: ");
                        Console.print("" + ((long) time));
                        Console.print("us");
                        Console.print(", channels: ");

                        int[] capturedChannels = new int[4];
                        for (int i = 0; i < 4; i++) {
                            capturedChannels[i] = Short.toUnsignedInt(buffer.getShort());
                            if (i < channelCount) {
                                Console.print(capturedChannels[i] + " ");
                            }
                        }
                        this.proposedBytes = channelCount * width * 2;

                        // allocate buffer and image
                        rawMultiChannelBuffer = new int[channelCount * width];
                        this.si = new SEMImage(channelCount, capturedChannels, width, height);
                        result = SEMThread.Phase.WAITING_FOR_BYTES_OR_EFRAME;
                        lastBytes = this.proposedBytes + 24; 
                        break;

                    case "EPS_SEM_BYTES...":
                        if (phase != SEMThread.Phase.WAITING_FOR_BYTES_OR_EFRAME) {
                            throw new SEMException(SEMError.ERROR_WRONG_PHASE);
                        }
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
                                throw new SEMException(SEMError.ERROR_BYTE_COUNT);
                            }
                            buffer.flip();
                        }

                        // read check sum
                        checkSumRead = buffer.getInt();
                        // read line number (unsigned short)
                        int line = Short.toUnsignedInt(buffer.getShort());
                        // read byte count (unsigned short)
                        int bytes = Short.toUnsignedInt(buffer.getShort());

                        // read line bytes
                        checkSum = bytes + line;
                        if (bytes != this.proposedBytes) {
                            channel.write(ByteBuffer.wrap("NG".getBytes(StandardCharsets.UTF_8)));
                            numErrors++;
                            Console.print("-");
                            dotCounter++;

                            throw new SEMException(SEMError.ERROR_BYTE_COUNT);
                        }

                        int word;
                        for (int i = 0; i < bytes / 2; i++) {
                            word = Short.toUnsignedInt(buffer.getShort());
                            rawMultiChannelBuffer[i] = word;
                            checkSum += word;
                        }

                        if (checkSum != checkSumRead) {
                            throw new SEMException(SEMError.ERROR_CHECK_SUM);
                        }

                        // print dot for successful lines, send "ok", process line
                        if (++dotCounter % lines == 0) {
                            Console.print(".");
                        }
                        channel.write(ByteBuffer.wrap("OK".getBytes(StandardCharsets.UTF_8)));
                        numOKs++;
                        this.si.parseRawLine(line, this.rawMultiChannelBuffer, bytes / 2);

                        result = SEMThread.Phase.WAITING_FOR_BYTES_OR_EFRAME;
                        break;

                    case "EPS_SEM_ENDFRAME":
                        if (phase != SEMThread.Phase.WAITING_FOR_BYTES_OR_EFRAME) {
                            throw new SEMException(SEMError.ERROR_WRONG_PHASE);
                        }

                        Console.println();
                        Console.print("End of frame. Max line adc time: ");
                        if (buffer.remaining() < 2) {
                            buffer.position(0);
                            buffer.limit(2);
                            n = channel.read(buffer);
                            buffer.position(0);
                            //System.out.print("[read " + n + "bytes]");
                        }
                        Console.print(Short.toUnsignedInt(buffer.getShort()) + "us, frame send time: ");
                        if (buffer.remaining() < 2) {
                            buffer.position(0);
                            buffer.limit(2);
                            n = channel.read(buffer);
                            buffer.position(0);
                            //System.out.print("[read " + n + "bytes]");
                        }
                        Console.print(Short.toUnsignedInt(buffer.getShort()) + "ms, OKs: ");
                        Console.println(numOKs + ", errors: " + numErrors);
                        synchronized (ltq) {
                            ltq.add(this.si);
                            this.si = null;
                        }
                        Platform.runLater(updateDisplayLambda);
                        result = SEMThread.Phase.WAITING_FOR_FRAME;
                        break;

                    default:
                        throw new SEMException(SEMError.ERROR_UNKNOWN_COMMAND);
                }
                // done processing succesful message. reset buffer
                buffer.position(0);
            } else {
                //  we get here through timeout
                //Thread.sleep(1);
                result = phase;
            }
        } catch (SEMException e) {
            String command = "NG";
//            Console.printOn();
//            Console.print("-");
//            Console.print(" " +e.error.name()+" ");
            if (findSentinel()) {
                if (phase == SEMThread.Phase.WAITING_FOR_BYTES_OR_EFRAME) {
                    result = phase;
                } else {
                    command = "AB"; //abort frame
                    result = SEMThread.Phase.WAITING_FOR_FRAME;
                }
            } else {
                Console.println("Unable to recover, closing connection.");
               command = "AB"; //abort frame
                result = SEMThread.Phase.FINISHED;
            }

            try {
                channel.write(ByteBuffer.wrap(command.getBytes(StandardCharsets.UTF_8)));
                numErrors++;
                if (command.equals("AB")) {
                    // drain the channel in a desparate attempt to reset the frame transport
                    this.drain();
                }
            } catch (IOException ex) {
                Console.println("Unable to communicate, closing connection.");
                result = SEMThread.Phase.FINISHED;
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
