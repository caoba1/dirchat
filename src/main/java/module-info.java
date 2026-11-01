// dirchat - A chat application
// Copyright (C) 2026 Carlos de Obaldia Pastor
// SPDX-License-Identifier: AGPL-3.0-or-later

module org.dirchat {
	// JDK
    requires java.xml;
    requires java.sql;
    requires java.desktop;

    // JPMS
    requires com.fasterxml.jackson.databind;
    requires javafx.controls;
    requires javafx.fxml;
    requires org.apache.tika.core;
    requires org.apache.tika.parser.pdf;
    requires org.apache.pdfbox;

    exports org.dirchat.util;

    opens org.dirchat.controller to javafx.graphics, javafx.fxml;
    opens org.dirchat to javafx.graphics, javafx.fxml;  // MainApp
}