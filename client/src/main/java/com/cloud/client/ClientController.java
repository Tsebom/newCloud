package com.cloud.client;

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
import java.util.stream.Collectors;

public class ClientController implements Initializable {
    @FXML
    public TextField pathField;
    @FXML
    public ComboBox disks;
    @FXML
    TableView fileTable;

    private Path selectedFilePathForCopy;

    private Stage clientStage;
    private RegController regController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Path path = Paths.get(".");

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

        updateFileTable(path);

        fileTable.getSortOrder().add(sizeFileColumn);
        fileTable.getSortOrder().add(nameFileColumn);
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
        Platform.exit();
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
    }

    public short pasteFile(ActionEvent actionEvent) {
        Path path = Paths.get(pathField.getText());

        if (!Files.isDirectory(selectedFilePathForCopy)) {
            try {
                //if file is exist
                if (Files.exists(path.resolve(selectedFilePathForCopy.getFileName()))) {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                            "This file is exist. Do you want to continue", ButtonType.APPLY, ButtonType.CANCEL);
                    Optional<ButtonType> option = alert.showAndWait();
                    if (option.get() == ButtonType.APPLY) {
                        Files.copy(selectedFilePathForCopy, path.resolve(selectedFilePathForCopy.getFileName()),
                                StandardCopyOption.REPLACE_EXISTING);
                        return 0;
                    }
                    if (option.get() == ButtonType.CANCEL) {
                        return -1;
                    }
                }
                Files.copy(selectedFilePathForCopy, path.resolve(selectedFilePathForCopy.getFileName()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        updateFileTable(Paths.get(pathField.getText()));
        return 1;
    }

    public void moveFile(ActionEvent actionEvent) {
        if (!pathField.getText().startsWith(selectedFilePathForCopy.toString())) {
            short i = pasteFile(actionEvent);
            try {
                if (i >= 0) {
                    Files.delete(selectedFilePathForCopy);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        Alert alert = new Alert(Alert.AlertType.WARNING, "You can't made move command to the folder "
                .concat(selectedFilePathForCopy.getFileName().toString()), ButtonType.OK);
        alert.showAndWait();
    }

    public void deleteFile(ActionEvent actionEvent) {
        selectedFilePathForCopy = Paths.get(pathField.getText()).resolve(
                ((FileInfo)fileTable.getSelectionModel().getSelectedItem()).getFilename());
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "You are sure", ButtonType.YES,
                ButtonType.CANCEL);
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