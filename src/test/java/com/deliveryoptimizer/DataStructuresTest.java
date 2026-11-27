package com.deliveryoptimizer;

import com.deliveryoptimizer.datastructures.AVLTree;
import com.deliveryoptimizer.datastructures.BinaryHeap;
import com.deliveryoptimizer.datastructures.DoublyLinkedList;
import com.deliveryoptimizer.datastructures.HashTable;
import com.deliveryoptimizer.datastructures.KDTree;
import com.deliveryoptimizer.datastructures.KnapsackSolver;
import com.deliveryoptimizer.model.Location;
import com.deliveryoptimizer.model.Location.Priority;
import com.deliveryoptimizer.model.Route.Delivery;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataStructuresTest {

    @Test
    void testMinAndMaxBinaryHeap() {
        BinaryHeap<Integer> min = BinaryHeap.minHeap();
        BinaryHeap<Integer> max = BinaryHeap.maxHeap();
        for (int v : new int[]{42, 12, 99, 5, 18, 1, 27}) {
            min.insert(v);
            max.insert(v);
        }
        List<Integer> asc = new ArrayList<>();
        while (!min.isEmpty()) asc.add(min.extractTop());
        assertEquals(List.of(1, 5, 12, 18, 27, 42, 99), asc);
        assertEquals(99, max.extractTop());
        assertEquals(42, max.extractTop());
    }

    @Test
    void testAVLTreeSelfBalancing() {
        AVLTree<Integer, String> avl = new AVLTree<>();
        for (int i = 1; i <= 50; i++) avl.insert(i, "Node-" + i);
        assertEquals(50, avl.size());
        assertTrue(avl.isBalanced());
        assertEquals("Node-27", avl.search(27));

        for (int i = 1; i <= 20; i++) assertTrue(avl.delete(i));
        assertEquals(30, avl.size());
        assertTrue(avl.isBalanced());
        assertNull(avl.search(10));
    }

    @Test
    void testHashTableCollisionsAndResize() {
        record CollidingKey(int id) {
            @Override public int hashCode() { return 42; }
        }
        HashTable<CollidingKey, String> table = new HashTable<>(4);
        for (int i = 0; i < 30; i++) table.put(new CollidingKey(i), "Val-" + i);
        assertEquals(30, table.size());
        assertTrue(table.getBucketCount() > 4);
        assertEquals("Val-17", table.get(new CollidingKey(17)));
        assertEquals("Val-17", table.remove(new CollidingKey(17)));
        assertNull(table.get(new CollidingKey(17)));
    }

    @Test
    void testKDTreeAndDoublyLinkedListAndKnapsack() {
        KDTree<Location> tree = new KDTree<>();
        Location a = new Location(1, 10, 10, 5, Priority.NORMAL);
        Location c = new Location(3, 52, 48, 5, Priority.NORMAL);
        tree.insert(a.getX(), a.getY(), a);
        tree.insert(c.getX(), c.getY(), c);
        assertEquals(3, tree.findNearest(50, 50).getId());
        assertEquals(2, tree.rangeSearch(0, 0, 100, 100).size());

        DoublyLinkedList<String> dll = new DoublyLinkedList<>();
        dll.addLast("B");
        dll.addFirst("A");
        dll.addLast("C");
        assertTrue(dll.remove("B"));
        assertEquals("A", dll.removeFirst());
        assertEquals("C", dll.removeLast());

        List<Delivery> deliveries = List.of(
                new Delivery(1, new Location(1, 0, 0, 10, Priority.LOW)),
                new Delivery(2, new Location(2, 0, 0, 20, Priority.URGENT)),
                new Delivery(3, new Location(3, 0, 0, 15, Priority.HIGH))
        );
        KnapsackSolver.KnapsackResult res = new KnapsackSolver().selectOptimalDeliveries(deliveries, 35);
        assertEquals(35, res.getTotalWeight());
        assertEquals(2, res.getSelectedDeliveries().size());
    }
}
