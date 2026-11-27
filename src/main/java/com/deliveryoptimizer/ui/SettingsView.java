package com.deliveryoptimizer.ui;

import com.deliveryoptimizer.model.Location.Priority;
import com.deliveryoptimizer.model.ProblemConfig;
import com.deliveryoptimizer.services.AppLogger;
import com.deliveryoptimizer.services.OptimizationService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class SettingsView extends VBox {

    private final OptimizationService optimizationService;
    private final Runnable onApply;
    private final Label feedback = new Label();

    private final TextField nodeCountField = new TextField();
    private final TextField vehicleCountField = new TextField();
    private final TextField vehicleCapField = new TextField();
    private final TextField minDemandField = new TextField();
    private final TextField maxDemandField = new TextField();
    private final ComboBox<Priority> priorityBox = new ComboBox<>(FXCollections.observableArrayList(Priority.values()));
    private final TextField roadDensityField = new TextField();
    private final TextField seedField = new TextField();

    private final TextField gaPopField = new TextField();
    private final TextField gaGenField = new TextField();
    private final TextField gaMutField = new TextField();
    private final TextField gaCrossField = new TextField();
    private final TextField saTempField = new TextField();
    private final TextField saCoolField = new TextField();
    private final TextField saIterField = new TextField();
    private final TextField maxOptIterField = new TextField();

    public SettingsView(OptimizationService optimizationService, Runnable onApply) {
        this.optimizationService = optimizationService;
        this.onApply = onApply;
        setSpacing(14);
        setPadding(new Insets(16));

        Label title = new Label("Problem & Algorithm Settings");
        title.getStyleClass().add("section-title");

        GridPane g1 = grid();
        addRow(g1, 0, "Delivery Nodes (3–100):", nodeCountField);
        addRow(g1, 1, "Fleet Vehicles (1–12):", vehicleCountField);
        addRow(g1, 2, "Vehicle Capacity:", vehicleCapField);
        addRow(g1, 3, "Min Package Demand:", minDemandField);
        addRow(g1, 4, "Max Package Demand:", maxDemandField);
        addRow(g1, 5, "Default Priority:", priorityBox);
        addRow(g1, 6, "Road Density (0.05–1.0):", roadDensityField);
        addRow(g1, 7, "Random Seed:", seedField);

        GridPane g2 = grid();
        addRow(g2, 0, "GA Population Size:", gaPopField);
        addRow(g2, 1, "GA Generations:", gaGenField);
        addRow(g2, 2, "GA Mutation Rate (0–1):", gaMutField);
        addRow(g2, 3, "GA Crossover Rate (0–1):", gaCrossField);
        addRow(g2, 4, "SA Initial Temperature:", saTempField);
        addRow(g2, 5, "SA Cooling Rate (0–1):", saCoolField);
        addRow(g2, 6, "SA Max Iterations:", saIterField);
        addRow(g2, 7, "Local Search Max Iterations:", maxOptIterField);

        VBox c1 = card("Network & Fleet Configuration", g1);
        VBox c2 = card("Metaheuristic & Local Search Parameters", g2);
        HBox cols = new HBox(16, c1, c2);
        HBox.setHgrow(c1, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(c2, javafx.scene.layout.Priority.ALWAYS);

        Button applyBtn = new Button("Apply & Regenerate Network");
        applyBtn.getStyleClass().add("btn-primary");
        applyBtn.setOnAction(e -> applySettings());

        Button autoBtn = new Button("Auto-Fit Capacity");
        autoBtn.getStyleClass().add("btn-purple");
        autoBtn.setOnAction(e -> autoFitCapacity());

        Button resetBtn = new Button("Restore Defaults");
        resetBtn.getStyleClass().add("btn-secondary");
        resetBtn.setOnAction(e -> {
            ProblemConfig defaults = new ProblemConfig();
            optimizationService.setActiveConfig(defaults);
            loadFromConfig(defaults);
            feedback.setText("Default configuration restored.");
            feedback.getStyleClass().setAll("badge-info");
        });

        ScrollPane scroll = new ScrollPane(new VBox(14, title, cols, new HBox(12, applyBtn, autoBtn, resetBtn, feedback)));
        scroll.setFitToWidth(true);
        getChildren().add(scroll);
        loadFromConfig(optimizationService.getActiveConfig());
    }

    public void loadFromConfig(ProblemConfig cfg) {
        nodeCountField.setText(String.valueOf(cfg.getNodeCount()));
        vehicleCountField.setText(String.valueOf(cfg.getVehicleCount()));
        vehicleCapField.setText(String.valueOf(cfg.getVehicleCapacity()));
        minDemandField.setText(String.valueOf(cfg.getMinDemand()));
        maxDemandField.setText(String.valueOf(cfg.getMaxDemand()));
        priorityBox.setValue(cfg.getDefaultPriority());
        roadDensityField.setText(String.valueOf(cfg.getRoadDensity()));
        seedField.setText(String.valueOf(cfg.getRandomSeed()));
        gaPopField.setText(String.valueOf(cfg.getGaPopulationSize()));
        gaGenField.setText(String.valueOf(cfg.getGaGenerations()));
        gaMutField.setText(String.valueOf(cfg.getGaMutationRate()));
        gaCrossField.setText(String.valueOf(cfg.getGaCrossoverRate()));
        saTempField.setText(String.valueOf(cfg.getSaInitialTemp()));
        saCoolField.setText(String.valueOf(cfg.getSaCoolingRate()));
        saIterField.setText(String.valueOf(cfg.getSaMaxIterations()));
        maxOptIterField.setText(String.valueOf(cfg.getMaxOptimizationIterations()));
    }

    private void applySettings() {
        try {
            ProblemConfig cfg = new ProblemConfig();
            cfg.setNodeCount(Integer.parseInt(nodeCountField.getText().trim()));
            cfg.setVehicleCount(Integer.parseInt(vehicleCountField.getText().trim()));
            cfg.setVehicleCapacity(Integer.parseInt(vehicleCapField.getText().trim()));
            cfg.setMinDemand(Integer.parseInt(minDemandField.getText().trim()));
            cfg.setMaxDemand(Integer.parseInt(maxDemandField.getText().trim()));
            cfg.setDefaultPriority(priorityBox.getValue());
            cfg.setRoadDensity(Double.parseDouble(roadDensityField.getText().trim()));
            cfg.setRandomSeed(Long.parseLong(seedField.getText().trim()));
            cfg.setGaPopulationSize(Integer.parseInt(gaPopField.getText().trim()));
            cfg.setGaGenerations(Integer.parseInt(gaGenField.getText().trim()));
            cfg.setGaMutationRate(Double.parseDouble(gaMutField.getText().trim()));
            cfg.setGaCrossoverRate(Double.parseDouble(gaCrossField.getText().trim()));
            cfg.setSaInitialTemp(Double.parseDouble(saTempField.getText().trim()));
            cfg.setSaCoolingRate(Double.parseDouble(saCoolField.getText().trim()));
            cfg.setSaMaxIterations(Integer.parseInt(saIterField.getText().trim()));
            cfg.setMaxOptimizationIterations(Integer.parseInt(maxOptIterField.getText().trim()));
            cfg.validate();

            optimizationService.setActiveConfig(cfg);
            feedback.setText("Configuration validated and applied.");
            feedback.getStyleClass().setAll("badge-success");
            AppLogger.getInstance().success("Settings", "Applied configuration (seed=" + cfg.getRandomSeed() + ").");
            if (onApply != null) onApply.run();
        } catch (RuntimeException ex) {
            feedback.setText(ex.getMessage());
            feedback.getStyleClass().setAll("badge-error");
            AppLogger.getInstance().error("Settings", ex.getMessage());
        }
    }

    private void autoFitCapacity() {
        try {
            int nodes = Integer.parseInt(nodeCountField.getText().trim());
            int vehicles = Math.max(1, Integer.parseInt(vehicleCountField.getText().trim()));
            int maxDem = Math.max(1, Integer.parseInt(maxDemandField.getText().trim()));
            int minDem = Math.max(1, Integer.parseInt(minDemandField.getText().trim()));
            int rec = Math.max(maxDem, (int) Math.ceil((nodes * ((minDem + maxDem) / 2.0) * 1.25) / vehicles));
            vehicleCapField.setText(String.valueOf(rec));
            feedback.setText("Auto-fitted capacity to " + rec + " (Total = " + (rec * vehicles) + ").");
            feedback.getStyleClass().setAll("badge-info");
        } catch (NumberFormatException ex) {
            feedback.setText("Enter valid numbers for Nodes, Vehicles, and Demand first.");
            feedback.getStyleClass().setAll("badge-error");
        }
    }

    private GridPane grid() { GridPane g = new GridPane(); g.setHgap(12); g.setVgap(10); return g; }

    private void addRow(GridPane g, int row, String label, javafx.scene.Node field) {
        Label l = new Label(label);
        l.getStyleClass().add("muted-label");
        g.add(l, 0, row);
        g.add(field, 1, row);
    }

    private VBox card(String heading, GridPane grid) {
        VBox box = new VBox(12);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(16));
        Label h = new Label(heading);
        h.getStyleClass().add("card-title");
        box.getChildren().addAll(h, grid);
        return box;
    }
}
