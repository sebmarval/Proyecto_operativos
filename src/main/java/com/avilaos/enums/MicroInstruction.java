/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.enums;

/**
 * Instrucciones que puede ejecutar un proceso en un ciclo.
 *
 * USER_WORK es trabajo de usuario (calcular, producir o consumir un elemento).
 * Las demás son llamadas al sistema sobre un buffer y se ejecutan en modo SO;
 * cada una cuenta como 1 instrucción = 1 ciclo (supuesto del enunciado).
 *
 * Micro-programa por elemento (Stallings, Fig. 5.13):
 *   Productor:  USER_WORK x k, WAIT_EMPTY, WAIT_MUTEX, ADD_ITEM, SIGNAL_MUTEX, SIGNAL_FULL
 *   Consumidor: WAIT_FULL, WAIT_MUTEX, REMOVE_ITEM, SIGNAL_MUTEX, SIGNAL_EMPTY, USER_WORK x k
 *
 * @author SebMarval
 */
public enum MicroInstruction {

    USER_WORK(null, false),
    WAIT_EMPTY(SemaphoreType.EMPTY, true),
    WAIT_FULL(SemaphoreType.FULL, true),
    WAIT_MUTEX(SemaphoreType.MUTEX, true),
    SIGNAL_MUTEX(SemaphoreType.MUTEX, false),
    SIGNAL_FULL(SemaphoreType.FULL, false),
    SIGNAL_EMPTY(SemaphoreType.EMPTY, false),
    ADD_ITEM(null, false),
    REMOVE_ITEM(null, false);

    /** Instrucciones de SO por elemento (2 semWait + operación + 2 semSignal). */
    public static final int SYSTEM_OPS_PER_ITEM = 5;

    private final SemaphoreType semaphore;
    private final boolean isWait;

    MicroInstruction(SemaphoreType semaphore, boolean isWait) {
        this.semaphore = semaphore;
        this.isWait = isWait;
    }

    /** True si se ejecuta en modo SO (toda instrucción salvo USER_WORK). */
    public boolean isSystemCall() {
        return this != USER_WORK;
    }

    public boolean isWait() {
        return isWait;
    }

    public boolean isSignal() {
        return semaphore != null && !isWait;
    }

    /** Semáforo sobre el que actúa (null si no es wait/signal). */
    public SemaphoreType getSemaphore() {
        return semaphore;
    }
}

