/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.semconsolewebapp;
//spark imports

import static spark.Spark.*;
import com.github.sarxos.webcam.Webcam;

//java fx imports for SEM Console
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.LinkedTransferQueue;
import java.util.function.BooleanSupplier;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import static javafx.application.Application.launch;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleGroup;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
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
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 *
 * @author gm et al.
 */
public class Console extends Application {

    private enum DisplayMode {
        NORMAL,
        FFT,
        HISTOGRAM,
        OSCILLOSCOPE
    }

    private static DisplayMode displayMode = DisplayMode.NORMAL;
    private static SEMThread semThread;
    private Pane root;
    private HBox top;
    private int channels;
    private ImageView[] aViews;
    private ImageView bigView;
    private StackPane bigSp;
    private StackPane[] aPanes;
    private StackPane masterPane;
    private VBox left;
    private VBox thumbnails;
    private StackPane right;
    final private LinkedTransferQueue<SEMImage> ltq = new LinkedTransferQueue<>();
    private static SEMImage currentImageSet = null;
    public static SEMImage lastImageSet = null;
    private SEMImage siLeft = null;
    private SEMImage siRight = null;
    private Button btnConnect;
    private Text txt;
    private Scene scene;
    private Stage stage;
    private BorderPane bp;
    private Stage bigStage = null;
    private StackPane selectedPane = null;
    private CheckBox autoUpload = null;
    private String session = null;
    private BooleanSupplier fStereo;
    private BooleanSupplier fStereoLeft;
    private String stereoName = null;
    private String stereoSuffix = null;
    private Session currentSession = null;
    private RadioButton stereoLeft;
    private RadioButton stereoRight;
    private Button stereoButton;
    private ProgressIndicator pin = null;
    private Text metaKV;
    private Text metaMag;
    private Text metaWD;
    private RadioButton[] ch = {null, null, null, null, null};
    private static String operators = "unknown";
    final static public String[] channelNames = {"SEI", "BEI1", "BEI2", "AEI", "All"};

    static boolean testMode = false;
    final ArrayList<SEMImage> testPics = new ArrayList<>();

    static private ConsolePane cp;
    static private boolean printOff = false;

    static public double dNewWeight = 1.0;

    final static private String[] colorScheme1 = {"#accbe1", "#7c98b3", "#536b78", "#cee5f2", "#e2c044"};
    final static private String[] colorScheme2 = {"#536b78", "#7c98b3", "#accbe1", "#cee5f2", "#e2c044"};
    final static private String[] colorScheme3 = {"#2e6266", "#6e8898", "#9fb1bc", "#d3d0cb", "#e2c044"};
    final static private String[] colorScheme4 = {"#d3d0cb", "#9fb1bc", "#6e8898", "#2e6266", "#e2c044"};
    final static private String[] colorScheme = colorScheme4;

    private Thread slideshowThread;
    private Alert slideshowAlert;
    private int slideshowSeconds = 5;
    public static Database db = null;//new Database();

    // main fx launcher
    public static void main(String[] args) {

        //this line calls the function to insert the test images into the database
        //this has to be done anew with each run of the sql script
        //db.initializeTables(operators);
        //DROP SPARK ROUTE HANDLERS HERE
        staticFiles.location("/static");
        get("/getImageData", "application/json", (req, res) -> getImageDataHandler(req), new JSONRT());
        get("/getImage", "application/json", (req, res) -> getImageHandler(req, res), new JSONRT());
        get("/updateVote", "application/json", (req, res) -> updateVotesHandler(req), new JSONRT());
        get("/getImagesByPopularity", "application/json", (req, res) -> getImagesByPopularityHandler(), new JSONRT());
        get("/getImagesByColored", "application/json", (req, res) -> getImagesByColoredHandler(), new JSONRT());
        get("/getImagesByTimestamp", "application/json", (req, res) -> getImagesByTimestampHandler(), new JSONRT());
        get("/setCookie", setCookie); //neither get or set cookie are implemented anymore
        get("/getCookie", getCookie);

        get("/showStream", "application/json", (req, res) -> showStreamHandler(req), new JSONRT());

        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        //Here is where you would have to use sout Webcam.getWebCams(timeoutvalue) to see what webcams are available, then
        //then set Webcam w = the preffered webcam
        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // todo: figure out how this works, this is SamFK's work
//        Webcam w = Webcam.getDefault();//<---------then comment this out
//        new WebcamStreamer(8080, w, 100, true, false);
//
//        Webcam v = Webcam.getDefault();
//        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
//        //This is example setting
//        //to switch on the live view of the sem, flip the last false into a true;
//        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
//        new WebcamStreamer(8079, v, 25, true, false);
        //START OF SEM CODE 
        //ConsolePane.MoveSystemConsole();
        /*
        ArrayList<RemoteDevice> a = Bluetooth.getDevices();
        for (RemoteDevice r : a) {
            try {
                if (r.isTrustedDevice()) {
                    System.out.println("" + r.getFriendlyName(false) + "(" + r.getBluetoothAddress() + ")");
                }
            } catch (IOException ex) {
            }
        }
         */
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

        Runtime.getRuntime()
                .halt(0);
    }

