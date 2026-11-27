package com.deliveryoptimizer.algorithms;

import com.deliveryoptimizer.graph.DistanceMatrix;
import com.deliveryoptimizer.graph.RoadNetwork;
import com.deliveryoptimizer.model.Exceptions.InfeasibleRoutingException;
import com.deliveryoptimizer.model.Location;
import com.deliveryoptimizer.model.Location.Depot;
import com.deliveryoptimizer.model.Results.OptimizationResult;
import com.deliveryoptimizer.model.Route;
import com.deliveryoptimizer.model.Vehicle;
import com.deliveryoptimizer.model.Vehicle.Fleet;
import com.deliveryoptimizer.services.AppLogger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class AbstractRoutingSolver implements RoutingAlgorithm {

    protected final AppLogger logger = AppLogger.getInstance();

    protected void validateProblemFeasibility(RoadNetwork network, DistanceMatrix matrix, Fleet fleet) {
        if (network == null || matrix == null || fleet == null || network.getDepot() == null || fleet.size() <= 0) {
            throw new InfeasibleRoutingException("Network, distance matrix, depot, and fleet must be valid.");
        }
        int totalDemand = network.getTotalDeliveryDemand();
        int totalCapacity = fleet.getTotalCapacity();
        if (totalDemand > totalCapacity) {
            String msg = String.format(
                    "Infeasible problem: Total package demand (%d) exceeds total fleet capacity (%d).",
                    totalDemand, totalCapacity);
            logger.error(getAlgorithmName(), msg);
            throw new InfeasibleRoutingException(msg);
        }
        int maxCap = fleet.getVehicles().stream().mapToInt(Vehicle::getCapacity).max().orElse(0);
        for (Location loc : network.getDeliveryLocations()) {
            if (loc.getDemand() > maxCap) {
                String msg = String.format(
                        "Infeasible problem: Node %d demand (%d) exceeds max vehicle capacity (%d).",
                        loc.getId(), loc.getDemand(), maxCap);
                logger.error(getAlgorithmName(), msg);
                throw new InfeasibleRoutingException(msg);
            }
        }
    }

    protected double computeTotalDistance(List<Route> routes, DistanceMatrix matrix) {
        double sum = 0.0;
        for (Route r : routes) sum += r.calculateTotalDistance(matrix);
        return sum;
    }

    protected List<Route> padToFleetSize(List<Route> routes, Depot depot, int fleetSize) {
        List<Route> padded = new ArrayList<>(routes);
        while (padded.size() < fleetSize) padded.add(new Route(depot));
        return padded;
    }

    protected void verifyFeasibility(List<Route> routes, List<Location> deliveries, Fleet fleet) {
        if (routes.size() > fleet.size()) {
            throw new InfeasibleRoutingException("Routes exceed fleet size (" + fleet.size() + ").");
        }
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < routes.size(); i++) {
            Route r = routes.get(i);
            int cap = fleet.getVehicles().get(i).getCapacity();
            if (r.calculateTotalDemand() > cap) {
                throw new InfeasibleRoutingException("Vehicle " + (i + 1) + " exceeded capacity (" + cap + ").");
            }
            for (Location s : r.getStops()) {
                if (!seen.add(s.getId())) {
                    throw new InfeasibleRoutingException("Duplicate delivery Node " + s.getId());
                }
            }
        }
        if (seen.size() != deliveries.size()) {
            throw new InfeasibleRoutingException("Expected " + deliveries.size() + " deliveries, got " + seen.size());
        }
    }

    protected OptimizationResult buildResult(List<Route> routes, DistanceMatrix matrix, Fleet fleet,
                                             double initialDistance, long startTimeMs,
                                             List<Double> convergenceHistory) {
        for (Route r : routes) r.reconstructFullRoadPath(matrix);
        double totalDistance = computeTotalDistance(routes, matrix);
        int active = 0;
        double utilSum = 0.0;
        for (int i = 0; i < routes.size(); i++) {
            if (!routes.get(i).isEmpty()) {
                active++;
                utilSum += (routes.get(i).calculateTotalDemand() * 100.0) / fleet.getVehicles().get(i).getCapacity();
            }
        }
        double avgUtil = active > 0 ? utilSum / active : 0.0;
        long elapsed = Math.max(1L, System.currentTimeMillis() - startTimeMs);
        return new OptimizationResult(getAlgorithmName(), routes, initialDistance, totalDistance,
                elapsed, active, avgUtil, true, convergenceHistory);
    }

    protected List<Route> packFirstFitDecreasing(Depot depot, List<Location> deliveries, Fleet fleet) {
        List<Location> sorted = new ArrayList<>(deliveries);
        sorted.sort(Comparator.comparingInt(Location::getDemand).reversed());

        List<Route> routes = new ArrayList<>();
        for (int i = 0; i < fleet.size(); i++) routes.add(new Route(depot));

        List<Location> unplaced = new ArrayList<>();
        for (Location loc : sorted) {
            boolean placed = false;
            for (int i = 0; i < fleet.size(); i++) {
                if (routes.get(i).calculateTotalDemand() + loc.getDemand() <= fleet.getVehicles().get(i).getCapacity()) {
                    routes.get(i).addStop(loc);
                    placed = true;
                    break;
                }
            }
            if (!placed) unplaced.add(loc);
        }

        for (Location loc : unplaced) {
            if (!tryInsertViaSwap(loc, routes, fleet)) {
                throw new InfeasibleRoutingException(
                        "Unable to pack Node " + loc.getId() + " (demand=" + loc.getDemand()
                                + ") within " + fleet.size() + " vehicles.");
            }
        }
        return routes;
    }

    private boolean tryInsertViaSwap(Location target, List<Route> routes, Fleet fleet) {
        for (int i = 0; i < routes.size(); i++) {
            Route rI = routes.get(i);
            int capI = fleet.getVehicles().get(i).getCapacity();
            int loadI = rI.calculateTotalDemand();
            for (int idx = 0; idx < rI.getStopCount(); idx++) {
                Location cand = rI.getStops().get(idx);
                if (loadI - cand.getDemand() + target.getDemand() <= capI) {
                    for (int j = 0; j < routes.size(); j++) {
                        if (i != j && routes.get(j).calculateTotalDemand() + cand.getDemand() <= fleet.getVehicles().get(j).getCapacity()) {
                            rI.getMutableStops().remove(idx);
                            routes.get(j).addStop(cand);
                            rI.addStop(target);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
