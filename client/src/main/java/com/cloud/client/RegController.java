package com.cloud.client;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class RegController {
    private static Stage stage;

    @FXML
    public Button sign_in;
    @FXML
    public Button sign_up;
    @FXML
    public Button registration;
    @FXML
    public TextField loginField;
    @FXML
    public PasswordField passwordField;

    private ClientConnect connect;

    private boolean isRegistration = false;

    public static void setStage(Stage stage) {
        RegController.stage = stage;
    }

    private void regOrAuth (boolean isRegistration) {
        sign_in.setVisible(isRegistration);
        sign_in.setManaged(isRegistration);
        sign_up.setVisible(isRegistration);
        sign_up.setManaged(isRegistration);

        registration.setVisible(!isRegistration);
        registration.setManaged(!isRegistration);
    }

    public void signUp(ActionEvent actionEvent) {
        regOrAuth(isRegistration);
        isRegistration = !isRegistration;
        stage.setTitle("Cloud Registration");
    }

    public void registration(ActionEvent actionEvent) {
        connect = ClientConnect.getInstance();
        connect.getQueue().add("reg ".concat(loginField.getText().trim() + " ")
                .concat(passwordField.getText().trim()));
    }

    public void signIn(ActionEvent actionEvent) {
        connect = ClientConnect.getInstance();
        connect.getQueue().add("auth ".concat(loginField.getText().trim() + " ")
                .concat(passwordField.getText().trim()));
    }

    public void initClientWindow() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/client.fxml"));
            Parent root = fxmlLoader.load();
            stage.setScene(new Scene(root, 1200, 800));
            stage.setTitle("Cloud");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