    //Cookie Handler here
    public static Route setCookie = (Request req, Response res) -> { //doesn't run anymore
        String index = req.queryParams("data");

        res.cookie(index, index);//this is the only line that doesn't work, meant to set the cookie with a title of cookie and data of cookie
        return "done";

    };

    public static Route getCookie = (Request req, Response res) -> {
        String index = req.queryParams("var");
        if (index == null) {
            return "false";
        }
        String cookie = req.cookie(index);
        return cookie;
    };

    //INSERT FUNCTIONS FOR REQUEST HANDLING HERE 
    public static Object[] getImagesByTimestampHandler() {
        return db.getIndexesByTimestamp();
    }

    public static Object[] getImagesByColoredHandler() {
        return db.getIndexesByColored();
    }

    public static Object[] getImagesByPopularityHandler() {
        return db.getIndexesByPopularity();
    }

    public static Object getImageHandler(Request req, Response res) {
        String rawIndex = req.queryParams("index");
        int index = Integer.parseInt(rawIndex);
        byte[] data = db.retrieveImage(index);
        //put byte array into a useful response
        //this was snagged from a stack overflow page. 
        //It takes a byte array and converts it into an output stream that gets put into the response, therebye decoding the image.
        HttpServletResponse raw = res.raw();
        res.header("Content-Disposition", "attachment; filename=image.jpg");
        res.type("application/force-download");
        try {
            raw.getOutputStream().write(data);
            raw.getOutputStream().flush();
            raw.getOutputStream().close();
        } catch (Exception e) {

            e.printStackTrace();
        }

        return raw;
    }

    public static String[] getImageDataHandler(Request req) {
        String rawIndex = req.queryParams("index");
        int index = Integer.parseInt(rawIndex);
        String[] data = db.retrieveImageData(index);
        return data;
    }

    public static String[] updateVotesHandler(Request req) {
        String rawIndex = req.queryParams("index");
        String rawIncrement = req.queryParams("increment");
        int index = Integer.parseInt(rawIndex);
        //the updata function calles the get image metadata function and returns that
        //then we generate a new paragraph that has the updated metadata
        if (rawIncrement.compareToIgnoreCase("false") == 0) {
            return db.updateVotes(index, -1);
        } else {
            return db.updateVotes(index, 1);
        }
    }

    public static Object showStreamHandler(Request req) throws InterruptedException {
        Webcam w = Webcam.getDefault();
        new WebcamStreamer(4567, w, 0.5, true, false);
        do {
            Thread.sleep(5000);
        } while (true);
    }

