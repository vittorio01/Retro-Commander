module retrocommander.retrocommander {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires com.fazecast.jSerialComm;

    opens retrocommander.retrocommander to javafx.fxml;
    opens retrocommander.disk_creator to javafx.fxml;
    exports retrocommander.retrocommander;
}