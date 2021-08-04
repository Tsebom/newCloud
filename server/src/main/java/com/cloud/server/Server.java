package com.cloud.server;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * The Server class implements interact with clients
 */
public class Server {
    protected static final Logger logger = Logger.getLogger(Server.class.getName());
    private static final LogManager logmanager = LogManager.getLogManager();

    private Connection connection;
    private Statement statement;
    private PreparedStatement addUser;

    private static final int PORT = 5679;
    private static final String IP_ADRESS = "localhost";
    private static final int BUFFER_SIZE = 1460;
    private static final int TIMEOUT = 3000;

    private ServerSocketChannel server;
    private Selector selector;

    private Path root = Paths.get("./server");

    private ExecutorService service;

    private AuthService authService;

    private Map<SocketAddress, ClientHandler> mapAuthUser = Collections.synchronizedMap(new HashMap<>());
    private Map<SocketAddress, AcceptHandler> mapRequestAuthUser = Collections.synchronizedMap(new HashMap<>());
    private Set<SocketAddress> processing = Collections.synchronizedSet(new HashSet<>());

    public Server() {
        try {
            logmanager.readConfiguration(new FileInputStream("server/logging.properties"));

            service = Executors.newFixedThreadPool(5);
            authService = new DataBaseAuthService(this);

            server = ServerSocketChannel.open();
            server.bind(new InetSocketAddress(IP_ADRESS, PORT));
            server.configureBlocking(false);

            selector = Selector.open();
            server.register(selector, SelectionKey.OP_ACCEPT, ByteBuffer.allocate(BUFFER_SIZE));
            connectDataBase();
            logger.info("Server has started.");
            setAllPrepareStatement();

            while (server.isOpen()) {
                selector.select(TIMEOUT);
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    if (key.isValid()) {
                        if (key.isAcceptable()) {
                            logger.info("request accept");
                            service.execute(new AcceptHandler(this, key));
                        }
                        if (key.isReadable()) {
                            SocketAddress socket = ((SocketChannel) key.channel()).getRemoteAddress();
                            if (key.channel().isOpen() && mapAuthUser.containsKey(socket)
                                    && !processing.contains(socket)) {
                                logger.info("readable event from " + socket);
                                processing.add(socket);
                                service.execute(() -> {
                                    mapAuthUser.get(socket).read();
                                });
                            } else if (key.channel().isOpen() && mapRequestAuthUser.containsKey(socket)
                                    && !processing.contains(socket)) {
                                processing.add(socket);
                                service.execute(() -> {
                                    mapRequestAuthUser.get(socket).read();
                                });
                            }
                        }
                    } else {
                        logger.info(key + " is not valid");
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

    public Set<SocketAddress> getProcessing() {
        return processing;
    }

    public Path getRoot() {
        return root;
    }

    public Statement getStatement() {
        return statement;
    }

    public PreparedStatement getAddUser() {
        return addUser;
    }

    /**
     * Set connect to RegBase
     * @throws ClassNotFoundException
     * @throws SQLException
     */
    private void connectDataBase(){
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:server/src/main/resources/RegBase.db");
            statement = connection.createStatement();
            logger.info(statement.toString());
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
    private void disconnectDataBase(){
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