    //SEM CODE FROM MR. MEIN
    @Override
    public void start(Stage primaryStage) {

        // main transfer mechanism with other thread
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
        this.btnConnect = new Button("Connect");
        btnConnect.setOnAction((event) -> startSEMThread());
        btnConnect.setPrefWidth(100);

        txt = new Text("Not connected");
        HBox h = new HBox();
        h.getChildren().add(txt);
        h.setPrefWidth(110);
        h.setPadding(new Insets(6, 12, 6, 12));

        // auto upload checkbox
        autoUpload = new CheckBox("Auto upload");
        HBox h2 = new HBox();
        h2.getChildren().addAll(autoUpload);
        h2.setPadding(new Insets(6, 12, 6, 12));
        autoUpload.setSelected(!testMode);

        // hbox for stereo controls
        HBox stereoLR = new HBox();
        stereoLeft = new RadioButton("Left    ");
        stereoRight = new RadioButton("Right");
        stereoLeft.setDisable(true);
        stereoRight.setDisable(true);

        stereoLeft.setOnAction((e) -> {
            switchStereoState(StereoState.ONLEFT);
        });

        stereoRight.setOnAction((e) -> {
            switchStereoState(StereoState.ONRIGHT);
        });

        this.fStereo = () -> !stereoLeft.isDisabled();
        this.fStereoLeft = () -> stereoLeft.isSelected();

        stereoButton = new Button("Stereo pair");
        stereoButton.setPrefWidth(100);
        stereoButton.setOnAction((e) -> {
            switchStereoState(this.fStereo.getAsBoolean() ? StereoState.CANCEL : StereoState.ONLEFT);
        });

        stereoLR.getChildren().addAll(stereoLeft, stereoRight);
        stereoLR.setPadding(new Insets(6, 12, 6, 12));

        HBox stereoBox = new HBox();
        stereoBox.getChildren().addAll(stereoButton, stereoLR);

        for (int i = 0; i < 5; i++) {
            this.ch[i] = new RadioButton(this.channelNames[i]);
        }

        this.ch[0].setSelected(true);

        for (int i = 0; i < 4; i++) {
            final int j = i;
            this.ch[i].setOnAction((e) -> {

                if (this.ch[j].isSelected()) {
                    for (RadioButton ch : this.ch) {
                        if (ch != this.ch[j]) {
                            ch.setSelected(false);
                        }
                    }
                    SEMThread.channels = (byte) (1 << j);
                } else {
                    SEMThread.channels &= ~(1 << j);
                }
                Console.printOn();
                Console.println("Channels: " + SEMThread.channels);
            });
        }

        this.ch[4].setOnAction((e) -> {
            if (this.ch[4].isSelected()) {
                for (RadioButton ch : this.ch) {
                    if (ch != this.ch[4]) {
                        ch.setSelected(false);
                    }
                }
                SEMThread.channels = (byte) 0xF;
            }
            Console.printOn();
            Console.println("Channels: " + SEMThread.channels);
        });

        VBox res = new VBox();
        res.getChildren().addAll(ch);
        res.setPadding(new Insets(6, 12, 6, 12));

        // metadata
        metaKV = new Text("");
        HBox metab1 = new HBox();

        metab1.getChildren().add(metaKV);
        metab1.setPrefWidth(100);
        metab1.setPadding(new Insets(6, 12, 6, 12));

        metaMag = new Text("");
        HBox metab2 = new HBox();

        metab2.getChildren().add(metaMag);
        metab2.setPrefWidth(100);
        metab2.setPadding(new Insets(6, 12, 6, 12));

        metaWD = new Text("");
        HBox metab3 = new HBox();

        metab3.getChildren().add(metaWD);
        metab3.setPrefWidth(100);
        metab3.setPadding(new Insets(6, 12, 6, 12));

        VBox meta = new VBox();

        meta.getChildren().addAll(metab1, metab2, metab3);
        meta.setPrefWidth(200);

        // slider for cumulative mode
        Slider slider = new Slider();

        slider.valueProperty().addListener((f) -> {
            Console.dNewWeight = slider.getValue();
        });

        slider.setPrefHeight(80);
        slider.setMin(0.05);
        slider.setMax(1.0);
        slider.setValue(1.0);
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);
        slider.setMajorTickUnit(0.1);
        slider.setOrientation(Orientation.VERTICAL);

        // display mode
        ToggleGroup tg = new ToggleGroup();

        RadioButton rbdmNormal = new RadioButton("Normal");
        rbdmNormal.setToggleGroup(tg);
        rbdmNormal.setSelected(true);
        rbdmNormal.setOnAction((e) -> {
            displayMode = DisplayMode.NORMAL;
        });
        RadioButton rbdmOsc = new RadioButton("Oscilloscope");
        rbdmOsc.setToggleGroup(tg);
        rbdmOsc.setOnAction((e) -> {
            displayMode = DisplayMode.OSCILLOSCOPE;
        });

        RadioButton rbdmHist = new RadioButton("Histogram");
        rbdmHist.setToggleGroup(tg);
        rbdmHist.setOnAction((e) -> {
            displayMode = DisplayMode.HISTOGRAM;
        });
        RadioButton rbdmFreq = new RadioButton("Frequencies");
        rbdmFreq.setToggleGroup(tg);
        rbdmFreq.setOnAction((e) -> {
            displayMode = DisplayMode.FFT;
        });

        VBox dm = new VBox();
        dm.getChildren().addAll(rbdmNormal, rbdmOsc, rbdmHist, rbdmFreq);
        dm.setPadding(new Insets(6, 12, 6, 12));

        // snapshot
        Button snap = new Button("Snapshot");
        snap.setPrefSize(100, 100);
        snap.setOnAction((e) -> snapshot());

        // put the whole UI together
        top.setPadding(new Insets(15, 12, 15, 12));
        top.getChildren().addAll(newSession, new Text("  "), btnConnect, h, h2, new Text("  "),
                stereoBox, new Text("  "), res, new Text(" "), meta, new Text(" "),
                dm, new Text(" Weight: "), slider, snap);
        bp.setTop(top);

        // output console
        cp = new ConsolePane();
        cp.setPrefWidth(740);       // determines initial width of unmaximized window

        // display panes
        this.aPanes = new StackPane[4];
        for (int i = 0;
                i < 4; i++) {
            this.aPanes[i] = new StackPane();
            this.aPanes[i].setPadding(new Insets(4, 4, 4, 4));
        }

        this.masterPane = new StackPane();
        //this.masterPane.setPadding(new Insets(8, 8, 8, 8));

