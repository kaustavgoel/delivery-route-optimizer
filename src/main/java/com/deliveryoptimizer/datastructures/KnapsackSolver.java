package com.deliveryoptimizer.datastructures;

import com.deliveryoptimizer.model.Route.Delivery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KnapsackSolver {

    public static final class KnapsackResult {
        private final List<Delivery> selectedDeliveries;
        private final int totalWeight;
        private final int totalValue;

        public KnapsackResult(List<Delivery> selectedDeliveries, int totalWeight, int totalValue) {
            this.selectedDeliveries = Collections.unmodifiableList(selectedDeliveries);
            this.totalWeight = totalWeight;
            this.totalValue = totalValue;
        }

        public List<Delivery> getSelectedDeliveries() { return selectedDeliveries; }
        public int getTotalWeight() { return totalWeight; }
        public int getTotalValue() { return totalValue; }
    }

    public KnapsackResult selectOptimalDeliveries(List<Delivery> candidates, int remainingCapacity) {
        if (candidates == null || candidates.isEmpty() || remainingCapacity <= 0) {
            return new KnapsackResult(Collections.emptyList(), 0, 0);
        }

        int n = candidates.size();
        int[][] dp = new int[n + 1][remainingCapacity + 1];

        for (int i = 1; i <= n; i++) {
            Delivery d = candidates.get(i - 1);
            int wt = d.getPackageWeight();
            int val = d.getValueScore();
            for (int w = 0; w <= remainingCapacity; w++) {
                dp[i][w] = dp[i - 1][w];
                if (wt <= w && dp[i - 1][w - wt] + val > dp[i][w]) {
                    dp[i][w] = dp[i - 1][w - wt] + val;
                }
            }
        }

        List<Delivery> chosen = new ArrayList<>();
        int w = remainingCapacity, totalWeight = 0;
        for (int i = n; i >= 1; i--) {
            if (dp[i][w] != dp[i - 1][w]) {
                Delivery d = candidates.get(i - 1);
                chosen.add(d);
                w -= d.getPackageWeight();
                totalWeight += d.getPackageWeight();
            }
        }
        Collections.reverse(chosen);
        return new KnapsackResult(chosen, totalWeight, dp[n][remainingCapacity]);
    }
}
