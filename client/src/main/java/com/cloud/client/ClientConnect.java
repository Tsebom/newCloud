package com.cloud.client;

import javafx.application.Platform;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
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

    private BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private ByteBuffer buffer = ByteBuffer.allocate(1460);
    private String command;
    private boolean breakeClientConnect;

    private ClientConnect() {
        instance = this;
        breakeClientConnect = false;
        logger.info("client instance created");
        try {
            logmanager.readConfiguration(new FileInputStream("client/logging.properties"));
            selector = Selector.open();
            channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_CONNECT);
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
                if (breakeClientConnect) {
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
                        readChanel();
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

    public BlockingQueue<String> getQueue() {
        return queue;
    }

    public void setServerController(ServerController serverController) {
        this.serverController = serverController;
    }

    public void readChanel() {
        logger.info("the start reading data from the channel: " + serverAddress);
        try {
            int bytesRead = channel.read(buffer);

            if (bytesRead < 0) {
                channel.close();
            } else if (bytesRead == 0) {
                return;
            }

            buffer.flip();
            StringBuilder sb = new StringBuilder();
            while (buffer.hasRemaining()) {
                sb.append((char) buffer.get());
            }
            buffer.clear();

            command = sb.toString();
            logger.info("the end read data from the channel: " + serverAddress);
            logger.info(command);
            processingCommand(command);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processingCommand(String command) {
        if (command.equals("auth_ok")) {
            logger.info("authorization has been confirmed");
            serverController.switchServerWindow(serverController.isRegistration());
            serverController.setTitle("Cloud");

        } else if (command.equals("reg_ok")) {
            logger.info("registration has been confirmed");
            serverController.switchServerWindow(serverController.isRegistration());
        } else if (command.equals("disconnect")) {
            logger.info(command);
            breakeClientConnect = true;
            Platform.exit();
        }
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
            }
        }
    }
}
