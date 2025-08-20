package org.dirchat.controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ProgressDialog {
    private final Stage dialogStage;
    private final Label messageLabel;
    private final ProgressBar progressBar;

    public ProgressDialog(Stage owner, String initialMessage) {
        dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("Progress");

        messageLabel = new Label(initialMessage);
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);

        VBox vbox = new VBox(10, messageLabel, progressBar);
        vbox.setPadding(new Insets(20));
        Scene scene = new Scene(vbox);
        dialogStage.setScene(scene);
    }

    public void show() {
        Platform.runLater(dialogStage::show);
    }

    public void close() {
        Platform.runLater(dialogStage::close);
    }

    public void setMessage(String message) {
        Platform.runLater(() -> messageLabel.setText(message));
    }

    public void setProgress(double progress) {
        Platform.runLater(() -> progressBar.setProgress(progress));
    }
}

