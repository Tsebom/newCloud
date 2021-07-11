package com.cloud.server;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.util.logging.Logger;

public class ClientHandler {
    private String userName;

    private Server server;
    private SocketChannel channel;
    private Selector selector;
    private SocketAddress clientAddress;

    private Logger logger = Server.logger;

    private ByteBuffer buffer = ByteBuffer.allocate(1460);
    private String command = "";

    public ClientHandler(Server server, SocketChannel channel, Selector selector, String userName) {
        this.userName = userName;
        this.server = server;
        this.channel = channel;
        this.selector = selector;

        try {
            clientAddress = channel.getRemoteAddress();
        } catch (IOException e) {
            e.printStackTrace();
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

            command = sb.toString();
            logger.info("the end read data from the channel: " + clientAddress);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
