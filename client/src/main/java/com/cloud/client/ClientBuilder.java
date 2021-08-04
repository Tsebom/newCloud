package com.cloud.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Start Client
 */
public class ClientBuilder extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        Parent root = FXMLLoader.load(getClass().getResource("/client.fxml"));
        primaryStage.setTitle("Cloud Authorization");
        primaryStage.setScene(new Scene(root, 1400, 800));
        primaryStage.show();
        ServerController.setStage(primaryStage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
