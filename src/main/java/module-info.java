module com.umg.sysemu {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.kordamp.bootstrapfx.core;

    opens com.umg.sysemu to javafx.fxml;
    exports com.umg.sysemu;
}