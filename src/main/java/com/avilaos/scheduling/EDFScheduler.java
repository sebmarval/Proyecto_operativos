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
public class EDFScheduler implements SchedulingAlgorithm {

    @Override
    public SchedulingPolicy getPolicy() {
        return SchedulingPolicy.EDF;
    }

    @Override
    public int selectNextIndex(CustomLinkedList<ProcessControlBlock> readyQueue) {
        if (readyQueue == null || readyQueue.isEmpty()) {
            return -1;
        }

        int bestIndex = 0;
        int minDeadline = readyQueue.get(0).getDeadline();

        for (int i = 1; i < readyQueue.getSize(); i++) {
            ProcessControlBlock p = readyQueue.get(i);
            if (p.getDeadline() < minDeadline) {
                minDeadline = p.getDeadline();
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    @Override
    public boolean shouldPreempt(ProcessControlBlock running,
                          CustomLinkedList<ProcessControlBlock> readyQueue,
                          int quantumUsed) {
        if (running == null || readyQueue == null || readyQueue.isEmpty()) {
            return false;
        }

        for (int i = 0; i < readyQueue.getSize(); i++) {
            if (readyQueue.get(i).getDeadline() < running.getDeadline()) {
                return true;
            }
        }
        return false;
    }
}
