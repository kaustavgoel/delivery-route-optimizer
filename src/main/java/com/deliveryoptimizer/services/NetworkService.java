package com.deliveryoptimizer.services;

import com.deliveryoptimizer.graph.RoadNetwork;
import com.deliveryoptimizer.model.Location;
import com.deliveryoptimizer.model.Location.Depot;
import com.deliveryoptimizer.model.Location.Priority;
import com.deliveryoptimizer.model.ProblemConfig;
import com.deliveryoptimizer.model.Vehicle.Fleet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class NetworkService {

    private final AppLogger logger = AppLogger.getInstance();

    public RoadNetwork generateRandomNetwork(ProblemConfig config) {
        config.validate();
        Random random = new Random(config.getRandomSeed());

        RoadNetwork network = new RoadNetwork();
        Depot depot = new Depot(0, ProblemConfig.MAP_WIDTH / 2.0, ProblemConfig.MAP_HEIGHT / 2.0);
        network.setDepot(depot);

        List<Location> allNodes = new ArrayList<>();
        allNodes.add(depot);

        double minSpacing = Math.max(38.0, 260.0 / Math.sqrt(config.getNodeCount()));
        for (int i = 1; i <= config.getNodeCount(); i++) {
            double x = 0, y = 0;
            boolean placed = false;
            for (int attempt = 0; attempt < 60 && !placed; attempt++) {
                double cx = randRange(random, ProblemConfig.MAP_PADDING, ProblemConfig.MAP_WIDTH - ProblemConfig.MAP_PADDING);
                double cy = randRange(random, ProblemConfig.MAP_PADDING, ProblemConfig.MAP_HEIGHT - ProblemConfig.MAP_PADDING);
                boolean close = false;
                for (Location ex : allNodes) {
                    if (ex.distanceTo(cx, cy) < minSpacing) { close = true; break; }
                }
                if (!close) { x = cx; y = cy; placed = true; }
            }
            if (!placed) {
                x = randRange(random, ProblemConfig.MAP_PADDING, ProblemConfig.MAP_WIDTH - ProblemConfig.MAP_PADDING);
                y = randRange(random, ProblemConfig.MAP_PADDING, ProblemConfig.MAP_HEIGHT - ProblemConfig.MAP_PADDING);
            }
            int demand = config.getMinDemand() + random.nextInt(config.getMaxDemand() - config.getMinDemand() + 1);
            Location loc = new Location(i, "D-" + i, x, y, demand, pickPriority(random, config.getDefaultPriority()));
            network.addLocation(loc);
            allNodes.add(loc);
        }

        int n = allNodes.size();
        boolean[] inMst = new boolean[n];
        double[] minDist = new double[n];
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) { minDist[i] = Double.POSITIVE_INFINITY; parent[i] = -1; }
        minDist[0] = 0.0;

        for (int iter = 0; iter < n; iter++) {
            int u = -1;
            for (int i = 0; i < n; i++) if (!inMst[i] && (u == -1 || minDist[i] < minDist[u])) u = i;
            inMst[u] = true;
            if (parent[u] != -1) {
                Location a = allNodes.get(u), b = allNodes.get(parent[u]);
                network.addRoad(a, b, a.distanceTo(b) * randRange(random, 1.0, 1.15));
            }
            for (int v = 0; v < n; v++) {
                if (!inMst[v]) {
                    double d = allNodes.get(u).distanceTo(allNodes.get(v));
                    if (d < minDist[v]) { minDist[v] = d; parent[v] = u; }
                }
            }
        }

        int extraTarget = Math.max(1, (int) Math.round(config.getRoadDensity() * 5.0));
        for (int i = 0; i < n; i++) {
            Location src = allNodes.get(i);
            List<Location> nearest = new ArrayList<>(allNodes);
            nearest.sort(Comparator.comparingDouble(src::distanceTo));
            int added = 0;
            for (int j = 1; j < nearest.size() && added < extraTarget + 2; j++) {
                Location dst = nearest.get(j);
                if (!network.hasRoad(src.getId(), dst.getId())) {
                    if (random.nextDouble() <= config.getRoadDensity() + 0.25) {
                        network.addRoad(src, dst, src.distanceTo(dst) * randRange(random, 1.02, 1.22));
                        added++;
                    }
                } else {
                    added++;
                }
            }
        }

        logger.info("Network", String.format("Generated %d nodes and %d roads (seed=%d).",
                config.getNodeCount(), network.getRoadCount(), config.getRandomSeed()));
        return network;
    }

    public Fleet createFleet(RoadNetwork network, ProblemConfig config) {
        Fleet fleet = new Fleet(network.getDepot(), config.getVehicleCount(), config.getVehicleCapacity());
        logger.info("Fleet", String.format("Initialized %d vehicles (cap=%d each, total=%d).",
                fleet.size(), config.getVehicleCapacity(), fleet.getTotalCapacity()));
        return fleet;
    }

    public Location addManualNode(RoadNetwork network, double x, double y, int demand, Priority priority) {
        int id = network.allocateNextId();
        Location loc = new Location(id, "D-" + id, x, y, demand, priority);
        List<Location> existing = network.getAllLocationsSorted();
        network.addLocation(loc);

        List<Location> nearest = new ArrayList<>(existing);
        nearest.sort(Comparator.comparingDouble(loc::distanceTo));
        for (int i = 0; i < Math.min(3, nearest.size()); i++) {
            network.addRoad(loc, nearest.get(i), Math.max(1.0, loc.distanceTo(nearest.get(i)) * 1.05));
        }
        logger.info("Network", String.format("Added Node %d at (%.0f, %.0f), demand=%d.", id, x, y, demand));
        return loc;
    }

    public boolean removeNode(RoadNetwork network, int locationId) {
        boolean removed = network.removeLocation(locationId);
        if (removed) {
            network.ensureConnectivity();
            logger.info("Network", "Removed Node " + locationId + " and verified connectivity.");
        }
        return removed;
    }

    private double randRange(Random r, double min, double max) { return min + (max - min) * r.nextDouble(); }

    private Priority pickPriority(Random r, Priority fallback) {
        double p = r.nextDouble();
        if (p < 0.14) return Priority.URGENT;
        if (p < 0.36) return Priority.HIGH;
        if (p < 0.80) return fallback != null ? fallback : Priority.NORMAL;
        return Priority.LOW;
    }
}
