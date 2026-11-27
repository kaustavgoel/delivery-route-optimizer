package com.deliveryoptimizer;

import com.deliveryoptimizer.algorithms.AStarSolver;
import com.deliveryoptimizer.algorithms.DijkstraSolver;
import com.deliveryoptimizer.algorithms.GraphTraversals;
import com.deliveryoptimizer.graph.DistanceMatrix;
import com.deliveryoptimizer.graph.RoadNetwork;
import com.deliveryoptimizer.model.Location;
import com.deliveryoptimizer.model.Location.Depot;
import com.deliveryoptimizer.model.Location.Priority;
import com.deliveryoptimizer.model.Results.PathfindingResult;
import com.deliveryoptimizer.model.Results.TraversalResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphAndPathfindingTest {

    private RoadNetwork buildTestGraph() {
        RoadNetwork net = new RoadNetwork();
        Depot depot = new Depot(0, 0, 0);
        Location n1 = new Location(1, 30, 0, 5, Priority.NORMAL);
        Location n2 = new Location(2, 30, 40, 5, Priority.NORMAL);
        Location n3 = new Location(3, 80, 0, 5, Priority.NORMAL);
        Location n4 = new Location(4, 100, 40, 5, Priority.NORMAL);

        net.setDepot(depot);
        net.addLocation(n1);
        net.addLocation(n2);
        net.addLocation(n3);
        net.addLocation(n4);

        net.addRoad(depot, n1, 30.0);
        net.addRoad(n1, n2, 40.0);
        net.addRoad(depot, n3, 85.0);
        net.addRoad(n1, n3, 50.0);
        net.addRoad(n2, n4, 70.0);
        net.addRoad(n3, n4, 45.0);
        return net;
    }

    @Test
    void testDijkstraAndAStarExactness() {
        RoadNetwork net = buildTestGraph();
        PathfindingResult dRes = new DijkstraSolver().findShortestPath(net, net.getDepot(), net.getLocation(4));
        PathfindingResult aRes = new AStarSolver().findShortestPath(net, net.getDepot(), net.getLocation(4));

        assertTrue(dRes.isReachable());
        assertTrue(aRes.isReachable());
        assertEquals(125.0, dRes.getTotalDistance(), 1e-6);
        assertEquals(125.0, aRes.getTotalDistance(), 1e-6);
        assertTrue(aRes.getNodesExplored() <= dRes.getNodesExplored());

        DistanceMatrix matrix = new DistanceMatrix(net, new AStarSolver());
        assertEquals(125.0, matrix.getDistance(0, 4), 1e-6);
        assertEquals(125.0, matrix.getDistance(4, 0), 1e-6);
    }

    @Test
    void testBFSAndDFSAndUnreachable() {
        RoadNetwork net = buildTestGraph();
        Location isolated = new Location(99, 500, 500, 4, Priority.LOW);
        net.addLocation(isolated);

        assertFalse(net.isConnected());
        TraversalResult bfs = GraphTraversals.bfs(net, net.getDepot(), null);
        TraversalResult dfs = GraphTraversals.dfs(net, net.getDepot(), null);
        assertEquals(5, bfs.getReachableCount());
        assertEquals(5, dfs.getReachableCount());

        PathfindingResult unreach = new DijkstraSolver().findShortestPath(net, net.getDepot(), isolated);
        assertFalse(unreach.isReachable());
    }
}
