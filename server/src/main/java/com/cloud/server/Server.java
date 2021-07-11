package com.cloud.server;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Server {
    protected static final Logger logger = Logger.getLogger(Server.class.getName());
    private static final LogManager logmanager = LogManager.getLogManager();

    private static Connection connection;
    private static Statement statement;
    private static PreparedStatement addUser;

    private static final int PORT = 5679;
    private static final String IP_ADRESS = "localhost";
    private ServerSocketChannel server;

    private Path root = Paths.get("./server");

    private ExecutorService service;

    private AuthService authService;

    //list connected users
    private Map<SocketAddress, ClientHandler> mapAuthUser = new HashMap<>();
    private Map<SocketAddress, AcceptHandler> mapRequestAuthUser = new HashMap<>();

    public Server() {
        try {
            logmanager.readConfiguration(new FileInputStream("server/logging.properties"));

            service = Executors.newFixedThreadPool(5);
            authService = new DataBaseAuthService(this);

            server = ServerSocketChannel.open();
            server.bind(new InetSocketAddress(IP_ADRESS, PORT));
            server.configureBlocking(false);

            Selector selector = Selector.open();
            server.register(selector, SelectionKey.OP_ACCEPT);
            connectDataBase();
            logger.info("Server has started.");
            setAllPrepareStatement();

            while (server.isOpen()) {
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    if (key.isAcceptable()) {
                        logger.info("request accept");
                        service.execute(new AcceptHandler(this, key));
                    }
                    if (key.isReadable()) {
                        SocketAddress socket = ((SocketChannel) key.channel()).getRemoteAddress();
                        logger.info("readable event from " + socket);

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
                        key.interestOps(SelectionKey.OP_WRITE);
                    }
                    iterator.remove();
                }
            }
        } catch (FileNotFoundException f) {
            throw new RuntimeException("Logger file \"logging.properties\" hasn't been found");
        } catch (ClosedChannelException e) {
            logger.log(Level.SEVERE, "",e);
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                disconnectDataBase();
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

    public Path getRoot() {
        return root;
    }

    public static Statement getStatement() {
        return statement;
    }

    public static PreparedStatement getAddUser() {
        return addUser;
    }

    /**
     * Set connect to RegBase
     * @throws ClassNotFoundException
     * @throws SQLException
     */
    private static void connectDataBase(){
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:RegBase.db");
            statement = connection.createStatement();
            logger.info("server has connected to RegBase");
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "class \"org.sqlite.JDBC\" hasn't be find", e);
        } catch (SQLException sql) {
            logger.log(Level.SEVERE, "registration database access occurs while connect", sql);
        }
    }

    /**
     * Close connect to RegBase
     */
    private static void disconnectDataBase(){
        try {
            statement.close();
            connection.close();
            logger.info("server has disconnected to RegBase");
        } catch (SQLException sql) {
            logger.log(Level.SEVERE, "registration database access occurs while disconnect", sql);
        }
    }

    /**
     * Set prepare statement for update RegBase
     * @throws SQLException
     */
    private void setAllPrepareStatement() {
        try {
            addUser = connection.prepareStatement(
                    "INSERT INTO UsersOFAuthorization (login, password, nickname)" +
                            " VALUES ( ? , ? , ? )");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
