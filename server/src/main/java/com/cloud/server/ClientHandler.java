package com.cloud.server;

import java.io.*;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
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
    private String command = "";

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
                server.getProcessing().remove(clientAddress);
                processing(sb.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *
     * @param data
     */
    private void  write(byte[] data) {
        ByteBuffer buf = (ByteBuffer) key.attachment();
        int i = 0;
        buf.clear();
        while (i < data.length) {
            while (buf.hasRemaining() && i < data.length) {
                buf.put(data[i]);
                i++;
            }
            buf.flip();
            while (buf.hasRemaining()) {
                try {
                    channel.write(buf);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            buf.compact();
        }
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
        } else if (command.startsWith("moveTo")) {
            currentPath = currentPath.resolve(command.substring("moveTo ".length()));
            sendData(updateFileTable(currentPath));
        } else if (command.equals("moveBack")) {
            if (!currentPath.equals(root.resolve("users").resolve(userName))) {
                currentPath = currentPath.getParent();
                sendData(updateFileTable(currentPath));
            } else {
                sendData("ok");
            }
        } else if (command.startsWith("copy")) {
            copyFile(command);
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

    private void deleteFile(String command) {
        try {
            Files.delete(currentPath.resolve(command.substring("delete ".length())));
            sendData("ok");
        } catch (IOException e) {
            e.printStackTrace();
            sendData("alert File cannot be deleted");
        }
    }

    private void copyFile(String command) {
        selectFileForCopy = currentPath.resolve(command.substring("copy ".length()));
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
