package com.cloud.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerBuild {
    private static final int PORT = 5679;
    private static final String IP_ADRESS = "localhost";
    private ServerSocketChannel server;

    Path root = Paths.get("./server");

    ExecutorService service = Executors.newFixedThreadPool(3);

    //list connected users
    private Map<SocketAddress, ClientHandler> map = new HashMap<>();

    public ServerBuild() {
        try {
            this.server = ServerSocketChannel.open();
            server.bind(new InetSocketAddress(IP_ADRESS, PORT));
            server.configureBlocking(false);

            Selector selector = Selector.open();
            server.register(selector, SelectionKey.OP_ACCEPT | SelectionKey.OP_WRITE);
            System.out.println("Server has started.");

            while (server.isOpen()) {
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    if (key.isAcceptable()) {
                        //create channel for user
                        handleAccept(key, selector);
                    }
                    if (key.isReadable()) {

                    }
                    if (key.isWritable()) {

                    }
                    iterator.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleAccept(SelectionKey key, Selector selector) {
        try {
            SocketChannel channel = ((ServerSocketChannel)key.channel()).accept();
            channel.configureBlocking(false);
            System.out.println("Client has connected " + channel.getRemoteAddress());
            channel.register(selector, SelectionKey.OP_READ);
            map.put(channel.getRemoteAddress(), new ClientHandler(this, channel, selector));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Path getRoot() {
        return root;
    }

    public static void main(String[] args) {
        new ServerBuild();
    }
}
