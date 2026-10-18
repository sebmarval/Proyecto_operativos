/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.sync;

import com.avilaos.datastructures.CustomQueue;
import com.avilaos.enums.SemaphoreType;
import com.avilaos.model.ProcessControlBlock;

/**
 * Semáforo SIMULADO de ÁvilaOS (no confundir con java.util.concurrent.Semaphore).
 *
 * Implementa la definición de Stallings (Fig. 5.3):
 *  - semWait: decrementa el valor; si queda negativo, el proceso se bloquea y
 *    entra a la cola del semáforo.
 *  - semSignal: incrementa el valor; si queda <= 0, había procesos esperando y
 *    se despierta al primero (FIFO, semáforo fuerte: no hay inanición).
 *
 * Con valor negativo, |valor| = número de procesos en la cola.
 *
 * Esta clase NO bloquea hilos de Java ni cambia el estado del PCB: solo dice
 * quién se bloquea o quién despierta. El kernel mueve los procesos entre colas.
 * Tampoco tiene exclusión mutua propia: la protege el lock del BoundedBuffer.
 *
 * @author SebMarval
 */
public class SimSemaphore {

    private final String name;
    private final SemaphoreType type;
    private int value;
    private final CustomQueue<ProcessControlBlock> waiting = new CustomQueue<>();

    public SimSemaphore(String name, SemaphoreType type, int initialValue) {
        if (initialValue < 0) {
            throw new IllegalArgumentException("El valor inicial de un semáforo no puede ser negativo");
        }
        if (type == SemaphoreType.MUTEX && initialValue > 1) {
            throw new IllegalArgumentException("Un semáforo binario (mutex) solo puede iniciar en 0 o 1");
        }
        this.name = name;
        this.type = type;
        this.value = initialValue;
    }

    /**
     * @return true si el proceso quedó bloqueado (entró a la cola del semáforo)
     */
    public boolean semWait(ProcessControlBlock process) {
        value--;
        if (value < 0) {
            waiting.enqueue(process);
            return true;
        }
        return false;
    }

    /**
     * @return el proceso que debe despertar, o null si nadie esperaba
     */
    public ProcessControlBlock semSignal() {
        value++;
        if (value <= 0) {
            return waiting.dequeue();
        }
        return null;
    }

    /**
     * Retira de la cola a un proceso que murió esperando (p. ej. venció su
     * deadline) y deshace su semWait para que el valor siga siendo coherente.
     *
     * @return true si el proceso estaba en la cola
     */
    public boolean cancelWait(ProcessControlBlock process) {
        if (waiting.remove(process)) {
            value++;
            return true;
        }
        return false;
    }

    public String getName() { return name; }
    public SemaphoreType getType() { return type; }
    public int getValue() { return value; }
    public int getWaitingCount() { return waiting.size(); }
    public Object[] getWaitingSnapshot() { return waiting.toArray(); }

    @Override
    public String toString() {
        return name + "=" + value + (waiting.isEmpty() ? "" : " (esperan " + waiting.size() + ")");
    }
}

