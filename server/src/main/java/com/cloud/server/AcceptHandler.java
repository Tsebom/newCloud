package com.cloud.server;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AcceptHandler implements Runnable {
    private static final Logger logger = Server.logger;

    private final Server server;
    private final Selector selector;
    private final SelectionKey key;

    private SocketChannel channel;
    private SocketAddress clientAddress;

    private ByteBuffer buffer = ByteBuffer.allocate(1460);
    private String command = "";

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
            server.getMapRequestAuthUser().put(channel.getRemoteAddress(), this);
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
                        if (token.length < 3) {
                            logger.info("client " + clientAddress + " has sent incorrect data: " + command);
                            channel.write(ByteBuffer.wrap("fail_auth The login or the password is not correct"
                                    .getBytes(StandardCharsets.UTF_8)));
                            command = "";
                            continue;
                        }
                        if (command.startsWith("auth")) {
                            logger.info("client " + clientAddress + " has requested authorization");
                            if (server.getAuthService().isRegistration(token[1], token[2])) {
                                logger.info("client " + clientAddress + " has got authorization");
                                server.getMapAuthUser().put(clientAddress,
                                        new ClientHandler(server, channel, selector, server.getAuthService().
                                                getNickNameByLoginAndPassword(token[1], token[2])));
                                channel.write(ByteBuffer.wrap("auth_ok".getBytes(StandardCharsets.UTF_8)));
                                command = "";
                                return;
                            } else {
                                logger.info("client " + clientAddress + " hasn't got authorization");
                                channel.write(ByteBuffer.wrap("fail_auth The login or the password is not correct"
                                        .getBytes(StandardCharsets.UTF_8)));
                                command = "";
                            }
                        }
                        if (command.startsWith("reg")) {
                            logger.info("client " + clientAddress + " has requested registration");
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

    public void readChanel() {
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




        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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

    private synchronized void newCommand(String message) {
        command = message;
        notify();
    }
}
