package com.deliveryoptimizer.algorithms;

import com.deliveryoptimizer.graph.DistanceMatrix;
import com.deliveryoptimizer.model.Location;
import com.deliveryoptimizer.model.ProblemConfig;
import com.deliveryoptimizer.model.Route;
import com.deliveryoptimizer.services.AppLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LocalSearch {

    private LocalSearch() {}

    public static Route twoOptRoute(Route route, DistanceMatrix matrix, ProblemConfig config, boolean log) {
        Route improved = new Route(route);
        List<Location> stops = improved.getMutableStops();
        int n = stops.size();
        if (n < 2) {
            improved.calculateTotalDistance(matrix);
            return improved;
        }
        int maxIter = config != null ? config.getMaxOptimizationIterations() : 300;
        int depotId = improved.getDepot().getId();
        boolean changed = true;

        for (int iter = 0; changed && iter < maxIter; iter++) {
            changed = false;
            for (int i = 0; i < n - 1; i++) {
                for (int k = i + 1; k < n; k++) {
                    int a = i == 0 ? depotId : stops.get(i - 1).getId();
                    int b = stops.get(i).getId();
                    int c = stops.get(k).getId();
                    int d = k == n - 1 ? depotId : stops.get(k + 1).getId();
                    double removed = matrix.getDistance(a, b) + matrix.getDistance(c, d);
                    double added = matrix.getDistance(a, c) + matrix.getDistance(b, d);
                    if (added + 1e-6 < removed) {
                        for (int l = i, r = k; l < r; l++, r--) Collections.swap(stops, l, r);
                        changed = true;
                    }
                }
            }
        }
        improved.calculateTotalDistance(matrix);
        return improved;
    }

    public static List<Route> twoOptAll(List<Route> routes, DistanceMatrix matrix, ProblemConfig config, boolean log) {
        double before = routes.stream().mapToDouble(r -> r.calculateTotalDistance(matrix)).sum();
        List<Route> out = new ArrayList<>(routes.size());
        for (Route r : routes) out.add(twoOptRoute(r, matrix, config, false));
        double after = out.stream().mapToDouble(Route::getCachedDistance).sum();
        if (log) {
            AppLogger.getInstance().success("2-Opt", String.format("Routes improved: %.2f -> %.2f (saved %.2f).",
                    before, after, before - after));
        }
        return out;
    }

    public static Route orOptRoute(Route route, DistanceMatrix matrix, ProblemConfig config) {
        Route improved = new Route(route);
        List<Location> stops = improved.getMutableStops();
        if (stops.size() < 3) {
            improved.calculateTotalDistance(matrix);
            return improved;
        }
        int maxIter = config != null ? config.getMaxOptimizationIterations() : 300;
        int depotId = improved.getDepot().getId();
        boolean changed = true;

        for (int iter = 0; changed && iter < maxIter; iter++) {
            changed = false;
            for (int len = 3; len >= 1 && !changed; len--) {
                for (int i = 0; i <= stops.size() - len && !changed; i++) {
                    List<Location> chain = new ArrayList<>(stops.subList(i, i + len));
                    List<Location> rest = new ArrayList<>(stops);
                    for (int k = 0; k < len; k++) rest.remove(i);

                    double bestDist = improved.calculateTotalDistance(matrix);
                    int bestPos = -1;
                    for (int pos = 0; pos <= rest.size(); pos++) {
                        if (pos == i) continue;
                        List<Location> cand = new ArrayList<>(rest);
                        cand.addAll(pos, chain);
                        double d = seqDist(depotId, cand, matrix);
                        if (d + 1e-6 < bestDist) {
                            bestDist = d;
                            bestPos = pos;
                        }
                    }
                    if (bestPos != -1) {
                        stops.clear();
                        stops.addAll(rest);
                        stops.addAll(bestPos, chain);
                        improved.calculateTotalDistance(matrix);
                        changed = true;
                    }
                }
            }
        }
        return improved;
    }

    public static List<Route> orOptAll(List<Route> routes, DistanceMatrix matrix, ProblemConfig config,
                                       int vehicleCapacity, boolean log) {
        double before = routes.stream().mapToDouble(r -> r.calculateTotalDistance(matrix)).sum();
        List<Route> res = new ArrayList<>(routes.size());
        for (Route r : routes) res.add(orOptRoute(r, matrix, config));

        if (vehicleCapacity < Integer.MAX_VALUE && res.size() > 1) {
            int maxIter = config != null ? Math.min(120, config.getMaxOptimizationIterations()) : 100;
            boolean moved = true;
            for (int it = 0; moved && it < maxIter; it++) {
                moved = false;
                for (int from = 0; from < res.size() && !moved; from++) {
                    Route src = res.get(from);
                    for (int idx = 0; idx < src.getStopCount() && !moved; idx++) {
                        Location node = src.getStops().get(idx);
                        for (int to = 0; to < res.size() && !moved; to++) {
                            if (from == to) continue;
                            Route dst = res.get(to);
                            if (dst.calculateTotalDemand() + node.getDemand() > vehicleCapacity) continue;

                            double oldPair = src.calculateTotalDistance(matrix) + dst.calculateTotalDistance(matrix);
                            List<Location> newSrc = new ArrayList<>(src.getStops());
                            newSrc.remove(idx);
                            double newSrcDist = seqDist(src.getDepot().getId(), newSrc, matrix);

                            int bestPos = -1;
                            double bestPair = oldPair;
                            for (int pos = 0; pos <= dst.getStopCount(); pos++) {
                                List<Location> newDst = new ArrayList<>(dst.getStops());
                                newDst.add(pos, node);
                                double pair = newSrcDist + seqDist(dst.getDepot().getId(), newDst, matrix);
                                if (pair + 1e-6 < bestPair) {
                                    bestPair = pair;
                                    bestPos = pos;
                                }
                            }
                            if (bestPos != -1) {
                                src.getMutableStops().remove(idx);
                                dst.getMutableStops().add(bestPos, node);
                                moved = true;
                            }
                        }
                    }
                }
            }
        }

        double after = 0.0;
        for (int i = 0; i < res.size(); i++) {
            res.set(i, orOptRoute(res.get(i), matrix, config));
            after += res.get(i).getCachedDistance();
        }
        if (log) {
            AppLogger.getInstance().success("Or-Opt", String.format("Or-Opt completed: %.2f -> %.2f (saved %.2f).",
                    before, after, before - after));
        }
        return res;
    }

    private static double seqDist(int depotId, List<Location> stops, DistanceMatrix matrix) {
        if (stops.isEmpty()) return 0.0;
        double sum = 0.0;
        int prev = depotId;
        for (Location loc : stops) {
            sum += matrix.getDistance(prev, loc.getId());
            prev = loc.getId();
        }
        return sum + matrix.getDistance(prev, depotId);
    }
}