        this.left = new VBox();

        this.left.setPadding(new Insets(4, 4, 4, 4));

        this.thumbnails = new VBox();

        this.pin = new ProgressIndicator();

        this.pin.setMaxHeight(400);

        ScrollPane scp = new ScrollPane(thumbnails);

        scp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        scp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        scp.setMinWidth(240);
        scp.setPrefHeight(1000);
        scp.getStyleClass().add("edge-to-edge");

        this.left.getChildren().addAll(/*sp1,*/scp);

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

        /*
        MetaBadge mb = new MetaBadge(25, 100, 39, new String[]{"GM", "RPF"});
        masterPane.getChildren().add(mb);
        masterPane.setAlignment(mb, Pos.BOTTOM_RIGHT);
         */
        vb.setAlignment(Pos.CENTER);

        VBox vb2 = new VBox();

        this.bp.setStyle("-fx-background-color: " + colorScheme[0] + ";");
        scp.setStyle("-fx-background: " + colorScheme[1] + ";");

        this.top.setStyle("-fx-background-color: " + colorScheme[2] + ";");
        stereoBox.setBorder(new Border(new BorderStroke(Color.web(colorScheme[3]), BorderStrokeStyle.SOLID, new CornerRadii(2.0), new BorderWidths(2.0))));

        this.pin.setStyle(/*"-fx-background-color: " + colorScheme[0] +*/";-fx-progress-color: " + colorScheme[4] + ";");

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

        primaryStage.setOnCloseRequest((e) -> {
            if (bigStage != null) {
                bigStage.close();
            }
        });

