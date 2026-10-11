/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.datastructures;

/**
 *
 * @author aleja
 */
public class CustomStack<T> {
    private StackNode<T> top;
    private int size;

    public CustomStack() {
        this.top = null;
        this.size = 0;
    }

    public synchronized void push(T element) {
        StackNode<T> newNode = new StackNode<>(element);
        newNode.setNext(top);
        top = newNode;
        size++;
    }

    public synchronized T pop() {
        if (isEmpty()) {
            return null;
        }
        T data = top.getData();
        top = top.getNext();
        size--;
        return data;
    }

    public synchronized T peek() {
        if (isEmpty()) {
            return null;
        }
        return top.getData();
    }

    public synchronized boolean isEmpty() {
        return top == null;
    }

    public synchronized int size() {
        return size;
    }
}
