package com.umg.sysemu.UI.Controller;

import com.umg.sysemu.UI.DTO.Averages;
import com.umg.sysemu.UI.DTO.ProcessRow;
import com.umg.sysemu.UI.DTO.TimelineSlice;
import com.umg.sysemu.kernel.Kernel;
import com.umg.sysemu.kernel.MLTermScheduler;
import com.umg.sysemu.kernel.MainMemory;
import com.umg.sysemu.kernel.VirtualMemory;
import com.umg.sysemu.process.PCB;
import com.umg.sysemu.process.Type;
import com.umg.sysemu.schedulers.*;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.control.Alert.AlertType;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    @FXML private Button btnRun;
    @FXML private Button btnLoadFile;
    @FXML private Button btnCreate;
    @FXML private Button btnSRamApply;
    @FXML private Button btnInit;

    @FXML private Label lblScheduler;
    @FXML private Label lblWaiting;
    @FXML private Label lblCompletion;
    @FXML private Label lblTurnaround;

    @FXML private Label lblLastScheduler;
    @FXML private Label lblLastWaiting;
    @FXML private Label lblLastCompletion;
    @FXML private Label lblLastTurnaround;

    @FXML private Label lblPathJob;

    @FXML private ChoiceBox<String> cmbPolicies;
    @FXML private ChoiceBox<String> cmbType;

    @FXML private TextField txtRRQuantum;
    @FXML private TextField txtMLQSQ;
    @FXML private TextField txtMLQUQ;
    @FXML private TextField txtFSQ;

    @FXML private TextField txtPriority;
    @FXML private TextField txtRam;
    @FXML private TextField txtCpu;
    @FXML private TextField txtUser;
    @FXML private TextField txtSRam;

    @FXML private AnchorPane desktop;
    @FXML private GanttController ganttController;

    @FXML TableView<ProcessRow> tblProcess;
    @FXML TableColumn<ProcessRow, String> cPid;
    @FXML TableColumn<ProcessRow, String> cType;
    @FXML TableColumn<ProcessRow, String> cUser;
    @FXML TableColumn<ProcessRow, String> cArrival;
    @FXML TableColumn<ProcessRow, String> cWaiting;
    @FXML TableColumn<ProcessRow, String> cCompletion;
    @FXML TableColumn<ProcessRow, String> cTurnaround;

    private ObservableList<ProcessRow> processRows = FXCollections.observableArrayList();

    private Kernel krnl;
    private FileChooser file = new FileChooser();
    private String[] processType = {"SYSTEM","USER","BATCH"};
    private String[] schedulingPolicies = {"ROUND ROBIN","FCFS","MULTILEVEL QUEUE","FAIR SHARE"};
    private enum Policy{RR,FCFS,MLQ,FS}
    private Policy actualPolicy;
    private enum Type{SYS,US,BT}
    private Type actualType;
    private int ramSize;
    private String pathToFile;
    private List<PCB> futureJobs = new ArrayList<>();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        cPid.setCellValueFactory(p -> new SimpleStringProperty(String.valueOf(p.getValue().pid())));
        cUser.setCellValueFactory(p -> new SimpleStringProperty(String.valueOf(p.getValue().user())));
        cType.setCellValueFactory(p -> new SimpleStringProperty(String.valueOf(p.getValue().type())));
        cArrival.setCellValueFactory(p -> new SimpleStringProperty(String.valueOf(p.getValue().arrival())));
        cWaiting.setCellValueFactory(p -> new SimpleStringProperty(String.valueOf(p.getValue().waiting())));
        cCompletion.setCellValueFactory(p -> new SimpleStringProperty(String.valueOf(p.getValue().completion())));
        cTurnaround.setCellValueFactory(p -> new SimpleStringProperty(String.valueOf(p.getValue().turnaround())));

        cmbPolicies.getItems().addAll(schedulingPolicies);
        cmbPolicies.setValue(schedulingPolicies[0]);
        actualPolicy = Policy.RR;
        ganttController.setLaneMode(GanttController.LaneMode.PID);
        ganttController.setColorMode(GanttController.ColorMode.PID);
        cmbPolicies.setOnAction(e -> changePolicy());

        txtRRQuantum.setText("0");
        txtMLQSQ.setText("0");
        txtMLQUQ.setText("0");
        txtFSQ.setText("0");

        txtPriority.setText("0");
        txtRam.setText("0");
        txtCpu.setText("0");
        txtUser.setText("NONE");

        txtSRam.setText("0");
        ramSize = 0;

        cmbType.getItems().addAll(processType);
        cmbType.setValue(processType[0]);
        actualType = Type.SYS;
        cmbType.setOnAction(e -> changeType());

        if(ganttController != null) ganttController.setScale(150);

        file.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt")
        );

        btnLoadFile.setOnAction(e -> loadFile());

        btnRun.setDisable(true);
        btnLoadFile.setDisable(true);
        btnCreate.setDisable(true);

        btnInit.setOnAction(e -> init());
        btnCreate.setOnAction(e -> createProcess());
    }

    public void loadFile() {
        Stage primaryStage = (Stage)desktop.getScene().getWindow();

        File path = file.showOpenDialog(primaryStage);
        if(path != null) {
            pathToFile = path.getPath();
            lblPathJob.setText(pathToFile);
            displayInformationDialog(primaryStage,AlertType.INFORMATION,"Archivo de trabajos cargado correctamente al kernel");
        }else {
            pathToFile = null;
            lblPathJob.setText("N/A");
            displayInformationDialog(primaryStage,AlertType.INFORMATION,"No se ha cargado ningun archivo de trabajos");
        }
    }

    public void createProcess() {
        Stage primaryStage = (Stage)desktop.getScene().getWindow();
        if(krnl != null) {
            int priority = Integer.parseInt(txtPriority.getText());
            if(priority < 1) {
                displayInformationDialog(primaryStage,AlertType.ERROR,"Prioridad invalida");
                txtPriority.setText("0");
                return;
            }
            int ram = Integer.parseInt(txtRRQuantum.getText());
            if(ram < 0 || ram > ramSize) {
                displayInformationDialog(primaryStage,AlertType.ERROR,"RAM invalida");
                txtRam.setText("0");
                return;
            }
            int cpu = Integer.parseInt(txtCpu.getText());
            if(cpu < 0 || cpu > 99) {
                displayInformationDialog(primaryStage,AlertType.ERROR,"CPU invalida");
                txtCpu.setText("0");
                return;
            }
            String user = txtUser.getText();
            if(user.isEmpty()) {
                displayInformationDialog(primaryStage,AlertType.ERROR,"Usuario invalida");
                txtUser.setText("NONE");
                return;
            }
            com.umg.sysemu.process.Type pType;
            switch (actualType) {
                case SYS -> pType = com.umg.sysemu.process.Type.SYSTEM;
                case US -> pType = com.umg.sysemu.process.Type.USER;
                case BT -> pType = com.umg.sysemu.process.Type.BATCH;
                default -> pType = com.umg.sysemu.process.Type.SYSTEM;
            }
            PCB p = new PCB(priority,cpu,ram,pType,user);
            futureJobs.add(p);
            txtPriority.setText("0");
            txtRam.setText("0");
            txtCpu.setText("0");
            txtUser.setText("NONE");
            cmbType.setValue(processType[0]);
            displayInformationDialog(primaryStage,AlertType.INFORMATION,"Proceso agregado correctamente");
        }
    }

    public void init() {
        int ram = Integer.parseInt(txtSRam.getText());
        Stage primaryStage = (Stage)desktop.getScene().getWindow();
        if(ram <= 0) {
            displayInformationDialog(primaryStage,AlertType.ERROR,"Se ha ingresado un valor invalido para la RAM, por favor vuelva a intentar");
            return;
        }
        ramSize = ram;
        if(!applyPolicy()) return;
        switch(actualPolicy) {
            case RR -> krnl = new Kernel(
                    () -> new MainMemory(ramSize),
                    () -> new VirtualMemory(),
                    () -> new MLTermScheduler(MLTermScheduler.VictimPolicy.LOW_PRIORITY_FIRST),
                    () -> new RoundRobin(Integer.parseInt(txtRRQuantum.getText()))
            );
            case FCFS -> krnl = new Kernel(
                    () -> new MainMemory(ramSize),
                    () -> new VirtualMemory(),
                    () -> new MLTermScheduler(MLTermScheduler.VictimPolicy.LOW_PRIORITY_FIRST),
                    () -> new FCFS()
            );
            case MLQ -> krnl = new Kernel(
                    () -> new MainMemory(ramSize),
                    () -> new VirtualMemory(),
                    () -> new MLTermScheduler(MLTermScheduler.VictimPolicy.LOW_PRIORITY_FIRST),
                    () -> new MultilevelQueue(Integer.parseInt(txtMLQSQ.getText()),Integer.parseInt(txtMLQUQ.getText()) )
            );
            case FS -> krnl = new Kernel(
                    () -> new MainMemory(ramSize),
                    () -> new VirtualMemory(),
                    () -> new MLTermScheduler(MLTermScheduler.VictimPolicy.LOW_PRIORITY_FIRST),
                    () -> new FairShare(Integer.parseInt(txtFSQ.getText()))
            );
        }
        displayInformationDialog(primaryStage,AlertType.INFORMATION,"Arranque del Kernel exitoso");

        lblLastScheduler.setText(lblScheduler.getText());
        lblLastCompletion.setText(lblCompletion.getText());
        lblLastWaiting.setText(lblWaiting.getText());
        lblLastTurnaround.setText(lblTurnaround.getText());

        lblScheduler.setText(cmbPolicies.getValue());

        btnRun.setDisable(false);
        btnLoadFile.setDisable(false);
        btnCreate.setDisable(false);

        btnRun.setOnAction(e -> run());
        if(!processRows.isEmpty()) processRows.clear();
    }

    public void run() {
        Stage primaryStage = (Stage)desktop.getScene().getWindow();
        if(krnl == null) {
            displayInformationDialog(primaryStage,AlertType.ERROR,"Atencion: el kernel del sistema aun no esta inicializado, por favor intente instanciarlo nuevamente");
            return;
        }

        boolean isFileNotLoad = pathToFile == null;
        boolean thereArentUsProcess = futureJobs.isEmpty();
        if(isFileNotLoad && thereArentUsProcess) {
            displayInformationDialog(primaryStage,AlertType.ERROR,"Por favor seleccione un archivo de texto plano con un pool de trabajos o carguelos manualmente");
            return;
        }
        if(!isFileNotLoad) {
            krnl.loadJobsAtBoot(pathToFile);
        }
        if(!futureJobs.isEmpty()) {
            for(PCB job : futureJobs) {
                krnl.addProcess(job);
            }
            futureJobs.clear();
        }


        while(!krnl.isFinished()) {
            krnl.step();
        }
        Averages avg = krnl.getAverages();
        bindKernel(krnl);

        fillProcessTable(krnl.getProcessTable());

        lblWaiting.setText(Double.toString(avg.avgWaiting()));
        lblTurnaround.setText(Double.toString(avg.avgTurnaround()));
        lblCompletion.setText(Double.toString(avg.avgResponse()));

        krnl.reset();
        displayInformationDialog(primaryStage,AlertType.INFORMATION,"Se ha completado la simulacion correctamente");
    }

    public boolean applyPolicy() {
        Stage primaryStage = (Stage) desktop.getScene().getWindow();
        switch (actualPolicy) {
            case RR:
                int q = Integer.parseInt(txtRRQuantum.getText());
                if(q <= 0){
                    displayInformationDialog(primaryStage, AlertType.ERROR,"Por favor seleccione un valor de quantum valido para el algoritmo Round Robin");
                    return false;
                }
                return true;
            case MLQ:
                int q2 = Integer.parseInt(txtMLQSQ.getText());
                int q3 = Integer.parseInt(txtMLQUQ.getText());

                boolean f1 = true;
                boolean f2 = true;
                if(q2 <= 0){
                    displayInformationDialog(primaryStage, AlertType.ERROR,"Por favor seleccione un valor de quantum de sistema valido para el algoritmo Multilevel Queue");
                    f1 = false;
                }
                if(q3 <= 0){
                    displayInformationDialog(primaryStage, AlertType.ERROR,"Por favor seleccione un valor de quantum de usuario valido para el algoritmo Multilevel Queue");
                    f2 = false;
                }
                return f1 && f2;
            case FS:
                int q4 = Integer.parseInt(txtFSQ.getText());
                if(q4 <= 0){
                    displayInformationDialog(primaryStage, AlertType.ERROR,"Por favor seleccione un valor de quantum valido para el algoritmo Fair Share");
                    return false;
                }
                return true;
            case FCFS: return true;
            default: return false;
        }
    }

    public void changePolicy() {
        switch (cmbPolicies.getValue()) {
            case "ROUND ROBIN" -> {
                actualPolicy = Policy.RR;
                System.out.println(actualPolicy);
                ganttController.setLaneMode(GanttController.LaneMode.PID);
                ganttController.setColorMode(GanttController.ColorMode.PID);
            }
            case "FCFS" -> {
                actualPolicy = Policy.FCFS;
                System.out.println(actualPolicy);
                ganttController.setLaneMode(GanttController.LaneMode.PID);
                ganttController.setColorMode(GanttController.ColorMode.PID);
            }
            case "MULTILEVEL QUEUE" -> {
                actualPolicy = Policy.MLQ;
                System.out.println(actualPolicy);
                ganttController.setLaneMode(GanttController.LaneMode.LANE);
                ganttController.setColorMode(GanttController.ColorMode.LANE);
            }
            case "FAIR SHARE" -> {
                actualPolicy = Policy.FS; System.out.println(actualPolicy);
                ganttController.setLaneMode(GanttController.LaneMode.LANE);
                ganttController.setColorMode(GanttController.ColorMode.LANE);
            }
        }
    }

    public void changeType() {
        switch (cmbType.getValue()) {
            case "SYSTEM" -> {actualType = Type.SYS; System.out.println(actualType);}
            case "USER" -> {actualType = Type.US; System.out.println(actualType);}
            case "BATCH" -> {actualType = Type.BT; System.out.println(actualType);}
        }
    }

    private void displayInformationDialog(Stage primaryStage, Alert.AlertType type, String info) {
        Alert alert;
        switch(type) {
            case INFORMATION:
                alert = new Alert(Alert.AlertType.INFORMATION);
                alert.initOwner(primaryStage);
                alert.setTitle("Information");
                alert.setHeaderText("Atención");
                alert.setContentText(info);
                alert.showAndWait();
                break;
            case ERROR:
                alert = new Alert(Alert.AlertType.ERROR);
                alert.initOwner(primaryStage);
                alert.setTitle("Error");
                alert.setHeaderText("Atención");
                alert.setContentText(info);
                alert.showAndWait();
                break;

        }
    }

    private void bindKernel(Kernel krnl) {
        ganttController.setData(krnl.getTimeline());
    }

    private void fillProcessTable(List<ProcessRow> rows) {
        processRows.addAll(rows);
        tblProcess.setItems(processRows);
        tblProcess.refresh();
    }

}
