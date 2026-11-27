package com.deliveryoptimizer.ui;

import com.deliveryoptimizer.algorithms.AlgorithmFactory.RoutingType;
import com.deliveryoptimizer.graph.DistanceMatrix;
import com.deliveryoptimizer.graph.RoadNetwork;
import com.deliveryoptimizer.model.ProblemConfig;
import com.deliveryoptimizer.model.Results.OptimizationResult;
import com.deliveryoptimizer.model.Vehicle;
import com.deliveryoptimizer.model.Vehicle.Fleet;
import com.deliveryoptimizer.services.AppLogger;
import com.deliveryoptimizer.services.OptimizationService;
import com.deliveryoptimizer.services.OptimizationService.ComparisonRow;
import com.deliveryoptimizer.services.VehicleSimulator;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.Chart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;

public class AnalyticsView extends VBox {

    private final OptimizationService optimizationService;
    private final TableView<ComparisonRow> table = new TableView<>();
    private final Button benchmarkBtn = new Button("Run Full 7-Algorithm Comparison");
    private final Label statusLabel = new Label("Click to benchmark all 7 routing pipelines on the active network.");

    private final BarChart<String, Number> distanceChart = barChart("Total Route Distance Comparison", "Algorithm", "Distance (u)");
    private final BarChart<String, Number> timeChart = barChart("Execution Time Comparison", "Algorithm", "Time (ms)");
    private final LineChart<Number, Number> gaChart = lineChart("Genetic Algorithm Convergence", "Generation", "Best Distance");
    private final LineChart<Number, Number> saChart = lineChart("Simulated Annealing Convergence", "Iteration", "Best Distance");
    private final PieChart loadPieChart = new PieChart();
    private final AreaChart<Number, Number> completionChart = areaChart("Deliveries Completed Over Simulation Time", "Sim Time (s)", "Completed");

    private RoadNetwork network;
    private DistanceMatrix matrix;
    private Fleet fleet;
    private VehicleSimulator simulator;

