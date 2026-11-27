package com.deliveryoptimizer.ui;

import com.deliveryoptimizer.graph.DistanceMatrix;
import com.deliveryoptimizer.model.ProblemConfig;
import com.deliveryoptimizer.model.Route;
import com.deliveryoptimizer.model.Vehicle;
import com.deliveryoptimizer.model.Vehicle.Fleet;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.function.Consumer;

public class FleetView extends VBox {

    private final FlowPane cardsPane = new FlowPane(14, 14);
    private final Label summaryLabel = new Label("Click any vehicle card to highlight its route on the Simulation Map.");
    private final Consumer<Vehicle> onHighlightVehicle;

    public FleetView(Consumer<Vehicle> onHighlightVehicle) {
        this.onHighlightVehicle = onHighlightVehicle;
        setSpacing(14);
        setPadding(new Insets(16));

        Label title = new Label("Fleet Management");
        title.getStyleClass().add("section-title");
        summaryLabel.getStyleClass().add("muted-label");

        ScrollPane scroll = new ScrollPane(cardsPane);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(title, summaryLabel, scroll);
    }

    public void refresh(Fleet fleet, DistanceMatrix matrix) {
        cardsPane.getChildren().clear();
        if (fleet == null) return;

        for (Vehicle v : fleet.getVehicles()) {
            cardsPane.getChildren().add(createCard(v, matrix));
        }
        summaryLabel.setText(String.format("Fleet Size: %d | Active: %d | Total Capacity: %d units. Click any card to highlight on map.",
                fleet.size(), fleet.getActiveVehicleCount(), fleet.getTotalCapacity()));
    }

    private VBox createCard(Vehicle v, DistanceMatrix matrix) {
        VBox card = new VBox(9);
        card.getStyleClass().addAll("card", "card-interactive");
        card.setPadding(new Insets(14));
        card.setPrefWidth(310);

        Circle dot = new Circle(6, Color.web(v.getColorHex()));
        Label title = new Label(v.getName());
        title.getStyleClass().add("card-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label badge = new Label(v.getVehicleStatus().getLabel());
        badge.getStyleClass().setAll(switch (v.getVehicleStatus()) {
            case COMPLETED -> "badge-success";
            case EN_ROUTE, DELIVERING, RETURNING -> "badge-warning";
            case ASSIGNED -> "badge-info";
            case IDLE -> "badge-error";
        });
        HBox header = new HBox(8, dot, title, spacer, badge);
        header.setAlignment(Pos.CENTER_LEFT);

        double util = v.getCapacity() > 0 ? (double) v.getCurrentLoad() / v.getCapacity() : 0.0;
        ProgressBar bar = new ProgressBar(Math.min(1.0, util));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefHeight(8);
        bar.setStyle("-fx-accent: " + v.getColorHex() + ";");

        Route r = v.getAssignedRoute();
        int stops = r != null ? r.getStopCount() : 0;
        double dist = v.getRouteDistance(matrix);

        Label l1 = muted(String.format("Load: %d / %d (%.0f%%)   Remaining: %d",
                v.getCurrentLoad(), v.getCapacity(), util * 100.0, v.calculateRemainingCapacity()));
        Label l2 = muted(String.format("Stops: %d/%d done   Distance: %.1f u",
                v.getCompletedDeliveries(), stops, dist));
        Label l3 = muted(String.format("Est. Time: %.1f s | Traveled: %.1f u",
                dist / ProblemConfig.BASE_VEHICLE_SPEED, v.getDistanceTraveled()));
        Label l4 = muted("Route: " + (r != null && !r.isEmpty() ? r.toString() : "Unassigned"));
        l4.setWrapText(true);

        card.getChildren().addAll(header, bar, l1, l2, l3, l4);
        card.setOnMouseClicked(e -> { if (onHighlightVehicle != null) onHighlightVehicle.accept(v); });
        return card;
    }

    private Label muted(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("muted-label");
        return l;
    }
}
