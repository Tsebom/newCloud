package com.cloud.server;

public interface AuthService {
    /**
     * Gives nickname by login and password
     * @param login - a login
     * @param password - a password
     * @return - nickname if login and password are correct otherwise null if not
     */
    String getNickNameByLoginAndPassword(String login, String password);

    /**
     * Returns information whether the user was registration
     * @param login - the login of the user
     * @param password - the password of the user
     * @return - true if the user was registered and false if not
     */
    boolean isRegistration(String login, String password);

    /**
     * Adding registration information about user
     * @param login - the login of the user
     * @param password - the password of the user
     */
    void setRegistration(String login, String password);
}
