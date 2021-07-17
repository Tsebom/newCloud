package com.cloud.server;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The AcceptHandler class implements the processing of registration or authorization data from users
 * and establishes a connection with the user.
 */
public class AcceptHandler implements Runnable {
    private static final Logger logger = Server.logger;

    private final Server server;
    private final Selector selector;
    private final SelectionKey key;

    private SocketChannel channel;
    private SocketAddress clientAddress;

    private ByteBuffer buffer = ByteBuffer.allocate(1460);
    private String command = "";

    /**
     * Initializes a new instance of this class.
     * @param server
     * @param key
     */
    public AcceptHandler(Server server, SelectionKey key) {
        this.server = server;
        this.key = key;
        selector = key.selector();
        try {
            channel = ((ServerSocketChannel)key.channel()).accept();
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_READ);
            clientAddress = channel.getRemoteAddress();
            logger.info("client has connected: " + clientAddress);
            server.getMapRequestAuthUser().put(clientAddress, this);
            logger.info("create AcceptHandler: " + clientAddress);
        } catch (ClosedChannelException e) {
            logger.log(Level.WARNING, "connect has failed: " + clientAddress, e);
        } catch (IOException e) {
            logger.log(Level.WARNING, "I/O error occur: " + clientAddress, e);
        }
    }

    @Override
    public void run() {
        try {
            if (key.isValid()) {
                while (true) {
                    waitCommand();
                    if (!command.equals("")) {
                        logger.info("client " + clientAddress + " has sent: " + command);
                        String[] token = command.split(" ");
                        //check valid data
                        if (token.length < 3) {
                            //disconnect
                            if (command.equals("disconnect")) {
                                channel.close();
                                break;
                            }

                            logger.info("client " + clientAddress + " has sent incorrect data: " + command);
                            channel.write(ByteBuffer.wrap("alert_fail_data The login or the password is not correct"
                                    .getBytes(StandardCharsets.UTF_8)));
                            command = "";
                            continue;
                        }

                        if (command.startsWith("auth")) {
                            logger.info("client " + clientAddress + " has requested authorization");
                            if (server.getAuthService().isRegistration(token[1], token[2])) {
                                server.getMapAuthUser().put(clientAddress,
                                        new ClientHandler(server, channel, selector, server.getAuthService().
                                                getNickNameByLoginAndPassword(token[1], token[2])));
                                channel.write(ByteBuffer.wrap("auth_ok".getBytes(StandardCharsets.UTF_8)));
                                logger.info("client " + clientAddress + " has got authorization");
                                command = "";
                                return;
                            } else {
                                logger.info("client " + clientAddress + " hasn't got authorization");
                                channel.write(ByteBuffer.wrap("alert_fail_auth The login or the password is not correct"
                                        .getBytes(StandardCharsets.UTF_8)));
                                command = "";
                            }
                        } else if (command.startsWith("reg")) {
                            logger.info("client " + clientAddress + " has requested registration");
                            if (server.getAuthService().isRegistration(token[1], token[2])) {
                                logger.info("client " + clientAddress + " hasn't got registration");
                                channel.write(ByteBuffer.wrap("alert_fail_reg This user already exist"
                                        .getBytes(StandardCharsets.UTF_8)));
                                command = "";
                            } else {
                                server.getAuthService().setRegistration(token[1], token[2]);
                                Files.createDirectory(server.getRoot().resolve("users").resolve(token[1]));
                                server.getMapAuthUser().put(clientAddress,
                                        new ClientHandler(server, channel, selector, server.getAuthService().
                                                getNickNameByLoginAndPassword(token[1], token[2])));
                                channel.write(ByteBuffer.wrap("reg_ok".getBytes(StandardCharsets.UTF_8)));
                                logger.info("client " + clientAddress + " has got registration");
                                command = "";
                                return;
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            server.getMapRequestAuthUser().remove(clientAddress);
        }
    }

    /**
     *
     */
    public void readChanel() {
        if (channel.isOpen()) {
            logger.info("the start reading data from the channel: " + clientAddress);
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
                logger.info("the end read data from the channel: " + clientAddress);

                newCommand(sb.toString());
                server.getProcessing().remove(clientAddress);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *
     */
    private synchronized void waitCommand() {
        while (command.equals("")) {
            try {
                logger.info("wait command from client");
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *
     * @param message
     */
    private synchronized void newCommand(String message) {
        command = message;
        if (command.equals("disconnect")) {
            breakConnect();
        }
        notify();
    }

    private void breakConnect() {
        try {
            channel.write(ByteBuffer.wrap("disconnect".getBytes(StandardCharsets.UTF_8)));
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
