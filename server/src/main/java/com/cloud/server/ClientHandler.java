package com.cloud.server;

import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;

public class ClientHandler {
    private String userName;

    private Server server;
    private SocketChannel channel;
    private Selector selector;

    public ClientHandler(Server server, SocketChannel channel, Selector selector, String userName) {
        this.userName = userName;
        this.server = server;
        this.channel = channel;
        this.selector = selector;

        try {
            Files.createDirectory(server.getRoot().resolve(userName));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
