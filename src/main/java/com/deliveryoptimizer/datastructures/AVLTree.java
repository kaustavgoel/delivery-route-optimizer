package com.deliveryoptimizer.datastructures;

import java.util.ArrayList;
import java.util.List;

public class AVLTree<K extends Comparable<K>, V> {

    private static final class Node<K, V> {
        K key;
        V value;
        Node<K, V> left;
        Node<K, V> right;
        int height = 1;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private Node<K, V> root;
    private int size;

    public void insert(K key, V value) {
        if (key == null) throw new IllegalArgumentException("Key cannot be null.");
        root = insertRec(root, key, value);
    }

    public V search(K key) {
        if (key == null) return null;
        Node<K, V> cur = root;
        while (cur != null) {
            int cmp = key.compareTo(cur.key);
            if (cmp < 0) cur = cur.left;
            else if (cmp > 0) cur = cur.right;
            else return cur.value;
        }
        return null;
    }

    public boolean delete(K key) {
        if (key == null || search(key) == null) return false;
        root = deleteRec(root, key);
        size--;
        return true;
    }

    public List<V> inOrderValues() {
        List<V> result = new ArrayList<>(size);
        inOrder(root, result);
        return result;
    }

    public int size() { return size; }
    public int getHeight() { return height(root); }
    public boolean isBalanced() { return isBalancedRec(root); }

    private Node<K, V> insertRec(Node<K, V> node, K key, V value) {
        if (node == null) {
            size++;
            return new Node<>(key, value);
        }
        int cmp = key.compareTo(node.key);
        if (cmp < 0) node.left = insertRec(node.left, key, value);
        else if (cmp > 0) node.right = insertRec(node.right, key, value);
        else {
            node.value = value;
            return node;
        }
        return rebalance(node);
    }

    private Node<K, V> deleteRec(Node<K, V> node, K key) {
        if (node == null) return null;
        int cmp = key.compareTo(node.key);
        if (cmp < 0) node.left = deleteRec(node.left, key);
        else if (cmp > 0) node.right = deleteRec(node.right, key);
        else if (node.left == null || node.right == null) {
            node = node.left != null ? node.left : node.right;
        } else {
            Node<K, V> succ = node.right;
            while (succ.left != null) succ = succ.left;
            node.key = succ.key;
            node.value = succ.value;
            node.right = deleteRec(node.right, succ.key);
        }
        return node == null ? null : rebalance(node);
    }

    private Node<K, V> rebalance(Node<K, V> node) {
        updateHeight(node);
        int bf = balanceFactor(node);
        if (bf > 1) {
            if (balanceFactor(node.left) < 0) node.left = rotateLeft(node.left);
            return rotateRight(node);
        }
        if (bf < -1) {
            if (balanceFactor(node.right) > 0) node.right = rotateRight(node.right);
            return rotateLeft(node);
        }
        return node;
    }

    private Node<K, V> rotateRight(Node<K, V> y) {
        Node<K, V> x = y.left;
        y.left = x.right;
        x.right = y;
        updateHeight(y);
        updateHeight(x);
        return x;
    }

    private Node<K, V> rotateLeft(Node<K, V> x) {
        Node<K, V> y = x.right;
        x.right = y.left;
        y.left = x;
        updateHeight(x);
        updateHeight(y);
        return y;
    }

    private int height(Node<K, V> n) { return n == null ? 0 : n.height; }
    private void updateHeight(Node<K, V> n) { n.height = 1 + Math.max(height(n.left), height(n.right)); }
    private int balanceFactor(Node<K, V> n) { return n == null ? 0 : height(n.left) - height(n.right); }

    private void inOrder(Node<K, V> n, List<V> out) {
        if (n == null) return;
        inOrder(n.left, out);
        out.add(n.value);
        inOrder(n.right, out);
    }

    private boolean isBalancedRec(Node<K, V> n) {
        if (n == null) return true;
        return Math.abs(balanceFactor(n)) <= 1 && isBalancedRec(n.left) && isBalancedRec(n.right);
    }
}
