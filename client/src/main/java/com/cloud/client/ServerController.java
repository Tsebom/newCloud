package com.cloud.client;

import com.cloud.server.FileInfo;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javax.swing.*;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Logger;

public class ServerController implements Initializable {
    private Logger logger = ClientConnect.logger;

    private static final int PORT = 5679;
    private static final String IP_ADRESS = "localhost";
    private static Stage stage;

    private ClientConnect connect;
    private Path root;

    @FXML
    public TextField pathField;
    @FXML
    public VBox manager_box;
    @FXML
    public VBox auth_box;
    @FXML
    public Button sign_in;
    @FXML
    public Button sign_up;
    @FXML
    public Button registration;
    @FXML
    public Button back_sign_in;
    @FXML
    public TextField loginField;
    @FXML
    public PasswordField passwordField;
    @FXML
    public TableView fileTable;

    private boolean isRegistration = true;
    private boolean isTryRegistration = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        TableColumn<FileInfo, String> nameFileColumn = new TableColumn<>("Name");
        nameFileColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getFilename()));
        nameFileColumn.setPrefWidth(150);

        TableColumn<FileInfo, Long> sizeFileColumn = new TableColumn<>("Size");
        sizeFileColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getSize()));
        sizeFileColumn.setPrefWidth(150);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        TableColumn<FileInfo, String> dateFileColumn = new TableColumn<>("Modified");
        dateFileColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getLastTimeModify()
                .format(dtf)));
        dateFileColumn.setPrefWidth(150);

        fileTable.getColumns().addAll(nameFileColumn, sizeFileColumn, dateFileColumn);

        sizeFileColumn.setCellFactory(column -> {
            return new TableCell<FileInfo, Long>() {
                @Override
                protected void updateItem(Long item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item == null || empty) {
                        setText(null);
                        setStyle("");
                    } else {
                        String text = String.format("%,d bytes", item);
                        if (item == -1L) {
                            text = "[DIR]";
                        }
                        setText(text);
                    }
                }
            };
        });

        fileTable.getSortOrder().add(sizeFileColumn);
        fileTable.getSortOrder().add(nameFileColumn);
    }

    public static void setStage(Stage stage) {
        ServerController.stage = stage;
    }

    public boolean isRegistration() {
        return isRegistration;
    }

    public void signUp(ActionEvent actionEvent) {
        regOrAuth(isTryRegistration);
        setTitle("Cloud Registration");
    }

    public void registration(ActionEvent actionEvent) {
        connect = ClientConnect.getInstance();
        connect.setNameUser(loginField.getText());
        connect.setServerController(this);
        connect.getQueue().add("reg ".concat(loginField.getText().trim() + " ")
                .concat(passwordField.getText().trim()));
    }

    public void signIn(ActionEvent actionEvent) {
        connect = ClientConnect.getInstance();
        connect.setNameUser(loginField.getText());
        connect.setServerController(this);
        connect.getQueue().add("auth ".concat(loginField.getText().trim() + " ")
                .concat(passwordField.getText().trim()));
    }

    public void setTitle(String title) {
        Platform.runLater(() -> {
            stage.setTitle(title);
        });
    }

    protected void switchServerWindow(boolean isRegistration) {
        auth_box.setVisible(!isRegistration);
        auth_box.setManaged(!isRegistration);
        manager_box.setVisible(isRegistration);
        manager_box.setManaged(isRegistration);
        this.isRegistration = !isRegistration;
    }

    public void updateFileTable(List<FileInfo> list) {
            fileTable.getItems().clear();
            fileTable.getItems().addAll(list);
            fileTable.sort();
    }

    private void regOrAuth (boolean isTryRegistration) {
        sign_in.setVisible(isTryRegistration);
        sign_in.setManaged(isTryRegistration);
        sign_up.setVisible(isTryRegistration);
        sign_up.setManaged(isTryRegistration);
        registration.setVisible(!isTryRegistration);
        registration.setManaged(!isTryRegistration);
        back_sign_in.setVisible(!isTryRegistration);
        back_sign_in.setManaged(!isTryRegistration);
        this.isTryRegistration = !isTryRegistration;
    }

    public void createNewFolderOrFile(ActionEvent actionEvent) {
        String name = JOptionPane.showInputDialog("Type the name folder");
        if (name != null && !name.equals("")) {
            if (name.contains(".")) {
                connect.getQueue().add("create_file ".concat(name));
            }
            else {
                connect.getQueue().add("create_dir ".concat(name));
            }
        }
    }

    public void selectDirectory(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 2) {
            connect.getQueue().add("doubleclick ".concat(
                    ((FileInfo)fileTable.getSelectionModel().getSelectedItem()).getFilename()));
        }
    }

    public void toParentPathAction(ActionEvent actionEvent) {
        connect.getQueue().add("back");
    }
}
