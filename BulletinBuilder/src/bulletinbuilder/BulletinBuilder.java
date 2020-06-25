/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bulletinbuilder;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;

 

public class BulletinBuilder extends Application {
    
    private Stage theStage;
    private Label lblmasterFilename;
    private FileChooser fileChooser;
    private TextArea log;
    private WebViewPane wvChecking;
    private WebViewPane wvGateway;
    private WebViewPane wvFinal;
    private String projectID = "";
    private String masterFilename = "";
    private File masterFile;
    private String syncFolder = "";
    private String bbuilderPath;
    final String issues = "issues";
    private String publication;
    private Thread buildThread;
    private Preferences prefs;
    private String pubFolder;
    private TabPane tabPane;
    private HBox buttonBox;
    private long lastModified = 0;
    private String defCmd = "build.cmd";  // The command to run when the master file is updated. TODO: instead of hard-coding this, make it the first button.
    

    private static final String APPLICATION_NAME = "Gmail API Java Quickstart";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_LABELS);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = BulletinBuilder.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    public static void rest(String... args) throws IOException, GeneralSecurityException {
        // Build a new authorized API client service.
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Gmail service = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();

        // Print the labels in the user's account.
        String user = "me";
        ListLabelsResponse listResponse = service.users().labels().list(user).execute();
        List<Label> labels = listResponse.getLabels();
        if (labels.isEmpty()) {
            System.out.println("No labels found.");
        } else {
            System.out.println("Labels:");
            for (Label label : labels) {
//                System.out.printf("- %s\n", label.getName());
            }
        }
    }
    
    
    
     private void loadSettings() {
        prefs = Preferences.userRoot().node("SilBulletinbuilder");
        masterFilename = prefs.get("masterFile", "");
    }

     private void saveSettings() {
         prefs.put("masterFile", masterFilename);
    }

     @Override public void start(Stage primaryStage) throws Exception {

        theStage = primaryStage;
        bbuilderPath = System.getProperty("user.dir");
        loadSettings();
        tabPane = new TabPane();

        fileChooser = new FileChooser();
        if (syncFolder.length() > 1) {
            File sf = new File(syncFolder);
            if (sf.isDirectory()) {
                fileChooser.setInitialDirectory(sf);
            }
        }
        fileChooser.setTitle("Select a \"Publication#issue.txt\" file:");
        
        lblmasterFilename = new Label("");
        lblmasterFilename.setStyle("-fx-border-color:black;");
        GridPane.setHgrow(lblmasterFilename, Priority.ALWAYS);
        Button btnOpen = new Button("Select Master File");
        btnOpen.setOnAction(e -> { getMasterFilename(); });

        log = new TextArea();
        log.setPrefRowCount(40);

        buttonBox = new HBox();
        buttonBox.setSpacing(5);

        GridPane grid = new GridPane();
        grid.setVgap(5);
        grid.setHgap(5);
        grid.setPadding(new Insets(5));
        GridPane.setConstraints(lblmasterFilename, 0, 0, 1, 1, HPos.RIGHT, VPos.CENTER, Priority.ALWAYS, Priority.NEVER);
        GridPane.setConstraints(btnOpen,1,0);
        GridPane.setConstraints(buttonBox,0,1,2,1);
        GridPane.setConstraints(log, 0, 2, 2, 1, HPos.LEFT, VPos.CENTER, Priority.ALWAYS, Priority.ALWAYS);
        grid.getColumnConstraints().addAll(
                new ColumnConstraints(100, 800, Double.MAX_VALUE, Priority.ALWAYS, HPos.LEFT, true),
                new ColumnConstraints(110, 110, 110, Priority.NEVER, HPos.RIGHT, true)
        );
        grid.getChildren().addAll(lblmasterFilename, btnOpen, buttonBox, log);
        
        Tab tab1 = new Tab("Dashboard", grid);
        tab1.setClosable(false);
        tabPane.getTabs().add(tab1);

        VBox vBox = new VBox(tabPane);
        Scene scene = new Scene(vBox);

        theStage.setScene(scene);
        theStage.show();

        checkMasterFilename(masterFilename);

        // set up loop to check for updates
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(1500), new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if (lastModified == 0) { return; }
                if (masterFile.lastModified() > lastModified) {
                    lastModified = masterFile.lastModified();
                    log.appendText("Master file has been updated. Building...\n");
                    runCommand(defCmd);
                }
            }
        }));
        timeline.setCycleCount(Animation.INDEFINITE); // loop forever
        timeline.play();
    }

     private void addTab(String tabString) {
        String filename = tabString; // Later, this may have additional parameters to parse
        String fullfilename = pubFolder + "\\" + issues + "\\" + projectID + "\\" + filename;
        File f = new File(fullfilename);
        if (!f.exists()) {
            log.appendText("Load tab: " + filename + " does not yet exist.\n");
        } else {
            WebViewPane w = new WebViewPane(filename);
            Tab t = new Tab(filename, w);
    //        t.setClosable(false);
            tabPane.getTabs().add(t);
        }
     }
     

    private void loadCommandButtons() {
        buttonBox.getChildren().removeAll(buttonBox.getChildren());
        String loadCommandsFN = pubFolder + "\\commands.txt"; 
         try  
        {  
            //the file to be opened for reading  
            FileInputStream fis=new FileInputStream(loadCommandsFN);       
            Scanner sc=new Scanner(fis);    //file to be scanned  
            while(sc.hasNextLine())  
            {  
                String txt = sc.nextLine();    
                txt = txt.replaceAll("//.*", "");  // remove comments
                Matcher m = Pattern.compile("^\\s*(.*?)\\s*=\\s*(.*?)\\s*$").matcher(txt); 
                if (m.find())
                {
                    Button btnCmd = new Button(m.group(1));
                    btnCmd.setOnAction(e -> { runCommand(m.group(2)); });
                    buttonBox.getChildren().add(btnCmd);
                }
            }  
            sc.close();     //closes the scanner  
        }  
        catch(IOException e)  {  e.printStackTrace();  }  
    }
    
     private void closeAllTabs() {
         tabPane.getTabs().remove(1, tabPane.getTabs().size());
     }

     private void closeTabs(String tabnames) {
        tabnames = tabnames.toLowerCase();
        for (int i = tabPane.getTabs().size()-1; i>0; i--) {
           if (tabnames.contains(";" + tabPane.getTabs().get(i).getText().toLowerCase() + ";")) {
               tabPane.getTabs().remove(i);
           }
        }
     }

    private void getMasterFilename() {
        try {
            File selectedFile = fileChooser.showOpenDialog(theStage);
            if (selectedFile != null) {
                checkMasterFilename(selectedFile.getPath());
            }
        } catch (Exception e2) {
        }
    }
    
    private Boolean processMasterFilename(String filename) {
        Matcher m = Pattern.compile("(?i)(.*)[/\\\\]([^/\\\\]+?)\\s*#\\s*([^/\\\\#]+?)\\s*\\.txt$").matcher(filename); 
        if (m.find()) {
            syncFolder = m.group(1);
            publication = m.group(2);
            projectID = m.group(3);
            return true;
        } else {
            syncFolder = "";
            publication = "";
            projectID = "";
            return false;
        }
    }
    
    private void checkMasterFilename(String filename) {

        if (! processMasterFilename(filename)) {
            lastModified = 0;
            lblmasterFilename.setText("Select a master file with name in the format: Publication#issue.txt");
            theStage.setTitle("Bulletin Builder");
            return;
        }
        File sf = new File(syncFolder);
        if (sf.isDirectory()) {
            fileChooser.setInitialDirectory(sf);
        }
        pubFolder = bbuilderPath + "\\Pubs\\" + publication;
        File pf = new File(pubFolder);
        if (! pf.isDirectory()) {
            lblmasterFilename.setText("Publication folder not found: " + pubFolder);
            theStage.setTitle("Bulletin Builder");
            return;
        }
        
        log.setText("");
        lastModified = 0;
        masterFilename = filename;
        theStage.setTitle("Bulletin Builder: " + projectID);
//        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        lblmasterFilename.setText(masterFilename);
//        lblmasterFilename.setText(masterFilename + "    " + sdf.format(mf.lastModified()));
        closeAllTabs();
        loadCommandButtons();
        runCommand("OnOpen.cmd");
        saveSettings();
        masterFile = new File(filename);
        lastModified = masterFile.lastModified();
    }

    private void runCommand(String command) {
//        log.setText("");
        
        buildThread = new Thread(() -> {
            try {
                ProcessBuilder builder = new ProcessBuilder(pubFolder + "\\" + command, projectID, masterFilename);
                //builder.directory(new File("C:/AsiaBulletin-SVN/trunk"));
                Process process = builder.start();
                Consumer<String> display;
                display = a -> { 
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            Matcher m = Pattern.compile("(?i)^\\s*(OPENTAB|CLOSETAB)\\s+(.*\\S)").matcher(a);
                            if (m.find()) {
                                if (m.group(1).equalsIgnoreCase("OPENTAB")) {
                                    addTab(m.group(2));
                                } else {
                                    closeTabs(";" + m.group(2) + ";");
                                }
                            } else {                                
                                String b = a.replaceAll("\\[\\d+m", "");  // Remove DOS shell color codes
                                log.appendText(b + "\n");
                                log.setScrollTop(Double.MAX_VALUE);
                            }
                        }
                    });
                };
                StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream(), display);
                StreamGobbler errGobbler = new StreamGobbler(process.getErrorStream(), display);
                Executors.newSingleThreadExecutor().submit(streamGobbler);
                Executors.newSingleThreadExecutor().submit(errGobbler);
                int exitCode = 0;
                try {
                    exitCode = process.waitFor();
                } catch (InterruptedException ex) {
                    Logger.getLogger(BulletinBuilder.class.getName()).log(Level.SEVERE, null, ex);
                }
                assert exitCode == 0;
            } catch (IOException ex) {
                Logger.getLogger(BulletinBuilder.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        buildThread.start();
    }

    /**
     * The main() method is ignored in correctly deployed JavaFX 
     * application. main() serves only as fallback in case the 
     * application can not be launched through deployment artifacts,
     * e.g., in IDEs with limited FX support. NetBeans ignores main().
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    private static class StreamGobbler implements Runnable {
        private InputStream inputStream;
        private Consumer<String> consumer;

        public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
              .forEach(consumer);
        }
    }
    

    public class WebViewPane extends Pane {
        
        private String thisUrl;
        private String thisPage;
        final WebEngine eng;

        public WebViewPane(String myPage) {
            thisPage = myPage;
            VBox.setVgrow(this, Priority.ALWAYS);
            setMaxWidth(Double.MAX_VALUE);
            setMaxHeight(Double.MAX_VALUE);

            WebView view = new WebView();
            view.setMinSize(50, 100);
            view.setPrefSize(1000, 1000);
            eng = view.getEngine();
            reload();
            //final TextField locationField = new TextField(myUrl);
            //locationField.setMaxHeight(Double.MAX_VALUE);
            Button goButton = new Button("Refresh");
            Button copyButton = new Button("Copy");
            goButton.setDefaultButton(true);
            EventHandler<ActionEvent> goAction = new EventHandler<ActionEvent>() {
                @Override public void handle(ActionEvent event) {
                    reload();
                }
            };
            goButton.setOnAction(goAction);
            EventHandler<ActionEvent> copyAction = new EventHandler<ActionEvent>() {
                @Override public void handle(ActionEvent event) {
                    String fileString = "";
                    try {
                        fileString = new String(Files.readAllBytes(Paths.get(pubFolder + "\\" + issues + "\\" + projectID + "\\" + thisPage)), StandardCharsets.UTF_8);
                    } catch (IOException ex) {
                        Logger.getLogger(BulletinBuilder.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    ClipboardContent content = new ClipboardContent();
                    content.putString(fileString);
//                    content.putHtml("<b>Bold</b> text");
                    Clipboard.getSystemClipboard().setContent(content);
                }
            };
            copyButton.setOnAction(copyAction);
            //locationField.setOnAction(goAction);
            //eng.locationProperty().addListener(new ChangeListener<String>() {
            //    @Override public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
            //        locationField.setText(newValue);
            //    }
            //});
            GridPane grid = new GridPane();
            grid.setVgap(5);
            grid.setHgap(5);
            //GridPane.setConstraints(locationField, 0, 0, 1, 1, HPos.CENTER, VPos.CENTER, Priority.ALWAYS, Priority.SOMETIMES);
            GridPane.setConstraints(goButton,0,0);
            GridPane.setConstraints(copyButton,1,0);
            GridPane.setConstraints(view, 0, 1, 2, 1, HPos.LEFT, VPos.CENTER, Priority.ALWAYS, Priority.ALWAYS);
            grid.getColumnConstraints().addAll(
                    new ColumnConstraints(100, 100, Double.MAX_VALUE, Priority.ALWAYS, HPos.LEFT, true),
                    new ColumnConstraints(100, 100, 100, Priority.NEVER, HPos.RIGHT, true)
            );
            //grid.getChildren().addAll(locationField, goButton, view);
            grid.getChildren().addAll(goButton, copyButton, view);
            getChildren().add(grid);
        }

        @Override protected void layoutChildren() {
            List<Node> managed = getManagedChildren();
            double width = getWidth();
            double height = getHeight();
            double top = getInsets().getTop();
            double right = getInsets().getRight();
            double left = getInsets().getLeft();
            double bottom = getInsets().getBottom();
            for (int i = 0; i < managed.size(); i++) {
                Node child = managed.get(i);
                layoutInArea(child, left, top,
                               width - left - right, height - top - bottom,
                               0, Insets.EMPTY, true, true, HPos.LEFT, VPos.TOP);
            }
        }

        public void reload() {
            String filename = pubFolder + "\\" + issues + "\\" + projectID + "\\" + thisPage;
            File html = new File(filename);
            if (html.exists() ) {
                thisUrl = "file:///" + filename;
                eng.load(thisUrl);
            } else {
                eng.loadContent("<strong>File not found:</strong> " + filename, "text/html");
            }
        }
    }
}
