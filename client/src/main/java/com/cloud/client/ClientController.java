package com.cloud.client;

import com.cloud.server.FileInfo;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;


import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.channels.SocketChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ClientController implements Initializable {
    private Logger logger = ClientConnect.logger;

    @FXML
    public TextField pathField;
    @FXML
    public ComboBox disks;
    @FXML
    TableView fileTable;

    private static Stage stage;

    private ClientConnect connect;
    private Path root;
    private Path selectedFilePathForCopy;
    private Path selectedFilePathForCut;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        root = Paths.get(".");

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

        fillDiskList();

        updateFileTable(root);

        fileTable.getSortOrder().add(sizeFileColumn);
        fileTable.getSortOrder().add(nameFileColumn);

        Platform.runLater(() -> {
            stage.setOnCloseRequest((event) -> {
                if (connect == null) {
                    connect = ClientConnect.getInstance();
                }
                connect.getQueue().add("disconnect");
            });
        });
    }

    public static void setStage(Stage stage) {
        ClientController.stage = stage;
    }

    public static void alertWarning(String warning) {
        Alert alert = new Alert(Alert.AlertType.WARNING, warning, ButtonType.OK);
        alert.showAndWait();
    }

    public void updateFileTable (Path path) {
        try {
            pathField.setText(path.normalize().toAbsolutePath().toString());
            fileTable.getItems().clear();
            fileTable.getItems().addAll(Files.list(path).map(FileInfo::new).collect(Collectors.toList()));
            fileTable.sort();
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Can not update the files list", ButtonType.OK);
            alert.showAndWait();
        }
    }

    public void exitAction(ActionEvent actionEvent) {
        if (connect == null) {
            connect = ClientConnect.getInstance();
        }
        connect.getQueue().add("disconnect");
    }


    public void toParentPathAction(ActionEvent actionEvent) {
        Path parent = Paths.get(pathField.getText()).getParent();
        if (parent != null) {
            updateFileTable(parent);
        }
    }

    public void selectDisk(ActionEvent actionEvent) {
        ComboBox<String> disk = (ComboBox<String>) actionEvent.getSource();
        updateFileTable(Paths.get(disk.getSelectionModel().getSelectedItem()));
    }

    public void selectDirectory(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 2) {
            Path path = Paths.get(pathField.getText()).resolve(
                    ((FileInfo)fileTable.getSelectionModel().getSelectedItem()).getFilename());
            if (Files.isDirectory(path)) {
                updateFileTable(path);
            }
        }
    }

    public void copyFile(ActionEvent actionEvent) {
        selectedFilePathForCopy = Paths.get(pathField.getText()).resolve(
                ((FileInfo)fileTable.getSelectionModel().getSelectedItem()).getFilename());
        selectedFilePathForCut = null;
        logger.info(selectedFilePathForCopy.toString());
    }

    public void cutFile(ActionEvent actionEvent) {
        selectedFilePathForCut = Paths.get(pathField.getText()).resolve(
                ((FileInfo)fileTable.getSelectionModel().getSelectedItem()).getFilename());
        selectedFilePathForCopy = null;
        logger.info(selectedFilePathForCut.toString());
    }

    public void pasteFile(ActionEvent actionEvent) {
        Path path = Paths.get(pathField.getText());

        if (selectedFilePathForCopy != null && selectedFilePathForCut == null) {
            if (!Files.isDirectory(selectedFilePathForCopy)) {
                try {
                    //if file is exist
                    if (Files.exists(path.resolve(selectedFilePathForCopy.getFileName()))) {
                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                                "This file is exist. Do you want to continue",
                                ButtonType.YES, ButtonType.NO);
                        Optional<ButtonType> option = alert.showAndWait();
                        if (option.get() == ButtonType.YES) {
                            Files.copy(selectedFilePathForCopy, path.resolve(selectedFilePathForCopy.getFileName()),
                                    StandardCopyOption.REPLACE_EXISTING);
                        }
                    } else {
                        Files.copy(selectedFilePathForCopy, path.resolve(selectedFilePathForCopy.getFileName()));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                selectedFilePathForCopy = null;
            }
        } else if (selectedFilePathForCopy == null && selectedFilePathForCut != null) {
            try {
                //if file is exist
                if (Files.exists(path.resolve(selectedFilePathForCut.getFileName()))) {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                            "This file is exist. Do you want to continue",
                            ButtonType.YES, ButtonType.NO);
                    Optional<ButtonType> option = alert.showAndWait();
                    if (option.get() == ButtonType.YES) {
                        Files.copy(selectedFilePathForCut, path.resolve(selectedFilePathForCut.getFileName()),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                } else {
                    Files.copy(selectedFilePathForCut, path.resolve(selectedFilePathForCut.getFileName()));
                    Files.delete(selectedFilePathForCut);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            selectedFilePathForCut = null;
        }
        updateFileTable(Paths.get(pathField.getText()));
    }

    public void deleteFile(ActionEvent actionEvent) {
        selectedFilePathForCopy = Paths.get(pathField.getText()).resolve(
                ((FileInfo)fileTable.getSelectionModel().getSelectedItem()).getFilename());
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "You are sure",
                ButtonType.YES, ButtonType.CANCEL);
        Optional<ButtonType> option = alert.showAndWait();
        if (option.get() == ButtonType.YES) {
            try {
                Files.delete(selectedFilePathForCopy);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        updateFileTable(Paths.get(pathField.getText()));
    }

    public void renameFile(ActionEvent actionEvent) {
        selectedFilePathForCopy = Paths.get(pathField.getText()).resolve(
                ((FileInfo)fileTable.getSelectionModel().getSelectedItem()).getFilename());
        String rename = JOptionPane.showInputDialog("Type the new name");
        File currentName = new File(selectedFilePathForCopy.toString());
        File newName = new File(selectedFilePathForCopy.getParent().resolve(rename).toString());
        if (rename != null && !rename.equals("")) {
            currentName.renameTo(newName);
        }
        updateFileTable(Paths.get(pathField.getText()));
    }

    public void createNewFolderOrFile(ActionEvent actionEvent) {
        String name = JOptionPane.showInputDialog("Type the name folder");
        if (name != null && !name.equals("")) {
            try {
                if (name.contains(".")) {
                    Files.createFile(Paths.get(pathField.getText()).resolve(name));
                }
                else {
                    Files.createDirectory(Paths.get(pathField.getText()).resolve(name));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        updateFileTable(Paths.get(pathField.getText()));
    }

    private void fillDiskList() {
        disks.getItems().clear();
        for (Path path : FileSystems.getDefault().getRootDirectories()) {
            disks.getItems().add(path.toString());
        }
        disks.getSelectionModel().select(0);
    }

    public void upLoad(ActionEvent actionEvent) {
        FileInfo fileInfo = (FileInfo)fileTable.getSelectionModel().getSelectedItem();
        if (connect == null) {
            connect = ClientConnect.getInstance();
            connect.setClientController(this);
        }
        connect.getQueue().add("upload " + fileInfo.getFilename() + " " + fileInfo.getSize());
    }

    public void downloadFile(ActionEvent actionEvent) {
        if (connect == null) {
            connect = ClientConnect.getInstance();
            connect.setClientController(this);
        }
        connect.getQueue().add("download");
    }

//    private List<Path> walkDirectory(Path path) {
//        List<Path> list = new ArrayList<>();
//        try {
//            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
//                @Override
//                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
//                    list.addAll(Files.list(dir).collect(Collectors.toList()));
//                    return FileVisitResult.CONTINUE;
//                }
//            });
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return list;
//    }
}