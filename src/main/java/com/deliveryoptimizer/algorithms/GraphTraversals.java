package com.deliveryoptimizer.algorithms;

import com.deliveryoptimizer.datastructures.HashTable;
import com.deliveryoptimizer.graph.RoadNetwork;
import com.deliveryoptimizer.graph.RoadNetwork.GraphEdge;
import com.deliveryoptimizer.graph.RoadNetwork.GraphNode;
import com.deliveryoptimizer.model.Location;
import com.deliveryoptimizer.model.Results.TraversalResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public final class GraphTraversals {

    private GraphTraversals() {}

    public static TraversalResult bfs(RoadNetwork network, Location start, Location target) {
        HashTable<Integer, Boolean> visited = new HashTable<>(network.getNodeCount() * 2 + 8);
        HashTable<Integer, Integer> parent = new HashTable<>(network.getNodeCount() * 2 + 8);
        Queue<GraphNode> queue = new LinkedList<>();
        List<Location> order = new ArrayList<>();

        visited.put(start.getId(), Boolean.TRUE);
        queue.offer(network.getNode(start.getId()));

        while (!queue.isEmpty()) {
            GraphNode cur = queue.poll();
            order.add(cur.getLocation());
            if (target != null && cur.getId() == target.getId()) break;
            for (GraphEdge e : cur.getAdjacencyList()) {
                int nextId = e.getTo().getId();
                if (!visited.containsKey(nextId)) {
                    visited.put(nextId, Boolean.TRUE);
                    parent.put(nextId, cur.getId());
                    queue.offer(e.getTo());
                }
            }
        }

        List<Location> path = buildPath(network, parent, start.getId(), target, visited);
        return new TraversalResult("BFS", order, path,
                target == null && order.size() == network.getNodeCount(),
                order.size(), network.getNodeCount());
    }

    public static TraversalResult dfs(RoadNetwork network, Location start, Location target) {
        HashTable<Integer, Boolean> visited = new HashTable<>(network.getNodeCount() * 2 + 8);
        HashTable<Integer, Integer> parent = new HashTable<>(network.getNodeCount() * 2 + 8);
        List<Location> order = new ArrayList<>();
        dfsRec(network.getNode(start.getId()), target, visited, parent, order);

        List<Location> path = buildPath(network, parent, start.getId(), target, visited);
        return new TraversalResult("DFS", order, path,
                target == null && order.size() == network.getNodeCount(),
                order.size(), network.getNodeCount());
    }

    private static boolean dfsRec(GraphNode cur, Location target, HashTable<Integer, Boolean> visited,
                                  HashTable<Integer, Integer> parent, List<Location> order) {
        visited.put(cur.getId(), Boolean.TRUE);
        order.add(cur.getLocation());
        if (target != null && cur.getId() == target.getId()) return true;
        for (GraphEdge e : cur.getAdjacencyList()) {
            int nextId = e.getTo().getId();
            if (!visited.containsKey(nextId)) {
                parent.put(nextId, cur.getId());
                if (dfsRec(e.getTo(), target, visited, parent, order) && target != null) return true;
            }
        }
        return false;
    }

    private static List<Location> buildPath(RoadNetwork net, HashTable<Integer, Integer> parent,
                                            int startId, Location target, HashTable<Integer, Boolean> visited) {
        if (target == null || !visited.containsKey(target.getId())) return Collections.emptyList();
        List<Location> path = new ArrayList<>();
        Integer cur = target.getId();
        while (cur != null) {
            path.add(net.getNode(cur).getLocation());
            if (cur == startId) break;
            cur = parent.get(cur);
        }
        Collections.reverse(path);
        return path;
    }
}
