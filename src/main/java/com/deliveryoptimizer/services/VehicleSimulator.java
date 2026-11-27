package com.deliveryoptimizer.services;

import com.deliveryoptimizer.datastructures.DoublyLinkedList;
import com.deliveryoptimizer.graph.DistanceMatrix;
import com.deliveryoptimizer.model.Location;
import com.deliveryoptimizer.model.Location.DeliveryStatus;
import com.deliveryoptimizer.model.Location.Depot;
import com.deliveryoptimizer.model.ProblemConfig;
import com.deliveryoptimizer.model.Route;
import com.deliveryoptimizer.model.Vehicle;
import com.deliveryoptimizer.model.Vehicle.Fleet;
import com.deliveryoptimizer.model.Vehicle.VehicleStatus;
import javafx.animation.AnimationTimer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class VehicleSimulator {

    public enum SimulationState {
        IDLE("Idle"), READY("Ready"), RUNNING("Running"), PAUSED("Paused"), COMPLETED("Completed");
        private final String label;
        SimulationState(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum EventType {
        FRAME_TICK, DELIVERY_COMPLETED, VEHICLE_RETURNED,
        SIMULATION_STARTED, SIMULATION_PAUSED, SIMULATION_RESUMED,
        SIMULATION_STOPPED, SIMULATION_COMPLETED, ROUTES_UPDATED
    }

    public interface SimulationListener {
        void onSimulationUpdated(SimulationState state, EventType eventType);
    }

    private static final class Tracker {
        final Vehicle vehicle;
        final List<Location> roadPath;
        final Set<Integer> targets = new HashSet<>();
        final Set<Integer> done = new HashSet<>();
        int segIdx = 0;
        double segPx = 0.0;
        boolean finished = false;

        Tracker(Vehicle v, List<Location> path) {
            this.vehicle = v;
            this.roadPath = path;
            if (v.getAssignedRoute() != null) {
                for (Location s : v.getAssignedRoute().getStops()) targets.add(s.getId());
            }
            if (path.size() < 2) finished = true;
        }
    }

    private final AppLogger logger = AppLogger.getInstance();
    private final List<SimulationListener> listeners = new CopyOnWriteArrayList<>();
    private final DoublyLinkedList<String> eventHistory = new DoublyLinkedList<>();
    private final List<double[]> completionTimeline = new ArrayList<>();
    private final Map<Integer, Tracker> trackers = new HashMap<>();

    private Fleet fleet;
    private SimulationState state = SimulationState.IDLE;
    private double elapsedSimSeconds = 0.0;
    private double speedMultiplier = 1.0;
    private int expectedDeliveries = 0;
    private int completedDeliveries = 0;
    private double lastSampleSec = 0.0;

    private AnimationTimer timer;
    private long lastNanos = -1L;

    public void addListener(SimulationListener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }

    public void prepare(Fleet fleet, DistanceMatrix matrix) {
        stopTimer();
        this.fleet = fleet;
        trackers.clear();
        eventHistory.clear();
        completionTimeline.clear();
        elapsedSimSeconds = 0.0;
        completedDeliveries = 0;
        expectedDeliveries = 0;
        lastSampleSec = 0.0;

        if (fleet == null) { state = SimulationState.IDLE; return; }
        Depot depot = fleet.getDepot();
        for (Vehicle v : fleet.getVehicles()) {
            v.resetToDepot(depot);
            Route route = v.getAssignedRoute();
            List<Location> path = Collections.emptyList();
            if (route != null && !route.isEmpty()) {
                if (matrix != null && route.getFullRoadPath().isEmpty()) route.reconstructFullRoadPath(matrix);
                path = route.getFullRoadPath().isEmpty() ? route.getRouteSequence() : route.getFullRoadPath();
                expectedDeliveries += route.getStopCount();
                v.setVehicleStatus(VehicleStatus.ASSIGNED);
            } else {
                v.setVehicleStatus(VehicleStatus.IDLE);
            }
            trackers.put(v.getVehicleId(), new Tracker(v, path));
        }
        completionTimeline.add(new double[]{0.0, 0.0});
        state = expectedDeliveries > 0 ? SimulationState.READY : SimulationState.IDLE;
        notifyListeners(EventType.ROUTES_UPDATED, "Simulation prepared.");
    }

    public void start() {
        startHeadless();
        startTimer();
    }

    public void startHeadless() {
        if (fleet == null || expectedDeliveries == 0) return;
        if (state == SimulationState.COMPLETED || state == SimulationState.IDLE) reset();
        state = SimulationState.RUNNING;
        for (Tracker t : trackers.values()) {
            if (!t.finished) {
                t.vehicle.setVehicleStatus(VehicleStatus.EN_ROUTE);
                for (Location s : t.vehicle.getAssignedRoute().getStops()) {
                    if (s.getDeliveryStatus() == DeliveryStatus.PENDING) s.setDeliveryStatus(DeliveryStatus.IN_TRANSIT);
                }
            }
        }
        logger.info("Simulation", "Vehicle animation started.");
        notifyListeners(EventType.SIMULATION_STARTED, "Simulation started.");
    }

    public void pause() {
        if (state == SimulationState.RUNNING) {
            state = SimulationState.PAUSED;
            stopTimer();
            logger.info("Simulation", "Simulation paused.");
            notifyListeners(EventType.SIMULATION_PAUSED, "Simulation paused.");
        }
    }

    public void resume() {
        if (state == SimulationState.PAUSED) {
            state = SimulationState.RUNNING;
            startTimer();
            logger.info("Simulation", "Simulation resumed.");
            notifyListeners(EventType.SIMULATION_RESUMED, "Simulation resumed.");
        }
    }

    public void stop() {
        stopTimer();
        if (fleet == null) { state = SimulationState.IDLE; return; }
        reset();
        logger.info("Simulation", "Simulation stopped.");
        notifyListeners(EventType.SIMULATION_STOPPED, "Simulation stopped.");
    }

    public void reset() {
        if (fleet == null) return;
        Depot depot = fleet.getDepot();
        elapsedSimSeconds = 0.0;
        completedDeliveries = 0;
        lastSampleSec = 0.0;
        completionTimeline.clear();
        completionTimeline.add(new double[]{0.0, 0.0});

        for (Vehicle v : fleet.getVehicles()) {
            v.resetToDepot(depot);
            Route r = v.getAssignedRoute();
            List<Location> path = (r != null && !r.isEmpty())
                    ? (r.getFullRoadPath().isEmpty() ? r.getRouteSequence() : r.getFullRoadPath())
                    : Collections.emptyList();
            trackers.put(v.getVehicleId(), new Tracker(v, path));
        }
        state = expectedDeliveries > 0 ? SimulationState.READY : SimulationState.IDLE;
        notifyListeners(EventType.ROUTES_UPDATED, "Simulation reset.");
    }

    public void step(double deltaSec) {
        if (state != SimulationState.RUNNING || fleet == null) return;
        double dt = Math.min(0.1, Math.max(0.0, deltaSec)) * speedMultiplier;
        elapsedSimSeconds += dt;
        double moveDist = ProblemConfig.BASE_VEHICLE_SPEED * dt;

        boolean allDone = true;
        for (Tracker t : trackers.values()) {
            if (!t.finished) {
                allDone = false;
                advanceTracker(t, moveDist);
            }
        }
        if (elapsedSimSeconds - lastSampleSec >= 0.25) {
            completionTimeline.add(new double[]{elapsedSimSeconds, completedDeliveries});
            lastSampleSec = elapsedSimSeconds;
        }
        if (allDone) {
            state = SimulationState.COMPLETED;
            completionTimeline.add(new double[]{elapsedSimSeconds, completedDeliveries});
            logger.success("Simulation", String.format("All %d deliveries completed in %.1fs.",
                    completedDeliveries, elapsedSimSeconds));
            notifyListeners(EventType.SIMULATION_COMPLETED, "All routes completed.");
        } else {
            notifyListeners(EventType.FRAME_TICK, null);
        }
    }

    private void advanceTracker(Tracker t, double budget) {
        List<Location> path = t.roadPath;
        Vehicle v = t.vehicle;
        while (budget > 0 && t.segIdx < path.size() - 1) {
            Location from = path.get(t.segIdx), to = path.get(t.segIdx + 1);
            double len = Math.max(0.001, from.distanceTo(to));
            double left = len - t.segPx;

            if (budget >= left) {
                budget -= left;
                v.addDistanceTraveled(left);
                t.segIdx++;
                t.segPx = 0.0;
                v.updatePosition(to.getX(), to.getY());

                if (t.targets.contains(to.getId()) && !t.done.contains(to.getId())) {
                    t.done.add(to.getId());
                    to.setDeliveryStatus(DeliveryStatus.COMPLETED);
                    v.incrementCompletedDeliveries();
                    completedDeliveries++;
                    v.setVehicleStatus(VehicleStatus.DELIVERING);
                    completionTimeline.add(new double[]{elapsedSimSeconds, completedDeliveries});
                    logger.success("Fleet", String.format("%s delivered Node %d (%d/%d).",
                            v.getName(), to.getId(), v.getCompletedDeliveries(), v.getAssignedRoute().getStopCount()));
                    notifyListeners(EventType.DELIVERY_COMPLETED, v.getName() + " delivered Node " + to.getId());
                } else if (to.isDepot() && t.segIdx == path.size() - 1) {
                    v.setVehicleStatus(VehicleStatus.COMPLETED);
                    t.finished = true;
                    notifyListeners(EventType.VEHICLE_RETURNED, v.getName() + " returned to depot.");
                    return;
                }
            } else {
                t.segPx += budget;
                v.addDistanceTraveled(budget);
                double frac = t.segPx / len;
                v.updatePosition(from.getX() + (to.getX() - from.getX()) * frac,
                        from.getY() + (to.getY() - from.getY()) * frac);
                budget = 0;
                v.setVehicleStatus(t.done.size() == t.targets.size()
                        ? VehicleStatus.RETURNING : VehicleStatus.EN_ROUTE);
            }
        }
        if (t.segIdx >= path.size() - 1 && !t.finished) {
            t.finished = true;
            v.setVehicleStatus(VehicleStatus.COMPLETED);
        }
    }

    private void notifyListeners(EventType type, String msg) {
        if (msg != null) {
            eventHistory.addLast(msg);
            while (eventHistory.size() > 400) eventHistory.removeFirst();
        }
        for (SimulationListener l : listeners) l.onSimulationUpdated(state, type);
    }

    private void startTimer() {
        if (timer == null) {
            timer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (lastNanos < 0) { lastNanos = now; return; }
                    double dt = (now - lastNanos) / 1_000_000_000.0;
                    lastNanos = now;
                    step(dt);
                    if (state != SimulationState.RUNNING) stopTimer();
                }
            };
        }
        lastNanos = -1L;
        timer.start();
    }

    private void stopTimer() {
        if (timer != null) timer.stop();
        lastNanos = -1L;
    }

    public SimulationState getState() { return state; }
    public double getElapsedSimSeconds() { return elapsedSimSeconds; }
    public void setSpeedMultiplier(double speed) { this.speedMultiplier = Math.max(0.25, Math.min(8.0, speed)); }
    public int getExpectedDeliveries() { return expectedDeliveries; }
    public int getCompletedDeliveries() { return completedDeliveries; }
    public List<double[]> getCompletionTimeline() { return Collections.unmodifiableList(completionTimeline); }
}