        if (testMode) {
            Platform.runLater(() -> {
                testModeInit();
            });
        } else {
            Platform.runLater(() -> {
                startNewSession();
            });
        }
    }

    private void testModeInit() {

        this.txt.setText("File test mode active");
        this.session = getImageDir() + "test";
        currentSession = new Session(this.session, this, this.operators);
        // read existing files
        currentSession.readExistingFiles(this.pin, this.ltq, () -> {
            synchronized (ltq) {
                SEMImage si = ltq.peek();
                if (si != null) {
                    if (si.height >= 1500) {
                        Platform.runLater(() -> updateDisplay());
                    } else {
                        ltq.remove();
                        synchronized (testPics) {
                            testPics.add(si);
                        }
                    }
                }
            }
        });
        runLaterAfterDelay(2000, () -> testModePostPic());
    }

    private void testModePostPic() {
        Console.println("Posting test file ...");
        SEMImage si = null;

//        SEMImage si = this.currentSession.loadFile("img-26_channel-0_kv-10_mag-100_wd-15_operators-unknown_.png");
        synchronized (testPics) {
            if (testPics.size() > 0) {
                si = testPics.remove(0);
                testPics.add(si);
            }
        }

        if (si != null) {
            SEMThread.kv = si.kv;
            SEMThread.mag = si.magnification;
            SEMThread.operators = "Test";
            SEMThread.wd = si.wd;
            updateMeta();

            this.ltq.add(si);
            this.channels = 1;
            updateDisplay();
        }

        if (testMode) {
            runLaterAfterDelay(1000, () -> testModePostPic());
        }
    }

    private void startNewSession() {

        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        String sessionName = dateFormat.format(date);

        if (db != null) {
            db.addSession(operators);
        }

        TextInputDialog tid = new TextInputDialog(sessionName);
        tid.setTitle("New session");
        tid.setHeaderText("Enter new session name or 'slideshow [seconds]'");
        Optional<String> result = tid.showAndWait();

        if (result.isPresent()) {
            sessionName = result.get();
            if (sessionName.toLowerCase().startsWith("slideshow")) {
                String arg = sessionName.substring(9).trim();
                if (arg.length() > 0) {
                    try {
                        this.slideshowSeconds = Integer.parseInt(arg);
                    } catch (Exception e) {
                        System.err.println("Invalid length for slideshow");
                    }
                }
                startSlideShow();
                return;
            }
        } else {
            //currentSession = null;   
            sessionName = "default";
        }

        if (sessionName.startsWith("open ")) {
            sessionName = sessionName.substring(5).trim();
            if (sessionName.equalsIgnoreCase("test")) {
                testMode = true;
                testModeInit();
                return;
            }
        } else {
            // create a session for new images to go to 
            createFolder(getImageDir(), sessionName);
        }

        this.session = getImageDir() + sessionName;
        currentSession = new Session(this.session, this, this.operators);

        // read existing files
        if (!sessionName.equalsIgnoreCase("Default")) {
            currentSession.readExistingFiles(this.pin, this.ltq, () -> {
            });
        }
    }

    private void startSlideShow() {
        this.txt.setText("Slideshow active");

        // create a session to get images from 
        createFolder(getImageDir(), "favorites");
        this.session = getImageDir() + "favorites";
        currentSession = new Session(this.session, this, this.operators);

        String[] slideshowFiles = currentSession.gatherFiles();
        if (slideshowFiles.length > 0) {

            // set up a dialog for primary screen
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Slideshow running");
            alert.setHeaderText("The slidesshow you requested is running on the secondary display.");
            alert.setContentText("Select 'Cancel' to end it.");
            ButtonType buttonTypeCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(buttonTypeCancel);

            // start thread for secondary display
            this.slideshowAlert = alert;
            this.slideshowThread = new Thread(() -> slideshowThread(slideshowFiles));
            this.slideshowThread.start();

            // display modal dialog to quit, wait for it, interrupt slideshow thread
            Optional<ButtonType> result = alert.showAndWait();
            this.slideshowThread.interrupt();
        } else {
            // set up a dialog for primary screen
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Slide show");
            alert.setHeaderText("No slides found");
            alert.setContentText("Press ok to continue");

            alert.showAndWait();
        }

        // go back to login dialog
        Platform.runLater(() -> this.startNewSession());
    }

    private void slideshowThread(String[] slideshowFiles) {
        int i = 0;

        // create AWT robot to keep screen alive
        Robot r = null;
        try {
            r = new Robot();
        } catch (Exception e) {
            System.out.println("Could not create keep-alive robot");
            Scanner sc = new Scanner(System.in);
            sc.nextLine();
            return;
        }

        do {
            // get next file name
            String next = slideshowFiles[i++];
            i %= slideshowFiles.length;

            // displayt next picture
            Platform.runLater(() -> {
                Console.println("Next slide: " + next);
                displayPhoto(this.currentSession.loadFile(next));
            });

            // sleep for 5 seconds
            try {
                Thread.sleep(this.slideshowSeconds * 1000);
            } catch (InterruptedException e) {
                Platform.runLater(() -> this.slideshowAlert.close());
                return;
            }

            // keep screen from switching off during daytime
            Calendar c = Calendar.getInstance();
            if (c.get(Calendar.HOUR_OF_DAY) > 8 && c.get(Calendar.HOUR_OF_DAY) < 17) {
                // simulate a shift-key press and release
                r.keyPress(KeyEvent.VK_SHIFT);
                r.keyRelease(KeyEvent.VK_SHIFT);
            } else {
                // time to go away
                Platform.exit();
            }
        } while (!Thread.interrupted());
        Platform.runLater(() -> this.slideshowAlert.close());
    }

    private void displayImageSet(SEMImage si) {
        // handle the null case
        if (si == null) {
            for (int i = 0; i < 4; i++) {
                this.aViews[i].setImage(null);
            }
            return;
        }

        int channels = si.channels;

        // set absent channel images to empty
        if (channels < 4) {
            for (int i = channels; i < 4; i++) {
                this.aViews[i].setImage(null);
            }
        }

        try {
            // parse and create images if we have not done this before
            // pass along the old imageset for cumulative mode
            si.makeImagesForDisplay(Console.lastImageSet);
        } catch (Exception e) {
            System.err.println("makeImages:" + e.getMessage());
            e.printStackTrace(System.err);
        }

        switch (Console.displayMode) {
            case NORMAL:
                si.autoContrast();
                break;
            case FFT:
                si.fft();
                break;
            case HISTOGRAM:
                si.histogram();
                break;
            case OSCILLOSCOPE:
                si.oscilloscope();
                break;
            default:
                System.err.println("Invalid DisplayMode");
                break;
        }

        // record number of displayed channels globally
        this.channels = channels;

        // put the images in place, create metadata badges
        for (int i = 0; i < 4; i++) {
            if (i < channels) {
                this.aViews[i].setImage(si.displayImages[i] != null ? si.displayImages[i] : si.images[i]);
            }

            // remove old meta badge
            if (this.aPanes[i].getChildren().size() > 1) {
                this.aPanes[i].getChildren().remove(1);
            }

            // adjust all of their sizes
            setSizeNormal(aViews[i], i);

            if (i < channels) {
                double compression = si.width / this.aViews[i].getBoundsInLocal().getWidth();
                MetaBadge mb = new MetaBadge(si, si.capturedChannels[i], (si.operators == null ? new String[]{"unknown"} : new String[]{si.operators}), compression);
                StackPane.setAlignment(mb, Pos.BOTTOM_RIGHT);
                this.aPanes[i].getChildren().add(mb);
            }
        }

        Console.lastImageSet = si;
        Console.currentImageSet = si;
    }

    // called by SEMThread, passed as lambda
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
        try {
            for (SEMImage si : newImages) {
                if (si.height < 1500) {
                    this.currentImageSet = si;
                    this.hideProgressIndicator();
                    displayImageSet(this.currentImageSet);
                } else {
                    // for large (photo button) images, display photo on large screen
                    if (!testMode) {
                        displayPhoto(si);
                    }

                    /// and add to session
                    if (currentSession != null) {
                        this.currentSession.saveImageSetAndAdd(si, this.stereoName, this.stereoSuffix, this.autoUpload.isSelected());
                    }
                    // check if we are in the process of taking a stereo pair, and do the right thing
                    // this will also save images to the session
                    this.checkForStereo(si);
                }
            }
        } catch (Exception e) {
            System.err.println("UpdateDisplay:" + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    private void snapshot() {
        if (Console.currentImageSet == null) {
            return;
        }
        displayPhoto(Console.currentImageSet);

        /// and add to session
        if (currentSession != null) {
            this.currentSession.saveImageSetAndAdd(Console.currentImageSet, this.stereoName, this.stereoSuffix, this.autoUpload.isSelected());
        }
        // check if we are in the process of taking a stereo pair, and do the right thing
        // this will also save images to the session
        this.checkForStereo(Console.currentImageSet);

    }

    private void showProgressIndicator() {
        //displayImageSet(null);
        int index = this.masterPane.getChildren().indexOf(this.pin);
        int size = this.masterPane.getChildren().size();

        if (index != size - 1) {
            if (index != -1) {
                this.masterPane.getChildren().remove(index);
            }
            this.masterPane.getChildren().add(this.pin);
        }

        this.pin.setVisible(true);
    }

    private void hideProgressIndicator() {
        int index = this.masterPane.getChildren().indexOf(this.pin);
        if (index != -1) {
            this.masterPane.getChildren().remove(index);
        }
        this.pin.setProgress(0);
        this.pin.setVisible(false);
    }

    private void updateScanning() {
//        System.out.println("Progress: " + this.semThread.progress);
        this.showProgressIndicator();
        this.pin.setProgress(Console.semThread.progress);
    }

    private void updateMeta() {
//        Console.printOn();
//        Console.println("Meta-data: " + SEMThread.kv + "kv, x" + SEMThread.mag + ", WD:" + SEMThread.wd + "mm");
        metaKV.setText("Accelerating Voltage: " + SEMThread.kv + "KV");
        metaMag.setText("Magnification: " + SEMThread.mag + "x");
        metaWD.setText("WD: " + SEMThread.wd + "mm");

    }

    private enum StereoState {
        CANCEL, ONLEFT, ONRIGHT, FINALIZE
    }

    private void checkForStereo(SEMImage si) {
        if (this.stereoName != null) {
            if (this.fStereoLeft.getAsBoolean()) {
                this.siLeft = si;
                if (this.siRight != null) {
                    switchStereoState(StereoState.FINALIZE);
                } else {
                    switchStereoState(StereoState.ONRIGHT);
                }
            } else {
                this.siRight = si;
                if (this.siLeft != null) {
                    switchStereoState(StereoState.FINALIZE);
                } else {
                    switchStereoState(StereoState.ONLEFT);
                }
            }
        }
    }

    private void switchStereoState(StereoState n) {
        switch (n) {
            case CANCEL: // switch stereo mode off, cancel
                stereoLeft.setDisable(true);
                stereoRight.setDisable(true);
                stereoLeft.setSelected(false);
                stereoRight.setSelected(false);
                stereoButton.setText("Stereo Pair");
                this.stereoName = null;
                this.stereoSuffix = null;
                this.siLeft = null;
                this.siRight = null;
                break;
            case ONLEFT: // switch stereo mode on, left
                stereoLeft.setDisable(false);
                stereoRight.setDisable(false);
                stereoLeft.setSelected(true);
                stereoRight.setSelected(false);
                stereoButton.setText("Cancel");
                if (currentSession != null) {
                    this.stereoName = this.currentSession.generatePartialImageName();
                }
                this.stereoSuffix = "L";
                break;
            case ONRIGHT: // switch stereo mode on, right
                stereoLeft.setDisable(false);
                stereoRight.setDisable(false);
                stereoLeft.setSelected(false);
                stereoRight.setSelected(true);
                stereoButton.setText("Cancel");
                this.stereoSuffix = "R";
                break;
            case FINALIZE:
                if (currentSession != null) {
                    this.currentSession.addStereoImage(this.siLeft, this.siRight, this.stereoName, this.autoUpload.isSelected());
                }
                switchStereoState(StereoState.CANCEL);
                break;
            default:
                break;
        }
    }

    private void startSEMThread() {
        testMode = false;
        btnConnect.setDisable(true);
        btnConnect.setTextFill(Color.GRAY);
        this.pin.setProgress(-1);
        this.showProgressIndicator();
        new Thread(() -> {
            // stop any existing SEM thread
            stopSEMThread();

            this.txt.setText("Connecting ...");
            Console.println();
            Console.println("[Console: connecting ...]");

            // and create a new one
            try {
                semThread = new SEMThread(this.ltq, () -> updateDisplay(),
                        () -> SEMThreadStopped(), () -> updateScanning(),
                        () -> updateMeta(),
                        this.operators);
                semThread.start();
                semThread.setPriority(Thread.MAX_PRIORITY);
            } catch (Exception e) {
                System.err.println("Thread start");
                System.out.println(e.getMessage());
            }

            runLaterAfterDelay(3000, () -> startThreadLambda());
        }).start();
    }

    private void startThreadLambda() {
        if (semThread != null && semThread.isAlive()) {
            this.btnConnect.setText("Disconnect");
            this.txt.setText("Connected");
            btnConnect.setOnAction((event) -> disconnect());
        } else {
            semThread = null;
            this.txt.setText("Not connected");
            this.metaKV.setText("");
            this.metaMag.setText("");
            this.metaWD.setText("");
        }
        btnConnect.setDisable(false);
        btnConnect.setTextFill(Color.BLACK);
        this.pin.setProgress(1);
        this.hideProgressIndicator();
    }

    public void runLaterAfterDelay(int ms, Runnable r) {
        final KeyFrame kf1 = new KeyFrame(Duration.millis(ms), (e) -> r.run());
        final Timeline timeline = new Timeline(kf1);
        timeline.play();
    }

    private void stopSEMThread() {

        if (semThread != null) {
            Console.println();

            // stop any existing SEM thread
            this.txt.setText("Stopping ...");
            Console.println("[Console: disconnecting ...]");
            semThread.interrupt();
            try {
                semThread.join();
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
            }
            semThread = null;
            Console.println("[Console: disconnected]");
            this.txt.setText("Not connected");
            this.metaKV.setText("");
            this.metaMag.setText("");
            this.metaWD.setText("");

        }
    }

    private void disconnect() {
        btnConnect.setDisable(true);
        btnConnect.setTextFill(Color.GRAY);
        this.pin.setProgress(-1.0);
        this.showProgressIndicator();
        new Thread(() -> {
            stopSEMThread();
            Platform.runLater(() -> {
                this.hideProgressIndicator();
                btnConnect.setOnAction((event) -> startSEMThread());
                btnConnect.setDisable(false);
                this.btnConnect.setText("Connect");
                btnConnect.setTextFill(Color.BLACK);
            });
        }).start();

    }

    private void SEMThreadStopped() {
        Console.printOn();
        Console.println("[Console: disconnected]");
        this.btnConnect.setText("Connect");
        this.txt.setText("Not connected");
        this.metaKV.setText("");
        this.metaMag.setText("");
        this.metaWD.setText("");

        btnConnect.setOnAction((event) -> startSEMThread());
    }

    private void setSizeNormal(ImageView iv, int channel) {
        switch (this.channels) {
            case 2:
                if (channel < 2) {
                    iv.fitHeightProperty().bind(this.stage.widthProperty().subtract(300).multiply(3).divide(8));
                    iv.fitWidthProperty().bind(this.stage.widthProperty().subtract(300).divide(2));
                } else {
                    iv.fitHeightProperty().unbind();
                    iv.fitWidthProperty().unbind();
                    iv.setFitWidth(0);
                    iv.setFitHeight(0);
                }
                break;
            case 1:
                if (channel < 1) {
                    iv.fitHeightProperty().bind(this.stage.heightProperty().subtract(260));
                    iv.fitWidthProperty().bind(this.stage.heightProperty().subtract(260).multiply(4).divide(3));
                } else {
                    iv.fitHeightProperty().unbind();
                    iv.fitWidthProperty().unbind();
                    iv.setFitWidth(0);
                    iv.setFitHeight(0);
                }
                break;
            default:
                iv.fitHeightProperty().bind(this.stage.heightProperty().subtract(260).divide(2));
                iv.fitWidthProperty().bind(this.stage.heightProperty().subtract(300).divide(2).multiply(4).divide(3));
                break;
        }
    }

    public void displayPhoto(SEMImage si) {

        List<Screen> allScreens = Screen.getScreens();
        MetaBadge mb;
        double compression;
        EventHandler<Event> eh = null;

        si.makeImagesForDisplay(null);

        Image image = si.images[0];

        // create large display window
        this.bigView = new ImageView(image);
        bigView.setSmooth(true);
        this.bigView.setImage(image);
        this.bigSp = new StackPane();

        if (allScreens.size() > 1) {
            // two screens or more
            Screen secondaryScreen = allScreens.get(1);
            Rectangle2D bounds = secondaryScreen.getVisualBounds();

            if (this.bigStage == null) {
                this.bigStage = new Stage();
                this.bigStage.setX(bounds.getMinX());
                this.bigStage.setY(bounds.getMinY());
                this.bigStage.setWidth(bounds.getWidth());
                this.bigStage.setHeight(bounds.getHeight());

                this.bigStage.initStyle(StageStyle.UNDECORATED);
                this.bigStage.initModality(Modality.NONE);
                this.bigStage.setFullScreenExitHint("");
                Scene sc = new Scene(this.bigSp);
                this.bigStage.setScene(sc);
            }

            compression = si.height / bounds.getHeight();
        } else {
            // One screen only
            Screen screen = allScreens.get(0);
            Rectangle2D bounds = screen.getVisualBounds();

            if (this.bigStage == null) {
                this.bigStage = new Stage();
                this.bigStage.setFullScreen(true);

                this.bigStage.initStyle(StageStyle.UNDECORATED);
                this.bigStage.initModality(Modality.APPLICATION_MODAL);
                this.bigStage.setFullScreenExitHint("");
                Scene sc = new Scene(this.bigSp);
                this.bigStage.setScene(sc);
            }

            compression = si.height / bounds.getHeight();

            // create an event that will close the stage
            eh = (e) -> {
                // one screen : close it
                this.bigStage.close();
                this.bigStage = null;
                if (this.slideshowThread != null) {
                    this.slideshowThread.interrupt();
                }
                e.consume();
            };

        }

        // create meta badge and add it and view to pane
        mb = new MetaBadge(si, si.capturedChannels[0], (si.operators == null ? new String[]{"unknown"} : new String[]{si.operators}), compression);
        StackPane.setAlignment(mb, Pos.BOTTOM_RIGHT);
        this.bigSp.getChildren().addAll(this.bigView, mb);

        // squeeze image and adjust meta badge placement based on dimensions
        if (this.bigStage.getWidth() / this.bigStage.getHeight() > si.width / (double) si.height) {
            this.bigView.setFitHeight(this.bigStage.getHeight());
            this.bigView.setFitWidth(this.bigStage.getHeight() / si.height * si.width);
            mb.setTranslateX(((this.bigStage.getHeight() / si.height * si.width) - this.bigStage.getWidth()) / 2);
        } else {
            // todo: should code here for the case that image is wider (in aspect) than screen
        }

        this.bigStage.getScene().setRoot(this.bigSp);
        this.bigStage.getScene().setOnMouseClicked(eh);
        this.bigStage.getScene().setOnKeyTyped(eh);
        this.bigStage.setFullScreen(true);
        this.bigStage.show();
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

    public void addThumbnail(SEMImage si) {
        StackPane sp = new StackPane();
        for (int i = si.channels - 1; i >= 0; i--) {
            ImageView iv;
            iv = new ImageView(si.images[i]);

            iv.setFitHeight(150);
            iv.setFitWidth(200);
            iv.setPreserveRatio(false);
            iv.setSmooth(false);
            iv.setTranslateX((si.channels - i) * 8);
            iv.setTranslateY((si.channels - i) * 8);
            Pane p = new Pane(iv);
            p.setPadding(new Insets((si.channels - i) * 8, (si.channels - i) * 8, i * 8, i * 8));
            sp.getChildren().add(p);
        }
        sp.setPrefSize(si.channels * 8 + 208, si.channels * 8 + 158);

        // now take a snapshot of the whole stack, and put that into a new stackpane, saving lots of memory.
        Image img = sp.snapshot(null, null);
        ImageView iv = new ImageView(img);
        StackPane sp2 = new StackPane(iv);

        // kill the references to all the big images
        //si.dehydrate();
        sp2.setPadding(new Insets(4, 4, 4, 4));
        sp2.setAlignment(Pos.CENTER);
        sp2.setOnMouseClicked((e) -> {
            displayPhoto(si);
            selectPane(sp2);
        });

        List t = thumbnails.getChildren();
        t.add(0, sp2);
//        if (t.size() > 10) {
//            t.remove(t.size() - 1);
//        }
//        animateListItem(sp2,  si.channels * 8 + 158);
    }

    public static BufferedImage getCurrentSEMImage() {
        try {
            return SwingFXUtils.fromFXImage(currentImageSet.images[0], null);
        } catch (Exception ex) {
            System.err.println("Get current image");
            System.out.println(ex.getMessage());
        }
        return null;
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
                System.err.println("image write");
                System.out.println(ex.getMessage());
            }
        }
        //code to insert the image into the SEMDatabase
        //infer false colored.
        db.storeImageInDatabase(file, si.kv, si.magnification, si.wd, false);
    }

    private String createFolderName() {
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

        String fileName = "";
        try {
            fileName = "imageset_" + dateFormat.format(date);
        } catch (Exception e) {
            System.err.println("could not create folder");
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

}
