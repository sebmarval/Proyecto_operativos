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
public interface SchedulingAlgorithm {

    SchedulingPolicy getPolicy();

    int selectNextIndex(CustomLinkedList<ProcessControlBlock> readyQueue);

    boolean shouldPreempt(ProcessControlBlock running,
                          CustomLinkedList<ProcessControlBlock> readyQueue,
                          int quantumUsed);
}