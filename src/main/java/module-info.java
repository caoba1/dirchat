module org.dirchat {
	// JDK
    requires java.xml;
    requires java.sql;
    requires java.desktop;

    // JPMS
    requires com.fasterxml.jackson.databind;
    requires javafx.controls;
    requires javafx.fxml;

    exports org.dirchat.util;

    opens org.dirchat.controller to javafx.graphics, javafx.fxml;
    opens org.dirchat to javafx.graphics, javafx.fxml;  // MainApp
}