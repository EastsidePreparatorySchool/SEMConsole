/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package console;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedTransferQueue;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javax.imageio.ImageIO;

/**
 *
 * @author gmein
 */
public class Console extends Application {

    private static SEMThread semThread;
    private Pane root;
    private HBox top;
    private int channels;
    private ImageView[] aViews;
    private ImageView bigView;
    private StackPane[] aPanes;
    private StackPane masterPane;
    private VBox left;
    private VBox thumbnails;
    private StackPane right;
    private LinkedTransferQueue<SEMImage> ltq;
    private SEMImage currentImageSet = null;
    private SEMImage nextImageSet = null;
    private Button btn;
    private Text txt;
    private Scene scene;
    private Stage stage;
    private BorderPane bp;
    private Stage bigStage = null;
    private boolean isLive = true;
    private StackPane selectedPane = null;
    private CheckBox autoSave = null;
    private CheckBox autoUpload = null;
    private String session = null;
    private int imgCounter = 1;

    static private ConsolePane cp;
    static private boolean printOff = false;

    @Override
    public void start(Stage primaryStage) {

        // main transfer mechanism with other thread
        this.ltq = new LinkedTransferQueue<>();
        this.stage = primaryStage;

        primaryStage.setTitle("SEM Console");
        this.bp = new BorderPane();
        this.root = this.bp;

        // top line - controls
        top = new HBox();
        //top.setMinHeight(30);

        Button newSession = new Button("New Session");
        newSession.setOnAction((e) -> startNewSession());

        // button for connection
        this.btn = new Button("Connect");
        btn.setOnAction((event) -> startSEMThread());

        Button btn4 = new Button("Save image set");
        btn4.setOnAction((event) -> saveImageSet(this.currentImageSet));

        Button btn5 = new Button("Clear image list");
        btn5.setOnAction((event) -> {clearImageList();});

        txt = new Text("Not connected");
        HBox h = new HBox();
        h.getChildren().add(txt);
        h.setPadding(new Insets(6, 12, 6, 12));

        autoSave = new CheckBox("Auto save photos   ");
        autoSave.setOnAction((e) -> {
            if (!autoSave.isSelected()) {
                autoUpload.setSelected(false);
            }
        });
        autoSave.setSelected(true);
        autoUpload = new CheckBox("Auto upload");
        HBox h2 = new HBox();
        h2.getChildren().addAll(autoSave, autoUpload);
        h2.setPadding(new Insets(6, 12, 6, 12));

        top.setPadding(new Insets(15, 12, 15, 12));
        top.getChildren().addAll(newSession, btn, h, h2, btn4, btn5);
        bp.setTop(top);
        cp = new ConsolePane();
        cp.setPrefWidth(740);       // determines initial width of unmaximized window

        //panes
        this.aPanes = new StackPane[4];
        for (int i = 0; i < 4; i++) {
            this.aPanes[i] = new StackPane();
            this.aPanes[i].setPadding(new Insets(4, 4, 4, 4));
        }
        this.masterPane = new StackPane();
        //this.masterPane.setPadding(new Insets(8, 8, 8, 8));

        this.left = new VBox();
        this.left.setPadding(new Insets(4, 4, 4, 4));
        this.thumbnails = new VBox();
        ImageView lv = new ImageView(new Image("live.png"));
        lv.setFitHeight(150);
        lv.setFitWidth(200);
        lv.setPreserveRatio(false);
        lv.setSmooth(true);

        StackPane sp1 = new StackPane();
        sp1.setPrefSize(200, 150);
        sp1.setPadding(new Insets(4, 4, 4, 4));
        sp1.setAlignment(Pos.CENTER);
        sp1.getChildren().add(lv);
        sp1.setOnMouseClicked((e) -> {
            this.isLive = true;
            //System.out.println("Console: switched to live view");
            displayImageSet(this.nextImageSet);
            selectPane(sp1);
            e.consume();
        });

        ScrollPane scp = new ScrollPane(thumbnails);
        scp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.left.getChildren().addAll(sp1, scp);

        this.right = new StackPane();
        this.right.setPadding(new Insets(4, 4, 4, 4));

        // img
        this.aViews = new ImageView[4];
        for (int i = 0; i < 4; i++) {
            this.aViews[i] = new ImageView();
            this.aViews[i].setSmooth(false);
            this.aViews[i].setCache(true);
            this.aPanes[i].getChildren().add(this.aViews[i]);
            final int lambdaParam = i;
            this.aViews[i].setOnMouseClicked((e) -> displayPhoto(this.aViews[lambdaParam].getImage()));

            if (i != 0) {
                ColorAdjust colorAdjust = new ColorAdjust();
                colorAdjust.setHue(new double[]{0, -1, -0.2, 0.3}[i]);
//                colorAdjust.setBrightness(0.1);
//                colorAdjust.setSaturation(0.2);
//                colorAdjust.setContrast(0.1);
                this.aViews[i].setEffect(colorAdjust);
            }
        }

        HBox hbUp = new HBox();
        HBox hbDown = new HBox();
        VBox vb = new VBox();
        hbUp.getChildren().addAll(this.aPanes[0], this.aPanes[1]);
        hbUp.setAlignment(Pos.CENTER);
        hbDown.getChildren().addAll(this.aPanes[2], this.aPanes[3]);
        vb.getChildren().addAll(hbUp, hbDown);
        hbDown.setAlignment(Pos.CENTER);

        masterPane.getChildren().add(vb);
        masterPane.setAlignment(vb, Pos.CENTER);
        vb.setAlignment(Pos.CENTER);

        VBox vb2 = new VBox();

        bp.setBottom(this.cp);
        bp.setAlignment(this.cp, Pos.CENTER);
        bp.setLeft(this.left);
        bp.setRight(this.right);
        bp.setCenter(this.masterPane);
        bp.setAlignment(this.masterPane, Pos.CENTER);

        this.scene = new Scene(bp, 1200, 900);
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);
        primaryStage.show();

