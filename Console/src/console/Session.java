package console;

import java.io.File;
import java.util.ArrayList;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
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
    private String operators;

    Session(String folderPath, Console instance, String operators) {
        this.folder = folderPath;
        this.imgCounter = 1;
        asi = new ArrayList<>();
        consoleInstance = instance;
        this.operators = operators;
    }

    private void addFolderThumbnail(String imageFileName) {
        Image thumbnail = new Image(imageFileName, 400, 300, false, false);

    }

    public String generatePartialImageName() {
        return this.folder + System.getProperty("file.separator") + "img_" + this.imgCounter++;
    }

    public void saveImageSetAndAdd(SEMImage si, final String partialName, final String suffix, boolean upload) {
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

                fullName += "_channel_" + si.capturedChannels[i] + ".png";
                si.fileName = fullName;
                try {

                    file = new File(fullName);
                    ImageIO.write(SwingFXUtils.fromFXImage(si.images[i], null), "png", file);
                } catch (Exception ex) {
                    System.out.println(ex.getMessage());
                }
                
                if (i == 0 && upload) {
                    fullName += "_channel_" + si.capturedChannels[i] + ".jpg";
                    si.fileName = fullName;
                    try {
                        // make lower-resolution jpg from image, then save and upload
                        file = new File(fullName);
                        ImageView iv = new ImageView(si.images[i]);
                        Pane p = new Pane();
                        p.getChildren().add(iv);
                        p.setMinSize(1080/3*4, 1080);
                        p.setMaxSize(1080/3*4, 1080);
                        WritableImage wi = new WritableImage(1080/3*4, 1080);
                        p.snapshot(null, wi);
                        
                        ImageIO.write(SwingFXUtils.fromFXImage(wi, null), "jpg", file);

                        FileUpload.uploadFileAndMetaDataToServer(fullName, 
                                this.operators, 
                                Console.channelNames[si.capturedChannels[i]], 
                                si.kv, 
                                si.magnification, 
                                si.wd);
                    } catch (Exception ex) {
                        System.out.println(ex.getMessage());
                    }
                }
            }
        });
        t.start();

    }

    private void scanFolder(String folderPath) {
    }

    public void addStereoImage(SEMImage siLeft, SEMImage siRight, String name, boolean upload) {
        SEMImage siStereo = new SEMImage(siLeft, siRight);
        siStereo.knitStereoImage();
        this.consoleInstance.displayPhoto(siStereo);
        this.saveImageSetAndAdd(siStereo, name, null, upload);
    }

}
