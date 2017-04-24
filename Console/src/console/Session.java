package console;

import java.io.File;
import java.util.ArrayList;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
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

    Session(String folderPath) {
        this.folder = folderPath;
        this.imgCounter = 1;
        asi = new ArrayList<>();
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
                fullName += "_channel_" + si.capturedChannels[i] + ".png";
                si.fileName = fullName;
                try {

                    file = new File(fullName);
                    ImageIO.write(SwingFXUtils.fromFXImage(si.images[i], null), "png", file);

                    if (i == 0 && upload) {
                        FileUpload.uploadFileToServer(fullName);
                    }
                } catch (Exception ex) {
                    System.out.println(ex.getMessage());
                }
                this.addFolderThumbnail(fullName);
            }
        });
        t.start();

    }

    private void scanFolder(String folderPath) {
    }

    public void addStereoImage(SEMImage siLeft, SEMImage siRight, String name, boolean upload) {
        SEMImage siStereo = new SEMImage(siLeft, siRight);
        siStereo.knitStereoImage();
        this.saveImageSetAndAdd(siStereo, name, null, upload);
    }

}
