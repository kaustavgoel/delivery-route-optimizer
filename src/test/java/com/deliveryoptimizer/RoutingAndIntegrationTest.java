package com.deliveryoptimizer;

import com.deliveryoptimizer.algorithms.AStarSolver;
import com.deliveryoptimizer.algorithms.AlgorithmFactory;
import com.deliveryoptimizer.algorithms.AlgorithmFactory.PathfindingType;
import com.deliveryoptimizer.algorithms.AlgorithmFactory.RoutingType;
import com.deliveryoptimizer.algorithms.Clustering;
import com.deliveryoptimizer.algorithms.DijkstraSolver;
import com.deliveryoptimizer.algorithms.LocalSearch;
import com.deliveryoptimizer.graph.DistanceMatrix;
import com.deliveryoptimizer.graph.RoadNetwork;
import com.deliveryoptimizer.model.Exceptions.InfeasibleRoutingException;
import com.deliveryoptimizer.model.Location;
import com.deliveryoptimizer.model.Location.DeliveryStatus;
import com.deliveryoptimizer.model.Location.Depot;
import com.deliveryoptimizer.model.Location.Priority;
import com.deliveryoptimizer.model.ProblemConfig;
import com.deliveryoptimizer.model.Results.ClusterResult;
import com.deliveryoptimizer.model.Results.OptimizationResult;
import com.deliveryoptimizer.model.Route;
import com.deliveryoptimizer.model.Vehicle.Fleet;
import com.deliveryoptimizer.services.NetworkService;
import com.deliveryoptimizer.services.OptimizationService;
import com.deliveryoptimizer.services.VehicleSimulator;
import com.deliveryoptimizer.services.VehicleSimulator.SimulationState;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoutingAndIntegrationTest {

    private void assertStrictlyFeasible(OptimizationResult result, RoadNetwork network, Fleet fleet) {
        assertTrue(result.isFeasible());
        assertTrue(result.getRoutes().size() <= fleet.size());
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < result.getRoutes().size(); i++) {
            Route r = result.getRoutes().get(i);
            assertTrue(r.calculateTotalDemand() <= fleet.getVehicles().get(i).getCapacity());
            for (Location s : r.getStops()) assertTrue(seen.add(s.getId()));
        }
        assertEquals(network.getDeliveryLocations().size(), seen.size());
    }

    @Test
    void testAllNineRoutingAlgorithmsOnDefaultConfig() {
        ProblemConfig cfg = new ProblemConfig();
        RoadNetwork net = new NetworkService().generateRandomNetwork(cfg);
        DistanceMatrix matrix = new DistanceMatrix(net, new DijkstraSolver());

        for (RoutingType type : RoutingType.values()) {
            Fleet fleet = new Fleet(net.getDepot(), cfg.getVehicleCount(), cfg.getVehicleCapacity());
            OptimizationResult res = AlgorithmFactory.createRoutingAlgorithm(type).optimize(net, matrix, fleet, cfg);
            assertStrictlyFeasible(res, net, fleet);
        }
    }

    @Test
    void testTwoOptAndOrOptImproveCrossingRoute() {
        RoadNetwork net = new RoadNetwork();
        Depot depot = new Depot(0, 0, 0);
        Location a = new Location(1, 0, 100, 5, Priority.NORMAL);
        Location b = new Location(2, 100, 0, 5, Priority.NORMAL);
        Location c = new Location(3, 100, 100, 5, Priority.NORMAL);
        net.setDepot(depot);
        net.addLocation(a);
        net.addLocation(b);
        net.addLocation(c);
        List<Location> all = net.getAllLocationsSorted();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                net.addRoad(all.get(i), all.get(j), all.get(i).distanceTo(all.get(j)));
            }
        }
        DistanceMatrix matrix = new DistanceMatrix(net, new AStarSolver());
        Route crossing = new Route(depot);
        crossing.addStop(a);
        crossing.addStop(b);
        crossing.addStop(c);
        double before = crossing.calculateTotalDistance(matrix);

        Route twoOpt = LocalSearch.twoOptRoute(crossing, matrix, new ProblemConfig(), false);
        assertEquals(400.0, twoOpt.calculateTotalDistance(matrix), 1e-4);
        Route orOpt = LocalSearch.orOptRoute(crossing, matrix, new ProblemConfig());
        assertTrue(orOpt.calculateTotalDistance(matrix) < before);
    }

    @Test
    void testClusteringAndInfeasibilityCheck() {
        ProblemConfig cfg = new ProblemConfig();
        RoadNetwork net = new NetworkService().generateRandomNetwork(cfg);

        ClusterResult km = Clustering.kMeans(net.getDepot(), net.getDeliveryLocations(),
                cfg.getVehicleCount(), cfg.getVehicleCapacity(), cfg.getRandomSeed());
        ClusterResult sw = Clustering.sweep(net.getDepot(), net.getDeliveryLocations(),
                cfg.getVehicleCount(), cfg.getVehicleCapacity());
        assertTrue(km.isFeasible());
        assertTrue(sw.isFeasible());

        DistanceMatrix matrix = new DistanceMatrix(net, new AStarSolver());
        Fleet tiny = new Fleet(net.getDepot(), 2, 10);
        assertThrows(InfeasibleRoutingException.class,
                () -> AlgorithmFactory.createRoutingAlgorithm(RoutingType.CLARKE_WRIGHT)
                        .optimize(net, matrix, tiny, cfg));
    }

    @Test
    void testEndToEndSimulationAndBenchmark() {
        ProblemConfig cfg = new ProblemConfig();
        cfg.setNodeCount(16);
        cfg.setRandomSeed(777L);
        cfg.setGaPopulationSize(25);
        cfg.setGaGenerations(30);
        cfg.setSaMaxIterations(150);

        NetworkService netService = new NetworkService();
        OptimizationService optService = new OptimizationService();
        RoadNetwork net = netService.generateRandomNetwork(cfg);
        Fleet fleet = netService.createFleet(net, cfg);
        DistanceMatrix matrix = optService.buildDistanceMatrix(net, PathfindingType.A_STAR);

        OptimizationResult res = optService.runOptimization(net, matrix, fleet, cfg, RoutingType.CLARKE_WRIGHT_LOCAL_SEARCH);
        assertEquals(16, res.getTotalDeliveriesAssigned());

        Map<RoutingType, OptimizationResult> bench = optService.runBenchmarkComparison(net, matrix, cfg);
        assertEquals(7, bench.size());

        VehicleSimulator sim = new VehicleSimulator();
        sim.prepare(fleet, matrix);
        sim.setSpeedMultiplier(5.0);
        sim.startHeadless();
        for (int i = 0; i < 4000 && sim.getState() == SimulationState.RUNNING; i++) sim.step(0.05);

        assertEquals(SimulationState.COMPLETED, sim.getState());
        assertEquals(16, sim.getCompletedDeliveries());
        for (Location loc : net.getDeliveryLocations()) assertEquals(DeliveryStatus.COMPLETED, loc.getDeliveryStatus());
    }
}
