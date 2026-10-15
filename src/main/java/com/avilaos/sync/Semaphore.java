/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.sync;
import com.avilaos.model.ProcessControlBlock;
import com.avilaos.datastructures.CustomQueue;
import com.avilaos.datastructures.CustomQueue;
/**
 *
 * @author aleja
 */
public class Semaphore {

    private int value;
    private final String id;

    public Semaphore(String id, int initialValue) {
        this.id = id;
        this.value = initialValue;
    }

    public synchronized void semWait(ProcessControlBlock process) {
        this.value--;
        if (this.value < 0) {
            // TODO: Agregar el proceso a la cola de bloqueados del semaforo 
        }
    }

    public synchronized void semSignal() {
        this.value++;
        if (this.value <= 0) {
            // TODO: Despertar el siguiente proceso de la cola 
        }
    }

    public int getValue() {
        return value;
    }

    public String getId() {
        return id;
    }
}
