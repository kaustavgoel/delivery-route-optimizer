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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClarkeWrightSolver extends AbstractRoutingSolver {

    private static final class Saving implements Comparable<Saving> {
        final Location i, j;
        final double value;
        Saving(Location i, Location j, double value) { this.i = i; this.j = j; this.value = value; }
        @Override public int compareTo(Saving o) { return Double.compare(o.value, this.value); }
    }

    private final boolean applyLocalSearch;

    public ClarkeWrightSolver(boolean applyLocalSearch) {
        this.applyLocalSearch = applyLocalSearch;
    }

    @Override
    public OptimizationResult optimize(RoadNetwork network, DistanceMatrix matrix, Fleet fleet, ProblemConfig config) {
        long startMs = System.currentTimeMillis();
        validateProblemFeasibility(network, matrix, fleet);

        Depot depot = network.getDepot();
        List<Location> deliveries = network.getDeliveryLocations();
        int cap = fleet.getVehicles().get(0).getCapacity();

        List<Route> activeRoutes = new ArrayList<>();
        Map<Integer, Route> nodeToRoute = new HashMap<>();
        for (Location loc : deliveries) {
            Route r = new Route(depot);
            r.addStop(loc);
            activeRoutes.add(r);
            nodeToRoute.put(loc.getId(), r);
        }
        double initialDist = computeTotalDistance(activeRoutes, matrix);

        List<Saving> savings = new ArrayList<>();
        for (int a = 0; a < deliveries.size(); a++) {
            Location li = deliveries.get(a);
            double d0i = matrix.getDistance(depot, li);
            for (int b = a + 1; b < deliveries.size(); b++) {
                Location lj = deliveries.get(b);
                savings.add(new Saving(li, lj, d0i + matrix.getDistance(depot, lj) - matrix.getDistance(li, lj)));
            }
        }
        Collections.sort(savings);

        int merges = 0;
        for (Saving s : savings) {
            if (s.value <= 0 && activeRoutes.size() <= fleet.size()) break;
            Route rI = nodeToRoute.get(s.i.getId()), rJ = nodeToRoute.get(s.j.getId());
            if (rI == null || rJ == null || rI == rJ) continue;
            if (rI.calculateTotalDemand() + rJ.calculateTotalDemand() > cap) continue;

            List<Location> sI = rI.getMutableStops(), sJ = rJ.getMutableStops();
            boolean iEnd = sI.get(0).getId() == s.i.getId() || sI.get(sI.size() - 1).getId() == s.i.getId();
            boolean jEnd = sJ.get(0).getId() == s.j.getId() || sJ.get(sJ.size() - 1).getId() == s.j.getId();
            if (!iEnd || !jEnd) continue;

            if (sI.get(0).getId() == s.i.getId() && sI.size() > 1) Collections.reverse(sI);
            if (sJ.get(sJ.size() - 1).getId() == s.j.getId() && sJ.size() > 1) Collections.reverse(sJ);

            for (Location stop : sJ) {
                sI.add(stop);
                nodeToRoute.put(stop.getId(), rI);
            }
            activeRoutes.remove(rJ);
            merges++;
            if (merges <= 6) {
                logger.info(getAlgorithmName(), String.format("Merged (%d, %d): S=%.2f, load=%d/%d.",
                        s.i.getId(), s.j.getId(), s.value, rI.calculateTotalDemand(), cap));
            }
        }

        if (activeRoutes.size() > fleet.size()) {
            consolidate(activeRoutes, fleet.size(), cap, depot, deliveries, fleet);
        }

        List<Route> finalRoutes = padToFleetSize(activeRoutes, depot, fleet.size());
        if (applyLocalSearch) {
            finalRoutes = LocalSearch.twoOptAll(finalRoutes, matrix, config, true);
            finalRoutes = LocalSearch.orOptAll(finalRoutes, matrix, config, cap, true);
        }

        verifyFeasibility(finalRoutes, deliveries, fleet);
        double finalDist = computeTotalDistance(finalRoutes, matrix);
        logger.success(getAlgorithmName(), String.format("Completed (%d merges): %.2f -> %.2f u.",
                merges, initialDist, finalDist));

        return buildResult(finalRoutes, matrix, fleet, initialDist, startMs, List.of(initialDist, finalDist));
    }

    private void consolidate(List<Route> routes, int limit, int cap, Depot depot, List<Location> deliveries, Fleet fleet) {
        boolean merged = true;
        while (routes.size() > limit && merged) {
            merged = false;
            routes.sort(Comparator.comparingInt(Route::calculateTotalDemand));
            for (int i = 0; i < routes.size() && !merged; i++) {
                for (int j = routes.size() - 1; j > i; j--) {
                    if (routes.get(i).calculateTotalDemand() + routes.get(j).calculateTotalDemand() <= cap) {
                        routes.get(j).getMutableStops().addAll(routes.get(i).getStops());
                        routes.remove(i);
                        merged = true;
                        break;
                    }
                }
            }
        }
        if (routes.size() > limit) {
            routes.clear();
            routes.addAll(packFirstFitDecreasing(depot, deliveries, fleet));
        }
    }

    @Override
    public String getAlgorithmName() {
        return applyLocalSearch ? "Clarke-Wright + Local Search" : "Clarke-Wright Savings";
    }
}
