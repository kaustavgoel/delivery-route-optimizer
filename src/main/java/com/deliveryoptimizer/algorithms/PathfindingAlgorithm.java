package com.deliveryoptimizer.algorithms;

import com.deliveryoptimizer.graph.RoadNetwork;
import com.deliveryoptimizer.model.Location;
import com.deliveryoptimizer.model.Results.PathfindingResult;

public interface PathfindingAlgorithm {
    PathfindingResult findShortestPath(RoadNetwork network, Location source, Location target);
    String getAlgorithmName();
}
