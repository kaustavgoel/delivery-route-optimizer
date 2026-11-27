package com.deliveryoptimizer.algorithms;

import com.deliveryoptimizer.graph.DistanceMatrix;
import com.deliveryoptimizer.graph.RoadNetwork;
import com.deliveryoptimizer.model.Location;
import com.deliveryoptimizer.model.ProblemConfig;
import com.deliveryoptimizer.model.Results.OptimizationResult;
import com.deliveryoptimizer.model.Route;
import com.deliveryoptimizer.model.Vehicle.Fleet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class SimulatedAnnealingSolver extends AbstractRoutingSolver {

    @Override
    public OptimizationResult optimize(RoadNetwork network, DistanceMatrix matrix, Fleet fleet, ProblemConfig config) {
        long startMs = System.currentTimeMillis();
        validateProblemFeasibility(network, matrix, fleet);

        double temp = config != null ? config.getSaInitialTemp() : 1500.0;
        double cool = config != null ? config.getSaCoolingRate() : 0.965;
        int maxIter = config != null ? config.getSaMaxIterations() : 450;
        int cap = fleet.getVehicles().get(0).getCapacity();
        Random random = new Random((config != null ? config.getRandomSeed() : 2026L) + 202L);

        OptimizationResult init = new NearestNeighbourSolver(false, false).optimize(network, matrix, fleet, config);
        List<Route> cur = copyRoutes(init.getRoutes());
        double curCost = computeTotalDistance(cur, matrix);
        double initialCost = curCost;

        List<Route> best = copyRoutes(cur);
        double bestCost = curCost;
        List<Double> history = new ArrayList<>(maxIter + 1);
        history.add(bestCost);

        for (int iter = 1; iter <= maxIter && temp > 0.05; iter++) {
            List<Route> cand = copyRoutes(cur);
            if (applyRandomMove(cand, cap, random)) {
                double candCost = computeTotalDistance(cand, matrix);
                double delta = candCost - curCost;
                if (delta < 0 || random.nextDouble() < Math.exp(-delta / temp)) {
                    cur = cand;
                    curCost = candCost;
                    if (curCost < bestCost) {
                        bestCost = curCost;
                        best = copyRoutes(cur);
                    }
                }
            }
            temp *= cool;
            history.add(bestCost);
        }

        verifyFeasibility(best, network.getDeliveryLocations(), fleet);
        logger.success(getAlgorithmName(), String.format("SA finished: %.2f -> %.2f u.", initialCost, bestCost));
        return buildResult(best, matrix, fleet, initialCost, startMs, history);
    }

    private boolean applyRandomMove(List<Route> routes, int cap, Random r) {
        List<Integer> active = new ArrayList<>();
        for (int i = 0; i < routes.size(); i++) if (!routes.get(i).isEmpty()) active.add(i);
        if (active.isEmpty()) return false;

        int move = r.nextInt(4);
        if (move == 0 || routes.size() == 1) {
            List<Location> stops = routes.get(active.get(r.nextInt(active.size()))).getMutableStops();
            if (stops.size() < 2) return false;
            int a = r.nextInt(stops.size()), b = r.nextInt(stops.size());
            if (a == b) return false;
            for (int lo = Math.min(a, b), hi = Math.max(a, b); lo < hi; lo++, hi--) Collections.swap(stops, lo, hi);
            return true;
        } else if (move == 1) {
            List<Location> stops = routes.get(active.get(r.nextInt(active.size()))).getMutableStops();
            if (stops.size() < 2) return false;
            int a = r.nextInt(stops.size()), b = r.nextInt(stops.size());
            if (a == b) return false;
            Collections.swap(stops, a, b);
            return true;
        } else if (move == 2) {
            int from = active.get(r.nextInt(active.size())), to = r.nextInt(routes.size());
            if (from == to) return false;
            List<Location> src = routes.get(from).getMutableStops();
            int idx = r.nextInt(src.size());
            Location node = src.get(idx);
            Route dst = routes.get(to);
            if (dst.calculateTotalDemand() + node.getDemand() > cap) return false;
            src.remove(idx);
            dst.getMutableStops().add(r.nextInt(dst.getStopCount() + 1), node);
            return true;
        } else {
            if (active.size() < 2) return false;
            int rA = active.get(r.nextInt(active.size())), rB = active.get(r.nextInt(active.size()));
            if (rA == rB) return false;
            Route routeA = routes.get(rA), routeB = routes.get(rB);
            int iA = r.nextInt(routeA.getStopCount()), iB = r.nextInt(routeB.getStopCount());
            Location lA = routeA.getStops().get(iA), lB = routeB.getStops().get(iB);
            if (routeA.calculateTotalDemand() - lA.getDemand() + lB.getDemand() > cap
                    || routeB.calculateTotalDemand() - lB.getDemand() + lA.getDemand() > cap) return false;
            routeA.getMutableStops().set(iA, lB);
            routeB.getMutableStops().set(iB, lA);
            return true;
        }
    }

    private List<Route> copyRoutes(List<Route> routes) {
        List<Route> copy = new ArrayList<>(routes.size());
        for (Route r : routes) copy.add(new Route(r));
        return copy;
    }

    @Override
    public String getAlgorithmName() { return "Simulated Annealing"; }
}
