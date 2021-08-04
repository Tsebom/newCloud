package com.cloud.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
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

    private String command = "";

    /**
     * Initializes a new instance of AcceptHandler class.
     * @param server - link to the instance Sever class
     * @param key -
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
            server.getProcessing().add(clientAddress);
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
                        if (token.length < 3) {
                            if (command.equals("disconnect")) {
                                channel.close();
                                break;
                            }
                            serializeData("alert_fail_data The login or the password is not correct");
                            command = "";
                            continue;
                        }

                        if (command.startsWith("auth")) {
                            logger.info("client " + clientAddress + " has requested authorization");
                            if (server.getAuthService().isRegistration(token[1], token[2])) {
                                server.getMapAuthUser().put(clientAddress,
                                        new ClientHandler(server, key, channel, server.getAuthService().
                                                getNickNameByLoginAndPassword(token[1], token[2])));
                                serializeData("auth_ok");
                                logger.info("client " + clientAddress + " has got authorization");
                                command = "";
                                break;
                            } else {
                                logger.info("client " + clientAddress + " hasn't got authorization");
                                serializeData("alert_fail_auth The login or the password is not correct");
                                command = "";
                            }
                        } else if (command.startsWith("reg")) {
                            logger.info("client " + clientAddress + " has requested registration");
                            if (server.getAuthService().isRegistration(token[1], token[2])) {
                                logger.info("client " + clientAddress + " hasn't got registration");
                                serializeData("alert_fail_reg This user already exist");
                                command = "";
                            } else {
                                server.getAuthService().setRegistration(token[1], token[2]);
                                Files.createDirectory(server.getRoot().resolve("users").resolve(token[1]));
                                server.getMapAuthUser().put(clientAddress,
                                        new ClientHandler(server, key, channel, server.getAuthService().
                                                getNickNameByLoginAndPassword(token[1], token[2])));
                                serializeData("reg_ok");
                                logger.info("client " + clientAddress + " has got registration");
                                command = "";
                                break;
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            server.getMapRequestAuthUser().remove(clientAddress);
            server.getProcessing().remove(clientAddress);
            if (!server.getMapRequestAuthUser().containsKey(clientAddress)) {
                logger.info("AcceptHandler was deleted: " + clientAddress);
            }
        }
    }

    /**
     *  Read data from the channel
     */
    public void read() {
        if (channel.isOpen()) {
            logger.info("the start reading data from the channel: " + clientAddress);
            ByteBuffer buf = (ByteBuffer) key.attachment();
            try {
                int bytesRead = channel.read(buf);
                if (bytesRead < 0) {
                    channel.close();
                } else if (bytesRead == 0) {
                    return;
                }
                buf.flip();
                StringBuilder sb = new StringBuilder();
                while (buf.hasRemaining()) {
                    sb.append((char) buf.get());
                }
                buf.clear();
                logger.info("the end read data from the channel: " + clientAddress);
                newCommand(sb.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Write data to the channel
     * @param data - source the data
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
     * Synchronized method is setting thread for waiting new command
     */
    private synchronized void waitCommand() {
        while (command.equals("")) {
            try {
                logger.info("wait command from client");
                server.getProcessing().remove(clientAddress);
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Weak up thread for processing a new command
     * @param command - the command
     */
    private synchronized void newCommand(String command) {
        if (command.equals("disconnect")) {
            serializeData("disconnect");
        }
        this.command = command;
        notify();
    }

    /**
     * Serialize a data for writing the data to the channel
     * @param ob
     */
    private void serializeData(Object ob) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(ob);
            oos.flush();
            write(baos.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
