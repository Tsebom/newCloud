package com.cloud.client;

import com.cloud.common.FileInfo;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
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

        updateFileTable(root.toAbsolutePath().getRoot());

        fileTable.getSortOrder().add(sizeFileColumn);
        fileTable.getSortOrder().add(nameFileColumn);
    }

    public Path getSelectedFileForUpload() {
        return selectedFileForUpload;
    }

    /**
     *
     * @param warning -
     */
    public static void alertWarning(String warning) {
        Alert alert = new Alert(Alert.AlertType.WARNING, warning, ButtonType.OK);
        alert.showAndWait();
    }

    /**
     *
     * @param path -
     */
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

    /**
     *
     * @param actionEvent -
     */
    public void exitAction(ActionEvent actionEvent) {
        if (connect == null) {
            connect = ClientConnect.getInstance();
        }
        connect.getQueue().add("disconnect");
    }

    /**
     *
     * @param actionEvent -
     */
    public void toParentPathAction(ActionEvent actionEvent) {
        Path parent = Paths.get(pathField.getText()).getParent();
        if (parent != null) {
            updateFileTable(parent);
        }
    }

    /**
     *
     * @param actionEvent -
     */
    public void selectDisk(ActionEvent actionEvent) {
        ComboBox<String> disk = (ComboBox<String>) actionEvent.getSource();
        updateFileTable(Paths.get(disk.getSelectionModel().getSelectedItem()));
    }

    /**
     *
     * @param mouseEvent -
     */
    public void selectDirectoryOrFile(MouseEvent mouseEvent) {
        FileInfo fileInfo = (FileInfo)fileTable.getSelectionModel().getSelectedItem();
        if (mouseEvent.getClickCount() == 1) {
            if (fileInfo != null) {
                selected = Paths.get(pathField.getText()).resolve(fileInfo.getFilename());
            }
        } else if (mouseEvent.getClickCount() == 2) {
            if (fileInfo != null) {
                Path path = Paths.get(pathField.getText()).resolve((fileInfo).getFilename());
                if (Files.isDirectory(path)) {
                    updateFileTable(path);
                }
                selected = null;
            }
        }
    }

    /**
     *
     * @param actionEvent -
     */
    public void copyFileOrDir(ActionEvent actionEvent) {
        if (selected != null) {
            selectedFilePathForCopy = selected;
            selected = null;
            logger.info(selectedFilePathForCopy.toString());
        } else {
            alertWarning("No one file was selected");
        }
    }

    /**
     *
     * @param actionEvent -
     */
    public void cutFileOrDir(ActionEvent actionEvent) {
        if (selected != null) {
            selectedFilePathForCut = selected;
            selected = null;
            logger.info(selectedFilePathForCut.toString());
        } else {
            alertWarning("No one file was selected");
        }
    }

    /**
     *
     * @param actionEvent -
     */
    public void pasteFileOrDir(ActionEvent actionEvent) {
        Path path = Paths.get(pathField.getText());

        if (selectedFilePathForCopy != null && selectedFilePathForCut == null) {
            pastCopyFileOrDir(selectedFilePathForCopy, path);
            selectedFilePathForCopy = null;
        } else if (selectedFilePathForCopy == null && selectedFilePathForCut != null) {
            pastCopyFileOrDir(selectedFilePathForCut, path);
            deleteFileOrDir(selectedFilePathForCut);
            selectedFilePathForCut = null;
        } else {
            alertWarning("No one file was selected");
            return;
        }
        updateFileTable(Paths.get(pathField.getText()));
    }

    /**
     *
     * @param source -
     * @param target -
     */
    private void pastCopyFileOrDir(Path source, Path target) {
        if (!Files.isDirectory(source)) {
            copyFile(source, target.resolve(source.getFileName()));
        }else if (Files.isDirectory(source)) {
            copyDirectory(source, target);
        }
    }

    /**
     *
     * @param source -
     * @param target -
     */
    private void copyFile(Path source, Path target) {
        try {
            if (Files.exists(target)) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                        "This file is exist. Do you want to continue",
                        ButtonType.YES, ButtonType.NO);
                Optional<ButtonType> option = alert.showAndWait();
                if (option.get() == ButtonType.YES) {
                    Files.copy(source, target,
                            StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                Files.copy(source, target);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * @param source -
     * @param target -
     */
    private void copyDirectory(Path source, Path target) {
        try {
            target = target.resolve(source.getFileName());
            if (!Files.exists(target)) {
                Files.createDirectory(target);
            }
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
                    if (!Files.exists(t)) {
                        Files.createDirectory(t);
                    }
                } else if (!Files.isDirectory(s)) {
                    logger.info(target.toString());
                    copyFile(s, t);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * @param actionEvent -
     */
    public void deleteCommand(ActionEvent actionEvent) {
        if (selected != null) {
            selectedFilePathForDelete = selected;
            selected = null;
            deleteFileOrDir(selectedFilePathForDelete);
            selectedFilePathForDelete = null;
        } else {
            alertWarning("No one file was selected");
            return;
        }
    }

    /**
     *
     * @param target -
     */
    public void deleteFileOrDir(Path target) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "You are sure",
                ButtonType.YES, ButtonType.CANCEL);
        Optional<ButtonType> option = alert.showAndWait();
        if (option.get() == ButtonType.YES) {
            try {
                if (!Files.isDirectory(target)) {
                    Files.delete(target);
                } else if (Files.isDirectory(target)) {
                    deleteDirectory(target);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        updateFileTable(Paths.get(pathField.getText()));
    }

    /**
     *
     * @param path -
     */
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

    /**
     *
     * @param actionEvent -
     */
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

    /**
     *
     * @param actionEvent -
     */
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

    /**
     *
     */
    private void fillDiskList() {
        disks.getItems().clear();
        for (Path path : FileSystems.getDefault().getRootDirectories()) {
            disks.getItems().add(path.toString());
        }
        disks.getSelectionModel().select(0);
    }

    /**
     *
     * @param actionEvent -
     */
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

    /**
     *
     * @param actionEvent -
     */
    public void downloadFile(ActionEvent actionEvent) {
        if (connect == null) {
            connect = ClientConnect.getInstance();
            connect.setClientController(this);
        }
        connect.getQueue().add("download");
    }

    /**
     *
     * @param path -
     * @return -
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

    /**
     *
     * @param path -
     * @param source -
     * @return -
     */
    private Path truncationPath(Path path, Path source) {
        return  path.subpath(source.getNameCount(), path.getNameCount());
    }
}