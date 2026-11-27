package com.deliveryoptimizer.services;

import com.deliveryoptimizer.algorithms.AlgorithmFactory;
import com.deliveryoptimizer.algorithms.AlgorithmFactory.PathfindingType;
import com.deliveryoptimizer.algorithms.AlgorithmFactory.RoutingType;
import com.deliveryoptimizer.algorithms.PathfindingAlgorithm;
import com.deliveryoptimizer.algorithms.RoutingAlgorithm;
import com.deliveryoptimizer.datastructures.BinaryHeap;
import com.deliveryoptimizer.datastructures.KnapsackSolver;
import com.deliveryoptimizer.graph.DistanceMatrix;
import com.deliveryoptimizer.graph.RoadNetwork;
import com.deliveryoptimizer.model.Location;
import com.deliveryoptimizer.model.Location.DeliveryStatus;
import com.deliveryoptimizer.model.ProblemConfig;
import com.deliveryoptimizer.model.Results.OptimizationResult;
import com.deliveryoptimizer.model.Route;
import com.deliveryoptimizer.model.Route.Delivery;
import com.deliveryoptimizer.model.Vehicle;
import com.deliveryoptimizer.model.Vehicle.Fleet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OptimizationService {

    public static class OverallStats {
        public final int totalNodes, totalVehicles, activeVehicles, totalDemand, completed, pending;
        public final double totalDistance, estTimeSec, avgUtilization;

        public OverallStats(int totalNodes, int totalVehicles, int activeVehicles, double totalDistance,
                            double estTimeSec, int totalDemand, double avgUtilization, int completed, int pending) {
            this.totalNodes = totalNodes;
            this.totalVehicles = totalVehicles;
            this.activeVehicles = activeVehicles;
            this.totalDistance = totalDistance;
            this.estTimeSec = estTimeSec;
            this.totalDemand = totalDemand;
            this.avgUtilization = avgUtilization;
            this.completed = completed;
            this.pending = pending;
        }
    }

    public static class ComparisonRow {
        public final String algorithmName;
        public final double totalDistance;
        public final long executionTimeMs;
        public final double improvementPercent;
        public final double utilizationPercent;
        public final int vehiclesUsed;

        public ComparisonRow(String algorithmName, double totalDistance, long executionTimeMs,
                             double improvementPercent, double utilizationPercent, int vehiclesUsed) {
            this.algorithmName = algorithmName;
            this.totalDistance = totalDistance;
            this.executionTimeMs = executionTimeMs;
            this.improvementPercent = improvementPercent;
            this.utilizationPercent = utilizationPercent;
            this.vehiclesUsed = vehiclesUsed;
        }
    }

    private final AppLogger logger = AppLogger.getInstance();
    private final KnapsackSolver knapsackSolver = new KnapsackSolver();
    private ProblemConfig activeConfig = new ProblemConfig();

    public ProblemConfig getActiveConfig() { return activeConfig; }
    public void setActiveConfig(ProblemConfig config) { if (config != null) this.activeConfig = config; }

    public DistanceMatrix buildDistanceMatrix(RoadNetwork network, PathfindingType type) {
        PathfindingAlgorithm pf = AlgorithmFactory.createPathfinder(type);
        logger.info("DistanceMatrix", "Building distance matrix using " + pf.getAlgorithmName() + "...");
        DistanceMatrix matrix = new DistanceMatrix(network, pf);
        logger.success("DistanceMatrix", String.format("Matrix (%dx%d) built in %d ms.",
                matrix.size(), matrix.size(), matrix.getComputationTimeMs()));
        return matrix;
    }

    public OptimizationResult runOptimization(RoadNetwork network, DistanceMatrix matrix, Fleet fleet,
                                              ProblemConfig config, RoutingType routingType) {
        network.resetDeliveryStates();
        fleet.clearAllRoutes();
        RoutingAlgorithm solver = AlgorithmFactory.createRoutingAlgorithm(routingType);
        OptimizationResult res = solver.optimize(network, matrix, fleet, config);
        List<Route> routes = res.getRoutes();
        for (int i = 0; i < fleet.size() && i < routes.size(); i++) {
            fleet.getVehicles().get(i).assignRoute(routes.get(i));
        }
        return res;
    }

    public Map<RoutingType, OptimizationResult> runBenchmarkComparison(RoadNetwork network,
                                                                       DistanceMatrix matrix,
                                                                       ProblemConfig config) {
        RoutingType[] suite = {
                RoutingType.NEAREST_NEIGHBOUR,
                RoutingType.NEAREST_NEIGHBOUR_2OPT,
                RoutingType.NEAREST_NEIGHBOUR_2OPT_OROPT,
                RoutingType.CLARKE_WRIGHT,
                RoutingType.CLARKE_WRIGHT_LOCAL_SEARCH,
                RoutingType.GENETIC_ALGORITHM,
                RoutingType.SIMULATED_ANNEALING
        };
        Map<RoutingType, OptimizationResult> results = new LinkedHashMap<>();
        logger.info("Benchmark", "Running 7-algorithm comparison on identical network & fleet...");
        for (RoutingType type : suite) {
            Fleet tempFleet = new Fleet(network.getDepot(), config.getVehicleCount(), config.getVehicleCapacity());
            results.put(type, AlgorithmFactory.createRoutingAlgorithm(type).optimize(network, matrix, tempFleet, config));
        }
        logger.success("Benchmark", "Completed 7-algorithm comparison.");
        return results;
    }

    public List<Delivery> prioritizeDeliveries(RoadNetwork network) {
        BinaryHeap<Delivery> maxHeap = BinaryHeap.maxHeap();
        int idx = 1;
        for (Location loc : network.getDeliveryLocations()) maxHeap.insert(new Delivery(idx++, loc));
        return maxHeap.drainSorted();
    }

    public KnapsackSolver.KnapsackResult selectHighValueBatch(RoadNetwork network, int capacity) {
        return knapsackSolver.selectOptimalDeliveries(prioritizeDeliveries(network), capacity);
    }

    public OverallStats computeOverallStats(RoadNetwork network, Fleet fleet, DistanceMatrix matrix) {
        if (network == null || fleet == null) return new OverallStats(0, 0, 0, 0, 0, 0, 0, 0, 0);
        int completed = 0, pending = 0, demand = 0;
        List<Location> deliveries = network.getDeliveryLocations();
        for (Location l : deliveries) {
            demand += l.getDemand();
            if (l.getDeliveryStatus() == DeliveryStatus.COMPLETED) completed++;
            else pending++;
        }
        double totalDist = 0, maxDist = 0, utilSum = 0;
        int active = 0;
        for (Vehicle v : fleet.getVehicles()) {
            double d = v.getRouteDistance(matrix);
            totalDist += d;
            maxDist = Math.max(maxDist, d);
            if (v.getAssignedRoute() != null && !v.getAssignedRoute().isEmpty()) {
                active++;
                utilSum += v.getUtilizationPercentage();
            }
        }
        return new OverallStats(deliveries.size(), fleet.size(), active, totalDist,
                maxDist / ProblemConfig.BASE_VEHICLE_SPEED, demand,
                active > 0 ? utilSum / active : 0.0, completed, pending);
    }

    public List<ComparisonRow> buildComparisonRows(Map<?, OptimizationResult> results) {
        List<ComparisonRow> rows = new ArrayList<>();
        if (results == null || results.isEmpty()) return rows;
        double baseline = results.values().iterator().next().getTotalDistance();
        for (OptimizationResult res : results.values()) {
            double imp = baseline > 0 ? ((baseline - res.getTotalDistance()) / baseline) * 100.0 : 0.0;
            rows.add(new ComparisonRow(res.getAlgorithmName(), res.getTotalDistance(),
                    res.getExecutionTimeMs(), imp, res.getAverageUtilizationPercent(), res.getVehiclesUsed()));
        }
        return rows;
    }
}
