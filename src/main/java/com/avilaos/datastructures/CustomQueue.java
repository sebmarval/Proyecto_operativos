/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.datastructures;

/**
 *
 * @author aleja
 */
public class CustomQueue<T> {
    private QueueNode<T> head;
    private QueueNode<T> tail;
    private int size;

    public CustomQueue() {
        this.head = null;
        this.tail = null;
        this.size = 0;
    }

    public synchronized void enqueue(T element) {
        QueueNode<T> newNode = new QueueNode<>(element);
        if (isEmpty()) {
            head = newNode;
            tail = newNode;
        } else {
            tail.setNext(newNode);
            tail = newNode;
        }
        size++;
    }

    public synchronized T dequeue() {
        if (isEmpty()) {
            return null;
        }
        T data = head.getData();
        head = head.getNext();
        if (head == null) {
            tail = null;
        }
        size--;
        return data;
    }

    public synchronized T peek() {
        if (isEmpty()) {
            return null;
        }
        return head.getData();
    }

    public synchronized boolean remove(T element) {
        if (isEmpty()) return false;

        if (head.getData().equals(element)) {
            dequeue();
            return true;
        }

        QueueNode<T> current = head;
        while (current.getNext() != null) {
            if (current.getNext().getData().equals(element)) {
                if (current.getNext() == tail) {
                    tail = current;
                }
                current.setNext(current.getNext().getNext());
                size--;
                return true;
            }
            current = current.getNext();
        }
        return false;
    }

    public synchronized boolean isEmpty() {
        return head == null;
    }

    public synchronized int size() {
        return size;
    }
}
