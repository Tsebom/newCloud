package com.cloud.server;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Realization of interface AuthService for DataBase SQLite
 */
public class DataBaseAuthService implements AuthService{
    private Server server;
    private Logger logger = Server.logger;

    public DataBaseAuthService(Server server) {
        this.server = server;
    }

    @Override
    public String getNickNameByLoginAndPassword(String login, String password) {
        ResultSet result;

        try {
            result = server.getStatement().executeQuery("SELECT nickname FROM UsersOFAuthorization WHERE " +
                    "login = '" + login + "' AND password = '" + password + "'");
            if (result.next()) {
                return result.getString(1);
            }
        }catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean isRegistration(String login, String password) {
        ResultSet result;

        try {
            result = server.getStatement().executeQuery(String.format("SELECT * FROM UsersOFAuthorization " +
                    "WHERE login = '%s' AND password = '%s'", login, password));
            if (result.next() && result != null) {
                return true;
//
            }
        }catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void setRegistration(String login, String password) {
        try {
            server.getAddUser().setString(1, login);
            server.getAddUser().setString(2, password);
            server.getAddUser().setString(3, login);
            server.getAddUser().executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
