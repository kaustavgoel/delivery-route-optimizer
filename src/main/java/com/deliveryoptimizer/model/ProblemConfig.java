package com.deliveryoptimizer.model;

import com.deliveryoptimizer.model.Exceptions.InvalidNetworkException;
import com.deliveryoptimizer.model.Location.Priority;

public class ProblemConfig {

    public static final double MAP_WIDTH = 1000.0;
    public static final double MAP_HEIGHT = 700.0;
    public static final double MAP_PADDING = 55.0;
    public static final double BASE_VEHICLE_SPEED = 110.0;

    public static final String[] VEHICLE_COLORS = {
        "#FF5364", "#318BFF", "#35D98A", "#8957FF", "#FF9F43", "#00D2D3", "#FF6B81", "#FECA57"
    };

    public static String getVehicleColor(int index) {
        return VEHICLE_COLORS[Math.abs(index) % VEHICLE_COLORS.length];
    }

    private int nodeCount = 22;
    private int vehicleCount = 4;
    private int vehicleCapacity = 55;
    private int minDemand = 3;
    private int maxDemand = 12;
    private double roadDensity = 0.35;
    private long randomSeed = 2026L;
    private Priority defaultPriority = Priority.NORMAL;

    private int gaPopulationSize = 60;
    private int gaGenerations = 120;
    private double gaMutationRate = 0.18;
    private double gaCrossoverRate = 0.85;

    private double saInitialTemp = 1500.0;
    private double saCoolingRate = 0.965;
    private int saMaxIterations = 450;

    private int maxOptimizationIterations = 300;

    public ProblemConfig copy() {
        ProblemConfig c = new ProblemConfig();
        c.nodeCount = nodeCount;
        c.vehicleCount = vehicleCount;
        c.vehicleCapacity = vehicleCapacity;
        c.minDemand = minDemand;
        c.maxDemand = maxDemand;
        c.roadDensity = roadDensity;
        c.randomSeed = randomSeed;
        c.defaultPriority = defaultPriority;
        c.gaPopulationSize = gaPopulationSize;
        c.gaGenerations = gaGenerations;
        c.gaMutationRate = gaMutationRate;
        c.gaCrossoverRate = gaCrossoverRate;
        c.saInitialTemp = saInitialTemp;
        c.saCoolingRate = saCoolingRate;
        c.saMaxIterations = saMaxIterations;
        c.maxOptimizationIterations = maxOptimizationIterations;
        return c;
    }

    public void validate() {
        if (nodeCount < 3 || nodeCount > 100) {
            throw new InvalidNetworkException("Delivery node count must be between 3 and 100.");
        }
        if (vehicleCount < 1 || vehicleCount > 12) {
            throw new InvalidNetworkException("Vehicle count must be between 1 and 12.");
        }
        if (vehicleCapacity < 1) {
            throw new InvalidNetworkException("Vehicle capacity must be at least 1.");
        }
        if (minDemand < 1 || maxDemand < minDemand) {
            throw new InvalidNetworkException("Demand range is invalid (min >= 1 and max >= min).");
        }
        if (maxDemand > vehicleCapacity) {
            throw new InvalidNetworkException(
                    "Max node demand (" + maxDemand + ") cannot exceed vehicle capacity (" + vehicleCapacity + ").");
        }
        if (roadDensity < 0.05 || roadDensity > 1.0) {
            throw new InvalidNetworkException("Road density must be between 0.05 and 1.0.");
        }
        if (gaPopulationSize < 10 || gaGenerations < 5) {
            throw new InvalidNetworkException("GA population must be >= 10 and generations >= 5.");
        }
        if (gaMutationRate < 0 || gaMutationRate > 1 || gaCrossoverRate < 0 || gaCrossoverRate > 1) {
            throw new InvalidNetworkException("GA mutation and crossover rates must be between 0.0 and 1.0.");
        }
        if (saInitialTemp <= 0 || saCoolingRate <= 0 || saCoolingRate >= 1) {
            throw new InvalidNetworkException("SA temperature must be > 0 and cooling rate in (0, 1).");
        }
        if (saMaxIterations < 10 || maxOptimizationIterations < 10) {
            throw new InvalidNetworkException("Optimization iterations must be at least 10.");
        }
    }

    public int getNodeCount() { return nodeCount; }
    public void setNodeCount(int nodeCount) { this.nodeCount = nodeCount; }
    public int getVehicleCount() { return vehicleCount; }
    public void setVehicleCount(int vehicleCount) { this.vehicleCount = vehicleCount; }
    public int getVehicleCapacity() { return vehicleCapacity; }
    public void setVehicleCapacity(int vehicleCapacity) { this.vehicleCapacity = vehicleCapacity; }
    public int getMinDemand() { return minDemand; }
    public void setMinDemand(int minDemand) { this.minDemand = minDemand; }
    public int getMaxDemand() { return maxDemand; }
    public void setMaxDemand(int maxDemand) { this.maxDemand = maxDemand; }
    public double getRoadDensity() { return roadDensity; }
    public void setRoadDensity(double roadDensity) { this.roadDensity = roadDensity; }
    public long getRandomSeed() { return randomSeed; }
    public void setRandomSeed(long randomSeed) { this.randomSeed = randomSeed; }
    public Priority getDefaultPriority() { return defaultPriority; }
    public void setDefaultPriority(Priority defaultPriority) { this.defaultPriority = defaultPriority; }
    public int getGaPopulationSize() { return gaPopulationSize; }
    public void setGaPopulationSize(int gaPopulationSize) { this.gaPopulationSize = gaPopulationSize; }
    public int getGaGenerations() { return gaGenerations; }
    public void setGaGenerations(int gaGenerations) { this.gaGenerations = gaGenerations; }
    public double getGaMutationRate() { return gaMutationRate; }
    public void setGaMutationRate(double gaMutationRate) { this.gaMutationRate = gaMutationRate; }
    public double getGaCrossoverRate() { return gaCrossoverRate; }
    public void setGaCrossoverRate(double gaCrossoverRate) { this.gaCrossoverRate = gaCrossoverRate; }
    public double getSaInitialTemp() { return saInitialTemp; }
    public void setSaInitialTemp(double saInitialTemp) { this.saInitialTemp = saInitialTemp; }
    public double getSaCoolingRate() { return saCoolingRate; }
    public void setSaCoolingRate(double saCoolingRate) { this.saCoolingRate = saCoolingRate; }
    public int getSaMaxIterations() { return saMaxIterations; }
    public void setSaMaxIterations(int saMaxIterations) { this.saMaxIterations = saMaxIterations; }
    public int getMaxOptimizationIterations() { return maxOptimizationIterations; }
    public void setMaxOptimizationIterations(int maxOpt) { this.maxOptimizationIterations = maxOpt; }
}
