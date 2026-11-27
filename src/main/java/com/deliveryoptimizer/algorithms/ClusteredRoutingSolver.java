package com.deliveryoptimizer.algorithms;

import com.deliveryoptimizer.graph.DistanceMatrix;
import com.deliveryoptimizer.graph.RoadNetwork;
import com.deliveryoptimizer.model.Location;
import com.deliveryoptimizer.model.Location.Depot;
import com.deliveryoptimizer.model.ProblemConfig;
import com.deliveryoptimizer.model.Results.ClusterResult;
import com.deliveryoptimizer.model.Results.OptimizationResult;
import com.deliveryoptimizer.model.Route;
import com.deliveryoptimizer.model.Vehicle.Fleet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClusteredRoutingSolver extends AbstractRoutingSolver {

    private final boolean useKMeans;

    public ClusteredRoutingSolver(boolean useKMeans) {
        this.useKMeans = useKMeans;
    }

    @Override
    public OptimizationResult optimize(RoadNetwork network, DistanceMatrix matrix, Fleet fleet, ProblemConfig config) {
        long startMs = System.currentTimeMillis();
        validateProblemFeasibility(network, matrix, fleet);

        Depot depot = network.getDepot();
        List<Location> deliveries = network.getDeliveryLocations();
        int cap = fleet.getVehicles().get(0).getCapacity();
        long seed = config != null ? config.getRandomSeed() : 2026L;

        ClusterResult cr = useKMeans
                ? Clustering.kMeans(depot, deliveries, fleet.size(), cap, seed)
                : Clustering.sweep(depot, deliveries, fleet.size(), cap);

        List<Route> routes;
        if (!cr.isFeasible()) {
            routes = packFirstFitDecreasing(depot, deliveries, fleet);
        } else {
            routes = new ArrayList<>();
            for (List<Location> cluster : cr.getClusters()) routes.add(orderCluster(depot, cluster, matrix));
            routes = padToFleetSize(routes, depot, fleet.size());
        }

        double initialDist = computeTotalDistance(routes, matrix);
        routes = LocalSearch.twoOptAll(routes, matrix, config, false);
        verifyFeasibility(routes, deliveries, fleet);
        double finalDist = computeTotalDistance(routes, matrix);

        logger.success(getAlgorithmName(), String.format("Completed: %.2f -> %.2f u.", initialDist, finalDist));
        return buildResult(routes, matrix, fleet, initialDist, startMs, List.of(initialDist, finalDist));
    }

    private Route orderCluster(Depot depot, List<Location> cluster, DistanceMatrix matrix) {
        Route r = new Route(depot);
        Set<Integer> rem = new HashSet<>();
        for (Location l : cluster) rem.add(l.getId());
        int curId = depot.getId();
        while (!rem.isEmpty()) {
            Location nearest = null;
            double best = Double.POSITIVE_INFINITY;
            for (Location l : cluster) {
                if (rem.contains(l.getId())) {
                    double d = matrix.getDistance(curId, l.getId());
                    if (d < best) { best = d; nearest = l; }
                }
            }
            if (nearest == null) break;
            r.addStop(nearest);
            rem.remove(nearest.getId());
            curId = nearest.getId();
        }
        r.calculateTotalDistance(matrix);
        return r;
    }

    @Override
    public String getAlgorithmName() {
        return useKMeans ? "K-Means Clustering + 2-Opt" : "Sweep Algorithm + 2-Opt";
    }
}
