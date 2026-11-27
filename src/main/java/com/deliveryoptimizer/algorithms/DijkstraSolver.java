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

public class DijkstraSolver implements PathfindingAlgorithm {

    private static final class Entry implements Comparable<Entry> {
        final int nodeId;
        final double cost;
        Entry(int nodeId, double cost) { this.nodeId = nodeId; this.cost = cost; }
        @Override public int compareTo(Entry o) { return Double.compare(this.cost, o.cost); }
    }

    @Override
    public PathfindingResult findShortestPath(RoadNetwork network, Location source, Location target) {
        long startNano = System.nanoTime();
        int cap = Math.max(16, network.getNodeCount() * 2);
        HashTable<Integer, Double> dist = new HashTable<>(cap);
        HashTable<Integer, Integer> parent = new HashTable<>(cap);
        HashTable<Integer, Boolean> visited = new HashTable<>(cap);
        BinaryHeap<Entry> pq = BinaryHeap.minHeap();

        for (GraphNode n : network.getNodes()) dist.put(n.getId(), Double.POSITIVE_INFINITY);
        dist.put(source.getId(), 0.0);
        pq.insert(new Entry(source.getId(), 0.0));

        List<Location> explored = new ArrayList<>();
        int relaxed = 0;

        while (!pq.isEmpty()) {
            int u = pq.extractTop().nodeId;
            if (visited.containsKey(u)) continue;
            visited.put(u, Boolean.TRUE);
            GraphNode uNode = network.getNode(u);
            explored.add(uNode.getLocation());
            if (u == target.getId()) break;

            double distU = dist.get(u);
            for (GraphEdge e : uNode.getAdjacencyList()) {
                int v = e.getTo().getId();
                if (visited.containsKey(v)) continue;
                double cand = distU + e.getWeight();
                if (cand < dist.get(v)) {
                    dist.put(v, cand);
                    parent.put(v, u);
                    pq.insert(new Entry(v, cand));
                    relaxed++;
                }
            }
        }

        double total = dist.get(target.getId());
        boolean reachable = Double.isFinite(total);
        List<Location> path = reachable ? reconstruct(network, parent, source.getId(), target.getId()) : Collections.emptyList();
        return new PathfindingResult(getAlgorithmName(), source, target, path, explored,
                total, explored.size(), relaxed, System.nanoTime() - startNano, reachable);
    }

    static List<Location> reconstruct(RoadNetwork net, HashTable<Integer, Integer> parent, int srcId, int dstId) {
        List<Location> path = new ArrayList<>();
        Integer cur = dstId;
        while (cur != null) {
            GraphNode node = net.getNode(cur);
            if (node == null) return Collections.emptyList();
            path.add(node.getLocation());
            if (cur == srcId) break;
            cur = parent.get(cur);
        }
        Collections.reverse(path);
        return path;
    }

    @Override
    public String getAlgorithmName() { return "Dijkstra's Algorithm"; }
}
