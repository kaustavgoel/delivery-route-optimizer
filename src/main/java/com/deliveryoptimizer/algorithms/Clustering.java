package com.deliveryoptimizer.algorithms;

import com.deliveryoptimizer.model.Location;
import com.deliveryoptimizer.model.Location.Depot;
import com.deliveryoptimizer.model.Results.ClusterResult;
import com.deliveryoptimizer.services.AppLogger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public final class Clustering {

    private Clustering() {}

    public static ClusterResult kMeans(Depot depot, List<Location> deliveries, int vehicleCount,
                                       int vehicleCapacity, long seed) {
        int k = Math.max(1, Math.min(vehicleCount, Math.max(1, deliveries.size())));
        List<List<Location>> clusters = new ArrayList<>(k);
        List<double[]> centroids = new ArrayList<>(k);
        for (int i = 0; i < k; i++) clusters.add(new ArrayList<>());
        if (deliveries.isEmpty()) return new ClusterResult("K-Means Clustering", clusters, centroids, true);

        Random random = new Random(seed);
        Location first = deliveries.get(random.nextInt(deliveries.size()));
        centroids.add(new double[]{first.getX(), first.getY()});

        while (centroids.size() < k) {
            Location farthest = deliveries.get(0);
            double maxMin = -1;
            for (Location loc : deliveries) {
                double minD = Double.POSITIVE_INFINITY;
                for (double[] c : centroids) minD = Math.min(minD, Math.hypot(loc.getX() - c[0], loc.getY() - c[1]));
                if (minD > maxMin) { maxMin = minD; farthest = loc; }
            }
            centroids.add(new double[]{farthest.getX(), farthest.getY()});
        }

        for (int iter = 0; iter < 25; iter++) {
            for (List<Location> c : clusters) c.clear();
            for (Location loc : deliveries) {
                int best = 0;
                double bestD = Double.POSITIVE_INFINITY;
                for (int i = 0; i < k; i++) {
                    double d = Math.hypot(loc.getX() - centroids.get(i)[0], loc.getY() - centroids.get(i)[1]);
                    if (d < bestD) { bestD = d; best = i; }
                }
                clusters.get(best).add(loc);
            }
            boolean shifted = false;
            for (int i = 0; i < k; i++) {
                if (clusters.get(i).isEmpty()) continue;
                double sx = 0, sy = 0;
                for (Location l : clusters.get(i)) { sx += l.getX(); sy += l.getY(); }
                double nx = sx / clusters.get(i).size(), ny = sy / clusters.get(i).size();
                if (Math.hypot(nx - centroids.get(i)[0], ny - centroids.get(i)[1]) > 0.5) shifted = true;
                centroids.get(i)[0] = nx;
                centroids.get(i)[1] = ny;
            }
            if (!shifted) break;
        }

        boolean feasible = repairCapacity(clusters, centroids, vehicleCapacity);
        AppLogger.getInstance().info("K-Means", String.format("Grouped %d nodes into %d clusters (feasible=%s).",
                deliveries.size(), k, feasible));
        return new ClusterResult("K-Means Clustering", clusters, centroids, feasible);
    }

    public static ClusterResult sweep(Depot depot, List<Location> deliveries, int vehicleCount, int vehicleCapacity) {
        List<Location> sorted = new ArrayList<>(deliveries);
        sorted.sort(Comparator.comparingDouble(l -> Math.atan2(l.getY() - depot.getY(), l.getX() - depot.getX())));

        List<List<Location>> clusters = new ArrayList<>();
        for (int i = 0; i < vehicleCount; i++) clusters.add(new ArrayList<>());
        int[] loads = new int[vehicleCount];
        int curVeh = 0;
        boolean feasible = true;

        for (Location loc : sorted) {
            if (loads[curVeh] + loc.getDemand() <= vehicleCapacity) {
                clusters.get(curVeh).add(loc);
                loads[curVeh] += loc.getDemand();
            } else {
                int target = -1;
                for (int v = 0; v < vehicleCount; v++) {
                    if (loads[v] + loc.getDemand() <= vehicleCapacity) {
                        target = v;
                        if (v > curVeh) curVeh = v;
                        break;
                    }
                }
                if (target != -1) {
                    clusters.get(target).add(loc);
                    loads[target] += loc.getDemand();
                } else {
                    clusters.get(curVeh).add(loc);
                    loads[curVeh] += loc.getDemand();
                    feasible = false;
                }
            }
        }

        List<double[]> centroids = new ArrayList<>();
        for (List<Location> c : clusters) {
            if (c.isEmpty()) centroids.add(new double[]{depot.getX(), depot.getY()});
            else {
                double sx = 0, sy = 0;
                for (Location l : c) { sx += l.getX(); sy += l.getY(); }
                centroids.add(new double[]{sx / c.size(), sy / c.size()});
            }
        }
        AppLogger.getInstance().info("Sweep", String.format("Polar Sweep grouped %d nodes across %d vehicles (feasible=%s).",
                deliveries.size(), vehicleCount, feasible));
        return new ClusterResult("Sweep Algorithm", clusters, centroids, feasible);
    }

    private static boolean repairCapacity(List<List<Location>> clusters, List<double[]> centroids, int cap) {
        int[] loads = new int[clusters.size()];
        for (int i = 0; i < clusters.size(); i++) {
            for (Location l : clusters.get(i)) loads[i] += l.getDemand();
        }
        for (int i = 0; i < clusters.size(); i++) {
            if (loads[i] <= cap) continue;
            double[] cI = centroids.get(i);
            clusters.get(i).sort(Comparator.comparingDouble(
                    (Location l) -> Math.hypot(l.getX() - cI[0], l.getY() - cI[1])).reversed());
            List<Location> keep = new ArrayList<>();
            for (Location loc : clusters.get(i)) {
                if (loads[i] <= cap) { keep.add(loc); continue; }
                int bestTarget = -1;
                double bestDist = Double.POSITIVE_INFINITY;
                for (int j = 0; j < clusters.size(); j++) {
                    if (i != j && loads[j] + loc.getDemand() <= cap) {
                        double d = Math.hypot(loc.getX() - centroids.get(j)[0], loc.getY() - centroids.get(j)[1]);
                        if (d < bestDist) { bestDist = d; bestTarget = j; }
                    }
                }
                if (bestTarget != -1) {
                    clusters.get(bestTarget).add(loc);
                    loads[bestTarget] += loc.getDemand();
                    loads[i] -= loc.getDemand();
                } else {
                    keep.add(loc);
                }
            }
            clusters.get(i).clear();
            clusters.get(i).addAll(keep);
        }
        for (int l : loads) if (l > cap) return false;
        return true;
    }
}
