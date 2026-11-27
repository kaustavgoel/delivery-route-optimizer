package com.deliveryoptimizer.algorithms;

public final class AlgorithmFactory {

    public enum PathfindingType {
        DIJKSTRA("Dijkstra's Algorithm"),
        A_STAR("A* Search");

        private final String displayName;
        PathfindingType(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
    }

    public enum RoutingType {
        NEAREST_NEIGHBOUR("Nearest Neighbour"),
        NEAREST_NEIGHBOUR_2OPT("Nearest Neighbour + 2-Opt"),
        NEAREST_NEIGHBOUR_2OPT_OROPT("Nearest Neighbour + 2-Opt + Or-Opt"),
        CLARKE_WRIGHT("Clarke-Wright Savings"),
        CLARKE_WRIGHT_LOCAL_SEARCH("Clarke-Wright + Local Search"),
        GENETIC_ALGORITHM("Genetic Algorithm"),
        SIMULATED_ANNEALING("Simulated Annealing"),
        KMEANS_ROUTING("K-Means Clustering + 2-Opt"),
        SWEEP_ROUTING("Sweep Algorithm + 2-Opt");

        private final String displayName;
        RoutingType(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
    }

    private AlgorithmFactory() {}

    public static PathfindingAlgorithm createPathfinder(PathfindingType type) {
        return switch (type) {
            case DIJKSTRA -> new DijkstraSolver();
            case A_STAR -> new AStarSolver();
        };
    }

    public static RoutingAlgorithm createRoutingAlgorithm(RoutingType type) {
        return switch (type) {
            case NEAREST_NEIGHBOUR -> new NearestNeighbourSolver(false, false);
            case NEAREST_NEIGHBOUR_2OPT -> new NearestNeighbourSolver(true, false);
            case NEAREST_NEIGHBOUR_2OPT_OROPT -> new NearestNeighbourSolver(true, true);
            case CLARKE_WRIGHT -> new ClarkeWrightSolver(false);
            case CLARKE_WRIGHT_LOCAL_SEARCH -> new ClarkeWrightSolver(true);
            case GENETIC_ALGORITHM -> new GeneticAlgorithmSolver();
            case SIMULATED_ANNEALING -> new SimulatedAnnealingSolver();
            case KMEANS_ROUTING -> new ClusteredRoutingSolver(true);
            case SWEEP_ROUTING -> new ClusteredRoutingSolver(false);
        };
    }
}
