/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.datastructures;

/**
 *
 * @author aleja
 */
public class CustomLinkedList<T> {
    private Node<T> head;
    private int size;

    public CustomLinkedList() {
        this.head = null;
        this.size = 0;
    }

    public synchronized void add(T element) {
        Node<T> newNode = new Node<>(element);
        if (head == null) {
            head = newNode;
        } else {
            Node<T> current = head;
            while (current.getNext() != null) {
                current = current.getNext();
            }
            current.setNext(newNode);
        }
        size++;
    }

    public synchronized T get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index);
        }
        Node<T> current = head;
        for (int i = 0; i < index; i++) {
            current = current.getNext();
        }
        return current.getData();
    }

    public synchronized boolean remove(T element) {
        if (head == null) return false;

        if (head.getData().equals(element)) {
            head = head.getNext();
            size--;
            return true;
        }

        Node<T> current = head;
        while (current.getNext() != null) {
            if (current.getNext().getData().equals(element)) {
                current.setNext(current.getNext().getNext());
                size--;
                return true;
            }
            current = current.getNext();
        }
        return false;
    }

    public synchronized T removeAt(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index);
        }
        if (index == 0) {
            T data = head.getData();
            head = head.getNext();
            size--;
            return data;
        }

        Node<T> current = head;
        for (int i = 0; i < index - 1; i++) {
            current = current.getNext();
        }
        T data = current.getNext().getData();
        current.setNext(current.getNext().getNext());
        size--;
        return data;
    }

    public synchronized boolean isEmpty() {
        return size == 0;
    }

    public synchronized int getSize() {
        return size;
    }
}
