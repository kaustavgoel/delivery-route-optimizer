package com.deliveryoptimizer.app;

import com.deliveryoptimizer.ui.MainDashboard;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        MainDashboard dashboard = new MainDashboard();
        Scene scene = new Scene(dashboard.getRoot(), 1440, 900);

        scene.getStylesheets().addAll(
                Objects.requireNonNull(getClass().getResource("/css/theme.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/dashboard.css")).toExternalForm()
        );

        primaryStage.setTitle("DELIVERY ROUTE OPTIMIZER — Multi-Algorithm VRP Command Center");
        primaryStage.setMinWidth(1180);
        primaryStage.setMinHeight(760);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
