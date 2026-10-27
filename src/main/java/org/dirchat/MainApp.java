/*
 * dirchat — A directory chat application written in Java.
 * A front end for directory querying using network embeddings and
 * question answering on retrieved local documents using language models.
 *
 * Copyright (C) 2026 Carlos de Obaldia Pastor
 *
 * Contact: info@uiloops.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 */
package org.dirchat;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.dirchat.controller.UIController;
import org.dirchat.util.ConfigManager;
import org.dirchat.util.LogMessages;
import org.dirchat.util.Messages;

import java.nio.file.Path;

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
            LogMessages.logWarning("Could not clear previous log files.", e);
        }
    }

    @Override
    public void start( Stage mainStage) throws Exception {
        Messages.setLanguage(ConfigManager.getLanguage());
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/MainView.fxml"),
                Messages.getBundle());
        Scene scene = new Scene(loader.load());

        UIController controller = loader.getController();
        mainStage.setOnCloseRequest( event -> controller.shutdown());

        // Set window title
        mainStage.setTitle(Messages.get("app.window.title"));

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