// Launches the JavaFX application
package org.dirchat;


import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

public class MainApp extends Application {

    public static void main(String[] args) {
        clearLogAndDeleteLckFiles();
        launch(args);
    }

    private static void clearLogAndDeleteLckFiles() {
        try {
            // Truncate dirchat.log
            java.nio.file.Files.write(java.nio.file.Paths.get("dirchat.log"), new byte[0], java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.StandardOpenOption.CREATE);
            // Delete .lck files in current directory
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
    public void start(@NotNull Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/MainView.fxml"));
        Scene scene = new Scene(loader.load());
        primaryStage.setTitle("dirchat - Directory Chat");
        // Set window/taskbar icon
        primaryStage.getIcons().add(new javafx.scene.image.Image(getClass().getResourceAsStream("/icons/dirchat-logo.png")));
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
