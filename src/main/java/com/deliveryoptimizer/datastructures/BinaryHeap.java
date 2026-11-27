package com.deliveryoptimizer.datastructures;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

public class BinaryHeap<T> {

    private final List<T> heap = new ArrayList<>();
    private final Comparator<T> comparator;

    @SuppressWarnings("unchecked")
    public static <T extends Comparable<T>> BinaryHeap<T> minHeap() {
        return new BinaryHeap<>((a, b) -> a.compareTo(b));
    }

    @SuppressWarnings("unchecked")
    public static <T extends Comparable<T>> BinaryHeap<T> maxHeap() {
        return new BinaryHeap<>((a, b) -> b.compareTo(a));
    }

    public BinaryHeap(Comparator<T> comparator) {
        if (comparator == null) throw new IllegalArgumentException("Comparator cannot be null.");
        this.comparator = comparator;
    }

    public void insert(T item) {
        if (item == null) throw new IllegalArgumentException("Cannot insert null.");
        heap.add(item);
        siftUp(heap.size() - 1);
    }

    public T peek() {
        if (heap.isEmpty()) throw new NoSuchElementException("Heap is empty.");
        return heap.get(0);
    }

    public T extractTop() {
        if (heap.isEmpty()) throw new NoSuchElementException("Heap is empty.");
        T top = heap.get(0);
        T last = heap.remove(heap.size() - 1);
        if (!heap.isEmpty()) {
            heap.set(0, last);
            siftDown(0);
        }
        return top;
    }

    public List<T> drainSorted() {
        List<T> out = new ArrayList<>(heap.size());
        BinaryHeap<T> copy = new BinaryHeap<>(comparator);
        copy.heap.addAll(this.heap);
        while (!copy.isEmpty()) out.add(copy.extractTop());
        return out;
    }

    public boolean isEmpty() { return heap.isEmpty(); }
    public int size() { return heap.size(); }

    private void siftUp(int idx) {
        while (idx > 0) {
            int parent = (idx - 1) >>> 1;
            if (comparator.compare(heap.get(idx), heap.get(parent)) < 0) {
                swap(idx, parent);
                idx = parent;
            } else {
                break;
            }
        }
    }

    private void siftDown(int idx) {
        int n = heap.size();
        while (true) {
            int left = (idx << 1) + 1;
            int right = left + 1;
            int best = idx;
            if (left < n && comparator.compare(heap.get(left), heap.get(best)) < 0) best = left;
            if (right < n && comparator.compare(heap.get(right), heap.get(best)) < 0) best = right;
            if (best != idx) {
                swap(idx, best);
                idx = best;
            } else {
                break;
            }
        }
    }

    private void swap(int i, int j) {
        T tmp = heap.get(i);
        heap.set(i, heap.get(j));
        heap.set(j, tmp);
    }
}
