package com.cloud.server;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;

public class ClientHandler {
    private String idUser;

    private ServerBuild server;
    private SocketChannel channel;
    private Selector selector;

    public ClientHandler(ServerBuild server, SocketChannel channel, Selector selector) {
        this.idUser = "user";
        this.server = server;
        this.channel = channel;
        this.selector = selector;

        try {
            Files.createDirectory(server.getRoot().resolve(idUser));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
