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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class GeneticAlgorithmSolver extends AbstractRoutingSolver {

    private static final class Individual {
        final List<Location> genes;
        List<Route> routes;
        double fitnessCost;
        double roadDistance;
        int penalty;
        Individual(List<Location> genes) { this.genes = genes; }
    }

    @Override
    public OptimizationResult optimize(RoadNetwork network, DistanceMatrix matrix, Fleet fleet, ProblemConfig config) {
        long startMs = System.currentTimeMillis();
        validateProblemFeasibility(network, matrix, fleet);

        Depot depot = network.getDepot();
        List<Location> deliveries = network.getDeliveryLocations();
        int popSize = config != null ? config.getGaPopulationSize() : 60;
        int generations = config != null ? config.getGaGenerations() : 120;
        double crossRate = config != null ? config.getGaCrossoverRate() : 0.85;
        double mutRate = config != null ? config.getGaMutationRate() : 0.18;
        int cap = fleet.getVehicles().get(0).getCapacity();
        Random random = new Random((config != null ? config.getRandomSeed() : 2026L) + 101L);

        OptimizationResult nn = new NearestNeighbourSolver(false, false).optimize(network, matrix, fleet, config);
        List<Location> seedGenes = new ArrayList<>();
        for (Route r : nn.getRoutes()) seedGenes.addAll(r.getStops());

        List<Individual> pop = new ArrayList<>(popSize);
        if (seedGenes.size() == deliveries.size()) {
            Individual ind = new Individual(seedGenes);
            evaluate(ind, depot, matrix, fleet, cap, deliveries.size());
            pop.add(ind);
        }
        while (pop.size() < popSize) {
            List<Location> perm = new ArrayList<>(deliveries);
            Collections.shuffle(perm, random);
            Individual ind = new Individual(perm);
            evaluate(ind, depot, matrix, fleet, cap, deliveries.size());
            pop.add(ind);
        }

        pop.sort(Comparator.comparingDouble(i -> i.fitnessCost));
        Individual best = pop.get(0);
        double initialBest = best.roadDistance;
        List<Double> history = new ArrayList<>(generations + 1);
        history.add(best.roadDistance);

        for (int gen = 1; gen <= generations; gen++) {
            List<Individual> next = new ArrayList<>(popSize);
            next.add(pop.get(0));
            if (popSize > 1) next.add(pop.get(1));

            while (next.size() < popSize) {
                Individual p1 = tournament(pop, random), p2 = tournament(pop, random);
                List<Location> childGenes = (random.nextDouble() < crossRate && deliveries.size() >= 2)
                        ? orderCrossover(p1.genes, p2.genes, random)
                        : new ArrayList<>(p1.genes);
                if (random.nextDouble() < mutRate && childGenes.size() >= 2) mutate(childGenes, random);
                Individual child = new Individual(childGenes);
                evaluate(child, depot, matrix, fleet, cap, deliveries.size());
                next.add(child);
            }
            next.sort(Comparator.comparingDouble(i -> i.fitnessCost));
            pop = next;
            if (pop.get(0).fitnessCost < best.fitnessCost) best = pop.get(0);
            history.add(best.roadDistance);
        }

        List<Route> finalRoutes = best.routes;
        if (finalRoutes.size() > fleet.size() || best.penalty > 0) {
            finalRoutes = packFirstFitDecreasing(depot, deliveries, fleet);
        }
        finalRoutes = LocalSearch.twoOptAll(padToFleetSize(finalRoutes, depot, fleet.size()), matrix, config, false);
        verifyFeasibility(finalRoutes, deliveries, fleet);

        double finalDist = computeTotalDistance(finalRoutes, matrix);
        history.set(history.size() - 1, Math.min(history.get(history.size() - 1), finalDist));
        logger.success(getAlgorithmName(), String.format("GA converged (%d gens): %.2f -> %.2f u.",
                generations, initialBest, finalDist));
        return buildResult(finalRoutes, matrix, fleet, initialBest, startMs, history);
    }

    private void evaluate(Individual ind, Depot depot, DistanceMatrix matrix, Fleet fleet, int cap, int expected) {
        List<Route> routes = new ArrayList<>();
        Route cur = new Route(depot);
        for (Location loc : ind.genes) {
            if (cur.calculateTotalDemand() + loc.getDemand() <= cap) cur.addStop(loc);
            else {
                routes.add(cur);
                cur = new Route(depot);
                cur.addStop(loc);
            }
        }
        if (!cur.isEmpty()) routes.add(cur);

        while (routes.size() > fleet.size()) {
            Route overflow = routes.get(routes.size() - 1);
            boolean moved = false;
            for (int i = overflow.getStopCount() - 1; i >= 0; i--) {
                Location stop = overflow.getStops().get(i);
                for (int r = 0; r < fleet.size(); r++) {
                    if (routes.get(r).calculateTotalDemand() + stop.getDemand() <= cap) {
                        routes.get(r).addStop(stop);
                        overflow.getMutableStops().remove(i);
                        moved = true;
                        break;
                    }
                }
            }
            if (overflow.isEmpty()) routes.remove(routes.size() - 1);
            else if (!moved) break;
        }

        double dist = computeTotalDistance(routes, matrix);
        Set<Integer> seen = new HashSet<>();
        int penalty = Math.max(0, routes.size() - fleet.size()) * 5;
        for (Route r : routes) {
            if (r.calculateTotalDemand() > cap) penalty += r.calculateTotalDemand() - cap;
            for (Location s : r.getStops()) if (!seen.add(s.getId())) penalty += 10;
        }
        if (seen.size() != expected) penalty += Math.abs(expected - seen.size()) * 10;

        ind.routes = routes;
        ind.roadDistance = dist;
        ind.penalty = penalty;
        ind.fitnessCost = dist + penalty * 5000.0;
    }

    private Individual tournament(List<Individual> pop, Random r) {
        Individual best = null;
        for (int i = 0; i < 3; i++) {
            Individual c = pop.get(r.nextInt(pop.size()));
            if (best == null || c.fitnessCost < best.fitnessCost) best = c;
        }
        return best;
    }

    private List<Location> orderCrossover(List<Location> p1, List<Location> p2, Random r) {
        int n = p1.size();
        int a = r.nextInt(n), b = r.nextInt(n);
        int lo = Math.min(a, b), hi = Math.max(a, b);
        List<Location> child = new ArrayList<>(Collections.nCopies(n, null));
        Set<Integer> used = new HashSet<>();
        for (int i = lo; i <= hi; i++) {
            child.set(i, p1.get(i));
            used.add(p1.get(i).getId());
        }
        int idx = 0;
        for (int i = 0; i < n; i++) {
            if (child.get(i) == null) {
                while (used.contains(p2.get(idx).getId())) idx++;
                Location g = p2.get(idx++);
                child.set(i, g);
                used.add(g.getId());
            }
        }
        return child;
    }

    private void mutate(List<Location> genes, Random r) {
        int n = genes.size();
        int i = r.nextInt(n), j = r.nextInt(n);
        if (i == j) j = (i + 1) % n;
        if (r.nextBoolean()) Collections.swap(genes, i, j);
        else {
            int lo = Math.min(i, j), hi = Math.max(i, j);
            while (lo < hi) Collections.swap(genes, lo++, hi--);
        }
    }

    @Override
    public String getAlgorithmName() { return "Genetic Algorithm"; }
}
