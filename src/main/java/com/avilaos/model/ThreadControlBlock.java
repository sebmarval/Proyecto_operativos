/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.model;
import  com.avilaos.enums.ProcessState;
/**
 *
 * @author aleja
 */
public class ThreadControlBlock {

    private final String threadId;
    private int programCounter;
    private ProcessState state;

    public ThreadControlBlock(String threadId) {
        this.threadId = threadId;
        this.programCounter = 0;
        this.state = ProcessState.NEW;
    }

    public void incrementPC() {
        this.programCounter++;
    }

    public String getThreadId() {
        return threadId;
    }

    public int getProgramCounter() {
        return programCounter;
    }

    public void setProgramCounter(int programCounter) {
        this.programCounter = programCounter;
    }

    public ProcessState getState() {
        return state;
    }

    public void setState(ProcessState state) {
        this.state = state;
    }
    // TODO: Revisar si se requieren registros adicionales de CPU
}
