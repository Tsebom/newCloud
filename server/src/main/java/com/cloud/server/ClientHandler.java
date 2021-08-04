package com.cloud.server;

import com.cloud.common.FileInfo;

import java.io.*;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * The ClientHandler implements the processing of commands from users
 */
public class ClientHandler {
    private String userName;

    private Server server;
    private SelectionKey key;
    private SocketChannel channel;
    private Selector selector;
    private SocketAddress clientAddress;

    private Logger logger = Server.logger;

    private Path root = Paths.get("./server");
    private Path currentPath;
    private Path selectFileForCopy;
    private Path selectFileForCut;

    public ClientHandler(Server server, SelectionKey key, SocketChannel channel, String userName) {
        this.userName = userName;
        this.server = server;
        this.key = key;
        this.channel = channel;

        selector = key.selector();
        currentPath = root.resolve("users").resolve(userName);
        try {
            clientAddress = channel.getRemoteAddress();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("create ClientHandler: " + clientAddress);
    }

    /**
     * Read data from channel
     */
    public void read() {
        if (channel.isOpen()) {
            logger.info("the start reading data from the channel: " + clientAddress);
            ByteBuffer buf = (ByteBuffer) key.attachment();
            try {
                int bytesRead = channel.read(buf);
                if (bytesRead < 0 || bytesRead == 0) {
                    server.getProcessing().remove(clientAddress);
                    return;
                }
                buf.flip();
                StringBuilder sb = new StringBuilder();
                while (buf.hasRemaining()) {
                    sb.append((char) buf.get());
                }
                buf.clear();
                logger.info("the end read data from the channel: " + clientAddress);
                processing(sb.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Reading the data when user send file to server
     * @param pathFile - the path to file where data writing from channel
     * @param sizeFile - the size of file
     */
    private void readFile(Path pathFile, long sizeFile) {
        logger.info("start download file");
        try {
            server.getProcessing().add(clientAddress);
            RandomAccessFile file = new RandomAccessFile(pathFile.toString(), "rw");
            FileChannel fileChannel = file.getChannel();
            ByteBuffer buff = (ByteBuffer) key.attachment();
            long size = 0L;
            buff.clear();
            while (size < sizeFile) {

                size += channel.read(buff);

                buff.flip();
                while (buff.hasRemaining()) {
                    fileChannel.write(buff);
                }
                buff.compact();
            }
            serializeBeforeSendData("ok");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Writing the data to the channel
     * @param data - the data for writing
     */
    private void  write(byte[] data) {
        ByteBuffer buff = (ByteBuffer) key.attachment();
        int i = 0;
        buff.clear();
        while (i < data.length) {
            while (buff.hasRemaining() && i < data.length) {
                buff.put(data[i]);
                i++;
            }
            buff.flip();
            while (buff.hasRemaining()) {
                try {
                    channel.write(buff);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            buff.compact();
        }
        server.getProcessing().remove(clientAddress);
    }

    /**
     * Writing the file  when user download file from server
     * @param path - the path to target file
     */
    private void  writeFile(Path path) {
        logger.info("start sending " + path);
        try {
            RandomAccessFile file = new RandomAccessFile(path.toString(), "r");
            FileChannel fileChannel = file.getChannel();
            ByteBuffer buff = (ByteBuffer) key.attachment();

            int i = 0;
            buff.clear();
            while (i != -1) {

                i = fileChannel.read(buff);

                buff.flip();
                channel.write(buff);
                buff.compact();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            serializeBeforeSendData("alert This file is not exist");
        } catch (IOException e) {
            e.printStackTrace();
        }
        server.getProcessing().remove(clientAddress);
        logger.info("end sending " + path);
    }

    /**
     * Method processing commands coming from client
     * @param command - the command coming from client
     */
    private void processing(String command) {
        logger.info(clientAddress + " send command: " + command);
        if (command.equals("getUpdateFileTable")){
            serializeBeforeSendData(updateFileTable(currentPath));
        } else if (command.equals("getPathField")) {
            String s = currentPath.toString();
            serializeBeforeSendData(s.replace(root.resolve("users") + File.separator, ""));
        } else if (command.startsWith("download")) {
            downloadFile(command);
        } else if (command.startsWith("upload")) {
            uploadFile(command);
        } else if (command.startsWith("moveTo")) {
            moveTo(command);
        } else if (command.equals("moveBack")) {
            moveBack();
        } else if (command.startsWith("copy")) {
            setSelectCopyFile(command);
        } else if (command.equals("past")) {
            pastFileOrDir();
        } else if (command.startsWith("cut")) {
            setSelectCutFile(command);
        } else if (command.startsWith("create")) {
            createFileOrDirectory(command);
        } else if (command.startsWith("rename")) {
            renameFile(command);
        } else if (command.startsWith("delete")) {
            deleteFileOrDirectory(currentPath.resolve(command.substring("delete ".length())));
        } else if (command.equals("disconnect")) {
            breakConnect();
        }
    }

    /**
     *  This method prepared server for accept the file
     * @param command - the command with information about the file
     */
    private void uploadFile(String command) {
        String[] token = command.split(" ");
        if (Files.exists(currentPath.resolve(token[1]))) {
            serializeBeforeSendData("alert This file is exist");
        } else {
            try {
                Files.createFile(currentPath.resolve(token[1]));
                serializeBeforeSendData("ready_for_get_file");
                long size = Long.parseLong(token[2]);
                readFile(currentPath.resolve(token[1]), size);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Send the file to the client
     * @param command - the information about the requested file
     */
    private void downloadFile(String command) {
        String[] token = command.split(" ");
        writeFile(currentPath.resolve(token[1]));
    }

    /**
     * Seting path of selected file for copy
     * @param command - the name of selected file
     */
    private void setSelectCopyFile(String command) {
        selectFileForCopy = currentPath.resolve(command.substring("copy ".length()));
        logger.info("selectFileForCopy is " +  selectFileForCopy);
        server.getProcessing().remove(clientAddress);
    }

    /**
     * Seting path of selected file for cut
     * @param command - the name of selected file
     */
    private void setSelectCutFile(String command) {
        selectFileForCut = currentPath.resolve(command.substring("cut ".length()));
        logger.info("selectFileForCut is " +  selectFileForCut);
        server.getProcessing().remove(clientAddress);
    }

    /**
     * Defines operation (cut or copy) requested by client
     */
    private void pastFileOrDir() {
        logger.info("past");
        if (selectFileForCopy != null && selectFileForCut == null) {
            pastCopyFileOrDir(selectFileForCopy);
            selectFileForCopy = null;
        } else if (selectFileForCopy == null && selectFileForCut != null) {
            pastCopyFileOrDir(selectFileForCut);
            deleteFileOrDirectory(selectFileForCut);
            selectFileForCut = null;
            return; //to don't invoke twice sendData("ok")
        } else {
            server.getProcessing().remove(channel);
        }
        serializeBeforeSendData("ok");
    }

    /**
     * Ending the command of copy
     * @param source - the path to the copying file
     */
    private void pastCopyFileOrDir(Path source) {
        logger.info("past fod");
        Path target = currentPath.resolve(source.getFileName());

        if (!Files.isDirectory(source)) {
            copyFile(source, target);
        }else if (Files.isDirectory(source)) {
            copyDirectory(source, target);
        }
    }

    /**
     * implementing copying of directory
     * @param source - the path to source directory
     * @param target - the path to target coping directory
     */
    private void copyDirectory(Path source, Path target) {
        logger.info("start copy dir");
        try {
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
                    copyFile(s, t);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     *  Coping file
     * @param source - the path to source file
     * @param target - the path to target of copy
     */
    private void copyFile(Path source, Path target) {
        try {
            if (Files.exists(target)) {
                serializeBeforeSendData("alert The file name is exist");
            } else {
                Files.copy(source, target);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Implementing a command to move back along directory
     */
    private void moveBack() {
        if (!currentPath.equals(root.resolve("users").resolve(userName))) {
            currentPath = currentPath.getParent();
            serializeBeforeSendData(updateFileTable(currentPath));
        } else {
            serializeBeforeSendData("ok");
        }
    }

    /**
     * Implementing a command to move a selected directory
     * @param command - the name selected directory
     */
    private void moveTo(String command) {
        currentPath = currentPath.resolve(command.substring("moveTo ".length()));
        if (Files.isDirectory(currentPath)) {
            serializeBeforeSendData(updateFileTable(currentPath));
        } else {
            currentPath = currentPath.getParent();
            serializeBeforeSendData("alert This is not directory");
        }
    }

    /**
     * Implementing a request to delete a directory or file
     * @param target - the target file or directory
     */
    private void deleteFileOrDirectory(Path target) {
        try {
            if (!Files.isDirectory(target)) {
                Files.delete(target);
            } else if (Files.isDirectory(target)) {
                deleteDirectory(target);
            }
            serializeBeforeSendData("ok");
        } catch (IOException e) {
            e.printStackTrace();
            serializeBeforeSendData("alert File cannot be deleted");
        }
    }

    /**
     * Implementing a request delete a directory or file
     * @param path - the path to the target directory
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
     * Create a fle or a directory
     * @param command - the name of the file or directory
     */
    private void createFileOrDirectory(String command) {
        try {
            if (command.startsWith("create_file")) {
                Files.createFile(Paths.get(currentPath.resolve(
                        command.substring("create_file ".length())).toString()));
                serializeBeforeSendData("ok");
            } else if (command.startsWith("create_dir")) {
                Files.createDirectory(Paths.get(currentPath.resolve(
                        command.substring("create_file ".length())).toString()));
                serializeBeforeSendData("ok");
            }
        } catch (IOException e) {
            e.printStackTrace();
            serializeBeforeSendData("alert File cannot be create");
        }
    }

    /**
     * Renaming selected a file or a directory
     * @param command - the old and the new name of the file or directory
     */
    private void renameFile(String command) {
        String[] token = command.split(" ");
        File currentName = new File(currentPath.resolve(token[1]).toString());
        File newName = new File(currentPath.resolve(token[2]).toString());
        currentName.renameTo(newName);
        serializeBeforeSendData("ok");
    }

    /**
     * Implementing a request disconnect client
     */
    private void breakConnect() {
        try {
            serializeBeforeSendData("disconnect");
            server.getMapAuthUser().remove(clientAddress);
            if (!server.getMapAuthUser().containsKey(clientAddress)) {
                logger.info("ClientHandler was deleted: " + clientAddress);
            }
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Serialize object before sending to client
     * @param ob - the target object
     */
    private void serializeBeforeSendData(Object ob) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(ob);
            oos.flush();
            write(baos.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Implementing a request for update the list of server files
     * @param path - the target a directory
     * @return - the list of FileInfo class contenting information about the files inside this directory
     */
    private List<FileInfo> updateFileTable (Path path) {
        try {
            return Files.list(path).map(FileInfo::new).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            serializeBeforeSendData("alert Can not update the files list");
        }
        return null;
    }

    /**
     * Detouring content of target directory
     * @param path - the path to the target directory
     * @return - the list of path the files consisting into directory
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
     * Cutting redundant part the path of file for further processing
     * @param path - the previous path
     * @param source - the redundant path
     * @return - the required path
     */
    private Path truncationPath(Path path, Path source) {
        return  path.subpath(source.getNameCount(), path.getNameCount());

    }
}