        for (int i = 0; i < 4; i++) {
            setSizeNormal(aViews[i], i);
        }

        cp.prefWidthProperty().bind(this.stage.widthProperty().subtract(16));
        left.prefHeightProperty().bind(this.stage.heightProperty().subtract(300));
        
        primaryStage.setOnCloseRequest((e) -> {if (bigStage != null) {bigStage.close();}});
        Platform.runLater(()->startNewSession());
    }

    private void startNewSession() {
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        String session = dateFormat.format(date);

        TextInputDialog tid = new TextInputDialog(session);
        tid.setTitle("New session");
        tid.setHeaderText("Enter a name for the new session:");
        Optional<String> result = tid.showAndWait();

        if (result.isPresent()) {
            session = result.get();
        }
        
        createFolder(getImageDir(), session);
        this.session = getImageDir() + session;
        this.imgCounter = 1;
    }

    private void displayImageSet(SEMImage si) {
        int channels = 0;
        if (si != null) {
            channels = si.channels;
        }

        // set absent channel images to empty
        if (channels < 4) {
            for (int i = channels; i < 4; i++) {
                this.aViews[i].setImage(null);
            }
        }

        if (si == null) {
            return;
        }

        // parse and create images if we have not done this before
        si.makeImagesForDisplay();

        // put the images in place
        for (int i = 0; i < channels; i++) {
            this.aViews[i].setImage(si.images[i]);

        }

        // adjust all of their sizes
        this.channels = channels;
        for (int i = 0; i < 4; i++) {
            setSizeNormal(aViews[i], i);
        }

        this.currentImageSet = si;
    }

    private void updateDisplay() {
        ArrayList<SEMImage> newImages = new ArrayList<>();
        synchronized (this.ltq) {
            if (!this.ltq.isEmpty()) {
                ltq.drainTo(newImages);
            }
        }

        if (newImages.isEmpty()) {
            return;
        }

//        Console.println("[Console: Received " + newImages.size() + " image set"
//                + (newImages.size() == 1 ? "" : "s") + "]");
        for (SEMImage si : newImages) {
            if (si.height < 1500) {
                if (this.isLive) {
                    // for live view, show  images
                    this.currentImageSet = si;
                    displayImageSet(this.currentImageSet);
                } else {
                    // for when we return to live view
                    this.nextImageSet = si;
                }
            } else {
                // for large (photo button) images, add to thumbnails
                addThumbnail(si);
            }
        }
    }

    private void startSEMThread() {
        // stop any existing SEM thread
        stopSEMThread();

        btn.setDisable(true);
        this.txt.setText("Trying to connect, please be patient ...");
        Console.println();
        Console.println("[Console: connecting ...]");

        // and create a new one
        try {
            System.out.println("starting thread ...");
            semThread = new SEMThread(this.ltq, () -> updateDisplay(), () -> SEMThreadStopped());
            semThread.start();
        } catch (Exception e) {
            System.out.println("exception starting thread.");
            System.out.println(e.getMessage());
        }

        runLaterAfterDelay(1000, () -> startThreadLambda());
    }

    private void startThreadLambda() {
        if (semThread != null && semThread.isAlive()) {
            this.btn.setText("Disconnect");
            this.txt.setText("Connected");
            btn.setOnAction((event) -> stopSEMThread());
        } else {
            this.txt.setText("Not connected");
        }
        btn.setDisable(false);
    }

    public void runLaterAfterDelay(int ms, Runnable r) {
        final KeyFrame kf1 = new KeyFrame(Duration.millis(ms), (e) -> r.run());
        final Timeline timeline = new Timeline(kf1);
        timeline.play();
    }

    private void stopSEMThread() {
        btn.setDisable(true);
        if (semThread != null) {
            Console.println();

            // stop any existing SEM thread
            this.txt.setText("Stopping worker thread ...");
            Console.println("[Console: disconnecting ...]");
            semThread.interrupt();
            try {
                semThread.join();
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
            }
            semThread = null;
            Console.println("[Console: disconnected]");
            this.btn.setText("Connect");
            this.txt.setText("Not connected");
        }
        btn.setOnAction((event) -> startSEMThread());
        btn.setDisable(false);
    }

    private void SEMThreadStopped() {
        Console.printOn();
        Console.println("[Console: disconnected]");
        this.btn.setText("Connect");
        this.txt.setText("Not connected");
        btn.setOnAction((event) -> startSEMThread());
    }

    private void setSizeNormal(ImageView iv, int channel) {
        if (this.channels == 2) {
            if (channel < 2) {
                iv.fitHeightProperty().bind(this.stage.widthProperty().subtract(300).multiply(3).divide(8));
                iv.fitWidthProperty().bind(this.stage.widthProperty().subtract(300).divide(2));
            } else {
                iv.fitHeightProperty().unbind();
                iv.fitWidthProperty().unbind();
                iv.setFitWidth(0);
                iv.setFitHeight(0);
            }
        } else if (this.channels == 1) {
            if (channel < 1) {
                iv.fitHeightProperty().bind(this.stage.heightProperty().subtract(260));
                iv.fitWidthProperty().bind(this.stage.heightProperty().subtract(260).multiply(4).divide(3));
            } else {
                iv.fitHeightProperty().unbind();
                iv.fitWidthProperty().unbind();
                iv.setFitWidth(0);
                iv.setFitHeight(0);
            }
        } else {
            iv.fitHeightProperty().bind(this.stage.heightProperty().subtract(260).divide(2));
            iv.fitWidthProperty().bind(this.stage.heightProperty().subtract(300).divide(2).multiply(4).divide(3));
        }
    }

    private void displayPhoto(Image image) {
        List<Screen> allScreens = Screen.getScreens();

        if (this.bigStage == null) {
            // create large display window
            this.bigView = new ImageView(image);
            StackPane sp = new StackPane();
            sp.getChildren().addAll(this.bigView);

            if (allScreens.size() > 1) {
                // two screens or more
                Screen secondaryScreen = allScreens.get(1);
                Rectangle2D bounds = secondaryScreen.getVisualBounds();

                this.bigStage = new Stage();
                this.bigStage.setX(bounds.getMinX());
                this.bigStage.setY(bounds.getMinY());
                this.bigStage.setWidth(bounds.getWidth());
                this.bigStage.setHeight(bounds.getHeight());

                this.bigStage.initStyle(StageStyle.UNDECORATED);
                this.bigStage.initModality(Modality.NONE);
                Scene sc = new Scene(sp);
                this.bigStage.setScene(sc);
                this.bigStage.show();

            } else {
                // One screen only
                this.bigStage = new Stage();
                this.bigStage.setFullScreen(true);

                this.bigStage.initStyle(StageStyle.UNDECORATED);
                this.bigStage.initModality(Modality.APPLICATION_MODAL);
                this.bigStage.setFullScreenExitHint("");

                // create a scene with events that will close the stage
                Scene sc = new Scene(sp);
                sc.setOnMouseClicked((e) -> {
                    // one screen : close it
                    this.bigStage.close();
                    this.bigStage = null;
                    e.consume();
                });
                sc.setOnKeyTyped((e) -> {
                    // one screen : close it
                    this.bigStage.close();
                    this.bigStage = null;
                    e.consume();
                });
                this.bigStage.setScene(sc);
                this.bigStage.setFullScreen(true);
                this.bigStage.show();
            }
            this.bigView.fitHeightProperty().bind(this.bigStage.heightProperty());
            this.bigView.fitWidthProperty().bind(this.bigStage.heightProperty().multiply(4).divide(3));
            this.bigStage.show();

        } else {
            // big display is already there
            if (allScreens.size() == 1) {
                // one screen : close it
                this.bigStage.close();
                this.bigStage = null;
            } else {
                // second screen: update image
                this.bigView.setImage(image);
            }
        }
    }

    String channelText(int width, int height, int channel) {
        String result = "" + width + "x" + height + " channel: ";
        switch (channel) {
            case 0:
                result += "A0 (secondary electron image)";
                break;
            case 1:
                result += "A1 (absorbed current image)";
                break;
            case 2:
                result += "A2 (backscatter image 1)";
                break;
            case 3:
                result += "A3 (backscatter image 2)";
                break;
        }

        return result;
    }

    private void selectPane(StackPane sp) {
        if (this.selectedPane != null) {
            this.selectedPane.setBackground(null);
            sp.setBackground(new Background(new BackgroundFill(Color.LIGHTSKYBLUE, CornerRadii.EMPTY, Insets.EMPTY)));
            sp.setBorder(new Border(new BorderStroke(Color.LIGHTSKYBLUE, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderStroke.THIN)));
            this.selectedPane = sp;
        }
    }
    
    
    private void clearImageList() {
        thumbnails.getChildren().clear();
        if (this.bigStage != null) {
            this.bigStage.close();
            this.bigStage = null;
        }
    }
            

    private void addThumbnail(SEMImage si) {
        si.makeImagesForDisplay();
        displayPhoto(si.images[0]);
        StackPane sp = new StackPane();
        for (int i = si.channels - 1; i >= 0; i--) {
            ImageView iv = new ImageView(si.images[i]);
            iv.setFitHeight(150);
            iv.setFitWidth(200);
            iv.setPreserveRatio(false);
            iv.setSmooth(true);
            iv.setTranslateX((si.channels - i) * 8);
            iv.setTranslateY((si.channels - i) * 8);
            Pane p = new Pane(iv);
            p.setPadding(new Insets((si.channels - i) * 8, (si.channels - i) * 8, i * 8, i * 8));
            sp.getChildren().add(p);
        }
        sp.setPrefSize(si.channels * 8 + 208, si.channels * 8 + 158);

        sp.setPadding(new Insets(4, 4, 4, 4));
        sp.setAlignment(Pos.CENTER);
        sp.setOnMouseClicked((e) -> {
            this.isLive = false;
            displayImageSet(si);
            selectPane(sp);
        });

        List t = thumbnails.getChildren();
        t.add(0, sp);
        if (t.size() > 10) {
            t.remove(t.size() - 1);
        }
        //    animateListItem(sp,  si.channels * 8 + 158);

        if (autoSave.isSelected()) {
            saveImageSet(si);
        }

    }

    public void saveFile(SEMImage si) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Image");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            if (!file.getName().toLowerCase().endsWith("png")) {
                // TODO: create new file object with added extension
                file = new File(file.getName() + ".png");
            }

            try {
                ImageIO.write(SwingFXUtils.fromFXImage(si.images[0], null), "png", file);
                Console.println("Image written to " + file.getName());
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }
    }

    private String createFolderName() {
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

        String fileName = "";
        try {
            fileName = "imageset_" + dateFormat.format(date);
        } catch (Exception e) {
        }

        return fileName;
    }

    public static void createFolder(String parent, String folder) {
        File file = new File(parent + folder);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                System.err.println("Folder " + folder + "does not exist and cannot be created");
            }
        }

    }

    public void saveImageSet(SEMImage si) {
        final boolean upload = this.autoUpload.isSelected();
        //String name = createFolderName();


        Thread t = new Thread(() -> {
            for (int i = 0; i < si.channels; i++) {
                File file;
                String fullName;
                try {
                    si.makeImagesForDisplay();

                    fullName = this.session + System.getProperty("file.separator") + "img_" + this.imgCounter + "_channel_" + si.capturedChannels[i] + ".png";
                    file = new File(fullName);
                    ImageIO.write(SwingFXUtils.fromFXImage(si.images[i], null), "png", file);

//                    si.makeImagesForSave();
//                    ImageIO.write(si.grayImages[i], "png", file);
                    Console.println();
                    Console.println("Image written to " + file.getName());
                    if (i == 0 && upload) {
                        FileUpload.uploadFileToServer(fullName);
                    }
                } catch (Exception ex) {
                    System.out.println(ex.getMessage());
                }

            }
            this.imgCounter++;
        });
        t.start();

    }

    String getImageDir() {
        // probably started from other folder
        String path = System.getProperty("user.home");
        path = path.toLowerCase();
        path += System.getProperty("file.separator");
        path += "Documents";
        path += System.getProperty("file.separator");
        path += "SEM Images";
        //createFolder(path);

        path += System.getProperty("file.separator");
        return path;

    }

    /*
          
     */
    private void animateListItem(StackPane sp, int fullHeight) {

        sp.setOpacity(0);
        sp.setPrefHeight(0);

        //create a timeline for growing the item
        Timeline timeline = new Timeline();
        timeline.setCycleCount(501);
        timeline.setAutoReverse(false);

        KeyValue keyValueY = new KeyValue(sp.prefHeightProperty(), fullHeight);

        //create a keyFrame, the keyValue is reached at time 0.5s
        Duration duration = Duration.millis(500);
        KeyFrame keyFrame = new KeyFrame(duration,
                (t) -> {
                    timeline.stop();
                    sp.setPrefHeight(fullHeight);
                    sp.setOpacity(1);
                },
                keyValueY);

        //add the keyframe to the timeline
        timeline.getKeyFrames().add(keyFrame);
        timeline.play();

    }

    // console util functions
    public static void println(String s) {
        if (!printOff) {
            cp.println(s);
        }
    }

    public static void print(String s) {
        if (!printOff) {
            cp.print(s);
        }
    }

    public static void println() {
        if (!printOff) {
            cp.println();
        }
    }

    public static void printOff() {
        printOff = true;
    }

    public static void printOn() {
        printOff = false;
    }

    // main fx launcher
    public static void main(String[] args) {

        //UsbJava.test();
        try {
            launch(args);
        } finally {
            if (semThread != null) {
                semThread.interrupt();
                try {
                    semThread.join();
                } catch (InterruptedException ie) {
                }
            }
        }
    }
}
