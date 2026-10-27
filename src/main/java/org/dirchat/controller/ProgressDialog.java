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

package org.dirchat.controller;

import org.dirchat.util.Messages;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ProgressDialog {
    private final Stage dialogStage;
    private final Label messageLabel;
    private final ProgressBar progressBar;
    private final Label fileLabel;
    private final ProgressBar fileProgressBar;
    private final java.util.ResourceBundle messages = Messages.getBundle();
    private boolean cancelled = false;
    private final Button cancelButton;

    public ProgressDialog(Stage owner, String initialMessage) {
        dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle(messages.getString("indexing.progress.title"));

        messageLabel = new Label(initialMessage);
        fileLabel = new Label("");
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);
        fileProgressBar = new ProgressBar(0);
        fileProgressBar.setPrefWidth(300);


        cancelButton = new Button(messages.getString("indexing.progress.cancel"));
        cancelButton.setOnAction(e -> {
            cancelled = true;
            close();
        });
        VBox vbox = new VBox(10, messageLabel, fileLabel, progressBar, fileProgressBar, cancelButton);
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

    public void setFileName(String fileName) {
        Platform.runLater(() -> fileLabel.setText(fileName));
    }

    public void setFileProgress(double progress) {
        Platform.runLater(() -> fileProgressBar.setProgress(progress));
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
