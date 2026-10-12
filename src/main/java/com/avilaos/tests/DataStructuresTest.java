/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.tests;

import com.avilaos.datastructures.CustomLinkedList;
import com.avilaos.datastructures.CustomQueue;

/**
 * Prueba manual de las estructuras propias.
 * Ejecutar en NetBeans: clic derecho sobre este archivo -> Run File (Shift+F6).
 *
 * @author SebMarval
 */
public class DataStructuresTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testLinkedList();
        testQueue();
        System.out.println("--------------------------------");
        System.out.println("Pruebas OK: " + passed + " | Fallidas: " + failed);
    }

    private static void testLinkedList() {
        CustomLinkedList<String> list = new CustomLinkedList<>();
        check("lista nueva vacía", list.isEmpty() && list.getSize() == 0);

        list.add("A");
        list.add("B");
        list.add("C");
        check("add mantiene el orden", "A".equals(list.get(0)) && "C".equals(list.get(2)));

        check("removeAt del último devuelve C", "C".equals(list.removeAt(2)));
        list.add("D"); // si tail quedó mal, D se perdería
        check("tail se actualiza tras quitar el último", "D".equals(list.get(2)) && list.getSize() == 3);

        check("remove del primero", list.remove("A") && "B".equals(list.get(0)));
        check("contains", list.contains("D") && !list.contains("A"));
        check("toArray", list.toArray().length == 2);

        list.removeAt(0);
        list.removeAt(0);
        list.add("E"); // lista vaciada por completo y reutilizada
        check("reutilizar lista vaciada", list.getSize() == 1 && "E".equals(list.get(0)));
    }

    private static void testQueue() {
        CustomQueue<Integer> queue = new CustomQueue<>();
        check("dequeue en cola vacía es null", queue.dequeue() == null);

        queue.enqueue(1);
        queue.enqueue(2);
        queue.enqueue(3);
        check("FIFO", queue.dequeue() == 1 && queue.peek() == 2);

        check("remove del último", queue.remove(3) && queue.size() == 1);
        queue.enqueue(4); // si tail quedó mal, 4 se perdería
        check("tail correcto tras remove", queue.size() == 2 && queue.toArray()[1].equals(4));
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("[OK]    " + name);
        } else {
            failed++;
            System.out.println("[FALLA] " + name);
        }
    }
}