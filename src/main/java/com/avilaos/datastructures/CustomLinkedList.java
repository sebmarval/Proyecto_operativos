/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.datastructures;

/**
 * Lista simplemente enlazada propia (sustituye a ArrayList/LinkedList).
 *
 * Se usa para colas que necesitan acceso o extracción por posición, por
 * ejemplo la cola de listos: la política de planificación recorre la lista
 * y extrae el proceso elegido con removeAt(index).
 *
 * IMPORTANTE: esta clase NO tiene sincronización interna. La exclusión mutua
 * entre hilos se hace desde el kernel con java.util.concurrent.Semaphore,
 * porque las operaciones compuestas (recorrer + extraer) deben ser atómicas
 * en conjunto, y eso no se logra sincronizando cada método por separado.
 *
 * @author aleja
 * @author SebMarval
 */
public class CustomLinkedList<T> {

    private Node<T> head;
    private Node<T> tail;
    private int size;

    public CustomLinkedList() {
        this.head = null;
        this.tail = null;
        this.size = 0;
    }

    /** Agrega al final en O(1) gracias al puntero tail. */
    public void add(T element) {
        Node<T> newNode = new Node<>(element);
        if (head == null) {
            head = newNode;
        } else {
            tail.setNext(newNode);
        }
        tail = newNode;
        size++;
    }

    public T get(int index) {
        checkIndex(index);
        Node<T> current = head;
        for (int i = 0; i < index; i++) {
            current = current.getNext();
        }
        return current.getData();
    }

    /** Elimina la primera aparición del elemento (comparación por identidad o equals). */
    public boolean remove(T element) {
        Node<T> previous = null;
        Node<T> current = head;
        while (current != null) {
            if (sameElement(current.getData(), element)) {
                unlink(previous, current);
                return true;
            }
            previous = current;
            current = current.getNext();
        }
        return false;
    }

    public T removeAt(int index) {
        checkIndex(index);
        Node<T> previous = null;
        Node<T> current = head;
        for (int i = 0; i < index; i++) {
            previous = current;
            current = current.getNext();
        }
        unlink(previous, current);
        return current.getData();
    }

    public boolean contains(T element) {
        Node<T> current = head;
        while (current != null) {
            if (sameElement(current.getData(), element)) {
                return true;
            }
            current = current.getNext();
        }
        return false;
    }

    /** Copia los elementos a un arreglo (para que la GUI lea sin tocar la lista). */
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

    public void clear() {
        head = null;
        tail = null;
        size = 0;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int getSize() {
        return size;
    }

    // ---------------------------------------------------------------- privados

    private void unlink(Node<T> previous, Node<T> current) {
        if (previous == null) {
            head = current.getNext();
        } else {
            previous.setNext(current.getNext());
        }
        if (current == tail) {
            tail = previous;
        }
        size--;
    }

    private boolean sameElement(T a, T b) {
        return a == b || (a != null && a.equals(b));
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index);
        }
    }
}
