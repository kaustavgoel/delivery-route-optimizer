package com.deliveryoptimizer.algorithms;

import com.deliveryoptimizer.graph.DistanceMatrix;
import com.deliveryoptimizer.graph.RoadNetwork;
import com.deliveryoptimizer.model.Location;
import com.deliveryoptimizer.model.Location.Depot;
import com.deliveryoptimizer.model.ProblemConfig;
import com.deliveryoptimizer.model.Results.OptimizationResult;
import com.deliveryoptimizer.model.Route;
import com.deliveryoptimizer.model.Vehicle.Fleet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NearestNeighbourSolver extends AbstractRoutingSolver {

    private final boolean applyTwoOpt;
    private final boolean applyOrOpt;

    public NearestNeighbourSolver(boolean applyTwoOpt, boolean applyOrOpt) {
        this.applyTwoOpt = applyTwoOpt;
        this.applyOrOpt = applyOrOpt;
    }

    @Override
    public OptimizationResult optimize(RoadNetwork network, DistanceMatrix matrix, Fleet fleet, ProblemConfig config) {
        long startMs = System.currentTimeMillis();
        validateProblemFeasibility(network, matrix, fleet);

        Depot depot = network.getDepot();
        List<Location> deliveries = network.getDeliveryLocations();
        Set<Integer> unvisited = new HashSet<>();
        for (Location loc : deliveries) unvisited.add(loc.getId());

        logger.info(getAlgorithmName(), "Routing " + deliveries.size() + " deliveries across " + fleet.size() + " vehicles.");

        List<Route> routes = new ArrayList<>();
        for (int v = 0; v < fleet.size() && !unvisited.isEmpty(); v++) {
            int remCap = fleet.getVehicles().get(v).getCapacity();
            Route route = new Route(depot);
            int curId = depot.getId();

            while (true) {
                Location best = null;
                double bestDist = Double.POSITIVE_INFINITY;
                for (Location cand : deliveries) {
                    if (unvisited.contains(cand.getId()) && cand.getDemand() <= remCap) {
                        double d = matrix.getDistance(curId, cand.getId());
                        if (d < bestDist) {
                            bestDist = d;
                            best = cand;
                        }
                    }
                }
                if (best == null) break;
                route.addStop(best);
                unvisited.remove(best.getId());
                remCap -= best.getDemand();
                curId = best.getId();
            }
            routes.add(route);
        }

        if (!unvisited.isEmpty()) {
            logger.warning(getAlgorithmName(), "Greedy pass hit fragmentation; applying capacity packing.");
            routes = packFirstFitDecreasing(depot, deliveries, fleet);
        }

        routes = padToFleetSize(routes, depot, fleet.size());
        double initialDist = computeTotalDistance(routes, matrix);

        if (applyTwoOpt) routes = LocalSearch.twoOptAll(routes, matrix, config, true);
        if (applyOrOpt) routes = LocalSearch.orOptAll(routes, matrix, config, fleet.getVehicles().get(0).getCapacity(), true);

        verifyFeasibility(routes, deliveries, fleet);
        double finalDist = computeTotalDistance(routes, matrix);
        logger.success(getAlgorithmName(), String.format("Completed: total distance = %.2f u.", finalDist));

        return buildResult(routes, matrix, fleet, initialDist, startMs, List.of(initialDist, finalDist));
    }

    @Override
    public String getAlgorithmName() {
        if (applyTwoOpt && applyOrOpt) return "Nearest Neighbour + 2-Opt + Or-Opt";
        if (applyTwoOpt) return "Nearest Neighbour + 2-Opt";
        return "Nearest Neighbour";
    }
}
