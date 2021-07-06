package com.cloud.server;

public class DataBaseAuthService implements AuthService{
    Server server;

    public DataBaseAuthService(Server server) {
        this.server = server;
    }

    @Override
    public String getNickNameByLoginAndPassword(String login, String password) {
        return null;
    }

    @Override
    public boolean isRegistration(String login, String password) {
        return false;
    }
}
