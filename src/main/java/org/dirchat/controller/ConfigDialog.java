package org.dirchat.controller;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.dirchat.util.ConfigManager;

public class ConfigDialog extends Stage {
    public ConfigDialog( Stage parent) {
        setTitle("dirchat - Configuration");
        initOwner(parent);
        initModality(Modality.APPLICATION_MODAL);

        String[] methods = {"default", "sbert", "ollama"};
        ComboBox<String> methodSelector = new ComboBox<>();
        methodSelector.getItems().addAll(methods);
        methodSelector.setValue(ConfigManager.getIndexingMethod());

        String[] dbTypes = {"sqlite", "file"};
        ComboBox<String> dbTypeSelector = new ComboBox<>();
        dbTypeSelector.getItems().addAll(dbTypes);
        dbTypeSelector.setValue(ConfigManager.getDatabaseType());

        Button saveButton = new Button("Save");
        saveButton.setOnAction(event -> {
            try{
                ConfigManager.setIndexingMethod(methodSelector.getValue());
                ConfigManager.setDatabaseType(dbTypeSelector.getValue());
                close();
            } catch (Exception e) {
                System.err.println("Failed to save configuration: " + e.getMessage());
            }
        });

        VBox root = new VBox(10,
            new Label("Indexing Method:"), methodSelector,
            new Label("Database type:"), dbTypeSelector,
            saveButton
        );
        root.setPadding(new Insets(15));
        setScene(new Scene(root, 300, 250));
    }
}
