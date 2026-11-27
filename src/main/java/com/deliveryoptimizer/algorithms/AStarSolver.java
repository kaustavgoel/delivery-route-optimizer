package com.deliveryoptimizer.algorithms;

import com.deliveryoptimizer.datastructures.BinaryHeap;
import com.deliveryoptimizer.datastructures.HashTable;
import com.deliveryoptimizer.graph.RoadNetwork;
import com.deliveryoptimizer.graph.RoadNetwork.GraphEdge;
import com.deliveryoptimizer.graph.RoadNetwork.GraphNode;
import com.deliveryoptimizer.model.Location;
import com.deliveryoptimizer.model.Results.PathfindingResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AStarSolver implements PathfindingAlgorithm {

    private static final class Entry implements Comparable<Entry> {
        final int nodeId;
        final double fScore;
        Entry(int nodeId, double fScore) { this.nodeId = nodeId; this.fScore = fScore; }
        @Override public int compareTo(Entry o) { return Double.compare(this.fScore, o.fScore); }
    }

    @Override
    public PathfindingResult findShortestPath(RoadNetwork network, Location source, Location target) {
        long startNano = System.nanoTime();
        int cap = Math.max(16, network.getNodeCount() * 2);
        HashTable<Integer, Double> gScore = new HashTable<>(cap);
        HashTable<Integer, Integer> parent = new HashTable<>(cap);
        HashTable<Integer, Boolean> closed = new HashTable<>(cap);
        BinaryHeap<Entry> open = BinaryHeap.minHeap();

        for (GraphNode n : network.getNodes()) gScore.put(n.getId(), Double.POSITIVE_INFINITY);
        gScore.put(source.getId(), 0.0);
        open.insert(new Entry(source.getId(), source.distanceTo(target)));

        List<Location> explored = new ArrayList<>();
        int relaxed = 0;

        while (!open.isEmpty()) {
            int u = open.extractTop().nodeId;
            if (closed.containsKey(u)) continue;
            closed.put(u, Boolean.TRUE);
            GraphNode uNode = network.getNode(u);
            explored.add(uNode.getLocation());
            if (u == target.getId()) break;

            double gU = gScore.get(u);
            for (GraphEdge e : uNode.getAdjacencyList()) {
                int v = e.getTo().getId();
                if (closed.containsKey(v)) continue;
                double candG = gU + e.getWeight();
                if (candG < gScore.get(v)) {
                    gScore.put(v, candG);
                    parent.put(v, u);
                    double h = e.getTo().getLocation().distanceTo(target);
                    open.insert(new Entry(v, candG + h));
                    relaxed++;
                }
            }
        }

        double total = gScore.get(target.getId());
        boolean reachable = Double.isFinite(total);
        List<Location> path = reachable ? DijkstraSolver.reconstruct(network, parent, source.getId(), target.getId())
                : Collections.emptyList();
        return new PathfindingResult(getAlgorithmName(), source, target, path, explored,
                total, explored.size(), relaxed, System.nanoTime() - startNano, reachable);
    }

    @Override
    public String getAlgorithmName() { return "A* Search"; }
}
