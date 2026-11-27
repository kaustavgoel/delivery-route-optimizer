package com.deliveryoptimizer.ui;

import com.deliveryoptimizer.services.NetworkService;
import com.deliveryoptimizer.services.OptimizationService;
import com.deliveryoptimizer.services.VehicleSimulator;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

public class MainDashboard {

    public enum Section {
        SIMULATION("Simulation"),
        ANALYTICS("Analytics"),
        FLEET("Fleet Management"),
        LOGS("Live Logs"),
        SETTINGS("Settings");

        private final String title;
        Section(String title) { this.title = title; }
        public String getTitle() { return title; }
    }

    private final BorderPane root = new BorderPane();
    private final StackPane content = new StackPane();
    private final Map<Section, Button> navButtons = new LinkedHashMap<>();
    private final Map<Section, Node> views = new LinkedHashMap<>();
    private final Label headerBadge = new Label("Two-Level VRP Optimization Ready");

    public MainDashboard() {
        NetworkService networkService = new NetworkService();
        OptimizationService optimizationService = new OptimizationService();
        VehicleSimulator simulator = new VehicleSimulator();

        SimulationView simView = new SimulationView(networkService, optimizationService, simulator);
        AnalyticsView analyticsView = new AnalyticsView(optimizationService);
        FleetView fleetView = new FleetView(vehicle -> {
            simView.highlightVehicleOnMap(vehicle.getVehicleId());
            showSection(Section.SIMULATION);
        });
        LiveLogsView logsView = new LiveLogsView();
        SettingsView settingsView = new SettingsView(optimizationService, () -> {
            simView.regenerateNetwork();
            showSection(Section.SIMULATION);
        });

        simView.setOnDataChanged(() -> {
            settingsView.loadFromConfig(optimizationService.getActiveConfig());
            fleetView.refresh(simView.getFleet(), simView.getDistanceMatrix());
            analyticsView.setContext(simView.getNetwork(), simView.getDistanceMatrix(), simView.getFleet(), simulator);
            if (simView.getLastResult() != null) {
                var r = simView.getLastResult();
                headerBadge.setText(String.format("%s | Distance: %.1f u | Active Vehicles: %d",
                        r.getAlgorithmName(), r.getTotalDistance(), r.getVehiclesUsed()));
            } else if (simView.getNetwork() != null && simView.getFleet() != null
                    && simView.getNetwork().getTotalDeliveryDemand() > simView.getFleet().getTotalCapacity()) {
                headerBadge.setText("Infeasible — Demand Exceeds Fleet Capacity");
            }
        });
        simView.setOnOptimizationDone(analyticsView::updateConvergence);

        views.put(Section.SIMULATION, simView);
        views.put(Section.ANALYTICS, analyticsView);
        views.put(Section.FLEET, fleetView);
        views.put(Section.LOGS, logsView);
        views.put(Section.SETTINGS, settingsView);

        root.setLeft(buildSidebar());
        root.setTop(buildTopBar());
        root.setCenter(content);

        showSection(Section.SIMULATION);
        simView.regenerateNetwork();
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(8);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(220);
        sidebar.setPadding(new Insets(18, 12, 18, 12));

        Label brand = new Label("ROUTE OPTIMIZER");
        brand.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 16px; -fx-font-weight: bold;");
        Label sub = new Label("Multi-Algorithm VRP Simulator");
        sub.setStyle("-fx-text-fill: #318BFF; -fx-font-size: 11px; -fx-font-weight: bold;");
        VBox brandBox = new VBox(3, brand, sub);
        brandBox.setPadding(new Insets(0, 6, 16, 6));
        sidebar.getChildren().add(brandBox);

        for (Section s : Section.values()) {
            Button btn = new Button(s.getTitle());
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.getStyleClass().add("nav-button");
            btn.setOnAction(e -> showSection(s));
            navButtons.put(s, btn);
            sidebar.getChildren().add(btn);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label footer = new Label("Java 17 • JavaFX 21\nBTech III Sem OOP & DSA");
        footer.getStyleClass().add("muted-label");
        footer.setPadding(new Insets(10, 6, 0, 6));
        sidebar.getChildren().addAll(spacer, footer);
        return sidebar;
    }

    private HBox buildTopBar() {
        HBox topbar = new HBox(12);
        topbar.getStyleClass().add("topbar");
        topbar.setPadding(new Insets(12, 18, 12, 18));
        topbar.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Logistics Command Center");
        title.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 15px; -fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        headerBadge.getStyleClass().add("badge-info");
        topbar.getChildren().addAll(title, spacer, headerBadge);
        return topbar;
    }

    public void showSection(Section section) {
        Node view = views.get(section);
        if (view != null) content.getChildren().setAll(view);
        navButtons.forEach((sec, btn) -> btn.getStyleClass().setAll(sec == section ? "nav-button-active" : "nav-button"));
    }

    public Parent getRoot() { return root; }
}
