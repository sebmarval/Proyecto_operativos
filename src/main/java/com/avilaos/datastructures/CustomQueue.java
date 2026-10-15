/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.datastructures;

/**
 * Cola FIFO propia (sustituye a java.util.Queue).
 *
 * Se usa para la cola de nuevos (admisión en orden de llegada) y para la
 * cola de procesos bloqueados de cada semáforo simulado.
 *
 * IMPORTANTE: sin sincronización interna; la protege quien la usa con
 * java.util.concurrent.Semaphore (ver docs/ARQUITECTURA.md).
 *
 * @author aleja
 * @author SebMarval
 */
public class CustomQueue<T> {

    private Node<T> head;
    private Node<T> tail;
    private int size;

    public CustomQueue() {
        this.head = null;
        this.tail = null;
        this.size = 0;
    }

    public void enqueue(T element) {
        Node<T> newNode = new Node<>(element);
        if (isEmpty()) {
            head = newNode;
        } else {
            tail.setNext(newNode);
        }
        tail = newNode;
        size++;
    }

    /** Extrae el primero; devuelve null si la cola está vacía. */
    public T dequeue() {
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

    public T peek() {
        return isEmpty() ? null : head.getData();
    }

    public boolean remove(T element) {
        Node<T> previous = null;
        Node<T> current = head;
        while (current != null) {
            T data = current.getData();
            if (data == element || (data != null && data.equals(element))) {
                if (previous == null) {
                    head = current.getNext();
                } else {
                    previous.setNext(current.getNext());
                }
                if (current == tail) {
                    tail = previous;
                }
                size--;
                return true;
            }
            previous = current;
            current = current.getNext();
        }
        return false;
    }

    /** Copia los elementos en orden FIFO (para la GUI). */
    public Object[] toArray() {
        Object[] result = new Object[size];
        Node<T> current = head;
        int i = 0;
        while (current != null) {
            result[i++] = current.getData();
            current = current.getNext();
        }
        return result;
    }

    public boolean isEmpty() {
        return head == null;
    }

    public int size() {
        return size;
    }
}
