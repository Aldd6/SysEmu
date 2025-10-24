module com.umg.sysemu {
    requires javafx.controls;
    requires javafx.fxml;

    // Tu clase Application, etc.
    exports com.umg.sysemu;

    // Controladores referenciados con fx:controller en FXML
    opens com.umg.sysemu.UI.Controller to javafx.fxml;

    // Si tienes un GanttView.fxml + GanttController en este paquete:
    exports com.umg.sysemu.UI.Controller;                 // si lo usas por nombre de clase en FXML

    // Si usas TableView con PropertyValueFactory sobre POJOs/records:
    opens com.umg.sysemu.UI.DTO to javafx.base;
}