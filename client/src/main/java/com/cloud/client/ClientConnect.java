package com.cloud.client;

import com.cloud.server.FileInfo;
import javafx.application.Platform;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class ClientConnect implements Runnable{
    private static ClientConnect instance;

    protected static final Logger logger = Logger.getLogger(ClientConnect.class.getName());
    private static final LogManager logmanager = LogManager.getLogManager();

    private static final int PORT = 5679;
    private static final String IP_ADRESS = "localhost";

    private Selector selector;
    private SocketChannel channel;
    private SocketAddress serverAddress;

    private ServerController serverController;

    private Queue<String> queue = new LinkedBlockingQueue<>();
    private boolean breakClientConnect;
    private String nameUser;

    private ClientConnect() {
        instance = this;
        breakClientConnect = false;
        logger.info("client instance created");
        try {
            logmanager.readConfiguration(new FileInputStream("client/logging.properties"));
            selector = Selector.open();
            channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_CONNECT, ByteBuffer.allocate(1460));
            channel.connect(new InetSocketAddress(IP_ADRESS, PORT));
            serverAddress = channel.getRemoteAddress();
        } catch (ClosedChannelException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                if (breakClientConnect) {
                    break;
                }
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    if (key.isConnectable()) {
                        channel.finishConnect();
                        logger.info("Connection to the server");
                        key.interestOps(SelectionKey.OP_WRITE);
                    }
                    if (key.isWritable()) {
                        String line = queue.poll();
                        if (line != null) {
                            logger.info("send command to the server: " + line);
                            channel.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
                            key.interestOps(SelectionKey.OP_READ);
                        }
                    }
                    if (key.isReadable()) {
                        read(key);
                        key.interestOps(SelectionKey.OP_WRITE);
                    }
                    iterator.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ClientConnect getInstance() {
       if (instance == null) {
           new Thread(new ClientConnect()).start();
       }
       return instance;
    }

    public Queue<String> getQueue() {
        return queue;
    }

    public void setServerController(ServerController serverController) {
        this.serverController = serverController;
    }


    public void setNameUser(String nameUser) {
        this.nameUser = nameUser;
    }

    public void read(SelectionKey key) {
        logger.info("the start reading data from the channel: " + serverAddress);
        ByteBuffer buf = (ByteBuffer) key.attachment();
        try {
            int bytesRead = channel.read(buf);
            List<Byte> list = new ArrayList<>();

            if (bytesRead < 0 || bytesRead == 0) {
                return;
            }

            buf.flip();
            while (buf.hasRemaining()) {
                list.add(buf.get());
            }
            buf.clear();

            //crutch
            byte[] b = new byte[list.size()];
            for (int i = 0; i < list.size(); i++) {
                b[i] = list.get(i);
            }

            convertData(b);
            logger.info("the end read data from the channel: " + serverAddress);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processingCommand(String command) {
        logger.info(command);
        //warning about fail
        if (command.startsWith("alert")) {
            logger.info("alert");
            if (command.startsWith("alert_fail_reg")) {
                Platform.runLater(() -> ClientController.
                        alertWarning(command.replace("alert_fail_reg ", "")));
            } else if (command.startsWith("alert_fail_auth")) {
                Platform.runLater(() -> ClientController.
                        alertWarning(command.replace("alert_fail_auth ", "")));
            } else if (command.startsWith("alert_fail_data")) {
                Platform.runLater(() -> ClientController.
                        alertWarning(command.replace("alert_fail_data ", "")));
            } else {
                Platform.runLater(() -> ClientController.
                        alertWarning(command.replace("alert ", "")));
            }
        }
        //relevant commands
        if (command.equals("ok")) {
            queue.add("getUpdateFileTable");
        } else if (command.equals("auth_ok")) {
            logger.info("authorization has been confirmed");
            serverController.switchServerWindow(serverController.isRegistration());
            serverController.setTitle("Cloud");
            queue.add("getUpdateFileTable");
        } else if (command.startsWith(nameUser)) {
            serverController.pathField.setText(command.replace(
                    command.substring(0, nameUser.length()), nameUser + ":~" ));
        } else if (command.equals("reg_ok")) {
            logger.info("registration has been confirmed");
            serverController.switchServerWindow(serverController.isRegistration());
            serverController.setTitle("Cloud");
            queue.add("getPathField");
        } else if (command.equals("disconnect")) {
            breakClientConnect = true;
            Platform.exit();
        }
    }

    private void convertData(byte[] b) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(b);
            ObjectInputStream ois = new ObjectInputStream(bais);
            Object ob = ois.readObject();

            if (ob instanceof String) {
                processingCommand((String) ob);
            }
            if (ob instanceof ArrayList) {
                castFileInfo(ob);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void castFileInfo(Object ob) {
        List<FileInfo> fl = new ArrayList<>();
        for (Object o : (ArrayList) ob) {
            if (o instanceof FileInfo) {
                fl.add((FileInfo) o);
            }
        }
        serverController.updateFileTable(fl);
        queue.add("getPathField");
    }
}
