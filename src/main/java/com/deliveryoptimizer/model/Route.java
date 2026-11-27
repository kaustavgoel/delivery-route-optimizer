package com.deliveryoptimizer.model;

import com.deliveryoptimizer.graph.DistanceMatrix;
import com.deliveryoptimizer.model.Location.Depot;
import com.deliveryoptimizer.model.Location.Priority;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Route {

    public static class Road {
        private final Location from;
        private final Location to;
        private final double distance;

        public Road(Location from, Location to, double distance) {
            this.from = Objects.requireNonNull(from);
            this.to = Objects.requireNonNull(to);
            if (distance <= 0) throw new IllegalArgumentException("Road distance must be positive.");
            this.distance = distance;
        }

        public Location getFrom() { return from; }
        public Location getTo() { return to; }
        public double getDistance() { return distance; }
    }

    public static class Delivery implements Comparable<Delivery> {
        private final int deliveryId;
        private final Location destination;
        private final int packageWeight;
        private final Priority priority;
        private final int valueScore;

        public Delivery(int deliveryId, Location destination) {
            this.deliveryId = deliveryId;
            this.destination = Objects.requireNonNull(destination);
            this.packageWeight = destination.getDemand();
            this.priority = destination.getPriority();
            this.valueScore = packageWeight * priority.getWeight() + priority.getWeight() * 5;
        }

        public int getDeliveryId() { return deliveryId; }
        public Location getDestination() { return destination; }
        public int getPackageWeight() { return packageWeight; }
        public Priority getPriority() { return priority; }
        public int getValueScore() { return valueScore; }

        @Override
        public int compareTo(Delivery other) {
            int p = Integer.compare(this.priority.getWeight(), other.priority.getWeight());
            if (p != 0) return p;
            int v = Integer.compare(this.valueScore, other.valueScore);
            return v != 0 ? v : Integer.compare(other.deliveryId, this.deliveryId);
        }
    }

    private final Depot depot;
    private final List<Location> stops;
    private final List<Location> fullRoadPath;
    private double cachedDistance;

    public Route(Depot depot) {
        this.depot = depot;
        this.stops = new ArrayList<>();
        this.fullRoadPath = new ArrayList<>();
    }

    public Route(Route other) {
        this.depot = other.depot;
        this.stops = new ArrayList<>(other.stops);
        this.fullRoadPath = new ArrayList<>(other.fullRoadPath);
        this.cachedDistance = other.cachedDistance;
    }

    public Depot getDepot() { return depot; }

    public void addStop(Location loc) {
        if (loc != null && !loc.isDepot()) stops.add(loc);
    }

    public void addStop(int index, Location loc) {
        if (loc != null && !loc.isDepot()) stops.add(index, loc);
    }

    public List<Location> getStops() { return Collections.unmodifiableList(stops); }
    public List<Location> getMutableStops() { return stops; }
    public boolean isEmpty() { return stops.isEmpty(); }
    public int getStopCount() { return stops.size(); }
    public double getCachedDistance() { return cachedDistance; }

    public List<Location> getRouteSequence() {
        List<Location> seq = new ArrayList<>();
        if (depot == null) return seq;
        seq.add(depot);
        if (!stops.isEmpty()) {
            seq.addAll(stops);
            seq.add(depot);
        }
        return seq;
    }

    public double calculateTotalDistance(DistanceMatrix matrix) {
        if (stops.isEmpty()) {
            cachedDistance = 0.0;
            return 0.0;
        }
        double total = 0.0;
        int prevId = depot.getId();
        for (Location stop : stops) {
            total += matrix.getDistance(prevId, stop.getId());
            prevId = stop.getId();
        }
        total += matrix.getDistance(prevId, depot.getId());
        cachedDistance = total;
        return total;
    }

    public int calculateTotalDemand() {
        int sum = 0;
        for (Location stop : stops) sum += stop.getDemand();
        return sum;
    }

    public List<Location> reconstructFullRoadPath(DistanceMatrix matrix) {
        fullRoadPath.clear();
        if (stops.isEmpty() || depot == null) return Collections.emptyList();
        List<Location> seq = getRouteSequence();
        for (int i = 0; i < seq.size() - 1; i++) {
            List<Location> seg = matrix.getPath(seq.get(i).getId(), seq.get(i + 1).getId());
            if (seg.isEmpty()) continue;
            int startIdx = fullRoadPath.isEmpty() ? 0 : 1;
            for (int j = startIdx; j < seg.size(); j++) fullRoadPath.add(seg.get(j));
        }
        calculateTotalDistance(matrix);
        return Collections.unmodifiableList(fullRoadPath);
    }

    public List<Location> getFullRoadPath() { return Collections.unmodifiableList(fullRoadPath); }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("D");
        for (Location s : stops) sb.append(" -> ").append(s.getId());
        if (!stops.isEmpty()) sb.append(" -> D");
        return sb.toString();
    }
}
