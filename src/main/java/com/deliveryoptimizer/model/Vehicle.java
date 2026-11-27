package com.deliveryoptimizer.model;

import com.deliveryoptimizer.graph.DistanceMatrix;
import com.deliveryoptimizer.model.Exceptions.CapacityExceededException;
import com.deliveryoptimizer.model.Location.DeliveryStatus;
import com.deliveryoptimizer.model.Location.Depot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Vehicle {

    public enum VehicleStatus {
        IDLE("Idle"),
        ASSIGNED("Assigned"),
        EN_ROUTE("En Route"),
        DELIVERING("Delivering"),
        RETURNING("Returning to Depot"),
        COMPLETED("Route Completed");

        private final String label;
        VehicleStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public static class Fleet {
        private final List<Vehicle> vehicles = new ArrayList<>();
        private final Depot depot;

        public Fleet(Depot depot, int vehicleCount, int vehicleCapacity) {
            this.depot = depot;
            for (int i = 1; i <= vehicleCount; i++) {
                vehicles.add(new Vehicle(i, vehicleCapacity, ProblemConfig.getVehicleColor(i - 1), depot));
            }
        }

        public List<Vehicle> getVehicles() { return Collections.unmodifiableList(vehicles); }
        public int size() { return vehicles.size(); }
        public Depot getDepot() { return depot; }

        public int getTotalCapacity() {
            int total = 0;
            for (Vehicle v : vehicles) total += v.getCapacity();
            return total;
        }

        public int getActiveVehicleCount() {
            int active = 0;
            for (Vehicle v : vehicles) {
                if (v.getAssignedRoute() != null && !v.getAssignedRoute().isEmpty()) active++;
            }
            return active;
        }

        public Vehicle getVehicleById(int id) {
            for (Vehicle v : vehicles) if (v.getVehicleId() == id) return v;
            return null;
        }

        public void clearAllRoutes() {
            for (Vehicle v : vehicles) v.assignRoute(new Route(depot));
        }
    }

    private final int vehicleId;
    private final String name;
    private final String colorHex;
    private int capacity;
    private int currentLoad;
    private double currentX;
    private double currentY;
    private Route assignedRoute;
    private VehicleStatus vehicleStatus;
    private int completedDeliveries;
    private double distanceTraveled;

    public Vehicle(int vehicleId, int capacity, String colorHex, Depot depot) {
        if (capacity < 1) throw new IllegalArgumentException("Vehicle capacity must be positive.");
        this.vehicleId = vehicleId;
        this.name = "Vehicle " + vehicleId;
        this.colorHex = colorHex;
        this.capacity = capacity;
        this.assignedRoute = depot != null ? new Route(depot) : null;
        this.vehicleStatus = VehicleStatus.IDLE;
        if (depot != null) {
            this.currentX = depot.getX();
            this.currentY = depot.getY();
        }
    }

    public void assignRoute(Route route) {
        if (route == null) throw new IllegalArgumentException("Assigned route cannot be null.");
        int demand = route.calculateTotalDemand();
        if (demand > capacity) {
            throw new CapacityExceededException(
                    "Route demand (" + demand + ") exceeds " + name + " capacity (" + capacity + ").");
        }
        this.assignedRoute = route;
        this.currentLoad = demand;
        for (Location stop : route.getStops()) stop.setAssignedVehicleId(vehicleId);
        this.vehicleStatus = route.isEmpty() ? VehicleStatus.IDLE : VehicleStatus.ASSIGNED;
        this.completedDeliveries = 0;
        this.distanceTraveled = 0.0;
        if (route.getDepot() != null) updatePosition(route.getDepot().getX(), route.getDepot().getY());
    }

    public void addDelivery(Location location) {
        if (location == null || location.isDepot()) return;
        if (currentLoad + location.getDemand() > capacity) {
            throw new CapacityExceededException("Adding Node " + location.getId() + " exceeds capacity.");
        }
        assignedRoute.addStop(location);
        currentLoad += location.getDemand();
        location.setAssignedVehicleId(vehicleId);
        vehicleStatus = VehicleStatus.ASSIGNED;
    }

    public int calculateRemainingCapacity() { return Math.max(0, capacity - currentLoad); }
    public void updatePosition(double x, double y) { this.currentX = x; this.currentY = y; }

    public void resetToDepot(Depot depot) {
        this.completedDeliveries = 0;
        this.distanceTraveled = 0.0;
        if (depot != null) updatePosition(depot.getX(), depot.getY());
        if (assignedRoute != null && !assignedRoute.isEmpty()) {
            this.vehicleStatus = VehicleStatus.ASSIGNED;
            for (Location stop : assignedRoute.getStops()) stop.setDeliveryStatus(DeliveryStatus.PENDING);
        } else {
            this.vehicleStatus = VehicleStatus.IDLE;
        }
    }

    public double getUtilizationPercentage() { return capacity == 0 ? 0.0 : (currentLoad * 100.0) / capacity; }

    public double getRouteDistance(DistanceMatrix matrix) {
        if (assignedRoute == null) return 0.0;
        return matrix != null ? assignedRoute.calculateTotalDistance(matrix) : assignedRoute.getCachedDistance();
    }

    public int getVehicleId() { return vehicleId; }
    public String getName() { return name; }
    public String getColorHex() { return colorHex; }
    public int getCapacity() { return capacity; }
    public int getCurrentLoad() { return currentLoad; }
    public double getCurrentX() { return currentX; }
    public double getCurrentY() { return currentY; }
    public Route getAssignedRoute() { return assignedRoute; }
    public VehicleStatus getVehicleStatus() { return vehicleStatus; }
    public void setVehicleStatus(VehicleStatus status) { this.vehicleStatus = status; }
    public int getCompletedDeliveries() { return completedDeliveries; }
    public void incrementCompletedDeliveries() { this.completedDeliveries++; }
    public double getDistanceTraveled() { return distanceTraveled; }
    public void addDistanceTraveled(double delta) { this.distanceTraveled += delta; }
}
