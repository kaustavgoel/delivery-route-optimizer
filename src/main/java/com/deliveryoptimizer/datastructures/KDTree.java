package com.deliveryoptimizer.datastructures;

import java.util.ArrayList;
import java.util.List;

public class KDTree<T> {

    private static final class KDNode<T> {
        final double x;
        final double y;
        final T value;
        KDNode<T> left;
        KDNode<T> right;

        KDNode(double x, double y, T value) {
            this.x = x;
            this.y = y;
            this.value = value;
        }
    }

    private KDNode<T> root;
    private int size;

    public void insert(double x, double y, T value) {
        root = insertRec(root, x, y, value, 0);
        size++;
    }

    public T findNearest(double tx, double ty) {
        return findNearestWithin(tx, ty, Double.POSITIVE_INFINITY);
    }

    public T findNearestWithin(double tx, double ty, double maxRadius) {
        if (root == null) return null;
        KDNode<T>[] bestNode = new KDNode[1];
        double[] bestDistSq = { Double.isInfinite(maxRadius) ? Double.POSITIVE_INFINITY : maxRadius * maxRadius };
        nearestRec(root, tx, ty, 0, bestNode, bestDistSq);
        return bestNode[0] != null ? bestNode[0].value : null;
    }

    public List<T> rangeSearch(double minX, double minY, double maxX, double maxY) {
        List<T> found = new ArrayList<>();
        rangeRec(root, minX, minY, maxX, maxY, 0, found);
        return found;
    }

    public int size() { return size; }

    private KDNode<T> insertRec(KDNode<T> node, double x, double y, T value, int depth) {
        if (node == null) return new KDNode<>(x, y, value);
        boolean splitX = (depth % 2 == 0);
        if (splitX ? x < node.x : y < node.y) node.left = insertRec(node.left, x, y, value, depth + 1);
        else node.right = insertRec(node.right, x, y, value, depth + 1);
        return node;
    }

    private void nearestRec(KDNode<T> node, double tx, double ty, int depth,
                            KDNode<T>[] bestNode, double[] bestDistSq) {
        if (node == null) return;
        double dx = node.x - tx, dy = node.y - ty;
        double dSq = dx * dx + dy * dy;
        if (dSq < bestDistSq[0]) {
            bestDistSq[0] = dSq;
            bestNode[0] = node;
        }
        boolean splitX = (depth % 2 == 0);
        double diff = splitX ? (tx - node.x) : (ty - node.y);
        KDNode<T> near = diff < 0 ? node.left : node.right;
        KDNode<T> far = diff < 0 ? node.right : node.left;

        nearestRec(near, tx, ty, depth + 1, bestNode, bestDistSq);
        if (diff * diff < bestDistSq[0]) {
            nearestRec(far, tx, ty, depth + 1, bestNode, bestDistSq);
        }
    }

    private void rangeRec(KDNode<T> node, double minX, double minY, double maxX, double maxY, int depth, List<T> out) {
        if (node == null) return;
        if (node.x >= minX && node.x <= maxX && node.y >= minY && node.y <= maxY) out.add(node.value);
        boolean splitX = (depth % 2 == 0);
        if (splitX) {
            if (minX <= node.x) rangeRec(node.left, minX, minY, maxX, maxY, depth + 1, out);
            if (maxX >= node.x) rangeRec(node.right, minX, minY, maxX, maxY, depth + 1, out);
        } else {
            if (minY <= node.y) rangeRec(node.left, minX, minY, maxX, maxY, depth + 1, out);
            if (maxY >= node.y) rangeRec(node.right, minX, minY, maxX, maxY, depth + 1, out);
        }
    }
}
