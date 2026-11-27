package com.deliveryoptimizer.model;

import java.util.Objects;

public class Location implements Comparable<Location> {

    public enum Priority {
        LOW(1, "Low"),
        NORMAL(2, "Normal"),
        HIGH(3, "High"),
        URGENT(4, "Urgent");

        private final int weight;
        private final String label;

        Priority(int weight, String label) {
            this.weight = weight;
            this.label = label;
        }

        public int getWeight() { return weight; }
        public String getLabel() { return label; }
    }

    public enum DeliveryStatus {
        PENDING("Pending"),
        IN_TRANSIT("In Transit"),
        COMPLETED("Completed");

        private final String label;

        DeliveryStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public static class Depot extends Location {
        public Depot(int id, double x, double y) {
            super(id, "Central Depot", x, y, 0, Priority.URGENT);
            setDeliveryStatus(DeliveryStatus.COMPLETED);
        }

        @Override
        public boolean isDepot() { return true; }

        @Override
        public void setDemand(int demand) { super.setDemand(0); }

        @Override
        public void resetDeliveryState() {
            setDeliveryStatus(DeliveryStatus.COMPLETED);
            setAssignedVehicleId(null);
        }
    }

    private final int id;
    private String name;
    private double x;
    private double y;
    private int demand;
    private Priority priority;
    private DeliveryStatus deliveryStatus;
    private Integer assignedVehicleId;

    public Location(int id, double x, double y, int demand, Priority priority) {
        this(id, "Loc-" + id, x, y, demand, priority);
    }

    public Location(int id, String name, double x, double y, int demand, Priority priority) {
        if (demand < 0) throw new IllegalArgumentException("Demand cannot be negative: " + demand);
        this.id = id;
        this.name = name != null ? name : ("Loc-" + id);
        this.x = x;
        this.y = y;
        this.demand = demand;
        this.priority = priority != null ? priority : Priority.NORMAL;
        this.deliveryStatus = DeliveryStatus.PENDING;
    }

    public boolean isDepot() { return false; }
    public int getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { if (name != null && !name.isBlank()) this.name = name; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public int getDemand() { return demand; }
    public void setDemand(int demand) {
        if (demand < 0) throw new IllegalArgumentException("Demand cannot be negative.");
        this.demand = demand;
    }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority != null ? priority : Priority.NORMAL; }
    public DeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(DeliveryStatus status) {
        this.deliveryStatus = status != null ? status : DeliveryStatus.PENDING;
    }
    public Integer getAssignedVehicleId() { return assignedVehicleId; }
    public void setAssignedVehicleId(Integer assignedVehicleId) { this.assignedVehicleId = assignedVehicleId; }

    public double distanceTo(Location other) { return distanceTo(other.x, other.y); }
    public double distanceTo(double tx, double ty) { return Math.hypot(this.x - tx, this.y - ty); }

    public void resetDeliveryState() {
        this.deliveryStatus = DeliveryStatus.PENDING;
        this.assignedVehicleId = null;
    }

    @Override
    public int compareTo(Location other) { return Integer.compare(this.id, other.id); }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Location loc && id == loc.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return String.format("%s[id=%d, (%.1f, %.1f), demand=%d]",
                isDepot() ? "Depot" : "Location", id, x, y, demand);
    }
}
