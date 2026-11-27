package com.deliveryoptimizer.graph;

import com.deliveryoptimizer.algorithms.PathfindingAlgorithm;
import com.deliveryoptimizer.model.Exceptions.InvalidNetworkException;
import com.deliveryoptimizer.model.Location;
import com.deliveryoptimizer.model.Results.PathfindingResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DistanceMatrix {

    private final Map<Integer, Integer> idToIndex;
    private final double[][] distances;
    private final List<Location>[][] paths;
    private final long computationTimeMs;

    @SuppressWarnings("unchecked")
    public DistanceMatrix(RoadNetwork network, PathfindingAlgorithm pathfinder) {
        if (network == null || network.getNodeCount() == 0 || !network.isConnected()) {
            throw new InvalidNetworkException("Road network must be non-empty and connected.");
        }
        long start = System.nanoTime();
        List<Location> locations = network.getAllLocationsSorted();
        int n = locations.size();
        this.idToIndex = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) idToIndex.put(locations.get(i).getId(), i);
        this.distances = new double[n][n];
        this.paths = (List<Location>[][]) new List[n][n];

        for (int i = 0; i < n; i++) {
            Location src = locations.get(i);
            paths[i][i] = List.of(src);
            for (int j = i + 1; j < n; j++) {
                Location dst = locations.get(j);
                PathfindingResult res = pathfinder.findShortestPath(network, src, dst);
                if (!res.isReachable()) {
                    throw new InvalidNetworkException("No path between " + src.getId() + " and " + dst.getId());
                }
                distances[i][j] = distances[j][i] = res.getTotalDistance();
                paths[i][j] = res.getPath();
                List<Location> rev = new ArrayList<>(res.getPath());
                Collections.reverse(rev);
                paths[j][i] = Collections.unmodifiableList(rev);
            }
        }
        this.computationTimeMs = (System.nanoTime() - start) / 1_000_000L;
    }

    public double getDistance(int fromId, int toId) {
        Integer i = idToIndex.get(fromId), j = idToIndex.get(toId);
        if (i == null || j == null) throw new InvalidNetworkException("Unknown node ID: " + fromId + " -> " + toId);
        return distances[i][j];
    }

    public double getDistance(Location a, Location b) { return getDistance(a.getId(), b.getId()); }

    public List<Location> getPath(int fromId, int toId) {
        Integer i = idToIndex.get(fromId), j = idToIndex.get(toId);
        return (i == null || j == null) ? Collections.emptyList() : paths[i][j];
    }

    public long getComputationTimeMs() { return computationTimeMs; }
    public int size() { return idToIndex.size(); }
}
