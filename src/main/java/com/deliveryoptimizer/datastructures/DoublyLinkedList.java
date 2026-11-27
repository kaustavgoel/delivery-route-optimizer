package com.deliveryoptimizer.datastructures;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

public class DoublyLinkedList<T> implements Iterable<T> {

    private static final class Node<T> {
        T data;
        Node<T> prev;
        Node<T> next;
        Node(T data) { this.data = data; }
    }

    private Node<T> head;
    private Node<T> tail;
    private int size;

    public void addFirst(T item) {
        Node<T> node = new Node<>(item);
        if (head == null) head = tail = node;
        else {
            node.next = head;
            head.prev = node;
            head = node;
        }
        size++;
    }

    public void addLast(T item) {
        Node<T> node = new Node<>(item);
        if (tail == null) head = tail = node;
        else {
            tail.next = node;
            node.prev = tail;
            tail = node;
        }
        size++;
    }

    public T removeFirst() {
        if (head == null) throw new NoSuchElementException("List is empty.");
        T data = head.data;
        head = head.next;
        if (head != null) head.prev = null;
        else tail = null;
        size--;
        return data;
    }

    public T removeLast() {
        if (tail == null) throw new NoSuchElementException("List is empty.");
        T data = tail.data;
        tail = tail.prev;
        if (tail != null) tail.next = null;
        else head = null;
        size--;
        return data;
    }

    public boolean remove(T target) {
        for (Node<T> cur = head; cur != null; cur = cur.next) {
            if (Objects.equals(cur.data, target)) {
                if (cur.prev != null) cur.prev.next = cur.next;
                else head = cur.next;
                if (cur.next != null) cur.next.prev = cur.prev;
                else tail = cur.prev;
                size--;
                return true;
            }
        }
        return false;
    }

    public T get(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
        Node<T> cur = head;
        for (int i = 0; i < index; i++) cur = cur.next;
        return cur.data;
    }

    public T getFirst() { return head.data; }
    public T getLast() { return tail.data; }
    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
    public void clear() { head = tail = null; size = 0; }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private Node<T> cur = head;
            @Override public boolean hasNext() { return cur != null; }
            @Override public T next() {
                if (cur == null) throw new NoSuchElementException();
                T val = cur.data;
                cur = cur.next;
                return val;
            }
        };
    }
}
