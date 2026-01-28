module org.dirchat {
    requires java.xml;
    requires java.sql;
    requires java.desktop;
    requires org.apache.tika.core;
    requires org.apache.opennlp.tools;
    requires com.fasterxml.jackson.databind;
    requires langchain4j.core;
    requires langchain4j.ollama;
    requires org.apache.pdfbox;
    requires org.apache.tika.parser.pdf;
    requires javafx.controls;
    requires javafx.fxml;
    exports org.dirchat.util;
    opens org.dirchat.controller to javafx.controls, javafx.fxml;
    opens org.dirchat.MainApp to javafx.controls, javafx.fxml;
}