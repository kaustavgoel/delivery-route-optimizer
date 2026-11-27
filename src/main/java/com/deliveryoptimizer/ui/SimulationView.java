package com.deliveryoptimizer.ui;

import com.deliveryoptimizer.algorithms.AlgorithmFactory.PathfindingType;
import com.deliveryoptimizer.algorithms.AlgorithmFactory.RoutingType;
import com.deliveryoptimizer.algorithms.Clustering;
import com.deliveryoptimizer.graph.DistanceMatrix;
import com.deliveryoptimizer.graph.RoadNetwork;
import com.deliveryoptimizer.model.Location;
import com.deliveryoptimizer.model.ProblemConfig;
import com.deliveryoptimizer.model.Results.OptimizationResult;
import com.deliveryoptimizer.model.Vehicle;
import com.deliveryoptimizer.model.Vehicle.Fleet;
import com.deliveryoptimizer.services.AppLogger;
import com.deliveryoptimizer.services.NetworkService;
import com.deliveryoptimizer.services.OptimizationService;
import com.deliveryoptimizer.services.OptimizationService.OverallStats;
import com.deliveryoptimizer.services.VehicleSimulator;
import com.deliveryoptimizer.services.VehicleSimulator.EventType;
import com.deliveryoptimizer.services.VehicleSimulator.SimulationState;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class SimulationView extends BorderPane {

    private final NetworkService networkService;
    private final OptimizationService optimizationService;
    private final VehicleSimulator simulator;
    private final MapCanvas mapCanvas = new MapCanvas();

    private final Label nodesVal = kpiVal(), vehiclesVal = kpiVal(), distanceVal = kpiVal(), etaVal = kpiVal();
    private final Label demandVal = kpiVal(), utilVal = kpiVal(), completedVal = kpiVal(), pendingVal = kpiVal();

    private final ComboBox<PathfindingType> pathfindingBox = new ComboBox<>(FXCollections.observableArrayList(PathfindingType.values()));
    private final ComboBox<RoutingType> routingBox = new ComboBox<>(FXCollections.observableArrayList(RoutingType.values()));
    private final Button runOptBtn = new Button("Run Optimization");
    private final Button addNodeBtn = new Button("Add Node");
    private final Button removeNodeBtn = new Button("Remove Node");
    private final CheckBox showClustersCb = new CheckBox("Show K-Means Cluster Overlay");
    private final Label inspectorTitle = new Label("Inspector — Select a Node or Vehicle");
    private final Label inspectorBody = new Label("Click any node or vehicle on the map to inspect its details, demand, route, and status.");
    private final Label simStatusLabel = new Label("Status: Ready");
    private final Label speedLabel = new Label("1.0x");

    private RoadNetwork network;
    private DistanceMatrix distanceMatrix;
    private Fleet fleet;
    private OptimizationResult lastResult;
    private Location selectedLocation;
    private Vehicle selectedVehicle;

    private Runnable onDataChanged;
    private Consumer<OptimizationResult> onOptimizationDone;

    public SimulationView(NetworkService networkService, OptimizationService optimizationService, VehicleSimulator simulator) {
        this.networkService = networkService;
        this.optimizationService = optimizationService;
        this.simulator = simulator;
        setPadding(new Insets(14));

        pathfindingBox.setValue(PathfindingType.A_STAR);
        pathfindingBox.setMaxWidth(Double.MAX_VALUE);
        routingBox.setValue(RoutingType.CLARKE_WRIGHT_LOCAL_SEARCH);
        routingBox.setMaxWidth(Double.MAX_VALUE);

        buildLayout();
        wireEvents();
        simulator.addListener(this::onSimulationUpdate);
    }

    private void buildLayout() {
        FlowPane toolbar = new FlowPane(8, 8);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10, 12, 10, 12));
        toolbar.getStyleClass().add("card");

        Button randomBtn = makeBtn("Random Map", "btn-secondary", e -> {
            ProblemConfig cfg = optimizationService.getActiveConfig();
            cfg.setRandomSeed((System.currentTimeMillis() % 900000L) + 1000L);
            regenerateNetwork();
        });
        addNodeBtn.getStyleClass().add("btn-secondary");
        addNodeBtn.setMinWidth(Region.USE_PREF_SIZE);
        addNodeBtn.setOnAction(e -> {
            boolean next = !mapCanvas.isAddNodeMode();
            mapCanvas.setAddNodeMode(next);
            addNodeBtn.setText(next ? "Cancel Add" : "Add Node");
        });

        removeNodeBtn.getStyleClass().add("btn-secondary");
        removeNodeBtn.setMinWidth(Region.USE_PREF_SIZE);
        removeNodeBtn.setDisable(true);
        removeNodeBtn.setOnAction(e -> removeSelectedNode());

        Button resetBtn = makeBtn("Reset", "btn-secondary", e -> {
            simulator.stop();
            mapCanvas.setHighlightedVehicleId(null);
            simStatusLabel.setText(simulator.getExpectedDeliveries() > 0 ? "Status: Ready" : "Status: Idle");
            refreshAll();
        });
        runOptBtn.getStyleClass().add("btn-primary");
        runOptBtn.setMinWidth(Region.USE_PREF_SIZE);
        runOptBtn.setOnAction(e -> runOptimizationAsync());

        Button startBtn = makeBtn("Start Animation", "btn-success", e -> {
            if (simulator.getExpectedDeliveries() == 0) {
                showError("No Routes Ready", "Run Optimization first before starting animation.");
                return;
            }
            if (simulator.getState() != SimulationState.RUNNING) simulator.start();
        });
        Button pauseBtn = makeBtn("Pause", "btn-warning", e -> simulator.pause());
        Button resumeBtn = makeBtn("Resume", "btn-secondary", e -> simulator.resume());
        Button stopBtn = makeBtn("Stop", "btn-danger", e -> simulator.stop());

        toolbar.getChildren().addAll(randomBtn, addNodeBtn, removeNodeBtn, resetBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                runOptBtn, startBtn, pauseBtn, resumeBtn, stopBtn);

        VBox center = new VBox(12, toolbar, mapCanvas, buildKpiGrid());
        VBox.setVgrow(mapCanvas, Priority.ALWAYS);
        setCenter(center);

        VBox algoCard = new VBox(8);
        algoCard.getStyleClass().add("card");
        algoCard.setPadding(new Insets(12));
        Label algoHdr = new Label("Two-Level Solver Pipeline");
        algoHdr.getStyleClass().add("card-title");
        CheckBox showWeightsCb = new CheckBox("Show Road Distances");
        showWeightsCb.setOnAction(e -> mapCanvas.setShowRoadWeights(showWeightsCb.isSelected()));
        showClustersCb.setOnAction(e -> {
            if (showClustersCb.isSelected() && network != null && fleet != null) {
                ProblemConfig cfg = optimizationService.getActiveConfig();
                mapCanvas.setClusterOverlay(Clustering.kMeans(network.getDepot(), network.getDeliveryLocations(),
                        fleet.size(), cfg.getVehicleCapacity(), cfg.getRandomSeed()), true);
            } else {
                mapCanvas.setClusterOverlay(null, false);
            }
        });
        algoCard.getChildren().addAll(algoHdr, muted("Level 1 — Road Pathfinding:"), pathfindingBox,
                muted("Level 2 — Vehicle Routing:"), routingBox, showWeightsCb, showClustersCb);

        VBox simCard = new VBox(8);
        simCard.getStyleClass().add("card");
        simCard.setPadding(new Insets(12));
        Label simHdr = new Label("Simulation Control");
        simHdr.getStyleClass().add("card-title");
        simStatusLabel.getStyleClass().add("badge-info");
        Slider speedSlider = new Slider(0.25, 5.0, 1.0);
        speedSlider.valueProperty().addListener((o, a, b) -> {
            simulator.setSpeedMultiplier(b.doubleValue());
            speedLabel.setText(String.format("%.2fx", b.doubleValue()));
        });
        HBox speedRow = new HBox(8, new Label("Speed:"), speedSlider, speedLabel);
        speedRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(speedSlider, Priority.ALWAYS);
        simCard.getChildren().addAll(simHdr, simStatusLabel, speedRow);

        VBox inspCard = new VBox(8);
        inspCard.getStyleClass().add("card");
        inspCard.setPadding(new Insets(12));
        inspectorTitle.getStyleClass().add("card-title");
        inspectorBody.getStyleClass().add("muted-label");
        inspectorBody.setWrapText(true);
        Button showAllBtn = new Button("Show All Vehicle Routes");
        showAllBtn.getStyleClass().add("btn-secondary");
        showAllBtn.setMaxWidth(Double.MAX_VALUE);
        showAllBtn.setOnAction(e -> mapCanvas.setHighlightedVehicleId(null));
        inspCard.getChildren().addAll(inspectorTitle, inspectorBody, showAllBtn);

        VBox right = new VBox(12, algoCard, simCard, inspCard);
        right.setPrefWidth(305);
        right.setPadding(new Insets(0, 0, 0, 12));
        ScrollPane rightScroll = new ScrollPane(right);
        rightScroll.setFitToWidth(true);
        rightScroll.setPrefWidth(320);
        setRight(rightScroll);
    }

    private GridPane buildKpiGrid() {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(12);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25.0);
            cc.setHgrow(Priority.ALWAYS);
            g.getColumnConstraints().add(cc);
        }
        g.add(kpiCard("DELIVERY NODES", nodesVal, "#318BFF"), 0, 0);
        g.add(kpiCard("ACTIVE FLEET", vehiclesVal, "#8957FF"), 1, 0);
        g.add(kpiCard("TOTAL DISTANCE", distanceVal, "#35D98A"), 2, 0);
        g.add(kpiCard("EST. TIME", etaVal, "#FFB547"), 3, 0);
        g.add(kpiCard("TOTAL DEMAND", demandVal, "#318BFF"), 0, 1);
        g.add(kpiCard("AVG UTILIZATION", utilVal, "#8957FF"), 1, 1);
        g.add(kpiCard("COMPLETED", completedVal, "#35D98A"), 2, 1);
        g.add(kpiCard("PENDING", pendingVal, "#FF5364"), 3, 1);
        return g;
    }

    private void wireEvents() {
        mapCanvas.setOnLocationSelected(loc -> {
            selectedLocation = loc;
            selectedVehicle = null;
            removeNodeBtn.setDisable(loc.isDepot());
            showLocation(loc);
        });
        mapCanvas.setOnVehicleSelected(v -> {
            selectedVehicle = v;
            selectedLocation = null;
            removeNodeBtn.setDisable(true);
            showVehicle(v);
        });
        mapCanvas.setOnMapClickedForNewNode((wx, wy) -> {
            ProblemConfig cfg = optimizationService.getActiveConfig();
            int demand = Math.min(cfg.getVehicleCapacity(), Math.max(cfg.getMinDemand(), (cfg.getMinDemand() + cfg.getMaxDemand()) / 2));
            Location added = networkService.addManualNode(network, wx, wy, demand, cfg.getDefaultPriority());
            mapCanvas.setAddNodeMode(false);
            addNodeBtn.setText("Add Node");
            selectedLocation = added;
            mapCanvas.setSelectedLocationId(added.getId());
            showLocation(added);
            distanceMatrix = optimizationService.buildDistanceMatrix(network, pathfindingBox.getValue());
            mapCanvas.setNetworkAndFleet(network, fleet);
            runOptimizationAsync();
        });
    }

    public void regenerateNetwork() {
        simulator.stop();
        ProblemConfig cfg = optimizationService.getActiveConfig();
        network = networkService.generateRandomNetwork(cfg);
        fleet = networkService.createFleet(network, cfg);
        lastResult = null;
        selectedLocation = null;
        selectedVehicle = null;
        removeNodeBtn.setDisable(true);
        showClustersCb.setSelected(false);
        mapCanvas.setHighlightedVehicleId(null);
        mapCanvas.setClusterOverlay(null, false);
        distanceMatrix = optimizationService.buildDistanceMatrix(network, pathfindingBox.getValue());
        mapCanvas.setNetworkAndFleet(network, fleet);
        simulator.prepare(fleet, distanceMatrix);
        refreshAll();
        if (onDataChanged != null) onDataChanged.run();
        runOptimizationAsync();
    }

    public void runOptimizationAsync() {
        if (network == null || fleet == null) return;
        simulator.stop();
        runOptBtn.setDisable(true);
        simStatusLabel.setText("Status: Optimizing...");
        PathfindingType pf = pathfindingBox.getValue();
        RoutingType rt = routingBox.getValue();
        ProblemConfig cfg = optimizationService.getActiveConfig().copy();

        Task<OptimizationResult> task = new Task<>() {
            @Override protected OptimizationResult call() {
                distanceMatrix = optimizationService.buildDistanceMatrix(network, pf);
                return optimizationService.runOptimization(network, distanceMatrix, fleet, cfg, rt);
            }
        };
        task.setOnSucceeded(e -> {
            lastResult = task.getValue();
            runOptBtn.setDisable(false);
            mapCanvas.setNetworkAndFleet(network, fleet);
            simulator.prepare(fleet, distanceMatrix);
            simStatusLabel.setText(String.format("Ready — %.1f u (%d ms)", lastResult.getTotalDistance(), lastResult.getExecutionTimeMs()));
            refreshAll();
            if (onOptimizationDone != null) onOptimizationDone.accept(lastResult);
            if (onDataChanged != null) onDataChanged.run();
        });
        task.setOnFailed(e -> {
            runOptBtn.setDisable(false);
            lastResult = null;
            Throwable ex = task.getException();
            String msg = ex != null ? ex.getMessage() : "Optimization failed.";
            AppLogger.getInstance().error("Optimizer", msg);
            simStatusLabel.setText("Error: Infeasible");
            if (onDataChanged != null) onDataChanged.run();
            String tip = (network != null && fleet != null && network.getTotalDeliveryDemand() > fleet.getTotalCapacity())
                    ? String.format("\n\nTip: Increase capacity in Settings (need >= %d per vehicle) or reduce nodes.",
                    (int) Math.ceil((double) network.getTotalDeliveryDemand() / Math.max(1, fleet.size())) + 5)
                    : "";
            showError("Optimization Error", msg + tip);
        });
        Thread t = new Thread(task, "vrp-worker");
        t.setDaemon(true);
        t.start();
    }

    private void removeSelectedNode() {
        if (selectedLocation == null || selectedLocation.isDepot()) return;
        simulator.stop();
        if (networkService.removeNode(network, selectedLocation.getId())) {
            selectedLocation = null;
            removeNodeBtn.setDisable(true);
            inspectorTitle.setText("Inspector — Node Removed");
            inspectorBody.setText("Node removed and road connectivity verified.");
            distanceMatrix = optimizationService.buildDistanceMatrix(network, pathfindingBox.getValue());
            mapCanvas.setNetworkAndFleet(network, fleet);
            runOptimizationAsync();
        }
    }

    private void onSimulationUpdate(SimulationState state, EventType type) {
        Runnable ui = () -> {
            mapCanvas.render();
            refreshAll();
            simStatusLabel.setText(String.format("Sim: %s (%.1fs | %d/%d done)",
                    state.getLabel(), simulator.getElapsedSimSeconds(),
                    simulator.getCompletedDeliveries(), simulator.getExpectedDeliveries()));
            if (selectedLocation != null) showLocation(selectedLocation);
            else if (selectedVehicle != null) showVehicle(selectedVehicle);
            if (type != EventType.FRAME_TICK && onDataChanged != null) onDataChanged.run();
        };
        if (Platform.isFxApplicationThread()) ui.run();
        else Platform.runLater(ui);
    }

    public void refreshAll() {
        mapCanvas.render();
        OverallStats s = optimizationService.computeOverallStats(network, fleet, distanceMatrix);
        nodesVal.setText(String.valueOf(s.totalNodes));
        vehiclesVal.setText(s.activeVehicles + " / " + s.totalVehicles);
        distanceVal.setText(String.format("%.1f u", s.totalDistance));
        etaVal.setText(String.format("%.1f s", s.estTimeSec));
        demandVal.setText(String.valueOf(s.totalDemand));
        utilVal.setText(String.format("%.1f%%", s.avgUtilization));
        completedVal.setText(String.valueOf(s.completed));
        pendingVal.setText(String.valueOf(s.pending));
    }

    public void highlightVehicleOnMap(int id) {
        mapCanvas.setHighlightedVehicleId(id);
        Vehicle v = fleet != null ? fleet.getVehicleById(id) : null;
        if (v != null) { selectedVehicle = v; showVehicle(v); }
    }

    private void showLocation(Location loc) {
        if (loc.isDepot()) {
            inspectorTitle.setText("Central Depot (Node 0)");
            inspectorBody.setText(String.format("Coordinates: (%.1f, %.1f)\nFleet Capacity: %d\nTotal Demand: %d",
                    loc.getX(), loc.getY(), fleet != null ? fleet.getTotalCapacity() : 0,
                    network != null ? network.getTotalDeliveryDemand() : 0));
        } else {
            inspectorTitle.setText("Delivery Node #" + loc.getId());
            inspectorBody.setText(String.format("Coordinates: (%.1f, %.1f)\nDemand: %d | Priority: %s\nVehicle: %s\nStatus: %s",
                    loc.getX(), loc.getY(), loc.getDemand(), loc.getPriority().getLabel(),
                    loc.getAssignedVehicleId() != null ? "Vehicle " + loc.getAssignedVehicleId() : "Unassigned",
                    loc.getDeliveryStatus().getLabel()));
        }
    }

    private void showVehicle(Vehicle v) {
        inspectorTitle.setText(v.getName() + " — " + v.getVehicleStatus().getLabel());
        inspectorBody.setText(String.format("Load: %d/%d (Rem: %d)\nStops: %d (%d done)\nRoute Distance: %.2f u\nRoute: %s",
                v.getCurrentLoad(), v.getCapacity(), v.calculateRemainingCapacity(),
                v.getAssignedRoute() != null ? v.getAssignedRoute().getStopCount() : 0,
                v.getCompletedDeliveries(), v.getRouteDistance(distanceMatrix),
                v.getAssignedRoute() != null ? v.getAssignedRoute().toString() : "None"));
    }

    private Button makeBtn(String text, String style, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button b = new Button(text);
        b.getStyleClass().add(style);
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(action);
        return b;
    }

    private static Label kpiVal() { Label l = new Label("0"); l.getStyleClass().add("kpi-value"); return l; }
    private static Label muted(String t) { Label l = new Label(t); l.getStyleClass().add("muted-label"); return l; }

    private VBox kpiCard(String title, Label val, String accent) {
        VBox box = new VBox(4, new Label(title), val);
        box.getChildren().get(0).getStyleClass().add("kpi-label");
        box.getStyleClass().add("card");
        box.setPadding(new Insets(12, 14, 12, 14));
        box.setStyle("-fx-border-color: " + accent + "44; -fx-border-width: 1;");
        return box;
    }

    private void showError(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(msg);
        alert.show();
    }

    public RoadNetwork getNetwork() { return network; }
    public DistanceMatrix getDistanceMatrix() { return distanceMatrix; }
    public Fleet getFleet() { return fleet; }
    public OptimizationResult getLastResult() { return lastResult; }
    public void setOnDataChanged(Runnable cb) { this.onDataChanged = cb; }
    public void setOnOptimizationDone(Consumer<OptimizationResult> cb) { this.onOptimizationDone = cb; }
}
