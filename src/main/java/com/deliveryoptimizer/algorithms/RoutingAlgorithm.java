package com.deliveryoptimizer.algorithms;

import com.deliveryoptimizer.graph.DistanceMatrix;
import com.deliveryoptimizer.graph.RoadNetwork;
import com.deliveryoptimizer.model.ProblemConfig;
import com.deliveryoptimizer.model.Results.OptimizationResult;
import com.deliveryoptimizer.model.Vehicle.Fleet;

public interface RoutingAlgorithm {
    OptimizationResult optimize(RoadNetwork network, DistanceMatrix matrix, Fleet fleet, ProblemConfig config);
    String getAlgorithmName();
}
