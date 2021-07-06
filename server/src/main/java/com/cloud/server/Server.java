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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final int PORT = 5679;
    private static final String IP_ADRESS = "localhost";
    private ServerSocketChannel server;

    Path root = Paths.get("./server");

    private ExecutorService service;

    private AuthService authService;

    //list connected users
    private Map<SocketAddress, ClientHandler> mapAuthUser = new HashMap<>();
    private Map<SocketAddress, AcceptHandler> mapRequestAuthUser = new HashMap<>();

    public Server() {
        try {
            service = Executors.newFixedThreadPool(5);
            authService = new DataBaseAuthService(this);

            this.server = ServerSocketChannel.open();
            server.bind(new InetSocketAddress(IP_ADRESS, PORT));
            server.configureBlocking(false);

            Selector selector = Selector.open();
            server.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Server has started.");

            while (server.isOpen()) {
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    SocketAddress socket = ((SocketChannel)key.channel()).getRemoteAddress();
                    if (key.isAcceptable()) {
                       service.execute(new AcceptHandler(this, key));
                    }
                    if (key.isReadable()) {
                        if (mapRequestAuthUser.containsKey(socket)) {
                            service.execute(() -> {
                                mapRequestAuthUser.get(socket).readChanel();
                            });
                        }
                        if (mapAuthUser.containsKey(socket)) {
//                            service.execute(() -> {
//                                try {
//                                    mapAuthUser.get(socket.getRemoteAddress()).read();
//                                } catch (IOException e) {
//                                    e.printStackTrace();
//                                }
//                            });
                        }
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

    public AuthService getAuthService() {
        return authService;
    }

    public Map<SocketAddress, AcceptHandler> getMapRequestAuthUser() {
        return mapRequestAuthUser;
    }

    public Map<SocketAddress, ClientHandler> getMapAuthUser() {
        return mapAuthUser;
    }

//    private void handleAccept(SelectionKey key, Selector selector) {
//        try {
//            SocketChannel channel = ((ServerSocketChannel)key.channel()).accept();
//            channel.configureBlocking(false);
//            System.out.println("Client has connected " + channel.getRemoteAddress());
//            channel.register(selector, SelectionKey.OP_READ);
//            map.put(channel.getRemoteAddress(), new ClientHandler(this, channel, selector));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    public Path getRoot() {
        return root;
    }

    public static void main(String[] args) {
        new Server();
    }
}
