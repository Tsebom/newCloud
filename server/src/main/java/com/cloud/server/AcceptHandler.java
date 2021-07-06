package com.cloud.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;

public class AcceptHandler implements Runnable {
    private Server server;
    private Selector selector;
    private SocketChannel channel;

    private ByteBuffer buffer = ByteBuffer.allocate(1024);
    private String command = null;

    public AcceptHandler(Server server, SelectionKey key) {
        this.server = server;
        this.selector = key.selector();

        try {
            this.channel = ((ServerSocketChannel)key.channel()).accept();
            channel.configureBlocking(false);
            System.out.println("Client has connected " + channel.getRemoteAddress());
            server.getMapRequestAuthUser().put(channel.getRemoteAddress(), this);
            channel.register(selector, SelectionKey.OP_READ);
        } catch (ClosedChannelException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                if (command != null) {
                    String[] token = command.split(" ");
                    if (token.length < 3) {
                        channel.write(ByteBuffer.wrap("fail_auth The login or the password is not correct"
                                .getBytes(StandardCharsets.UTF_8)));
                        continue;
                    }
                    if (command.startsWith("auth")) {
                        if (server.getAuthService().isRegistration(token[1], token[2])) {
                            server.getMapAuthUser().put(channel.getRemoteAddress(),
                                    new ClientHandler(server, channel, selector, server.getAuthService().
                                            getNickNameByLoginAndPassword(token[1], token[2])));
                            server.getMapRequestAuthUser().remove(channel.getRemoteAddress());
                            channel.write(ByteBuffer.wrap("auth_ok".getBytes(StandardCharsets.UTF_8)));
                            return;
                        } else {
                            channel.write(ByteBuffer.wrap("fail_auth The login or the password is not correct"
                                    .getBytes(StandardCharsets.UTF_8)));
                        }
                    }
                    if (command.startsWith("reg")) {

                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void readChanel() {
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
            synchronized (command) {
                command = sb.toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
