/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.scheduling;
import com.avilaos.enums.SchedulingPolicy;

/**
 *
 * @author aleja
 */
public final class SchedulerFactory {

    private SchedulerFactory() {
    }

    public static SchedulingAlgorithm create(SchedulingPolicy policy, int quantum) {
        if (policy == null) {
            throw new IllegalArgumentException();
        }
        return switch (policy) {
            case FCFS -> new FCFSScheduler();
            case ROUND_ROBIN -> new RoundRobinScheduler(quantum);
            case EDF -> new EDFScheduler();
            case PREEMPTIVE_PRIORITY -> new PreemptivePriorityScheduler();
        };
    }
}
