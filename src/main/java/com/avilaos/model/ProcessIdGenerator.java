/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.model;

import java.util.concurrent.Semaphore;

/**
 * Genera IDs de proceso únicos en 
 * el clúster (no por computador).
 *
 * Varios hilos pueden crear procesos a la vez (la GUI, la carga inicial),
 * así que el contador se protege con un Semaphore de Java (mutex binario).
 * Sin esto, dos hilos podrían leer el mismo valor y repetir un ID.
 *
 * @author SebMarval
 */
public final class ProcessIdGenerator {

    private static final Semaphore MUTEX = new Semaphore(1);
    private static int nextId = 1;

    private ProcessIdGenerator() {
        // Clase utilitaria: no se instancia.
    }

    public static int nextId() {
        MUTEX.acquireUninterruptibly();
        try {
            return nextId++;
        } finally {
            MUTEX.release();
        }
    }

    /** Reinicia el contador (al cargar una nueva configuración). */
    public static void reset() {
        MUTEX.acquireUninterruptibly();
        try {
            nextId = 1;
        } finally {
            MUTEX.release();
        }
    }
}