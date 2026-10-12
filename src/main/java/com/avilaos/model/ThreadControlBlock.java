/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.model;

import com.avilaos.enums.ProcessState;

/**
 * Bloque de control del hilo (TCB).
 *
 * Modelo monohilo: cada proceso tiene exactamente un hilo (relación 1:1
 * PCB:TCB). El TCB guarda el contexto de ejecución del hilo: PC y MAR.
 * Por el supuesto simplificador del enunciado, ambos avanzan +1 por cada
 * instrucción ejecutada.
 *
 * @author aleja
 * @author SebMarval
 */
public class ThreadControlBlock {

    private final String threadId;
    private int programCounter;
    private int memoryAddressRegister;
    private ProcessState state;

    public ThreadControlBlock(String threadId) {
        this.threadId = threadId;
        this.programCounter = 0;
        this.memoryAddressRegister = 0;
        this.state = ProcessState.NEW;
    }

    /** Avanza PC y MAR una posición (una instrucción ejecutada). */
    public void advance() {
        this.programCounter++;
        this.memoryAddressRegister++;
    }

    public String getThreadId() {
        return threadId;
    }

    public int getProgramCounter() {
        return programCounter;
    }

    public int getMemoryAddressRegister() {
        return memoryAddressRegister;
    }

    public ProcessState getState() {
        return state;
    }

    public void setState(ProcessState state) {
        this.state = state;
    }
}