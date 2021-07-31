package com.cloud.server;

import java.io.*;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
     *
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
                //server.getProcessing().remove(clientAddress);
                processing(sb.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *
     * @param key
     * @param pathFile
     * @param sizeFile
     */
    private void readFile(SelectionKey key, Path pathFile, long sizeFile) {
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
                logger.info(String.valueOf(size) + " " + String.valueOf(sizeFile));

                buff.flip();
                while (buff.hasRemaining()) {
                    fileChannel.write(buff);
                }
                buff.compact();
            }
            sendData("ok");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * @param data
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
     *
     * @param
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
            sendData("alert This file is not exist");
        } catch (IOException e) {
            e.printStackTrace();
        }
        server.getProcessing().remove(clientAddress);
        logger.info("end sending " + path);
    }

    /**
     *
     * @param command
     */
    private void processing(String command) {
        logger.info(clientAddress + " send command: " + command);
        if (command.equals("getUpdateFileTable")){
            sendData(updateFileTable(currentPath));
        } else if (command.equals("getPathField")) {
            String s = currentPath.toString();
            sendData(s.replace(root.resolve("users") + File.separator, ""));
        } else if (command.startsWith("download")) {
            downloadFile(command);
        } else if (command.startsWith("upload")) {
            uploadFile(command);
        } else if (command.startsWith("moveTo")) {
            moveTo(command);
        } else if (command.equals("moveBack")) {
            moveBack();
        } else if (command.startsWith("copy")) {
            copyFile(command);
        } else if (command.equals("past")) {
            pastFile();
        } else if (command.startsWith("cut")) {
            cutFile(command);
        } else if (command.startsWith("create")) {
            createFileOrDirectory(command);
        } else if (command.startsWith("rename")) {
            renameFile(command);
        } else if (command.startsWith("delete")) {
            deleteFile(command);
        } else if (command.equals("disconnect")) {
            breakConnect();
        }
    }

    private void uploadFile(String command) {
        String[] token = command.split(" ");
        if (Files.exists(currentPath.resolve(token[1]))) {
            sendData("alert This file is exist");
        } else {
            try {
                Files.createFile(currentPath.resolve(token[1]));
                sendData("ready_for_get_file");
                long size = Long.parseLong(token[2]);
                readFile(key, currentPath.resolve(token[1]), size);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void downloadFile(String command) {
        String[] token = command.split(" ");
        writeFile(currentPath.resolve(token[1]));
    }

    private void copyFile(String command) {
        selectFileForCopy = currentPath.resolve(command.substring("copy ".length()));
        server.getProcessing().remove(clientAddress);
    }

    private void cutFile(String command) {
        selectFileForCut = currentPath.resolve(command.substring("cut ".length()));
        server.getProcessing().remove(clientAddress);
    }

    private void pastFile() {
        try {
            if (selectFileForCopy != null && selectFileForCut == null) {
                if (!Files.exists(currentPath.resolve(selectFileForCopy.getFileName()))) {
                    Files.copy(selectFileForCopy, currentPath.resolve(selectFileForCopy.getFileName()));
                    selectFileForCopy = null;
                    sendData("ok");
                } else {
                    sendData("alert The file name is exist");
                }
            } else if (selectFileForCopy == null && selectFileForCut != null) {
                if (!Files.exists(currentPath.resolve(selectFileForCut.getFileName()))) {
                    Files.copy(selectFileForCut, currentPath.resolve(selectFileForCut.getFileName()));
                    Files.delete(selectFileForCut);
                    selectFileForCut = null;
                    sendData("ok");
                } else {
                    sendData("alert The file name is exist");
                }
            } else {
                server.getProcessing().remove(channel);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void moveBack() {
        if (!currentPath.equals(root.resolve("users").resolve(userName))) {
            currentPath = currentPath.getParent();
            sendData(updateFileTable(currentPath));
        } else {
            sendData("ok");
        }
    }

    private void moveTo(String command) {
        currentPath = currentPath.resolve(command.substring("moveTo ".length()));
        if (Files.isDirectory(currentPath)) {
            sendData(updateFileTable(currentPath));
        } else {
            currentPath = currentPath.getParent();
            sendData("alert This is not directory");
        }
    }

    private void deleteFile(String command) {
        try {
            Files.delete(currentPath.resolve(command.substring("delete ".length())));
            sendData("ok");
        } catch (IOException e) {
            e.printStackTrace();
            sendData("alert File cannot be deleted");
        }
    }

    private void createFileOrDirectory(String command) {
        try {
            if (command.startsWith("create_file")) {
                Files.createFile(Paths.get(currentPath.resolve(
                        command.substring("create_file ".length())).toString()));
                sendData("ok");
            } else if (command.startsWith("create_dir")) {
                Files.createDirectory(Paths.get(currentPath.resolve(
                        command.substring("create_file ".length())).toString()));
                sendData("ok");
            }
        } catch (IOException e) {
            e.printStackTrace();
            sendData("alert File cannot be create");
        }
    }

    private void renameFile(String command) {
        String[] token = command.split(" ");
        File currentName = new File(currentPath.resolve(token[1]).toString());
        File newName = new File(currentPath.resolve(token[2]).toString());
        currentName.renameTo(newName);
        sendData("ok");
    }

    private void breakConnect() {
        try {
            sendData("disconnect");
            server.getMapAuthUser().remove(clientAddress);
            if (!server.getMapAuthUser().containsKey(clientAddress)) {
                logger.info("ClientHandler was deleted: " + clientAddress);
            }
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendData(Object ob) {
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

    private List<FileInfo> updateFileTable (Path path) {
        try {
            return Files.list(path).map(FileInfo::new).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            sendData("alert Can not update the files list");
        }
        return null;
    }
}