    @SuppressWarnings("unchecked")
    public AnalyticsView(OptimizationService optimizationService) {
        this.optimizationService = optimizationService;
        setSpacing(14);
        setPadding(new Insets(16));

        Label title = new Label("Analytics & Algorithm Comparison");
        title.getStyleClass().add("section-title");

        benchmarkBtn.getStyleClass().add("btn-primary");
        benchmarkBtn.setOnAction(e -> runBenchmarkAsync());
        statusLabel.getStyleClass().add("muted-label");
        HBox bar = new HBox(12, benchmarkBtn, statusLabel);
        bar.setAlignment(Pos.CENTER_LEFT);

        table.setPrefHeight(245);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().addAll(
                col("Algorithm", r -> r.algorithmName),
                col("Total Distance", r -> String.format("%.2f u", r.totalDistance)),
                col("Exec Time", r -> r.executionTimeMs + " ms"),
                col("Improvement vs NN", r -> String.format("%+.2f%%", r.improvementPercent)),
                col("Avg Utilization", r -> String.format("%.1f%%", r.utilizationPercent)),
                col("Vehicles Used", r -> String.valueOf(r.vehiclesUsed))
        );

        VBox tableCard = new VBox(10, bar, table);
        tableCard.getStyleClass().add("card");
        tableCard.setPadding(new Insets(14));

        loadPieChart.setTitle("Vehicle Load Distribution");
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.add(wrapChart(distanceChart), 0, 0);
        grid.add(wrapChart(timeChart), 1, 0);
        grid.add(wrapChart(gaChart), 0, 1);
        grid.add(wrapChart(saChart), 1, 1);
        grid.add(wrapChart(loadPieChart), 0, 2);
        grid.add(wrapChart(completionChart), 1, 2);

        ScrollPane scroll = new ScrollPane(new VBox(14, title, tableCard, grid));
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);
    }

    public void setContext(RoadNetwork network, DistanceMatrix matrix, Fleet fleet, VehicleSimulator simulator) {
        this.network = network;
        this.matrix = matrix;
        this.fleet = fleet;
        this.simulator = simulator;
        refreshLiveCharts();
    }

    public void refreshLiveCharts() {
        if (fleet == null) return;
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        for (Vehicle v : fleet.getVehicles()) {
            if (v.getCurrentLoad() > 0) {
                pieData.add(new PieChart.Data(v.getName() + " (" + v.getCurrentLoad() + "/" + v.getCapacity() + ")", v.getCurrentLoad()));
            }
        }
        if (pieData.isEmpty()) pieData.add(new PieChart.Data("Unassigned", 1));
        loadPieChart.setData(pieData);

        if (simulator != null) {
            XYChart.Series<Number, Number> s = new XYChart.Series<>();
            s.setName("Completed");
            for (double[] pt : simulator.getCompletionTimeline()) s.getData().add(new XYChart.Data<>(pt[0], pt[1]));
            completionChart.getData().setAll(s);
        }
    }

    public void updateConvergence(OptimizationResult result) {
        if (result == null || result.getConvergenceHistory().isEmpty()) return;
        if (result.getAlgorithmName().contains("Genetic")) plotLine(gaChart, "GA Best Distance", result.getConvergenceHistory());
        else if (result.getAlgorithmName().contains("Simulated Annealing")) plotLine(saChart, "SA Best Distance", result.getConvergenceHistory());
    }

    public void runBenchmarkAsync() {
        if (network == null || matrix == null) return;
        benchmarkBtn.setDisable(true);
        statusLabel.setText("Running 7 algorithms on identical network & fleet...");
        ProblemConfig cfg = optimizationService.getActiveConfig().copy();

        Task<Map<RoutingType, OptimizationResult>> task = new Task<>() {
            @Override protected Map<RoutingType, OptimizationResult> call() {
                return optimizationService.runBenchmarkComparison(network, matrix, cfg);
            }
        };
        task.setOnSucceeded(e -> {
            benchmarkBtn.setDisable(false);
            Map<RoutingType, OptimizationResult> results = task.getValue();
            List<ComparisonRow> rows = optimizationService.buildComparisonRows(results);
            table.setItems(FXCollections.observableArrayList(rows));

            XYChart.Series<String, Number> dSeries = new XYChart.Series<>();
            XYChart.Series<String, Number> tSeries = new XYChart.Series<>();
            ObservableList<String> cats = FXCollections.observableArrayList();
            for (ComparisonRow r : rows) {
                String shortName = r.algorithmName
                        .replace("Nearest Neighbour + 2-Opt + Or-Opt", "NN+2Opt+Or")
                        .replace("Nearest Neighbour + 2-Opt", "NN+2Opt")
                        .replace("Nearest Neighbour", "NN")
                        .replace("Clarke-Wright + Local Search", "CW+LS")
                        .replace("Clarke-Wright Savings", "Clarke-W")
                        .replace("Genetic Algorithm", "GA")
                        .replace("Simulated Annealing", "SA");
                cats.add(shortName);
                dSeries.getData().add(new XYChart.Data<>(shortName, r.totalDistance));
                tSeries.getData().add(new XYChart.Data<>(shortName, r.executionTimeMs));
            }
            ((CategoryAxis) distanceChart.getXAxis()).setCategories(cats);
            ((CategoryAxis) timeChart.getXAxis()).setCategories(FXCollections.observableArrayList(cats));
            distanceChart.getData().setAll(dSeries);
            timeChart.getData().setAll(tSeries);

            if (results.containsKey(RoutingType.GENETIC_ALGORITHM)) {
                plotLine(gaChart, "GA Best Distance", results.get(RoutingType.GENETIC_ALGORITHM).getConvergenceHistory());
            }
            if (results.containsKey(RoutingType.SIMULATED_ANNEALING)) {
                plotLine(saChart, "SA Best Distance", results.get(RoutingType.SIMULATED_ANNEALING).getConvergenceHistory());
            }
            statusLabel.setText("Benchmark completed across all 7 algorithms.");
        });
        task.setOnFailed(e -> {
            benchmarkBtn.setDisable(false);
            String msg = task.getException() != null ? task.getException().getMessage() : "Benchmark failed.";
            statusLabel.setText("Error: " + msg);
            AppLogger.getInstance().error("Benchmark", msg);
        });
        Thread th = new Thread(task, "benchmark-worker");
        th.setDaemon(true);
        th.start();
    }

    private void plotLine(LineChart<Number, Number> chart, String name, List<Double> history) {
        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        s.setName(name);
        int step = Math.max(1, history.size() / 120);
        for (int i = 0; i < history.size(); i += step) s.getData().add(new XYChart.Data<>(i, history.get(i)));
        if (!history.isEmpty() && (history.size() - 1) % step != 0) {
            s.getData().add(new XYChart.Data<>(history.size() - 1, history.get(history.size() - 1)));
        }
        chart.getData().setAll(s);
    }

    private TableColumn<ComparisonRow, String> col(String title, java.util.function.Function<ComparisonRow, String> fn) {
        TableColumn<ComparisonRow, String> c = new TableColumn<>(title);
        c.setCellValueFactory(d -> new SimpleStringProperty(fn.apply(d.getValue())));
        return c;
    }

    private VBox wrapChart(Chart c) {
        c.setAnimated(false);
        c.setPrefSize(480, 290);
        VBox box = new VBox(c);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(10));
        GridPane.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private BarChart<String, Number> barChart(String t, String x, String y) {
        CategoryAxis xa = new CategoryAxis(); xa.setLabel(x);
        NumberAxis ya = new NumberAxis(); ya.setLabel(y);
        BarChart<String, Number> c = new BarChart<>(xa, ya);
        c.setTitle(t); c.setLegendVisible(false);
        return c;
    }

    private LineChart<Number, Number> lineChart(String t, String x, String y) {
        NumberAxis xa = new NumberAxis(); xa.setLabel(x);
        NumberAxis ya = new NumberAxis(); ya.setLabel(y); ya.setForceZeroInRange(false);
        LineChart<Number, Number> c = new LineChart<>(xa, ya);
        c.setTitle(t); c.setCreateSymbols(false);
        return c;
    }

    private AreaChart<Number, Number> areaChart(String t, String x, String y) {
        NumberAxis xa = new NumberAxis(); xa.setLabel(x);
        NumberAxis ya = new NumberAxis(); ya.setLabel(y);
        AreaChart<Number, Number> c = new AreaChart<>(xa, ya);
        c.setTitle(t); c.setCreateSymbols(false);
        return c;
    }
}
