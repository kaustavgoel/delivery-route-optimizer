package com.deliveryoptimizer.datastructures;

import java.util.Objects;

public class HashTable<K, V> {

    private static final class Entry<K, V> {
        final K key;
        V value;
        Entry<K, V> next;

        Entry(K key, V value, Entry<K, V> next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    private Entry<K, V>[] buckets;
    private int size;

    @SuppressWarnings("unchecked")
    public HashTable(int initialCapacity) {
        this.buckets = (Entry<K, V>[]) new Entry[Math.max(4, initialCapacity)];
    }

    public HashTable() {
        this(16);
    }

    public void put(K key, V value) {
        if (key == null) throw new IllegalArgumentException("Key cannot be null.");
        int idx = bucketIndex(key, buckets.length);
        for (Entry<K, V> cur = buckets[idx]; cur != null; cur = cur.next) {
            if (Objects.equals(cur.key, key)) {
                cur.value = value;
                return;
            }
        }
        buckets[idx] = new Entry<>(key, value, buckets[idx]);
        size++;
        if ((double) size / buckets.length >= 0.75) {
            resize(buckets.length * 2);
        }
    }

    public V get(K key) {
        if (key == null) return null;
        int idx = bucketIndex(key, buckets.length);
        for (Entry<K, V> cur = buckets[idx]; cur != null; cur = cur.next) {
            if (Objects.equals(cur.key, key)) return cur.value;
        }
        return null;
    }

    public boolean containsKey(K key) { return get(key) != null; }

    public V remove(K key) {
        if (key == null) return null;
        int idx = bucketIndex(key, buckets.length);
        Entry<K, V> cur = buckets[idx], prev = null;
        while (cur != null) {
            if (Objects.equals(cur.key, key)) {
                if (prev == null) buckets[idx] = cur.next;
                else prev.next = cur.next;
                size--;
                return cur.value;
            }
            prev = cur;
            cur = cur.next;
        }
        return null;
    }

    public int size() { return size; }
    public int getBucketCount() { return buckets.length; }

    private int bucketIndex(K key, int capacity) {
        int h = key.hashCode();
        h ^= (h >>> 16);
        return (h & 0x7FFFFFFF) % capacity;
    }

    @SuppressWarnings("unchecked")
    private void resize(int newCap) {
        Entry<K, V>[] old = buckets;
        buckets = (Entry<K, V>[]) new Entry[newCap];
        for (Entry<K, V> head : old) {
            while (head != null) {
                Entry<K, V> next = head.next;
                int idx = bucketIndex(head.key, newCap);
                head.next = buckets[idx];
                buckets[idx] = head;
                head = next;
            }
        }
    }
}
