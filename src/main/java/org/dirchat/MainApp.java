// Launches the JavaFX application
package org.dirchat;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.dirchat.controller.UIController;

import java.nio.file.Path;
import java.util.ResourceBundle;

public class MainApp extends Application {

    public static void main(String[] args) {
        clearLogAndDeleteLckFiles();    // Reset log. Logs are cleared before each run (!)
        launch(args);                   // Start JavaFX application
    }

    private static void clearLogAndDeleteLckFiles() {
        try {
            // Truncate dirchat.log
            Path logFile = java.nio.file.Paths.get("dirchat.log");
            java.nio.file.Files.write( logFile,
                    new byte[0],
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                    java.nio.file.StandardOpenOption.CREATE);

            // Delete .lck files in current directory. Created after each run by Log4j2
            java.io.File cwd = new java.io.File(".");
            java.io.File[] lckFiles = cwd.listFiles((dir, name) -> name.endsWith(".lck"));
            if (lckFiles != null) {
                for (java.io.File f : lckFiles) {
                    f.delete();
                }
            }
        } catch (Exception e) {
            org.dirchat.util.LogMessages.logWarning(e.getMessage());
            System.err.println("Failed to clear log or delete .lck files: " + e.getMessage());
        }
    }

    @Override
    public void start( Stage mainStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/MainView.fxml"));
        Scene scene = new Scene(loader.load());

        UIController controller = loader.getController();
        mainStage.setOnCloseRequest( event -> controller.shutdown());

        // Set window title
        String windowTitle = ResourceBundle.getBundle("messages").getString("window.title").trim();
        mainStage.setTitle(windowTitle);

        // Set window/taskbar icon
        var runtimeClass = getClass();
        Image dirchatLogo = new Image(runtimeClass.getResourceAsStream("/icons/dirchat-logo.png"));
        mainStage.getIcons().add( dirchatLogo);
        mainStage.setScene(scene);

        // Set window size to 3/5 of screen width, maintain aspect ratio
        javafx.geometry.Rectangle2D screenBounds = javafx.stage.Screen.getPrimary().getBounds();
        double screenWidth = screenBounds.getWidth();
        double screenHeight = screenBounds.getHeight();
        double aspectRatio = screenWidth / screenHeight;
        double windowWidth = screenWidth * 0.6;
        double windowHeight = windowWidth / aspectRatio;
        mainStage.setWidth(windowWidth);
        mainStage.setHeight(windowHeight);
        mainStage.centerOnScreen();

        // Set bright background color for the scene
        scene.getRoot().setStyle("-fx-background-color: #f8fbff;");

        mainStage.show();


    }
}