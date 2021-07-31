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
import java.nio.file.attribute.FileAttribute;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
    private Path selected;
    private Path selectedFilePathForDelete;
    private Path selectedFilePathForCopy;
    private Path selectedFilePathForCut;
    private Path selectedFileForUpload;
    private Path selectedFilePathForRename;

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
    }

    public Path getSelectedFileForUpload() {
        return selectedFileForUpload;
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

    public void selectDirectoryOrFile(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 1) {
            selected = Paths.get(pathField.getText()).resolve(
                    ((FileInfo)fileTable.getSelectionModel().getSelectedItem()).getFilename());
        } else if (mouseEvent.getClickCount() == 2) {
            Path path = Paths.get(pathField.getText()).resolve(
                    ((FileInfo)fileTable.getSelectionModel().getSelectedItem()).getFilename());
            if (Files.isDirectory(path)) {
                updateFileTable(path);
            }
            selected = null;
        }
    }

    public void copyFileOrDir(ActionEvent actionEvent) {
        if (selected != null) {
            selectedFilePathForCopy = selected;
            selected = null;
            logger.info(selectedFilePathForCopy.toString());
        } else {
            alertWarning("No one file was selected");
        }
    }

    public void cutFileOrDir(ActionEvent actionEvent) {
        if (selected != null) {
            selectedFilePathForCut = selected;
            selected = null;
            logger.info(selectedFilePathForCut.toString());
        } else {
            alertWarning("No one file was selected");
        }
    }

    public void pasteFileOrDir(ActionEvent actionEvent) {
        Path path = Paths.get(pathField.getText());

        if (selectedFilePathForCopy != null && selectedFilePathForCut == null) {
            pastCopyFileOrDir(path);
        } else if (selectedFilePathForCopy == null && selectedFilePathForCut != null) {
            pastCutFileOrDir(path);
        } else {
            alertWarning("No one file was selected");
            return;
        }
        updateFileTable(Paths.get(pathField.getText()));
    }

    private void pastCutFileOrDir(Path path) {
        try {
            if (!Files.isDirectory(selectedFilePathForCut)) {
                copyFile(selectedFilePathForCopy, path);
                Files.delete(selectedFilePathForCut);
            }else if (Files.isDirectory(selectedFilePathForCut)) {
                copyDirectory(selectedFilePathForCut, path);
                deleteDirectory(selectedFilePathForCut);
            }
            selectedFilePathForCut = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void pastCopyFileOrDir(Path path) {
        if (!Files.isDirectory(selectedFilePathForCopy)) {
            copyFile(selectedFilePathForCopy, path);
        }else if (Files.isDirectory(selectedFilePathForCopy)) {
            copyDirectory(selectedFilePathForCopy, path);
        }
        selectedFilePathForCopy = null;
    }

    private void copyFile(Path source, Path target) {
        try {
            if (Files.exists(target.resolve(source.getFileName()))) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                        "This file is exist. Do you want to continue",
                        ButtonType.YES, ButtonType.NO);
                Optional<ButtonType> option = alert.showAndWait();
                if (option.get() == ButtonType.YES) {
                    Files.copy(source, target.resolve(source.getFileName()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                Files.copy(source, target.resolve(source.getFileName()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void copyDirectory(Path source, Path target) {
        try {
            target = target.resolve(source.getFileName());
            Files.createDirectory(target);
            List<Path> list = walkDirectory(source);
            logger.info(list.toString());
            if (list.isEmpty()) {
                return;
            }
            Collections.sort(list);

            for (Path p : list) {
                Path s = source.resolve(p);
                Path t = target.resolve(p);
                if (Files.isDirectory(s)) {
                    Files.createDirectory(t);
                } else if (!Files.isDirectory(s)) {
                    Files.createFile(t);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deleteFileOrDir(ActionEvent actionEvent) {
        if (selected != null) {
            selectedFilePathForDelete = selected;
            selected = null;

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "You are sure",
                    ButtonType.YES, ButtonType.CANCEL);
            Optional<ButtonType> option = alert.showAndWait();
            if (option.get() == ButtonType.YES) {
                try {
                    if (!Files.isDirectory(selectedFilePathForDelete)) {
                        Files.delete(selectedFilePathForDelete);
                    } else if (Files.isDirectory(selectedFilePathForDelete)) {
                        deleteDirectory(selectedFilePathForDelete);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        } else {
            alertWarning("No one file was selected");
            return;
        }
        selectedFilePathForDelete = null;
        updateFileTable(Paths.get(pathField.getText()));
    }

    private void deleteDirectory(Path path) {
        try {
            List<Path> list = walkDirectory(path);

            if (!list.isEmpty()) {
                Collections.sort(list);
                Collections.reverse(list);
            } else {
                Files.delete(path);
                return;
            }

            while (!list.isEmpty()) {
                Iterator<Path> iterator = list.iterator();
                while (iterator.hasNext()) {
                    Path p = iterator.next();
                    Path t = path.resolve(p);
                    if (!Files.isDirectory(t)) {
                        Files.delete(t);
                    } else if (Files.isDirectory(t) && walkDirectory(t).isEmpty()) {
                        Files.delete(t);
                    }
                    iterator.remove();
                    list.remove(p);
                }
            }
            Files.delete(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void renameFile(ActionEvent actionEvent) {
        if (selected != null) {
            selectedFilePathForRename = selected;
            selected = null;

            String rename = JOptionPane.showInputDialog("Type a new name of the file");
            File currentName = new File(selectedFilePathForRename.toString());
            File newName = new File(selectedFilePathForRename.getParent().resolve(rename).toString());
            if (rename != null && !rename.equals("")) {
                currentName.renameTo(newName);
            }
            selectedFilePathForRename = null;
            updateFileTable(Paths.get(pathField.getText()));
        } else {
            alertWarning("No one file was selected");
        }
    }

    public void createNewFolderOrFile(ActionEvent actionEvent) {
        String name = JOptionPane.showInputDialog("Type a name folder");
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

    public void uploadFile(ActionEvent actionEvent) {
        if (selected != null) {
            FileInfo fileInfo = (FileInfo)fileTable.getSelectionModel().getSelectedItem();
            selectedFileForUpload = selected;
            selected = null;
            if (connect == null) {
                connect = ClientConnect.getInstance();
                connect.setClientController(this);
            }
            connect.getQueue().add("upload " + fileInfo.getFilename() + " " + fileInfo.getSize());
        } else {
            alertWarning("No one file was selected");
        }
    }

    public void downloadFile(ActionEvent actionEvent) {
        if (connect == null) {
            connect = ClientConnect.getInstance();
            connect.setClientController(this);
        }
        if (fileTable.getSelectionModel().getSelectedItem() != null) {
            connect.getQueue().add("download");
        }
    }

    /**
     *
     * @param path
     * @return
     */
    private List<Path> walkDirectory(Path path) {
        List<Path> list = new ArrayList<>();
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    list.addAll(Files.list(dir).map(p -> truncationPath(p, path)).
                            filter(p -> p != null).collect(Collectors.toList()));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }

    private Path truncationPath(Path path, Path source) {
        return  path.subpath(source.getNameCount(), path.getNameCount());

    }
}