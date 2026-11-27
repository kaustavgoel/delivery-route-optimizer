package com.deliveryoptimizer.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Results {
    private Results() {}

    public static class PathfindingResult {
        private final String algorithmName;
        private final Location source;
        private final Location target;
        private final List<Location> path;
        private final List<Location> exploredOrder;
        private final double totalDistance;
        private final int nodesExplored;
        private final int edgesRelaxed;
        private final long executionTimeNanos;
        private final boolean reachable;

        public PathfindingResult(String algorithmName, Location source, Location target,
                                 List<Location> path, List<Location> exploredOrder,
                                 double totalDistance, int nodesExplored, int edgesRelaxed,
                                 long executionTimeNanos, boolean reachable) {
            this.algorithmName = algorithmName;
            this.source = source;
            this.target = target;
            this.path = path != null ? List.copyOf(path) : Collections.emptyList();
            this.exploredOrder = exploredOrder != null ? List.copyOf(exploredOrder) : Collections.emptyList();
            this.totalDistance = totalDistance;
            this.nodesExplored = nodesExplored;
            this.edgesRelaxed = edgesRelaxed;
            this.executionTimeNanos = executionTimeNanos;
            this.reachable = reachable;
        }

        public String getAlgorithmName() { return algorithmName; }
        public Location getSource() { return source; }
        public Location getTarget() { return target; }
        public List<Location> getPath() { return path; }
        public List<Location> getExploredOrder() { return exploredOrder; }
        public double getTotalDistance() { return totalDistance; }
        public int getNodesExplored() { return nodesExplored; }
        public int getEdgesRelaxed() { return edgesRelaxed; }
        public double getExecutionTimeMs() { return executionTimeNanos / 1_000_000.0; }
        public boolean isReachable() { return reachable; }
    }

    public static class TraversalResult {
        private final String algorithmName;
        private final List<Location> traversalOrder;
        private final List<Location> path;
        private final boolean connectedAll;
        private final int reachableCount;
        private final int totalNodes;

        public TraversalResult(String algorithmName, List<Location> traversalOrder, List<Location> path,
                               boolean connectedAll, int reachableCount, int totalNodes) {
            this.algorithmName = algorithmName;
            this.traversalOrder = traversalOrder != null ? List.copyOf(traversalOrder) : Collections.emptyList();
            this.path = path != null ? List.copyOf(path) : Collections.emptyList();
            this.connectedAll = connectedAll;
            this.reachableCount = reachableCount;
            this.totalNodes = totalNodes;
        }

        public String getAlgorithmName() { return algorithmName; }
        public List<Location> getTraversalOrder() { return traversalOrder; }
        public List<Location> getPath() { return path; }
        public boolean isConnectedAll() { return connectedAll; }
        public int getReachableCount() { return reachableCount; }
        public int getTotalNodes() { return totalNodes; }
    }

    public static class OptimizationResult {
        private final String algorithmName;
        private final List<Route> routes;
        private final double initialDistance;
        private final double totalDistance;
        private final long executionTimeMs;
        private final int vehiclesUsed;
        private final double averageUtilizationPercent;
        private final boolean feasible;
        private final List<Double> convergenceHistory;

        public OptimizationResult(String algorithmName, List<Route> routes, double initialDistance,
                                  double totalDistance, long executionTimeMs, int vehiclesUsed,
                                  double averageUtilizationPercent, boolean feasible,
                                  List<Double> convergenceHistory) {
            this.algorithmName = algorithmName;
            this.routes = routes != null ? new ArrayList<>(routes) : Collections.emptyList();
            this.initialDistance = initialDistance;
            this.totalDistance = totalDistance;
            this.executionTimeMs = executionTimeMs;
            this.vehiclesUsed = vehiclesUsed;
            this.averageUtilizationPercent = averageUtilizationPercent;
            this.feasible = feasible;
            this.convergenceHistory = convergenceHistory != null
                    ? Collections.unmodifiableList(new ArrayList<>(convergenceHistory))
                    : Collections.emptyList();
        }

        public String getAlgorithmName() { return algorithmName; }
        public List<Route> getRoutes() { return Collections.unmodifiableList(routes); }
        public double getInitialDistance() { return initialDistance; }
        public double getTotalDistance() { return totalDistance; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public int getVehiclesUsed() { return vehiclesUsed; }
        public double getAverageUtilizationPercent() { return averageUtilizationPercent; }
        public boolean isFeasible() { return feasible; }
        public List<Double> getConvergenceHistory() { return convergenceHistory; }

        public int getTotalDeliveriesAssigned() {
            int count = 0;
            for (Route r : routes) count += r.getStopCount();
            return count;
        }
    }

    public static class ClusterResult {
        private final String algorithmName;
        private final List<List<Location>> clusters;
        private final List<double[]> centroids;
        private final boolean feasible;

        public ClusterResult(String algorithmName, List<List<Location>> clusters,
                             List<double[]> centroids, boolean feasible) {
            this.algorithmName = algorithmName;
            this.clusters = clusters != null ? clusters : Collections.emptyList();
            this.centroids = centroids != null ? centroids : Collections.emptyList();
            this.feasible = feasible;
        }

        public String getAlgorithmName() { return algorithmName; }
        public List<List<Location>> getClusters() { return clusters; }
        public List<double[]> getCentroids() { return centroids; }
        public boolean isFeasible() { return feasible; }
    }
}
