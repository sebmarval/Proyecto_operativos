/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.scheduling;
import com.avilaos.datastructures.CustomLinkedList;
import com.avilaos.enums.SchedulingPolicy;
import com.avilaos.model.ProcessControlBlock;
/**
 *
 * @author aleja
 */
public class RoundRobinScheduler implements SchedulingAlgorithm {

    private int quantum;

    public RoundRobinScheduler(int quantum) {
        setQuantum(quantum);
    }

    public final void setQuantum(int quantum) {
        if (quantum <= 0) {
            throw new IllegalArgumentException();
        }
        this.quantum = quantum;
    }

    public int getQuantum() {
        return quantum;
    }

    @Override
    public SchedulingPolicy getPolicy() {
        return SchedulingPolicy.ROUND_ROBIN;
    }

    @Override
    public int selectNextIndex(CustomLinkedList<ProcessControlBlock> readyQueue) {
        if (readyQueue == null || readyQueue.isEmpty()) {
            return -1;
        }
        return 0;
    }

    @Override
    public boolean shouldPreempt(ProcessControlBlock running,
                          CustomLinkedList<ProcessControlBlock> readyQueue,
                          int quantumUsed) {
        return quantumUsed >= quantum && readyQueue != null && !readyQueue.isEmpty();
    }
}
