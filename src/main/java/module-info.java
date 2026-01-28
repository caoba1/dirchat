module org.dirchat {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.apache.tika.core;
    requires com.fasterxml.jackson.databind;
    requires org.sqlite.jdbc;
    requires langchain4j.core;

    // Export your main packages
    exports org.dirchat.controller;
    exports org.dirchat.llm;
    exports org.dirchat.document;
    exports org.dirchat.embedding;
    exports org.dirchat.util;
}