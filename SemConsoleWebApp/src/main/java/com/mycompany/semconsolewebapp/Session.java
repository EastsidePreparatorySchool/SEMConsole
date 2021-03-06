package com.mycompany.semconsolewebapp;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.LinkedTransferQueue;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javax.imageio.ImageIO;

public class Session {

    private class SessionImage {

        SEMImage si;
        Image thumbNail;
        String fileName;

        public void dehydrate() {
        }

        public void rehydrate() {
        }
    }

    private String folder;
    private ArrayList<SessionImage> asi;
    private int imgCounter;
    private Console consoleInstance;
    public ArrayList<String> operators;

    Session(String folderPath, Console instance) {
        this.folder = folderPath;
        this.imgCounter = 1;
        asi = new ArrayList<>();
        consoleInstance = instance;
        this.operators = new ArrayList<>();
        
        new Thread(()->Bluetooth.getDevices(operators, Console.registered)).start();
    }

  
    private void addFolderThumbnail(String imageFileName) {
        Image thumbnail = new Image(imageFileName, 400, 300, false, false);

    }

    public String generatePartialImageName() {
        return "img-" + this.imgCounter++;
    }

    public void saveImageSetAndAdd(SEMImage si, final String partialName, final String suffix, boolean upload, WritableImage wiJPG) {
        Thread t = new Thread(() -> {
            for (int i = 0; i < si.channels; i++) {
                File file;
                String fullName;
                if (partialName == null) {
                    fullName = this.generatePartialImageName();
                } else {
                    fullName = partialName;
                    if (suffix != null) {
                        fullName += suffix;
                    }
                }
                Platform.runLater(() -> {
                    this.consoleInstance.addThumbnail(si);
                });

                if (!Console.testMode) {
                    // add metadata to file name
                    fullName = decorateFileName(fullName, si, i);
                    si.fileName = fullName;
                    try {
                        file = new File(this.folder + System.getProperty("file.separator") + fullName);
                        ImageIO.write(SwingFXUtils.fromFXImage(si.images[i], null), "png", file);
                    } catch (Exception ex) {
                        System.err.println("session: write failed");
                        System.err.println(ex.getMessage());
                    }

                    if (i == 0 && upload && wiJPG != null) {
                        fullName = fullName.substring(0, fullName.length() - 4); // take of ".png"
                        fullName += ".jpg";
                        si.fileName = fullName;
                        try {
                            // make lower-resolution jpg from image, then save and upload
                            file = new File(this.folder + System.getProperty("file.separator") + fullName);

                            ImageIO.write(SwingFXUtils.fromFXImage(wiJPG, null), "jpg", file);

                            FileUpload.uploadFileAndMetaDataToServer(fullName,
                                    this.getOperatorString(),
                                    Console.channelNames[si.capturedChannels[i]],
                                    si.kv,
                                    si.magnification,
                                    si.wd);
                        } catch (Exception ex) {
                            System.err.println("jpg upload error: " + ex.getMessage());
                            ex.printStackTrace(System.err);
                        }
                    }
                }
            }
        });
        t.start();

    }

    public String getOperatorString() {
        return String.join("&", operators);
    }

    public String decorateFileName(String fileName, SEMImage si, int channel) {
        return fileName + "_channel-" + si.capturedChannels[channel]
                + "_kv-" + si.kv
                + "_mag-" + si.magnification
                + "_wd-" + si.wd
                + "_operators-" + getOperatorString()
                + "_.png";
    }

    public static void parseFileName(String fileName, SEMImage si) {
        int slash = fileName.lastIndexOf(System.getProperty("file.separator"));
        if (slash != -1) {
            fileName = fileName.substring(slash + 1);
        }

        String[] parts = fileName.split("[_.]");
//        for (int i = 0; i < parts.length; i++) {
//            System.out.println("part: " + parts[i]);
//        }
        String partName = null;
        String partValue = null;
        for (int i = 0; i < parts.length - 2; i++) {
            try {
                partName = parts[i].substring(0, parts[i].indexOf("-"));
                partValue = parts[i].substring(parts[i].indexOf("-") + 1);
                switch (partName) {
                    case "channel":
                        si.capturedChannels[0] = Integer.parseInt(partValue);
                        break;
                    case "kv":
                        si.kv = Integer.parseInt(partValue);
                        break;
                    case "mag":
                        si.magnification = Integer.parseInt(partValue);
                        break;
                    case "wd":
                        si.wd = Integer.parseInt(partValue);
                        break;
                    case "operators":
                        si.operators = partValue;
                        break;
                    default:
                        //System.out.println("Unregonized file part name " + partName);
                        break;
                }
            } catch (Exception e) {
                System.out.println("Part exception: " + partName + ":" + partValue);
            }

        }
    }

    public SEMImage loadFile(String fileName) {
        SEMImage si = new SEMImage(fileName);
        try {
            Console.println("Loading file " + fileName);
            File file = new File(this.folder + System.getProperty("file.separator") + fileName);
            BufferedImage bi = ImageIO.read(file);
            Console.println("succesfully read file " + fileName);

            si.images[0] = SwingFXUtils.toFXImage(bi, null);
            Console.println("successfully converted file " + fileName);

            si.width = (int) si.images[0].getWidth();
            si.height = (int) si.images[0].getHeight();
            si.readers[0] = si.images[0].getPixelReader();
            si.writers[0] = si.images[0].getPixelWriter();
        } catch (Exception ex) {
            System.err.println("session: file read error");
            System.err.println(ex.getMessage());
            si = null;
        }

        return si;
    }

    public String[] gatherFiles() {
        ArrayList<String> files = new ArrayList<>();
        Console.println("Scanning " + this.folder);

        scanFolder(this.folder, files);
        Console.println("Images found: " + files.size());
        return files.toArray(new String[files.size()]);

    }

    public void scanFolder(String folder, ArrayList<String> picFiles) {
        try {
            File folderFile = new File(folder);

            File[] files = folderFile.listFiles();

            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        // recurse
                        scanFolder(folder + f.getName() + System.getProperty("file.separator"), picFiles);
                    } else if ((f.getName().toLowerCase().endsWith(".png"))) {
                        picFiles.add(f.getName());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("folder scan error");
            System.err.println(e.toString());
        }
    }

    public void addStereoImage(SEMImage siLeft, SEMImage siRight, String name, boolean upload) {
        SEMImage siStereo = new SEMImage(siLeft, siRight);
        siStereo.knitStereoImage();
        this.consoleInstance.displayPhoto(siStereo);
        this.saveImageSetAndAdd(siStereo, name, null, upload, null);
    }

    void readExistingFiles(ProgressIndicator pin, LinkedTransferQueue<SEMImage> ltq, Runnable updateDisplayLambda) {
        ArrayList<String> pics = new ArrayList<>();
        this.scanFolder(this.folder, pics);
        pics.sort(null);

        pin.setProgress(-1);
        pin.setVisible(true);

        // load them all, but on another thread
        Thread t = new Thread(() -> {
            SEMImage si = null;
            SEMImage siDisplay = null;

            int i = 0;
            for (String s : pics) {
                int i2 = i++;
                Platform.runLater(() -> {
                    pin.setProgress(i2 / (double) pics.size());
                });

                si = this.loadFile(s);
                if (si != null) {
                    synchronized (ltq) {
                        ltq.add(si);
                    }
                    Platform.runLater(updateDisplayLambda);
                }
            }

            Platform.runLater(() -> {
                pin.setProgress(1);
                pin.setVisible(false);
            });

        }
        );
        t.start();
    }

}
