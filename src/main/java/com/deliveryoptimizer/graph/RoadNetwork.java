package com.deliveryoptimizer.graph;

import com.deliveryoptimizer.datastructures.AVLTree;
import com.deliveryoptimizer.datastructures.HashTable;
import com.deliveryoptimizer.datastructures.KDTree;
import com.deliveryoptimizer.model.Exceptions.InvalidNetworkException;
import com.deliveryoptimizer.model.Location;
import com.deliveryoptimizer.model.Location.Depot;
import com.deliveryoptimizer.model.Route.Road;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class RoadNetwork {

    public static class GraphEdge {
        private final GraphNode from;
        private final GraphNode to;
        private final double weight;

        public GraphEdge(GraphNode from, GraphNode to, double weight) {
            this.from = from;
            this.to = to;
            this.weight = weight;
        }

        public GraphNode getFrom() { return from; }
        public GraphNode getTo() { return to; }
        public double getWeight() { return weight; }
    }

    public static class GraphNode {
        private final Location location;
        private final List<GraphEdge> adjacencyList = new ArrayList<>();

        public GraphNode(Location location) { this.location = location; }
        public int getId() { return location.getId(); }
        public Location getLocation() { return location; }
        public void addEdge(GraphEdge edge) { adjacencyList.add(edge); }
        public void removeEdgesTo(int id) { adjacencyList.removeIf(e -> e.getTo().getId() == id); }
        public boolean hasEdgeTo(int id) { return adjacencyList.stream().anyMatch(e -> e.getTo().getId() == id); }
        public List<GraphEdge> getAdjacencyList() { return Collections.unmodifiableList(adjacencyList); }
    }

    private Depot depot;
    private final List<GraphNode> nodes = new ArrayList<>();
    private final List<Road> roads = new ArrayList<>();
    private final HashTable<Integer, GraphNode> nodeTable = new HashTable<>(32);
    private final AVLTree<Integer, Location> locationCatalog = new AVLTree<>();
    private KDTree<Location> spatialIndex = new KDTree<>();
    private int nextNodeId = 1;

    public void setDepot(Depot depot) {
        if (depot == null) throw new InvalidNetworkException("Depot cannot be null.");
        if (this.depot != null) removeLocation(this.depot.getId());
        this.depot = depot;
        addLocation(depot);
    }

    public Depot getDepot() { return depot; }

    public void addLocation(Location location) {
        if (location == null) throw new IllegalArgumentException("Location cannot be null.");
        if (nodeTable.containsKey(location.getId())) {
            throw new InvalidNetworkException("Duplicate location ID: " + location.getId());
        }
        GraphNode node = new GraphNode(location);
        nodes.add(node);
        nodeTable.put(location.getId(), node);
        locationCatalog.insert(location.getId(), location);
        spatialIndex.insert(location.getX(), location.getY(), location);
        if (location.getId() >= nextNodeId) nextNodeId = location.getId() + 1;
    }

    public boolean removeLocation(int locationId) {
        GraphNode target = nodeTable.get(locationId);
        if (target == null || (depot != null && depot.getId() == locationId)) return false;
        nodes.remove(target);
        nodeTable.remove(locationId);
        locationCatalog.delete(locationId);
        for (GraphNode n : nodes) n.removeEdgesTo(locationId);
        roads.removeIf(r -> r.getFrom().getId() == locationId || r.getTo().getId() == locationId);
        rebuildSpatialIndex();
        return true;
    }

    public void addRoad(Location a, Location b, double distance) {
        if (a == null || b == null || a.getId() == b.getId()) return;
        GraphNode nA = nodeTable.get(a.getId()), nB = nodeTable.get(b.getId());
        if (nA == null || nB == null) throw new InvalidNetworkException("Locations must be in RoadNetwork.");
        if (nA.hasEdgeTo(b.getId())) return;
        roads.add(new Road(a, b, distance));
        nA.addEdge(new GraphEdge(nA, nB, distance));
        nB.addEdge(new GraphEdge(nB, nA, distance));
    }

    public boolean hasRoad(int idA, int idB) {
        GraphNode nA = nodeTable.get(idA);
        return nA != null && nA.hasEdgeTo(idB);
    }

    public GraphNode getNode(int id) { return nodeTable.get(id); }
    public Location getLocation(int id) { return locationCatalog.search(id); }
    public Location findNearestLocation(double x, double y, double r) { return spatialIndex.findNearestWithin(x, y, r); }
    public Location findNearestLocation(double x, double y) { return spatialIndex.findNearest(x, y); }
    public List<GraphNode> getNodes() { return Collections.unmodifiableList(nodes); }
    public List<Location> getAllLocationsSorted() { return locationCatalog.inOrderValues(); }
    public List<Road> getRoads() { return Collections.unmodifiableList(roads); }
    public int getNodeCount() { return nodes.size(); }
    public int getRoadCount() { return roads.size(); }
    public int allocateNextId() { return nextNodeId++; }

    public List<Location> getDeliveryLocations() {
        List<Location> list = new ArrayList<>();
        for (Location loc : locationCatalog.inOrderValues()) {
            if (!loc.isDepot()) list.add(loc);
        }
        return list;
    }

    public int getTotalDeliveryDemand() {
        int sum = 0;
        for (GraphNode n : nodes) if (!n.getLocation().isDepot()) sum += n.getLocation().getDemand();
        return sum;
    }

    public boolean isConnected() {
        if (nodes.isEmpty()) return true;
        GraphNode start = depot != null ? nodeTable.get(depot.getId()) : nodes.get(0);
        if (start == null) return false;
        HashTable<Integer, Boolean> visited = new HashTable<>(nodes.size() * 2);
        Queue<GraphNode> queue = new LinkedList<>();
        queue.offer(start);
        visited.put(start.getId(), Boolean.TRUE);
        while (!queue.isEmpty()) {
            for (GraphEdge e : queue.poll().getAdjacencyList()) {
                if (!visited.containsKey(e.getTo().getId())) {
                    visited.put(e.getTo().getId(), Boolean.TRUE);
                    queue.offer(e.getTo());
                }
            }
        }
        return visited.size() == nodes.size();
    }

    public void ensureConnectivity() {
        if (nodes.size() <= 1 || isConnected()) return;
        GraphNode start = depot != null ? nodeTable.get(depot.getId()) : nodes.get(0);
        HashTable<Integer, Boolean> visited = new HashTable<>(nodes.size() * 2);
        Queue<GraphNode> queue = new LinkedList<>();
        queue.offer(start);
        visited.put(start.getId(), Boolean.TRUE);
        while (!queue.isEmpty()) {
            for (GraphEdge e : queue.poll().getAdjacencyList()) {
                if (!visited.containsKey(e.getTo().getId())) {
                    visited.put(e.getTo().getId(), Boolean.TRUE);
                    queue.offer(e.getTo());
                }
            }
        }
        for (GraphNode node : nodes) {
            if (!visited.containsKey(node.getId())) {
                GraphNode best = null;
                double bestDist = Double.POSITIVE_INFINITY;
                for (GraphNode cand : nodes) {
                    if (visited.containsKey(cand.getId())) {
                        double d = node.getLocation().distanceTo(cand.getLocation());
                        if (d < bestDist) { bestDist = d; best = cand; }
                    }
                }
                if (best != null) {
                    addRoad(node.getLocation(), best.getLocation(), Math.max(1.0, bestDist));
                    visited.put(node.getId(), Boolean.TRUE);
                }
            }
        }
    }

    public void resetDeliveryStates() {
        for (GraphNode n : nodes) n.getLocation().resetDeliveryState();
    }

    private void rebuildSpatialIndex() {
        spatialIndex = new KDTree<>();
        for (GraphNode n : nodes) spatialIndex.insert(n.getLocation().getX(), n.getLocation().getY(), n.getLocation());
    }
}
